package com.mkras.zulip.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: Long,
    val senderFullName: String,
    val senderEmail: String,
    val content: String,
    val topic: String,
    val streamName: String?,
    val timestampSeconds: Long,
    val isRead: Boolean,
    val isStarred: Boolean = false,
    val isMentioned: Boolean = false,
    val isWildcardMentioned: Boolean = false,
    val reactionSummary: String?,
    val avatarUrl: String = "",
    val messageType: String = "",
    val conversationKey: String = "",
    val dmDisplayName: String = ""
)
