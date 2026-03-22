package com.mkras.zulip.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mkras.zulip.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages ORDER BY id ASC")
    fun observeMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isStarred = 1 ORDER BY timestampSeconds DESC")
    fun observeStarredMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageType = 'private' ORDER BY timestampSeconds ASC")
    fun observePrivateMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE streamName = :streamName AND topic = :topicName ORDER BY id ASC")
    fun observeMessages(streamName: String, topicName: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE streamName = :streamName ORDER BY id ASC")
    fun observeMessagesForStream(streamName: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET isRead = :isRead WHERE id IN (:ids)")
    suspend fun updateReadFlags(ids: List<Long>, isRead: Boolean)

    @Query("UPDATE messages SET isStarred = :isStarred WHERE id IN (:ids)")
    suspend fun updateStarredFlags(ids: List<Long>, isStarred: Boolean)

    @Query("UPDATE messages SET isMentioned = :isMentioned WHERE id IN (:ids)")
    suspend fun updateMentionedFlags(ids: List<Long>, isMentioned: Boolean)

    @Query("UPDATE messages SET isWildcardMentioned = :isWildcardMentioned WHERE id IN (:ids)")
    suspend fun updateWildcardMentionedFlags(ids: List<Long>, isWildcardMentioned: Boolean)

    @Query("UPDATE messages SET reactionSummary = :summary WHERE id = :messageId")
    suspend fun updateReactionSummary(messageId: Long, summary: String?)

    @Query("UPDATE messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String)

    @Query("UPDATE messages SET content = :content, isRead = :isRead WHERE id = :messageId")
    suspend fun updateMessageContentAndReadState(messageId: Long, content: String, isRead: Boolean)

    @Query("UPDATE messages SET topic = :topic WHERE id IN (:ids)")
    suspend fun updateTopics(ids: List<Long>, topic: String)

    @Query("UPDATE messages SET streamName = :streamName, topic = :topic WHERE id IN (:ids)")
    suspend fun updateStreamAndTopic(ids: List<Long>, streamName: String?, topic: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessages(ids: List<Long>)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
