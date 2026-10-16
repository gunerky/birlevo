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
    ndkVersion = "27.1.12297006"
    namespace = "com.example.contentstudio"
    compileSdk = 36
    defaultConfig {
        applicationId = "studio.birlevo.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "0.1.11"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
        externalNativeBuild { cmake { arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON" } }
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
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }

// Ship exact corresponding encoder source and its Android build/interface files offline.
val mp3Sources by tasks.registering(Zip::class) {
    archiveFileName.set("birlevo-mp3-sources.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/mp3Assets/licenses"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(rootProject.file("third_party/lame")) { into("third_party/lame") }
    from("src/main/cpp") { into("app/src/main/cpp") }
    from("src/main/java/com/example/contentstudio/audio/LameEncoder.java") { into("interface") }
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/mp3Assets"))
tasks.named("preBuild") { dependsOn(mp3Sources) }

// Read-only metadata resolution: this task does not build a release or read signing material.
tasks.register("exportRuntimeLicenseInventory") {
    doLast {
        val lines = configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
            .sortedBy { it.moduleVersion.id.toString() + it.file.name }
            .map { "${it.moduleVersion.id}\t${it.file.absolutePath}" }
            .distinct()
        val report = rootProject.file("output/development/mp3-expansion/runtime-artifacts.tsv")
        report.parentFile.mkdirs(); report.writeText(lines.joinToString("\n", postfix = "\n"))
        println("Resolved ${lines.size} runtime artifacts; no release built.")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-muxer:1.11.1")
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
