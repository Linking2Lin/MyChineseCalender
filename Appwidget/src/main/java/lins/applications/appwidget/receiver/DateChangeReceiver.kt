package lins.applications.appwidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import lins.applications.appwidget.helper.WidgetScheduler

/**
 * 日期、时间、时区和恢复事件的统一入口，只重新安排任务，不在广播生命周期里等待网络。
 * DATE_CHANGED 由 App 动态注册；其余系统事件在主模块 Manifest 注册，ROLLOVER 为显式闹钟。
 * 新增触发事件时需同时核对这里的白名单和对应注册入口。
 */
class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED, WidgetScheduler.ACTION_ROLLOVER)) {
            WidgetScheduler.ensureScheduled(context)
            WidgetScheduler.requestSync(context)
        }
    }
}
