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
 * 辅助触发器：监听系统日期/时区变更，触发 Widget 数据刷新。
 * 主要更新机制为 WorkManager 定时同步。
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