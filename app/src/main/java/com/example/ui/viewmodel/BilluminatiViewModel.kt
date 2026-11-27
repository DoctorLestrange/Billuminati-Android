package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupParseResult
import com.example.data.local.AppPreferences
import com.example.data.local.BilluminatiDatabase
import com.example.data.local.PreferencesStore
import com.example.data.repository.BilluminatiRepository
import com.example.domain.engine.BillingEngine
import com.example.domain.model.BankDetails
import com.example.domain.model.BusinessDefaults
import com.example.domain.model.CatalogItem
import com.example.domain.model.DashboardFilters
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceLineItem
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.PackagingType
import com.example.domain.model.PartyDetails
import com.example.domain.model.PdfPageOptions
import com.example.domain.model.PriceEntryMode
import com.example.domain.model.TaxMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class DashboardMetrics(
    val totalVolume: Double = 0.0,
    val totalCount: Int = 0,
    val paidTotal: Double = 0.0,
    val paidCount: Int = 0,
    val pendingTotal: Double = 0.0,
    val pendingCount: Int = 0,
    val overdueTotal: Double = 0.0,
    val overdueCount: Int = 0
)

data class BilluminatiUiState(
    val invoices: List<Invoice> = emptyList(),
    val filteredInvoices: List<Invoice> = emptyList(),
    val metrics: DashboardMetrics = DashboardMetrics(),
    val catalog: List<CatalogItem> = emptyList(),
    val defaults: BusinessDefaults = BusinessDefaults(),
    val preferences: AppPreferences = AppPreferences(),
    val filters: DashboardFilters = DashboardFilters(),
    val isLoaded: Boolean = false
)

class BilluminatiViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BilluminatiDatabase.getInstance(application)
    private val preferencesStore = PreferencesStore(application)
    private val repository = BilluminatiRepository(database.dao(), preferencesStore)
    private val idCounter = AtomicLong(1L)
    private val isSaving = AtomicBoolean(false)
    private val pdfOptionsMutex = Mutex()
    private val duplicateMutex = Mutex()

    private val _filters = MutableStateFlow(DashboardFilters())
    private val _editingInvoice = MutableStateFlow<Invoice?>(null)
    val editingInvoice: StateFlow<Invoice?> = _editingInvoice.asStateFlow()

    private val _originalEditingInvoice = MutableStateFlow<Invoice?>(null)

    private val _isCreatingNew = MutableStateFlow(false)
    val isCreatingNew: StateFlow<Boolean> = _isCreatingNew.asStateFlow()

    private val _pdfPreviewInvoiceId = MutableStateFlow<String?>(null)
    val pdfPreviewInvoiceId: StateFlow<String?> = _pdfPreviewInvoiceId.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val hasUnsavedChanges: StateFlow<Boolean> = combine(
        _editingInvoice,
        _originalEditingInvoice,
        _isCreatingNew
    ) { current, original, creating ->
        if (current == null || original == null) {
            false
        } else {
            val currentComparable = current.copy(updatedAt = "")
            val originalComparable = original.copy(updatedAt = "")
            currentComparable != originalComparable || (creating && (current.items.any { it.name.isNotBlank() || it.unitPrice > 0.0 } || current.buyer.companyName.isNotBlank()))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val uiState: StateFlow<BilluminatiUiState> = combine(
        repository.invoicesFlow,
        repository.catalogFlow,
        repository.businessDefaultsFlow,
        repository.preferencesFlow,
        _filters
    ) { invoices, catalog, defaults, prefs, filters ->
        val metrics = computeMetrics(invoices)
        val filtered = BillingEngine.filterAndSortInvoices(invoices, filters)
        BilluminatiUiState(
            invoices = invoices,
            filteredInvoices = filtered,
            metrics = metrics,
            catalog = catalog,
            defaults = defaults,
            preferences = prefs,
            filters = filters,
            isLoaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BilluminatiUiState()
    )

    private fun nextId(prefix: String): String =
        "$prefix-${System.currentTimeMillis()}-${idCounter.getAndIncrement()}"

    private fun computeMetrics(invoices: List<Invoice>): DashboardMetrics {
        var totalVol = 0.0
        var paidTotal = 0.0
        var paidCount = 0
        var pendingTotal = 0.0
        var pendingCount = 0
        var overdueTotal = 0.0
        var overdueCount = 0

        for (inv in invoices) {
            val total = BillingEngine.calculateInvoiceSummary(inv).grandTotal
            totalVol = BillingEngine.roundCurrency(totalVol + total)
            when (InvoiceStatus.fromLabel(inv.status)) {
                InvoiceStatus.PAID -> {
                    paidTotal = BillingEngine.roundCurrency(paidTotal + total)
                    paidCount++
                }
                InvoiceStatus.PENDING -> {
                    pendingTotal = BillingEngine.roundCurrency(pendingTotal + total)
                    pendingCount++
                }
                InvoiceStatus.OVERDUE -> {
                    overdueTotal = BillingEngine.roundCurrency(overdueTotal + total)
                    overdueCount++
                }
            }
        }
        return DashboardMetrics(
            totalVolume = totalVol,
            totalCount = invoices.size,
            paidTotal = paidTotal,
            paidCount = paidCount,
            pendingTotal = pendingTotal,
            pendingCount = pendingCount,
            overdueTotal = overdueTotal,
            overdueCount = overdueCount
        )
    }

    fun updateFilters(transform: (DashboardFilters) -> DashboardFilters) {
        _filters.update(transform)
    }

    fun toggleTheme() {
        viewModelScope.launch {
            repository.setDarkTheme(!uiState.value.preferences.isDarkTheme)
        }
    }

    fun dismissWelcome() {
        viewModelScope.launch {
            repository.markWelcomeSeen()
        }
    }

    fun startNewInvoice() {
        val state = uiState.value
        val now = System.currentTimeMillis()
        val blankId = nextId("inv")
        val blank = Invoice(
            id = blankId,
            invoiceNumber = BillingEngine.generateNextInvoiceNumber(state.invoices),
            issueDate = BillingEngine.todayIsoDate(now),
            dueDate = BillingEngine.addDaysIsoDate(15, now),
            status = InvoiceStatus.PENDING.label,
            currency = "INR",
            taxMode = TaxMode.CGST_SGST.code,
            seller = state.defaults.defaultSeller,
            buyer = PartyDetails(country = "India"),
            bankDetails = state.defaults.defaultBank,
            items = listOf(createEmptyLineItem(TaxMode.CGST_SGST)),
            additionalDiscountAmount = 0.0,
            roundOff = true,
            notes = "",
            termsAndConditions = state.defaults.defaultTermsAndConditions,
            pdfOptions = state.defaults.defaultPdfOptions,
            createdAt = BillingEngine.nowIsoTimestamp(now),
            updatedAt = BillingEngine.nowIsoTimestamp(now)
        )
        _editingInvoice.value = blank
        _originalEditingInvoice.value = blank
        _isCreatingNew.value = true

        // If tapped on cold start before Room emitted initial state, hydrate defaults & next sequence safely.
        if (!state.isLoaded) {
            viewModelScope.launch {
                val latestInvoices = repository.getAllInvoicesOnce()
                val latestDefaults = repository.getBusinessDefaultsOnce()
                val nextNum = BillingEngine.generateNextInvoiceNumber(latestInvoices)
                val current = _editingInvoice.value
                if (current != null && current.id == blankId && current == _originalEditingInvoice.value) {
                    val hydrated = current.copy(
                        invoiceNumber = nextNum,
                        seller = latestDefaults.defaultSeller,
                        bankDetails = latestDefaults.defaultBank,
                        termsAndConditions = latestDefaults.defaultTermsAndConditions,
                        pdfOptions = latestDefaults.defaultPdfOptions
                    )
                    _editingInvoice.value = hydrated
                    _originalEditingInvoice.value = hydrated
                }
            }
        }
    }

    fun startEditInvoice(invoice: Invoice) {
        val normalized = BillingEngine.normalizeInvoiceRecord(invoice)
        _editingInvoice.value = normalized
        _originalEditingInvoice.value = normalized
        _isCreatingNew.value = false
    }

    fun discardEditingInvoice() {
        _editingInvoice.value = null
        _originalEditingInvoice.value = null
        _isCreatingNew.value = false
    }

    fun updateEditingInvoice(transform: (Invoice) -> Invoice) {
        _editingInvoice.update { current ->
            current?.let { transform(it).copy(updatedAt = BillingEngine.nowIsoTimestamp()) }
        }
    }

    fun changeEditingStatus(newStatus: InvoiceStatus) {
        updateEditingInvoice { BillingEngine.applyStatusTransition(it, newStatus) }
    }

    fun changeEditingTaxMode(newMode: TaxMode) {
        updateEditingInvoice { inv ->
            val updatedItems = inv.items.map { item ->
                BillingEngine.updateItemTaxRates(
                    item = item,
                    cgst = item.cgstPercent,
                    sgst = item.sgstPercent,
                    igst = item.igstPercent,
                    taxMode = newMode
                )
            }
            inv.copy(taxMode = newMode.code, items = updatedItems)
        }
    }

    fun addEmptyLineItem(): String {
        val currentMode = _editingInvoice.value?.let { TaxMode.fromCode(it.taxMode) } ?: TaxMode.CGST_SGST
        val newItem = createEmptyLineItem(currentMode)
        updateEditingInvoice { inv ->
            inv.copy(items = inv.items + newItem)
        }
        return newItem.id
    }

    fun addLineItemFromCatalog(preset: CatalogItem) {
        updateEditingInvoice { inv ->
            val taxMode = TaxMode.fromCode(inv.taxMode)
            val baseGst = kotlin.math.max(0.0, preset.taxPercent)
            val halfGst = BillingEngine.roundCurrency(baseGst / 2.0)
            val effectiveRate = if (taxMode == TaxMode.EXEMPT) 0.0 else baseGst
            val inclPrice = BillingEngine.getCatalogItemInclusivePrice(preset)
            val exclPrice = if (taxMode == TaxMode.EXEMPT) {
                inclPrice
            } else {
                BillingEngine.roundRate(inclPrice / (1.0 + effectiveRate / 100.0))
            }
            val calibrated = InvoiceLineItem(
                id = nextId("item"),
                name = preset.name,
                description = "",
                skuOrHsn = preset.skuOrHsn,
                packagingType = preset.packagingType,
                unitsPerPackage = 1,
                packageCount = 1.0,
                quantity = 1.0,
                priceEntryMode = PriceEntryMode.INCLUSIVE.code,
                unitPrice = exclPrice,
                unitPriceInclusive = inclPrice,
                discountAmount = 0.0,
                taxPercent = effectiveRate,
                cgstPercent = halfGst,
                sgstPercent = BillingEngine.roundCurrency(baseGst - halfGst),
                igstPercent = baseGst
            )
            val cleaned = if (inv.items.size == 1 && inv.items.first().name.isBlank() && inv.items.first().unitPrice == 0.0 && inv.items.first().unitPriceInclusive == 0.0) {
                listOf(calibrated)
            } else {
                inv.items + calibrated
            }
            inv.copy(items = cleaned)
        }
    }

    fun removeLineItem(itemId: String) {
        updateEditingInvoice { inv ->
            val mode = TaxMode.fromCode(inv.taxMode)
            val remaining = inv.items.filterNot { it.id == itemId }
            inv.copy(items = remaining.ifEmpty { listOf(createEmptyLineItem(mode)) })
        }
    }

    fun updateLineItem(itemId: String, transform: (InvoiceLineItem, TaxMode) -> InvoiceLineItem) {
        updateEditingInvoice { inv ->
            val mode = TaxMode.fromCode(inv.taxMode)
            inv.copy(
                items = inv.items.map { item ->
                    if (item.id == itemId) transform(item, mode) else item
                }
            )
        }
    }

    fun saveEditingInvoice(onSaved: ((Invoice) -> Unit)? = null) {
        val draft = _editingInvoice.value ?: return
        if (draft.invoiceNumber.trim().isEmpty()) {
            _snackbarEvents.tryEmit("Please enter an invoice number before saving")
            return
        }
        if (!draft.isCashCustomer &&
            draft.buyer.companyName.trim().isEmpty() &&
            draft.buyer.name.trim().isEmpty()
        ) {
            _snackbarEvents.tryEmit("Please enter a buyer name or enable Cash Sale")
            return
        }
        if (draft.items.isEmpty() || draft.items.any { it.name.trim().isEmpty() }) {
            _snackbarEvents.tryEmit("Please name all line items before saving")
            return
        }
        if (!isSaving.compareAndSet(false, true)) return

        val normalized = BillingEngine.normalizeInvoiceRecord(
            draft.copy(updatedAt = BillingEngine.nowIsoTimestamp())
        )
        viewModelScope.launch {
            try {
                repository.saveInvoice(normalized)
                val latestDefaults = repository.getBusinessDefaultsOnce()
                if (normalized.seller.logoDataUrl.isNotBlank() &&
                    normalized.seller.logoDataUrl != latestDefaults.defaultSeller.logoDataUrl
                ) {
                    val updatedDefaults = latestDefaults.copy(
                        defaultSeller = latestDefaults.defaultSeller.copy(
                            logoDataUrl = normalized.seller.logoDataUrl
                        )
                    )
                    repository.saveBusinessDefaults(updatedDefaults)
                }
                _editingInvoice.value = normalized
                _originalEditingInvoice.value = normalized
                _isCreatingNew.value = false
                _snackbarEvents.tryEmit("Saved ${normalized.invoiceNumber}")
                onSaved?.invoke(normalized)
            } finally {
                isSaving.set(false)
            }
        }
    }

    fun openPdfPreview(invoice: Invoice) {
        _pdfPreviewInvoiceId.value = invoice.id
    }

    fun getInvoiceForPreview(): Invoice? {
        val targetId = _pdfPreviewInvoiceId.value ?: return null
        return uiState.value.invoices.firstOrNull { it.id == targetId }
            ?: _editingInvoice.value?.takeIf { it.id == targetId }
    }

    fun duplicateInvoice(source: Invoice) {
        viewModelScope.launch {
            duplicateMutex.withLock {
                val now = System.currentTimeMillis()
                val existingInvoices = repository.getAllInvoicesOnce()
                val cloned = BillingEngine.normalizeInvoiceRecord(
                    source.copy(
                        id = nextId("inv"),
                        invoiceNumber = BillingEngine.generateNextInvoiceNumber(existingInvoices),
                        issueDate = BillingEngine.todayIsoDate(now),
                        dueDate = BillingEngine.addDaysIsoDate(15, now),
                        status = InvoiceStatus.PENDING.label,
                        paymentMethod = "",
                        paymentMethodOther = "",
                        paymentDate = "",
                        paymentMethodDetails = "",
                        items = source.items.map { item ->
                            item.copy(id = nextId("item"))
                        },
                        createdAt = BillingEngine.nowIsoTimestamp(now),
                        updatedAt = BillingEngine.nowIsoTimestamp(now)
                    )
                )
                repository.saveInvoice(cloned)
                _snackbarEvents.tryEmit("Duplicated as ${cloned.invoiceNumber}")
            }
        }
    }

    fun deleteInvoice(invoiceId: String) {
        viewModelScope.launch {
            repository.deleteInvoice(invoiceId)
            if (_editingInvoice.value?.id == invoiceId) {
                _editingInvoice.value = null
                _originalEditingInvoice.value = null
            }
            if (_pdfPreviewInvoiceId.value == invoiceId) {
                _pdfPreviewInvoiceId.value = null
            }
            _snackbarEvents.tryEmit("Invoice deleted")
        }
    }

    fun changeInvoiceStatus(invoiceId: String, status: InvoiceStatus) {
        viewModelScope.launch {
            val existing = repository.getInvoiceById(invoiceId)
                ?: uiState.value.invoices.firstOrNull { it.id == invoiceId }
                ?: return@launch
            val updated = BillingEngine.applyStatusTransition(existing, status)
            repository.saveInvoice(updated)
            if (_editingInvoice.value?.id == invoiceId) {
                _editingInvoice.value = updated
                _originalEditingInvoice.value = updated
            }
            _snackbarEvents.tryEmit("${updated.invoiceNumber} marked ${status.label}")
        }
    }

    /**
     * Atomically applies a transform to an invoice's PDF options under a Mutex against the latest
     * persisted record so concurrent rapid toggles and debounced text edits never clobber each other.
     */
    fun updateInvoicePdfOptions(invoiceId: String, transform: (PdfPageOptions) -> PdfPageOptions) {
        viewModelScope.launch {
            pdfOptionsMutex.withLock {
                val persisted = repository.getInvoiceById(invoiceId)
                    ?: uiState.value.invoices.firstOrNull { it.id == invoiceId }
                val baseOptions = persisted?.pdfOptions
                    ?: _editingInvoice.value?.takeIf { it.id == invoiceId }?.pdfOptions
                    ?: return@withLock
                val nextOptions = transform(baseOptions)
                if (persisted != null) {
                    repository.saveInvoice(
                        persisted.copy(
                            pdfOptions = nextOptions,
                            updatedAt = BillingEngine.nowIsoTimestamp()
                        )
                    )
                }
                _editingInvoice.update { current ->
                    if (current?.id == invoiceId) current.copy(pdfOptions = nextOptions) else current
                }
                _originalEditingInvoice.update { orig ->
                    if (orig?.id == invoiceId) orig.copy(pdfOptions = nextOptions) else orig
                }
            }
        }
    }

    fun updateSellerLogoEverywhere(invoiceId: String?, logoDataUrl: String) {
        viewModelScope.launch {
            val currentDefaults = repository.getBusinessDefaultsOnce()
            val previousDefaultLogo = currentDefaults.defaultSeller.logoDataUrl
            repository.saveBusinessDefaults(
                currentDefaults.copy(
                    defaultSeller = currentDefaults.defaultSeller.copy(logoDataUrl = logoDataUrl)
                )
            )
            val allInvoices = repository.getAllInvoicesOnce()
            val changedInvoices = allInvoices.mapNotNull { inv ->
                val shouldUpdate = invoiceId == null ||
                    inv.id == invoiceId ||
                    inv.seller.logoDataUrl.isBlank() ||
                    inv.seller.logoDataUrl == previousDefaultLogo
                if (shouldUpdate && inv.seller.logoDataUrl != logoDataUrl) {
                    inv.copy(
                        seller = inv.seller.copy(logoDataUrl = logoDataUrl),
                        updatedAt = BillingEngine.nowIsoTimestamp()
                    )
                } else {
                    null
                }
            }
            if (changedInvoices.isNotEmpty()) {
                repository.saveInvoices(changedInvoices)
            }
            _editingInvoice.update { current ->
                current?.copy(seller = current.seller.copy(logoDataUrl = logoDataUrl))
            }
            _originalEditingInvoice.update { orig ->
                orig?.copy(seller = orig.seller.copy(logoDataUrl = logoDataUrl))
            }
            _snackbarEvents.tryEmit(if (logoDataUrl.isBlank()) "Seller logo removed" else "Seller logo updated")
        }
    }

    fun saveBusinessDefaults(seller: PartyDetails, bank: BankDetails, defaultTerms: String) {
        viewModelScope.launch {
            val currentDefaults = repository.getBusinessDefaultsOnce()
            val previousDefaultLogo = currentDefaults.defaultSeller.logoDataUrl
            repository.saveBusinessDefaults(
                currentDefaults.copy(
                    defaultSeller = seller,
                    defaultBank = bank,
                    defaultTermsAndConditions = defaultTerms
                )
            )
            if (previousDefaultLogo != seller.logoDataUrl) {
                val allInvoices = repository.getAllInvoicesOnce()
                val logoChangedInvoices = allInvoices.mapNotNull { inv ->
                    val usesDefaultLogo = inv.seller.logoDataUrl.isBlank() ||
                        inv.seller.logoDataUrl == previousDefaultLogo
                    if (usesDefaultLogo && inv.seller.logoDataUrl != seller.logoDataUrl) {
                        inv.copy(seller = inv.seller.copy(logoDataUrl = seller.logoDataUrl))
                    } else {
                        null
                    }
                }
                if (logoChangedInvoices.isNotEmpty()) {
                    repository.saveInvoices(logoChangedInvoices)
                }
                _editingInvoice.update { current ->
                    current?.copy(seller = current.seller.copy(logoDataUrl = seller.logoDataUrl))
                }
                _originalEditingInvoice.update { orig ->
                    orig?.copy(seller = orig.seller.copy(logoDataUrl = seller.logoDataUrl))
                }
            }
            _snackbarEvents.tryEmit("Business defaults saved")
        }
    }

    fun addCatalogItem(
        name: String,
        skuOrHsn: String,
        packagingType: PackagingType,
        unitPriceInclusive: Double,
        taxPercent: Double
    ) {
        if (name.isBlank()) return
        val item = BillingEngine.createCatalogItemFromInclusive(
            id = nextId("cat"),
            name = name,
            skuOrHsn = skuOrHsn,
            packagingType = packagingType,
            unitPriceInclusive = unitPriceInclusive,
            taxPercent = taxPercent
        )
        viewModelScope.launch {
            repository.saveCatalogItem(item)
            _snackbarEvents.tryEmit("Added \"${item.name}\" to catalog")
        }
    }

    fun deleteCatalogItem(id: String) {
        viewModelScope.launch {
            repository.deleteCatalogItem(id)
            _snackbarEvents.tryEmit("Catalog preset removed")
        }
    }

    fun buildBackupJson(): String {
        val state = uiState.value
        return repository.serializer.exportBackupJson(
            invoices = state.invoices,
            defaults = state.defaults,
            catalog = state.catalog
        )
    }

    fun importBackupJson(rawJson: String, replaceExisting: Boolean, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                repository.serializer.validateAndParseBackupJson(rawJson)
            }
            when (result) {
                is BackupParseResult.Error -> {
                    onResult(false, result.message)
                }
                is BackupParseResult.Success -> {
                    val latestDefaults = repository.getBusinessDefaultsOnce()
                    repository.importBackup(
                        backup = result.backup,
                        replaceExisting = replaceExisting,
                        currentDefaults = latestDefaults
                    )
                    val modeLabel = if (replaceExisting) "Replaced workspace with" else "Merged"
                    val msg = "$modeLabel ${result.backup.invoices.size} invoice(s)"
                    _snackbarEvents.tryEmit(msg)
                    onResult(true, msg)
                }
            }
        }
    }

    fun emitSnackbar(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    private fun createEmptyLineItem(taxMode: TaxMode = TaxMode.CGST_SGST): InvoiceLineItem {
        val isExempt = taxMode == TaxMode.EXEMPT
        return InvoiceLineItem(
            id = nextId("item"),
            name = "",
            description = "",
            skuOrHsn = "",
            packagingType = PackagingType.BOX.label,
            unitsPerPackage = 1,
            packageCount = 1.0,
            quantity = 1.0,
            priceEntryMode = PriceEntryMode.EXCLUSIVE.code,
            unitPrice = 0.0,
            unitPriceInclusive = 0.0,
            discountAmount = 0.0,
            taxPercent = if (isExempt) 0.0 else 18.0,
            cgstPercent = 9.0,
            sgstPercent = 9.0,
            igstPercent = 18.0
        )
    }
}
