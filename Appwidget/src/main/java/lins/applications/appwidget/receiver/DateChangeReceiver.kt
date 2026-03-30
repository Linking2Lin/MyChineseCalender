package lins.applications.appwidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import lins.applications.appwidget.MyAppWidget
import lins.applications.appwidget.data.HkoRepository
import lins.applications.appwidget.database.AppDataBase
import lins.applications.appwidget.model.LunarDateEntity
import lins.libs.module_base.Logger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private const val TAG = "DateChangeReceiver"

/**
 * 辅助触发器：监听系统日期/时区变更，触发 Widget 数据刷新。
 * 主要更新机制为 appwidget-provider 的 updatePeriodMillis（每 30 分钟）。
 */
class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logger.d(TAG, "onReceive: ${intent.action}")

        if (intent.action == Intent.ACTION_DATE_CHANGED
            || intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            goAsync {
                val today = LocalDate.now()
                val dateString = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                // 1. 从 HkoRepository 获取农历数据
                val response = try {
                    HkoRepository().fetchLunarDate(dateString)
                } catch (e: Exception) {
                    Logger.d(TAG, "onReceive: fetch failed: ${e.message}")
                    null
                }

                // 2. 存入数据库
                if (response != null) {
                    try {
                        val db = Room.databaseBuilder(
                            context,
                            AppDataBase::class.java,
                            "database-name"
                        ).fallbackToDestructiveMigration(dropAllTables = true).build()

                        val entity = LunarDateEntity.fromResponse(dateString, response)
                        db.lunarDateDao().insertOrReplace(entity)
                        db.lunarDateDao().cleanup()
                        Logger.d(TAG, "onReceive: saved to DB: $entity")
                    } catch (e: Exception) {
                        Logger.d(TAG, "onReceive: DB write failed: ${e.message}")
                    }
                }

                // 3. 触发 widget 更新
                runCatching {
                    GlanceAppWidgetManager(context)
                        .getGlanceIds(MyAppWidget::class.java).forEach { glanceId ->
                            Logger.d(TAG, "onReceive: updating glanceId: $glanceId")
                            MyAppWidget().update(context, glanceId)
                        }
                }.onFailure { exception ->
                    Logger.d(TAG, "onReceive: update failed: " + exception.stackTraceToString())
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
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(context) {
        try {
            block(pendingResult)
        } catch (e: Exception) {
            Logger.d(TAG, "goAsync: e: " + e.stackTraceToString())
        } finally {
            pendingResult.finish()
        }
    }
}