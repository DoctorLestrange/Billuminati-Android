package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.engine.BillingEngine
import com.example.domain.model.InvoiceStatus
import com.example.service.LogoProcessor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OverdueContainerDark
import com.example.ui.theme.OverdueContainerLight
import com.example.ui.theme.OverdueDark
import com.example.ui.theme.OverdueLight
import com.example.ui.theme.PaidContainerDark
import com.example.ui.theme.PaidContainerLight
import com.example.ui.theme.PaidDark
import com.example.ui.theme.PaidLight
import com.example.ui.theme.PendingContainerDark
import com.example.ui.theme.PendingContainerLight
import com.example.ui.theme.PendingDark
import com.example.ui.theme.PendingLight
import com.example.ui.theme.PlusJakartaSansFontFamily
import java.util.Locale
import kotlin.math.floor

@Composable
fun BilluminatiSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null || headerAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (subtitle != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (headerAction != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        headerAction()
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            content()
        }
    }
}

/**
 * Focus-safe numeric input mirroring Billuminati's NumericCellInput so coupled bidirectional
 * pricing fields never overwrite active user typing while focused.
 * Clears initial "0" automatically on focus so users can type a number directly without "0250".
 */
@Composable
fun NumericInputField(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prefix: String? = null,
    suffix: String? = null,
    decimals: Int = 2,
    testTagName: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val formattedExternal = remember(value, decimals) { formatNumberForInput(value, decimals) }
    var text by remember { mutableStateOf(formattedExternal) }

    LaunchedEffect(formattedExternal, isFocused) {
        if (!isFocused) {
            text = formattedExternal
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val sanitized = raw.filter { it.isDigit() || it == '.' }.take(14)
            if (sanitized.count { it == '.' } <= 1) {
                text = sanitized
                val parsed = sanitized.toDoubleOrNull()
                if (parsed != null) {
                    onValueChange(parsed)
                } else if (sanitized.isEmpty()) {
                    onValueChange(0.0)
                }
            }
        },
        modifier = modifier
            .then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier)
            .onFocusChanged { focusState ->
                if (!isFocused && focusState.isFocused) {
                    if (text == "0") {
                        text = ""
                    }
                } else if (isFocused && !focusState.isFocused) {
                    val parsed = text.toDoubleOrNull() ?: 0.0
                    onValueChange(parsed)
                    text = formatNumberForInput(parsed, decimals)
                }
                isFocused = focusState.isFocused
            },
        enabled = enabled,
        singleLine = true,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        placeholder = { Text("0", fontFamily = JetBrainsMonoFontFamily) },
        prefix = prefix?.let { { Text(it, fontFamily = JetBrainsMonoFontFamily) } },
        suffix = suffix?.let { { Text(it, fontFamily = JetBrainsMonoFontFamily) } },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(10.dp)
    )
}

private fun formatNumberForInput(value: Double, decimals: Int): String {
    if (!value.isFinite() || value == 0.0) return "0"
    return if (value == floor(value)) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.${decimals}f", value).trimEnd('0').trimEnd('.')
    }
}

/**
 * Reliable clickable read-only OutlinedTextField using PressInteraction.Release on its
 * MutableInteractionSource, working around M3 OutlinedTextField's internal pointer consumption.
 */
@Composable
fun ClickableReadOnlyField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    monospace: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    testTagName: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource, enabled) {
        if (enabled) {
            interactionSource.interactions.collect { interaction ->
                if (interaction is PressInteraction.Release) {
                    onClick()
                }
            }
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        singleLine = true,
        interactionSource = interactionSource,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        textStyle = if (monospace) {
            MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily)
        } else {
            MaterialTheme.typography.bodyMedium
        },
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsoDatePickerField(
    label: String,
    value: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowClear: Boolean = false,
    placeholder: String = "YYYY-MM-DD",
    testTagName: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    ClickableReadOnlyField(
        value = value,
        label = label,
        onClick = { if (enabled) showDialog = true },
        enabled = enabled,
        placeholder = placeholder,
        monospace = true,
        testTagName = testTagName,
        modifier = modifier,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (allowClear && value.isNotBlank() && enabled) {
                    IconButton(onClick = { onDateSelected("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear $label",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { if (enabled) showDialog = true },
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = "Pick $label",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    )

    if (showDialog) {
        val initialMillis = remember(value) {
            BillingEngine.isoDateToUtcMillis(value) ?: System.currentTimeMillis()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onDateSelected(BillingEngine.millisToIsoDate(millis))
                        }
                        showDialog = false
                    }
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun StatusBadgeSelector(
    status: InvoiceStatus,
    isDark: Boolean,
    onStatusSelected: ((InvoiceStatus) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val (fg, bg) = when (status) {
        InvoiceStatus.PAID -> if (isDark) PaidDark to PaidContainerDark else PaidLight to PaidContainerLight
        InvoiceStatus.PENDING -> if (isDark) PendingDark to PendingContainerDark else PendingLight to PendingContainerLight
        InvoiceStatus.OVERDUE -> if (isDark) OverdueDark to OverdueContainerDark else OverdueLight to OverdueContainerLight
    }

    Box(modifier = modifier) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, fg.copy(alpha = 0.25f)),
            modifier = Modifier
                .then(
                    if (onStatusSelected != null) {
                        Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expanded = true }
                    } else {
                        Modifier.clip(RoundedCornerShape(8.dp))
                    }
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = fg
                )
                if (onStatusSelected != null) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Change invoice status",
                        tint = fg,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        if (onStatusSelected != null) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                InvoiceStatus.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = if (option == status) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            expanded = false
                            if (option != status) onStatusSelected(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SellerLogoThumbnail(
    logoDataUrl: String,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(logoDataUrl) {
        LogoProcessor.decodeDataUrlToBitmap(logoDataUrl)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Seller business logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .padding(4.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Business,
                contentDescription = "No seller logo uploaded",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

@Composable
fun WelcomeDialog(
    onDismiss: () -> Unit,
    onSetupSellerProfile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Welcome to Billuminati",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Offline-first Indian GST Tax Invoice and billing studio with bidirectional tax-inclusive/exclusive pricing, 25 packaging units, and native IBM Plex Serif PDF export.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "All invoices, seller profiles, logos, and catalog presets are stored locally on your device in Room SQLite with cross-platform JSON backup and restore. Always verify GST rates and totals independently.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("welcome_get_started_button")
            ) {
                Text("Get Started")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onSetupSellerProfile,
                modifier = Modifier.testTag("welcome_setup_seller_button")
            ) {
                Text("Set Up Seller Profile")
            }
        }
    )
}
