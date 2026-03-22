package com.mkras.zulip.presentation.channels

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.request.ImageRequest
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import com.mkras.zulip.data.local.entity.StreamEntity
import com.mkras.zulip.data.local.entity.TopicEntity
import com.mkras.zulip.domain.repository.DirectMessageCandidate
import com.mkras.zulip.presentation.chat.EmojiPickerDialog
import com.mkras.zulip.presentation.chat.MessageComposeInput
import com.mkras.zulip.presentation.chat.UploadAttachmentMessage
import com.mkras.zulip.presentation.chat.readPickedAttachment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.DoNotDisturb
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val PanelCard = Color(0xFF1A2B44)
private val ErrorCard = Color(0xFF3A1E2B)
private val StrongText = Color(0xFFEAF2FF)
private val BodyText = Color(0xFFB8CAE4)
private val Accent = Color(0xFF8CD9FF)
private val ErrorText = Color(0xFFFFDDE6)

@Composable
fun ChannelsScreen(
    uiState: ChannelsUiState,
    onStreamSelected: (StreamEntity) -> Unit,
    onTopicSelected: (TopicEntity) -> Unit,
    onBackToStreams: () -> Unit,
    onBackToTopics: () -> Unit,
    onRetry: () -> Unit,
    compactMode: Boolean,
    markdownEnabled: Boolean,
    currentUserEmail: String = "",
    serverUrl: String,
    imageAuthHeader: String? = null,
    onUploadAttachmentMessage: UploadAttachmentMessage = { _, _, _, _, onSuccess, _ -> onSuccess() },
    onSendChannelMessage: (String, String, String) -> Unit = { _, _, _ -> },
    onAddReaction: (Long, String) -> Unit = { _, _ -> },
    onEditMessage: (Long) -> Unit = { _ -> },
    onDeleteMessage: (Long, () -> Unit, (String) -> Unit) -> Unit = { _, onSuccess, _ -> onSuccess() },
    onMoveMessageToTopic: (Long, String, String, Long?, () -> Unit, (String) -> Unit) -> Unit =
        { _, _, _, _, onSuccess, _ -> onSuccess() },
    onLoadTopicsForStream: (Long, (List<String>) -> Unit) -> Unit = { _, _ -> },
    onMessagesRendered: (List<Long>) -> Unit = {},
    onScrollConsumed: () -> Unit = {},
    onLoadOlderMessages: () -> Unit = {},
    onRefreshLatestMessages: () -> Unit = {},
    unreadByStream: Map<String, Int> = emptyMap(),
    unreadByTopic: Map<String, Int> = emptyMap(),
    mentionCandidates: List<DirectMessageCandidate> = emptyList(),
    onRequestMentionCandidates: () -> Unit = {},
    isChannelMuted: (String) -> Boolean = { false },
    onSetChannelMuted: (String, Boolean) -> Unit = { _, _ -> },
    isChannelDisabled: (String) -> Boolean = { false },
    onSetChannelDisabled: (String, Boolean) -> Unit = { _, _ -> },
    canModerateAllMessages: Boolean = false,
    onForwardToDm: (String) -> Unit = {}
) {
    val sectionGap = if (compactMode) 6.dp else 8.dp
    val cardPadding = if (compactMode) 10.dp else 12.dp
    val bodyStyle = MaterialTheme.typography.bodySmall
    val titleStyle = MaterialTheme.typography.titleSmall
    val backStyle = if (compactMode) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    var isSearchDialogOpen by remember { mutableStateOf(false) }
    var streamSearchQuery by remember { mutableStateOf("") }
    val topicCandidates = remember(uiState.topics, uiState.selectedTopic?.name) {
        buildList {
            uiState.selectedTopic?.name
                ?.takeIf { it.isNotBlank() }
                ?.let { add(it) }
            uiState.topics
                .map { it.name }
                .filter { it.isNotBlank() }
                .forEach { topicName ->
                    if (!contains(topicName)) {
                        add(topicName)
                    }
                }
        }
    }
    LaunchedEffect(uiState.selectedTopic?.key, uiState.messages.size) {
        if (uiState.selectedTopic != null && uiState.messages.isNotEmpty()) {
            val recent = uiState.messages.takeLast(40).map { it.id }
            onMessagesRendered(recent)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(sectionGap)) {
            uiState.statusMessage?.let { status ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ErrorCard)
                ) {
                    Column(modifier = Modifier.padding(if (compactMode) 8.dp else 10.dp)) {
                        Text(status, color = ErrorText, style = bodyStyle)
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = if (compactMode) 6.dp else 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = Color(0xFF0B1220)
                            )
                        ) {
                            Text("Odśwież", fontWeight = FontWeight.SemiBold, style = backStyle)
                        }
                    }
                }
            }

            when {
                uiState.selectedStream == null -> StreamsList(
                    uiState = uiState,
                    onStreamSelected = onStreamSelected,
                    sectionGap = sectionGap,
                    cardPadding = cardPadding,
                    titleStyle = titleStyle,
                    bodyStyle = bodyStyle,
                    compactMode = compactMode,
                    markdownEnabled = markdownEnabled,
                    unreadByStream = unreadByStream,
                    searchQuery = streamSearchQuery,
                    isChannelDisabled = isChannelDisabled,
                    onSetChannelDisabled = onSetChannelDisabled
                )
                uiState.selectedTopic == null -> TopicsList(uiState, onTopicSelected, onBackToStreams, sectionGap, cardPadding, titleStyle, bodyStyle, backStyle, unreadByTopic)
                else -> NarrowMessages(
                    uiState = uiState,
                    onBackToTopics = onBackToTopics,
                    sectionGap = sectionGap,
                    cardPadding = cardPadding,
                    titleStyle = titleStyle,
                    bodyStyle = bodyStyle,
                    backStyle = backStyle,
                    compactMode = compactMode,
                    markdownEnabled = markdownEnabled,
                    currentUserEmail = currentUserEmail,
                    serverUrl = serverUrl,
                    imageAuthHeader = imageAuthHeader,
                    onUploadAttachmentMessage = onUploadAttachmentMessage,
                    onSendChannelMessage = onSendChannelMessage,
                    onAddReaction = onAddReaction,
                    onEditMessage = onEditMessage,
                    onDeleteMessage = onDeleteMessage,
                    onMoveMessageToTopic = onMoveMessageToTopic,
                    onLoadTopicsForStream = onLoadTopicsForStream,
                    onScrollConsumed = onScrollConsumed,
                    onLoadOlderMessages = onLoadOlderMessages,
                    onRefreshLatestMessages = onRefreshLatestMessages,
                    mentionCandidates = mentionCandidates,
                    onRequestMentionCandidates = onRequestMentionCandidates,
                    topicCandidates = topicCandidates,
                    isChannelMuted = isChannelMuted,
                    onSetChannelMuted = onSetChannelMuted,
                    canModerateAllMessages = canModerateAllMessages,
                    onForwardToDm = onForwardToDm
                )
            }
        }

        if (uiState.selectedStream == null) {
            FloatingActionButton(
                onClick = { isSearchDialogOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = Color(0xFF2B5A83),
                contentColor = Color(0xFFEAF2FF)
            ) {
                Icon(Icons.Rounded.Search, contentDescription = "Szukaj kanałów")
            }
        }
    }

    if (isSearchDialogOpen) {
        AlertDialog(
            onDismissRequest = { isSearchDialogOpen = false },
            title = { Text("Wyszukiwarka kanałów") },
            text = {
                OutlinedTextField(
                    value = streamSearchQuery,
                    onValueChange = { streamSearchQuery = it },
                    singleLine = true,
                    label = { Text("Nazwa lub opis kanału") }
                )
            },
            confirmButton = {
                Button(onClick = { isSearchDialogOpen = false }) {
                    Text("Zamknij")
                }
            },
            dismissButton = {
                Button(onClick = { streamSearchQuery = "" }) {
                    Text("Wyczyść")
                }
            }
        )
    }
}

@Composable
private fun StreamsList(
    uiState: ChannelsUiState,
    onStreamSelected: (StreamEntity) -> Unit,
    sectionGap: androidx.compose.ui.unit.Dp,
    cardPadding: androidx.compose.ui.unit.Dp,
    titleStyle: androidx.compose.ui.text.TextStyle,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    compactMode: Boolean,
    markdownEnabled: Boolean,
    unreadByStream: Map<String, Int>,
    searchQuery: String = "",
    isChannelDisabled: (String) -> Boolean = { false },
    onSetChannelDisabled: (String, Boolean) -> Unit = { _, _ -> }
) {
    if (uiState.streams.isEmpty()) {
        EmptyState(
            title = "Brak streamow",
            message = "Nie pobralismy jeszcze listy kanalow z serwera Zulip. Sprawdz polaczenie i zalogowanie."
        )
        return
    }

    val sortedStreams = remember(uiState.streams, unreadByStream) {
        uiState.streams.sortedWith(
            compareByDescending<StreamEntity> { unreadByStream[it.name] ?: 0 }
                .thenBy { it.name.lowercase() }
        )
    }

    val enabledSortedStreams = remember(sortedStreams) {
        sortedStreams.filter { stream -> !isChannelDisabled(stream.name) }
    }

    val subscribedStreams = remember(enabledSortedStreams) {
        enabledSortedStreams.filter { it.subscribed }
    }

    val baseStreams = remember(enabledSortedStreams, subscribedStreams) {
        // Prefer subscribed channels; fallback to visible channels if server does not return subscription flags.
        if (subscribedStreams.isNotEmpty()) subscribedStreams else enabledSortedStreams
    }

    val normalizedQuery = remember(searchQuery) { searchQuery.trim().lowercase() }
    val visibleStreams = remember(baseStreams, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            baseStreams
        } else {
            baseStreams.filter { stream ->
                stream.name.lowercase().contains(normalizedQuery) ||
                    stream.description.lowercase().contains(normalizedQuery)
            }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    if (visibleStreams.isEmpty() && normalizedQuery.isNotBlank()) {
        EmptyState(
            title = "Brak wyników",
            message = "Nie znaleziono kanałów pasujących do: \"$searchQuery\""
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(sectionGap)) {
            items(visibleStreams, key = { it.id }) { stream ->
                val unreadCount = unreadByStream[stream.name] ?: 0
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStreamSelected(stream) },
                    colors = CardDefaults.cardColors(containerColor = PanelCard)
                ) {
                    Column(modifier = Modifier.padding(cardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stream.name, fontWeight = FontWeight.SemiBold, color = StrongText, style = titleStyle, modifier = Modifier.weight(1f))
                            if (unreadCount > 0) {
                                Badge(containerColor = Color(0xFF8A3B2E)) {
                                    Text(unreadCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            IconButton(
                                onClick = {
                                    onSetChannelDisabled(stream.name, true)
                                    coroutineScope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Kanał wyłączony z listy",
                                            actionLabel = "Cofnij",
                                            withDismissAction = true
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onSetChannelDisabled(stream.name, false)
                                        }
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DoNotDisturb,
                                    contentDescription = "Wyłącz kanał",
                                    tint = Color(0xFF4A5568),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (markdownEnabled) {
                            MarkdownText(
                                markdown = stream.description,
                                textColor = BodyText,
                                compactMode = compactMode,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else {
                            Text(stream.description, color = BodyText, style = bodyStyle)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun TopicsList(
    uiState: ChannelsUiState,
    onTopicSelected: (TopicEntity) -> Unit,
    onBackToStreams: () -> Unit,
    sectionGap: androidx.compose.ui.unit.Dp,
    cardPadding: androidx.compose.ui.unit.Dp,
    titleStyle: androidx.compose.ui.text.TextStyle,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    backStyle: androidx.compose.ui.text.TextStyle,
    unreadByTopic: Map<String, Int>
) {
    if (uiState.topics.isEmpty()) {
        val selectedStream = uiState.selectedStream
        val streamId = selectedStream?.id ?: 0L
        Column(verticalArrangement = Arrangement.spacedBy(sectionGap)) {
            Row(modifier = Modifier.fillMaxWidth().clickable { onBackToStreams() }.padding(8.dp)) {
                Text("<- Wróć do streamów", color = Accent, fontWeight = FontWeight.Medium, style = backStyle)
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onTopicSelected(
                            TopicEntity(
                                key = "$streamId::__all__",
                                streamId = streamId,
                                name = "",
                                maxMessageId = 0L
                            )
                        )
                    },
                colors = CardDefaults.cardColors(containerColor = PanelCard)
            ) {
                Column(modifier = Modifier.padding(cardPadding)) {
                    Text("Wszystkie wiadomości kanału", fontWeight = FontWeight.SemiBold, color = StrongText, style = titleStyle)
                    Text("Otwórz kanał bez filtrowania po temacie", color = BodyText, style = bodyStyle)
                }
            }
            EmptyState(
                title = "Brak tematow",
                message = "Dla tego streamu nie ma jeszcze tematow lub serwer nie zwrocil danych. Możesz wejść do wszystkich wiadomości kanału kartą powyżej."
            )
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(sectionGap)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().clickable { onBackToStreams() }.padding(8.dp)) {
                Text("<- Wróćóć do streamów", color = Accent, fontWeight = FontWeight.Medium, style = backStyle)
            }
        }
        items(uiState.topics, key = { it.key }) { topic ->
            val selectedStreamName = uiState.selectedStream?.name.orEmpty()
            val topicKey = "$selectedStreamName::${topic.name}"
            val unreadCount = unreadByTopic[topicKey] ?: 0
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTopicSelected(topic) },
                colors = CardDefaults.cardColors(containerColor = PanelCard)
            ) {
                Column(modifier = Modifier.padding(cardPadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(topic.name, fontWeight = FontWeight.SemiBold, color = StrongText, style = titleStyle, modifier = Modifier.weight(1f))
                        if (unreadCount > 0) {
                            Badge(containerColor = Color(0xFF8A3B2E)) {
                                Text(unreadCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelSenderAvatar(
    avatarUrl: String,
    fullName: String,
    compactMode: Boolean,
    onClick: () -> Unit
) {
    val size = if (compactMode) 24.dp else 28.dp
    val initials = remember(fullName) {
        fullName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "?" }
    }

    Box(modifier = Modifier.clickable { onClick() }) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = fullName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else {
            Surface(
                modifier = Modifier.size(size),
                shape = CircleShape,
                color = Color(0xFF2D4B6E)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        color = Color(0xFFBDD5F2),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatChannelMessageTime(timestampSeconds: Long): String {
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestampSeconds * 1000))
}

private fun hasWildcardMention(content: String): Boolean {
    val normalized = content.lowercase()
    return normalized.contains("@all") ||
        normalized.contains("@everyone") ||
        normalized.contains("@channel") ||
        normalized.contains("@stream") ||
        normalized.contains("@**all**") ||
        normalized.contains("@**everyone**") ||
        normalized.contains("@**channel**") ||
        normalized.contains("@**stream**")
}

private fun hasDirectUserMention(content: String, currentUserEmail: String): Boolean {
    val email = currentUserEmail.trim().lowercase()
    if (email.isBlank()) return false
    val handle = email.substringBefore('@').trim()
    val normalized = content.lowercase()
    if (normalized.contains(email)) return true
    if (handle.isBlank()) return false
    return normalized.contains("@${handle.lowercase()}") ||
        normalized.contains("@**${handle.lowercase()}**")
}

private fun firstImageUrl(rawContent: String, serverUrl: String): String? {
    val candidates = mutableListOf<String>()

    Regex("src\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        .findAll(rawContent)
        .forEach { candidates += it.groupValues[1] }
    Regex("src\\s*=\\s*'([^']+)'", RegexOption.IGNORE_CASE)
        .findAll(rawContent)
        .forEach { candidates += it.groupValues[1] }
    Regex("href\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        .findAll(rawContent)
        .forEach { candidates += it.groupValues[1] }
    Regex("href\\s*=\\s*'([^']+)'", RegexOption.IGNORE_CASE)
        .findAll(rawContent)
        .forEach { candidates += it.groupValues[1] }
    Regex("!\\[[^\\]]*]\\(([^)]+)\\)")
        .findAll(rawContent)
        .forEach { candidates += it.groupValues[1] }
    Regex("\\[([^\\]]*?)]\\(([^)]+)\\)")
        .findAll(rawContent)
        .forEach { candidates += it.groupValues[2] }
    Regex("https?://[^\\s\"'<>()]+", RegexOption.IGNORE_CASE)
        .findAll(rawContent)
        .forEach { candidates += it.value }
    Regex("/user_uploads/[^\\s\"'<>()]*", RegexOption.IGNORE_CASE)
        .findAll(rawContent)
        .forEach { candidates += it.value }

    val imagePath = candidates.firstOrNull {
        it.matches(Regex(".*\\.(png|jpe?g|gif|webp|bmp|heic|heif|svg)(\\?.*)?$", RegexOption.IGNORE_CASE)) ||
            it.contains("/user_uploads/", ignoreCase = true)
    } ?: return null

    return if (imagePath.startsWith("http", ignoreCase = true)) {
        imagePath
    } else if (imagePath.startsWith("/")) {
        serverUrl.trimEnd('/') + imagePath
    } else {
        serverUrl.trimEnd('/') + "/" + imagePath.trimStart('/')
    }
}

@Composable
private fun ChannelAttachmentPreview(
    imageUrl: String,
    authHeader: String?,
    compactMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageModel = remember(context, imageUrl, authHeader) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .apply {
                if (!authHeader.isNullOrBlank()) {
                    addHeader("Authorization", authHeader)
                }
            }
            .crossfade(false)
            .build()
    }

    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var isFullscreenOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0x1A8CD9FF),
        border = BorderStroke(1.dp, Color(0x33284869))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compactMode) 160.dp else 220.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasError) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🖼️",
                        style = MaterialTheme.typography.displaySmall
                    )
                    Text(
                        text = "Nie można załadować",
                        color = Color(0xFF8CD9FF),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Załączony obraz",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isFullscreenOpen = true },
                    onLoading = { isLoading = true },
                    onSuccess = { isLoading = false },
                    onError = {
                        isLoading = false
                        hasError = true
                    }
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF8CD9FF),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }

    if (isFullscreenOpen) {
        ChannelImageFullscreenDialog(imageModel = imageModel) { isFullscreenOpen = false }
    }
}

@Composable
private fun ChannelImageFullscreenDialog(
    imageModel: Any,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = "Powiększony obraz",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            scale = newScale
                            if (newScale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = StrongText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        Text(message, color = BodyText, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NarrowMessages(
    uiState: ChannelsUiState,
    onBackToTopics: () -> Unit,
    sectionGap: androidx.compose.ui.unit.Dp,
    cardPadding: androidx.compose.ui.unit.Dp,
    titleStyle: androidx.compose.ui.text.TextStyle,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    backStyle: androidx.compose.ui.text.TextStyle,
    compactMode: Boolean,
    markdownEnabled: Boolean,
    currentUserEmail: String = "",
    serverUrl: String,
    imageAuthHeader: String?,
    onUploadAttachmentMessage: UploadAttachmentMessage = { _, _, _, _, onSuccess, _ -> onSuccess() },
    onSendChannelMessage: (String, String, String) -> Unit = { _, _, _ -> },
    onAddReaction: (Long, String) -> Unit = { _, _ -> },
    onEditMessage: (Long) -> Unit = { _ -> },
    onDeleteMessage: (Long, () -> Unit, (String) -> Unit) -> Unit = { _, onSuccess, _ -> onSuccess() },
    onMoveMessageToTopic: (Long, String, String, Long?, () -> Unit, (String) -> Unit) -> Unit =
        { _, _, _, _, onSuccess, _ -> onSuccess() },
    onLoadTopicsForStream: (Long, (List<String>) -> Unit) -> Unit = { _, _ -> },
    onScrollConsumed: () -> Unit = {},
    onLoadOlderMessages: () -> Unit = {},
    onRefreshLatestMessages: () -> Unit = {},
    mentionCandidates: List<DirectMessageCandidate> = emptyList(),
    onRequestMentionCandidates: () -> Unit = {},
    topicCandidates: List<String> = emptyList(),
    isChannelMuted: (String) -> Boolean = { false },
    onSetChannelMuted: (String, Boolean) -> Unit = { _, _ -> },
    canModerateAllMessages: Boolean = false,
    onForwardToDm: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedStreamName = uiState.selectedStream?.name.orEmpty()
    val selectedTopicName = uiState.selectedTopic?.name.orEmpty()
    val topicTitle = selectedTopicName.ifBlank { "Wszystkie wiadomości kanału" }
    val lazyListState = rememberLazyListState()

    var expandedMenuMessageId by remember { mutableStateOf<Long?>(null) }
    var emojiPickerMessageId by remember { mutableStateOf<Long?>(null) }
    var highlightedMessageId by remember(selectedStreamName, selectedTopicName) { mutableStateOf<Long?>(null) }
    var autoScrolledToLatest by remember(selectedStreamName, selectedTopicName) { mutableStateOf(false) }
    var pendingMentionText by remember(selectedStreamName, selectedTopicName) { mutableStateOf<String?>(null) }
    var isUploadingAttachment by remember { mutableStateOf(false) }
    var moveMessageId by remember { mutableStateOf<Long?>(null) }
    var moveMessageContent by remember { mutableStateOf("") }
    var moveTargetStreamId by remember { mutableStateOf<Long?>(uiState.selectedStream?.id) }
    var moveTargetTopic by remember { mutableStateOf(selectedTopicName) }
    var moveTopicOptions by remember { mutableStateOf(topicCandidates) }
    var moveStreamMenuExpanded by remember { mutableStateOf(false) }
    var moveTopicMenuExpanded by remember { mutableStateOf(false) }
    var isCurrentChannelMuted by remember(selectedStreamName) {
        mutableStateOf(if (selectedStreamName.isBlank()) false else isChannelMuted(selectedStreamName))
    }

    LaunchedEffect(selectedStreamName) {
        isCurrentChannelMuted = if (selectedStreamName.isBlank()) false else isChannelMuted(selectedStreamName)
    }

    LaunchedEffect(selectedStreamName, selectedTopicName, topicCandidates) {
        if (moveMessageId == null) {
            moveTargetStreamId = uiState.selectedStream?.id
            moveTargetTopic = selectedTopicName
            moveTopicOptions = topicCandidates
            moveStreamMenuExpanded = false
            moveTopicMenuExpanded = false
        }
    }

    val attachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (selectedStreamName.isBlank() || selectedTopicName.isBlank()) {
            Toast.makeText(context, "Najpierw wybierz kanał i wątek", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            isUploadingAttachment = true
            val attachment = readPickedAttachment(context, uri)
            if (attachment == null) {
                isUploadingAttachment = false
                Toast.makeText(context, "Nie udało się odczytać pliku", Toast.LENGTH_SHORT).show()
                return@launch
            }
            onUploadAttachmentMessage(
                "stream",
                selectedStreamName,
                selectedTopicName,
                attachment,
                {
                    isUploadingAttachment = false
                    Toast.makeText(context, "Załącznik wysłany", Toast.LENGTH_SHORT).show()
                    onRefreshLatestMessages()
                },
                { error ->
                    isUploadingAttachment = false
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    LaunchedEffect(selectedStreamName, selectedTopicName, uiState.messages.size, autoScrolledToLatest) {
        if (!autoScrolledToLatest && uiState.messages.isNotEmpty()) {
            lazyListState.scrollToItem(uiState.messages.lastIndex)
            autoScrolledToLatest = true
        }
    }

    LaunchedEffect(uiState.scrollToMessageId, uiState.messages.size) {
        val targetId = uiState.scrollToMessageId ?: return@LaunchedEffect
        val index = uiState.messages.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            lazyListState.scrollToItem(index)
            highlightedMessageId = targetId
            onScrollConsumed()
        }
    }

    LaunchedEffect(
        lazyListState.firstVisibleItemIndex,
        lazyListState.firstVisibleItemScrollOffset,
        uiState.hasMoreOlderMessages,
        uiState.isLoadingOlderMessages,
        uiState.messages.firstOrNull()?.id
    ) {
        if (
            uiState.messages.isNotEmpty() &&
            uiState.hasMoreOlderMessages &&
            !uiState.isLoadingOlderMessages &&
            lazyListState.firstVisibleItemIndex <= 1 &&
            lazyListState.firstVisibleItemScrollOffset == 0
        ) {
            onLoadOlderMessages()
        }
    }

    fun toggleChannelMuted() {
        if (selectedStreamName.isBlank()) return
        val newMuted = !isCurrentChannelMuted
        isCurrentChannelMuted = newMuted
        onSetChannelMuted(selectedStreamName, newMuted)
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            if (newMuted) {
                val result = snackbarHostState.showSnackbar(
                    message = "Kanał wyciszony",
                    actionLabel = "Cofnij",
                    withDismissAction = true
                )
                if (result == SnackbarResult.ActionPerformed) {
                    isCurrentChannelMuted = false
                    onSetChannelMuted(selectedStreamName, false)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(sectionGap)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(if (compactMode) 18.dp else 22.dp),
                color = PanelCard
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = cardPadding, vertical = if (compactMode) 8.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "<- Wróć",
                        color = Accent,
                        fontWeight = FontWeight.Medium,
                        style = backStyle,
                        modifier = Modifier.clickable { onBackToTopics() }.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#$selectedStreamName",
                            color = StrongText,
                            style = titleStyle,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = topicTitle,
                            color = BodyText,
                            style = bodyStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = ::toggleChannelMuted) {
                        Icon(
                            imageVector = if (isCurrentChannelMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                            contentDescription = if (isCurrentChannelMuted) "Kanał wyciszony" else "Wycisz kanał",
                            tint = if (isCurrentChannelMuted) Color(0xFFFF8A80) else Accent
                        )
                    }
                }
            }

            if (uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    EmptyState(
                        title = "Brak wiadomości",
                        message = "Ten widok nie ma jeszcze wiadomości do pokazania."
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(sectionGap)
                ) {
                    if (uiState.isLoadingOlderMessages) {
                        item {
                            Text(
                                text = "Ładowanie starszych wiadomości...",
                                color = Accent,
                                style = backStyle,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    items(uiState.messages, key = { it.id }) { message ->
                        val isOwnMessage = message.senderEmail.equals(currentUserEmail, ignoreCase = true)
                        val canManageMessage = isOwnMessage || canModerateAllMessages
                        val isHighlighted = highlightedMessageId == message.id
                        val isStarredMessage = message.isStarred
                        val isWildcardMentionMessage = !isOwnMessage && (message.isWildcardMentioned || hasWildcardMention(message.content))
                        val isDirectMentionMessage = !isOwnMessage && (message.isMentioned || hasDirectUserMention(message.content, currentUserEmail))
                        val imageUrl = remember(message.id, message.content, serverUrl) {
                            firstImageUrl(message.content, serverUrl)
                        }
                        val plainContent = remember(message.id, message.content) {
                            message.content
                                .replace(Regex("<[^>]*>"), "")
                                .replace(Regex("!\\[[^\\]]*]\\(([^)]+)\\)"), "")
                                .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1")
                                .trim()
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            if (!isOwnMessage) {
                                ChannelSenderAvatar(
                                    avatarUrl = message.avatarUrl,
                                    fullName = message.senderFullName,
                                    compactMode = compactMode,
                                    onClick = { pendingMentionText = "@**${message.senderFullName}**" }
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                            }

                            Box(modifier = Modifier.fillMaxWidth(if (isOwnMessage) 0.86f else 1f)) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = { expandedMenuMessageId = message.id }
                                        ),
                                    shape = RoundedCornerShape(18.dp),
                                    color = when {
                                        isHighlighted && isOwnMessage -> Color(0xFF2F5C84)
                                        isHighlighted -> Color(0xFF243E61)
                                        isWildcardMentionMessage -> Color(0xFF5A3E1A)
                                        isDirectMentionMessage -> Color(0xFF1E4B35)
                                        isStarredMessage -> Color(0xFF53401A)
                                        isOwnMessage -> Color(0xFF23415F)
                                        else -> PanelCard
                                    },
                                    border = when {
                                        isHighlighted -> BorderStroke(1.dp, Accent.copy(alpha = 0.45f))
                                        isWildcardMentionMessage -> BorderStroke(1.dp, Color(0xFFFFC977).copy(alpha = 0.65f))
                                        isDirectMentionMessage -> BorderStroke(1.dp, Color(0xFF8EE4BC).copy(alpha = 0.65f))
                                        isStarredMessage -> BorderStroke(1.dp, Color(0xFFFFD76E).copy(alpha = 0.6f))
                                        else -> null
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(cardPadding)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (!isOwnMessage) {
                                                Text(
                                                    text = message.senderFullName,
                                                    color = StrongText,
                                                    style = titleStyle,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { pendingMentionText = "@**${message.senderFullName}**" }
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                            Text(
                                                text = formatChannelMessageTime(message.timestampSeconds),
                                                color = BodyText,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                        if (isStarredMessage || isWildcardMentionMessage || isDirectMentionMessage) {
                                            Row(
                                                modifier = Modifier.padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (isStarredMessage) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = Color(0x33FFD76E)
                                                    ) {
                                                        Text(
                                                            text = "★ gwiazdka",
                                                            color = Color(0xFFFFE39A),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                                if (isWildcardMentionMessage) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = Color(0x33FFC977)
                                                    ) {
                                                        Text(
                                                            text = "@all",
                                                            color = Color(0xFFFFD79A),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                                if (isDirectMentionMessage) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = Color(0x338EE4BC)
                                                    ) {
                                                        Text(
                                                            text = "@ty",
                                                            color = Color(0xFFB9F2D8),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (selectedTopicName.isBlank() && message.topic.isNotBlank()) {
                                            Text(
                                                text = message.topic,
                                                color = Accent,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = Color(0xFF213754)
                                        ) {
                                            if (markdownEnabled) {
                                                MarkdownText(
                                                    markdown = message.content,
                                                    textColor = BodyText,
                                                    compactMode = compactMode,
                                                    modifier = Modifier.padding(cardPadding)
                                                )
                                            } else {
                                                Text(
                                                    text = plainContent,
                                                    color = BodyText,
                                                    style = bodyStyle,
                                                    modifier = Modifier.padding(cardPadding)
                                                )
                                            }
                                        }
                                        if (imageUrl != null) {
                                            ChannelAttachmentPreview(
                                                imageUrl = imageUrl,
                                                authHeader = imageAuthHeader,
                                                compactMode = compactMode,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp)
                                            )
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = expandedMenuMessageId == message.id,
                                    onDismissRequest = { expandedMenuMessageId = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("@ Wspomnij") },
                                        onClick = {
                                            expandedMenuMessageId = null
                                            pendingMentionText = "@**${message.senderFullName}**"
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(" Reakcja") },
                                        onClick = {
                                            expandedMenuMessageId = null
                                            emojiPickerMessageId = message.id
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(" Cytuj") },
                                        onClick = {
                                            expandedMenuMessageId = null
                                            val quoteBlock = plainContent.lines()
                                                .filter { it.isNotBlank() }
                                                .joinToString("\n") { "> $it" }
                                            if (quoteBlock.isNotBlank()) {
                                                pendingMentionText = "$quoteBlock\n\n"
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(" Przekaż do DM") },
                                        onClick = {
                                            expandedMenuMessageId = null
                                            val forward = buildString {
                                                append("Przekazane z #")
                                                append(selectedStreamName)
                                                if (message.topic.isNotBlank()) {
                                                    append(" > ")
                                                    append(message.topic)
                                                }
                                                append("\nOd: ")
                                                append(message.senderFullName)
                                                append("\n\n")
                                                append(plainContent)
                                            }
                                            onForwardToDm(forward)
                                        }
                                    )
                                    if (canManageMessage) {
                                        DropdownMenuItem(
                                            text = { Text(" Przenieś do wątku") },
                                            onClick = {
                                                expandedMenuMessageId = null
                                                moveMessageId = message.id
                                                moveMessageContent = message.content
                                                moveTargetStreamId = uiState.selectedStream?.id
                                                moveTargetTopic = message.topic
                                                moveTopicOptions = topicCandidates
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(" Edytuj") },
                                            onClick = {
                                                expandedMenuMessageId = null
                                                onEditMessage(message.id)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(" Usuń") },
                                            onClick = {
                                                expandedMenuMessageId = null
                                                onDeleteMessage(
                                                    message.id,
                                                    {
                                                        Toast.makeText(context, "Wiadomość usunięta", Toast.LENGTH_SHORT).show()
                                                    },
                                                    { error ->
                                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isUploadingAttachment) {
                Text(
                    text = "Wysyłanie pliku...",
                    color = Accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = backStyle
                )
            }

            MessageComposeInput(
                onSend = { content ->
                    if (selectedStreamName.isNotBlank() && selectedTopicName.isNotBlank()) {
                        onSendChannelMessage(selectedStreamName, selectedTopicName, content)
                    }
                },
                compactMode = compactMode,
                modifier = Modifier.imePadding(),
                enabled = selectedStreamName.isNotBlank() && selectedTopicName.isNotBlank() && !isUploadingAttachment,
                mentionCandidates = mentionCandidates,
                onRequestMentionCandidates = onRequestMentionCandidates,
                topicCandidates = topicCandidates,
                allowTopicSuggestions = true,
                pendingInsertionText = pendingMentionText,
                onPendingInsertionConsumed = { pendingMentionText = null },
                onAddAttachment = { attachmentLauncher.launch(arrayOf("*/*")) },
                onGenerateVideoCall = {
                    val timestamp = System.currentTimeMillis()
                    val meetingId = "call_${timestamp}"
                    val videoLink = "<b> Video Call:</b> <a href=\"https://jitsi.example.com/$meetingId\">https://jitsi.example.com/$meetingId</a>"
                    if (selectedStreamName.isNotBlank() && selectedTopicName.isNotBlank()) {
                        onSendChannelMessage(selectedStreamName, selectedTopicName, videoLink)
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (moveMessageId != null) {
        val subscribedStreams = uiState.streams.filter { it.subscribed }
        val moveStreams = if (subscribedStreams.isNotEmpty()) subscribedStreams else uiState.streams
        val selectedMoveStream = moveStreams.firstOrNull { it.id == moveTargetStreamId }
        AlertDialog(
            onDismissRequest = { moveMessageId = null },
            title = { Text("Przenieś wiadomość") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = moveStreamMenuExpanded,
                        onExpandedChange = { moveStreamMenuExpanded = !moveStreamMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedMoveStream?.name.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Kanał") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = moveStreamMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = moveStreamMenuExpanded,
                            onDismissRequest = { moveStreamMenuExpanded = false }
                        ) {
                            moveStreams.forEach { stream ->
                                DropdownMenuItem(
                                    text = { Text(stream.name) },
                                    onClick = {
                                        moveStreamMenuExpanded = false
                                        moveTargetStreamId = stream.id
                                        moveTargetTopic = ""
                                        moveTopicOptions = emptyList()
                                        onLoadTopicsForStream(stream.id) { topics ->
                                            moveTopicOptions = topics
                                            if (topics.isNotEmpty()) {
                                                moveTargetTopic = topics.first()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = moveTopicMenuExpanded,
                        onExpandedChange = {
                            if (moveTopicOptions.isNotEmpty()) {
                                moveTopicMenuExpanded = !moveTopicMenuExpanded
                            }
                        }
                    ) {
                        OutlinedTextField(
                            value = moveTargetTopic,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Wątek") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = moveTopicMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = moveTopicMenuExpanded,
                            onDismissRequest = { moveTopicMenuExpanded = false }
                        ) {
                            moveTopicOptions.forEach { topicName ->
                                DropdownMenuItem(
                                    text = { Text(topicName) },
                                    onClick = {
                                        moveTopicMenuExpanded = false
                                        moveTargetTopic = topicName
                                    }
                                )
                            }
                        }
                    }

                    if (moveTopicOptions.isEmpty()) {
                        Text(
                            text = "Brak listy tematów dla tego kanału. Otwórz kanał, aby załadować wątki.",
                            color = BodyText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = moveMessageId
                        val newTopic = moveTargetTopic.trim()
                        val targetStreamId = moveTargetStreamId
                        if (targetId != null && targetStreamId != null && newTopic.isNotBlank()) {
                            onMoveMessageToTopic(
                                targetId,
                                moveMessageContent,
                                newTopic,
                                targetStreamId,
                                {
                                    Toast.makeText(context, "Wiadomość przeniesiona", Toast.LENGTH_SHORT).show()
                                    moveMessageId = null
                                },
                                { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Wybierz kanał i wątek", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Przenieś")
                }
            },
            dismissButton = {
                Button(onClick = { moveMessageId = null }) {
                    Text("Anuluj")
                }
            }
        )
    }

    emojiPickerMessageId?.let { msgId ->
        EmojiPickerDialog(
            onEmojiSelected = { emoji ->
                onAddReaction(msgId, emoji)
                emojiPickerMessageId = null
            },
            onDismiss = { emojiPickerMessageId = null }
        )
    }
}

