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
        token: String
    ): Result<GitHubReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/releases/latest")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .build()

            httpClient.newCall(request).execute().use { response ->
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

    suspend fun downloadAndInstall(
        context: Context,
        release: GitHubReleaseInfo,
        token: String
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
                error("Włącz zgodę na instalację nieznanych aplikacji dla Toya Zulip")
            }

            val request = Request.Builder()
                .url(release.apkUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/octet-stream")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .build()

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
