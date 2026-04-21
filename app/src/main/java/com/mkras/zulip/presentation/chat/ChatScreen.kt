package com.mkras.zulip.presentation.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import android.text.method.LinkMovementMethod
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mkras.zulip.data.local.entity.MessageEntity
import com.mkras.zulip.domain.repository.DirectMessageCandidate
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardBg = Color(0xFF1A2B44)
private val NameColor = Color(0xFFE9F0FF)
private val SubtleColor = Color(0xFF8FA8C8)
private val ContentColor = Color(0xFFD0DCF0)
private val OutgoingBubble = Color(0xFF2E3A36)
private val OutgoingText = Color(0xFFFFFFFF)
private val BubbleOutline = Color(0xFF284869)
private val ThreadBackdrop = Color(0x99111D31)

// Pre-compiled regexes for better performance
private val HTML_TAG_REGEX = Regex("<[^>]*>")
private val MD_IMAGE_REGEX = Regex("!\\[[^\\]]*]\\(([^)]+)\\)")
private val MD_LINK_REGEX = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
private val IMAGE_EXT_REGEX = Regex(".*\\.(png|jpe?g|gif|webp|bmp|heic|heif|svg)(\\?.*)?$", RegexOption.IGNORE_CASE)

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onMessagesRendered: (List<Long>) -> Unit,
    onResumeResync: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onBackToList: () -> Unit,
    currentUserEmail: String,
    serverUrl: String,
    imageAuthHeader: String? = null,
    compactMode: Boolean,
    onUploadAttachmentMessage: UploadAttachmentMessage = { _, _, _, _, onSuccess, _ -> onSuccess() },
    onOpenNewDmPicker: () -> Unit,
    onCloseNewDmPicker: () -> Unit,
    onNewDmQueryChange: (String) -> Unit,
    onSelectNewDmPerson: (DirectMessageCandidate) -> Unit,
    mentionCandidates: List<DirectMessageCandidate> = emptyList(),
    onRequestMentionCandidates: () -> Unit = {},
    isDirectMessageMuted: (String) -> Boolean = { false },
    onSetDirectMessageMuted: (String, Boolean) -> Unit = { _, _ -> },
    onScrollConsumed: () -> Unit = {},
    onSendMessage: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onAddReaction: (Long, EmojiHelper.ReactionSelection) -> Unit = { _, _ -> },
    onRemoveReaction: (Long, EmojiHelper.ReactionSelection) -> Unit = { _, _ -> },
    onEditMessage: (Long, String) -> Unit = { _, _ -> },
    onDeleteMessage: (Long) -> Unit = { _ -> },
    pendingDirectMessageContent: String? = null,
    onPendingDirectMessageContentConsumed: () -> Unit = {},
    canModerateAllMessages: Boolean = false,
    currentUserId: Long? = null,
    typingText: String? = null,
    onAttachmentOperationStart: () -> Unit = {},
    onAttachmentOperationEnd: () -> Unit = {},
    customEmojiById: Map<String, String> = emptyMap(),
    customEmojiByName: Map<String, String> = emptyMap(),
    customEmojis: List<EmojiHelper.CustomEmojiItem> = emptyList()
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(uiState.privateMessages.size, uiState.selectedConversationKey) {
        val key = uiState.selectedConversationKey ?: return@LaunchedEffect
        val ids = uiState.privateMessages
            .filter { it.conversationKey == key }
            .map { it.id }
        if (ids.isNotEmpty()) {
            onMessagesRendered(ids)
        }
    }

    LaunchedEffect(Unit) {
        onResumeResync()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onResumeResync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selectedKey = uiState.selectedConversationKey
    if (selectedKey == null) {
        DmConversationList(
            conversations = uiState.dmConversations,
            onSelect = onSelectConversation,
            compactMode = compactMode,
            onOpenNewDmPicker = onOpenNewDmPicker,
            isNewDmPickerVisible = uiState.isNewDmPickerVisible,
            isNewDmLoading = uiState.isNewDmLoading,
            newDmError = uiState.newDmError,
            newDmQuery = uiState.newDmQuery,
            newDmPeople = uiState.newDmPeople,
            onCloseNewDmPicker = onCloseNewDmPicker,
            onNewDmQueryChange = onNewDmQueryChange,
            onSelectNewDmPerson = onSelectNewDmPerson,
            presenceByEmail = uiState.presenceByEmail,
            currentUserEmail = currentUserEmail,
            typingText = typingText
        )

    } else {
        val thread = remember(uiState.privateMessages, selectedKey) {
            uiState.privateMessages.filter { it.conversationKey == selectedKey }
        }
        val name = uiState.selectedConversationTitle
            ?: uiState.dmConversations.find { it.conversationKey == selectedKey }?.displayName
            ?: selectedKey
        DmThreadView(
            name = name,
            messages = thread,
            scrollToMessageId = uiState.dmScrollToMessageId,
            conversationKey = selectedKey,
            typingText = uiState.typingText,
            onMessagesRendered = onMessagesRendered,
            onBack = onBackToList,
            onScrollConsumed = onScrollConsumed,
            currentUserEmail = currentUserEmail,
            serverUrl = serverUrl,
            imageAuthHeader = imageAuthHeader,
            compactMode = compactMode,
            mentionCandidates = mentionCandidates,
            onRequestMentionCandidates = onRequestMentionCandidates,
            isDirectMessageMuted = isDirectMessageMuted,
            onSetDirectMessageMuted = onSetDirectMessageMuted,
            onUploadAttachmentMessage = onUploadAttachmentMessage,
            onSendMessage = onSendMessage,
            onAddReaction = onAddReaction,
            onRemoveReaction = onRemoveReaction,
            onEditMessage = onEditMessage,
            onDeleteMessage = onDeleteMessage,
            pendingDirectMessageContent = pendingDirectMessageContent,
            onPendingDirectMessageContentConsumed = onPendingDirectMessageContentConsumed,
            canModerateAllMessages = canModerateAllMessages,
            currentUserId = currentUserId,
            onAttachmentOperationStart = onAttachmentOperationStart,
            onAttachmentOperationEnd = onAttachmentOperationEnd,
            sendMessageError = uiState.sendMessageError,
            resyncError = uiState.resyncError,
            onClearSendError = { /* Will be connected in ZulipHomeScreen */ },
            onClearResyncError = { /* Will be connected in ZulipHomeScreen */ },
            onSaveDmScrollPosition = { key, index -> /* Will be connected in ZulipHomeScreen */ },
            customEmojiById = customEmojiById,
            customEmojiByName = customEmojiByName,
            customEmojis = customEmojis
        )
    }
}

@Composable
private fun DmConversationList(
    conversations: List<DmConversation>,
    onSelect: (String) -> Unit,
    compactMode: Boolean,
    onOpenNewDmPicker: () -> Unit,
    isNewDmPickerVisible: Boolean,
    isNewDmLoading: Boolean,
    newDmError: String?,
    newDmQuery: String,
    newDmPeople: List<DirectMessageCandidate>,
    onCloseNewDmPicker: () -> Unit,
    onNewDmQueryChange: (String) -> Unit,
    onSelectNewDmPerson: (DirectMessageCandidate) -> Unit,
    presenceByEmail: Map<String, String> = emptyMap(),
    currentUserEmail: String = "",
    typingText: String? = null
) {
    val gap = if (compactMode) 2.dp else 3.dp
    val cardVertical = if (compactMode) 8.dp else 12.dp
    val cardHorizontal = if (compactMode) 10.dp else 14.dp
    val avatarSize = if (compactMode) 24.dp else 28.dp
    val textStyle = if (compactMode) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge

    val filteredPeople = remember(newDmPeople, newDmQuery) {
        val query = newDmQuery.trim().lowercase()
        if (query.isBlank()) {
            newDmPeople
        } else {
            newDmPeople.filter {
                it.fullName.lowercase().contains(query) || it.email.lowercase().contains(query)
            }.sortedWith(
                compareBy<DirectMessageCandidate>(
                    { !it.fullName.lowercase().startsWith(query) },
                    { !it.email.lowercase().startsWith(query) },
                    { it.fullName.lowercase() }
                )
            )
        }
    }
    val queryToHighlight = newDmQuery.trim()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(isNewDmPickerVisible) {
        if (isNewDmPickerVisible) {
            delay(150)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Brak wiadomości", color = SubtleColor)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                items(conversations, key = { it.conversationKey }) { conv ->
                    val peerEmail = remember(conv.conversationKey, currentUserEmail) {
                        val selfNorm = currentUserEmail.trim().lowercase()
                        conv.conversationKey.split(",")
                            .map { it.trim().lowercase() }
                            .filter { it.isNotBlank() && it != selfNorm }
                            .firstOrNull() ?: conv.conversationKey.trim().lowercase()
                    }
                    val presenceStatus = presenceByEmail[peerEmail]
                    val typingEmailFromText = remember(typingText) {
                        typingText?.substringBefore(" pisze")?.trim()?.lowercase()
                    }
                    val isTyping = typingEmailFromText == peerEmail
                    val infiniteTransition = rememberInfiniteTransition(label = "typing_pulse")
                    val typingBorderAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "typing_alpha"
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(conv.conversationKey) },
                        shape = RoundedCornerShape(if (compactMode) 20.dp else 24.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, BubbleOutline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = cardHorizontal, vertical = cardVertical),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.BottomEnd,
                                modifier = Modifier.then(
                                    if (isTyping) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = Color(0xFF8CD9FF).copy(alpha = typingBorderAlpha),
                                            shape = CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                            ) {
                                AvatarImage(
                                    avatarUrl = conv.avatarUrl,
                                    initials = conv.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    size = avatarSize
                                )
                                if (presenceStatus != null) {
                                    val dotColor = when (presenceStatus) {
                                        "active" -> Color(0xFF43D87A)
                                        "idle" -> Color(0xFFF5C543)
                                        else -> Color(0xFF6B7280)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(dotColor, CircleShape)
                                            .border(1.5.dp, CardBg, CircleShape)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = if (compactMode) 10.dp else 12.dp)
                            ) {
                                Text(
                                    text = conv.displayName,
                                    style = textStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = NameColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (conv.unreadCount > 0) "Nowe wiadomości: ${conv.unreadCount}" else "Otwórz konwersację",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SubtleColor
                                )
                            }
                            if (conv.unreadCount > 0) {
                                Badge(containerColor = Color(0xFF8A3B2E)) {
                                    Text(
                                        text = conv.unreadCount.toString(),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenNewDmPicker,
            containerColor = Color(0xFF7FD6FF),
            contentColor = Color(0xFF08101F),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Nowa wiadomość"
            )
        }

        if (isNewDmPickerVisible) {
            Dialog(onDismissRequest = onCloseNewDmPicker) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF101E33)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nowa rozmowa",
                                color = NameColor,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = onCloseNewDmPicker) {
                                Text("Zamknij", color = Color(0xFF8CD9FF))
                            }
                        }
                        OutlinedTextField(
                            value = newDmQuery,
                            onValueChange = onNewDmQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            placeholder = { Text("Szukaj osoby", maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = SubtleColor
                                )
                            },
                            trailingIcon = {
                                if (newDmQuery.isNotBlank()) {
                                    IconButton(onClick = { onNewDmQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Wyczyść",
                                            tint = SubtleColor
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF16273D),
                                unfocusedContainerColor = Color(0xFF16273D),
                                focusedIndicatorColor = Color(0xFF8CD9FF),
                                unfocusedIndicatorColor = Color(0xFF2A4B6E),
                                focusedTextColor = NameColor,
                                unfocusedTextColor = NameColor,
                                cursorColor = Color(0xFF8CD9FF)
                            )
                        )
                        when {
                            isNewDmLoading -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF8CD9FF))
                                }
                            }
                            newDmError != null -> {
                                Text(
                                    text = newDmError,
                                    color = Color(0xFFFFA8B5),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            filteredPeople.isEmpty() -> {
                                Text(
                                    text = "Brak osób dla podanego filtra.",
                                    color = SubtleColor,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    contentPadding = PaddingValues(bottom = 20.dp)
                                ) {
                                    items(filteredPeople, key = { it.userId }) { person ->
                                        val peerEmail = remember(person.email) {
                                            person.email.trim().lowercase()
                                        }
                                        val presenceStatus = presenceByEmail[peerEmail]
                                        val dotColor = when (presenceStatus) {
                                            "active" -> Color(0xFF43D87A)
                                            "idle" -> Color(0xFFF5C543)
                                            else -> Color(0xFF6B7280)
                                        }
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSelectNewDmPerson(person)
                                                    onCloseNewDmPicker()
                                                },
                                            color = Color(0xFF14253B),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color(0xFF31577D))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 10.dp,
                                                    vertical = 7.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(contentAlignment = Alignment.BottomEnd) {
                                                    Surface(
                                                        modifier = Modifier.size(30.dp),
                                                        shape = CircleShape,
                                                        color = Color(0xFF2D4B6E)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = person.fullName.firstOrNull()
                                                                    ?.uppercaseChar()?.toString() ?: "?",
                                                                color = Color(0xFFBDD5F2),
                                                                fontWeight = FontWeight.Bold,
                                                                style = MaterialTheme.typography.labelLarge
                                                            )
                                                        }
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .size(9.dp)
                                                            .background(dotColor, CircleShape)
                                                            .border(
                                                                1.5.dp,
                                                                Color(0xFF14253B),
                                                                CircleShape
                                                            )
                                                    )
                                                }
                                                Text(
                                                    text = highlightedName(
                                                        person.fullName,
                                                        queryToHighlight
                                                    ),
                                                    color = NameColor,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(start = 8.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "DM",
                                                    color = Color(0xFF8CD9FF),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun highlightedName(fullName: String, query: String): androidx.compose.ui.text.AnnotatedString {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) return buildAnnotatedString { append(fullName) }

    val lowerName = fullName.lowercase()
    val lowerQuery = trimmedQuery.lowercase()
    val firstMatch = lowerName.indexOf(lowerQuery)
    if (firstMatch < 0) return buildAnnotatedString { append(fullName) }

    val matchEnd = firstMatch + lowerQuery.length
    return buildAnnotatedString {
        append(fullName.substring(0, firstMatch))
        withStyle(SpanStyle(color = Color(0xFF8CD9FF), fontWeight = FontWeight.SemiBold)) {
            append(fullName.substring(firstMatch, matchEnd))
        }
        append(fullName.substring(matchEnd))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DmThreadView(
    name: String,
    messages: List<com.mkras.zulip.data.local.entity.MessageEntity>,
    scrollToMessageId: Long?,
    conversationKey: String,
    typingText: String?,
    onMessagesRendered: (List<Long>) -> Unit,
    onBack: () -> Unit,
    onScrollConsumed: () -> Unit,
    currentUserEmail: String,
    serverUrl: String,
    imageAuthHeader: String?,
    compactMode: Boolean,
    mentionCandidates: List<DirectMessageCandidate> = emptyList(),
    onRequestMentionCandidates: () -> Unit = {},
    isDirectMessageMuted: (String) -> Boolean = { false },
    onSetDirectMessageMuted: (String, Boolean) -> Unit = { _, _ -> },
    onUploadAttachmentMessage: UploadAttachmentMessage = { _, _, _, _, onSuccess, _ -> onSuccess() },
    onSendMessage: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onAddReaction: (Long, EmojiHelper.ReactionSelection) -> Unit = { _, _ -> },
    onRemoveReaction: (Long, EmojiHelper.ReactionSelection) -> Unit = { _, _ -> },
    onEditMessage: (Long, String) -> Unit = { _, _ -> },
    onDeleteMessage: (Long) -> Unit = { _ -> },
    pendingDirectMessageContent: String? = null,
    onPendingDirectMessageContentConsumed: () -> Unit = {},
    canModerateAllMessages: Boolean = false,
    currentUserId: Long? = null,
    onAttachmentOperationStart: () -> Unit = {},
    onAttachmentOperationEnd: () -> Unit = {},
    sendMessageError: String? = null,
    resyncError: String? = null,
    onClearSendError: () -> Unit = {},
    onClearResyncError: () -> Unit = {},
    onSaveDmScrollPosition: (String, Int) -> Unit = { _, _ -> },
    customEmojiById: Map<String, String> = emptyMap(),
    customEmojiByName: Map<String, String> = emptyMap(),
    customEmojis: List<EmojiHelper.CustomEmojiItem> = emptyList()
) {
    val rowBottom = if (compactMode) 10.dp else 14.dp
    val gap = if (compactMode) 4.dp else 6.dp
    val senderStyle = if (compactMode) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    val titleStyle = if (compactMode) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val metaStyle = MaterialTheme.typography.labelSmall

    var expandedMenuMessageId by remember { mutableStateOf<Long?>(null) }
    var emojiPickerMessageId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val isImeVisible = WindowInsets.Companion.isImeVisible
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val showJumpToLatestFab by remember {
        derivedStateOf {
            messages.isNotEmpty() &&
                listState.layoutInfo.totalItemsCount > 0 &&
                listState.firstVisibleItemIndex < messages.size - 5
        }
    }
    val isNearBottom by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 0) true
            else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= total - 2
            }
        }
    }
    var autoScrolledToLatest by remember(conversationKey) { mutableStateOf(false) }
    var highlightedMessageId by remember(conversationKey) { mutableStateOf<Long?>(null) }
    var isUploadingAttachment by remember { mutableStateOf(false) }
    var pendingMentionText by remember(conversationKey) { mutableStateOf<String?>(null) }
    var editingMessageId by remember { mutableStateOf<Long?>(null) }
    var editingMessageDraft by remember { mutableStateOf("") }
    var isCurrentDirectMessageMuted by remember(conversationKey) {
        mutableStateOf(isDirectMessageMuted(conversationKey))
    }

    LaunchedEffect(conversationKey) {
        isCurrentDirectMessageMuted = isDirectMessageMuted(conversationKey)
    }
    val attachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        onAttachmentOperationEnd()
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingAttachment = true
            val attachment = readPickedAttachment(context, uri)
            if (attachment == null) {
                isUploadingAttachment = false
                Toast.makeText(context, "Nie udało się odczytać pliku", Toast.LENGTH_SHORT).show()
                return@launch
            }
            onUploadAttachmentMessage("private", conversationKey, null, attachment, {
                isUploadingAttachment = false
                Toast.makeText(context, "Załącznik wysłany", Toast.LENGTH_SHORT).show()
            }, { msg ->
                isUploadingAttachment = false
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            })
        }
    }

    LaunchedEffect(conversationKey, if (autoScrolledToLatest) Unit else messages.size) {
        if (!autoScrolledToLatest && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            autoScrolledToLatest = true
        }
    }

    LaunchedEffect(conversationKey, messages.lastOrNull()?.id) {
        if (messages.isNotEmpty() && autoScrolledToLatest) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(conversationKey, messages.size, currentUserEmail) {
        if (messages.isNotEmpty()) {
            val unreadIncomingIds = messages
                .asSequence()
                .filter { !it.isRead }
                .filterNot { it.senderEmail.equals(currentUserEmail, ignoreCase = true) }
                .map { it.id }
                .toList()
            if (unreadIncomingIds.isNotEmpty()) {
                onMessagesRendered(unreadIncomingIds)
            }
        }
    }

    LaunchedEffect(scrollToMessageId, messages.size) {
        val targetId = scrollToMessageId ?: return@LaunchedEffect
        val index = messages.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            listState.scrollToItem(index)
            highlightedMessageId = targetId
            onScrollConsumed()
            delay(1800)
            if (highlightedMessageId == targetId) highlightedMessageId = null
        }
    }

    LaunchedEffect(isImeVisible, conversationKey, messages.lastOrNull()?.id) {
        if (isImeVisible && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(if (compactMode) 18.dp else 22.dp),
            color = ThreadBackdrop
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = if (compactMode) 6.dp else 8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Wróć", tint = Color(0xFF8CD9FF))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = titleStyle, color = NameColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "Prywatna rozmowa", style = metaStyle, color = SubtleColor)
                }
                IconButton(onClick = {
                    val newMuted = !isCurrentDirectMessageMuted
                    isCurrentDirectMessageMuted = newMuted
                    onSetDirectMessageMuted(conversationKey, newMuted)
                }) {
                    Icon(
                        imageVector = if (isCurrentDirectMessageMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                        contentDescription = if (isCurrentDirectMessageMuted) "DM wyciszony" else "Wycisz DM",
                        tint = if (isCurrentDirectMessageMuted) Color(0xFFFF7070) else Color(0xFF8CD9FF)
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = rowBottom, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            items(
                items = messages,
                key = { it.id },
                contentType = { it.senderEmail.equals(currentUserEmail, ignoreCase = true) }
            ) { message ->
                val isOwnMessage = remember(message.senderEmail, currentUserEmail) {
                    message.senderEmail.equals(currentUserEmail, ignoreCase = true)
                }
                val isHighlighted = highlightedMessageId == message.id
                val avatarSize = if (compactMode) 22.dp else 26.dp
                val avatarInitials = remember(message.senderFullName) {
                    message.senderFullName
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.first().uppercaseChar().toString() }
                        .ifBlank { "?" }
                }
                val imageUrl = remember(message.id, serverUrl) {
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!isOwnMessage) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.clickable {
                                expandedMenuMessageId = message.id
                            }) {
                                AvatarImage(avatarUrl = message.avatarUrl, initials = avatarInitials, size = avatarSize)
                            }
                            IconButton(
                                onClick = { expandedMenuMessageId = message.id },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Menu wiadomości",
                                    tint = Color(0xFF8CD9FF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(6.dp))
                    }
                    Box(modifier = Modifier.fillMaxWidth(if (isOwnMessage) 0.82f else 1f)) {
                        MessageBubble(
                            message = message,
                            isOwnMessage = isOwnMessage,
                            canModerateAllMessages = canModerateAllMessages,
                            isHighlighted = isHighlighted,
                            imageUrl = imageUrl,
                            imageAuthHeader = imageAuthHeader,
                            compactMode = compactMode,
                            senderStyle = senderStyle,
                            onLongClick = { expandedMenuMessageId = message.id },
                            onEditMessage = {
                                editingMessageId = message.id
                                editingMessageDraft = plainContent
                            },
                            onDeleteMessage = { onDeleteMessage(message.id) },
                            onAddReaction = { reaction -> onAddReaction(message.id, reaction) },
                            onToggleReaction = { reaction, reactedByCurrentUser ->
                                if (reactedByCurrentUser) onRemoveReaction(message.id, reaction)
                                else onAddReaction(message.id, reaction)
                            },
                            onMentionRequest = { pendingMentionText = "@**${message.senderFullName}**" },
                            onQuoteRequest = {
                                val quoteBlock = plainContent.lines()
                                    .filter { it.isNotBlank() }
                                    .joinToString("\n") { "> $it" }
                                if (quoteBlock.isNotBlank()) {
                                    pendingMentionText = "$quoteBlock\n\n"
                                }
                            },
                            expandedMenuMessageId = expandedMenuMessageId,
                            onMenuDismiss = { expandedMenuMessageId = null },
                            onEmojiPickerOpen = { emojiPickerMessageId = message.id },
                            currentUserId = currentUserId,
                            customEmojiById = customEmojiById,
                            customEmojiByName = customEmojiByName
                        )
                    }
                    if (isOwnMessage) {
                        IconButton(
                            onClick = { expandedMenuMessageId = message.id },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Menu wiadomości",
                                tint = Color(0xFF8CD9FF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(avatarSize + 6.dp))
                    }
                }
            }
        }
        if (showJumpToLatestFab) {
            FloatingActionButton(
                onClick = { coroutineScope.launch { listState.animateScrollToItem(messages.size - 1) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                containerColor = Color(0xFF2B5A83),
                contentColor = Color(0xFF8CD9FF)
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Skocz do najnowszych")
            }
        }
        } // close Box
        if (resyncError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF5A2B2B)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Błąd synchronizacji: $resyncError",
                        color = Color(0xFFFFB3B3),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearResyncError) {
                        Text("OK", color = Color(0xFFFFB3B3), fontSize = 10.sp)
                    }
                }
            }
        }
        if (sendMessageError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF5A3B2B)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Błąd wysyłania: $sendMessageError",
                        color = Color(0xFFFFA8B5),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearSendError) {
                        Text("OK", color = Color(0xFFFFA8B5), fontSize = 10.sp)
                    }
                }
            }
        }
        if (typingText != null) {
            Text(text = typingText, color = Color(0xFF8CD9FF), modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp))
        }
        if (isUploadingAttachment) {
            Text(text = "Wysyłanie pliku...", color = Color(0xFF8CD9FF), modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp), style = metaStyle)
        }
        if (editingMessageId != null) {
            AlertDialog(
                onDismissRequest = { editingMessageId = null },
                title = { Text("Edytuj wiadomość") },
                text = {
                    OutlinedTextField(
                        value = editingMessageDraft,
                        onValueChange = { editingMessageDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val messageId = editingMessageId
                        val trimmed = editingMessageDraft.trim()
                        if (messageId == null) return@Button
                        if (trimmed.isBlank()) {
                            Toast.makeText(context, "Treść nie może być pusta", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onEditMessage(messageId, trimmed)
                        editingMessageId = null
                    }) {
                        Text("Zapisz")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { editingMessageId = null }) {
                        Text("Anuluj")
                    }
                }
            )
        }
        MessageComposeInput(
            onSend = { content -> onSendMessage("private", conversationKey, content, null) },
            compactMode = compactMode,
            modifier = Modifier.navigationBarsPadding(),
            enabled = !isUploadingAttachment,
            mentionCandidates = mentionCandidates,
            onRequestMentionCandidates = onRequestMentionCandidates,
            pendingInsertionText = pendingDirectMessageContent ?: pendingMentionText,
            onPendingInsertionConsumed = {
                if (!pendingDirectMessageContent.isNullOrBlank()) {
                    onPendingDirectMessageContentConsumed()
                } else {
                    pendingMentionText = null
                }
            },
            onInputFocused = {
                if (messages.isNotEmpty()) {
                    coroutineScope.launch {
                        listState.animateScrollToItem(messages.lastIndex)
                    }
                }
            },
            onAddAttachment = {
                onAttachmentOperationStart()
                attachmentLauncher.launch(arrayOf("*/*"))
            },
            onGenerateVideoCall = {
                val timestamp = System.currentTimeMillis()
                val meetingId = "call_${timestamp}"
                val videoLink = "<b>📞 Video Call:</b> <a href=\"https://jitsi.example.com/$meetingId\">https://jitsi.example.com/$meetingId</a>"
                onSendMessage("private", conversationKey, videoLink, null)
            }
        )
    }

    emojiPickerMessageId?.let { msgId ->
        EmojiPickerDialog(
            onEmojiSelected = { reaction ->
                onAddReaction(msgId, reaction)
                emojiPickerMessageId = null
            },
            onDismiss = { emojiPickerMessageId = null },
            customEmojis = customEmojis
        )
    }
}

@ExperimentalFoundationApi
@Composable
private fun MessageBubble(
    message: MessageEntity,
    isOwnMessage: Boolean,
    canModerateAllMessages: Boolean,
    isHighlighted: Boolean,
    imageUrl: String?,
    imageAuthHeader: String?,
    compactMode: Boolean,
    senderStyle: androidx.compose.ui.text.TextStyle,
    onLongClick: () -> Unit,
    onEditMessage: () -> Unit,
    onDeleteMessage: () -> Unit,
    onAddReaction: (EmojiHelper.ReactionSelection) -> Unit,
    onToggleReaction: (EmojiHelper.ReactionSelection, Boolean) -> Unit,
    onMentionRequest: () -> Unit,
    onQuoteRequest: () -> Unit,
    expandedMenuMessageId: Long?,
    onMenuDismiss: () -> Unit,
    onEmojiPickerOpen: () -> Unit,
    currentUserId: Long?,
    customEmojiById: Map<String, String>,
    customEmojiByName: Map<String, String>
) {
    val canManageMessage = isOwnMessage || canModerateAllMessages
    Column(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongClick),
        horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(0.80f).background(
                color = when {
                    isHighlighted && isOwnMessage -> Color(0xFF3A4A44)
                    isHighlighted -> Color(0xFF1F3A4D)
                    isOwnMessage -> OutgoingBubble
                    else -> CardBg
                },
                shape = RoundedCornerShape(12.dp)
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (!isOwnMessage) {
                    Text(text = message.senderFullName, style = senderStyle, fontWeight = FontWeight.Bold, color = NameColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onMentionRequest() }.padding(bottom = 2.dp))
                }
                Spacer(modifier = Modifier.height(2.dp))
                
                // Render HTML content with quotes, bold, tables etc
                HtmlText(
                    html = message.content,
                    modifier = Modifier.fillMaxWidth(),
                    compactMode = compactMode,
                    isOutgoing = isOwnMessage,
                    onLongPress = onLongClick
                )

                val reactions = com.mkras.zulip.presentation.chat.EmojiHelper
                    .summarizeReactionSummary(message.reactionSummary, currentUserId)
                if (reactions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reactions.forEach { aggregate ->
                            val token = aggregate.token
                            val display = com.mkras.zulip.presentation.chat.EmojiHelper
                                .resolveReactionDisplay(
                                    token = token,
                                    customEmojiById = customEmojiById,
                                    customEmojiByName = customEmojiByName
                                )
                            Surface(
                                modifier = Modifier.clickable {
                                    onToggleReaction(
                                        com.mkras.zulip.presentation.chat.EmojiHelper.toReactionSelection(token),
                                        aggregate.reactedByCurrentUser
                                    )
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = if (aggregate.reactedByCurrentUser) Color(0x4A8CD9FF) else Color(0x2A8CD9FF)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    if (!display.imageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = display.imageUrl,
                                            contentDescription = token.name,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else {
                                        Text(
                                            text = display.emojiText ?: "",
                                            color = ContentColor,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    if (aggregate.count > 1) {
                                        Text(
                                            text = " ${aggregate.count}",
                                            color = ContentColor,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (isOwnMessage) {
                    Text(
                        text = formatMessageTime(message.timestampSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFC9D7CF),
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                    )
                }
                if (imageUrl != null) {
                    AttachmentPreview(imageUrl = imageUrl, authHeader = imageAuthHeader, compactMode = compactMode, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
        
        DropdownMenu(expanded = expandedMenuMessageId == message.id, onDismissRequest = onMenuDismiss) {
            DropdownMenuItem(text = { Text("\uD83D\uDE0A Reakcja") }, onClick = { onMenuDismiss(); onEmojiPickerOpen() })
            DropdownMenuItem(text = { Text("@ Wspomnij") }, onClick = { onMenuDismiss(); onMentionRequest() })
            DropdownMenuItem(text = { Text("\uD83D\uDCDD Cytuj") }, onClick = { onMenuDismiss(); onQuoteRequest() })
            if (canManageMessage) {
                DropdownMenuItem(text = { Text("\u270F\uFE0F Edytuj") }, onClick = { onMenuDismiss(); onEditMessage() })
                DropdownMenuItem(text = { Text("\uD83D\uDDD1\uFE0F Usuń") }, onClick = { onMenuDismiss(); onDeleteMessage() })
            }
        }
    }
}

@Composable
private fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    compactMode: Boolean,
    isOutgoing: Boolean = false,
    onLongPress: () -> Unit = {}
) {
    val textColor = if (isOutgoing) OutgoingText.toArgb() else ContentColor.toArgb()
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(ImagesPlugin.create())
            .usePlugin(CoilImagesPlugin.create(context))
            .build()
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                textSize = if (compactMode) 12f else 14f
                setLineSpacing(2f, 1f)
                movementMethod = LinkMovementMethod.getInstance()
                isLongClickable = true
                setOnLongClickListener {
                    onLongPress()
                    true
                }
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.textSize = if (compactMode) 12f else 14f
            markwon.setMarkdown(tv, html)
        }
    )
}

private fun formatMessageTime(timestampSeconds: Long): String {
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestampSeconds * 1000))
}

@Composable
private fun AttachmentPreview(imageUrl: String, authHeader: String?, compactMode: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageModel = remember(imageUrl, authHeader) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .apply { if (!authHeader.isNullOrBlank()) addHeader("Authorization", authHeader) }
            .crossfade(false)
            .build()
    }

    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var isFullscreenOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth().height(if (compactMode) 140.dp else 180.dp).background(Color(0x1A8CD9FF), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (hasError) {
            Text(text = "🖼️ Nie można załadować", color = Color(0xFF8CD9FF), style = MaterialTheme.typography.labelSmall)
        } else {
            AsyncImage(
                model = imageModel,
                contentDescription = "Załączony obraz",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable { isFullscreenOpen = true },
                onLoading = { isLoading = true },
                onSuccess = { isLoading = false },
                onError = { isLoading = false; hasError = true }
            )
            if (isLoading) CircularProgressIndicator(color = Color(0xFF8CD9FF), strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }

    if (isFullscreenOpen) {
        ImageFullscreenDialog(imageModel = imageModel) { isFullscreenOpen = false }
    }
}

@Composable
private fun ImageFullscreenDialog(
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
                .background(Color(0xE6000000))
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
    MD_IMAGE_REGEX
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
        it.contains(IMAGE_EXT_REGEX) || it.contains("/user_uploads/", ignoreCase = true)
    } ?: return null

    return when {
        imagePath.startsWith("http", ignoreCase = true) -> imagePath
        imagePath.startsWith("/") -> serverUrl.trimEnd('/') + imagePath
        else -> serverUrl.trimEnd('/') + "/" + imagePath.trimStart('/')
    }
}

@Composable
private fun AvatarImage(avatarUrl: String, initials: String, size: androidx.compose.ui.unit.Dp) {
    var avatarLoadFailed by remember(avatarUrl) { mutableStateOf(false) }
    if (avatarUrl.isNotBlank() && !avatarLoadFailed) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
            onError = { avatarLoadFailed = true }
        )
    } else {
        Surface(modifier = Modifier.size(size), shape = CircleShape, color = Color(0xFF2D4B6E)) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = initials, color = Color(0xFFBDD5F2), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun UsersScreen(
    people: List<DirectMessageCandidate>,
    presenceByEmail: Map<String, String>,
    isLoading: Boolean,
    error: String?,
    onEnsureLoaded: () -> Unit,
    onSelectUser: (DirectMessageCandidate) -> Unit,
    serverUrl: String = "",
    compactMode: Boolean
) {
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { onEnsureLoaded() }

    val filteredPeople = remember(people, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) people
        else people.filter {
            it.fullName.lowercase().contains(q) || it.email.lowercase().contains(q)
        }.sortedWith(compareBy(
            { !it.fullName.lowercase().startsWith(q) },
            { it.fullName.lowercase() }
        ))
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Szukaj pracownika", maxLines = 1) },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = SubtleColor)
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Wyczyść", tint = SubtleColor)
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF16273D),
                unfocusedContainerColor = Color(0xFF16273D),
                focusedIndicatorColor = Color(0xFF8CD9FF),
                unfocusedIndicatorColor = Color(0xFF2A4B6E),
                focusedTextColor = NameColor,
                unfocusedTextColor = NameColor,
                cursorColor = Color(0xFF8CD9FF)
            )
        )

        when {
            isLoading && people.isEmpty() -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF8CD9FF))
                }
            }
            error != null && people.isEmpty() -> {
                Text(text = error, color = Color(0xFFFFA8B5), style = MaterialTheme.typography.bodySmall)
            }
            filteredPeople.isEmpty() -> {
                Text(
                    text = "Brak wyników.",
                    color = SubtleColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 6.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(filteredPeople, key = { it.userId }) { person ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectUser(person) },
                            color = CardBg,
                            shape = RoundedCornerShape(if (compactMode) 16.dp else 18.dp),
                            border = BorderStroke(1.dp, BubbleOutline)
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = if (compactMode) 10.dp else 14.dp,
                                    vertical = if (compactMode) 8.dp else 12.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val avatarSize = if (compactMode) 28.dp else 36.dp
                                AvatarImage(
                                    avatarUrl = person.avatarUrl,
                                    initials = person.fullName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    size = avatarSize
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = if (compactMode) 8.dp else 12.dp)
                                ) {
                                    Text(
                                        text = highlightedName(person.fullName, query.trim()),
                                        color = NameColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium,
                                        style = if (compactMode) MaterialTheme.typography.bodyMedium
                                            else MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
