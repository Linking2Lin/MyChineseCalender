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
}
