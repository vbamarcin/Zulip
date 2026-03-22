# Zulip Android App - Comprehensive DM Flow Analysis

**Analysis Date**: March 22, 2026  
**Scope**: Complete Direct Message workflow from initialization through display, edge cases, and UX

---

## Executive Summary

The Zulip Android app implements a multi-layered DM system using MVVM with Room database, Retrofit networking, and long-polling real-time updates. **Critical issues identified**: inconsistent conversation key normalization across 3 different builder functions, missing self-filtering in real-time processors, potential auto-scroll race conditions, and unread badge calculation flaws.

---

## 1. DM Initialization and List Loading

### 1.1 DM Conversation List Flow

**Entry Point**: [ZulipHomeScreen.kt](ZulipHomeScreen.kt#L150-L200)
- User sees 6-tab navigation: DMs, Channels, All, Search, Users, Settings
- Taps "DMs" tab → displays `DmConversationList` composable

**Loading Process** ([ChatViewModel.kt](ChatViewModel.kt#L48-L58)):
```kotlin
init {
    observeMentionCandidates()
    observeMessages()              // All messages
    observeAllMessages()           // All public + private
    observeStarredMessages()       // Starred only
    observeTypingEvents()
    observePresenceEvents()
    ensureMentionCandidatesLoaded()
    ...
}
```

### 1.2 Observable Flows

**Private Messages Stream** ([ChatRepositoryImpl.kt](ChatRepositoryImpl.kt#L43)):
```kotlin
override fun observePrivateMessages(): Flow<List<MessageEntity>> = 
    messageDao.observePrivateMessages()
```

**Database Query** ([MessageDao.kt](MessageDao.kt#L14)):
```kotlin
@Query("SELECT * FROM messages WHERE messageType = 'private' ORDER BY timestampSeconds ASC")
fun observePrivateMessages(): Flow<List<MessageEntity>>
```

**ViewModel Processing** ([ChatViewModel.kt](ChatViewModel.kt#L256-L295)):
```kotlin
private fun observeMessages() {
    viewModelScope.launch {
        chatRepository.observePrivateMessages().collect { messages ->
            val normalizedMessages = messages.map { message ->
                val normalizedKey = normalizeDmConversationKey(message.conversationKey, message.senderEmail)
                if (normalizedKey == message.conversationKey) message 
                else message.copy(conversationKey = normalizedKey)
            }
            
            val conversations = normalizedMessages
                .groupBy { it.conversationKey }
                .map { (key, msgs) ->
                    val latest = msgs.maxByOrNull { it.timestampSeconds }
                    DmConversation(
                        conversationKey = key,
                        senderEmail = latest?.senderEmail ?: "",
                        displayName = latest?.dmDisplayName?.ifBlank { latest.senderFullName } ?: key,
                        avatarUrl = avatarUrl,
                        unreadCount = msgs.count { !it.isRead && !it.senderEmail... },
                        latestTimestamp = latest?.timestampSeconds ?: 0L
                    )
                }
                .sortedByDescending { it.latestTimestamp }
            _uiState.update { it.copy(privateMessages = normalizedMessages, dmConversations = conversations) }
        }
    }
}
```

**Data Flow**:
1. Room emits `List<MessageEntity>` for all private messages
2. ViewModel groups by `conversationKey`
3. Extracts latest message per conversation
4. Computes unread count
5. Sorts by timestamp descending
6. UI receives `List<DmConversation>`

### 1.3 DM Initialization: Fetching Candidates

**User Opens "New DM" Picker** ([ChatViewModel.kt](ChatViewModel.kt#L121-L132)):
```kotlin
fun openNewDmPicker() {
    _uiState.update { it.copy(isNewDmPickerVisible = true, newDmQuery = "", newDmError = null) }
    ensureMentionCandidatesLoaded(forceRefresh = uiState.value.newDmPeople.isEmpty())
}
```

**Candidate Loading** ([ChatViewModel.kt](ChatViewModel.kt#L183-L227)):
```kotlin
fun ensureMentionCandidatesLoaded(forceRefresh: Boolean = false) {
    if (uiState.value.isNewDmLoading) return
    
    val hasCache = uiState.value.newDmPeople.isNotEmpty()
    val cacheAge = System.currentTimeMillis() - secureSessionStorage.getMentionCandidatesLastSyncTime()
    val isCacheFresh = cacheAge in 1 until MENTION_CANDIDATES_CACHE_TTL_MS
    
    if (!forceRefresh && hasCache && isCacheFresh) return
    loadDirectMessageCandidates(forceRefresh = forceRefresh)
}

private fun loadDirectMessageCandidates(forceRefresh: Boolean = false) {
    _uiState.update { it.copy(isNewDmLoading = true, newDmError = null) }
    viewModelScope.launch {
        chatRepository.getDirectMessageCandidates()
            .onSuccess { users ->
                secureSessionStorage.saveMentionCandidatesLastSyncTime(System.currentTimeMillis())
                _uiState.update { it.copy(isNewDmLoading = false, newDmPeople = users, newDmError = null) }
            }
            .onFailure { ... }
    }
}
```

**Cache TTL**: 6 hours ([ChatViewModel.kt](ChatViewModel.kt#L33)):
```kotlin
const val MENTION_CANDIDATES_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
```

**Repository Fetch** ([ChatRepositoryImpl.kt](ChatRepositoryImpl.kt#L559-L610)):
```kotlin
override suspend fun getDirectMessageCandidates(): Result<List<DirectMessageCandidate>> {
    return try {
        val response = service.getUsers()  // GET /api/v1/users
        if (response.result != "success") return Result.failure(...)
        
        val users = response.members
            .asSequence()
            .filter { it.isActive }
            .filter { !it.email.equals(auth.email, ignoreCase = true) }  // Exclude self
            .map { DirectMessageCandidate(...) }
            .sortedBy { it.fullName.lowercase() }
            .toList()
        
        directMessageCandidateDao.clearAll()
        directMessageCandidateDao.upsertAll(
            users.map { DirectMessageCandidateEntity(...) }
        )
        Result.success(users)
    } catch (e: Exception) { Result.failure(e) }
}
```

**Database Entity** ([DirectMessageCandidateEntity.kt](DirectMessageCandidateEntity.kt)):
```kotlin
@Entity(tableName = "direct_message_candidates")
data class DirectMessageCandidateEntity(
    @PrimaryKey val email: String,
    val userId: Long,
    val fullName: String,
    val avatarUrl: String,
    val sortKey: String
)
```

### 1.4 Display and Sorting

**Conversation List UI** ([ChatScreen.kt](ChatScreen.kt#L300-L450)):
```kotlin
LazyColumn(...) {
    items(conversations, key = { it.conversationKey }) { conv ->
        val peerEmail = conv.conversationKey.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it != selfNorm }
            .firstOrNull() ?: conv.conversationKey.trim().lowercase()
        
        val presenceStatus = presenceByEmail[peerEmail]
        val typingEmailFromText = typingText?.substringBefore(" pisze")?.trim()?.lowercase()
        val isTyping = typingEmailFromText == peerEmail
        
        Surface(...) {
            Row(...) {
                // Avatar with presence indicator
                Box(...) {
                    AvatarImage(avatarUrl = conv.avatarUrl, ...)
                    if (presenceStatus != null) {
                        val dotColor = when (presenceStatus) {
                            "active" -> Color(0xFF43D87A)     // Green
                            "idle" -> Color(0xFFF5C543)       // Yellow
                            else -> Color(0xFF6B7280)         // Gray
                        }
                        Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
                    }
                }
                // Display name + unread count
                Text(conv.displayName, ...)
                if (conv.unreadCount > 0) {
                    Badge(containerColor = Color(0xFF8A3B2E)) {
                        Text(conv.unreadCount.toString())
                    }
                }
            }
        }
    }
}
```

**UX Badges**: Shows unread badge in red (0xFF8A3B2E) with count

---

## 2. Conversation Key Management

### 2.1 Key Generation - CRITICAL ISSUE

**Problem**: 3 duplicate builder functions with **different normalization behavior**

#### Function 1: ChatRepositoryImpl.buildConversationKey (USED IN MESSAGES)
[ChatRepositoryImpl.kt](ChatRepositoryImpl.kt#L355-L373):
```kotlin
private fun buildConversationKey(recipients: List<Map<*, *>>, fallbackEmail: String, selfEmail: String): String {
    val recipientEmails = recipients
        .mapNotNull { it["email"] as? String }
        .map { it.trim() }
        .filter { it.isNotBlank() }
    
    val participants = if (recipientEmails.isEmpty()) listOf(fallbackEmail) else recipientEmails
    val withoutSelf = participants.filterNot { it.equals(selfEmail, ignoreCase = true) }
    val normalized = (if (withoutSelf.isNotEmpty()) withoutSelf else participants)
        .map { it.lowercase() }          // ✓ Lowercases
        .distinct()
        .sorted()
    
    return normalized.joinToString(",")
}
```

#### Function 2: EventProcessor.buildConversationKey (USED IN REAL-TIME)
[EventProcessor.kt](EventProcessor.kt#L91-L95):
```kotlin
private fun buildConversationKey(recipients: List<Map<*, *>>, fallbackEmail: String): String {
    val emails = recipients.mapNotNull { it["email"] as? String }
    return if (emails.isEmpty()) fallbackEmail 
    else emails.sorted().joinToString(",")  // ✗ NO lowercase, NO self-filtering
}
```

#### Function 3: ZulipNotificationHelper.buildConversationKey (USED IN NOTIFICATIONS)
[ZulipNotificationHelper.kt](ZulipNotificationHelper.kt#L270-L274):
```kotlin
private fun buildConversationKey(recipients: List<Map<*, *>>, fallbackEmail: String): String {
    val emails = recipients.mapNotNull { it["email"] as? String }
    return if (emails.isEmpty()) fallbackEmail 
    else emails.sorted().joinToString(",")  // ✗ NO lowercase, NO self-filtering
}
```

### 2.2 Key Normalization - Attempt to Fix

**ViewModel Normalization** ([ChatViewModel.kt](ChatViewModel.kt#L296-L312)):
```kotlin
private fun normalizeDmConversationKey(rawKey: String, fallbackEmail: String): String {
    val participants = rawKey
        .split(',')
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf(fallbackEmail.trim().lowercase()).filter { it.isNotBlank() } }
    
    val withoutSelf = participants.filterNot { it == currentUserEmail.trim().lowercase() }
    return (if (withoutSelf.isNotEmpty()) withoutSelf else participants)
        .distinct()
        .sorted()
        .joinToString(",")
}
```

**Usage in observeMessages** ([ChatViewModel.kt](ChatViewModel.kt#L258-L261)):
```kotlin
val normalizedMessages = messages.map { message ->
    val normalizedKey = normalizeDmConversationKey(message.conversationKey, message.senderEmail)
    if (normalizedKey == message.conversationKey) message 
    else message.copy(conversationKey = normalizedKey)
}
```

### 2.3 Key Matching Flow

**Scenario**: User sent DM to user@example.com

1. **On Send** ([ChatRepositoryImpl.kt](ChatRepositoryImpl.kt#L478-L490)):
   - Input: `to = "user@example.com"`
   - Calls: `normalizePrivateConversationKey(to, auth.email)`
   - Result: `"user@example.com"` (after normalization)
   - Stored in DB with key: `"user@example.com"`

2. **On Receive** (Real-time via WebSocket):
   - EventProcessor.processMessageEvent → buildConversationKey
   - **BUG**: Missing self-filtering and lowercasing
   - Result: Could be `"User@Example.COM,currentuser@example.com"` (wrong order, casing)
   - Does NOT match DB key

3. **In UI**:
   - Loaded from DB as `privateMessages`
   - ViewModel applies normalizeDmConversationKey AFTER grouping
   - Could create duplicate conversation entries before normalization

### 2.4 Key Mismatch Edge Cases

**Case 1: Group DM (3+ people)**
- Zulip API returns displayRecipient as sorted array
- EventProcessor doesn't filter self → could include current user in key
- ViewModel normalization removes self
- **Result**: Keys won't match

**Case 2: Case-Sensitive Email Server**
- EventProcessor: `"User@Example.COM"`
- ChatRepositoryImpl: `"user@example.com"`
- **Result**: Duplicate conversations

**Case 3: Email with Spaces**
- API: `"  user@example.com  "` (with spaces)
- EventProcessor: Doesn't trim
- ChatRepositoryImpl: Trims then lowercases
- **Result**: Keys don't match

---

## 3. Message Sending Flow

### 3.1 User Initiates Send

**UI Input** ([ChatScreen.kt](ChatScreen.kt#L900+) - MessageComposeInput):
```kotlin
MessageComposeInput(
    onSend = { content -> onSendMessage("private", conversationKey, content, null) },
    ...
)
```

### 3.2 ViewModel Dispatch

**Send Handler** ([ChatViewModel.kt](ChatViewModel.kt#L360-L369)):
```kotlin
fun sendMessage(
    type: String,      // "private"
    to: String,        // conversationKey like "user@example.com"
    content: String,
    topic: String? = null,
    onSuccess: (Long) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    viewModelScope.launch {
        chatRepository.sendMessage(type, to, content, topic)
            .onSuccess { messageId ->
                resyncOnResume()      // Fetch latest from server
                onSuccess(messageId)
            }
            .onFailure { error -> onError(error.message ?: "Failed to send message") }
    }
}
```

### 3.3 Network Request

**Repository Send** ([ChatRepositoryImpl.kt](ChatRepositoryImpl.kt#L451-L492)):
```kotlin
override suspend fun sendMessage(type: String, to: String, content: String, topic: String?): Result<Long> {
    return try {
        val service = zulipApiFactory.create(serverUrl = auth.serverUrl, credentials = ...)
        
        val response = service.sendMessage(
            type = type,           // "private"
            to = to,               // "user@example.com"
            content = content,
            topic = topic
        )
        
        if (response.result == "success") {
            val normalizedType = type.trim().lowercase()
            val messageId = response.messageId
            val senderDisplayName = auth.email.substringBefore('@').ifBlank { auth.email }
            
            // IMMEDIATE LOCAL INSERT - This is the user's sent message
            messageDao.upsert(
                MessageEntity(
                    id = messageId,
                    senderFullName = senderDisplayName,
                    senderEmail = auth.email,
                    content = content,
                    topic = topic.orEmpty(),
                    streamName = if (normalizedType == "stream") to else null,
                    timestampSeconds = System.currentTimeMillis() / 1000,
                    isRead = true,    // Own message is auto-read
                    isStarred = false,
                    isMentioned = false,
                    isWildcardMentioned = false,
                    reactionSummary = null,
                    avatarUrl = "",
                    messageType = normalizedType,
                    conversationKey = if (normalizedType == "private") 
                        normalizePrivateConversationKey(to, auth.email) 
                    else "",
                    dmDisplayName = if (normalizedType == "private") to else ""
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
```

### 3.4 Local State Update Timeline

**t=0ms**: User taps send
**t=1ms**: Network request initiated
**t=2ms**: Message inserted into DB with generated messageId
**t=5ms**: Network response received with server messageId
**t=10ms**: resyncOnResume called

**Issue**: Generated messageId vs server messageId could differ → duplicate messages or stale IDs

### 3.5 Database Sync

**After Send Success** ([ChatViewModel.kt](ChatViewModel.kt#L365)):
```kotlin
resyncOnResume()  // Calls:
```

**Resync Implementation** ([ChatViewModel.kt](ChatViewModel.kt#L103-L108)):
```kotlin
fun resyncOnResume() {
    loadModerationPermission()
    initialPresenceSync()
    viewModelScope.launch {
        chatRepository.resyncLatestMessages()
        chatRepository.resyncStarredMessages()
    }
}
```

**Latest Messages Resync** ([ChatRepositoryImpl.kt](ChatRepositoryImpl.kt#L60-L114)):
```kotlin
override suspend fun resyncLatestMessages() {
    val response = service.getMessages(
        numBefore = 100,              // Last 100 messages
        applyMarkdown = false
    )
    
    val mapped = response.messages.map { dto ->
        val msgType = dto.type.orEmpty()
        val recipients = if (msgType == "private") parsePrivateRecipients(dto.displayRecipient) else emptyList()
        
        MessageEntity(
            id = dto.id,
            ...
            conversationKey = if (msgType == "private") 
                buildConversationKey(recipients, dto.senderEmail.orEmpty(), selfEmail) 
            else "",
            dmDisplayName = if (msgType == "private") 
                buildDmDisplayName(recipients, selfEmail, dto.senderFullName.orEmpty()) 
            else ""
        )
    }.filterNotNull()
    
    messageDao.upsertAll(mapped)  // REPLACE OR INSERT
}
```

### 3.6 Local → Network → Database States

| State | Location | Data |
|-------|----------|------|
| Optimistic | Memory (LazyColumn) | Message with optimistic ID |
| Pending | Network | POST /api/v1/messages |
| Confirmed | Room DB | Message with server ID |
| Synced | Remote | Server event queue |
| Real-time | EventProcessor + Room | Message with flags updated |

---

## 4. Message Receiving Flow (Real-time)

### 4.1 WebSocket Connection

**Event Service** ([ZulipEventService.kt](ZulipEventService.kt) - background long-poll):
- Registers event queue via POST /api/v1/register
- Long-polls GET /api/v1/events?queue_id=X&last_event_id=Y
- Routes events to EventProcessor

### 4.2 Event Processing

**Incoming Event** ([EventProcessor.kt](EventProcessor.kt#L36-L67)):
```kotlin
suspend fun process(
    event: EventDto,
    dmNotificationsEnabled: Boolean,
    channelNotificationsEnabled: Boolean,
    selfEmail: String,
    serverUrl: String = ""
) {
    when (event.type) {
        "message" -> processMessageEvent(...)
        "update_message" -> processUpdateMessageEvent(...)
        "delete_message" -> processDeleteMessageEvent(...)
        "update_message_flags" -> processMessageFlagsEvent(...)
        "reaction" -> processReactionEvent(...)
        "typing" -> { /* emit typing event */ }
        "presence" -> { /* emit presence update */ }
    }
}
```

### 4.3 Message Event Processing - CRITICAL ISSUE

**processMessageEvent** ([EventProcessor.kt](EventProcessor.kt#L109-L162)):
```kotlin
private suspend fun processMessageEvent(
    message: EventMessageDto?,
    dmNotificationsEnabled: Boolean,
    channelNotificationsEnabled: Boolean,
    selfEmail: String,
    serverUrl: String
) {
    if (message == null) return
    
    val senderEmail = message.senderEmail.orEmpty()
    val isOwnMessage = senderEmail.equals(selfEmail, ignoreCase = true)
    if (isOwnMessage) return  // ✓ Correctly ignores own messages
    
    val isPrivate = message.type == "private"
    val recipients = if (isPrivate) parsePrivateRecipients(message.displayRecipient) else emptyList()
    
    // ✗ CRITICAL BUG #1: Missing selfEmail parameter
    val conversationKey = if (isPrivate) 
        buildConversationKey(recipients, message.senderEmail.orEmpty()) 
    else ""
    
    // ✗ CRITICAL BUG #2: Not removing self from recipients
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
    
    // Insert into database
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
            conversationKey = if (msgType == "private") conversationKey else "",  // Uses unnormalized key!
            dmDisplayName = if (msgType == "private") buildDmDisplayName(recipients, selfEmail, ...) else ""
        )
    )
    
    if (shouldNotify) {
        notificationHelper.showMessageNotification(message, selfEmail)
    }
}
```

**3 Critical Issues**:

1. **Missing selfEmail in buildConversationKey call**: Should be `buildConversationKey(recipients, senderEmail, selfEmail)` with self-filtering
2. **Recipients not filtered**: Group DMs will include self in recipients list
3. **Key format mismatch**: EventProcessor creates unnormalized key vs ChatRepositoryImpl creates normalized key

### 4.4 Database Update

**Room Insert** ([MessageDao.kt](MessageDao.kt#L27-L30)):
```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertAll(messages: List<MessageEntity>)
```

**Conflict Resolution**: REPLACE strategy means if ID exists, update all fields

**Flow to UI**:
- Room emits via Flow
- ChatViewModel collects and groups by conversationKey
- UI recomposition with new messages

---

## 5. Unread Status Tracking

### 5.1 Unread Count Calculation

**In ViewModel** ([ChatViewModel.kt](ChatViewModel.kt#L278-L281)):
```kotlin
unreadCount = msgs.count {
    !it.isRead && !it.senderEmail.equals(currentUserEmail, ignoreCase = true)
}
```

**Logic**:
- Filters to messages where `isRead = false`
- Excludes own messages (sender is current user)
- Counts remaining messages

**Issue #1**: Doesn't account for muted conversations
- A muted DM still shows unread badge
- Expected: Badge should not show or show differently for muted

### 5.2 Read Status Source

**From API Response Flags** ([ChatRepositoryImpl.kt](ChatRepositoryImpl.kt#L97)):
```kotlin
isRead = dto.flags?.contains("read") == true
```

**Server Flags**:
- `"read"` - Message has been read by user
- `"starred"` - User starred the message
- `"mentioned"` - User was mentioned
- `"wildcard_mentioned"` - User caught by @all or @channel

### 5.3 Mark as Read Flow

**On Message Render** ([ChatViewModel.kt](ChatViewModel.kt#L81-L87)):
```kotlin
fun onMessagesRendered(ids: List<Long>) {
    val targetIds = ids.distinct()
    if (targetIds.isEmpty()) return
    
    viewModelScope.launch {
        chatRepository.markMessagesAsRead(targetIds)
    }
}
```

**Called from UI** ([ChatScreen.kt](ChatScreen.kt#L155-L158)):
```kotlin
LaunchedEffect(uiState.privateMessages.size) {
    val recent = uiState.privateMessages.takeLast(30).map { it.id }
    onMessagesRendered(recent)
}
```

**Takes last 30** → Could miss messages in the thread view

**Repository Implementation** ([ChatRepositoryImpl.kt](ChatRepositoryImpl.kt#L410-L425)):
```kotlin
override suspend fun markMessagesAsRead(ids: List<Long>) {
    if (ids.isEmpty()) return
    
    messageDao.updateReadFlags(ids = ids, isRead = true)  // Local DB update
    
    val service = zulipApiFactory.create(...)
    
    service.updateMessageFlags(
        messagesJson = ids.joinToString(prefix = "[", postfix = "]"),
        operation = "add",
        flag = "read"
    )  // POST /api/v1/messages/flags
}
```

**Sequence**:
1. Update local DB immediately
2. POST to server asynchronously
3. If POST fails, local DB is stale
4. Server will send update_message_flags event when server reads via another client

**Issue #2**: No retry mechanism if network fails

### 5.4 Real-time Read Flag Updates

**update_message_flags Event** ([EventProcessor.kt](EventProcessor.kt#L205-L220)):
```kotlin
private suspend fun processMessageFlagsEvent(event: EventDto) {
    val ids = event.messages ?: return
    val isEnabled = event.op == "add"
    when (event.flag) {
        "read" -> {
            messageDao.updateReadFlags(ids = ids, isRead = isEnabled)
            if (isEnabled) {
                notificationHelper.onMessagesRead(ids)  // Clears notifications
            }
        }
        "starred" -> messageDao.updateStarredFlags(ids = ids, isStarred = isEnabled)
        "mentioned" -> messageDao.updateMentionedFlags(ids = ids, isMentioned = isEnabled)
        ...
    }
}
```

### 5.5 Unread Badge Display

**In Conversation List** ([ChatScreen.kt](ChatScreen.kt#L430-L436)):
```kotlin
if (conv.unreadCount > 0) {
    Badge(containerColor = Color(0xFF8A3B2E)) {
        Text(
            text = conv.unreadCount.toString(),
            color = Color.White
        )
    }
}
```

**Badge Appears**:
- When `unreadCount > 0`
- Shows numeric badge with message count
- Red background (0xFF8A3B2E)

**Missing Feature**: Should indicate muted status
- Muted DM with unread: should show grayed out badge or different icon
- Currently: Same visual treatment as unmuted

---

## 6. Image/Attachment Handling in DM

### 6.1 Image URL Extraction

**From Message Content** ([ChatScreen.kt](ChatScreen.kt#L1130-L1145)):
```kotlin
private val MD_IMAGE_REGEX = Regex("!\\[[^\\]]*]\\(([^)]+)\\)")
private val IMAGE_EXT_REGEX = Regex(".*\\.(png|jpe?g|gif|webp|bmp|heic|heif|svg)(\\?.*)?$", RegexOption.IGNORE_CASE)

@Composable
private fun MessageBubble(...) {
    val imageUrl = remember(message.id, serverUrl) {
        firstImageUrl(message.content, serverUrl)
    }
    ...
}
```

**firstImageUrl Function** (referenced but not shown):
- Regex extracts first markdown image: `![alt](url)`
- Validates extension against IMAGE_EXT_REGEX
- Resolves relative URLs to serverUrl

### 6.2 Image Rendering

**Coil AsyncImage** ([ChatScreen.kt](ChatScreen.kt#L1170+)):
```kotlin
@Composable
private fun AttachmentPreview(
    imageUrl: String,
    authHeader: String?,
    compactMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageModel = remember(imageUrl, authHeader) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .apply {
                if (!authHeader.isNullOrBlank()) {
                    header("Authorization", authHeader)  // Adds auth header
                }
            }
            .build()
    }
    
    AsyncImage(
        model = imageModel,
        contentDescription = null,
        modifier = modifier.height(200.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop
    )
}
```

**Auth Header**: Used for private server images
- Passed from ZulipHomeScreen → ChatScreen → AttachmentPreview
- Zulip server may require auth for image URLs

### 6.3 Attachment Upload

**User Picks File** ([ChatScreen.kt](ChatScreen.kt#L1015-L1025)):
```kotlin
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
        onUploadAttachmentMessage(
            "private", conversationKey, null, attachment,
            {  // onSuccess
                isUploadingAttachment = false
                Toast.makeText(context, "Załącznik wysłany", Toast.LENGTH_SHORT).show()
            },
            { msg ->  // onError
                isUploadingAttachment = false
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        )
    }
}
```

**Upload Handler** ([ChatViewModel.kt](ChatViewModel.kt#L378-L398)):
```kotlin
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
            chatRepository.sendMessage(type, to, content, topic)
                .onSuccess {
                    resyncOnResume()
                    onSuccess()
                }
                .onFailure { error -> onError(error.message ?: "Failed to send attachment") }
        }.onFailure { error -> onError(error.message ?: "Failed to upload attachment") }
    }
}
```

**Upload Flow**:
1. POST /api/v1/user_uploads → returns filename & upload URL
2. Format as markdown link: `[filename](url)`
3. Send as regular message
4. Receiver gets message with embedded link

### 6.4 Error Handling

**Upload Errors** ([ChatRepositoryImpl.kt](ChatRepositoryImpl.kt#L429-L460)):
```kotlin
override suspend fun uploadFile(
    fileName: String,
    mimeType: String?,
    bytes: ByteArray
): Result<UploadedFile> {
    return try {
        val service = zulipApiFactory.create(...)
        val requestBody = bytes.toRequestBody((mimeType ?: "application/octet-stream").toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
        val response = service.uploadFile(filePart)
        val uploadedUrl = response.url ?: response.uri
        
        if (response.result == "success" && !uploadedUrl.isNullOrBlank()) {
            Result.success(UploadedFile(filename = response.filename ?: fileName, url = uploadedUrl))
        } else {
            Result.failure(Exception(response.message.ifBlank { "Failed to upload file" }))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

**Possible Failures**:
- Network timeout
- Server quota exceeded
- Invalid file type
- Large file exceeds limit

---

## 7. Auto-Scroll Behavior

### 7.1 Initial Load Auto-Scroll

**First Time Selecting Conversation** ([ChatScreen.kt](ChatScreen.kt#L970-L978)):
```kotlin
var autoScrolledToLatest by remember(conversationKey) { mutableStateOf(false) }

LaunchedEffect(conversationKey, if (autoScrolledToLatest) Unit else messages.size) {
    if (!autoScrolledToLatest && messages.isNotEmpty()) {
        listState.scrollToItem(messages.lastIndex)
        autoScrolledToLatest = true
    }
}
```

**Logic**:
- When conversation selected, flag resets (via `remember(conversationKey)`)
- Scrolls to last message index
- Sets flag to true to prevent re-scroll

### 7.2 Smart Auto-Scroll on New Messages

**Continuous Scroll Detection** ([ChatScreen.kt](ChatScreen.kt#L982-L1010)):
```kotlin
val isNearBottom by remember {
    derivedStateOf {
        val total = listState.layoutInfo.totalItemsCount
        if (total <= 0) true
        else {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 2  // Within 2 items of bottom
        }
    }
}

LaunchedEffect(conversationKey, messages.size) {
    if (messages.isNotEmpty()) {
        if (autoScrolledToLatest && isNearBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
        onMessagesRendered(messages.takeLast(40).map { it.id })
    }
}
```

**Conditions for Auto-Scroll**:
- `autoScrolledToLatest = true` (has scrolled to bottom at least once)
- `isNearBottom = true` (currently showing last 2 items)
- New message arrives (messages.size changes)
- **Then**: Animate scroll to new last item

### 7.3 Jump-to-Latest FAB

**Floating Action Button** ([ChatScreen.kt](ChatScreen.kt#L1050-L1060)):
```kotlin
val showJumpToLatestFab by remember {
    derivedStateOf {
        messages.isNotEmpty() &&
            listState.layoutInfo.totalItemsCount > 0 &&
            listState.firstVisibleItemIndex < messages.size - 5
    }
}

if (showJumpToLatestFab) {
    FloatingActionButton(
        onClick = { coroutineScope.launch { listState.animateScrollToItem(messages.lastIndex) } },
        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        containerColor = Color(0xFF2B5A83),
        contentColor = Color(0xFF8CD9FF)
    ) {
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Skocz do najnowszych")
    }
}
```

**Shows FAB When**:
- User is scrolled up (firstVisibleItemIndex < messages.size - 5)
- Has messages to show
- Allows manual jump to latest

### 7.4 Scroll to Specific Message

**From Notification** ([ChatViewModel.kt](ChatViewModel.kt#L200-L215)):
```kotlin
fun openConversationFromNotification(
    conversationKey: String,
    conversationTitle: String?,
    messageId: Long
) {
    _uiState.update {
        it.copy(
            selectedConversationKey = conversationKey,
            selectedConversationTitle = conversationTitle,
            dmScrollToMessageId = messageId,  // Set scroll target
            isNewDmPickerVisible = false,
            newDmQuery = "",
            newDmError = null
        )
    }
}
```

**Scroll Handler** ([ChatScreen.kt](ChatScreen.kt#L1010-L1024)):
```kotlin
var highlightedMessageId by remember(conversationKey) { mutableStateOf<Long?>(null) }

LaunchedEffect(scrollToMessageId, messages.size) {
    val targetId = scrollToMessageId ?: return@LaunchedEffect
    val index = messages.indexOfFirst { it.id == targetId }
    if (index >= 0) {
        listState.scrollToItem(index)
        highlightedMessageId = targetId
        onScrollConsumed()
        delay(1800)
        if (highlightedMessageId == targetId) highlightedMessageId = null  // Clear highlight
    }
}
```

**Highlight Animation**:
- Scrolls to message with ID
- Sets highlightedMessageId (triggers background color change in MessageBubble)
- Clears highlight after 1800ms

### 7.5 CRITICAL BUG: Auto-Scroll Race Condition

**Issue**: The flag `autoScrolledToLatest` can fail to reset properly

```kotlin
var autoScrolledToLatest by remember(conversationKey) { mutableStateOf(false) }

// Problems:
// 1. If user rapidly switches conversations, the LaunchedEffect triggers before messages load
// 2. If conversation has 5+ messages, user scrolls up, then new message arrives:
//    - isNearBottom = false
//    - autoScrolledToLatest = true
//    - New message = silent, no scroll (expected)
// 3. BUT if user is at EXACT bottom and new message makes scrollTo(lastIndex) scroll by 1 item
//    while user just scrolled to index N, the positions could desync
```

**Scenario**:
1. Open conversation → scrolls to index 999 ✓
2. New message arrives → index becomes 1000 → isNearBottom checks if 999 >= 998 ✓ → scrolls
3. BUT: Between step 2 check and scroll, user manually scrolls → now at index 950
4. Code scrolls to 1000 anyway ✗ (jarring, unexpected jump)

**Fix Needed**: Check isNearBottom immediately before animate scroll:
```kotlin
if (autoScrolledToLatest && isNearBottom && messages.isNotEmpty()) {
    listState.animateScrollToItem(messages.lastIndex)
}
```

---

## 8. Biometric Lock Interaction

### 8.1 Lock Initialization

**In ZulipRoot** ([ZulipRoot.kt](ZulipRoot.kt#L64-L116)):
```kotlin
val lockTimeoutMs = 60_000L  // 60 seconds

val biometricEnabled = session != null && viewModel.getBiometricLockEnabled()
val lifecycleOwner = LocalLifecycleOwner.current

DisposableEffect(lifecycleOwner, session) {
    val observer = LifecycleEventObserver { _, event ->
        if (session == null || !biometricEnabled) return@LifecycleEventObserver
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {
                backgroundedAtMs = System.currentTimeMillis()
            }
            Lifecycle.Event.ON_RESUME -> {
                val now = System.currentTimeMillis()
                val backgroundTimeout = backgroundedAtMs?.let { now - it >= lockTimeoutMs } == true
                val idleTimeout = now - lastInteractionAtMs.get() >= lockTimeoutMs
                if (backgroundTimeout || idleTimeout) {
                    biometricAuthenticated = false  // Lock the app
                }
                lastInteractionAtMs.set(now)
                backgroundedAtMs = null
            }
            else -> Unit
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

**Lock Trigger Conditions**:
1. **Background Timeout**: App was backgrounded ≥ 60 seconds
2. **Idle Timeout**: No user interaction ≥ 60 seconds in foreground

### 8.2 Does Lock Prevent DM Access?

**Yes**, fully prevents access:

```kotlin
if (session == null) {
    LoginScreen(...)
} else {
    if (biometricEnabled && !biometricAuthenticated && !isHandlingAttachment) {
        BiometricLockScreen(...)  // Shows lock screen INSTEAD of ZulipHomeScreen
    } else {
        ZulipHomeScreen(...)
    }
}
```

**During Lock**:
- User sees BiometricLockScreen
- Cannot access DMs, Channels, or any other feature
- Must authenticate via fingerprint/face/PIN

### 8.3 Can User Resume Where They Left Off?

**YES** - Scroll State is Preserved:

**Scroll Preservation**:
- LazyColumn state is in `remember(conversationKey)`, not global
- When biometric lock clears, ZulipHomeScreen is re-composed
- But if same conversation selected, LazyColumn state is preserved
- **However**: If lock screen caused full recomposition, list scroll resets

**Message State**:
- All messages in DB remain
- Conversation list state is in ViewModel (preserved)
- Unread flags persist in DB

**DM History**:
- Full conversation history available
- Messages loaded from DB
- User returns to exact same state

**Issue**: If user was at bottom of 500-message conversation:
1. Lock triggers → BiometricLockScreen shown
2. User authenticates → back to ZulipHomeScreen
3. ViewModel re-initialized? NO - persists in viewModelScope
4. Same conversation selected → LazyColumn re-created
5. Scroll resets to top of list ✗

**Not Actually YES** - Auto-scroll triggered on re-composition:
```kotlin
LaunchedEffect(conversationKey) {
    isCurrentDirectMessageMuted = isDirectMessageMuted(conversationKey)
}
// + the auto-scroll on load:
LaunchedEffect(conversationKey, if (autoScrolledToLatest) Unit else messages.size) {
    if (!autoScrolledToLatest && messages.isNotEmpty()) {
        listState.scrollToItem(messages.lastIndex)  // Goes to BOTTOM again
        autoScrolledToLatest = true
    }
}
```

So user RETURNS to bottom, not exact scroll position.

### 8.4 Interaction Tracking

**Window Callback Intercepts All Input** ([ZulipRoot.kt](ZulipRoot.kt#L168-L179)):
```kotlin
private class InteractionTrackingWindowCallback(
    private val delegate: Window.Callback,
    private val onInteraction: () -> Unit
) : Window.Callback by delegate {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        onInteraction()
        return delegate.dispatchTouchEvent(event)
    }
    
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        onInteraction()
        return delegate.dispatchKeyEvent(event)
    }
}
```

**Applied to Window** ([ZulipRoot.kt](ZulipRoot.kt#L133-L145)):
```kotlin
DisposableEffect(session?.serverUrl, session?.email, biometricEnabled) {
    val activity = context as? AppCompatActivity
    if (activity == null || session == null || !biometricEnabled) {
        onDispose { }
    } else {
        val window = activity.window
        val originalCallback = window.callback
        val trackingCallback = InteractionTrackingWindowCallback(originalCallback) {
            lastInteractionAtMs.set(System.currentTimeMillis())  // Update on every touch/key
        }
        window.callback = trackingCallback
        onDispose {
            if (window.callback === trackingCallback) {
                window.callback = originalCallback
            }
        }
    }
}
```

**Resets Lock Timer**: Every touch/key press resets the 60-second idle timeout

### 8.5 Lock During Attachment Upload

**Attachment Handling** ([ZulipRoot.kt](ZulipRoot.kt#L160-L165)):
```kotlin
if (biometricEnabled && !biometricAuthenticated && !isHandlingAttachment) {
    BiometricLockScreen(...)  // Lock doesn't show if isHandlingAttachment = true
}
```

**File Picker Prevents Lock**:
- User opens file picker → isHandlingAttachment = true
- Lock bypassed (prevents interruption)
- File upload completes → isHandlingAttachment = false
- Reset interaction time

---

## 9. UI States

### 9.1 Empty State

**DM List Empty** ([ChatScreen.kt](ChatScreen.kt#L277-L282)):
```kotlin
if (conversations.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Brak wiadomości", color = SubtleColor)  // "No messages"
    }
}
```

**Shows**:
- When no private conversations loaded
- Centered text "Brak wiadomości"

### 9.2 Loading State

**Candidate Loading** ([ChatScreen.kt](ChatScreen.kt#L377-L382)):
```kotlin
when {
    isNewDmLoading -> {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF8CD9FF))
        }
    }
    ...
}
```

**Shows**:
- When fetching users for new DM picker
- Loading spinner in center

**Cache Strategy**:
- 6-hour TTL to avoid repeated network calls
- If cache fresh, shows list instantly

### 9.3 Error State

**Network Error** ([ChatScreen.kt](ChatScreen.kt#L383-L390)):
```kotlin
newDmError != null -> {
    Text(
        text = newDmError,
        color = Color(0xFFFFA8B5),  // Red
        style = MaterialTheme.typography.bodySmall
    )
}
```

**Shows**:
- When candidate fetch fails
- Error message in red
- No retry button (must close and reopen)

**Issue**: Silent failures on resync:
- If `resyncLatestMessages()` fails in background, no UI indication
- User sees stale messages without knowing sync failed

### 9.4 Message Thread Loading

**Thread View** ([ChatScreen.kt](ChatScreen.kt#L966-L1000+)):
```kotlin
val thread = remember(uiState.privateMessages, selectedKey) {
    uiState.privateMessages.filter { it.conversationKey == selectedKey }
}

DmThreadView(messages = thread, ...)
```

**No Loading Indicator**: Messages appear instantly from DB or show as empty

**Issue**: If conversation has 0 unread messages, empty thread doesn't distinguish:
- No messages ever sent to this user
- Messages loaded but filtering bug excluded them
- Load failed but error not shown

### 9.5 Attachment Upload State

**During Upload** ([ChatScreen.kt](ChatScreen.kt#L1065-1068]):
```kotlin
if (isUploadingAttachment) {
    Text(
        text = "Wysyłanie pliku...",  // "Sending file..."
        color = Color(0xFF8CD9FF),
        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp),
        style = metaStyle
    )
}
```

**UI Indication**:
- Text above compose box
- Compose input disabled (`enabled = !isUploadingAttachment`)

---

## 10. Edge Cases and Potential Issues

### 10.1 Conversation Key Mismatches (DOCUMENTED ABOVE)

**Summary**: 3 different key builders create mismatch between:
- Sent messages (ChatRepositoryImpl - normalized)
- Real-time messages (EventProcessor - unnormalized)  
- Notifications (ZulipNotificationHelper - unnormalized)

**Impact**: 
- Duplicate conversation entries
- Messages appear in wrong thread
- Unread badges incorrect

**Fix**:
```kotlin
// EventProcessor should use:
private fun buildConversationKey(
    recipients: List<Map<*, *>>, 
    fallbackEmail: String, 
    selfEmail: String  // Add parameter
): String {
    val emails = recipients
        .mapNotNull { it["email"] as? String }
        .map { it.trim() }
        .filter { it.isNotBlank() }
    
    val participants = if (emails.isEmpty()) listOf(fallbackEmail) else emails
    val withoutSelf = participants.filterNot { it.equals(selfEmail, ignoreCase = true) }
    val normalized = (if (withoutSelf.isNotEmpty()) withoutSelf else participants)
        .map { it.lowercase() }
        .distinct()
        .sorted()
    
    return normalized.joinToString(",")
}
```

### 10.2 Race Condition: sendMessage vs Incoming Event

**Sequence**:
1. User sends message at t=0ms
2. Message inserted locally with senderEmail = currentUserEmail
3. eventProcessor.processMessageEvent ignores own messages:
   ```kotlin
   val isOwnMessage = senderEmail.equals(selfEmail, ignoreCase = true)
   if (isOwnMessage) return
   ```
4. Server broadcasts event immediately
5. WebSocket processes: **Correct, own message ignored**

**No Bug Here** ✓

### 10.3 Race Condition: Message Delete While Viewing

**Scenario**:
1. User viewing message with ID 123
2. Another client deletes message 123
3. EventProcessor receives delete event
4. messageDao.deleteMessage(123) removes from DB

**UI Effect** ([ChatScreen.kt](ChatScreen.kt#L1130)):
```kotlin
val thread = remember(uiState.privateMessages, selectedKey) {
    uiState.privateMessages.filter { it.conversationKey == selectedKey }
}
```

- Message removed from DB
- Flow emits new list without message 123
- LazyColumn recomposes and message disappears from UI ✓
- No error, smooth removal

### 10.4 Race Condition: AutoScroll During Delete

```kotlin
val showJumpToLatestFab by remember {
    derivedStateOf {
        messages.isNotEmpty() &&
            listState.layoutInfo.totalItemsCount > 0 &&
            listState.firstVisibleItemIndex < messages.size - 5
    }
}

LaunchedEffect(conversationKey, messages.size) {
    if (messages.isNotEmpty()) {
        if (autoScrolledToLatest && isNearBottom) {
            listState.animateScrollToItem(messages.lastIndex)  // Could crash if lastIndex > actual items
        }
    }
}
```

**Potential Crash**:
- messages.size = 100 → lastIndex = 99
- User scrolls to item 99
- Received delete event → messages.size = 99 → lastIndex = 98
- animateScrollToItem(99) but only 99 items (0-98) → **IndexOutOfBoundsException**

**Needs Fix**:
```kotlin
if (autoScrolledToLatest && isNearBottom && messages.isNotEmpty()) {
    listState.animateScrollToItem(messages.size - 1)  // Safe
}
```

### 10.5 Unread Count Not Decreasing on Mark-as-Read

**Scenario**:
1. Unread badge shows "3"
2. User opens thread and messages are marked read
3. Badge should disappear

**Code Flow**:
```kotlin
// Mark as read sent to server

// Server sends back update_message_flags event

// EventProcessor processes:
messageDao.updateReadFlags(ids = ids, isRead = true)

// ViewModel observes:
val unreadCount = msgs.count { !it.isRead && ... }  // Should now be 0
```

**Expected**: Badge disappears

**Possible Bug**: If network request to server fails, messages marked read locally but server never knows:
```kotlin
override suspend fun markMessagesAsRead(ids: List<Long>) {
    messageDao.updateReadFlags(ids = ids, isRead = true)  // LOCAL
    
    val service = zulipApiFactory.create(...)
    
    service.updateMessageFlags(...)  // NETWORK - could fail silently
    // No error handling, no retry
}
```

**Result**: 
- Badge gone on this client
- Other clients still see as unread
- Next sync could bring back old read flags

### 10.6 Stale Avatar in Conversation List

**Avatar Selection** ([ChatViewModel.kt](ChatViewModel.kt#L269-L276)):
```kotlin
val latest = msgs.maxByOrNull { it.timestampSeconds }

val peerMessage = msgs.find { !it.senderEmail.equals(currentUserEmail, ignoreCase = true) }
val avatarUrl = if (peerMessage != null) {
    resolveAvatarUrl(peerMessage.avatarUrl.orEmpty())
} else {
    resolveAvatarUrl(latest?.avatarUrl.orEmpty())
}
```

**Logic**:
1. Finds first non-self message
2. If not found, uses latest message's avatar (likely self-sent)

**Issue**: 
- If user is alone in DM (only sent message), avatar is empty
- If group DM and latest message is from different user, shows wrong avatar
- No fallback to actual recipient's avatar

**Expected**: Show peer's avatar, not latest sender's

### 10.7 Group DM Mismatch (3+ Participants)

**Scenario**: Group DM between currentUser, alice@example.com, bob@example.com

**Send**:
```kotlin
to = "alice@example.com,bob@example.com"  // ChatRepositoryImpl normalizes

normalizePrivateConversationKey:
    participants = ["alice@example.com", "bob@example.com"]
    withoutSelf = ["alice@example.com", "bob@example.com"]
    result = "alice@example.com,bob@example.com"
```

**Receive via WebSocket**:
```kotlin
recipients = [
    {"email": "alice@example.com", "full_name": "Alice"},
    {"email": "bob@example.com", "full_name": "Bob"},
    {"email": "currentuser@example.com", "full_name": "Current"}
]

buildConversationKey (EventProcessor - WRONG):
    emails = ["alice@example.com", "bob@example.com", "currentuser@example.com"]
    sorted = ["alice@example.com", "bob@example.com", "currentuser@example.com"]
    result = "alice@example.com,bob@example.com,currentuser@example.com"  // ✗ Includes self
```

**Mismatch**: Keys don't match → New conversation created instead of merging

### 10.8 Group DM Missing Members Display

**displayName Creation** ([ChatViewModel.kt](ChatViewModel.kt#L280)):
```kotlin
displayName = latest?.dmDisplayName?.ifBlank { latest.senderFullName } ?: key
```

**dmDisplayName Built When** ([ChatRepositoryImpl.kt](ChatRepositoryImpl.kt#L115-116)):
```kotlin
dmDisplayName = if (msgType == "private") 
    buildDmDisplayName(recipients, selfEmail, dto.senderFullName.orEmpty()) 
else ""
```

**buildDmDisplayName** ([ChatRepositoryImpl.kt](ChatRepositoryImpl.kt#L391-397)):
```kotlin
private fun buildDmDisplayName(recipients: List<Map<*, *>>, selfEmail: String, fallback: String): String {
    val names = recipients
        .filter { (it["email"] as? String)?.equals(selfEmail, ignoreCase = true) == false }
        .mapNotNull { it["full_name"] as? String }
    return names.joinToString(", ").ifBlank { fallback }
}
```

**Example**:
- Participants: Alice, Bob, CurrentUser
- Recipients list has: Alice (full_name), Bob (full_name) - CurrentUser filtered
- Display: "Alice, Bob" ✓

**Issue**: Display name only from latest message's recipients list
- If user added to group DM later, their name might not appear in all messages
- Conversation title could show partial member list

### 10.9 Network Partition During Send

**User Sends While Offline**:
1. Message inserted locally
2. Network request queued
3. Sent message appears in thread
4. Network eventually fails

**Current Behavior**:
```kotlin
chatRepository.sendMessage(type, to, content, topic)
    .onSuccess { messageId -> resyncOnResume() }
    .onFailure { error -> onError(error.message) }
```

- onError shows toast
- Message stays in DB with temporary ID
- No retry mechanism
- When app syncs, server has no record of this message

**Expected**: 
- Retry on reconnection
- Visual indicator (e.g., "!" icon) showing send failed
- Option to resend

### 10.10 Muted DM Badge Not Grayed Out

**Current**: Unread badge shows same regardless of mute status

**Code** ([ChatScreen.kt](ChatScreen.kt#L430-436)):
```kotlin
if (conv.unreadCount > 0) {
    Badge(containerColor = Color(0xFF8A3B2E)) {  // Always red
        Text(conv.unreadCount.toString(), color = Color.White)
    }
}
```

**Missing**: Check if muted and show different badge style:
```kotlin
if (conv.unreadCount > 0) {
    val isMuted = isDirectMessageMuted(conv.conversationKey)
    Badge(containerColor = if (isMuted) Color(0xFF666666) else Color(0xFF8A3B2E)) {
        Text(conv.unreadCount.toString())
    }
}
```

### 10.11 Scroll Position Lost on Config Change

**Portrait → Landscape Rotation**:
- Activity would normally recreate
- Compose prevents full recomposition via saved state
- LazyColumn `remember` preserves scroll state

**But if**:
- Conversation changed before rotation
- `remember(conversationKey)` resets on new key
- New LazyColumn instance created
- Auto-scroll triggers → goes to bottom

**Mostly OK** - Designed behavior to go to latest

### 10.12 Very Large Message Count (1000+)

**Performance Issues**:
```kotlin
val conversations = normalizedMessages
    .groupBy { it.conversationKey }  // ← O(n) grouping
    .map { ... }                       // ← O(k) conversations
    .sortedByDescending { it.latestTimestamp }  // ← O(k log k) sort
```

- If 1000+ messages per conversation, grouping is slow
- UI might stutter on initial load

**No pagination** - loads all messages into memory

**Fix**: Room query with LIMIT:
```sql
SELECT * FROM messages WHERE messageType = 'private' LIMIT 200
```

---

## Summary of Issues

| Issue | Severity | Location | Impact |
|-------|----------|----------|--------|
| **Conversation Key Mismatch** | 🔴 CRITICAL | EventProcessor vs ChatRepositoryImpl | Messages in wrong thread, duplicates |
| **EventProcessor Missing Self Filter** | 🔴 CRITICAL | EventProcessor.buildConversationKey | Group DM keys include self |
| **Auto-Scroll Race Condition** | 🟡 HIGH | ChatScreen LaunchedEffect | Potential IndexOutOfBounds crash |
| **Mark-as-Read No Retry** | 🟡 HIGH | ChatRepositoryImpl.markMessagesAsRead | Stale read flags on failure |
| **Unread Badge Not Muted-Aware** | 🟠 MEDIUM | ChatScreen conversation list | UX: muted DMs show same badge |
| **Empty Avatar Fallback** | 🟠 MEDIUM | ChatViewModel avatar selection | Solo DMs show empty avatar |
| **Group DM Member Names Incomplete** | 🟠 MEDIUM | buildDmDisplayName | Late arrivals might not show |
| **No Send Failure Indication** | 🟠 MEDIUM | sendMessage callback | Failed sends not visible to user |
| **Large Message List Performance** | 🟠 MEDIUM | observeMessages room query | UI stutter with 1000+ messages |
| **Mute Status Not Persisted Across App Restart** | 🟢 LOW | SecureSessionStorage | Mute state might reset (if no persistence) |

---

## Code Quality Observations

✅ **Strengths**:
- Clean separation of concerns (Repository, ViewModel, UI)
- Coroutine-based async (no callback hell)
- Room provides reactive updates via Flow
- Security: EncryptedSharedPreferences for credentials

⚠️ **Improvements Needed**:
- Unified conversation key builder
- Comprehensive error handling and retry logic
- Network state awareness
- Test coverage for key matching scenarios
- Performance optimization for large datasets

