# 📬 پروتکل مهندسی معکوس پیام‌های خوانده‌نشده و قفل منوی کالینان (Unread Messages & Menu Lock)

این سند رفتار سامانه تغذیه کالینان دانشگاه علوم پزشکی بیرجند در مواجهه با کاربران دارای پیام خوانده‌نشده، سازوکار قفل سراسری منوها، و پروتکل دقیق باز کردن و خواندن پیام‌ها از طریق درخواست‌های دلتای ASP.NET را بر اساس ضبط واقعی ترافیک شبکه با ابزار PCAPdroid تشریح می‌کند.

---

## ۱. خلاصه‌ی رفتار سامانه و قفل منوی رزرو

سامانه کالینان دارای سازوکار اجباری برای اطمینان از مطالعه اطلاعیه‌ها و پیام‌های ارسالی توسط دانشجویان است:
1. **قفل سراسری امکانات:** در صورتی که کاربر حتی یک پیام خوانده‌نشده داشته باشد، تمامی آیتم‌های منوی سیستم (شامل «رزرو غذا»، «رزرو روزفروش»، «افزایش اعتبار» و...) در سمت سرور حذف شده و کاربر تنها به صفحه پیام‌ها (`/MyCullinan/MyMessage.aspx`) دسترسی دارد.
2. **ریدایرکت احراز هویت:**
   - کاربر بدون پیام خوانده‌نشده: `POST /Login.aspx` ← `302 Location: /Master/Default.aspx`
   - کاربر با حداقل یک پیام خوانده‌نشده: `POST /Login.aspx` ← `302 Location: /MyCullinan/MyMessage.aspx`
3. **ثبت خوانده‌شدن در سمت سرور:** ثبت تاریخ و زمان مطالعه پیام دقیقاً با ارسال درخواست کلیک روی دکمه تصویر متن پیام (`imgbtnMessageText`) انجام می‌شود و نه با دیالوگ تایید کلاینت.
4. **رفتار پاپ‌آپ مرورگر:** دکمه «تایید» در پاپ‌آپ صرفاً دستور جاوااسکریپتی `window.location.reload()` است که موجب ارسال یک درخواست `GET` مجدد به صفحه پیام‌ها می‌شود.
5. **رفع قفل منو:** پس از اینکه آخرین پیام خوانده‌نشده باز شد، پاسخ درخواست `GET /MyCullinan/MyMessage.aspx` مجدداً حاوی لینک‌های کامل سامانه از جمله `Reservation/Reservation.aspx` خواهد بود.

---

## ۲. ساختار صفحه پیام‌ها (`MyMessage.aspx`)

- **آدرس:** `GET https://jeton.bums.ac.ir/MyCullinan/MyMessage.aspx`
- **شناسه فرم:** `<form id="MainForm" action="./MyMessage.aspx">`
- **شناسه ژنراتور حالت:** `__VIEWSTATEGENERATOR=87AF9C2D`
- **جدول پیام‌ها:** `<table id="cphMain_grdContent">`

### ستون‌های جدول (`cphMain_grdContent`):
| ستون | عنوان | توضیح فنی |
|:---:|---|---|
| ۰ | **ردیف** | شماره ردیف پیام (مثلاً ۱، ۲، ...) |
| ۱ | **عنوان پیام** | عنوان اطلاعیه (مثلاً «همراه داشتن کارت دانشجویی») |
| ۲ | **متن پیام** | دکمه تصویری `<input type="image" name="ctl00$cphMain$grdContent$ctlNN$imgbtnMessageText">` |
| ۳ | **تاریخ ارسال** | تاریخ هجری شمسی ارسالی از مدیریت تغذیه |
| ۴ | **زمان ارسال** | ساعت ارسال پیام |
| ۵ | **وضعیت** | آیکون وضعیت: `MsgClose.png` (خوانده‌نشده) یا `MsgOpen.png` (خوانده‌شده) |
| ۶ | **تاریخ خواندن** | تاریخ مطالعه توسط کاربر (برای پیام‌های جدید مقدار کاراکتر `-`) |
| ۷ | **زمان خواندن** | ساعت مطالعه پیام (برای پیام‌های جدید مقدار `-`) |

> 📌 **نکته نام‌گذاری کنترل‌ها:** ردیف اول داده با اندیس `ctl02`، ردیف دوم با `ctl03` و به ازای ردیف $N$ با فرمت دو رقمی `ctl(N+1)` نام‌گذاری می‌شود.

---

## ۳. درخواست باز کردن و خواندن پیام (Delta POST)

برای ثبت خوانده‌شدن و استخراج متن، درخواست ناهمگام ASP.NET AJAX ارسال می‌گردد:

```http
POST https://jeton.bums.ac.ir/MyCullinan/MyMessage.aspx HTTP/1.1
Host: jeton.bums.ac.ir
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
X-Requested-With: XMLHttpRequest
X-MicrosoftAjax: Delta=true
Cache-Control: no-cache
Accept: */*
Origin: https://jeton.bums.ac.ir
Referer: https://jeton.bums.ac.ir/MyCullinan/MyMessage.aspx
Cookie: ASP.NET_SessionId=...; keyId=...; __AntiXsrfToken=...
```

### فیلدهای فرم ارسالی (Form Data):
```
ctl00$ScriptManager=ctl00$UpdatePanel1|ctl00$cphMain$grdContent$ctl02$imgbtnMessageText
__EVENTTARGET=
__EVENTARGUMENT=
__VIEWSTATE=<Current_ViewState_Token>
__VIEWSTATEGENERATOR=87AF9C2D
__VIEWSTATEENCRYPTED=
ctl00$cphMain$hdnId=
__ASYNCPOST=true
ctl00$cphMain$grdContent$ctl02$imgbtnMessageText.x=10
ctl00$cphMain$grdContent$ctl02$imgbtnMessageText.y=10
```

---

## ۴. ساختار پاسخ دلتا (Delta Response)

سرور یک فریم چندبخشی دلتا با کد ۲۰۰ برمی‌گرداند:
`length|type|id|content|...`

اجزای کلیدی این پاسخ:
1. **بخش UpdatePanel:** حاوی شناسه `updatePanel|UpdatePanel1|...`
   - متن پیام داخل تگ پاراگراف `<p id="cphMain_pMessage">` قرار دارد.
   - جدول پیام‌ها نیز به‌روزرسانی شده و آیکون همان ردیف به `MsgOpen.png` تبدیل شده است.
2. **بخش فیلد مخفی (Hidden Field):** حاوی شناسه `hiddenField|__VIEWSTATE|<New_ViewState_Token>|`
   - مقدار جدید `__VIEWSTATE` برای درخواست پیام بعدی استخراج می‌شود.
3. **اسکریپت سمت کلاینت:** حاوی `scriptStartupBlock` با محتوای `showMessage();`.

---

## ۵. الگوریتم خواندن زنجیره‌ای (ViewState Chaining) در کلاینت کالینان

```
               [GET /MyMessage.aspx]
                         │
                         ▼
             استخراج ViewState اولیه (VS_0)
                         │
                         ▼
        ┌───► [پیام خوانده‌نشده باقی است؟] ───────┐
        │                │                       │
        │             بله │                       │ خیر
        │                ▼                       ▼
        │     POST Delta با فیلد VS_i     [GET /MyMessage.aspx]
        │                │                       │
        │                ▼                       ▼
        │      دریافت VS جدید (VS_i+1)     آیا منوی رزرو فعال است؟
        │                │                       │
        └────────────────┘                       ▼
                                       ورود مستقیم به رزرو غذا
```

### الزامات امنیتی و پایداری در پیاده‌سازی:
1. **زنجیره ترتیبی (Strict Serialization):** هرگز درخواست‌های باز کردن پیام نباید به صورت همزمان یا موازی ارسال شوند؛ فیلد `__VIEWSTATE` وب‌فرم‌های دات‌نت حالت وضعیت صفحه را نگه می‌دارد و ارسال موازی بلافاصله باعث خطای `Validation of viewstate MAC failed` می‌شود.
2. **عدم تلاش مجدد خودکار (No Auto-Retry):** در صورت بروز هرگونه خطای شبکه در متد POST خواندن پیام، فرآیند بلافاصله متوقف می‌شود تا توکن‌های نشست و سشن مخدوش نگردند.
3. **سقف تکرار (Iteration Ceiling):** حلقه خواندن حداکثر ۵۰ پیام متوالی را پردازش می‌کند تا در صورت وجود باگ در دیتابیس دانشگاه، حلقه بی‌پایان رخ ندهد.
4. **حفاظت از داده‌های حساس:** متن پیام‌ها و رشته چندکیلوبایتی ViewState در لاگ‌های عمومی ثبت نمی‌شوند.

---

## ۶. پیاده‌سازی در معماری اپلیکیشن اندروید

- **`AuthRepository.login`:** بررسی مقدار هدر `Location` پاسخ ۳۰۲ جهت شناسایی ورود مستقیم یا نیاز به هدایت به صفحه پیام‌ها.
- **`PageClassifier.TYPE_MESSAGES`:** تشخیص صفحه پیام‌ها با شناسایی جدول `cphMain_grdContent`، شمارش دقیق ردیف‌های `MsgClose` و بررسی وجود کنترل‌های صفحه‌بندی (`PagerStyle`).
- **`MessageRepository`:** کپسوله‌سازی منطق واکشی صفحه، درخواست‌های دلتا و زنجیره خواندن سریال پیام‌ها.
- **`MenuRepository`:** محافظت در برابر ریدایرکت مستقیم از صفحه رزرو به پیام‌ها با پرتاب استثنای `UnreadMessagesException`.
- **`MessagesScreen` و `MessagesViewModel`:**
  - تب اختصاصی در `NavigationBar` با بج نشانگر تعداد پیام‌های جدید.
  - حالت مسدودکننده (Blocking Mode) با بنر توضیحی و دکمه صریح «خواندن پیام‌ها و فعال‌سازی رزرو».
  - نمایش پیشرفت لحظه‌ای خواندن پیام‌ها.
  - انتقال مستقیم و خودکار به جدول رزرو پس از باز شدن قفل سامانه.
