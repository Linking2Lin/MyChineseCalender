package lins.libs.module_base.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object KtorClient {
    val client = HttpClient(Android){
        engine {
            // 设置网络请求的超时时间，防止在弱网环境下长时间阻塞
            connectTimeout = 10_000 // 10秒连接超时
            socketTimeout = 10_000  // 10秒读取超时
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.BODY
        }

        install(DefaultRequest)

        // 安装 ContentNegotiation 插件，这是解析 JSON 的核心
        install(ContentNegotiation) {
            val jsonConfig = Json {
                prettyPrint = true
                isLenient = true       // 宽松模式，允许不规范的 JSON（如引号缺失）
                ignoreUnknownKeys = true // 忽略未知字段，接口新增字段时不会崩溃
            }
            // 标准 JSON 响应（application/json）
            json(jsonConfig)
            // 兼容 HKO 等返回 JSON 内容却误报 text/html Content-Type 的 API
            json(jsonConfig, contentType = ContentType.Text.Html)
            json(jsonConfig, contentType = ContentType.Text.Plain)
        }

        defaultRequest {
//            headers {
//                append(
//                    "Accept",
//                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
//                )
//                append("Accept-Encoding", "gzip, deflate, br,zstd")
//                append("Accept-Language", "en,en-US;q=0.9,zh-CN;q=0.8,zh;q=0.7")
//                append("Dnt", "1")
//                append("Cache-control", "max-age=0")
//                append("Priority", "u=0, i")
//            }
        }
    }
}
