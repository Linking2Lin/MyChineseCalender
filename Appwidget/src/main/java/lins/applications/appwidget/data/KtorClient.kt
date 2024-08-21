package lins.applications.appwidget.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.headers

object KtorClient {
    val client = HttpClient(CIO) {
        engine {
            // this: CIOEngineConfig
            maxConnectionsCount = 1000
            endpoint {
                // this: EndpointConfig
                maxConnectionsPerRoute = 100
                pipelineMaxSize = 20
                keepAliveTime = 5000
                connectTimeout = 5000
                connectAttempts = 5
            }
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.BODY
        }

        install(DefaultRequest)

        defaultRequest {
            headers {
                append(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                )
                append("Accept-Encoding", "gzip, deflate, br,zstd")
                append("Accept-Language", "en,en-US;q=0.9,zh-CN;q=0.8,zh;q=0.7")
                append("Dnt", "1")
                append("Cache-control", "max-age=0")
                append("Priority", "u=0, i")
            }
        }
    }
}