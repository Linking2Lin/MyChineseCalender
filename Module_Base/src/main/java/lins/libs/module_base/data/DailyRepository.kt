package lins.libs.module_base.data

import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 按公历日期存取数据的最小缓存契约，使仓库不直接依赖 Room 或 Android。
 *
 * 实现方必须按传入日期精确读写，不能在缺失时返回其他日期的最后一条记录。
 * 生产实现由 CalendarRepositories 连接 DAO，测试实现使用内存表和可注入的读写故障。
 */
interface DailyCache<T> {
    /** 单次查询；没有该日记录返回 null，存储故障则抛出异常交给仓库处理。 */
    suspend fun read(date: LocalDate): T?
    /** 冷数据流：订阅时读取当前记录，随后发出该日缓存的变化。 */
    fun observe(date: LocalDate): Flow<T?>
    /** 同一天只保留一份数据；实现方使用替换写入，而不是追加自增记录。 */
    suspend fun write(date: LocalDate, data: T)
    /** 只保留闭区间 [before, after]；参数与业务日期采用同一时区解释。 */
    suspend fun prune(before: LocalDate, after: LocalDate)
}

/**
 * 一次按日加载的可展示状态，数据与错误允许同时存在。
 *
 * @property date 本次查询的日期身份；即使无数据或请求失败也必须保留。
 * @property data 当前查询日的有效数据；刷新失败时可以继续显示同日缓存。
 * @property loading 是否正在重新获取；不能用 data 是否为空推断加载状态。
 * @property error 网络或业务校验失败，与磁盘缓存故障分开反馈。
 * @property cacheError 缓存读写/清理异常；非空不代表 data 不可展示。
 */
data class DateLoadResult<T>(
    val date: LocalDate,
    val data: T? = null,
    val loading: Boolean = false,
    val error: Exception? = null,
    val cacheError: Exception? = null,
)

/**
 * 黄历与 HKO 农历共用的按日加载流程：读取缓存 → 必要时请求 → 校验 → 保存 → 发布状态。
 *
 * 所有 load 在同一个 Mutex 中串行执行，包含不同日期的请求。并发普通加载在前一个
 * 请求成功后可直接复用缓存；force 请求仍会逐个访问网络，并非合并成一次强制刷新。
 *
 * 状态以查询日期为键，防止跨午夜后旧响应覆盖新日期。外部通过 observe 订阅，不能
 * 把“最近完成的请求”当成今天。CancellationException 始终向上传播，不转成业务失败。
 *
 * @param isValid 核心字段与日期校验；持久化缓存和网络数据使用相同标准。
 * @param today 注入的设备日期提供者，仅用于保留范围，便于测试跨天与时间回拨。
 * @param dispatcher observe 上游的执行环境；load 在调用方协程中执行，缓存实现应支持挂起调用。
 */
class DailyRepository<T>(
    private val cache: DailyCache<T>,
    private val fetch: suspend (LocalDate) -> T,
    private val isValid: (T, LocalDate) -> Boolean,
    private val today: () -> LocalDate = LocalDate::now,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val results = MutableStateFlow<Map<LocalDate, DateLoadResult<T>>>(emptyMap())

    /**
     * 合并缓存初值与进程内加载状态，本方法本身不发网络请求。
     * 首次发出前等待缓存查询完成，不把“尚未读取”当作“没有数据”。
     * 有本次加载状态时优先显示它，保留 loading/error 和尚未落盘的网络数据；因此后续
     * 业务写入应通过 load，不能绕过仓库直接写 DAO 并期待覆盖已有的内存状态。
     */
    fun observe(date: LocalDate): Flow<DateLoadResult<T>> = combine(
        // 包裹 observe 的创建过程，连数据库打开失败也能被 catch 接住。
        // 不预先发 null，否则组件会在每次重新订阅时短暂覆盖当天已有内容。
        flow { emitAll(cache.observe(date)) }.catch { e ->
            if (e is CancellationException) throw e
            // 观察链失败不终止 results 的订阅；具体存储故障由 load 转为 cacheError。
            emit(null)
        }, results,
    ) { cached, memory ->
        memory[date] ?: DateLoadResult(date, cached?.takeIf { isValid(it, date) })
    }.flowOn(dispatcher)

    /**
     * 加载指定日期。force=false 优先复用有效缓存；force=true 尝试联网，但失败不抹掉同日旧值。
     * 成功响应只有通过 isValid 后才能写库。可恢复异常返回状态，协程取消仍抛给调用者。
     */
    suspend fun load(date: LocalDate, force: Boolean = false): DateLoadResult<T> = mutex.withLock {
        var cacheError: Exception? = null
        val persisted = try {
            cache.read(date)?.takeIf { isValid(it, date) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cacheError = e
            null
        }
        // 磁盘不可用时仍可复用上一次未落盘的同日结果；此处绝不查找前一天或未来的数据。
        val cached = persisted ?: results.value[date]?.data?.takeIf { isValid(it, date) }

        if (cached != null && !force) {
            if (persisted == null) {
                // 上次网络成功但保存失败：重试持久化即可，避免仅因磁盘故障再次请求接口。
                try {
                    cache.write(date, cached)
                    cache.prune(today().minusDays(7), today().plusDays(2))
                    cacheError = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    cacheError = e
                }
            }
            return@withLock publish(DateLoadResult(date, cached, cacheError = cacheError))
        }
        publish(DateLoadResult(date, cached, loading = true, cacheError = cacheError))
        try {
            val data = fetch(date)
            check(isValid(data, date)) { "Incomplete or mismatched date response for $date" }
            try {
                cache.write(date, data)
                // 保留近期历史与预取日期。仅在写入路径清理，不是独立定时清理任务。
                cache.prune(today().minusDays(7), today().plusDays(2))
                cacheError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cacheError = e
            }
            publish(DateLoadResult(date, data, cacheError = cacheError))
        } catch (e: CancellationException) {
            // 撤销本次 loading，避免新订阅者看到一个已取消、不会完成的请求。
            publish(DateLoadResult(date, cached, cacheError = cacheError))
            throw e
        } catch (e: Exception) {
            publish(DateLoadResult(date, cached, error = e, cacheError = cacheError))
        }
    }

    /** 在加载锁内发布，限制常驻内存的日期范围；本次结果即使在范围外也保留到后续发布。 */
    private fun publish(result: DateLoadResult<T>): DateLoadResult<T> {
        val now = today()
        results.value = results.value.filterKeys {
            !it.isBefore(now.minusDays(7)) && !it.isAfter(now.plusDays(2))
        } + (result.date to result)
        return result
    }
}
