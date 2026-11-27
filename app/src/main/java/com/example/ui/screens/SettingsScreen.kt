package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.domain.engine.BillingEngine
import com.example.domain.model.BankDetails
import com.example.domain.model.BusinessDefaults
import com.example.domain.model.PartyDetails
import com.example.service.LogoProcessor
import com.example.service.PdfExportHelper
import com.example.ui.components.BilluminatiSectionCard
import com.example.ui.components.SellerLogoThumbnail
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    defaults: BusinessDefaults,
    invoiceCount: Int,
    catalogCount: Int,
    selectedTab: Int, // 0 = Seller & Bank, 1 = JSON Backup
    onSelectTab: (Int) -> Unit,
    onSaveDefaults: (PartyDetails, BankDetails, String) -> Unit,
    onBuildBackupJson: () -> String,
    onImportBackupJson: (String, Boolean, (Boolean, String) -> Unit) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var seller by remember { mutableStateOf(defaults.defaultSeller) }
    var bank by remember { mutableStateOf(defaults.defaultBank) }
    var defaultTerms by remember { mutableStateOf(defaults.defaultTermsAndConditions) }

    LaunchedEffect(defaults) {
        seller = defaults.defaultSeller
        bank = defaults.defaultBank
        defaultTerms = defaults.defaultTermsAndConditions
    }

    val hasUnsavedDefaults = seller != defaults.defaultSeller ||
        bank != defaults.defaultBank ||
        defaultTerms != defaults.defaultTermsAndConditions

    val sellerGstinWarning = seller.taxId.isNotBlank() && seller.taxId.trim().length != 15
    val ifscWarning = bank.routingOrIfsc.isNotBlank() && bank.routingOrIfsc.trim().length != 11

    var rawJsonInput by remember { mutableStateOf("") }
    var showRawJsonPaste by remember { mutableStateOf(false) }
    var replaceMode by remember { mutableStateOf(false) }
    var importFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var pendingReplacePayload by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val executeImport: (String, Boolean, Boolean) -> Unit = { jsonContent, replace, clearPaste ->
        onImportBackupJson(jsonContent, replace) { ok, msg ->
            importFeedback = ok to msg
            if (ok && clearPaste) rawJsonInput = ""
        }
    }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                LogoProcessor.processLogoUri(context, uri)
                    .onSuccess { dataUrl ->
                        seller = seller.copy(logoDataUrl = dataUrl)
                        onSaveDefaults(seller, bank, defaultTerms)
                    }
                    .onFailure { onShowMessage(it.message ?: "Logo upload failed") }
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = onBuildBackupJson()
                PdfExportHelper.writeTextToUri(context, json, uri)
                    .onSuccess { onShowMessage("Exported Billuminati JSON backup") }
                    .onFailure { onShowMessage(it.message ?: "Export failed") }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                PdfExportHelper.readTextFromUri(context, uri)
                    .onSuccess { content ->
                        if (replaceMode && invoiceCount > 0) {
                            pendingReplacePayload = content to false
                        } else {
                            executeImport(content, replaceMode, false)
                        }
                    }
                    .onFailure { err ->
                        importFeedback = false to (err.message ?: "Could not read file")
                    }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "settings_segmented") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { onSelectTab(0) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("settings_tab_seller")
                    ) {
                        Text("Business Profile", maxLines = 1)
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { onSelectTab(1) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("settings_tab_backup")
                    ) {
                        Text("JSON Backup", maxLines = 1)
                    }
                }
            }

            if (selectedTab == 0) {
                item(key = "seller_logo_card") {
                    BilluminatiSectionCard(
                        title = "Business Logo",
                        subtitle = "Padded to 512×512 PNG for crisp PDF rendering."
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SellerLogoThumbnail(logoDataUrl = seller.logoDataUrl, size = 64.dp)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            logoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("settings_upload_logo_button")
                                    ) {
                                        Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pick Logo")
                                    }
                                    if (seller.logoDataUrl.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                seller = seller.copy(logoDataUrl = "")
                                                onSaveDefaults(seller, bank, defaultTerms)
                                            }
                                        ) {
                                            Text("Remove")
                                        }
                                    }
                                }
                                Text(
                                    text = "Private Android Photo Picker (no storage permission).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item(key = "seller_profile_card") {
                    BilluminatiSectionCard(title = "Default Seller Profile") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = seller.companyName,
                                onValueChange = { seller = seller.copy(companyName = it) },
                                label = { Text("Business / Legal Company Name", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_seller_company_input")
                            )
                            OutlinedTextField(
                                value = seller.name,
                                onValueChange = { seller = seller.copy(name = it) },
                                label = { Text("Owner / Contact Name", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = seller.taxId,
                                onValueChange = { seller = seller.copy(taxId = it.uppercase().take(15)) },
                                label = { Text("Seller GSTIN (15 chars)", style = MaterialTheme.typography.labelSmall) },
                                supportingText = if (sellerGstinWarning) {
                                    { Text("GSTIN should be 15 characters (${seller.taxId.trim().length}/15)") }
                                } else null,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = seller.addressLine1,
                                onValueChange = { seller = seller.copy(addressLine1 = it) },
                                label = { Text("Street Address", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = seller.cityStatePostal,
                                onValueChange = { seller = seller.copy(cityStatePostal = it) },
                                label = { Text("City, State - PIN Code", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = seller.phone,
                                onValueChange = { seller = seller.copy(phone = it) },
                                label = { Text("Phone", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = seller.email,
                                onValueChange = { seller = seller.copy(email = it) },
                                label = { Text("Email", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item(key = "bank_defaults_card") {
                    BilluminatiSectionCard(title = "Default Bank & UPI Details") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = bank.bankName,
                                onValueChange = { bank = bank.copy(bankName = it) },
                                label = { Text("Bank Name", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bank.accountName,
                                onValueChange = { bank = bank.copy(accountName = it) },
                                label = { Text("Account Holder Name", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bank.accountNumber,
                                onValueChange = { bank = bank.copy(accountNumber = it) },
                                label = { Text("Account Number", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bank.routingOrIfsc,
                                onValueChange = { bank = bank.copy(routingOrIfsc = it.uppercase().take(11)) },
                                label = { Text("IFSC Code (11 chars)", style = MaterialTheme.typography.labelSmall) },
                                supportingText = if (ifscWarning) {
                                    { Text("IFSC should be 11 characters (${bank.routingOrIfsc.trim().length}/11)") }
                                } else null,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bank.swiftOrIban,
                                onValueChange = { bank = bank.copy(swiftOrIban = it) },
                                label = { Text("UPI ID / Branch", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bank.paymentInstructions,
                                onValueChange = { bank = bank.copy(paymentInstructions = it) },
                                label = { Text("Payment Instructions (Optional)", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item(key = "default_terms_card") {
                    BilluminatiSectionCard(
                        title = "Default Terms & Conditions",
                        subtitle = "Pre-filled automatically on newly created invoices."
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = defaultTerms,
                                onValueChange = { defaultTerms = it },
                                placeholder = { Text("Leave empty for blank Terms, or enter default terms...") },
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { onSaveDefaults(seller, bank, defaultTerms) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_save_defaults_button")
                            ) {
                                Icon(
                                    imageVector = if (hasUnsavedDefaults) Icons.Outlined.Save else Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (hasUnsavedDefaults) "Save Business Defaults" else "Business Defaults Saved")
                            }
                        }
                    }
                }
            } else {
                item(key = "export_backup_card") {
                    BilluminatiSectionCard(
                        title = "Export JSON Backup",
                        subtitle = "100% compatible with Billuminati Web. Storing $invoiceCount invoice(s) and $catalogCount catalog preset(s) locally."
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val stamp = BillingEngine.todayIsoDate()
                                    exportJsonLauncher.launch("billuminati-backup-$stamp.json")
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("backup_export_file_button")
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save JSON", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val json = onBuildBackupJson()
                                        val file = withContext(Dispatchers.IO) {
                                            File(
                                                PdfExportHelper.getExportCacheDir(context),
                                                "billuminati-backup-${BillingEngine.todayIsoDate()}.json"
                                            ).apply { writeText(json) }
                                        }
                                        PdfExportHelper.shareFile(
                                            context = context,
                                            file = file,
                                            mimeType = "application/json",
                                            chooserTitle = "Share Billuminati Backup"
                                        ).onFailure { onShowMessage(it.message ?: "Unable to share backup") }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("backup_share_json_button")
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", maxLines = 1)
                            }
                        }
                    }
                }

                item(key = "import_backup_card") {
                    BilluminatiSectionCard(
                        title = "Import & Restore",
                        subtitle = "Supports full Billuminati backups, raw invoice arrays, or single invoices."
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !replaceMode,
                                    onClick = { replaceMode = false },
                                    label = { Text("Merge (Safe)") },
                                    modifier = Modifier.testTag("backup_mode_merge")
                                )
                                FilterChip(
                                    selected = replaceMode,
                                    onClick = { replaceMode = true },
                                    label = { Text("Replace All") },
                                    modifier = Modifier.testTag("backup_mode_replace")
                                )
                            }

                            Button(
                                onClick = {
                                    importJsonLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("backup_import_file_button")
                            ) {
                                Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose JSON File")
                            }

                            TextButton(
                                onClick = { showRawJsonPaste = !showRawJsonPaste },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Or Paste Raw JSON Backup",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (showRawJsonPaste) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = "Toggle raw JSON input"
                                )
                            }

                            AnimatedVisibility(visible = showRawJsonPaste) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = rawJsonInput,
                                        onValueChange = { rawJsonInput = it },
                                        placeholder = { Text("{\"app\": \"Billuminati\", \"invoices\": [...]}") },
                                        minLines = 4,
                                        maxLines = 8,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("backup_paste_json_input")
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            if (replaceMode && invoiceCount > 0) {
                                                pendingReplacePayload = rawJsonInput to true
                                            } else {
                                                executeImport(rawJsonInput, replaceMode, true)
                                            }
                                        },
                                        enabled = rawJsonInput.isNotBlank(),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("backup_import_paste_button")
                                    ) {
                                        Text(if (replaceMode) "Validate & Replace Workspace" else "Validate & Merge JSON")
                                    }
                                }
                            }

                            importFeedback?.let { (ok, message) ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (ok) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (ok) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        },
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "about_app_card") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Billuminati for Android",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Offline GST Billing Studio · Zero permissions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("settings_app_version")
                            )
                        }
                    }
                }
            }
        }

        // Floating sticky save prompt when Business defaults have unsaved changes
        if (selectedTab == 0 && hasUnsavedDefaults) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Unsaved profile changes",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onSaveDefaults(seller, bank, defaultTerms) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            }
        }
    }

    pendingReplacePayload?.let { (jsonPayload, clearPaste) ->
        AlertDialog(
            onDismissRequest = { pendingReplacePayload = null },
            title = { Text("Replace entire workspace?") },
            text = {
                Text(
                    "This will permanently replace all $invoiceCount existing invoice(s) in your local ledger with the imported backup. Consider exporting a JSON backup first."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingReplacePayload = null
                        executeImport(jsonPayload, true, clearPaste)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_replace_backup_button")
                ) {
                    Text("Replace Workspace")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplacePayload = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
