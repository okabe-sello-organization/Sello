package com.software.sello.platform

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Sistema's source: the phone's accent from Android 12, none before (SELLO-203). */
@RunWith(AndroidJUnit4::class)
class SystemAccentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun androidElevenHasNoAccent() {
        assertNull(AndroidSystemAccent(context, Build.VERSION_CODES.R).hue())
    }

    @Test
    fun fromAndroidTwelveTheAccentGivesAWholeDegree() {
        for (sdk in listOf(Build.VERSION_CODES.S, Build.VERSION.SDK_INT)) {
            val hue = AndroidSystemAccent(context, sdk).hue()
            assertNotNull("$sdk", hue)
            assertTrue("$hue", hue in 0..359)
        }
    }

    @Test
    fun theColourIsReadAtStartAndAgainOnlyWhenAsked() {
        var reads = 0
        var phone: Int? = 140
        val color = SystemColor {
            reads++
            phone
        }
        assertEquals(140, color.hue.value)

        phone = null
        assertEquals(140, color.hue.value)
        color.refresh()
        assertNull(color.hue.value)
        assertEquals(2, reads)
    }
}
