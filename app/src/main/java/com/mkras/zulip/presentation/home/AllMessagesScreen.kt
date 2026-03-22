package com.mkras.zulip.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mkras.zulip.data.local.entity.MessageEntity
import java.text.DateFormat
import java.util.Date
import android.widget.TextView
import android.text.method.LinkMovementMethod
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin

private val PanelCard     = Color(0xFF1A2B44)
private val StrongText    = Color(0xFFEAF2FF)
private val BodyText      = Color(0xFFB8CAE4)
private val Accent        = Color(0xFF8CD9FF)
private val DmBadgeBg     = Color(0xFF1E3D5A)
private val DmBadgeText   = Color(0xFF7FD6FF)
private val StreamBadgeBg = Color(0xFF1A3828)
private val StreamBadgeText = Color(0xFF5FC98A)
private val SubtleOutline   = Color(0xFF284869)
private val ContentSurface  = Color(0xFF213754)

@Composable
fun AllMessagesScreen(
    messages: List<MessageEntity>,
    compactMode: Boolean,
    serverUrl: String,
    imageAuthHeader: String? = null,
    onMessageClick: (MessageEntity) -> Unit = {}
) {
    val orderedMessages = remember(messages) { messages.sortedByDescending { it.timestampSeconds } }
    val gap        = if (compactMode) 6.dp else 8.dp
    val cardPadding = if (compactMode) 10.dp else 14.dp
    val metaStyle  = MaterialTheme.typography.labelSmall
    val senderStyle = if (compactMode) MaterialTheme.typography.labelMedium
                      else MaterialTheme.typography.labelLarge
    val bodyStyle  = if (compactMode) MaterialTheme.typography.bodySmall
                     else MaterialTheme.typography.bodyMedium
    if (orderedMessages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Brak wiadomości", color = BodyText, style = bodyStyle)
        }
        return
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        items(orderedMessages, key = { it.id }) { message ->
                    val isDm = message.messageType == "private"
                    val plainContent = remember(message.id, message.content) {
                        message.content
                            .replace(Regex("<[^>]*>"), "")
                            .replace(Regex("!\\[[^\\]]*]\\(([^)]+)\\)"), "")
                            .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1")
                            .trim()
                    }
                    val imageUrl = remember(message.id, message.content, serverUrl) {
                        firstImageUrl(message.content, serverUrl)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMessageClick(message) },
                        shape = RoundedCornerShape(22.dp),
                        color = PanelCard,
                        border = BorderStroke(1.dp, SubtleOutline),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Column(modifier = Modifier.padding(cardPadding)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = message.senderFullName,
                                    style = senderStyle,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StrongText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatAllMsgTime(message.timestampSeconds),
                                    style = metaStyle,
                                    color = BodyText,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.padding(top = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isDm) DmBadgeBg else StreamBadgeBg
                                ) {
                                    Text(
                                        text = if (isDm) "DM" else "#${message.streamName ?: "kanał"}",
                                        color = if (isDm) DmBadgeText else StreamBadgeText,
                                        style = metaStyle,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (!isDm && message.topic.isNotBlank()) {
                                    Text(
                                        text = "> ${message.topic}",
                                        color = Accent,
                                        style = metaStyle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.size(6.dp))

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = ContentSurface
                            ) {
                                Column(modifier = Modifier.padding(cardPadding).fillMaxWidth()) {
                                    AllMessagesHtmlText(
                                        html = message.content,
                                        compactMode = compactMode
                                    )
                                }
                            }

                            if (imageUrl != null) {
                                AllMessagesAttachmentPreview(
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
        }
    }
    if (showFab) {
        FloatingActionButton(
            onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            containerColor = Color(0xFF2B5A83),
            contentColor = Accent
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Skocz do najnowszych")
        }
    }
    } // outer Box
}

@Composable
private fun AllMessagesAttachmentPreview(
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
                        color = Accent,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Załączony obraz",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().clickable { isFullscreenOpen = true },
                    onLoading = { isLoading = true },
                    onSuccess = { isLoading = false },
                    onError = {
                        isLoading = false
                        hasError = true
                    }
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }

    if (isFullscreenOpen) {
        AllMessagesImageFullscreenDialog(imageModel = imageModel) { isFullscreenOpen = false }
    }
}

@Composable
private fun AllMessagesImageFullscreenDialog(
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
    Regex("\\[([^\\]]*?)\\]\\(([^)]+)\\)")
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

private fun formatAllMsgTime(timestampSeconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(timestampSeconds * 1000))

@Composable
private fun AllMessagesHtmlText(
    html: String,
    compactMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textColor = BodyText.toArgb()
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
                textSize = if (compactMode) 11f else 13f
                setLineSpacing(2f, 1f)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.textSize = if (compactMode) 11f else 13f
            markwon.setMarkdown(tv, html)
        }
    )
}
