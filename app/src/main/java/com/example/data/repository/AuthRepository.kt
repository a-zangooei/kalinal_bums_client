package com.example.data.repository

import com.example.data.crypto.RsaEncryptor
import com.example.data.logger.AppLogger
import com.example.data.logger.LogContext
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.LogSanitizer
import com.example.data.model.LoginPageState
import com.example.data.model.MealType
import com.example.data.model.UserSession
import com.example.data.model.WeeklyMenu
import com.example.data.network.CullinanHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

class AuthRepository {

    private val sessionMutex = Mutex()

    suspend fun fetchLoginPage(): Result<LoginPageState> = withContext(Dispatchers.IO) {
        val flowId = AppLogger.generateFlowId("auth_init")
        AppLogger.log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = "AuthRepository",
                operation = "FetchLoginPage",
                flowId = flowId,
                message = "Initiating fetch of login page"
            )
        )

        sessionMutex.withLock {
            try {
                // Start with a clean session cookie so pre-login state cannot collide with old cookies
                CullinanHttpClient.cookieJar.clear()

                val request = Request.Builder()
                    .url(CullinanHttpClient.LOGIN_URL)
                    .get()
                    .tag(LogContext::class.java, LogContext(flowId, "FetchLoginPage"))
                    .build()

                var isSuccessful = false
                var responseCode = 0
                var html = ""
                CullinanHttpClient.executeCall(request).use { response ->
                    isSuccessful = response.isSuccessful
                    responseCode = response.code
                    if (isSuccessful) {
                        html = response.body?.string() ?: ""
                    }
                }
                if (!isSuccessful) {
                    AppLogger.logError("AuthRepository", "Fetch login page failed with HTTP $responseCode", flowId = flowId)
                    return@withLock Result.failure(Exception("خطا در برقراری ارتباط با سرور دانشگاه ($responseCode)"))
                }

                val doc = Jsoup.parse(html)

                val viewState = doc.select("input[name=__VIEWSTATE]").`val`()
                val viewStateGenerator = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`().ifEmpty { "C2EE9ABB" }
                val publicKeyPem = doc.select("#publicKeytxt, textarea[name=publicKeytxt]").text().trim()

                AppLogger.log(
                    LogEntry(
                        timestamp = "",
                        level = if (viewState.isBlank() || publicKeyPem.isBlank()) LogLevel.WARN else LogLevel.INFO,
                        component = "AuthRepository",
                        operation = "FetchLoginPageSuccess",
                        flowId = flowId,
                        errorCategory = if (publicKeyPem.isBlank()) "MISSING_RSA_PUBLIC_KEY" else if (viewState.isBlank()) "MISSING_VIEWSTATE" else null,
                        message = "Login page fetched (VS len=${viewState.length}, gen=$viewStateGenerator, PEM len=${publicKeyPem.length}, PEM fp=${LogSanitizer.computeFingerprint(publicKeyPem)})"
                    )
                )

                // Fetch live captcha with the exact same session
                val captchaBytes = fetchCaptchaBytesInternal(flowId)
                if (captchaBytes == null || captchaBytes.isEmpty()) {
                    AppLogger.logWarn(
                        component = "AuthRepository",
                        operation = "FetchLoginCaptchaMissing",
                        message = "Login page loaded but initial captcha image was empty or failed",
                        flowId = flowId,
                        errorCategory = "CAPTCHA_EMPTY"
                    )
                }

                Result.success(
                    LoginPageState(
                        viewState = viewState,
                        viewStateGenerator = viewStateGenerator,
                        publicKeyPem = publicKeyPem,
                        captchaBytes = captchaBytes,
                        isLoading = false
                    )
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.logError("AuthRepository", "Fetch login page exception: ${e.message}", e, flowId = flowId, operation = "FetchLoginPageError", errorCategory = "AUTH_INIT_EXCEPTION")
                Result.failure(e)
            }
        }
    }

    suspend fun refreshCaptcha(): Result<ByteArray> = withContext(Dispatchers.IO) {
        val flowId = AppLogger.generateFlowId("captcha")
        sessionMutex.withLock {
            try {
                val bytes = fetchCaptchaBytesInternal(flowId)
                if (bytes != null) {
                    Result.success(bytes)
                } else {
                    AppLogger.logError("AuthRepository", "Failed to retrieve fresh captcha bytes", flowId = flowId)
                    Result.failure(Exception("دریافت تصویر کد امنیتی از سرور ناموفق بود"))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.logError("AuthRepository", "Captcha refresh exception: ${e.message}", e, flowId = flowId)
                Result.failure(e)
            }
        }
    }

    suspend fun login(
        usernamePlain: String,
        passwordPlain: String,
        captchaInput: String,
        pageState: LoginPageState
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        val flowId = AppLogger.generateFlowId("login")
        AppLogger.log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = "AuthRepository",
                operation = "LoginAttempt",
                flowId = flowId,
                message = "Initiating user login authentication attempt"
            )
        )

        sessionMutex.withLock {
            try {
                if (pageState.publicKeyPem.isBlank()) {
                    AppLogger.logError("AuthRepository", "Public key missing from page state", flowId = flowId)
                    return@withLock Result.failure(Exception("کلید عمومی رمزنگاری از سرور دریافت نشده است"))
                }

                // RSA encrypt username and password using public key from server
                val encryptedUsername = RsaEncryptor.encryptWithPem(usernamePlain.trim(), pageState.publicKeyPem)
                val encryptedPassword = RsaEncryptor.encryptWithPem(passwordPlain.trim(), pageState.publicKeyPem)

                val formBody = FormBody.Builder()
                    .add("__EVENTTARGET", "")
                    .add("__EVENTARGUMENT", "")
                    .add("__VIEWSTATE", pageState.viewState)
                    .add("__VIEWSTATEGENERATOR", pageState.viewStateGenerator)
                    .add("__VIEWSTATEENCRYPTED", "")
                    .add("txtUsernamePlain", "")
                    .add("txtPasswordPlain", "")
                    .add("txtUsername", encryptedUsername)
                    .add("txtPassword", encryptedPassword)
                    .add("txtCaptcha", captchaInput.trim())
                    .add("btnLogin", "ورود سمت سرور ")
                    .add("CanSaveloginPasstxt", "True")
                    .add("loginSecurtxt", "True")
                    .add("publicKeytxt", pageState.publicKeyPem)
                    .build()

                val request = Request.Builder()
                    .url(CullinanHttpClient.LOGIN_URL)
                    .post(formBody)
                    .header("Referer", CullinanHttpClient.LOGIN_URL)
                    .header("Origin", CullinanHttpClient.BASE_URL)
                    .tag(LogContext::class.java, LogContext(flowId, "LoginPost"))
                    .build()

                var statusCode = 0
                var location = ""
                var bodyString = ""
                CullinanHttpClient.executeCallNoRedirect(request).use { response ->
                    statusCode = response.code
                    location = response.header("Location") ?: ""
                    bodyString = response.body?.string() ?: ""
                }

                // Detection of successful login strictly based on 302 Location header:
                val isRedirect = statusCode in 300..399 || location.isNotBlank()
                val isMessagesRedirect = location.contains("MyMessage.aspx", ignoreCase = true)
                val isDashboardRedirect = location.contains("Default.aspx", ignoreCase = true) ||
                        location.contains("Reservation", ignoreCase = true) ||
                        location.contains("/Master/", ignoreCase = true)

                if (isRedirect && (isMessagesRedirect || isDashboardRedirect)) {
                    AppLogger.log(
                        LogEntry(
                            timestamp = "",
                            level = LogLevel.INFO,
                            component = "AuthRepository",
                            operation = "LoginSuccess",
                            flowId = flowId,
                            message = "User successfully authenticated (HTTP $statusCode -> Location: $location, hasUnreadMessages: $isMessagesRedirect)"
                        )
                    )

                    Result.success(
                        UserSession(
                            isLoggedIn = true,
                            username = usernamePlain,
                            studentNumber = usernamePlain,
                            nationalId = "",
                            hasUnreadMessages = isMessagesRedirect
                        )
                    )
                } else {
                    // If not redirected, login failed on server. Extract user-facing Persian error message from HTML response.
                    val doc = Jsoup.parse(bodyString)
                    val alertError = doc.select(".alertBox, #cphMain_alertBoxValue, #lblUserNameError, #lblCaptchaError").text().trim()
                    val errorMsg = when {
                        alertError.isNotBlank() -> alertError
                        bodyString.contains("کد امنیتی") -> "کد امنیتی وارد شده اشتباه یا منقضی شده است"
                        bodyString.contains("کلمه عبور اشتباه") || bodyString.contains("نام کاربری") -> "نام کاربری یا گذرواژه اشتباه است"
                        else -> "ورود ناموفق بود. لطفاً نام کاربری، گذرواژه و کد امنیتی را بررسی نمایید"
                    }

                    AppLogger.log(
                        LogEntry(
                            timestamp = "",
                            level = LogLevel.WARN,
                            component = "AuthRepository",
                            operation = "LoginFailed",
                            flowId = flowId,
                            errorCategory = "AUTH_REJECTED",
                            message = "Authentication rejected (HTTP $statusCode, Location: $location): $errorMsg"
                        )
                    )

                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.logError("AuthRepository", "Login attempt exception: ${e.message}", e, flowId = flowId)
                Result.failure(e)
            }
        }
    }

    suspend fun logoutFromServer(currentMenu: WeeklyMenu?): Result<Boolean> = withContext(Dispatchers.IO) {
        val flowId = AppLogger.generateFlowId("logout")
        AppLogger.log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = "AuthRepository",
                operation = "ServerLogout",
                flowId = flowId,
                message = "Initiating real server-side logout request"
            )
        )

        try {
            var viewState = currentMenu?.viewState ?: ""
            var viewStateGen = currentMenu?.viewStateGenerator?.ifEmpty { "3ED98A75" } ?: "3ED98A75"
            var eventVal = currentMenu?.eventValidation ?: ""

            // If viewState is missing, fetch current page HTML to extract latest ViewState
            if (viewState.isBlank()) {
                AppLogger.logWarn(
                    component = "AuthRepository",
                    operation = "LogoutFetchFallbackState",
                    message = "Current menu missing ViewState during logout; fetching Reservation.aspx for fresh state",
                    flowId = flowId,
                    errorCategory = "LOGOUT_STATE_FALLBACK"
                )
                val getReq = Request.Builder()
                    .url(CullinanHttpClient.RESERVATION_URL)
                    .get()
                    .header("Referer", CullinanHttpClient.BASE_URL)
                    .tag(LogContext::class.java, LogContext(flowId, "LogoutFetchState"))
                    .build()
                CullinanHttpClient.executeCall(getReq).use { getResp ->
                    if (getResp.isSuccessful) {
                        val html = getResp.body?.string() ?: ""
                        val doc = Jsoup.parse(html)
                        viewState = doc.select("input[name=__VIEWSTATE]").`val`()
                        val gen = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`()
                        if (gen.isNotBlank()) viewStateGen = gen
                        eventVal = doc.select("input[name=__EVENTVALIDATION]").`val`()
                    } else {
                        AppLogger.logWarn(
                            component = "AuthRepository",
                            operation = "LogoutFetchStateFailed",
                            message = "Fallback Reservation GET during logout failed with HTTP ${getResp.code}",
                            flowId = flowId,
                            errorCategory = "LOGOUT_STATE_HTTP_${getResp.code}"
                        )
                    }
                }
            }

            val formBuilder = FormBody.Builder()
                .add("__EVENTTARGET", "ctl00\$logout")
                .add("__EVENTARGUMENT", "")
                .add("__LASTFOCUS", "")
                .add("__VIEWSTATE", viewState)
                .add("__VIEWSTATEGENERATOR", viewStateGen)
                .add("__VIEWSTATEENCRYPTED", "")

            if (eventVal.isNotEmpty()) {
                formBuilder.add("__EVENTVALIDATION", eventVal)
            }

            formBuilder.add("ctl00\$cphMain\$hdnSelectedTab", "css3-tabstrip-0-1")
            formBuilder.add("css3-tabstrip-0", "on")

            // Replicate table row fields if present
            currentMenu?.days?.forEachIndexed { dayIdx, day ->
                day.meals[MealType.LUNCH]?.firstOrNull()?.let { meal ->
                    val prefix = "ctl00\$cphMain\$grdReservationLunch\$ctl${String.format("%02d", dayIdx + 2)}"
                    if (meal.status.isReserved) {
                        formBuilder.add("$prefix\$chkReserve", "on")
                    }
                    if (meal.selectedSelfId.isNotEmpty() && meal.selectedSelfId != "-1") {
                        formBuilder.add("$prefix\$drpSelf", meal.selectedSelfId)
                    }
                    if (meal.selectedFoodId.isNotEmpty() && meal.selectedFoodId != "-1") {
                        formBuilder.add("$prefix\$drpFood", meal.selectedFoodId)
                    }
                }
            }

            val request = Request.Builder()
                .url(CullinanHttpClient.RESERVATION_URL)
                .post(formBuilder.build())
                .header("Referer", CullinanHttpClient.RESERVATION_URL)
                .header("Origin", CullinanHttpClient.BASE_URL)
                .tag(LogContext::class.java, LogContext(flowId, "LogoutPost"))
                .build()

            var responseCode = 0
            var location = ""
            var bodyString = ""
            var finalUrl = ""
            CullinanHttpClient.executeCallNoRedirect(request).use { response ->
                responseCode = response.code
                location = response.header("Location") ?: ""
                bodyString = response.body?.string() ?: ""
                finalUrl = response.request.url.toString()
            }

            val isSuccess = responseCode == 302 ||
                    location.contains("Login", ignoreCase = true) ||
                    finalUrl.contains("Login", ignoreCase = true) ||
                    bodyString.contains("txtUsername", ignoreCase = true)

            if (isSuccess) {
                AppLogger.log(
                    LogEntry(
                        timestamp = "",
                        level = LogLevel.INFO,
                        component = "AuthRepository",
                        operation = "ServerLogoutSuccess",
                        flowId = flowId,
                        message = "Server confirmed logout (code=$responseCode, Location=$location)"
                    )
                )
                Result.success(true)
            } else {
                AppLogger.log(
                    LogEntry(
                        timestamp = "",
                        level = LogLevel.WARN,
                        component = "AuthRepository",
                        operation = "ServerLogoutWarning",
                        flowId = flowId,
                        errorCategory = "LOGOUT_NON_302",
                        message = "Server returned non-redirect status on logout: $responseCode"
                    )
                )
                Result.failure(Exception("سرور ریدایرکت خروج صادر نکرد (کد: $responseCode)"))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.logError("AuthRepository", "Server logout exception: ${e.message}", e, flowId = flowId)
            Result.failure(e)
        }
    }

    private suspend fun fetchCaptchaBytesInternal(flowId: String? = null): ByteArray? = withContext(Dispatchers.IO) {
        val effFlowId = flowId ?: AppLogger.generateFlowId("cap")
        try {
            val url = "${CullinanHttpClient.CAPTCHA_URL}?r=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Referer", CullinanHttpClient.LOGIN_URL)
                .tag(LogContext::class.java, LogContext(effFlowId, "FetchCaptcha"))
                .build()

            CullinanHttpClient.executeCall(request).use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        AppLogger.logWarn(
                            component = "AuthRepository",
                            operation = "FetchCaptchaEmpty",
                            message = "Captcha HTTP ${response.code} returned empty byte array",
                            flowId = effFlowId,
                            errorCategory = "CAPTCHA_EMPTY_BODY"
                        )
                    } else {
                        AppLogger.logDebug(
                            component = "AuthRepository",
                            operation = "FetchCaptchaSuccess",
                            message = "Fetched captcha image (${bytes.size} bytes)",
                            flowId = effFlowId
                        )
                    }
                    bytes
                } else {
                    AppLogger.logWarn(
                        component = "AuthRepository",
                        operation = "FetchCaptchaHttpError",
                        message = "Captcha request failed with HTTP ${response.code}",
                        flowId = effFlowId,
                        errorCategory = "CAPTCHA_HTTP_${response.code}"
                    )
                    null
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.logError(
                tag = "AuthRepository",
                message = "Captcha fetch exception: ${e.message}",
                throwable = e,
                flowId = effFlowId,
                operation = "FetchCaptchaException",
                errorCategory = "CAPTCHA_NETWORK_ERROR"
            )
            null
        }
    }
}
