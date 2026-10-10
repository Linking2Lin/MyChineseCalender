package lins.libs.module_poem.model

import kotlinx.serialization.Serializable

/**
 * 今日诗词接口的顶层响应模型。
 *
 * `status` 用来表示接口层状态；`data` 是真正展示在 UI 上的诗词内容；
 * `token` 保留接口可能返回的字段，当前仓库不使用它更新 Token；持久 Token 来自独立 token 接口。
 * @param status 服务端业务状态，只有 success 可通过可用性校验。
 * @param data 诗词正文与出处，null 表示没有可展示的数据。
 * @param token 服务端可能附带的 Token，当前保留解析但不用于持久化。
 */
@Serializable
data class PoemResponse(
    val status: String,
    val data: PoemData? = null,
    val token: String? = null
) {
    /**
     * HTTP 成功之外还需检查业务成功与正文非空；出处和翻译缺省不影响卡片展示。
     * @return Boolean；业务状态成功且正文非空时为 true。
     */
    fun isUsable(): Boolean = status == "success" && !data?.content.isNullOrBlank()
}

/**
 * 诗词正文数据。
 *
 * 这个层级的字段主要服务于主页面卡片展示：
 * - `content`：诗句正文
 * - `origin`：原文出处信息
 * - `popularity`：热度值，当前页面暂未使用，但保留以便后续功能扩展
 * @param id 服务端诗词标识，缺省为空字符串。
 * @param content 当前诗句正文，非空白是业务成功的必要条件。
 * @param popularity 服务端热度值，当前不参与展示计算。
 * @param origin 出处详情，缺省时仍可单独展示诗句。
 */
@Serializable
data class PoemData(
    val id: String = "",
    val content: String = "",
    val popularity: Int = 0,
    val origin: PoemOrigin? = null
)

/**
 * 诗词出处信息。
 *
 * 用于展示作者、朝代和作品标题。`content` / `translate` 目前页面上未直接使用，
 * 但保留可以支持后续扩展“原文/译文”展示。
 * @param title 作品名，沿用服务端文本。
 * @param dynasty 作者朝代，沿用服务端文本。
 * @param author 作者名称，沿用服务端文本。
 * @param content 原文段落列表，当前页面不逐段展示。
 * @param translate 可选译文段落列表，null 表示服务端未提供。
 */
@Serializable
data class PoemOrigin(
    val title: String = "",
    val dynasty: String = "",
    val author: String = "",
    val content: List<String> = emptyList(),
    val translate: List<String>? = null
)
