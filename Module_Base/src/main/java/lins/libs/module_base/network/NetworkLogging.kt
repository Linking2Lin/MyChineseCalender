package lins.libs.module_base.network

import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.LoggingConfig

/**
 * 集中配置网络日志，避免调试包把 Token 接口正文或认证请求头写入日志。
 * @param isDebug 是否为调试构建；调试仅输出脱敏头，发布完全关闭 HTTP 日志。
 * @return Unit；修改当前 LoggingConfig，不创建客户端或发送请求。
 */
internal fun LoggingConfig.configureSafeLogging(isDebug: Boolean) {
    // Token 也可能出现在响应正文，不能仅靠隐藏请求头后继续打印 BODY。
    level = if (isDebug) LogLevel.HEADERS else LogLevel.NONE
    sanitizeHeader { name ->
        name.equals("X-User-Token", ignoreCase = true) ||
            name.equals("Authorization", ignoreCase = true) ||
            name.equals("Cookie", ignoreCase = true) ||
            name.equals("Set-Cookie", ignoreCase = true)
    }
}
