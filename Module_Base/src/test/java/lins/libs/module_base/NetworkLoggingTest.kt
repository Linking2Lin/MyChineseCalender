package lins.libs.module_base

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.headersOf
import java.util.Collections
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import lins.libs.module_base.network.configureSafeLogging
import org.junit.Assert.*
import org.junit.Test

/** 验证真实 Ktor 日志插件的最终输出，而非只检查配置常量。 */
class NetworkLoggingTest {
    /**
     * 捕获真实 Ktor Logging 输出，验证 Debug 隐藏认证头与正文，Release 不产生 HTTP 日志。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun credentialsAreHiddenInDebugAndRelease() = runTest {
        for (debug in listOf(true, false)) {
            val messages = Collections.synchronizedList(mutableListOf<String>())
            val client = HttpClient(MockEngine {
                respond("body-token-placeholder", headers = headersOf("Set-Cookie", "response-cookie-placeholder"))
            }) {
                install(Logging) {
                    logger = object : Logger {
                        /**
                         * 收集日志插件实际发出的文本，供请求结束后的脱敏断言使用。
                         * @param message Logging 插件产生的一条日志，不在本回调中改写或二次脱敏。
                         * @return Unit；将文本追加到线程安全的测试收集列表。
                         */
                        override fun log(message: String) { messages += message }
                    }
                    configureSafeLogging(debug)
                }
            }
            try {
                // 凭证放在不同大小写的头与正文，确保脱敏不依赖调用处的头名格式。
                client.get("https://example.test/token") {
                    header("x-user-token", "request-token-placeholder")
                    header("Authorization", "authorization-placeholder")
                    header("Cookie", "request-cookie-placeholder")
                }.bodyAsText()
            } finally {
                client.close()
                // 等待客户端内部日志协程结束后再断言，避免遗漏最后几条异步日志。
                client.coroutineContext[Job]?.join()
            }
            val output = messages.joinToString("\n")
            assertFalse(output.contains("placeholder"))
            if (debug) assertTrue(output.contains("***")) else assertTrue(messages.isEmpty())
        }
    }
}
