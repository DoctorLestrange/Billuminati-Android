package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BilluminatiDatabase
import com.example.data.local.PreferencesStore
import com.example.data.repository.BilluminatiRepository
import com.example.domain.engine.BillingEngine
import com.example.domain.model.BankDetails
import com.example.domain.model.BilluminatiBackup
import com.example.domain.model.BusinessDefaults
import com.example.domain.model.CatalogItem
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceLineItem
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.PackagingType
import com.example.domain.model.PartyDetails
import com.example.domain.model.PdfOrientation
import com.example.domain.model.PdfPageOptions
import com.example.domain.model.PdfPaperSize
import com.example.domain.model.TaxMode
import com.example.service.InvoicePdfGenerator
import com.example.service.LogoProcessor
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InvoiceEditorScreen
import com.example.ui.theme.BilluminatiTheme
import com.example.ui.viewmodel.BilluminatiUiState
import com.example.ui.viewmodel.DashboardMetrics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp")
class BilluminatiIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: BilluminatiDatabase
    private lateinit var repository: BilluminatiRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BilluminatiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BilluminatiRepository(db.dao(), PreferencesStore(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun roomRepository_crudAndBackupMergeVsReplaceWorkReliably(): Unit = runBlocking {
        val inv1 = Invoice(
            id = "inv-1",
            invoiceNumber = "INV-2026-0101",
            issueDate = "2026-11-20",
            status = InvoiceStatus.PAID.label,
            buyer = PartyDetails(companyName = "Alpha Corp"),
            items = listOf(InvoiceLineItem(id = "item-1", name = "Cartons", quantity = 5.0, unitPrice = 200.0))
        )
        val inv2 = Invoice(
            id = "inv-2",
            invoiceNumber = "INV-2026-0102",
            issueDate = "2026-11-21",
            status = InvoiceStatus.PENDING.label,
            buyer = PartyDetails(companyName = "Beta Logistics"),
            items = listOf(InvoiceLineItem(id = "item-2", name = "Tape Rolls", quantity = 10.0, unitPrice = 50.0))
        )

        repository.saveInvoice(inv1)
        assertEquals(1, repository.invoicesFlow.first().size)

        // Merge backup containing inv2 keeps inv1 + adds inv2
        repository.importBackup(
            backup = BilluminatiBackup(invoices = listOf(inv2)),
            replaceExisting = false,
            currentDefaults = BusinessDefaults()
        )
        assertEquals(2, repository.invoicesFlow.first().size)

        // Replace All wipes inv1 and keeps only inv2
        repository.importBackup(
            backup = BilluminatiBackup(
                invoices = listOf(inv2),
                catalog = listOf(
                    CatalogItem(
                        id = "cat-1",
                        name = "Bubble Wrap",
                        skuOrHsn = "3920",
                        packagingType = PackagingType.ROLL.label,
                        unitPrice = 400.0,
                        unitPriceInclusive = 472.0,
                        taxPercent = 18.0
                    )
                )
            ),
            replaceExisting = true,
            currentDefaults = BusinessDefaults()
        )
        val afterReplace = repository.invoicesFlow.first()
        assertEquals(1, afterReplace.size)
        assertEquals("INV-2026-0102", afterReplace.first().invoiceNumber)
        assertEquals(1, repository.catalogFlow.first().size)

        repository.deleteInvoice("inv-2")
        assertTrue(repository.invoicesFlow.first().isEmpty())
    }

    @Test
    fun logoProcessor_squareNormalizationRoundTrips512Png() {
        val raw = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.DKGRAY)
        }
        val dataUrl = LogoProcessor.encodeSquareLogoBitmap(raw, targetSide = 512)
        raw.recycle()

        assertTrue(dataUrl.startsWith("data:image/png;base64,"))
        val decoded = LogoProcessor.decodeDataUrlToBitmap(dataUrl)
        assertNotNull(decoded)
        assertEquals(512, decoded!!.width)
        assertEquals(512, decoded.height)
        decoded.recycle()
    }

    @Test
    fun pdfGenerator_generatesValidMultiPageAndDense11ColumnPdfs(): Unit = runBlocking {
        val denseItems = (1..30).map { i ->
            InvoiceLineItem(
                id = "li-$i",
                name = "Industrial Corrugated Double-Wall Shipping Carton Batch #$i",
                description = "350 GSM Virgin Kraft Liner · Heavy Load Certified",
                skuOrHsn = "48191010",
                packagingType = PackagingType.BUNDLE.label,
                quantity = (i * 5).toDouble(),
                unitPrice = 1234.56,
                unitPriceInclusive = 1456.78,
                discountAmount = 250.0,
                cgstPercent = 9.0,
                sgstPercent = 9.0,
                igstPercent = 18.0,
                taxPercent = 18.0
            )
        }

        val invoice = Invoice(
            id = "inv-pdf-1",
            invoiceNumber = "INV-2026-0999",
            issueDate = "2026-11-21",
            dueDate = "2026-12-06",
            status = InvoiceStatus.PENDING.label,
            taxMode = TaxMode.CGST_SGST.code,
            purchaseOrderNumber = "PO-IND-88412",
            placeOfSupply = "Maharashtra (27)",
            seller = PartyDetails(
                companyName = "Billuminati Precision Packaging & Logistics Enterprises Pvt. Ltd.",
                name = "Arjun Mehta",
                addressLine1 = "Plot 42, MIDC Industrial Estate, Chakan Phase II",
                cityStatePostal = "Pune, Maharashtra - 410501",
                taxId = "27AABCU9603R1ZM",
                email = "billing@billuminati.in",
                phone = "+91 98200 12345"
            ),
            buyer = PartyDetails(
                companyName = "Vanguard Automotive Components India Ltd.",
                name = "Priya Deshmukh",
                addressLine1 = "18 Industrial Corridor, Oragadam",
                cityStatePostal = "Chennai, Tamil Nadu - 602105",
                taxId = "33AAACV4567D1Z5"
            ),
            bankDetails = BankDetails(
                bankName = "HDFC Bank Ltd.",
                accountName = "Billuminati Precision Packaging Pvt. Ltd.",
                accountNumber = "50200045678901",
                routingOrIfsc = "HDFC0001234",
                swiftOrIban = "billuminati@hdfcbank",
                paymentInstructions = "Quote invoice number in NEFT/RTGS remarks."
            ),
            items = denseItems,
            notes = "Dispatched via BlueDart Surface Freight · LR #991827",
            termsAndConditions = "Goods once sold will not be taken back. Subject to Pune jurisdiction.",
            pdfOptions = PdfPageOptions(
                paperSize = PdfPaperSize.A4.code,
                orientation = PdfOrientation.PORTRAIT.code,
                showSkuColumn = true,
                showPackagingColumn = true,
                showDiscountColumn = true,
                showTaxBreakdown = true,
                showAmountInWords = true,
                showBankDetails = true,
                showSignatureBlock = true,
                footerNote = "Computer-generated GST Tax Invoice"
            )
        )

        val renderedPages = mutableListOf<Bitmap>()
        val bitmapSink = object : InvoicePdfGenerator.PageSink {
            override fun startPage(width: Int, height: Int, pageNumber: Int): android.graphics.Canvas {
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                renderedPages.add(bmp)
                return android.graphics.Canvas(bmp)
            }

            override fun finishCurrentPage() = Unit
        }

        // Verify Portrait A4 with all 11 columns + 30 items paginates cleanly into 2+ pages
        val portraitPages = InvoicePdfGenerator.renderIntoSink(context, invoice, invoice.pdfOptions, bitmapSink)
        assertTrue("Expected multi-page pagination for 30 items, got $portraitPages", portraitPages >= 2)
        assertEquals(portraitPages, renderedPages.size)
        assertEquals(PdfPaperSize.A4.widthPt, renderedPages.first().width)
        assertEquals(PdfPaperSize.A4.heightPt, renderedPages.first().height)
        renderedPages.forEach { it.recycle() }
        renderedPages.clear()

        // Verify Landscape Letter with IGST & Cash Customer
        val cashIgst = invoice.copy(
            isCashCustomer = true,
            taxMode = TaxMode.IGST.code,
            items = denseItems.take(3),
            pdfOptions = invoice.pdfOptions.copy(
                paperSize = PdfPaperSize.LETTER.code,
                orientation = PdfOrientation.LANDSCAPE.code
            )
        )
        val landscapePages = InvoicePdfGenerator.renderIntoSink(context, cashIgst, cashIgst.pdfOptions, bitmapSink)
        assertTrue("Expected at least 1 landscape page, got $landscapePages", landscapePages >= 1)
        assertTrue("Expected 30-item portrait invoice ($portraitPages) to span more pages than 3-item landscape ($landscapePages)", portraitPages > landscapePages)
        assertEquals(landscapePages, renderedPages.size)
        assertEquals(PdfPaperSize.LETTER.heightPt, renderedPages.first().width)
        assertEquals(PdfPaperSize.LETTER.widthPt, renderedPages.first().height)
        renderedPages.forEach { it.recycle() }
        renderedPages.clear()

        // Verify empty line items guard renders cleanly without crashing
        val emptyItemsInv = invoice.copy(items = emptyList(), taxMode = TaxMode.EXEMPT.code)
        val emptyPages = InvoicePdfGenerator.renderIntoSink(context, emptyItemsInv, emptyItemsInv.pdfOptions, bitmapSink)
        assertTrue("Expected at least 1 page for empty-items invoice, got $emptyPages", emptyPages >= 1)
        assertEquals(emptyPages, renderedPages.size)
        renderedPages.forEach { it.recycle() }
        renderedPages.clear()
    }

    @Test
    fun dashboardUi_displaysInvoicesAndFiltersCorrectly() {
        val inv = Invoice(
            id = "ui-inv-1",
            invoiceNumber = "INV-2026-0201",
            issueDate = "2026-11-21",
            status = InvoiceStatus.PAID.label,
            buyer = PartyDetails(companyName = "Zenith Traders"),
            items = listOf(InvoiceLineItem(id = "i1", name = "Paper Reams", quantity = 2.0, unitPrice = 500.0))
        )
        var selectedFilterStatus: InvoiceStatus? = null

        composeTestRule.setContent {
            BilluminatiTheme(darkTheme = false) {
                DashboardScreen(
                    uiState = BilluminatiUiState(
                        invoices = listOf(inv),
                        filteredInvoices = listOf(inv),
                        metrics = DashboardMetrics(
                            totalVolume = 1180.0,
                            totalCount = 1,
                            paidTotal = 1180.0,
                            paidCount = 1
                        ),
                        isLoaded = true
                    ),
                    isDark = false,
                    isExpandedWidth = false,
                    onCreateNew = {},
                    onEditInvoice = {},
                    onOpenPdfPreview = {},
                    onDuplicateInvoice = {},
                    onDeleteInvoice = {},
                    onStatusChange = { _, _ -> },
                    onUpdateFilters = { transform ->
                        selectedFilterStatus = transform(com.example.domain.model.DashboardFilters()).statusFilter
                    },
                    onOpenBackupSettings = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Invoice Ledger").assertIsDisplayed()
        composeTestRule.onNodeWithTag("invoice_card_INV-2026-0201").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zenith Traders").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pdf_btn_INV-2026-0201").assertIsDisplayed()

        // Tap Paid filter chip and verify filter callback fires
        composeTestRule.onNodeWithTag("filter_status_paid").performClick()
        composeTestRule.waitForIdle()
        assertEquals(InvoiceStatus.PAID, selectedFilterStatus)
    }

    @Test
    fun catalogUi_addsNewPresetViaForm() {
        var addedName = ""
        var addedPrice = 0.0

        composeTestRule.setContent {
            BilluminatiTheme(darkTheme = false) {
                CatalogScreen(
                    catalog = emptyList(),
                    onAddCatalogItem = { name, _, _, price, _ ->
                        addedName = name
                        addedPrice = price
                    },
                    onDeleteCatalogItem = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("catalog_name_input").performTextInput("Thermal Labels")
        composeTestRule.onNodeWithTag("catalog_price_input").performTextInput("590")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("catalog_add_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("Thermal Labels", addedName)
        assertEquals(590.0, addedPrice, 0.01)
    }

    @Test
    fun editorUi_showsUnsavedChangesGuardOnBackPress() {
        var draft by mutableStateOf(
            Invoice(
                id = "ed-1",
                invoiceNumber = "INV-2026-0301",
                issueDate = "2026-11-21",
                items = listOf(InvoiceLineItem(id = "l1", name = "Widget", quantity = 1.0, unitPrice = 100.0))
            )
        )
        var discarded = false

        composeTestRule.setContent {
            BilluminatiTheme(darkTheme = false) {
                InvoiceEditorScreen(
                    invoice = draft,
                    isCreatingNew = false,
                    hasUnsavedChanges = true,
                    catalog = emptyList(),
                    isDark = false,
                    isExpandedWidth = false,
                    onBack = {},
                    onDiscardAndBack = { discarded = true },
                    onUpdateInvoice = { draft = it(draft) },
                    onStatusChange = {},
                    onTaxModeChange = {},
                    onAddEmptyItem = {},
                    onAddCatalogItem = {},
                    onRemoveItem = {},
                    onUpdateLineItem = { id, transform ->
                        draft = draft.copy(items = draft.items.map { if (it.id == id) transform(it, TaxMode.CGST_SGST) else it })
                    },
                    onUpdateSellerLogo = {},
                    onSave = {},
                    onSaveAndExit = {},
                    onSaveAndPreviewPdf = {},
                    onShowMessage = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("editor_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Unsaved changes").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unsaved_discard_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(discarded)
    }
}
