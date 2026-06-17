package lins.libs.module_poem.model

import kotlinx.serialization.Serializable

@Serializable
data class PoemResponse(
    val status: String,
    val data: PoemData? = null,
    val token: String? = null
)

@Serializable
data class PoemData(
    val id: String = "",
    val content: String = "",
    val popularity: Int = 0,
    val origin: PoemOrigin? = null
)

@Serializable
data class PoemOrigin(
    val title: String,
    val dynasty: String,
    val author: String,
    val content: List<String> = emptyList(),
    val translate: List<String>? = null
)
