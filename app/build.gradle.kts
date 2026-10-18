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

// Explicit development opt-in. Neither property can activate a release or a live ad unit.
val accountPrivacyTest = providers.gradleProperty("birlevo.adPrivacyAccount").orNull.let {
    require(it == null || it == "true" || it == "false") { "birlevo.adPrivacyAccount must be true or false" }
    it == "true"
}
val privacyTestRegion = providers.gradleProperty("birlevo.adPrivacyRegion").orElse("eea").get()
require(privacyTestRegion in setOf("eea", "us", "other")) {
    "birlevo.adPrivacyRegion must be eea, us or other"
}
val sampleAdApplication = "ca-app-pub-3940256099942544~3347511713"
val accountAdApplication = "ca-app-pub-5967660342681325~8485054661"

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
        getByName("debug") {
            buildConfigField("boolean", "AD_TESTING", "true")
            buildConfigField("boolean", "AD_ACCOUNT_TESTING", accountPrivacyTest.toString())
            buildConfigField("String", "AD_APPLICATION_ID", "\"${if (accountPrivacyTest) accountAdApplication else sampleAdApplication}\"")
            buildConfigField("String", "AD_PRIVACY_REGION", "\"$privacyTestRegion\"")
            manifestPlaceholders["adApplicationId"] = if (accountPrivacyTest) accountAdApplication else sampleAdApplication
        }
        getByName("release") {
            // Production activation requires account messages, disclosure and placement review.
            buildConfigField("boolean", "AD_TESTING", "false")
            buildConfigField("boolean", "AD_ACCOUNT_TESTING", "false")
            buildConfigField("String", "AD_APPLICATION_ID", "\"$sampleAdApplication\"")
            buildConfigField("String", "AD_PRIVACY_REGION", "\"disabled\"")
            manifestPlaceholders["adApplicationId"] = sampleAdApplication
            isMinifyEnabled = true
            isShrinkResources = true
            // Include available native symbols; pre-stripped dependency libraries need upstream symbols.
            ndk.debugSymbolLevel = "FULL"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasUploadKey) signingConfig = signingConfigs.getByName("upload")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
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
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
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
    testImplementation("org.json:json:20260814")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.09.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
}
