package com.mkras.zulip.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_bootstrap")
data class AppBootstrapEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
