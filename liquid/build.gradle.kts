plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.zpan2327.liquidglass"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
        // AGP writes these into the consumer's manifest when needed.
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/**.version"
        }
    }
}

dependencies {
    // Public API mentions LayerBackdrop and ImageVector, so Compose + miuix must be
    // `api` — consumers get them transitively and do not have to pin versions themselves.
    api(platform("androidx.compose:compose-bom:2026.09.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4-rc01")
    api("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4-rc01")
}
