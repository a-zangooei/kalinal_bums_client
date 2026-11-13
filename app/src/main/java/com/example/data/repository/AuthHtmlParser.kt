package com.example.data.repository

import com.example.data.logger.AppLogger
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.LogSanitizer
import com.example.data.model.LoginPageState
import org.jsoup.Jsoup

/**
 * Parses HTML responses for login state, fallback logout hidden fields, and Persian auth error messages.
 */
internal object AuthHtmlParser {

    data class HiddenFields(
        val viewState: String,
        val viewStateGenerator: String,
        val eventValidation: String
    )

    fun parseLoginPageState(
        html: String,
        captchaBytes: ByteArray?,
        flowId: String
    ): LoginPageState {
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

        return LoginPageState(
            viewState = viewState,
            viewStateGenerator = viewStateGenerator,
            publicKeyPem = publicKeyPem,
            captchaBytes = captchaBytes,
            isLoading = false
        )
    }

    fun extractHiddenFields(html: String, defaultGenerator: String): HiddenFields {
        val doc = Jsoup.parse(html)
        val viewState = doc.select("input[name=__VIEWSTATE]").`val`()
        val gen = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`().ifBlank { defaultGenerator }
        val eventVal = doc.select("input[name=__EVENTVALIDATION]").`val`()
        return HiddenFields(viewState = viewState, viewStateGenerator = gen, eventValidation = eventVal)
    }

    fun extractLoginRejectionMessage(bodyString: String): String {
        val doc = Jsoup.parse(bodyString)
        val alertError = doc.select(".alertBox, #cphMain_alertBoxValue, #lblUserNameError, #lblCaptchaError").text().trim()
        return when {
            alertError.isNotBlank() -> alertError
            bodyString.contains("کد امنیتی") -> "کد امنیتی وارد شده اشتباه یا منقضی شده است"
            bodyString.contains("کلمه عبور اشتباه") || bodyString.contains("نام کاربری") -> "نام کاربری یا گذرواژه اشتباه است"
            else -> "ورود ناموفق بود. لطفاً نام کاربری، گذرواژه و کد امنیتی را بررسی نمایید"
        }
    }
}
