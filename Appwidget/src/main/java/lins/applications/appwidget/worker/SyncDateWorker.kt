package lins.applications.appwidget.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import lins.applications.appwidget.helper.WidgetDataSyncHelper
import lins.libs.module_base.Logger

private const val TAG = "SyncDateWorker"

/**
 * 使用 WorkManager 执行后台同步任务。
 *
 * 这个 Worker 不直接处理 UI，它只负责在后台触发一次完整同步，
 * 包括拉取数据、写入缓存和刷新 widget。
 */
class SyncDateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Logger.d(TAG, "doWork: start syncing lunar date")

        val success = WidgetDataSyncHelper.syncAndUpdate(applicationContext)

        return if (success) {
            Logger.d(TAG, "doWork: sync completed successfully")
            Result.success()
        } else {
            Logger.w(TAG, "doWork: sync failed, will retry")
            Result.retry()
        }
    }
}