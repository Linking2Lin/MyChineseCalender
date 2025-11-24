package lins.applications.appwidget.woker

import android.content.Context
import android.icu.util.Calendar
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import lins.applications.appwidget.MyAppWidget
import lins.applications.appwidget.data.ChineseCalenderRepository
import lins.applications.appwidget.database.AppDataBase
import lins.applications.appwidget.model.CHNDateEnity
import lins.libs.module_base.Logger

private const val TAG = "SyncDateWorker"

class SyncDateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(
    appContext, workerParams
) {
    override suspend fun doWork(): Result {

        val repository = ChineseCalenderRepository()
        val calender = Calendar.getInstance()
        val requestResult = repository.getLunarDate(
            calender.get(Calendar.YEAR).toString(),
            (calender.get(Calendar.MONTH) + 1).toString(),
            calender.get(Calendar.DAY_OF_MONTH).toString()
            )

        val db = Room.databaseBuilder(
            context = applicationContext,
            klass = AppDataBase::class.java,
            name = "database-name"
        ).build()

        db.chnDateDao().insertDate(CHNDateEnity.covert(requestResult))

        Logger.d(TAG, "doWork: $requestResult")
        runCatching {
            GlanceAppWidgetManager(applicationContext)
                .getGlanceIds(MyAppWidget::class.java).forEach { glanceId ->
                    Logger.d(TAG, "onReceive: glanceId : $glanceId")
                    MyAppWidget().update(applicationContext, glanceId)
                }

        }.onFailure { exception ->
            Logger.d(TAG, "onReceive: exc : " + exception.stackTraceToString())
        }

        return Result.success()
    }

}