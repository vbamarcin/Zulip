package com.mkras.zulip.core.di

import com.mkras.zulip.data.repository.AuthRepositoryImpl
import com.mkras.zulip.data.repository.ChatRepositoryImpl
import com.mkras.zulip.data.repository.ChannelsRepositoryImpl
import com.mkras.zulip.domain.repository.AuthRepository
import com.mkras.zulip.domain.repository.ChatRepository
import com.mkras.zulip.domain.repository.ChannelsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(authRepositoryImpl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(chatRepositoryImpl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindChannelsRepository(channelsRepositoryImpl: ChannelsRepositoryImpl): ChannelsRepository
}
