package com.mkras.zulip.core.network

import android.util.Base64
import com.mkras.zulip.core.security.SecureSessionStorage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthHeaderProvider @Inject constructor(
    private val secureSessionStorage: SecureSessionStorage
) {

    fun basicAuthHeaderOrNull(): String? {
        val storedAuth = secureSessionStorage.getAuth() ?: return null
        val raw = "${storedAuth.email}:${storedAuth.apiKey}"
        val encoded = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }
}
