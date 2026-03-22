package com.mkras.zulip.presentation.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkras.zulip.data.local.entity.MessageEntity
import com.mkras.zulip.data.local.entity.StreamEntity
import com.mkras.zulip.data.local.entity.TopicEntity
import com.mkras.zulip.domain.repository.ChannelsRepository
import com.mkras.zulip.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val channelsRepository: ChannelsRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelsUiState())
    val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    private var topicsJob: Job? = null
    private var narrowRealtimeJob: Job? = null

    init {
        observeStreams()
        onChannelsVisible()
    }

    fun onChannelsVisible() {
        refreshStreams()
    }

    fun onStreamSelected(stream: StreamEntity) {
        _uiState.update {
            it.copy(
                selectedStream = stream,
                selectedTopic = null,
                messages = emptyList(),
                isLoadingOlderMessages = false,
                hasMoreOlderMessages = true,
                lastOlderAnchorMessageId = null
            )
        }
        observeTopics(stream.id)
        refreshTopics(stream.id)
    }

    fun onTopicSelected(topic: TopicEntity) {
        val stream = uiState.value.selectedStream ?: return
        _uiState.update {
            it.copy(
                selectedTopic = topic,
                isLoadingOlderMessages = false,
                hasMoreOlderMessages = false,
                lastOlderAnchorMessageId = null
            )
        }
        startNarrowRealtimeSync(stream.name, topic.name)
    }

    fun backToStreams() {
        _uiState.update {
            it.copy(
                selectedStream = null,
                selectedTopic = null,
                topics = emptyList(),
                messages = emptyList(),
                isLoadingOlderMessages = false,
                hasMoreOlderMessages = true,
                lastOlderAnchorMessageId = null
            )
        }
        topicsJob?.cancel()
        narrowRealtimeJob?.cancel()
    }

    fun backToTopics() {
        _uiState.update {
            it.copy(
                selectedTopic = null,
                messages = emptyList(),
                isLoadingOlderMessages = false,
                hasMoreOlderMessages = true,
                lastOlderAnchorMessageId = null
            )
        }
        narrowRealtimeJob?.cancel()
    }

    fun clearScrollTarget() {
        _uiState.update { it.copy(scrollToMessageId = null) }
    }

    fun openFromAllMessages(streamName: String, topicName: String, messageId: Long? = null) {
        val stream = uiState.value.streams.firstOrNull { it.name.equals(streamName, ignoreCase = true) }
        if (stream == null) {
            _uiState.update {
                it.copy(statusMessage = "Nie znaleziono kanału '$streamName' na liście.")
            }
            return
        }

        val topic = TopicEntity(
            key = "${stream.id}::$topicName",
            streamId = stream.id,
            name = topicName,
            maxMessageId = 0L
        )

        _uiState.update {
            it.copy(
                selectedStream = stream,
                selectedTopic = topic,
                statusMessage = null,
                scrollToMessageId = messageId,
                isLoadingOlderMessages = false,
                hasMoreOlderMessages = false,
                lastOlderAnchorMessageId = null
            )
        }

        observeTopics(stream.id)
        viewModelScope.launch { channelsRepository.refreshTopics(stream.id) }
        startNarrowRealtimeSync(stream.name, topic.name)
    }

    fun refreshSelectedNarrow() {
        val stream = uiState.value.selectedStream ?: return
        val topic = uiState.value.selectedTopic ?: return
        viewModelScope.launch {
            chatRepository.fetchNarrowMessagesOnline(stream.name, topic.name)
                .onSuccess { messages ->
                    _uiState.update {
                        it.copy(
                            messages = messages,
                            statusMessage = null,
                            hasMoreOlderMessages = false,
                            isLoadingOlderMessages = false,
                            lastOlderAnchorMessageId = null
                        )
                    }
                }
        }
    }

    private fun startNarrowRealtimeSync(streamName: String, topicName: String) {
        narrowRealtimeJob?.cancel()
        narrowRealtimeJob = viewModelScope.launch {
            // Online-first: keep selected thread synced from server in short intervals.
            chatRepository.fetchNarrowMessagesOnline(streamName, topicName)
                .onSuccess { messages ->
                    _uiState.update {
                        it.copy(
                            messages = messages,
                            statusMessage = null,
                            hasMoreOlderMessages = false,
                            isLoadingOlderMessages = false,
                            lastOlderAnchorMessageId = null
                        )
                    }
                }
            while (true) {
                delay(2500)
                chatRepository.fetchNarrowMessagesOnline(streamName, topicName)
                    .onSuccess { messages ->
                        _uiState.update { current ->
                            // Preserve hasMoreOlderMessages and pagination state so that
                            // loadOlderMessagesForSelectedTopic results are not overwritten.
                            current.copy(
                                messages = if (current.hasMoreOlderMessages) {
                                    // Merge: keep older pages at front, update newest tail
                                    val existingOlderIds = current.messages.map { it.id }.toSet()
                                    val newOnly = messages.filter { it.id !in existingOlderIds }
                                    current.messages + newOnly
                                } else {
                                    messages
                                },
                                statusMessage = null,
                                isLoadingOlderMessages = false
                            )
                        }
                    }
            }
        }
    }

    fun loadTopicsForStream(streamId: Long, onLoaded: (List<String>) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                channelsRepository.refreshTopics(streamId)
                channelsRepository.observeTopics(streamId).first()
                    .map { it.name.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
            }.onSuccess { topicNames ->
                onLoaded(topicNames)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(statusMessage = throwable.message ?: "Nie udalo sie pobrac tematow.")
                }
                onLoaded(emptyList())
            }
        }
    }

    fun loadOlderMessagesForSelectedTopic() {
        val state = uiState.value
        val stream = state.selectedStream ?: return
        val topic = state.selectedTopic ?: return
        val oldestMessageId = state.messages.firstOrNull()?.id ?: return

        if (state.isLoadingOlderMessages || !state.hasMoreOlderMessages) return
        if (state.lastOlderAnchorMessageId == oldestMessageId) return

        _uiState.update {
            it.copy(
                isLoadingOlderMessages = true,
                lastOlderAnchorMessageId = oldestMessageId
            )
        }

        viewModelScope.launch {
            val pageSize = 100
            val result = chatRepository.loadOlderNarrowMessages(
                streamName = stream.name,
                topicName = topic.name,
                anchorMessageId = oldestMessageId,
                pageSize = pageSize
            )

            result.onSuccess { fetchedCount ->
                _uiState.update {
                    it.copy(
                        isLoadingOlderMessages = false,
                        hasMoreOlderMessages = fetchedCount >= pageSize
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoadingOlderMessages = false,
                        statusMessage = it.statusMessage ?: "Nie udalo sie pobrac starszych wiadomosci."
                    )
                }
            }
        }
    }

    private fun observeStreams() {
        viewModelScope.launch {
            channelsRepository.observeStreams().collect { streams ->
                _uiState.update { state ->
                    val selected = state.selectedStream
                    if (selected != null && streams.none { it.id == selected.id }) {
                        state.copy(
                            streams = streams,
                            selectedStream = null,
                            selectedTopic = null,
                            topics = emptyList(),
                            messages = emptyList(),
                            scrollToMessageId = null,
                            statusMessage = null,
                            isLoadingOlderMessages = false,
                            hasMoreOlderMessages = true,
                            lastOlderAnchorMessageId = null
                        )
                    } else {
                        state.copy(streams = streams)
                    }
                }
            }
        }
    }

    private fun observeTopics(streamId: Long) {
        topicsJob?.cancel()
        topicsJob = viewModelScope.launch {
            channelsRepository.observeTopics(streamId).collect { topics ->
                _uiState.update { it.copy(topics = topics) }
            }
        }
    }

    private fun refreshStreams() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, statusMessage = null) }
            runCatching {
                channelsRepository.refreshStreams()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(statusMessage = throwable.message ?: "Nie udalo sie pobrac streamow.")
                }
            }
            _uiState.update { state ->
                val fallback = if (state.streams.isEmpty() && state.statusMessage == null) {
                    "Brak streamow z API. Kliknij Odswiez albo sprawdz uprawnienia konta."
                } else {
                    state.statusMessage
                }
                state.copy(isRefreshing = false, statusMessage = fallback)
            }
        }
    }

    private fun refreshTopics(streamId: Long) {
        viewModelScope.launch {
            runCatching {
                channelsRepository.refreshTopics(streamId)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(statusMessage = throwable.message ?: "Nie udalo sie pobrac tematow.")
                }
            }
        }
    }
}

@Immutable
data class ChannelsUiState(
    val streams: List<StreamEntity> = emptyList(),
    val topics: List<TopicEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val selectedStream: StreamEntity? = null,
    val selectedTopic: TopicEntity? = null,
    val isRefreshing: Boolean = false,
    val statusMessage: String? = null,
    val scrollToMessageId: Long? = null,
    val isLoadingOlderMessages: Boolean = false,
    val hasMoreOlderMessages: Boolean = true,
    val lastOlderAnchorMessageId: Long? = null
)
