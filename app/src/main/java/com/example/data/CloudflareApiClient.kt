package com.example.data

import com.example.model.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class CloudflareApiClient(
    private val baseApiUrl: String = "https://api.cloudflare.com/client/v4",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val wsClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val jsModuleMediaType = "application/javascript+module".toMediaType()
    private val plainTextMediaType = "text/plain; charset=utf-8".toMediaType()

    private fun baseUrl(path: String): String = "${baseApiUrl.trimEnd('/')}$path"

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private suspend fun executeJson(
        token: String,
        method: String,
        path: String,
        bodyJson: String? = null,
        rawBody: RequestBody? = null
    ): ApiResult<JSONObject> = withContext(ioDispatcher) {
        try {
            val requestBody = rawBody ?: bodyJson?.toRequestBody(jsonMediaType)
            val builder = Request.Builder()
                .url(baseUrl(path))
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/json")

            when (method.uppercase()) {
                "GET" -> builder.get()
                "POST" -> builder.post(requestBody ?: "".toRequestBody(jsonMediaType))
                "PUT" -> builder.put(requestBody ?: "".toRequestBody(jsonMediaType))
                "PATCH" -> builder.patch(requestBody ?: "".toRequestBody(jsonMediaType))
                "DELETE" -> {
                    if (requestBody != null) builder.delete(requestBody) else builder.delete()
                }
                else -> builder.method(method, requestBody)
            }

            client.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Error(parseErrorResponse(response.code, raw, path))
                }
                val json = if (raw.isBlank()) JSONObject().put("success", true) else JSONObject(raw)
                if (json.has("success") && !json.optBoolean("success", true)) {
                    return@withContext ApiResult.Error(parseErrorResponse(response.code, raw, path))
                }
                ApiResult.Success(json)
            }
        } catch (e: IOException) {
            ApiResult.Error(
                ApiErrorInfo(
                    httpStatus = 0,
                    message = e.message ?: "Network error",
                    persianSummary = "خطا در برقراری ارتباط شبکه با سرورهای کلودفلر. اتصال اینترنت را بررسی کنید.",
                    rawDetail = e.stackTraceToString().take(800)
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(
                ApiErrorInfo(
                    httpStatus = 0,
                    message = e.message ?: "Unexpected error",
                    persianSummary = "خطای غیرمنتظره در پردازش پاسخ کلودفلر.",
                    rawDetail = e.toString()
                )
            )
        }
    }

    private fun parseErrorResponse(httpStatus: Int, rawBody: String, path: String): ApiErrorInfo {
        var code: Int? = null
        var message = "HTTP $httpStatus"
        try {
            val json = JSONObject(rawBody)
            val errors = json.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val collected = mutableListOf<String>()
                for (i in 0 until errors.length()) {
                    val errObj = errors.optJSONObject(i) ?: continue
                    if (i == 0 && errObj.has("code")) code = errObj.optInt("code")
                    val m = errObj.optString("message", "").trim()
                    if (m.isNotEmpty() && m !in collected) collected.add(m)
                }
                if (collected.isNotEmpty()) {
                    message = collected.joinToString(" | ")
                }
            } else if (json.has("message")) {
                message = json.optString("message", message)
            }
        } catch (_: Exception) {
            if (rawBody.isNotBlank()) message = rawBody.take(250)
        }

        val isPermissionIssue = httpStatus == 403 || httpStatus == 401 || code == 10000 || code == 9109 ||
            message.contains("Authentication", ignoreCase = true) ||
            message.contains("permission", ignoreCase = true) ||
            message.contains("unauthorized", ignoreCase = true)

        val scopeHint = when {
            path.contains("/d1/") -> "D1: Edit"
            path.contains("/storage/kv/") -> "Workers KV Storage: Edit"
            path.contains("/r2/") -> "Workers R2 Storage: Edit"
            path.contains("/queues") -> "Queues: Edit"
            path.contains("/zones") -> "Zone / Workers Routes: Edit"
            path.contains("/workers/") -> "Workers Scripts: Edit"
            else -> null
        }

        val persianHint = when {
            isPermissionIssue && scopeHint != null && (httpStatus == 403 || code == 10000 || code == 9109 ||
                message.contains("permission", ignoreCase = true) || message.contains("Authentication", ignoreCase = true)) -> {
                "توکن فعلی مجوز لازم را ندارد. لطفاً در پنل کلودفلر دسترسی «$scopeHint» را به توکن اضافه کنید ($message)."
            }
            httpStatus == 401 || code == 9109 || code == 10001 -> {
                "توکن API نامعتبر یا منقضی است. لطفاً توکن و Account ID را بررسی کنید."
            }
            httpStatus == 403 || code == 10000 -> {
                "عدم دسترسی (403): توکن فعلی مجوز انجام این عملیات را ندارد ($message)."
            }
            httpStatus == 404 || code == 10007 -> {
                "منبع درخواستی در این اکانت یافت نشد ($message)."
            }
            httpStatus == 429 -> {
                "محدودیت نرخ درخواست کلودفلر (429). چند لحظه بعد دوباره تلاش کنید."
            }
            else -> "خطای کلودفلر (کد ${code ?: httpStatus}): $message"
        }

        return ApiErrorInfo(
            httpStatus = httpStatus,
            code = code,
            message = message,
            persianSummary = persianHint,
            rawDetail = rawBody.ifBlank { "HTTP $httpStatus ($path)" }
        )
    }

    // === Account Verification & Subdomain ===

    suspend fun verifyToken(accountId: String, token: String): ApiResult<String> {
        val cleanAccountId = accountId.trim()
        val primaryRes = executeJson(token, "GET", "/accounts/$cleanAccountId/tokens/verify")
        if (primaryRes is ApiResult.Success) {
            val status = primaryRes.data.optJSONObject("result")?.optString("status", "active") ?: "active"
            return ApiResult.Success(status)
        }
        val err = (primaryRes as ApiResult.Error).error
        // Account-owned tokens verify at /accounts/{id}/tokens/verify, while User API tokens verify at /user/tokens/verify
        val shouldTryUserFallback = err.httpStatus != 0 &&
            (err.httpStatus in setOf(400, 403, 404) || err.code == 10000)
        if (!shouldTryUserFallback) return primaryRes

        return when (val userRes = executeJson(token, "GET", "/user/tokens/verify")) {
            is ApiResult.Success -> {
                val status = userRes.data.optJSONObject("result")?.optString("status", "active") ?: "active"
                ApiResult.Success(status)
            }
            is ApiResult.Error -> userRes
        }
    }

    suspend fun getWorkersSubdomain(accountId: String, token: String): ApiResult<String> {
        return when (val res = executeJson(token, "GET", "/accounts/${accountId.trim()}/workers/subdomain")) {
            is ApiResult.Success -> {
                val sub = res.data.optJSONObject("result")?.optString("subdomain", "").orEmpty()
                ApiResult.Success(sub)
            }
            is ApiResult.Error -> {
                if (res.error.httpStatus == 404 || res.error.code == 10007) {
                    ApiResult.Success("")
                } else {
                    res
                }
            }
        }
    }

    suspend fun registerWorkersSubdomain(
        accountId: String,
        token: String,
        subdomain: String
    ): ApiResult<String> {
        val body = JSONObject().put("subdomain", subdomain.trim().lowercase()).toString()
        return when (val res = executeJson(token, "PUT", "/accounts/$accountId/workers/subdomain", body)) {
            is ApiResult.Success -> {
                val sub = res.data.optJSONObject("result")?.optString("subdomain", subdomain).orEmpty()
                ApiResult.Success(sub)
            }
            is ApiResult.Error -> res
        }
    }

    // === Tab 1: Workers Scripts ===

    suspend fun listWorkers(accountId: String, token: String): ApiResult<List<WorkerScriptSummary>> {
        return when (val res = executeJson(token, "GET", "/accounts/$accountId/workers/scripts")) {
            is ApiResult.Success -> {
                val arr = res.data.optJSONArray("result") ?: JSONArray()
                val items = mutableListOf<WorkerScriptSummary>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    items.add(
                        WorkerScriptSummary(
                            id = o.optString("id"),
                            modifiedOn = o.optString("modified_on", ""),
                            createdOn = o.optString("created_on", ""),
                            usageModel = o.optString("usage_model", "")
                        )
                    )
                }
                ApiResult.Success(items.sortedByDescending { it.modifiedOn })
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun getWorkerScriptCode(
        accountId: String,
        token: String,
        scriptName: String
    ): ApiResult<String> = withContext(ioDispatcher) {
        try {
            val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}"
            val request = Request.Builder()
                .url(baseUrl(path))
                .header("Authorization", "Bearer ${token.trim()}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Error(parseErrorResponse(response.code, raw, path))
                }
                val contentType = response.header("Content-Type").orEmpty()
                val extractedCode = if (contentType.contains("multipart/form-data", ignoreCase = true)) {
                    extractJsFromMultipartResponse(contentType, raw)
                } else {
                    raw
                }
                ApiResult.Success(extractedCode)
            }
        } catch (e: Exception) {
            ApiResult.Error(
                ApiErrorInfo(
                    message = e.message ?: "Error loading script",
                    persianSummary = "خطا در دریافت سورس کد ورکر از کلودفلر.",
                    rawDetail = e.toString()
                )
            )
        }
    }

    private fun extractJsFromMultipartResponse(contentType: String, raw: String): String {
        val boundaryMatch = Regex("boundary=([^;\\s]+)", RegexOption.IGNORE_CASE).find(contentType)
        val boundary = boundaryMatch?.groupValues?.getOrNull(1)?.trim('"')
        if (boundary.isNullOrBlank()) {
            return raw
        }
        val delimiter = "--$boundary"
        val parts = raw.split(delimiter)
        val jsCandidates = mutableListOf<Pair<String, String>>()

        for (part in parts) {
            val trimmed = part.trimStart('\r', '\n')
            if (trimmed.isBlank() || trimmed.startsWith("--")) continue
            val headerEndIdx = trimmed.indexOf("\r\n\r\n").let { idx ->
                if (idx >= 0) idx to 4 else {
                    val lfIdx = trimmed.indexOf("\n\n")
                    if (lfIdx >= 0) lfIdx to 2 else -1 to 0
                }
            }
            if (headerEndIdx.first < 0) continue
            val headersBlock = trimmed.substring(0, headerEndIdx.first)
            var body = trimmed.substring(headerEndIdx.first + headerEndIdx.second)
            if (body.endsWith("\r\n")) body = body.dropLast(2)
            else if (body.endsWith("\n")) body = body.dropLast(1)

            val isJsContent = headersBlock.contains("javascript", ignoreCase = true) ||
                headersBlock.contains(".js", ignoreCase = true) ||
                headersBlock.contains(".mjs", ignoreCase = true)
            val isMeta = headersBlock.contains("name=\"metadata\"", ignoreCase = true) ||
                headersBlock.contains("application/json", ignoreCase = true)

            if (!isMeta && (isJsContent || headersBlock.contains("filename=", ignoreCase = true))) {
                jsCandidates.add(headersBlock to body)
            }
        }

        val preferred = jsCandidates.firstOrNull { (hdr, _) ->
            hdr.contains("worker.js", ignoreCase = true) || hdr.contains("index.js", ignoreCase = true)
        } ?: jsCandidates.firstOrNull()

        return preferred?.second ?: raw
    }

    suspend fun deleteWorker(accountId: String, token: String, scriptName: String): ApiResult<Unit> {
        return when (
            val res = executeJson(
                token,
                "DELETE",
                "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}?force=true"
            )
        ) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    // === Tab 2: Multipart Version Upload + Subdomain Enable + Deploy + Rollback ===

    private fun isServiceWorkerSyntax(code: String): Boolean {
        return WorkerCodeUtils.isServiceWorkerSyntax(code)
    }

    private fun buildWorkerMultipartBody(editor: WorkerEditorState): RequestBody {
        val bindingsArray = JSONArray()
        editor.bindings.forEach { b ->
            val obj = JSONObject()
            obj.put("type", b.type.apiType)
            obj.put("name", b.name.trim())
            when (b.type) {
                BindingType.PLAIN_TEXT -> obj.put("text", b.textValue)
                BindingType.D1 -> obj.put("database_id", b.databaseId.trim())
                BindingType.KV -> obj.put("namespace_id", b.namespaceId.trim())
                BindingType.R2 -> obj.put("bucket_name", b.bucketName.trim())
                BindingType.QUEUE -> obj.put("queue_name", b.queueName.trim())
                BindingType.DURABLE_OBJECT -> {
                    obj.put("class_name", b.className.trim())
                    if (b.scriptName.isNotBlank()) obj.put("script_name", b.scriptName.trim())
                }
                BindingType.AI -> Unit
                BindingType.HYPERDRIVE -> obj.put("id", b.hyperdriveId.trim())
                BindingType.ANALYTICS_ENGINE -> obj.put("dataset", b.dataset.trim())
                BindingType.SERVICE -> obj.put("service", b.serviceName.trim())
            }
            bindingsArray.put(obj)
        }

        val flagsArray = JSONArray()
        if (editor.nodejsCompat) flagsArray.put("nodejs_compat")

        val serviceWorkerMode = isServiceWorkerSyntax(editor.code)
        val metadataJson = JSONObject().apply {
            if (serviceWorkerMode) {
                put("body_part", "worker.js")
            } else {
                put("main_module", "worker.js")
            }
            put("compatibility_date", editor.compatibilityDate.trim().ifBlank { "2026-11-15" })
            if (flagsArray.length() > 0) {
                put("compatibility_flags", flagsArray)
            }
            put("bindings", bindingsArray)
            if (editor.smartPlacement) {
                put("placement", JSONObject().put("mode", "smart"))
            }
            put("observability", JSONObject().put("enabled", editor.observabilityEnabled))
        }

        val scriptMediaType = if (serviceWorkerMode) {
            "application/javascript".toMediaType()
        } else {
            jsModuleMediaType
        }

        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata",
                null,
                metadataJson.toString().toRequestBody("application/json".toMediaType())
            )
            .addFormDataPart(
                "worker.js",
                "worker.js",
                editor.code.toRequestBody(scriptMediaType)
            )
            .build()
    }

    suspend fun uploadWorkerVersionSafe(
        accountId: String,
        token: String,
        editor: WorkerEditorState
    ): ApiResult<String> {
        val validationError = WorkerCodeUtils.validateWorkerUpload(editor)
        if (validationError != null) {
            return ApiResult.Error(
                ApiErrorInfo(
                    httpStatus = 400,
                    message = "Pre-upload validation failed",
                    persianSummary = validationError
                )
            )
        }
        val scriptEncoded = encodeSegment(editor.scriptName.trim())
        val multipart = buildWorkerMultipartBody(editor)
        val versionsPath = "/accounts/$accountId/workers/scripts/$scriptEncoded/versions"

        return when (val res = executeJson(token, "POST", versionsPath, rawBody = multipart)) {
            is ApiResult.Success -> {
                val versionId = res.data.optJSONObject("result")?.optString("id", "").orEmpty()
                ensureWorkerSubdomainRouting(accountId, token, editor.scriptName.trim(), true, true)
                ApiResult.Success(versionId)
            }
            is ApiResult.Error -> {
                if (res.error.httpStatus == 404 || res.error.code == 10007) {
                    val putMultipart = buildWorkerMultipartBody(editor)
                    val putPath = "/accounts/$accountId/workers/scripts/$scriptEncoded"
                    when (val putRes = executeJson(token, "PUT", putPath, rawBody = putMultipart)) {
                        is ApiResult.Success -> {
                            val resultObj = putRes.data.optJSONObject("result")
                            var versionId = resultObj?.optString("latest_version_id", "").orEmpty()
                            if (versionId.isBlank()) {
                                versionId = resultObj?.optString("id", "").orEmpty()
                            }
                            if (versionId.isBlank() || versionId == editor.scriptName.trim()) {
                                val listed = listVersions(accountId, token, editor.scriptName.trim())
                                if (listed is ApiResult.Success) {
                                    versionId = listed.data.firstOrNull()?.id.orEmpty()
                                }
                            }
                            ensureWorkerSubdomainRouting(accountId, token, editor.scriptName.trim(), true, true)
                            ApiResult.Success(versionId)
                        }
                        is ApiResult.Error -> putRes
                    }
                } else {
                    res
                }
            }
        }
    }

    suspend fun ensureWorkerSubdomainRouting(
        accountId: String,
        token: String,
        scriptName: String,
        enabled: Boolean,
        previewsEnabled: Boolean
    ): ApiResult<ScriptSubdomainStatus> {
        val body = JSONObject()
            .put("enabled", enabled)
            .put("previews_enabled", previewsEnabled)
            .toString()
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/subdomain"
        return when (val res = executeJson(token, "POST", path, body)) {
            is ApiResult.Success -> {
                val r = res.data.optJSONObject("result")
                ApiResult.Success(
                    ScriptSubdomainStatus(
                        enabled = r?.optBoolean("enabled", enabled) ?: enabled,
                        previewsEnabled = r?.optBoolean("previews_enabled", previewsEnabled) ?: previewsEnabled
                    )
                )
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun getWorkerSubdomainRouting(
        accountId: String,
        token: String,
        scriptName: String
    ): ApiResult<ScriptSubdomainStatus> {
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/subdomain"
        return when (val res = executeJson(token, "GET", path)) {
            is ApiResult.Success -> {
                val r = res.data.optJSONObject("result")
                ApiResult.Success(
                    ScriptSubdomainStatus(
                        enabled = r?.optBoolean("enabled", false) ?: false,
                        previewsEnabled = r?.optBoolean("previews_enabled", false) ?: false
                    )
                )
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun deployVersions(
        accountId: String,
        token: String,
        scriptName: String,
        allocations: List<DeploymentVersionAllocation>
    ): ApiResult<WorkerDeploymentSummary> {
        val versionsArr = JSONArray()
        allocations.forEach { alloc ->
            versionsArr.put(
                JSONObject()
                    .put("version_id", alloc.versionId)
                    .put("percentage", alloc.percentage)
            )
        }
        val body = JSONObject()
            .put("strategy", "percentage")
            .put("versions", versionsArr)
            .toString()

        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/deployments"
        return when (val res = executeJson(token, "POST", path, body)) {
            is ApiResult.Success -> {
                val r = res.data.optJSONObject("result")
                ApiResult.Success(parseDeployment(r))
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun listVersions(
        accountId: String,
        token: String,
        scriptName: String
    ): ApiResult<List<WorkerVersionSummary>> {
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/versions"
        return when (val res = executeJson(token, "GET", path)) {
            is ApiResult.Success -> {
                val resultObj = res.data.optJSONObject("result")
                val itemsArr = resultObj?.optJSONArray("items")
                    ?: res.data.optJSONArray("result")
                    ?: JSONArray()
                val list = mutableListOf<WorkerVersionSummary>()
                for (i in 0 until itemsArr.length()) {
                    val o = itemsArr.optJSONObject(i) ?: continue
                    val meta = o.optJSONObject("metadata")
                    list.add(
                        WorkerVersionSummary(
                            id = o.optString("id"),
                            number = o.optInt("number", 0),
                            createdOn = meta?.optString("created_on", o.optString("created_on", "")) ?: "",
                            source = meta?.optString("source", o.optString("source", "")) ?: "",
                            authorEmail = meta?.optString("author_email", "") ?: ""
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun getWorkerVersionDetail(
        accountId: String,
        token: String,
        scriptName: String,
        versionId: String
    ): ApiResult<WorkerVersionDetail> {
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/versions/${encodeSegment(versionId)}"
        return when (val res = executeJson(token, "GET", path)) {
            is ApiResult.Success -> {
                val r = res.data.optJSONObject("result") ?: JSONObject()
                val resources = r.optJSONObject("resources") ?: r
                val compatDate = resources.optString("compatibility_date", r.optString("compatibility_date", "2026-11-15"))
                    .ifBlank { "2026-11-15" }
                val flagsArr = resources.optJSONArray("compatibility_flags") ?: r.optJSONArray("compatibility_flags")
                var nodejsCompat = false
                if (flagsArr != null) {
                    for (i in 0 until flagsArr.length()) {
                        if (flagsArr.optString(i) == "nodejs_compat") nodejsCompat = true
                    }
                }
                val placementObj = resources.optJSONObject("placement") ?: r.optJSONObject("placement")
                val smartPlacement = placementObj?.optString("mode") == "smart"
                val obsObj = resources.optJSONObject("observability") ?: r.optJSONObject("observability")
                val obsEnabled = obsObj?.optBoolean("enabled", true) ?: true

                val bindingsArr = resources.optJSONArray("bindings") ?: r.optJSONArray("bindings") ?: JSONArray()
                val parsedBindings = mutableListOf<WorkerBinding>()
                for (i in 0 until bindingsArr.length()) {
                    val b = bindingsArr.optJSONObject(i) ?: continue
                    val apiType = b.optString("type")
                    val name = b.optString("name")
                    val type = BindingType.entries.firstOrNull { it.apiType == apiType } ?: continue
                    parsedBindings.add(
                        WorkerBinding(
                            uid = UUID.randomUUID().toString(),
                            type = type,
                            name = name,
                            textValue = b.optString("text", ""),
                            databaseId = b.optString("database_id", ""),
                            namespaceId = b.optString("namespace_id", ""),
                            bucketName = b.optString("bucket_name", ""),
                            queueName = b.optString("queue_name", ""),
                            className = b.optString("class_name", ""),
                            scriptName = b.optString("script_name", ""),
                            hyperdriveId = b.optString("id", ""),
                            dataset = b.optString("dataset", ""),
                            serviceName = b.optString("service", "")
                        )
                    )
                }

                ApiResult.Success(
                    WorkerVersionDetail(
                        id = r.optString("id", versionId),
                        compatibilityDate = compatDate,
                        nodejsCompat = nodejsCompat,
                        smartPlacement = smartPlacement,
                        observabilityEnabled = obsEnabled,
                        bindings = parsedBindings
                    )
                )
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun listDeployments(
        accountId: String,
        token: String,
        scriptName: String
    ): ApiResult<List<WorkerDeploymentSummary>> {
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/deployments"
        return when (val res = executeJson(token, "GET", path)) {
            is ApiResult.Success -> {
                val resultObj = res.data.optJSONObject("result")
                val arr = resultObj?.optJSONArray("deployments")
                    ?: res.data.optJSONArray("result")
                    ?: JSONArray()
                val list = mutableListOf<WorkerDeploymentSummary>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(parseDeployment(o))
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    private fun parseDeployment(obj: JSONObject?): WorkerDeploymentSummary {
        if (obj == null) return WorkerDeploymentSummary(id = "")
        val allocs = mutableListOf<DeploymentVersionAllocation>()
        val vers = obj.optJSONArray("versions") ?: JSONArray()
        for (i in 0 until vers.length()) {
            val v = vers.optJSONObject(i) ?: continue
            allocs.add(
                DeploymentVersionAllocation(
                    versionId = v.optString("version_id"),
                    percentage = v.optInt("percentage", 100)
                )
            )
        }
        return WorkerDeploymentSummary(
            id = obj.optString("id"),
            createdOn = obj.optString("created_on", ""),
            strategy = obj.optString("strategy", "percentage"),
            allocations = allocs
        )
    }

    // === Built-in Live HTTP Request Tester ===

    suspend fun sendLiveHttpRequest(
        fullUrl: String,
        method: String,
        headers: List<HttpHeaderEntry>,
        bodyText: String
    ): ApiResult<HttpTestResponse> = withContext(ioDispatcher) {
        try {
            val builder = Request.Builder().url(fullUrl.trim())
            var explicitContentType: String? = null
            headers.forEach { h ->
                val k = h.key.trim()
                if (k.isNotEmpty()) {
                    if (k.equals("Content-Type", ignoreCase = true)) {
                        explicitContentType = h.value.trim()
                    }
                    builder.addHeader(k, h.value)
                }
            }
            val upper = method.uppercase()
            val reqBody = if (upper == "GET" || upper == "HEAD") {
                null
            } else {
                val mediaType = (explicitContentType ?: "application/json; charset=utf-8").toMediaType()
                bodyText.toRequestBody(mediaType)
            }
            when (upper) {
                "GET" -> builder.get()
                "POST" -> builder.post(reqBody ?: "".toRequestBody(jsonMediaType))
                "PUT" -> builder.put(reqBody ?: "".toRequestBody(jsonMediaType))
                "PATCH" -> builder.patch(reqBody ?: "".toRequestBody(jsonMediaType))
                "DELETE" -> if (reqBody != null) builder.delete(reqBody) else builder.delete()
                else -> builder.method(upper, reqBody)
            }

            val startNs = System.nanoTime()
            client.newCall(builder.build()).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                val respHeaders = mutableListOf<Pair<String, String>>()
                for (i in 0 until resp.headers.size) {
                    respHeaders.add(resp.headers.name(i) to resp.headers.value(i))
                }
                val prettyBody = tryFormatJson(rawBody)
                ApiResult.Success(
                    HttpTestResponse(
                        statusCode = resp.code,
                        statusMessage = resp.message,
                        latencyMs = elapsedMs,
                        headers = respHeaders,
                        body = prettyBody
                    )
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(
                ApiErrorInfo(
                    message = e.message ?: "Request failed",
                    persianSummary = "ارسال درخواست HTTP با خطا مواجه شد: ${e.message ?: "آدرس نامعتبر یا خطای شبکه"}",
                    rawDetail = e.toString()
                )
            )
        }
    }

    suspend fun fetchRawCodeFromUrl(rawInputUrl: String): ApiResult<String> = withContext(ioDispatcher) {
        try {
            var target = rawInputUrl.trim()
            if (target.isBlank()) {
                return@withContext ApiResult.Error(
                    ApiErrorInfo(
                        message = "Empty URL",
                        persianSummary = "لینک فایل کد خالی است."
                    )
                )
            }
            if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
                target = "https://$target"
            }
            // Convert GitHub file view links (/blob/) automatically to raw.githubusercontent.com
            val githubBlobRegex = Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$", RegexOption.IGNORE_CASE)
            githubBlobRegex.matchEntire(target)?.let { m ->
                target = "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}"
            }

            val req = Request.Builder().url(target).get().build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext ApiResult.Error(
                        ApiErrorInfo(
                            httpStatus = resp.code,
                            message = resp.message,
                            persianSummary = "دریافت کد از لینک ناموفق بود (HTTP ${resp.code}).",
                            rawDetail = text.take(1000)
                        )
                    )
                }
                if (text.length > 2_000_000) {
                    return@withContext ApiResult.Error(
                        ApiErrorInfo(
                            message = "File too large",
                            persianSummary = "حجم فایل دریافتی بیش از ۲ مگابایت است و در ادیتور پشتیبانی نمی‌شود."
                        )
                    )
                }
                ApiResult.Success(text)
            }
        } catch (e: Exception) {
            ApiResult.Error(
                ApiErrorInfo(
                    message = e.message ?: "Fetch failed",
                    persianSummary = "خطا در دریافت کد از لینک: ${e.message ?: "آدرس نامعتبر"}",
                    rawDetail = e.toString()
                )
            )
        }
    }

    // === Tail Logs (REST + WebSocket trace-v1) ===

    suspend fun createTailSession(
        accountId: String,
        token: String,
        scriptName: String
    ): ApiResult<Pair<String, String>> {
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/tails"
        return when (val res = executeJson(token, "POST", path, "{}")) {
            is ApiResult.Success -> {
                val result = res.data.optJSONObject("result")
                val id = result?.optString("id").orEmpty()
                val url = result?.optString("url").orEmpty()
                if (id.isBlank() || url.isBlank()) {
                    ApiResult.Error(
                        ApiErrorInfo(
                            message = "Missing tail id/url",
                            persianSummary = "کلودفلر آدرس WebSocket لاگ زنده را برنگرداند.",
                            rawDetail = res.data.toString()
                        )
                    )
                } else {
                    ApiResult.Success(id to url)
                }
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun deleteTailSession(
        accountId: String,
        token: String,
        scriptName: String,
        tailId: String
    ): ApiResult<Unit> {
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/tails/${encodeSegment(tailId)}"
        return when (val res = executeJson(token, "DELETE", path)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    fun openTailWebSocket(
        wsUrl: String,
        onConnected: () -> Unit,
        onTraceEvent: (TailLogEntry) -> Unit,
        onError: (String) -> Unit,
        onClosed: () -> Unit
    ): WebSocket {
        val request = Request.Builder()
            .url(wsUrl)
            .header("Sec-WebSocket-Protocol", "trace-v1")
            .build()

        return wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseTailMessage(text)?.let(onTraceEvent)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                parseTailMessage(bytes.utf8())?.let(onTraceEvent)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError(t.message ?: "WebSocket connection failed")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onClosed()
            }
        })
    }

    private fun parseTailMessage(raw: String): TailLogEntry? {
        return try {
            val json = JSONObject(raw)
            val eventReq = json.optJSONObject("event")?.optJSONObject("request")
            val method = eventReq?.optString("method", "EVENT") ?: "EVENT"
            val url = eventReq?.optString("url", "") ?: ""
            val outcome = json.optString("outcome", "ok")
            val ts = json.optLong("eventTimestamp", System.currentTimeMillis())

            val logs = mutableListOf<String>()
            val logsArr = json.optJSONArray("logs")
            if (logsArr != null) {
                for (i in 0 until logsArr.length()) {
                    val l = logsArr.optJSONObject(i) ?: continue
                    val lvl = l.optString("level", "log")
                    val msgArr = l.optJSONArray("message")
                    val msgText = if (msgArr != null) {
                        (0 until msgArr.length()).joinToString(" ") { idx -> msgArr.opt(idx)?.toString() ?: "" }
                    } else {
                        l.optString("message", "")
                    }
                    logs.add("[$lvl] $msgText")
                }
            }

            val exceptions = mutableListOf<String>()
            val excArr = json.optJSONArray("exceptions")
            if (excArr != null) {
                for (i in 0 until excArr.length()) {
                    val ex = excArr.optJSONObject(i) ?: continue
                    val name = ex.optString("name", "Error")
                    val msg = ex.optString("message", "")
                    exceptions.add("$name: $msg")
                }
            }

            val formattedTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date(ts))

            TailLogEntry(
                id = UUID.randomUUID().toString(),
                timestamp = formattedTime,
                method = method,
                url = url,
                outcome = outcome,
                logs = logs,
                exceptions = exceptions
            )
        } catch (_: Exception) {
            null
        }
    }

    // === Tab 3: D1 Databases ===

    suspend fun listD1Databases(accountId: String, token: String): ApiResult<List<D1Database>> {
        return when (val res = executeJson(token, "GET", "/accounts/$accountId/d1/database")) {
            is ApiResult.Success -> {
                val arr = res.data.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<D1Database>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        D1Database(
                            uuid = o.optString("uuid"),
                            name = o.optString("name"),
                            version = o.optString("version", ""),
                            numTables = o.optInt("num_tables", 0),
                            fileSize = o.optLong("file_size", 0L),
                            createdAt = o.optString("created_at", "")
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun createD1Database(accountId: String, token: String, name: String): ApiResult<D1Database> {
        val body = JSONObject().put("name", name.trim()).toString()
        return when (val res = executeJson(token, "POST", "/accounts/$accountId/d1/database", body)) {
            is ApiResult.Success -> {
                val o = res.data.optJSONObject("result") ?: JSONObject()
                ApiResult.Success(
                    D1Database(
                        uuid = o.optString("uuid"),
                        name = o.optString("name", name),
                        version = o.optString("version", ""),
                        createdAt = o.optString("created_at", "")
                    )
                )
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun deleteD1Database(accountId: String, token: String, dbId: String): ApiResult<Unit> {
        return when (val res = executeJson(token, "DELETE", "/accounts/$accountId/d1/database/${encodeSegment(dbId)}")) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun queryD1Database(
        accountId: String,
        token: String,
        dbId: String,
        sql: String
    ): ApiResult<D1QueryResult> {
        val body = JSONObject()
            .put("sql", sql.trim())
            .put("params", JSONArray())
            .toString()
        val path = "/accounts/$accountId/d1/database/${encodeSegment(dbId)}/query"
        return when (val res = executeJson(token, "POST", path, body)) {
            is ApiResult.Success -> {
                val resultArr = res.data.optJSONArray("result")
                val statements = mutableListOf<JSONObject>()
                if (resultArr != null) {
                    for (i in 0 until resultArr.length()) {
                        resultArr.optJSONObject(i)?.let { statements.add(it) }
                    }
                } else {
                    res.data.optJSONObject("result")?.let { statements.add(it) }
                }

                // For multi-statement SQL, prefer the last statement that produced rows, else the last statement
                val targetStatement = statements.lastOrNull {
                    (it.optJSONArray("results")?.length() ?: 0) > 0
                } ?: statements.lastOrNull()

                val rowsArr = targetStatement?.optJSONArray("results") ?: JSONArray()
                val columns = linkedSetOf<String>()
                for (i in 0 until rowsArr.length()) {
                    val rowObj = rowsArr.optJSONObject(i) ?: continue
                    val keys = rowObj.keys()
                    while (keys.hasNext()) columns.add(keys.next())
                }
                val colList = columns.toList()
                val rows = mutableListOf<List<String>>()
                for (i in 0 until rowsArr.length()) {
                    val rowObj = rowsArr.optJSONObject(i) ?: continue
                    rows.add(colList.map { col ->
                        val v = rowObj.opt(col)
                        if (v == null || v == JSONObject.NULL) "NULL" else v.toString()
                    })
                }

                var totalDuration = 0.0
                var totalChanges = 0
                var totalRowsRead = 0
                statements.forEach { st ->
                    val m = st.optJSONObject("meta")
                    totalDuration += m?.optDouble("duration", 0.0) ?: 0.0
                    totalChanges += m?.optInt("changes", 0) ?: 0
                    totalRowsRead += m?.optInt("rows_read", 0) ?: 0
                }
                if (totalRowsRead == 0 && rows.isNotEmpty()) totalRowsRead = rows.size
                val stmtPrefix = if (statements.size > 1) "${statements.size} دستور | " else ""
                val summary = "$stmtPrefix${rows.size} ردیف | تغییرات: $totalChanges | خوانده شده: $totalRowsRead | زمان: ${String.format(java.util.Locale.US, "%.2f", totalDuration)}ms"
                ApiResult.Success(
                    D1QueryResult(
                        columns = colList,
                        rows = rows,
                        metaSummary = summary,
                        rawJson = res.data.toString(2)
                    )
                )
            }
            is ApiResult.Error -> res
        }
    }

    // === Tab 3: Workers KV ===

    suspend fun listKvNamespaces(accountId: String, token: String): ApiResult<List<KvNamespace>> {
        return when (val res = executeJson(token, "GET", "/accounts/$accountId/storage/kv/namespaces?per_page=100")) {
            is ApiResult.Success -> {
                val arr = res.data.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<KvNamespace>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(KvNamespace(id = o.optString("id"), title = o.optString("title")))
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun createKvNamespace(accountId: String, token: String, title: String): ApiResult<KvNamespace> {
        val body = JSONObject().put("title", title.trim()).toString()
        return when (val res = executeJson(token, "POST", "/accounts/$accountId/storage/kv/namespaces", body)) {
            is ApiResult.Success -> {
                val o = res.data.optJSONObject("result") ?: JSONObject()
                ApiResult.Success(KvNamespace(id = o.optString("id"), title = o.optString("title", title)))
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun deleteKvNamespace(accountId: String, token: String, nsId: String): ApiResult<Unit> {
        return when (val res = executeJson(token, "DELETE", "/accounts/$accountId/storage/kv/namespaces/${encodeSegment(nsId)}")) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun listKvKeys(
        accountId: String,
        token: String,
        nsId: String,
        prefix: String
    ): ApiResult<List<KvKeyEntry>> {
        val query = if (prefix.isBlank()) "?limit=100" else "?limit=100&prefix=${encodeSegment(prefix.trim())}"
        val path = "/accounts/$accountId/storage/kv/namespaces/${encodeSegment(nsId)}/keys$query"
        return when (val res = executeJson(token, "GET", path)) {
            is ApiResult.Success -> {
                val arr = res.data.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<KvKeyEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        KvKeyEntry(
                            name = o.optString("name"),
                            expiration = if (o.has("expiration") && !o.isNull("expiration")) o.optLong("expiration") else null
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun readKvValue(
        accountId: String,
        token: String,
        nsId: String,
        key: String
    ): ApiResult<String> = withContext(ioDispatcher) {
        try {
            val path = "/accounts/$accountId/storage/kv/namespaces/${encodeSegment(nsId)}/values/${encodeSegment(key)}"
            val req = Request.Builder()
                .url(baseUrl(path))
                .header("Authorization", "Bearer ${token.trim()}")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext ApiResult.Error(parseErrorResponse(resp.code, raw, path))
                }
                ApiResult.Success(raw)
            }
        } catch (e: Exception) {
            ApiResult.Error(
                ApiErrorInfo(
                    message = e.message ?: "KV read error",
                    persianSummary = "خطا در خواندن مقدار کلید از KV.",
                    rawDetail = e.toString()
                )
            )
        }
    }

    suspend fun writeKvValue(
        accountId: String,
        token: String,
        nsId: String,
        key: String,
        value: String
    ): ApiResult<Unit> {
        val path = "/accounts/$accountId/storage/kv/namespaces/${encodeSegment(nsId)}/values/${encodeSegment(key)}"
        return when (val res = executeJson(token, "PUT", path, rawBody = value.toRequestBody(plainTextMediaType))) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun deleteKvKey(
        accountId: String,
        token: String,
        nsId: String,
        key: String
    ): ApiResult<Unit> {
        val path = "/accounts/$accountId/storage/kv/namespaces/${encodeSegment(nsId)}/values/${encodeSegment(key)}"
        return when (val res = executeJson(token, "DELETE", path)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    // === Tab 3: Cloudflare R2 ===

    suspend fun listR2Buckets(accountId: String, token: String): ApiResult<List<R2Bucket>> {
        return when (val res = executeJson(token, "GET", "/accounts/$accountId/r2/buckets")) {
            is ApiResult.Success -> {
                val resultObj = res.data.optJSONObject("result")
                val arr = resultObj?.optJSONArray("buckets") ?: JSONArray()
                val list = mutableListOf<R2Bucket>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        R2Bucket(
                            name = o.optString("name"),
                            creationDate = o.optString("creation_date", ""),
                            location = o.optString("location", "")
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun createR2Bucket(accountId: String, token: String, name: String): ApiResult<Unit> {
        val body = JSONObject().put("name", name.trim().lowercase()).toString()
        return when (val res = executeJson(token, "POST", "/accounts/$accountId/r2/buckets", body)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun deleteR2Bucket(accountId: String, token: String, bucketName: String): ApiResult<Unit> {
        return when (val res = executeJson(token, "DELETE", "/accounts/$accountId/r2/buckets/${encodeSegment(bucketName)}")) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun listR2Objects(accountId: String, token: String, bucketName: String): ApiResult<List<R2ObjectEntry>> {
        val path = "/accounts/$accountId/r2/buckets/${encodeSegment(bucketName)}/objects?per_page=100"
        return when (val res = executeJson(token, "GET", path)) {
            is ApiResult.Success -> {
                val result = res.data.opt("result")
                val arr = when (result) {
                    is JSONArray -> result
                    is JSONObject -> result.optJSONArray("objects") ?: JSONArray()
                    else -> JSONArray()
                }
                val list = mutableListOf<R2ObjectEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        R2ObjectEntry(
                            key = o.optString("key"),
                            size = o.optLong("size", 0L),
                            etag = o.optString("etag", ""),
                            lastModified = o.optString("last_modified", "")
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun getR2ManagedDomain(
        accountId: String,
        token: String,
        bucketName: String
    ): ApiResult<R2ManagedDomainInfo> {
        val path = "/accounts/$accountId/r2/buckets/${encodeSegment(bucketName)}/domains/managed"
        return when (val res = executeJson(token, "GET", path)) {
            is ApiResult.Success -> {
                val r = res.data.optJSONObject("result")
                ApiResult.Success(
                    R2ManagedDomainInfo(
                        enabled = r?.optBoolean("enabled", false) ?: false,
                        domain = r?.optString("domain", "") ?: ""
                    )
                )
            }
            is ApiResult.Error -> {
                if (res.error.httpStatus == 404) {
                    ApiResult.Success(R2ManagedDomainInfo(enabled = false, domain = ""))
                } else res
            }
        }
    }

    suspend fun setR2ManagedDomain(
        accountId: String,
        token: String,
        bucketName: String,
        enabled: Boolean
    ): ApiResult<R2ManagedDomainInfo> {
        val path = "/accounts/$accountId/r2/buckets/${encodeSegment(bucketName)}/domains/managed"
        val body = JSONObject().put("enabled", enabled).toString()
        return when (val res = executeJson(token, "PUT", path, body)) {
            is ApiResult.Success -> {
                val r = res.data.optJSONObject("result")
                ApiResult.Success(
                    R2ManagedDomainInfo(
                        enabled = r?.optBoolean("enabled", enabled) ?: enabled,
                        domain = r?.optString("domain", "") ?: ""
                    )
                )
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun getR2Cors(accountId: String, token: String, bucketName: String): ApiResult<String> {
        val path = "/accounts/$accountId/r2/buckets/${encodeSegment(bucketName)}/cors"
        return when (val res = executeJson(token, "GET", path)) {
            is ApiResult.Success -> ApiResult.Success(res.data.opt("result")?.let { tryFormatJson(it.toString()) } ?: "[]")
            is ApiResult.Error -> {
                if (res.error.httpStatus == 404) ApiResult.Success("[]") else res
            }
        }
    }

    // === Tab 3: Queues & Durable Objects ===

    suspend fun listQueues(accountId: String, token: String): ApiResult<List<QueueInfo>> {
        return when (val res = executeJson(token, "GET", "/accounts/$accountId/queues")) {
            is ApiResult.Success -> {
                val arr = res.data.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<QueueInfo>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        QueueInfo(
                            queueId = o.optString("queue_id"),
                            queueName = o.optString("queue_name"),
                            createdOn = o.optString("created_on", ""),
                            producersCount = o.optInt("producers_total_count", 0),
                            consumersCount = o.optInt("consumers_total_count", 0)
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun createQueue(accountId: String, token: String, name: String): ApiResult<Unit> {
        val body = JSONObject().put("queue_name", name.trim()).toString()
        return when (val res = executeJson(token, "POST", "/accounts/$accountId/queues", body)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun deleteQueue(accountId: String, token: String, queueId: String): ApiResult<Unit> {
        return when (val res = executeJson(token, "DELETE", "/accounts/$accountId/queues/${encodeSegment(queueId)}")) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun listDurableObjectNamespaces(
        accountId: String,
        token: String
    ): ApiResult<List<DurableObjectNamespace>> {
        return when (val res = executeJson(token, "GET", "/accounts/$accountId/workers/durable_objects/namespaces")) {
            is ApiResult.Success -> {
                val arr = res.data.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<DurableObjectNamespace>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        DurableObjectNamespace(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            script = o.optString("script", ""),
                            className = o.optString("class", "")
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun listDurableObjects(
        accountId: String,
        token: String,
        nsId: String
    ): ApiResult<List<DurableObjectInstance>> {
        val path = "/accounts/$accountId/workers/durable_objects/namespaces/${encodeSegment(nsId)}/objects"
        return when (val res = executeJson(token, "GET", path)) {
            is ApiResult.Success -> {
                val arr = res.data.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<DurableObjectInstance>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        DurableObjectInstance(
                            id = o.optString("id"),
                            hasStoredData = o.optBoolean("hasStoredData", false)
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    // === Tab 4: Secrets, Cron, Custom Domains, Zones & Routes, Script Settings ===

    suspend fun listSecrets(
        accountId: String,
        token: String,
        scriptName: String
    ): ApiResult<List<SecretEntry>> {
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/secrets"
        return when (val res = executeJson(token, "GET", path)) {
            is ApiResult.Success -> {
                val arr = res.data.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<SecretEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(SecretEntry(name = o.optString("name"), type = o.optString("type", "secret_text")))
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun patchSecretsBulk(
        accountId: String,
        token: String,
        scriptName: String,
        key: String,
        value: String
    ): ApiResult<Unit> {
        val secretObj = JSONObject()
            .put("type", "secret_text")
            .put("name", key.trim())
            .put("text", value)
        val secretsMap = JSONObject().put(key.trim(), secretObj)
        val body = JSONObject().put("secrets", secretsMap).toString()
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/secrets-bulk"
        return when (val res = executeJson(token, "PATCH", path, body)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun deleteSecret(
        accountId: String,
        token: String,
        scriptName: String,
        secretName: String
    ): ApiResult<Unit> {
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/secrets/${encodeSegment(secretName)}"
        return when (val res = executeJson(token, "DELETE", path)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun getCronSchedules(
        accountId: String,
        token: String,
        scriptName: String
    ): ApiResult<List<CronSchedule>> {
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/schedules"
        return when (val res = executeJson(token, "GET", path)) {
            is ApiResult.Success -> {
                val schedulesArr = res.data.optJSONObject("result")?.optJSONArray("schedules") ?: JSONArray()
                val list = mutableListOf<CronSchedule>()
                for (i in 0 until schedulesArr.length()) {
                    val o = schedulesArr.optJSONObject(i) ?: continue
                    list.add(
                        CronSchedule(
                            cron = o.optString("cron"),
                            createdOn = o.optString("created_on", ""),
                            modifiedOn = o.optString("modified_on", "")
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun updateCronSchedules(
        accountId: String,
        token: String,
        scriptName: String,
        crons: List<String>
    ): ApiResult<List<CronSchedule>> {
        val arr = JSONArray()
        crons.forEach { cron -> arr.put(JSONObject().put("cron", cron.trim())) }
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/schedules"
        return when (val res = executeJson(token, "PUT", path, arr.toString())) {
            is ApiResult.Success -> {
                val schedulesArr = res.data.optJSONObject("result")?.optJSONArray("schedules") ?: JSONArray()
                val list = mutableListOf<CronSchedule>()
                for (i in 0 until schedulesArr.length()) {
                    val o = schedulesArr.optJSONObject(i) ?: continue
                    list.add(
                        CronSchedule(
                            cron = o.optString("cron"),
                            createdOn = o.optString("created_on", ""),
                            modifiedOn = o.optString("modified_on", "")
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun listCustomDomains(accountId: String, token: String): ApiResult<List<CustomDomainRecord>> {
        return when (val res = executeJson(token, "GET", "/accounts/$accountId/workers/domains")) {
            is ApiResult.Success -> {
                val arr = res.data.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<CustomDomainRecord>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        CustomDomainRecord(
                            id = o.optString("id"),
                            hostname = o.optString("hostname"),
                            service = o.optString("service"),
                            zoneId = o.optString("zone_id"),
                            zoneName = o.optString("zone_name", ""),
                            environment = o.optString("environment", "production")
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun attachCustomDomain(
        accountId: String,
        token: String,
        hostname: String,
        service: String,
        zoneId: String
    ): ApiResult<Unit> {
        val body = JSONObject()
            .put("hostname", hostname.trim())
            .put("service", service.trim())
            .put("zone_id", zoneId.trim())
            .put("environment", "production")
            .toString()
        return when (val res = executeJson(token, "PUT", "/accounts/$accountId/workers/domains", body)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun detachCustomDomain(accountId: String, token: String, domainId: String): ApiResult<Unit> {
        return when (val res = executeJson(token, "DELETE", "/accounts/$accountId/workers/domains/${encodeSegment(domainId)}")) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun listZones(token: String): ApiResult<List<ZoneInfo>> {
        return when (val res = executeJson(token, "GET", "/zones?per_page=50")) {
            is ApiResult.Success -> {
                val arr = res.data.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<ZoneInfo>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        ZoneInfo(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            status = o.optString("status", "")
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun listZoneRoutes(token: String, zoneId: String): ApiResult<List<WorkerRouteRecord>> {
        return when (val res = executeJson(token, "GET", "/zones/${encodeSegment(zoneId)}/workers/routes")) {
            is ApiResult.Success -> {
                val arr = res.data.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<WorkerRouteRecord>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        WorkerRouteRecord(
                            id = o.optString("id"),
                            pattern = o.optString("pattern"),
                            script = o.optString("script", "")
                        )
                    )
                }
                ApiResult.Success(list)
            }
            is ApiResult.Error -> res
        }
    }

    suspend fun createZoneRoute(
        token: String,
        zoneId: String,
        pattern: String,
        scriptName: String
    ): ApiResult<Unit> {
        if (pattern.isBlank() || scriptName.isBlank()) {
            return ApiResult.Error(
                ApiErrorInfo(
                    message = "Missing route pattern or script name",
                    persianSummary = "الگوی مسیر و نام ورکر نمی‌توانند خالی باشند."
                )
            )
        }
        val body = JSONObject()
            .put("pattern", pattern.trim())
            .put("script", scriptName.trim())
            .toString()
        return when (val res = executeJson(token, "POST", "/zones/${encodeSegment(zoneId)}/workers/routes", body)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun deleteZoneRoute(token: String, zoneId: String, routeId: String): ApiResult<Unit> {
        return when (val res = executeJson(token, "DELETE", "/zones/${encodeSegment(zoneId)}/workers/routes/${encodeSegment(routeId)}")) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> res
        }
    }

    suspend fun getScriptSettings(
        accountId: String,
        token: String,
        scriptName: String
    ): ApiResult<ScriptSettingsInfo> {
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/script-settings"
        return when (val res = executeJson(token, "GET", path)) {
            is ApiResult.Success -> {
                val r = res.data.optJSONObject("result")
                val obs = r?.optJSONObject("observability")?.optBoolean("enabled", false) ?: false
                val logpush = r?.optBoolean("logpush", false) ?: false
                ApiResult.Success(ScriptSettingsInfo(observabilityEnabled = obs, logpushEnabled = logpush))
            }
            is ApiResult.Error -> {
                if (res.error.httpStatus == 404 || res.error.code == 10007) {
                    ApiResult.Success(ScriptSettingsInfo())
                } else {
                    res
                }
            }
        }
    }

    suspend fun patchScriptSettings(
        accountId: String,
        token: String,
        scriptName: String,
        observabilityEnabled: Boolean,
        logpushEnabled: Boolean
    ): ApiResult<ScriptSettingsInfo> {
        val body = JSONObject()
            .put("observability", JSONObject().put("enabled", observabilityEnabled))
            .put("logpush", logpushEnabled)
            .toString()
        val path = "/accounts/$accountId/workers/scripts/${encodeSegment(scriptName)}/script-settings"
        return when (val res = executeJson(token, "PATCH", path, body)) {
            is ApiResult.Success -> {
                val r = res.data.optJSONObject("result")
                val obs = r?.optJSONObject("observability")?.optBoolean("enabled", observabilityEnabled) ?: observabilityEnabled
                val logpush = r?.optBoolean("logpush", logpushEnabled) ?: logpushEnabled
                ApiResult.Success(ScriptSettingsInfo(observabilityEnabled = obs, logpushEnabled = logpush))
            }
            is ApiResult.Error -> res
        }
    }

    companion object {
        fun tryFormatJson(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            return try {
                when {
                    trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                    trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                    else -> raw
                }
            } catch (_: Exception) {
                raw
            }
        }
    }
}
