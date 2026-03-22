package com.mkras.zulip.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mkras.zulip.data.local.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {

    @Query("SELECT * FROM topics WHERE streamId = :streamId ORDER BY maxMessageId DESC")
    fun observeTopics(streamId: Long): Flow<List<TopicEntity>>

    @Query("DELETE FROM topics WHERE streamId = :streamId")
    suspend fun clearForStream(streamId: Long)

    @Query("DELETE FROM topics")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(topics: List<TopicEntity>)
}
