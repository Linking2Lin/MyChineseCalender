package lins.libs.module_base.network

import kotlinx.serialization.json.Json

/**
 * 网络层唯一 JSON 约定，同时供 Ktor 内容协商与手工 bodyAsText 解码使用。
 * 容忍新增字段和部分非严格语法只解决解析兼容；HTTP 状态、必填内容、日期匹配仍由业务校验。
 * 新接口不要直接使用默认 Json，否则会绕过这里的扩字段兼容配置。
 */
val NetworkJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
}
