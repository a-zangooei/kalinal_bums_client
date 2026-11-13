package com.example.data.repository

import com.example.data.model.LoginPageState
import com.example.data.model.MealType
import com.example.data.model.WeeklyMenu
import okhttp3.FormBody

/**
 * Constructs ASP.NET WebForms POST payloads for login and server-side logout requests.
 */
internal object AuthFormBuilder {

    fun buildLoginFormBody(
        encryptedUsername: String,
        encryptedPassword: String,
        captchaInput: String,
        pageState: LoginPageState
    ): FormBody = FormBody.Builder()
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

    fun buildLogoutFormBody(
        viewState: String,
        viewStateGenerator: String,
        eventValidation: String,
        currentMenu: WeeklyMenu?
    ): FormBody {
        val formBuilder = FormBody.Builder()
            .add("__EVENTTARGET", "ctl00\$logout")
            .add("__EVENTARGUMENT", "")
            .add("__LASTFOCUS", "")
            .add("__VIEWSTATE", viewState)
            .add("__VIEWSTATEGENERATOR", viewStateGenerator)
            .add("__VIEWSTATEENCRYPTED", "")

        if (eventValidation.isNotEmpty()) {
            formBuilder.add("__EVENTVALIDATION", eventValidation)
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

        return formBuilder.build()
    }
}
