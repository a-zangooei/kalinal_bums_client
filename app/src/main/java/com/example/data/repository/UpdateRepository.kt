package com.example.data.repository

import com.example.BuildConfig
import com.example.data.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UpdateRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        const val GITHUB_OWNER = "a-zangooei"
        const val GITHUB_REPO = "kalinan_bums_client"
        const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        const val FALLBACK_RELEASE_WEB_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    }

    suspend fun checkForUpdate(
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Kalinan-Android-Client")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("عدم دریافت اطلاعات نسخه از گیت‌هاب (کد خطا: ${response.code})")
                )
            }

            val bodyString = response.body?.string() ?: ""
            if (bodyString.isBlank()) {
                return@withContext Result.failure(Exception("پاسخ سرور گیت‌هاب خالی بود"))
            }

            val json = JSONObject(bodyString)
            val tagName = json.optString("tag_name", "").trim()
            val rawBody = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")

            var downloadUrl = FALLBACK_RELEASE_WEB_URL
            val assetsArray = json.optJSONArray("assets")
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", downloadUrl)
                        break
                    }
                }
            }

            val changelogSnippet = extractChangelogSnippet(rawBody)
            val isNewer = isNewerVersion(latest = tagName, current = currentVersionName)

            Result.success(
                AppUpdateInfo(
                    isUpdateAvailable = isNewer,
                    latestVersionName = tagName.removePrefix("v").removePrefix("V"),
                    currentVersionName = currentVersionName.removePrefix("v").removePrefix("V"),
                    downloadUrl = downloadUrl,
                    changelogSnippet = changelogSnippet,
                    publishedAt = publishedAt
                )
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    fun extractChangelogSnippet(rawBody: String): String {
        if (rawBody.isBlank()) return "بهبودهای کلی در عملکرد و امنیت برنامه"

        // Search for known Persian or English changelog headers
        val headers = listOf(
            "### 📝 تغییرات این نسخه",
            "### تغییرات این نسخه",
            "### 📢 پیام این نسخه",
            "## 📝 تغییرات این نسخه",
            "## تغییرات این نسخه",
            "### Changelog",
            "## Changelog"
        )

        for (header in headers) {
            val startIndex = rawBody.indexOf(header)
            if (startIndex != -1) {
                val afterHeader = rawBody.substring(startIndex + header.length).trim()
                // Take content up to the next horizontal rule '---' or next header '###'
                val endMarkerIndex = listOf(
                    afterHeader.indexOf("\n---"),
                    afterHeader.indexOf("\n###"),
                    afterHeader.indexOf("\n##")
                ).filter { it != -1 }.minOrNull()

                val extracted = if (endMarkerIndex != null) {
                    afterHeader.substring(0, endMarkerIndex).trim()
                } else {
                    afterHeader.trim()
                }

                if (extracted.isNotBlank()) {
                    return extracted
                }
            }
        }

        // Fallback: extract bullet points if any exist
        val bulletLines = rawBody.lines()
            .map { it.trim() }
            .filter { it.startsWith("•") || it.startsWith("-") || it.startsWith("*") }
            .take(5)

        return if (bulletLines.isNotEmpty()) {
            bulletLines.joinToString("\n")
        } else {
            "بهبود ارتباط با سرور و ارتقای امنیت برنامه"
        }
    }

    fun isNewerVersion(latest: String, current: String): Boolean {
        val cleanLatest = latest.trim().removePrefix("v").removePrefix("V").split("-")[0]
        val cleanCurrent = current.trim().removePrefix("v").removePrefix("V").split("-")[0]

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        if (latestParts.isEmpty() || currentParts.isEmpty()) return false

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }

        return false
    }
}
