# Security Policy

## Security & Privacy Posture

Billuminati for Android is built to protect sensitive business and financial data by design:

- **100% Offline Local Storage**: Invoices, buyer/seller GSTINs, bank remittance details, and catalog presets are stored solely in the device's private Room SQLite database and Jetpack DataStore.
- **Zero Runtime Permissions**: No broad storage (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_*`), location, contacts, or camera permissions are declared or requested.
- **Sandboxed File Sharing**: Exported PDFs are shared exclusively via a non-exported `FileProvider` restricted to the app's `cache/exports/` directory, with stale files automatically purged.
- **No Telemetry or Remote Calls**: The app performs zero background network synchronization or analytics collection.

## Supported Versions

| Version | Supported |
|---|---|
| `1.0.x` | Yes |

## Reporting a Vulnerability

If you discover a security vulnerability (for example, in backup deserialization, file URI handling, or PDF export paths), please **do not** open a public GitHub issue.

Instead, report it privately via GitHub's **Security Advisories** (*Security -> Advisories -> Report a vulnerability*) with:
1. A clear description of the vulnerability and potential impact.
2. Steps to reproduce or a minimal proof of concept.
3. Affected Android API levels and app version.

We aim to acknowledge reports within **72 hours** and coordinate a fix before public disclosure.
