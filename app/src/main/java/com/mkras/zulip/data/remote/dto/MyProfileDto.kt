package com.mkras.zulip.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MyProfileResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "user_id") val userId: Long? = null,
    @Json(name = "is_admin") val isAdmin: Boolean = false,
    @Json(name = "is_owner") val isOwner: Boolean = false,
    @Json(name = "is_moderator") val isModerator: Boolean = false,
    @Json(name = "role") val role: Int? = null
)
