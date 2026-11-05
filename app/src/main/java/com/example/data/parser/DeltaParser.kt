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
    val rawTokens: List<DeltaToken> = emptyList()
)

object DeltaParser {

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

        if (!updatePanels.containsKey("UpdatePanel1")) {
            val upPattern = Regex("""\|updatePanel\|UpdatePanel1\|([\s\S]*?)(?:\|\d+\|hiddenField\||\z)""")
            val upMatch = upPattern.find(deltaText)
            if (upMatch != null) {
                updatePanels["UpdatePanel1"] = upMatch.groupValues[1]
            }
        }

        return DeltaResponse(
            updatePanels = updatePanels,
            hiddenFields = hiddenFields,
            formAction = formAction,
            redirectUrl = redirectUrl,
            startupScripts = startupScripts,
            rawTokens = rawTokens
        )
    }
}
