package com.mkras.zulip.data.remote.dto

import com.squareup.moshi.Json

// Send Message
data class SendMessageRequest(
    @Json(name = "content") val content: String,
    @Json(name = "type") val type: String, // "private" or "stream"
    @Json(name = "to") val to: String, // comma-separated user IDs for private, stream_id for stream
    @Json(name = "topic") val topic: String? = null // only for stream messages
)

data class SendMessageResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "id") val messageId: Long
)

data class UploadFileResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "url") val url: String? = null,
    @Json(name = "uri") val uri: String? = null,
    @Json(name = "filename") val filename: String? = null
)

// Reactions
data class AddReactionRequest(
    @Json(name = "emoji_name") val emojiName: String,
    @Json(name = "emoji_code") val emojiCode: String? = null,
    @Json(name = "reaction_type") val reactionType: String = "unicode_emoji"
)

data class ReactionResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String
)

// Search
data class SearchResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "messages") val messages: List<SearchMessageDto>
)

data class SearchMessageDto(
    @Json(name = "id") val id: Long,
    @Json(name = "content") val content: String,
    @Json(name = "subject") val topic: String,
    @Json(name = "type") val type: String,
    @Json(name = "display_recipient") val displayRecipient: Any?,
    @Json(name = "sender_full_name") val senderFullName: String,
    @Json(name = "sender_email") val senderEmail: String,
    @Json(name = "timestamp") val timestamp: Long
)

// Edit/Delete (both return simple success response)
data class MessageActionResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String
)
