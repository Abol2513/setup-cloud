package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.StudioUiState
import com.example.ui.StudioViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun StorageTabScreen(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Sub-tab bar
        ScrollableTabRow(
            selectedTabIndex = StorageSubTab.entries.indexOf(state.storageSubTab),
            containerColor = SlateSurface,
            contentColor = CloudflareOrange,
            edgePadding = 12.dp
        ) {
            StorageSubTab.entries.forEach { subTab ->
                Tab(
                    selected = state.storageSubTab == subTab,
                    onClick = { viewModel.selectStorageSubTab(subTab) },
                    text = {
                        Text(
                            text = subTab.titleFa,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state.storageSubTab == subTab) CloudflareOrange else TextSecondary
                        )
                    }
                )
            }
        }

        when (state.storageSubTab) {
            StorageSubTab.D1 -> D1Section(state, viewModel)
            StorageSubTab.KV -> KvSection(state, viewModel)
            StorageSubTab.R2 -> R2Section(state, viewModel)
            StorageSubTab.QUEUES_DO -> QueuesAndDoSection(state, viewModel)
        }
    }
}

@Composable
private fun D1Section(state: StudioUiState, viewModel: StudioViewModel) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var dbPendingDelete by remember { mutableStateOf<D1Database?>(null) }
    var showJsonResult by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.d1Error?.let { err ->
            item { ErrorBannerCard(error = err) }
        }

        item {
            SectionCard(
                title = "دیتابیس‌های SQL D1 (${state.d1Databases.size})",
                icon = Icons.Default.Storage,
                headerAction = {
                    Row {
                        IconButton(onClick = { viewModel.refreshD1() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی D1", tint = ElectricCyan)
                        }
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "ساخت D1", tint = CloudflareOrange)
                        }
                    }
                }
            ) {
                if (state.d1Loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CloudflareOrange)
                }

                if (state.d1Databases.isEmpty() && !state.d1Loading) {
                    EmptyStateBox(
                        icon = Icons.Default.Storage,
                        title = "دیتابیس D1 یافت نشد",
                        subtitle = "برای ساخت دیتابیس جدید آیکون + را بزنید."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.d1Databases.forEach { db ->
                            val isSelected = state.selectedD1?.uuid == db.uuid
                            Surface(
                                color = if (isSelected) SlateHighlight else SlateElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) CloudflareOrange else SlateBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectD1(db) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Text(
                                                    text = db.name,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = if (isSelected) CloudflareOrange else TextPrimary
                                                )
                                                Text(
                                                    text = db.uuid,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Row {
                                            OutlinedButton(
                                                onClick = { viewModel.bindD1ToWorker(db) },
                                                border = BorderStroke(1.dp, ElectricCyan),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.heightIn(min = 38.dp)
                                            ) {
                                                Text("اتصال به ورکر", color = ElectricCyan, style = MaterialTheme.typography.labelSmall)
                                            }
                                            IconButton(onClick = { dbPendingDelete = db }) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف D1", tint = StatusError)
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

        if (state.selectedD1 != null) {
            item {
                SectionCard(
                    title = "کنسول کوئری SQL — ${state.selectedD1.name}",
                    icon = Icons.Default.Terminal,
                    headerAction = {
                        TextButton(
                            onClick = {
                                viewModel.runD1Query("SELECT name, type, sql FROM sqlite_schema WHERE type IN ('table','view') ORDER BY name;")
                            }
                        ) {
                            Text("نمایش جدولها", color = ElectricCyan)
                        }
                    }
                ) {
                    LtrField(
                        value = state.d1SqlQuery,
                        onValueChange = viewModel::updateD1SqlQuery,
                        label = "دستور SQL",
                        placeholder = "SELECT * FROM users LIMIT 20;",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 8,
                        testTag = "d1_sql_input"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.runD1Query() },
                            enabled = !state.d1Loading,
                            colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("run_d1_query_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("اجرای کوئری")
                        }
                        FilterChip(
                            selected = showJsonResult,
                            onClick = { showJsonResult = !showJsonResult },
                            label = { Text(if (showJsonResult) "نمایش جدول" else "نمایش JSON") }
                        )
                    }

                    state.d1QueryResult?.let { result ->
                        Text(
                            text = result.metaSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusSuccess
                        )
                        if (showJsonResult || result.columns.isEmpty()) {
                            LtrReadOnlyConsole(
                                text = result.rawJson,
                                title = "D1 JSON Output",
                                onCopy = { viewModel.showMessage("خروجی JSON کپی شد") }
                            )
                        } else {
                            // Horizontal scrollable SQL table
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                val hScroll = rememberScrollState()
                                Surface(
                                    color = CodeSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, SlateBorder)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(hScroll)
                                            .padding(8.dp)
                                    ) {
                                        // Header
                                        Row(
                                            modifier = Modifier
                                                .background(SlateElevated)
                                                .padding(vertical = 6.dp, horizontal = 8.dp)
                                        ) {
                                            result.columns.forEach { col ->
                                                Text(
                                                    text = col,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ElectricCyan,
                                                    modifier = Modifier
                                                        .widthIn(min = 90.dp, max = 220.dp)
                                                        .padding(end = 12.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = SlateBorder)
                                        result.rows.take(100).forEach { row ->
                                            Row(
                                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
                                            ) {
                                                row.forEach { cell ->
                                                    Text(
                                                        text = cell,
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = TextPrimary,
                                                        modifier = Modifier
                                                            .widthIn(min = 90.dp, max = 220.dp)
                                                            .padding(end = 12.dp),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            HorizontalDivider(color = SlateBorder.copy(alpha = 0.4f))
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

    if (showCreateDialog) {
        SingleNameCreateDialog(
            title = "ساخت دیتابیس D1 جدید",
            label = "Database Name",
            placeholder = "app-main-db",
            onConfirm = { name ->
                viewModel.createD1Database(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    dbPendingDelete?.let { db ->
        ConfirmDeleteDialog(
            title = "حذف دیتابیس ${db.name}",
            message = "تمامی جداول و داده‌های این دیتابیس D1 پاک خواهند شد.",
            onConfirm = {
                viewModel.deleteD1Database(db.uuid)
                dbPendingDelete = null
            },
            onDismiss = { dbPendingDelete = null }
        )
    }
}

@Composable
private fun KvSection(state: StudioUiState, viewModel: StudioViewModel) {
    var showCreateNsDialog by remember { mutableStateOf(false) }
    var nsPendingDelete by remember { mutableStateOf<KvNamespace?>(null) }
    var keyNameInput by remember(state.selectedKvReadSeq, state.selectedKvKeyName) {
        mutableStateOf(state.selectedKvKeyName)
    }
    var keyValueInput by remember(state.selectedKvReadSeq) {
        mutableStateOf(state.selectedKvKeyValue)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.kvError?.let { item { ErrorBannerCard(error = it) } }

        item {
            SectionCard(
                title = "فضاهای Workers KV (${state.kvNamespaces.size})",
                icon = Icons.Default.Key,
                headerAction = {
                    Row {
                        IconButton(onClick = { viewModel.refreshKv() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی KV", tint = ElectricCyan)
                        }
                        IconButton(onClick = { showCreateNsDialog = true }) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "ساخت KV", tint = CloudflareOrange)
                        }
                    }
                }
            ) {
                if (state.kvLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CloudflareOrange)
                }
                if (state.kvNamespaces.isEmpty() && !state.kvLoading) {
                    EmptyStateBox(
                        icon = Icons.Default.Key,
                        title = "فضای KV یافت نشد",
                        subtitle = "برای ایجاد یک Namespace جدید دکمه + را بزنید."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.kvNamespaces.forEach { ns ->
                            val isSelected = state.selectedKv?.id == ns.id
                            Surface(
                                color = if (isSelected) SlateHighlight else SlateElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) CloudflareOrange else SlateBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectKv(ns) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = ns.title,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (isSelected) CloudflareOrange else TextPrimary
                                            )
                                            Text(
                                                text = ns.id,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row {
                                        OutlinedButton(
                                            onClick = { viewModel.bindKvToWorker(ns) },
                                            border = BorderStroke(1.dp, ElectricCyan),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.heightIn(min = 38.dp)
                                        ) {
                                            Text("اتصال به ورکر", color = ElectricCyan, style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { nsPendingDelete = ns }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف KV", tint = StatusError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.selectedKv != null) {
            item {
                SectionCard(
                    title = "کلیدها و مقادیر — ${state.selectedKv.title}",
                    icon = Icons.Default.DataObject
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        LtrField(
                            value = state.kvPrefixFilter,
                            onValueChange = viewModel::updateKvPrefixFilter,
                            label = "فیلتر پیشوند (Prefix)",
                            placeholder = "user:",
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = viewModel::refreshKvKeys,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                            modifier = Modifier.heightIn(min = 52.dp)
                        ) {
                            Text("جستجو")
                        }
                    }

                    if (state.kvKeys.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.kvKeys, key = { it.name }) { entry ->
                                FilterChip(
                                    selected = entry.name == state.selectedKvKeyName,
                                    onClick = { viewModel.readKvValue(entry.name) },
                                    label = {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(entry.name, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        Text("کلیدی یافت نشد.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }

                    HorizontalDivider(color = SlateBorder)

                    LtrField(
                        value = keyNameInput,
                        onValueChange = { keyNameInput = it },
                        label = "نام کلید (Key)",
                        placeholder = "config:theme"
                    )
                    LtrField(
                        value = keyValueInput,
                        onValueChange = { keyValueInput = it },
                        label = "مقدار (Value)",
                        placeholder = "{\"dark\": true}",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 8
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.writeKvValue(keyNameInput, keyValueInput) },
                            enabled = keyNameInput.isNotBlank() && !state.kvLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text("ذخیره / بروزرسانی کلید")
                        }
                        if (keyNameInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = { viewModel.deleteKvKey(keyNameInput) },
                                border = BorderStroke(1.dp, StatusError),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("حذف کلید", color = StatusError)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateNsDialog) {
        SingleNameCreateDialog(
            title = "ساخت فضای KV جدید",
            label = "Namespace Title",
            placeholder = "CACHE_KV",
            onConfirm = { title ->
                viewModel.createKvNamespace(title)
                showCreateNsDialog = false
            },
            onDismiss = { showCreateNsDialog = false }
        )
    }

    nsPendingDelete?.let { ns ->
        ConfirmDeleteDialog(
            title = "حذف فضای KV ${ns.title}",
            message = "آیا از حذف این فضای KV و تمامی کلیدهای آن مطمئن هستید؟",
            onConfirm = {
                viewModel.deleteKvNamespace(ns.id)
                nsPendingDelete = null
            },
            onDismiss = { nsPendingDelete = null }
        )
    }
}

@Composable
private fun R2Section(state: StudioUiState, viewModel: StudioViewModel) {
    var showCreateBucketDialog by remember { mutableStateOf(false) }
    var bucketPendingDelete by remember { mutableStateOf<R2Bucket?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.r2Error?.let { item { ErrorBannerCard(error = it) } }

        item {
            SectionCard(
                title = "باکت‌های Cloudflare R2 (${state.r2Buckets.size})",
                icon = Icons.Default.Folder,
                headerAction = {
                    Row {
                        IconButton(onClick = { viewModel.refreshR2() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی R2", tint = ElectricCyan)
                        }
                        IconButton(onClick = { showCreateBucketDialog = true }) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "ساخت باکت R2", tint = CloudflareOrange)
                        }
                    }
                }
            ) {
                if (state.r2Loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CloudflareOrange)
                }
                if (state.r2Buckets.isEmpty() && !state.r2Loading) {
                    EmptyStateBox(
                        icon = Icons.Default.Folder,
                        title = "باکت R2 یافت نشد",
                        subtitle = "برای ایجاد باکت جدید آیکون + را بزنید."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.r2Buckets.forEach { bucket ->
                            val isSelected = state.selectedR2?.name == bucket.name
                            Surface(
                                color = if (isSelected) SlateHighlight else SlateElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) CloudflareOrange else SlateBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.inspectR2Bucket(bucket) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = bucket.name,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (isSelected) CloudflareOrange else TextPrimary
                                            )
                                        }
                                        if (bucket.location.isNotBlank()) {
                                            Text(
                                                text = "منطقه: ${bucket.location}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                    Row {
                                        OutlinedButton(
                                            onClick = { viewModel.bindR2ToWorker(bucket) },
                                            border = BorderStroke(1.dp, ElectricCyan),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.heightIn(min = 38.dp)
                                        ) {
                                            Text("اتصال به ورکر", color = ElectricCyan, style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { bucketPendingDelete = bucket }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف باکت", tint = StatusError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.selectedR2 != null) {
            item {
                SectionCard(
                    title = "فایل‌ها و تنظیمات — ${state.selectedR2.name}",
                    icon = Icons.Default.Public
                ) {
                    state.r2ManagedDomain?.let { dom ->
                        Surface(
                            color = SlateElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("دسترسی عمومی r2.dev", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    if (dom.domain.isNotBlank()) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = dom.domain,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ElectricCyan
                                            )
                                        }
                                    }
                                }
                                Switch(
                                    checked = dom.enabled,
                                    onCheckedChange = viewModel::toggleR2ManagedDomain,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianBg,
                                        checkedTrackColor = CloudflareOrange
                                    )
                                )
                            }
                        }
                    }

                    Text(
                        text = "آبجکت‌های داخل باکت (${state.r2Objects.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    if (state.r2Objects.isEmpty()) {
                        Text("این باکت در حال حاضر خالی است.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.r2Objects.take(50).forEach { obj ->
                                Surface(
                                    color = CodeSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, SlateBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = obj.key,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextPrimary,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${obj.size} B",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ElectricCyan
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (state.r2CorsJson.isNotBlank()) {
                        LtrReadOnlyConsole(
                            text = state.r2CorsJson,
                            title = "R2 CORS Rules",
                            onCopy = { viewModel.showMessage("قوانین CORS کپی شد") }
                        )
                    }
                }
            }
        }
    }

    if (showCreateBucketDialog) {
        SingleNameCreateDialog(
            title = "ساخت باکت R2 جدید",
            label = "Bucket Name (lowercase)",
            placeholder = "media-assets",
            onConfirm = { name ->
                viewModel.createR2Bucket(name)
                showCreateBucketDialog = false
            },
            onDismiss = { showCreateBucketDialog = false }
        )
    }

    bucketPendingDelete?.let { b ->
        ConfirmDeleteDialog(
            title = "حذف باکت ${b.name}",
            message = "باکت باید خالی باشد. آیا از حذف باکت «${b.name}» مطمئن هستید؟",
            onConfirm = {
                viewModel.deleteR2Bucket(b.name)
                bucketPendingDelete = null
            },
            onDismiss = { bucketPendingDelete = null }
        )
    }
}

@Composable
private fun QueuesAndDoSection(state: StudioUiState, viewModel: StudioViewModel) {
    var showCreateQueueDialog by remember { mutableStateOf(false) }
    var queuePendingDelete by remember { mutableStateOf<QueueInfo?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.queuesDoError?.let { item { ErrorBannerCard(error = it) } }

        item {
            SectionCard(
                title = "صف‌های پیام (Cloudflare Queues)",
                icon = Icons.Default.Queue,
                headerAction = {
                    Row {
                        IconButton(onClick = { viewModel.refreshQueuesAndDo() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی", tint = ElectricCyan)
                        }
                        IconButton(onClick = { showCreateQueueDialog = true }) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "ساخت صف", tint = CloudflareOrange)
                        }
                    }
                }
            ) {
                if (state.queuesDoLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CloudflareOrange)
                }
                if (state.queues.isEmpty() && !state.queuesDoLoading) {
                    EmptyStateBox(
                        icon = Icons.Default.Queue,
                        title = "صفی تعریف نشده است",
                        subtitle = "برای ایجاد صف پیام دکمه + را بزنید."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.queues.forEach { q ->
                            Surface(
                                color = SlateElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = q.queueName,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = TextPrimary
                                            )
                                        }
                                        Text(
                                            text = "تولیدکننده: ${q.producersCount} | مصرف‌کننده: ${q.consumersCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    IconButton(onClick = { queuePendingDelete = q }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف صف", tint = StatusError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "فضاهای Durable Objects (${state.doNamespaces.size})",
                icon = Icons.Default.Memory
            ) {
                if (state.doNamespaces.isEmpty()) {
                    EmptyStateBox(
                        icon = Icons.Default.Memory,
                        title = "فضای Durable Object یافت نشد",
                        subtitle = "پس از تعریف Durable Object در ورکر، کلاس‌های آن اینجا نمایش داده می‌شوند."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.doNamespaces.forEach { ns ->
                            val isSelected = state.selectedDoNamespace?.id == ns.id
                            Surface(
                                color = if (isSelected) SlateHighlight else SlateElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) ElectricCyan else SlateBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.inspectDoNamespace(ns) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = "${ns.name} (${ns.className})",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = ElectricCyan
                                        )
                                        Text(
                                            text = "Script: ${ns.script} • ID: ${ns.id}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.selectedDoNamespace != null) {
                    HorizontalDivider(color = SlateBorder)
                    Text(
                        text = "آبجکت‌های داخل ${state.selectedDoNamespace.name} (${state.doInstances.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    if (state.doInstances.isEmpty()) {
                        Text("نمونه‌ای (Instance) ثبت نشده است.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    } else {
                        state.doInstances.take(30).forEach { inst ->
                            LtrReadOnlyConsole(
                                text = "${inst.id}  (hasStoredData=${inst.hasStoredData})"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateQueueDialog) {
        SingleNameCreateDialog(
            title = "ساخت صف پیام جدید",
            label = "Queue Name",
            placeholder = "email-jobs-queue",
            onConfirm = { name ->
                viewModel.createQueue(name)
                showCreateQueueDialog = false
            },
            onDismiss = { showCreateQueueDialog = false }
        )
    }

    queuePendingDelete?.let { q ->
        ConfirmDeleteDialog(
            title = "حذف صف ${q.queueName}",
            message = "آیا از حذف صف «${q.queueName}» مطمئن هستید؟",
            onConfirm = {
                viewModel.deleteQueue(q.queueId)
                queuePendingDelete = null
            },
            onDismiss = { queuePendingDelete = null }
        )
    }
}

@Composable
private fun SingleNameCreateDialog(
    title: String,
    label: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateElevated,
        title = { Text(title, color = TextPrimary) },
        text = {
            LtrField(
                value = name,
                onValueChange = { name = it },
                label = label,
                placeholder = placeholder
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg)
            ) {
                Text("ساخت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف", color = TextSecondary) }
        }
    )
}
