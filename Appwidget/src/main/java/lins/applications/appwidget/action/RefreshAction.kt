package lins.applications.appwidget.action

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import lins.applications.appwidget.MyAppWidget
import lins.applications.appwidget.helper.WidgetDataSyncHelper
import lins.libs.module_base.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "RefreshAction"

/**
 * 点击 widget 时触发的刷新动作。
 *
 * 这个回调负责：
 * 1. 通过原子锁避免用户连续点击导致并发刷新
 * 2. 调用数据同步逻辑拉取最新农历
 * 3. 刷新对应 widget 实例
 * 4. 通过 Toast 反馈结果
 */
class RefreshAction : ActionCallback {

    companion object {
        /**
         * 进程内刷新锁。
         * 只要当前有一个刷新任务在执行，其他点击就暂时忽略。
         */
        private val isRefreshing = AtomicBoolean(false)
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // 如果当前已经有刷新任务在跑，就直接返回，避免重复请求。
        if (!isRefreshing.compareAndSet(false, true)) {
            Logger.d(TAG, "onAction: already refreshing, ignoring click")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "正在刷新中…", Toast.LENGTH_SHORT).show()
            }
            return
        }

        Logger.d(TAG, "onAction: refreshing widget $glanceId")

        try {
            // 先拉取数据并写入缓存。
            val success = WidgetDataSyncHelper.fetchAndCacheLunarDate(context) != null

            // 再刷新当前 widget，让用户立即看到变化。
            MyAppWidget().update(context, glanceId)

            withContext(Dispatchers.Main) {
                val message = if (success) "刷新成功 ✓" else "刷新失败，请检查网络"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } finally {
            isRefreshing.set(false)
        }
    }
}
