package com.mkras.zulip

import android.app.Application
import android.util.Base64
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.mkras.zulip.core.security.SecureSessionStorage
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient

@HiltAndroidApp
class ZulipApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val storage = SecureSessionStorage(this)
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val auth = storage.getAuth()
                val request = if (auth != null && auth.email.isNotBlank() && auth.apiKey.isNotBlank()) {
                    val encoded = Base64.encodeToString(
                        "${auth.email}:${auth.apiKey}".toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )
                    chain.request().newBuilder()
                        .header("Authorization", "Basic $encoded")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .build()
    }
}
