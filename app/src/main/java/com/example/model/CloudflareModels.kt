package com.example.model

enum class StudioTab(val titleFa: String) {
    WORKERS("ورکرها"),
    DEPLOY_TEST("تست و انتشار"),
    STORAGE("دیتابیس و فایل"),
    SETTINGS("تنظیمات و دامنه")
}

enum class StorageSubTab(val titleFa: String) {
    D1("دیتابیس D1"),
    KV("حافظه KV"),
    R2("فایل R2"),
    QUEUES_DO("صف و DO")
}

data class CloudflareAccount(
    val id: String,
    val profileName: String,
    val accountId: String,
    val apiToken: String,
    val isVerified: Boolean = false,
    val tokenStatusText: String = "",
    val subdomain: String = "",
    val lastCheckedAt: String = ""
)

data class ApiErrorInfo(
    val httpStatus: Int = 0,
    val code: Int? = null,
    val message: String,
    val persianSummary: String,
    val rawDetail: String = ""
)

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val error: ApiErrorInfo) : ApiResult<Nothing>()
}

// === Tab 1: Workers & Bindings ===

data class WorkerScriptSummary(
    val id: String,
    val modifiedOn: String = "",
    val createdOn: String = "",
    val usageModel: String = ""
)

data class WorkerTemplate(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val defaultName: String,
    val code: String,
    val suggestedBindings: List<WorkerBinding> = emptyList()
)

enum class BindingType(val apiType: String, val labelFa: String) {
    PLAIN_TEXT("plain_text", "متغیر محیطی (Text)"),
    D1("d1", "دیتابیس D1"),
    KV("kv_namespace", "فضای KV"),
    R2("r2_bucket", "باکت R2"),
    QUEUE("queue", "صف پیام (Queue)"),
    DURABLE_OBJECT("durable_object_namespace", "Durable Object"),
    AI("ai", "Workers AI"),
    HYPERDRIVE("hyperdrive", "Hyperdrive"),
    ANALYTICS_ENGINE("analytics_engine", "Analytics Engine"),
    SERVICE("service", "سرویس ورکر (Service)")
}

data class WorkerBinding(
    val uid: String,
    val type: BindingType,
    val name: String,
    val textValue: String = "",
    val databaseId: String = "",
    val namespaceId: String = "",
    val bucketName: String = "",
    val queueName: String = "",
    val className: String = "",
    val scriptName: String = "",
    val hyperdriveId: String = "",
    val dataset: String = "",
    val serviceName: String = ""
)

data class WorkerEditorState(
    val scriptName: String = "hello-setup-cloud",
    val code: String = "",
    val compatibilityDate: String = "2026-11-15",
    val nodejsCompat: Boolean = false,
    val smartPlacement: Boolean = false,
    val observabilityEnabled: Boolean = true,
    val bindings: List<WorkerBinding> = emptyList(),
    val isEditorExpanded: Boolean = false,
    val isLoadingScript: Boolean = false
)

// === Tab 2: Versions, HTTP Tester, Tail Logs, Deployments ===

data class WorkerVersionSummary(
    val id: String,
    val number: Int = 0,
    val createdOn: String = "",
    val source: String = "",
    val authorEmail: String = ""
)

data class WorkerVersionDetail(
    val id: String,
    val compatibilityDate: String = "2026-11-15",
    val nodejsCompat: Boolean = false,
    val smartPlacement: Boolean = false,
    val observabilityEnabled: Boolean = true,
    val bindings: List<WorkerBinding> = emptyList()
)

enum class EditorRunnerMode(val titleFa: String) {
    LOCAL_V8("شبیه‌ساز محلی V8"),
    CLOUDFLARE_EDGE("اجرای زنده کلودفلر")
}

data class LocalRunResult(
    val statusCode: Int,
    val statusText: String,
    val headers: List<Pair<String, String>>,
    val body: String,
    val consoleLogs: List<String>,
    val durationMs: Long,
    val errorMessage: String? = null
)

data class DeploymentVersionAllocation(
    val versionId: String,
    val percentage: Int
)

data class WorkerDeploymentSummary(
    val id: String,
    val createdOn: String = "",
    val strategy: String = "",
    val allocations: List<DeploymentVersionAllocation> = emptyList()
)

enum class HttpTargetMode(val labelFa: String) {
    PREVIEW("پیش‌نمایش نسخه"),
    PRODUCTION("نسخه اصلی"),
    CUSTOM_URL("لینک دلخواه")
}

data class HttpHeaderEntry(
    val id: String,
    val key: String,
    val value: String
)

data class HttpTestResponse(
    val statusCode: Int,
    val statusMessage: String,
    val latencyMs: Long,
    val headers: List<Pair<String, String>>,
    val body: String
)

data class TailLogEntry(
    val id: String,
    val timestamp: String,
    val method: String,
    val url: String,
    val outcome: String,
    val logs: List<String>,
    val exceptions: List<String>
)

// === Tab 3: Storage (D1, KV, R2, Queues, DO) ===

data class D1Database(
    val uuid: String,
    val name: String,
    val version: String = "",
    val numTables: Int = 0,
    val fileSize: Long = 0L,
    val createdAt: String = ""
)

data class D1QueryResult(
    val columns: List<String>,
    val rows: List<List<String>>,
    val metaSummary: String,
    val rawJson: String
)

data class KvNamespace(
    val id: String,
    val title: String
)

data class KvKeyEntry(
    val name: String,
    val expiration: Long? = null
)

data class R2Bucket(
    val name: String,
    val creationDate: String = "",
    val location: String = ""
)

data class R2ObjectEntry(
    val key: String,
    val size: Long = 0L,
    val etag: String = "",
    val lastModified: String = ""
)

data class R2ManagedDomainInfo(
    val enabled: Boolean = false,
    val domain: String = ""
)

data class QueueInfo(
    val queueId: String,
    val queueName: String,
    val createdOn: String = "",
    val producersCount: Int = 0,
    val consumersCount: Int = 0
)

data class DurableObjectNamespace(
    val id: String,
    val name: String,
    val script: String = "",
    val className: String = ""
)

data class DurableObjectInstance(
    val id: String,
    val hasStoredData: Boolean = false
)

// === Tab 4: Secrets, Cron, Domains, Routes, Script Settings ===

data class SecretEntry(
    val name: String,
    val type: String = "secret_text"
)

data class CronSchedule(
    val cron: String,
    val createdOn: String = "",
    val modifiedOn: String = ""
)

data class CustomDomainRecord(
    val id: String,
    val hostname: String,
    val service: String,
    val zoneId: String,
    val zoneName: String = "",
    val environment: String = "production"
)

data class ZoneInfo(
    val id: String,
    val name: String,
    val status: String = ""
)

data class WorkerRouteRecord(
    val id: String,
    val pattern: String,
    val script: String = ""
)

data class ScriptSubdomainStatus(
    val enabled: Boolean = false,
    val previewsEnabled: Boolean = false
)

data class ScriptSettingsInfo(
    val observabilityEnabled: Boolean = false,
    val logpushEnabled: Boolean = false
)
