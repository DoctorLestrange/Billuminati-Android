<div align="center">

<img src="app/src/main/res/drawable/ic_billuminati_icon.png" alt="Billuminati Logo" width="96" height="96" />

# Billuminati for Android

**Offline-First Indian GST Tax Invoice & Billing Studio — Built 100% Native with Kotlin & Jetpack Compose**

[![Release](https://img.shields.io/badge/Release-v0.2.1-18181B?style=flat-square&logo=github)](../../releases/tag/v0.2.1)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024–36)-18181B?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-18181B?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-18181B?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Storage](https://img.shields.io/badge/Storage-Room%20SQLite%20%2B%20DataStore-18181B?style=flat-square)](https://developer.android.com/training/data-storage/room)
[![Privacy](https://img.shields.io/badge/Permissions-Zero%20Runtime%20Permissions-065F46?style=flat-square)](#privacy--permissions)
[![License](https://img.shields.io/badge/License-MIT-18181B?style=flat-square)](LICENSE)

</div>

---

## Download APK

Ready-to-install Android APKs (plus `SHA256SUMS.txt` verification checksums) are built and verified automatically by GitHub Actions on every push to `main` and every version tag (`v*`):

1. Open [**Releases -> v0.2.1 (Latest)**](../../releases/latest).
2. Download **`Billuminati-v0.2.1.apk`** onto your Android 7.0+ (API 24+) device.
3. Tap the downloaded APK to install (enable *Install unknown apps* if prompted).

---

## Overview

**Billuminati** is a fast, private, completely offline Indian GST invoicing studio for native Android. Engineered with calculation parity to the [Billuminati web app](https://github.com/DoctorLestrange/Billuminati), this Android implementation is built natively from the ground up with **Kotlin**, **Jetpack Compose**, **Material Design 3**, **Room SQLite**, and the native Android **PdfDocument / PdfRenderer / PrintManager** pipeline — with no WebView or cloud lock-in.

It preserves Billuminati's signature **neutral graphite drafting-paper aesthetic** (zero blue tint in Light and Dark themes, subtle `24dp` square drafting grid, and a curated four-font editorial typography system) inside a **mobile-first Android UX** designed cleanly for narrow vertical phone screens as well as foldables and tablets.

---

## Key Features

### Accurate Indian GST & Bidirectional Billing Engine
- **Tax Regimes**: Full support for **CGST + SGST** (intra-state), **IGST** (inter-state), and **GST Exempt** invoicing, with automatic per-slab GST breakdown summaries.
- **Bidirectional Reverse Pricing**: Enter any one of **Tax-Exclusive Rate**, **Tax-Inclusive Rate**, **Line Taxable Value**, or **Final Line Total** — every dependent field recalculates with penny-accurate precision, including fractional quantities (e.g., `0.5 kg`) and 25 Indian packaging units.
- **Pre-Tax Discount Calibration (Billuminati Web Parity)**: Per-item and invoice-level discounts are deducted from taxable value **before GST** using tax-factor calibration so that entering a ₹ discount reduces the final post-tax total by the exact entered rupee amount while keeping CGST, SGST, and IGST splits tax-compliant.
- **Round-Off & Indian Numbering**: Optional automatic ₹ round-off and Indian numbering-system **Amount in Words** (Lakhs / Crores / Paise).
- **Walk-In & B2B Buyers**: Instant toggle for Cash / Counter Sale invoices, 15-character GSTIN and 11-character IFSC validation hints, PO reference tracking, and payment status workflows (`Paid`, `Pending`, `Overdue`).

### Mobile-First Android UX & Adaptive Layouts
- **Compact Ledger Overview**: Clean hero revenue card with tappable `Paid`, `Pending`, and `Overdue` status counters, expandable search/filter/sort controls, date-range presets/picker, and quick actions (`Edit`, `PDF`, `Duplicate`, `Delete`).
- **3-Tab Phone Invoice Editor**: Organized into focused **`Details` · `Items` · `Totals`** tabs on compact vertical screens with a sticky bottom Save/Total bar and a dedicated slide-up **Line Item Editor Sheet** for distraction-free item pricing.
- **Tablet & Foldable Adaptive UI**: Automatically upgrades to a side `NavigationRail` and a two-pane Invoice Editor with a sticky live Financial Summary on expanded screens (`>= 840dp`).
- **Unsaved-Changes Guard**: Intercepts system Back presses and navigation exits when unsaved edits exist, offering Save, Discard, or Stay.

### Native `IBM Plex Serif` PDF Studio & Printing
- **Direct Vector PDF Generation**: Uses Android's native `PdfDocument`, `Canvas`, and `StaticLayout` — renders crisp, vector-sharp GST tax invoices strictly in **IBM Plex Serif**.
- **Collision-Free GST Tables**: Pre-measured `StaticLayout` pagination, wrapped seller/buyer headers, UPI-only or full bank account blocks, and dynamic font-fitting (`drawFittedText`) keep dense Portrait A4 GST tables legible without overlapping text or blank trailing pages.
- **Customizable Page Studio**: Live multi-page preview with pinch-to-zoom, pan, floating zoom controls, and a slide-up sheet for paper size (`A4`, `Letter`, `Legal`), orientation (`Portrait`, `Landscape`), margin presets (`Compact`, `Standard`, `Wide`), 5 executive accent colors, column visibility toggles, signatory label, and footer note.
- **Native Export & Printing**: Save directly via Android's Storage Access Framework (`CreateDocument`), share through sandboxed `FileProvider`, or print straight to wireless/PDF printers via `PrintManager`.

### Offline-First Storage, Catalog & Portable Backups
- **Local Room SQLite + DataStore**: All invoices, reusable HSN/SKU catalog presets, and default seller/bank profiles live locally in Room SQLite; theme and onboarding preferences live in Jetpack DataStore.
- **Cross-Platform JSON Backup & Restore**: Export and import portable JSON backups compatible with Billuminati Web (gracefully handling explicit `null` fields), supporting atomic transactional **Merge** or confirmation-guarded **Replace All** workflows via file picker or pasted JSON.
- **Seller Logo Studio**: Pick any business logo via the zero-permission Android Photo Picker; validates image signatures, handles EXIF orientation safely against OOM, and normalizes to a crisp `512×512` square PNG embedded in invoices and backups.

---

## Architecture

Billuminati follows **MVVM + Clean Layer Separation** with unidirectional state flow (`StateFlow` + `collectAsStateWithLifecycle`) and constructor injection without heavy DI frameworks.

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # Edge-to-edge host, adaptive nav (BottomBar / NavigationRail), SAF launchers
├── domain/
│   ├── model/
│   │   └── BillingModels.kt         # Moshi-annotated domain models, GST enums, PDF page options
│   └── engine/
│       └── BillingEngine.kt         # Pure Kotlin GST math, pre-tax discount calibration, INR words, filter & sort
├── data/
│   ├── local/
│   │   ├── BilluminatiDatabase.kt   # Room SQLite database & Moshi type converters
│   │   ├── BilluminatiDao.kt        # Reactive Flow queries, point reads & @Transaction backup import
│   │   ├── BilluminatiEntities.kt   # InvoiceEntity, CatalogItemEntity & BusinessDefaultsEntity schemas
│   │   └── PreferencesStore.kt      # Jetpack DataStore for Light/Dark theme & onboarding preferences
│   ├── backup/
│   │   └── BackupSerializer.kt      # Null-tolerant JSON serializer/deserializer (Billuminati Web compatible)
│   └── repository/
│       └── BilluminatiRepository.kt # Single source of truth coordinating Room & DataStore
├── service/
│   ├── InvoicePdfGenerator.kt       # Native PdfDocument + StaticLayout vector GST invoice renderer
│   ├── PdfExportHelper.kt           # PdfRenderer page rasterization, FileProvider sharing & PrintManager
│   └── LogoProcessor.kt             # Signature-checked, OOM-safe EXIF decode & 512px square PNG encoder
└── ui/
    ├── theme/                       # Neutral graphite ColorScheme & 4-font Typography (Plus Jakarta Sans, Lora, JetBrains Mono, IBM Plex Serif)
    ├── viewmodel/
    │   └── BilluminatiViewModel.kt  # Unidirectional UI state, mutex-guarded saves, monotonic invoice numbering
    ├── components/
    │   ├── CommonComponents.kt      # Section cards, focus-safe numeric inputs, date picker, status badges, Welcome dialog
    │   └── LineItemCard.kt          # Compact line-item summary card & bidirectional Line Item Editor bottom sheet
    └── screens/
        ├── DashboardScreen.kt       # Compact KPI ledger overview, collapsible filter bar, invoice cards
        ├── InvoiceEditorScreen.kt   # 3-tab mobile editor / 2-pane tablet editor with validation & Back guard
        ├── EditorSections.kt        # Seller, Buyer, Bank, Payment, Notes & Terms editor cards
        ├── EditorSummaryCard.kt     # Live GST slab breakdown, pre-tax discount & Grand Total card
        ├── PdfPreviewScreen.kt      # Pinch-zoom multi-page preview, retry handling & Save/Share/Print bar
        ├── PdfSettingsSheet.kt      # Atomic PDF paper, orientation, margin, accent & column controls
        ├── CatalogScreen.kt         # Create, edit & delete reusable HSN/SKU product presets
        └── SettingsScreen.kt        # Business Profile, Seller Logo, JSON Backup/Restore & About version card
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin `2.4.21` |
| **UI Framework** | Jetpack Compose (`BOM 2024.09.00`) + Material Design 3 |
| **Navigation** | Navigation Compose `2.8.9` |
| **Local Database** | Room `2.7.0` (SQLite + KSP `2.3.5`) |
| **Preferences** | Jetpack DataStore Preferences `1.1.7` |
| **Serialization** | Moshi `1.15.2` (KSP codegen) |
| **Concurrency** | Kotlin Coroutines & Flow `1.10.2` |
| **PDF & Printing** | Android `PdfDocument`, `PdfRenderer`, `PrintManager`, `FileProvider` |
| **Typography** | Local `.ttf` bundles: `Plus Jakarta Sans`, `Lora`, `JetBrains Mono`, `IBM Plex Serif` |
| **Testing** | JUnit 4, Robolectric `4.16.1`, Compose UI Test, Coroutines Test |
| **Build System** | Android Gradle Plugin `9.1.1` + Gradle `9.3.1` (Kotlin DSL + Version Catalog) |

---

## Getting Started

### Prerequisites
- **Android Studio** Ladybug / Meerkat or newer
- **JDK 21** (Java 11 bytecode target)
- **Android SDK 36.1** (`compileSdk = 36` minor 1, `targetSdk = 36`, `minSdk = 24`)

### Clone & Build

```bash
git clone https://github.com/DoctorLestrange/Billuminati.git
cd Billuminati
```

If `debug.keystore` is not present locally (it is gitignored), decode the bundled debug keystore:

```bash
base64 -d debug.keystore.base64 > debug.keystore
```

Build a debug APK (Gradle `9.3.1`):

```bash
gradle :app:assembleDebug
```

*(If you generate a local Gradle wrapper with `gradle wrapper`, you can also use `./gradlew`.)*

### Run Tests & Lint

Billuminati includes 30+ fast JVM unit tests, Room persistence & backup tests, logo validation tests, native PDF pagination tests, and Compose UI tests via Robolectric — no emulator required:

```bash
# Run all unit & Robolectric integration/UI tests
gradle :app:testDebugUnitTest

# Run Android Lint verification
gradle :app:lintDebug
```

---

## Release Builds & Signing

`app/build.gradle.kts` reads release signing credentials exclusively from environment variables — never commit keystores or passwords to version control.

1. Generate an upload keystore (if you don't already have one):
   ```bash
   keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. Export signing environment variables and assemble the signed release bundle (`.aab`) or APK:
   ```bash
   export KEYSTORE_PATH="/absolute/path/to/my-upload-key.jks"
   export STORE_PASSWORD="your-store-password"
   export KEY_PASSWORD="your-key-password"

   # Google Play Android App Bundle
   gradle :app:bundleRelease

   # Direct distribution APK
   gradle :app:assembleRelease
   ```

> **Automated GitHub Releases**: Every push to `main` automatically tags and publishes a directly installable APK (`Billuminati-v<versionName>.apk`) and `SHA256SUMS.txt` under **GitHub Releases** (currently **`v0.2.1`**). Pushing a version tag (`v*`) or triggering the **Tagged Release APK & AAB** workflow manually also builds and publishes the release. To sign automated releases with your production upload key (and also publish an `.aab` bundle), add `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` under **Settings -> Secrets and variables -> Actions**.

> **Note on Play Store Publishing**: The project uses `applicationId = "com.aistudio.billuminati.gstapp"`. Before publishing to your own Google Play Console account, customize `applicationId` in `app/build.gradle.kts` to your reverse-domain package identifier (e.g., `com.yourorg.billuminati`) and optionally enable `isMinifyEnabled = true` (`app/proguard-rules.pro` already includes keep rules for Moshi adapters, domain models, and Room).

---

## Privacy & Permissions

Billuminati is designed for **zero-permission, offline-first privacy**:

| Capability | Android API Used | Runtime Permission Required |
|---|---|---|
| Logo Selection | `ActivityResultContracts.PickVisualMedia` (Android Photo Picker) | **None** |
| Save PDF / Export JSON | `ActivityResultContracts.CreateDocument` (SAF) | **None** |
| Import JSON Backup | `ActivityResultContracts.OpenDocument` (SAF) | **None** |
| Share PDF / JSON | `androidx.core.content.FileProvider` (cache sandbox) | **None** |
| Print Invoice | `android.print.PrintManager` | **None** |
| Network / Analytics | None (100% offline) | **None** |

---

## Contributing & Community

Contributions, bug reports, and feature requests are welcome!

- Read [CONTRIBUTING.md](CONTRIBUTING.md) for branch, testing, and code style guidelines.
- Review our [Code of Conduct](CODE_OF_CONDUCT.md).
- Report security issues privately as described in [SECURITY.md](SECURITY.md).
- See [CHANGELOG.md](CHANGELOG.md) for release history.

---

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
