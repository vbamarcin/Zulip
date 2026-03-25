package com.mkras.zulip.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private const val GITHUB_API_BASE = "https://api.github.com"

data class GitHubReleaseInfo(
    val tagName: String,
    val apkName: String,
    val apkUrl: String
)

object GitHubUpdateManager {
    private val httpClient = OkHttpClient()

    suspend fun checkForUpdate(
        owner: String,
        repo: String,
        currentVersionName: String,
        token: String? = null
    ): Result<GitHubReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBuilder = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/releases/latest")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
            if (!token.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            val request = requestBuilder.build()

            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    // Fallback for repositories that keep APK files in releases/ folder
                    // but do not publish GitHub Release objects.
                    return@use checkForUpdateFromReleasesDirectory(
                        owner = owner,
                        repo = repo,
                        currentVersionName = currentVersionName,
                        token = token
                    )
                }

                if (!response.isSuccessful) {
                    error("Nie można pobrać informacji o release (${response.code})")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    error("Pusta odpowiedź z GitHub API")
                }
                val json = JSONObject(body)
                val tagName = json.optString("tag_name").ifBlank { error("Brak tag_name") }
                val latestVersion = normalizeVersion(tagName)
                val currentVersion = normalizeVersion(currentVersionName)
                if (!isVersionNewer(latestVersion, currentVersion)) {
                    return@use null
                }

                val assets = json.optJSONArray("assets") ?: error("Brak assets w release")
                var apkName = ""
                var apkUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkName = name
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }

                if (apkName.isBlank() || apkUrl.isBlank()) {
                    error("W release nie znaleziono pliku APK")
                }

                GitHubReleaseInfo(
                    tagName = tagName,
                    apkName = apkName,
                    apkUrl = apkUrl
                )
            }
        }
    }

    suspend fun checkForUpdateFromReleaseNotes(
        owner: String,
        repo: String,
        currentVersionName: String,
        token: String? = null
    ): Result<GitHubReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://raw.githubusercontent.com/$owner/$repo/main/releases/RELEASES.md")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@use null
                }

                val versionMatch = Regex("""##\s*.*v(\d+\.\d+\.\d+)""", RegexOption.IGNORE_CASE)
                    .find(body)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: return@use null

                val latestVersion = normalizeVersion(versionMatch)
                val currentVersion = normalizeVersion(currentVersionName)
                if (!isVersionNewer(latestVersion, currentVersion)) {
                    return@use null
                }

                val explicitApk = Regex("""Zulip-v$latestVersion\.apk""", RegexOption.IGNORE_CASE)
                    .find(body)
                    ?.value
                val apkName = explicitApk ?: "Zulip-v$latestVersion.apk"
                GitHubReleaseInfo(
                    tagName = "v$latestVersion",
                    apkName = apkName,
                    apkUrl = "https://github.com/$owner/$repo/raw/main/releases/$apkName"
                )
            }
        }
    }

    private fun checkForUpdateFromReleasesDirectory(
        owner: String,
        repo: String,
        currentVersionName: String,
        token: String? = null
    ): GitHubReleaseInfo? {
        val requestBuilder = Request.Builder()
            .url("$GITHUB_API_BASE/repos/$owner/$repo/contents/releases")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        val request = requestBuilder.build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Nie można pobrać plików z folderu releases (${response.code})")
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                return null
            }

            val items = JSONArray(body)
            var bestVersion: String? = null
            var bestName: String? = null
            var bestUrl: String? = null

            val currentVersion = normalizeVersion(currentVersionName)

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val name = item.optString("name").orEmpty()
                if (!name.endsWith(".apk", ignoreCase = true)) {
                    continue
                }

                val version = extractVersionFromFileName(name) ?: continue
                if (!isVersionNewer(version, currentVersion)) {
                    continue
                }

                val candidateBetter = bestVersion == null || compareVersions(version, bestVersion!!) > 0
                if (candidateBetter) {
                    val directUrl = item.optString("download_url").orEmpty()
                    val apiUrl = item.optString("url").orEmpty()
                    val chosenUrl = if (directUrl.isNotBlank()) directUrl else apiUrl
                    if (chosenUrl.isNotBlank()) {
                        bestVersion = version
                        bestName = name
                        bestUrl = chosenUrl
                    }
                }
            }

            if (bestVersion == null || bestName == null || bestUrl == null) {
                return null
            }

            return GitHubReleaseInfo(
                tagName = "v$bestVersion",
                apkName = bestName!!,
                apkUrl = bestUrl!!
            )
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        release: GitHubReleaseInfo,
        token: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                error("Włącz zgodę na instalację nieznanych aplikacji dla Zulip")
            }

            val requestBuilder = Request.Builder()
                .url(release.apkUrl)
                .addHeader(
                    "Accept",
                    if (release.apkUrl.startsWith(GITHUB_API_BASE)) "application/vnd.github.raw" else "application/octet-stream"
                )
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
            if (!token.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            val request = requestBuilder.build()

            val updateDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "updates"
            )
            if (!updateDir.exists()) {
                updateDir.mkdirs()
            }
            val targetFile = File(updateDir, release.apkName)

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Nie udało się pobrać APK (${response.code})")
                }
                val body = response.body ?: error("Brak danych APK")
                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        }
    }

    private fun normalizeVersion(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V")
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        return compareVersions(latest, current) > 0
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

    private fun extractVersionFromFileName(fileName: String): String? {
        val match = Regex("""v?(\\d+\\.\\d+\\.\\d+)""").find(fileName) ?: return null
        return normalizeVersion(match.groupValues[1])
    }

    private fun legacyIsVersionNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split('.').map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxSize) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
