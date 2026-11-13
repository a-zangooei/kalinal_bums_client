# 🔐 پروتکل رمزنگاری سمت کلاینت با کلید عمومی (RSA Encryption)

سامانه کالینان دانشگاه برای جلوگیری از ارسال متن خام نام کاربری و کلمه عبور در بستر شبکه، از الگوریتم رمزنگاری نامتقارن RSA با استاندارد **PKCS#1 v1.5** استفاده می‌کند. این سند نحوه دریافت کلید عمومی، تبدیل آن و رمزگذاری اطلاعات را شرح می‌دهد.

---

## ۱. نحوه استخراج کلید عمومی سرور

هنگام فراخوانی متد `GET /Login.aspx`، سرور دانشگاه یک کلید عمومی با فرمت PEM را داخل عنصر متنی زیر در صفحه تزریق می‌کند:

```html
<textarea id="publicKeytxt" name="publicKeytxt" style="display:none;">
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
-----END PUBLIC KEY-----
</textarea>
```

در کلاس `AuthRepository.kt` این کلید با کتابخانه Jsoup استخراج می‌شود:
```kotlin
val publicKeyPem = doc.select("#publicKeytxt, textarea[name=publicKeytxt]").text().trim()
```

---

## ۲. پیاده‌سازی رمزنگاری در کلاس `RsaEncryptor`

برای استفاده از این کلید در کدهای اندروید:
1. هدر و فوتر استاندارد (`-----BEGIN PUBLIC KEY-----` و `-----END PUBLIC KEY-----`) و کاراکترهای شکست خط حذف می‌شوند.
2. متن Base64 باقیمانده به بایت‌های باینری استاندارد `X509EncodedKeySpec` تبدیل می‌گردد.
3. یک شیء `PublicKey` با الگوریتم `"RSA"` ساخته می‌شود.
4. متن ورودی با سایفر `"RSA/ECB/PKCS1Padding"` رمزنگاری شده و خروجی نهایی به صورت رشته Base64 به سرور ارسال می‌شود.

کد الگوریتم پیاده‌سازی‌شده در `app/src/main/java/com/example/data/crypto/RsaEncryptor.kt`:

```kotlin
object RsaEncryptor {
    fun encryptWithPem(plainText: String, pemKey: String): String {
        val cleanKey = pemKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")

        val keyBytes = Base64.decode(cleanKey, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(keySpec)

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }
}
```

---

## ۳. نکات امنیتی و تطابق با سرور

- **پدینگ (Padding):** سرور مایکروسافت از پدینگ `PKCS1Padding` استفاده می‌کند. استفاده از الگوریتم‌های دیگر مانند `OAEP` منجر به خطای عدم امکان رمزگشایی در سرور ASP.NET خواهد شد.
- **فرمت ارسال:** رشته خروجی باید بدون درج کاراکترهای خط جدید (`Base64.NO_WRAP`) در فرم ارسال شود تا در فرم وب‌فرم دچار شکستگی کاراکتر نشود.
- **وابستگی به اعتبارسنجی TLS:** رمزنگاری با کلید عمومی دریافت‌شده از صفحه ورود (`publicKeytxt`) تنها زمانی در برابر شنود فعال (MITM) مقاوم است که هویت سرور با گواهی TLS معتبر احراز شده باشد. در وضعیت موقت فعلی برنامه، اگر حالت بدون اعتبارسنجی گواهی (`fallbackClient`) فعال شود، مهاجم میانی می‌تواند روی شبکه ناامن کلید عمومی صفحه را جایگزین کند یا پس از ورود کوکی‌های نشست را سرقت نماید.

