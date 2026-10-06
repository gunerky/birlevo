package com.example.contentstudio.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/** Uses Android's launch window; never adds a second splash or a timed loading delay. */
fun Activity.installStudioSplash(animate: Boolean) {
    val splash = installSplashScreen()
    splash.setOnExitAnimationListener { provider ->
        val motionEnabled = if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
        else Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        if (!animate || !motionEnabled) {
            provider.remove()
        } else {
            // Some Android launches provide a splash without an icon. The compat getter
            // assumes it exists; keep the window fade working for those launches too.
            val icon = try { provider.iconView } catch (_: NullPointerException) { null }
            val animation = AnimatorSet().apply {
                val fades = mutableListOf<Animator>(ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f))
                if (icon != null) {
                    fades += ObjectAnimator.ofFloat(icon, View.SCALE_X, 0.94f, 1.02f)
                    fades += ObjectAnimator.ofFloat(icon, View.SCALE_Y, 0.94f, 1.02f)
                }
                playTogether(fades)
                duration = 300L
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { provider.remove() }
                })
            }
            animation.start()
        }
    }
}
