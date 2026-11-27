# Changelog

All notable changes to **Billuminati for Android** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.1] - 2026-11-27

### Added
- **Edit Catalog Presets**: Tap any saved catalog card or its Edit button to modify product name, HSN/SAC, packaging unit, inclusive rate, or GST slab in place.

### Changed
- **App-Wide Smoothness & Frame Performance**:
  - Scoped state collection per navigation destination in `MainActivity` so typing in the Invoice Editor or searching the ledger no longer recomposes the root host, top bar, or background grid.
  - Offloaded Room JSON mapping, metrics aggregation, and backup serialization onto `Dispatchers.Default`, and precomputed invoice ledger summaries off the main thread.
  - Cached the `24dp` square-ruled drafting grid into a single `Path` via `drawWithCache`, added async + LRU-cached seller logo decoding, reused `ThreadLocal` date formatters, added `LazyColumn` `contentType`, and tightened screen transitions to 140ms.

### Fixed
- **Removed Extra Top Space on Invoice Editor & PDF Preview**: Eliminated double `WindowInsets` (`statusBars` and `navigationBars`) between the host `Scaffold` and full-screen child `Scaffold`s, and tightened spacing above the Invoice Details / Invoice Number card.

## [0.2.0] - 2026-11-27

### Added
- **Versioned App Identity & About Card**: App version (`v0.2.0`, build `2`) now displays cleanly in Settings and the Welcome dialog via `BuildConfig`.
- **Mobile-First Invoice Editor & Line Item Sheet**: Redesigned phone layout into focused **`Details` · `Items` · `Totals`** tabs with a sticky Save/Total action bar and a dedicated slide-up **Line Item Editor** bottom sheet.
- **Compact Ledger Overview**: Streamlined Dashboard KPI summary card with tappable `Paid`, `Pending`, and `Overdue` status counters and collapsible filter controls for small vertical screens.
- **Automated Versioned GitHub Releases**: CI now automatically publishes installable `Billuminati-v0.2.0.apk` and `SHA256SUMS.txt` artifacts under the matching `v0.2.0` GitHub Release tag.

### Changed
- **Pre-Tax Discount Parity with Billuminati Web**: Per-item and invoice-level discounts are now calibrated before GST so that the final post-tax total reduces by the exact entered rupee discount while preserving accurate CGST/SGST/IGST slab breakdowns.
- **Unique Android Application ID**: Updated `applicationId` to `com.aistudio.billuminati.gstapp` for clean device installs and distribution.

### Fixed
- **Backup & Persistence Hardening**:
  - Atomic `@Transaction` backup imports with automatic line-item ID deduplication and 25 MB payload guards.
  - Null-tolerant JSON backup parsing so Billuminati Web backups containing explicit `null` fields import cleanly without errors.
  - DataStore `IOException` recovery and Room point-read queries (`getInvoiceById`, `getBusinessDefaultsOnce`).
- **PDF, Print & Logo Reliability**:
  - Prevented trailing blank pages on long terms/notes and enabled UPI-only Bank/Payment blocks in generated PDFs.
  - Matched `PrintManager` media size to the invoice's paper size and orientation, added SAF `"wt"` overwrite truncation, and attached `ClipData` URI grants for Share intents.
  - Added image magic-byte validation and `inSampleSize` bounds decoding to `LogoProcessor` to guard against corrupt inputs and OOM crashes.
- **Editor & Preview Stability**:
  - Added mutex guards against rapid double-saves, atomic PDF settings updates, zoom reset on paper/orientation changes, retry action on PDF render errors, and paste-safe numeric input sanitization.

## [0.1.0] - 2026-11-21

### Added
- **Initial Native Android Release**: Built natively in Kotlin, Jetpack Compose, Material Design 3, Room SQLite, and Jetpack DataStore with zero WebView wrappers.
- **Indian GST Billing Engine**: Support for `CGST + SGST`, `IGST`, and `GST Exempt` regimes, bidirectional tax-inclusive/exclusive pricing, fractional quantities, optional round-off, and Indian Amount in Words.
- **Native `IBM Plex Serif` PDF Studio**: Direct vector PDF rendering (`PdfDocument` / `StaticLayout`), interactive pinch-to-zoom preview, customizable paper/margin/accent settings, and zero-permission Save, Share, and Print.
- **Offline Ledger, Catalog & Portable Backups**: Reusable HSN/SKU catalog presets, EXIF-normalized `512×512` seller logo processing, and cross-platform JSON Backup & Restore.
