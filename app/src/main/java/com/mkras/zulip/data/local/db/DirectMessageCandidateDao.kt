package com.mkras.zulip.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mkras.zulip.data.local.entity.DirectMessageCandidateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectMessageCandidateDao {

    @Query("SELECT * FROM direct_message_candidates ORDER BY sortKey ASC")
    fun observeCandidates(): Flow<List<DirectMessageCandidateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(candidates: List<DirectMessageCandidateEntity>)

    @Query("DELETE FROM direct_message_candidates")
    suspend fun clearAll()
}