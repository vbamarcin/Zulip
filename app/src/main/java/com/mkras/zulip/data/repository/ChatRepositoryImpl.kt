package com.mkras.zulip.data.repository

import android.util.Log
import com.mkras.zulip.core.chat.DmConversationKey
import com.mkras.zulip.core.network.BasicCredentials
import com.mkras.zulip.core.network.ZulipApiFactory
import com.mkras.zulip.core.security.SecureSessionStorage
import com.mkras.zulip.data.local.db.DirectMessageCandidateDao
import com.mkras.zulip.data.local.db.MessageDao
import com.mkras.zulip.data.local.db.StreamDao
import com.mkras.zulip.data.local.entity.DirectMessageCandidateEntity
import com.mkras.zulip.data.local.entity.MessageEntity
import com.mkras.zulip.domain.repository.ChatRepository
import com.mkras.zulip.domain.repository.CustomEmoji
import com.mkras.zulip.domain.repository.DirectMessageCandidate
import com.mkras.zulip.domain.repository.UploadedFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val secureSessionStorage: SecureSessionStorage,
    private val zulipApiFactory: ZulipApiFactory,
    private val messageDao: MessageDao,
    private val streamDao: StreamDao,
    private val directMessageCandidateDao: DirectMessageCandidateDao
) : ChatRepository {

    private companion object {
        const val TAG = "PresenceDebug"
    }

    private fun escapeJsonOperand(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    override fun observeMessages(): Flow<List<MessageEntity>> = messageDao.observeMessages()

    override fun observePrivateMessages(): Flow<List<MessageEntity>> = messageDao.observePrivateMessages()

    override fun observeStarredMessages(): Flow<List<MessageEntity>> = messageDao.observeStarredMessages()

    override fun observeDirectMessageCandidates(): Flow<List<DirectMessageCandidate>> {
        return directMessageCandidateDao.observeCandidates()
            .map { cached ->
                cached.map {
                    DirectMessageCandidate(
                        userId = it.userId,
                        fullName = it.fullName,
                        email = it.email,
                        avatarUrl = it.avatarUrl
                    )
                }
            }
    }

    override fun observeMessages(streamName: String, topicName: String): Flow<List<MessageEntity>> {
        return if (topicName.isBlank()) {
            messageDao.observeMessagesForStream(streamName = streamName)
        } else {
            messageDao.observeMessages(streamName = streamName, topicName = topicName)
        }
    }

    override suspend fun resyncLatestMessages() {
        val auth = secureSessionStorage.getAuth() ?: return
        val service = zulipApiFactory.create(
            serverUrl = auth.serverUrl,
            credentials = BasicCredentials(auth.email, auth.apiKey)
        )

        val response = service.getMessages(
            numBefore = 200,
            applyMarkdown = false
        )
        if (response.result != "success") {
            return
        }

        val selfEmail = auth.email
        val serverUrl = auth.serverUrl
        val subscribedStreams = streamDao.observeStreams()
            .map { streams -> streams.filter { it.subscribed }.map { it.name.lowercase() }.toSet() }
            .first()
        val disabledStreams = secureSessionStorage.getDisabledChannels()
        val mapped = response.messages.map { dto ->
            val msgType = dto.type.orEmpty()
            val dtoStreamName = if (msgType == "stream") dto.displayRecipient?.toString().orEmpty().trim() else ""
            if (msgType == "stream" && dtoStreamName.isNotBlank() && !subscribedStreams.contains(dtoStreamName.lowercase())) {
                return@map null
            }
            if (msgType == "stream" && dtoStreamName.isNotBlank() && disabledStreams.contains(dtoStreamName.lowercase())) {
                return@map null
            }
            val recipients = if (msgType == "private") parsePrivateRecipients(dto.displayRecipient) else emptyList()
            MessageEntity(
                id = dto.id,
                senderFullName = dto.senderFullName.orEmpty().ifBlank { dto.senderEmail.orEmpty() },
                senderEmail = dto.senderEmail.orEmpty(),
                content = dto.content.orEmpty(),
                topic = dto.subject.orEmpty(),
                streamName = if (msgType == "stream") dtoStreamName else null,
                timestampSeconds = dto.timestamp,
                isRead = dto.flags?.contains("read") == true,
                isStarred = dto.flags?.contains("starred") == true,
                isMentioned = dto.flags?.contains("mentioned") == true,
                isWildcardMentioned = dto.flags?.contains("wildcard_mentioned") == true,
                reactionSummary = buildReactionSummary(dto.reactions),
                avatarUrl = resolveAvatarUrl(dto.avatarUrl.orEmpty(), serverUrl),
                messageType = msgType,
                conversationKey = if (msgType == "private") DmConversationKey.fromRecipientMaps(recipients, dto.senderEmail.orEmpty(), selfEmail) else "",
                dmDisplayName = if (msgType == "private") buildDmDisplayName(recipients, selfEmail, dto.senderFullName.orEmpty()) else ""
            )
        }.filterNotNull()

        messageDao.upsertAll(mapped)
    }

    override suspend fun resyncStarredMessages() {
        val auth = secureSessionStorage.getAuth() ?: return
        val service = zulipApiFactory.create(
            serverUrl = auth.serverUrl,
            credentials = BasicCredentials(auth.email, auth.apiKey)
        )

        val narrowJson = "[{\"operator\":\"is\",\"operand\":\"starred\"}]"
        val response = service.getMessages(
            anchor = "newest",
            numBefore = 200,
            numAfter = 0,
            narrowJson = narrowJson,
            applyMarkdown = false
        )
        if (response.result != "success") {
            return
        }

        val selfEmail = auth.email
        val serverUrl = auth.serverUrl
        val disabledStreams = secureSessionStorage.getDisabledChannels()
        val mapped = response.messages.map { dto ->
            val msgType = dto.type.orEmpty()
            val dtoStreamName = if (msgType == "stream") dto.displayRecipient?.toString().orEmpty().trim() else ""
            if (msgType == "stream" && dtoStreamName.isNotBlank() && disabledStreams.contains(dtoStreamName.lowercase())) {
                return@map null
            }
            val recipients = if (msgType == "private") parsePrivateRecipients(dto.displayRecipient) else emptyList()
            MessageEntity(
                id = dto.id,
                senderFullName = dto.senderFullName.orEmpty().ifBlank { dto.senderEmail.orEmpty() },
                senderEmail = dto.senderEmail.orEmpty(),
                content = dto.content.orEmpty(),
                topic = dto.subject.orEmpty(),
                streamName = if (msgType == "stream") dtoStreamName else null,
                timestampSeconds = dto.timestamp,
                isRead = dto.flags?.contains("read") == true,
                isStarred = dto.flags?.contains("starred") == true,
                isMentioned = dto.flags?.contains("mentioned") == true,
                isWildcardMentioned = dto.flags?.contains("wildcard_mentioned") == true,
                reactionSummary = buildReactionSummary(dto.reactions),
                avatarUrl = resolveAvatarUrl(dto.avatarUrl.orEmpty(), serverUrl),
                messageType = msgType,
                conversationKey = if (msgType == "private") DmConversationKey.fromRecipientMaps(recipients, dto.senderEmail.orEmpty(), selfEmail) else "",
                dmDisplayName = if (msgType == "private") buildDmDisplayName(recipients, selfEmail, dto.senderFullName.orEmpty()) else ""
            )
        }.filterNotNull()

        messageDao.upsertAll(mapped)
    }

    override suspend fun resyncNarrow(streamName: String, topicName: String) {
        val auth = secureSessionStorage.getAuth() ?: return
        val service = zulipApiFactory.create(
            serverUrl = auth.serverUrl,
            credentials = BasicCredentials(auth.email, auth.apiKey)
        )

                val streamEsc = escapeJsonOperand(streamName)
                val narrowJson = if (topicName.isBlank()) {
                        "[{\"operator\":\"stream\",\"operand\":\"$streamEsc\"}]"
                } else {
                        val topicEsc = escapeJsonOperand(topicName)
                        "[{\"operator\":\"stream\",\"operand\":\"$streamEsc\"},{\"operator\":\"topic\",\"operand\":\"$topicEsc\"}]"
                }

        val response = service.getMessages(
            numBefore = 100,
            narrowJson = narrowJson,
            applyMarkdown = false
        )
        if (response.result != "success") {
            return
        }

        val mapped = response.messages.map { dto ->
            MessageEntity(
                id = dto.id,
                senderFullName = dto.senderFullName.orEmpty().ifBlank { dto.senderEmail.orEmpty() },
                senderEmail = dto.senderEmail.orEmpty(),
                content = dto.content.orEmpty(),
                topic = dto.subject.orEmpty(),
                streamName = streamName,
                timestampSeconds = dto.timestamp,
                isRead = dto.flags?.contains("read") == true,
                isStarred = dto.flags?.contains("starred") == true,
                isMentioned = dto.flags?.contains("mentioned") == true,
                isWildcardMentioned = dto.flags?.contains("wildcard_mentioned") == true,
                reactionSummary = buildReactionSummary(dto.reactions),
                avatarUrl = resolveAvatarUrl(dto.avatarUrl.orEmpty(), auth.serverUrl),
                messageType = "stream",
                conversationKey = "",
                dmDisplayName = ""
            )
        }

        messageDao.upsertAll(mapped)
    }

    override suspend fun fetchNarrowMessagesOnline(streamName: String, topicName: String): Result<List<MessageEntity>> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val streamEsc = escapeJsonOperand(streamName)
            val narrowJson = if (topicName.isBlank()) {
                "[{\"operator\":\"stream\",\"operand\":\"$streamEsc\"}]"
            } else {
                val topicEsc = escapeJsonOperand(topicName)
                "[{\"operator\":\"stream\",\"operand\":\"$streamEsc\"},{\"operator\":\"topic\",\"operand\":\"$topicEsc\"}]"
            }

            val response = service.getMessages(
                numBefore = 100,
                narrowJson = narrowJson,
                applyMarkdown = false
            )
            if (response.result != "success") {
                return Result.failure(Exception(response.message.ifBlank { "Nie udalo sie pobrac wiadomosci online." }))
            }

            val mapped = response.messages.map { dto ->
                MessageEntity(
                    id = dto.id,
                    senderFullName = dto.senderFullName.orEmpty().ifBlank { dto.senderEmail.orEmpty() },
                    senderEmail = dto.senderEmail.orEmpty(),
                    content = dto.content.orEmpty(),
                    topic = dto.subject.orEmpty(),
                    streamName = streamName,
                    timestampSeconds = dto.timestamp,
                    isRead = dto.flags?.contains("read") == true,
                    isStarred = dto.flags?.contains("starred") == true,
                    isMentioned = dto.flags?.contains("mentioned") == true,
                    isWildcardMentioned = dto.flags?.contains("wildcard_mentioned") == true,
                    reactionSummary = buildReactionSummary(dto.reactions),
                    avatarUrl = resolveAvatarUrl(dto.avatarUrl.orEmpty(), auth.serverUrl),
                    messageType = "stream",
                    conversationKey = "",
                    dmDisplayName = ""
                )
            }.sortedBy { it.id }

            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadOlderNarrowMessages(
        streamName: String,
        topicName: String,
        anchorMessageId: Long,
        pageSize: Int
    ): Result<Int> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val streamEsc = escapeJsonOperand(streamName)
            val narrowJson = if (topicName.isBlank()) {
                "[{\"operator\":\"stream\",\"operand\":\"$streamEsc\"}]"
            } else {
                val topicEsc = escapeJsonOperand(topicName)
                "[{\"operator\":\"stream\",\"operand\":\"$streamEsc\"},{\"operator\":\"topic\",\"operand\":\"$topicEsc\"}]"
            }

            val response = service.getMessages(
                anchor = anchorMessageId.toString(),
                numBefore = pageSize,
                numAfter = 0,
                narrowJson = narrowJson,
                applyMarkdown = false
            )
            if (response.result != "success") {
                return Result.failure(Exception(response.message.ifBlank { "Nie udalo sie pobrac starszych wiadomosci." }))
            }

            val mapped = response.messages.map { dto ->
                MessageEntity(
                    id = dto.id,
                    senderFullName = dto.senderFullName.orEmpty().ifBlank { dto.senderEmail.orEmpty() },
                    senderEmail = dto.senderEmail.orEmpty(),
                    content = dto.content.orEmpty(),
                    topic = dto.subject.orEmpty(),
                    streamName = streamName,
                    timestampSeconds = dto.timestamp,
                    isRead = dto.flags?.contains("read") == true,
                    isStarred = dto.flags?.contains("starred") == true,
                    isMentioned = dto.flags?.contains("mentioned") == true,
                    isWildcardMentioned = dto.flags?.contains("wildcard_mentioned") == true,
                    reactionSummary = buildReactionSummary(dto.reactions),
                    avatarUrl = resolveAvatarUrl(dto.avatarUrl.orEmpty(), auth.serverUrl),
                    messageType = "stream",
                    conversationKey = "",
                    dmDisplayName = ""
                )
            }

            messageDao.upsertAll(mapped)
            Result.success(mapped.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePrivateRecipients(displayRecipient: Any?): List<Map<*, *>> {
        return (displayRecipient as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            .orEmpty()
    }

    private fun buildDmDisplayName(recipients: List<Map<*, *>>, selfEmail: String, fallback: String): String {
        val names = recipients
            .filter { (it["email"] as? String)?.equals(selfEmail, ignoreCase = true) == false }
            .mapNotNull { it["full_name"] as? String }
        return names.joinToString(", ").ifBlank { fallback }
    }

    private fun resolveAvatarUrl(raw: String, serverUrl: String): String {
        if (raw.isBlank()) return ""
        return if (raw.startsWith("http")) raw else "${serverUrl.trimEnd('/')}$raw"
    }

    private fun buildReactionSummary(reactions: List<com.mkras.zulip.data.remote.dto.MessageReactionDto>?): String? {
        val tokens = reactions
            ?.mapNotNull { reaction ->
                val name = reaction.emojiName?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                encodeReactionToken(name, reaction.emojiCode, reaction.reactionType, reaction.userId)
            }
            .orEmpty()
        return tokens.joinToString("|").ifBlank { null }
    }

    private fun encodeReactionToken(name: String, code: String?, type: String?, userId: Long?): String {
        val safeName = name.replace("::", ":")
        val safeCode = code?.replace("::", ":").orEmpty()
        val safeType = type?.replace("::", ":").orEmpty()
        val safeUserId = userId?.toString().orEmpty()
        return "$safeName::$safeCode::$safeType::$safeUserId"
    }

    private fun extractApiErrorMessage(exception: HttpException): String {
        val body = runCatching { exception.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        val apiMessage = Regex("\"msg\"\\s*:\\s*\"([^\"]+)\"")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\\\\"", "\"")
            ?.trim()
        return listOfNotNull(apiMessage, "HTTP ${exception.code()}")
            .joinToString(" ")
            .ifBlank { "HTTP ${exception.code()}" }
    }

    private fun unicodeReactionNameCandidates(emojiName: String, emojiCode: String?): List<String> {
        val normalizedName = emojiName.trim().lowercase()
        val normalizedCode = emojiCode?.trim()?.lowercase().orEmpty()
        val candidates = mutableListOf<String>()

        if (emojiName.isNotBlank()) {
            candidates += emojiName.trim()
        }

        when (normalizedName) {
            "+1", "thumbs_up" -> candidates += listOf("thumbs_up", "+1")
            "-1", "thumbs_down" -> candidates += listOf("thumbs_down", "-1")
            "joy", "laughing" -> candidates += listOf("joy", "laughing")
            "open_mouth", "surprised" -> candidates += listOf("open_mouth", "surprised")
            "cry", "crying" -> candidates += listOf("cry", "crying")
        }

        when (normalizedCode) {
            "1f44d" -> candidates += listOf("thumbs_up", "+1")
            "1f44e" -> candidates += listOf("thumbs_down", "-1")
            "1f602" -> candidates += listOf("joy", "laughing")
            "1f62e", "1f62e-fe0f" -> candidates += listOf("open_mouth", "surprised")
            "1f622" -> candidates += listOf("cry", "crying")
            "2764", "2764-fe0f" -> candidates += "heart"
            "1f389" -> candidates += "tada"
            "1f525" -> candidates += "fire"
            "1f440" -> candidates += "eyes"
            "2728" -> candidates += "sparkles"
            "1f64c" -> candidates += "raised_hands"
            "1f4af" -> candidates += "100"
            "1f680" -> candidates += "rocket"
        }

        return candidates
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf(emojiName) }
    }

    override suspend fun markMessagesAsRead(ids: List<Long>) {
        if (ids.isEmpty()) {
            return
        }

        messageDao.updateReadFlags(ids = ids, isRead = true)

        val auth = secureSessionStorage.getAuth() ?: return
        val service = zulipApiFactory.create(
            serverUrl = auth.serverUrl,
            credentials = BasicCredentials(auth.email, auth.apiKey)
        )

        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                service.updateMessageFlags(
                    messagesJson = ids.joinToString(prefix = "[", postfix = "]"),
                    operation = "add",
                    flag = "read"
                )
                return
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) {
                    delay((400L shl attempt).coerceAtMost(2_000L))
                }
            }
        }

        // All retries failed: revert local DB update to prevent stale state.
        messageDao.updateReadFlags(ids = ids, isRead = false)
        throw (lastError ?: Exception("Failed to mark messages as read"))
    }

    override suspend fun uploadFile(fileName: String, mimeType: String?, bytes: ByteArray): Result<UploadedFile> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val requestBody = bytes.toRequestBody((mimeType ?: "application/octet-stream").toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
            val response = try {
                service.uploadFile(filePart)
            } catch (http: HttpException) {
                if (http.code() == 403 || http.code() == 404) {
                    service.uploadFileLegacy(filePart)
                } else {
                    throw http
                }
            }
            val uploadedUrl = response.url ?: response.uri

            if (response.result == "success" && !uploadedUrl.isNullOrBlank()) {
                Result.success(
                    UploadedFile(
                        filename = response.filename?.ifBlank { fileName } ?: fileName,
                        url = uploadedUrl
                    )
                )
            } else {
                Result.failure(Exception(response.message.ifBlank { "Failed to upload file" }))
            }
        } catch (e: HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
            val apiMessage = Regex("\"msg\"\\s*:\\s*\"([^\"]+)\"")
                .find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace("\\\\\"", "\"")
                ?.trim()

            val hint = if (e.code() == 403) {
                "Upload zablokowany po stronie serwera (uprawnienia, limit albo polityka uploadu)."
            } else {
                null
            }
            val message = listOfNotNull(apiMessage, hint, "HTTP ${e.code()}").joinToString(" ").ifBlank { "Failed to upload file" }
            Result.failure(Exception(message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendMessage(type: String, to: String, content: String, topic: String?, displayName: String): Result<Long> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val response = service.sendMessage(
                type = type,
                to = to,
                content = content,
                topic = topic
            )

            if (response.result == "success") {
                val normalizedType = type.trim().lowercase()
                val messageId = response.messageId
                val senderDisplayName = auth.email.substringBefore('@').ifBlank { auth.email }
                messageDao.upsert(
                    MessageEntity(
                        id = messageId,
                        senderFullName = senderDisplayName,
                        senderEmail = auth.email,
                        content = content,
                        topic = topic.orEmpty(),
                        streamName = if (normalizedType == "stream") to else null,
                        timestampSeconds = System.currentTimeMillis() / 1000,
                        isRead = true,
                        isStarred = false,
                        isMentioned = false,
                        isWildcardMentioned = false,
                        reactionSummary = null,
                        avatarUrl = "",
                        messageType = normalizedType,
                        conversationKey = if (normalizedType == "private") DmConversationKey.fromRawTo(to, auth.email) else "",
                        dmDisplayName = if (normalizedType == "private") displayName.ifBlank { to } else ""
                    )
                )
                Result.success(response.messageId)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addReaction(
        messageId: Long,
        emojiName: String,
        emojiCode: String?,
        reactionType: String?
    ): Result<Unit> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val resolvedType = reactionType ?: "unicode_emoji"
            val candidateNames = if (resolvedType == "unicode_emoji") {
                unicodeReactionNameCandidates(emojiName, emojiCode)
            } else {
                listOf(emojiName)
            }

            var lastError: Throwable? = null
            for (candidateName in candidateNames) {
                try {
                    val response = service.addReaction(
                        messageId = messageId,
                        emojiName = candidateName,
                        emojiCode = emojiCode,
                        reactionType = resolvedType
                    )

                    if (response.result == "success") {
                        return Result.success(Unit)
                    }

                    lastError = Exception(response.message.ifBlank { "Failed to add reaction" })
                } catch (e: HttpException) {
                    lastError = Exception(extractApiErrorMessage(e), e)
                }
            }

            Result.failure(lastError ?: Exception("Failed to add reaction"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeReaction(
        messageId: Long,
        emojiName: String,
        emojiCode: String?,
        reactionType: String?
    ): Result<Unit> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val resolvedType = reactionType ?: "unicode_emoji"
            val candidateNames = if (resolvedType == "unicode_emoji") {
                unicodeReactionNameCandidates(emojiName, emojiCode)
            } else {
                listOf(emojiName)
            }

            var lastError: Throwable? = null
            for (candidateName in candidateNames) {
                try {
                    val response = service.removeReaction(
                        messageId = messageId,
                        emojiName = candidateName,
                        emojiCode = emojiCode,
                        reactionType = resolvedType
                    )

                    if (response.result == "success") {
                        return Result.success(Unit)
                    }

                    lastError = Exception(response.message.ifBlank { "Failed to remove reaction" })
                } catch (e: HttpException) {
                    lastError = Exception(extractApiErrorMessage(e), e)
                }
            }

            Result.failure(lastError ?: Exception("Failed to remove reaction"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun editMessage(
        messageId: Long,
        newContent: String,
        newTopic: String?,
        newStreamId: Long?
    ): Result<Unit> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val response = service.editMessage(
                messageId = messageId,
                content = newContent,
                topic = newTopic,
                streamId = newStreamId
            )

            if (response.result == "success") {
                messageDao.updateMessageContent(messageId, newContent)
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMessage(messageId: Long): Result<Unit> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val response = service.deleteMessage(messageId = messageId)

            if (response.result == "success") {
                messageDao.deleteMessage(messageId)
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchMessages(query: String): Result<List<MessageEntity>> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val narrowJson = "[{\"operator\":\"search\",\"operand\":\"${escapeJsonOperand(query)}\"}]"
            val response = service.getMessages(
                anchor = "newest",
                numBefore = 50,
                numAfter = 0,
                narrowJson = narrowJson,
                applyMarkdown = false
            )

            if (response.result == "success") {
                val mapped = response.messages.map { dto ->
                    MessageEntity(
                        id = dto.id,
                        senderFullName = dto.senderFullName.orEmpty().ifBlank { dto.senderEmail.orEmpty() },
                        senderEmail = dto.senderEmail.orEmpty(),
                        content = dto.content.orEmpty(),
                        topic = dto.subject.orEmpty(),
                        streamName = if (dto.type == "stream") dto.displayRecipient?.toString() else null,
                        timestampSeconds = dto.timestamp,
                        isRead = dto.flags?.contains("read") == true,
                        isStarred = dto.flags?.contains("starred") == true,
                        isMentioned = dto.flags?.contains("mentioned") == true,
                        isWildcardMentioned = dto.flags?.contains("wildcard_mentioned") == true,
                        reactionSummary = buildReactionSummary(dto.reactions),
                        avatarUrl = "",
                        messageType = dto.type.orEmpty(),
                        conversationKey = "",
                        dmDisplayName = ""
                    )
                }
                Result.success(mapped)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDirectMessageCandidates(): Result<List<DirectMessageCandidate>> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val response = service.getUsers()
            if (response.result != "success") {
                return Result.failure(Exception(response.message))
            }

            val users = response.members
                .asSequence()
                .filter { it.isActive }
                .filter { !it.email.equals(auth.email, ignoreCase = true) }
                .map {
                    DirectMessageCandidate(
                        userId = it.userId,
                        fullName = it.fullName,
                        email = it.email,
                        avatarUrl = resolveAvatarUrl(it.avatarUrl.orEmpty(), auth.serverUrl)
                    )
                }
                .sortedBy { it.fullName.lowercase() }
                .toList()

            directMessageCandidateDao.clearAll()
            directMessageCandidateDao.upsertAll(
                users.map {
                    DirectMessageCandidateEntity(
                        email = it.email,
                        userId = it.userId,
                        fullName = it.fullName,
                        avatarUrl = it.avatarUrl,
                        sortKey = it.fullName.lowercase()
                    )
                }
            )

            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPresence(): Result<Map<String, String>> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val response = service.getAllPresences()
            if (response.result == "success") {
                val presences = response.presences
                    ?.mapNotNull { entry ->
                        val normalizedEmail = entry.key.trim().lowercase()
                        if (normalizedEmail.isBlank()) return@mapNotNull null

                        val rawStatus = entry.value.aggregated?.status
                            ?: entry.value.website?.status
                        val normalizedStatus = when (rawStatus?.trim()?.lowercase()) {
                            "active", "online" -> "active"
                            "idle", "away" -> "idle"
                            else -> null
                        }

                        if (normalizedStatus != null) normalizedEmail to normalizedStatus else null
                    }
                    ?.toMap()
                    ?: emptyMap()

                Log.d(
                    TAG,
                    "getPresence success: raw_count=${response.presences?.size ?: 0}, mapped_count=${presences.size}"
                )
                Result.success(presences)
            } else {
                Log.w(TAG, "getPresence error result: ${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPresence exception", e)
            Result.failure(e)
        }
    }

    override suspend fun setOwnPresence(status: String): Result<Unit> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val normalizedStatus = when (status.trim().lowercase()) {
                "active", "online" -> "active"
                "idle", "away" -> "idle"
                else -> return Result.failure(Exception("Invalid presence status: $status"))
            }

            val response = service.setOwnPresence(status = normalizedStatus)
            if (response.result == "success") {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifBlank { "Failed to update presence" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUserId(): Result<Long> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val response = service.getMyProfile()
            if (response.result == "success" && response.userId != null) {
                Result.success(response.userId)
            } else {
                Result.failure(Exception(response.message.ifBlank { "Failed to fetch current user id" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun canModerateAllMessages(): Result<Boolean> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val response = service.getMyProfile()
            if (response.result == "success") {
                val roleBasedModeration = when (response.role) {
                    100, 200, 300 -> true
                    else -> false
                }
                Result.success(response.isAdmin || response.isOwner || response.isModerator || roleBasedModeration)
            } else {
                Result.failure(Exception(response.message.ifBlank { "Failed to fetch profile permissions" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCustomEmojis(): Result<List<CustomEmoji>> {
        return try {
            val auth = secureSessionStorage.getAuth() ?: return Result.failure(Exception("Not authenticated"))
            val service = zulipApiFactory.create(
                serverUrl = auth.serverUrl,
                credentials = BasicCredentials(auth.email, auth.apiKey)
            )

            val response = service.getRealmEmoji()
            if (response.result != "success") {
                return Result.failure(Exception(response.message.ifBlank { "Failed to load custom emoji" }))
            }

            val serverUrl = auth.serverUrl.trimEnd('/')
            val items = response.emoji.mapNotNull { (key, dto) ->
                val name = dto.name?.trim().takeIf { !it.isNullOrBlank() } ?: key.trim()
                val id = dto.id?.trim().takeIf { !it.isNullOrBlank() } ?: name
                val rawUrl = dto.sourceUrl?.trim().orEmpty()
                if (name.isBlank() || rawUrl.isBlank()) return@mapNotNull null
                val resolvedUrl = if (rawUrl.startsWith("http", ignoreCase = true)) rawUrl else "$serverUrl$rawUrl"
                CustomEmoji(
                    id = id,
                    name = name,
                    url = resolvedUrl,
                    isDeactivated = dto.deactivated == true
                )
            }

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
