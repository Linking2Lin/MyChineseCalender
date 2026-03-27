package lins.applications.appwidget.action

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.room.Room
import lins.applications.appwidget.MyAppWidget
import lins.applications.appwidget.data.HkoRepository
import lins.applications.appwidget.database.AppDataBase
import lins.applications.appwidget.model.LunarDateEntity
import lins.libs.module_base.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "RefreshAction"

/**
 * 点击 Widget 时触发的刷新动作：
 * 1. 从 HkoRepository 拉取最新农历数据
 * 2. 写入数据库缓存
 * 3. 更新 Widget UI
 * 4. 弹出 Toast 提示结果
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Logger.d(TAG, "onAction: refreshing widget $glanceId")

        val today = LocalDate.now()
        val dateString = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val success = withContext(Dispatchers.IO) {
            try {
                val response = HkoRepository().fetchLunarDate(dateString)
                if (response != null) {
                    val db = Room.databaseBuilder(
                        context, AppDataBase::class.java, "database-name"
                    ).fallbackToDestructiveMigration(dropAllTables = true).build()

                    db.lunarDateDao().insertOrReplace(
                        LunarDateEntity.fromResponse(dateString, response)
                    )
                    Logger.d(TAG, "onAction: fetched & saved: $response")
                    true
                } else {
                    Logger.d(TAG, "onAction: HKO returned null")
                    false
                }
            } catch (e: Exception) {
                Logger.d(TAG, "onAction: refresh failed: ${e.message}")
                false
            }
        }

        // 更新 Widget
        MyAppWidget().update(context, glanceId)

        // 在主线程弹 Toast
        withContext(Dispatchers.Main) {
            val message = if (success) "刷新成功 ✓" else "刷新失败，请检查网络"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
