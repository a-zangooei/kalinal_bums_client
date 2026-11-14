package com.example.data.parser

import com.example.data.logger.AppLogger
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.ParserDiagnostics
import com.example.data.model.ProfileFetchOutcome
import com.example.data.model.UserProfile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.NumberFormat
import java.util.Locale

object ProfileParser {

    fun parse(
        html: String,
        httpStatusCode: Int = 200,
        finalUrl: String? = null,
        flowId: String? = null
    ): ProfileFetchOutcome {
        val classification = PageClassifier.classify(
            html = html,
            httpStatusCode = httpStatusCode,
            finalUrl = finalUrl,
            flowId = flowId
        )

        when (classification.pageType) {
            PageClassifier.TYPE_LOGIN -> {
                return ProfileFetchOutcome.SessionExpired("نشست کاری در سامانه منقضی شده است. لطفاً مجدداً وارد شوید.")
            }
            PageClassifier.TYPE_MESSAGES -> {
                return ProfileFetchOutcome.UnreadMessagesRequired(
                    "برای مشاهده پروفایل کاربری، ابتدا باید پیام‌های خوانده‌نشده سامانه مطالعه شوند."
                )
            }
            PageClassifier.TYPE_NOTICE -> {
                return ProfileFetchOutcome.UnreadMessagesRequired(
                    "پیش از دسترسی به پروفایل، لازم است اطلاعیه الزامی سامانه مشاهده و تایید شود."
                )
            }
            PageClassifier.TYPE_ERROR -> {
                return ProfileFetchOutcome.Failure("خطای داخلی سرور هنگام دریافت صفحه پروفایل رخ داد.")
            }
        }

        val doc: Document = Jsoup.parse(html)
        val hasContents = doc.selectFirst("#cphMain_pnlContents") != null
        val hasNationalCode = doc.selectFirst("#cphMain_lblNationalCode") != null
        val hasName = doc.selectFirst("#cphMain_lblName") != null

        // Fail-closed structural verification: never return empty fake fields if structure doesn't match
        if (!hasContents || (!hasNationalCode && !hasName)) {
            AppLogger.logWarn(
                component = "ProfileParser",
                operation = "ParseProfileUnknownStructure",
                message = "Profile HTML missing required anchors (hasContents=$hasContents, hasNationalCode=$hasNationalCode, hasName=$hasName, len=${html.length})",
                flowId = flowId,
                errorCategory = "PROFILE_UNKNOWN_STRUCTURE"
            )
            return ProfileFetchOutcome.Failure("ساختار صفحه «پروفایل من» شناسایی نشد یا تغییر یافته است.")
        }

        val alertBoxEl = doc.selectFirst("#cphMain_alertBox")
        val alertStyle = alertBoxEl?.attr("style")?.replace(" ", "")?.lowercase(Locale.US) ?: ""
        val isAlertHidden = alertStyle.contains("display:none")
        val alertValueText = cleanFieldValue(doc.selectFirst("#cphMain_alertBoxValue")?.text())
        val visibleAlert = if (!isAlertHidden) alertValueText else null

        val rawSelfCredit = normalizePersianArabicText(doc.selectFirst("#lblCredit")?.text())
        val rawCafeteriaCredit = normalizePersianArabicText(doc.selectFirst("#lblCafeteriaCredit")?.text())

        val selfCreditRials = parseCreditRialsOrNull(rawSelfCredit)
        val cafeteriaCreditRials = parseCreditRialsOrNull(rawCafeteriaCredit)

        val sidebarName = cleanFieldValue(doc.selectFirst("#divUsername")?.text())
        val sidebarId = cleanFieldValue(doc.selectFirst("#lblIdNumber")?.text())

        val profile = UserProfile(
            fullName = cleanFieldValue(doc.selectFirst("#cphMain_lblName")?.text()) ?: sidebarName,
            userType = cleanFieldValue(doc.selectFirst("#cphMain_lblUserType")?.text()),
            idNumber = cleanFieldValue(doc.selectFirst("#cphMain_lblIdNumber")?.text()) ?: sidebarId,
            nationalCode = cleanFieldValue(doc.selectFirst("#cphMain_lblNationalCode")?.text()),
            group = cleanFieldValue(doc.selectFirst("#cphMain_lblGroup")?.text()),
            subGroup = cleanFieldValue(doc.selectFirst("#cphMain_lblSubGroup")?.text()),
            gender = cleanFieldValue(doc.selectFirst("#cphMain_lblGender")?.text()),
            description = cleanFieldValue(doc.selectFirst("#cphMain_lblDescription")?.text()),
            college = cleanFieldValue(doc.selectFirst("#cphMain_lblCollege")?.text()),
            field = cleanFieldValue(doc.selectFirst("#cphMain_lblField")?.text()),
            dorm = cleanFieldValue(doc.selectFirst("#cphMain_lblDorm")?.text()),
            blockNumber = cleanFieldValue(doc.selectFirst("#cphMain_lblBlockNumber")?.text()),
            roomNumber = cleanFieldValue(doc.selectFirst("#cphMain_lblRoomNumber")?.text()),
            mobile = cleanFieldValue(doc.selectFirst("#cphMain_lblMobile")?.text()),
            grade = cleanFieldValue(doc.selectFirst("#cphMain_lblGrade")?.text()),
            email = cleanFieldValue(doc.selectFirst("#cphMain_lblEmail")?.text()),
            bankAccount = cleanFieldValue(doc.selectFirst("#cphMain_lblBankAccount")?.text()),
            lastLogin = cleanFieldValue(doc.selectFirst("#cphMain_lblLastLogin")?.text()),
            cardNumber = cleanFieldValue(doc.selectFirst("#cphMain_lblCardNumber")?.text()),
            barcodeNumber = cleanFieldValue(doc.selectFirst("#cphMain_lblBarcodeNumber")?.text()),
            cardDeliveryDate = cleanFieldValue(doc.selectFirst("#cphMain_lblCardDeliveryDate")?.text()),
            cardExpirationDate = cleanFieldValue(doc.selectFirst("#cphMain_lblCardExpirationDate")?.text()),
            cardStatus = cleanFieldValue(doc.selectFirst("#cphMain_lblStatus")?.text()),
            selfCreditRials = selfCreditRials,
            selfCreditText = formatCreditDisplay(selfCreditRials, rawSelfCredit),
            cafeteriaCreditRials = cafeteriaCreditRials,
            cafeteriaCreditText = formatCreditDisplay(cafeteriaCreditRials, rawCafeteriaCredit),
            todayDate = cleanFieldValue(doc.selectFirst("#lblTodayDate")?.text()),
            portalVersion = cleanFieldValue(doc.selectFirst("#lblVersion")?.text()),
            portalLastUpdate = cleanFieldValue(doc.selectFirst("#Label1")?.text()),
            alertMessage = visibleAlert,
            isFullProfileLoaded = true
        )

        logNonPiiSummary(profile, html.length, flowId)
        return ProfileFetchOutcome.Success(profile)
    }

    internal fun cleanFieldValue(raw: String?): String? {
        if (raw == null) return null
        val normalized = normalizePersianArabicText(raw)
        if (normalized.isEmpty() || normalized == "...") return null
        return normalized
    }

    internal fun normalizePersianArabicText(raw: String?): String {
        if (raw == null) return ""
        return raw
            .replace('\u064A', '\u06CC') // Arabic Yeh 'ي' -> Persian Yeh 'ی'
            .replace('\u0643', '\u06A9') // Arabic Kaf 'ك' -> Persian Kaf 'ک'
            .trim()
    }

    internal fun normalizeDigitsToLatin(raw: String): String {
        return buildString(raw.length) {
            for (ch in raw) {
                when (ch) {
                    in '۰'..'۹' -> append(('0'.code + (ch.code - '۰'.code)).toChar())
                    in '٠'..'٩' -> append(('0'.code + (ch.code - '٠'.code)).toChar())
                    else -> append(ch)
                }
            }
        }
    }

    internal fun parseCreditRialsOrNull(rawText: String?): Long? {
        if (rawText.isNullOrBlank()) return null
        val normalized = normalizeDigitsToLatin(normalizePersianArabicText(rawText))
        val digitsOnly = normalized.filter { it in '0'..'9' }
        val absValue = digitsOnly.toLongOrNull() ?: return null
        // Supports both leading ("-123,000") and server trailing ("123,000- ريال ") negative formatting
        val isNegative = normalized.contains('-')
        return if (isNegative && absValue > 0L) -absValue else absValue
    }

    internal fun formatCreditDisplay(amount: Long?, rawFallback: String?): String? {
        if (amount == null) {
            return cleanFieldValue(rawFallback)
        }
        val formattedAbs = NumberFormat.getNumberInstance(Locale.US).format(kotlin.math.abs(amount))
        return if (amount < 0L) {
            "$formattedAbs- ریال (بدهی)"
        } else {
            "$formattedAbs ریال"
        }
    }

    private fun logNonPiiSummary(profile: UserProfile, htmlLength: Int, flowId: String?) {
        try {
            val presentFieldCount = listOf(
                profile.fullName,
                profile.userType,
                profile.idNumber,
                profile.nationalCode,
                profile.group,
                profile.subGroup,
                profile.gender,
                profile.description,
                profile.college,
                profile.field,
                profile.dorm,
                profile.blockNumber,
                profile.roomNumber,
                profile.mobile,
                profile.grade,
                profile.email,
                profile.bankAccount,
                profile.lastLogin,
                profile.cardNumber,
                profile.barcodeNumber,
                profile.cardDeliveryDate,
                profile.cardExpirationDate,
                profile.cardStatus
            ).count { it != null }

            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = LogLevel.DEBUG,
                    component = "ProfileParser",
                    operation = "ParseMyProfile",
                    flowId = flowId,
                    parser = ParserDiagnostics(
                        pageType = PageClassifier.TYPE_PROFILE,
                        htmlLength = htmlLength
                    ),
                    message = "Parsed profile structure (fields=$presentFieldCount/23, hasSelfCredit=${profile.selfCreditRials != null}, hasCafeteriaCredit=${profile.cafeteriaCreditRials != null}, version='${profile.portalVersion ?: "N/A"}')"
                )
            )
        } catch (_: Exception) {}
    }
}
