package com.mkras.zulip.data.repository

import com.mkras.zulip.core.network.BasicCredentials
import com.mkras.zulip.core.network.ZulipApiFactory
import com.mkras.zulip.core.security.SecureSessionStorage
import com.mkras.zulip.data.local.db.StreamDao
import com.mkras.zulip.data.local.db.TopicDao
import com.mkras.zulip.data.local.entity.StreamEntity
import com.mkras.zulip.data.local.entity.TopicEntity
import com.mkras.zulip.domain.repository.ChannelsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ChannelsRepositoryImpl @Inject constructor(
    private val secureSessionStorage: SecureSessionStorage,
    private val zulipApiFactory: ZulipApiFactory,
    private val streamDao: StreamDao,
    private val topicDao: TopicDao
) : ChannelsRepository {

    override fun observeStreams(): Flow<List<StreamEntity>> = streamDao.observeStreams()

    override fun observeTopics(streamId: Long): Flow<List<TopicEntity>> = topicDao.observeTopics(streamId)

    override suspend fun refreshStreams() {
        val auth = secureSessionStorage.getAuth() ?: return
        val service = zulipApiFactory.create(
            serverUrl = auth.serverUrl,
            credentials = BasicCredentials(auth.email, auth.apiKey)
        )

        val subscriptionsResponse = service.getSubscriptions()
        check(subscriptionsResponse.result == "success") {
            subscriptionsResponse.message.ifBlank { "API zwrocilo blad podczas pobierania subskrypcji." }
        }

        val subscriptionsById = subscriptionsResponse.subscriptions.associateBy { it.id }
        val mutedChannels = subscriptionsResponse.subscriptions
            .filter { it.isMuted == true }
            .map { it.name }
            .toSet()
        secureSessionStorage.replaceMutedChannels(mutedChannels)

        val response = service.getStreams()
        check(response.result == "success") {
            response.message.ifBlank { "API zwrocilo blad podczas pobierania streamow." }
        }

        streamDao.replaceAll(
            response.streams.map {
                val subscription = subscriptionsById[it.id]
                StreamEntity(
                    id = it.id,
                    name = it.name,
                    description = it.description.orEmpty(),
                    subscribed = subscription != null,
                    isMuted = subscription?.isMuted ?: false,
                    desktopNotifications = subscription?.desktopNotifications ?: true
                )
            }
        )
    }

    override suspend fun refreshTopics(streamId: Long) {
        val auth = secureSessionStorage.getAuth() ?: return
        val service = zulipApiFactory.create(
            serverUrl = auth.serverUrl,
            credentials = BasicCredentials(auth.email, auth.apiKey)
        )

        val response = service.getTopics(streamId)
        check(response.result == "success") {
            response.message.ifBlank { "API zwrocilo blad podczas pobierania tematow." }
        }

        val apiTopics = response.topics.map {
            TopicEntity(
                key = "$streamId:${it.name}",
                streamId = streamId,
                name = it.name,
                maxMessageId = it.maxId
            )
        }

        val topicsToPersist = if (apiTopics.isNotEmpty()) {
            apiTopics
        } else {
            // Fallback: derive recent topics from latest stream messages.
            // Some servers/accounts can return an empty topics list even when messages exist.
            val latestMessages = service.getMessages(
                numBefore = 1000,
                applyMarkdown = false
            )
            if (latestMessages.result == "success") {
                latestMessages.messages
                    .asSequence()
                    .filter { it.type == "stream" && it.streamId == streamId }
                    .mapNotNull { dto ->
                        val topic = dto.subject.orEmpty().trim()
                        if (topic.isBlank()) null else topic to dto.id
                    }
                    .groupBy({ it.first }, { it.second })
                    .map { (topicName, ids) ->
                        TopicEntity(
                            key = "$streamId:$topicName",
                            streamId = streamId,
                            name = topicName,
                            maxMessageId = ids.maxOrNull() ?: 0L
                        )
                    }
                    .sortedByDescending { it.maxMessageId }
                    .toList()
            } else {
                emptyList()
            }
        }

        if (topicsToPersist.isNotEmpty()) {
            topicDao.clearForStream(streamId)
            topicDao.upsertAll(topicsToPersist)
        }
    }
}
