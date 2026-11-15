package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.WorkerTemplates
import com.example.model.*
import com.example.ui.StudioUiState
import com.example.ui.StudioViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkersTabScreen(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onNavigateToDeployTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfigSection by remember { mutableStateOf(false) }
    var showTemplatesSheet by remember { mutableStateOf(false) }
    var showBindingsSheet by remember { mutableStateOf(false) }
    var showRunnerSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showWorkersListSection by remember { mutableStateOf(true) }
    var workerPendingDelete by remember { mutableStateOf<WorkerScriptSummary?>(null) }

    var codeFieldValue by remember { mutableStateOf(TextFieldValue(state.editor.code)) }
    LaunchedEffect(state.editor.code) {
        if (state.editor.code != codeFieldValue.text) {
            val newLen = state.editor.code.length
            codeFieldValue = TextFieldValue(
                text = state.editor.code,
                selection = TextRange(
                    codeFieldValue.selection.start.coerceIn(0, newLen),
                    codeFieldValue.selection.end.coerceIn(0, newLen)
                )
            )
        }
    }

    fun insertSnippetAtCursor(snippet: String) {
        val currentText = codeFieldValue.text
        val start = codeFieldValue.selection.min.coerceIn(0, currentText.length)
        val end = codeFieldValue.selection.max.coerceIn(0, currentText.length)
        val updated = buildString {
            append(currentText, 0, start)
            append(snippet)
            append(currentText, end, currentText.length)
        }
        val nextCursor = start + snippet.length
        codeFieldValue = TextFieldValue(
            text = updated,
            selection = TextRange(nextCursor)
        )
        viewModel.updateEditorCode(updated)
    }

    val filteredWorkers = remember(state.workers, state.workersSearchQuery) {
        val q = state.workersSearchQuery.trim()
        if (q.isEmpty()) state.workers
        else state.workers.filter { it.id.contains(q, ignoreCase = true) }
    }

    if (state.editor.isEditorExpanded) {
        BackHandler {
            viewModel.setEditorExpanded(false)
        }
        // Full-height focused IDE Editor mode
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = SlateSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = CloudflareOrange)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "${state.editor.scriptName.ifBlank { "worker" }}.js",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { showImportSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("expanded_import_code_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "ایمپورت کد", tint = StatusSuccess)
                        }
                        IconButton(
                            onClick = viewModel::formatEditorCode,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("expanded_format_code_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, contentDescription = "مرتب‌سازی کد", tint = CloudflareOrange)
                        }
                        IconButton(
                            onClick = { showBindingsSheet = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = "بایندینگ‌ها", tint = ElectricCyan)
                        }
                        IconButton(
                            onClick = { viewModel.setEditorExpanded(false) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("collapse_editor_button")
                        ) {
                            Icon(Icons.Default.CloseFullscreen, contentDescription = "خروج از حالت تمام‌صفحه", tint = TextPrimary)
                        }
                    }
                }
            }

            EditorQuickSnippetBar(
                onInsertSnippet = ::insertSnippetAtCursor
            )

            LtrCodeEditorBox(
                value = codeFieldValue,
                onValueChange = { next ->
                    codeFieldValue = next
                    if (next.text != state.editor.code) {
                        viewModel.updateEditorCode(next.text)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                fixedHeightDp = null,
                testTag = "expanded_code_editor_input"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showRunnerSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess, contentColor = ObsidianBg),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("expanded_open_runner_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("اجرا و تست")
                }
                Button(
                    onClick = {
                        viewModel.uploadWorkerVersionSafe()
                    },
                    enabled = !state.isUploadingVersion,
                    colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    if (state.isUploadingVersion) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ObsidianBg)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("آپلود نسخه")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.setEditorExpanded(false)
                        onNavigateToDeployTest()
                    },
                    border = BorderStroke(1.dp, ElectricCyan),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("انتشار", color = ElectricCyan)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // === Section 1: Existing Workers Scripts ===
            item {
                SectionCard(
                    title = "ورکرهای اکانت (${state.workers.size})",
                    subtitle = "بارگذاری کد در ادیتور یا حذف اسکریپت",
                    icon = Icons.Default.Dns,
                    headerAction = {
                        Row {
                            IconButton(onClick = viewModel::refreshWorkersList) {
                                Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی لیست", tint = ElectricCyan)
                            }
                            IconButton(onClick = { showWorkersListSection = !showWorkersListSection }) {
                                Icon(
                                    imageVector = if (showWorkersListSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "باز/بسته کردن لیست",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                ) {
                    AnimatedVisibility(visible = showWorkersListSection) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.workersSearchQuery,
                                onValueChange = viewModel::updateWorkersSearch,
                                placeholder = { Text("جستجوی نام ورکر...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (state.isLoadingWorkers) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = CloudflareOrange,
                                    trackColor = SlateElevated
                                )
                            }

                            if (filteredWorkers.isEmpty() && !state.isLoadingWorkers) {
                                EmptyStateBox(
                                    icon = Icons.Default.CodeOff,
                                    title = "ورکری یافت نشد",
                                    subtitle = "می‌توانید از قالب‌های آماده پایین یک ورکر جدید بسازید و آپلود کنید."
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    filteredWorkers.take(25).forEach { worker ->
                                        val isCurrent = worker.id == state.editor.scriptName
                                        Surface(
                                            color = if (isCurrent) SlateHighlight else SlateElevated,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isCurrent) CloudflareOrange else SlateBorder.copy(alpha = 0.7f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.loadWorkerIntoEditor(worker.id) }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                        Text(
                                                            text = worker.id,
                                                            fontFamily = JetBrainsMonoFontFamily,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = if (isCurrent) CloudflareOrange else TextPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (worker.modifiedOn.isNotBlank()) {
                                                        Text(
                                                            text = "آخرین تغییر: ${worker.modifiedOn.take(16).replace("T", " ")}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = TextMuted
                                                        )
                                                    }
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    TextButton(onClick = { viewModel.loadWorkerIntoEditor(worker.id) }) {
                                                        Text("باز کردن", color = ElectricCyan)
                                                    }
                                                    IconButton(
                                                        onClick = { workerPendingDelete = worker },
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف ورکر", tint = StatusError)
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
            }

            // === Section 2: Worker Editor, Templates, Config & Modal Bindings ===
            item {
                SectionCard(
                    title = "ادیتور کد ورکر",
                    subtitle = "تنظیمات ES Module، بایندینگ‌ها و کد جاوااسکریپت",
                    icon = Icons.Default.Code,
                    headerAction = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { showImportSheet = true },
                                border = BorderStroke(1.dp, StatusSuccess),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("open_import_code_button")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("ایمپورت کد", color = StatusSuccess, style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { showTemplatesSheet = true },
                                border = BorderStroke(1.dp, ElectricCyan),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("قالب", color = ElectricCyan, style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(
                                onClick = { viewModel.setEditorExpanded(true) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("expand_editor_button")
                            ) {
                                Icon(Icons.Default.OpenInFull, contentDescription = "بزرگ‌نمایی ادیتور", tint = CloudflareOrange)
                            }
                        }
                    }
                ) {
                    if (state.editor.isLoadingScript) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = ElectricCyan,
                            trackColor = SlateElevated
                        )
                    }

                    LtrField(
                        value = state.editor.scriptName,
                        onValueChange = viewModel::updateEditorScriptName,
                        label = "نام ورکر (Worker Name - حروف کوچک انگلیسی و خط تیره)",
                        placeholder = "my-api-worker",
                        testTag = "worker_name_input"
                    )

                    // Collapsible Config + Modal Bindings Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showConfigSection = !showConfigSection },
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("تنظیمات اجرا", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(
                                imageVector = if (showConfigSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = { showBindingsSheet = true },
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.8f)),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("open_bindings_sheet_button")
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "بایندینگ‌ها (${state.editor.bindings.size})",
                                color = ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    AnimatedVisibility(visible = showConfigSection) {
                        Surface(
                            color = SlateElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LtrField(
                                    value = state.editor.compatibilityDate,
                                    onValueChange = viewModel::updateCompatibilityDate,
                                    label = "Compatibility Date",
                                    placeholder = "2026-11-15"
                                )
                                ConfigSwitchRow(
                                    label = "پرچم nodejs_compat",
                                    subtitle = "پشتیبانی از ماژول‌های استاندارد Node.js",
                                    checked = state.editor.nodejsCompat,
                                    onCheckedChange = viewModel::toggleNodejsCompat
                                )
                                ConfigSwitchRow(
                                    label = "Smart Placement",
                                    subtitle = "اجرای خودکار ورکر نزدیک به دیتابیس و بک‌اند",
                                    checked = state.editor.smartPlacement,
                                    onCheckedChange = viewModel::toggleSmartPlacement
                                )
                                ConfigSwitchRow(
                                    label = "Observability",
                                    subtitle = "ثبت متریک‌ها و لاگ‌های زمان اجرا در کلودفلر",
                                    checked = state.editor.observabilityEnabled,
                                    onCheckedChange = viewModel::toggleEditorObservability
                                )
                            }
                        }
                    }

                    if (state.editor.bindings.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.editor.bindings, key = { it.uid }) { binding ->
                                InputChip(
                                    selected = true,
                                    onClick = { showBindingsSheet = true },
                                    label = {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = "${binding.name} (${binding.type.apiType})",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.removeBinding(binding.uid) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "حذف بایندینگ", modifier = Modifier.size(14.dp))
                                        }
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = ElectricCyanContainer,
                                        selectedLabelColor = ElectricCyan,
                                        selectedTrailingIconColor = ElectricCyan
                                    )
                                )
                            }
                        }
                    }

                    EditorQuickSnippetBar(
                        onInsertSnippet = ::insertSnippetAtCursor
                    )

                    LtrCodeEditorBox(
                        value = codeFieldValue,
                        onValueChange = { next ->
                            codeFieldValue = next
                            if (next.text != state.editor.code) {
                                viewModel.updateEditorCode(next.text)
                            }
                        },
                        fixedHeightDp = 280,
                        testTag = "inline_code_editor_input"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val lines = remember(state.editor.code) {
                            maxOf(1, state.editor.code.count { it == '\n' } + 1)
                        }
                        Text(
                            text = "$lines خط | ${state.editor.code.length} کاراکتر",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = viewModel::formatEditorCode,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("inline_format_code_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, contentDescription = null, modifier = Modifier.size(16.dp), tint = CloudflareOrange)
                                Spacer(Modifier.width(4.dp))
                                Text("مرتب‌سازی", color = CloudflareOrange)
                            }
                            TextButton(
                                onClick = { viewModel.setEditorExpanded(true) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricCyan)
                                Spacer(Modifier.width(4.dp))
                                Text("تمام‌صفحه", color = ElectricCyan)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showRunnerSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess, contentColor = ObsidianBg),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("open_runner_sheet_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("اجرا و تست کد", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = {
                                viewModel.uploadWorkerVersionSafe()
                            },
                            enabled = !state.isUploadingVersion,
                            colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("upload_version_from_editor_button")
                        ) {
                            if (state.isUploadingVersion) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ObsidianBg)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("آپلود نسخه تستی", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    // === Templates Bottom Sheet ===
    if (showTemplatesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTemplatesSheet = false },
            containerColor = SlateSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "انتخاب قالب آماده ورکر",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(WorkerTemplates.all, key = { it.id }) { tpl ->
                        Surface(
                            color = SlateElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.applyTemplate(tpl)
                                    showTemplatesSheet = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = tpl.titleFa,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CloudflareOrange,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = tpl.subtitleFa,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // === Modal Bottom Sheet Bindings Builder ===
    if (showBindingsSheet) {
        BindingsBuilderModalSheet(
            state = state,
            onAddBinding = viewModel::addBinding,
            onRemoveBinding = viewModel::removeBinding,
            onDismiss = { showBindingsSheet = false }
        )
    }

    // === Hybrid Worker Execution Environment Sheet ===
    if (showRunnerSheet) {
        EditorRunnerModalSheet(
            state = state,
            onSelectMode = viewModel::setRunnerMode,
            onSelectMethod = viewModel::setRunnerMethod,
            onChangePath = viewModel::setRunnerPath,
            onChangeBody = viewModel::setRunnerBody,
            onExecute = viewModel::executeEditorRunner,
            onDeployProduction = {
                viewModel.deployCurrentCodeToProduction {
                    showRunnerSheet = false
                    viewModel.setEditorExpanded(false)
                    onNavigateToDeployTest()
                }
            },
            onGoToDeployTest = {
                showRunnerSheet = false
                viewModel.setEditorExpanded(false)
                onNavigateToDeployTest()
            },
            onCopyFeedback = { viewModel.showMessage("در کلیپ‌بورد کپی شد") },
            onDismiss = { showRunnerSheet = false }
        )
    }

    // === Import Code Sheet (File Picker / URL / Clipboard) ===
    if (showImportSheet) {
        ImportCodeModalSheet(
            isImporting = state.isImportingCode,
            onImportText = { raw, fileName ->
                viewModel.importCodeFromText(raw, fileName)
                showImportSheet = false
            },
            onImportUrl = { url, useName ->
                val suggested = if (useName) null else ""
                viewModel.importCodeFromUrl(url, suggestedName = suggested) {
                    showImportSheet = false
                }
            },
            onShowMessage = viewModel::showMessage,
            onDismiss = { showImportSheet = false }
        )
    }

    workerPendingDelete?.let { w ->
        ConfirmDeleteDialog(
            title = "حذف ورکر ${w.id}",
            message = "آیا مطمئن هستید که می‌خواهید ورکر «${w.id}» را برای همیشه حذف کنید؟",
            onConfirm = {
                viewModel.deleteWorker(w.id)
                workerPendingDelete = null
            },
            onDismiss = { workerPendingDelete = null }
        )
    }
}

@Composable
private fun EditorQuickSnippetBar(onInsertSnippet: (String) -> Unit) {
    val snippets = listOf(
        "Response.json" to "return Response.json({ ok: true });",
        "env.DB" to "const { results } = await env.DB.prepare(\"SELECT 1\").all();",
        "env.KV" to "const val = await env.KV.get(\"key\");",
        "env.BUCKET" to "const obj = await env.BUCKET.get(\"file.txt\");",
        "env.AI" to "const aiRes = await env.AI.run(\"@cf/meta/llama-3.1-8b-instruct\", { prompt: \"Hello\" });"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        snippets.forEach { (label, code) ->
            AssistChip(
                onClick = { onInsertSnippet(code) },
                label = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(label, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = SlateElevated,
                    labelColor = ElectricCyan
                ),
                border = BorderStroke(1.dp, SlateBorder)
            )
        }
    }
}

@Composable
private fun ConfigSwitchRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBg,
                checkedTrackColor = CloudflareOrange
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BindingsBuilderModalSheet(
    state: StudioUiState,
    onAddBinding: (WorkerBinding) -> Unit,
    onRemoveBinding: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(BindingType.PLAIN_TEXT) }
    var varName by remember { mutableStateOf("API_ENV") }
    var value1 by remember { mutableStateOf("") }
    var value2 by remember { mutableStateOf("") }

    fun defaultVarNameFor(type: BindingType): String = when (type) {
        BindingType.PLAIN_TEXT -> "MY_VAR"
        BindingType.D1 -> "DB"
        BindingType.KV -> "KV"
        BindingType.R2 -> "BUCKET"
        BindingType.QUEUE -> "MY_QUEUE"
        BindingType.DURABLE_OBJECT -> "MY_DO"
        BindingType.AI -> "AI"
        BindingType.HYPERDRIVE -> "HYPERDRIVE"
        BindingType.ANALYTICS_ENGINE -> "ANALYTICS"
        BindingType.SERVICE -> "AUTH_SERVICE"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مدیریت بایندینگ‌ها (Bindings Builder)",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "بستن", tint = TextSecondary)
                }
            }

            // Current Bindings
            if (state.editor.bindings.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("بایندینگ‌های فعال:", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                    state.editor.bindings.forEach { b ->
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
                                            text = "env.${b.name}  [${b.type.apiType}]",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = CloudflareOrange
                                        )
                                        val summary = when (b.type) {
                                            BindingType.PLAIN_TEXT -> b.textValue
                                            BindingType.D1 -> b.databaseId
                                            BindingType.KV -> b.namespaceId
                                            BindingType.R2 -> b.bucketName
                                            BindingType.QUEUE -> b.queueName
                                            BindingType.DURABLE_OBJECT -> "${b.className} ${b.scriptName}".trim()
                                            BindingType.AI -> "Workers AI"
                                            BindingType.HYPERDRIVE -> b.hyperdriveId
                                            BindingType.ANALYTICS_ENGINE -> b.dataset
                                            BindingType.SERVICE -> b.serviceName
                                        }
                                        if (summary.isNotBlank()) {
                                            Text(
                                                text = summary,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = { onRemoveBinding(b.uid) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف بایندینگ", tint = StatusError)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = SlateBorder)
            }

            Text("افزودن بایندینگ جدید:", style = MaterialTheme.typography.titleSmall, color = TextPrimary)

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(BindingType.entries) { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            varName = defaultVarNameFor(type)
                            value1 = ""
                            value2 = ""
                        },
                        label = { Text(type.labelFa, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CloudflareOrangeContainer,
                            selectedLabelColor = CloudflareOrange
                        )
                    )
                }
            }

            LtrField(
                value = varName,
                onValueChange = { varName = it },
                label = "نام متغیر در env (مثلاً DB یا API_URL)",
                placeholder = defaultVarNameFor(selectedType)
            )

            when (selectedType) {
                BindingType.PLAIN_TEXT -> {
                    LtrField(value = value1, onValueChange = { value1 = it }, label = "مقدار متغیر (Text)", placeholder = "production")
                }
                BindingType.D1 -> {
                    if (state.d1Databases.isNotEmpty()) {
                        Text("انتخاب از دیتابیس‌های موجود:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.d1Databases, key = { it.uuid }) { db ->
                                AssistChip(
                                    onClick = { value1 = db.uuid },
                                    label = { Text(db.name, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    LtrField(value = value1, onValueChange = { value1 = it }, label = "Database UUID", placeholder = "xxxxxxxx-xxxx-...")
                }
                BindingType.KV -> {
                    if (state.kvNamespaces.isNotEmpty()) {
                        Text("انتخاب از فضاهای KV موجود:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.kvNamespaces, key = { it.id }) { ns ->
                                AssistChip(
                                    onClick = { value1 = ns.id },
                                    label = { Text(ns.title, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    LtrField(value = value1, onValueChange = { value1 = it }, label = "KV Namespace ID", placeholder = "32-char hex ID")
                }
                BindingType.R2 -> {
                    if (state.r2Buckets.isNotEmpty()) {
                        Text("انتخاب از باکت‌های R2 موجود:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.r2Buckets, key = { it.name }) { bucket ->
                                AssistChip(
                                    onClick = { value1 = bucket.name },
                                    label = { Text(bucket.name, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    LtrField(value = value1, onValueChange = { value1 = it }, label = "Bucket Name", placeholder = "my-assets-bucket")
                }
                BindingType.QUEUE -> {
                    if (state.queues.isNotEmpty()) {
                        Text("انتخاب از صف‌های موجود:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.queues, key = { it.queueId }) { q ->
                                AssistChip(
                                    onClick = { value1 = q.queueName },
                                    label = { Text(q.queueName, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    LtrField(value = value1, onValueChange = { value1 = it }, label = "Queue Name", placeholder = "jobs-queue")
                }
                BindingType.DURABLE_OBJECT -> {
                    LtrField(value = value1, onValueChange = { value1 = it }, label = "Class Name", placeholder = "CounterDurableObject")
                    LtrField(value = value2, onValueChange = { value2 = it }, label = "Script Name (اختیاری)", placeholder = "other-worker")
                }
                BindingType.AI -> {
                    Text(
                        text = "بایندینگ Workers AI دسترسی مستقیم به مدل‌های هوش مصنوعی در env.${varName.ifBlank { "AI" }} را فعال می‌کند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan
                    )
                }
                BindingType.HYPERDRIVE -> {
                    LtrField(value = value1, onValueChange = { value1 = it }, label = "Hyperdrive Config ID", placeholder = "hyperdrive-id")
                }
                BindingType.ANALYTICS_ENGINE -> {
                    LtrField(value = value1, onValueChange = { value1 = it }, label = "Dataset Name", placeholder = "app_events")
                }
                BindingType.SERVICE -> {
                    if (state.workers.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.workers, key = { it.id }) { w ->
                                AssistChip(
                                    onClick = { value1 = w.id },
                                    label = { Text(w.id, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    LtrField(value = value1, onValueChange = { value1 = it }, label = "Target Service Worker", placeholder = "auth-worker")
                }
            }

            val cleanVarName = varName.trim()
            val cleanVal1 = value1.trim()
            val bindingValidationHint = when {
                cleanVarName.isBlank() -> "نام متغیر env را وارد کنید (مثلاً DB یا KV)."
                !cleanVarName.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*$")) ->
                    "نام متغیر فقط می‌تواند با حرف انگلیسی یا _ شروع شود و شامل حروف، عدد و _ باشد."
                selectedType == BindingType.D1 && cleanVal1.isBlank() -> "Database UUID را انتخاب یا وارد کنید."
                selectedType == BindingType.KV && cleanVal1.isBlank() -> "KV Namespace ID را انتخاب یا وارد کنید."
                selectedType == BindingType.R2 && cleanVal1.isBlank() -> "نام باکت R2 را انتخاب یا وارد کنید."
                selectedType == BindingType.QUEUE && cleanVal1.isBlank() -> "نام صف (Queue Name) را وارد کنید."
                selectedType == BindingType.DURABLE_OBJECT && cleanVal1.isBlank() -> "نام کلاس (Class Name) Durable Object الزامی است."
                selectedType == BindingType.HYPERDRIVE && cleanVal1.isBlank() -> "شناسه Hyperdrive Config ID را وارد کنید."
                selectedType == BindingType.ANALYTICS_ENGINE && cleanVal1.isBlank() -> "نام Dataset الزامی است."
                selectedType == BindingType.SERVICE && cleanVal1.isBlank() -> "نام ورکر مقصد برای Service Binding الزامی است."
                else -> null
            }

            if (bindingValidationHint != null) {
                Text(
                    text = bindingValidationHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusWarning
                )
            }

            Button(
                onClick = {
                    if (bindingValidationHint == null) {
                        onAddBinding(
                            WorkerBinding(
                                uid = UUID.randomUUID().toString(),
                                type = selectedType,
                                name = cleanVarName,
                                textValue = if (selectedType == BindingType.PLAIN_TEXT) value1 else "",
                                databaseId = if (selectedType == BindingType.D1) cleanVal1 else "",
                                namespaceId = if (selectedType == BindingType.KV) cleanVal1 else "",
                                bucketName = if (selectedType == BindingType.R2) cleanVal1 else "",
                                queueName = if (selectedType == BindingType.QUEUE) cleanVal1 else "",
                                className = if (selectedType == BindingType.DURABLE_OBJECT) cleanVal1 else "",
                                scriptName = if (selectedType == BindingType.DURABLE_OBJECT) value2.trim() else "",
                                hyperdriveId = if (selectedType == BindingType.HYPERDRIVE) cleanVal1 else "",
                                dataset = if (selectedType == BindingType.ANALYTICS_ENGINE) cleanVal1 else "",
                                serviceName = if (selectedType == BindingType.SERVICE) cleanVal1 else ""
                            )
                        )
                        value1 = ""
                        value2 = ""
                    }
                },
                enabled = bindingValidationHint == null,
                colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("add_binding_confirm_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("افزودن به بایندینگ‌های ورکر")
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorRunnerModalSheet(
    state: StudioUiState,
    onSelectMode: (EditorRunnerMode) -> Unit,
    onSelectMethod: (String) -> Unit,
    onChangePath: (String) -> Unit,
    onChangeBody: (String) -> Unit,
    onExecute: () -> Unit,
    onDeployProduction: () -> Unit,
    onGoToDeployTest: () -> Unit,
    onCopyFeedback: () -> Unit,
    onDismiss: () -> Unit
) {
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = StatusSuccess)
                    Text(
                        text = "محیط اجرای کد ورکر",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "بستن", tint = TextSecondary)
                }
            }

            // Mode selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorRunnerMode.entries.forEach { mode ->
                    val selected = state.runnerMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectMode(mode) },
                        label = {
                            Text(
                                text = mode.titleFa,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (mode == EditorRunnerMode.LOCAL_V8) Icons.Default.Memory else Icons.Default.Public,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (mode == EditorRunnerMode.LOCAL_V8) ElectricCyanContainer else CloudflareOrangeContainer,
                            selectedLabelColor = if (mode == EditorRunnerMode.LOCAL_V8) ElectricCyan else CloudflareOrange,
                            selectedLeadingIconColor = if (mode == EditorRunnerMode.LOCAL_V8) ElectricCyan else CloudflareOrange
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("runner_mode_${mode.name.lowercase()}")
                    )
                }
            }

            // Capability explanation card
            Surface(
                color = SlateElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Text(
                    text = if (state.runnerMode == EditorRunnerMode.LOCAL_V8) {
                        "اجرای فوری در موتور جاوااسکریپت V8 روی گوشی (پشتیبانی از console.log و متغیرهای plain_text). بایندینگ‌های ابری مانند D1، KV، R2، AI و importهای npm در شبیه‌ساز آفلاین شبیه‌سازی نمی‌شوند و برای تست واقعی آن‌ها از «اجرای زنده کلودفلر» استفاده کنید."
                    } else {
                        "کد فعلی ادیتور به همراه بایندینگ‌ها به عنوان نسخه تستی روی کلودفلر آپلود شده و بلافاصله روی سرور واقعی کلودفلر اجرا و پاسخ آن نمایش داده می‌شود."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(10.dp)
                )
            }

            // HTTP Method
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(methods) { m ->
                    FilterChip(
                        selected = state.runnerMethod == m,
                        onClick = { onSelectMethod(m) },
                        label = {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(m, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CloudflareOrangeContainer,
                            selectedLabelColor = CloudflareOrange
                        )
                    )
                }
            }

            LtrField(
                value = state.runnerPath,
                onValueChange = onChangePath,
                label = "مسیر درخواست (Path & Query)",
                placeholder = "/api/hello?name=ali",
                testTag = "runner_path_input"
            )

            if (state.runnerMethod != "GET" && state.runnerMethod != "DELETE") {
                LtrField(
                    value = state.runnerBody,
                    onValueChange = onChangeBody,
                    label = "بدنه درخواست (Request Body)",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                    testTag = "runner_body_input"
                )
            }

            Button(
                onClick = onExecute,
                enabled = !state.isRunningCode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.runnerMode == EditorRunnerMode.LOCAL_V8) ElectricCyan else CloudflareOrange,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("execute_runner_button")
            ) {
                if (state.isRunningCode) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ObsidianBg)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.runnerMode == EditorRunnerMode.LOCAL_V8) "در حال اجرای محلی..." else "در حال آپلود و اجرای زنده..."
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (state.runnerMode == EditorRunnerMode.LOCAL_V8) "اجرای محلی در V8" else "آپلود و اجرای زنده روی کلودفلر"
                    )
                }
            }

            // Runner Error
            state.runnerError?.let { err ->
                ErrorBannerCard(error = err)
            }

            // Local V8 Result
            if (state.runnerMode == EditorRunnerMode.LOCAL_V8) {
                state.localRunResult?.let { res ->
                    HorizontalDivider(color = SlateBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isOk = res.errorMessage == null && res.statusCode in 200..299
                        StatusPill(
                            text = "HTTP ${res.statusCode} ${res.statusText}".trim(),
                            color = if (isOk) StatusSuccess else StatusError,
                            containerColor = if (isOk) StatusSuccessContainer else StatusErrorContainer,
                            icon = if (isOk) Icons.Default.CheckCircle else Icons.Default.Error
                        )
                        StatusPill(
                            text = "${res.durationMs} ms",
                            color = ElectricCyan,
                            containerColor = ElectricCyanContainer,
                            icon = Icons.Default.Speed
                        )
                    }

                    if (!res.errorMessage.isNullOrBlank()) {
                        Surface(
                            color = StatusErrorContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StatusError.copy(alpha = 0.6f))
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = res.errorMessage,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                )
                            }
                        }
                    }

                    if (res.consoleLogs.isNotEmpty()) {
                        LtrReadOnlyConsole(
                            title = "خروجی کنسول (console.log)",
                            text = res.consoleLogs.joinToString("\n"),
                            accentColor = CloudflareOrange,
                            onCopy = onCopyFeedback
                        )
                    }

                    if (res.body.isNotBlank() || res.errorMessage.isNullOrBlank()) {
                        LtrReadOnlyConsole(
                            title = "پاسخ خروجی (Response Body)",
                            text = res.body,
                            accentColor = ElectricCyan,
                            onCopy = onCopyFeedback
                        )
                    }

                    if (res.headers.isNotEmpty()) {
                        LtrReadOnlyConsole(
                            title = "هدرهای پاسخ (Headers)",
                            text = res.headers.joinToString("\n") { "${it.first}: ${it.second}" },
                            accentColor = TextSecondary,
                            onCopy = onCopyFeedback
                        )
                    }
                }
            } else {
                // Cloudflare Edge Result
                state.edgeRunResult?.let { res ->
                    HorizontalDivider(color = SlateBorder)
                    val targetUrl = state.previewUrl.ifBlank { state.productionUrl }
                    if (targetUrl.isNotBlank()) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = targetUrl,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isOk = res.statusCode in 200..299
                        StatusPill(
                            text = "HTTP ${res.statusCode} ${res.statusMessage}".trim(),
                            color = if (isOk) StatusSuccess else StatusError,
                            containerColor = if (isOk) StatusSuccessContainer else StatusErrorContainer,
                            icon = if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning
                        )
                        StatusPill(
                            text = "${res.latencyMs} ms",
                            color = ElectricCyan,
                            containerColor = ElectricCyanContainer,
                            icon = Icons.Default.Speed
                        )
                    }

                    LtrReadOnlyConsole(
                        title = "پاسخ زنده کلودفلر (Response Body)",
                        text = res.body,
                        accentColor = CloudflareOrange,
                        onCopy = onCopyFeedback
                    )

                    if (res.headers.isNotEmpty()) {
                        LtrReadOnlyConsole(
                            title = "هدرهای پاسخ (Headers)",
                            text = res.headers.joinToString("\n") { "${it.first}: ${it.second}" },
                            accentColor = TextSecondary,
                            onCopy = onCopyFeedback
                        )
                    }
                }
            }

            // Next step pipeline: Deploy 100% -> Test Link
            HorizontalDivider(color = SlateBorder)
            Text(
                text = "مرحله بعدی: انتشار و تست لینک",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDeployProduction,
                    enabled = !state.isDeploying && !state.isRunningCode,
                    colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("runner_deploy_production_button")
                ) {
                    if (state.isDeploying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ObsidianBg)
                    } else {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("انتشار ۱۰۰٪ نهایی", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onGoToDeployTest,
                    border = BorderStroke(1.dp, ElectricCyan),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("runner_goto_link_test_button")
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تست لینک و لاگ", color = ElectricCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportCodeModalSheet(
    isImporting: Boolean,
    onImportText: (rawCode: String, fileName: String?) -> Unit,
    onImportUrl: (url: String, useFilenameAsWorkerName: Boolean) -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }
    var useFileNameAsWorkerName by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                var displayName: String? = null
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) displayName = cursor.getString(idx)
                    }
                }
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                }.orEmpty()
                if (text.isBlank()) {
                    onShowMessage("فایل انتخاب‌شده خالی یا غیرقابل خواندن است")
                } else {
                    onImportText(text, if (useFileNameAsWorkerName) displayName else null)
                }
            } catch (e: Exception) {
                onShowMessage("خطا در خواندن فایل: ${e.message ?: "نامشخص"}")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = StatusSuccess)
                    Text(
                        text = "ایمپورت کد ورکر",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "بستن", tint = TextSecondary)
                }
            }

            Surface(
                color = SlateElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Text(
                    text = "می‌توانید فایل‌های تک‌فایلی JS / MJS / TXT را از حافظه گوشی، کلیپ‌بورد یا لینک مستقیم (مثل GitHub Raw) وارد ادیتور کنید، سپس تست بگیرید و منتشر کنید. (توجه: پروژه‌های چندفایلی یا پکیج‌های npm قبل از آپلود باید به یک فایل bundle تبدیل شده باشند.)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(10.dp)
                )
            }

            ConfigSwitchRow(
                label = "تنظیم خودکار نام ورکر از نام فایل",
                subtitle = "در صورت فعال بودن، نام فایل جایگزین نام ورکر فعلی می‌شود",
                checked = useFileNameAsWorkerName,
                onCheckedChange = { useFileNameAsWorkerName = it }
            )

            // 1. Device File Picker (Zero-permission SAF)
            Button(
                onClick = {
                    filePickerLauncher.launch(arrayOf("text/*", "application/javascript", "application/x-javascript", "*/*"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess, contentColor = ObsidianBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("pick_worker_file_button")
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("انتخاب فایل از گوشی (.js / .mjs / .txt)")
            }

            // 2. Paste from Clipboard
            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipText = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (clipText.isBlank()) {
                        onShowMessage("کلیپ‌بورد خالی است")
                    } else {
                        onImportText(clipText, null)
                    }
                },
                border = BorderStroke(1.dp, ElectricCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("paste_clipboard_code_button")
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = ElectricCyan)
                Spacer(Modifier.width(8.dp))
                Text("جایگذاری سریع از کلیپ‌بورد (Paste)", color = ElectricCyan)
            }

            HorizontalDivider(color = SlateBorder)

            // 3. Import from URL / GitHub
            LtrField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = "وارد کردن از لینک مستقیم یا GitHub",
                placeholder = "https://github.com/user/repo/blob/main/worker.js",
                testTag = "import_code_url_input"
            )

            Button(
                onClick = { onImportUrl(urlInput, useFileNameAsWorkerName) },
                enabled = !isImporting && urlInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("fetch_import_url_button")
            ) {
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ObsidianBg)
                    Spacer(Modifier.width(8.dp))
                    Text("در حال دانلود کد...")
                } else {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("دریافت و ایمپورت از لینک")
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

