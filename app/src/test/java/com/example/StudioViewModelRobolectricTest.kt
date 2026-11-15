package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.CloudflareApiClient
import com.example.model.BindingType
import com.example.model.HttpTargetMode
import com.example.model.StorageSubTab
import com.example.model.StudioTab
import com.example.model.WorkerBinding
import com.example.ui.StudioViewModel
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudioViewModelRobolectricTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var server: HttpServer
    private lateinit var api: CloudflareApiClient
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.rawPath
            val method = exchange.requestMethod
            val (code, body) = when {
                path.endsWith("/tokens/verify") ->
                    200 to """{"success":true,"result":{"status":"active"}}"""
                path.endsWith("/workers/subdomain") ->
                    200 to """{"success":true,"result":{"subdomain":"edge-studio"}}"""
                method == "GET" && path.endsWith("/workers/scripts") ->
                    200 to """{"success":true,"result":[{"id":"api-worker","modified_on":"2026-11-15T10:00:00Z"}]}"""
                method == "GET" && path.endsWith("/workers/scripts/api-worker") ->
                    200 to "export default { async fetch(req, env) { return new Response('live'); } };"
                method == "GET" && path.endsWith("/workers/scripts/api-worker/versions") ->
                    200 to """{"success":true,"result":{"items":[{"id":"ver-11112222","number":1,"created_on":"2026-11-15T10:00:00Z"}]}}"""
                method == "GET" && path.endsWith("/workers/scripts/api-worker/versions/ver-11112222") ->
                    200 to """{
                        "success":true,
                        "result":{
                            "id":"ver-11112222",
                            "compatibility_date":"2026-10-01",
                            "compatibility_flags":["nodejs_compat"],
                            "placement":{"mode":"smart"},
                            "observability":{"enabled":false},
                            "bindings":[{"type":"plain_text","name":"MODE","text":"prod"}]
                        }
                    }"""
                method == "GET" && path.endsWith("/workers/scripts/api-worker/deployments") ->
                    200 to """{"success":true,"result":{"deployments":[]}}"""
                method == "DELETE" && path.endsWith("/workers/scripts/api-worker") ->
                    200 to """{"success":true,"result":{}}"""
                path.endsWith("/d1/database") ->
                    200 to """{"success":true,"result":[{"uuid":"d1-uuid-1","name":"main-db"}]}"""
                path.endsWith("/storage/kv/namespaces") ->
                    200 to """{"success":true,"result":[{"id":"kv-ns-1","title":"CACHE_KV"}]}"""
                path.endsWith("/storage/kv/namespaces/kv-ns-1/keys") ->
                    200 to """{"success":true,"result":[{"name":"session:abc"}]}"""
                path.endsWith("/r2/buckets") ->
                    200 to """{"success":true,"result":{"buckets":[{"name":"assets-bucket","location":"WEUR"}]}}"""
                path.endsWith("/r2/buckets/assets-bucket/objects") ->
                    200 to """{"success":true,"result":[{"key":"logo.png","size":2048}]}"""
                path.endsWith("/r2/buckets/assets-bucket/domains/managed") ->
                    200 to """{"success":true,"result":{"enabled":true,"domain":"pub-test.r2.dev"}}"""
                path.endsWith("/r2/buckets/assets-bucket/cors") ->
                    200 to """{"success":true,"result":[]}"""
                path.endsWith("/queues") ->
                    200 to """{"success":true,"result":[]}"""
                path.endsWith("/durable_objects/namespaces") ->
                    200 to """{"success":true,"result":[]}"""
                else ->
                    200 to """{"success":true,"result":{}}"""
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        api = CloudflareApiClient(
            baseApiUrl = "http://127.0.0.1:${server.address.port}/client/v4",
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.stop(0)
    }

    @Test
    fun `opening storage KV and R2 auto-inspects keys and objects after silent bootstrap`() = runTest(testDispatcher) {
        val vm = StudioViewModel(app, api)
        advanceUntilIdle()

        // Bootstrap populated namespaces and buckets, but keys/objects were deferred until tab open
        assertEquals("kv-ns-1", vm.uiState.value.selectedKv?.id)
        assertTrue(vm.uiState.value.kvKeys.isEmpty())
        assertEquals("assets-bucket", vm.uiState.value.selectedR2?.name)
        assertTrue(vm.uiState.value.r2Objects.isEmpty())

        // Opening Storage -> KV automatically fetches keys
        vm.selectStorageSubTab(StorageSubTab.KV)
        vm.selectTab(StudioTab.STORAGE)
        advanceUntilIdle()
        assertEquals(listOf("session:abc"), vm.uiState.value.kvKeys.map { it.name })

        // Switching to R2 automatically inspects objects and managed r2.dev domain
        vm.selectStorageSubTab(StorageSubTab.R2)
        advanceUntilIdle()
        assertEquals(listOf("logo.png"), vm.uiState.value.r2Objects.map { it.key })
        assertEquals("pub-test.r2.dev", vm.uiState.value.r2ManagedDomain?.domain)
    }

    @Test
    fun `loadWorkerIntoEditor restores bindings and flags and deleteWorker cleans active urls`() = runTest(testDispatcher) {
        val vm = StudioViewModel(app, api)
        advanceUntilIdle()

        vm.loadWorkerIntoEditor("api-worker")
        advanceUntilIdle()

        val editor = vm.uiState.value.editor
        assertEquals("api-worker", editor.scriptName)
        assertEquals("2026-10-01", editor.compatibilityDate)
        assertTrue(editor.nodejsCompat)
        assertTrue(editor.smartPlacement)
        assertFalse(editor.observabilityEnabled)
        assertEquals(1, editor.bindings.size)
        assertEquals("MODE", editor.bindings.first().name)
        assertEquals(BindingType.PLAIN_TEXT, editor.bindings.first().type)
        assertEquals("https://ver-1111-api-worker.edge-studio.workers.dev", vm.uiState.value.previewUrl)
        assertEquals("https://api-worker.edge-studio.workers.dev", vm.uiState.value.productionUrl)

        vm.deleteWorker("api-worker")
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.latestUploadedVersionId)
        assertEquals("", vm.uiState.value.previewUrl)
        assertEquals("", vm.uiState.value.productionUrl)
        assertTrue(vm.uiState.value.versions.isEmpty())
    }

    @Test
    fun `importCodeFromText, formatEditorCode, and testCustomUrlInDeployTab update editor and navigation state`() = runTest(testDispatcher) {
        val vm = StudioViewModel(app, api)
        advanceUntilIdle()

        vm.importCodeFromText(
            rawCode = "export default {\nfetch() {\nreturn new Response(\"{hello}\");\n}\n};",
            suggestedName = "My_Edge_Worker.mjs",
            sourceLabel = "فایل"
        )
        assertEquals("my_edge_worker", vm.uiState.value.editor.scriptName)
        assertNull(vm.uiState.value.runnerError)

        vm.formatEditorCode()
        val formatted = vm.uiState.value.editor.code
        assertTrue(formatted.contains("  fetch() {"))
        assertTrue(formatted.contains("    return new Response(\"{hello}\");"))

        vm.testCustomUrlInDeployTab("api.customdomain.ir/ping")
        advanceUntilIdle()
        assertEquals(StudioTab.DEPLOY_TEST, vm.uiState.value.activeTab)
        assertEquals(HttpTargetMode.CUSTOM_URL, vm.uiState.value.httpTargetMode)
        assertEquals("https://api.customdomain.ir/ping", vm.uiState.value.httpCustomUrl)
    }
}
