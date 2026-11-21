<div align="center">

<img src="app/src/main/res/drawable/ic_billuminati_icon.png" alt="Billuminati Logo" width="96" height="96" />

# Billuminati for Android

**Offline-First Indian GST Tax Invoice & Billing Studio — Built 100% Native with Kotlin & Jetpack Compose**

[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024–36)-18181B?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-18181B?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-18181B?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Storage](https://img.shields.io/badge/Storage-Room%20SQLite%20%2B%20DataStore-18181B?style=flat-square)](https://developer.android.com/training/data-storage/room)
[![Privacy](https://img.shields.io/badge/Permissions-Zero%20Runtime%20Permissions-065F46?style=flat-square)](#privacy--permissions)
[![License](https://img.shields.io/badge/License-MIT-18181B?style=flat-square)](LICENSE)

</div>

---

## Overview

**Billuminati** is a fast, private, completely offline Indian GST invoicing studio for native Android. Originally inspired by the [Billuminati web app](https://github.com/DoctorLestrange/Billuminati), this Android implementation is engineered from the ground up using **Kotlin**, **Jetpack Compose**, **Material Design 3**, **Room SQLite**, and the native Android **PdfDocument / PdfRenderer / PrintManager** pipeline — with no WebView or cloud lock-in.

It preserves Billuminati's signature **neutral graphite drafting-paper aesthetic** (zero blue tint in Light and Dark themes, subtle `24dp` square drafting grid, and a curated four-font editorial typography system) while adapting smoothly across phones, foldables, and tablets.

---

## Key Features

### Indian GST & Bidirectional Billing Engine
- **Tax Regimes**: Full support for **CGST + SGST** (intra-state), **IGST** (inter-state), and **GST Exempt** invoicing, with automatic per-slab GST breakdown summaries.
- **Bidirectional Reverse Pricing**: Enter either a **Tax-Exclusive rate**, **Tax-Inclusive rate**, **Line Taxable Value**, or **Final Line Total** — every dependent field recalculates with penny-accurate precision, including fractional quantities (e.g., `0.5 kg`).
- **Discounts & Round-Off**: Per-item discounts, invoice-level additional discounts (before or after tax), optional automatic ₹ round-off, and Indian numbering-system **Amount in Words** (Lakhs / Crores / Paise).
- **Walk-In & B2B Buyers**: Instant toggle for Cash / Counter Sale invoices, 15-character GSTIN and 11-character IFSC format helpers, PO reference tracking, and payment status workflows (`Paid`, `Pending`, `Overdue`).

### Native `IBM Plex Serif` PDF Studio & Printing
- **Direct Vector PDF Generation**: Uses Android's native `PdfDocument`, `Canvas`, and `StaticLayout` — renders crisp, vector-sharp GST tax invoices strictly in **IBM Plex Serif**.
- **Collision-Free 11-Column GST Tables**: Pre-measured `StaticLayout` pagination, wrapped seller/buyer headers, and dynamic font-fitting (`drawFittedText`) keep dense Portrait A4 GST tables legible without overlapping text.
- **Customizable Page Studio**: Live preview with pinch-to-zoom, pan, floating zoom controls, and a slide-up sheet for paper size (`A4`, `Letter`, `Legal`), orientation (`Portrait`, `Landscape`), margin presets (`Compact`, `Standard`, `Wide`), 5 executive accent colors, column visibility toggles, signatory label, and footer note.
- **Native Export & Printing**: Save directly via Android's Storage Access Framework (`CreateDocument`), share through sandboxed `FileProvider`, or print straight to wireless/PDF printers via `PrintManager`.

### Offline-First Ledger, Catalog & Portable Backups
- **Local Room SQLite + DataStore**: All invoices, reusable SKU/HSN catalog presets, seller profile defaults, and theme preferences live privately on device.
- **Tappable KPI Dashboard**: Instant revenue volume, paid, pending, and overdue counters (with auto-scaling for large Crore figures), search, status filter chips, date-range presets/picker, and multi-criteria sorting.
- **Cross-Platform JSON Backup & Restore**: Export and import portable JSON backups compatible with Billuminati web data, supporting safe **Merge** or confirmation-guarded **Replace All** workflows (via file picker or pasted JSON).
- **Adaptive Layouts**: Phone bottom navigation + tablet `NavigationRail` and two-pane Invoice Editor with a sticky Financial Summary on expanded screens (`>= 840dp`).
- **Seller Logo Studio**: Pick any business logo via the zero-permission Android Photo Picker; automatically handles EXIF rotation and normalizes to a crisp `512×512` square PNG embedded in invoices and backups.

---

## Architecture

Billuminati follows **MVVM + Clean Layer Separation** with unidirectional state flow (`StateFlow` + `collectAsStateWithLifecycle`) and zero third-party DI bloat.

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # Edge-to-edge host, adaptive nav (BottomBar / NavigationRail), SAF launchers
├── domain/
│   ├── model/
│   │   └── BillingModels.kt         # Moshi-annotated domain models, GST enums, PDF page options
│   └── engine/
│       └── BillingEngine.kt         # Pure Kotlin GST math, bidirectional pricing, INR words, filtering & sorting
├── data/
│   ├── local/
│   │   ├── BilluminatiDatabase.kt   # Room SQLite database & Moshi type converters
│   │   ├── BilluminatiDao.kt        # Reactive Flow queries & atomic transactional backup import
│   │   ├── BilluminatiEntities.kt   # InvoiceEntity & CatalogItemEntity schemas
│   │   └── PreferencesStore.kt      # Jetpack DataStore for business defaults & Light/Dark theme
│   ├── backup/
│   │   └── BackupSerializer.kt      # Portable JSON serializer/deserializer (Billuminati Web compatible)
│   └── repository/
│       └── BilluminatiRepository.kt # Single source of truth coordinating Room & DataStore
├── service/
│   ├── InvoicePdfGenerator.kt       # Native PdfDocument + StaticLayout vector GST invoice renderer
│   ├── PdfExportHelper.kt           # PdfRenderer page rasterization, FileProvider sharing & PrintManager
│   └── LogoProcessor.kt             # Streaming OOM-safe decode, EXIF normalization & 512px square PNG encoder
└── ui/
    ├── theme/                       # Neutral graphite ColorScheme, 4-font Typography (Plus Jakarta Sans, Lora, JetBrains Mono, IBM Plex Serif)
    ├── viewmodel/
    │   └── BilluminatiViewModel.kt  # Unidirectional UI state, unsaved-change tracking, monotonic IDs
    ├── components/
    │   ├── CommonComponents.kt      # 24dp drafting grid canvas, read-only picker fields, numeric inputs, status badges
    │   └── LineItemCard.kt          # Bidirectional line-item editor card with packaging & GST inputs
    └── screens/
        ├── DashboardScreen.kt       # KPI cards, search/filter/sort controls, invoice ledger
        ├── InvoiceEditorScreen.kt   # Adaptive 1-pane / 2-pane editor with unsaved-changes guard
        ├── EditorSections.kt        # Seller, Buyer, Bank, Payment, Notes & Terms sections
        ├── EditorSummaryCard.kt     # Live GST slab & Grand Total breakdown card
        ├── PdfPreviewScreen.kt      # Pinch-zoom / scroll-safe multi-page preview & Print/Save/Share bar
        ├── PdfSettingsSheet.kt      # Debounced PDF paper, margin, accent & visibility controls
        ├── CatalogScreen.kt         # Reusable HSN/SKU product & packaging presets manager
        └── SettingsScreen.kt        # Business defaults, seller logo & JSON Backup/Restore tabs
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
| **Build System** | Android Gradle Plugin `9.1.1` (Kotlin DSL + Version Catalog) |

---

## Getting Started

### Prerequisites
- **Android Studio** Ladybug / Meerkat or newer
- **JDK 17** or **JDK 21** (Java 11 bytecode target)
- **Android SDK 36** (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`)

### Clone & Build

```bash
git clone https://github.com/DoctorLestrange/Billuminati.git
cd Billuminati
```

If `debug.keystore` is not present locally (it is gitignored), decode the bundled debug keystore or generate a local one:

```bash
base64 -d debug.keystore.base64 > debug.keystore
```

Build a debug APK (Gradle `9.3.1`):

```bash
gradle :app:assembleDebug
```

*(If you generate a local Gradle wrapper with `gradle wrapper`, you can also use `./gradlew`.)*

### Run Tests & Lint

Billuminati includes fast JVM unit tests, Room persistence & backup tests, logo encoding tests, native PDF pagination tests, and Compose UI tests via Robolectric — no emulator required:

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

> **Note on Play Store Publishing**: Before publishing to Google Play, update `applicationId` in `app/build.gradle.kts` to your own reverse-domain package identifier (e.g., `com.yourorg.billuminati`) and optionally enable `isMinifyEnabled = true` (`app/proguard-rules.pro` already includes keep rules for Moshi adapters, domain models, and Room).

---

## Privacy & Permissions

Billuminati is designed for **zero-permission, offline-first privacy**:

| Capability | Android API Used | Runtime Permission Required |
|---|---|---|
| Logo Selection | `ActivityResultContracts.PickVisualMedia` (Android Photo Picker) | **None** |
| Save PDF / Export JSON | `ActivityResultContracts.CreateDocument` (SAF) | **None** |
| Import JSON Backup | `ActivityResultContracts.OpenDocument` (SAF) | **None** |
| Share PDF | `androidx.core.content.FileProvider` (cache sandbox) | **None** |
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
