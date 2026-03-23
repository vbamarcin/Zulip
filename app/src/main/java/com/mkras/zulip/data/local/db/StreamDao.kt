package com.mkras.zulip.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import androidx.room.Query
import com.mkras.zulip.data.local.entity.StreamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamDao {

    @Query("SELECT * FROM streams ORDER BY name ASC")
    fun observeStreams(): Flow<List<StreamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(streams: List<StreamEntity>)

    @Query("DELETE FROM streams")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(streams: List<StreamEntity>) {
        clearAll()
        if (streams.isNotEmpty()) {
            upsertAll(streams)
        }
    }

    @Query("SELECT EXISTS(SELECT 1 FROM streams WHERE subscribed = 1 AND lower(name) = lower(:streamName))")
    suspend fun isSubscribedStream(streamName: String): Boolean

    @Query("SELECT name FROM streams WHERE id = :streamId LIMIT 1")
    suspend fun getNameById(streamId: Long): String?

    @Query("SELECT COUNT(*) FROM streams")
    suspend fun countStreams(): Int

    @Query("SELECT * FROM streams WHERE lower(name) = lower(:streamName) LIMIT 1")
    suspend fun getStreamByName(streamName: String): StreamEntity?
}
