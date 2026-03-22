package com.mkras.zulip.data.remote.dto

import com.squareup.moshi.Json

data class FetchApiKeyResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "api_key") val apiKey: String?,
    @Json(name = "email") val email: String?
)

data class RegisterResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "queue_id") val queueId: String?,
    @Json(name = "last_event_id") val lastEventId: Long?,
    @Json(name = "realm_user_settings") val realmUserSettings: RealmUserSettingsDto?
)

data class RealmUserSettingsDto(
    @Json(name = "enable_desktop_notifications") val enableDesktopNotifications: Boolean?
)

data class EventDto(
    @Json(name = "id") val id: Long,
    @Json(name = "type") val type: String,
    @Json(name = "op") val op: String? = null,
    @Json(name = "message") val message: EventMessageDto? = null,
    @Json(name = "message_id") val messageId: Long? = null,
    @Json(name = "message_ids") val messageIds: List<Long>? = null,
    @Json(name = "messages") val messages: List<Long>? = null,
    @Json(name = "flag") val flag: String? = null,
    @Json(name = "flags") val flags: List<String>? = null,
    @Json(name = "user_id") val userId: Long? = null,
    @Json(name = "sender_id") val senderId: Long? = null,
    @Json(name = "sender_email") val senderEmail: String? = null,
    @Json(name = "emoji_name") val emojiName: String? = null,
    @Json(name = "status") val presenceStatus: String? = null,
    @Json(name = "content") val content: String? = null,
    @Json(name = "subject") val subject: String? = null,
    @Json(name = "stream_id") val streamId: Long? = null,
    @Json(name = "new_stream_id") val newStreamId: Long? = null,
    @Json(name = "stream_name") val streamName: String? = null,
    @Json(name = "rendering_only") val renderingOnly: Boolean? = null,
    @Json(name = "message_type") val messageType: String? = null
)

data class EventMessageDto(
    @Json(name = "id") val id: Long,
    @Json(name = "sender_full_name") val senderFullName: String?,
    @Json(name = "sender_email") val senderEmail: String?,
    @Json(name = "content") val content: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "flags") val flags: List<String>?,
    @Json(name = "subject") val subject: String?,
    @Json(name = "display_recipient") val displayRecipient: Any?,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "timestamp") val timestamp: Long? = null
)

data class EventsResponseDto(
    @Json(name = "result") val result: String,
    @Json(name = "msg") val message: String,
    @Json(name = "events") val events: List<EventDto> = emptyList(),
    @Json(name = "queue_id") val queueId: String?,
    @Json(name = "last_event_id") val lastEventId: Long?
)
