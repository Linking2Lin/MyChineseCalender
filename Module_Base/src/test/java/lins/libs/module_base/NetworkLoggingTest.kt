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
    /** @return Unit；模拟凭证不得出现在 Debug 输出，Release 不产生 HTTP 日志。 */
    @Test fun credentialsAreHiddenInDebugAndRelease() = runTest {
        for (debug in listOf(true, false)) {
            val messages = Collections.synchronizedList(mutableListOf<String>())
            val client = HttpClient(MockEngine {
                respond("body-token-placeholder", headers = headersOf("Set-Cookie", "response-cookie-placeholder"))
            }) {
                install(Logging) {
                    logger = object : Logger {
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
                client.coroutineContext[Job]?.join()
            }
            val output = messages.joinToString("\n")
            assertFalse(output.contains("placeholder"))
            if (debug) assertTrue(output.contains("***")) else assertTrue(messages.isEmpty())
        }
    }
}
