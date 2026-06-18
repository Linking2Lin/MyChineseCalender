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
 * 点击 Widget 时触发的刷新动作：
 * 1. 防连点：正在请求时忽略后续点击
 * 2. 使用 WidgetDataSyncHelper 拉取并缓存数据
 * 3. 更新当前 Widget UI
 * 4. 弹出 Toast 提示结果
 */
class RefreshAction : ActionCallback {

    companion object {
        /** 全局锁：正在刷新时为 true，防止重复点击 */
        private val isRefreshing = AtomicBoolean(false)
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // 防连点：如果已经在刷新中，直接返回
        if (!isRefreshing.compareAndSet(false, true)) {
            Logger.d(TAG, "onAction: already refreshing, ignoring click")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "正在刷新中…", Toast.LENGTH_SHORT).show()
            }
            return
        }

        Logger.d(TAG, "onAction: refreshing widget $glanceId")

        try {
            val success = WidgetDataSyncHelper.fetchAndCacheLunarDate(context) != null

            // 更新当前 Widget
            MyAppWidget().update(context, glanceId)

            // 弹 Toast
            withContext(Dispatchers.Main) {
                val message = if (success) "刷新成功 ✓" else "刷新失败，请检查网络"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } finally {
            isRefreshing.set(false)
        }
    }
}
