package com.mkras.zulip.domain.usecase.auth

import com.mkras.zulip.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke() = authRepository.logout()
}
