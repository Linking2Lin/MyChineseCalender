package lins.applications.appwidget.helper

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CancellationException
import lins.applications.appwidget.MyAppWidget
import lins.applications.appwidget.data.CalendarRepositories
import lins.libs.module_base.Logger
import lins.libs.module_base.time.CalendarDates

/**
 * 将“今日数据有效”“缓存操作未报错”“组件更新请求未报错”分开，供 Toast 和 Worker 决策。
 * succeeded 只表示应用侧整条流程成功，不表示桌面宿主已完成绘制。
 * @param dataReady 本次结果确属今天、有有效数据且没有网络/校验失败时为 true。
 * @param cacheSaved 缓存操作没有返回故障时为 true，需要与 dataReady 一起判断。
 * @param updates 最后一轮组件更新请求的结果统计。
 */
data class WidgetSyncResult(val dataReady: Boolean, val cacheSaved: Boolean, val updates: WidgetUpdateResult) {
    /** 数据、缓存操作与更新请求三项同时满足时，Worker 才进入预取并返回成功。 */
    val succeeded: Boolean get() = dataReady && cacheSaved && updates.succeeded
}

/** 连接共享仓库与 Glance 更新的协调层；所有小组件入口复用相同的同步顺序和错误语义。 */
object WidgetDataSyncHelper {
    /**
     * 请求所有已登记的 Glance 实例更新，单实例失败后继续其他实例。
     * 枚举本身失败时返回 (total=0, failures=1) 作为整体失败标记，不能将 total=0 等同于成功。
     * @param context 调用入口的 Context；长生命周期依赖使用 applicationContext，避免持有页面。
     * @return 组件更新请求统计；枚举失败记为一次整体失败，不代表宿主已绘制完成。
     */
    suspend fun updateAllWidgets(context: Context): WidgetUpdateResult = try {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(MyAppWidget::class.java)
        updateWidgetsIndependently(ids) { id ->
            try {
                MyAppWidget().update(context, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("WidgetSync", "Unable to request update for $id", e)
                throw e
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.e("WidgetSync", "Unable to enumerate widgets", e)
        WidgetUpdateResult(0, 1)
    }

    /**
     * 先触发展示本地状态，再加载当天数据，最后再次请求展示结果。
     * 点击与后台均复用当天有效缓存；同一日期成功获取后不重复联网，无缓存时仍可重试。
     * 请求期间跨日时，同样先检查新日期的缓存。本入口不提供强制联网选项。
     * @param context 调用入口的 Context；长生命周期依赖使用 applicationContext，避免持有页面。
     * @return WidgetSyncResult；分别说明今日数据、缓存操作及最终更新请求是否成功。
     */
    suspend fun syncAndUpdate(context: Context): WidgetSyncResult {
        val repository = CalendarRepositories.get(context).lunar
        val date = CalendarDates.today()
        // 先请求展示，避免慢网络使昨天内容一直停留；最终返回值以末次更新请求结果为准。
        updateAllWidgets(context)
        val result = repository.load(date)
        // 跨午夜的旧请求不能作为今天的成功结果。只补查一次，避免反复改时钟造成无界循环；
        // 返回前再次核对当前日期，若又跨日则判失败，让后续任务重试。
        val current = if (CalendarDates.today() == date) result else repository.load(CalendarDates.today())
        val updates = updateAllWidgets(context)
        return WidgetSyncResult(current.date == CalendarDates.today() && current.data != null && current.error == null,
            current.cacheError == null, updates)
    }

    /**
     * 尽力预取次日，利用普通 load 的缓存去重；失败状态不用于覆盖当天同步的返回结果。
     * @param context 调用入口的 Context；长生命周期依赖使用 applicationContext，避免持有页面。
     * @return Unit（挂起）；尝试预取明日结果，不用其业务失败覆盖今日同步结果。
     */
    suspend fun prefetchTomorrow(context: Context) {
        CalendarRepositories.get(context).lunar.load(CalendarDates.today().plusDays(1))
    }
}
