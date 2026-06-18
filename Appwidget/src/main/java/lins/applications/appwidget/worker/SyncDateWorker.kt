package lins.applications.appwidget.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import lins.applications.appwidget.helper.WidgetDataSyncHelper
import lins.libs.module_base.Logger

private const val TAG = "SyncDateWorker"

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