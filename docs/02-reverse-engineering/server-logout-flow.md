# 🚪 پیاده‌سازی «خروج واقعی» از سامانه کالینان (Server-Side Logout)

این مستند به تحلیل دقیق فنی و پیاده‌سازی فرآیند **خروج واقعی (Server-Side Session Termination)** از سامانه تغذیه کالینان دانشگاه علوم پزشکی بیرجند (`jeton.bums.ac.ir`) بر مبنای ضبط ترافیک زنده و رمزگشایی TLS مرورگر Chrome (توسط ابزار PCAPdroid) می‌پردازد.

---

## ۱. هدف و ضرورت پیاده‌سازی

پیش از این، کلاینت‌های غیررسمی برای خروج صرفاً کوکی‌های محلی دستگاه (`CullinanCookieJar`) را پاک می‌کردند. این اقدام کافی نبود؛ زیرا سشن کاربر (`ASP.NET_SessionId`) در حافظه سرور دانشگاه تا زمان منقضی شدن فعال می‌ماند. برای ابطال قطعی نشست در سمت سرور، کلاینت باید دکمه خروج وب‌سایت را همانند رفتار مرورگر شبیه‌سازی کرده و سپس وضعیت محلی را تخلیه نماید.

---

## ۲. دکمه خروج در ساختار HTML سایت

در هدر مشترک صفحات مستر سامانه (هم داشبورد `Master/Default.aspx` و هم جدول `Reservation/Reservation.aspx`):

```html
<a id="logout" href="javascript:__doPostBack('ctl00$logout','')">
  <img src="../images/Style/logout.png" title="خروج" />
</a>
```

این دکمه یک رویداد استاندارد `__doPostBack` در چارچوب ASP.NET WebForms است:
- پارامتر `__EVENTTARGET` برابر با `ctl00$logout` تنظیم می‌شود.
- کل فرم صفحه (`MainForm`) به صورت یک درخواست **POST سنکرون و معمولی (غیر Ajax / غیر Delta)** ارسال می‌گردد.
- این المان خارج از `UpdatePanel` قرار دارد؛ بنابراین هدرهای `X-MicrosoftAjax` و `X-Requested-With` و فیلد `__ASYNCPOST` به سرور ارسال **نمی‌شوند**.

---

## ۳. تحلیل ترافیک در ضبط واقعی (Packet Capture)

```http
POST /Reservation/Reservation.aspx HTTP/2
Host: jeton.bums.ac.ir
Content-Type: application/x-www-form-urlencoded
Origin: https://jeton.bums.ac.ir
Referer: https://jeton.bums.ac.ir/Reservation/Reservation.aspx
Cookie: ASP.NET_SessionId=...; keyId=...; __AntiXsrfToken=...
```

### ساختار بدنه فرم ارسالی:

| نام فیلد | مقدار ارسالی | توضیحات |
|---|---|---|
| `__EVENTTARGET` | `ctl00$logout` | هدف رویداد پست‌بک جهت فراخوانی تابع خروج سرور |
| `__EVENTARGUMENT` | `""` | رشته تهی |
| `__LASTFOCUS` | `""` | رشته تهی |
| `__VIEWSTATE` | آخرین ViewState فعال صفحه | رشته کدگذاری‌شده وضعیت صفحه جاری |
| `__VIEWSTATEGENERATOR` | `3ED98A75` | شناسه تولیدکننده ساختار صفحه |
| `__VIEWSTATEENCRYPTED` | `""` | فیلد تهی |
| `__EVENTVALIDATION` | توکن اعتبارسنجی رویداد | در صورت وجود در جدول فعال |
| `ctl00$cphMain$hdnSelectedTab` | `css3-tabstrip-0-1` | تب انتخاب‌شده جدول هفتگی |
| `css3-tabstrip-0` | `on` | استایل تب‌بار |
| فیلدهای ردیف‌های ناهار | وضعیت چک‌باکس‌ها و منوها | ارسال ساختار کامل فرم مطابق مرورگر |

---

## ۴. پاسخ موفق سرور دانشگاه به خروج

```http
HTTP/2 302 Found
Location: /Login.aspx
Set-Cookie: ASP.NET_SessionId=; path=/; secure; HttpOnly; SameSite=None
Cache-Control: no-cache, no-store
```

- **کد ۳۰۲ (Found):** سرور کاربر را به آدرس `/Login.aspx` هدایت (Redirect) می‌کند.
- **ابطال کوکی سشن:** سرور با ارسال هدر `Set-Cookie` با مقدار خالی برای `ASP.NET_SessionId`، نشست را در لایه مرورگر باطل می‌کند.

---

## ۵. معماری پیاده‌سازی در اپلیکیشن اندروید (`AuthRepository`)

1. **غیرفعال‌سازی ریدایرکت خودکار در OkHttp:**  
   برای شناسایی دقیق پاسخ `302 Found` و تفکیک خروج موفق از شکست، درخواست خروج با کلاینت بدون ریدایرکت ارسال می‌شود:
   ```kotlin
   val noRedirectClient = baseClient.newBuilder()
       .followRedirects(false)
       .followSslRedirects(false)
       .connectTimeout(6, TimeUnit.SECONDS)
       .readTimeout(6, TimeUnit.SECONDS)
       .build()
   ```
2. **بررسی پاسخ و تشخیص موفقیت:**
   ```kotlin
   val location = response.header("Location") ?: ""
   val isSuccess = (response.code in 300..399 && location.contains("Login.aspx", ignoreCase = true)) ||
           location.contains("Login.aspx", ignoreCase = true)
   ```
3. **پاک‌سازی بدون قیدوشرط محلی (Unconditional Local Purge):**
   حتی اگر ارتباط اینترنتی قطع باشد یا سرور خطای ۵۰۰ دهد، کلاینت محلی بلافاصله اقدامات زیر را به انجام می‌رساند:
   - پاک‌سازی سشن و کوکی‌ها با `CullinanHttpClient.cookieJar.clear()`.
   - صفر کردن مقادیر مدل `UserSession` و منوی فعال در حافظه رم.
   - حفظ اطلاعات ذخیره‌شده «مرا به خاطر بسپار» در صورت تمایل کاربر.
   - نمایش پیام متناسب فارسی با `Toast` به کاربر.

---

## ۶. نتایج آزمون‌های واحد و شبیه‌سازی (Unit Tests)

- در کلاس `MockWebServerSecurityUnitTest`، سناریوهای بازگشت کد ۳۰۲ و باطل‌سازی کوکی با وب‌سرور شبیه‌ساز راستی‌آزمایی شده است.
- رفتار شبکه در شرایط قطعی اینترنت و تایم‌اوت ارزیابی شده و تضمین می‌کند که رابط کاربری دچار قفل‌شدگی نمی‌شود.
