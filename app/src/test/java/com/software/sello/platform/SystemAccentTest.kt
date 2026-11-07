package com.software.sello.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Sistema's source: the phone's accent from Android 12, none before (SELLO-203). */
@RunWith(AndroidJUnit4::class)
class SystemAccentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** [context] with its accent at its middle tone set to [argb], as a phone's style sets it. */
    private fun withAccent(argb: Int): Context = object : ContextWrapper(context) {
        @Suppress("DEPRECATION")
        private val resources = object : Resources(
            context.assets,
            context.resources.displayMetrics,
            context.resources.configuration
        ) {
            override fun getColor(id: Int, theme: Theme?): Int =
                if (id == android.R.color.system_accent1_500) argb else super.getColor(id, theme)
        }

        override fun getResources(): Resources = resources
    }

    @Test
    fun androidElevenHasNoAccent() {
        assertEquals(PhoneColor.None, AndroidSystemAccent(context, Build.VERSION_CODES.R).read())
    }

    @Test
    fun fromAndroidTwelveTheAccentGivesAWholeDegree() {
        for (sdk in listOf(Build.VERSION_CODES.S, Build.VERSION.SDK_INT)) {
            val read = AndroidSystemAccent(context, sdk).read()
            assertTrue("$sdk: $read", read is PhoneColor.Hue && read.degrees in 0..359)
        }
        // sRGB blue is 264.05° in OKLCH.
        assertEquals(
            PhoneColor.Hue(264),
            AndroidSystemAccent(withAccent(0xFF0000FF.toInt()), Build.VERSION_CODES.S).read()
        )
    }

    @Test
    fun aGreyAccentIsStillAnAccentWithNoHue() {
        for (grey in listOf(0xFF808080, 0xFF5E5E5E)) {
            assertEquals(
                PhoneColor.Grey,
                AndroidSystemAccent(withAccent(grey.toInt()), Build.VERSION_CODES.S).read()
            )
        }
        // Android 11 has no accent, whatever its resources say.
        assertEquals(
            PhoneColor.None,
            AndroidSystemAccent(withAccent(0xFF808080.toInt()), Build.VERSION_CODES.R).read()
        )
    }

    @Test
    fun theColourIsReadAtStartAndAgainOnlyWhenAsked() {
        var reads = 0
        var phone: PhoneColor = PhoneColor.Hue(140)
        val color = SystemColor {
            reads++
            phone
        }
        assertEquals(PhoneColor.Hue(140), color.color.value)

        phone = PhoneColor.Grey
        assertEquals(PhoneColor.Hue(140), color.color.value)
        color.refresh()
        assertEquals(PhoneColor.Grey, color.color.value)
        assertEquals(2, reads)
    }
}
