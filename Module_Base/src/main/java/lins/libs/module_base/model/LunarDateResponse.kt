package lins.libs.module_base.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @Author LinXuXu
 * @Date 2026/3/27 14:46
 * 
 */
@Serializable
data class LunarDateResponse(
    // 使用 @SerialName 映射 JSON 中的大写字段名到 Kotlin 风格的驼峰命名
    @SerialName("LunarYear")
    val lunarYear: String = "", // 例如："癸卯年，兔"

    @SerialName("LunarDate")
    val lunarDate: String = ""  // 例如："二月初十"
)
