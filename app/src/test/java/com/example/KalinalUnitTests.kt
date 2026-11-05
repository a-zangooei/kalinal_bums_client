package com.example

import com.example.data.crypto.RsaEncryptor
import com.example.data.local.DemoDataSource
import com.example.data.parser.DeltaParser
import com.example.data.parser.ReservationHtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KalinalUnitTests {

    @Test
    fun testRsaEncryptionWithDemoKey() {
        val plain = "4031122001510050"
        val encrypted = RsaEncryptor.encryptWithPem(plain, DemoDataSource.DEMO_PUBLIC_KEY)
        assertNotNull(encrypted)
        assertTrue("Encrypted output must be non-empty Base64", encrypted.isNotEmpty())
    }

    @Test
    fun testDeltaParserSimpleTokens() {
        val deltaSample = "1|#||4|8|hiddenField|__VIEWSTATEGENERATOR|3ED98A75|18|formAction||./Reservation.aspx|"
        val parsed = DeltaParser.parse(deltaSample)
        assertEquals("3ED98A75", parsed.hiddenFields["__VIEWSTATEGENERATOR"])
        assertEquals("./Reservation.aspx", parsed.formAction)
    }

    @Test
    fun testReservationHtmlParserWithDemoData() {
        val sampleHtml = """
            <label id="cphMain_lblWeekDate">1405/08/16  الی  1405/08/22</label>
            <label id="cphMain_lblCreditValue">اعتبار شما: 1,410,000- ریال</label>
            <label id="cphMain_titleBoxValue">کاربر: علیرضا زنگویی - 4031122001510050</label>
            <input type="hidden" name="__VIEWSTATE" id="__VIEWSTATE" value="TEST_VIEW_STATE" />
            <table id="cphMain_grdReservationLunch">
                <tr class="HeaderStyle"><th>روز</th><th>سلف</th><th>غذا</th><th>تعداد</th><th>کالری</th><th>مبلغ</th><th>وضعیت</th></tr>
                <tr class="aspNetDisabled">
                    <td><input id="chk_0" type="checkbox" checked="checked" disabled="disabled" /></td>
                    <td>شنبه 1405/08/16</td>
                    <td><select><option selected="selected" value="1">پردیس</option></select></td>
                    <td><select><option selected="selected" value="89228">چلو کباب کوبیده مرغ 180000 ریال</option></select></td>
                    <td><select><option selected="selected" value="1">1</option></select></td>
                    <td><span id="cal_0">680</span></td>
                    <td><span id="amt_0">180000</span></td>
                    <td><img src="../Images/Icon/lockAcceptGreen24.png" title="رزرو شده، غیرفعال" /></td>
                </tr>
            </table>
        """.trimIndent()

        val weeklyMenu = ReservationHtmlParser.parse(sampleHtml)
        assertEquals("1405/08/16 الی 1405/08/22", weeklyMenu.weekDateRange)
        assertEquals("علیرضا زنگویی", weeklyMenu.studentName)
        assertEquals("4031122001510050", weeklyMenu.studentNumber)
        assertEquals(-1410000L, weeklyMenu.creditValueRials)
        assertEquals(7, weeklyMenu.days.size)

        val saturdayMeals = weeklyMenu.days[0].meals
        val lunchMeals = saturdayMeals[com.example.data.model.MealType.LUNCH]
        assertNotNull(lunchMeals)
        assertEquals(1, lunchMeals?.size)
        assertEquals("چلو کباب کوبیده مرغ", lunchMeals?.get(0)?.foodName)
        assertEquals(180000L, lunchMeals?.get(0)?.priceRials)
        assertEquals(com.example.data.model.ReservationStatus.RESERVED_LOCKED, lunchMeals?.get(0)?.status)
    }
}
