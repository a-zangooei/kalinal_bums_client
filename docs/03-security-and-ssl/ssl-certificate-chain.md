# ⚠️ وضعیت فعلی SSL، تحلیل زنجیره گواهی و ریسک‌های شناخته‌شده (SSL Certificate & Security Audit)

> [!WARNING]
> **وضعیت فعلی برنامه (موقت — پیش از فاز اصلاح SSL):**
> در نگارش کنونی، در دستگاه‌هایی که مخزن گواهی‌های سیستم‌عامل آن‌ها فاقد ریشه `Certum Trusted Root CA` است (یا هنگام بروز هرگونه `SSLException`)، کلاس `CullinanHttpClient` به‌صورت خودکار وارد **حالت سازگار (`fallbackClient`)** می‌شود.
> این کلاینت جایگزین **هیچ‌گونه اعتبارسنجی روی گواهی SSL و نام میزبان (`HostnameVerifier`) انجام نمی‌دهد** و عملاً بدون بررسی اصالت گواهی کار می‌کند. بنابراین **تا پیش از اجرای تغییرات بعدی (باندل کردن گواهی ریشه معتبر و حذف Fallback بدون اعتبارسنجی)، استفاده از برنامه روی شبکه‌های نامطمئن (وای‌فای عمومی، پروکسی‌های مشکوک یا شبکه‌های دارای شنود) می‌تواند در برابر حملات MITM ناامن باشد.**

---

## ۱. داده‌های واقعی اندازه‌گیری‌شده روی `jeton.bums.ac.ir`

بررسی مستقیم روی سرور `jeton.bums.ac.ir` (تاریخ بررسی: `2026-11-10` / `1405/08/19` با `openssl s_client` در Termux بدون VPN و ابزار شنود) نشان داد:

### ۱.۱ زنجیره گواهی ارسالی سرور [اندازه‌گیری]

| ردیف | نقش | Subject | Issuer | بازه اعتبار |
|:---:|---|---|---|---|
| ۱ | **Leaf** | `CN=*.bums.ac.ir` (SAN: `*.bums.ac.ir`, `bums.ac.ir`) | `Certum DV TLS G2 R39 CA` | `2025-12-28` تا **`2026-12-28`** |
| ۲ | **میانی (Intermediate)** | `Certum DV TLS G2 R39 CA` | `Certum Trusted Root CA` | `2024-06-18` تا `2039-06-05` |
| — | **ریشه (ارسال نمی‌شود)** | `Certum Trusted Root CA` | خودامضا (Self-Signed) | `2018-03-16` تا **`2043-03-16`** |

- سرور **هر دو گواهی** (Leaf و گواهی میانی) را ارسال می‌کند و نفرستادن گواهی ریشه طبق استاندارد TLS کاملاً درست است.
- **اثرانگشت SHA-256 ریشه (`Certum Trusted Root CA`):**  
  `FE:76:96:57:38:55:77:3E:37:A9:5E:7A:D4:D9:CC:96:C3:01:57:C1:5D:31:76:5B:A9:B1:57:04:E1:AE:78:FD` (مطابق با پرونده پذیرش موزیلا: [Mozilla Bug 1598577](https://bugzilla.mozilla.org/show_bug.cgi?id=1598577)).
- **نشانی‌های AIA داخل گواهی‌ها:**  
  - در Leaf: OCSP به نشانی `http://certumdvtlsg2r39ca.ocsp-certum.com` و CA Issuers به نشانی `http://certumdvtlsg2r39ca.repository.certum.pl/certumdvtlsg2r39ca.cer`.
  - در میانی: CA Issuers به نشانی `http://subca.repository.certum.pl/ctrca.cer`.
  - نشانی‌های AIA همگی **HTTP ساده** هستند و در آزمایش میدانی از ایران با Timeout روبه‌رو شدند.

### ۱.۲ پروتکل و رمزنگاری لایه انتقال [اندازه‌گیری]
- پروتکل مذاکره‌شده: **TLS 1.2** با رمز `ECDHE-RSA-AES256-GCM-SHA384` (با وجود پشتیبانی کلاینت OpenSSL 3.6 از TLS 1.3، سرور احتمالاً TLS 1.3 را عرضه نمی‌کند).
- **HTTP/2** فعال است.

### ۱.۳ آزمایش عملی روی گوشی واقعی (Android 12، پچ `2024-02-01`) [اندازه‌گیری]

| مخزن گواهی در دستگاه | تعداد ریشه | وجود `Certum Trusted Root CA` | نتیجه اعتبارسنجی Leaf + میانی ارسالی |
|---|:---:|:---:|---|
| `/system/etc/security/cacerts` | ۱۲۵ | **ندارد** (فقط `Certum Trusted Network CA` و `CA 2`) | **شکست**: `unable to get local issuer certificate` |
| `/apex/com.android.conscrypt/cacerts` | ۱۴۹ | دارد | موفق |
| مخزن `system` + افزودن ریشه `Certum Trusted Root CA` | ۱۲۶ | دارد | **موفق (`sent1.pem: OK`)** |

در لاگ همان گوشی (بیلد ۵۴)، اولین درخواست پس از اجرای برنامه با خطای زیر شکست خورد و سپس برنامه به حالت بدون اعتبارسنجی رفت:
```text
SSLHandshakeException: java.security.cert.CertPathValidatorException: Trust anchor for certification path not found.
```

---

## ۲. اصلاح اشتباه مستندات قبلی (چرا خطا رخ می‌دهد و مرورگر باز می‌کند؟)

در مستندات پیشین به اشتباه تصور می‌شد که سرور دانشگاه گواهی میانی را ارسال نمی‌کند و پشته اندروید به دلیل نداشتن AIA Chasing شکست می‌خورد. **داده‌های واقعی این فرضیه را رد کردند:**
1. سرور دانشگاه گواهی میانی (`Certum DV TLS G2 R39 CA`) را **ارسال می‌کند**؛ بنابراین مشکل از AIA Chasing نیست.
2. **علت اصلی:** ریشه جدیدتر **`Certum Trusted Root CA` (صادرشده در ۲۰۱۸)** در مخزن قدیمی‌تر `/system/etc/security/cacerts` برخی نسخه‌ها و رام‌های اندروید (مانند گوشی تست‌شده با اندروید ۱۲) وجود ندارد و برنامه اندروید عملاً از همین مخزن استفاده می‌کند.
3. **چرا مرورگر مشکل ندارد؟** Chrome از مخزن مستقل خودش (`Chrome Root Store`) و Firefox از مخزن `NSS` (از نسخه ۹۰ / NSS 3.66 به بعد شامل Certum) استفاده می‌کنند، اما برنامه‌های بومی اندروید به مخزن ریشه‌های سیستم‌عامل وابسته‌اند. همچنین در اندروید ۷ به بالا (`minSdk = 24`)، برنامه‌ها به‌صورت پیش‌فرض به گواهی‌های نصب‌شده توسط کاربر اعتماد نمی‌کنند و بررسی ابطال (OCSP/CRL) نیز به‌صورت پیش‌فرض در اندروید انجام نمی‌شود.

| نسخه اندروید | وضعیت احتمالی مخزن اعتماد (`system`) | سطح اطمینان |
|---|---|---|
| ۷.۰ تا ۱۱ | مخزن `system` منجمد در زمان انتشار نسخه؛ ریشه Certum 2018 احتمالاً در بسیاری از دستگاه‌ها موجود نیست. | [فرضیه] |
| **۱۲ (دستگاه تست)** | **۱۲۵ ریشه در `system` بدون `Certum Trusted Root CA` (شکست واقعی).** | **[اندازه‌گیری]** |
| ۱۳ | نامشخص؛ وابسته به سازنده و سطح پچ امنیتی. | نیازمند تست |
| ۱۴ به بعد | به‌روزرسانی ریشه‌ها از طریق ماژول Conscrypt (Mainline)؛ احتمال وجود ریشه بیشتر است اما به Google Play System Update وابسته است. | [فرضیه] |
| رام‌های سفارشی / بدون GMS | وابسته به مخزن تعیین‌شده توسط سازنده. | [فرضیه] |

---

## ۳. ممیزی صادقانه وضعیت فعلی کدهای برنامه (`CullinanHttpClient` و پیکربندی شبکه)

برخلاف ادعاهای نادرست پیشین مبنی بر «امن بودن کامل و انحصار دامنه در کلاینت جایگزین»، بررسی دقیق کدهای فعلی (`CullinanHttpClient.kt` و `network_security_config.xml`) موارد زیر را نشان می‌دهد:

1. **عدم اعتبارسنجی گواهی و نام میزبان در `fallbackClient`:**
   - پیاده‌سازی کنونی `fallbackClient` از یک `X509TrustManager` کاملاً باز (متدهای خالی `checkClientTrusted` و `checkServerTrusted` و `getAcceptedIssuers()` خالی) همراه با `hostnameVerifier { _, _ -> true }` استفاده می‌کند.
   - **هیچ محدودیت دامنه‌ای (`*.bums.ac.ir`) در `fallbackClient` اعمال نشده است** و هر گواهی یا نام میزبانی بدون بررسی پذیرفته می‌شود.
2. **فعال‌شدن Fallback روی هر `SSLException`:**
   - متد `isSslHandshakeException` برای **هر** `SSLException` مقدار `true` برمی‌گرداند. یعنی حتی اختلال شبکه، قطع اتصال توسط DPI/ISP (`Connection reset`) یا پروکسی‌های مخرب نیز می‌تواند `_isCompatibilityModeActive` را برای کل نشست `true` کند و برنامه را به حالت بدون اعتبارسنجی ببرد.
3. **مجاز بودن گواهی کاربر در `network_security_config.xml`:**
   - در حال حاضر نه‌تنها برای `jeton.bums.ac.ir`، بلکه در `<base-config>` نیز `<certificates src="user" />` فعال است که در بیلدهای انتشار (Release) می‌تواند سطح حمله را افزایش دهد.
4. **پیامد امنیتی فعلی (MITM Risk):**
   - هرچند نام کاربری و گذرواژه در فرم ورود با کلید عمومی RSA (`publicKeytxt`) رمزگذاری می‌شوند، اگر هنگام فعال‌بودن حالت بدون گواهی یک مهاجم میانی (MITM) روی وای‌فای عمومی یا پروکسی مخرب ترافیک را شنود کند، **می‌تواند کلید عمومی جعلی جایگزین کند یا کوکی‌های نشست (`ASP.NET_SessionId`، `__AntiXsrfToken`) و داده‌های حساب را سرقت نماید.**

---

## ۴. ماتریس سناریوهای گواهی و رفتار برنامه (فعلی در برابر اصلاح برنامه‌ریزی‌شده)

اصلاح برنامه‌ریزی‌شده برای فاز بعدی SSL: **افزودن فایل PEM ریشه `Certum Trusted Root CA` به `res/raw` به‌صورت TrustManager ترکیبی (ریشه‌های سیستم + ریشه همراه)، حذف کامل `fallbackClient` بدون اعتبارسنجی، و تفکیک دقیق خطاهای گواهی از خطاهای شبکه.**

| # | وضعیت | نشانه خطا / شرایط | رفتار **فعلی** برنامه | رفتار **بعد از اصلاح آتی** | اقدام نگهداری |
|---|---|---|---|---|---|
| ۵.۱ | ریشه Certum در مخزن دستگاه هست | اتصال بدون خطا | کلاینت استاندارد موفق می‌شود. | مسیر ترکیبی (سیستم + ریشه همراه) موفق می‌شود. | ندارد |
| ۵.۲ | **ریشه Certum در مخزن نیست (حالت اندازه‌گیری‌شده)** | `CertPathValidatorException: Trust anchor for certification path not found` | استاندارد شکست می‌خورد ← سوییچ به `fallbackClient` **بدون اعتبارسنجی (ناامن)** | ریشه همراه `res/raw` زنجیره را می‌بندد و **اتصال امن** برقرار می‌شود. | افزودن PEM ریشه در فاز آتی SSL |
| ۵.۳ | سرور میانی را نفرستد (خطای آینده سرور) | `Trust anchor ... not found` حتی با وجود ریشه | سوییچ به `fallbackClient` ناامن | شکست مشخص با خطای گواهی (یا در صورت نیاز افزودن میانی به‌عنوان helper بدون اعتماد) | پایش دوره‌ای سرور |
| ۵.۴ | تمدید سالانه گواهی Leaf | تعویض Leaf (فعلی تا `2026-12-28`) | بی‌تأثیر | بی‌تأثیر | **هرگز Leaf را Pin نکنید.** |
| ۵.۵ | تغییر گواهی میانی (مثلاً شماره R جدید) | سرور میانی جدید را می‌فرستد | بی‌تأثیر | بی‌تأثیر تا وقتی ریشه همان باشد | **هرگز Intermediate را Pin نکنید.** |
| ۵.۶ | تغییر CA یا ریشه دانشگاه | `Trust anchor ... not found` | سوییچ به `fallbackClient` ناامن | شکست مشخص با پیام «گواهی سرور معتبر نیست» | انتشار نسخه جدید با PEM ریشه تازه |
| ۵.۷ | انقضای گواهی سرور | `CertificateExpiredException` | سوییچ به `fallbackClient` ناامن | توقف و نمایش پیام روشن (بدون Fallback) | اطلاع به پشتیبانی سامانه |
| ۵.۸ | اشتباه‌بودن ساعت دستگاه | `CertificateNotYetValidException` / منقضی | سوییچ به `fallbackClient` ناامن | توقف و یادآوری بررسی ساعت و تاریخ گوشی | اصلاح ساعت دستگاه کاربر |
| ۵.۹ | شبکه شنودکننده (وای‌فای عمومی / پروکسی / PCAPdroid) | `Trust anchor` یا `PCAPdroid CA` یا عدم تطابق میزبان | **با Fallback، کوکی و داده در معرض شنودگر قرار می‌گیرد** | در بیلد Release رد می‌شود؛ CA کاربر فقط در Debug (در صورت نیاز ضبط) مجاز شود. | حذف `<certificates src="user" />` از Release |
| ۵.۱۰ | اتصال با IP یا نام دیگر (خارج از `*.bums.ac.ir`) | `SSLPeerUnverifiedException` | در Fallback با `hostnameVerifier -> true` پذیرفته می‌شود! | مسدود با بررسی نام میزبان | فقط دامنه `jeton.bums.ac.ir` مجاز باشد |
| ۵.۱۱ | پروتکل یا رمز قدیمی | `SSLProtocolException` (TLS 1.0/1.1) | نباید Fallback شود | توقف با پیام «تنظیمات امنیتی سرور ناسازگار است» | بررسی فنی |
| ۵.۱۲ | فیلتر، DPI یا قطع شبکه | `SSLException: Connection reset`, `SocketTimeoutException` | **باگ فعلی:** هر `SSLException` حالت بدون اعتبارسنجی را روشن می‌کند | خطای شبکه محسوب می‌شود (`GET`: حداکثر ۱ تلاش مجدد؛ `POST`: هرگز تکرار خودکار نشود) | تفکیک دقیق استثناها |
| ۵.۱۳ | ابطال گواهی (OCSP/CRL) | افشای احتمالی کلید سرور | بدون بررسی (پیش‌فرض اندروید) | خارج از دامنه کلاینت (ریسک پذیرفته‌شده) | پایش عمومی |

---

## ۵. دسته‌بندی پیشنهادی خطاها برای پیاده‌سازی اصلاح آتی

| استثنا در زنجیره علت (`cause`) | دسته پیشنهادی | رفتار مطلوب در فاز آتی SSL |
|---|---|---|
| `CertPathValidatorException`, `CertificateException`, `SSLPeerUnverifiedException` | `TLS_CERT_UNTRUSTED` | توقف فوری + پیام «گواهی سرور معتبر نیست»؛ **بدون تلاش مجدد و بدون Fallback** |
| `CertificateExpiredException`, `CertificateNotYetValidException` | `TLS_CERT_DATE` | توقف + پیام بررسی ساعت و تاریخ گوشی |
| `SSLProtocolException` | `TLS_PROTOCOL` | توقف + پیام فنی ناسازگاری پروتکل امنیتی |
| سایر `SSLException`، `SocketException`، `EOFException`، `SocketTimeoutException` | `NETWORK` | فقط خطای شبکه؛ `GET` حداکثر یک بار تکرار، `POST` بدون تکرار خودکار |
| `UnknownHostException`, `ConnectException` | `NETWORK_DNS/CONNECT` | پیام بررسی اتصال اینترنت |

---

## ۶. روش تشخیص روی هر دستگاه (Termux) و جدول پایش

پیش از اجرا، هرگونه VPN، پروکسی و ابزار ضبط (مثل PCAPdroid) را خاموش کنید:

```bash
pkg install -y openssl-tool
cat > ~/chain_check.sh <<'EOF'
H=${1:-jeton.bums.ac.ir}
mkdir -p ~/chain && cd ~/chain && rm -f sent*.pem
openssl s_client -connect $H:443 -servername $H -showcerts </dev/null >raw.txt 2>/dev/null
awk '/BEGIN CERTIFICATE/{n++;on=1} on{print > ("sent" n ".pem")} /END CERTIFICATE/{on=0}' raw.txt
echo "Sent certs: $(ls sent*.pem | wc -l)"
for f in sent*.pem; do echo "-- $f"; openssl x509 -in $f -noout -subject -issuer -dates -fingerprint -sha256; done
grep -E "Protocol|Cipher" raw.txt | head -2
cat sent[2-9].pem > inter.pem 2>/dev/null
echo "Android $(getprop ro.build.version.release) / patch $(getprop ro.build.version.security_patch) / $(getprop ro.product.manufacturer)"
for D in /system/etc/security/cacerts /apex/com.android.conscrypt/cacerts; do
  [ -d $D ] || continue
  : > store.pem
  for f in $D/*; do openssl x509 -in "$f" >> store.pem 2>/dev/null; done
  echo "== $D : $(grep -c 'BEGIN CERT' store.pem) roots"
  echo -n "Certum Trusted Root CA: "
  for f in $D/*; do openssl x509 -in "$f" -noout -subject 2>/dev/null; done | grep -c "CN=Certum Trusted Root CA"
  echo -n "Verify: "; openssl verify -CAfile store.pem -untrusted inter.pem sent1.pem 2>&1 | tail -1
done
EOF
bash ~/chain_check.sh
```

### جدول دستگاه‌های ثبت‌شده

| دستگاه / سازنده | نسخه اندروید | پچ امنیتی | وجود `Certum Trusted Root CA` در `system` | خروجی `openssl verify` | رفتار برنامه فعلی |
|---|---|---|---|---|---|
| دستگاه تست مرجع | ۱۲ | `2024-02-01` | ندارد (۱۲۵ ریشه) | شکست (`unable to get local issuer certificate`) | `Trust anchor not found` ← سوییچ به Fallback بدون اعتبارسنجی [اندازه‌گیری] |

### یادداشت‌های نگهداری و محدودیت‌های شناخته‌شده
- **تاریخ‌های کلیدی:** Leaf تا **`2026-12-28`**، میانی تا **`2039-06-05`**، ریشه تا **`2043-03-16`**.
- دقیقاً مشخص نیست کدام نسخه/پچ‌های اندروید ۱۳ و ۱۴ به بعد ریشه Certum 2018 را به‌طور کامل در مخزن `/system` دارند؛ صرفاً یک دستگاه (اندروید ۱۲) تاکنون به‌صورت کامل اندازه‌گیری شده است.
- وضعیت cross-sign بودن `ctrca.cer` و پشتیبانی سرور از TLS 1.3 نیازمند بررسی تکمیلی در شرایط شبکه پایدار است.
