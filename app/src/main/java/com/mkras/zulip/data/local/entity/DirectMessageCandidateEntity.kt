package com.mkras.zulip.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "direct_message_candidates")
data class DirectMessageCandidateEntity(
    @PrimaryKey
    val email: String,
    val userId: Long,
    val fullName: String,
    val avatarUrl: String,
    val sortKey: String
)