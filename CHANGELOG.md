# Changelog

All notable changes to **Billuminati for Android** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-11-21

### Added
- **Native Android App Architecture**: Built natively in Kotlin, Jetpack Compose, Material Design 3, Room SQLite, and Jetpack DataStore with zero WebView wrappers.
- **Indian GST Billing Engine**:
  - Support for `CGST + SGST`, `IGST`, and `GST Exempt` regimes with per-slab tax breakdowns.
  - Bidirectional reverse pricing across exclusive rate, inclusive rate, line taxable value, and final line total (including fractional quantities).
  - Item-level discounts, invoice-level additional discounts, optional automatic round-off, and Indian numbering-system Amount in Words (Lakhs / Crores / Paise).
- **Native `IBM Plex Serif` PDF Studio**:
  - Direct vector PDF generation with `PdfDocument`, `Canvas`, and pre-measured `StaticLayout` pagination.
  - Auto-fitting 11-column GST tables, wrapped headers, page footers, and empty-item safety.
  - Interactive multi-page preview with pinch-to-zoom, scroll-safe gestures, floating zoom bar, and debounced PDF page settings (paper size, orientation, margins, 5 accent colors, column/section toggles).
  - Native Save (SAF), Share (`FileProvider`), and wireless Print (`PrintManager`) with zero runtime permissions.
- **Dashboard, Editor, Catalog & Settings**:
  - Tappable KPI cards with auto-scaled Crore formatting, live search, status chips, date range presets/picker, and sorting.
  - Adaptive phone & tablet (`NavigationRail` + 2-pane sticky Financial Summary) layouts.
  - Unsaved-changes Back-press confirmation guard, blank invoice number validation, full seller/buyer/bank overrides, and GSTIN/IFSC hints.
  - Reusable HSN/SKU catalog presets and OOM-safe EXIF-normalized `512×512` seller logo processing.
  - Cross-platform JSON Backup & Restore (compatible with Billuminati Web) with Merge and confirmation-guarded Replace All modes.
- **Quality & CI**:
  - Comprehensive unit, Room persistence, logo processing, PDF layout/pagination, and Compose UI test suite powered by Robolectric.
  - Production R8/ProGuard keep rules, Android Auto Backup rules, and GitHub Actions CI workflow.
