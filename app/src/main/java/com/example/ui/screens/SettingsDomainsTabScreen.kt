package com.example.ui.screens

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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.StudioUiState
import com.example.ui.StudioViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun SettingsDomainsTabScreen(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    var secretKey by remember { mutableStateOf("") }
    var secretVal by remember { mutableStateOf("") }

    var cronInput by remember { mutableStateOf("*/5 * * * *") }

    var customDomainHostname by remember { mutableStateOf("") }
    var routePattern by remember { mutableStateOf("") }

    val scriptName = state.editor.scriptName.ifBlank { "hello-setup-cloud" }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.settingsError?.let { err ->
            item { ErrorBannerCard(error = err) }
        }

        if (state.settingsLoading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CloudflareOrange)
            }
        }

        // === 1. Worker Secrets (Encrypted) ===
        item {
            SectionCard(
                title = "رازهای رمزنگاری شده (Secrets)",
                subtitle = "ورکر: $scriptName",
                icon = Icons.Default.Lock,
                headerAction = {
                    IconButton(onClick = viewModel::refreshSettingsTab) {
                        Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی تنظیمات", tint = ElectricCyan)
                    }
                }
            ) {
                Text(
                    text = "توضیح امنیتی: کلودفلر به دلایل امنیتی مقدار رازهای ذخیره‌شده را نمایش نمی‌دهد و فقط نام آن‌ها را برمی‌گرداند. برای تغییر مقدار، همان نام را دوباره ذخیره کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (state.secrets.isEmpty()) {
                    Text("هنوز رازی برای این ورکر ثبت نشده است.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.secrets.forEach { sec ->
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
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = "${sec.name}  (encrypted)",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = ElectricCyan
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteSecret(sec.name) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف راز", tint = StatusError)
                                    }
                                }
                            }
                        }
                    }
                }

                LtrField(
                    value = secretKey,
                    onValueChange = { secretKey = it },
                    label = "نام راز (Secret Name)",
                    placeholder = "JWT_SECRET"
                )
                LtrField(
                    value = secretVal,
                    onValueChange = { secretVal = it },
                    label = "مقدار محرمانه (Value)",
                    placeholder = "super-secret-value"
                )
                Button(
                    onClick = {
                        viewModel.saveSecret(secretKey, secretVal)
                        secretKey = ""
                        secretVal = ""
                    },
                    enabled = secretKey.isNotBlank() && secretVal.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_secret_button")
                ) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("افزودن / بروزرسانی امن راز (PATCH secrets-bulk)")
                }
            }
        }

        // === 2. Cron Triggers ===
        item {
            SectionCard(
                title = "زمان‌بندی خودکار (Cron Triggers)",
                subtitle = "اجرای دوره‌ای تابع scheduled() ورکر",
                icon = Icons.Default.Schedule
            ) {
                val presets = listOf(
                    "هر ۱ دقیقه" to "* * * * *",
                    "هر ۵ دقیقه" to "*/5 * * * *",
                    "هر ساعت" to "0 * * * *",
                    "روزانه" to "0 0 * * *"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { (labelFa, expr) ->
                        AssistChip(
                            onClick = { cronInput = expr },
                            label = { Text("$labelFa ($expr)", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = SlateElevated,
                                labelColor = ElectricCyan
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    LtrField(
                        value = cronInput,
                        onValueChange = { cronInput = it },
                        label = "عبارت Cron",
                        placeholder = "*/5 * * * *",
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val updated = (state.cronSchedules.map { it.cron } + cronInput.trim()).distinct()
                            viewModel.updateCrons(updated)
                        },
                        enabled = cronInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg),
                        modifier = Modifier.heightIn(min = 52.dp)
                    ) {
                        Text("افزودن")
                    }
                }

                if (state.cronSchedules.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.cronSchedules.forEach { item ->
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
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = item.cron,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = CloudflareOrange
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val remaining = state.cronSchedules.map { it.cron }.filterNot { it == item.cron }
                                            viewModel.updateCrons(remaining)
                                        }
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف Cron", tint = StatusError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // === 3. Subdomain Routing, Custom Domains & Zone Routes ===
        item {
            SectionCard(
                title = "سابدامین workers.dev و دامنه‌های اختصاصی",
                icon = Icons.Default.Public
            ) {
                Surface(
                    color = SlateElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("فعال‌سازی مسیر اصلی workers.dev", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Switch(
                                checked = state.scriptSubdomainStatus.enabled,
                                onCheckedChange = {
                                    viewModel.updateScriptSubdomainToggles(it, state.scriptSubdomainStatus.previewsEnabled)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = CloudflareOrange)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("فعال‌سازی لینک پیش‌نمایش نسخه‌ها (Previews)", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Switch(
                                checked = state.scriptSubdomainStatus.previewsEnabled,
                                onCheckedChange = {
                                    viewModel.updateScriptSubdomainToggles(state.scriptSubdomainStatus.enabled, it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = ElectricCyan)
                            )
                        }
                    }
                }

                // Zones picker
                if (state.zones.isNotEmpty()) {
                    Text("انتخاب Zone برای دامنه و مسیرها:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.zones.forEach { z ->
                            FilterChip(
                                selected = state.selectedZone?.id == z.id,
                                onClick = { viewModel.selectZone(z) },
                                label = {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(z.name, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Text("دامنه‌ای (Zone) در این اکانت یافت نشد.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }

                // Custom Domains
                LtrField(
                    value = customDomainHostname,
                    onValueChange = { customDomainHostname = it },
                    label = "اتصال دامنه اختصاصی (Custom Domain)",
                    placeholder = "api.yourdomain.com"
                )
                val cleanHost = customDomainHostname.trim().lowercase()
                val activeZoneName = state.selectedZone?.name?.lowercase().orEmpty()
                if (cleanHost.isNotBlank() && activeZoneName.isNotBlank() &&
                    cleanHost != activeZoneName && !cleanHost.endsWith(".$activeZoneName")
                ) {
                    Text(
                        text = "توجه: دامنه واردشده باید زیرمجموعه Zone انتخابی ($activeZoneName) باشد.",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusWarning
                    )
                }
                Button(
                    onClick = {
                        val zoneId = state.selectedZone?.id.orEmpty()
                        if (zoneId.isNotBlank()) {
                            viewModel.attachCustomDomain(customDomainHostname, scriptName, zoneId)
                            customDomainHostname = ""
                        } else {
                            viewModel.showMessage("ابتدا یک Zone انتخاب کنید")
                        }
                    },
                    enabled = customDomainHostname.isNotBlank() && state.selectedZone != null,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("اتصال Custom Domain به $scriptName")
                }

                state.customDomains.forEach { dom ->
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
                                    Text(dom.hostname, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                                    Text("-> ${dom.service} (${dom.environment})", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { viewModel.testCustomUrlInDeployTab("https://${dom.hostname}") },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("test_domain_${dom.id}")
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricCyan)
                                    Spacer(Modifier.width(4.dp))
                                    Text("تست لینک", color = ElectricCyan)
                                }
                                IconButton(
                                    onClick = { viewModel.detachCustomDomain(dom.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "جدا کردن دامنه", tint = StatusError)
                                }
                            }
                        }
                    }
                }

                // Zone Worker Routes
                if (state.selectedZone != null) {
                    HorizontalDivider(color = SlateBorder)
                    Text("مسیرهای ورکر (Zone Routes — ${state.selectedZone.name})", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        LtrField(
                            value = routePattern,
                            onValueChange = { routePattern = it },
                            label = "الگوی مسیر (Route Pattern)",
                            placeholder = "${state.selectedZone.name}/api/*",
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                viewModel.createZoneRoute(routePattern, scriptName)
                                routePattern = ""
                            },
                            enabled = routePattern.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg),
                            modifier = Modifier.heightIn(min = 52.dp)
                        ) {
                            Text("ثبت")
                        }
                    }

                    state.zoneRoutes.forEach { rt ->
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
                                        Text(rt.pattern, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                                        Text("Worker: ${rt.script.ifBlank { "(disabled)" }}", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                }
                                IconButton(onClick = { viewModel.deleteZoneRoute(rt.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف مسیر", tint = StatusError)
                                }
                            }
                        }
                    }
                }
            }
        }

        // === 4. Script Settings & Observability ===
        item {
            SectionCard(
                title = "تنظیمات اسکریپت و مانیتورینگ",
                subtitle = "Observability و Logpush برای $scriptName",
                icon = Icons.Default.Settings
            ) {
                Surface(
                    color = SlateElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Observability (لاگ و آمار کلودفلر)", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Switch(
                                checked = state.scriptSettings.observabilityEnabled,
                                onCheckedChange = {
                                    viewModel.patchScriptSettings(it, state.scriptSettings.logpushEnabled)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = CloudflareOrange)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Logpush (ارسال لاگ‌های ورکر)", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Switch(
                                checked = state.scriptSettings.logpushEnabled,
                                onCheckedChange = {
                                    viewModel.patchScriptSettings(state.scriptSettings.observabilityEnabled, it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = ElectricCyan)
                            )
                        }
                    }
                }
            }
        }
    }
}
