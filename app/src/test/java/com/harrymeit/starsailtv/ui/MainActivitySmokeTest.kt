package com.harrymeit.starsailtv.ui

import android.view.View
import com.harrymeit.starsailtv.MainActivity
import com.harrymeit.starsailtv.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35], qualifiers = "w960dp-h540dp-land-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivitySmokeTest {
    @Test fun coldLaunchAndBackWorkOnOldAndNewAndroid() {
        val context = RuntimeEnvironment.getApplication()
        Prefs(context).apply { serverUrl = ""; lastUpdateCheck = System.currentTimeMillis() }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertEquals("星帆 TV", activity.applicationInfo.loadLabel(activity.packageManager).toString())
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(activity.window.decorView.findViewWithTag<View>("nav:discover"))
        assertTrue(activity.isFinishing)
        controller.pause().stop().destroy()
    }
}
