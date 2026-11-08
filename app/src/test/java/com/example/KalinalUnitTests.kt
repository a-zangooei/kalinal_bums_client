package com.example

import com.example.data.crypto.RsaEncryptor
import com.example.data.parser.DeltaParser
import com.example.data.parser.ReservationHtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KalinalUnitTests {

    companion object {
        private const val TEST_PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3pAPzCnTcX4A3p9w+10PNY5ekRthWl/3mXANELfvo6mAzNlHhHLCKfjcXMuYT0XeaPyirNZjSdjWKyasou0Bb3JqVYsJBxCMzAOLv2fCMuOvYRwGWMqfmfrD25oUzFL2BQO8YTSCz766jm8h7RNeLQsmxObAIevSeSNNSF7A3q2dX6l0hj+TgSV5oewEVKTSu1MRKggXkTq02m33vdeqNhXwzQX1bmj783kUsSq6HP3ko6CzMyMAmszJNuQZ/o0/OxpIUMWRsp4Y2fcRrWtGzVbzjFw9R7RqLYUk4YX0Hl5BZXPj/lOjpVK9Mljc5yZc0nYhV78tk54W9i0fUERhWQIDAQAB
-----END PUBLIC KEY-----"""
    }

    @Test
    fun testRsaEncryptionWithKey() {
        val plain = "4031000000000000"
        val encrypted = RsaEncryptor.encryptWithPem(plain, TEST_PUBLIC_KEY)
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
            <label id="cphMain_titleBoxValue">کاربر: دانشجوی نمونه - 4031000000000000</label>
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
        assertEquals("دانشجوی نمونه", weeklyMenu.studentName)
        assertEquals("4031000000000000", weeklyMenu.studentNumber)
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
