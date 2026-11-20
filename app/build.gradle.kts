import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The release key: from keystore.properties beside this project (never committed), or from the
// environment, as .github/workflows/release.yml sets it from the repository's secrets.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signing(prop: String, env: String): String? = keystoreProps.getProperty(prop) ?: System.getenv(env)

android {
    namespace = "com.omrscanner.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.omrscanner.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            signing("storeFile", "OMR_KEYSTORE_FILE")?.let { path ->
                storeFile = file(path)
                storePassword = signing("storePassword", "OMR_KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "OMR_KEY_ALIAS")
                keyPassword = signing("keyPassword", "OMR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed when the release key is there; otherwise the APK comes out unsigned.
            signingConfigs.getByName("release").takeIf { it.storeFile != null }?.let { signingConfig = it }
            // Phones only: OpenCV's emulator (x86) libraries would double the download.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)

    // Storage
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Camera + vision
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.opencv)
    implementation(libs.mlkit.document.scanner)

    // Question bank API
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
}
