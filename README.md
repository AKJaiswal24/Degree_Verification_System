# IBU Certificate Verifier

An offline Android application that verifies the authenticity of academic certificates by scanning the QR code printed on them.

The app runs entirely on-device. It performs no network calls, requires no sign-in, and holds no server-side component. Certificate records live in an encrypted local database on the device and are never transmitted.

---

## Table of Contents

- [Purpose](#purpose)
- [Features](#features)
- [How Verification Works](#how-verification-works)
- [Gate Sequence Reference](#gate-sequence-reference)
- [Screens](#screens)
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
- **No data leaves the device.** There is no analytics, no crash reporting, and no network permission in the manifest.

---

## Features

- **QR scanning** powered by ZXing with a custom viewfinder overlay, torch toggle, and a QR-only decoder so 1D barcodes cannot trigger verification.
- **A five-stage validation gate** that distinguishes between a damaged code, a foreign QR code, an unsupported secure code, a plain typed ID, and a genuine lookup miss.
- **Integrity checking** via a position-weighted checksum, so a single altered character is detected before any database access occurs.
- **Encrypted local storage** using AES-256-GCM with per-record random salts and IVs.
- **Indexed lookup** via a peppered SHA-256 hash, which allows an exact match to be found without storing the certificate ID in a searchable column.
- **Four focused screens** with no navigation drawer, no settings, and no account.
- **Seven seeded sample records** inserted on first launch so the app is testable immediately after install.
- **Backups disabled**, so the encrypted store is not exfiltrated through cloud backup.

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

**The codec is obfuscation with integrity validation, not encryption.** It obscures the certificate ID from casual reading and reliably detects tampering. It is not designed to resist a determined analyst, and should not be treated as a cryptographic control. Confidentiality in this app comes from the encrypted database, not from the QR payload.

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
| 4 | Store cannot be decrypted | *This device could not read its own certificate store* |
| 4 | No matching record | `NotFoundActivity` |
| 4 | Matching record | `VerificationActivity` |

Gate 2 exists to give a clear, deliberate message when someone photographs a plain-text certificate ID instead of a QR code, rather than treating it as a damaged scan.

---

## Screens

| Screen | Purpose |
| --- | --- |
| `HomeActivity` | Launcher. Seeds the database on first launch and offers a single "Scan QR Code" button. |
| `ScannerActivity` | Camera preview, torch toggle, refusal banner, and lookup progress indicator. |
| `VerificationActivity` | Displays the decrypted record for a certificate that passed every gate. |
| `NotFoundActivity` | Shows the scanned certificate ID when the code is genuine but the record is absent. |

All four are declared `android:exported="false"` except the launcher, and all are locked to portrait orientation.

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

---

## Installation

### Prerequisites

- JDK 11 or newer
- Android SDK with Platform 36 and Build Tools installed
- An Android 7.0+ device or emulator

### Steps

1. **Clone the repository**

   ```bash
   git clone <your-repository-url>
   cd IBU_Verification
   ```

2. **Generate the encryption secrets**

   This happens automatically on the first Gradle build, so there is nothing to run by hand. If `encryption.properties` is absent, the build generates it with two independent 32-byte random values:

   ```properties
   cert.master.secret=<64 hex characters>
   cert.hash.pepper=<64 hex characters>
   ```

   > **Keep this file.** It is gitignored and is never committed. Losing or replacing it makes every encrypted record in the local database permanently unreadable, and the app will report *"This device could not read its own certificate store."* See [Troubleshooting](#troubleshooting).

3. **Build the debug APK**

   ```bash
   # macOS / Linux
   ./gradlew assembleDebug
   ```

   ```powershell
   # Windows
   .\gradlew.bat assembleDebug
   ```

4. **Locate the APK**

   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

5. **Install on a connected device**

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

6. **Grant camera access**

   The app requests `CAMERA` at runtime the first time the scanner opens. Camera hardware is declared optional, so the app still installs on devices without a camera.

---

## Usage

1. Open **IBU Certificate Verifier**.
2. Tap **Scan QR Code** and allow camera access.
3. Point the camera at the QR code printed on the certificate.
4. Read the result:

   - **Green, "Certificate Verified"** — the code is intact and the record exists. The student's name, roll number, enrolment number, course, university, year of passing, percentage, and certificate type are decrypted from the local store and displayed.
   - **Red, "Certificate Not Found"** — the code itself is valid and undamaged, but no matching record exists on this device. The scanned ID is shown so it can be checked against the issuing record.
   - **Red banner, "The QR code is damaged or has been altered"** — a character was changed after the code was generated. Re-scan at a better angle, or treat the certificate as suspect.

5. Use the torch button in the lower-right corner in low light, and **Scan Another Certificate** to verify a second certificate without returning to the home screen.

### Testing with sample codes

The repository ships a generator that produces real, scannable QR images covering the valid and refusal paths.

```powershell
.\tools\run_tools.bat
```

Output is written to `sample_qr_codes/`, split into two folders:

| File | Gate exercised | Expected result |
| --- | --- | --- |
| `encoded/CER1IBAU0001.png` … `encoded/CER3IBAU0007.png` | All pass | Certificate Verified |
| `plain/CER1IBAU0001.png` | Gate 2 | *Plain Certificate ID not accepted* |
| `plain/httpsexampleeduverifyid7.png` | Gate 2 | *Not a certificate QR code* |
| `plain/WIFISCampusNetTWPAPsecret.png` | Gate 2 | *Not a certificate QR code* |
| `plain/secure_SQR1AAAABBBBCCCCDDDDEEEE.png` | Gate 1 | *Secure certificate not supported* |

The `encoded/` fixtures contain a valid `IBU1` payload. The `plain/` fixtures contain the raw text that should be refused, and are the quickest way to confirm each refusal path renders correctly.

`sample_qr_codes/` and `tools/build/` are gitignored, so run the generator after every fresh clone.

---

## Security Model

### Data at rest

Every one of the eleven stored columns is encrypted. Nothing readable is written to the database except the lookup hash.

| Property | Value |
| --- | --- |
| Cipher | `AES/GCM/NoPadding` |
| Key size | 256 bits |
| Key derivation | `PBKDF2WithHmacSHA256`, 10,000 iterations |
| Salt | 16 random bytes, unique per value |
| IV | 12 random bytes, unique per value |
| Auth tag | 128 bits |
| Stored format | `Base64( salt ‖ iv ‖ ciphertext ‖ tag )` |

The 128-bit GCM tag means any modification to a stored value is detected on read rather than silently returning corrupted data.

### Lookup without a searchable plaintext ID

The `cert_id_hash` column holds `SHA-256(certificateId ‖ cert.hash.pepper)` in lowercase hex, with a unique index. Lookups match on this column, so the database never needs a plaintext index on the certificate ID itself. An attacker with a copy of the database still cannot enumerate certificate IDs without the pepper.

### Secrets

Two independent secrets are used, and they are **not** interchangeable:

- `cert.master.secret` derives the AES key. Rotating it makes existing records undecryptable.
- `cert.hash.pepper` derives the lookup hash. Rotating it makes existing records unfindable, which surfaces as *"Certificate Not Found"* rather than a store error.

Both live in `encryption.properties`, which is gitignored. If the master secret is ever absent, `EncryptionHelper` logs an error and degrades to plaintext rather than crashing — a deliberate availability-over-confidentiality trade-off that is logged loudly so it cannot pass unnoticed.

### Platform posture

- `android:allowBackup="false"`, with explicit data extraction and backup rules, so the encrypted store is not copied to cloud backup.
- No `INTERNET` permission is declared.
- Only the launcher activity is exported.

---

## Project Structure

```
IBU_Verification/
├── app/
│   ├── build.gradle.kts            # BuildConfig secrets, auto-generates encryption.properties
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/ibu_verification/
│       │   │   ├── HomeActivity.java
│       │   │   ├── ScannerActivity.java
│       │   │   ├── VerificationActivity.java
│       │   │   ├── NotFoundActivity.java
│       │   │   ├── AppGraph.java            # Minimal manual dependency wiring
│       │   │   ├── model/
│       │   │   │   └── Certificate.java
│       │   │   ├── security/
│       │   │   │   ├── GateSequence.java         # Gates 0-4
│       │   │   │   ├── GateResult.java
│       │   │   │   ├── CertificateLookup.java    # Lookup boundary interface
│       │   │   │   ├── CertificatePayloadCodec.java
│       │   │   │   ├── EncryptionHelper.java     # AES-GCM + PBKDF2
│       │   │   │   └── HashUtil.java             # SHA-256 lookup hash
│       │   │   └── db/
│       │   │       ├── CertificateDbHelper.java   # Schema, index, migration
│       │   │       ├── CertificateRepository.java # Encrypted CRUD
│       │   │       └── CertificateSeeder.java     # First-launch sample data
│       │   └── res/
│       │       ├── layout/                        # XML layouts incl. view_scanner.xml
│       │       ├── values/                       # strings.xml, colors, themes
│       │       └── values-night/
│       ├── test/                                  # JVM unit tests
│       └── androidTest/                           # On-device instrumentation tests
├── tools/
│   ├── java/tools/               # Desktop generators and self-tests
│   └── run_tools.bat             # Compiles and runs the above
├── sample_qr_codes/              # Generated, gitignored
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

These cover the layers a JVM test cannot reach: layout inflation, SQLite, and the real encrypt/decrypt round-trip.

```bash
./gradlew connectedDebugAndroidTest
```

6 tests across both source sets, including `StoreUnreadableDiagnosticTest`, which writes a record and reads it back to prove the storage round-trip is correct. A bug once shipped in which `insert()` wrote `certificate_id` in plaintext while `readRow()` decrypted it; this suite now fails loudly if that asymmetry is ever reintroduced.

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

This compiles the tools against the same `CertificatePayloadCodec` source the app uses, then runs:

- **`CryptoSelfTest`** — round-trip, ciphertext randomness, bit-flip rejection, wrong-pepper rejection, and blob-length verification.
- **`QrFixtureGenerator`** — emits the eleven PNG fixtures and re-verifies each generated code decodes back to its original ID and is rejected after a single-character edit.

Expected output ends with:

```
CRYPTO SELF TEST PASSED
ALL CHECKS PASSED
```

> The script expects the Android Studio JBR at `C:\Program Files\Android\Android Studio\jbr\bin`. Adjust the `JBR` variable in `run_tools.bat` if your JDK is elsewhere.

---

## Configuration

There is no settings screen and no runtime configuration. Behaviour is fixed at build time.

| Setting | Location | Notes |
| --- | --- | --- |
| `cert.master.secret` | `encryption.properties` | AES key material. Auto-generated if absent. |
| `cert.hash.pepper` | `encryption.properties` | Lookup-hash pepper. Auto-generated if absent. |
| `minSdk` / `targetSdk` | `app/build.gradle.kts` | 24 / 36 |
| Seed records | `CertificateSeeder.java` | Seven sample certificates, inserted on first launch only |
| Database schema | `CertificateDbHelper.java` | `DATABASE_VERSION` with a migration hook |

> **Do not commit `encryption.properties`.** It is already in `.gitignore`. Each developer or CI machine generates its own, so databases are not portable between installations by design.

---

## Troubleshooting

### *"This device could not read its own certificate store."*

This message is emitted only when decryption of a matched record fails. It is never caused by camera permissions, QR quality, or device security settings. In order of likelihood:

1. **Stale data written by an older build.** Clear the application data and relaunch so the seed records are re-inserted:
   ```bash
   adb shell pm clear com.example.ibu_verification
   ```
   Simply reinstalling the APK is not sufficient, because `CertificateSeeder.seedIfEmpty()` returns early when the table already holds rows and will not repair them.

2. **`cert.master.secret` changed while `cert.hash.pepper` did not.** The hash still matches, so the record is found, and then the GCM authentication tag fails. Restore the original `encryption.properties` and clear app data.

3. **The app was first launched when `cert.master.secret` was blank.** `EncryptionHelper` degrades to plaintext in that case, writing unencrypted rows. Once a real secret is supplied, those rows can no longer be decrypted. Clear app data.

Rotating *both* secrets instead produces a clean *"Certificate Not Found"*, because the record can no longer be located at all — a distinctly different symptom that confirms which secret changed.

### A QR code is rejected as damaged

Re-scan under better lighting and hold the phone steady. If a specific code always fails while others pass, the printed code was likely physically altered. To confirm a code is genuinely valid, regenerate the fixtures with `tools\run_tools.bat` and compare.

### The camera does not open

Grant the permission in **Settings → Apps → IBU Certificate Verifier → Permissions → Camera**. On first use the app shows a rationale in the refusal banner if access is denied.

### First launch takes several seconds

Seeding encrypts seven records, each involving a 10,000-iteration PBKDF2 derivation, and currently runs on the main thread. This is a known performance issue, not a hang. See [Known Limitations](#known-limitations).

---

## Known Limitations

These are known and unaddressed, listed so they are not mistaken for defects you introduced:

- **PBKDF2 runs on every encrypt and decrypt.** The derived key is not cached, so each operation pays 10,000 iterations. This is the main cost behind the slow first launch.
- **Seeding runs on the main thread**, which delays first launch by several seconds and risks an ANR on a slow device.
- **A malformed stored year would crash rather than show an error.** `CertificateRepository` parses the decrypted passing year with `Integer.parseInt` outside its checked path, and `GateSequence` only handles `EncryptionException`. Unreachable while encryption is correct, but worth hardening.
- **`recyclerview` is declared but unused.** There is no list surface in the current design.
- **The QR payload is obfuscated, not encrypted.** Anyone motivated can recover the certificate ID from a printed code. This is a documented design boundary, not a bug.
- **The store is per-device and not portable.** Records are seeded locally, so the app verifies against its own sample data rather than an institutional database. A production deployment would need a signed data import path.

---

## Contributing

Contributions are welcome. Because this project is a verification tool, changes to the security or gate logic are held to a higher standard than UI changes.

### Getting started

1. Fork and clone the repository.
2. Open the project in Android Studio using a version that supports Android Gradle Plugin 9.3.3, or build from the command line with the bundled Gradle wrapper.
3. Build and test before making changes:
   ```bash
   ./gradlew assembleDebug testDebugUnitTest
   ```
4. Create a topic branch:
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

- Any change to `GateSequence`, `CertificatePayloadCodec`, `EncryptionHelper`, `HashUtil`, or `CertificateRepository` **must** come with a test that fails before the change and passes after it.
- Changes affecting stored data need an instrumentation test. Layout inflation, SQLite, and `android.util.Base64` are unavailable to JVM unit tests, so bugs in these layers are invisible to the unit suite — this is exactly how the plaintext `certificate_id` defect shipped in the first place.
- Prefer extending `StoreUnreadableDiagnosticTest` or adding a focused suite over writing broad integration tests.

**Security-sensitive changes**

Please open an issue before submitting a pull request that touches encryption, key derivation, hashing, the codec, or the gate sequence. Changes to these files alter what the app *certifies*, and should be reviewed deliberately.

**Scope**

- Preserve the four-screen architecture. Adding a settings screen, a manual ID entry field, or an account flow changes the security model and needs discussion first.
- Do not add network permissions.
- UI changes should keep the shared blue `AppBarLayout` / `MaterialToolbar` styling and respect the forced light theme in night mode.

### Commit messages

Use an imperative, descriptive subject line, and keep the scope narrow.

```
Encrypt certificate_id on insert to match the decrypt path on read
Add instrumentation coverage for the storage round-trip
Remove the offline footer from the home screen
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
