package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.AccountPreferencesRepository
import com.example.model.CloudflareAccount
import com.example.ui.theme.*

@Composable
fun AccountPillButton(
    account: CloudflareAccount,
    isTesting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = SlateElevated,
        shape = RoundedCornerShape(50),
        border = BorderStroke(
            1.dp,
            if (account.isVerified) StatusSuccess.copy(alpha = 0.5f) else SlateBorder
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("account_pill_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = CloudflareOrange
                )
            } else {
                Icon(
                    imageVector = if (account.isVerified) Icons.Default.Verified else Icons.Default.Cloud,
                    contentDescription = "وضعیت اکانت",
                    tint = if (account.isVerified) StatusSuccess else CloudflareOrange,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = account.profileName,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = if (account.subdomain.isNotBlank()) {
                            "${account.subdomain}.workers.dev"
                        } else {
                            AccountPreferencesRepository.maskAccountId(account.accountId)
                        },
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "تغییر اکانت",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AccountsDrawerContent(
    accounts: List<CloudflareAccount>,
    activeAccountId: String,
    isTestingConnection: Boolean,
    onSwitchAccount: (String) -> Unit,
    onTestConnection: () -> Unit,
    onOpenSubdomainDialog: () -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (CloudflareAccount) -> Unit,
    onDeleteAccount: (CloudflareAccount) -> Unit,
    onResetDefault: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = SlateSurface,
        drawerContentColor = TextPrimary,
        modifier = Modifier.widthIn(max = 330.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
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
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = CloudflareOrange
                    )
                    Column {
                        Text(
                            text = "مدیریت اکانت‌های کلودفلر",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Setup cloud Studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
                IconButton(onClick = onCloseDrawer) {
                    Icon(Icons.Default.Close, contentDescription = "بستن منو", tint = TextSecondary)
                }
            }

            // Quick Actions for Active Account
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTestConnection,
                    enabled = !isTestingConnection,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CloudflareOrange,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("test_connection_button")
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ObsidianBg)
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("تست اتصال و سابدامین", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAddAccount,
                    border = BorderStroke(1.dp, ElectricCyan),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("add_account_button")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("افزودن اکانت جدید", color = ElectricCyan)
                }

                OutlinedButton(
                    onClick = onOpenSubdomainDialog,
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Dns, contentDescription = "ثبت سابدامین", tint = TextPrimary, modifier = Modifier.size(16.dp))
                }
            }

            HorizontalDivider(color = SlateBorder)

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(accounts, key = { it.id }) { account ->
                    val isActive = account.id == activeAccountId
                    var revealToken by remember(account.id) { mutableStateOf(false) }

                    Surface(
                        color = if (isActive) SlateHighlight else SlateElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = if (isActive) 1.5.dp else 1.dp,
                            color = if (isActive) CloudflareOrange else SlateBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSwitchAccount(account.id)
                                onCloseDrawer()
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = account.profileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isActive) {
                                        StatusPill(
                                            text = "فعال",
                                            color = CloudflareOrange,
                                            containerColor = CloudflareOrangeContainer
                                        )
                                    }
                                }
                                if (account.isVerified) {
                                    StatusPill(
                                        text = "تایید شده",
                                        color = StatusSuccess,
                                        containerColor = StatusSuccessContainer,
                                        icon = Icons.Default.CheckCircle
                                    )
                                }
                            }

                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = "ID: ${AccountPreferencesRepository.maskAccountId(account.accountId)}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (revealToken) account.apiToken
                                        else AccountPreferencesRepository.maskToken(account.apiToken),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { revealToken = !revealToken },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (revealToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (revealToken) "مخفی کردن توکن" else "نمایش توکن",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (account.subdomain.isNotBlank()) {
                                    Text(
                                        text = "https://*.${account.subdomain}.workers.dev",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { onEditAccount(account) }) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricCyan)
                                    Spacer(Modifier.width(4.dp))
                                    Text("ویرایش", color = ElectricCyan)
                                }
                                if (accounts.size > 1) {
                                    TextButton(onClick = { onDeleteAccount(account) }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusError)
                                        Spacer(Modifier.width(4.dp))
                                        Text("حذف", color = StatusError)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = onResetDefault,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("بازگردانی اکانت پیش‌فرض", color = TextSecondary)
            }
        }
    }
}

@Composable
fun AccountEditorDialog(
    initialAccount: CloudflareAccount?,
    isVerifyingDraft: Boolean,
    draftVerifyStatus: String?,
    onVerifyDraft: (String, String) -> Unit,
    onSave: (String?, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var profileName by remember(initialAccount) { mutableStateOf(initialAccount?.profileName ?: "") }
    var accountId by remember(initialAccount) { mutableStateOf(initialAccount?.accountId ?: "") }
    var apiToken by remember(initialAccount) { mutableStateOf(initialAccount?.apiToken ?: "") }
    var revealToken by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateElevated,
        title = {
            Text(
                text = if (initialAccount == null) "افزودن اکانت جدید" else "ویرایش اکانت",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            val cleanAccountId = accountId.trim()
            val showAccountIdFormatWarning = cleanAccountId.isNotEmpty() &&
                !cleanAccountId.matches(Regex("^[a-fA-F0-9]{32}$"))
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("نام پروفایل (مثلاً پروژه شخصی)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                LtrField(
                    value = accountId,
                    onValueChange = { accountId = it },
                    label = "Cloudflare Account ID",
                    placeholder = "32-char hex ID"
                )
                if (showAccountIdFormatWarning) {
                    Text(
                        text = "هشدار: Account ID کلودفلر معمولاً ۳۲ کاراکتر هگزادسیمال (0-9 و a-f) است.",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusWarning
                    )
                }
                LtrField(
                    value = apiToken,
                    onValueChange = { apiToken = it },
                    label = "API Token (Bearer)",
                    placeholder = "cfat_...",
                    visualTransformation = if (revealToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { revealToken = !revealToken }) {
                            Icon(
                                imageVector = if (revealToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "نمایش/مخفی توکن",
                                tint = ElectricCyan
                            )
                        }
                    }
                )

                OutlinedButton(
                    onClick = { onVerifyDraft(accountId, apiToken) },
                    enabled = !isVerifyingDraft && accountId.isNotBlank() && apiToken.isNotBlank(),
                    border = BorderStroke(1.dp, ElectricCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    if (isVerifyingDraft) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ElectricCyan)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("بررسی اعتبار", color = ElectricCyan)
                }

                if (!draftVerifyStatus.isNullOrBlank()) {
                    Surface(
                        color = if (draftVerifyStatus.startsWith("خطا")) StatusErrorContainer else StatusSuccessContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = draftVerifyStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (profileName.isNotBlank() && accountId.isNotBlank() && apiToken.isNotBlank()) {
                        onSave(initialAccount?.id, profileName, accountId, apiToken)
                    }
                },
                enabled = profileName.isNotBlank() && accountId.isNotBlank() && apiToken.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg)
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = TextSecondary)
            }
        }
    )
}

@Composable
fun RegisterSubdomainDialog(
    currentSubdomain: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var sub by remember(currentSubdomain) { mutableStateOf(currentSubdomain) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateElevated,
        title = { Text("تنظیم سابدامین workers.dev", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "اگر برای این اکانت هنوز سابدامین workers.dev ثبت نکرده‌اید، نام دلخواه را وارد کنید:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                LtrField(
                    value = sub,
                    onValueChange = { sub = it.lowercase().filter { c -> c in 'a'..'z' || c in '0'..'9' || c == '-' } },
                    label = "Subdomain",
                    placeholder = "my-edge-studio"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(sub) },
                enabled = sub.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CloudflareOrange, contentColor = ObsidianBg)
            ) {
                Text("ثبت سابدامین")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف", color = TextSecondary) }
        }
    )
}
