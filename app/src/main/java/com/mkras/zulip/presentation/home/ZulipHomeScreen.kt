package com.mkras.zulip.presentation.home

import com.mkras.zulip.BuildConfig
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInbox
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.mkras.zulip.core.security.StoredAuth
import com.mkras.zulip.NotificationNavigationTarget
import com.mkras.zulip.R
import com.mkras.zulip.presentation.auth.rememberSharedImageAuthHeader
import com.mkras.zulip.presentation.chat.ChatScreen
import com.mkras.zulip.presentation.chat.ChatViewModel
import com.mkras.zulip.presentation.channels.ChannelsScreen
import com.mkras.zulip.presentation.channels.ChannelsViewModel
import com.mkras.zulip.presentation.search.SearchScreen
import com.mkras.zulip.core.update.GitHubReleaseInfo
import com.mkras.zulip.core.update.GitHubUpdateManager
import kotlinx.coroutines.launch

private data class RootTab(
    val label: String,
    val iconVector: androidx.compose.ui.graphics.vector.ImageVector
)

private val HOME_TABS = listOf(
    RootTab("DMs", Icons.Rounded.ChatBubbleOutline),
    RootTab("Kanały", Icons.Rounded.Forum),
    RootTab("Wszystkie", Icons.Rounded.AllInbox),
    RootTab("Szukaj", Icons.AutoMirrored.Rounded.ManageSearch),
    RootTab("★ Gwiazdki", Icons.Rounded.Star),
    RootTab("Ustawienia", Icons.Rounded.Settings)
)
private val TabBarBg      = Color(0xCC0B1728)
private val TabBarBorder  = Color(0x334A8AC2)
private val TabSelected   = Color(0xFF8CD9FF)
private val TabUnselected = Color(0xFFA8B4C7)
private val TabBadgeBg    = Color(0xFF8A3B2E)
private const val UPDATE_REPO_OWNER = "vbamarcin"
private const val UPDATE_REPO_NAME = "Zulip"
private const val BUILTIN_GITHUB_TOKEN =
    "github_pat_" +
        "11A3CKDDI01RVOg20V6RoH_DxglM1CEiLszqVTSsjRsdD4Cew0JvUWKwlyIGlOyAswTQVT4HVTNLGZVmWF"

private fun moderationErrorMessage(raw: String): String {
    val normalized = raw.lowercase()
    return when {
        "forbidden" in normalized || "status 403" in normalized -> "Brak uprawnień do tej operacji"
        "bad request" in normalized || "status 400" in normalized -> "Nie można wykonać operacji dla tej wiadomości"
        normalized.isBlank() -> "Operacja nie powiodła się"
        else -> raw
    }
}

@Composable
fun ZulipHomeScreen(
    session: StoredAuth,
    onLogout: () -> Unit,
    initialCompactMode: Boolean = true,
    onSaveCompactMode: (Boolean) -> Unit = {},
    initialFontScale: Float = 1.0f,
    onSaveFontScale: (Float) -> Unit = {},
    initialMarkdownEnabled: Boolean = true,
    onSaveMarkdownEnabled: (Boolean) -> Unit = {},
    initialNotificationsEnabled: Boolean = true,
    onSaveNotificationsEnabled: (Boolean) -> Unit = {},
    initialDmNotificationsEnabled: Boolean = true,
    onSaveDmNotificationsEnabled: (Boolean) -> Unit = {},
    initialChannelNotificationsEnabled: Boolean = true,
    onSaveChannelNotificationsEnabled: (Boolean) -> Unit = {},
    onResetAllNotifications: () -> Unit = {},
    isDirectMessageMuted: (String) -> Boolean = { false },
    onSetDirectMessageMuted: (String, Boolean) -> Unit = { _, _ -> },
    isChannelMuted: (String) -> Boolean = { false },
    onSetChannelMuted: (String, Boolean) -> Unit = { _, _ -> },
    isChannelDisabled: (String) -> Boolean = { false },
    onSetChannelDisabled: (String, Boolean) -> Unit = { _, _ -> },
    getMutedChannels: () -> Set<String> = { emptySet() },
    getDisabledChannels: () -> Set<String> = { emptySet() },
    notificationTarget: NotificationNavigationTarget? = null,
    onNotificationTargetConsumed: () -> Unit = {},
    initialBiometricLockEnabled: Boolean = false,
    onSaveBiometricLockEnabled: (Boolean) -> Unit = {},
    initialAutoUpdateEnabled: Boolean = true,
    onSaveAutoUpdateEnabled: (Boolean) -> Unit = {},
    chatViewModel: ChatViewModel = hiltViewModel(),
    channelsViewModel: ChannelsViewModel = hiltViewModel()
) {
    val chatUiState = chatViewModel.uiState.collectAsStateWithLifecycle().value
    val channelsUiState = channelsViewModel.uiState.collectAsStateWithLifecycle().value
    var compactMode by rememberSaveable { mutableStateOf(initialCompactMode) }
    var fontScale by rememberSaveable { mutableStateOf(initialFontScale.coerceIn(0.85f, 1.30f)) }
    var markdownEnabled by rememberSaveable { mutableStateOf(initialMarkdownEnabled) }
    var notificationsEnabled by rememberSaveable { mutableStateOf(initialNotificationsEnabled) }
    var dmNotificationsEnabled by rememberSaveable { mutableStateOf(initialDmNotificationsEnabled) }
    var channelNotificationsEnabled by rememberSaveable { mutableStateOf(initialChannelNotificationsEnabled) }
    var biometricLockEnabled by rememberSaveable { mutableStateOf(initialBiometricLockEnabled) }
    var autoUpdateEnabled by rememberSaveable { mutableStateOf(initialAutoUpdateEnabled) }
    var isCheckingUpdate by rememberSaveable { mutableStateOf(false) }
    var isInstallingUpdate by rememberSaveable { mutableStateOf(false) }
    var updateStatusText by rememberSaveable { mutableStateOf<String?>(null) }
    var checkedUpdateThisSession by rememberSaveable { mutableStateOf(false) }
    var availableRelease by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gitHubToken = BUILTIN_GITHUB_TOKEN

    val hasNotificationPermission = remember(context) {
        {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(notificationsEnabled) {
        if (notificationsEnabled && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val imageAuthHeader = rememberSharedImageAuthHeader()

    fun checkForUpdates(manual: Boolean = false) {
        coroutineScope.launch {
            isCheckingUpdate = true
            updateStatusText = if (manual) "Sprawdzanie aktualizacji..." else null
            val result = GitHubUpdateManager.checkForUpdate(
                owner = UPDATE_REPO_OWNER,
                repo = UPDATE_REPO_NAME,
                currentVersionName = BuildConfig.VERSION_NAME,
                token = gitHubToken
            )
            isCheckingUpdate = false
            result.onSuccess { release ->
                availableRelease = release
                updateStatusText = if (release != null) {
                    "Dostępna aktualizacja: ${release.tagName}"
                } else {
                    "Aplikacja jest aktualna"
                }
            }.onFailure { error ->
                updateStatusText = error.message ?: "Nie udało się sprawdzić aktualizacji"
            }
        }
    }

    LaunchedEffect(autoUpdateEnabled) {
        if (!checkedUpdateThisSession && autoUpdateEnabled) {
            checkedUpdateThisSession = true
            checkForUpdates(manual = false)
        }
    }

    val unreadDm = chatUiState.privateMessages.count { !it.isRead }
    val unreadStreamMessages = chatUiState.allMessages
        .asSequence()
        .filter {
            it.messageType == "stream" &&
                !it.isRead &&
                !isChannelDisabled(it.streamName.orEmpty())
        }
        .toList()

    val unreadByStream = unreadStreamMessages
        .mapNotNull { message -> message.streamName }
        .groupingBy { it }
        .eachCount()

    val unreadByTopic = unreadStreamMessages
        .asSequence()
        .filter { it.streamName != null && it.topic.isNotBlank() }
        .map { "${it.streamName}::${it.topic}" }
        .groupingBy { it }
        .eachCount()

    val unreadAggregates = HomeUnreadAggregates(
        unreadDmCount = unreadDm,
        unreadChannelCount = unreadStreamMessages.size,
        unreadByStream = unreadByStream,
        unreadByTopic = unreadByTopic
    )

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            channelsViewModel.onChannelsVisible()
        }
    }

    LaunchedEffect(notificationTarget?.messageId) {
        val target = notificationTarget ?: return@LaunchedEffect
        if (target.messageType == "private") {
            val key = target.conversationKey.orEmpty()
            if (key.isNotBlank()) {
                selectedTab = 0
                chatViewModel.openConversationFromNotification(
                    conversationKey = key,
                    conversationTitle = target.conversationTitle,
                    messageId = target.messageId
                )
            }
        } else if (target.messageType == "stream") {
            val stream = target.streamName.orEmpty()
            if (stream.isNotBlank()) {
                selectedTab = 1
                channelsViewModel.openFromAllMessages(
                    streamName = stream,
                    topicName = target.topic.orEmpty(),
                    messageId = target.messageId
                )
            }
        }
        onNotificationTargetConsumed()
    }

    val baseTypography = MaterialTheme.typography
    val scaledTypo = remember(fontScale, baseTypography) { scaledTypography(baseTypography, fontScale) }
    MaterialTheme(
        typography = scaledTypo
    ) {
    Scaffold(
        bottomBar = {
            // Completely custom bottom bar to avoid clipping issues
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compactMode) 76.dp else 88.dp)
                        .background(TabBarBg, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .border(1.dp, TabBarBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HOME_TABS.forEachIndexed { index, tab ->
                        val isSelected = selectedTab == index
                        val badgeCount = when (index) {
                            0 -> unreadAggregates.unreadDmCount
                            1 -> unreadAggregates.unreadChannelCount
                            else -> 0
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedTab = index }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = tab.iconVector,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) TabSelected else TabUnselected,
                                    modifier = Modifier.size(if (compactMode) 20.dp else 24.dp)
                                )
                                if (badgeCount > 0) {
                                    Surface(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.TopEnd),
                                        shape = CircleShape,
                                        color = TabBadgeBg
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = tab.label,
                                fontSize = if (compactMode) 9.sp else 10.sp,
                                color = if (isSelected) TabSelected else TabUnselected,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF08101F), Color(0xFF13233A), Color(0xFF06111D))
                    )
                )
                .padding(padding)
                .padding(if (compactMode) 12.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(if (compactMode) 20.dp else 24.dp),
                color = Color(0x382A3E5A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x334987C3))
            ) {
                Column(
                    modifier = Modifier.padding(if (compactMode) 16.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 12.dp)
                ) {
                    Text(
                        text = HOME_TABS[selectedTab].label,
                        color = Color(0xFFF2F6FF),
                        fontWeight = FontWeight.SemiBold
                    )
                    when (selectedTab) {
                        0 -> ChatScreen(
                            uiState = chatUiState,
                            onMessagesRendered = chatViewModel::onMessagesRendered,
                            onResumeResync = chatViewModel::resyncOnResume,
                            onSelectConversation = chatViewModel::selectConversation,
                            onBackToList = chatViewModel::backToConversationList,
                            currentUserEmail = session.email,
                            serverUrl = session.serverUrl,
                            imageAuthHeader = imageAuthHeader,
                            compactMode = compactMode,
                            onUploadAttachmentMessage = { type, to, topic, attachment, onSuccess, onError ->
                                chatViewModel.uploadAttachmentAndSendMessage(
                                    type = type,
                                    to = to,
                                    topic = topic,
                                    attachment = attachment,
                                    onSuccess = onSuccess,
                                    onError = onError
                                )
                            },
                            onOpenNewDmPicker = chatViewModel::openNewDmPicker,
                            onCloseNewDmPicker = chatViewModel::closeNewDmPicker,
                            onNewDmQueryChange = chatViewModel::updateNewDmQuery,
                            onSelectNewDmPerson = chatViewModel::startDirectMessage,
                            mentionCandidates = chatUiState.newDmPeople,
                            onRequestMentionCandidates = chatViewModel::ensureMentionCandidatesLoaded,
                            onSendMessage = { type, to, content, topic ->
                                chatViewModel.sendMessage(type, to, content, topic)
                            },
                            onAddReaction = { msgId, emoji ->
                                chatViewModel.addReaction(msgId, emoji)
                            },
                            onEditMessage = { msgId ->
                                // Edit UI to be implemented
                            },
                            onDeleteMessage = { msgId ->
                                chatViewModel.deleteMessage(msgId)
                            },
                            isDirectMessageMuted = isDirectMessageMuted,
                            onSetDirectMessageMuted = onSetDirectMessageMuted,
                            onScrollConsumed = chatViewModel::clearDmScrollTarget,
                            pendingDirectMessageContent = chatUiState.pendingDirectMessageContent,
                            onPendingDirectMessageContentConsumed = chatViewModel::consumePendingDirectMessageContent,
                            canModerateAllMessages = chatUiState.canModerateAllMessages,
                            typingText = chatUiState.typingText
                        )
                        1 -> ChannelsScreen(
                            uiState = channelsUiState,
                            onStreamSelected = channelsViewModel::onStreamSelected,
                            onTopicSelected = channelsViewModel::onTopicSelected,
                            onBackToStreams = channelsViewModel::backToStreams,
                            onBackToTopics = channelsViewModel::backToTopics,
                            onRetry = channelsViewModel::onChannelsVisible,
                            compactMode = compactMode,
                            markdownEnabled = markdownEnabled,
                            currentUserEmail = session.email,
                            serverUrl = session.serverUrl,
                            imageAuthHeader = imageAuthHeader,
                            onUploadAttachmentMessage = { type, to, topic, attachment, onSuccess, onError ->
                                chatViewModel.uploadAttachmentAndSendMessage(
                                    type = type,
                                    to = to,
                                    topic = topic,
                                    attachment = attachment,
                                    onSuccess = onSuccess,
                                    onError = onError
                                )
                            },
                            onSendChannelMessage = { streamName, topicName, content ->
                                chatViewModel.sendMessage(
                                    type = "stream",
                                    to = streamName,
                                    content = content,
                                    topic = topicName,
                                    onSuccess = {
                                        channelsViewModel.refreshSelectedNarrow()
                                    }
                                )
                            },
                            onAddReaction = { msgId, emoji -> chatViewModel.addReaction(msgId, emoji) },
                            onEditMessage = { msgId -> chatViewModel.editMessage(msgId, "") },
                            onDeleteMessage = { msgId, onSuccess, onError ->
                                chatViewModel.deleteMessage(
                                    messageId = msgId,
                                    onSuccess = onSuccess,
                                    onError = { raw -> onError(moderationErrorMessage(raw)) }
                                )
                            },
                            onMoveMessageToTopic = { msgId, content, newTopic, targetStreamId, onSuccess, onError ->
                                chatViewModel.editMessage(
                                    messageId = msgId,
                                    newContent = content,
                                    newTopic = newTopic,
                                    newStreamId = targetStreamId,
                                    onSuccess = onSuccess,
                                    onError = { raw ->
                                        val message = moderationErrorMessage(raw)
                                        onError(message)
                                    }
                                )
                            },
                            onLoadTopicsForStream = channelsViewModel::loadTopicsForStream,
                            onMessagesRendered = chatViewModel::onMessagesRendered,
                            onScrollConsumed = channelsViewModel::clearScrollTarget,
                            onLoadOlderMessages = channelsViewModel::loadOlderMessagesForSelectedTopic,
                            onRefreshLatestMessages = channelsViewModel::refreshSelectedNarrow,
                            unreadByStream = unreadAggregates.unreadByStream,
                            unreadByTopic = unreadAggregates.unreadByTopic,
                            mentionCandidates = chatUiState.newDmPeople,
                            onRequestMentionCandidates = chatViewModel::ensureMentionCandidatesLoaded,
                            isChannelMuted = isChannelMuted,
                            onSetChannelMuted = onSetChannelMuted,
                            isChannelDisabled = isChannelDisabled,
                            onSetChannelDisabled = onSetChannelDisabled,
                            canModerateAllMessages = chatUiState.canModerateAllMessages,
                            onForwardToDm = { forwardedContent ->
                                selectedTab = 0
                                chatViewModel.forwardToNewDirectMessage(forwardedContent)
                            }
                        )
                        2 -> AllMessagesScreen(
                            messages = chatUiState.allMessages,
                            compactMode = compactMode,
                            serverUrl = session.serverUrl,
                            imageAuthHeader = imageAuthHeader,
                            onMessageClick = { message ->
                                if (message.messageType == "private") {
                                    selectedTab = 0
                                    chatViewModel.openConversationFromMessage(message)
                                } else {
                                    val streamName = message.streamName
                                    if (streamName != null && message.topic.isNotBlank()) {
                                        selectedTab = 1
                                        channelsViewModel.openFromAllMessages(streamName, message.topic, message.id)
                                    }
                                }
                            }
                        )
                        3 -> SearchScreen(
                            uiState = chatUiState,
                            compactMode = compactMode,
                            onQueryChange = chatViewModel::updateSearchQuery,
                            onSearch = chatViewModel::submitSearch
                        )
                        4 -> AllMessagesScreen(
                            messages = chatUiState.starredMessages,
                            compactMode = compactMode,
                            serverUrl = session.serverUrl,
                            imageAuthHeader = imageAuthHeader,
                            onMessageClick = { message ->
                                if (message.messageType == "private") {
                                    selectedTab = 0
                                    chatViewModel.openConversationFromMessage(message)
                                } else {
                                    val streamName = message.streamName
                                    if (streamName != null && message.topic.isNotBlank()) {
                                        selectedTab = 1
                                        channelsViewModel.openFromAllMessages(streamName, message.topic, message.id)
                                    }
                                }
                            }
                        )
                        else -> SettingsPanel(
                            session = session,
                            onLogout = onLogout,
                            compactMode = compactMode,
                            fontScale = fontScale,
                            markdownEnabled = markdownEnabled,
                            notificationsEnabled = notificationsEnabled,
                            dmNotificationsEnabled = dmNotificationsEnabled,
                            channelNotificationsEnabled = channelNotificationsEnabled,
                            onCompactModeChanged = { newMode ->
                                compactMode = newMode
                                onSaveCompactMode(newMode)
                            },
                            onFontScaleChanged = { newScale ->
                                val adjusted = newScale.coerceIn(0.85f, 1.30f)
                                fontScale = adjusted
                                onSaveFontScale(adjusted)
                            },
                            onMarkdownEnabledChanged = { enabled ->
                                markdownEnabled = enabled
                                onSaveMarkdownEnabled(enabled)
                            },
                            onNotificationsEnabledChanged = { enabled ->
                                notificationsEnabled = enabled
                                dmNotificationsEnabled = enabled
                                channelNotificationsEnabled = enabled
                                onSaveNotificationsEnabled(enabled)
                                onSaveDmNotificationsEnabled(enabled)
                                onSaveChannelNotificationsEnabled(enabled)
                                if (enabled && !hasNotificationPermission()) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onDmNotificationsEnabledChanged = { enabled ->
                                dmNotificationsEnabled = enabled
                                onSaveDmNotificationsEnabled(enabled)
                                val global = dmNotificationsEnabled || channelNotificationsEnabled
                                notificationsEnabled = global
                            },
                            onChannelNotificationsEnabledChanged = { enabled ->
                                channelNotificationsEnabled = enabled
                                onSaveChannelNotificationsEnabled(enabled)
                                val global = dmNotificationsEnabled || channelNotificationsEnabled
                                notificationsEnabled = global
                            },
                            onResetNotifications = {
                                notificationsEnabled = true
                                dmNotificationsEnabled = true
                                channelNotificationsEnabled = true
                                onResetAllNotifications()
                                onSaveNotificationsEnabled(true)
                                onSaveDmNotificationsEnabled(true)
                                onSaveChannelNotificationsEnabled(true)
                                if (!hasNotificationPermission()) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onSetChannelMuted = onSetChannelMuted,
                            getMutedChannels = getMutedChannels,
                            onSetChannelDisabled = onSetChannelDisabled,
                            getDisabledChannels = getDisabledChannels,
                            biometricLockEnabled = biometricLockEnabled,
                            onBiometricLockChanged = { enabled ->
                                biometricLockEnabled = enabled
                                onSaveBiometricLockEnabled(enabled)
                            },
                            autoUpdateEnabled = autoUpdateEnabled,
                            onAutoUpdateChanged = { enabled ->
                                autoUpdateEnabled = enabled
                                onSaveAutoUpdateEnabled(enabled)
                            },
                            onCheckUpdatesNow = { checkForUpdates(manual = true) },
                            isCheckingUpdate = isCheckingUpdate,
                            updateStatusText = updateStatusText
                        )
                    }
                }
            }
        }
    }

    val releaseToInstall = availableRelease
    if (releaseToInstall != null) {
        AlertDialog(
            onDismissRequest = { availableRelease = null },
            title = { Text("Dostępna aktualizacja ${releaseToInstall.tagName}") },
            text = { Text("Znaleziono nowszą wersję aplikacji (${releaseToInstall.apkName}).") },
            confirmButton = {
                TextButton(
                    enabled = !isInstallingUpdate,
                    onClick = {
                        coroutineScope.launch {
                            isInstallingUpdate = true
                            val token = gitHubToken.trim()
                            val result = GitHubUpdateManager.downloadAndInstall(
                                context = context,
                                release = releaseToInstall,
                                token = token
                            )
                            isInstallingUpdate = false
                            result.onSuccess {
                                updateStatusText = "Uruchomiono instalator aktualizacji"
                                availableRelease = null
                            }.onFailure { error ->
                                updateStatusText = error.message ?: "Nie udało się zainstalować aktualizacji"
                            }
                        }
                    }
                ) {
                    if (isInstallingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Pobierz i zainstaluj")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { availableRelease = null }) {
                    Text("Później")
                }
            }
        )
    }
    }
}

private data class HomeUnreadAggregates(
    val unreadDmCount: Int,
    val unreadChannelCount: Int,
    val unreadByStream: Map<String, Int>,
    val unreadByTopic: Map<String, Int>
)

@Composable
private fun TabIconWithBadge(
    compactMode: Boolean,
    badgeCount: Int,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.size(if (compactMode) 24.dp else 26.dp),
        contentAlignment = Alignment.Center
    ) {
        icon()
        if (badgeCount > 0) {
            Badge(
                containerColor = Color(0xFF8A3B2E),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp
                )
            }
        }
    }
}

private fun scaledTypography(base: Typography, fontScale: Float): Typography {
    val scale = fontScale.coerceIn(0.85f, 1.30f)
    return base.copy(
        displayLarge = base.displayLarge.scaled(scale),
        displayMedium = base.displayMedium.scaled(scale),
        displaySmall = base.displaySmall.scaled(scale),
        headlineLarge = base.headlineLarge.scaled(scale),
        headlineMedium = base.headlineMedium.scaled(scale),
        headlineSmall = base.headlineSmall.scaled(scale),
        titleLarge = base.titleLarge.scaled(scale),
        titleMedium = base.titleMedium.scaled(scale),
        titleSmall = base.titleSmall.scaled(scale),
        bodyLarge = base.bodyLarge.scaled(scale),
        bodyMedium = base.bodyMedium.scaled(scale),
        bodySmall = base.bodySmall.scaled(scale),
        labelLarge = base.labelLarge.scaled(scale),
        labelMedium = base.labelMedium.scaled(scale),
        labelSmall = base.labelSmall.scaled(scale)
    )
}

private fun TextStyle.scaled(scale: Float): TextStyle {
    val safeScale = scale.coerceIn(0.85f, 1.30f)
    return copy(
        fontSize = if (fontSize.isSpecified) fontSize * safeScale else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * safeScale else lineHeight
    )
}

@Composable
private fun SettingsPanel(
    session: StoredAuth,
    onLogout: () -> Unit,
    compactMode: Boolean,
    fontScale: Float,
    markdownEnabled: Boolean,
    notificationsEnabled: Boolean,
    dmNotificationsEnabled: Boolean,
    channelNotificationsEnabled: Boolean,
    onCompactModeChanged: (Boolean) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onMarkdownEnabledChanged: (Boolean) -> Unit,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    onDmNotificationsEnabledChanged: (Boolean) -> Unit,
    onChannelNotificationsEnabledChanged: (Boolean) -> Unit,
    onResetNotifications: () -> Unit,
    onSetChannelMuted: (String, Boolean) -> Unit,
    getMutedChannels: () -> Set<String>,
    onSetChannelDisabled: (String, Boolean) -> Unit,
    getDisabledChannels: () -> Set<String>,
    biometricLockEnabled: Boolean = false,
    onBiometricLockChanged: (Boolean) -> Unit = {},
    autoUpdateEnabled: Boolean = true,
    onAutoUpdateChanged: (Boolean) -> Unit = {},
    onCheckUpdatesNow: () -> Unit = {},
    isCheckingUpdate: Boolean = false,
    updateStatusText: String? = null
) {
    var mutedChannels by remember {
        mutableStateOf(getMutedChannels().toList().sorted())
    }
    var disabledChannels by remember {
        mutableStateOf(getDisabledChannels().toList().sorted())
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF16273D)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Konto", color = Color(0xFFF2F6FF), fontWeight = FontWeight.SemiBold)
                Text(text = "Serwer: ${session.serverUrl}", color = Color(0xFFD7E2F5))
                Text(text = "Email: ${session.email}", color = Color(0xFFD7E2F5))
                Text(text = "Tryb: ${if (session.authType.name == "PASSWORD") "Hasło" else "Klucz API"}", color = Color(0xFFD7E2F5))
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF16273D)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Preferencje", color = Color(0xFFF2F6FF), fontWeight = FontWeight.SemiBold)

                Text(text = "Wygląd", color = Color(0xFFF2F6FF), fontWeight = FontWeight.Medium)
                SettingsToggleRow(label = "Skalowanie interfejsu (tryb kompaktowy)", checked = compactMode, onCheckedChange = onCompactModeChanged)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val label = when {
                        fontScale < 0.98f -> "Mała czcionka"
                        fontScale > 1.08f -> "Duża czcionka"
                        else -> "Normalna czcionka"
                    }
                    Text(text = "Rozmiar tekstu: $label (${(fontScale * 100).toInt()}%)", color = Color(0xFFD7E2F5))
                    Slider(
                        value = fontScale,
                        onValueChange = onFontScaleChanged,
                        valueRange = 0.85f..1.30f
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onFontScaleChanged(0.90f) }) { Text("Mała") }
                        OutlinedButton(onClick = { onFontScaleChanged(1.00f) }) { Text("Normalna") }
                        OutlinedButton(onClick = { onFontScaleChanged(1.15f) }) { Text("Duża") }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                SettingsToggleRow(label = "Markdown w kanałach", checked = markdownEnabled, onCheckedChange = onMarkdownEnabledChanged)

                Text(text = "Bezpieczeństwo", color = Color(0xFFF2F6FF), fontWeight = FontWeight.Medium)
                SettingsToggleRow(label = "Blokada biometryczna / PIN", checked = biometricLockEnabled, onCheckedChange = onBiometricLockChanged)

                Text(text = "Aktualizacje", color = Color(0xFFF2F6FF), fontWeight = FontWeight.Medium)
                SettingsToggleRow(label = "Automatyczne aktualizacje (GitHub)", checked = autoUpdateEnabled, onCheckedChange = onAutoUpdateChanged)
                OutlinedButton(
                    onClick = onCheckUpdatesNow,
                    enabled = !isCheckingUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Sprawdź aktualizacje")
                    }
                }
                if (!updateStatusText.isNullOrBlank()) {
                    Text(
                        text = updateStatusText,
                        color = Color(0xFF8CD9FF),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(text = "Powiadomienia", color = Color(0xFFF2F6FF), fontWeight = FontWeight.Medium)
                SettingsToggleRow(label = "Powiadomienia lokalne (global)", checked = notificationsEnabled, onCheckedChange = onNotificationsEnabledChanged)
                SettingsToggleRow(label = "Powiadomienia DM", checked = dmNotificationsEnabled, onCheckedChange = onDmNotificationsEnabledChanged)
                SettingsToggleRow(label = "Powiadomienia kanałów", checked = channelNotificationsEnabled, onCheckedChange = onChannelNotificationsEnabledChanged)
                Button(
                    onClick = {
                        onResetNotifications()
                        mutedChannels = emptyList()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2B5A83),
                        contentColor = Color(0xFFF2F6FF)
                    )
                ) {
                    Text("Reset notyfikacji")
                }

                if (mutedChannels.isNotEmpty()) {
                    Text(
                        text = "Wyciszone kanały",
                        color = Color(0xFFF2F6FF),
                        fontWeight = FontWeight.SemiBold
                    )
                    mutedChannels.forEach { channelName ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = channelName,
                                color = Color(0xFFD7E2F5),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = {
                                onSetChannelMuted(channelName, false)
                                mutedChannels = mutedChannels.filterNot { it.equals(channelName, ignoreCase = true) }
                            }) {
                                Text("Przywróć")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            mutedChannels.forEach { channelName ->
                                onSetChannelMuted(channelName, false)
                            }
                            mutedChannels = emptyList()
                        }
                    ) {
                        Text("Przywróć wszystkie kanały")
                    }

                    if (disabledChannels.isNotEmpty()) {
                        Text(
                            text = "Wyłączone kanały",
                            color = Color(0xFFF2F6FF),
                            fontWeight = FontWeight.SemiBold
                        )
                        disabledChannels.forEach { channelName ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = channelName,
                                    color = Color(0xFFD7E2F5),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(onClick = {
                                    onSetChannelDisabled(channelName, false)
                                    disabledChannels = disabledChannels.filterNot { it.equals(channelName, ignoreCase = true) }
                                }) {
                                    Text("Włącz")
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                disabledChannels.forEach { channelName ->
                                    onSetChannelDisabled(channelName, false)
                                }
                                disabledChannels = emptyList()
                            }
                        ) {
                            Text("Włącz wszystkie kanały")
                        }
                    }
                }
            }
        }
        Button(onClick = onLogout) {
            Text("Wyloguj")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ikona),
                contentDescription = "Logo aplikacji",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "  Toya Zulip v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | SysADM Toya",
                    color = Color(0xFF9FB2CC),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFFD7E2F5), modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onCheckedChange(!checked) }) {
            Text(if (checked) "Wyłącz" else "Włącz")
        }
    }
}
