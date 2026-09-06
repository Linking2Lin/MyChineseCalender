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
 */
data class WidgetSyncResult(val dataReady: Boolean, val cacheSaved: Boolean, val updates: WidgetUpdateResult) {
    val succeeded: Boolean get() = dataReady && cacheSaved && updates.succeeded
}

/** 连接共享仓库与 Glance 更新的协调层；所有小组件入口复用相同的同步顺序和错误语义。 */
object WidgetDataSyncHelper {
    /**
     * 请求所有已登记的 Glance 实例更新，单实例失败后继续其他实例。
     * 枚举本身失败时返回 (total=0, failures=1) 作为整体失败标记，不能将 total=0 等同于成功。
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
     * 先触发展示本地状态，再获取指定日数据，最后再次请求展示结果。
     * force 只影响第一次读取；请求期间跨日时，对新日期采用普通缓存优先加载。
     */
    suspend fun syncAndUpdate(context: Context, force: Boolean = false): WidgetSyncResult {
        val repository = CalendarRepositories.get(context).lunar
        val date = CalendarDates.today()
        // 先请求展示，避免慢网络使昨天内容一直停留；最终返回值以末次更新请求结果为准。
        updateAllWidgets(context)
        val result = repository.load(date, force)
        // 跨午夜的旧请求不能作为今天的成功结果。只补查一次，避免反复改时钟造成无界循环；
        // 返回前再次核对当前日期，若又跨日则判失败，让后续任务重试。
        val current = if (CalendarDates.today() == date) result else repository.load(CalendarDates.today())
        val updates = updateAllWidgets(context)
        return WidgetSyncResult(current.date == CalendarDates.today() && current.data != null && current.error == null,
            current.cacheError == null, updates)
    }

    /** 尽力预取次日，利用普通 load 的缓存去重；失败状态不用于覆盖当天同步的返回结果。 */
    suspend fun prefetchTomorrow(context: Context) {
        CalendarRepositories.get(context).lunar.load(CalendarDates.today().plusDays(1))
    }
}
