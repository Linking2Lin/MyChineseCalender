package lins.applications.appwidget.data

import android.util.Log
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import lins.applications.appwidget.model.LunarDateResponse

/**
 * @Author LinXuXu
 * @Date 2026/3/27 14:51
 * 使用香港天文台API获取公历阴历对照
 */
class HkoRepository {
    private val client = KtorClient.client

    /**
     * 获取指定日期的农历信息
     * @param date 格式必须为 YYYY-MM-DD，例如 "2023-03-01"
     */
    suspend fun fetchLunarDate(date: String): LunarDateResponse? {
        return try {
            // 发起 GET 请求，添加浏览器请求头防止服务器以 403 拒绝访问
            val httpResponse = client.get("https://data.weather.gov.hk/weatherAPI/opendata/lunardate.php") {
                parameter("date", date)
                // 模拟浏览器请求头，避免服务器因识别为爬虫而返回 403 Forbidden
                header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                header("Accept", "application/json, text/plain, */*")
                header("Referer", "https://www.hko.gov.hk/")
            }

            // 先检查 HTTP 状态码，非 2xx 时不尝试反序列化，避免 NoTransformationFoundException
            if (!httpResponse.status.isSuccess()) {
                Log.e("HkoRepository", "fetchLunarDate failed: HTTP ${httpResponse.status.value} for date=$date")
                return null
            }

            httpResponse.body<LunarDateResponse>()
        } catch (e: Exception) {
            // 网络异常、解析异常都会走到这里
            Log.e("HkoRepository", "fetchLunarDate exception for date=$date", e)
            null
        }
    }
}