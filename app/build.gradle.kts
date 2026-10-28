import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The version lives in version.properties, which ./release.sh bumps.
val versions = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }

// Release signing from keystore.properties (git-ignored) when there is one; otherwise this project's debug key.
// Every Cosmic Plumber APK must carry the same key, or Android won't take it as an update.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dev.m4s4t0.cosmicplumber"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.m4s4t0.cosmicplumber"
        minSdk = 29
        targetSdk = 36
        versionCode = versions.getProperty("versionCode").toInt()
        versionName = versions.getProperty("versionName")
        // In-app updates come from this repository's GitHub releases (see Updater.kt and release.sh).
        buildConfigField("String", "UPDATE_REPO", "\"M4S4T0-V01D/cosmic-plumber\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
