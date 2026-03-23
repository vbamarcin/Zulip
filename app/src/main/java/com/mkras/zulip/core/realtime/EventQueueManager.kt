package com.mkras.zulip.core.realtime

import android.util.Log
import com.mkras.zulip.core.network.BasicCredentials
import com.mkras.zulip.core.network.ZulipApiFactory
import com.mkras.zulip.core.security.SecureSessionStorage
import com.mkras.zulip.core.security.StoredAuth
import com.mkras.zulip.data.remote.api.ZulipApiService
import com.mkras.zulip.data.remote.dto.RegisterResponseDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Singleton
class EventQueueManager @Inject constructor(
    private val secureSessionStorage: SecureSessionStorage,
    private val zulipApiFactory: ZulipApiFactory,
    private val eventProcessor: EventProcessor
) {

    private companion object {
        const val TAG = "EventQueueManager"
        const val EVENT_TYPES_PRIMARY = "[\"message\",\"typing\",\"presence\",\"reaction\",\"update_message_flags\",\"update_message\",\"delete_message\"]"
        const val EVENT_TYPES_NO_REACTION = "[\"message\",\"typing\",\"presence\",\"update_message_flags\",\"update_message\",\"delete_message\"]"
        const val EVENT_TYPES_COMPAT = "[\"message\",\"typing\",\"presence\"]"
        val EVENT_TYPE_CANDIDATES = listOf(
            EVENT_TYPES_PRIMARY,
            EVENT_TYPES_NO_REACTION,
            EVENT_TYPES_COMPAT
        )
    }

    private fun buildService(auth: StoredAuth) = zulipApiFactory.create(
        serverUrl = auth.serverUrl,
        credentials = BasicCredentials(email = auth.email, secret = auth.apiKey)
    )

    suspend fun runLoop() {
        var activeAuth = secureSessionStorage.getAuth() ?: return
        var service = buildService(activeAuth)

        var queueId: String? = null
        var lastEventId: Long? = null
        var dmNotificationsEnabled = secureSessionStorage.getDmNotificationsEnabled()
        var channelNotificationsEnabled = secureSessionStorage.getChannelNotificationsEnabled()
        var attempt = 0L

        while (currentCoroutineContext().isActive) {
            try {
                val latestAuth = secureSessionStorage.getAuth()
                if (latestAuth == null) {
                    queueId = null
                    lastEventId = null
                    delay(2_000L)
                    continue
                }

                if (latestAuth != activeAuth) {
                    activeAuth = latestAuth
                    service = buildService(activeAuth)
                    queueId = null
                    lastEventId = null
                    dmNotificationsEnabled = secureSessionStorage.getDmNotificationsEnabled()
                    channelNotificationsEnabled = secureSessionStorage.getChannelNotificationsEnabled()
                    attempt = 0
                }

                if (queueId.isNullOrBlank() || lastEventId == null) {
                    val registerResponse = registerWithFallback(service)
                    queueId = requireNotNull(registerResponse.queueId)
                    lastEventId = requireNotNull(registerResponse.lastEventId)
                    dmNotificationsEnabled = secureSessionStorage.getDmNotificationsEnabled()
                    channelNotificationsEnabled = secureSessionStorage.getChannelNotificationsEnabled()
                    attempt = 0
                }

                // Reflect runtime changes from app settings without restarting the service.
                dmNotificationsEnabled = secureSessionStorage.getDmNotificationsEnabled()
                channelNotificationsEnabled = secureSessionStorage.getChannelNotificationsEnabled()

                val eventsResponse = service.getEvents(
                    queueId = queueId,
                    lastEventId = lastEventId,
                    dontBlock = false
                )
                check(eventsResponse.result == "success") {
                    eventsResponse.message.ifBlank { "Błąd pobierania zdarzeń." }
                }

                for (event in eventsResponse.events) {
                    runCatching {
                        eventProcessor.process(
                            event = event,
                            dmNotificationsEnabled = dmNotificationsEnabled,
                            channelNotificationsEnabled = channelNotificationsEnabled,
                            selfEmail = activeAuth.email,
                            serverUrl = activeAuth.serverUrl
                        )
                    }.onFailure { throwable ->
                        Log.w(TAG, "Nie udało się przetworzyć eventu id=${event.id}, type=${event.type}", throwable)
                    }
                    if (event.id > (lastEventId ?: 0L)) {
                        lastEventId = event.id
                    }
                }

                eventsResponse.lastEventId?.let { responseLastId ->
                    if (responseLastId > (lastEventId ?: 0L)) {
                        lastEventId = responseLastId
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Błąd pętli eventów; reset kolejki i retry", e)
                queueId = null
                val backoffMs = (2_000L * (attempt + 1)).coerceAtMost(15_000L)
                attempt++
                delay(backoffMs)
            }
        }
    }

    private suspend fun registerWithFallback(service: ZulipApiService): RegisterResponseDto {
        var lastErrorMessage = "Nie udało się zarejestrować kolejki zdarzeń."

        for (eventTypes in EVENT_TYPE_CANDIDATES) {
            val response = service.registerEventQueue(
                eventTypesJson = eventTypes,
                allPublicStreams = false
            )
            if (response.result == "success") {
                if (eventTypes != EVENT_TYPES_PRIMARY) {
                    Log.w(TAG, "Rejestracja kolejki w trybie zgodności. event_types=$eventTypes")
                }
                return response
            }

            lastErrorMessage = response.message.ifBlank { lastErrorMessage }
            Log.w(TAG, "Nieudana rejestracja kolejki. event_types=$eventTypes, msg=${response.message}")
        }

        error(lastErrorMessage)
    }
}
