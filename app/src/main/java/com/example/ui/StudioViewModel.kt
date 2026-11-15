package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountPreferencesRepository
import com.example.data.CloudflareApiClient
import com.example.data.LocalJsWorkerRunner
import com.example.data.WorkerCodeUtils
import com.example.data.WorkerTemplates
import com.example.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class StudioUiState(
    val activeTab: StudioTab = StudioTab.WORKERS,
    val storageSubTab: StorageSubTab = StorageSubTab.D1,

    // Accounts
    val accounts: List<CloudflareAccount> = emptyList(),
    val activeAccountId: String = "",
    val isTestingConnection: Boolean = false,
    val draftVerifyStatus: String? = null,
    val isVerifyingDraft: Boolean = false,

    // Global feedback
    val globalError: ApiErrorInfo? = null,
    val snackbarMessage: String? = null,

    // Tab 1: Workers & Editor + Hybrid Runner
    val workers: List<WorkerScriptSummary> = emptyList(),
    val workersSearchQuery: String = "",
    val isLoadingWorkers: Boolean = false,
    val editor: WorkerEditorState = WorkerEditorState(
        scriptName = WorkerTemplates.all.first().defaultName,
        code = WorkerTemplates.all.first().code
    ),
    val runnerMode: EditorRunnerMode = EditorRunnerMode.LOCAL_V8,
    val runnerMethod: String = "GET",
    val runnerPath: String = "/",
    val runnerBody: String = "{\n  \"hello\": \"setup-cloud\"\n}",
    val isRunningCode: Boolean = false,
    val isImportingCode: Boolean = false,
    val localRunResult: LocalRunResult? = null,
    val edgeRunResult: HttpTestResponse? = null,
    val runnerError: ApiErrorInfo? = null,

    // Tab 2: Test, Preview, Tail, Deploy
    val isUploadingVersion: Boolean = false,
    val latestUploadedVersionId: String = "",
    val previewUrl: String = "",
    val productionUrl: String = "",
    val httpTargetMode: HttpTargetMode = HttpTargetMode.PREVIEW,
    val httpCustomUrl: String = "",
    val httpMethod: String = "GET",
    val httpPath: String = "/",
    val httpHeaders: List<HttpHeaderEntry> = listOf(
        HttpHeaderEntry("hdr-1", "Accept", "application/json")
    ),
    val httpRequestBody: String = "{\n  \"hello\": \"setup-cloud\"\n}",
    val isSendingHttpTest: Boolean = false,
    val httpTestResponse: HttpTestResponse? = null,

    val isTailStarting: Boolean = false,
    val isTailConnected: Boolean = false,
    val activeTailId: String = "",
    val activeTailScript: String = "",
    val tailLogs: List<TailLogEntry> = emptyList(),
    val tailStatusNote: String = "",

    val versions: List<WorkerVersionSummary> = emptyList(),
    val deployments: List<WorkerDeploymentSummary> = emptyList(),
    val isLoadingDeployHistory: Boolean = false,
    val isDeploying: Boolean = false,

    // Tab 3: Storage
    val d1Databases: List<D1Database> = emptyList(),
    val selectedD1: D1Database? = null,
    val d1SqlQuery: String = "SELECT name, type, sql FROM sqlite_schema WHERE type IN ('table','view') ORDER BY name;",
    val d1QueryResult: D1QueryResult? = null,
    val d1Loading: Boolean = false,
    val d1Error: ApiErrorInfo? = null,

    val kvNamespaces: List<KvNamespace> = emptyList(),
    val selectedKv: KvNamespace? = null,
    val inspectedKvNamespaceId: String = "",
    val kvPrefixFilter: String = "",
    val kvKeys: List<KvKeyEntry> = emptyList(),
    val selectedKvKeyName: String = "",
    val selectedKvKeyValue: String = "",
    val selectedKvReadSeq: Long = 0L,
    val kvLoading: Boolean = false,
    val kvError: ApiErrorInfo? = null,

    val r2Buckets: List<R2Bucket> = emptyList(),
    val selectedR2: R2Bucket? = null,
    val inspectedR2BucketName: String = "",
    val r2Objects: List<R2ObjectEntry> = emptyList(),
    val r2ManagedDomain: R2ManagedDomainInfo? = null,
    val r2CorsJson: String = "",
    val r2Loading: Boolean = false,
    val r2Error: ApiErrorInfo? = null,

    val queues: List<QueueInfo> = emptyList(),
    val doNamespaces: List<DurableObjectNamespace> = emptyList(),
    val selectedDoNamespace: DurableObjectNamespace? = null,
    val doInstances: List<DurableObjectInstance> = emptyList(),
    val queuesDoLoading: Boolean = false,
    val queuesDoError: ApiErrorInfo? = null,

    // Tab 4: Settings, Secrets, Cron, Domains, Routes
    val secrets: List<SecretEntry> = emptyList(),
    val cronSchedules: List<CronSchedule> = emptyList(),
    val scriptSubdomainStatus: ScriptSubdomainStatus = ScriptSubdomainStatus(),
    val customDomains: List<CustomDomainRecord> = emptyList(),
    val zones: List<ZoneInfo> = emptyList(),
    val selectedZone: ZoneInfo? = null,
    val zoneRoutes: List<WorkerRouteRecord> = emptyList(),
    val scriptSettings: ScriptSettingsInfo = ScriptSettingsInfo(),
    val settingsLoading: Boolean = false,
    val settingsError: ApiErrorInfo? = null
) {
    val activeAccount: CloudflareAccount
        get() = accounts.firstOrNull { it.id == activeAccountId }
            ?: accounts.firstOrNull()
            ?: CloudflareAccount("", "اکانت اصلی", "", "")
}

class StudioViewModel @JvmOverloads constructor(
    application: Application,
    private val api: CloudflareApiClient = CloudflareApiClient()
) : AndroidViewModel(application) {

    private val accountRepo = AccountPreferencesRepository(application.applicationContext)
    private val localRunner = LocalJsWorkerRunner(application.applicationContext)

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var activeTailSocket: WebSocket? = null
    private var tailSessionGeneration: Long = 0L

    init {
        val accounts = accountRepo.loadAccounts()
        val activeId = accountRepo.getActiveAccountId(accounts)
        _uiState.update {
            it.copy(accounts = accounts, activeAccountId = activeId)
        }
        bootstrapActiveAccountData()
    }

    private fun bootstrapActiveAccountData() {
        testConnectionAndSubdomain()
        refreshWorkersList()
        refreshD1(silent = true)
        refreshKv(silent = true)
        refreshR2(silent = true)
        refreshQueuesAndDo(silent = true)
    }

    private fun ensureActiveStorageSubTabLoaded(subTab: StorageSubTab) {
        val st = _uiState.value
        when (subTab) {
            StorageSubTab.D1 -> if (st.d1Databases.isEmpty()) refreshD1()
            StorageSubTab.KV -> {
                if (st.kvNamespaces.isEmpty()) {
                    refreshKv()
                } else {
                    val sel = st.selectedKv
                    if (sel != null && st.inspectedKvNamespaceId != sel.id) {
                        refreshKvKeys()
                    }
                }
            }
            StorageSubTab.R2 -> {
                if (st.r2Buckets.isEmpty()) {
                    refreshR2()
                } else {
                    val sel = st.selectedR2
                    if (sel != null && st.inspectedR2BucketName != sel.name) {
                        inspectR2Bucket(sel)
                    }
                }
            }
            StorageSubTab.QUEUES_DO -> if (st.queues.isEmpty() && st.doNamespaces.isEmpty()) refreshQueuesAndDo()
        }
    }

    fun selectTab(tab: StudioTab) {
        _uiState.update { it.copy(activeTab = tab, globalError = null) }
        when (tab) {
            StudioTab.WORKERS -> Unit
            StudioTab.DEPLOY_TEST -> {
                updateComputedUrls()
                refreshVersionsAndDeployments(silent = true)
            }
            StudioTab.STORAGE -> ensureActiveStorageSubTabLoaded(_uiState.value.storageSubTab)
            StudioTab.SETTINGS -> refreshSettingsTab()
        }
    }

    fun selectStorageSubTab(subTab: StorageSubTab) {
        _uiState.update { it.copy(storageSubTab = subTab) }
        ensureActiveStorageSubTabLoaded(subTab)
    }

    fun dismissGlobalError() {
        _uiState.update { it.copy(globalError = null) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    // === Multi-Account Management ===

    private fun resetAccountScopedState(state: StudioUiState, nextAccountId: String, nextAccounts: List<CloudflareAccount> = state.accounts): StudioUiState {
        return state.copy(
            accounts = nextAccounts,
            activeAccountId = nextAccountId,
            globalError = null,
            workers = emptyList(),
            versions = emptyList(),
            deployments = emptyList(),
            latestUploadedVersionId = "",
            previewUrl = "",
            productionUrl = "",
            httpTestResponse = null,
            localRunResult = null,
            edgeRunResult = null,
            runnerError = null,
            tailLogs = emptyList(),
            d1Databases = emptyList(),
            selectedD1 = null,
            d1QueryResult = null,
            d1Error = null,
            kvNamespaces = emptyList(),
            selectedKv = null,
            inspectedKvNamespaceId = "",
            kvKeys = emptyList(),
            selectedKvKeyName = "",
            selectedKvKeyValue = "",
            selectedKvReadSeq = state.selectedKvReadSeq + 1,
            kvError = null,
            r2Buckets = emptyList(),
            selectedR2 = null,
            inspectedR2BucketName = "",
            r2Objects = emptyList(),
            r2ManagedDomain = null,
            r2CorsJson = "",
            r2Error = null,
            queues = emptyList(),
            doNamespaces = emptyList(),
            selectedDoNamespace = null,
            doInstances = emptyList(),
            queuesDoError = null,
            secrets = emptyList(),
            cronSchedules = emptyList(),
            scriptSubdomainStatus = ScriptSubdomainStatus(),
            scriptSettings = ScriptSettingsInfo(),
            customDomains = emptyList(),
            zones = emptyList(),
            selectedZone = null,
            zoneRoutes = emptyList(),
            settingsError = null
        )
    }

    fun switchAccount(accountId: String) {
        if (accountId == _uiState.value.activeAccountId) return
        stopTailLogs()
        accountRepo.setActiveAccountId(accountId)
        _uiState.update { resetAccountScopedState(it, accountId) }
        bootstrapActiveAccountData()
        showMessage("اکانت فعال تغییر کرد")
    }

    fun verifyDraftAccount(accountId: String, apiToken: String) {
        if (accountId.isBlank() || apiToken.isBlank()) {
            _uiState.update { it.copy(draftVerifyStatus = "Account ID و API Token را وارد کنید") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingDraft = true, draftVerifyStatus = null) }
            when (val res = api.verifyToken(accountId.trim(), apiToken.trim())) {
                is ApiResult.Success -> {
                    val subRes = api.getWorkersSubdomain(accountId.trim(), apiToken.trim())
                    val sub = (subRes as? ApiResult.Success)?.data.orEmpty()
                    val msg = if (sub.isNotBlank()) {
                        "توکن معتبر است (${res.data}) | سابدامین: $sub.workers.dev"
                    } else {
                        "توکن معتبر است (${res.data})"
                    }
                    _uiState.update { it.copy(isVerifyingDraft = false, draftVerifyStatus = msg) }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isVerifyingDraft = false, draftVerifyStatus = "خطا: ${res.error.persianSummary}")
                    }
                }
            }
        }
    }

    fun clearDraftVerifyStatus() {
        _uiState.update { it.copy(draftVerifyStatus = null) }
    }

    fun saveAccount(editingId: String?, profileName: String, accountId: String, apiToken: String) {
        val current = _uiState.value.accounts.toMutableList()
        val targetId = editingId ?: UUID.randomUUID().toString()
        val updatedAccount = if (editingId != null) {
            val idx = current.indexOfFirst { it.id == editingId }
            val existing = if (idx >= 0) current[idx] else null
            val next = (existing ?: CloudflareAccount(targetId, "", "", "")).copy(
                profileName = profileName.trim(),
                accountId = accountId.trim(),
                apiToken = apiToken.trim(),
                isVerified = false,
                tokenStatusText = ""
            )
            if (idx >= 0) current[idx] = next else current.add(next)
            next
        } else {
            val next = CloudflareAccount(
                id = targetId,
                profileName = profileName.trim(),
                accountId = accountId.trim(),
                apiToken = apiToken.trim()
            )
            current.add(next)
            next
        }

        val shouldReload = editingId == null || editingId == _uiState.value.activeAccountId
        val nextActiveId = if (editingId == null) updatedAccount.id else _uiState.value.activeAccountId
        accountRepo.saveAccounts(current, nextActiveId)
        _uiState.update {
            it.copy(
                accounts = current,
                activeAccountId = nextActiveId,
                draftVerifyStatus = null
            )
        }
        if (shouldReload) {
            if (editingId == null) {
                switchAccount(updatedAccount.id)
            } else {
                bootstrapActiveAccountData()
            }
        }
        showMessage(if (editingId == null) "اکانت جدید اضافه شد" else "اطلاعات اکانت بروزرسانی شد")
    }

    fun deleteAccount(accountId: String) {
        val current = _uiState.value.accounts.toMutableList()
        if (current.size <= 1) {
            showMessage("حداقل یک اکانت باید باقی بماند. برای بازنشانی از گزینه بازگشت به پیش‌فرض استفاده کنید.")
            return
        }
        current.removeAll { it.id == accountId }
        val nextActive = if (_uiState.value.activeAccountId == accountId) {
            current.first().id
        } else {
            _uiState.value.activeAccountId
        }
        accountRepo.saveAccounts(current, nextActive)
        val switched = nextActive != _uiState.value.activeAccountId
        if (switched) {
            stopTailLogs()
            _uiState.update { resetAccountScopedState(it, nextActive, current) }
            bootstrapActiveAccountData()
        } else {
            _uiState.update { it.copy(accounts = current, activeAccountId = nextActive) }
        }
        showMessage("اکانت حذف شد")
    }

    fun resetToDefaultAccount() {
        stopTailLogs()
        val resetList = accountRepo.resetToDefaultAccount()
        val activeId = resetList.first().id
        _uiState.update { resetAccountScopedState(it, activeId, resetList) }
        bootstrapActiveAccountData()
        showMessage("اکانت اصلی پیش‌فرض بازیابی شد")
    }

    fun testConnectionAndSubdomain() {
        val acc = _uiState.value.activeAccount
        if (acc.accountId.isBlank() || acc.apiToken.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true) }
            val verifyRes = api.verifyToken(acc.accountId, acc.apiToken)
            val subRes = api.getWorkersSubdomain(acc.accountId, acc.apiToken)

            val now = SimpleDateFormat("HH:mm", Locale.US).format(Date())
            val verified = verifyRes is ApiResult.Success
            val statusText = when (verifyRes) {
                is ApiResult.Success -> verifyRes.data
                is ApiResult.Error -> "invalid"
            }
            val sub = (subRes as? ApiResult.Success)?.data ?: acc.subdomain

            val updatedAccounts = _uiState.value.accounts.map {
                if (it.id == acc.id) {
                    it.copy(
                        isVerified = verified,
                        tokenStatusText = statusText,
                        subdomain = sub,
                        lastCheckedAt = now
                    )
                } else it
            }
            accountRepo.saveAccounts(updatedAccounts, _uiState.value.activeAccountId)
            _uiState.update { state ->
                state.copy(
                    accounts = updatedAccounts,
                    isTestingConnection = false,
                    globalError = if (verifyRes is ApiResult.Error) verifyRes.error else state.globalError
                )
            }
            updateComputedUrls()
        }
    }

    fun registerSubdomain(subdomain: String) {
        val acc = _uiState.value.activeAccount
        if (subdomain.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true) }
            when (val res = api.registerWorkersSubdomain(acc.accountId, acc.apiToken, subdomain)) {
                is ApiResult.Success -> {
                    val updated = _uiState.value.accounts.map {
                        if (it.id == acc.id) it.copy(subdomain = res.data) else it
                    }
                    accountRepo.saveAccounts(updated, _uiState.value.activeAccountId)
                    _uiState.update { it.copy(accounts = updated, isTestingConnection = false) }
                    updateComputedUrls()
                    showMessage("سابدامین ${res.data}.workers.dev ثبت شد")
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isTestingConnection = false, globalError = res.error) }
                }
            }
        }
    }

    // === Tab 1: Workers & Editor ===

    fun updateWorkersSearch(query: String) {
        _uiState.update { it.copy(workersSearchQuery = query) }
    }

    fun refreshWorkersList() {
        val acc = _uiState.value.activeAccount
        if (acc.accountId.isBlank() || acc.apiToken.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWorkers = true) }
            when (val res = api.listWorkers(acc.accountId, acc.apiToken)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(workers = res.data, isLoadingWorkers = false) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoadingWorkers = false, globalError = res.error) }
                }
            }
        }
    }

    fun loadWorkerIntoEditor(scriptName: String) {
        val acc = _uiState.value.activeAccount
        if (_uiState.value.activeTailScript.isNotBlank() && _uiState.value.activeTailScript != scriptName) {
            stopTailLogs()
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    editor = it.editor.copy(scriptName = scriptName, isLoadingScript = true),
                    latestUploadedVersionId = "",
                    previewUrl = "",
                    productionUrl = "",
                    httpTestResponse = null,
                    versions = emptyList(),
                    deployments = emptyList(),
                    localRunResult = null,
                    edgeRunResult = null,
                    runnerError = null,
                    secrets = emptyList(),
                    cronSchedules = emptyList(),
                    scriptSubdomainStatus = ScriptSubdomainStatus(),
                    scriptSettings = ScriptSettingsInfo(),
                    globalError = null
                )
            }
            updateComputedUrls()
            val codeRes = api.getWorkerScriptCode(acc.accountId, acc.apiToken, scriptName)
            when (codeRes) {
                is ApiResult.Success -> {
                    val versionsRes = api.listVersions(acc.accountId, acc.apiToken, scriptName)
                    val versionsList = (versionsRes as? ApiResult.Success)?.data ?: emptyList()
                    val latestVer = versionsList.firstOrNull()?.id.orEmpty()
                    val verDetail = if (latestVer.isNotBlank()) {
                        (api.getWorkerVersionDetail(acc.accountId, acc.apiToken, scriptName, latestVer) as? ApiResult.Success)?.data
                    } else null

                    _uiState.update { state ->
                        state.copy(
                            versions = versionsList,
                            latestUploadedVersionId = latestVer,
                            editor = state.editor.copy(
                                scriptName = scriptName,
                                code = codeRes.data,
                                compatibilityDate = verDetail?.compatibilityDate ?: state.editor.compatibilityDate,
                                nodejsCompat = verDetail?.nodejsCompat ?: state.editor.nodejsCompat,
                                smartPlacement = verDetail?.smartPlacement ?: state.editor.smartPlacement,
                                observabilityEnabled = verDetail?.observabilityEnabled ?: state.editor.observabilityEnabled,
                                bindings = verDetail?.bindings ?: emptyList(),
                                isLoadingScript = false
                            )
                        )
                    }
                    updateComputedUrls()
                    refreshVersionsAndDeployments(silent = true)
                    val bindingsCount = verDetail?.bindings?.size ?: 0
                    showMessage("ورکر $scriptName بارگذاری شد ($bindingsCount بایندینگ بازیابی شد)")
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            editor = it.editor.copy(isLoadingScript = false),
                            globalError = codeRes.error
                        )
                    }
                }
            }
        }
    }

    fun deleteWorker(scriptName: String) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWorkers = true) }
            when (val res = api.deleteWorker(acc.accountId, acc.apiToken, scriptName)) {
                is ApiResult.Success -> {
                    if (_uiState.value.activeTailScript == scriptName) {
                        stopTailLogs()
                    }
                    if (_uiState.value.editor.scriptName == scriptName) {
                        _uiState.update {
                            it.copy(
                                latestUploadedVersionId = "",
                                versions = emptyList(),
                                deployments = emptyList(),
                                previewUrl = "",
                                productionUrl = "",
                                edgeRunResult = null,
                                httpTestResponse = null,
                                secrets = emptyList(),
                                cronSchedules = emptyList(),
                                scriptSubdomainStatus = ScriptSubdomainStatus(),
                                scriptSettings = ScriptSettingsInfo()
                            )
                        }
                    }
                    showMessage("ورکر $scriptName حذف شد")
                    refreshWorkersList()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoadingWorkers = false, globalError = res.error) }
                }
            }
        }
    }

    fun applyTemplate(template: WorkerTemplate) {
        _uiState.update { state ->
            val mergedBindings = if (template.suggestedBindings.isEmpty()) {
                state.editor.bindings
            } else {
                val existingNames = state.editor.bindings.map { it.name }.toSet()
                state.editor.bindings + template.suggestedBindings.filter { it.name !in existingNames }
            }
            state.copy(
                latestUploadedVersionId = "",
                previewUrl = "",
                localRunResult = null,
                edgeRunResult = null,
                runnerError = null,
                editor = state.editor.copy(
                    scriptName = template.defaultName,
                    code = template.code,
                    bindings = mergedBindings
                )
            )
        }
        updateComputedUrls()
        showMessage("قالب ${template.titleFa} در ادیتور اعمال شد")
    }

    fun updateEditorScriptName(name: String) {
        val sanitized = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
        val changed = sanitized != _uiState.value.editor.scriptName
        _uiState.update { state ->
            state.copy(
                editor = state.editor.copy(scriptName = sanitized),
                latestUploadedVersionId = if (changed) "" else state.latestUploadedVersionId,
                previewUrl = if (changed) "" else state.previewUrl,
                versions = if (changed) emptyList() else state.versions,
                deployments = if (changed) emptyList() else state.deployments,
                edgeRunResult = if (changed) null else state.edgeRunResult
            )
        }
        updateComputedUrls()
    }

    fun updateEditorCode(code: String) {
        _uiState.update { it.copy(editor = it.editor.copy(code = code)) }
    }

    fun importCodeFromText(
        rawCode: String,
        suggestedName: String? = null,
        sourceLabel: String = "فایل"
    ) {
        if (rawCode.isBlank()) {
            showMessage("محتوای کد واردشده خالی است")
            return
        }
        val cleanName = suggestedName
            ?.substringBeforeLast('.')
            ?.lowercase()
            ?.filter { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
            .orEmpty()
        _uiState.update { state ->
            val nextScript = if (cleanName.isNotBlank()) cleanName else state.editor.scriptName
            val nameChanged = nextScript != state.editor.scriptName
            state.copy(
                latestUploadedVersionId = if (nameChanged) "" else state.latestUploadedVersionId,
                previewUrl = if (nameChanged) "" else state.previewUrl,
                localRunResult = null,
                edgeRunResult = null,
                runnerError = null,
                editor = state.editor.copy(
                    scriptName = nextScript,
                    code = rawCode
                )
            )
        }
        updateComputedUrls()
        showMessage("کد با موفقیت از $sourceLabel ایمپورت شد (${rawCode.lines().size} خط)")
    }

    fun importCodeFromUrl(
        url: String,
        suggestedName: String? = null,
        onImported: () -> Unit = {}
    ) {
        if (url.isBlank()) {
            showMessage("ابتدا لینک فایل کد را وارد کنید")
            return
        }
        val derivedFileName = suggestedName ?: url.trim().substringAfterLast('/').substringBefore('?')
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingCode = true, globalError = null) }
            when (val res = api.fetchRawCodeFromUrl(url)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isImportingCode = false) }
                    importCodeFromText(
                        rawCode = res.data,
                        suggestedName = derivedFileName.takeIf { it.contains('.') },
                        sourceLabel = "لینک"
                    )
                    onImported()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isImportingCode = false, globalError = res.error) }
                }
            }
        }
    }

    fun formatEditorCode() {
        val raw = _uiState.value.editor.code
        if (raw.isBlank()) return
        val formatted = WorkerCodeUtils.formatJavaScript(raw)
        _uiState.update { it.copy(editor = it.editor.copy(code = formatted)) }
        showMessage("کد مرتب‌سازی شد")
    }

    // === Hybrid Worker Runner inside Editor ===

    fun setRunnerMode(mode: EditorRunnerMode) {
        _uiState.update { it.copy(runnerMode = mode, runnerError = null) }
    }

    fun setRunnerMethod(method: String) {
        _uiState.update { it.copy(runnerMethod = method) }
    }

    fun setRunnerPath(path: String) {
        _uiState.update { it.copy(runnerPath = path) }
    }

    fun setRunnerBody(body: String) {
        _uiState.update { it.copy(runnerBody = body) }
    }

    fun executeEditorRunner() {
        val state = _uiState.value
        if (state.editor.code.isBlank()) {
            showMessage("کد ورکر خالی است")
            return
        }
        when (state.runnerMode) {
            EditorRunnerMode.LOCAL_V8 -> runCodeLocally()
            EditorRunnerMode.CLOUDFLARE_EDGE -> saveAndRunOnCloudflareEdge()
        }
    }

    private fun runCodeLocally() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update {
                it.copy(isRunningCode = true, runnerError = null, localRunResult = null)
            }
            val result = localRunner.execute(
                code = state.editor.code,
                bindings = state.editor.bindings,
                method = state.runnerMethod,
                path = state.runnerPath,
                body = state.runnerBody
            )
            _uiState.update {
                it.copy(isRunningCode = false, localRunResult = result)
            }
        }
    }

    private fun saveAndRunOnCloudflareEdge() {
        val state = _uiState.value
        val acc = state.activeAccount
        val editor = state.editor
        if (editor.scriptName.isBlank()) {
            showMessage("ابتدا نام ورکر را وارد کنید")
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(isRunningCode = true, isUploadingVersion = true, runnerError = null, edgeRunResult = null)
            }
            when (val uploadRes = api.uploadWorkerVersionSafe(acc.accountId, acc.apiToken, editor)) {
                is ApiResult.Success -> {
                    val versionId = uploadRes.data
                    _uiState.update {
                        it.copy(
                            isUploadingVersion = false,
                            latestUploadedVersionId = versionId,
                            httpTargetMode = if (versionId.isNotBlank()) HttpTargetMode.PREVIEW else HttpTargetMode.PRODUCTION
                        )
                    }
                    updateComputedUrls()
                    refreshWorkersList()
                    refreshVersionsAndDeployments(silent = true)

                    val baseUrl = _uiState.value.previewUrl.ifBlank { _uiState.value.productionUrl }
                    if (baseUrl.isBlank()) {
                        _uiState.update {
                            it.copy(
                                isRunningCode = false,
                                runnerError = ApiErrorInfo(
                                    message = "No workers.dev subdomain",
                                    persianSummary = "نسخه آپلود شد اما سابدامین workers.dev اکانت تنظیم نشده است تا آدرس اجرا ساخته شود."
                                )
                            )
                        }
                        return@launch
                    }

                    val normalizedPath = if (state.runnerPath.startsWith("/")) state.runnerPath else "/${state.runnerPath}"
                    val targetUrl = "$baseUrl$normalizedPath"
                    val headers = listOf(HttpHeaderEntry("run-hdr", "Accept", "application/json"))

                    var probeRes = api.sendLiveHttpRequest(targetUrl, state.runnerMethod, headers, state.runnerBody)
                    // Brief retry if Cloudflare edge DNS/preview route is still warming up
                    if (probeRes is ApiResult.Error || (probeRes is ApiResult.Success && probeRes.data.statusCode == 404)) {
                        delay(700)
                        probeRes = api.sendLiveHttpRequest(targetUrl, state.runnerMethod, headers, state.runnerBody)
                    }

                    when (probeRes) {
                        is ApiResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isRunningCode = false,
                                    edgeRunResult = probeRes.data,
                                    httpTestResponse = probeRes.data
                                )
                            }
                        }
                        is ApiResult.Error -> {
                            _uiState.update {
                                it.copy(isRunningCode = false, runnerError = probeRes.error)
                            }
                        }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isRunningCode = false, isUploadingVersion = false, runnerError = uploadRes.error)
                    }
                }
            }
        }
    }

    fun updateCompatibilityDate(date: String) {
        _uiState.update { it.copy(editor = it.editor.copy(compatibilityDate = date)) }
    }

    fun toggleNodejsCompat(enabled: Boolean) {
        _uiState.update { it.copy(editor = it.editor.copy(nodejsCompat = enabled)) }
    }

    fun toggleSmartPlacement(enabled: Boolean) {
        _uiState.update { it.copy(editor = it.editor.copy(smartPlacement = enabled)) }
    }

    fun toggleEditorObservability(enabled: Boolean) {
        _uiState.update { it.copy(editor = it.editor.copy(observabilityEnabled = enabled)) }
    }

    fun setEditorExpanded(expanded: Boolean) {
        _uiState.update { it.copy(editor = it.editor.copy(isEditorExpanded = expanded)) }
    }

    fun addBinding(binding: WorkerBinding) {
        _uiState.update { state ->
            val updated = state.editor.bindings.filterNot { it.name == binding.name } + binding
            state.copy(editor = state.editor.copy(bindings = updated))
        }
        showMessage("بایندینگ ${binding.name} اضافه شد")
    }

    fun removeBinding(uid: String) {
        _uiState.update { state ->
            state.copy(editor = state.editor.copy(bindings = state.editor.bindings.filterNot { it.uid == uid }))
        }
    }

    fun bindD1ToWorker(db: D1Database) {
        addBinding(
            WorkerBinding(
                uid = UUID.randomUUID().toString(),
                type = BindingType.D1,
                name = "DB",
                databaseId = db.uuid
            )
        )
        showMessage("دیتابیس ${db.name} به عنوان env.DB به ورکر متصل شد")
    }

    fun bindKvToWorker(ns: KvNamespace) {
        addBinding(
            WorkerBinding(
                uid = UUID.randomUUID().toString(),
                type = BindingType.KV,
                name = "KV",
                namespaceId = ns.id
            )
        )
        showMessage("فضای ${ns.title} به عنوان env.KV به ورکر متصل شد")
    }

    fun bindR2ToWorker(bucket: R2Bucket) {
        addBinding(
            WorkerBinding(
                uid = UUID.randomUUID().toString(),
                type = BindingType.R2,
                name = "BUCKET",
                bucketName = bucket.name
            )
        )
        showMessage("باکت ${bucket.name} به عنوان env.BUCKET به ورکر متصل شد")
    }

    // === Tab 2: Upload Version, URLs, HTTP Tester, Tail, Deployments ===

    private fun updateComputedUrls() {
        val state = _uiState.value
        val sub = state.activeAccount.subdomain.trim()
        val script = state.editor.scriptName.trim()
        val ver = state.latestUploadedVersionId.trim()
        if (sub.isBlank() || script.isBlank()) {
            _uiState.update { it.copy(previewUrl = "", productionUrl = "") }
            return
        }
        val prodUrl = "https://$script.$sub.workers.dev"
        val prevUrl = if (ver.isNotBlank()) "https://${ver.take(8)}-$script.$sub.workers.dev" else ""
        _uiState.update { it.copy(previewUrl = prevUrl, productionUrl = prodUrl) }
    }

    fun uploadWorkerVersionSafe() {
        val acc = _uiState.value.activeAccount
        val editor = _uiState.value.editor
        if (editor.scriptName.isBlank()) {
            showMessage("ابتدا نام ورکر را وارد کنید")
            return
        }
        if (editor.code.isBlank()) {
            showMessage("کد ورکر نمی‌تواند خالی باشد")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingVersion = true, globalError = null) }
            when (val res = api.uploadWorkerVersionSafe(acc.accountId, acc.apiToken, editor)) {
                is ApiResult.Success -> {
                    val versionId = res.data
                    _uiState.update {
                        it.copy(
                            isUploadingVersion = false,
                            latestUploadedVersionId = versionId,
                            httpTargetMode = if (versionId.isNotBlank()) HttpTargetMode.PREVIEW else HttpTargetMode.PRODUCTION
                        )
                    }
                    updateComputedUrls()
                    refreshWorkersList()
                    refreshVersionsAndDeployments(silent = true)
                    showMessage("نسخه تستی با موفقیت آپلود شد (${versionId.take(8)})")
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isUploadingVersion = false, globalError = res.error) }
                }
            }
        }
    }

    fun setHttpTargetMode(mode: HttpTargetMode) {
        _uiState.update { it.copy(httpTargetMode = mode) }
    }

    fun setHttpCustomUrl(url: String) {
        _uiState.update { it.copy(httpCustomUrl = url) }
    }

    fun testCustomUrlInDeployTab(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        val normalized = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        _uiState.update {
            it.copy(
                activeTab = StudioTab.DEPLOY_TEST,
                httpTargetMode = HttpTargetMode.CUSTOM_URL,
                httpCustomUrl = normalized,
                globalError = null
            )
        }
        updateComputedUrls()
        refreshVersionsAndDeployments(silent = true)
    }

    fun setHttpMethod(method: String) {
        _uiState.update { it.copy(httpMethod = method) }
    }

    fun setHttpPath(path: String) {
        _uiState.update { it.copy(httpPath = path) }
    }

    fun addHttpHeader() {
        _uiState.update {
            it.copy(httpHeaders = it.httpHeaders + HttpHeaderEntry(UUID.randomUUID().toString(), "", ""))
        }
    }

    fun updateHttpHeader(id: String, key: String, value: String) {
        _uiState.update { state ->
            state.copy(
                httpHeaders = state.httpHeaders.map { h ->
                    if (h.id == id) h.copy(key = key, value = value) else h
                }
            )
        }
    }

    fun removeHttpHeader(id: String) {
        _uiState.update { state ->
            state.copy(httpHeaders = state.httpHeaders.filterNot { it.id == id })
        }
    }

    fun setHttpRequestBody(body: String) {
        _uiState.update { it.copy(httpRequestBody = body) }
    }

    fun sendHttpTestRequest() {
        val state = _uiState.value
        val fullUrl = when (state.httpTargetMode) {
            HttpTargetMode.CUSTOM_URL -> {
                val raw = state.httpCustomUrl.trim()
                if (raw.isBlank()) {
                    showMessage("آدرس لینک دلخواه را وارد کنید")
                    return
                }
                if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
                    raw
                } else {
                    "https://$raw"
                }
            }
            HttpTargetMode.PREVIEW, HttpTargetMode.PRODUCTION -> {
                val base = if (state.httpTargetMode == HttpTargetMode.PREVIEW) {
                    state.previewUrl.ifBlank { state.productionUrl }
                } else {
                    state.productionUrl
                }
                if (base.isBlank()) {
                    showMessage("سابدامین workers.dev اکانت تنظیم نشده است یا ابتدا نسخه را آپلود کنید.")
                    return
                }
                val normalizedPath = if (state.httpPath.startsWith("/")) state.httpPath else "/${state.httpPath}"
                "$base$normalizedPath"
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingHttpTest = true, globalError = null) }
            when (
                val res = api.sendLiveHttpRequest(
                    fullUrl = fullUrl,
                    method = state.httpMethod,
                    headers = state.httpHeaders,
                    bodyText = state.httpRequestBody
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSendingHttpTest = false, httpTestResponse = res.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isSendingHttpTest = false, globalError = res.error) }
                }
            }
        }
    }

    fun startTailLogs() {
        val state = _uiState.value
        val acc = state.activeAccount
        val scriptName = state.editor.scriptName.trim()
        if (scriptName.isBlank()) {
            showMessage("نام ورکر را مشخص کنید")
            return
        }
        if (state.isTailConnected || state.isTailStarting) return

        val gen = ++tailSessionGeneration
        viewModelScope.launch {
            _uiState.update {
                it.copy(isTailStarting = true, tailStatusNote = "در حال ایجاد نشست لاگ زنده...")
            }
            when (val res = api.createTailSession(acc.accountId, acc.apiToken, scriptName)) {
                is ApiResult.Success -> {
                    val (tailId, wsUrl) = res.data
                    // Guard against user pressing Stop or switching accounts while session was creating
                    if (gen != tailSessionGeneration) {
                        api.deleteTailSession(acc.accountId, acc.apiToken, scriptName, tailId)
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            activeTailId = tailId,
                            activeTailScript = scriptName,
                            tailStatusNote = "در حال اتصال به WebSocket..."
                        )
                    }
                    activeTailSocket = api.openTailWebSocket(
                        wsUrl = wsUrl,
                        onConnected = {
                            if (gen == tailSessionGeneration) {
                                _uiState.update {
                                    it.copy(
                                        isTailStarting = false,
                                        isTailConnected = true,
                                        tailStatusNote = "متصل شد (trace-v1) — در انتظار ترافیک Production"
                                    )
                                }
                            }
                        },
                        onTraceEvent = { entry ->
                            if (gen == tailSessionGeneration) {
                                _uiState.update { st ->
                                    st.copy(tailLogs = (listOf(entry) + st.tailLogs).take(200))
                                }
                            }
                        },
                        onError = { errMsg ->
                            if (gen == tailSessionGeneration) {
                                _uiState.update {
                                    it.copy(
                                        isTailStarting = false,
                                        isTailConnected = false,
                                        tailStatusNote = "خطای WebSocket: $errMsg"
                                    )
                                }
                            }
                        },
                        onClosed = {
                            if (gen == tailSessionGeneration) {
                                _uiState.update {
                                    it.copy(
                                        isTailStarting = false,
                                        isTailConnected = false,
                                        tailStatusNote = "اتصال لاگ زنده بسته شد."
                                    )
                                }
                            }
                        }
                    )
                }
                is ApiResult.Error -> {
                    if (gen == tailSessionGeneration) {
                        _uiState.update {
                            it.copy(
                                isTailStarting = false,
                                tailStatusNote = res.error.persianSummary,
                                globalError = res.error
                            )
                        }
                    }
                }
            }
        }
    }

    fun stopTailLogs() {
        tailSessionGeneration++
        val tailId = _uiState.value.activeTailId
        val script = _uiState.value.activeTailScript
        val acc = _uiState.value.activeAccount

        activeTailSocket?.close(1000, "User stopped tail")
        activeTailSocket = null

        _uiState.update {
            it.copy(
                isTailStarting = false,
                isTailConnected = false,
                activeTailId = "",
                activeTailScript = "",
                tailStatusNote = "متوقف شد"
            )
        }

        if (tailId.isNotBlank() && script.isNotBlank() && acc.accountId.isNotBlank()) {
            viewModelScope.launch {
                api.deleteTailSession(acc.accountId, acc.apiToken, script, tailId)
            }
        }
    }

    fun clearTailLogs() {
        _uiState.update { it.copy(tailLogs = emptyList()) }
    }

    fun refreshVersionsAndDeployments(silent: Boolean = false) {
        val acc = _uiState.value.activeAccount
        val script = _uiState.value.editor.scriptName.trim()
        if (acc.accountId.isBlank() || script.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDeployHistory = true) }
            val vRes = api.listVersions(acc.accountId, acc.apiToken, script)
            val dRes = api.listDeployments(acc.accountId, acc.apiToken, script)
            val nextVersions = (vRes as? ApiResult.Success)?.data ?: _uiState.value.versions
            val latestVerId = _uiState.value.latestUploadedVersionId.ifBlank {
                nextVersions.firstOrNull()?.id.orEmpty()
            }
            _uiState.update {
                it.copy(
                    versions = nextVersions,
                    deployments = (dRes as? ApiResult.Success)?.data ?: it.deployments,
                    latestUploadedVersionId = latestVerId,
                    isLoadingDeployHistory = false,
                    globalError = if (!silent && vRes is ApiResult.Error) vRes.error else it.globalError
                )
            }
            updateComputedUrls()
        }
    }

    fun deploy100Percent(versionId: String) {
        if (versionId.isBlank()) {
            showMessage("نسخه‌ای برای انتشار انتخاب نشده است")
            return
        }
        val acc = _uiState.value.activeAccount
        val script = _uiState.value.editor.scriptName.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(isDeploying = true) }
            when (
                val res = api.deployVersions(
                    acc.accountId,
                    acc.apiToken,
                    script,
                    listOf(DeploymentVersionAllocation(versionId, 100))
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isDeploying = false, httpTargetMode = HttpTargetMode.PRODUCTION) }
                    refreshVersionsAndDeployments(silent = true)
                    showMessage("نسخه ${versionId.take(8)} با سهم ۱۰۰٪ روی Production منتشر شد")
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isDeploying = false, globalError = res.error) }
                }
            }
        }
    }

    fun deployCurrentCodeToProduction(onSuccessNavigate: (() -> Unit)? = null) {
        val acc = _uiState.value.activeAccount
        val editor = _uiState.value.editor
        if (editor.scriptName.isBlank()) {
            showMessage("ابتدا نام ورکر را وارد کنید")
            return
        }
        if (editor.code.isBlank()) {
            showMessage("کد ورکر نمی‌تواند خالی باشد")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isDeploying = true, isUploadingVersion = true, runnerError = null) }
            when (val uploadRes = api.uploadWorkerVersionSafe(acc.accountId, acc.apiToken, editor)) {
                is ApiResult.Success -> {
                    val versionId = uploadRes.data
                    _uiState.update {
                        it.copy(
                            isUploadingVersion = false,
                            latestUploadedVersionId = versionId
                        )
                    }
                    if (versionId.isBlank()) {
                        _uiState.update { it.copy(isDeploying = false, httpTargetMode = HttpTargetMode.PRODUCTION) }
                        updateComputedUrls()
                        refreshWorkersList()
                        refreshVersionsAndDeployments(silent = true)
                        showMessage("ورکر ${editor.scriptName} با موفقیت منتشر شد")
                        onSuccessNavigate?.invoke()
                        return@launch
                    }
                    when (
                        val depRes = api.deployVersions(
                            acc.accountId,
                            acc.apiToken,
                            editor.scriptName.trim(),
                            listOf(DeploymentVersionAllocation(versionId, 100))
                        )
                    ) {
                        is ApiResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isDeploying = false,
                                    httpTargetMode = HttpTargetMode.PRODUCTION
                                )
                            }
                            updateComputedUrls()
                            refreshWorkersList()
                            refreshVersionsAndDeployments(silent = true)
                            showMessage("کد با موفقیت روی لینک اصلی (Production) منتشر شد")
                            onSuccessNavigate?.invoke()
                        }
                        is ApiResult.Error -> {
                            _uiState.update { it.copy(isDeploying = false, runnerError = depRes.error, globalError = depRes.error) }
                        }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isDeploying = false, isUploadingVersion = false, runnerError = uploadRes.error, globalError = uploadRes.error)
                    }
                }
            }
        }
    }

    fun deployGradualSplit(versionA: String, percentageA: Int, versionB: String) {
        if (versionA.isBlank() || versionB.isBlank()) {
            showMessage("هر دو نسخه را برای انتشار تدریجی انتخاب کنید")
            return
        }
        val acc = _uiState.value.activeAccount
        val script = _uiState.value.editor.scriptName.trim()
        val pctA = percentageA.coerceIn(0, 100)
        val pctB = 100 - pctA
        val allocations = if (versionA == versionB) {
            listOf(DeploymentVersionAllocation(versionA, 100))
        } else {
            buildList {
                if (pctA > 0) add(DeploymentVersionAllocation(versionA, pctA))
                if (pctB > 0) add(DeploymentVersionAllocation(versionB, pctB))
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isDeploying = true) }
            when (val res = api.deployVersions(acc.accountId, acc.apiToken, script, allocations)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isDeploying = false) }
                    refreshVersionsAndDeployments(silent = true)
                    showMessage(
                        if (versionA == versionB) "نسخه با سهم ۱۰۰٪ منتشر شد"
                        else "انتشار تدریجی ($pctA% / $pctB%) با موفقیت ثبت شد"
                    )
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isDeploying = false, globalError = res.error) }
                }
            }
        }
    }

    fun rollbackToDeployment(deployment: WorkerDeploymentSummary) {
        if (deployment.allocations.isEmpty()) return
        val acc = _uiState.value.activeAccount
        val script = _uiState.value.editor.scriptName.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(isDeploying = true) }
            when (val res = api.deployVersions(acc.accountId, acc.apiToken, script, deployment.allocations)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isDeploying = false) }
                    refreshVersionsAndDeployments(silent = true)
                    showMessage("بازگشت (Rollback) به نسخه قبلی انجام شد")
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isDeploying = false, globalError = res.error) }
                }
            }
        }
    }

    // === Tab 3: D1 ===

    fun refreshD1(silent: Boolean = false) {
        val acc = _uiState.value.activeAccount
        if (acc.accountId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(d1Loading = true, d1Error = null) }
            when (val res = api.listD1Databases(acc.accountId, acc.apiToken)) {
                is ApiResult.Success -> {
                    val selected = _uiState.value.selectedD1?.let { sel ->
                        res.data.firstOrNull { it.uuid == sel.uuid }
                    } ?: res.data.firstOrNull()
                    _uiState.update {
                        it.copy(d1Databases = res.data, selectedD1 = selected, d1Loading = false)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            d1Loading = false,
                            d1Error = res.error,
                            globalError = if (!silent) res.error else it.globalError
                        )
                    }
                }
            }
        }
    }

    fun selectD1(db: D1Database) {
        _uiState.update { it.copy(selectedD1 = db, d1QueryResult = null, d1Error = null) }
    }

    fun updateD1SqlQuery(sql: String) {
        _uiState.update { it.copy(d1SqlQuery = sql) }
    }

    fun createD1Database(name: String) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(d1Loading = true, d1Error = null) }
            when (val res = api.createD1Database(acc.accountId, acc.apiToken, name)) {
                is ApiResult.Success -> {
                    showMessage("دیتابیس D1 ${res.data.name} ساخته شد")
                    refreshD1()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(d1Loading = false, d1Error = res.error) }
                }
            }
        }
    }

    fun deleteD1Database(dbId: String) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(d1Loading = true, d1Error = null) }
            when (val res = api.deleteD1Database(acc.accountId, acc.apiToken, dbId)) {
                is ApiResult.Success -> {
                    showMessage("دیتابیس D1 حذف شد")
                    _uiState.update { it.copy(selectedD1 = null, d1QueryResult = null) }
                    refreshD1()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(d1Loading = false, d1Error = res.error) }
                }
            }
        }
    }

    fun runD1Query(customSql: String? = null) {
        val db = _uiState.value.selectedD1 ?: run {
            showMessage("ابتدا یک دیتابیس D1 انتخاب کنید")
            return
        }
        val sql = (customSql ?: _uiState.value.d1SqlQuery).trim()
        if (sql.isBlank()) return
        if (customSql != null) {
            _uiState.update { it.copy(d1SqlQuery = customSql) }
        }
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(d1Loading = true, d1Error = null) }
            when (val res = api.queryD1Database(acc.accountId, acc.apiToken, db.uuid, sql)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(d1Loading = false, d1QueryResult = res.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(d1Loading = false, d1Error = res.error) }
                }
            }
        }
    }

    // === Tab 3: KV ===

    fun refreshKv(silent: Boolean = false) {
        val acc = _uiState.value.activeAccount
        if (acc.accountId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(kvLoading = true, kvError = null) }
            when (val res = api.listKvNamespaces(acc.accountId, acc.apiToken)) {
                is ApiResult.Success -> {
                    val selected = _uiState.value.selectedKv?.let { sel ->
                        res.data.firstOrNull { it.id == sel.id }
                    } ?: res.data.firstOrNull()
                    _uiState.update { it.copy(kvNamespaces = res.data, selectedKv = selected, kvLoading = false) }
                    if (!silent && selected != null) {
                        refreshKvKeys()
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            kvLoading = false,
                            kvError = res.error,
                            globalError = if (!silent) res.error else it.globalError
                        )
                    }
                }
            }
        }
    }

    fun selectKv(ns: KvNamespace) {
        _uiState.update {
            it.copy(
                selectedKv = ns,
                kvKeys = emptyList(),
                selectedKvKeyName = "",
                selectedKvKeyValue = "",
                selectedKvReadSeq = it.selectedKvReadSeq + 1
            )
        }
        refreshKvKeys()
    }

    fun updateKvPrefixFilter(prefix: String) {
        _uiState.update { it.copy(kvPrefixFilter = prefix) }
    }

    fun createKvNamespace(title: String) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(kvLoading = true, kvError = null) }
            when (val res = api.createKvNamespace(acc.accountId, acc.apiToken, title)) {
                is ApiResult.Success -> {
                    showMessage("فضای KV ${res.data.title} ساخته شد")
                    refreshKv()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(kvLoading = false, kvError = res.error) }
                }
            }
        }
    }

    fun deleteKvNamespace(nsId: String) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(kvLoading = true, kvError = null) }
            when (val res = api.deleteKvNamespace(acc.accountId, acc.apiToken, nsId)) {
                is ApiResult.Success -> {
                    showMessage("فضای KV حذف شد")
                    _uiState.update {
                        it.copy(
                            selectedKv = null,
                            inspectedKvNamespaceId = "",
                            kvKeys = emptyList(),
                            selectedKvKeyName = "",
                            selectedKvKeyValue = "",
                            selectedKvReadSeq = it.selectedKvReadSeq + 1
                        )
                    }
                    refreshKv()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(kvLoading = false, kvError = res.error) }
                }
            }
        }
    }

    fun refreshKvKeys() {
        val ns = _uiState.value.selectedKv ?: return
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(kvLoading = true, kvError = null, inspectedKvNamespaceId = ns.id) }
            when (val res = api.listKvKeys(acc.accountId, acc.apiToken, ns.id, _uiState.value.kvPrefixFilter)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(kvKeys = res.data, kvLoading = false, inspectedKvNamespaceId = ns.id) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(kvLoading = false, kvError = res.error) }
                }
            }
        }
    }

    fun readKvValue(key: String) {
        val ns = _uiState.value.selectedKv ?: return
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(kvLoading = true, kvError = null, selectedKvKeyName = key) }
            when (val res = api.readKvValue(acc.accountId, acc.apiToken, ns.id, key)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            kvLoading = false,
                            selectedKvKeyValue = res.data,
                            selectedKvReadSeq = it.selectedKvReadSeq + 1
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(kvLoading = false, kvError = res.error) }
                }
            }
        }
    }

    fun writeKvValue(key: String, value: String) {
        val ns = _uiState.value.selectedKv ?: return
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(kvLoading = true, kvError = null) }
            when (val res = api.writeKvValue(acc.accountId, acc.apiToken, ns.id, key, value)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            selectedKvKeyName = key,
                            selectedKvKeyValue = value,
                            selectedKvReadSeq = it.selectedKvReadSeq + 1
                        )
                    }
                    showMessage("کلید $key در KV ذخیره شد")
                    refreshKvKeys()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(kvLoading = false, kvError = res.error) }
                }
            }
        }
    }

    fun deleteKvKey(key: String) {
        val ns = _uiState.value.selectedKv ?: return
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(kvLoading = true, kvError = null) }
            when (val res = api.deleteKvKey(acc.accountId, acc.apiToken, ns.id, key)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            selectedKvKeyName = "",
                            selectedKvKeyValue = "",
                            selectedKvReadSeq = it.selectedKvReadSeq + 1
                        )
                    }
                    showMessage("کلید $key حذف شد")
                    refreshKvKeys()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(kvLoading = false, kvError = res.error) }
                }
            }
        }
    }

    // === Tab 3: R2 ===

    fun refreshR2(silent: Boolean = false) {
        val acc = _uiState.value.activeAccount
        if (acc.accountId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(r2Loading = true, r2Error = null) }
            when (val res = api.listR2Buckets(acc.accountId, acc.apiToken)) {
                is ApiResult.Success -> {
                    val selected = _uiState.value.selectedR2?.let { sel ->
                        res.data.firstOrNull { it.name == sel.name }
                    } ?: res.data.firstOrNull()
                    _uiState.update { it.copy(r2Buckets = res.data, selectedR2 = selected, r2Loading = false) }
                    if (!silent && selected != null) {
                        inspectR2Bucket(selected)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            r2Loading = false,
                            r2Error = res.error,
                            globalError = if (!silent) res.error else it.globalError
                        )
                    }
                }
            }
        }
    }

    fun inspectR2Bucket(bucket: R2Bucket) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedR2 = bucket,
                    inspectedR2BucketName = bucket.name,
                    r2Loading = true,
                    r2Error = null,
                    r2Objects = emptyList(),
                    r2ManagedDomain = null,
                    r2CorsJson = ""
                )
            }
            val objRes = api.listR2Objects(acc.accountId, acc.apiToken, bucket.name)
            val domRes = api.getR2ManagedDomain(acc.accountId, acc.apiToken, bucket.name)
            val corsRes = api.getR2Cors(acc.accountId, acc.apiToken, bucket.name)

            _uiState.update {
                it.copy(
                    r2Loading = false,
                    inspectedR2BucketName = bucket.name,
                    r2Objects = (objRes as? ApiResult.Success)?.data ?: emptyList(),
                    r2ManagedDomain = (domRes as? ApiResult.Success)?.data,
                    r2CorsJson = (corsRes as? ApiResult.Success)?.data ?: "",
                    r2Error = (objRes as? ApiResult.Error)?.error
                )
            }
        }
    }

    fun createR2Bucket(name: String) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(r2Loading = true, r2Error = null) }
            when (val res = api.createR2Bucket(acc.accountId, acc.apiToken, name)) {
                is ApiResult.Success -> {
                    showMessage("باکت R2 ساخته شد")
                    refreshR2()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(r2Loading = false, r2Error = res.error) }
                }
            }
        }
    }

    fun deleteR2Bucket(name: String) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(r2Loading = true, r2Error = null) }
            when (val res = api.deleteR2Bucket(acc.accountId, acc.apiToken, name)) {
                is ApiResult.Success -> {
                    showMessage("باکت R2 حذف شد")
                    _uiState.update {
                        it.copy(
                            selectedR2 = null,
                            inspectedR2BucketName = "",
                            r2Objects = emptyList(),
                            r2ManagedDomain = null,
                            r2CorsJson = ""
                        )
                    }
                    refreshR2()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(r2Loading = false, r2Error = res.error) }
                }
            }
        }
    }

    fun toggleR2ManagedDomain(enabled: Boolean) {
        val bucket = _uiState.value.selectedR2 ?: return
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(r2Loading = true, r2Error = null) }
            when (val res = api.setR2ManagedDomain(acc.accountId, acc.apiToken, bucket.name, enabled)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(r2Loading = false, r2ManagedDomain = res.data) }
                    showMessage(if (enabled) "دسترسی عمومی r2.dev فعال شد" else "دسترسی عمومی r2.dev غیرفعال شد")
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(r2Loading = false, r2Error = res.error) }
                }
            }
        }
    }

    // === Tab 3: Queues & Durable Objects ===

    fun refreshQueuesAndDo(silent: Boolean = false) {
        val acc = _uiState.value.activeAccount
        if (acc.accountId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(queuesDoLoading = true, queuesDoError = null) }
            val qRes = api.listQueues(acc.accountId, acc.apiToken)
            val doRes = api.listDurableObjectNamespaces(acc.accountId, acc.apiToken)
            _uiState.update {
                it.copy(
                    queuesDoLoading = false,
                    queues = (qRes as? ApiResult.Success)?.data ?: it.queues,
                    doNamespaces = (doRes as? ApiResult.Success)?.data ?: it.doNamespaces,
                    queuesDoError = if (!silent) {
                        (qRes as? ApiResult.Error)?.error ?: (doRes as? ApiResult.Error)?.error
                    } else null
                )
            }
        }
    }

    fun createQueue(name: String) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(queuesDoLoading = true, queuesDoError = null) }
            when (val res = api.createQueue(acc.accountId, acc.apiToken, name)) {
                is ApiResult.Success -> {
                    showMessage("صف $name ساخته شد")
                    refreshQueuesAndDo()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(queuesDoLoading = false, queuesDoError = res.error) }
                }
            }
        }
    }

    fun deleteQueue(queueId: String) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(queuesDoLoading = true, queuesDoError = null) }
            when (val res = api.deleteQueue(acc.accountId, acc.apiToken, queueId)) {
                is ApiResult.Success -> {
                    showMessage("صف حذف شد")
                    refreshQueuesAndDo()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(queuesDoLoading = false, queuesDoError = res.error) }
                }
            }
        }
    }

    fun inspectDoNamespace(ns: DurableObjectNamespace) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(selectedDoNamespace = ns, queuesDoLoading = true, doInstances = emptyList()) }
            when (val res = api.listDurableObjects(acc.accountId, acc.apiToken, ns.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(queuesDoLoading = false, doInstances = res.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(queuesDoLoading = false, queuesDoError = res.error) }
                }
            }
        }
    }

    // === Tab 4: Secrets, Cron, Subdomain, Domains, Zones & Routes, Script Settings ===

    fun refreshSettingsTab() {
        val acc = _uiState.value.activeAccount
        val script = _uiState.value.editor.scriptName.trim()
        if (acc.accountId.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    settingsLoading = true,
                    settingsError = null,
                    secrets = emptyList(),
                    cronSchedules = emptyList(),
                    scriptSubdomainStatus = ScriptSubdomainStatus(),
                    scriptSettings = ScriptSettingsInfo()
                )
            }
            val domainsRes = api.listCustomDomains(acc.accountId, acc.apiToken)
            val zonesRes = api.listZones(acc.apiToken)

            var firstError: ApiErrorInfo? = (domainsRes as? ApiResult.Error)?.error
                ?: (zonesRes as? ApiResult.Error)?.error

            var secretsList = emptyList<SecretEntry>()
            var cronsList = emptyList<CronSchedule>()
            var subStatus = ScriptSubdomainStatus()
            var scriptSettings = ScriptSettingsInfo()

            if (script.isNotBlank()) {
                when (val secRes = api.listSecrets(acc.accountId, acc.apiToken, script)) {
                    is ApiResult.Success -> secretsList = secRes.data
                    is ApiResult.Error -> if (firstError == null && secRes.error.httpStatus != 404 && secRes.error.code != 10007) firstError = secRes.error
                }
                when (val cronRes = api.getCronSchedules(acc.accountId, acc.apiToken, script)) {
                    is ApiResult.Success -> cronsList = cronRes.data
                    is ApiResult.Error -> if (firstError == null && cronRes.error.httpStatus != 404 && cronRes.error.code != 10007) firstError = cronRes.error
                }
                when (val subRes = api.getWorkerSubdomainRouting(acc.accountId, acc.apiToken, script)) {
                    is ApiResult.Success -> subStatus = subRes.data
                    is ApiResult.Error -> if (firstError == null && subRes.error.httpStatus != 404 && subRes.error.code != 10007) firstError = subRes.error
                }
                when (val setRes = api.getScriptSettings(acc.accountId, acc.apiToken, script)) {
                    is ApiResult.Success -> scriptSettings = setRes.data
                    is ApiResult.Error -> if (firstError == null && setRes.error.httpStatus != 404 && setRes.error.code != 10007) firstError = setRes.error
                }
            }

            val zones = (zonesRes as? ApiResult.Success)?.data ?: emptyList()
            val selectedZone = _uiState.value.selectedZone?.let { cur -> zones.firstOrNull { it.id == cur.id } } ?: zones.firstOrNull()
            val routes = if (selectedZone != null) {
                when (val rRes = api.listZoneRoutes(acc.apiToken, selectedZone.id)) {
                    is ApiResult.Success -> rRes.data
                    is ApiResult.Error -> {
                        if (firstError == null) firstError = rRes.error
                        emptyList()
                    }
                }
            } else emptyList()

            _uiState.update {
                it.copy(
                    settingsLoading = false,
                    settingsError = firstError,
                    customDomains = (domainsRes as? ApiResult.Success)?.data ?: it.customDomains,
                    zones = zones,
                    selectedZone = selectedZone,
                    zoneRoutes = routes,
                    secrets = secretsList,
                    cronSchedules = cronsList,
                    scriptSubdomainStatus = subStatus,
                    scriptSettings = scriptSettings
                )
            }
        }
    }

    fun saveSecret(key: String, value: String) {
        val acc = _uiState.value.activeAccount
        val script = _uiState.value.editor.scriptName.trim()
        if (script.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsLoading = true, settingsError = null) }
            when (val res = api.patchSecretsBulk(acc.accountId, acc.apiToken, script, key, value)) {
                is ApiResult.Success -> {
                    showMessage("راز $key با موفقیت رمزنگاری و ذخیره شد")
                    refreshSettingsTab()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(settingsLoading = false, settingsError = res.error) }
                }
            }
        }
    }

    fun deleteSecret(secretName: String) {
        val acc = _uiState.value.activeAccount
        val script = _uiState.value.editor.scriptName.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(settingsLoading = true, settingsError = null) }
            when (val res = api.deleteSecret(acc.accountId, acc.apiToken, script, secretName)) {
                is ApiResult.Success -> {
                    showMessage("راز $secretName حذف شد")
                    refreshSettingsTab()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(settingsLoading = false, settingsError = res.error) }
                }
            }
        }
    }

    fun updateCrons(crons: List<String>) {
        val acc = _uiState.value.activeAccount
        val script = _uiState.value.editor.scriptName.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(settingsLoading = true, settingsError = null) }
            when (val res = api.updateCronSchedules(acc.accountId, acc.apiToken, script, crons)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(settingsLoading = false, cronSchedules = res.data) }
                    showMessage("زمان‌بندی‌های Cron ذخیره شد")
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(settingsLoading = false, settingsError = res.error) }
                }
            }
        }
    }

    fun updateScriptSubdomainToggles(enabled: Boolean, previewsEnabled: Boolean) {
        val acc = _uiState.value.activeAccount
        val script = _uiState.value.editor.scriptName.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(settingsLoading = true, settingsError = null) }
            when (val res = api.ensureWorkerSubdomainRouting(acc.accountId, acc.apiToken, script, enabled, previewsEnabled)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(settingsLoading = false, scriptSubdomainStatus = res.data) }
                    showMessage("تنظیمات workers.dev بروزرسانی شد")
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(settingsLoading = false, settingsError = res.error) }
                }
            }
        }
    }

    fun attachCustomDomain(hostname: String, service: String, zoneId: String) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(settingsLoading = true, settingsError = null) }
            when (val res = api.attachCustomDomain(acc.accountId, acc.apiToken, hostname, service, zoneId)) {
                is ApiResult.Success -> {
                    showMessage("دامنه $hostname متصل شد")
                    refreshSettingsTab()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(settingsLoading = false, settingsError = res.error) }
                }
            }
        }
    }

    fun detachCustomDomain(domainId: String) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(settingsLoading = true, settingsError = null) }
            when (val res = api.detachCustomDomain(acc.accountId, acc.apiToken, domainId)) {
                is ApiResult.Success -> {
                    showMessage("اتصال دامنه حذف شد")
                    refreshSettingsTab()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(settingsLoading = false, settingsError = res.error) }
                }
            }
        }
    }

    fun selectZone(zone: ZoneInfo) {
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(selectedZone = zone, settingsLoading = true) }
            when (val res = api.listZoneRoutes(acc.apiToken, zone.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(zoneRoutes = res.data, settingsLoading = false) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(settingsLoading = false, settingsError = res.error) }
                }
            }
        }
    }

    fun createZoneRoute(pattern: String, scriptName: String) {
        val zone = _uiState.value.selectedZone ?: return
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(settingsLoading = true, settingsError = null) }
            when (val res = api.createZoneRoute(acc.apiToken, zone.id, pattern, scriptName)) {
                is ApiResult.Success -> {
                    showMessage("مسیر $pattern اضافه شد")
                    selectZone(zone)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(settingsLoading = false, settingsError = res.error) }
                }
            }
        }
    }

    fun deleteZoneRoute(routeId: String) {
        val zone = _uiState.value.selectedZone ?: return
        val acc = _uiState.value.activeAccount
        viewModelScope.launch {
            _uiState.update { it.copy(settingsLoading = true, settingsError = null) }
            when (val res = api.deleteZoneRoute(acc.apiToken, zone.id, routeId)) {
                is ApiResult.Success -> {
                    showMessage("مسیر حذف شد")
                    selectZone(zone)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(settingsLoading = false, settingsError = res.error) }
                }
            }
        }
    }

    fun patchScriptSettings(observabilityEnabled: Boolean, logpushEnabled: Boolean) {
        val acc = _uiState.value.activeAccount
        val script = _uiState.value.editor.scriptName.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(settingsLoading = true, settingsError = null) }
            when (
                val res = api.patchScriptSettings(
                    acc.accountId,
                    acc.apiToken,
                    script,
                    observabilityEnabled,
                    logpushEnabled
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(settingsLoading = false, scriptSettings = res.data) }
                    showMessage("تنظیمات اسکریپت ذخیره شد")
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(settingsLoading = false, settingsError = res.error) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeTailSocket?.close(1000, "ViewModel cleared")
        activeTailSocket = null
    }
}
