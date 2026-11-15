package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.HttpTargetMode
import com.example.ui.StudioUiState
import com.example.ui.StudioViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun DeployTestTabScreen(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var splitVersionA by remember(state.versions) {
        mutableStateOf(state.latestUploadedVersionId.ifBlank { state.versions.firstOrNull()?.id.orEmpty() })
    }
    var splitVersionB by remember(state.versions) {
        mutableStateOf(state.versions.getOrNull(1)?.id ?: state.versions.firstOrNull()?.id.orEmpty())
    }
    var splitPercentA by remember { mutableFloatStateOf(50f) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // === 1. Safe Version Upload & Preview/Production URLs ===
        item {
            SectionCard(
                title = "آپلود امن نسخه و آدرس‌ها",
                subtitle = "ورکر: ${state.editor.scriptName.ifBlank { "تعریف نشده" }}",
                icon = Icons.Default.CloudUpload
            ) {
                Button(
                    onClick = viewModel::uploadWorkerVersionSafe,
                    enabled = !state.isUploadingVersion,
                    colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("upload_safe_version_button")
                ) {
                    if (state.isUploadingVersion) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ObsidianBg)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("آپلود نسخه تستی")
                }

                if (state.activeAccount.subdomain.isBlank()) {
                    Surface(
                        color = StatusWarningContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "سابدامین workers.dev هنوز در این اکانت دریافت یا ثبت نشده است. از منوی اکانت‌ها دکمه «تست اتصال و سابدامین» را بزنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                UrlCopyOpenCard(
                    label = "آدرس پیش‌نمایش نسخه (Preview URL)",
                    url = state.previewUrl,
                    accentColor = ElectricCyan,
                    onCopy = { viewModel.showMessage("آدرس پیش‌نمایش کپی شد") },
                    onOpen = {
                        if (state.previewUrl.isNotBlank()) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.previewUrl)))
                            }
                        }
                    }
                )

                UrlCopyOpenCard(
                    label = "آدرس اصلی (Production URL)",
                    url = state.productionUrl,
                    accentColor = StatusSuccess,
                    onCopy = { viewModel.showMessage("آدرس اصلی کپی شد") },
                    onOpen = {
                        if (state.productionUrl.isNotBlank()) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.productionUrl)))
                            }
                        }
                    }
                )
            }
        }

        // === 2. Built-in Postman-style HTTP Request Tester ===
        item {
            SectionCard(
                title = "تست زنده HTTP (Postman داخلی)",
                subtitle = "ارسال درخواست مستقیم به پیش‌نمایش یا نسخه اصلی",
                icon = Icons.Default.Send
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HttpTargetMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.httpTargetMode == mode,
                            onClick = { viewModel.setHttpTargetMode(mode) },
                            label = {
                                Text(
                                    text = mode.labelFa,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyanContainer,
                                selectedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (state.httpTargetMode == HttpTargetMode.CUSTOM_URL) {
                    LtrField(
                        value = state.httpCustomUrl,
                        onValueChange = viewModel::setHttpCustomUrl,
                        label = "آدرس کامل لینک یا دامنه اختصاصی (URL)",
                        placeholder = "https://api.example.com/health",
                        testTag = "http_custom_url_input"
                    )
                    if (state.customDomains.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.customDomains.forEach { dom ->
                                AssistChip(
                                    onClick = { viewModel.setHttpCustomUrl("https://${dom.hostname}") },
                                    label = {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = dom.hostname,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = SlateElevated,
                                        labelColor = ElectricCyan
                                    )
                                )
                            }
                        }
                    }
                } else {
                    LtrField(
                        value = state.httpPath,
                        onValueChange = viewModel::setHttpPath,
                        label = "مسیر درخواست (Path)",
                        placeholder = "/api/health"
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("GET", "POST", "PUT", "PATCH", "DELETE").forEach { method ->
                        FilterChip(
                            selected = state.httpMethod == method,
                            onClick = { viewModel.setHttpMethod(method) },
                            label = {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(method, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CloudflareOrangeContainer,
                                selectedLabelColor = CloudflareOrange
                            )
                        )
                    }
                }

                // Dynamic Headers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("هدرهای درخواست (Headers)", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    TextButton(onClick = viewModel::addHttpHeader) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricCyan)
                        Spacer(Modifier.width(4.dp))
                        Text("افزودن هدر", color = ElectricCyan)
                    }
                }

                state.httpHeaders.forEach { hdr ->
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = hdr.key,
                                onValueChange = { viewModel.updateHttpHeader(hdr.id, it, hdr.value) },
                                placeholder = { Text("Header", fontFamily = JetBrainsMonoFontFamily) },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = TextPrimary
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = hdr.value,
                                onValueChange = { viewModel.updateHttpHeader(hdr.id, hdr.key, it) },
                                placeholder = { Text("Value", fontFamily = JetBrainsMonoFontFamily) },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = TextPrimary
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.removeHttpHeader(hdr.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "حذف هدر", tint = StatusError)
                            }
                        }
                    }
                }

                if (state.httpMethod != "GET" && state.httpMethod != "DELETE") {
                    LtrField(
                        value = state.httpRequestBody,
                        onValueChange = viewModel::setHttpRequestBody,
                        label = "بدنه درخواست (Request Body)",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 8
                    )
                }

                val targetUrlPreview = when (state.httpTargetMode) {
                    HttpTargetMode.CUSTOM_URL -> state.httpCustomUrl.trim()
                    HttpTargetMode.PREVIEW -> {
                        val base = state.previewUrl.ifBlank { state.productionUrl }
                        val normalizedPath = if (state.httpPath.startsWith("/")) state.httpPath else "/${state.httpPath}"
                        if (base.isNotBlank()) "$base$normalizedPath" else ""
                    }
                    HttpTargetMode.PRODUCTION -> {
                        val normalizedPath = if (state.httpPath.startsWith("/")) state.httpPath else "/${state.httpPath}"
                        if (state.productionUrl.isNotBlank()) "${state.productionUrl}$normalizedPath" else ""
                    }
                }
                if (targetUrlPreview.isNotBlank()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = "${state.httpMethod} $targetUrlPreview",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = viewModel::sendHttpTestRequest,
                    enabled = !state.isSendingHttpTest,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("send_http_test_button")
                ) {
                    if (state.isSendingHttpTest) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ObsidianBg)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("ارسال درخواست آزمایشی")
                }

                state.httpTestResponse?.let { resp ->
                    val statusColor = when {
                        resp.statusCode in 200..299 -> StatusSuccess
                        resp.statusCode in 300..399 -> ElectricCyan
                        resp.statusCode in 400..499 -> StatusWarning
                        else -> StatusError
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = "HTTP ${resp.statusCode} ${resp.statusMessage}",
                            color = statusColor,
                            containerColor = SlateElevated
                        )
                        StatusPill(
                            text = "${resp.latencyMs} ms",
                            color = ElectricCyan,
                            containerColor = ElectricCyanContainer
                        )
                    }
                    if (resp.headers.isNotEmpty()) {
                        val headerText = resp.headers.joinToString("\n") { "${it.first}: ${it.second}" }
                        LtrReadOnlyConsole(
                            text = headerText,
                            title = "Response Headers",
                            onCopy = { viewModel.showMessage("هدرهای پاسخ کپی شد") }
                        )
                    }
                    LtrReadOnlyConsole(
                        text = resp.body,
                        title = "Response Body",
                        accentColor = statusColor,
                        onCopy = { viewModel.showMessage("بدنه پاسخ کپی شد") }
                    )
                }
            }
        }

        // === 3. Real-Time Tail Logs Console (WebSocket trace-v1) ===
        item {
            SectionCard(
                title = "کنسول لاگ زنده (Real-Time Tail)",
                subtitle = "پروتکل WebSocket trace-v1",
                icon = Icons.Default.Terminal,
                headerAction = {
                    if (state.tailLogs.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearTailLogs) {
                            Text("پاک کردن", color = TextSecondary)
                        }
                    }
                }
            ) {
                Surface(
                    color = ElectricCyanContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "نکته: لاگ زنده (Tail Logs) ترافیک ارسالی به آدرس اصلی (Production URL) را ثبت می‌کند و درخواست‌های آدرس موقت پیش‌نمایش را نمایش نمی‌دهد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (state.isTailConnected || state.isTailStarting) viewModel.stopTailLogs()
                            else viewModel.startTailLogs()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isTailConnected) StatusError else StatusSuccess,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("toggle_tail_logs_button")
                    ) {
                        Icon(
                            imageVector = if (state.isTailConnected) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                state.isTailStarting -> "در حال اتصال... (توقف)"
                                state.isTailConnected -> "توقف لاگ زنده"
                                else -> "شروع لاگ زنده"
                            }
                        )
                    }
                }

                if (state.tailStatusNote.isNotBlank()) {
                    Text(
                        text = state.tailStatusNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isTailConnected) StatusSuccess else TextSecondary
                    )
                }

                if (state.tailLogs.isEmpty()) {
                    EmptyStateBox(
                        icon = Icons.Default.Terminal,
                        title = "لاگی ثبت نشده است",
                        subtitle = "پس از شروع لاگ زنده، به آدرس Production درخواست بفرستید تا console.log و خطاها اینجا ظاهر شوند."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.tailLogs.take(40).forEach { entry ->
                            val isOk = entry.outcome == "ok" && entry.exceptions.isEmpty()
                            val summary = buildString {
                                appendLine("${entry.timestamp} | ${entry.method} ${entry.url} | outcome=${entry.outcome}")
                                entry.logs.forEach { appendLine("  LOG: $it") }
                                entry.exceptions.forEach { appendLine("  EXC: $it") }
                            }.trimEnd()
                            LtrReadOnlyConsole(
                                text = summary,
                                accentColor = if (isOk) StatusSuccess else StatusError
                            )
                        }
                    }
                }
            }
        }

        // === 4. Deployments, Gradual Rollouts & Rollback ===
        item {
            SectionCard(
                title = "انتشار روی نسخه اصلی، تقسیم ترافیک و بازگشت",
                subtitle = "مدیریت نسخه‌ها و استقرار",
                icon = Icons.Default.RocketLaunch,
                headerAction = {
                    IconButton(onClick = { viewModel.refreshVersionsAndDeployments() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی تاریخچه", tint = ElectricCyan)
                    }
                }
            ) {
                val targetVersion = state.latestUploadedVersionId.ifBlank {
                    state.versions.firstOrNull()?.id.orEmpty()
                }
                Button(
                    onClick = { viewModel.deploy100Percent(targetVersion) },
                    enabled = !state.isDeploying && targetVersion.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("deploy_100_percent_button")
                ) {
                    if (state.isDeploying) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ObsidianBg)
                    } else {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("انتشار ۱۰۰٪ روی نسخه اصلی (${targetVersion.take(8).ifBlank { "-" }})")
                }

                // Gradual Split Deployment UI
                if (state.versions.size >= 2) {
                    Surface(
                        color = SlateElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("انتشار تدریجی (Gradual Split)", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                            val pctA = splitPercentA.roundToInt()
                            val pctB = 100 - pctA
                            Text(
                                text = "نسخه اول (${splitVersionA.take(8)}): $pctA%  |  نسخه دوم (${splitVersionB.take(8)}): $pctB%",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                            if (splitVersionA.isNotBlank() && splitVersionA == splitVersionB) {
                                Text(
                                    text = "دو نسخه یکسان انتخاب شده‌اند (۱۰۰٪ ترافیک به همین نسخه اعمال می‌شود).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusWarning
                                )
                            }
                            Slider(
                                value = splitPercentA,
                                onValueChange = { splitPercentA = it },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = CloudflareOrange,
                                    activeTrackColor = CloudflareOrange
                                )
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.versions.take(6).forEach { ver ->
                                    val isA = ver.id == splitVersionA
                                    val isB = ver.id == splitVersionB
                                    FilterChip(
                                        selected = isA || isB,
                                        onClick = {
                                            if (!isA) {
                                                splitVersionB = splitVersionA
                                                splitVersionA = ver.id
                                            }
                                        },
                                        label = {
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Text(
                                                    text = (if (isA) "A:" else if (isB) "B:" else "") + ver.id.take(8),
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.deployGradualSplit(splitVersionA, pctA, splitVersionB) },
                                enabled = !state.isDeploying,
                                border = BorderStroke(1.dp, ElectricCyan),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("اعمال انتشار تدریجی ($pctA% / $pctB%)", color = ElectricCyan)
                            }
                        }
                    }
                }

                if (state.isLoadingDeployHistory) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CloudflareOrange)
                }

                // Version History
                if (state.versions.isNotEmpty()) {
                    Text("تاریخچه نسخه‌ها (Versions)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    state.versions.take(8).forEach { ver ->
                        Surface(
                            color = SlateElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = "v${ver.number} • ${ver.id.take(12)}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = TextPrimary
                                        )
                                    }
                                    Text(
                                        text = ver.createdOn.take(19).replace("T", " "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                                TextButton(onClick = { viewModel.deploy100Percent(ver.id) }) {
                                    Text("انتشار ۱۰۰٪", color = CloudflareOrange)
                                }
                            }
                        }
                    }
                }

                // Deployment History + Rollback
                if (state.deployments.isNotEmpty()) {
                    Text("تاریخچه استقرارها (Deployments)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    state.deployments.take(6).forEachIndexed { idx, dep ->
                        Surface(
                            color = SlateElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (idx == 0) StatusSuccess.copy(alpha = 0.5f) else SlateBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val allocSummary = dep.allocations.joinToString(", ") {
                                        "${it.versionId.take(8)} (${it.percentage}%)"
                                    }
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = allocSummary.ifBlank { dep.id.take(10) },
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (idx == 0) StatusSuccess else TextPrimary
                                        )
                                    }
                                    Text(
                                        text = if (idx == 0) "نسخه فعال فعلی • ${dep.createdOn.take(16).replace("T", " ")}"
                                        else dep.createdOn.take(16).replace("T", " "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                                if (idx > 0 && dep.allocations.isNotEmpty()) {
                                    TextButton(onClick = { viewModel.rollbackToDeployment(dep) }) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricCyan)
                                        Spacer(Modifier.width(4.dp))
                                        Text("بازگشت", color = ElectricCyan)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlCopyOpenCard(
    label: String,
    url: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onCopy: () -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        color = CodeSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = url.ifBlank { "هنوز تولید نشده" },
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (url.isNotBlank()) accentColor else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (url.isNotBlank()) {
                Row {
                    IconButton(
                        onClick = { copyToClipboard(context, label, url, onCopy) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "کپی آدرس", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onOpen, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "باز کردن در مرورگر", tint = accentColor, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
