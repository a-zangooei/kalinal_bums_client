# برنامه اجرایی پیاده‌سازی رزرو و لغو رزرو غذا در کلاینت کالینال (سامانه سلف دانشگاه)

این سند مراحل دقیق پیاده‌سازی قابلیت **رزرو غذا (Reservation)** و **لغو رزرو (Cancellation)** ناهار را بر اساس تحلیل درخواست‌ها و پاسخ‌های واقعی فایل HAR سامانه `jeton.bums.ac.ir` تشریح می‌کند.

---

## ۱. تحلیل رفتار سامانه بر اساس مستندات و داده‌های HAR

### ۱.۱. وضعیت‌های ردیف جدول و فرمت داده‌ها
- **تعداد روزها و ردیف‌ها**: هفته شامل ۷ روز (شنبه تا جمعه) است. در صفحه ۳ جدول وجود دارد:
  - `grdReservationBreakfast` (کنترل‌های `ctl02` تا `ctl08`)
  - `grdReservationLunch` (کنترل‌های `ctl02` تا `ctl08`)
  - `grdReservationDinner` (کنترل‌های `ctl02` تا `ctl08`)
- **وضعیت ردیف**:
  - ردیف‌های گذشته یا قفل‌شده دارای کلاس `aspNetDisabled` بوده و چک‌باکس آنها صفت `disabled="disabled"` دارد. این ردیف‌ها غیرقابل‌ویرایش هستند.
  - ردیف‌های فعال دارای چک‌باکس با قابلیت تغییر وضعیت هستند.
- **انتخاب غذا و سلف (`drpFood`, `drpSelf`, `drpFoodCount`)**:
  - هر ردیف دارای یک تگ `<select>` برای غذا شامل شناسه‌ها، نام‌ها و قیمت هر غذا است.
  - هر ردیف دارای `<select>` برای سلف غذاخوری است (با مقدار پیش‌فرض انتخاب‌شده).
  - در حالت بدون رزرو: `drpSelf=-1`، `drpFood=-1` و `drpFoodCount=0`.

### ۱.۲. ساختار درخواست‌های POST (ASP.NET WebForms + UpdatePanel)
تمام درخواست‌ها به نشانی `/Reservation/Reservation.aspx` با فرمت `application/x-www-form-urlencoded; charset=UTF-8` ارسال می‌شوند:
1. **زنجیره ViewState**:
   - درخواست جدید باید حتماً از مقدار `__VIEWSTATE` و `__VIEWSTATEGENERATOR` برگشتی از آخرین پاسخ معتبر استفاده کند.
   - ارسال تمامی ۲۱ ردیف (صبحانه، ناهار، شام) در پارامترهای فرم الزامی است (حفظ مقادیر موجود برای سایر ردیف‌ها).
2. **درخواست رزرو ناهار**:
   - برای ردیف هدف:
     - `ctl00$cphMain$grdReservationLunch$ctlXX$chkReserve=on`
     - `ctl00$cphMain$grdReservationLunch$ctlXX$drpSelf=<شناسه_سلف>`
     - `ctl00$cphMain$grdReservationLunch$ctlXX$drpFood=<شناسه_غذا>`
     - `ctl00$cphMain$grdReservationLunch$ctlXX$drpFoodCount=1`
   - دکمه ارسال: `ctl00$cphMain$btnSaveLunch=ذخیره ناهار`
   - مدیر اسکریپت: `ctl00$ScriptManager=ctl00$UpdatePanel1|ctl00$cphMain$btnSaveLunch`
3. **درخواست لغو رزرو ناهار**:
   - برای ردیف هدف، چک‌باکس حذف می‌شود (کلید `chkReserve` ارسال **نمی‌شود**):
     - `ctl00$cphMain$grdReservationLunch$ctlXX$drpSelf=<شناسه_سلف>`
     - `ctl00$cphMain$grdReservationLunch$ctlXX$drpFood=<شناسه_غذا>`
     - `ctl00$cphMain$grdReservationLunch$ctlXX$drpFoodCount=1`
   - دکمه و ScriptManager همان `ctl00$cphMain$btnSaveLunch=ذخیره ناهار` ارسال می‌شود.

### ۱.۳. ساختار پاسخ‌های سرور (ASP.NET AJAX Delta)
پاسخ در قالب قطعات تفکیک‌شده با اندازه برمی‌گردد: `length|type|id|content|`
- **قطعه `updatePanel|UpdatePanel1`**: حاوی قطعه جدید جدول HTML، اعتبار به‌روز شده (`lblUserCreditPrice`) و روزهای هفته.
- **قطعه `hiddenField|__VIEWSTATE`**: مقدار جدید `__VIEWSTATE` برای درخواست بعدی.
- **قطعه `scriptStartupBlock|ScriptContentNoTags`**: حاوی اجرای اسکریپت نوتیفیکیشن toast:
  - فرمت: `toastify('success', ' رزرو غذا برای 1 روز با موفقیت انجام شد؛ ', 'پیغام سیستم', ...)`
  - در صورت خطا: `toastify('error', 'موجودی کافی نیست...', ...)`
- **معیار موفقیت قطعی**:
  - استخراج اولین پارامتر تابع `toastify` برابر با `'success'`
  - به‌روزرسانی مقدار اعتبار و تغییر وضعیت ردیف به رزرو شده / لغو شده در HTML برگشتی

---

## ۲. گام‌های اجرایی و فایل‌های قابل تغییر

### ۲.۱. گسترش لایه مدل‌ها (`app/src/main/java/com/example/kalinal/data/model/MealModels.kt`)
- افزودن کلاس `FoodOption(val id: String, val name: String, val priceRials: Long, val isHalfPortion: Boolean = false)`
- افزودن کلاس `SelfOption(val id: String, val name: String, val isDefault: Boolean)`
- افزودن فیلدهای زیر به `MealItem`:
  - `isEditable: Boolean` (آیا دکمه رزرو/لغو مجاز است؟)
  - `tableRowIndex: String` (مانند `ctl06`)
  - `availableFoods: List<FoodOption>`
  - `availableSelfs: List<SelfOption>`
  - `selectedSelfId: String?`
  - `selectedFoodId: String?`
- افزودن مدل نتیجه تراکنش:
  - `data class ReservationResult(val isSuccess: Boolean, val message: String, val newCreditRials: Long?)`

### ۲.۲. به‌روزرسانی پارسر HTML و پارسر دلتا (`ReservationHtmlParser.kt` & `DeltaParser.kt`)
- پارس کردن گزینه‌های درون `<select id="...drpFood">` (استخراج شناسه، متن غذا و قیمت ریالی).
- پارس کردن گزینه‌های درون `<select id="...drpSelf">` و شناسایی سلف پیش‌فرض انتخاب‌شده.
- تشخیص فعال/غیرفعال بودن چک‌باکس `chkReserve`.
- استخراج پیام toastify از بخش `scriptStartupBlock` دلتا شامل نوع (`success`/`error`) و متن پیام فارسی.
- استخراج اعتبار جدید کاربر از `ctl00_lblUserCreditPrice`.

### ۲.۳. سازنده بدنه درخواست فرم و مدیریت وضعیت (`MenuRepository.kt`)
- **مدیریت سریال و نخ امن (Single Source of Truth & Mutex)**:
  - استفاده از `Mutex` برای اطمینان از عدم اجرای همزمان دو درخواست شبکه.
  - نگهداری آخرین مقدار `__VIEWSTATE` و `__VIEWSTATEGENERATOR` در ریپازیتوری.
- پیاده‌سازی متد `buildReservationPayload`:
  - بازسازی دقیق فرم ارسالی شامل تمام ۲۱ ردیف سه جدول مطابق داده‌های HAR.
  - درج مقادیر مربوط به روز هدف برای رزرو (همراه با `chkReserve=on`) یا لغو (بدون `chkReserve`).
- متدهای `reserveLunch(...)` و `cancelLunch(...)`:
  - اجرای فراخوانی به صورت POST با هدرهای استاندارد AJAX (`X-MicrosoftAjax: Delta=true`, `Cache-Control: no-cache`).
  - به‌روزرسانی کامل لیست غذاها و ViewState از قطعه برگشتی `UpdatePanel1`.

### ۲.۴. لایه ViewModel (`MenuViewModel.kt`)
- افزودن متدهای `reserveLunch(dayIndex: Int, foodId: String, selfId: String)` و `cancelLunch(dayIndex: Int)`.
- مدیریت وضعیت لودینگ مجزا برای جلوگیری از مسدود شدن کل صفحه (`processingMealKey: String?`).
- انتشار رخدادهای رابط کاربری (Toast / SnackBar) با پیام سرور به همراه صدای بازخورد سیستم.

### ۲.۵. طراحی UI انتخاب غذا و دیالوگ تأیید (`MenuScreen.kt` & `MealCard.kt`)
- در کارت غذای ناهار:
  - اگر ردیف قابل ویرایش نباشد: نمایش وضعیت ثابت (رزرو شده / رزرو نشده / منقضی شده) بدون دکمه اقدام.
  - اگر قابل رزرو باشد: دکمه برجسته «رزرو ناهار».
  - اگر رزرو شده و قابل ویرایش باشد: دکمه «لغو رزرو» و دکمه «تغییر غذا/سلف».
- **Bottom Sheet انتخاب غذا و سلف**:
  - نمایش رادیوباتن‌های غذا به همراه قیمت و نوع پرس.
  - نمایش سلف پیش‌فرض به همراه منوی آبشاری یا چیپ‌های تغییر سلف.
  - نمایش مقایسه اعتبار فعلی و مبلغ کسر/برگشت شونده.
- **دیالوگ تأیید نهایی**:
  - جلوگیری از کلیک تصادفی و تأیید نهایی کاربر قبل از ارسال درخواست به سرور.

### ۲.۶. آزمون‌های جامع واحد (`KalinalUnitTests.kt` & `ReservationDeltaTest.kt`)
- ایجاد تست‌های مستقل با استفاده از داده‌های واقعی استخراج‌شده از HAR:
  1. آزمون پارس پاسخ دلتای «رفتن به هفته بعد» و استخراج صحیح `__VIEWSTATE` جدید.
  2. آزمون پارس پاسخ دلتای «رزرو ناهار چهارشنبه» و تأیید استخراج پیغام موفقیت `toastify` و کسر اعتبار از ۵۹۰,۰۰۰ به ۴۱۰,۰۰۰ ریال.
  3. آزمون پارس پاسخ دلتای «لغو رزرو ناهار چهارشنبه» و تأیید افزایش اعتبار به ۵۹۰,۰۰۰ ریال.
  4. آزمون تطابق ساختار بدنه درخواست ساخته‌شده با داده‌های واقعی فرم درخواست در HAR.

---

## ۳. موارد نیازمند تست بر روی دستگاه و سرور زنده
- تغییرات اعتبار در سایر هفته‌ها و بررسی رفتار سلف هنگام رزرو مجدد در یک روز.
- تعمیم این الگو به صبحانه (`btnSaveBreakfast`) و شام (`btnSaveDinner`) در فازهای بعدی پس از اطمینان از عملکرد پایدار ناهار در محیط واقعی.
