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
    private class Store(var token: String? = null) : PoemTokenStore {
        var failRead = false
        var failWrite = false
        var cancelRead = false
        override suspend fun read(): String? {
            if (cancelRead) throw CancellationException("cancel read")
            if (failRead) throw IOException("disk read failed")
            return token
        }
        override suspend fun write(token: String?) {
            if (failWrite) throw IOException("disk write failed")
            this.token = token
        }
    }

    /** @return 配置了实际 JSON 解析器、由每个测试负责关闭的客户端。 */
    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handler)) { install(ContentNegotiation) { json(NetworkJson) } }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val poem = """{"status":"success","data":{"content":"测试诗句"}}"""
    private val token = """{"status":"success","data":"fresh-token"}"""

    /** 读/写盘失败时仍用新 Token 取诗，存储恢复后复用内存并补写。 */
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
            store.failRead = false
            store.failWrite = false
            assertNotNull(repository.fetchPoem())
            assertEquals("fresh-token", store.token)
            assertEquals(1, tokenRequests)
        } finally { http.close() }
    }

    /** 删除磁盘旧 Token 失败后，也不能把同一个被拒绝的 Token 再用于重试。 */
    @Test fun failedCacheDeletionDoesNotReuseRejectedToken() = runTest {
        val store = Store("stale-token").apply { failWrite = true }
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

    /** 持续拒绝认证时总共只请求两次诗句，并清掉最后一次无效 Token。 */
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

    /** 并发获取共享一次 Token 申请，诗句本身仍按各次请求返回。 */
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

    /** HTTP 成功不替代正文完整性检查，失败不会清空/换取仍有效的 Token。 */
    @Test fun emptyPoemIsRejectedWithoutTokenReset() = runTest {
        val store = Store("valid")
        val http = client { respond("""{"status":"success","data":{"content":" "}}""", headers = jsonHeaders) }
        try {
            assertNull(PoemRepository(store, http) { _, _ -> }.fetchPoem())
            assertEquals("valid", store.token)
        } finally { http.close() }
    }

    /** 取消读取时不联网、不转换成普通失败，供 ViewModel 正常结束任务。 */
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
