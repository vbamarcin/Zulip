package com.mkras.zulip.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Videocam
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mkras.zulip.domain.repository.DirectMessageCandidate

private val ComposePanelBg = Color(0xFF0D1820)
private val ComposePanelBorder = Color(0xFF1F3550)
private val ComposeFieldBg = Color(0xFF16273D)
private val ComposeFieldFocused = Color(0xFF8CD9FF)
private val ComposeFieldUnfocused = Color(0xFF2A4B6E)
private val ComposePlaceholder = Color(0xFF5A7A8E)
private val ComposeInputText = Color(0xFFE0E8F0)
private val ComposeActionTint = Color(0xFF8CD9FF)
private val ComposeSendEnabled = Color(0xFF1DB954)
private val ComposeSendDisabled = Color(0xFF606060)
private val ActionsPillBg = Color(0xCC112138)
private val ActionsPillBorder = Color(0xFF223A59)
private val ActionsEditTint = Color(0xFFA8C4E0)
private val ActionsDeleteTint = Color(0xFFE07080)

@Composable
fun MessageComposeInput(
    onSend: (String) -> Unit,
    compactMode: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    mentionCandidates: List<DirectMessageCandidate> = emptyList(),
    onRequestMentionCandidates: () -> Unit = {},
    topicCandidates: List<String> = emptyList(),
    allowTopicSuggestions: Boolean = false,
    pendingInsertionText: String? = null,
    onPendingInsertionConsumed: () -> Unit = {},
    onAddAttachment: (() -> Unit)? = null,
    onGenerateVideoCall: (() -> Unit)? = null
) {
    var text by remember { mutableStateOf("") }
    var selectedSuggestionIndex by remember { mutableStateOf(0) }
    val cornerRadius = if (compactMode) 16.dp else 18.dp
    val sendButtonSize = if (compactMode) 36.dp else 40.dp
    val utilityButtonSize = if (compactMode) 32.dp else 36.dp
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val baseBodySmall = androidx.compose.material3.MaterialTheme.typography.bodySmall
    val inputTextStyle = remember(baseBodySmall) { baseBodySmall.copy(color = ComposeInputText) }
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = ComposeFieldBg,
        unfocusedContainerColor = ComposeFieldBg,
        focusedIndicatorColor = ComposeFieldFocused,
        unfocusedIndicatorColor = ComposeFieldUnfocused,
        cursorColor = ComposeFieldFocused
    )
    val mentionQuery = remember(text) { trailingMentionQuery(text) }
    val topicQuery = remember(text, allowTopicSuggestions) {
        if (allowTopicSuggestions) trailingTopicQuery(text) else null
    }
    val suggestions = remember(mentionQuery, topicQuery, mentionCandidates, topicCandidates) {
        when {
            mentionQuery != null -> buildMentionSuggestions(mentionQuery, mentionCandidates)
            topicQuery != null -> buildTopicSuggestions(topicQuery, topicCandidates)
            else -> emptyList()
        }
    }

    LaunchedEffect(suggestions.map { it.key }) {
        selectedSuggestionIndex = 0
    }

    fun applySuggestion(suggestion: MentionSuggestion) {
        text = replaceTrailingMatch(
            text = text,
            marker = if (suggestion.trigger == SuggestionTrigger.Mention) '@' else '#',
            replacement = suggestion.replacement
        )
    }

    LaunchedEffect(mentionQuery, mentionCandidates.size) {
        if (mentionQuery != null && mentionCandidates.isEmpty()) {
            onRequestMentionCandidates()
        }
    }

    LaunchedEffect(pendingInsertionText) {
        val insertion = pendingInsertionText.orEmpty()
        if (insertion.isBlank()) {
            return@LaunchedEffect
        }
        val normalizedInsertion = insertion.trimEnd()
        if (normalizedInsertion.contains('\n')) {
            text = when {
                text.isBlank() -> normalizedInsertion
                text.endsWith("\n") -> text + normalizedInsertion
                else -> "$text\n$normalizedInsertion"
            }
        } else {
            val singleLine = normalizedInsertion.trim()
            text = when {
                text.isBlank() -> "$singleLine "
                text.endsWith(" ") || text.endsWith("\n") -> text + "$singleLine "
                else -> "$text $singleLine "
            }
        }
        onPendingInsertionConsumed()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ComposePanelBg, RoundedCornerShape(cornerRadius))
                .border(1.dp, ComposePanelBorder, RoundedCornerShape(cornerRadius))
                .padding(horizontal = 3.dp, vertical = 2.dp)
        ) {
            if ((mentionQuery != null || topicQuery != null) && suggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = ComposeFieldBg,
                    tonalElevation = 0.dp
                ) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 120.dp)
                    ) {
                        items(suggestions, key = { it.key }) { suggestion ->
                            val suggestionIndex = suggestions.indexOfFirst { it.key == suggestion.key }
                            val isSelected = suggestionIndex == selectedSuggestionIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) Color(0xFF24415F) else Color.Transparent)
                                    .clickable(enabled = enabled) {
                                        selectedSuggestionIndex = suggestionIndex.coerceAtLeast(0)
                                        applySuggestion(suggestion)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SuggestionLeading(suggestion = suggestion, compactMode = compactMode)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = highlightedText(suggestion.label, suggestion.matchQuery),
                                        color = ComposeInputText,
                                        modifier = Modifier.padding(start = 10.dp)
                                    )
                                    suggestion.supporting?.let { supporting ->
                                        Text(
                                            text = highlightedText(supporting, suggestion.matchQuery),
                                            color = ComposePlaceholder,
                                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(start = 10.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = suggestion.preview,
                                    color = ComposeActionTint,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown || suggestions.isEmpty()) {
                                return@onPreviewKeyEvent false
                            }

                            when (keyEvent.key) {
                                Key.DirectionDown -> {
                                    selectedSuggestionIndex = (selectedSuggestionIndex + 1).coerceAtMost(suggestions.lastIndex)
                                    true
                                }

                                Key.DirectionUp -> {
                                    selectedSuggestionIndex = (selectedSuggestionIndex - 1).coerceAtLeast(0)
                                    true
                                }

                                Key.Enter,
                                Key.NumPadEnter,
                                Key.Tab -> {
                                    applySuggestion(suggestions[selectedSuggestionIndex])
                                    true
                                }

                                else -> false
                            }
                        },
                    placeholder = { Text("Napisz...", color = ComposePlaceholder) },
                    textStyle = inputTextStyle,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (text.isNotBlank() && enabled) {
                                onSend(text.trim())
                                text = ""
                                keyboardController?.hide()
                                focusManager.clearFocus(force = false)
                            }
                        }
                    ),
                    colors = textFieldColors,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = false,
                    maxLines = 3,
                    enabled = enabled
                )
                if (onAddAttachment != null) {
                    IconButton(
                        onClick = { onAddAttachment() },
                        enabled = enabled,
                        modifier = Modifier.size(utilityButtonSize)
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = "Załącz", tint = ComposeActionTint, modifier = Modifier.size(15.dp))
                    }
                }
                if (onGenerateVideoCall != null) {
                    IconButton(
                        onClick = { onGenerateVideoCall() },
                        enabled = enabled,
                        modifier = Modifier.size(utilityButtonSize)
                    ) {
                        Icon(Icons.Rounded.Videocam, contentDescription = "Video", tint = ComposeActionTint, modifier = Modifier.size(15.dp))
                    }
                }
                IconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onSend(text.trim())
                            text = ""
                        }
                    },
                    enabled = text.isNotBlank() && enabled,
                    modifier = Modifier.size(sendButtonSize)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Wyślij",
                        tint = if (text.isNotBlank() && enabled) ComposeSendEnabled else ComposeSendDisabled,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

        }
    }
}

private enum class SuggestionTrigger {
    Mention,
    Topic
}

private data class MentionSuggestion(
    val key: String,
    val label: String,
    val supporting: String?,
    val preview: String,
    val replacement: String,
    val trigger: SuggestionTrigger,
    val matchQuery: String,
    val avatarUrl: String? = null,
    val avatarFallback: String = "@",
    val icon: ImageVector? = null
)

private fun trailingMentionQuery(text: String): String? {
    val match = Regex("(?:^|\\s)@([^\\s@]*)$").find(text) ?: return null
    return match.groupValues[1]
}

private fun trailingTopicQuery(text: String): String? {
    val match = Regex("(?:^|\\s)#([^\\s#]*)$").find(text) ?: return null
    return match.groupValues[1]
}

private fun buildMentionSuggestions(
    mentionQuery: String?,
    mentionCandidates: List<DirectMessageCandidate>
): List<MentionSuggestion> {
    if (mentionQuery == null) return emptyList()

    val normalizedQuery = mentionQuery.trim().lowercase()
    val suggestions = mutableListOf<MentionSuggestion>()

    if (normalizedQuery.isBlank() || "all".contains(normalizedQuery)) {
        suggestions += MentionSuggestion(
            key = "all",
            label = "Powiadom wszystkich",
            supporting = "Wstawia @all do wiadomości",
            preview = "@all",
            replacement = "@**all** ",
            trigger = SuggestionTrigger.Mention,
            matchQuery = normalizedQuery,
            avatarFallback = "ALL",
            icon = Icons.Rounded.AlternateEmail
        )
    }

    suggestions += mentionCandidates
        .asSequence()
        .filter {
            normalizedQuery.isBlank() ||
                it.fullName.lowercase().contains(normalizedQuery) ||
                it.email.lowercase().contains(normalizedQuery)
        }
        .take(8)
        .map {
            MentionSuggestion(
                key = it.email,
                label = it.fullName,
                supporting = it.email,
                preview = "@${it.fullName}",
                replacement = "@**${it.fullName}** ",
                trigger = SuggestionTrigger.Mention,
                matchQuery = normalizedQuery,
                avatarUrl = it.avatarUrl.takeIf { url -> url.isNotBlank() },
                avatarFallback = initialsForName(it.fullName),
                icon = Icons.Rounded.AlternateEmail
            )
        }
        .toList()

    return suggestions
}

private fun buildTopicSuggestions(
    topicQuery: String?,
    topicCandidates: List<String>
): List<MentionSuggestion> {
    if (topicQuery == null) return emptyList()

    val normalizedQuery = topicQuery.trim().lowercase()
    return topicCandidates
        .asSequence()
        .filter { topicName ->
            normalizedQuery.isBlank() || topicName.lowercase().contains(normalizedQuery)
        }
        .distinct()
        .take(8)
        .map { topicName ->
            MentionSuggestion(
                key = "topic:$topicName",
                label = topicName,
                supporting = "Temat kanału",
                preview = "#$topicName",
                replacement = "#$topicName ",
                trigger = SuggestionTrigger.Topic,
                matchQuery = normalizedQuery,
                avatarFallback = "#",
                icon = Icons.Rounded.Tag
            )
        }
        .toList()
}

private fun highlightedText(text: String, query: String): AnnotatedString {
    if (query.isBlank()) {
        return AnnotatedString(text)
    }

    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val startIndex = lowerText.indexOf(lowerQuery)
    if (startIndex < 0) {
        return AnnotatedString(text)
    }

    val endIndex = startIndex + lowerQuery.length
    return buildAnnotatedString {
        append(text.substring(0, startIndex))
        withStyle(SpanStyle(color = ComposeActionTint)) {
            append(text.substring(startIndex, endIndex))
        }
        append(text.substring(endIndex))
    }
}

private fun replaceTrailingMatch(text: String, marker: Char, replacement: String): String {
    val regex = Regex("(^|\\s)\\${marker}[^\\s\\${marker}]*$")
    val match = regex.find(text) ?: return "$text$replacement"
    val prefix = match.groupValues[1]
    val start = match.range.first
    return text.substring(0, start) + prefix + replacement
}

private fun initialsForName(name: String): String {
    return name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "@" }
}

@Composable
private fun SuggestionLeading(
    suggestion: MentionSuggestion,
    compactMode: Boolean
) {
    val avatarSize = if (compactMode) 28.dp else 32.dp
    Surface(
        modifier = Modifier.size(avatarSize),
        shape = CircleShape,
        color = Color(0xFF27415D)
    ) {
        if (!suggestion.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = suggestion.avatarUrl,
                contentDescription = suggestion.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(avatarSize)
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(avatarSize)) {
                if (suggestion.icon != null && suggestion.avatarFallback.length <= 2) {
                    Icon(
                        imageVector = suggestion.icon,
                        contentDescription = null,
                        tint = ComposeActionTint,
                        modifier = Modifier.size(if (compactMode) 16.dp else 18.dp)
                    )
                } else {
                    Text(
                        text = suggestion.avatarFallback,
                        color = ComposeInputText,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun MessageActionsRow(
    onReaction: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    compactMode: Boolean,
    alignEnd: Boolean = false
) {
    var showEmojiPicker by remember { mutableStateOf(false) }
    val containerPadding = if (compactMode) 4.dp else 6.dp
    val iconSize = if (compactMode) 16.dp else 18.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .background(ActionsPillBg, RoundedCornerShape(999.dp))
                .border(1.dp, ActionsPillBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = containerPadding, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ThreadActionButton(onClick = { showEmojiPicker = !showEmojiPicker }) {
                Icon(Icons.Rounded.EmojiEmotions, contentDescription = "Dodaj emoji", tint = ComposeActionTint, modifier = Modifier.size(iconSize))
            }
            ThreadActionButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edytuj", tint = ActionsEditTint, modifier = Modifier.size(iconSize))
            }
            ThreadActionButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Usuń", tint = ActionsDeleteTint, modifier = Modifier.size(iconSize))
            }
        }
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            onEmojiSelected = { emoji ->
                onReaction(emoji)
                showEmojiPicker = false
            },
            onDismiss = { showEmojiPicker = false }
        )
    }
}

@Composable
fun EmojiPickerDialog(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF1A2B44), shape = RoundedCornerShape(16.dp))
                .padding(16.dp)
                .fillMaxWidth(0.9f)
        ) {
            Column {
                Text("Wybierz emoji", color = Color(0xFFE0E8F0))
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(EmojiHelper.COMMON_EMOJIS) { (emoji, name) ->
                        Button(
                            onClick = { onEmojiSelected(name) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A4B6E))
                        ) {
                            Text(emoji, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp), content = content)
}
