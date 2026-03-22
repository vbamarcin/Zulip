package com.mkras.zulip.core.network

import android.util.Base64
import com.mkras.zulip.BuildConfig
import com.mkras.zulip.data.remote.api.ZulipApiService
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZulipApiFactory @Inject constructor(
    private val moshi: Moshi
) {

    fun create(serverUrl: String, credentials: BasicCredentials? = null): ZulipApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)

        credentials?.let {
            clientBuilder.addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", it.toAuthorizationHeader())
                    .build()
                chain.proceed(request)
            })
        }

        return Retrofit.Builder()
            .baseUrl(normalizeServerUrl(serverUrl))
            .client(clientBuilder.build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ZulipApiService::class.java)
    }

    fun normalizeServerUrl(serverUrl: String): String {
        val trimmed = serverUrl.trim()
        require(!trimmed.startsWith("http://", ignoreCase = true)) {
            "Połączenia HTTP nie są wspierane. Użyj adresu HTTPS."
        }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}

data class BasicCredentials(
    val email: String,
    val secret: String
) {
    fun toAuthorizationHeader(): String {
        val raw = "$email:$secret"
        val encoded = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }
}
