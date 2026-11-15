package com.example

import com.example.data.WorkerCodeUtils
import com.example.model.BindingType
import com.example.model.WorkerBinding
import com.example.model.WorkerEditorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerCodeUtilsTest {

    @Test
    fun `validateWorkerUpload accepts valid module worker and bindings`() {
        val state = WorkerEditorState(
            scriptName = "my-edge_worker-1",
            code = "export default { fetch() { return new Response('ok'); } }",
            compatibilityDate = "2026-11-15",
            bindings = listOf(
                WorkerBinding(uid = "1", type = BindingType.PLAIN_TEXT, name = "APP_ENV", textValue = "prod"),
                WorkerBinding(uid = "2", type = BindingType.D1, name = "DB", databaseId = "uuid-123"),
                WorkerBinding(uid = "3", type = BindingType.AI, name = "AI")
            )
        )
        assertNull(WorkerCodeUtils.validateWorkerUpload(state))
    }

    @Test
    fun `validateWorkerUpload rejects invalid script names, dates, duplicate and incomplete bindings`() {
        val badName = WorkerEditorState(scriptName = "My Worker!", code = "export default {}")
        assertNotNull(WorkerCodeUtils.validateWorkerUpload(badName))

        val badDate = WorkerEditorState(
            scriptName = "valid-worker",
            code = "export default {}",
            compatibilityDate = "15-11-2026"
        )
        assertNotNull(WorkerCodeUtils.validateWorkerUpload(badDate))

        val badBindingVar = WorkerEditorState(
            scriptName = "valid-worker",
            code = "export default {}",
            bindings = listOf(WorkerBinding("1", BindingType.PLAIN_TEXT, "1INVALID", textValue = "x"))
        )
        assertNotNull(WorkerCodeUtils.validateWorkerUpload(badBindingVar))

        val dupBinding = WorkerEditorState(
            scriptName = "valid-worker",
            code = "export default {}",
            bindings = listOf(
                WorkerBinding("1", BindingType.PLAIN_TEXT, "CFG", textValue = "a"),
                WorkerBinding("2", BindingType.PLAIN_TEXT, "CFG", textValue = "b")
            )
        )
        assertNotNull(WorkerCodeUtils.validateWorkerUpload(dupBinding))

        val missingD1Id = WorkerEditorState(
            scriptName = "valid-worker",
            code = "export default {}",
            bindings = listOf(WorkerBinding("1", BindingType.D1, "DB", databaseId = "  "))
        )
        assertTrue(WorkerCodeUtils.validateWorkerUpload(missingD1Id)?.contains("Database UUID") == true)
    }

    @Test
    fun `isServiceWorkerSyntax distinguishes service worker from esm and ignores comments`() {
        val esmWithComment = """
            // addEventListener('fetch', () => {})
            export default {
              async fetch(request) { return new Response("ok"); }
            };
        """.trimIndent()
        assertFalse(WorkerCodeUtils.isServiceWorkerSyntax(esmWithComment))

        val swWithComment = """
            /* export default { fetch() {} } */
            addEventListener('fetch', (event) => {
              event.respondWith(new Response('hello'));
            });
        """.trimIndent()
        assertTrue(WorkerCodeUtils.isServiceWorkerSyntax(swWithComment))
    }

    @Test
    fun `hasStaticEsmImports detects real imports and ignores string literals`() {
        val withImport = """
            import { Hono } from 'hono';
            export default new Hono();
        """.trimIndent()
        assertTrue(WorkerCodeUtils.hasStaticEsmImports(withImport))

        val stringOnly = """
            export default {
              fetch() {
                const doc = "import { Hono } from 'hono';";
                return new Response(doc);
              }
            };
        """.trimIndent()
        assertFalse(WorkerCodeUtils.hasStaticEsmImports(stringOnly))
    }

    @Test
    fun `transformModuleForLocalSandbox preserves strings containing export default`() {
        val src = """
            export const GREETING = "do not export default here";
            export default {
              async fetch() {
                return new Response(GREETING);
              }
            };
        """.trimIndent()

        val transformed = WorkerCodeUtils.transformModuleForLocalSandbox(src)
        assertTrue(transformed.contains("const GREETING = \"do not export default here\";"))
        assertTrue(transformed.contains("const __setupCloudDefaultExport ="))
        assertFalse(transformed.contains("const __setupCloudDefaultExport =  here"))
    }

    @Test
    fun `formatJavaScript handles braces inside strings and comments accurately`() {
        val messy = """
            export default {
            async fetch(req) {
            const msg = "brace { test {{{";
            // trailing comment }}}}
            return Response.json({
            ok: true,
            msg
            });
            }
            };
        """.trimIndent()

        val formatted = WorkerCodeUtils.formatJavaScript(messy)
        val lines = formatted.lines()
        assertEquals("export default {", lines[0])
        assertEquals("  async fetch(req) {", lines[1])
        assertEquals("    const msg = \"brace { test {{{\";", lines[2])
        assertEquals("    // trailing comment }}}}", lines[3])
        assertEquals("    return Response.json({", lines[4])
        assertEquals("      ok: true,", lines[5])
        assertEquals("      msg", lines[6])
        assertEquals("    });", lines[7])
        assertEquals("  }", lines[8])
        assertEquals("};", lines[9])
    }
}
