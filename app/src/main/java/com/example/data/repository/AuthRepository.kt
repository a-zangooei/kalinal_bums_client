package com.example.data.repository

import com.example.data.crypto.RsaEncryptor
import com.example.data.model.LoginPageState
import com.example.data.model.UserSession
import com.example.data.network.CullinanHttpClient
import com.example.data.model.MealType
import com.example.data.model.WeeklyMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

class AuthRepository {

    suspend fun fetchLoginPage(): Result<LoginPageState> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(CullinanHttpClient.LOGIN_URL)
                .get()
                .build()

            val response = CullinanHttpClient.executeCall(request)
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("خطا در برقراری ارتباط با سرور دانشگاه (${response.code})"))
            }

            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html)

            val viewState = doc.select("input[name=__VIEWSTATE]").`val`()
            val viewStateGenerator = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`().ifEmpty { "C2EE9ABB" }
            val publicKeyPem = doc.select("#publicKeytxt, textarea[name=publicKeytxt]").text().trim()

            // Fetch live captcha with current session
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun refreshCaptcha(): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val bytes = fetchCaptchaBytesInternal()
            if (bytes != null) {
                Result.success(bytes)
            } else {
                Result.failure(Exception("دریافت تصویر کد امنیتی از سرور ناموفق بود"))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun login(
        usernamePlain: String,
        passwordPlain: String,
        captchaInput: String,
        pageState: LoginPageState
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        try {
            if (pageState.publicKeyPem.isBlank()) {
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
                .build()

            val response = CullinanHttpClient.executeCall(request)
            val finalUrl = response.request.url.toString()
            val bodyString = response.body?.string() ?: ""

            // Detection of successful login:
            // 1. Redirected to /Master/Default.aspx or /Reservation/Reservation.aspx
            // 2. Or absence of txtUsernamePlain and presence of student panel
            val isSuccess = finalUrl.contains("/Master/Default.aspx", ignoreCase = true) ||
                    finalUrl.contains("/Reservation/", ignoreCase = true) ||
                    (!bodyString.contains("txtUsernamePlain") && (bodyString.contains("خروج") || bodyString.contains("اطلاعات کاربری") || bodyString.contains("رزرو غذا")))

            if (isSuccess) {
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
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun logoutFromServer(currentMenu: WeeklyMenu?): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val formBuilder = FormBody.Builder()
                .add("__EVENTTARGET", "ctl00\$logout")
                .add("__EVENTARGUMENT", "")
                .add("__LASTFOCUS", "")
                .add("__VIEWSTATE", currentMenu?.viewState ?: "")
                .add("__VIEWSTATEGENERATOR", currentMenu?.viewStateGenerator?.ifEmpty { "3ED98A75" } ?: "3ED98A75")
                .add("__VIEWSTATEENCRYPTED", "")

            if (currentMenu?.eventValidation?.isNotEmpty() == true) {
                formBuilder.add("__EVENTVALIDATION", currentMenu.eventValidation)
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
                .header("Origin", CullinanHttpClient.BASE_URL)
                .header("Referer", CullinanHttpClient.RESERVATION_URL)
                .build()

            val response = CullinanHttpClient.executeCallNoRedirect(request)
            val location = response.header("Location") ?: ""
            val isSuccess = (response.code in 300..399 && location.contains("Login.aspx", ignoreCase = true)) ||
                    location.contains("Login.aspx", ignoreCase = true)

            if (isSuccess) {
                Result.success(true)
            } else {
                Result.failure(Exception("سرور ریدایرکت خروج (302) صادر نکرد (کد: ${response.code})"))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
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

        val response = CullinanHttpClient.executeCall(request)
        return if (response.isSuccessful) response.body?.bytes() else null
    }
}
