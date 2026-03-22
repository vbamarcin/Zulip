package com.mkras.zulip.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.TextStyle
import com.mkras.zulip.core.security.AuthType

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onServerUrlChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onSecretChange: (String) -> Unit,
    onAuthTypeChange: (AuthType) -> Unit,
    onSubmit: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val serverFocusRequester = remember { FocusRequester() }
    val emailFocusRequester = remember { FocusRequester() }
    val secretFocusRequester = remember { FocusRequester() }

    val secretLabel = if (uiState.authType == AuthType.PASSWORD) "Hasło" else "Klucz API"
    val secretSupporting = if (uiState.authType == AuthType.PASSWORD) {
        "Pobierzemy klucz API przez /fetch_api_key z użyciem Basic Auth."
    } else {
        "Wklej klucz API wygenerowany dla użytkownika na serwerze Zulip."
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color(0xFFF4F8FF),
        unfocusedTextColor = Color(0xFFE0E9F8),
        focusedLabelColor = Color(0xFF9FDFFF),
        unfocusedLabelColor = Color(0xFF95A7C3),
        focusedPlaceholderColor = Color(0xFF6E809A),
        unfocusedPlaceholderColor = Color(0xFF6E809A),
        focusedBorderColor = Color(0xFF8CD9FF),
        unfocusedBorderColor = Color(0xFF5E6E87),
        cursorColor = Color(0xFF8CD9FF),
        focusedSupportingTextColor = Color(0xFF95A7C3),
        unfocusedSupportingTextColor = Color(0xFF95A7C3)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0E172A), Color(0xFF12243F), Color(0xFF060C14))
                )
            )
            .padding(20.dp)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xDD101A2D),
            tonalElevation = 0.dp,
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Toya Zulip 2.0",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFF5F7FF),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Logowanie do dowolnego serwera Zulipa z webowym modelem powiadomień.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB5C3D9)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AuthType.values().forEach { authType ->
                        val selected = uiState.authType == authType
                        AssistChip(
                            onClick = { onAuthTypeChange(authType) },
                            label = {
                                Text(
                                    if (authType == AuthType.PASSWORD) "Hasło" else "Klucz API"
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selected) Color(0xFF8CD9FF) else Color(0xFF1C2940),
                                labelColor = if (selected) Color(0xFF08111E) else Color(0xFFE0EAFA)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = onServerUrlChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(serverFocusRequester)
                        .onFocusChanged { if (it.isFocused) keyboardController?.show() },
                    label = { Text("Adres serwera") },
                    supportingText = { Text("Dozwolone są tylko adresy HTTPS.") },
                    placeholder = { Text("https://twoj-serwer.zulipchat.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onNext = {
                            emailFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    ),
                    textStyle = TextStyle(color = Color(0xFFF4F8FF)),
                    colors = fieldColors
                )

                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = onEmailChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(emailFocusRequester)
                        .onFocusChanged { if (it.isFocused) keyboardController?.show() },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onNext = {
                            secretFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    ),
                    textStyle = TextStyle(color = Color(0xFFF4F8FF)),
                    colors = fieldColors
                )

                OutlinedTextField(
                    value = uiState.secret,
                    onValueChange = onSecretChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(secretFocusRequester)
                        .onFocusChanged { if (it.isFocused) keyboardController?.show() },
                    label = { Text(secretLabel) },
                    supportingText = { Text(secretSupporting) },
                    singleLine = true,
                    visualTransformation = if (uiState.authType == AuthType.PASSWORD) {
                        PasswordVisualTransformation()
                    } else {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (uiState.authType == AuthType.PASSWORD) KeyboardType.Password else KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                            onSubmit()
                        }
                    ),
                    textStyle = TextStyle(color = Color(0xFFF4F8FF)),
                    colors = fieldColors
                )

                uiState.errorMessage?.let { error ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0x33FF6B6B)
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFFFD7D7),
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Button(
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSubmitting,
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text("Zaloguj")
                }

                Text(
                    text = "Tryb hasła pobiera klucz API przez Basic Auth. Tryb API key używa bezpośrednio danych użytkownika i zapisuje je w EncryptedSharedPreferences. Aplikacja akceptuje wyłącznie połączenia HTTPS.",
                    color = Color(0xFF8EA0BA),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
