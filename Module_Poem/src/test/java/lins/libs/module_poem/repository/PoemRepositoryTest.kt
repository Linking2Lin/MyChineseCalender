package lins.libs.module_poem.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import lins.libs.module_base.network.NetworkJson
import org.junit.Assert.*
import org.junit.Test

/** 离线验证真实仓库的 HTTP 请求和存储故障分支，不访问今日诗词服务。 */
class PoemRepositoryTest {
    /**
     * 模拟 DataStore 中的单个 Token 键，读写故障和取消可以独立触发。
     * @param token 初始持久值；null 表示尚无 Token。
     */
    private class Store(var token: String? = null) : PoemTokenStore {
        var failRead = false
        var failWrite = false
        var cancelRead = false
        /**
         * 按开关模拟 Token 读取成功、磁盘失败或协程取消。
         * @return String?；返回当前测试 Token，故障开关启用时抛出对应异常。
         */
        override suspend fun read(): String? {
            if (cancelRead) throw CancellationException("cancel read")
            if (failRead) throw IOException("disk read failed")
            return token
        }
        /**
         * 模拟 Token 替换或删除，失败时保留原测试值。
         * @param token 目标 Token；null 表示删除。
         * @return Unit；更新内存存储，failWrite 启用时抛出 IOException。
         */
        override suspend fun write(token: String?) {
            if (failWrite) throw IOException("disk write failed")
            this.token = token
        }
    }

    /**
     * 创建使用生产 JSON 配置的模拟客户端，供用例控制每次 HTTP 响应。
     * @param handler MockEngine 请求处理回调，接收请求并返回测试响应，可校验参数或制造故障。
     * @return HttpClient；仅使用模拟引擎，调用方负责在 finally 中关闭。
     */
    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handler)) { install(ContentNegotiation) { json(NetworkJson) } }

    // 模拟 Content-Type 与业务响应分开定义，使测试走实际的 JSON 内容协商。
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val poem = """{"status":"success","data":{"content":"测试诗句"}}"""
    private val token = """{"status":"success","data":"fresh-token"}"""

    /**
     * Token 读写均失败时仍使用新凭证取诗，存储恢复后复用内存值并补写。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun storageFailureDoesNotDiscardTokenAndCanRecover() = runTest {
        val store = Store().apply { failRead = true; failWrite = true }
        var tokenRequests = 0
        val http = client { request ->
            if (request.url.encodedPath == "/token") {
                tokenRequests++
                respond(token, headers = jsonHeaders)
            } else {
                assertEquals("fresh-token", request.headers["X-User-Token"])
                respond(poem, headers = jsonHeaders)
            }
        }
        try {
            val repository = PoemRepository(store, http) { _, _ -> }
            assertEquals("测试诗句", repository.fetchPoem()?.data?.content)
            // 第二次调用验证已获取的内存凭证被复用，同时重试此前失败的落盘。
            store.failRead = false
            store.failWrite = false
            assertNotNull(repository.fetchPoem())
            assertEquals("fresh-token", store.token)
            assertEquals(1, tokenRequests)
        } finally { http.close() }
    }

    /**
     * 旧 Token 被拒绝且磁盘删除失败时，验证重试使用新 Token，不重复发送已拒绝的凭证。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun failedCacheDeletionDoesNotReuseRejectedToken() = runTest {
        val store = Store("stale-token").apply { failWrite = true }
        // 记录实际发出的认证头顺序，直接证明第二次没有复用失效凭证。
        val sent = mutableListOf<String?>()
        val http = client { request ->
            if (request.url.encodedPath == "/token") respond(token, headers = jsonHeaders)
            else {
                sent += request.headers["X-User-Token"]
                if (sent.size == 1) respond("denied", HttpStatusCode.Unauthorized)
                else respond(poem, headers = jsonHeaders)
            }
        }
        try {
            assertNotNull(PoemRepository(store, http) { _, _ -> }.fetchPoem())
            assertEquals(listOf("stale-token", "fresh-token"), sent)
        } finally { http.close() }
    }

    /**
     * 诗句接口持续拒绝认证时，验证总共仅请求两次并清除最后一个无效 Token。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun persistentAuthenticationFailureIsBounded() = runTest {
        val store = Store("stale-token")
        var sentences = 0
        val http = client { request ->
            if (request.url.encodedPath == "/token") respond(token, headers = jsonHeaders)
            else { sentences++; respond("denied", HttpStatusCode.Forbidden) }
        }
        try {
            assertNull(PoemRepository(store, http) { _, _ -> }.fetchPoem())
            assertEquals(2, sentences)
            assertNull(store.token)
        } finally { http.close() }
    }

    /**
     * 并发取诗时延迟 Token 接口，验证各次请求共享一次 Token 申请，诗句请求仍分别执行。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun concurrentRequestsReuseToken() = runTest {
        var tokens = 0
        val http = client { request ->
            if (request.url.encodedPath == "/token") {
                tokens++; delay(100); respond(token, headers = jsonHeaders)
            } else respond(poem, headers = jsonHeaders)
        }
        try {
            val repository = PoemRepository(Store(), http) { _, _ -> }
            assertTrue(List(4) { async { repository.fetchPoem() } }.awaitAll().all { it != null })
            assertEquals(1, tokens)
        } finally { http.close() }
    }

    /**
     * HTTP 成功但诗句正文为空白时，验证返回失败并保留当前 Token，不触发认证重置。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun emptyPoemIsRejectedWithoutTokenReset() = runTest {
        val store = Store("valid")
        val http = client { respond("""{"status":"success","data":{"content":" "}}""", headers = jsonHeaders) }
        try {
            assertNull(PoemRepository(store, http) { _, _ -> }.fetchPoem())
            assertEquals("valid", store.token)
        } finally { http.close() }
    }

    /**
     * 读取 Token 时抛出取消异常，验证仓库继续传播取消，不联网也不发起重试。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun cancellationIsNotSwallowedOrRetried() = runTest {
        var requests = 0
        val http = client { requests++; respond(poem, headers = jsonHeaders) }
        try {
            try {
                PoemRepository(Store().apply { cancelRead = true }, http) { _, _ -> }.fetchPoem()
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
            assertEquals(0, requests)
        } finally { http.close() }
    }
}
