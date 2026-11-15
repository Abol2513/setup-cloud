package com.example

import com.example.data.CloudflareApiClient
import com.example.model.ApiResult
import com.example.model.BindingType
import com.example.model.WorkerBinding
import com.example.model.WorkerEditorState
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudflareApiClientTest {

    private lateinit var server: HttpServer
    private lateinit var client: CloudflareApiClient
    private val recordedRequests = CopyOnWriteArrayList<Pair<String, String>>()

    @Volatile
    private var handlerBlock: (method: String, pathWithQuery: String, body: String) -> Pair<Int, String> =
        { _, _, _ -> 200 to """{"success":true,"result":{}}""" }

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
            val uri = exchange.requestURI
            val fullPath = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
            recordedRequests.add(exchange.requestMethod to fullPath)
            val (status, respBody) = handlerBlock(exchange.requestMethod, fullPath, body)
            val bytes = respBody.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val baseUrl = "http://127.0.0.1:${server.address.port}/client/v4"
        client = CloudflareApiClient(baseApiUrl = baseUrl)
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `verifyToken automatically falls back to user verify endpoint when account verify returns 403`() = runTest {
        handlerBlock = { _, path, _ ->
            when (path) {
                "/client/v4/accounts/acc123/tokens/verify" ->
                    403 to """{"success":false,"errors":[{"code":10000,"message":"Authentication error"}]}"""
                "/client/v4/user/tokens/verify" ->
                    200 to """{"success":true,"result":{"status":"active"}}"""
                else -> 404 to """{"success":false,"errors":[{"code":10007,"message":"Not found"}]}"""
            }
        }

        val res = client.verifyToken("acc123", "user-token")
        assertTrue(res is ApiResult.Success)
        assertEquals("active", (res as ApiResult.Success).data)
        assertEquals(
            listOf(
                "GET" to "/client/v4/accounts/acc123/tokens/verify",
                "GET" to "/client/v4/user/tokens/verify"
            ),
            recordedRequests.toList()
        )
    }

    @Test
    fun `getWorkersSubdomain returns empty string cleanly on 404`() = runTest {
        handlerBlock = { _, _, _ ->
            404 to """{"success":false,"errors":[{"code":10007,"message":"workers.dev subdomain not found"}]}"""
        }

        val res = client.getWorkersSubdomain("acc123", "tok")
        assertTrue(res is ApiResult.Success)
        assertEquals("", (res as ApiResult.Success).data)
    }

    @Test
    fun `getScriptSettings returns default flags cleanly on 404`() = runTest {
        handlerBlock = { _, _, _ ->
            404 to """{"success":false,"errors":[{"code":10007,"message":"script not found"}]}"""
        }

        val res = client.getScriptSettings("acc123", "tok", "new-worker")
        assertTrue(res is ApiResult.Success)
        val info = (res as ApiResult.Success).data
        assertEquals(false, info.observabilityEnabled)
        assertEquals(false, info.logpushEnabled)
    }

    @Test
    fun `uploadWorkerVersionSafe stops invalid bindings before making network request`() = runTest {
        val badEditor = WorkerEditorState(
            scriptName = "good-name",
            code = "export default { fetch() { return new Response('hi'); } }",
            bindings = listOf(WorkerBinding(uid = "1", type = BindingType.KV, name = "KV", namespaceId = ""))
        )

        val res = client.uploadWorkerVersionSafe("acc123", "tok", badEditor)
        assertTrue(res is ApiResult.Error)
        assertTrue(recordedRequests.isEmpty())
    }

    @Test
    fun `uploadWorkerVersionSafe falls back to PUT script on 404 and enables subdomain routing`() = runTest {
        handlerBlock = { method, path, _ ->
            when {
                method == "POST" && path == "/client/v4/accounts/acc123/workers/scripts/hello-w/versions" ->
                    404 to """{"success":false,"errors":[{"code":10007,"message":"script not found"}]}"""
                method == "PUT" && path == "/client/v4/accounts/acc123/workers/scripts/hello-w" ->
                    200 to """{"success":true,"result":{"id":"hello-w","latest_version_id":"ver-9988"}}"""
                method == "POST" && path == "/client/v4/accounts/acc123/workers/scripts/hello-w/subdomain" ->
                    200 to """{"success":true,"result":{"enabled":true,"previews_enabled":true}}"""
                else -> 404 to """{"success":false}"""
            }
        }

        val editor = WorkerEditorState(
            scriptName = "hello-w",
            code = "export default { fetch() { return new Response('ok'); } }"
        )
        val res = client.uploadWorkerVersionSafe("acc123", "tok", editor)
        assertTrue(res is ApiResult.Success)
        assertEquals("ver-9988", (res as ApiResult.Success).data)
        assertEquals(3, recordedRequests.size)
    }

    @Test
    fun `queryD1Database aggregates multi-statement SQL results and metadata`() = runTest {
        handlerBlock = { _, _, _ ->
            200 to """
                {
                  "success": true,
                  "result": [
                    {
                      "results": [],
                      "meta": { "duration": 0.45, "changes": 1, "rows_read": 0 }
                    },
                    {
                      "results": [
                        { "id": 1, "title": "alpha" },
                        { "id": 2, "title": "beta" }
                      ],
                      "meta": { "duration": 1.25, "changes": 0, "rows_read": 2 }
                    }
                  ]
                }
            """.trimIndent()
        }

        val res = client.queryD1Database("acc123", "tok", "db-1", "INSERT INTO items VALUES (1,'a'); SELECT * FROM items;")
        assertTrue(res is ApiResult.Success)
        val data = (res as ApiResult.Success).data
        assertEquals(listOf("id", "title"), data.columns)
        assertEquals(2, data.rows.size)
        assertEquals(listOf("1", "alpha"), data.rows[0])
        assertTrue(data.metaSummary.contains("2 دستور"))
        assertTrue(data.metaSummary.contains("تغییرات: 1"))
        assertTrue(data.metaSummary.contains("1.70ms"))
    }

    @Test
    fun `parseErrorResponse provides KV scope hint on 403 permission error`() = runTest {
        handlerBlock = { _, _, _ ->
            403 to """{"success":false,"errors":[{"code":10000,"message":"Authentication error"}]}"""
        }

        val res = client.listKvNamespaces("acc123", "tok")
        assertTrue(res is ApiResult.Error)
        val err = (res as ApiResult.Error).error
        assertTrue(err.persianSummary.contains("Workers KV Storage: Edit"))
    }
}
