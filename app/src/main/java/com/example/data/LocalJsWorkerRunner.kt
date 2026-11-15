package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.model.BindingType
import com.example.model.LocalRunResult
import com.example.model.WorkerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

class LocalJsWorkerRunner(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun execute(
        code: String,
        bindings: List<WorkerBinding>,
        method: String,
        path: String,
        body: String
    ): LocalRunResult {
        if (code.isBlank()) {
            return LocalRunResult(
                statusCode = 400,
                statusText = "Empty Code",
                headers = emptyList(),
                body = "",
                consoleLogs = emptyList(),
                durationMs = 0L,
                errorMessage = "کد ورکر خالی است."
            )
        }
        if (WorkerCodeUtils.hasStaticEsmImports(code)) {
            return LocalRunResult(
                statusCode = 422,
                statusText = "Unsupported Static Import",
                headers = emptyList(),
                body = "",
                consoleLogs = emptyList(),
                durationMs = 0L,
                errorMessage = "اجرای محلی از دستورهای static import (پکیج‌های npm یا ماژول‌های خارجی) پشتیبانی نمی‌کند. لطفاً کد باندل‌شده وارد کنید یا از حالت «اجرای زنده کلودفلر» استفاده نمایید."
            )
        }

        return withContext(Dispatchers.Main) {
            val startMs = System.currentTimeMillis()
            val result = withTimeoutOrNull(6500L) {
                suspendCancellableCoroutine<LocalRunResult> { cont ->
                    val webView = WebView(context.applicationContext)
                    webView.settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        domStorageEnabled = false
                        cacheMode = WebSettings.LOAD_NO_CACHE
                    }

                    var settled = false
                    fun disposeWebView() {
                        runCatching {
                            webView.stopLoading()
                            webView.removeJavascriptInterface("AndroidRunnerBridge")
                            webView.destroy()
                        }
                    }

                    fun finish(res: LocalRunResult) {
                        if (!settled) {
                            settled = true
                            disposeWebView()
                            if (cont.isActive) cont.resume(res)
                        }
                    }

                    val bridge = object {
                        @JavascriptInterface
                        fun onComplete(jsonPayload: String) {
                            val elapsed = System.currentTimeMillis() - startMs
                            try {
                                val obj = JSONObject(jsonPayload)
                                val ok = obj.optBoolean("ok", false)
                                val status = obj.optInt("status", if (ok) 200 else 500)
                                val statusText = obj.optString("statusText", "")
                                val respBody = obj.optString("body", "")
                                val err = obj.optString("error", "")

                                val hdrList = mutableListOf<Pair<String, String>>()
                                val hdrObj = obj.optJSONObject("headers")
                                if (hdrObj != null) {
                                    val keys = hdrObj.keys()
                                    while (keys.hasNext()) {
                                        val k = keys.next()
                                        hdrList.add(k to hdrObj.optString(k))
                                    }
                                }

                                val logsList = mutableListOf<String>()
                                val logsArr = obj.optJSONArray("logs")
                                if (logsArr != null) {
                                    for (i in 0 until logsArr.length()) {
                                        logsList.add(logsArr.optString(i))
                                    }
                                }

                                finish(
                                    LocalRunResult(
                                        statusCode = status,
                                        statusText = statusText,
                                        headers = hdrList,
                                        body = CloudflareApiClient.tryFormatJson(respBody),
                                        consoleLogs = logsList,
                                        durationMs = elapsed,
                                        errorMessage = err.ifBlank { null }
                                    )
                                )
                            } catch (e: Exception) {
                                finish(
                                    LocalRunResult(
                                        statusCode = 500,
                                        statusText = "Bridge Error",
                                        headers = emptyList(),
                                        body = "",
                                        consoleLogs = emptyList(),
                                        durationMs = elapsed,
                                        errorMessage = e.message ?: "Failed to parse runner output"
                                    )
                                )
                            }
                        }
                    }

                    webView.addJavascriptInterface(bridge, "AndroidRunnerBridge")
                    val html = buildSandboxHtml(code, bindings, method, path, body)

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript("window.__runSetupCloudWorker && window.__runSetupCloudWorker();", null)
                        }
                    }

                    cont.invokeOnCancellation {
                        if (!settled) {
                            settled = true
                            disposeWebView()
                        }
                    }

                    webView.loadDataWithBaseURL("https://worker.local/", html, "text/html", "utf-8", null)
                }
            }

            result ?: LocalRunResult(
                statusCode = 408,
                statusText = "Timeout",
                headers = emptyList(),
                body = "",
                consoleLogs = emptyList(),
                durationMs = System.currentTimeMillis() - startMs,
                errorMessage = "زمان اجرای محلی کد به پایان رسید (بیش از ۶.۵ ثانیه) یا خطای نحوی باعث توقف اجرا شد."
            )
        }
    }

    private fun buildSandboxHtml(
        code: String,
        bindings: List<WorkerBinding>,
        method: String,
        path: String,
        body: String
    ): String {
        val envJson = JSONObject()
        val unsupportedNotes = JSONArray()
        bindings.forEach { b ->
            when (b.type) {
                BindingType.PLAIN_TEXT -> envJson.put(b.name, b.textValue)
                else -> unsupportedNotes.put(
                    JSONObject()
                        .put("name", b.name)
                        .put("type", b.type.apiType)
                )
            }
        }

        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val reqConfig = JSONObject()
            .put("method", method.uppercase())
            .put("url", "https://worker.local$normalizedPath")
            .put("body", body)

        val transformedCode = WorkerCodeUtils.transformModuleForLocalSandbox(code)
        val codeBase64 = android.util.Base64.encodeToString(
            transformedCode.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )

        return """
            <!doctype html>
            <html>
            <head><meta charset="utf-8"></head>
            <body>
            <script>
            (function() {
              const __logs = [];
              const __fmt = (args) => Array.from(args).map(a => {
                if (typeof a === 'string') return a;
                if (a instanceof Error) return (a.name || 'Error') + ': ' + (a.message || '');
                if (a instanceof Map) { try { return JSON.stringify(Object.fromEntries(a)); } catch(_) {} }
                if (a instanceof Set) { try { return JSON.stringify(Array.from(a)); } catch(_) {} }
                try { return JSON.stringify(a); } catch(_) { return String(a); }
              }).join(' ');

              console.log = (...a) => __logs.push('[LOG] ' + __fmt(a));
              console.info = (...a) => __logs.push('[INFO] ' + __fmt(a));
              console.warn = (...a) => __logs.push('[WARN] ' + __fmt(a));
              console.error = (...a) => __logs.push('[ERROR] ' + __fmt(a));

              if (typeof crypto !== 'undefined' && !crypto.randomUUID) {
                crypto.randomUUID = function() {
                  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
                    const r = Math.random() * 16 | 0;
                    const v = c === 'x' ? r : (r & 0x3 | 0x8);
                    return v.toString(16);
                  });
                };
              }

              if (typeof Response !== 'undefined' && !Response.json) {
                Response.json = function(data, init) {
                  init = init || {};
                  const hdrs = new Headers(init.headers || {});
                  if (!hdrs.has('content-type')) hdrs.set('content-type', 'application/json; charset=utf-8');
                  return new Response(JSON.stringify(data), Object.assign({}, init, { headers: hdrs }));
                };
              }

              window.__runSetupCloudWorker = async function() {
                try {
                  const rawB64 = "$codeBase64";
                  const bytes = Uint8Array.from(atob(rawB64), c => c.charCodeAt(0));
                  const transformed = new TextDecoder().decode(bytes);
                  const env = ${envJson};
                  const cloudBindings = ${unsupportedNotes};
                  cloudBindings.forEach(cb => {
                    const msg = "بایندینگ ابری env." + cb.name + " (" + cb.type + ") فقط در حالت اجرای زنده روی کلودفلر قابل دسترس است.";
                    env[cb.name] = new Proxy({}, {
                      get(_, prop) {
                        return function() { throw new Error(msg); };
                      }
                    });
                  });

                  const reqCfg = ${reqConfig};
                  const reqInit = { method: reqCfg.method, headers: { "x-setup-cloud-runner": "local-v8" } };
                  if (reqCfg.method !== 'GET' && reqCfg.method !== 'HEAD') {
                    reqInit.body = reqCfg.body;
                  }
                  const request = new Request(reqCfg.url, reqInit);
                  request.cf = { colo: "LOCAL-V8", country: "IR", city: "Sandbox" };

                  let swHandler = null;
                  const origAddEventListener = window.addEventListener.bind(window);
                  window.addEventListener = function(type, fn) {
                    if (type === 'fetch') { swHandler = fn; }
                    else { origAddEventListener(type, fn); }
                  };

                  const wrapped = transformed + '\n;return (typeof __setupCloudDefaultExport !== "undefined" ? __setupCloudDefaultExport : null);';
                  const factory = new Function(wrapped);
                  const exported = factory();

                  const waitPromises = [];
                  const ctx = {
                    waitUntil: (p) => waitPromises.push(Promise.resolve(p).catch(e => __logs.push('[waitUntil error] ' + e))),
                    passThroughOnException: () => {}
                  };

                  let response = null;
                  if (exported && typeof exported.fetch === 'function') {
                    response = await exported.fetch(request, env, ctx);
                  } else if (swHandler) {
                    let capturedPromise = null;
                    const evt = {
                      request,
                      respondWith: (p) => { capturedPromise = Promise.resolve(p); },
                      waitUntil: ctx.waitUntil
                    };
                    swHandler(evt);
                    if (capturedPromise) response = await capturedPromise;
                  } else {
                    throw new Error("تابع fetch در ورکر یافت نشد (export default { fetch } یا addEventListener('fetch', ...)).");
                  }

                  if (waitPromises.length > 0) {
                    await Promise.allSettled(waitPromises);
                  }

                  if (!(response instanceof Response)) {
                    throw new Error("خروجی تابع fetch یک نمونه معتبر Response نیست.");
                  }

                  const respHeaders = {};
                  response.headers.forEach((v, k) => { respHeaders[k] = v; });
                  const respBody = await response.text();

                  AndroidRunnerBridge.onComplete(JSON.stringify({
                    ok: true,
                    status: response.status,
                    statusText: response.statusText || 'OK',
                    headers: respHeaders,
                    body: respBody,
                    logs: __logs
                  }));
                } catch (err) {
                  __logs.push('[EXCEPTION] ' + (err && err.stack ? err.stack : String(err)));
                  AndroidRunnerBridge.onComplete(JSON.stringify({
                    ok: false,
                    status: 500,
                    statusText: 'Runtime Error',
                    headers: {},
                    body: '',
                    error: err && err.message ? err.message : String(err),
                    logs: __logs
                  }));
                }
              };
            })();
            </script>
            </body>
            </html>
        """.trimIndent()
    }
}
