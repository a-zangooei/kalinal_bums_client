package com.example.data.repository

import com.example.data.crypto.RsaEncryptor
import com.example.data.local.DemoDataSource
import com.example.data.model.LoginPageState
import com.example.data.model.UserSession
import com.example.data.network.CullinanHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

class AuthRepository {

    suspend fun fetchLoginPage(isDemo: Boolean): Result<LoginPageState> = withContext(Dispatchers.IO) {
        if (isDemo) {
            val captcha = DemoDataSource.generateDemoCaptcha()
            return@withContext Result.success(
                LoginPageState(
                    viewState = "DEMO_VIEW_STATE",
                    viewStateGenerator = "C2EE9ABB",
                    publicKeyPem = DemoDataSource.DEMO_PUBLIC_KEY,
                    captchaBytes = captcha,
                    isLoading = false
                )
            )
        }

        try {
            val request = Request.Builder()
                .url(CullinanHttpClient.LOGIN_URL)
                .get()
                .build()

            val response = CullinanHttpClient.client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("خطا در بارگذاری صفحه ورود (${response.code})"))
            }

            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html)

            val viewState = doc.select("input[name=__VIEWSTATE]").`val`()
            val viewStateGenerator = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`().ifEmpty { "C2EE9ABB" }
            val publicKeyPem = doc.select("#publicKeytxt, textarea[name=publicKeytxt]").text().trim()

            // Fetch Captcha with current session
            val captchaBytes = fetchCaptchaBytesInternal()

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
            Result.failure(e)
        }
    }

    suspend fun refreshCaptcha(isDemo: Boolean): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (isDemo) {
            return@withContext Result.success(DemoDataSource.generateDemoCaptcha())
        }
        try {
            val bytes = fetchCaptchaBytesInternal()
            if (bytes != null) {
                Result.success(bytes)
            } else {
                Result.failure(Exception("دریافت تصویر کد امنیتی ناموفق بود"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(
        usernamePlain: String,
        passwordPlain: String,
        captchaInput: String,
        pageState: LoginPageState,
        isDemo: Boolean
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        if (isDemo) {
            if (captchaInput.trim() != DemoDataSource.currentDemoCaptchaCode && captchaInput.trim() != "1234") {
                return@withContext Result.failure(Exception("کد امنیتی وارد شده اشتباه است"))
            }
            return@withContext Result.success(
                UserSession(
                    isLoggedIn = true,
                    username = usernamePlain,
                    studentName = "علیرضا زنگویی",
                    studentNumber = usernamePlain.ifEmpty { "4031122001510050" },
                    nationalId = passwordPlain.ifEmpty { "0640954992" },
                    creditFormatted = "1,410,000- ریال",
                    isDemoMode = true
                )
            )
        }

        try {
            if (pageState.publicKeyPem.isBlank()) {
                return@withContext Result.failure(Exception("کلید عمومی رمزنگاری از سرور دریافت نشده است"))
            }

            // RSA encrypt username and password
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
                .build()

            val response = CullinanHttpClient.client.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            val bodyString = response.body?.string() ?: ""

            // Detection of successful login:
            // 1. Redirected to /Master/Default.aspx or /Reservation/Reservation.aspx
            // 2. Or absence of txtUsernamePlain and presencia of student panel
            val isSuccess = finalUrl.contains("/Master/Default.aspx", ignoreCase = true) ||
                    finalUrl.contains("/Reservation/", ignoreCase = true) ||
                    (!bodyString.contains("txtUsernamePlain") && (bodyString.contains("خروج") || bodyString.contains("اطلاعات کاربری") || bodyString.contains("رزرو غذا")))

            if (isSuccess) {
                Result.success(
                    UserSession(
                        isLoggedIn = true,
                        username = usernamePlain,
                        studentNumber = usernamePlain,
                        nationalId = passwordPlain,
                        isDemoMode = false
                    )
                )
            } else {
                // Extract error message from page
                val doc = Jsoup.parse(bodyString)
                val alertError = doc.select(".alertBox, #cphMain_alertBoxValue, #lblUserNameError, #lblCaptchaError").text().trim()
                val errorMsg = when {
                    alertError.isNotBlank() -> alertError
                    bodyString.contains("کد امنیتی") -> "کد امنیتی وارد شده اشتباه یا منقضی شده است"
                    bodyString.contains("کلمه عبور اشتباه") || bodyString.contains("نام کاربری") -> "نام کاربری یا گذرواژه اشتباه است"
                    else -> "ورود ناموفق بود. لطفاً اطلاعات و کد امنیتی را بررسی نمایید"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchCaptchaBytesInternal(): ByteArray? {
        val captchaUrl = "${CullinanHttpClient.CAPTCHA_URL}?t=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(captchaUrl)
            .get()
            .header("Referer", CullinanHttpClient.LOGIN_URL)
            .build()

        val response = CullinanHttpClient.client.newCall(request).execute()
        return if (response.isSuccessful) response.body?.bytes() else null
    }
}
