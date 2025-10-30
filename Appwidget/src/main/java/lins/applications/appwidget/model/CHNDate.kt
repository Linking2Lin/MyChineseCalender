package lins.applications.appwidget.model

import kotlinx.serialization.SerialName

data class CHNDate(
    @SerialName("公历日期") val year: String? = null,
    @SerialName("农历日期") val lunarDate: String? = null
)
