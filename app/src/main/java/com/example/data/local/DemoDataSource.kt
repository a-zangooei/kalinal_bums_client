package com.example.data.local

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.data.model.DayMenu
import com.example.data.model.MealItem
import com.example.data.model.MealType
import com.example.data.model.ReservationStatus
import com.example.data.model.WeeklyMenu
import java.io.ByteArrayOutputStream
import kotlin.random.Random

object DemoDataSource {

    const val DEMO_PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3pAPzCnTcX4A3p9w+10PNY5ekRthWl/3mXANELfvo6mAzNlHhHLCKfjcXMuYT0XeaPyirNZjSdjWKyasou0Bb3JqVYsJBxCMzAOLv2fCMuOvYRwGWMqfmfrD25oUzFL2BQO8YTSCz766jm8h7RNeLQsmxObAIevSeSNNSF7A3q2dX6l0hj+TgSV5oewEVKTSu1MRKggXkTq02m33vdeqNhXwzQX1bmj783kUsSq6HP3ko6CzMyMAmszJNuQZ/o0/OxpIUMWRsp4Y2fcRrWtGzVbzjFw9R7RqLYUk4YX0Hl5BZXPj/lOjpVK9Mljc5yZc0nYhV78tk54W9i0fUERhWQIDAQAB
-----END PUBLIC KEY-----"""

    var currentDemoCaptchaCode = "8421"

    /**
     * Generates a realistic 4-digit captcha bitmap mimicking BotDetect / Kalinal captcha.
     */
    fun generateDemoCaptcha(): ByteArray {
        currentDemoCaptchaCode = String.format("%04d", Random.nextInt(1000, 9999))
        val width = 160
        val height = 60
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.rgb(245, 247, 250))

        // Noise lines
        val linePaint = Paint().apply {
            color = Color.rgb(200, 210, 220)
            strokeWidth = 2f
            isAntiAlias = true
        }
        for (i in 0..6) {
            canvas.drawLine(
                Random.nextFloat() * width, Random.nextFloat() * height,
                Random.nextFloat() * width, Random.nextFloat() * height,
                linePaint
            )
        }

        // Text
        val textPaint = Paint().apply {
            color = Color.rgb(0, 77, 64)
            textSize = 34f
            isFakeBoldText = true
            isAntiAlias = true
            letterSpacing = 0.2f
        }
        canvas.drawText(currentDemoCaptchaCode, 30f, 42f, textPaint)

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }

    fun getDemoWeeklyMenu(weekOffset: Int = 0): WeeklyMenu {
        return when (weekOffset) {
            -1 -> createPreviousWeekMenu()
            1 -> createFutureWeekMenu()
            else -> createCurrentWeekMenu()
        }
    }

    private fun createCurrentWeekMenu(): WeeklyMenu {
        val days = listOf(
            createDay(
                0, "شنبه", "1405/08/16",
                breakfast = MealItem("b_0", MealType.BREAKFAST, 0, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                lunch = MealItem("l_0", MealType.LUNCH, 0, "چلو کباب کوبیده مرغ، گوجه کبابی، نوشیدنی", "پردیس", 180000, "180,000 ریال", 680, ReservationStatus.RESERVED_LOCKED, 1, listOf("چلو کباب کوبیده مرغ 180000 ریال", "چلو خورشت قیمه 180000 ریال")),
                dinner = MealItem("d_0", MealType.DINNER, 0, "ماکارونی با گوشت چرخ کرده، ماست", "پردیس", 150000, "150,000 ریال", 600, ReservationStatus.RESERVED_LOCKED, 1)
            ),
            createDay(
                1, "یکشنبه", "1405/08/17",
                breakfast = MealItem("b_1", MealType.BREAKFAST, 1, "املت با نان بربری و پنیر", "پردیس", 50000, "50,000 ریال", 350, ReservationStatus.RESERVED, 1),
                lunch = MealItem("l_1", MealType.LUNCH, 1, "چلو قلیه مرغ، نوشیدنی", "پردیس", 180000, "180,000 ریال", 620, ReservationStatus.RESERVED, 1, listOf("چلو قلیه مرغ 180000 ریال", "چلو کباب کوبیده 180000 ریال")),
                dinner = MealItem("d_1", MealType.DINNER, 1, "کوکو سیب‌زمینی، سالاد کلم", "پردیس", 120000, "120,000 ریال", 450, ReservationStatus.NOT_RESERVED, 0)
            ),
            createDay(
                2, "دوشنبه", "1405/08/18",
                breakfast = MealItem("b_2", MealType.BREAKFAST, 2, "تخم مرغ آب‌پز، کره، عسل", "پردیس", 50000, "50,000 ریال", 380, ReservationStatus.NOT_RESERVED, 0),
                lunch = MealItem("l_2", MealType.LUNCH, 2, "زرشک پلو با مرغ، نوشیدنی", "پردیس", 180000, "180,000 ریال", 750, ReservationStatus.RESERVED, 1, listOf("زرشک پلو با مرغ 180000 ریال", "خوراک جوجه کباب 180000 ریال")),
                dinner = MealItem("d_2", MealType.DINNER, 2, "عدس پلو با سویا و کشمش", "پردیس", 130000, "130,000 ریال", 520, ReservationStatus.RESERVED, 1)
            ),
            createDay(
                3, "سه‌شنبه", "1405/08/19",
                breakfast = MealItem("b_3", MealType.BREAKFAST, 3, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                lunch = MealItem("l_3", MealType.LUNCH, 3, "چلو جوجه کباب زعفرانی، دوغ", "پردیس", 180000, "180,000 ریال", 710, ReservationStatus.NOT_RESERVED, 0, listOf("چلو جوجه کباب زعفرانی 180000 ریال", "چلو خورشت قرمه سبزی 180000 ریال")),
                dinner = MealItem("d_3", MealType.DINNER, 3, "کتلت گوشت، نان و گوجه", "پردیس", 150000, "150,000 ریال", 580, ReservationStatus.NOT_RESERVED, 0)
            ),
            createDay(
                4, "چهارشنبه", "1405/08/20",
                breakfast = MealItem("b_4", MealType.BREAKFAST, 4, "شیر و کیک صبحانه", "پردیس", 50000, "50,000 ریال", 300, ReservationStatus.RESERVED, 1),
                lunch = MealItem("l_4", MealType.LUNCH, 4, "چلو خورشت قرمه سبزی، ماست", "پردیس", 180000, "180,000 ریال", 660, ReservationStatus.RESERVED, 1, listOf("چلو خورشت قرمه سبزی 180000 ریال", "استامبولی پلو با سالاد شیرازی 180000 ریال")),
                dinner = MealItem("d_4", MealType.DINNER, 4, "خوراک لوبیا چیتی با قارچ", "پردیس", 120000, "120,000 ریال", 420, ReservationStatus.RESERVED, 1)
            ),
            createDay(
                5, "پنجشنبه", "1405/08/21",
                breakfast = MealItem("b_5", MealType.BREAKFAST, 5, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                lunch = MealItem("l_5", MealType.LUNCH, 5, "استامبولی پلو، ماست و خیار", "پردیس", 160000, "160,000 ریال", 590, ReservationStatus.NOT_RESERVED, 0),
                dinner = MealItem("d_5", MealType.DINNER, 5, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0)
            ),
            createDay(
                6, "جمعه", "1405/08/22",
                breakfast = MealItem("b_6", MealType.BREAKFAST, 6, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                lunch = MealItem("l_6", MealType.LUNCH, 6, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                dinner = MealItem("d_6", MealType.DINNER, 6, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0)
            )
        )

        return WeeklyMenu(
            weekDateRange = "1405/08/16 الی 1405/08/22",
            days = days,
            studentName = "علیرضا زنگویی",
            studentNumber = "4031122001510050",
            creditText = "اعتبار شما: 1,410,000- ریال",
            creditValueRials = -1410000L,
            viewState = "DEMO_VIEW_STATE",
            viewStateGenerator = "3ED98A75"
        )
    }

    private fun createPreviousWeekMenu(): WeeklyMenu {
        val days = listOf(
            createDay(
                0, "شنبه", "1405/08/09",
                breakfast = MealItem("b_0", MealType.BREAKFAST, 0, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                lunch = MealItem("l_0", MealType.LUNCH, 0, "چلو کباب کوبیده مرغ", "پردیس", 180000, "180,000 ریال", 680, ReservationStatus.CONSUMED, 1),
                dinner = MealItem("d_0", MealType.DINNER, 0, "سوپ جو با مرغ و نان", "پردیس", 110000, "110,000 ریال", 380, ReservationStatus.CONSUMED, 1)
            ),
            createDay(
                1, "یکشنبه", "1405/08/10",
                breakfast = MealItem("b_1", MealType.BREAKFAST, 1, "پنیر و گردو با نان سنگک", "پردیس", 50000, "50,000 ریال", 320, ReservationStatus.CONSUMED, 1),
                lunch = MealItem("l_1", MealType.LUNCH, 1, "چلو خورشت قیمه بادمجان", "پردیس", 180000, "180,000 ریال", 650, ReservationStatus.CONSUMED, 1),
                dinner = MealItem("d_1", MealType.DINNER, 1, "فلافل با سالاد و سس", "پردیس", 100000, "100,000 ریال", 490, ReservationStatus.CONSUMED, 1)
            ),
            createDay(
                2, "دوشنبه", "1405/08/11",
                breakfast = MealItem("b_2", MealType.BREAKFAST, 2, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                lunch = MealItem("l_2", MealType.LUNCH, 2, "چلو کباب دیگی، سالاد", "پردیس", 180000, "180,000 ریال", 710, ReservationStatus.CONSUMED, 1),
                dinner = MealItem("d_2", MealType.DINNER, 2, "آش رشته با کشک و پیاز داغ", "پردیس", 110000, "110,000 ریال", 450, ReservationStatus.CONSUMED, 1)
            ),
            createDay(
                3, "سه‌شنبه", "1405/08/12",
                breakfast = MealItem("b_3", MealType.BREAKFAST, 3, "حلوای شکری و کره", "پردیس", 50000, "50,000 ریال", 340, ReservationStatus.CONSUMED, 1),
                lunch = MealItem("l_3", MealType.LUNCH, 3, "چلو خورشت کرفس، ماست", "پردیس", 180000, "180,000 ریال", 600, ReservationStatus.CONSUMED, 1),
                dinner = MealItem("d_3", MealType.DINNER, 3, "شنیسل مرغ با سیب‌زمینی", "پردیس", 160000, "160,000 ریال", 630, ReservationStatus.CONSUMED, 1)
            ),
            createDay(
                4, "چهارشنبه", "1405/08/13",
                breakfast = MealItem("b_4", MealType.BREAKFAST, 4, "تخم مرغ نیمرو با نان", "پردیس", 50000, "50,000 ریال", 350, ReservationStatus.CONSUMED, 1),
                lunch = MealItem("l_4", MealType.LUNCH, 4, "ته‌چین مرغ زعفرانی", "پردیس", 180000, "180,000 ریال", 790, ReservationStatus.CONSUMED, 1),
                dinner = MealItem("d_4", MealType.DINNER, 4, "ماکارونی با سویا و قارچ", "پردیس", 130000, "130,000 ریال", 560, ReservationStatus.CONSUMED, 1)
            ),
            createDay(
                5, "پنجشنبه", "1405/08/14",
                breakfast = MealItem("b_5", MealType.BREAKFAST, 5, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                lunch = MealItem("l_5", MealType.LUNCH, 5, "عدس پلو با ماست", "پردیس", 150000, "150,000 ریال", 550, ReservationStatus.CONSUMED, 1),
                dinner = MealItem("d_5", MealType.DINNER, 5, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0)
            ),
            createDay(
                6, "جمعه", "1405/08/15",
                breakfast = MealItem("b_6", MealType.BREAKFAST, 6, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                lunch = MealItem("l_6", MealType.LUNCH, 6, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                dinner = MealItem("d_6", MealType.DINNER, 6, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0)
            )
        )

        return WeeklyMenu(
            weekDateRange = "1405/08/09 الی 1405/08/15",
            days = days,
            studentName = "علیرضا زنگویی",
            studentNumber = "4031122001510050",
            creditText = "اعتبار شما: 1,410,000- ریال",
            creditValueRials = -1410000L,
            viewState = "DEMO_PREV_VIEW_STATE",
            viewStateGenerator = "3ED98A75"
        )
    }

    private fun createFutureWeekMenu(): WeeklyMenu {
        val days = listOf(
            createDay(
                0, "شنبه", "1405/08/23",
                breakfast = MealItem("b_0", MealType.BREAKFAST, 0, "نان و پنیر و کره", "پردیس", 50000, "50,000 ریال", 300, ReservationStatus.NOT_RESERVED, 0),
                lunch = MealItem("l_0", MealType.LUNCH, 0, "چلو کباب کوبیده مرغ، نوشیدنی", "پردیس", 180000, "180,000 ریال", 680, ReservationStatus.NOT_RESERVED, 0),
                dinner = MealItem("d_0", MealType.DINNER, 0, "ماکارونی با گوشت چرخ کرده", "پردیس", 150000, "150,000 ریال", 600, ReservationStatus.NOT_RESERVED, 0)
            ),
            createDay(
                1, "یکشنبه", "1405/08/24",
                breakfast = MealItem("b_1", MealType.BREAKFAST, 1, "تخم مرغ آب‌پز و عسل", "پردیس", 50000, "50,000 ریال", 350, ReservationStatus.NOT_RESERVED, 0),
                lunch = MealItem("l_1", MealType.LUNCH, 1, "چلو قلیه مرغ، نوشیدنی", "پردیس", 180000, "180,000 ریال", 620, ReservationStatus.NOT_RESERVED, 0),
                dinner = MealItem("d_1", MealType.DINNER, 1, "کوکو سبزی با ماست", "پردیس", 120000, "120,000 ریال", 420, ReservationStatus.NOT_RESERVED, 0)
            ),
            createDay(
                2, "دوشنبه", "1405/08/25",
                breakfast = MealItem("b_2", MealType.BREAKFAST, 2, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.NOT_RESERVED, 0),
                lunch = MealItem("l_2", MealType.LUNCH, 2, "زرشک پلو با مرغ", "پردیس", 180000, "180,000 ریال", 750, ReservationStatus.NOT_RESERVED, 0),
                dinner = MealItem("d_2", MealType.DINNER, 2, "خوراک عدسی با قارچ", "پردیس", 110000, "110,000 ریال", 390, ReservationStatus.NOT_RESERVED, 0)
            ),
            createDay(
                3, "سه‌شنبه", "1405/08/26",
                breakfast = MealItem("b_3", MealType.BREAKFAST, 3, "شیر و کیک", "پردیس", 50000, "50,000 ریال", 310, ReservationStatus.NOT_RESERVED, 0),
                lunch = MealItem("l_3", MealType.LUNCH, 3, "چلو جوجه کباب", "پردیس", 180000, "180,000 ریال", 710, ReservationStatus.NOT_RESERVED, 0),
                dinner = MealItem("d_3", MealType.DINNER, 3, "کوکو سیب‌زمینی", "پردیس", 120000, "120,000 ریال", 450, ReservationStatus.NOT_RESERVED, 0)
            ),
            createDay(
                4, "چهارشنبه", "1405/08/27",
                breakfast = MealItem("b_4", MealType.BREAKFAST, 4, "املت گوجه‌فرنگی", "پردیس", 50000, "50,000 ریال", 350, ReservationStatus.NOT_RESERVED, 0),
                lunch = MealItem("l_4", MealType.LUNCH, 4, "چلو خورشت قیمه", "پردیس", 180000, "180,000 ریال", 660, ReservationStatus.NOT_RESERVED, 0),
                dinner = MealItem("d_4", MealType.DINNER, 4, "لوبیا پلو با سالاد", "پردیس", 140000, "140,000 ریال", 530, ReservationStatus.NOT_RESERVED, 0)
            ),
            createDay(
                5, "پنجشنبه", "1405/08/28",
                breakfast = MealItem("b_5", MealType.BREAKFAST, 5, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                lunch = MealItem("l_5", MealType.LUNCH, 5, "استامبولی پلو", "پردیس", 160000, "160,000 ریال", 590, ReservationStatus.NOT_RESERVED, 0),
                dinner = MealItem("d_5", MealType.DINNER, 5, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0)
            ),
            createDay(
                6, "جمعه", "1405/08/29",
                breakfast = MealItem("b_6", MealType.BREAKFAST, 6, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                lunch = MealItem("l_6", MealType.LUNCH, 6, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0),
                dinner = MealItem("d_6", MealType.DINNER, 6, "غذا انتخاب نشده است", "", 0, "رایگان", 0, ReservationStatus.LOCKED, 0)
            )
        )

        return WeeklyMenu(
            weekDateRange = "1405/08/23 الی 1405/08/29",
            days = days,
            studentName = "علیرضا زنگویی",
            studentNumber = "4031122001510050",
            creditText = "اعتبار شما: 1,410,000- ریال",
            creditValueRials = -1410000L,
            viewState = "DEMO_NEXT_VIEW_STATE",
            viewStateGenerator = "3ED98A75"
        )
    }

    private fun createDay(
        dayIndex: Int,
        dayName: String,
        dateJalali: String,
        breakfast: MealItem,
        lunch: MealItem,
        dinner: MealItem
    ): DayMenu {
        return DayMenu(
            dayIndex = dayIndex,
            dayName = dayName,
            dateJalali = dateJalali,
            isToday = dayIndex == 0,
            meals = mapOf(
                MealType.BREAKFAST to listOf(breakfast),
                MealType.LUNCH to listOf(lunch),
                MealType.DINNER to listOf(dinner)
            )
        )
    }
}
