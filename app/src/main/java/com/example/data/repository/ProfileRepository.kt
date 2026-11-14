package com.example.data.repository

import com.example.data.logger.AppLogger
import com.example.data.logger.LogContext
import com.example.data.model.ProfileFetchOutcome
import com.example.data.network.CullinanHttpClient
import com.example.data.parser.ProfileParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

class ProfilePhotoSessionExpiredException(message: String) : Exception(message)

class ProfileRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val profileMutex = Mutex()
    private val photoMutex = Mutex()

    suspend fun fetchProfile(): ProfileFetchOutcome = withContext(ioDispatcher) {
        val flowId = AppLogger.generateFlowId("profile")
        AppLogger.logInfo("ProfileRepository", "FetchProfile", "Fetching MyProfile.aspx", flowId)

        profileMutex.withLock {
            try {
                val request = Request.Builder()
                    .url(CullinanHttpClient.PROFILE_URL)
                    .get()
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "fa-IR,fa;q=0.9")
                    .header("Referer", "${CullinanHttpClient.BASE_URL}/Master/Default.aspx")
                    .tag(LogContext::class.java, LogContext(flowId, "FetchProfile"))
                    .build()

                var statusCode = 0
                var locationHeader: String? = null
                var finalUrl: String? = null
                var html = ""

                CullinanHttpClient.executeCallNoRedirect(request).use { response ->
                    statusCode = response.code
                    locationHeader = response.header("Location")
                    finalUrl = response.request.url.toString()
                    if (!response.isRedirect) {
                        html = response.body?.string() ?: ""
                    }
                }

                if (statusCode in 300..399) {
                    val loc = locationHeader.orEmpty()
                    AppLogger.logWarn(
                        component = "ProfileRepository",
                        operation = "FetchProfileRedirect",
                        message = "MyProfile returned HTTP $statusCode redirect (to=${loc.substringBefore('?')})",
                        flowId = flowId,
                        errorCategory = "PROFILE_REDIRECT"
                    )
                    return@withLock when {
                        loc.contains("Login.aspx", ignoreCase = true) ->
                            ProfileFetchOutcome.SessionExpired("نشست کاری در سامانه منقضی شده است. لطفاً مجدداً وارد شوید.")
                        loc.contains("MyMessage.aspx", ignoreCase = true) ->
                            ProfileFetchOutcome.UnreadMessagesRequired("برای دسترسی به پروفایل، ابتدا باید پیام‌های خوانده‌نشده سامانه مطالعه شوند.")
                        else ->
                            ProfileFetchOutcome.Failure("دریافت صفحه پروفایل با هدایت غیرمنتظره ($statusCode) متوقف شد.")
                    }
                }

                if (statusCode >= 500) {
                    AppLogger.logWarn(
                        component = "ProfileRepository",
                        operation = "FetchProfileServerError",
                        message = "MyProfile returned HTTP $statusCode",
                        flowId = flowId,
                        errorCategory = "PROFILE_HTTP_$statusCode"
                    )
                    return@withLock ProfileFetchOutcome.Failure("خطای داخلی سرور دانشگاه هنگام دریافت پروفایل (کد $statusCode)")
                }

                if (statusCode !in 200..299 || html.isBlank()) {
                    AppLogger.logWarn(
                        component = "ProfileRepository",
                        operation = "FetchProfileHttpError",
                        message = "MyProfile returned HTTP $statusCode (len=${html.length})",
                        flowId = flowId,
                        errorCategory = "PROFILE_HTTP_ERROR"
                    )
                    return@withLock ProfileFetchOutcome.Failure("دریافت اطلاعات پروفایل از سرور ناموفق بود (کد $statusCode)")
                }

                ProfileParser.parse(
                    html = html,
                    httpStatusCode = statusCode,
                    finalUrl = finalUrl,
                    flowId = flowId
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.logError(
                    tag = "ProfileRepository",
                    message = "FetchProfile exception: ${e.message}",
                    throwable = e,
                    flowId = flowId,
                    operation = "FetchProfileException",
                    errorCategory = "PROFILE_NETWORK_ERROR"
                )
                ProfileFetchOutcome.Failure("خطا در برقراری ارتباط با سرور برای دریافت پروفایل کاربری")
            }
        }
    }

    suspend fun fetchProfilePhoto(): Result<ByteArray?> = withContext(ioDispatcher) {
        val flowId = AppLogger.generateFlowId("avatar")
        photoMutex.withLock {
            try {
                val request = Request.Builder()
                    .url(CullinanHttpClient.USER_IMAGE_URL)
                    .get()
                    .header("Accept", "image/*")
                    .header("Referer", "${CullinanHttpClient.BASE_URL}/Master/Default.aspx")
                    .tag(LogContext::class.java, LogContext(flowId, "FetchProfilePhoto"))
                    .build()

                var statusCode = 0
                var locationHeader: String? = null
                var contentType: String? = null
                var bodyBytes: ByteArray? = null

                CullinanHttpClient.executeCallNoRedirect(request).use { response ->
                    statusCode = response.code
                    locationHeader = response.header("Location")
                    contentType = response.body?.contentType()?.toString()
                    if (response.isSuccessful) {
                        val contentLen = response.body?.contentLength() ?: -1L
                        if (contentLen <= MAX_IMAGE_BYTES) {
                            bodyBytes = response.body?.bytes()
                        }
                    }
                }

                if (statusCode in 300..399) {
                    val loc = locationHeader.orEmpty()
                    if (loc.contains("Login.aspx", ignoreCase = true)) {
                        AppLogger.logWarn(
                            component = "ProfileRepository",
                            operation = "FetchPhotoSessionExpired",
                            message = "UserImage.ashx redirected to Login.aspx",
                            flowId = flowId,
                            errorCategory = "AVATAR_SESSION_EXPIRED"
                        )
                        return@withLock Result.failure(
                            ProfilePhotoSessionExpiredException("نشست کاری منقضی شده است")
                        )
                    }
                    AppLogger.logDebug(
                        "ProfileRepository",
                        "FetchPhotoRedirect",
                        "UserImage.ashx redirected (status=$statusCode); falling back to default avatar",
                        flowId
                    )
                    return@withLock Result.success(null)
                }

                if (statusCode == 404) {
                    AppLogger.logDebug("ProfileRepository", "FetchPhotoNotFound", "UserImage.ashx returned 404; using local avatar", flowId)
                    return@withLock Result.success(null)
                }

                if (statusCode !in 200..299) {
                    return@withLock Result.failure(Exception("خطا در دریافت تصویر پروفایل (کد $statusCode)"))
                }

                val bytes = bodyBytes
                if (bytes == null || bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) {
                    AppLogger.logWarn(
                        component = "ProfileRepository",
                        operation = "FetchPhotoInvalidSize",
                        message = "Rejected UserImage.ashx payload (size=${bytes?.size ?: -1})",
                        flowId = flowId,
                        errorCategory = "AVATAR_INVALID_SIZE"
                    )
                    return@withLock Result.success(null)
                }

                val isAllowedMime = isAllowedImageContentType(contentType)
                val hasValidSignature = isSupportedImageMagicBytes(bytes)
                if (!isAllowedMime || !hasValidSignature) {
                    AppLogger.logWarn(
                        component = "ProfileRepository",
                        operation = "FetchPhotoRejectedPayload",
                        message = "Rejected non-image UserImage.ashx payload (mime=${contentType ?: "null"}, validMagic=$hasValidSignature)",
                        flowId = flowId,
                        errorCategory = "AVATAR_NON_IMAGE"
                    )
                    return@withLock Result.failure(Exception("پاسخ دریافت‌شده تصویر معتبر نیست"))
                }

                AppLogger.logDebug(
                    "ProfileRepository",
                    "FetchPhotoSuccess",
                    "Loaded profile photo in memory (${bytes.size} bytes, mime=${contentType ?: "unknown"})",
                    flowId
                )
                Result.success(bytes)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.logWarn(
                    component = "ProfileRepository",
                    operation = "FetchPhotoError",
                    message = "Failed to fetch UserImage.ashx: ${e.message}",
                    throwable = e,
                    flowId = flowId,
                    errorCategory = "AVATAR_FETCH_ERROR"
                )
                Result.failure(e)
            }
        }
    }

    internal companion object {
        private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024

        internal fun isAllowedImageContentType(contentType: String?): Boolean {
            if (contentType.isNullOrBlank()) return false
            val normalized = contentType.substringBefore(';').trim().lowercase()
            return normalized == "image/jpg" ||
                normalized == "image/jpeg" ||
                normalized == "image/png" ||
                normalized == "image/webp" ||
                normalized == "image/gif" ||
                normalized == "application/octet-stream"
        }

        internal fun isSupportedImageMagicBytes(bytes: ByteArray): Boolean {
            if (bytes.size < 4) return false
            // JPEG: FF D8 FF
            val isJpeg = (bytes[0].toInt() and 0xFF) == 0xFF &&
                (bytes[1].toInt() and 0xFF) == 0xD8 &&
                (bytes[2].toInt() and 0xFF) == 0xFF
            // PNG: 89 50 4E 47
            val isPng = (bytes[0].toInt() and 0xFF) == 0x89 &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()
            // GIF: GIF8
            val isGif = bytes[0] == 'G'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == '8'.code.toByte()
            // WebP: RIFF....WEBP
            val isWebp = bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() &&
                bytes[11] == 'P'.code.toByte()
            return isJpeg || isPng || isGif || isWebp
        }
    }
}
