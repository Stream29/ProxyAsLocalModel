package io.github.stream29.proxy.relocate.com.aallam.openai.api.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContentFilterOffsets(
    @SerialName("check_offset") val checkOffset: Int?,
    @SerialName("start_offset") val startOffset: Int?,
    @SerialName("end_offset") val endOffset: Int?,
)
