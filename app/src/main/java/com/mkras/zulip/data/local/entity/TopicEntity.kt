package com.mkras.zulip.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey
    val key: String,
    val streamId: Long,
    val name: String,
    val maxMessageId: Long
)
