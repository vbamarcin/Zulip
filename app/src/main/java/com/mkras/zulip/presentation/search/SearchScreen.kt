package com.mkras.zulip.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mkras.zulip.presentation.chat.ChatUiState
import java.text.DateFormat
import java.util.Date

private val SearchCard = Color(0xFF1A2B44)
private val SearchText = Color(0xFFEAF2FF)
private val SearchBody = Color(0xFFB8CAE4)
private val SearchAccent = Color(0xFF8CD9FF)

@Composable
fun SearchScreen(
    uiState: ChatUiState,
    compactMode: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    val gap = if (compactMode) 8.dp else 12.dp
    val cardPadding = if (compactMode) 10.dp else 14.dp
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Szukaj w wiadomościach...", color = SearchBody) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                        onSearch()
                    }
                ),
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF16273D),
                    unfocusedContainerColor = Color(0xFF16273D),
                    focusedIndicatorColor = SearchAccent,
                    unfocusedIndicatorColor = Color(0xFF2A4B6E),
                    focusedTextColor = SearchText,
                    unfocusedTextColor = SearchText,
                    cursorColor = SearchAccent
                )
            )
            Button(onClick = onSearch, enabled = uiState.searchQuery.isNotBlank() && !uiState.isSearching) {
                Icon(Icons.Rounded.Search, contentDescription = "Szukaj")
            }
        }

        uiState.searchError?.let {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF3A1E2B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = it,
                    color = Color(0xFFFFDDE6),
                    modifier = Modifier.padding(cardPadding)
                )
            }
        }

        when {
            uiState.isSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SearchAccent)
                }
            }
            uiState.searchResults.isEmpty() && uiState.searchQuery.isBlank() -> {
                EmptySearchState("Wpisz frazę, aby przeszukać wiadomości na serwerze.")
            }
            uiState.searchResults.isEmpty() -> {
                EmptySearchState("Brak wyników dla podanego zapytania.")
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(gap),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(uiState.searchResults, key = { it.id }) { message ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = SearchCard
                        ) {
                            Column(modifier = Modifier.padding(cardPadding)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = highlightQuery(message.senderFullName, uiState.searchQuery),
                                        color = SearchText,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(message.timestampSeconds * 1000)),
                                        color = SearchBody,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                if (message.streamName != null || message.topic.isNotBlank()) {
                                    Text(
                                        text = highlightQuery(
                                            listOfNotNull(message.streamName, message.topic.takeIf { it.isNotBlank() }).joinToString(" • "),
                                            uiState.searchQuery
                                        ),
                                        color = SearchAccent,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Text(
                                    text = highlightQuery(
                                        message.content
                                            .replace(Regex("<[^>]*>"), " ")
                                            .replace(Regex("\\s+"), " ")
                                            .trim(),
                                        uiState.searchQuery
                                    ),
                                    color = SearchBody,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = SearchBody, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun highlightQuery(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    val cleaned = text.ifBlank { "-" }
    val q = query.trim()
    if (q.isBlank()) {
        return buildAnnotatedString { append(cleaned) }
    }

    val lowerText = cleaned.lowercase()
    val lowerQuery = q.lowercase()

    return buildAnnotatedString {
        var current = 0
        while (current < cleaned.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, startIndex = current)
            if (matchIndex < 0) {
                append(cleaned.substring(current))
                break
            }

            if (matchIndex > current) {
                append(cleaned.substring(current, matchIndex))
            }

            val end = (matchIndex + lowerQuery.length).coerceAtMost(cleaned.length)
            withStyle(
                SpanStyle(
                    color = SearchAccent,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(cleaned.substring(matchIndex, end))
            }
            current = end
        }
    }
}
