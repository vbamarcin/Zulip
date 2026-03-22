package com.mkras.zulip.presentation.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkras.zulip.core.chat.DmConversationKey
import com.mkras.zulip.core.realtime.EventProcessor
import com.mkras.zulip.core.realtime.PresenceEvent
import com.mkras.zulip.core.realtime.TypingEvent
import com.mkras.zulip.core.security.SecureSessionStorage
import com.mkras.zulip.data.local.entity.MessageEntity
import com.mkras.zulip.domain.repository.ChatRepository
import com.mkras.zulip.domain.repository.DirectMessageCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val eventProcessor: EventProcessor,
    private val secureSessionStorage: SecureSessionStorage
) : ViewModel() {

    private companion object {
        const val MENTION_CANDIDATES_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        const val TAG = "PresenceDebug"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val serverUrl: String = secureSessionStorage.getAuth()?.serverUrl.orEmpty()
    private val currentUserEmail: String = secureSessionStorage.getAuth()?.email.orEmpty()
    private var typingTimeoutJob: Job? = null
    private var searchJob: Job? = null

    init {
        observeMentionCandidates()
        observeMessages()
        observeAllMessages()
        observeStarredMessages()
        observeTypingEvents()
        observePresenceEvents()
        ensureMentionCandidatesLoaded()
        loadModerationPermission()
        initialPresenceSync()
        resyncOnResume()
    }

    private fun initialPresenceSync() {
        viewModelScope.launch {
            chatRepository.getPresence()
                .onSuccess { presences ->
                    Log.d(TAG, "initialPresenceSync success: mapped_count=${presences.size}")
                    _uiState.update { it.copy(presenceByEmail = presences) }
                }
                .onFailure { error ->
                    Log.e(TAG, "initialPresenceSync failed", error)
                }
        }
    }

    private fun loadModerationPermission() {
        viewModelScope.launch {
            chatRepository.canModerateAllMessages()
                .onSuccess { canModerate ->
                    _uiState.update { it.copy(canModerateAllMessages = canModerate) }
                }
                .onFailure {
                    _uiState.update { it.copy(canModerateAllMessages = false) }
                }
        }
    }

    private fun resolveAvatarUrl(raw: String): String {
        if (raw.isBlank()) return ""
        return if (raw.startsWith("http")) raw else "${serverUrl.trimEnd('/')}$raw"
    }

    fun onMessagesRendered(ids: List<Long>) {
        val targetIds = ids.distinct()
        if (targetIds.isEmpty()) return

        viewModelScope.launch {
            chatRepository.markMessagesAsRead(targetIds)
        }
    }

    fun resyncOnResume() {
        loadModerationPermission()
        initialPresenceSync()
        viewModelScope.launch {
            try {
                chatRepository.resyncLatestMessages()
                _uiState.update { it.copy(resyncError = null) }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Failed to sync messages"
                _uiState.update { it.copy(resyncError = errorMsg) }
            }
            try {
                chatRepository.resyncStarredMessages()
            } catch (e: Exception) {
                _uiState.update { it.copy(resyncError = e.message ?: "Failed to sync starred") }
            }
        }
    }

    fun clearResyncError() {
        _uiState.update { it.copy(resyncError = null) }
    }

    fun selectConversation(key: String) {
        _uiState.update { it.copy(selectedConversationKey = key, selectedConversationTitle = null) }
    }

    fun backToConversationList() {
        _uiState.update {
            it.copy(
                selectedConversationKey = null,
                selectedConversationTitle = null,
                isNewDmPickerVisible = false,
                newDmQuery = "",
                newDmError = null
            )
        }
    }

    fun openNewDmPicker() {
        _uiState.update {
            it.copy(
                isNewDmPickerVisible = true,
                newDmQuery = "",
                newDmError = null
            )
        }
        ensureMentionCandidatesLoaded(forceRefresh = uiState.value.newDmPeople.isEmpty())
    }

    fun closeNewDmPicker() {
        _uiState.update {
            it.copy(
                isNewDmPickerVisible = false,
                newDmQuery = "",
                newDmError = null,
                pendingDirectMessageContent = null
            )
        }
    }

    fun updateNewDmQuery(query: String) {
        _uiState.update { it.copy(newDmQuery = query, newDmError = null) }
    }

    fun startDirectMessage(candidate: DirectMessageCandidate) {
        val targetEmail = candidate.email.trim().lowercase()
        val existingConversation = uiState.value.dmConversations.firstOrNull { conversation ->
            conversation.conversationKey
                .split(',')
                .map { it.trim().lowercase() }
                .any { it == targetEmail }
        }

        _uiState.update {
            it.copy(
                selectedConversationKey = existingConversation?.conversationKey ?: targetEmail,
                selectedConversationTitle = existingConversation?.displayName ?: candidate.fullName,
                isNewDmPickerVisible = false,
                newDmQuery = "",
                newDmError = null
            )
        }
    }

    fun forwardToNewDirectMessage(content: String) {
        _uiState.update {
            it.copy(
                selectedConversationKey = null,
                selectedConversationTitle = null,
                pendingDirectMessageContent = content,
                isNewDmPickerVisible = true,
                newDmQuery = "",
                newDmError = null
            )
        }
        ensureMentionCandidatesLoaded(forceRefresh = uiState.value.newDmPeople.isEmpty())
    }

    fun consumePendingDirectMessageContent() {
        _uiState.update { it.copy(pendingDirectMessageContent = null) }
    }

    fun ensureMentionCandidatesLoaded(forceRefresh: Boolean = false) {
        if (uiState.value.isNewDmLoading) return

        val hasCache = uiState.value.newDmPeople.isNotEmpty()
        val cacheAge = System.currentTimeMillis() - secureSessionStorage.getMentionCandidatesLastSyncTime()
        val isCacheFresh = cacheAge in 1 until MENTION_CANDIDATES_CACHE_TTL_MS

        if (!forceRefresh && hasCache && isCacheFresh) return

        loadDirectMessageCandidates(forceRefresh = forceRefresh)
    }

    fun openConversationFromMessage(message: MessageEntity) {
        val targetKey = message.conversationKey.ifBlank { message.senderEmail }
        val targetTitle = message.dmDisplayName.ifBlank { message.senderFullName }
        _uiState.update {
            it.copy(
                selectedConversationKey = targetKey,
                selectedConversationTitle = targetTitle,
                dmScrollToMessageId = message.id,
                isNewDmPickerVisible = false,
                newDmQuery = "",
                newDmError = null
            )
        }
    }

    fun openConversationFromNotification(
        conversationKey: String,
        conversationTitle: String?,
        messageId: Long
    ) {
        _uiState.update {
            it.copy(
                selectedConversationKey = conversationKey,
                selectedConversationTitle = conversationTitle,
                dmScrollToMessageId = messageId,
                isNewDmPickerVisible = false,
                newDmQuery = "",
                newDmError = null
            )
        }
    }

    fun clearDmScrollTarget() {
        _uiState.update { it.copy(dmScrollToMessageId = null) }
    }

    private fun loadDirectMessageCandidates(forceRefresh: Boolean = false) {
        _uiState.update { it.copy(isNewDmLoading = true, newDmError = null) }
        viewModelScope.launch {
            chatRepository.getDirectMessageCandidates()
                .onSuccess { users ->
                    secureSessionStorage.saveMentionCandidatesLastSyncTime(System.currentTimeMillis())
                    _uiState.update {
                        it.copy(
                            isNewDmLoading = false,
                            newDmPeople = users,
                            newDmError = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isNewDmLoading = false,
                            newDmError = if (forceRefresh || it.newDmPeople.isEmpty()) {
                                error.message ?: "Failed to load users"
                            } else null
                        )
                    }
                }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.observePrivateMessages().collect { messages ->
                val normalizedMessages = messages.map { message ->
                    val normalizedKey = DmConversationKey.fromStoredKey(message.conversationKey, message.senderEmail, currentUserEmail)
                    if (normalizedKey == message.conversationKey) message else message.copy(conversationKey = normalizedKey)
                }

                val conversations = normalizedMessages
                    .groupBy { it.conversationKey }
                    .map { (key, msgs) ->
                        val latest = msgs.maxByOrNull { it.timestampSeconds }
                        
                        // FIX: Look for a message from someone else to get their avatar
                        val peerMessage = msgs.find { !it.senderEmail.equals(currentUserEmail, ignoreCase = true) }
                        val avatarUrl = if (peerMessage != null) {
                            resolveAvatarUrl(peerMessage.avatarUrl.orEmpty())
                        } else {
                            resolveAvatarUrl(latest?.avatarUrl.orEmpty())
                        }

                        val isMuted = secureSessionStorage.isDirectMessageMuted(key)
                        val displayAvatar = if (avatarUrl.isBlank()) "https://zulip.com/static/images/avatar-default.png" else avatarUrl
                        DmConversation(
                            conversationKey = key,
                            senderEmail = latest?.senderEmail ?: "",
                            displayName = latest?.dmDisplayName?.ifBlank { latest.senderFullName } ?: key,
                            avatarUrl = displayAvatar,
                            unreadCount = msgs.count {
                                !it.isRead && !it.senderEmail.equals(currentUserEmail, ignoreCase = true) && !isMuted
                            },
                            latestTimestamp = latest?.timestampSeconds ?: 0L
                        )
                    }
                    .sortedByDescending { it.latestTimestamp }
                _uiState.update {
                    it.copy(privateMessages = normalizedMessages, dmConversations = conversations)
                }
            }
        }
    }

    private fun observeMentionCandidates() {
        viewModelScope.launch {
            chatRepository.observeDirectMessageCandidates().collect { users ->
                _uiState.update { state ->
                    if (state.newDmPeople == users) state else state.copy(newDmPeople = users)
                }
            }
        }
    }

    private fun observeAllMessages() {
        viewModelScope.launch {
            chatRepository.observeMessages().collect { messages ->
                _uiState.update {
                    it.copy(allMessages = messages.sortedByDescending { m -> m.timestampSeconds })
                }
            }
        }
    }

    private fun observeStarredMessages() {
        viewModelScope.launch {
            chatRepository.observeStarredMessages().collect { messages ->
                _uiState.update {
                    it.copy(starredMessages = messages.sortedByDescending { m -> m.timestampSeconds })
                }
            }
        }
    }

    private fun observePresenceEvents() {
        viewModelScope.launch {
            eventProcessor.presenceEvents.collect { event ->
                val email = event.email?.trim()?.lowercase()
                if (!email.isNullOrBlank()) {
                    _uiState.update { state ->
                        val updated = state.presenceByEmail.toMutableMap()
                        val normalizedStatus = when (event.status?.trim()?.lowercase()) {
                            "active", "online" -> "active"
                            "idle", "away" -> "idle"
                            else -> "offline"
                        }
                        if (normalizedStatus == "offline") updated.remove(email)
                        else updated[email] = normalizedStatus
                        Log.d(TAG, "presence update: email=$email, status=$normalizedStatus, total=${updated.size}")
                        state.copy(presenceByEmail = updated)
                    }
                }
            }
        }
    }

    private fun observeTypingEvents() {
        viewModelScope.launch {
            eventProcessor.typingEvents.collect { event ->
                handleTypingEvent(event)
            }
        }
    }

    private fun handleTypingEvent(event: TypingEvent) {
        val sender = event.senderEmail ?: return
        if (event.op == "start") {
            _uiState.update { it.copy(typingText = "$sender pisze...") }
            typingTimeoutJob?.cancel()
            typingTimeoutJob = viewModelScope.launch {
                delay(15_000)
                _uiState.update { state ->
                    if (state.typingText?.startsWith(sender) == true) state.copy(typingText = null)
                    else state
                }
            }
        } else if (event.op == "stop") {
            _uiState.update { state ->
                if (state.typingText?.startsWith(sender) == true) state.copy(typingText = null)
                else state
            }
        }
    }

    fun sendMessage(type: String, to: String, content: String, topic: String? = null, onSuccess: (Long) -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val displayName = if (type.trim().lowercase() == "private") {
                val emails = to.split(',').map { it.trim().lowercase() }.toSet()
                _uiState.value.newDmPeople
                    .filter { it.email.trim().lowercase() in emails }
                    .joinToString(", ") { it.fullName }
            } else ""
            chatRepository.sendMessage(type, to, content, topic, displayName)
                .onSuccess { messageId ->
                    _uiState.update { it.copy(sendMessageError = null) }
                    resyncOnResume()
                    onSuccess(messageId)
                }
                .onFailure { error ->
                    val errorMsg = error.message ?: "Failed to send message"
                    _uiState.update { it.copy(sendMessageError = errorMsg) }
                    onError(errorMsg)
                }
        }
    }

    fun clearSendMessageError() {
        _uiState.update { it.copy(sendMessageError = null) }
    }

    fun saveDmScrollPosition(key: String, index: Int) {
        _uiState.update { it.copy(dmScrollPosition = it.dmScrollPosition + (key to index)) }
    }

    fun getDmScrollPosition(key: String): Int {
        return _uiState.value.dmScrollPosition[key] ?: 0
    }

    fun uploadAttachmentAndSendMessage(
        type: String,
        to: String,
        topic: String? = null,
        attachment: PickedAttachment,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            chatRepository.uploadFile(
                fileName = attachment.fileName,
                mimeType = attachment.mimeType,
                bytes = attachment.bytes
            ).onSuccess { uploadedFile ->
                val content = "[${uploadedFile.filename.replace("[","(").replace("]",")")}](${uploadedFile.url})"
                val displayName = if (type.trim().lowercase() == "private") {
                    val emails = to.split(',').map { it.trim().lowercase() }.toSet()
                    _uiState.value.newDmPeople
                        .filter { it.email.trim().lowercase() in emails }
                        .joinToString(", ") { it.fullName }
                } else ""
                chatRepository.sendMessage(type, to, content, topic, displayName)
                    .onSuccess {
                        resyncOnResume()
                        onSuccess()
                    }
                    .onFailure { error -> onError(error.message ?: "Failed to send attachment") }
            }.onFailure { error -> onError(error.message ?: "Failed to upload attachment") }
        }
    }

    fun addReaction(messageId: Long, emojiName: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            chatRepository.addReaction(messageId, emojiName)
                .onSuccess { onSuccess() }
                .onFailure { error -> onError(error.message ?: "Failed to add reaction") }
        }
    }

    fun removeReaction(messageId: Long, emojiName: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            chatRepository.removeReaction(messageId, emojiName)
                .onSuccess { onSuccess() }
                .onFailure { error -> onError(error.message ?: "Failed to remove reaction") }
        }
    }

    fun editMessage(
        messageId: Long,
        newContent: String,
        newTopic: String? = null,
        newStreamId: Long? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            chatRepository.editMessage(messageId, newContent, newTopic, newStreamId)
                .onSuccess {
                    resyncOnResume()
                    onSuccess()
                }
                .onFailure { error -> onError(error.message ?: "Failed to edit message") }
        }
    }

    fun deleteMessage(messageId: Long, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
                .onSuccess {
                    resyncOnResume()
                    onSuccess()
                }
                .onFailure { error -> onError(error.message ?: "Failed to delete message") }
        }
    }

    fun searchMessages(query: String, onSuccess: (List<MessageEntity>) -> Unit = {}, onError: (String) -> Unit = {}) {
        performSearch(query = query, useDebounce = false, onSuccess = onSuccess, onError = onError)
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, searchError = null) }
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            searchJob?.cancel()
            _uiState.update { it.copy(searchResults = emptyList(), searchError = null, isSearching = false) }
            return
        }
        val localResults = localSearch(trimmed)
        _uiState.update { it.copy(searchResults = localResults, searchError = null, isSearching = trimmed.length >= 2) }
        if (trimmed.length < 2) {
            searchJob?.cancel()
            return
        }
        performSearch(query = trimmed, useDebounce = true)
    }

    fun submitSearch() {
        val query = uiState.value.searchQuery.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), searchError = null, isSearching = false) }
            return
        }
        performSearch(query = query, useDebounce = false)
    }

    private fun performSearch(
        query: String,
        useDebounce: Boolean,
        onSuccess: (List<MessageEntity>) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val localResults = localSearch(trimmed)
        _uiState.update { it.copy(searchQuery = trimmed, searchResults = localResults, searchError = null, isSearching = true) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (useDebounce) delay(350)
            if (uiState.value.searchQuery.trim() != trimmed) return@launch
            chatRepository.searchMessages(trimmed)
                .onSuccess { remoteResults ->
                    if (uiState.value.searchQuery.trim() != trimmed) return@onSuccess
                    val merged = (remoteResults + localResults).distinctBy { it.id }.sortedByDescending { it.timestampSeconds }
                    _uiState.update { it.copy(isSearching = false, searchResults = merged, searchError = null, searchQuery = trimmed) }
                    onSuccess(merged)
                }
                .onFailure { error ->
                    if (uiState.value.searchQuery.trim() != trimmed) return@onFailure
                    _uiState.update { it.copy(isSearching = false, searchError = error.message ?: "Failed to search", searchResults = localResults, searchQuery = trimmed) }
                    onError(error.message ?: "Failed to search")
                }
        }
    }

    private fun localSearch(query: String): List<MessageEntity> {
        val q = query.lowercase()
        return uiState.value.allMessages.asSequence().filter { message ->
            message.senderFullName.lowercase().contains(q) || message.content.lowercase().contains(q) || message.topic.lowercase().contains(q) || (message.streamName?.lowercase()?.contains(q) == true) || message.dmDisplayName.lowercase().contains(q)
        }.sortedByDescending { it.timestampSeconds }.take(80).toList()
    }
}

data class DmConversation(
    val conversationKey: String,
    val senderEmail: String,
    val displayName: String,
    val avatarUrl: String,
    val unreadCount: Int,
    val latestTimestamp: Long
)

@Immutable
data class ChatUiState(
    val privateMessages: List<MessageEntity> = emptyList(),
    val allMessages: List<MessageEntity> = emptyList(),
    val dmConversations: List<DmConversation> = emptyList(),
    val selectedConversationKey: String? = null,
    val selectedConversationTitle: String? = null,
    val typingText: String? = null,
    val searchQuery: String = "",
    val searchResults: List<MessageEntity> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val isNewDmPickerVisible: Boolean = false,
    val isNewDmLoading: Boolean = false,
    val newDmPeople: List<DirectMessageCandidate> = emptyList(),
    val newDmQuery: String = "",
    val newDmError: String? = null,
    val sendMessageError: String? = null,
    val resyncError: String? = null,
    val pendingDirectMessageContent: String? = null,
    val dmScrollToMessageId: Long? = null,
    val canModerateAllMessages: Boolean = false,
    val presenceByEmail: Map<String, String> = emptyMap(),
    val starredMessages: List<MessageEntity> = emptyList(),
    val dmScrollPosition: Map<String, Int> = emptyMap()
)
