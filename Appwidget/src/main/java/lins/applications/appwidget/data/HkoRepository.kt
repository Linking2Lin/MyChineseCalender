package lins.applications.appwidget.data

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import lins.libs.module_base.Logger
import lins.libs.module_base.model.LunarDateResponse
import lins.libs.module_base.network.KtorClient

/**
 * @Author LinXuXu
 * @Date 2026/3/27 14:51
 * 使用香港天文台API获取公历阴历对照
 */
object HkoRepository {
    private const val TAG = "HkoRepository"
    private val client = KtorClient.client

    /**
     * 获取指定日期的农历信息
     * @param date 格式必须为 YYYY-MM-DD，例如 "2023-03-01"
     */
    suspend fun fetchLunarDate(date: String): LunarDateResponse? {
        return try {
            val httpResponse = client.get("https://data.weather.gov.hk/weatherAPI/opendata/lunardate.php") {
                parameter("date", date)
                header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                header("Accept", "application/json, text/plain, */*")
                header("Referer", "https://www.hko.gov.hk/")
            }

            if (!httpResponse.status.isSuccess()) {
                Logger.e(TAG, "fetchLunarDate failed: HTTP ${httpResponse.status.value} for date=$date")
                return null
            }

            httpResponse.body<LunarDateResponse>()
        } catch (e: Exception) {
            Logger.e(TAG, "fetchLunarDate exception for date=$date", e)
            null
        }
    }
}