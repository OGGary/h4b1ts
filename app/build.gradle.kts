import java.util.Properties

/**
 * Signing credentials live outside the repo, in keystore.properties next to
 * settings.gradle.kts. See keystore.properties.template.
 *
 * Absent on purpose in a fresh clone: without it the release build is simply
 * unsigned rather than failing, so anyone can still build and run the app
 * without holding the key. Only the person shipping needs it.
 */
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}

/** Falls back to the environment so a CI runner can supply the same values. */
fun signing(key: String, env: String): String? =
    keystore.getProperty(key) ?: System.getenv(env)

val hasSigning = signing("storeFile", "H4B1TS_STORE_FILE") != null

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.h4b1ts.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.h4b1ts.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.4.0"
    }

    signingConfigs {
        create("release") {
            if (hasSigning) {
                storeFile = file(signing("storeFile", "H4B1TS_STORE_FILE")!!)
                storePassword = signing("storePassword", "H4B1TS_STORE_PASSWORD")
                keyAlias = signing("keyAlias", "H4B1TS_KEY_ALIAS")
                keyPassword = signing("keyPassword", "H4B1TS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Null rather than the config when there is no key: an unsigned
            // artifact is an obvious failure, whereas a release silently signed
            // with the debug key is one that installs fine and can never be
            // updated on Play.
            signingConfig = if (hasSigning) signingConfigs.getByName("release") else null
            // Enum constant names are persisted by the JSON stores, so R8 must
            // not rename them; see proguard-rules.pro. Resource shrinking is a
            // separate switch and stays off for now - one change at a time.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
        buildConfig = true
    }

    /**
     * Two ways of shipping the same app, differing in one capability.
     *
     * `full` keeps the accessibility service: it detects a foreground app in
     * tens of milliseconds, and Android restarts it by itself when a ROM kills
     * it. That is the better product, and it is the one to sideload.
     *
     * `play` leaves it out entirely and relies on usage statistics. Google
     * reserves the accessibility API for tools that assist users with
     * disabilities — an app blocker does not qualify — and Android 17's
     * Advanced Protection Mode revokes the permission from apps that are not
     * accessibility tools, including ones where the user already granted it.
     * A build that never asks for it cannot have it taken away, and has far
     * less to declare at review.
     *
     * Same applicationId on purpose: these are two builds of one app, not two
     * apps. Only one can be installed at a time.
     */
    flavorDimensions += "distribution"

    productFlavors {
        create("full") {
            dimension = "distribution"
            buildConfigField("boolean", "USES_ACCESSIBILITY", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "USES_ACCESSIBILITY", "false")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    // Reads the EXIF orientation tag BitmapFactory ignores.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // The android.jar the unit tests compile against stubs org.json out to
    // throw. The stores are built on it, so without the real thing their
    // persistence is the one part that cannot be tested off-device.
    testImplementation("org.json:json:20231013")
}
