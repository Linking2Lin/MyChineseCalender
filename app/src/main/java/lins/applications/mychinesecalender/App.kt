package lins.applications.mychinesecalender

import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import lins.applications.appwidget.helper.WidgetScheduler
import lins.applications.appwidget.receiver.DateChangeReceiver
import lins.libs.module_base.MyApplication

/**
 * 进程入口：先完成基础日志初始化，再接入日期广播及已有组件的后台任务恢复。
 * 系统可能仅为 Worker/小组件启动进程而不创建 Activity，因此调度恢复不能只写在页面里。
 */
class App : MyApplication() {
    /**
     * 初始化业务日志，注册进程内日期广播，并恢复已有小组件的后台调度。
     * @return Unit；完成注册与任务安排，实际数据同步由后续 Worker 执行。
     */
    override fun onCreate() {
        super.onCreate()
        // DATE_CHANGED 使用应用生命周期的动态注册；进程不存活时依靠持久任务和闹钟唤醒。
        ContextCompat.registerReceiver(this, DateChangeReceiver(), IntentFilter(Intent.ACTION_DATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        // 重复调用由唯一任务去重；没有小组件则取消历史任务，避免仅浏览主页也持续后台联网。
        WidgetScheduler.ensureScheduled(this)
    }
}
