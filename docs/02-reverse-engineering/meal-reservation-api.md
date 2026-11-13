# 🍱 مهندسی معکوس فرم‌ها و درخواست‌های رزرو غذا (Meal Reservation Protocol)

صفحه اصلی رزرواسیون غذا (`/Reservation/Reservation.aspx`) شامل جدول هفتگی و فرم‌های مربوط به وعده‌های صبحانه، ناهار و شام است. این مستند به تشریح دقیق نام فیلدها، ساختار جداول و نحوه ثبت و لغو غذا می‌پردازد.

---

## ۱. ساختار کنترل‌های جدول هفتگی در ASP.NET

در این سامانه هر روز هفته دارای یک ردیف در گرید ویو سرور (`grdReservationLunch`، `grdReservationBreakfast`، `grdReservationDinner`) با نام‌گذاری ایندکس‌دار است:

| روز هفته | شماره ایندکس روز | پیشوند کنترل در وب‌فرم |
|---|:---:|:---:|
| شنبه | `0` | `ctl02` |
| یکشنبه | `1` | `ctl03` |
| دوشنبه | `2` | `ctl04` |
| سه‌شنبه | `3` | `ctl05` |
| چهارشنبه | `4` | `ctl06` |
| پنج‌شنبه | `5` | `ctl07` |
| جمعه | `6` | `ctl08` |

برای هر روز، ۳ کنترل مرتبط وجود دارد:
1. `...$chkReserve`: تیک فعال بودن رزرو (`"on"` در صورت انتخاب یا رزرو بودن).
2. `...$drpSelf`: شناسه سلف مورد نظر دانشجو (مثلاً `"1"` برای سلف مرکزی).
3. `...$drpFood`: شناسه دیتابیسی غذای انتخاب‌شده (مثلاً `"89247"` برای چلو جوجه کباب، یا `"-1"` در صورت لغو).
4. `...$drpFoodCount`: تعداد پرس (پیش‌فرض `"1"` برای رزرو و `"0"` برای لغو).

---

## ۲. دکمه‌ها و تب‌های مربوط به هر وعده

| وعده غذایی | شناسه تب در فرم (`hdnSelectedTab`) | شناسه دکمه ذخیره (`ScriptManager`) | مقدار دکمه (`Button Value`) |
|---|:---:|---|---|
| **صبحانه** | `css3-tabstrip-0-0` | `ctl00$cphMain$btnSaveBreakfast` | `"ذخیره صبحانه"` |
| **ناهار** | `css3-tabstrip-0-1` | `ctl00$cphMain$btnSaveLunch` | `"ذخیره ناهار"` |
| **شام** | `css3-tabstrip-0-2` | `ctl00$cphMain$btnSaveDinner` | `"ذخیره شام"` |

---

## ۳. نحوه ساخت فرم‌بادی رزرو یا لغو غذا (`buildSaveMealBody`)

هنگام ثبت یا لغو یک غذا در یک روز خاص، فرم ارسالی باید وضعیت کنونی سایر روزهای هفته را نیز در فرم حفظ کند (State Preservation)؛ زیرا در وب‌فرم‌های ASP.NET، اگر ردیف‌های دیگر ارسال نشوند، سرور ممکن است وضعیت رزرو روزهای دیگر را پاک کند!

متد `buildSaveMealBody` در کلاس `MenuRepository.kt` این کار را به صورت خودکار مدیریت می‌کند:
1. برای روز مورد نظر:
   - **در حالت رزرو:** مقدار `chkReserve` برابر `"on"`، مقدار `drpFood` شناسه غذای هدف، و `drpFoodCount` برابر `"1"` قرار می‌گیرد.
   - **در حالت لغو:** مقدار `chkReserve` ارسال نمی‌شود، مقدار `drpFood` برابر `"-1"` و `drpFoodCount` برابر `"0"` تنظیم می‌شود.
2. برای سایر روزهای هفته:
   - وضعیت کنونی ذخیره‌شده (چه رزرو شده باشد و چه نشده باشد) دقیقاً بازتاب داده می‌شود تا رزرو سایر روزها دست‌نخورده باقی بماند.
3. هدرهای شبیه‌ساز ایجکس ارسال می‌شوند:
   ```http
   X-MicrosoftAjax: Delta=true
   X-Requested-With: XMLHttpRequest
   ```
4. پاسخ دریافتی توسط `DeltaParser` و `ReservationHtmlParser` تحلیل شده و موجودی ریالی و وضعیت جدید جدول فورا در صفحه به کاربر نمایش داده می‌شود.

---

## ۴. ناوبری بین هفته‌ها (Week Navigation Protocol)

جابه‌جایی بین هفته قبل، هفته جاری و هفته بعد روی صفحه `/Reservation/Reservation.aspx` با ارسال **Delta POST** از طریق کنترل‌های تصویری وب‌فرم انجام می‌شود:

| عملیات | کنترل دکمه تصویر (`ScriptManager`) | پارامترهای مختصات |
|---|---|---|
| **هفته قبل** | `ctl00$cphMain$imgbtnLastWeek` | `...$imgbtnLastWeek.x=8`, `...$imgbtnLastWeek.y=29` |
| **هفته جاری** | `ctl00$cphMain$imgbtnCurrentWeek` | `...$imgbtnCurrentWeek.x=8`, `...$imgbtnCurrentWeek.y=29` |
| **هفته بعد** | `ctl00$cphMain$imgbtnNextWeek` | `...$imgbtnNextWeek.x=8`, `...$imgbtnNextWeek.y=29` |

### الزامات فرم و چرخه وضعیت در `MenuRepository.buildNavigateWeekBody`:
1. **`User-Agent` مرورگر مدرن (`CullinanHttpClient.DEFAULT_USER_AGENT`):** کلاینت شبکه باید هدر استاندارد مرورگر (و نه `okhttp/...`) را در تمام درخواست‌ها ارسال کند؛ در غیر این صورت سرور ASP.NET قابلیت `ScriptManager.SupportsPartialRendering` را برابر `false` در نظر می‌گیرد و تمام POSTهای دلتا را با خطای `InvalidOperationException` رد می‌کند.
2. **`ctl00$ScriptManager`:** با مقدار `<updatePanelName>|<buttonControlName>` (به‌طور پیش‌فرض `ctl00$UpdatePanel1|...`) ارسال می‌شود و تحلیل‌گر HTML نباید فیلدهای مخفی کمکی مانند `ctl00_ScriptManager_HiddenField` یا `_TSM` را به جای شناسه اصلی `ScriptManager` انتخاب کند.
3. **حفظ تمام کنترل‌های فرم و ردیف‌های گرید:** دقیقاً مشابه مرورگر، وضعیت تمام ردیف‌های صبحانه، ناهار و شام فعلی همراه با `ctl00$cphMain$hdnSelectedTab` (تب فعال)، `css3-tabstrip-0=on`، `__LASTFOCUS=`، `__VIEWSTATE`، `__VIEWSTATEGENERATOR` و `__EVENTVALIDATION` در درخواست گنجانده می‌شوند.
4. **زنجیره‌سازی توکن‌های جدید و خطاها:** پس از دریافت پاسخ دلتا، مقادیر جدید `__VIEWSTATE` و `__EVENTVALIDATION` برای عملیات بعدی ذخیره می‌شوند، پیام‌های قابل‌نمایش جعبه هشدار (`#cphMain_alertBoxValue`) استخراج می‌گردند، و توکن‌های خطای دلتا (`error` / `asyncPostBackError`) به جای شکست بی‌صدا به لایه UI منتقل می‌شوند.

