plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val uploadKeySettings = listOf(
    "BIRLEVO_UPLOAD_KEYSTORE",
    "BIRLEVO_UPLOAD_STORE_PASSWORD",
    "BIRLEVO_UPLOAD_KEY_ALIAS",
    "BIRLEVO_UPLOAD_KEY_PASSWORD",
).associateWith { System.getenv(it) }
require(uploadKeySettings.values.all { it.isNullOrBlank() } || uploadKeySettings.values.all { !it.isNullOrBlank() }) {
    "Upload signing requires all four BIRLEVO_UPLOAD_* environment variables."
}
val hasUploadKey = uploadKeySettings.values.all { !it.isNullOrBlank() }

android {
    namespace = "com.example.contentstudio"
    compileSdk = 36
    defaultConfig {
        applicationId = "studio.birlevo.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "0.1.8"
        testInstrumentationRunner = if (providers.gradleProperty("birlevo.platformSmoke").orNull == "true")
            "com.example.contentstudio.ReleaseSmokeInstrumentation" else "androidx.test.runner.AndroidJUnitRunner"
    }
    if (hasUploadKey) {
        signingConfigs {
            create("upload") {
                storeFile = file(uploadKeySettings.getValue("BIRLEVO_UPLOAD_KEYSTORE")!!)
                storePassword = uploadKeySettings.getValue("BIRLEVO_UPLOAD_STORE_PASSWORD")!!
                keyAlias = uploadKeySettings.getValue("BIRLEVO_UPLOAD_KEY_ALIAS")!!
                keyPassword = uploadKeySettings.getValue("BIRLEVO_UPLOAD_KEY_PASSWORD")!!
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            // Include available native symbols; pre-stripped dependency libraries need upstream symbols.
            ndk.debugSymbolLevel = "FULL"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasUploadKey) signingConfig = signingConfigs.getByName("upload")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
}
