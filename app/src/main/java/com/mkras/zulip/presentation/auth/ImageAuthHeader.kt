package com.mkras.zulip.presentation.auth

import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mkras.zulip.core.security.SecureSessionStorage

@Composable
fun rememberSharedImageAuthHeader(): String? {
    val context = LocalContext.current
    val sessionStorage = remember(context) { SecureSessionStorage(context.applicationContext) }
    val auth = remember(sessionStorage) { sessionStorage.getAuth() }
    return remember(auth?.email, auth?.apiKey) {
        val email = auth?.email.orEmpty()
        val apiKey = auth?.apiKey.orEmpty()
        if (email.isBlank() || apiKey.isBlank()) {
            null
        } else {
            val encoded = Base64.encodeToString("$email:$apiKey".toByteArray(), Base64.NO_WRAP)
            "Basic $encoded"
        }
    }
}