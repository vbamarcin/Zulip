package com.mkras.zulip.domain.repository

import com.mkras.zulip.core.security.AuthType
import com.mkras.zulip.core.security.StoredAuth

interface AuthRepository {
    fun getStoredAuth(): StoredAuth?

    suspend fun login(
        serverUrl: String,
        email: String,
        secret: String,
        authType: AuthType
    ): Result<StoredAuth>

    fun logout()
}
