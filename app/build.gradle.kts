/*
 * CineDrive — local video player for Android TV
 * Step 1: application module configuration.
 *
 * PLAYER ENGINE DECISION — LibVLC (org.videolan.android:libvlc-all)
 *   • One AAR, natives for arm64-v8a / armeabi-v7a / x86_64 / x86, zero NDK work.
 *   • Covers the full spec out of the box: MKV/AVI/WebM/MP4 containers,
 *     AC3/E-AC3, DTS, TrueHD, AAC, MP3 audio, and PGS/VobSub/SSA/SRT subtitles.
 *   • Media3 would need the FFmpeg extension built from NDK source to match
 *     DTS/PGS coverage — rejected for maintenance cost.
 *   • The engine will sit behind a PlayerEngine interface (Step 5), so a
 *     Media3 backend can be added later without touching UI or domain code.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cinedrive.tv"

    // 36 is forced by libvlc-all 3.7.7 AAR metadata (it requires compileSdk
    // >= 36). AGP 8.10.x compiles against android-36 without issues; runtime
    // behaviour stays governed by targetSdk below.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cinedrive.tv"

        // Android 10 (API 29) is the spec's floor. Storage code is written
        // against 29+ APIs (ACTION_MEDIA_MOUNTED runtime receiver on 29,
        // StorageVolumeCallback on 30+).
        minSdk = 29

        // 34 keeps TV-side behaviour predictable on Android 14+ boxes;
        // nothing in this app needs 35 runtime semantics.
        targetSdk = 34

        versionCode = 1
        versionName = "1.0.0"

        // LibVLC natives: keep the four common TV/emulator ABIs only.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Sideload-friendly output: one universal APK plus one slim APK per ABI
    // (the universal one carries ~80 MB of LibVLC natives for all ABIs).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

ksp {
    // Export Room schemas to app/schemas/ for migration tests (Step 3).
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // ── AndroidX core ────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)          // SAF tree helpers (Step 2 fallback)

    // ── Compose (BOM-pinned) ─────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)             // LazyVerticalGrid for the browse grid
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended) // playback/remote glyphs; R8 strips unused
    implementation(libs.compose.ui.tooling.preview)

    // ── Compose for TV ───────────────────────────────────────────────────
    implementation(libs.tv.material)                    // TV Surface / Carousel / focus helpers

    // ── Lifecycle / ViewModel / Navigation ───────────────────────────────
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)      // collectAsStateWithLifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    // ── Dependency injection (KSP — no kapt) ─────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Persistence ──────────────────────────────────────────────────────
    implementation(libs.room.runtime)                   // resume points, volume UUIDs, NFO cache
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.datastore.preferences) // app settings (subtitle size, ...)

    // ── Playback engine ──────────────────────────────────────────────────
    implementation(libs.libvlc)

    // ── Images ───────────────────────────────────────────────────────────
    implementation(libs.coil.compose)                   // poster/fanart from file:// URIs

    // ── Coroutines ───────────────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── Debug tooling ────────────────────────────────────────────────────
    debugImplementation(libs.compose.ui.tooling)

    // ── Tests ────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    // Real XmlPullParser (kxml2) for JVM tests — the mockable android.jar
    // stubs XmlPullParserFactory.newInstance() to throw "not mocked".
    testImplementation(libs.kxml2)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
}
