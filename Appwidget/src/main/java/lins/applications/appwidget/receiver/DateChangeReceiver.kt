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
    /**
     * 只安排持久任务，不等待网络请求完成。
     * @param context 调用入口的 Context；长生命周期依赖使用 applicationContext，避免持有页面。
     * @param intent 收到的广播；仅白名单中的日期、时间、恢复和午夜事件会安排任务。
     * @return Unit；只安排持久任务，不等待网络请求完成。
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED, WidgetScheduler.ACTION_ROLLOVER)) {
            WidgetScheduler.ensureScheduled(context)
            WidgetScheduler.requestSync(context)
        }
    }
}
