package com.mkras.zulip.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mkras.zulip.data.local.entity.AppBootstrapEntity
import com.mkras.zulip.data.local.entity.DirectMessageCandidateEntity
import com.mkras.zulip.data.local.entity.MessageEntity
import com.mkras.zulip.data.local.entity.StreamEntity
import com.mkras.zulip.data.local.entity.TopicEntity

@Database(
    entities = [AppBootstrapEntity::class, MessageEntity::class, StreamEntity::class, TopicEntity::class, DirectMessageCandidateEntity::class],
    version = 7,
    exportSchema = false
)
abstract class ZulipDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun streamDao(): StreamDao
    abstract fun topicDao(): TopicDao
    abstract fun directMessageCandidateDao(): DirectMessageCandidateDao
}
