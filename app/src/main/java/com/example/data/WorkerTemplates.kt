package com.example.data

import com.example.model.BindingType
import com.example.model.WorkerBinding
import com.example.model.WorkerTemplate

object WorkerTemplates {

    val all: List<WorkerTemplate> = listOf(
        WorkerTemplate(
            id = "hello_world",
            titleFa = "Hello World",
            subtitleFa = "شروع سریع ES Module با پاسخ متنی و زمان سرور",
            defaultName = "hello-setup-cloud",
            code = """
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    return new Response(
      `Setup cloud Worker is live!\nPath: ${'$'}{url.pathname}\nTime: ${'$'}{new Date().toISOString()}`,
      {
        status: 200,
        headers: {
          "content-type": "text/plain; charset=utf-8",
          "x-powered-by": "Setup cloud"
        }
      }
    );
  }
};
            """.trimIndent()
        ),
        WorkerTemplate(
            id = "json_rest_api",
            titleFa = "JSON REST API",
            subtitleFa = "مسیریابی GET/POST با CORS و خروجی JSON",
            defaultName = "json-api-worker",
            code = """
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
  "Content-Type": "application/json; charset=utf-8"
};

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    const url = new URL(request.url);

    if (url.pathname === "/api/health" && request.method === "GET") {
      return Response.json(
        { ok: true, service: "setup-cloud-api", colo: request.cf?.colo ?? "EDGE" },
        { headers: corsHeaders }
      );
    }

    if (url.pathname === "/api/echo" && request.method === "POST") {
      const payload = await request.json().catch(() => ({}));
      return Response.json(
        { received: payload, timestamp: Date.now() },
        { status: 201, headers: corsHeaders }
      );
    }

    return Response.json(
      { error: "Not Found", path: url.pathname },
      { status: 404, headers: corsHeaders }
    );
  }
};
            """.trimIndent()
        ),
        WorkerTemplate(
            id = "reverse_proxy",
            titleFa = "Reverse Proxy / Fetch",
            subtitleFa = "پروکسی هوشمند درخواست‌ها با افزودن هدر امنیتی",
            defaultName = "edge-proxy-worker",
            code = """
export default {
  async fetch(request, env) {
    const incomingUrl = new URL(request.url);
    const targetOrigin = env.TARGET_ORIGIN || "https://httpbin.org";
    const targetUrl = new URL(incomingUrl.pathname + incomingUrl.search, targetOrigin);

    const proxiedRequest = new Request(targetUrl.toString(), {
      method: request.method,
      headers: request.headers,
      body: request.method !== "GET" && request.method !== "HEAD" ? request.body : undefined
    });

    const upstreamResponse = await fetch(proxiedRequest);
    const response = new Response(upstreamResponse.body, upstreamResponse);
    response.headers.set("X-Proxy-Edge", "Setup-Cloud-Worker");
    return response;
  }
};
            """.trimIndent(),
            suggestedBindings = listOf(
                WorkerBinding(
                    uid = "tpl-proxy-origin",
                    type = BindingType.PLAIN_TEXT,
                    name = "TARGET_ORIGIN",
                    textValue = "https://httpbin.org"
                )
            )
        ),
        WorkerTemplate(
            id = "cron_scheduled",
            titleFa = "Cron Scheduled",
            subtitleFa = "اجرای زمان‌بندی شده با scheduled() و تست HTTP",
            defaultName = "cron-job-worker",
            code = """
export default {
  async scheduled(controller, env, ctx) {
    console.log("Cron triggered:", controller.cron, new Date(controller.scheduledTime).toISOString());
    ctx.waitUntil(
      (async () => {
        // Perform background sync or health check here
        console.log("Scheduled task completed cleanly.");
      })()
    );
  },

  async fetch(request) {
    return Response.json({
      status: "ready",
      hint: "Attach a Cron Trigger in Tab 4 to run scheduled() automatically."
    });
  }
};
            """.trimIndent()
        ),
        WorkerTemplate(
            id = "d1_sql_crud",
            titleFa = "D1 SQL CRUD",
            subtitleFa = "عملیات خواندن و نوشتن در دیتابیس D1 با env.DB",
            defaultName = "d1-crud-worker",
            code = """
export default {
  async fetch(request, env) {
    if (!env.DB) {
      return Response.json({ error: "D1 binding 'DB' is not configured" }, { status: 500 });
    }

    const url = new URL(request.url);

    await env.DB.prepare(
      "CREATE TABLE IF NOT EXISTS items (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, created_at TEXT NOT NULL)"
    ).run();

    if (request.method === "GET" && url.pathname === "/items") {
      const { results } = await env.DB.prepare("SELECT * FROM items ORDER BY id DESC LIMIT 50").all();
      return Response.json({ items: results });
    }

    if (request.method === "POST" && url.pathname === "/items") {
      const body = await request.json().catch(() => ({}));
      const title = String(body.title || "Untitled item");
      const info = await env.DB.prepare("INSERT INTO items (title, created_at) VALUES (?, ?)")
        .bind(title, new Date().toISOString())
        .run();
      return Response.json({ inserted: true, meta: info.meta }, { status: 201 });
    }

    return Response.json({ endpoints: ["GET /items", "POST /items"] });
  }
};
            """.trimIndent()
        ),
        WorkerTemplate(
            id = "kv_cache",
            titleFa = "KV Cache Worker",
            subtitleFa = "ذخیره و خواندن سریع کلید-مقدار از env.KV",
            defaultName = "kv-cache-worker",
            code = """
export default {
  async fetch(request, env) {
    if (!env.KV) {
      return Response.json({ error: "KV binding 'KV' is not configured" }, { status: 500 });
    }

    const url = new URL(request.url);
    const key = url.searchParams.get("key") || "greeting";

    if (request.method === "PUT" || request.method === "POST") {
      const val = await request.text();
      await env.KV.put(key, val, { expirationTtl: 3600 });
      return Response.json({ saved: true, key, ttlSeconds: 3600 });
    }

    const value = await env.KV.get(key);
    return Response.json({ key, found: value !== null, value });
  }
};
            """.trimIndent()
        ),
        WorkerTemplate(
            id = "r2_file",
            titleFa = "R2 File Worker",
            subtitleFa = "آپلود، لیست و دریافت آبجکت از باکت env.BUCKET",
            defaultName = "r2-storage-worker",
            code = """
export default {
  async fetch(request, env) {
    if (!env.BUCKET) {
      return Response.json({ error: "R2 binding 'BUCKET' is not configured" }, { status: 500 });
    }

    const url = new URL(request.url);
    const key = url.pathname.replace(/^\/+/, "");

    if (!key) {
      const listed = await env.BUCKET.list({ limit: 50 });
      return Response.json({
        objects: listed.objects.map(o => ({ key: o.key, size: o.size, uploaded: o.uploaded }))
      });
    }

    if (request.method === "PUT") {
      await env.BUCKET.put(key, request.body, {
        httpMetadata: { contentType: request.headers.get("content-type") || "application/octet-stream" }
      });
      return Response.json({ uploaded: true, key }, { status: 201 });
    }

    const obj = await env.BUCKET.get(key);
    if (!obj) return new Response("Object Not Found", { status: 404 });

    const headers = new Headers();
    obj.writeHttpMetadata(headers);
    headers.set("etag", obj.httpEtag);
    return new Response(obj.body, { headers });
  }
};
            """.trimIndent()
        )
    )
}
