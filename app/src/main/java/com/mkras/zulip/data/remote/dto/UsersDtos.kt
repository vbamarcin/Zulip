package com.mkras.zulip.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UsersResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "members") val members: List<UserDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "user_id") val userId: Long,
    @Json(name = "email") val email: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "is_bot") val isBot: Boolean = false
)
