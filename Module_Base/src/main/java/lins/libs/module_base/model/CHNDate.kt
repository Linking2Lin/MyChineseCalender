package lins.libs.module_base.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/**
 * 主界面所使用的“通用黄历数据模型”。
 *
 * 这个模型对应的是 `ChineseCalendarRepository` 返回的内容，
 * 字段尽量和接口语义保持一致，方便 UI 直接展示。
 */
data class CHNDate(
    @SerialName("公历日期") val year: String? = null,
    @SerialName("农历日期") val lunarDate: String? = null,
    @SerialName("黄历日期") val huangLiDate: String? = null,
    @SerialName("回历日期") val huiLiDate: String? = null,
    @SerialName("干支日期") val ganZhiDate: String? = null,
    @SerialName("五行纳音") val wuXing: String? = null,
    @SerialName("值日星神") val zhiRiXingShen: String? = null,
    @SerialName("宜") val yi: String? = null,
    @SerialName("忌") val ji: String? = null,
) {
    /**
     * 把所有字段按顺序打包成列表。
     * 这个方法主要用于调试、遍历或测试时快速检查字段完整性。
     */
    fun asList(): List<String?> {
        return listOf(year, lunarDate, huangLiDate, huiLiDate, ganZhiDate, wuXing, zhiRiXingShen, yi, ji)
    }

    /**
     * 返回字段数量。
     * 当前和 `asList()` 保持一致，主要用于测试或结构校验。
     */
    fun getLength(): Int {
        return asList().size
    }

    companion object {
        /**
         * 测试用样例数据。
         * 适合 Preview、单元测试和离线 UI 验证。
         */
        val test = CHNDate(
            "2025年11月3日 星期一",
            "农历二零二五年 九月(大) 十四",
            "阳历2025年11月3日，乙巳年阴历九月十四日",
            "伊斯兰历1447年5月12日",
            "乙巳年 丙戌月 丙子日",
            "涧下水",
            "天牢(凶星)",
            "纳采、订婚 订盟、开业 开幕 开市、交易、立券、会亲友、纳畜、牧养、问名、搬家 移徙、解除、开厕、入学、起基、安床、开仓、出货财、安葬、启钻、入殓、除服、成服、",
            "搬迁新宅 乔迁新居 入宅 上梁 斋醮 出火 谢土"
        )

    }
}
