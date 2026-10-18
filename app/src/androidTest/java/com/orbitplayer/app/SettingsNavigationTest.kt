package com.orbitplayer.app

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun views(activity: SettingsActivity) = all(activity.window.decorView)
    private fun title(activity: SettingsActivity, text: String) = views(activity)
        .filterIsInstance<TextView>().first { it.text.toString() == text }

    @Test fun searchFindsPreferencesPersistsQueryAndSavesRealToggle() {
        val settings = OrbitSettings(InstrumentationRegistry.getInstrumentation().targetContext)
        val original = settings.resume
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse(activity.findViewById<EditText>(R.id.settingsSearch).hasFocus())
                    activity.findViewById<View>(R.id.btnSettingsSearch).performClick()
                    activity.findViewById<EditText>(R.id.settingsSearch).setText("subtitle")
                    assertTrue(title(activity, "Subtitle text size").isShown)
                    assertFalse(title(activity, "Resume playback").isShown)
                    activity.findViewById<EditText>(R.id.settingsSearch).setText("zz_no_setting_matches")
                    assertTrue(title(activity, activity.getString(R.string.no_settings_match)).isShown)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals("zz_no_setting_matches", activity.findViewById<EditText>(R.id.settingsSearch).text.toString())
                    assertTrue(title(activity, activity.getString(R.string.no_settings_match)).isShown)
                    activity.findViewById<EditText>(R.id.settingsSearch).setText("resume")
                    val toggle = views(activity).filterIsInstance<Switch>().first { it.contentDescription.toString().startsWith("Resume playback,") }
                    toggle.performClick()
                    assertEquals(!original, settings.resume)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val toggle = views(activity).filterIsInstance<Switch>().first { it.contentDescription.toString().startsWith("Resume playback,") }
                    assertEquals(!original, toggle.isChecked)
                    assertEquals("resume", activity.findViewById<EditText>(R.id.settingsSearch).text.toString())
                }
            }
        } finally { settings.resume = original }
    }

    @Test fun categorySurvivesRecreationAndBackRemainsReachableAfterScrolling() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                views(activity).first { it.contentDescription == "Playback settings" }.performClick()
                assertTrue(title(activity, "Autoplay").isShown)
                assertFalse(title(activity, "Swipe for volume").isShown)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(title(activity, "Autoplay").isShown)
                assertFalse(title(activity, "Swipe for volume").isShown)
                activity.findViewById<ScrollView>(R.id.settingsScroll).fullScroll(View.FOCUS_DOWN)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val back = views(activity).first { it.contentDescription == "Back to library" }
                assertTrue("Sticky back control went offscreen", back.getGlobalVisibleRect(android.graphics.Rect()))
                back.performClick()
            }
        }
    }
}
