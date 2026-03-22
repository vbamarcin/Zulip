package com.mkras.zulip.domain.repository

import com.mkras.zulip.data.local.entity.StreamEntity
import com.mkras.zulip.data.local.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

interface ChannelsRepository {
    fun observeStreams(): Flow<List<StreamEntity>>
    fun observeTopics(streamId: Long): Flow<List<TopicEntity>>
    suspend fun refreshStreams()
    suspend fun refreshTopics(streamId: Long)
}
