package com.example

import com.example.data.repository.UpdateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateRepositoryUnitTest {

    private lateinit var repository: UpdateRepository

    @Before
    fun setUp() {
        repository = UpdateRepository()
    }

    @Test
    fun testIsNewerVersion() {
        assertTrue(repository.isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(repository.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(repository.isNewerVersion("v1.1.0", "1.0.5"))
        assertTrue(repository.isNewerVersion("v2.0.0", "1.9.9"))
        assertTrue(repository.isNewerVersion("1.0.0.1", "1.0.0"))

        assertFalse(repository.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(repository.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(repository.isNewerVersion("v0.9.9", "1.0.0"))
        assertFalse(repository.isNewerVersion("1.0.0", "1.0.1"))
    }

    @Test
    fun testExtractChangelogSnippetWithPersianHeader() {
        val sampleReleaseBody = """
            ## 🍱 کالینان (Kalinal) - نسخه v1.0.1
            کلاینت مدرن، امن و متن‌باز سامانه تغذیه دانشگاه علوم پزشکی بیرجند

            ### 📝 تغییرات این نسخه
            • فعال‌سازی رزرو شام و صبحانه
            • حذف روزهای تعطیل پنجشنبه و جمعه
            • رفع خطای انقضای سشن کاری

            ---

            ### 📲 دانلود مستقیم فایل نصبی (بدون نیاز به آنزیپ)
            فایل‌ها با پسوند apk پیوست شده‌اند.
        """.trimIndent()

        val snippet = repository.extractChangelogSnippet(sampleReleaseBody)

        assertTrue(snippet.contains("فعال‌سازی رزرو شام و صبحانه"))
        assertTrue(snippet.contains("حذف روزهای تعطیل پنجشنبه و جمعه"))
        assertTrue(snippet.contains("رفع خطای انقضای سشن کاری"))
        assertFalse(snippet.contains("دانلود مستقیم فایل نصبی"))
    }

    @Test
    fun testExtractChangelogFallbackOnPlainBulletPoints() {
        val plainBody = """
            Release Notes:
            • Security fixes
            • Better network error handling
        """.trimIndent()

        val snippet = repository.extractChangelogSnippet(plainBody)
        assertTrue(snippet.contains("Security fixes"))
    }
}
