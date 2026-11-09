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
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

class AuthRepository {

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

        try {
            val request = Request.Builder()
                .url(CullinanHttpClient.LOGIN_URL)
                .get()
                .tag(LogContext::class.java, LogContext(flowId, "FetchLoginPage"))
                .build()

            val response = CullinanHttpClient.executeCall(request)
            if (!response.isSuccessful) {
                AppLogger.logError("AuthRepository", "Fetch login page failed with HTTP ${response.code}", flowId = flowId)
                return@withContext Result.failure(Exception("خطا در برقراری ارتباط با سرور دانشگاه (${response.code})"))
            }

            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html)

            val viewState = doc.select("input[name=__VIEWSTATE]").`val`()
            val viewStateGenerator = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`().ifEmpty { "C2EE9ABB" }
            val publicKeyPem = doc.select("#publicKeytxt, textarea[name=publicKeytxt]").text().trim()

            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = LogLevel.INFO,
                    component = "AuthRepository",
                    operation = "FetchLoginPageSuccess",
                    flowId = flowId,
                    message = "Login page fetched (VS len=${viewState.length}, PEM len=${publicKeyPem.length}, PEM fp=${LogSanitizer.computeFingerprint(publicKeyPem)})"
                )
            )

            // Fetch live captcha with current session
            val captchaBytes = fetchCaptchaBytesInternal(flowId)

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
            AppLogger.logError("AuthRepository", "Fetch login page exception: ${e.message}", e, flowId = flowId)
            Result.failure(e)
        }
    }

    suspend fun refreshCaptcha(): Result<ByteArray> = withContext(Dispatchers.IO) {
        val flowId = AppLogger.generateFlowId("captcha")
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

        try {
            if (pageState.publicKeyPem.isBlank()) {
                AppLogger.logError("AuthRepository", "Public key missing from page state", flowId = flowId)
                return@withContext Result.failure(Exception("کلید عمومی رمزنگاری از سرور دریافت نشده است"))
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

            val response = CullinanHttpClient.executeCall(request)
            val finalUrl = response.request.url.toString()
            val bodyString = response.body?.string() ?: ""

            // Detection of successful login:
            val isSuccess = finalUrl.contains("/Master/Default.aspx", ignoreCase = true) ||
                    finalUrl.contains("/Reservation/", ignoreCase = true) ||
                    (!bodyString.contains("txtUsernamePlain") && (bodyString.contains("خروج") || bodyString.contains("اطلاعات کاربری") || bodyString.contains("رزرو غذا")))

            if (isSuccess) {
                AppLogger.log(
                    LogEntry(
                        timestamp = "",
                        level = LogLevel.INFO,
                        component = "AuthRepository",
                        operation = "LoginSuccess",
                        flowId = flowId,
                        message = "User successfully authenticated. FinalUrl: $finalUrl"
                    )
                )

                Result.success(
                    UserSession(
                        isLoggedIn = true,
                        username = usernamePlain,
                        studentNumber = usernamePlain,
                        nationalId = ""
                    )
                )
            } else {
                // Extract error message from server response
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
                        message = "Authentication rejected: $errorMsg"
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
                val getReq = Request.Builder()
                    .url(CullinanHttpClient.RESERVATION_URL)
                    .get()
                    .header("Referer", CullinanHttpClient.BASE_URL)
                    .tag(LogContext::class.java, LogContext(flowId, "LogoutFetchState"))
                    .build()
                val getResp = CullinanHttpClient.executeCall(getReq)
                if (getResp.isSuccessful) {
                    val html = getResp.body?.string() ?: ""
                    val doc = Jsoup.parse(html)
                    viewState = doc.select("input[name=__VIEWSTATE]").`val`()
                    val gen = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`()
                    if (gen.isNotBlank()) viewStateGen = gen
                    eventVal = doc.select("input[name=__EVENTVALIDATION]").`val`()
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

            val response = CullinanHttpClient.executeCallNoRedirect(request)
            val location = response.header("Location") ?: ""
            val bodyString = response.body?.string() ?: ""
            val finalUrl = response.request.url.toString()

            val isSuccess = response.code == 302 ||
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
                        message = "Server confirmed logout (code=${response.code}, Location=$location)"
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
                        message = "Server returned non-redirect status on logout: ${response.code}"
                    )
                )
                Result.failure(Exception("سرور ریدایرکت خروج صادر نکرد (کد: ${response.code})"))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.logError("AuthRepository", "Server logout exception: ${e.message}", e, flowId = flowId)
            Result.failure(e)
        }
    }

    private suspend fun fetchCaptchaBytesInternal(flowId: String? = null): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val url = "${CullinanHttpClient.CAPTCHA_URL}?r=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Referer", CullinanHttpClient.LOGIN_URL)
                .tag(LogContext::class.java, LogContext(flowId ?: AppLogger.generateFlowId("cap"), "FetchCaptcha"))
                .build()

            val response = CullinanHttpClient.executeCall(request)
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                null
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }
}
