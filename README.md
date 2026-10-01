# IBU Certificate Verifier

A cloud-backed Android application that verifies the authenticity of academic certificates by scanning the QR code printed on them. It combines on-device AES-256-GCM encryption with a Supabase-backed certificate store, so certificate records are never stored in plaintext locally or on the server.

The app sends the scanned certificate ID to a server-side Supabase Edge Function, which computes a peppered SHA-256 lookup hash (the pepper never leaves the server) and retrieves the encrypted row. All field-level decryption happens client-side using a master secret held only on the device.

---

## Table of Contents

- [Purpose](#purpose)
- [Features](#features)
- [How Verification Works](#how-verification-works)
- [Gate Sequence Reference](#gate-sequence-reference)
- [Screens](#screens)
- [Architecture Overview](#architecture-overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [Security Model](#security-model)
- [Project Structure](#project-structure)
- [Testing](#testing)
- [Desktop Tooling](#desktop-tooling)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)
- [Known Limitations](#known-limitations)
- [Contributing](#contributing)
- [License](#license)

---

## Purpose

Printed certificates are easy to alter, and a photograph of a "valid" certificate proves nothing about the holder. This app gives a certificate holder a way to confirm, using nothing but the phone already in their pocket, that a printed QR code corresponds to a genuine record.

The design is deliberately conservative:

- **No manual ID entry.** The certificate ID is only ever read from the QR code. A text field would create a second path around the validation logic, which is the exact thing this app exists to prevent.
- **No partial trust.** A code either passes every check and shows the record, or it is refused with a specific reason. There is no degraded or "mostly valid" outcome.
- **Zero-knowledge fields.** Every field is encrypted client-side with AES-256-GCM before it leaves the device. Supabase stores only ciphertext; the decryption key (master secret) never leaves the device.

---

## Features

- **QR scanning** powered by ZXing with a custom viewfinder overlay, torch toggle, and a QR-only decoder so 1D barcodes cannot trigger verification.
- **A five-stage validation gate** that distinguishes between a damaged code, a foreign QR code, an unsupported secure code, a plain typed ID, and a genuine lookup miss.
- **Integrity checking** via a position-weighted checksum, so a single altered character is detected before any network access occurs.
- **Cloud-backed certificate store** on Supabase, accessed via a server-side Edge Function that applies the lookup-hash pepper.
- **AES-256-GCM field encryption** with per-record random salts and IVs, performed entirely client-side. The server never sees plaintext fields.
- **Indexed lookup** via a peppered SHA-256 hash computed server-side, so the certificate ID is never exposed to plaintext search on the server.
- **Four focused screens** with no navigation drawer, no settings, and no account.
- **Backups disabled**, so encrypted data and secrets are not exfiltrated through cloud backup.

---

## How Verification Works

A certificate ID has the form `CER<digit>IB<course-code>U<serial>`, for example `CER1IBAU0001`. It is validated against:

```
^CER[0-9]IB[A-Z0-9]+U[A-Z0-9]+$
```

The ID is then encoded into the QR payload by `CertificatePayloadCodec`:

1. Each byte is split into two 6-bit groups.
2. A two-group header carries the byte length.
3. The group list is padded to a multiple of five.
4. Groups are emitted in reversed blocks of five.
5. A position-weighted checksum modulo 64 is appended.
6. The result is written using a 64-character alphabet and prefixed with the marker `IBU1`.

For example:

```
CER1IBAU0001  ->  IBU1FQDMAAxQSQBQCQJAwQVQxAwAwAAAAA4
```

**The codec is obfuscation with integrity validation, not encryption.** It obscures the certificate ID from casual reading and reliably detects tampering. It is not designed to resist a determined analyst, and should not be treated as a cryptographic control. Confidentiality comes from the AES-256-GCM field encryption, not from the QR payload.

---

## Gate Sequence Reference

`GateSequence` evaluates gates in order and stops at the first failure, so the reported reason is always the most specific one.

| Gate | Condition | Outcome |
| --- | --- | --- |
| 0 | Payload is empty | *No QR code detected* |
| 0b | A result is already opening | Silently ignored |
| 1 | Payload contains the `SQR1` marker | *Secure certificate not supported* |
| 2 | No `IBU1` marker, but looks like a certificate ID | *Plain Certificate ID not accepted* |
| 2 | No `IBU1` marker, foreign text or URL | *Not a certificate QR code* |
| 3 | Length, padding, alphabet, or checksum fails | *The QR code is damaged or has been altered* |
| 4 | Network failure or store cannot be decrypted | *This device could not read its own certificate store* |
| 4 | No matching record | `NotFoundActivity` |
| 4 | Matching record | `VerificationActivity` |

Gate 2 exists to give a clear, deliberate message when someone photographs a plain-text certificate ID instead of a QR code, rather than treating it as a damaged scan.

---

## Screens

| Screen | Purpose |
| --- | --- |
| `HomeActivity` | Launcher. Offers a single "Scan QR Code" button. |
| `ScannerActivity` | Camera preview, torch toggle, refusal banner, and lookup progress indicator. |
| `VerificationActivity` | Displays the decrypted record for a certificate that passed every gate. |
| `NotFoundActivity` | Shows the scanned certificate ID when the code is genuine but the record is absent. |

All four are declared `android:exported="false"` except the launcher, and all are locked to portrait orientation.

---

## Architecture Overview

```
┌──────────────┐     ┌──────────────────────┐     ┌──────────────────┐     ┌──────────────────────┐
│  Scanner UI  │────▶│  GateSequence (0–4) │────▶│  CloudRepository │────▶│  Supabase Edge Fn    │
│  (ZXing)     │     │  decode + validate  │     │  (OkHttp)         │     │  lookup-cert         │
└──────────────┘     └──────────────────────┘     └──────────────────┘     └────────┬───────────────┘
                                                                                  │
                                                ┌──────────────────────┐          │
                                                │  Supabase Table      │◀─────────┘
                                                │  certificates        │
                                                │  (ciphertext only)   │
                                                └──────────────────────┘
```

**Data flow:**

1. The scanner decodes a QR code and extracts the certificate ID.
2. `GateSequence` validates the payload format, checksum, and integrity (Gates 0–3) before any network access.
3. `CloudCertificateRepository` calls `CertificateApi.findByCertificateId(certificateId)`.
4. `SupabaseCertificateApi` (OkHttp) sends the ID to the `lookup-cert` Edge Function.
5. The Edge Function computes `SHA-256(certificateId ‖ pepper)` server-side, looks up the matching encrypted row, and returns it.
6. `SupabaseCertificateApi` decrypts each field client-side using `EncryptionHelper` (AES-256-GCM with the device master secret).
7. The decrypted `Certificate` object is returned to `GateSequence`, which produces a `FOUND` result.

**Key boundaries:**

- The **lookup-hash pepper** lives only on the server (Edge Function). The client never holds it.
- The **master secret** lives only on the device (`encryption.properties` / `BuildConfig.CERT_MASTER_SECRET`). The server never holds it.
- The `cert.hash.pepper` on-device field is **retained for desktop tooling and testing** but is **not used by the app at runtime**; hash computation is delegated to the server.
- All database access goes through the `CertificateLookup` interface, which `CloudCertificateRepository` implements. The old SQLite-based `CertificateRepository`, `CertificateDbHelper`, and `CertificateSeeder` have been removed.

---

## Requirements

| Component | Version |
| --- | --- |
| Android Gradle Plugin | 9.3.3 |
| Compile / Target SDK | 36 |
| Minimum SDK | 24 (Android 7.0 Nougat) |
| Java | 11 |
| Gradle wrapper | included |

The project is a single `:app` module written in **Java** with **XML layouts** and `findViewById`. It deliberately uses no Kotlin, no Jetpack Compose, and no dependency injection framework.

### Runtime dependencies

| Library | Version | Purpose |
| --- | --- | --- |
| `androidx.appcompat` | 1.8.0 | AppCompat activity and theming |
| `com.google.android.material` | 1.14.0 | Toolbar, button, card components |
| `androidx.constraintlayout` | 2.2.1 | Screen layouts |
| `androidx.cardview` | 1.0.0 | Certificate detail card |
| `androidx.recyclerview` | 1.4.0 | Reserved for list surfaces |
| `com.google.zxing:core` | 3.5.3 | QR decoding |
| `com.journeyapps:zxing-android-embedded` | 4.3.0 | Camera view and `DecoratedBarcodeView` |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP client for Supabase API calls |

---

## Installation

### Prerequisites

- JDK 11 or newer
- Android SDK with Platform 36 and Build Tools installed
- An Android 7.0+ device or emulator with camera
- A Supabase project with the `lookup-cert` Edge Function deployed (see [Configuration](#configuration))

### Steps

1. **Clone the repository**

   ```bash
   git clone <your-repository-url>
   cd IBU_Verification
   ```

2. **Configure Supabase credentials**

   Create `supabase.properties` in the project root (already gitignored):

   ```properties
   supabase.url=https://<your-project-ref>.supabase.co
   supabase.anon.key=<your-anon-key>
   ```

   Values are loaded at build time and injected into `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_ANON_KEY`. If `supabase.properties` is absent, a placeholder file is auto-generated on the first Gradle build; replace the placeholders before running the app.

3. **Generate the encryption master secret**

   This happens automatically on the first Gradle build. If `encryption.properties` is absent, the build generates it with a single 32-byte random value:

   ```properties
   cert.master.secret=<64 hex characters>
   cert.hash.pepper=<64 hex characters>
   ```

   > **Keep `encryption.properties`.** It is gitignored and is never committed. It is required to decrypt fields returned from Supabase. If it is lost or regenerated, fields encrypted with the old secret will no longer decrypt, and the app will report *"This device could not read its own certificate store."* See [Troubleshooting](#troubleshooting).
   >
   > Note: `cert.hash.pepper` is retained for desktop tooling but is **not** used by the app at runtime — the server-side Edge Function applies its own pepper.

4. **Build the debug APK**

   ```bash
   # macOS / Linux
   ./gradlew assembleDebug
   ```

   ```powershell
   # Windows
   .\gradlew.bat assembleDebug
   ```

5. **Locate the APK**

   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

6. **Install on a connected device**

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

7. **Grant camera access**

   The app requests `CAMERA` at runtime the first time the scanner opens. Camera hardware is declared optional, so the app still installs on devices without a camera.

---

## Usage

1. Open **IBU Certificate Verifier**.
2. Tap **Scan QR Code** and allow camera access.
3. Point the camera at the QR code printed on the certificate.
4. Read the result:

   - **Green, "Certificate Verified"** — the code is intact and the record exists in Supabase. The student's name, roll number, enrollment number, course, university, year of passing, percentage, and certificate type are decrypted client-side and displayed.
   - **Red, "Certificate Not Found"** — the code itself is valid and undamaged, but no matching record exists in Supabase. The scanned ID is shown so it can be checked against the issuing record.
   - **Red banner, "The QR code is damaged or has been altered"** — a character was changed after the code was generated. Re-scan at a better angle, or treat the certificate as suspect.
   - **Red banner, "This device could not read its own certificate store."** — a network error occurred or decryption failed. Check connectivity and the encryption master secret.

5. Use the torch button in the lower-right corner in low light, and **Scan Another Certificate** to verify a second certificate without returning to the home screen.

### Testing with sample codes

The repository ships a generator that produces real, scannable QR images covering the valid and refusal paths.

```powershell
.\tools\run_tools.bat
```

Output is written to `sample_qr_codes/`, split into two folders:

| File | Gate exercised | Expected result |
| --- | --- | --- |
| `encoded/CER1IBAU0001.png` … `encoded/CER3IBAU0007.png` | Gates 0–3 + lookup miss | Certificate Not Found (no server seeding) |
| `plain/CER1IBAU0001.png` | Gate 2 | *Plain Certificate ID not accepted* |
| `plain/httpsexampleeduverifyid7.png` | Gate 2 | *Not a certificate QR code* |
| `plain/WIFISCampusNetTWPAPsecret.png` | Gate 2 | *Not a certificate QR code* |
| `plain/secure_SQR1AAAABBBBCCCCDDDDEEEE.png` | Gate 1 | *Secure certificate not supported* |

The `encoded/` fixtures contain a valid `IBU1` payload. The `plain/` fixtures contain the raw text that should be refused, and are the quickest way to confirm each refusal path renders correctly.

`sample_qr_codes/` and `tools/build/` are gitignored, so run the generator after every fresh clone.

> **Note:** The `encoded/` sample IDs (CER1–CER3) are no longer seeded on-device. They will now produce "Certificate Not Found" unless the corresponding records exist in Supabase. The refusal-path fixtures (`plain/`) still exercise the local gates without any network access and are unaffected.

---

## Security Model

### Data in transit

The app sends only the certificate ID to the `lookup-cert` Edge Function over HTTPS. The ID is the decoded value from the QR code — it is public by design (printed on the certificate). No plaintext field data, no master secret, and no pepper are sent from the client.

### Field encryption (client-side)

Every field in the response row is encrypted with AES-256-GCM at the time of storage (performed by whoever inserts records into Supabase). The app decrypts each field after retrieval using the device master secret. Encryption details:

| Property | Value |
| --- | --- |
| Cipher | `AES/GCM/NoPadding` |
| Key size | 256 bits |
| Key derivation | `PBKDF2WithHmacSHA256`, 10,000 iterations |
| Salt | 16 random bytes, unique per value |
| IV | 12 random bytes, unique per value |
| Auth tag | 128 bits |
| Stored format | `Base64( salt ‖ iv ‖ ciphertext ‖ tag )` |

The 128-bit GCM tag means any modification to a stored value is detected on decryption rather than silently returning corrupted data.

### Lookup hash (server-side)

The Edge Function computes `SHA-256(certificateId ‖ pepper)` using a secret pepper held only on the server. The `cert.hash.pepper` value on the device is retained for desktop tooling and testing compatibility but is **not** used by the app at runtime.

### Secrets

Two independent secrets are in scope, and they are **not** interchangeable:

- **`cert.master.secret`** — stored in `encryption.properties`. Derives the AES key for client-side field decryption. The server never sees this. Auto-generated if absent.
- **Supabase `supabase.anon.key`** — stored in `supabase.properties`. Used for authenticated API calls. This is a public anon key scoped to the `lookup-cert` function; it cannot read or write the `certificates` table directly (Row Level Security enforces this).
- **Edge Function pepper** — stored in Supabase secrets. Never appears in the app or in `supabase.properties`.

Both `encryption.properties` and `supabase.properties` are gitignored.

### Platform posture

- `android:allowBackup="false"`, with explicit data extraction and backup rules, so encrypted data and secrets are not copied to cloud backup.
- `INTERNET` permission is declared (required for Supabase API calls).
- Only the launcher activity is exported.
- The app performs no analytics, no crash reporting, and no telemetry.

---

## Project Structure

```
IBU_Verification/
├── app/
│   ├── build.gradle.kts            # BuildConfig secrets, Supabase properties, OkHttp
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/ibu_verification/
│       │   │   ├── HomeActivity.java
│       │   │   ├── ScannerActivity.java
│       │   │   ├── VerificationActivity.java
│       │   │   ├── NotFoundActivity.java
│       │   │   ├── AppGraph.java            # Manual dependency wiring (cloud stack)
│       │   │   ├── AndroidSecurityAdapters.java  # Android Base64 + Log adapters
│       │   │   ├── api/
│       │   │   │   ├── CertificateApi.java        # Lookup interface
│       │   │   │   └── SupabaseCertificateApi.java # OkHttp implementation
│       │   │   ├── db/
│       │   │   │   └── CloudCertificateRepository.java  # Cloud-backed CertificateLookup
│       │   │   ├── model/
│       │   │   │   └── Certificate.java
│       │   │   ├── security/
│       │   │   │   ├── GateSequence.java         # Gates 0-4
│       │   │   │   ├── GateResult.java
│       │   │   │   ├── CertificateLookup.java    # Lookup boundary interface
│       │   │   │   ├── CertificatePayloadCodec.java
│       │   │   │   ├── EncryptionHelper.java     # AES-GCM + PBKDF2 (desktop-compatible)
│       │   │   ├── HashUtil.java             # SHA-256 (used by desktop tools)
│       │   ├── res/
│       │   │   ├── layout/                        # XML layouts incl. view_scanner.xml
│       │   │   ├── values/                       # strings.xml, colors, themes
│       │   │   └── values-night/
│       │   ├── test/                                  # JVM unit tests
│       │   └── androidTest/                           # On-device instrumentation tests
├── tools/
│   ├── java/tools/               # Desktop generators and self-tests
│   └── run_tools.bat             # Compiles and runs the above
├── sample_qr_codes/              # Generated, gitignored
├── supabase.properties           # Gitignored — Supabase credentials
├── encryption.properties         # Gitignored — AES master secret + hash pepper
├── gradle/libs.versions.toml
└── DESIGN_AND_ROADMAP.txt        # Full design specification
```

`DESIGN_AND_ROADMAP.txt` is the authoritative design document and contains considerably more detail than this README.

---

## Testing

### Unit tests

```bash
./gradlew testDebugUnitTest
```

23 JVM tests covering the pure logic:

| Suite | Tests | Coverage |
| --- | --- | --- |
| `CertificatePayloadCodecTest` | 10 | Round-trip encoding, padding, length, alphabet, checksum rejection, ID validation |
| `GateSequenceTest` | 9 | Every gate and refusal reason, including first-failure ordering |
| `HashUtilTest` | 4 | Determinism, hex formatting, pepper sensitivity |

HTML reports are written to `app/build/reports/tests/testDebugUnitTest/index.html`.

### Instrumentation tests

These cover the layers a JVM test cannot reach: layout inflation, the encrypted storage round-trip, and end-to-end gate evaluation via mock API.

```bash
./gradlew connectedDebugAndroidTest
```

6 tests across both source sets, including `StoreUnreadableDiagnosticTest`, which exercises `CloudCertificateRepository` with a `MockCertificateApi` to verify the storage round-trip, decryption failures, and network error handling through the full gate sequence.

### Full verification

```bash
./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug
```

---

## Desktop Tooling

`tools/` contains standalone Java programs that run on the desktop without an emulator. They are useful for regenerating QR fixtures and for checking the codec and crypto logic quickly.

```powershell
.\tools\run_tools.bat
```

This compiles the tools against the same `CertificatePayloadCodec` and `EncryptionHelper` source the app uses, then runs:

- **`CryptoSelfTest`** — round-trip, ciphertext randomness, bit-flip rejection, wrong-pepper rejection, and blob-length verification.
- **`QrFixtureGenerator`** — emits the eleven PNG fixtures and re-verifies each generated code decodes back to its original ID and is rejected after a single-character edit.

Expected output ends with:

```
CRYPTO SELF TEST PASSED
ALL CHECKS PASSED
```

> The script expects a JDK with `java.util.Base64` (API 26+ equivalent). The `EncryptionHelper` class provides a JDK-default constructor for this purpose. Adjust the `JBR` or `JAVA_HOME` variable in `run_tools.bat` if your JDK is elsewhere.

---

## Configuration

Build-time secrets are auto-generated if absent and are **never committed**.

| Setting | Location | Notes |
| --- | --- | --- |
| `supabase.url` | `supabase.properties` | Supabase project URL. Auto-generated placeholder if absent. |
| `supabase.anon.key` | `supabase.properties` | Supabase anon key. Auto-generated placeholder if absent. |
| `cert.master.secret` | `encryption.properties` | AES key material. Auto-generated if absent. |
| `cert.hash.pepper` | `encryption.properties` | Lookup-hash pepper for desktop tools. Auto-generated if absent. **Not** used by the app at runtime. |
| `minSdk` / `targetSdk` | `app/build.gradle.kts` | 24 / 36 |

> **Do not commit `encryption.properties` or `supabase.properties`.** Both are in `.gitignore`. Each developer or CI machine generates its own encryption secret, so encrypted field data is not portable between installations by design.

### Server-side setup

The app expects a Supabase project with:

1. A `certificates` table with encrypted `text` columns (at minimum: `certificate_id`, `student_name`, `roll_number`, `enrollment_number`, `course_name`, `university_name`, `passing_year`, `percentage`, `certificate_type`).
2. A `lookup-cert` Edge Function that:
   - Receives the certificate ID from the client.
   - Computes `SHA-256(certificateId ‖ pepper)` using a server-side secret.
   - Queries the `certificates` table for a matching `cert_id_hash`.
   - Returns the encrypted row (or `{"found": false}`) to the client.
3. Row Level Security policies that allow the anon role only `SELECT` on the `certificates` table, and only through the Edge Function (not direct RPC).

---

## Troubleshooting

### *"This device could not read its own certificate store."*

This message appears when decryption of a returned record fails or a network error occurs. In order of likelihood:

1. **Network connectivity.** The app requires `INTERNET` to reach Supabase. Check Wi-Fi or mobile data.
2. **`cert.master.secret` changed.** If the secret in `encryption.properties` was regenerated or replaced, records encrypted with the old secret can no longer be decrypted. Restore the original `encryption.properties` or re-encrypt all stored records.
3. **The app was first launched when `cert.master.secret` was blank.** `EncryptionHelper` degrades to plaintext (returns values as-is) when the secret is empty. Once a real secret is supplied, those plaintext values will fail GCM decryption. Rebuild with a non-empty secret and clear app data.

### A QR code is rejected as damaged

Re-scan under better lighting and hold the phone steady. If a specific code always fails while others pass, the printed code was likely physically altered. To confirm a code is genuinely valid, regenerate the fixtures with `tools\run_tools.bat` and compare.

### The camera does not open

Grant the permission in **Settings → Apps → IBU Certificate Verifier → Permissions → Camera**. On first use the app shows a rationale in the refusal banner if access is denied.

### Supabase lookup returns "Certificate Not Found"

This is the expected behavior when no record exists in Supabase for the scanned ID. The `encoded/` sample fixtures (CER1–CER3) are no longer seeded on-device; they require corresponding records in the Supabase `certificates` table.

---

## Known Limitations

These are known and unaddressed, listed so they are not mistaken for defects you introduced:

- **PBKDF2 runs on every encrypt and decrypt.** The derived key is not cached, so each field operation pays 10,000 iterations.
- **Offline mode is not implemented.** The app requires network access to reach Supabase. A future enhancement could cache recently verified records locally.
- **A malformed stored year would crash rather than show an error.** `SupabaseCertificateApi.readCertificate()` parses the decrypted passing year with `Integer.parseInt` outside its checked path, and `GateSequence` only handles `EncryptionException`. Unreachable while encryption is correct, but worth hardening.
- **`recyclerview` is declared but unused.** There is no list surface in the current design.
- **The QR payload is obfuscated, not encrypted.** Anyone motivated can recover the certificate ID from a printed code. This is a documented design boundary, not a bug.
- **`cert.hash.pepper` is retained on device but not used at runtime.** It exists for desktop tooling compatibility. The lookup hash is computed server-side by the Edge Function.

---

## Contributing

Contributions are welcome. Because this project is a verification tool, changes to the security or gate logic are held to a higher standard than UI changes.

### Getting started

1. Fork and clone the repository.
2. Open the project in Android Studio using a version that supports Android Gradle Plugin 9.3.3, or build from the command line with the bundled Gradle wrapper.
3. Configure `supabase.properties` and ensure `encryption.properties` is generated (or create it manually with two 32-byte random hex values).
4. Build and test before making changes:
   ```bash
   ./gradlew assembleDebug testDebugUnitTest
   ```
5. Create a topic branch:
   ```bash
   git checkout -b feature/short-description
   ```

### Guidelines

**Before opening a pull request, all of the following must pass:**

```bash
./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug
./gradlew connectedDebugAndroidTest     # requires a connected device or emulator
tools\run_tools.bat                     # must print ALL CHECKS PASSED
```

**Code style**

- Java 11. Four-space indent, no wildcard imports, fields ordered before methods.
- Follow the conventions of the surrounding file. `GateSequence`, `EncryptionHelper`, and the Activities are internally consistent; match them rather than importing a different style.
- Comments explain *why* a decision was made, not what the line does. Security decisions in this codebase are non-obvious and deserve a reason.
- No new runtime dependencies without prior discussion. The current dependency set is intentionally small.

**Testing requirements**

- Any change to `GateSequence`, `CertificatePayloadCodec`, `EncryptionHelper`, `HashUtil`, or `CloudCertificateRepository` **must** come with a test that fails before the change and passes after it.
- Changes affecting stored data or API contracts need an instrumentation test. Layout inflation, the OkHttp-based API client, and `android.util.Base64` are unavailable to JVM unit tests, so bugs in these layers are invisible to the unit suite.
- Prefer extending `StoreUnreadableDiagnosticTest` or adding a focused suite over writing broad integration tests.

**Security-sensitive changes**

Please open an issue before submitting a pull request that touches encryption, key derivation, hashing, the codec, the gate sequence, or the Supabase Edge Function contract. Changes to these files alter what the app *certifies*, and should be reviewed deliberately.

**Scope**

- Preserve the four-screen architecture. Adding a settings screen, a manual ID entry field, or an account flow changes the security model and needs discussion first.
- Do not add network permissions beyond `INTERNET` (already declared) and `CAMERA` (already declared).
- UI changes should keep the shared blue `AppBarLayout` / `MaterialToolbar` styling and respect the forced light theme in night mode.

### Commit messages

Use an imperative, descriptive subject line, and keep the scope narrow.

```
Decrypt certificate fields with device master secret, not server key
Add OkHttp dependency and Supabase lookup integration
Remove SQLite seed logic; seed server-side via Edge Function
```

---

## License

**This repository currently has no `LICENSE` file.** All rights are reserved by default, which means the project cannot legally be reused or redistributed by others.

Before publishing this repository publicly, add a license. For an academic or internal project, [MIT](https://opensource.org/licenses/MIT) is the most permissive and lowest-friction option:

```text
MIT License

Copyright (c) 2026 iB University

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

If the project is meant to stay internal, delete this section instead and add a short internal-use notice.
