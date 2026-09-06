package lins.libs.module_base.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * HKO 农历接口的轻量返回模型。
 *
 * 这个接口只提供两个核心字段：
 * - `LunarYear`：年份描述，例如“癸卯年，兔”
 * - `LunarDate`：农历日期，例如“二月初十”
 *
 * 之所以单独维护这个模型，是为了让 widget 和缓存层只依赖最小必要字段，
 * 降低后续接口变化带来的影响。
 * 默认空字符串用于容忍缺字段的解析，仓库仍会拒绝任何核心字段为空的响应；
 * 此模型不包含公历日期，不能单独用它判断所属日期。
 */
@Serializable
data class LunarDateResponse(
    @SerialName("LunarYear")
    val lunarYear: String = "",

    @SerialName("LunarDate")
    val lunarDate: String = ""
)
