package com.example.data.repository

import com.example.data.local.DemoDataSource
import com.example.data.model.WeekNavigationDirection
import com.example.data.model.WeeklyMenu
import com.example.data.network.CullinanHttpClient
import com.example.data.parser.DeltaParser
import com.example.data.parser.ReservationHtmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

class MenuRepository {

    suspend fun fetchInitialMenu(isDemo: Boolean): Result<WeeklyMenu> = withContext(Dispatchers.IO) {
        if (isDemo) {
            return@withContext Result.success(DemoDataSource.getDemoWeeklyMenu(0))
        }

        try {
            val request = Request.Builder()
                .url(CullinanHttpClient.RESERVATION_URL)
                .get()
                .header("Referer", CullinanHttpClient.BASE_URL)
                .build()

            val response = CullinanHttpClient.client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("خطا در دریافت جدول برنامه غذایی (${response.code})"))
            }

            val html = response.body?.string() ?: ""
            val menu = ReservationHtmlParser.parse(html)
            Result.success(menu)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun navigateWeek(
        direction: WeekNavigationDirection,
        currentMenu: WeeklyMenu,
        isDemo: Boolean,
        currentOffset: Int
    ): Result<Pair<WeeklyMenu, Int>> = withContext(Dispatchers.IO) {
        val newOffset = when (direction) {
            WeekNavigationDirection.PREVIOUS -> currentOffset - 1
            WeekNavigationDirection.NEXT -> currentOffset + 1
            WeekNavigationDirection.CURRENT -> 0
        }

        if (isDemo) {
            val menu = DemoDataSource.getDemoWeeklyMenu(newOffset)
            return@withContext Result.success(Pair(menu, newOffset))
        }

        try {
            val buttonName = when (direction) {
                WeekNavigationDirection.PREVIOUS -> "ctl00\$cphMain\$imgbtnLastWeek"
                WeekNavigationDirection.NEXT -> "ctl00\$cphMain\$imgbtnNextWeek"
                WeekNavigationDirection.CURRENT -> "ctl00\$cphMain\$imgbtnCurrentWeek"
            }

            val formBody = FormBody.Builder()
                .add("ctl00\$ScriptManager1", "ctl00\$UpdatePanel1|$buttonName")
                .add("ctl00\$cphMain\$hdnSelectedTab", "css3-tabstrip-0-1")
                .add("__EVENTTARGET", "")
                .add("__EVENTARGUMENT", "")
                .add("__VIEWSTATE", currentMenu.viewState)
                .add("__VIEWSTATEGENERATOR", currentMenu.viewStateGenerator.ifEmpty { "3ED98A75" })
                .add("__VIEWSTATEENCRYPTED", "")
                .add("__ASYNCPOST", "true")
                .add("$buttonName.x", "22")
                .add("$buttonName.y", "30")
                .build()

            val request = Request.Builder()
                .url(CullinanHttpClient.RESERVATION_URL)
                .post(formBody)
                .header("X-MicrosoftAjax", "Delta=true")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", CullinanHttpClient.RESERVATION_URL)
                .header("Origin", CullinanHttpClient.BASE_URL)
                .build()

            val response = CullinanHttpClient.client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("خطا در جابه‌جایی هفته (${response.code})"))
            }

            val deltaText = response.body?.string() ?: ""
            val deltaResponse = DeltaParser.parse(deltaText)

            val updatePanelHtml = deltaResponse.updatePanels["UpdatePanel1"]
                ?: return@withContext Result.failure(Exception("پاسخ سرور شامل محتوای جدول هفته نبود"))

            val newViewState = deltaResponse.hiddenFields["__VIEWSTATE"] ?: currentMenu.viewState
            val newGenerator = deltaResponse.hiddenFields["__VIEWSTATEGENERATOR"] ?: currentMenu.viewStateGenerator

            val updatedMenu = ReservationHtmlParser.parse(
                html = updatePanelHtml,
                existingViewState = newViewState,
                existingGenerator = newGenerator
            )

            Result.success(Pair(updatedMenu, newOffset))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
