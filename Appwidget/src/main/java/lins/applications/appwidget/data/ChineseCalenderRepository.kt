package lins.applications.appwidget.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.time.LocalDate
import lins.libs.module_base.model.CHNDate
import lins.libs.module_base.network.KtorClient
import lins.libs.module_base.network.NetworkJson

/**
 * 通用黄历接口的纯网络适配器，不读写缓存、不吞掉失败，也不生成空模型伪装成成功。
 * 错误统一由 DailyRepository 转成可展示状态；client 可注入 MockEngine 进行离线契约测试。
 */
class ChineseCalenderRepository(private val client: HttpClient = KtorClient.client) {
    /** 按请求日拆分 year/month/day 参数，并校验返回的公历与农历；失败或取消直接抛给上层。 */
    suspend fun getLunarDate(date: LocalDate): CHNDate {
        val response = client.get("https://api.tiax.cn/almanac/") {
            parameter("year", date.year)
            parameter("month", date.monthValue)
            parameter("day", date.dayOfMonth)
        }
        // 错误页即使恰好包含可解析 JSON，也不能作为成功数据写入缓存。
        check(response.status.isSuccess()) { "Almanac HTTP ${response.status.value}" }
        // bodyAsText 不经过 ContentNegotiation，必须显式使用与全局客户端相同的 JSON 配置。
        return NetworkJson.decodeFromString<CHNDate>(response.bodyAsText()).also {
            check(it.isValidFor(date)) { "Incomplete or mismatched almanac for $date" }
        }
    }
}
