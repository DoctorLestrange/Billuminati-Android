# Contributing to Billuminati for Android

Thank you for your interest in contributing to **Billuminati**! Contributions that improve Indian GST accuracy, PDF typography/layout fidelity, offline reliability, accessibility, and test coverage are especially welcome.

## Getting Started

1. **Fork** the repository and create a feature branch from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. Open the project in **Android Studio** (SDK 36, JDK 17/21).
3. Verify the baseline build and tests pass before making changes:
   ```bash
   gradle :app:testDebugUnitTest :app:lintDebug
   ```

## Architecture & Coding Guidelines

- **100% Native Kotlin & Jetpack Compose**: Keep UI declarative with Material Design 3 and use `MaterialTheme.colorScheme` (avoid hardcoded hex colors in Composables).
- **Preserve Billuminati's Identity**: Maintain the pure neutral graphite Light/Dark palette (zero blue tint), `24dp` drafting-grid background, and bundled local typography (`Plus Jakarta Sans`, `Lora`, `JetBrains Mono`, and `IBM Plex Serif` for PDFs).
- **Keep Business Logic Pure**: All GST calculations, rounding, currency formatting, and filtering belong in `BillingEngine.kt` with deterministic unit tests in `BillingEngineTest.kt`.
- **Zero Runtime Permissions**: Prefer Android Photo Picker (`PickVisualMedia`), SAF (`CreateDocument` / `OpenDocument`), `FileProvider`, and `PrintManager` over storage permissions.
- **Accessibility & Testability**:
  - Provide `contentDescription` for meaningful icons and images.
  - Ensure interactive targets meet the `48dp` minimum touch target size.
  - Add `Modifier.testTag("snake_case_tag")` to new primary controls.
- **Modular Files**: Keep Compose screens focused and modular (ideally under ~500 lines).

## Pull Request Checklist

Before opening a Pull Request:
- [ ] `gradle :app:assembleDebug` compiles cleanly with zero new warnings.
- [ ] `gradle :app:testDebugUnitTest` passes 100%, including new tests for your changes.
- [ ] `gradle :app:lintDebug` passes without new errors.
- [ ] No keystores, `.env` secrets, or `local.properties` files are included in the commit.
- [ ] Update `CHANGELOG.md` and `README.md` if user-facing behavior changes.

## Commit Style

We recommend [Conventional Commits](https://www.conventionalcommits.org/):
- `feat: add e-way bill reference field to invoice PDF`
- `fix: preserve fractional quantities during inclusive price entry`
- `test: add Robolectric test for IGST slab rounding`
- `docs: update release signing instructions`
