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
import androidx.annotation.RequiresApi
import androidx.core.view.doOnPreDraw
import com.example.contentstudio.R

/** Uses Android's launch window; never adds a second splash or a timed loading delay. */
fun Activity.installStudioSplash(animate: Boolean) {
    // Keep the compat starting-theme resources, but use the platform exit API directly.
    // The compat exit listener reapplies deprecated bar colors and resets icon appearance.
    setTheme(R.style.Theme_ContentStudio)
    val motionEnabled = if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
        else Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    val shouldAnimate = animate && motionEnabled
    if (Build.VERSION.SDK_INT >= 31) {
        installPlatformSplashExit(shouldAnimate)
    } else if (shouldAnimate) {
        // Older Android owns a static launch window. Fade the first content frame instead
        // of adding another splash, blocking a draw, or imposing a loading delay.
        window.decorView.doOnPreDraw {
            val content = findViewById<View>(android.R.id.content)
            content.alpha = 0f
            content.animate().alpha(1f).setDuration(300L).setInterpolator(DecelerateInterpolator()).start()
        }
    }
}

@RequiresApi(31)
private fun Activity.installPlatformSplashExit(animate: Boolean) {
    splashScreen.setOnExitAnimationListener { splash ->
        // The platform splash may temporarily fit the decor to system windows.
        configureStudioWindow(window)
        applyStudioSystemBarAppearance(window, resources.configuration.uiMode)
        if (!animate) splash.remove() else {
            val animation = AnimatorSet().apply {
                val fades = mutableListOf<Animator>(ObjectAnimator.ofFloat(splash, View.ALPHA, 1f, 0f))
                splash.iconView?.let { icon ->
                    fades += ObjectAnimator.ofFloat(icon, View.SCALE_X, 0.94f, 1.02f)
                    fades += ObjectAnimator.ofFloat(icon, View.SCALE_Y, 0.94f, 1.02f)
                }
                playTogether(fades)
                duration = 300L
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { splash.remove() }
                })
            }
            animation.start()
        }
    }
}
