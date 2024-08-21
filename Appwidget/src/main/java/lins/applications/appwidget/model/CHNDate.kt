package lins.applications.appwidget.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CHNDate(
    @SerialName("LunarYear") val lunarYear: String? = null,

)
