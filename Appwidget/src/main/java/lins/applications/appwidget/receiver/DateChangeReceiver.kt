package lins.applications.appwidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import lins.applications.appwidget.MyAppWidget
import lins.applications.appwidget.data.ChineseCalenderRepository
import lins.applications.appwidget.database.AppDataBase
import lins.applications.appwidget.model.CHNDateEnity
import java.lang.Exception
import java.util.Calendar
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private const val TAG = "DateChangeReceiver"
class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: $context $intent")
        context.let {
            intent.let {
                if (it.action == Intent.ACTION_DATE_CHANGED
                    || it.action == Intent.ACTION_TIME_CHANGED
                    || it.action == Intent.ACTION_BATTERY_CHANGED
                ) {
                    goAsync {
                        val repository = ChineseCalenderRepository()
                        val calendar = Calendar.getInstance()
                        val result = repository.getLunarDate(
                            currentYear = calendar.get(Calendar.YEAR).toString(),
                            currentMonth = (calendar.get(Calendar.MONTH) + 1).toString(),
                            currentDay = calendar.get(Calendar.DAY_OF_MONTH).toString()
                        )
                        Log.d(TAG, "onReceive: $result")
                        val db = Room.databaseBuilder(
                            context,
                            AppDataBase::class.java, "database-name"
                        ).build()

                        // calendar.timeInMillis

                        //            if (db.chnDateDao().getById(result.year.hashCode()) != null){
                        //                db.chnDateDao().deleteById(result.year.hashCode())
                        //            }

                        db.chnDateDao().insertDate(CHNDateEnity.covert(result))

                        Log.d(TAG, "onReceive: last " + db.chnDateDao().getAll().last())

                        runCatching {


                            GlanceAppWidgetManager(context)
                                .getGlanceIds(MyAppWidget::class.java).forEach { glanceId ->
                                    Log.d(TAG, "onReceive: glanceId : $glanceId")
                                    MyAppWidget().update(context, glanceId)
                                }

                        }.onFailure { exception ->
                            Log.d(TAG, "onReceive: exc : " + exception.stackTraceToString())
                        }
                    }
                }
            }


        }


    }

}

fun BroadcastReceiver.goAsync(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.(BroadcastReceiver.PendingResult) -> Unit
) {
    val pendingResult = goAsync()
    @OptIn(DelicateCoroutinesApi::class) // Must run globally; there's no teardown callback.
    GlobalScope.launch(context) {
        try {
            block(pendingResult)
        }catch (e: Exception){
            Log.d(TAG, "goAsync: e :" + e.stackTraceToString())
        }
        finally {
            pendingResult.finish()
        }
    }
}