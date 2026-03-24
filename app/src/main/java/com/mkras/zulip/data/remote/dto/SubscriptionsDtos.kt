package com.mkras.zulip.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubscriptionDto(
    @Json(name = "stream_id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String?,
    @Json(name = "is_muted") val isMuted: Boolean? = false,
    @Json(name = "desktop_notifications") val desktopNotifications: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class SubscriptionsResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "subscriptions") val subscriptions: List<SubscriptionDto> = emptyList()
)