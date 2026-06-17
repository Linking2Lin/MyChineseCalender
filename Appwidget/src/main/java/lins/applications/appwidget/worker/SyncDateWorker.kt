package lins.applications.appwidget.worker

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import lins.applications.appwidget.MyAppWidget
import lins.applications.appwidget.data.HkoRepository
import lins.libs.module_base.database.AppDataBase
import lins.libs.module_base.model.LunarDateEntity
import lins.libs.module_base.Logger
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "SyncDateWorker"

class SyncDateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(
    appContext, workerParams
) {
    override suspend fun doWork(): Result {
        Logger.d(TAG, "doWork: start syncing lunar date from HKO")

        val today = LocalDate.now()
        val dateString = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        // 1. 从 HkoRepository 获取农历数据
        val response = try {
            HkoRepository().fetchLunarDate(dateString)
        } catch (e: Exception) {
            Logger.d(TAG, "doWork: fetch failed: ${e.message}")
            null
        }

        if (response == null) {
            Logger.d(TAG, "doWork: HKO returned null, will retry")
            return Result.retry()
        }

        // 2. 存入数据库
        try {
            val db = AppDataBase.getInstance(applicationContext)

            val entity = LunarDateEntity.fromResponse(dateString, response)
            db.lunarDateDao().insertOrReplace(entity)
            db.lunarDateDao().cleanup()
            Logger.d(TAG, "doWork: saved to DB: $entity")
        } catch (e: Exception) {
            Logger.d(TAG, "doWork: DB write failed: ${e.message}")
        }

        // 3. 触发 widget 更新
        runCatching {
            GlanceAppWidgetManager(applicationContext)
                .getGlanceIds(MyAppWidget::class.java).forEach { glanceId ->
                    Logger.d(TAG, "doWork: updating glanceId: $glanceId")
                    MyAppWidget().update(applicationContext, glanceId)
                }
        }.onFailure { exception ->
            Logger.d(TAG, "doWork: widget update failed: " + exception.stackTraceToString())
        }

        return Result.success()
    }
}