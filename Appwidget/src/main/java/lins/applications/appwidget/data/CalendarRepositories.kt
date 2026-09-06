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
 */
class CalendarRepositories private constructor(context: Context) {
    private val appContext = context.applicationContext
    // 延迟到缓存操作时再访问数据库，使数据库初始化异常落在 DailyRepository 的异常边界内。
    private fun db() = AppDataBase.getInstance(appContext)

    /** 完整黄历：公历字段必须与请求日匹配，农历非空；宜忌等补充资料允许缺失。 */
    val almanac = DailyRepository(
        cache = object : DailyCache<CHNDate> {
            override suspend fun read(date: LocalDate) = db().chnDateDao().getByDate(date.toString())?.toModel()
            // 使用 flow 延迟创建 DAO 查询，避免在订阅建立之前抛出数据库故障。
            override fun observe(date: LocalDate) = flow {
                emitAll(db().chnDateDao().observeByDate(date.toString()).map { it?.toModel() })
            }
            override suspend fun write(date: LocalDate, data: CHNDate) =
                db().chnDateDao().insertOrReplace(CHNDateEntity.fromModel(date, data))
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
            override suspend fun read(date: LocalDate) = db().lunarDateDao().getByDate(date.toString())?.toResponse()
            override fun observe(date: LocalDate) = flow {
                emitAll(db().lunarDateDao().observeByDate(date.toString()).map { it?.toResponse() })
            }
            override suspend fun write(date: LocalDate, data: LunarDateResponse) =
                db().lunarDateDao().insertOrReplace(requireNotNull(LunarDateEntity.fromResponse(date.toString(), data)))
            override suspend fun prune(before: LocalDate, after: LocalDate) =
                db().lunarDateDao().cleanup(before.toString(), after.toString())
        },
        fetch = { HkoRepository().fetchLunarDate(it) },
        isValid = { data, _ -> data.lunarYear.isNotBlank() && data.lunarDate.isNotBlank() },
    )

    companion object {
        @Volatile private var instance: CalendarRepositories? = null
        /** 双重检查避免页面、Worker 和 Glance 首次并发进入时创建多套仓库及互斥锁。 */
        fun get(context: Context): CalendarRepositories = instance ?: synchronized(this) {
            instance ?: CalendarRepositories(context).also { instance = it }
        }
    }
}
