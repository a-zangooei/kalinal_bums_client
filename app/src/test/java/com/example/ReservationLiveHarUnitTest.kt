package com.example

import com.example.data.model.MealType
import com.example.data.model.ReservationStatus
import com.example.data.parser.DeltaParser
import com.example.data.parser.ReservationHtmlParser
import com.example.data.repository.MenuRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReservationLiveHarUnitTest {

    private fun loadEntryText(index: Int): String {
        val candidates = listOf(
            File("docs/05-samples-and-fixtures/har_entry${index}_resp.txt"),
            File("../docs/05-samples-and-fixtures/har_entry${index}_resp.txt"),
            File("app/src/test/resources/har_entry${index}_resp.txt"),
            File("src/test/resources/har_entry${index}_resp.txt")
        )
        val file = candidates.find { it.exists() }
        if (file != null) return file.readText()

        val stream = javaClass.classLoader?.getResourceAsStream("har_entry${index}_resp.txt")
        if (stream != null) return stream.bufferedReader().readText()

        throw IllegalStateException("har_entry${index}_resp.txt not found")
    }

    @Test
    fun testParseHarEntry0_NextWeekNavigation() {
        val respText = loadEntryText(0)

        val delta = DeltaParser.parse(respText)
        assertTrue("Delta must contain UpdatePanel1", delta.updatePanels.containsKey("UpdatePanel1"))
        assertTrue("Delta must contain __VIEWSTATE", delta.hiddenFields.containsKey("__VIEWSTATE"))

        val updateHtml = delta.updatePanels["UpdatePanel1"]!!
        val menu = ReservationHtmlParser.parse(updateHtml, delta.hiddenFields["__VIEWSTATE"]!!)

        assertEquals("590,000 ریال", menu.creditText)
        assertEquals(590000L, menu.creditValueRials)
        assertEquals(7, menu.days.size)

        // Wednesday is index 4 (ctl06)
        val wednesdayLunch = menu.days[4].meals[MealType.LUNCH]?.firstOrNull()
        assertNotNull("Wednesday lunch must exist", wednesdayLunch)
        assertEquals(ReservationStatus.NOT_RESERVED, wednesdayLunch?.status)
        assertTrue("Wednesday lunch must be editable", wednesdayLunch?.isEditable == true)
        assertEquals("ctl06", wednesdayLunch?.tableRowIndex)
        assertTrue("Wednesday lunch must have available food options", wednesdayLunch?.availableFoodOptions?.isNotEmpty() == true)

        // Check options parsed
        val foods = wednesdayLunch?.availableFoodOptions ?: emptyList()
        assertTrue("Foods must include 89247 (چلو جوجه کباب)", foods.any { it.id == "89247" && it.priceRials == 180000L })
        assertTrue("Foods must include 89268 (نیم پرس)", foods.any { it.id == "89268" && it.isHalfPortion && it.priceRials == 90000L })
    }

    @Test
    fun testParseHarEntry1_ReserveWednesdayLunch() {
        val respText = loadEntryText(1)

        val delta = DeltaParser.parse(respText)
        assertEquals("success", delta.toastType)
        assertTrue("Toast message must confirm reservation", delta.toastMessage?.contains("رزرو غذا") == true)

        val updateHtml = delta.updatePanels["UpdatePanel1"]!!
        val menu = ReservationHtmlParser.parse(updateHtml, delta.hiddenFields["__VIEWSTATE"] ?: "")

        // Credit deducted from 590,000 to 410,000
        assertEquals(410000L, menu.creditValueRials)
        assertEquals("410,000 ریال", menu.creditText)

        // Wednesday lunch must now be RESERVED
        val wednesdayLunch = menu.days[4].meals[MealType.LUNCH]?.firstOrNull()
        assertNotNull("Wednesday lunch must exist", wednesdayLunch)
        assertEquals(ReservationStatus.RESERVED, wednesdayLunch?.status)
        assertTrue("Wednesday lunch must still be editable (can cancel or change)", wednesdayLunch?.isEditable == true)
    }

    @Test
    fun testParseHarEntry2_CancelWednesdayLunch() {
        val respText = loadEntryText(2)

        val delta = DeltaParser.parse(respText)
        assertEquals("success", delta.toastType)
        assertTrue("Toast message must confirm cancellation", delta.toastMessage?.contains("لغو رزرو") == true)

        val updateHtml = delta.updatePanels["UpdatePanel1"]!!
        val menu = ReservationHtmlParser.parse(updateHtml, delta.hiddenFields["__VIEWSTATE"] ?: "")

        // Credit restored back to 590,000
        assertEquals(590000L, menu.creditValueRials)

        // Wednesday lunch must now be NOT_RESERVED
        val wednesdayLunch = menu.days[4].meals[MealType.LUNCH]?.firstOrNull()
        assertNotNull("Wednesday lunch must exist", wednesdayLunch)
        assertEquals(ReservationStatus.NOT_RESERVED, wednesdayLunch?.status)
    }

    @Test
    fun testBuildSaveLunchBody_MatchesHarReservationStructure() {
        val respText = loadEntryText(0)
        val delta0 = DeltaParser.parse(respText)
        val currentMenu = ReservationHtmlParser.parse(delta0.updatePanels["UpdatePanel1"]!!, delta0.hiddenFields["__VIEWSTATE"]!!)

        val repository = MenuRepository()

        // 1. Test Reservation Request Body
        val reserveBody = repository.buildSaveLunchBody(
            currentMenu = currentMenu,
            targetDayIndex = 4, // Wednesday
            targetFoodId = "89247",
            targetSelfId = "1",
            isCancelling = false
        )

        val reserveParams = (0 until reserveBody.size).associate {
            reserveBody.name(it) to reserveBody.value(it)
        }

        assertEquals("ctl00\$UpdatePanel1|ctl00\$cphMain\$btnSaveLunch", reserveParams["ctl00\$ScriptManager"])
        assertEquals("css3-tabstrip-0-1", reserveParams["ctl00\$cphMain\$hdnSelectedTab"])
        assertEquals("on", reserveParams["ctl00\$cphMain\$grdReservationLunch\$ctl06\$chkReserve"])
        assertEquals("89247", reserveParams["ctl00\$cphMain\$grdReservationLunch\$ctl06\$drpFood"])
        assertEquals("1", reserveParams["ctl00\$cphMain\$grdReservationLunch\$ctl06\$drpSelf"])
        assertEquals("1", reserveParams["ctl00\$cphMain\$grdReservationLunch\$ctl06\$drpFoodCount"])
        assertEquals("ذخیره ناهار", reserveParams["ctl00\$cphMain\$btnSaveLunch"])

        // 2. Test Cancellation Request Body (Must NOT have chkReserve on ctl06)
        val cancelBody = repository.buildSaveLunchBody(
            currentMenu = currentMenu,
            targetDayIndex = 4,
            targetFoodId = "89247",
            targetSelfId = "1",
            isCancelling = true
        )

        val cancelParams = (0 until cancelBody.size).associate {
            cancelBody.name(it) to cancelBody.value(it)
        }

        assertFalse(
            "Cancellation request must NOT include chkReserve for target day",
            cancelParams.containsKey("ctl00\$cphMain\$grdReservationLunch\$ctl06\$chkReserve")
        )
        assertEquals("89247", cancelParams["ctl00\$cphMain\$grdReservationLunch\$ctl06\$drpFood"])
        assertEquals("1", cancelParams["ctl00\$cphMain\$grdReservationLunch\$ctl06\$drpSelf"])
        assertEquals("1", cancelParams["ctl00\$cphMain\$grdReservationLunch\$ctl06\$drpFoodCount"])
        assertEquals("ذخیره ناهار", cancelParams["ctl00\$cphMain\$btnSaveLunch"])
    }

    @Test
    fun testChangeFood_PreservesAllOtherDays() {
        // Load entry 1 where Wednesday is reserved, and Sunday, Monday, Tuesday were also previously reserved
        val respText = loadEntryText(1)
        val delta1 = DeltaParser.parse(respText)
        val currentMenu = ReservationHtmlParser.parse(delta1.updatePanels["UpdatePanel1"]!!, delta1.hiddenFields["__VIEWSTATE"]!!)

        // Ensure table rows snapshot was parsed
        assertTrue("Table rows snapshot must not be empty", currentMenu.tableRows.isNotEmpty())

        val repository = MenuRepository()

        // User changes Wednesday food from 89247 to 89268
        val changeFoodBody = repository.buildSaveLunchBody(
            currentMenu = currentMenu,
            targetDayIndex = 4, // Wednesday
            targetFoodId = "89268",
            targetSelfId = "1",
            isCancelling = false
        )

        val params = (0 until changeFoodBody.size).associate {
            changeFoodBody.name(it) to changeFoodBody.value(it)
        }

        // 1. Wednesday must be checked and updated to the new food ID
        assertEquals("on", params["ctl00\$cphMain\$grdReservationLunch\$ctl06\$chkReserve"])
        assertEquals("89268", params["ctl00\$cphMain\$grdReservationLunch\$ctl06\$drpFood"])
        assertEquals("1", params["ctl00\$cphMain\$grdReservationLunch\$ctl06\$drpSelf"])
        assertEquals("1", params["ctl00\$cphMain\$grdReservationLunch\$ctl06\$drpFoodCount"])

        // 2. Any other day in lunch that was already checked in the snapshot must retain chkReserve = on
        val lunchSnapshots = currentMenu.tableRows.filter { it.mealType == MealType.LUNCH }
        for (row in lunchSnapshots) {
            if (row.rowControlId != "ctl06") {
                if (row.isChecked) {
                    assertEquals(
                        "Previously checked day ${row.rowControlId} must still be checked",
                        "on",
                        params[row.chkReserveName]
                    )
                    assertEquals(
                        "Previously selected food for ${row.rowControlId} must be preserved",
                        row.drpFoodValue,
                        params[row.drpFoodName]
                    )
                } else {
                    assertFalse(
                        "Unchecked day ${row.rowControlId} must NOT have chkReserve",
                        params.containsKey(row.chkReserveName)
                    )
                }
            }
        }
    }

    @Test
    fun testBuildSaveMealBody_BreakfastAndDinner() {
        val respText = loadEntryText(0)
        val delta0 = DeltaParser.parse(respText)
        val currentMenu = ReservationHtmlParser.parse(delta0.updatePanels["UpdatePanel1"]!!, delta0.hiddenFields["__VIEWSTATE"]!!)

        val repository = MenuRepository()

        // 1. Breakfast reservation payload test
        val breakfastBody = repository.buildSaveMealBody(
            currentMenu = currentMenu,
            mealType = MealType.BREAKFAST,
            targetDayIndex = 1, // Sunday (ctl03)
            targetFoodId = "77112",
            targetSelfId = "1",
            isCancelling = false
        )
        val breakfastParams = (0 until breakfastBody.size).associate {
            breakfastBody.name(it) to breakfastBody.value(it)
        }
        assertEquals("ctl00\$UpdatePanel1|ctl00\$cphMain\$btnSaveBreakfast", breakfastParams["ctl00\$ScriptManager"])
        assertEquals("css3-tabstrip-0-0", breakfastParams["ctl00\$cphMain\$hdnSelectedTab"])
        assertEquals("ذخیره صبحانه", breakfastParams["ctl00\$cphMain\$btnSaveBreakfast"])
        assertEquals("on", breakfastParams["ctl00\$cphMain\$grdReservationBreakfast\$ctl03\$chkReserve"])
        assertEquals("77112", breakfastParams["ctl00\$cphMain\$grdReservationBreakfast\$ctl03\$drpFood"])
        assertEquals("1", breakfastParams["ctl00\$cphMain\$grdReservationBreakfast\$ctl03\$drpSelf"])

        // 2. Dinner reservation payload test
        val dinnerBody = repository.buildSaveMealBody(
            currentMenu = currentMenu,
            mealType = MealType.DINNER,
            targetDayIndex = 2, // Monday (ctl04)
            targetFoodId = "77334",
            targetSelfId = "1",
            isCancelling = false
        )
        val dinnerParams = (0 until dinnerBody.size).associate {
            dinnerBody.name(it) to dinnerBody.value(it)
        }
        assertEquals("ctl00\$UpdatePanel1|ctl00\$cphMain\$btnSaveDinner", dinnerParams["ctl00\$ScriptManager"])
        assertEquals("css3-tabstrip-0-2", dinnerParams["ctl00\$cphMain\$hdnSelectedTab"])
        assertEquals("ذخیره شام", dinnerParams["ctl00\$cphMain\$btnSaveDinner"])
        assertEquals("on", dinnerParams["ctl00\$cphMain\$grdReservationDinner\$ctl04\$chkReserve"])
        assertEquals("77334", dinnerParams["ctl00\$cphMain\$grdReservationDinner\$ctl04\$drpFood"])
        assertEquals("1", dinnerParams["ctl00\$cphMain\$grdReservationDinner\$ctl04\$drpSelf"])
    }
}


