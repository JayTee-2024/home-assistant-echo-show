import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "net.badacid.homeassist.echoshow"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.badacid.homeassist.echoshow"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The Echo Show 8 (1st gen) runs 32-bit ARM only; the WebRTC library's other native builds would
        // make the APK four times the size.
        ndk { abiFilters += listOf("armeabi-v7a") }
    }

    buildFeatures {
        buildConfig = true
    }

    // One key signs every build, debug and release, so an update never needs an uninstall (which would wipe
    // the sign-in, layout and PIN). The key is not in the repository: scripts/make-signing-key.sh makes it in
    // signing/, which git ignores. Without it, builds still work, signed with this machine's debug key.
    val signingFile = rootProject.file("signing/signing.properties")
    val signing = Properties()
    if (signingFile.exists()) signingFile.inputStream().use { signing.load(it) }
    if (signingFile.exists()) {
        signingConfigs.create("release") {
            storeFile = rootProject.file("signing/" + signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    } else {
        logger.warn("No signing/signing.properties: using the debug key. Such a build cannot update a properly signed one.")
    }
    buildTypes {
        val key = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        getByName("debug") { signingConfig = key }
        getByName("release") {
            signingConfig = key
            // Not shrunk or renamed: a sideloaded app's crash report should stay readable.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["test"].resources.srcDir("../fixtures")

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.useJUnitPlatform() }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.webrtc)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    // Views on the JVM, and screenshots of them (Robolectric runs through JUnit 4 on the vintage engine).
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage)
    testRuntimeOnly(libs.junit.launcher)

    // On-device tests only (the keystore); the app itself uses no AndroidX library.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
