package net.sudoer.nipo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.sudoer.nipo.ui.nd.NdBoxInput
import net.sudoer.nipo.ui.nd.NdGrid
import net.sudoer.nipo.ui.nd.NdIconButton
import net.sudoer.nipo.ui.nd.NdToggleBox
import net.sudoer.nipo.ui.theme.NothingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What TalkBack and touch see for the Nd controls (HIG accessibility: labels, roles, 44pt targets). */
@RunWith(AndroidJUnit4::class)
class NdAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun iconButtonIsLabelledAndFullSize() {
        rule.setContent { NothingTheme { NdIconButton(Icons.Filled.Add, "Add profile", {}) } }
        rule.onNodeWithContentDescription("Add profile")
            .assertWidthIsAtLeast(44.dp)
            .assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun toggleBoxIsASwitch() {
        var on by mutableStateOf(false)
        rule.setContent { NothingTheme { NdToggleBox("AES", on, { on = it }) } }
        rule.onNode(isToggleable())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .performClick()
            .assertIsOn()
    }

    @Test
    fun boxInputCarriesItsLabel() {
        rule.setContent { NothingTheme { NdBoxInput("Buffer · B", "65536", {}) } }
        rule.onNode(hasSetTextAction())
            .assert(hasText("BUFFER · B", substring = true))
            .assert(hasText("65536"))
    }

    @Test
    fun gridCellsShareOneHeight() {
        rule.setContent {
            NothingTheme {
                NdGrid(
                    4,
                    { m -> NdToggleBox("Tunnel", false, {}, m) },
                    { m -> NdToggleBox("Reuse", true, {}, m) },
                    { m -> NdToggleBox("TLS", true, {}, m) },
                    { m -> NdToggleBox("AES", true, {}, m) },
                )
            }
        }
        val heights = rule.onAllNodes(isToggleable()).fetchSemanticsNodes().map { it.boundsInRoot.height }
        assertEquals(4, heights.size)
        assertEquals(1, heights.distinct().size)
    }

    // The two tests below pin text to 100% so NdGrid keeps every column (4-up, 2-up),
    // the layout most phones get; the device's own font scale is left alone.
    @Test
    fun fourUpTogglesShareOneHeightAtDefaultTextSize() {
        rule.setContent {
            DefaultTextSize {
                NdGrid(
                    4,
                    { m -> NdToggleBox("Tunnel", false, {}, m) },
                    { m -> NdToggleBox("Reuse", true, {}, m) },
                    { m -> NdToggleBox("TLS", true, {}, m) },
                    { m -> NdToggleBox("AES", true, {}, m) },
                )
            }
        }
        val bounds = rule.onAllNodes(isToggleable()).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals("all four on one row", 1, bounds.map { it.top }.distinct().size)
        assertEquals(1, bounds.map { it.height }.distinct().size)
    }

    @Test
    fun sideBySideMultilineFieldsShareOneHeight() {
        rule.setContent {
            DefaultTextSize {
                NdGrid(
                    2,
                    { m -> NdBoxInput("Fake URLs", "a.com\nb.com\nc.com\nd.com\ne.com", {}, multiline = true, rows = 3, modifier = m) },
                    { m -> NdBoxInput("Endpoints", "api", {}, multiline = true, rows = 3, modifier = m) },
                )
            }
        }
        val bounds = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals("side by side", 1, bounds.map { it.top }.distinct().size)
        assertEquals(1, bounds.map { it.height }.distinct().size)
    }

    @Composable
    private fun DefaultTextSize(content: @Composable () -> Unit) = NothingTheme {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1f), content = content)
    }
}
