package com.mkras.zulip.domain.repository

import com.mkras.zulip.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(): Flow<List<MessageEntity>>
    fun observePrivateMessages(): Flow<List<MessageEntity>>
    fun observeStarredMessages(): Flow<List<MessageEntity>>
    fun observeMessages(streamName: String, topicName: String): Flow<List<MessageEntity>>
    fun observeDirectMessageCandidates(): Flow<List<DirectMessageCandidate>>
    suspend fun resyncLatestMessages()
    suspend fun resyncStarredMessages()
    suspend fun resyncNarrow(streamName: String, topicName: String)
    suspend fun fetchNarrowMessagesOnline(streamName: String, topicName: String): Result<List<MessageEntity>>
    suspend fun loadOlderNarrowMessages(
        streamName: String,
        topicName: String,
        anchorMessageId: Long,
        pageSize: Int = 50
    ): Result<Int>
    suspend fun markMessagesAsRead(ids: List<Long>)
    suspend fun uploadFile(fileName: String, mimeType: String?, bytes: ByteArray): Result<UploadedFile>
    suspend fun sendMessage(type: String, to: String, content: String, topic: String? = null, displayName: String = ""): Result<Long>
    suspend fun addReaction(messageId: Long, emojiName: String, emojiCode: String? = null, reactionType: String? = null): Result<Unit>
    suspend fun removeReaction(messageId: Long, emojiName: String, emojiCode: String? = null, reactionType: String? = null): Result<Unit>
    suspend fun editMessage(
        messageId: Long,
        newContent: String,
        newTopic: String? = null,
        newStreamId: Long? = null
    ): Result<Unit>
    suspend fun deleteMessage(messageId: Long): Result<Unit>
    suspend fun searchMessages(query: String): Result<List<MessageEntity>>
    suspend fun getDirectMessageCandidates(): Result<List<DirectMessageCandidate>>
    suspend fun getPresence(): Result<Map<String, String>>
    suspend fun setOwnPresence(status: String): Result<Unit>
    suspend fun getCurrentUserId(): Result<Long>
    suspend fun canModerateAllMessages(): Result<Boolean>
    suspend fun getCustomEmojis(): Result<List<CustomEmoji>>
}

data class DirectMessageCandidate(
    val userId: Long,
    val fullName: String,
    val email: String,
    val avatarUrl: String
)

data class UploadedFile(
    val filename: String,
    val url: String
)

data class CustomEmoji(
    val id: String,
    val name: String,
    val url: String,
    val isDeactivated: Boolean
)
