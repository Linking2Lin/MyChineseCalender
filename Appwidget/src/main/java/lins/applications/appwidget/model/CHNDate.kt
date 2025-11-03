package lins.applications.appwidget.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
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
){
    fun asList(): List<String?> {
        return listOf(year, lunarDate, huangLiDate, huiLiDate, ganZhiDate, wuXing, zhiRiXingShen, yi, ji)
    }

    fun getLength() : Int{
        return asList().size
    }

    companion object {
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
