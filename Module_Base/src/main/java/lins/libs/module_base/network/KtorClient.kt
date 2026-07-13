package lins.libs.module_base.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Ktor HttpClient 统一配置入口。
 *
 * 把客户端放在单例里统一复用，可以避免每次请求都重新创建连接池和插件，
 * 对于 app 内多处网络请求场景更省资源。
 */
object KtorClient {
    /**
     * 统一的 JSON 解析配置。
     *
     * - `isLenient = true`：容忍部分不严格 JSON
     * - `ignoreUnknownKeys = true`：接口扩字段时不至于崩溃
     */
    private val jsonConfig = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    /**
     * 全局共享的 HTTP 客户端实例。
     */
    val client = HttpClient(Android) {
        engine {
            // 网络超时策略：避免弱网环境下长时间卡住。
            connectTimeout = 10_000
            socketTimeout = 10_000
        }

        install(Logging) {
            // 调试模式下输出完整请求/响应体，方便排查网络问题。
            logger = Logger.DEFAULT
            level = if (lins.libs.module_base.BuildConfig.DEBUG) LogLevel.BODY else LogLevel.NONE
        }

        // 统一安装 JSON 解析能力，并兼容一些 Content-Type 标注不标准的接口。
        install(ContentNegotiation) {
            json(jsonConfig)
            json(jsonConfig, contentType = ContentType.Text.Html)
            json(jsonConfig, contentType = ContentType.Text.Plain)
        }
    }
}
