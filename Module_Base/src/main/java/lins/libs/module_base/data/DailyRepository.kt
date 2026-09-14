package lins.libs.module_base.data

import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
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
    /**
     * 单次查询；没有该日记录返回 null，存储故障则抛出异常交给仓库处理。
     * @param date 本次操作的公历日期，使用设备当前时区解释；不能用请求完成时的日期替换。
     * @return 指定日期的缓存数据；没有记录时返回 null，存储异常继续抛出。
     */
    suspend fun read(date: LocalDate): T?
    /**
     * 冷数据流：订阅时读取当前记录，随后发出该日缓存的变化。
     * @param date 本次操作的公历日期，使用设备当前时区解释；不能用请求完成时的日期替换。
     * @return 冷 Flow；仅发出指定日期的数据或 null，不主动发起网络请求。
     */
    fun observe(date: LocalDate): Flow<T?>
    /**
     * 同一天只保留一份数据；实现方使用替换写入，而不是追加自增记录。
     * @param date 本次操作的公历日期，使用设备当前时区解释；不能用请求完成时的日期替换。
     * @param data 本次操作使用的业务数据；写入路径要求已通过对应的有效性校验。
     * @return Unit；完成指定日期的数据替换写入，异常由上层处理。
     */
    suspend fun write(date: LocalDate, data: T)
    /**
     * 只保留闭区间 [before, after]；参数与业务日期采用同一时区解释。
     * @param before 保留区间的起始日期，包含当天；早于此值的记录将被清理。
     * @param after 保留区间的结束日期，包含当天；晚于此值的记录将被清理。
     * @return Unit；完成区间外缓存清理，异常由仓库记录并安排后续重试。
     */
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
 * 使用固定数量的日期分组锁，同日请求始终串行，今天与相邻日期的预取可独立进行。
 * 哈希碰撞最多导致不同日期排队，不影响正确性；锁数组不会随使用天数增长。
 * 普通同日并发加载复用前一次结果，force 请求仍会逐个联网。
 *
 * 状态以查询日期为键，防止跨午夜后旧响应覆盖新日期。外部通过 observe 订阅，不能
 * 把“最近完成的请求”当成今天。CancellationException 始终向上传播，不转成业务失败。
 *
 * @param isValid 核心字段与日期校验；持久化缓存和网络数据使用相同标准。
 * @param cache 按日缓存适配器，负责挂起读写、订阅与清理。
 * @param fetch 按请求日期获取网络数据的挂起函数，返回值仍需经过 isValid 校验。
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
    // 同日映射到同一把锁；固定数组限制内存占用，碰撞只会让不同日期额外排队。
    private val dateLocks = Array(32) { Mutex() }
    // 已获取但尚未完成持久化/清理的日期，取消也不能丢掉这项待办。
    private val pendingPersistence = ConcurrentHashMap.newKeySet<LocalDate>()
    // 以请求日期隔离加载状态，多个日期的发布必须通过 update 原子合并。
    private val results = MutableStateFlow<Map<LocalDate, DateLoadResult<T>>>(emptyMap())

    /**
     * 合并缓存初值与进程内加载状态，本方法本身不发网络请求。
     * 没有内存状态时等待缓存查询，不把“尚未读取”当作“没有数据”；已有内存状态则直接复用。
     * 有本次加载状态时优先显示它，保留 loading/error 和尚未落盘的网络数据；因此后续
     * 业务写入应通过 load，不能绕过仓库直接写 DAO 并期待覆盖已有的内存状态。
     * @param date 本次操作的公历日期，使用设备当前时区解释；不能用请求完成时的日期替换。
     * @return 冷 Flow<DateLoadResult<T>>；发出本日数据、加载与错误状态，不主动发起网络请求。
     */
    fun observe(date: LocalDate): Flow<DateLoadResult<T>> = combine(
        // 包裹 observe 的创建过程，连数据库打开失败也能被 catch 接住。
        // 不预先发 null，否则组件会在每次重新订阅时短暂覆盖当天已有内容。
        flow {
            // 只有已知真实状态才提前发出，保留冷启动等待磁盘、避免空态闪烁的约定。
            results.value[date]?.let { emit(it.data) }
            emitAll(cache.observe(date))
        }.catch { e ->
            if (e is CancellationException) throw e
            // 观察链失败不终止 results 的订阅；具体存储故障由 load 转为 cacheError。
            emit(null)
        }, results,
    ) { cached, memory ->
        memory[date] ?: DateLoadResult(date, cached?.takeIf { isValid(it, date) })
    }.distinctUntilChanged().flowOn(dispatcher)

    /**
     * 加载指定日期。force=false 优先复用有效缓存；force=true 尝试联网，但失败不抹掉同日旧值。
     * 成功响应只有通过 isValid 后才能写库。可恢复异常返回状态，协程取消仍抛给调用者。
     * @param date 本次操作的公历日期，使用设备当前时区解释；不能用请求完成时的日期替换。
     * @param force true 表示尝试强制联网；false 优先复用同日有效缓存。
     * @return DateLoadResult；保留指定日数据以及独立的网络/缓存错误，取消异常继续抛出。
     */
    suspend fun load(date: LocalDate, force: Boolean = false): DateLoadResult<T> =
        dateLocks[Math.floorMod(date.hashCode(), dateLocks.size)].withLock {
            // 先确认实际磁盘值，才能判断内存数据是否需要补写；缓存故障不等于网络故障。
            var cacheError: Exception? = null
            val persisted = try {
                cache.read(date)?.takeIf { isValid(it, date) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cacheError = e
                null
            }
            // 本仓库是业务写入入口，内存可能比磁盘新（上次写入失败）；不能让旧磁盘值覆盖它。
            val cached = results.value[date]?.data?.takeIf { isValid(it, date) } ?: persisted
            if (cached != null && !force) {
                // 命中缓存也可能有未完成的持久化步骤；修复存储不需要再联网。
                if (persisted != cached || date in pendingPersistence) {
                    cacheError = persist(date, cached)
                }
                return@withLock publish(DateLoadResult(date, cached, cacheError = cacheError))
            }

            publish(DateLoadResult(date, cached, loading = true, cacheError = cacheError))
            // 独立记录已经校验的最新值，供持久化期间取消或失败时继续展示。
            var newest = cached
            try {
                val data = fetch(date)
                // 防止不配合取消的适配器返回后继续写库；无效响应不能成为新的可展示数据。
                currentCoroutineContext().ensureActive()
                check(isValid(data, date)) { "Incomplete or mismatched date response for $date" }
                newest = data
                cacheError = persist(date, data)
                publish(DateLoadResult(date, data, cacheError = cacheError))
            } catch (e: CancellationException) {
                // 写入之后、清理期间取消时保留已取得的新值；待补写/清理标记由 persist 保留。
                publish(DateLoadResult(date, newest, cacheError = cacheError))
                throw e
            } catch (e: Exception) {
                publish(DateLoadResult(date, newest, error = e, cacheError = cacheError))
            }
        }

    /**
     * 写入与清理作为一个可重试的持久化步骤；不把磁盘故障变成网络失败。
     * @param date 数据所属公历日期，调用者已持有对应日期锁。
     * @param data 通过核心字段校验的最新数据。
     * @return null 表示写入及清理均完成；否则返回存储异常，取消异常仍抛给调用者。
     */
    private suspend fun persist(date: LocalDate, data: T): Exception? {
        pendingPersistence.add(date)
        return try {
            cache.write(date, data)
            val now = today()
            cache.prune(now.minusDays(7), now.plusDays(2))
            // 必须等两个步骤都完成后再清除标记，避免清理失败后再也没有重试机会。
            pendingPersistence.remove(date)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
    }

    /**
     * 原子合并不同日期的结果，并清理过期内存，避免并发发布互相覆盖。
     * @param result 本次加载产生的完整状态。
     * @return 传入状态本身，方便加载方法同时发布并返回结果。
     */
    private fun publish(result: DateLoadResult<T>): DateLoadResult<T> {
        val now = today()
        val before = now.minusDays(7)
        val after = now.plusDays(2)
        results.update { previous ->
            previous.filterKeys { it >= before && it <= after } + (result.date to result)
        }
        // 过期状态已不再保留，其待补写标记也不应无限增长；本次请求始终保留到后续发布。
        pendingPersistence.removeIf { it != result.date && (it < before || it > after) }
        return result
    }
}
