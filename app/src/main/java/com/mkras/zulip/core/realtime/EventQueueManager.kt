package com.mkras.zulip.core.realtime

import com.mkras.zulip.core.network.BasicCredentials
import com.mkras.zulip.core.network.ZulipApiFactory
import com.mkras.zulip.core.security.SecureSessionStorage
import com.mkras.zulip.core.security.StoredAuth
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
                    val registerResponse = service.registerEventQueue(
                        eventTypesJson = EVENT_TYPES,
                        allPublicStreams = true
                    )
                    check(registerResponse.result == "success") {
                        registerResponse.message.ifBlank { "Nie udało się zarejestrować kolejki zdarzeń." }
                    }
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
            } catch (_: Throwable) {
                queueId = null
                val backoffMs = (2_000L * (attempt + 1)).coerceAtMost(15_000L)
                attempt++
                delay(backoffMs)
            }
        }
    }

    companion object {
        private const val EVENT_TYPES = "[\"message\",\"typing\",\"presence\",\"reaction\",\"update_message_flags\",\"update_message\",\"delete_message\"]"
    }
}
