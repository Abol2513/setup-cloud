package com.example.data

import com.example.model.BindingType
import com.example.model.WorkerEditorState

object WorkerCodeUtils {

    private val workerNameRegex = Regex("^[a-z0-9][a-z0-9_-]{0,62}$")
    private val compatDateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val bindingVarRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val exportDefaultRegex = Regex("\\bexport\\s+default\\b")
    private val exportDeclRegex = Regex("\\bexport\\s+(async\\s+function|function|const|let|var|class)\\b")
    private val addEventListenerFetchRegex = Regex("addEventListener\\s*\\(\\s*['\"]fetch['\"]")
    private val staticImportRegex = Regex("(^|[;\\n])\\s*import\\s+(?:[^'\"\\n]+\\s+from\\s+)?['\"][^'\"]+['\"]")

    /**
     * Validates worker name, code, compatibility date, and all configured bindings
     * before issuing a multipart upload to Cloudflare Workers API.
     * Returns null when valid, or a clear Persian validation message.
     */
    fun validateWorkerUpload(editor: WorkerEditorState): String? {
        val scriptName = editor.scriptName.trim()
        if (scriptName.isBlank()) {
            return "نام ورکر نمی‌تواند خالی باشد."
        }
        if (!workerNameRegex.matches(scriptName)) {
            return "نام ورکر باید با حرف کوچک انگلیسی یا عدد شروع شود و فقط شامل a-z، 0-9، خط تیره (-) یا _ باشد (حداکثر ۶۳ کاراکتر)."
        }
        if (editor.code.isBlank()) {
            return "کد ورکر نمی‌تواند خالی باشد."
        }
        val compat = editor.compatibilityDate.trim()
        if (compat.isNotBlank() && !compatDateRegex.matches(compat)) {
            return "فرمت Compatibility Date نامعتبر است. نمونه صحیح: 2026-11-15"
        }

        val seenNames = mutableSetOf<String>()
        for (binding in editor.bindings) {
            val varName = binding.name.trim()
            if (varName.isBlank()) {
                return "نام متغیر برای یکی از بایندینگ‌ها خالی است."
            }
            if (!bindingVarRegex.matches(varName)) {
                return "نام متغیر بایندینگ «$varName» معتبر نیست (فقط حروف انگلیسی، عدد و _ مجاز است)."
            }
            if (!seenNames.add(varName)) {
                return "نام بایندینگ «$varName» تکراری است."
            }
            val missingReason = when (binding.type) {
                BindingType.PLAIN_TEXT -> null
                BindingType.AI -> null
                BindingType.D1 -> if (binding.databaseId.isBlank()) "شناسه Database UUID برای بایندینگ D1 «$varName» وارد نشده است." else null
                BindingType.KV -> if (binding.namespaceId.isBlank()) "شناسه Namespace ID برای بایندینگ KV «$varName» وارد نشده است." else null
                BindingType.R2 -> if (binding.bucketName.isBlank()) "نام باکت R2 برای بایندینگ «$varName» وارد نشده است." else null
                BindingType.QUEUE -> if (binding.queueName.isBlank()) "نام صف (Queue Name) برای بایندینگ «$varName» وارد نشده است." else null
                BindingType.DURABLE_OBJECT -> if (binding.className.isBlank()) "نام کلاس (Class Name) برای Durable Object «$varName» وارد نشده است." else null
                BindingType.HYPERDRIVE -> if (binding.hyperdriveId.isBlank()) "شناسه Hyperdrive برای بایندینگ «$varName» وارد نشده است." else null
                BindingType.ANALYTICS_ENGINE -> if (binding.dataset.isBlank()) "نام Dataset برای بایندینگ Analytics Engine «$varName» وارد نشده است." else null
                BindingType.SERVICE -> if (binding.serviceName.isBlank()) "نام ورکر مقصد برای Service Binding «$varName» وارد نشده است." else null
            }
            if (missingReason != null) return missingReason
        }
        return null
    }

    /**
     * Replaces comments and string/template contents with spaces while preserving newlines,
     * so syntax checks never match keywords or braces inside comments or literals.
     */
    fun maskCommentsAndStrings(source: String): String {
        val sb = StringBuilder(source.length)
        var i = 0
        val n = source.length
        while (i < n) {
            val c = source[i]
            val next = if (i + 1 < n) source[i + 1] else '\u0000'
            when {
                // Single-line comment
                c == '/' && next == '/' -> {
                    sb.append("  ")
                    i += 2
                    while (i < n && source[i] != '\n') {
                        sb.append(' ')
                        i++
                    }
                }
                // Multi-line comment
                c == '/' && next == '*' -> {
                    sb.append("  ")
                    i += 2
                    while (i < n) {
                        if (source[i] == '*' && i + 1 < n && source[i + 1] == '/') {
                            sb.append("  ")
                            i += 2
                            break
                        }
                        sb.append(if (source[i] == '\n') '\n' else ' ')
                        i++
                    }
                }
                // Single / Double / Template string literal
                c == '\'' || c == '"' || c == '`' -> {
                    val quote = c
                    sb.append(quote)
                    i++
                    while (i < n) {
                        val sc = source[i]
                        if (sc == '\\' && i + 1 < n) {
                            sb.append(' ')
                            sb.append(if (source[i + 1] == '\n') '\n' else ' ')
                            i += 2
                            continue
                        }
                        if (sc == quote) {
                            sb.append(quote)
                            i++
                            break
                        }
                        sb.append(if (sc == '\n') '\n' else ' ')
                        i++
                    }
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    fun isServiceWorkerSyntax(code: String): Boolean {
        val masked = maskCommentsAndStrings(code)
        val hasExportDefault = exportDefaultRegex.containsMatchIn(masked)
        val hasFetchListener = addEventListenerFetchRegex.findAll(code).any { match ->
            masked.getOrNull(match.range.first) == 'a'
        }
        return !hasExportDefault && hasFetchListener
    }

    fun hasStaticEsmImports(code: String): Boolean {
        val masked = maskCommentsAndStrings(code)
        return staticImportRegex.containsMatchIn(masked)
    }

    /**
     * Transforms ES module exports into a local sandbox-compatible script
     * without corrupting comments or string literals.
     */
    fun transformModuleForLocalSandbox(source: String): String {
        val masked = maskCommentsAndStrings(source)
        val replacements = mutableListOf<Triple<Int, Int, String>>()

        exportDefaultRegex.findAll(masked).forEach { m ->
            replacements.add(Triple(m.range.first, m.range.last + 1, "const __setupCloudDefaultExport = "))
        }
        exportDeclRegex.findAll(masked).forEach { m ->
            val declPart = source.substring(m.groups[1]!!.range.first, m.groups[1]!!.range.last + 1)
            replacements.add(Triple(m.range.first, m.range.last + 1, declPart))
        }

        if (replacements.isEmpty()) return source
        replacements.sortBy { it.first }

        val sb = StringBuilder(source.length + 32)
        var cursor = 0
        for ((start, end, rep) in replacements) {
            if (start < cursor) continue
            sb.append(source, cursor, start)
            sb.append(rep)
            cursor = end
        }
        if (cursor < source.length) {
            sb.append(source, cursor, source.length)
        }
        return sb.toString()
    }

    /**
     * Quote- and comment-aware JavaScript indentation formatter.
     */
    fun formatJavaScript(source: String): String {
        val normalized = source.replace("\r\n", "\n")
        val rawLines = normalized.lines()
        val maskedLines = maskCommentsAndStrings(normalized).lines()

        val sb = StringBuilder(normalized.length + 64)
        var indent = 0
        for (idx in rawLines.indices) {
            val trimmed = rawLines[idx].trim()
            if (trimmed.isEmpty()) {
                sb.append('\n')
                continue
            }
            val maskedTrimmed = maskedLines.getOrElse(idx) { "" }.trim()
            val startsClosing = maskedTrimmed.startsWith("}") ||
                maskedTrimmed.startsWith("]") ||
                maskedTrimmed.startsWith(")")

            val lineIndent = if (startsClosing) maxOf(0, indent - 1) else indent
            sb.append("  ".repeat(lineIndent)).append(trimmed).append('\n')

            var delta = 0
            for (ch in maskedTrimmed) {
                when (ch) {
                    '{', '[' -> delta++
                    '}', ']' -> delta--
                }
            }
            indent = maxOf(0, indent + delta)
        }
        return sb.toString().trimEnd()
    }
}
