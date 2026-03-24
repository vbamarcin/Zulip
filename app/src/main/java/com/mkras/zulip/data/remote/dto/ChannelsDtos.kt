package com.mkras.zulip.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StreamDto(
    @Json(name = "stream_id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String?,
    @Json(name = "subscribed") val subscribed: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class StreamsResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "streams") val streams: List<StreamDto>
)

@JsonClass(generateAdapter = true)
data class TopicDto(
    @Json(name = "name") val name: String,
    @Json(name = "max_id") val maxId: Long
)

@JsonClass(generateAdapter = true)
data class TopicsResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "topics") val topics: List<TopicDto>
)
