package com.example.contentstudio.advertising

import com.example.contentstudio.BuildConfig

/** Signed sample mode works on phones as well as emulators; live activation remains separate. */
internal object AdBuildPolicy {
    val enabled: Boolean get() = BuildConfig.AD_TESTING || BuildConfig.AD_PRODUCTION
    val homeFlow: Boolean get() = BuildConfig.AD_PRODUCTION || BuildConfig.AD_HOME_PREVIEW
    val homeAvailable: Boolean get() = homeAdvertisingAvailable(
        BuildConfig.AD_PRODUCTION, BuildConfig.AD_TESTING, BuildConfig.AD_HOME_PREVIEW,
        BuildConfig.AD_RELEASE_TESTING, BuildConfig.DEBUG,
        android.os.Build.HARDWARE in setOf("ranchu", "goldfish"),
    )
}

internal fun homeAdvertisingAvailable(
    production: Boolean, testing: Boolean, homePreview: Boolean,
    releaseTesting: Boolean, debug: Boolean, emulator: Boolean,
): Boolean = production || (testing && homePreview &&
    ((releaseTesting && !debug) || (debug && emulator)))
