package lins.applications.appwidget.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import lins.applications.appwidget.helper.WidgetDataSyncHelper
import lins.applications.appwidget.helper.WidgetScheduler

/**
 * WorkManager 持久任务入口，承接周期兜底和即时刷新。构造器由框架反射调用，
 * 类名还会被 WorkManager 保存，发布压缩规则中须保留它以兼容已有排队任务。
 * @param context 框架提供的应用 Context，不持有 Activity。
 * @param parameters 框架提供的任务参数，包括任务身份、运行次数等元数据。
 */
class SyncDateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    /**
     * 检查桌面实例、重排午夜任务、同步今天，并在成功后尽力预取明天。
     * @return WorkManager Result；无组件/同步成功时成功，可恢复失败时请求退避重试。
     */
    override suspend fun doWork(): Result {
        // 排队后用户可能已经删除所有实例，执行前再次检查，避免无意义的联网。
        if (!WidgetScheduler.hasWidgets(applicationContext)) return Result.success()
        WidgetScheduler.scheduleMidnight(applicationContext)
        val result = WidgetDataSyncHelper.syncAndUpdate(applicationContext)
        // 预取失败不会把今天改为失败；取消仍由协程传播给 WorkManager。
        if (result.succeeded) WidgetDataSyncHelper.prefetchTomorrow(applicationContext)
        // 任一今日数据/缓存/最终展示请求失败均交回 WorkManager 按退避策略重试。
        return if (result.succeeded) Result.success() else Result.retry()
    }
}
