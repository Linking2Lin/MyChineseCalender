package lins.applications.appwidget.helper

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import lins.applications.appwidget.MyAppWidgetReceiver
import lins.applications.appwidget.receiver.DateChangeReceiver
import lins.applications.appwidget.worker.SyncDateWorker
import lins.libs.module_base.time.CalendarDates

/**
 * 统一管理有小组件时才需要的后台任务，入口包括 Application、系统广播及组件生命周期。
 *
 * 30 分钟周期任务负责兜底，午夜闹钟负责尽早切日，即时任务处理用户返回应用/系统事件。
 * 三者都受系统后台限制，setAndAllowWhileIdle 是非精确闹钟，不承诺零点准时显示。
 * 所有方法只调度或取消任务，不执行网络、数据库查询，适合广播的短执行窗口。
 */
object WidgetScheduler {
    const val ACTION_ROLLOVER = "lins.applications.appwidget.ROLLOVER"
    // 唯一任务名是已有安装的持久化身份；随意改名会留下旧任务与新任务同时运行。
    private const val PERIODIC = "sync_lunar_date"
    private const val IMMEDIATE = "refresh_lunar_date"

    /** 用实际桌面实例判断需求，避免仅因用户打开主页就长期运行农历同步。 */
    fun hasWidgets(context: Context): Boolean = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, MyAppWidgetReceiver::class.java)).isNotEmpty()

    /** 可重复调用；UPDATE 更新既有周期任务配置，删除最后一个实例后转为取消。 */
    fun ensureScheduled(context: Context) {
        if (!hasWidgets(context)) {
            cancel(context)
            return
        }
        // 不设置网络约束：离线跨天也要请求展示新日期的缓存/空态，而不是一直显示昨天。
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncDateWorker>(30, TimeUnit.MINUTES).build(),
        )
        scheduleMidnight(context)
    }

    /**
     * 对密集触发的广播/订阅合并排队：KEEP 保留尚未完成的即时任务。
     * 周期与即时任务名字不同，仍可能同时触发；仓库层的 Mutex 负责串行化数据加载。
     */
    fun requestSync(context: Context) {
        if (hasWidgets(context)) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SyncDateWorker>().build(),
            )
        }
    }

    /** 重算下一次本地午夜；闹钟触发后及系统时间/时区改变后都要再次安排。 */
    fun scheduleMidnight(context: Context) {
        if (hasWidgets(context)) {
            // 使用非精确闹钟，不需要“闹钟和提醒”特殊权限；Doze 下仍可能推迟执行。
            context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, CalendarDates.nextMidnightMillis(), rolloverIntent(context),
            )
        }
    }

    /** 停止两种唯一任务与午夜闹钟。这里只处理本小组件的任务，不取消应用其他 Work。 */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(IMMEDIATE)
        }
        context.getSystemService(AlarmManager::class.java).cancel(rolloverIntent(context))
    }

    // 固定组件、action 和 requestCode，确保重设/取消指向同一个 PendingIntent。
    private fun rolloverIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, DateChangeReceiver::class.java).setAction(ACTION_ROLLOVER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
