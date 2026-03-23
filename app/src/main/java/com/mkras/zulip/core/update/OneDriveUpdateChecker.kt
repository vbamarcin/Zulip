package com.mkras.zulip.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class OneDriveUpdateInfo(
    val version: String,
    val fileName: String
)

object OneDriveUpdateChecker {
    private val httpClient = OkHttpClient()

    suspend fun findLatestVersion(shareUrl: String): Result<OneDriveUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(shareUrl)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Nie udało się sprawdzić aktualizacji (${response.code})")
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@use null
                }

                val plainMatches = Regex(
                    pattern = """(?:Toya-)?Zulip-v(\d+\.\d+\.\d+)\.apk""",
                    option = RegexOption.IGNORE_CASE
                ).findAll(body)
                    .map { match ->
                        val version = match.groupValues[1]
                        OneDriveUpdateInfo(version = version, fileName = match.value)
                    }
                    .toList()

                val encodedMatches = Regex(
                    pattern = """(?:Toya-)?Zulip-v(\d+%2E\d+%2E\d+)%2Eapk""",
                    option = RegexOption.IGNORE_CASE
                ).findAll(body)
                    .map { match ->
                        val version = match.groupValues[1].replace("%2E", ".", ignoreCase = true)
                        val decodedName = match.value.replace("%2E", ".", ignoreCase = true)
                        OneDriveUpdateInfo(version = version, fileName = decodedName)
                    }
                    .toList()

                val all = (plainMatches + encodedMatches).distinctBy { it.version }
                if (all.isEmpty()) {
                    return@use null
                }

                all.maxWithOrNull(compareByVersion)
            }
        }
    }

    fun isNewerVersion(latest: String, current: String): Boolean {
        return compareVersions(latest, current) > 0
    }

    private val compareByVersion = Comparator<OneDriveUpdateInfo> { a, b ->
        compareVersions(a.version, b.version)
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split('.').map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split('.').map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(leftParts.size, rightParts.size)
        for (i in 0 until maxSize) {
            val l = leftParts.getOrElse(i) { 0 }
            val r = rightParts.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }
}
