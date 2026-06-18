package lins.applications.appwidget.helper

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import lins.applications.appwidget.MyAppWidget
import lins.applications.appwidget.data.HkoRepository
import lins.libs.module_base.Constants
import lins.libs.module_base.Logger
import lins.libs.module_base.database.AppDataBase
import lins.libs.module_base.model.LunarDateEntity
import lins.libs.module_base.model.LunarDateResponse
import java.time.LocalDate

object WidgetDataSyncHelper {
    private const val TAG = "WidgetDataSyncHelper"

    /**
     * 从 HKO API 拉取今天的农历数据并存入数据库缓存。
     * @return 拉取到的农历数据，失败时返回 null
     */
    suspend fun fetchAndCacheLunarDate(context: Context): LunarDateResponse? {
        val today = LocalDate.now()
        val dateString = today.format(Constants.DATE_FORMATTER)

        return try {
            val response = HkoRepository.fetchLunarDate(dateString)
            if (response != null) {
                val db = AppDataBase.getInstance(context)
                db.lunarDateDao().insertOrReplace(
                    LunarDateEntity.fromResponse(dateString, response)
                )
                db.lunarDateDao().cleanup(7)
                Logger.d(TAG, "Fetched & cached lunar date for $dateString")
            }
            response
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to fetch lunar date", e)
            null
        }
    }

    /**
     * 更新所有已添加的 Widget 实例。
     */
    suspend fun updateAllWidgets(context: Context) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val widget = MyAppWidget()
            manager.getGlanceIds(MyAppWidget::class.java).forEach { glanceId ->
                Logger.d(TAG, "Updating widget: $glanceId")
                widget.update(context, glanceId)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update widgets", e)
        }
    }

    /**
     * 完整的同步流程：拉取数据 → 存库 → 更新 Widget
     * @return 是否成功
     */
    suspend fun syncAndUpdate(context: Context): Boolean {
        val response = fetchAndCacheLunarDate(context)
        updateAllWidgets(context)
        return response != null
    }
}
