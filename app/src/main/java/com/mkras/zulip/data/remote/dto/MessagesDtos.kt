package com.mkras.zulip.data.remote.dto

import com.squareup.moshi.Json

data class MessageDto(
    @Json(name = "id") val id: Long,
    @Json(name = "sender_full_name") val senderFullName: String?,
    @Json(name = "sender_email") val senderEmail: String?,
    @Json(name = "content") val content: String?,
    @Json(name = "subject") val subject: String?,
    @Json(name = "stream_id") val streamId: Long?,
    @Json(name = "display_recipient") val displayRecipient: Any?,
    @Json(name = "timestamp") val timestamp: Long,
    @Json(name = "flags") val flags: List<String>?,
    @Json(name = "type") val type: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

data class MessagesResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "messages") val messages: List<MessageDto>
)

data class MessageFlagsResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String
)
