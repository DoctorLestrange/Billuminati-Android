package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.WelcomeDialog
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InvoiceEditorScreen
import com.example.ui.screens.PdfPreviewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.BilluminatiTheme
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.squareRuledBackground
import com.example.ui.viewmodel.BilluminatiViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val CATALOG = "catalog"
    const val SETTINGS = "settings"
    const val EDITOR = "editor"
    const val PDF_PREVIEW = "pdf_preview"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(
        route = Routes.DASHBOARD,
        label = "Invoices",
        selectedIcon = Icons.Filled.ReceiptLong,
        unselectedIcon = Icons.Outlined.ReceiptLong,
        testTag = "nav_invoices"
    ),
    TopLevelDestination(
        route = Routes.CATALOG,
        label = "Catalog",
        selectedIcon = Icons.Filled.Inventory2,
        unselectedIcon = Icons.Outlined.Inventory2,
        testTag = "nav_catalog"
    ),
    TopLevelDestination(
        route = Routes.SETTINGS,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: BilluminatiViewModel = viewModel()
            BilluminatiApp(viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilluminatiApp(viewModel: BilluminatiViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editingInvoice by viewModel.editingInvoice.collectAsStateWithLifecycle()
    val isCreatingNew by viewModel.isCreatingNew.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val pdfPreviewInvoiceId by viewModel.pdfPreviewInvoiceId.collectAsStateWithLifecycle()

    val isDark = uiState.preferences.isDarkTheme
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.DASHBOARD
    val isTopLevel = currentRoute in setOf(Routes.DASHBOARD, Routes.CATALOG, Routes.SETTINGS)

    var settingsTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BilluminatiTheme(darkTheme = isDark) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .squareRuledBackground(isDark = isDark)
        ) {
            val isExpandedWidth = maxWidth >= 840.dp

            val navigateTopLevel: (String) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(Routes.DASHBOARD) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                if (isExpandedWidth && isTopLevel) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        TOP_LEVEL_DESTINATIONS.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (dest.route == Routes.SETTINGS && currentRoute != Routes.SETTINGS) {
                                        settingsTab = 0
                                    }
                                    navigateTopLevel(dest.route)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outline)
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        if (isTopLevel) {
                            val topTitle = when (currentRoute) {
                                Routes.CATALOG -> "Catalog Presets"
                                Routes.SETTINGS -> "Settings"
                                else -> "Billuminati"
                            }
                            androidx.compose.foundation.layout.Column {
                                TopAppBar(
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    title = {
                                        Text(
                                            text = topTitle,
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            letterSpacing = (-0.4).sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = { viewModel.toggleTheme() },
                                            modifier = Modifier.testTag("theme_toggle_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                                contentDescription = "Toggle light or dark theme"
                                            )
                                        }
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    },
                    bottomBar = {
                        if (!isExpandedWidth && isTopLevel) {
                            Box {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp
                                ) {
                                    TOP_LEVEL_DESTINATIONS.forEach { dest ->
                                        val selected = currentRoute == dest.route
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = {
                                                if (dest.route == Routes.SETTINGS && currentRoute != Routes.SETTINGS) {
                                                    settingsTab = 0
                                                }
                                                navigateTopLevel(dest.route)
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                    contentDescription = dest.label
                                                )
                                            },
                                            label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.testTag(dest.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    floatingActionButton = {
                        if (currentRoute == Routes.DASHBOARD) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    viewModel.startNewInvoice()
                                    navController.navigate(Routes.EDITOR)
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                                text = { Text("New Invoice", style = MaterialTheme.typography.labelLarge) },
                                modifier = Modifier.testTag("fab_new_invoice")
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Routes.DASHBOARD,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        composable(Routes.DASHBOARD) {
                            DashboardScreen(
                                uiState = uiState,
                                isDark = isDark,
                                isExpandedWidth = isExpandedWidth,
                                onCreateNew = {
                                    viewModel.startNewInvoice()
                                    navController.navigate(Routes.EDITOR)
                                },
                                onEditInvoice = { inv ->
                                    viewModel.startEditInvoice(inv)
                                    navController.navigate(Routes.EDITOR)
                                },
                                onOpenPdfPreview = { inv ->
                                    viewModel.openPdfPreview(inv)
                                    navController.navigate(Routes.PDF_PREVIEW)
                                },
                                onDuplicateInvoice = viewModel::duplicateInvoice,
                                onDeleteInvoice = viewModel::deleteInvoice,
                                onStatusChange = viewModel::changeInvoiceStatus,
                                onUpdateFilters = viewModel::updateFilters,
                                onOpenBackupSettings = {
                                    settingsTab = 1
                                    navigateTopLevel(Routes.SETTINGS)
                                }
                            )
                        }

                        composable(Routes.CATALOG) {
                            CatalogScreen(
                                catalog = uiState.catalog,
                                onAddCatalogItem = viewModel::addCatalogItem,
                                onDeleteCatalogItem = viewModel::deleteCatalogItem
                            )
                        }

                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                defaults = uiState.defaults,
                                invoiceCount = uiState.invoices.size,
                                catalogCount = uiState.catalog.size,
                                selectedTab = settingsTab,
                                onSelectTab = { settingsTab = it },
                                onSaveDefaults = viewModel::saveBusinessDefaults,
                                onBuildBackupJson = viewModel::buildBackupJson,
                                onImportBackupJson = viewModel::importBackupJson,
                                onShowMessage = viewModel::emitSnackbar
                            )
                        }

                        composable(Routes.EDITOR) {
                            val draft = editingInvoice
                            if (draft != null) {
                                InvoiceEditorScreen(
                                    invoice = draft,
                                    isCreatingNew = isCreatingNew,
                                    hasUnsavedChanges = hasUnsavedChanges,
                                    catalog = uiState.catalog,
                                    isDark = isDark,
                                    isExpandedWidth = isExpandedWidth,
                                    onBack = { navController.popBackStack() },
                                    onDiscardAndBack = {
                                        viewModel.discardEditingInvoice()
                                        navController.popBackStack()
                                    },
                                    onUpdateInvoice = viewModel::updateEditingInvoice,
                                    onStatusChange = viewModel::changeEditingStatus,
                                    onTaxModeChange = viewModel::changeEditingTaxMode,
                                    onAddEmptyItem = viewModel::addEmptyLineItem,
                                    onAddCatalogItem = viewModel::addLineItemFromCatalog,
                                    onRemoveItem = viewModel::removeLineItem,
                                    onUpdateLineItem = viewModel::updateLineItem,
                                    onUpdateSellerLogo = { logo ->
                                        viewModel.updateSellerLogoEverywhere(draft.id, logo)
                                    },
                                    onSave = { viewModel.saveEditingInvoice() },
                                    onSaveAndExit = {
                                        viewModel.saveEditingInvoice {
                                            navController.popBackStack()
                                        }
                                    },
                                    onSaveAndPreviewPdf = {
                                        viewModel.saveEditingInvoice { saved ->
                                            viewModel.openPdfPreview(saved)
                                            navController.navigate(Routes.PDF_PREVIEW)
                                        }
                                    },
                                    onShowMessage = viewModel::emitSnackbar
                                )
                            } else {
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            }
                        }

                        composable(Routes.PDF_PREVIEW) {
                            val previewInv = remember(uiState.invoices, editingInvoice, pdfPreviewInvoiceId) {
                                viewModel.getInvoiceForPreview()
                            }
                            if (previewInv != null) {
                                PdfPreviewScreen(
                                    invoice = previewInv,
                                    onBack = { navController.popBackStack() },
                                    onUpdatePdfOptions = { transform ->
                                        viewModel.updateInvoicePdfOptions(previewInv.id, transform)
                                    },
                                    onUpdateSellerLogo = { logo ->
                                        viewModel.updateSellerLogoEverywhere(previewInv.id, logo)
                                    },
                                    onShowMessage = viewModel::emitSnackbar
                                )
                            } else {
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            }
                        }
                    }
                }
            }

            if (uiState.isLoaded && !uiState.preferences.welcomeSeen) {
                WelcomeDialog(
                    onDismiss = viewModel::dismissWelcome,
                    onSetupSellerProfile = {
                        viewModel.dismissWelcome()
                        settingsTab = 0
                        navigateTopLevel(Routes.SETTINGS)
                    }
                )
            }
        }
    }
}
