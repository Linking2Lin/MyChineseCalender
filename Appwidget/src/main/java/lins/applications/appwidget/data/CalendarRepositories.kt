package lins.applications.appwidget.data

import android.content.Context
import java.time.LocalDate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import lins.libs.module_base.data.DailyCache
import lins.libs.module_base.data.DailyRepository
import lins.libs.module_base.database.AppDataBase
import lins.libs.module_base.model.CHNDate
import lins.libs.module_base.model.CHNDateEntity
import lins.libs.module_base.model.LunarDateEntity
import lins.libs.module_base.model.LunarDateResponse

/**
 * 应用的数据装配入口：把通用 DailyRepository、Room DAO 和具体 HTTP 适配器连接起来。
 *
 * 主页使用 almanac，小组件使用 lunar；它们采用相同缓存流程，但数据来源和互斥锁独立。
 * 同一类数据的所有入口应共享这里的仓库，才能共享进行中的加载顺序及未落盘结果。
 * 仓库只持有 Application Context，生命周期与应用进程一致。
 * @param context 装配入口 Context，内部只保存 applicationContext。
 */
class CalendarRepositories private constructor(context: Context) {
    private val appContext = context.applicationContext
    // 延迟到缓存操作时再访问数据库，使数据库初始化异常落在 DailyRepository 的异常边界内。
    /**
     * 进程内共享 AppDataBase；真正打开数据库可延迟到首次查询。
     * @return 进程内共享 AppDataBase；真正打开数据库可延迟到首次查询。
     */
    private fun db() = AppDataBase.getInstance(appContext)

    /** 完整黄历：公历字段必须与请求日匹配，农历非空；宜忌等补充资料允许缺失。 */
    val almanac = DailyRepository(
        cache = object : DailyCache<CHNDate> {
            /**
             * 精确查询完整黄历并转换为接口展示模型。
             * @param date 本次操作的公历日期，使用设备当前时区解释；不能用请求完成时的日期替换。
             * @return 指定日期的缓存数据；没有记录时返回 null，存储异常继续抛出。
             */
            override suspend fun read(date: LocalDate) = db().chnDateDao().getByDate(date.toString())?.toModel()
            // 使用 flow 延迟创建 DAO 查询，避免在订阅建立之前抛出数据库故障。
            /**
             * 订阅指定日期的黄历记录，将 Room 更新转成展示模型。
             * @param date 本次操作的公历日期，使用设备当前时区解释；不能用请求完成时的日期替换。
             * @return 冷 Flow；发出指定日期的展示模型或 null，加载与错误标记由外层仓库补充。
             */
            override fun observe(date: LocalDate) = flow {
                emitAll(db().chnDateDao().observeByDate(date.toString()).map { it?.toModel() })
            }
            /**
             * 校验黄历日期并转换为实体，按日期主键替换写入。
             * @param date 本次操作的公历日期，使用设备当前时区解释；不能用请求完成时的日期替换。
             * @param data 本次操作使用的业务数据；写入路径要求已通过对应的有效性校验。
             * @return Unit；完成指定日期的数据替换写入，异常由上层处理。
             */
            override suspend fun write(date: LocalDate, data: CHNDate) =
                db().chnDateDao().insertOrReplace(CHNDateEntity.fromModel(date, data))
            /**
             * 完成区间外缓存清理，异常由仓库记录并安排后续重试。
             * @param before 保留区间的起始日期，包含当天；早于此值的记录将被清理。
             * @param after 保留区间的结束日期，包含当天；晚于此值的记录将被清理。
             * @return Unit；完成区间外缓存清理，异常由仓库记录并安排后续重试。
             */
            override suspend fun prune(before: LocalDate, after: LocalDate) =
                db().chnDateDao().cleanup(before.toString(), after.toString())
        },
        fetch = { ChineseCalenderRepository().getLunarDate(it) },
        isValid = { data, date -> data.isValidFor(date) },
    )

    /**
     * HKO 数据只返回农历年/月日，没有可对照的公历字段；日期身份来自本次请求参数。
     * 不要把它与完整黄历的校验规则混用，也不能声称已经验证服务端返回的公历日期。
     */
    val lunar = DailyRepository(
        cache = object : DailyCache<LunarDateResponse> {
            /**
             * 精确查询 HKO 缓存并还原农历模型。
             * @param date 本次操作的公历日期，使用设备当前时区解释；不能用请求完成时的日期替换。
             * @return 指定日期的缓存数据；没有记录时返回 null，存储异常继续抛出。
             */
            override suspend fun read(date: LocalDate) = db().lunarDateDao().getByDate(date.toString())?.toResponse()
            /**
             * 订阅指定日期的 HKO 缓存，不在观察函数中联网。
             * @param date 本次操作的公历日期，使用设备当前时区解释；不能用请求完成时的日期替换。
             * @return 冷 Flow；发出指定日期的展示模型或 null，加载与错误标记由外层仓库补充。
             */
            override fun observe(date: LocalDate) = flow {
                emitAll(db().lunarDateDao().observeByDate(date.toString()).map { it?.toResponse() })
            }
            /**
             * 检查 HKO 核心字段后生成按日实体并替换写入。
             * @param date 本次操作的公历日期，使用设备当前时区解释；不能用请求完成时的日期替换。
             * @param data 本次操作使用的业务数据；写入路径要求已通过对应的有效性校验。
             * @return Unit；完成指定日期的数据替换写入，异常由上层处理。
             */
            override suspend fun write(date: LocalDate, data: LunarDateResponse) =
                db().lunarDateDao().insertOrReplace(requireNotNull(LunarDateEntity.fromResponse(date.toString(), data)))
            /**
             * 完成区间外缓存清理，异常由仓库记录并安排后续重试。
             * @param before 保留区间的起始日期，包含当天；早于此值的记录将被清理。
             * @param after 保留区间的结束日期，包含当天；晚于此值的记录将被清理。
             * @return Unit；完成区间外缓存清理，异常由仓库记录并安排后续重试。
             */
            override suspend fun prune(before: LocalDate, after: LocalDate) =
                db().lunarDateDao().cleanup(before.toString(), after.toString())
        },
        fetch = { HkoRepository().fetchLunarDate(it) },
        isValid = { data, _ -> data.lunarYear.isNotBlank() && data.lunarDate.isNotBlank() },
    )

    companion object {
        @Volatile private var instance: CalendarRepositories? = null
        /**
         * 双重检查避免页面、Worker 和 Glance 首次并发进入时创建多套仓库及互斥锁。
         * @param context 调用入口的 Context；长生命周期依赖使用 applicationContext，避免持有页面。
         * @return 进程内共享的 CalendarRepositories 装配对象。
         */
        fun get(context: Context): CalendarRepositories = instance ?: synchronized(this) {
            instance ?: CalendarRepositories(context).also { instance = it }
        }
    }
}
