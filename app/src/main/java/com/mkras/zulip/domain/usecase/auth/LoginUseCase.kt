package com.mkras.zulip.domain.usecase.auth

import com.mkras.zulip.core.security.AuthType
import com.mkras.zulip.core.security.StoredAuth
import com.mkras.zulip.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        serverUrl: String,
        email: String,
        secret: String,
        authType: AuthType
    ): Result<StoredAuth> {
        return authRepository.login(
            serverUrl = serverUrl,
            email = email,
            secret = secret,
            authType = authType
        )
    }
}
