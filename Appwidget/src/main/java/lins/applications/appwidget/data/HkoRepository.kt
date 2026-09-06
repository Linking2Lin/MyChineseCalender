package lins.applications.appwidget.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import java.time.LocalDate
import lins.libs.module_base.model.LunarDateResponse
import lins.libs.module_base.network.KtorClient

/**
 * 香港天文台农历接口适配器，仅负责一次请求与字段完整性校验。
 * 请求日期由调用方传入，便于切日和预取；不要在此重新读取“今天”，否则会改变请求身份。
 */
class HkoRepository(private val client: HttpClient = KtorClient.client) {
    /** 返回非空农历年与日期；HTTP、解析和校验异常均由上层仓库处理。 */
    suspend fun fetchLunarDate(date: LocalDate): LunarDateResponse {
        val response = client.get("https://data.weather.gov.hk/weatherAPI/opendata/lunardate.php") {
            parameter("date", date.toString())
            // 保留既有接口请求头；它们不代表应用真实运行系统，也不参与日期计算。
            header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            header("Accept", "application/json, text/plain, */*")
            header("Referer", "https://www.hko.gov.hk/")
        }
        check(response.status.isSuccess()) { "HKO HTTP ${response.status.value}" }
        return response.body<LunarDateResponse>().also {
            check(it.lunarYear.isNotBlank() && it.lunarDate.isNotBlank()) { "Empty HKO response for $date" }
        }
    }
}
