package com.mkras.zulip.data.remote.api

import com.mkras.zulip.data.remote.dto.EventsResponseDto
import com.mkras.zulip.data.remote.dto.FetchApiKeyResponseDto
import com.mkras.zulip.data.remote.dto.MessageFlagsResponseDto
import com.mkras.zulip.data.remote.dto.MessagesResponseDto
import com.mkras.zulip.data.remote.dto.RegisterResponseDto
import com.mkras.zulip.data.remote.dto.StreamsResponseDto
import com.mkras.zulip.data.remote.dto.TopicsResponseDto
import com.mkras.zulip.data.remote.dto.SendMessageResponseDto
import com.mkras.zulip.data.remote.dto.UploadFileResponseDto
import com.mkras.zulip.data.remote.dto.ReactionResponseDto
import com.mkras.zulip.data.remote.dto.MessageActionResponseDto
import com.mkras.zulip.data.remote.dto.UsersResponseDto
import com.mkras.zulip.data.remote.dto.MyProfileResponseDto
import com.mkras.zulip.data.remote.dto.PresenceResponseDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ZulipApiService {

    @FormUrlEncoded
    @POST("api/v1/fetch_api_key")
    suspend fun fetchApiKey(
        @Field("username") email: String,
        @Field("password") password: String
    ): FetchApiKeyResponseDto

    @FormUrlEncoded
    @POST("api/v1/register")
    suspend fun registerEventQueue(
        @Field("event_types") eventTypesJson: String,
        @Field("all_public_streams") allPublicStreams: Boolean = false,
        @Field("narrow") narrowJson: String? = null,
        @Field("client_gravatar") clientGravatar: Boolean = true
    ): RegisterResponseDto

    @GET("api/v1/events")
    suspend fun getEvents(
        @Query("queue_id") queueId: String,
        @Query("last_event_id") lastEventId: Long,
        @Query("dont_block") dontBlock: Boolean = false
    ): EventsResponseDto

    @GET("api/v1/messages")
    suspend fun getMessages(
        @Query("anchor") anchor: String = "newest",
        @Query("num_before") numBefore: Int = 50,
        @Query("num_after") numAfter: Int = 0,
        @Query("narrow") narrowJson: String? = null,
        @Query("apply_markdown") applyMarkdown: Boolean = true
    ): MessagesResponseDto

    @FormUrlEncoded
    @POST("api/v1/messages/flags")
    suspend fun updateMessageFlags(
        @Field("messages") messagesJson: String,
        @Field("op") operation: String,
        @Field("flag") flag: String
    ): MessageFlagsResponseDto

    @GET("api/v1/streams")
    suspend fun getStreams(): StreamsResponseDto

    @GET("api/v1/users/me/{stream_id}/topics")
    suspend fun getTopics(
        @Path("stream_id") streamId: Long
    ): TopicsResponseDto

    @GET("api/v1/users")
    suspend fun getUsers(): UsersResponseDto

    @GET("api/v1/users/me")
    suspend fun getMyProfile(): MyProfileResponseDto

    @GET("api/v1/users/me/presence")
    suspend fun getAllPresences(): PresenceResponseDto

    @FormUrlEncoded
    @POST("api/v1/messages")
    suspend fun sendMessage(
        @Field("type") type: String,
        @Field("to") to: String,
        @Field("content") content: String,
        @Field("topic") topic: String? = null
    ): SendMessageResponseDto

    @Multipart
    @POST("api/v1/user_uploads")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part
    ): UploadFileResponseDto

    @FormUrlEncoded
    @POST("api/v1/messages/{message_id}/reactions")
    suspend fun addReaction(
        @Path("message_id") messageId: Long,
        @Field("emoji_name") emojiName: String,
        @Field("emoji_code") emojiCode: String? = null,
        @Field("reaction_type") reactionType: String = "unicode_emoji"
    ): ReactionResponseDto

    @DELETE("api/v1/messages/{message_id}/reactions")
    suspend fun removeReaction(
        @Path("message_id") messageId: Long,
        @Query("emoji_name") emojiName: String,
        @Query("emoji_code") emojiCode: String? = null,
        @Query("reaction_type") reactionType: String = "unicode_emoji"
    ): ReactionResponseDto

    @FormUrlEncoded
    @PATCH("api/v1/messages/{message_id}")
    suspend fun editMessage(
        @Path("message_id") messageId: Long,
        @Field("content") content: String,
        @Field("topic") topic: String? = null,
        @Field("stream_id") streamId: Long? = null
    ): MessageActionResponseDto

    @DELETE("api/v1/messages/{message_id}")
    suspend fun deleteMessage(
        @Path("message_id") messageId: Long
    ): MessageActionResponseDto

}
