import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Release signing comes from environment variables so no key material is ever committed.
// Locally: export SIGNING_KEYSTORE_PATH=... SIGNING_STORE_PASSWORD=... SIGNING_KEY_ALIAS=... SIGNING_KEY_PASSWORD=...
// In CI: see .github/workflows/android.yml. Without them, assembleRelease yields an unsigned APK.
val releaseKeystore: File? = System.getenv("SIGNING_KEYSTORE_PATH")?.let(::File)?.takeIf { it.exists() }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.securevault"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.securevault"
        // 28+: platform BiometricPrompt, StrongBox Keymaster, setUnlockedDeviceRequired().
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Installs side by side with the release build, so testing never touches a real vault.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Exported schemas are required to write tested migrations later. Never use
    // fallbackToDestructiveMigration() in a password manager: it silently deletes the vault.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // --- Core / lifecycle -------------------------------------------------------------------
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    // BiometricPrompt requires a FragmentActivity host.
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // ProcessLifecycleOwner -> lock the vault when the whole app goes to the background.
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // --- Jetpack Compose ----------------------------------------------------------------------
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // foundation 1.7+ provides TextFieldState + BasicSecureTextField (no String-backed password state).
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Room + SQLCipher ---------------------------------------------------------------------
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1@aar")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // --- Biometrics ---------------------------------------------------------------------------
    implementation("androidx.biometric:biometric:1.1.0")

    // --- Security / crypto --------------------------------------------------------------------
    // Argon2id (native reference implementation via JNI).
    implementation("com.lambdapioneer.argon2kt:argon2kt:1.6.0")
    // zxcvbn password strength estimation (accepts CharSequence, supports wipe()).
    implementation("com.nulab-inc:zxcvbn:1.9.0")
    // NOTE: androidx.security:security-crypto (EncryptedFile / EncryptedSharedPreferences) is
    // deprecated as of 1.1.0-alpha07 and is intentionally NOT used. Everything it offered is done
    // directly against the AndroidKeyStore in CryptographyManager, with tighter key policies
    // (StrongBox, unlocked-device-required, biometric-bound keys). If your policy mandates it:
    // implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // --- Background clipboard wipe fallback (survives process death) --------------------------
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // --- Tests --------------------------------------------------------------------------------
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
