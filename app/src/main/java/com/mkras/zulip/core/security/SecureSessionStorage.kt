package com.mkras.zulip.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureSessionStorage @Inject constructor(
    @ApplicationContext context: Context
) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAuth(
        serverUrl: String,
        email: String,
        apiKey: String,
        authType: AuthType
    ) {
        prefs.edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_EMAIL, email)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_AUTH_TYPE, authType.name)
            .apply()
    }

    fun getAuth(): StoredAuth? {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null)
        val email = prefs.getString(KEY_EMAIL, null)
        val apiKey = prefs.getString(KEY_API_KEY, null)
        val authType = prefs.getString(KEY_AUTH_TYPE, null)

        if (serverUrl.isNullOrBlank() || email.isNullOrBlank() || apiKey.isNullOrBlank() || authType.isNullOrBlank()) {
            return null
        }

        return StoredAuth(
            serverUrl = if (serverUrl.startsWith("http://", ignoreCase = true)) {
                "https://${serverUrl.removePrefix("http://")}" 
            } else {
                serverUrl
            },
            email = email,
            apiKey = apiKey,
            authType = runCatching { AuthType.valueOf(authType) }.getOrDefault(AuthType.API_KEY)
        )
    }

    fun clearAuth() {
        prefs.edit().clear().apply()
    }

    fun saveCompactMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COMPACT_MODE, enabled).apply()
    }

    fun getCompactMode(): Boolean {
        return prefs.getBoolean(KEY_COMPACT_MODE, true)
    }

    fun saveFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT_SCALE, scale.coerceIn(0.85f, 1.30f)).apply()
    }

    fun getFontScale(): Float {
        return prefs.getFloat(KEY_FONT_SCALE, 1.0f).coerceIn(0.85f, 1.30f)
    }

    fun saveMarkdownEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MARKDOWN_ENABLED, enabled).apply()
    }

    fun getMarkdownEnabled(): Boolean {
        return prefs.getBoolean(KEY_MARKDOWN_ENABLED, true)
    }

    fun saveNotificationsEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_DM_NOTIFICATIONS_ENABLED, enabled)
            .putBoolean(KEY_CHANNEL_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun getNotificationsEnabled(): Boolean {
        return getDmNotificationsEnabled() || getChannelNotificationsEnabled()
    }

    fun saveDmNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DM_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getDmNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_DM_NOTIFICATIONS_ENABLED, true)
    }

    fun saveChannelNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHANNEL_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getChannelNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_CHANNEL_NOTIFICATIONS_ENABLED, true)
    }

    fun resetAllNotificationPreferences() {
        prefs.edit()
            .putBoolean(KEY_DM_NOTIFICATIONS_ENABLED, true)
            .putBoolean(KEY_CHANNEL_NOTIFICATIONS_ENABLED, true)
            .putStringSet(KEY_MUTED_CHANNELS, emptySet())
            .putStringSet(KEY_MUTED_DIRECT_MESSAGES, emptySet())
            .apply()
    }

    fun getMutedChannels(): Set<String> {
        return prefs.getStringSet(KEY_MUTED_CHANNELS, emptySet())
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
    }

    fun isChannelMuted(channelName: String): Boolean {
        val normalized = channelName.trim().lowercase()
        if (normalized.isBlank()) return false
        return getMutedChannels().contains(normalized)
    }

    fun setChannelMuted(channelName: String, muted: Boolean) {
        val normalized = channelName.trim().lowercase()
        if (normalized.isBlank()) return
        val updated = getMutedChannels().toMutableSet()
        if (muted) {
            updated.add(normalized)
        } else {
            updated.remove(normalized)
        }
        prefs.edit().putStringSet(KEY_MUTED_CHANNELS, updated).apply()
    }

    fun getDisabledChannels(): Set<String> {
        return prefs.getStringSet(KEY_DISABLED_CHANNELS, emptySet())
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
    }

    fun isChannelDisabled(channelName: String): Boolean {
        val normalized = channelName.trim().lowercase()
        if (normalized.isBlank()) return false
        return getDisabledChannels().contains(normalized)
    }

    fun setChannelDisabled(channelName: String, disabled: Boolean) {
        val normalized = channelName.trim().lowercase()
        if (normalized.isBlank()) return
        val updated = getDisabledChannels().toMutableSet()
        if (disabled) {
            updated.add(normalized)
        } else {
            updated.remove(normalized)
        }
        prefs.edit().putStringSet(KEY_DISABLED_CHANNELS, updated).apply()
    }

    fun getMutedDirectMessages(): Set<String> {
        return prefs.getStringSet(KEY_MUTED_DIRECT_MESSAGES, emptySet())
            ?.mapNotNull { normalizeDirectMessageKey(it) }
            ?.toSet()
            .orEmpty()
    }

    fun isDirectMessageMuted(conversationKey: String): Boolean {
        val normalized = normalizeDirectMessageKey(conversationKey) ?: return false
        return getMutedDirectMessages().contains(normalized)
    }

    fun setDirectMessageMuted(conversationKey: String, muted: Boolean) {
        val normalized = normalizeDirectMessageKey(conversationKey) ?: return
        val updated = getMutedDirectMessages().toMutableSet()
        if (muted) {
            updated.add(normalized)
        } else {
            updated.remove(normalized)
        }
        prefs.edit().putStringSet(KEY_MUTED_DIRECT_MESSAGES, updated).apply()
    }

    fun saveMentionCandidatesLastSyncTime(timestampMillis: Long) {
        prefs.edit().putLong(KEY_MENTION_CANDIDATES_LAST_SYNC, timestampMillis).apply()
    }

    fun getMentionCandidatesLastSyncTime(): Long {
        return prefs.getLong(KEY_MENTION_CANDIDATES_LAST_SYNC, 0L)
    }

    private fun normalizeDirectMessageKey(conversationKey: String): String? {
        val selfEmail = getAuth()?.email?.trim()?.lowercase().orEmpty()
        val normalizedParts = conversationKey
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .filter { part -> part != selfEmail }
            .sorted()

        return when {
            normalizedParts.isNotEmpty() -> normalizedParts.joinToString(",")
            conversationKey.trim().isNotBlank() -> conversationKey.trim().lowercase()
            else -> null
        }
    }

    companion object {
        private const val FILE_NAME = "secure_auth_store"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_EMAIL = "email"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_AUTH_TYPE = "auth_type"
        private const val KEY_COMPACT_MODE = "compact_mode"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_MARKDOWN_ENABLED = "markdown_enabled"
        private const val KEY_DM_NOTIFICATIONS_ENABLED = "dm_notifications_enabled"
        private const val KEY_CHANNEL_NOTIFICATIONS_ENABLED = "channel_notifications_enabled"
        private const val KEY_MUTED_CHANNELS = "muted_channels"
        private const val KEY_DISABLED_CHANNELS = "disabled_channels"
        private const val KEY_MUTED_DIRECT_MESSAGES = "muted_direct_messages"
        private const val KEY_MENTION_CANDIDATES_LAST_SYNC = "mention_candidates_last_sync"
        // Draft keys are dynamic: "draft_$conversationKey"
    }
}

enum class AuthType {
    PASSWORD,
    API_KEY
}

data class StoredAuth(
    val serverUrl: String,
    val email: String,
    val apiKey: String,
    val authType: AuthType
)
