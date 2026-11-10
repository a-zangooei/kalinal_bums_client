package com.example.data.parser

data class DeltaToken(
    val length: Int,
    val type: String,
    val id: String,
    val content: String
)

data class DeltaResponse(
    val updatePanels: Map<String, String> = emptyMap(),
    val hiddenFields: Map<String, String> = emptyMap(),
    val formAction: String? = null,
    val redirectUrl: String? = null,
    val startupScripts: List<String> = emptyList(),
    val rawTokens: List<DeltaToken> = emptyList(),
    val toastType: String? = null,
    val toastMessage: String? = null,
    val toastTitle: String? = null,
    val isSessionExpired: Boolean = false
)

object DeltaParser {

    private val TOAST_REGEX = Regex(
        """toastify\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"](?:\s*,\s*['"]([^'"]*)['"])?"""
    )

    fun parse(deltaText: String): DeltaResponse {
        val updatePanels = mutableMapOf<String, String>()
        val hiddenFields = mutableMapOf<String, String>()
        val startupScripts = mutableListOf<String>()
        val rawTokens = mutableListOf<DeltaToken>()
        var redirectUrl: String? = null
        var formAction: String? = null

        var pos = 0
        val len = deltaText.length

        while (pos < len) {
            val pipe1 = deltaText.indexOf('|', pos)
            if (pipe1 == -1) break
            val lengthStr = deltaText.substring(pos, pipe1).trim()
            val tokenLen = lengthStr.toIntOrNull()

            if (tokenLen == null) {
                pos = pipe1 + 1
                continue
            }

            val pipe2 = deltaText.indexOf('|', pipe1 + 1)
            if (pipe2 == -1) break
            val type = deltaText.substring(pipe1 + 1, pipe2)

            val pipe3 = deltaText.indexOf('|', pipe2 + 1)
            if (pipe3 == -1) break
            val id = deltaText.substring(pipe2 + 1, pipe3)

            val contentStart = pipe3 + 1
            val expectedEnd = contentStart + tokenLen
            val content: String

            if (expectedEnd <= len && expectedEnd < len && deltaText[expectedEnd] == '|') {
                content = deltaText.substring(contentStart, expectedEnd)
                pos = expectedEnd + 1
            } else {
                val nextTokenPattern = Regex("""\|(\d+)\|([a-zA-Z0-9_#]+)\|""")
                val match = nextTokenPattern.find(deltaText, contentStart)
                if (match != null) {
                    content = deltaText.substring(contentStart, match.range.first)
                    pos = match.range.first + 1
                } else {
                    content = if (contentStart < len) deltaText.substring(contentStart).trimEnd('|') else ""
                    pos = len
                }
            }

            val token = DeltaToken(tokenLen, type, id, content)
            rawTokens.add(token)

            when (type) {
                "updatePanel" -> updatePanels[id] = content
                "hiddenField" -> hiddenFields[id] = content
                "pageRedirect" -> redirectUrl = content
                "formAction" -> formAction = content
                "scriptStartupBlock" -> startupScripts.add(content)
            }
        }

        if (!hiddenFields.containsKey("__VIEWSTATE")) {
            val vsMatch = Regex("""\|hiddenField\|__VIEWSTATE\|([^|]+)\|""").find(deltaText)
            if (vsMatch != null) {
                hiddenFields["__VIEWSTATE"] = vsMatch.groupValues[1]
            }
        }
        if (!hiddenFields.containsKey("__VIEWSTATEGENERATOR")) {
            val vsgMatch = Regex("""\|hiddenField\|__VIEWSTATEGENERATOR\|([^|]+)\|""").find(deltaText)
            if (vsgMatch != null) {
                hiddenFields["__VIEWSTATEGENERATOR"] = vsgMatch.groupValues[1]
            }
        }

        if (!updatePanels.containsKey("UpdatePanel1") && !updatePanels.containsKey("ctl00\$UpdatePanel1")) {
            val upPattern = Regex("""\|updatePanel\|([^|]+)\|([\s\S]*?)(?:\|\d+\|hiddenField\||\z)""")
            val upMatches = upPattern.findAll(deltaText)
            for (match in upMatches) {
                val panelId = match.groupValues[1]
                val panelContent = match.groupValues[2]
                updatePanels[panelId] = panelContent
                if (panelId.endsWith("UpdatePanel1")) {
                    updatePanels["UpdatePanel1"] = panelContent
                    updatePanels["ctl00\$UpdatePanel1"] = panelContent
                }
            }
        }

        // Extract toastify notification
        var extractedToastType: String? = null
        var extractedToastMessage: String? = null
        var extractedToastTitle: String? = null

        // Check startupScripts first, or fallback to searching deltaText
        val scriptsToCheck = startupScripts.ifEmpty { listOf(deltaText) }
        for (script in scriptsToCheck) {
            val match = TOAST_REGEX.find(script)
            if (match != null) {
                extractedToastType = match.groupValues[1].trim()
                extractedToastMessage = match.groupValues[2].trim()
                extractedToastTitle = match.groupValues.getOrNull(3)?.trim()
                break
            }
        }

        val isSessionExpired = redirectUrl?.contains("Login.aspx", ignoreCase = true) == true ||
            (updatePanels.isEmpty() && (deltaText.contains("Login.aspx") || deltaText.contains("txtUsernamePlain")))

        com.example.data.logger.AppLogger.log(
            com.example.data.logger.LogEntry(
                timestamp = "",
                level = com.example.data.logger.LogLevel.DEBUG,
                component = "DeltaParser",
                operation = "ParseDelta",
                aspnet = com.example.data.logger.AspNetDiagnostics(
                    viewStateLength = hiddenFields["__VIEWSTATE"]?.length,
                    viewStateFingerprint = hiddenFields["__VIEWSTATE"]?.let { com.example.data.logger.LogSanitizer.computeFingerprint(it) },
                    viewStateGenerator = hiddenFields["__VIEWSTATEGENERATOR"]
                ),
                message = "Parsed ${rawTokens.size} delta tokens (Panels: ${updatePanels.keys.joinToString()}, Expired: $isSessionExpired, Toast: $extractedToastType)"
            )
        )

        return DeltaResponse(
            updatePanels = updatePanels,
            hiddenFields = hiddenFields,
            formAction = formAction,
            redirectUrl = redirectUrl,
            startupScripts = startupScripts,
            rawTokens = rawTokens,
            toastType = extractedToastType,
            toastMessage = extractedToastMessage,
            toastTitle = extractedToastTitle,
            isSessionExpired = isSessionExpired
        )
    }
}
