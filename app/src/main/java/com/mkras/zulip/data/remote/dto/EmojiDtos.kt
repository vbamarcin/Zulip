package com.mkras.zulip.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RealmEmojiResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "emoji") val emoji: Map<String, RealmEmojiEntryDto> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class RealmEmojiEntryDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "source_url") val sourceUrl: String? = null,
    @Json(name = "deactivated") val deactivated: Boolean? = null
)
