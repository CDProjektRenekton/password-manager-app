# SecureVault: offline Android password manager

Native Kotlin + Jetpack Compose password manager with a zero-knowledge, offline-only design.
The vault is encrypted with an Argon2id-derived key and two layers of encryption at rest
(SQLCipher and per-field AES-256-GCM). Its keys are wrapped by hardware-backed Android Keystore keys.

- **Min SDK** 28 · **Target SDK** 35 · Kotlin 2.1 · Compose BOM 2024.12 · Room 2.6 · SQLCipher 4.6
- **No `INTERNET` permission.** It is removed in the manifest, even if a dependency tries to merge it in.

---

## 1. Architecture

```
┌──────────────────────────── UI (Jetpack Compose, single FragmentActivity) ────────────────────────────┐
│ Setup · Unlock · Vault list · Details · Add/Edit (+ generator sheet) · Generator                        │
│ FLAG_SECURE on the window (dialogs/sheets/popups inherit) · TextFieldState/BasicSecureTextField only     │
└───────────────▲───────────────────────────────────────────────────────────────▲──────────────────────────┘
                │ ViewModels (own all text state; wipe on clear)                │ VaultState drives navigation
┌───────────────┴───────────────┐   ┌──────────────────────────────┐   ┌────────┴──────────────────────────┐
│ CredentialRepository          │   │ SecureClipboard (30 s wipe)  │   │ AutoLockManager                   │
│  encrypt/decrypt every field  │   │ PasswordGenerator (CSPRNG)   │   │  ProcessLifecycleOwner ON_STOP    │
│  AAD = "<uuid>|<column>"      │   │ PasswordStrengthEstimator    │   │  60 s inactivity watchdog         │
└───────────────┬───────────────┘   └──────────────────────────────┘   └────────┬──────────────────────────┘
                │                                                               │ lock()
┌───────────────┴───────────────────────────────────────────────────────────────┴──────────────────────────┐
│ VaultSession: key hierarchy and lifecycle (keys exist only while Unlocked, zeroed on lock)               │
├───────────────────────────────┬───────────────────────────────┬──────────────────────────────────────────┤
│ Argon2KeyDerivation (Argon2id)│ CryptographyManager (Keystore)│ Room + SQLCipher (VaultDatabase)         │
│ Hkdf (RFC 5869)               │  device-bound key             │  credentials table: all BLOB ciphertext  │
│ AesGcm (session keys)         │  biometric-bound key          │                                          │
└───────────────────────────────┴───────────────────────────────┴──────────────────────────────────────────┘
```

### Key hierarchy

```
master password ──Argon2id(salt16, 64 MiB, t=3, p=2)──► KEK (256-bit, transient)
                                                          │ AES-256-GCM unwrap (AAD "vk/password/v1")
                                                          ▼
BiometricPrompt ─► Keystore key (StrongBox/TEE, ──────► VAULT KEY (VK): 256-bit SecureRandom, in RAM only while unlocked
                   auth-per-use, BIOMETRIC_STRONG,        │
                   invalidated on new enrollment)         ├─HKDF "sqlcipher/v1"─► SQLCipher raw key (x'…')
                                                          └─HKDF "fields/v1"────► field key (AES-256-GCM)

vault.meta = AES-GCM(Keystore device-bound key) {
               argon2 salt + params, password-wrapped VK, biometric-wrapped VK? }
```

Design decisions:

| Decision | Why |
|---|---|
| A random vault key wraps the data, not the Argon2 output directly | Changing the master password or enabling biometrics only re-wraps 32 bytes. Data is never re-encrypted. |
| No stored password hash or verifier | A wrong password fails the AES-GCM tag when unwrapping the VK. Nothing on disk can be checked against guesses faster than the full Argon2id run. |
| Metadata sealed with a **device-bound Keystore key** | A copied data directory, such as a leaked backup or a forensic image, can't be brute-forced offline. The attacker also needs this device's TEE or StrongBox, unlocked. |
| Device key is **not** `unlockedDeviceRequired` | That flag ties a key to the lock-screen credential: generation fails on phones without a secure lock screen, and removing the lock screen later could make the key unusable, which would destroy the vault even for someone who knows the master password. The master password and Argon2id still guard the vault key, so the flag added little. |
| Biometric key: **auth-per-use + CryptoObject** | Biometrics gate unlocking *cryptographically*. The Keystore will not decrypt the VK without a fresh Class-3 auth, so hooking a "success" callback does nothing. |
| Field-level AES-GCM **on top of** SQLCipher | Defence in depth. Plaintext SQLite pages in RAM still hold only ciphertext for every user field, and the AAD (`uuid\|column`) stops ciphertexts being swapped between rows or columns. |
| Field key lives in RAM rather than in the Keystore | Keystore operations are IPC calls into the TEE: 1–20 ms each, and much slower on StrongBox. A Keystore-only field key would also make the master password irrelevant, because anyone holding the unlocked phone could decrypt through the app. The field key comes from the VK, so data stays bound to the master password. |
| Manual DI (`AppContainer`) | The security core is small. Explicit wiring keeps the key-handling object graph auditable. |

### Lock-down flow
1. When the app goes to the background, `ProcessLifecycleOwner` fires `ON_STOP` and `VaultSession.lock()` runs. `ON_STOP` is debounced so rotation doesn't trigger it.
2. After 60 s without interaction, the watchdog calls `lock()`. Interaction means touch or key events through `Activity.onUserInteraction()`, plus text edits reported by every field, because IME typing bypasses the activity.
3. `lock()` closes SQLCipher and zero-fills the VK, the DB passphrase and the field-key bytes. It then emits `Locked`.
4. `AppNavHost` pops the whole back stack, so every ViewModel's `onCleared()` wipes its `TextFieldState`s. Only then does it show the Unlock screen.
5. If an unlock finishes after the app was already backgrounded, it is immediately re-locked.

---

## 2. Feature → implementation map

| Requirement | Where |
|---|---|
| Argon2id master password | `security/crypto/Argon2KeyDerivation.kt`, `security/vault/VaultSession.kt` |
| Biometric unlock tied to Keystore | `security/crypto/CryptographyManager.kt` (`BIOMETRIC_BOUND`), `security/biometric/BiometricAuthenticator.kt` |
| Background / 1-minute lock | `security/lock/AutoLockManager.kt`, `SecureVaultApp.kt`, `MainActivity.onUserInteraction` |
| Room + SQLCipher | `data/db/CredentialEntity.kt`, `CredentialDao.kt`, `VaultDatabase.kt` |
| AES-256-GCM field encryption | `security/crypto/AesGcm.kt`, `data/repository/CredentialRepository.kt` |
| Hardware-backed keys | `CryptographyManager` (StrongBox when present, TEE otherwise; biometric key is also `setUnlockedDeviceRequired`) |
| 30 s clipboard wipe | `security/clipboard/SecureClipboard.kt` (coroutine, plus a WorkManager fallback for process death) |
| FLAG_SECURE | `MainActivity.onCreate`, plus `setRecentsScreenshotEnabled(false)` on 13+ |
| CharArray/ByteArray secrets | `security/crypto/SecureMemory.kt`, `ui/components/TextFieldStateExt.kt`, `CredentialDraft`/`CredentialSecrets` |
| zxcvbn strength meter | `security/password/PasswordStrengthEstimator.kt`, `ui/components/StrengthMeter.kt` |
| Zero-knowledge / offline | Manifest (`INTERNET` removed, backups and D2D transfer disabled), no network code anywhere |
| CRUD + search + visibility toggle | `ui/screens/vault`, `details`, `edit` |
| SecureRandom generator (8–64) | `security/password/PasswordGenerator.kt`, `ui/screens/generator` |

---

## 3. Step-by-step build order

1. **Gradle**: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `app/proguard-rules.pro`.
2. **Manifest hardening**: remove `INTERNET`, set `allowBackup=false`, add `data_extraction_rules.xml`.
3. **Crypto primitives**: `SecureMemory`, `EncryptedPayload`, `AesGcm`, `Hkdf`, `SqlCipherKey`. They are pure JVM and unit-tested.
4. **Keystore**: `CryptographyManager` with its device-bound and biometric-bound policies.
5. **KDF**: `Argon2KeyDerivation` with versioned `KdfParams`.
6. **Vault**: `VaultMetadataStore` (sealed, atomic writes), `UnlockThrottle`, `VaultSession`.
7. **Storage**: `CredentialEntity`, `CredentialDao`, `VaultDatabase` (SQLCipher factory), `CredentialRepository`.
8. **Platform protections**: `AutoLockManager`, `SecureClipboard`, `BiometricAuthenticator`.
9. **Password tools**: `PasswordGenerator`, `PasswordStrengthEstimator`.
10. **UI**: secure text fields, strength meter, then screens and `AppNavHost`, then `MainActivity` with FLAG_SECURE.

---

## 4. Memory-hygiene policy (and its honest limits)

- Master password, passwords, notes and generated passwords move through app code only as `CharArray`/`ByteArray`, and are zeroed in `finally` blocks (`useThenWipe`).
- Text input uses Compose `TextFieldState` / `BasicSecureTextField`. That state is **ViewModel-owned and never `rememberSaveable`**, so it is never written into the saved-instance `Bundle`, which Android can persist to disk. Wiping a field also clears its undo history.
- Revealed secrets are rendered through a disabled, read-only `BasicTextField`. Nothing can be selected or copied, so the only copy path is the auto-clearing clipboard.
- Passwords and notes are decrypted **on demand** (reveal, copy or edit) and never for the list screen.
- **Residual risk (unavoidable on Android/JVM):** Compose's text layout, `Parcel.writeCharSequence` (clipboard) and JCA's `SecretKeySpec` make internal copies that app code can't zero. Titles, URLs and usernames are rendered as `String`s, because Compose's `Text` requires them. The mitigations are short lifetimes, aggressive locking, and FLAG_SECURE. This trade-off is the same one every Android password manager accepts.

## 5. Threat model summary

| Attacker | Outcome |
|---|---|
| Steals a locked phone | The DB is SQLCipher-encrypted and the metadata is sealed by a non-exportable Keystore key. Brute-forcing requires Argon2id per guess, on-device, with exponential back-off after 5 failures. |
| Copies app data (root/forensics/backup) | Backups and D2D transfer are disabled. A copy can't be attacked offline because the metadata key never leaves the TEE or StrongBox. |
| Malware taking screenshots or screen-recording | FLAG_SECURE gives it black frames, and the Recents thumbnail is blank. |
| Clipboard sniffing | The clip is flagged `IS_SENSITIVE`, wiped after 30 s, and wiped even if the process dies (WorkManager). |
| New fingerprint enrolled by a thief who knows the PIN | The biometric key is invalidated, and the master password is required. |
| Network exfiltration | Not possible: the app has no `INTERNET` permission. |

## 6. Hardening backlog (recommended next steps)
- Master-password change and Argon2 parameter upgrade (re-wrap the VK: `VaultMetadata` is already versioned).
- Encrypted export/import (`.svault` = Argon2id + AES-GCM container) for user-controlled backups.
- Android Autofill service, plus disabling autofill on the vault's own fields.
- Play Integrity / root heuristics (advisory only), and tapjacking protection (`filterTouchesWhenObscured`).
- Benchmark Argon2 on the lowest supported device. Raise `m`/`t` if unlock stays under ~1 s.
- Instrumented tests for `CryptographyManager`, `VaultSession`, and Room migrations (`room-testing` is already on the classpath).

## 7. Building & testing

```bash
./gradlew :app:testDebugUnitTest     # JVM tests: HKDF RFC vectors, AES-GCM tamper/AAD, generator, zxcvbn
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease       # R8-minified, logs stripped (signed only if SIGNING_* env vars are set)
```

Every push runs `.github/workflows/android.yml`, which runs the tests and attaches the APKs to the
workflow run as downloadable artifacts.

## 8. Installing on a phone

Requires **Android 9 (API 28) or newer**. Biometric unlock needs a Class 3 ("strong") fingerprint or
face sensor. The app is not on the Play Store, so you sideload the APK.

> **Read before storing real passwords.** The vault exists only on the phone, and there's no export or
> backup yet. **Uninstalling the app deletes the vault.** Android also refuses to update an app whose
> signing key has changed, so the only way to replace such a build is to uninstall it, which deletes the vault.

### Option A: test build from GitHub (no tools needed)
1. On GitHub, open **Actions → Android build**, click the latest green run, and download the
   **`securevault-debug-apk`** artifact (a zip; artifacts are kept for 90 days).
2. Unzip it and copy `app-debug.apk` to the phone.
3. Open it on the phone. Allow **"Install unknown apps"** for your file manager or browser when asked, then install.
   It appears as *SecureVault* (package `com.securevault.debug`).

The debug build is for **trying the app only**. It is *debuggable*, so anyone with USB-debugging access
to the phone can attach a debugger to it. CI also signs it with a throwaway key that changes on every
run, so a newer debug APK can't be installed over the old one without uninstalling first, which wipes the vault.

### Option B: your own signed release build (for real use)
Do this once. You need a JDK for `keytool`.

```bash
keytool -genkeypair -v -keystore securevault.jks -alias securevault \
        -keyalg RSA -keysize 4096 -validity 10000      # use ONE password for store and key
base64 -w0 securevault.jks > securevault.jks.b64       # macOS: base64 -i securevault.jks -o securevault.jks.b64
```

In the repo, go to **Settings → Secrets and variables → Actions** and add these repository secrets:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | contents of `securevault.jks.b64` |
| `SIGNING_STORE_PASSWORD` | the password you chose |
| `SIGNING_KEY_ALIAS` | `securevault` |
| `SIGNING_KEY_PASSWORD` | the same password |

Push a commit, or click **Run workflow** on the Actions tab. The run now also produces
**`securevault-release-apk`**: minified, non-debuggable, and signed with *your* key, so later builds update in place.
**Back up `securevault.jks` and its password offline.** Without them you can never ship an update to your installed app.
Never commit the keystore (`*.jks` is git-ignored).

### Option C: Android Studio
Open the project folder, connect the phone with USB debugging on, and press **Run** for a debug install.
For a release install, use **Build → Generate Signed App Bundle / APK** with your keystore.
From the command line: `adb install -r app/build/outputs/apk/release/app-release.apk`.
