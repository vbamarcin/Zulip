package com.mkras.zulip.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
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
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "reactions") val reactions: List<MessageReactionDto>? = null
)

@JsonClass(generateAdapter = true)
data class MessageReactionDto(
    @Json(name = "emoji_name") val emojiName: String?,
    @Json(name = "emoji_code") val emojiCode: String? = null,
    @Json(name = "reaction_type") val reactionType: String? = null,
    @Json(name = "user_id") val userId: Long? = null
)

@JsonClass(generateAdapter = true)
data class MessagesResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "messages") val messages: List<MessageDto>
)

@JsonClass(generateAdapter = true)
data class MessageFlagsResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String
)
