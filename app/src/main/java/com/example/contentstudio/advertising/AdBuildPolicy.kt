package com.example.contentstudio.advertising

import com.example.contentstudio.BuildConfig

/** Test flags never activate production. The live switch stays closed pending account/release gates. */
internal object AdBuildPolicy {
    val enabled: Boolean get() = BuildConfig.AD_TESTING || BuildConfig.AD_PRODUCTION
    val homeFlow: Boolean get() = BuildConfig.AD_PRODUCTION || BuildConfig.AD_HOME_PREVIEW
    val homeAvailable: Boolean get() = BuildConfig.AD_PRODUCTION ||
        (BuildConfig.DEBUG && BuildConfig.AD_TESTING && BuildConfig.AD_HOME_PREVIEW &&
            android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
}
