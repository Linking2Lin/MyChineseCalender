package lins.applications.appwidget

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import lins.applications.appwidget.data.ChineseCalenderRepository
import lins.applications.appwidget.data.HkoRepository
import lins.libs.module_base.network.NetworkJson
import org.junit.Assert.*
import org.junit.Test

/**
 * 使用 Ktor MockEngine 断言请求参数、HTTP 状态和解析校验，不访问线上接口。
 * 每例创建的 client 必须关闭；扩展服务契约时同时增加成功、错日、残缺和错误状态场景。
 */
class RemoteRepositoryTest {
    private val today = LocalDate.of(2026, 9, 6)
    private val valid = """{"公历日期":"2026年9月6日 星期日","农历日期":"七月廿五","新增字段":"test"}"""

    @Test fun almanacUsesRequestedDayAndAcceptsExtraFields() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("2026", request.url.parameters["year"])
            assertEquals("9", request.url.parameters["month"])
            assertEquals("6", request.url.parameters["day"])
            respond(valid)
        })
        try { assertTrue(ChineseCalenderRepository(client).getLunarDate(today).isValidFor(today)) }
        finally { client.close() }
    }

    @Test fun errorStatusCannotBeAcceptedAsCalendarEvenWithValidBody() = runTest {
        val client = HttpClient(MockEngine { respond(valid, HttpStatusCode.InternalServerError) })
        try {
            try { ChineseCalenderRepository(client).getLunarDate(today); fail("Expected rejection") }
            catch (_: IllegalStateException) { }
        } finally { client.close() }
    }

    @Test fun mismatchedOrPartialAlmanacIsRejected() = runTest {
        for (body in listOf("""{"宜":"测试"}""", valid.replace("9月6日", "9月5日"))) {
            val client = HttpClient(MockEngine { respond(body) })
            try {
                try { ChineseCalenderRepository(client).getLunarDate(today); fail("Expected rejection") }
                catch (_: IllegalStateException) { }
            } finally { client.close() }
        }
    }

    @Test fun hkoRejectsBlankFieldsAndSendsIsoDate() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("2026-09-06", request.url.parameters["date"])
            respond("""{"LunarYear":"丙午年","LunarDate":""}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(NetworkJson) } }
        try {
            try { HkoRepository(client).fetchLunarDate(today); fail("Expected rejection") }
            catch (_: IllegalStateException) { }
        } finally { client.close() }
    }
}
