package lins.applications.mychinesecalender

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import lins.applications.appwidget.worker.SyncDateWorker
import lins.libs.module_base.MyApplication
import java.util.concurrent.TimeUnit

/**
 * App 级 Application，继承 Module_Base 的 MyApplication 以复用日志初始化，
 * 并在此注册 WorkManager 定时任务，确保即使用户不打开 Activity 也能定时同步 Widget 数据。
 */
class App : MyApplication() {

    override fun onCreate() {
        super.onCreate()
        setupWorkManager()
    }

    private fun setupWorkManager() {
        val networkRequired = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val updateDateRequest = PeriodicWorkRequestBuilder<SyncDateWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(networkRequired)
            .build()

        // 使用 enqueueUniquePeriodicWork 避免重复注册
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "sync_lunar_date",
            ExistingPeriodicWorkPolicy.KEEP,
            updateDateRequest
        )
    }
}
