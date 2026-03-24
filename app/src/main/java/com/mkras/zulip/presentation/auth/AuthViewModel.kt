package com.mkras.zulip.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkras.zulip.core.security.AuthType
import com.mkras.zulip.core.security.SecureSessionStorage
import com.mkras.zulip.core.security.StoredAuth
import com.mkras.zulip.domain.usecase.auth.GetStoredAuthUseCase
import com.mkras.zulip.domain.usecase.auth.LoginUseCase
import com.mkras.zulip.domain.usecase.auth.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val getStoredAuthUseCase: GetStoredAuthUseCase,
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val secureSessionStorage: SecureSessionStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                isReady = true,
                currentSession = getStoredAuthUseCase()
            )
        }
    }

    fun onServerUrlChange(value: String) {
        _uiState.update { it.copy(serverUrl = value, errorMessage = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    fun onSecretChange(value: String) {
        _uiState.update { it.copy(secret = value, errorMessage = null) }
    }

    fun onAuthTypeChange(value: AuthType) {
        _uiState.update { it.copy(authType = value, errorMessage = null, secret = "") }
    }

    fun submit() {
        val snapshot = uiState.value
        if (snapshot.isSubmitting) {
            return
        }

        if (snapshot.serverUrl.trim().startsWith("http://", ignoreCase = true)) {
            _uiState.update {
                it.copy(
                    errorMessage = "Adres serwera musi używać HTTPS. Połączenia HTTP są zablokowane."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = loginUseCase(
                serverUrl = snapshot.serverUrl,
                email = snapshot.email,
                secret = snapshot.secret,
                authType = snapshot.authType
            )

            result.onSuccess { session ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        currentSession = session,
                        secret = "",
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = throwable.message ?: "Logowanie nie powiodło się."
                    )
                }
            }
        }
    }

    fun logout() {
        logoutUseCase()
        _uiState.update {
            it.copy(
                currentSession = null,
                secret = "",
                errorMessage = null,
                isSubmitting = false
            )
        }
    }

    fun getCompactMode(): Boolean {
        return secureSessionStorage.getCompactMode()
    }

    fun saveCompactMode(enabled: Boolean) {
        secureSessionStorage.saveCompactMode(enabled)
    }

    fun getFontScale(): Float {
        return secureSessionStorage.getFontScale()
    }

    fun saveFontScale(scale: Float) {
        secureSessionStorage.saveFontScale(scale)
    }

    fun getMarkdownEnabled(): Boolean {
        return secureSessionStorage.getMarkdownEnabled()
    }

    fun saveMarkdownEnabled(enabled: Boolean) {
        secureSessionStorage.saveMarkdownEnabled(enabled)
    }

    fun getNotificationsEnabled(): Boolean {
        return secureSessionStorage.getNotificationsEnabled()
    }

    fun saveNotificationsEnabled(enabled: Boolean) {
        secureSessionStorage.saveNotificationsEnabled(enabled)
    }

    fun getDmNotificationsEnabled(): Boolean {
        return secureSessionStorage.getDmNotificationsEnabled()
    }

    fun saveDmNotificationsEnabled(enabled: Boolean) {
        secureSessionStorage.saveDmNotificationsEnabled(enabled)
    }

    fun getChannelNotificationsEnabled(): Boolean {
        return secureSessionStorage.getChannelNotificationsEnabled()
    }

    fun saveChannelNotificationsEnabled(enabled: Boolean) {
        secureSessionStorage.saveChannelNotificationsEnabled(enabled)
    }

    fun resetAllNotificationPreferences() {
        secureSessionStorage.resetAllNotificationPreferences()
    }

    fun isChannelMuted(channelName: String): Boolean {
        return secureSessionStorage.isChannelMuted(channelName)
    }

    fun setChannelMuted(channelName: String, muted: Boolean) {
        secureSessionStorage.setChannelMuted(channelName, muted)
    }

    fun getMutedChannels(): Set<String> {
        return secureSessionStorage.getLocalMutedChannels()
    }

    fun isTopicMuted(streamName: String, topicName: String): Boolean {
        return secureSessionStorage.isTopicMuted(streamName, topicName)
    }

    fun setTopicMuted(streamName: String, topicName: String, muted: Boolean) {
        secureSessionStorage.setTopicMuted(streamName, topicName, muted)
    }

    fun isChannelDisabled(channelName: String): Boolean {
        return secureSessionStorage.isChannelDisabled(channelName)
    }

    fun setChannelDisabled(channelName: String, disabled: Boolean) {
        secureSessionStorage.setChannelDisabled(channelName, disabled)
    }

    fun getDisabledChannels(): Set<String> {
        return secureSessionStorage.getDisabledChannels()
    }

    fun isDirectMessageMuted(conversationKey: String): Boolean {
        return secureSessionStorage.isDirectMessageMuted(conversationKey)
    }

    fun setDirectMessageMuted(conversationKey: String, muted: Boolean) {
        secureSessionStorage.setDirectMessageMuted(conversationKey, muted)
    }

    fun getBiometricLockEnabled(): Boolean = secureSessionStorage.getBiometricLockEnabled()

    fun saveBiometricLockEnabled(enabled: Boolean) = secureSessionStorage.saveBiometricLockEnabled(enabled)

    fun getAutoUpdateEnabled(): Boolean = secureSessionStorage.getAutoUpdateEnabled()

    fun saveAutoUpdateEnabled(enabled: Boolean) = secureSessionStorage.saveAutoUpdateEnabled(enabled)

    fun getGitHubToken(): String = secureSessionStorage.getGitHubToken()

    fun saveGitHubToken(token: String) = secureSessionStorage.saveGitHubToken(token)
}

data class AuthUiState(
    val isReady: Boolean = false,
    val isSubmitting: Boolean = false,
    val serverUrl: String = "https://",
    val email: String = "",
    val secret: String = "",
    val authType: AuthType = AuthType.API_KEY,
    val currentSession: StoredAuth? = null,
    val errorMessage: String? = null
)
