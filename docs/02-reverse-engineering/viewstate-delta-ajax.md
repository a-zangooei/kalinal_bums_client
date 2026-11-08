# 🔄 پروتکل دلتا در ASP.NET AJAX و تحلیل فیلدهای ViewState (Delta Response & ViewState)

یکی از پیچیده‌ترین بخش‌های مهندسی معکوس سامانه‌های مبتنی بر ASP.NET Web Forms، ارتباط با کنترل‌های `ScriptManager` و `UpdatePanel` است. در این مستند، ساختار پاسخ‌های دلتا و نحوه استخراج اطلاعات تشریح شده است.

---

## ۱. ساختار پاسخ‌های دلتا (Microsoft Ajax Delta Wire Protocol)

هنگامی که درخواستی با هدرهای ناهمگام مانند زیر ارسال می‌شود:
```http
X-MicrosoftAjax: Delta=true
X-Requested-With: XMLHttpRequest
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
```
سرور به جای یک سند کامل HTML، مجموعه‌ای از بلاک‌های متنی با قالب زیر برمی‌گرداند:

```text
length|type|id|content|
```

- **`length`:** تعداد کاراکترهای بخش `content`.
- **`type`:** نوع پیام دلتا، شامل:
  - `updatePanel`: محتوای HTML جدید یک پنل به روزرسانی‌شده (مثلاً `UpdatePanel1`).
  - `hiddenField`: فیلدهای مخفی جدید برای فرم‌های بعدی (مانند `__VIEWSTATE` و `__VIEWSTATEGENERATOR`).
  - `pageRedirect`: هدایت به صفحه دیگر در صورت انقضای نشست (مانند `/Login.aspx?error=SessionExpired`).
  - `asyncPostBackError`: خطای سمت سرور مایکروسافت.
- **`id`:** نام فیلد یا شناسه کنترل.
- **`content`:** مقدار یا بدنه HTML.

---

## ۲. پیاده‌سازی تحلیل‌گر دلتا (`DeltaParser.kt`)

تحلیل‌گر اختصاصی در کلاس `app/src/main/java/com/example/data/parser/DeltaParser.kt` قرار دارد:

```kotlin
object DeltaParser {
    fun parse(rawResponse: String): DeltaResponse {
        var cursor = 0
        val updatePanels = mutableMapOf<String, String>()
        val hiddenFields = mutableMapOf<String, String>()
        var isSessionExpired = false
        var redirectUrl: String? = null
        var toastMessage: String? = null
        var toastType: String? = null

        while (cursor < rawResponse.length) {
            val pipe1 = rawResponse.indexOf('|', cursor)
            if (pipe1 == -1) break
            val lengthStr = rawResponse.substring(cursor, pipe1)
            val length = lengthStr.toIntOrNull() ?: break

            val pipe2 = rawResponse.indexOf('|', pipe1 + 1)
            val type = rawResponse.substring(pipe1 + 1, pipe2)

            val pipe3 = rawResponse.indexOf('|', pipe2 + 1)
            val id = rawResponse.substring(pipe2 + 1, pipe3)

            val contentStart = pipe3 + 1
            val contentEnd = contentStart + length
            val content = rawResponse.substring(contentStart, contentEnd)

            when (type) {
                "updatePanel" -> updatePanels[id] = content
                "hiddenField" -> hiddenFields[id] = content
                "pageRedirect" -> {
                    isSessionExpired = content.contains("Login.aspx", ignoreCase = true)
                    redirectUrl = content
                }
            }
            cursor = contentEnd + 1 // رد شدن از پایپ پایانی
        }
        return DeltaResponse(...)
    }
}
```

---

## ۳. به‌روزرسانی زنجیره‌ای ViewState

در وب‌فرم‌های ASP.NET، هر بار که کاربر عملیاتی مانند جابه‌جایی هفته یا رزرو یک غذا انجام می‌دهد، سرور یک `__VIEWSTATE` جدید تولید می‌کند.  
**قانون بسیار مهم:**  
کلاینت **باید** در درخواست بعدی، دقیقاً از جدیدترین مقدار `__VIEWSTATE` دریافت شده از پاسخ قبلی استفاده کند؛ در غیر این صورت سرور خطای `Invalid Viewstate` یا `Validation of viewstate MAC failed` صادر کرده و درخواست را رد می‌کند.
کلاس `MenuRepository` این کار را به صورت خودکار و اتمیک با دریافت مقادیر از `deltaResponse.hiddenFields["__VIEWSTATE"]` انجام می‌دهد.
