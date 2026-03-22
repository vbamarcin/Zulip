package com.mkras.zulip.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "streams")
data class StreamEntity(
    @PrimaryKey
    val id: Long,
    val name: String,
    val description: String,
    val subscribed: Boolean
)
