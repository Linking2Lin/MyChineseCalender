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

/**
 * Widget 数据同步工具。
 *
 * 负责把“网络数据 -> 本地缓存 -> widget 刷新”这条链路收口在一个地方，
 * 方便 Worker、广播接收器、点击刷新等多个入口复用同一套逻辑。
 */
object WidgetDataSyncHelper {
    private const val TAG = "WidgetDataSyncHelper"

    /**
     * 从 HKO API 拉取今天的农历数据并写入本地数据库缓存。
     *
     * 返回值说明：
     * - 成功：返回接口解析出的 `LunarDateResponse`
     * - 失败：返回 null
     *
     * 这个方法只负责“数据本身”，不负责 widget 刷新，这样职责更清晰。
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
     * 刷新当前已添加的所有 widget 实例。
     *
     * 使用 `GlanceAppWidgetManager` 枚举当前所有 widget，然后逐个 update，
     * 可以避免只刷新单个 glanceId 导致多副本不同步的问题。
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
     * 完整同步流程：
     * 1. 拉取数据
     * 2. 写入缓存
     * 3. 刷新 widget
     *
     * @return 是否至少成功拿到了网络数据。
     */
    suspend fun syncAndUpdate(context: Context): Boolean {
        val response = fetchAndCacheLunarDate(context)
        updateAllWidgets(context)
        return response != null
    }
}

