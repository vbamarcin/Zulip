package com.mkras.zulip.core.di

import android.content.Context
import androidx.room.Room
import com.mkras.zulip.data.local.db.DirectMessageCandidateDao
import com.mkras.zulip.core.security.SecureSessionStorage
import com.mkras.zulip.data.local.db.MessageDao
import com.mkras.zulip.data.local.db.StreamDao
import com.mkras.zulip.data.local.db.TopicDao
import com.mkras.zulip.data.local.db.ZulipDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideZulipDatabase(@ApplicationContext context: Context): ZulipDatabase {
        return Room.databaseBuilder(
            context,
            ZulipDatabase::class.java,
            "zulip.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: ZulipDatabase): MessageDao = database.messageDao()

    @Provides
    @Singleton
    fun provideStreamDao(database: ZulipDatabase): StreamDao = database.streamDao()

    @Provides
    @Singleton
    fun provideTopicDao(database: ZulipDatabase): TopicDao = database.topicDao()

    @Provides
    @Singleton
    fun provideDirectMessageCandidateDao(database: ZulipDatabase): DirectMessageCandidateDao = database.directMessageCandidateDao()

    @Provides
    @Singleton
    fun provideSecureSessionStorage(@ApplicationContext context: Context): SecureSessionStorage {
        return SecureSessionStorage(context)
    }
}
