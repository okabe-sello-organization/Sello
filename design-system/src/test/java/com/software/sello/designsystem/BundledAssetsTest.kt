package com.software.sello.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloColors
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.designsystem.theme.selloColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BundledAssetsTest {
    @get:Rule
    val rule = createComposeRule()

    private val resources = InstrumentationRegistry.getInstrumentation().context.resources

    @Test
    fun fontsLoadFromThePackageWithoutNetwork() {
        assertNotNull(resources.getFont(R.font.schibsted_grotesk))
        assertNotNull(resources.getFont(R.font.saira_stencil_one))
    }

    @Test
    fun everyFontAndIconSetShipsItsLicenceText() {
        val licences = mapOf(
            R.raw.license_schibsted_grotesk to "SIL Open Font License",
            R.raw.license_saira_stencil_one to "SIL Open Font License",
            R.raw.license_material_symbols to "Apache License"
        )
        for ((id, name) in licences) {
            val text = resources.openRawResource(id).bufferedReader().use { it.readText() }
            assertTrue(name, text.contains(name))
        }
    }

    @Test
    fun everyIconDrawableInflates() {
        val context = InstrumentationRegistry.getInstrumentation().context
        SelloIcon.entries.forEach { icon ->
            assertNotNull(icon.name, context.getDrawable(icon.filled))
            icon.outlined?.let { assertNotNull(icon.name, context.getDrawable(it)) }
        }
    }

    @Test
    fun themeProvidesTheChosenSchemeToSelloAndMaterialReaders() {
        var sello: SelloColors? = null
        var materialPrimary: Color? = null
        rule.setContent {
            SelloTheme(ink = SelloInk.Violeta, darkTheme = true) {
                sello = SelloTheme.colors
                materialPrimary = MaterialTheme.colorScheme.primary
                Sample()
            }
        }
        rule.onNodeWithText("Al día").assertExists()
        assertEquals(selloColors(SelloInk.Violeta, isDark = true), sello)
        assertEquals(Color(0xFFB79CFF), materialPrimary)
    }

    @Composable
    private fun Sample() {
        Text("Al día", style = SelloTheme.type.stampLarge)
        Text("$ 937.200", style = MaterialTheme.typography.displayLarge)
    }
}
