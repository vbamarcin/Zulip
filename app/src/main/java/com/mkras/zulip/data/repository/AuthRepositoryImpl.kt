package com.mkras.zulip.data.repository

import com.mkras.zulip.core.network.BasicCredentials
import com.mkras.zulip.core.network.ZulipApiFactory
import com.mkras.zulip.core.security.AuthType
import com.mkras.zulip.core.security.SecureSessionStorage
import com.mkras.zulip.core.security.StoredAuth
import com.mkras.zulip.data.local.db.DirectMessageCandidateDao
import com.mkras.zulip.data.local.db.MessageDao
import com.mkras.zulip.data.local.db.StreamDao
import com.mkras.zulip.data.local.db.TopicDao
import com.mkras.zulip.domain.repository.AuthRepository
import org.json.JSONObject
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val secureSessionStorage: SecureSessionStorage,
    private val zulipApiFactory: ZulipApiFactory,
    private val messageDao: MessageDao,
    private val streamDao: StreamDao,
    private val topicDao: TopicDao,
    private val directMessageCandidateDao: DirectMessageCandidateDao
) : AuthRepository {

    override fun getStoredAuth(): StoredAuth? = secureSessionStorage.getAuth()

    override suspend fun login(
        serverUrl: String,
        email: String,
        secret: String,
        authType: AuthType
    ): Result<StoredAuth> = runCatching {
        require(serverUrl.trim().isNotBlank()) { "Podaj adres serwera Zulip." }

        val normalizedServerUrl = zulipApiFactory.normalizeServerUrl(serverUrl)
        val normalizedEmail = email.trim()

        require(normalizedEmail.isNotBlank()) { "Podaj adres email." }
        require(secret.isNotBlank()) { messageForSecret(authType) }

        val loginResult = when (authType) {
            AuthType.PASSWORD -> fetchApiKey(
                serverUrl = normalizedServerUrl,
                email = normalizedEmail,
                password = secret
            )
            AuthType.API_KEY -> FetchApiKeyResult(
                email = normalizedEmail,
                apiKey = secret.trim()
            )
        }

        validateCredentials(
            serverUrl = normalizedServerUrl,
            email = loginResult.email,
            apiKey = loginResult.apiKey
        )

        StoredAuth(
            serverUrl = normalizedServerUrl,
            email = loginResult.email,
            apiKey = loginResult.apiKey,
            authType = authType
        ).also { storedAuth ->
            val previousAuth = secureSessionStorage.getAuth()
            val accountChanged = previousAuth?.let {
                !it.serverUrl.equals(storedAuth.serverUrl, ignoreCase = true) ||
                    !it.email.equals(storedAuth.email, ignoreCase = true)
            } == true

            if (accountChanged) {
                messageDao.clearAll()
                topicDao.clearAll()
                streamDao.clearAll()
                directMessageCandidateDao.clearAll()
            }

            secureSessionStorage.saveAuth(
                serverUrl = storedAuth.serverUrl,
                email = storedAuth.email,
                apiKey = storedAuth.apiKey,
                authType = storedAuth.authType
            )
        }
    }.recoverCatching { throwable ->
        throw IllegalStateException(readableErrorMessage(throwable), throwable)
    }

    override fun logout() {
        secureSessionStorage.clearAuth()
    }

    private suspend fun fetchApiKey(
        serverUrl: String,
        email: String,
        password: String
    ): FetchApiKeyResult {
        val service = zulipApiFactory.create(
            serverUrl = serverUrl,
            credentials = BasicCredentials(email = email, secret = password)
        )
        val response = service.fetchApiKey(email = email, password = password)
        check(response.result == "success") { response.message.ifBlank { "Nie udało się pobrać klucza API." } }
        return FetchApiKeyResult(
            email = response.email?.trim().orEmpty().ifBlank { email },
            apiKey = requireNotNull(response.apiKey) { "Serwer nie zwrócił klucza API." }
        )
    }

    private suspend fun validateCredentials(
        serverUrl: String,
        email: String,
        apiKey: String
    ) {
        val service = zulipApiFactory.create(
            serverUrl = serverUrl,
            credentials = BasicCredentials(email = email, secret = apiKey)
        )
        val response = service.registerEventQueue(
            eventTypesJson = "[\"message\",\"typing\",\"presence\"]"
        )
        check(response.result == "success") {
            response.message.ifBlank { "Nie udało się zweryfikować poświadczeń." }
        }
    }

    private fun messageForSecret(authType: AuthType): String {
        return when (authType) {
            AuthType.PASSWORD -> "Podaj hasło do konta Zulip."
            AuthType.API_KEY -> "Wklej klucz API użytkownika."
        }
    }

    private fun readableErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is HttpException -> {
                val body = throwable.response()?.errorBody()?.string().orEmpty()
                val parsed = runCatching {
                    if (body.isBlank()) {
                        null
                    } else {
                        JSONObject(body).optString("msg").ifBlank { null }
                    }
                }.getOrNull()

                parsed ?: when (throwable.code()) {
                    400 -> "Serwer odrzucił żądanie logowania (HTTP 400). Sprawdź URL serwera i dane logowania."
                    401 -> "Nieprawidłowe dane logowania (HTTP 401)."
                    403 -> "Brak uprawnień (HTTP 403)."
                    else -> "Błąd serwera (HTTP ${throwable.code()})."
                }
            }
            else -> throwable.message ?: "Logowanie nie powiodło się."
        }
    }

    private data class FetchApiKeyResult(
        val email: String,
        val apiKey: String
    )
}
