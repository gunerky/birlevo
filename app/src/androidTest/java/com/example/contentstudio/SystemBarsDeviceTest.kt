package com.example.contentstudio

import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SystemBarsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun contentReachesWindowEdgesAndControlsAndDialogRemainUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(20000) { compose.onAllNodesWithTag("choose-language").fetchSemanticsNodes().isNotEmpty() }
            var statusTop = 0; var navigationBottom = 0; var windowHeight = 0
            var safeLeft = 0; var safeRight = 0; var windowWidth = 0
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                val insets = ViewCompat.getRootWindowInsets(decor)!!
                statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                windowHeight = decor.height
                windowWidth = decor.width
                val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                safeLeft = safe.left; safeRight = safe.right
                val content = activity.findViewById<android.view.View>(android.R.id.content)
                val location = IntArray(2); content.getLocationInWindow(location)
                assertEquals("Content still inset at top", 0, location[1])
                assertEquals("Content still inset at bottom", windowHeight, content.height)
                if (Build.VERSION.SDK_INT >= 30) assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                    activity.window.attributes.layoutInDisplayCutoutMode)
                val dark = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                val controller = WindowCompat.getInsetsController(activity.window, decor)
                assertEquals(!dark, controller.isAppearanceLightStatusBars)
                if (Build.VERSION.SDK_INT >= 26) assertEquals(!dark, controller.isAppearanceLightNavigationBars)
            }
            val language = compose.onNodeWithTag("choose-language").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
            assertTrue(language.top >= statusTop); assertTrue(language.bottom <= windowHeight-navigationBottom)
            assertTrue(language.left >= safeLeft); assertTrue(language.right <= windowWidth-safeRight)
            compose.onNodeWithTag("choose-language").performClick()
            compose.onNodeWithText(context.getString(R.string.close)).assertIsDisplayed()
            instrumentation.uiAutomation.waitForIdle(400, 3000)
            val image = instrumentation.uiAutomation.takeScreenshot()
            try { val folder = File(context.getExternalFilesDir(null),"qa").apply { mkdirs() }
                File(folder,"system-bars.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            } finally { image.recycle() }
            compose.onNodeWithText(context.getString(R.string.close)).performClick()
            compose.onNodeWithTag("choose-language").assertIsDisplayed()
        }
    }
}
