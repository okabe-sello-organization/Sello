package com.software.sello

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class LauncherIdentityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun launcher(name: String): ActivityInfo {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)
        return context.packageManager.queryIntentActivities(intent, 0)
            .single { it.activityInfo.name == name }
            .activityInfo
    }

    private fun artwork(activity: ActivityInfo, themed: Boolean): Bitmap {
        val resources = context.packageManager.getResourcesForApplication(activity.applicationInfo)
        var drawableId = activity.iconResource
        if (themed) {
            resources.getXml(drawableId).use { xml ->
                var monochrome = 0
                while (xml.next() != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.START_TAG && xml.name == "monochrome") {
                        monochrome = xml.getAttributeResourceValue(
                            "http://schemas.android.com/apk/res/android",
                            "drawable",
                            0
                        )
                    }
                }
                assertTrue("Each launcher needs a themed silhouette", monochrome != 0)
                drawableId = monochrome
            }
        }
        val drawable = checkNotNull(resources.getDrawable(drawableId, context.theme))
        return Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
        }
    }

    private fun distinct(themed: Boolean) {
        val main = artwork(launcher("com.software.sello.MainActivity"), themed)
        val tools = artwork(launcher("com.software.sello.devtools.ToolsActivity"), themed)
        assertFalse("Sello and Herramientas must not share launcher artwork", main.sameAs(tools))
        val suffix = if (themed) "-themed" else ""
        listOf("main" to main, "tools" to tools).forEach { (name, bitmap) ->
            File(context.filesDir, "launcher-$name$suffix.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test
    fun theDebugBuildIsItsOwnAppNamedSelloDebug() {
        assertEquals("com.software.sello.debug", context.packageName)
        assertEquals(
            "Sello Debug",
            context.applicationInfo.loadLabel(context.packageManager).toString()
        )
    }

    @Test fun theTwoLaunchersHaveDifferentIcons() = distinct(themed = false)

    @Test fun themedSilhouettesStayDifferentWithoutColour() = distinct(themed = true)
}
