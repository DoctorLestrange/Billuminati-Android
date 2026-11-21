package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

    // Seller & Bank local form state synced with Room defaults
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

    // JSON Import state + explicit confirmation guard for Replace All
    var rawJsonInput by remember { mutableStateOf("") }
    var replaceMode by remember { mutableStateOf(false) }
    var importFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var pendingReplacePayload by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // json to clearPasteBox

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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "settings_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text(
                            text = "Workspace Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Configure default seller profile, bank remittance details & cross-platform JSON backups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedTab == 0,
                            onClick = { onSelectTab(0) },
                            label = { Text("Seller & Bank Defaults") },
                            modifier = Modifier.testTag("settings_tab_seller")
                        )
                        FilterChip(
                            selected = selectedTab == 1,
                            onClick = { onSelectTab(1) },
                            label = { Text("JSON Backup & Restore") },
                            modifier = Modifier.testTag("settings_tab_backup")
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                item(key = "seller_logo_card") {
                    BilluminatiSectionCard(
                        title = "Default Seller Logo",
                        subtitle = "Automatically padded to a 512×512 square PNG and embedded in generated PDFs."
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SellerLogoThumbnail(logoDataUrl = seller.logoDataUrl, size = 68.dp)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    text = "Uses zero-permission Android Photo Picker.",
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
                                label = { Text("Business / Legal Company Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_seller_company_input")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = seller.name,
                                    onValueChange = { seller = seller.copy(name = it) },
                                    label = { Text("Owner / Contact Name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = seller.taxId,
                                    onValueChange = { seller = seller.copy(taxId = it.uppercase().take(15)) },
                                    label = { Text("Seller GSTIN") },
                                    supportingText = if (sellerGstinWarning) {
                                        { Text("GSTIN is 15 chars (${seller.taxId.trim().length}/15)") }
                                    } else null,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = seller.addressLine1,
                                onValueChange = { seller = seller.copy(addressLine1 = it) },
                                label = { Text("Street Address") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = seller.cityStatePostal,
                                onValueChange = { seller = seller.copy(cityStatePostal = it) },
                                label = { Text("City, State - PIN Code") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = seller.email,
                                    onValueChange = { seller = seller.copy(email = it) },
                                    label = { Text("Email") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = seller.phone,
                                    onValueChange = { seller = seller.copy(phone = it) },
                                    label = { Text("Phone") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                item(key = "bank_defaults_card") {
                    BilluminatiSectionCard(title = "Default Bank & Remittance Details") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = bank.bankName,
                                    onValueChange = { bank = bank.copy(bankName = it) },
                                    label = { Text("Bank Name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = bank.accountName,
                                    onValueChange = { bank = bank.copy(accountName = it) },
                                    label = { Text("Account Holder Name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = bank.accountNumber,
                                    onValueChange = { bank = bank.copy(accountNumber = it) },
                                    label = { Text("Account Number") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = bank.routingOrIfsc,
                                    onValueChange = { bank = bank.copy(routingOrIfsc = it.uppercase().take(11)) },
                                    label = { Text("IFSC Code") },
                                    supportingText = if (ifscWarning) {
                                        { Text("IFSC is 11 chars (${bank.routingOrIfsc.trim().length}/11)") }
                                    } else null,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = bank.swiftOrIban,
                                onValueChange = { bank = bank.copy(swiftOrIban = it) },
                                label = { Text("UPI ID / Branch") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bank.paymentInstructions,
                                onValueChange = { bank = bank.copy(paymentInstructions = it) },
                                label = { Text("Payment Instructions (Optional)") },
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
                                placeholder = { Text("Leave empty for blank Terms & Conditions, or enter default terms...") },
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
                // JSON Backup & Restore Tab
                item(key = "export_backup_card") {
                    BilluminatiSectionCard(
                        title = "Export Portable JSON Backup",
                        subtitle = "100% compatible with Billuminati Web. Currently storing $invoiceCount invoice(s) and $catalogCount catalog preset(s) in local Room SQLite."
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
                                Text("Save JSON")
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
                                Text("Share JSON")
                            }
                        }
                    }
                }

                item(key = "import_backup_card") {
                    BilluminatiSectionCard(
                        title = "Import & Restore JSON Backup",
                        subtitle = "Supports full Billuminati backups, raw invoice arrays, or single invoice objects."
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !replaceMode,
                                    onClick = { replaceMode = false },
                                    label = { Text("Merge (Keep Existing)") },
                                    modifier = Modifier.testTag("backup_mode_merge")
                                )
                                FilterChip(
                                    selected = replaceMode,
                                    onClick = { replaceMode = true },
                                    label = { Text("Replace All") },
                                    modifier = Modifier.testTag("backup_mode_replace")
                                )
                            }

                            OutlinedButton(
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
                                Text("Choose JSON File to Import")
                            }

                            OutlinedTextField(
                                value = rawJsonInput,
                                onValueChange = { rawJsonInput = it },
                                label = { Text("Or Paste Raw JSON Backup") },
                                placeholder = { Text("{\"app\": \"Billuminati\", \"invoices\": [...]}") },
                                minLines = 4,
                                maxLines = 8,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("backup_paste_json_input")
                            )

                            Button(
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
