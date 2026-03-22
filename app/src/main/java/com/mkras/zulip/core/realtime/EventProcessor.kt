package com.mkras.zulip.core.realtime

import android.util.Log
import com.mkras.zulip.core.security.SecureSessionStorage
import com.mkras.zulip.data.local.db.MessageDao
import com.mkras.zulip.data.local.db.StreamDao
import com.mkras.zulip.data.local.entity.MessageEntity
import com.mkras.zulip.data.remote.dto.EventDto
import com.mkras.zulip.data.remote.dto.EventMessageDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class EventProcessor @Inject constructor(
    private val notificationHelper: ZulipNotificationHelper,
    private val messageDao: MessageDao,
    private val streamDao: StreamDao,
    private val secureSessionStorage: SecureSessionStorage
) {

    private companion object {
        const val TAG = "PresenceDebug"
    }

    private val _typingEvents = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 64)
    val typingEvents: SharedFlow<TypingEvent> = _typingEvents.asSharedFlow()

    private val _presenceEvents = MutableSharedFlow<PresenceEvent>(extraBufferCapacity = 64)
    val presenceEvents: SharedFlow<PresenceEvent> = _presenceEvents.asSharedFlow()

    suspend fun process(
        event: EventDto,
        dmNotificationsEnabled: Boolean,
        channelNotificationsEnabled: Boolean,
        selfEmail: String,
        serverUrl: String = ""
    ) {
        when (event.type) {
            "message" -> processMessageEvent(
                message = event.message,
                dmNotificationsEnabled = dmNotificationsEnabled,
                channelNotificationsEnabled = channelNotificationsEnabled,
                selfEmail = selfEmail,
                serverUrl = serverUrl
            )
            "update_message" -> processUpdateMessageEvent(event)
            "delete_message" -> processDeleteMessageEvent(event)
            "update_message_flags" -> processMessageFlagsEvent(event)
            "reaction" -> processReactionEvent(event)
            "typing" -> {
                _typingEvents.emit(
                    TypingEvent(
                        op = event.op.orEmpty(),
                        senderEmail = event.senderEmail,
                        senderId = event.senderId,
                        messageId = event.messageId
                    )
                )
            }
            "presence" -> {
                val statusFromPresenceObject = event.presence
                    ?.let { presence ->
                        presence["aggregated"]?.status
                            ?: presence.values.firstNotNullOfOrNull { it.status }
                    }
                val finalStatus = event.presenceStatus ?: statusFromPresenceObject
                Log.d(
                    TAG,
                    "presence event: email=${event.email ?: event.senderEmail}, status=$finalStatus"
                )
                _presenceEvents.emit(
                    PresenceEvent(
                        userId = event.userId,
                        email = event.email ?: event.senderEmail,
                        status = finalStatus
                    )
                )
            }
        }
    }

    private fun parsePrivateRecipients(displayRecipient: Any?): List<Map<*, *>> {
        return (displayRecipient as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            .orEmpty()
    }

    private fun buildConversationKey(recipients: List<Map<*, *>>, fallbackEmail: String, selfEmail: String): String {
        val recipientEmails = recipients
            .mapNotNull { it["email"] as? String }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val participants = if (recipientEmails.isEmpty()) {
            listOf(fallbackEmail)
        } else {
            recipientEmails
        }

        val withoutSelf = participants.filterNot { it.equals(selfEmail, ignoreCase = true) }
        val normalized = (if (withoutSelf.isNotEmpty()) withoutSelf else participants)
            .map { it.lowercase() }
            .distinct()
            .sorted()

        return normalized.joinToString(",")
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

    private suspend fun processMessageEvent(
        message: EventMessageDto?,
        dmNotificationsEnabled: Boolean,
        channelNotificationsEnabled: Boolean,
        selfEmail: String,
        serverUrl: String
    ) {
        if (message == null) {
            return
        }

        val senderEmail = message.senderEmail.orEmpty()
        val isOwnMessage = senderEmail.equals(selfEmail, ignoreCase = true)
        if (isOwnMessage) {
            return
        }

        val isPrivate = message.type == "private"
        val recipients = if (isPrivate) parsePrivateRecipients(message.displayRecipient) else emptyList()
        val conversationKey = if (isPrivate) buildConversationKey(recipients, message.senderEmail.orEmpty(), selfEmail) else ""
        val streamName = if (!isPrivate) message.displayRecipient?.toString().orEmpty().trim() else ""
        val isRead = message.flags?.contains("read") == true

        val isMutedDirectMessage = isPrivate && secureSessionStorage.isDirectMessageMuted(conversationKey)
        val isMutedStream = !isPrivate && secureSessionStorage.isChannelMuted(streamName)
        val isMentioned = message.flags?.contains("mentioned") == true ||
            message.flags?.contains("wildcard_mentioned") == true
        val shouldNotify = if (isPrivate) {
            !isRead && dmNotificationsEnabled && !isMutedDirectMessage
        } else {
            !isRead && !isMutedStream && channelNotificationsEnabled && (message.type == "stream" || isMentioned)
        }

        val msgType = message.type.orEmpty()
        messageDao.upsert(
            MessageEntity(
                id = message.id,
                senderFullName = message.senderFullName.orEmpty().ifBlank { message.senderEmail.orEmpty() },
                senderEmail = senderEmail,
                content = message.content.orEmpty(),
                topic = message.subject.orEmpty(),
                streamName = if (msgType == "stream") message.displayRecipient?.toString() else null,
                timestampSeconds = message.timestamp ?: System.currentTimeMillis() / 1000,
                isRead = isRead,
                isStarred = message.flags?.contains("starred") == true,
                isMentioned = message.flags?.contains("mentioned") == true,
                isWildcardMentioned = message.flags?.contains("wildcard_mentioned") == true,
                reactionSummary = null,
                avatarUrl = resolveAvatarUrl(message.avatarUrl.orEmpty(), serverUrl),
                messageType = msgType,
                conversationKey = if (msgType == "private") conversationKey else "",
                dmDisplayName = if (msgType == "private") buildDmDisplayName(recipients, selfEmail, message.senderFullName.orEmpty()) else ""
            )
        )

        if (shouldNotify) {
            runCatching {
                notificationHelper.showMessageNotification(message, selfEmail)
            }
        }
    }

    private suspend fun processUpdateMessageEvent(event: EventDto) {
        val targetIds = event.messageIds.orEmpty()
            .ifEmpty { listOfNotNull(event.messageId) }
            .distinct()
        if (targetIds.isEmpty()) {
            return
        }

        event.content?.let { updatedContent ->
            val isRead = event.flags?.contains("read")
            if (isRead != null) {
                messageDao.updateMessageContentAndReadState(
                    messageId = event.messageId ?: targetIds.first(),
                    content = updatedContent,
                    isRead = isRead
                )
            } else {
                messageDao.updateMessageContent(
                    messageId = event.messageId ?: targetIds.first(),
                    content = updatedContent
                )
            }
        }

        val updatedTopic = event.subject
        val targetStreamName = event.newStreamId?.let { streamDao.getNameById(it) }

        when {
            updatedTopic != null && targetStreamName != null -> {
                messageDao.updateStreamAndTopic(
                    ids = targetIds,
                    streamName = targetStreamName,
                    topic = updatedTopic
                )
            }

            updatedTopic != null -> {
                messageDao.updateTopics(
                    ids = targetIds,
                    topic = updatedTopic
                )
            }
        }
    }

    private suspend fun processDeleteMessageEvent(event: EventDto) {
        val targetIds = event.messageIds.orEmpty()
            .ifEmpty { listOfNotNull(event.messageId) }
            .distinct()
        if (targetIds.isEmpty()) {
            return
        }

        messageDao.deleteMessages(targetIds)
        notificationHelper.onMessagesDeleted(targetIds)
    }

    private suspend fun processMessageFlagsEvent(event: EventDto) {
        val ids = event.messages ?: return
        val isEnabled = event.op == "add"
        when (event.flag) {
            "read" -> {
                messageDao.updateReadFlags(ids = ids, isRead = isEnabled)
                if (isEnabled) {
                    notificationHelper.onMessagesRead(ids)
                }
            }
            "starred" -> messageDao.updateStarredFlags(ids = ids, isStarred = isEnabled)
            "mentioned" -> messageDao.updateMentionedFlags(ids = ids, isMentioned = isEnabled)
            "wildcard_mentioned" -> messageDao.updateWildcardMentionedFlags(ids = ids, isWildcardMentioned = isEnabled)
        }
    }

    private suspend fun processReactionEvent(event: EventDto) {
        val messageId = event.messageId ?: return
        val emojiName = event.emojiName?.takeIf { it.isNotBlank() } ?: return
        val op = event.op ?: return

        val existing = messageDao.getReactionSummary(messageId)
        val reactions = if (existing.isNullOrBlank()) {
            mutableListOf()
        } else {
            existing.split("|").filter { it.isNotBlank() }.toMutableList()
        }

        if (op == "add") {
            if (!reactions.contains(emojiName)) {
                reactions.add(emojiName)
            }
        } else if (op == "remove") {
            reactions.remove(emojiName)
        }

        messageDao.updateReactionSummary(
            messageId = messageId,
            summary = reactions.joinToString("|").ifBlank { null }
        )
    }
}

data class TypingEvent(
    val op: String,
    val senderEmail: String?,
    val senderId: Long?,
    val messageId: Long?
)

data class PresenceEvent(
    val userId: Long?,
    val email: String?,
    val status: String?
)
