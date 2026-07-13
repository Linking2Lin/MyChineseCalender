package lins.applications.appwidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import lins.applications.appwidget.helper.WidgetDataSyncHelper
import lins.libs.module_base.Logger

private const val TAG = "DateChangeReceiver"

/**
 * 系统广播接收器：监听日期切换和时区变化。
 *
 * 当系统日期变化或用户切换时区时，widget 也应该尽快同步到新的“今天”。
 * 这里使用 goAsync() 是因为同步操作会涉及网络/数据库，不能在主线程里直接做。
 */
class DateChangeReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Logger.d(TAG, "onReceive: ${intent.action}")

        if (intent.action == Intent.ACTION_DATE_CHANGED
            || intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pendingResult = goAsync()
            scope.launch {
                try {
                    WidgetDataSyncHelper.syncAndUpdate(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}