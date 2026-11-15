package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.CloudflareAccount
import com.example.model.StudioTab
import com.example.ui.StudioViewModel
import com.example.ui.components.*
import com.example.ui.screens.DeployTestTabScreen
import com.example.ui.screens.SettingsDomainsTabScreen
import com.example.ui.screens.StorageTabScreen
import com.example.ui.screens.WorkersTabScreen
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SetupCloudTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SetupCloudRootScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupCloudRootScreen(
    viewModel: StudioViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAccountEditorDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<CloudflareAccount?>(null) }
    var accountPendingDelete by remember { mutableStateOf<CloudflareAccount?>(null) }
    var showSubdomainDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.consumeSnackbar()
        }
    }

    // Back handling for expanded editor or secondary tabs
    BackHandler(enabled = state.editor.isEditorExpanded || state.activeTab != StudioTab.WORKERS) {
        if (state.editor.isEditorExpanded) {
            viewModel.setEditorExpanded(false)
        } else {
            viewModel.selectTab(StudioTab.WORKERS)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AccountsDrawerContent(
                accounts = state.accounts,
                activeAccountId = state.activeAccountId,
                isTestingConnection = state.isTestingConnection,
                onSwitchAccount = viewModel::switchAccount,
                onTestConnection = viewModel::testConnectionAndSubdomain,
                onOpenSubdomainDialog = { showSubdomainDialog = true },
                onAddAccount = {
                    editingAccount = null
                    viewModel.clearDraftVerifyStatus()
                    showAccountEditorDialog = true
                },
                onEditAccount = { acc ->
                    editingAccount = acc
                    viewModel.clearDraftVerifyStatus()
                    showAccountEditorDialog = true
                },
                onDeleteAccount = { acc -> accountPendingDelete = acc },
                onResetDefault = viewModel::resetToDefaultAccount,
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 700.dp

            Scaffold(
                containerColor = ObsidianBg,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = SlateHighlight,
                            contentColor = TextPrimary,
                            actionColor = CloudflareOrange
                        )
                    }
                },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = SlateSurface,
                            titleContentColor = TextPrimary
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("open_accounts_drawer_button")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "منوی اکانت‌ها", tint = TextPrimary)
                            }
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Setup cloud",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        },
                        actions = {
                            AccountPillButton(
                                account = state.activeAccount,
                                isTesting = state.isTestingConnection,
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    )
                },
                bottomBar = {
                    if (!isWideScreen && !state.editor.isEditorExpanded) {
                        NavigationBar(
                            containerColor = SlateSurface,
                            contentColor = TextPrimary,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            StudioTab.entries.forEach { tab ->
                                val selected = state.activeTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = tabIcon(tab, selected),
                                            contentDescription = tab.titleFa
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.titleFa,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CloudflareOrange,
                                        selectedTextColor = CloudflareOrange,
                                        indicatorColor = CloudflareOrangeContainer,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isWideScreen && !state.editor.isEditorExpanded) {
                        NavigationRail(
                            containerColor = SlateSurface,
                            contentColor = TextPrimary
                        ) {
                            Spacer(Modifier.height(8.dp))
                            StudioTab.entries.forEach { tab ->
                                val selected = state.activeTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = tabIcon(tab, selected),
                                            contentDescription = tab.titleFa
                                        )
                                    },
                                    label = { Text(tab.titleFa, style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = CloudflareOrange,
                                        selectedTextColor = CloudflareOrange,
                                        indicatorColor = CloudflareOrangeContainer,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 880.dp)
                        ) {
                            state.globalError?.let { err ->
                                ErrorBannerCard(
                                    error = err,
                                    onDismiss = viewModel::dismissGlobalError,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }

                            when (state.activeTab) {
                                StudioTab.WORKERS -> WorkersTabScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    onNavigateToDeployTest = { viewModel.selectTab(StudioTab.DEPLOY_TEST) },
                                    modifier = Modifier.weight(1f)
                                )
                                StudioTab.DEPLOY_TEST -> DeployTestTabScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    modifier = Modifier.weight(1f)
                                )
                                StudioTab.STORAGE -> StorageTabScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    modifier = Modifier.weight(1f)
                                )
                                StudioTab.SETTINGS -> SettingsDomainsTabScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAccountEditorDialog) {
        AccountEditorDialog(
            initialAccount = editingAccount,
            isVerifyingDraft = state.isVerifyingDraft,
            draftVerifyStatus = state.draftVerifyStatus,
            onVerifyDraft = viewModel::verifyDraftAccount,
            onSave = { id, name, accId, token ->
                viewModel.saveAccount(id, name, accId, token)
                showAccountEditorDialog = false
            },
            onDismiss = { showAccountEditorDialog = false }
        )
    }

    accountPendingDelete?.let { acc ->
        ConfirmDeleteDialog(
            title = "حذف اکانت ${acc.profileName}",
            message = "این پروفایل از حافظه دستگاه حذف خواهد شد.",
            onConfirm = {
                viewModel.deleteAccount(acc.id)
                accountPendingDelete = null
            },
            onDismiss = { accountPendingDelete = null }
        )
    }

    if (showSubdomainDialog) {
        RegisterSubdomainDialog(
            currentSubdomain = state.activeAccount.subdomain,
            onSave = { sub ->
                viewModel.registerSubdomain(sub)
                showSubdomainDialog = false
            },
            onDismiss = { showSubdomainDialog = false }
        )
    }
}

private fun tabIcon(tab: StudioTab, selected: Boolean): ImageVector = when (tab) {
    StudioTab.WORKERS -> if (selected) Icons.Filled.Code else Icons.Outlined.Code
    StudioTab.DEPLOY_TEST -> if (selected) Icons.Filled.RocketLaunch else Icons.Outlined.RocketLaunch
    StudioTab.STORAGE -> if (selected) Icons.Filled.Storage else Icons.Outlined.Storage
    StudioTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}
