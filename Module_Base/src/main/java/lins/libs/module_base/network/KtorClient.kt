package lins.libs.module_base.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json

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
    private val jsonConfig = NetworkJson

    /**
     * 全局共享的 HTTP 客户端实例。
     * 生命周期与进程一致，单次仓库请求结束时不要 close，否则会影响其他模块。
     * 如需 MockEngine 应通过具体仓库的构造参数注入，避免测试修改全局客户端。
     */
    val client = HttpClient(Android) {
        engine {
            // 网络超时策略：避免弱网环境下长时间卡住。
            connectTimeout = 10_000
            socketTimeout = 10_000
        }

        install(HttpTimeout) {
            // 单次请求上限，不是“同步今日 + 更新组件 + 预取明日”整条流程的总超时。
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }

        install(Logging) {
            // 调试模式记录完整请求/响应体，可能包含诗词 Token；维护 Release 时须保持关闭。
            logger = Logger.DEFAULT
            level = if (lins.libs.module_base.BuildConfig.DEBUG) LogLevel.BODY else LogLevel.NONE
        }

        // 兼容把 JSON 标成 text/html 或 text/plain 的接口；真正的 HTML 错误页仍会解析失败。
        install(ContentNegotiation) {
            json(jsonConfig)
            json(jsonConfig, contentType = ContentType.Text.Html)
            json(jsonConfig, contentType = ContentType.Text.Plain)
        }
    }
}
