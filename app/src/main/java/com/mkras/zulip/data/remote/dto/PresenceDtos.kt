package com.mkras.zulip.data.remote.dto

import com.squareup.moshi.Json

data class PresenceResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "presences") val presences: Map<String, UserPresenceDto>? = null
)

data class UserPresenceDto(
    @Json(name = "aggregated") val aggregated: PresenceDetailDto? = null,
    @Json(name = "website") val website: PresenceDetailDto? = null
)

data class PresenceDetailDto(
    @Json(name = "status") val status: String? = null,
    @Json(name = "timestamp") val timestamp: Long? = null
)
