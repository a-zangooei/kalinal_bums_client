package com.example

import com.example.data.crypto.RsaEncryptor
import com.example.data.logger.AppLogger
import com.example.data.logger.LogContext
import com.example.data.logger.LogLevel
import com.example.data.network.CullinanHttpClient
import com.example.data.parser.DeltaParser
import com.example.data.parser.PageClassifier
import com.example.data.parser.ReservationHtmlParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ComprehensiveLoggingUnitTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        AppLogger.currentMinLogLevel = LogLevel.DEBUG
        AppLogger.clearLogs()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testStepByStepUncensoredHtmlAndDeltaSnapshotsReferencedInLogs() {
        val fullHtmlPage = """
            <!DOCTYPE html>
            <html>
              <body>
                <input type="hidden" name="__VIEWSTATE" value="/wEPDwUKMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA=" />
                <div id="cphMain_lblWeekDate">1405/08/16 - 1405/08/22</div>
                <span id="cphMain_titleBoxValue">کاربر: تست دانشجو - 40112345</span>
                <table id="cphMain_grdReservationLunch"><tr><th>روز</th></tr></table>
              </body>
            </html>
        """.trimIndent()

        val deltaPayload = "1|#||4|updatePanel|UpdatePanel1|<div id=\"cphMain_lblWeekDate\">هفته بعد</div>|10|hiddenField|__VIEWSTATE|NEXT_VS_12|"
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(fullHtmlPage)
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "image/png")
                .setBody(okio.Buffer().write(pngBytes))
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody(deltaPayload)
        )

        val flowId = AppLogger.generateFlowId("e2e")

        // Step 1: Initial HTML GET
        val req1 = Request.Builder()
            .url(server.url("/Reservation/Reservation.aspx"))
            .get()
            .tag(LogContext::class.java, LogContext(flowId, "StepOneHtml"))
            .build()
        val body1 = CullinanHttpClient.executeCall(req1).use { it.body!!.string() }
        assertEquals("Caller must still receive complete untruncated HTML body", fullHtmlPage, body1)

        // Step 2: Binary image captcha (200 OK image/png should not create an HTML snapshot)
        val req2 = Request.Builder()
            .url(server.url("/UserControls/Captcha.ashx"))
            .get()
            .tag(LogContext::class.java, LogContext(flowId, "StepTwoCaptcha"))
            .build()
        val body2 = CullinanHttpClient.executeCall(req2).use { it.body!!.bytes() }
        assertEquals(pngBytes.size, body2.size)

        // Step 3: Delta POST
        val postBody = "__VIEWSTATE=ABC&ctl00%24ScriptManager=ctl00%24UpdatePanel1%7Cctl00%24cphMain%24imgbtnNextWeek"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val req3 = Request.Builder()
            .url(server.url("/Reservation/Reservation.aspx"))
            .post(postBody)
            .tag(LogContext::class.java, LogContext(flowId, "StepThreeDelta"))
            .build()
        val body3 = CullinanHttpClient.executeCall(req3).use { it.body!!.string() }
        assertEquals(deltaPayload, body3)

        // Verify chronological snapshots
        val snapshots = AppLogger.pageSnapshots.value
        assertEquals("Expected 2 text/HTML/Delta snapshots (binary captcha skipped)", 2, snapshots.size)

        val snap1 = snapshots[0]
        val snap2 = snapshots[1]
        assertTrue(snap1.sequence < snap2.sequence)
        assertTrue(snap1.fileName.endsWith(".html"))
        assertEquals(fullHtmlPage, snap1.rawContent) // Uncensored and complete!
        assertTrue(snap1.rawContent.contains("40112345"))
        assertEquals(deltaPayload, snap2.rawContent)

        // Verify LogEntry references each snapshot accurately
        val entries = AppLogger.recentEntries.value
        val step1RespEntry = entries.firstOrNull { it.operation == "StepOneHtml:Response" && it.flowId == flowId }
        val step3RespEntry = entries.firstOrNull { it.operation == "StepThreeDelta:Response" && it.flowId == flowId }
        assertNotNull(step1RespEntry?.snapshot)
        assertEquals(snap1.fileName, step1RespEntry?.snapshot?.fileName)
        assertNotNull(step3RespEntry?.snapshot)
        assertEquals(snap2.fileName, step3RespEntry?.snapshot?.fileName)
    }

    @Test
    fun testServerErrorHtmlPageIsCapturedWithWarnLogLevel() {
        val errorHtml = "<html><body><h1>Server Error in '/' Application.</h1><p>Exception Details</p></body></html>"
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .addHeader("Content-Type", "text/html")
                .setBody(errorHtml)
        )

        val flowId = AppLogger.generateFlowId("err500")
        val req = Request.Builder()
            .url(server.url("/Reservation/Reservation.aspx"))
            .get()
            .tag(LogContext::class.java, LogContext(flowId, "FetchMenuErr"))
            .build()
        CullinanHttpClient.executeCall(req).use { resp ->
            assertEquals(500, resp.code)
        }

        val snap = AppLogger.pageSnapshots.value.lastOrNull()
        assertNotNull(snap)
        assertEquals(500, snap!!.statusCode)
        assertEquals(errorHtml, snap.rawContent)

        val respEntry = AppLogger.recentEntries.value.last { it.flowId == flowId && it.operation == "FetchMenuErr:Response" }
        assertEquals(LogLevel.WARN, respEntry.level)
        assertEquals(snap.fileName, respEntry.snapshot?.fileName)
    }

    @Test
    fun testCrashLoggingAndDiagnosticsZipExport() {
        val testHtml = "<html><body><div>Sample Portal HTML</div></body></html>"
        val snap = AppLogger.savePageSnapshot(
            operation = "TestZipStep",
            method = "GET",
            url = "https://jeton.bums.ac.ir/Reservation/Reservation.aspx",
            statusCode = 200,
            contentType = "text/html",
            rawContent = testHtml,
            flowId = "flw_zip"
        )
        assertNotNull(snap)

        AppLogger.logCrash("TestWorkerThread", IllegalStateException("Simulated Fatal Boom"))

        val fatalEntry = AppLogger.recentEntries.value.lastOrNull { it.level == LogLevel.FATAL }
        assertNotNull(fatalEntry)
        assertEquals("CrashHandler", fatalEntry?.component)
        assertTrue(fatalEntry?.stackTrace?.contains("Simulated Fatal Boom") == true)

        val tempZip = File.createTempFile("kalinan_diag_test", ".zip")
        try {
            val ok = AppLogger.exportDiagnosticsZip(
                destFile = tempZip,
                appVersion = "2.2.0",
                versionCode = 11,
                sslCompatibilityActive = true
            )
            assertTrue(ok)
            assertTrue(tempZip.length() > 0L)

            ZipFile(tempZip).use { zip ->
                assertNotNull(zip.getEntry("manifest.txt"))
                assertNotNull(zip.getEntry("cullinan_events.log"))
                assertNotNull(zip.getEntry("cullinan_events.jsonl"))
                assertNotNull(zip.getEntry("snapshots/index.jsonl"))
                val snapEntry = zip.getEntry("snapshots/${snap!!.fileName}")
                assertNotNull("ZIP must contain raw uncensored snapshot file", snapEntry)
                val zippedHtml = zip.getInputStream(snapEntry).bufferedReader(Charsets.UTF_8).readText()
                assertEquals(testHtml, zippedHtml)

                val manifest = zip.getInputStream(zip.getEntry("manifest.txt")).bufferedReader(Charsets.UTF_8).readText()
                assertTrue(manifest.contains("2.2.0"))
                assertTrue(manifest.contains(snap.fileName))
            }
        } finally {
            tempZip.delete()
        }
    }

    @Test
    fun testParsersAndEncryptorEmitDiagnosticLogs() {
        // 1. Unknown HTML -> WARN
        val unknownRes = PageClassifier.classify("<html><body><div>Unexpected portal layout</div></body></html>", 200, flowId = "flw_cls")
        assertEquals(PageClassifier.TYPE_UNKNOWN, unknownRes.pageType)

        // 2. Malformed Delta -> WARN
        DeltaParser.parse("corrupted|notanumber|broken", flowId = "flw_dlt")

        // 3. ReservationHtmlParser missing tables -> WARN
        ReservationHtmlParser.parse("<html><body><div id='cphMain_alertBox'><span id='cphMain_alertBoxValue'>خطای تست سرور</span></div></body></html>", flowId = "flw_prs")

        // 4. RsaEncryptor invalid PEM -> ERROR
        try {
            RsaEncryptor.encryptWithPem("secret", "INVALID_PEM")
        } catch (_: Exception) {
        }

        val entries = AppLogger.recentEntries.value
        assertTrue(entries.any { it.component == "PageClassifier" && it.level == LogLevel.WARN })
        assertTrue(entries.any { it.component == "DeltaParser" && it.level == LogLevel.WARN })
        assertTrue(entries.any { it.component == "Parser" && it.operation == "VisibleServerAlert" && it.level == LogLevel.WARN })
        assertTrue(entries.any { it.component == "RsaEncryptor" && it.level == LogLevel.ERROR })
        assertFalse("Plaintext RSA input must never be logged", entries.any { it.message.contains("secret") })
    }
}
