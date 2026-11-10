plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.zpan2327.liquidglass.demo"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.zpan2327.liquidglass.demo"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The demo APK is signed with the debug key on purpose: anyone can rebuild it and
            // get an installable artifact without a private keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/**.version"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "kotlin/**"
            excludes += "**.properties"
        }
    }
}

dependencies {
    implementation(project(":liquid"))

    // Compose, miuix and activity-compose versions come from :liquid's `api` dependencies,
    // which is the point of the module split — the demo only adds what the library does not
    // expose on purpose.
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4-rc01")
}
