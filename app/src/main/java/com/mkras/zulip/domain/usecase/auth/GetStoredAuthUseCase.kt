package com.mkras.zulip.domain.usecase.auth

import com.mkras.zulip.core.security.StoredAuth
import com.mkras.zulip.domain.repository.AuthRepository
import javax.inject.Inject

class GetStoredAuthUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): StoredAuth? = authRepository.getStoredAuth()
}
