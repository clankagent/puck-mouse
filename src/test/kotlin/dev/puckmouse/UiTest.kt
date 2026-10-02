package dev.puckmouse

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.toAwtImage
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class UiTest {
    private fun DesktopComposeUiTest.snapshot(name: String) {
        System.getProperty("puckmouse.screenshots")?.let {
            val directory=Path.of(it);Files.createDirectories(directory)
            ImageIO.write(captureToImage().toAwtImage(),"png",directory.resolve("$name.png").toFile())
        }
    }
    @Test fun radialPreviewShowsArbitraryAnglesAndCanCompareTheOldResponse() = runDesktopComposeUiTest(width=1440,height=1050) {
        AppController(SettingsStore(null),allowDesktopOutput=false).use { c ->
            setContent { PuckMouseApp(c) }
            c.setPreview(true)
            waitUntil(timeoutMillis=5000) { c.state.value.preview }
            val angle=Math.toRadians(30.0)
            c.simulate(Axis.SLIDE_X,.5*kotlin.math.cos(angle))
            c.simulate(Axis.SLIDE_Y,-.5*kotlin.math.sin(angle))
            waitUntil(timeoutMillis=5000) { (c.state.value.outputs[Output.POINTER_Y]?:0.0)>0 }
            onNodeWithTag("direction-preview").performScrollTo().assertIsDisplayed()
            val rate=c.state.value.outputs
            assertEquals(30.0,Math.toDegrees(kotlin.math.atan2(rate[Output.POINTER_Y]!!,rate[Output.POINTER_X]!!)),.01)
            snapshot("natural-pointer-desktop")
            onNodeWithTag("pointer-radial").performScrollTo().performClick()
            waitUntil(timeoutMillis=5000) { !c.state.value.profile.radialPointer }
            onNodeWithTag("direction-preview").performScrollTo()
            snapshot("independent-pointer-desktop")
            onNodeWithTag("natural-pointer-preset").performScrollTo().performClick()
            assertTrue(c.state.value.profile.radialPointer);assertTrue(c.state.value.profile.linkPointerTuning)
            assertEquals(defaultMappings().take(2),c.state.value.profile.mappings.take(2))
            onNodeWithTag("natural-pointer-preset").assertIsNotEnabled()
            onNodeWithTag("nav-settings").performClick()
            onNodeWithTag("hotkey-preset-0").performScrollTo().assertTextEquals("Pause · Recommended")
        }
    }
    @Test fun nativePreviewShowsProcessedRatesAndShortcutCaptureRejectsReservedKeys() = runDesktopComposeUiTest(width=1440,height=900) {
        AppController(SettingsStore(null),allowDesktopOutput=false).use { controller ->
            setContent { PuckMouseApp(controller) }
            controller.setPreview(true)
            waitUntil(timeoutMillis=5000) { controller.state.value.preview }
            controller.simulate(Axis.TWIST,.5)
            waitUntil(timeoutMillis=5000) { (controller.state.value.outputs[Output.SCROLL_Y] ?: 0.0)>0 }
            snapshot("preview-desktop")
            controller.reportError("Input processing paused. Resume when ready, then release the cap to center. Puck timestamps must be monotonic.")
            onNodeWithTag("pause-toggle").assertIsDisplayed()
            snapshot("processing-error-desktop")
            onNodeWithTag("error-dismiss").performClick()
            onNodeWithTag("nav-settings").performClick()
            onNodeWithTag("app-version").performScrollTo().assertIsDisplayed().assertTextEquals("Puck Mouse ${AppVersion.value}")
            snapshot("version-desktop")
            onNodeWithTag("hotkey-record").performScrollTo().performClick()
            onNodeWithTag("hotkey-capture").performKeyInput { pressKey(Key.F12) }
            onNodeWithTag("hotkey-problem").assertExists()
            assertEquals(0x13,controller.state.value.settings.hotkey.key)
            onNodeWithTag("hotkey-capture").performKeyInput {
                keyDown(Key.CtrlLeft);keyDown(Key.ShiftLeft);pressKey(Key.F10);keyUp(Key.ShiftLeft);keyUp(Key.CtrlLeft)
            }
            assertEquals(Hotkey(key=0x79,ctrl=true,alt=false,shift=true),controller.state.value.settings.hotkey)
        }
    }
    @Test fun mappingControlsChangeRealSettingsAndPreviewCanBeCentered() = runDesktopComposeUiTest(width=1440,height=900) {
        AppController(SettingsStore(null),native=false).use { controller ->
            setContent { PuckMouseApp(controller) }
            onNodeWithTag("pause-toggle").assertIsDisplayed().performClick()
            assertFalse(controller.state.value.paused)
            onNodeWithTag("mapping-SCROLL_Y").performClick()
            onNodeWithTag("source-selector").performClick()
            onNodeWithTag("source-TILT_X").performClick()
            assertEquals(Axis.TILT_X,controller.state.value.profile.mappings[2].axis)
            onNodeWithTag("invert-toggle").performScrollTo().performClick()
            assertTrue(controller.state.value.profile.mappings[2].inverted)
            onNodeWithTag("speed-slider").performSemanticsAction(SemanticsActions.SetProgress){it(18f)}
            assertEquals(18.0,controller.state.value.profile.mappings[2].speed)
            onNodeWithTag("advanced-toggle").performScrollTo().performClick()
            onNodeWithTag("deadzone-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(.15f)}
            assertEquals(.15,controller.state.value.profile.mappings[2].deadzone)
            onNodeWithTag("gentle-scroll-preset").performScrollTo().performClick()
            assertEquals(.14,controller.state.value.profile.mappings[2].deadzone)
            assertEquals(1.7,controller.state.value.profile.mappings[2].curve)
            assertEquals(18.0,controller.state.value.profile.mappings[2].speed)
            assertEquals(Axis.TILT_X,controller.state.value.profile.mappings[2].axis)
            assertTrue(controller.state.value.profile.mappings[2].inverted)
            onNodeWithTag("gentle-scroll-preset").assertIsNotEnabled()
            snapshot("gentle-scroll-desktop")
            onNodeWithTag("deadzone-slider").performScrollTo()
            snapshot("mapping-wide")
            onNodeWithTag("preview-toggle").performClick()
            onNodeWithTag("preview-axis-TILT_X").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(.6f)}
            assertEquals(.6,controller.state.value.axes.rx,.001)
            onNodeWithTag("preview-center").performScrollTo().performClick()
            assertTrue(controller.state.value.axes.neutral())
        }
    }
    @Test fun profileDialogsSupportKeyboardAndPreserveIndependentSettings() = runDesktopComposeUiTest(width=1160,height=800) {
        AppController(SettingsStore(null),native=false).use { controller ->
            setContent { PuckMouseApp(controller) }
            controller.setPreview(true)
            controller.simulate(Axis.SLIDE_X,.43);controller.simulate(Axis.SLIDE_Y,-.25)
            onNodeWithTag("direction-preview").performScrollTo().assertIsDisplayed()
            onNodeWithTag("pause-toggle").assertIsDisplayed()
            snapshot("natural-pointer-medium")
            controller.setPreview(false)
            onNodeWithTag("nav-profiles").performClick()
            onNodeWithTag("profile-duplicate").performClick()
            onNodeWithTag("profile-name-field").performTextReplacement("Reading")
            onNodeWithTag("profile-name-field").performKeyInput { pressKey(Key.Enter) }
            assertEquals(2,controller.state.value.settings.profiles.size)
            assertEquals("Reading",controller.state.value.profile.name)
            onNodeWithTag("profile-rename").performClick()
            onNodeWithTag("profile-name-field").performTextReplacement("Cancelled")
            onNodeWithTag("profile-name-field").performKeyInput { pressKey(Key.Escape) }
            assertEquals("Reading",controller.state.value.profile.name)
            onNodeWithTag("name-dialog").assertDoesNotExist()
            snapshot("profiles-desktop")
            onNodeWithTag("profile-delete").performClick()
            onNodeWithTag("profile-confirm").performClick()
            assertEquals(1,controller.state.value.settings.profiles.size)
        }
    }
    @Test fun narrowLayoutKeepsPauseAndNavigationVisible() = runDesktopComposeUiTest(width=390,height=844) {
        AppController(SettingsStore(null),native=false).use { controller ->
            setContent { PuckMouseApp(controller) }
            onNodeWithTag("pause-toggle").assertIsDisplayed()
            controller.reportError("Input processing paused. Resume when ready, then release the cap to center. Puck timestamps must be monotonic.")
            snapshot("processing-error-compact")
            onNodeWithTag("error-dismiss").performClick()
            onNodeWithTag("nav-buttons").assertIsDisplayed().performClick()
            onNodeWithTag("button2-action").performScrollTo().performClick()
            onNodeWithTag("button2-PAUSE_HOLD").performClick()
            assertEquals(ButtonAction.PAUSE_HOLD,controller.state.value.profile.button2)
            snapshot("buttons-compact")
            onNodeWithTag("nav-mappings").performClick()
            onNodeWithTag("pointer-radial").performScrollTo().assertIsDisplayed().performClick()
            assertFalse(controller.state.value.profile.radialPointer)
            onNodeWithTag("natural-pointer-preset").performScrollTo().performClick()
            assertTrue(controller.state.value.profile.radialPointer)
            controller.setPreview(true)
            controller.simulate(Axis.SLIDE_X,.43);controller.simulate(Axis.SLIDE_Y,-.25)
            snapshot("natural-pointer-compact")
            onNodeWithTag("direction-preview").performScrollTo().assertIsDisplayed()
            onNodeWithTag("pause-toggle").assertIsDisplayed()
            snapshot("pointer-direction-compact")
            onNodeWithTag("mapping-SCROLL_Y").performScrollTo().performClick()
            controller.updateMapping(controller.state.value.profile.mappings[2].copy(deadzone=.2,curve=3.0))
            onNodeWithTag("link-tuning").performScrollTo().performClick()
            onNodeWithTag("gentle-scroll-preset").performScrollTo().performClick()
            assertTrue(controller.state.value.profile.linkScrollTuning)
            for(m in controller.state.value.profile.mappings.filter{it.output.name.startsWith("SCROLL")}) {
                assertEquals(.14,m.deadzone);assertEquals(1.7,m.curve)
            }
            assertFalse(controller.state.value.profile.mappings[3].enabled)
            snapshot("gentle-scroll-compact")
            onNodeWithTag("nav-settings").performClick()
            onNodeWithTag("hotkey-preset-2").performScrollTo().performClick()
            assertEquals(Hotkey(key=0x78,ctrl=true,alt=false,shift=true),controller.state.value.settings.hotkey)
            onNodeWithTag("pause-toggle").assertIsDisplayed()
            onNodeWithTag("app-version").performScrollTo().assertIsDisplayed().assertTextEquals("Puck Mouse ${AppVersion.value}")
            snapshot("settings-compact")
        }
    }
    @Test fun linkedControlsShareExpandedTuningAndPresetKeepsDirection() = runDesktopComposeUiTest(width=1440,height=1050) {
        AppController(SettingsStore(null),native=false).use { c ->
            c.setTuningLinked(Output.POINTER_X,false)
            setContent { PuckMouseApp(c) }
            onNodeWithTag("link-tuning").performClick()
            onNodeWithTag("advanced-toggle").performScrollTo().performClick()
            onNodeWithTag("curve-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(8f)}
            onNodeWithTag("full-speed-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(.55f)}
            assertEquals(8.0,c.state.value.profile.mappings[1].curve)
            assertEquals(.55,c.state.value.profile.mappings[1].fullSpeedAt)
            assertTrue(c.state.value.profile.mappings[1].inverted)
            snapshot("linked-curve-desktop")
            onNodeWithTag("mapping-POINTER_Y").performClick()
            onNodeWithTag("natural-pointer-preset").performScrollTo().performClick()
            assertEquals(defaultMappings().take(2),c.state.value.profile.mappings.take(2))
            onNodeWithTag("natural-pointer-preset").assertIsNotEnabled()
            onNodeWithTag("link-tuning").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus){it()}
            onNodeWithTag("link-tuning").performKeyInput { pressKey(Key.Spacebar) }
            assertFalse(c.state.value.profile.linkPointerTuning)
            onNodeWithTag("advanced-toggle").performScrollTo().performClick()
            snapshot("pointer-default-desktop")
        }
    }
    @Test fun compactCurveControlsRemainAccessibleAndMaintainValidKnee() = runDesktopComposeUiTest(width=390,height=844) {
        AppController(SettingsStore(null),native=false).use { c ->
            c.setTuningLinked(Output.POINTER_X,false)
            setContent { PuckMouseApp(c) }
            onNodeWithTag("link-tuning").performScrollTo().performClick()
            snapshot("linked-compact")
            onNodeWithTag("advanced-toggle").performScrollTo().performClick()
            onNodeWithTag("full-speed-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(.1f)}
            onNodeWithTag("deadzone-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(.5f)}
            assertEquals(.55,c.state.value.profile.mappings[0].fullSpeedAt)
            onNodeWithTag("curve-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(12f)}
            snapshot("curve-controls-compact")
            onNodeWithTag("pause-toggle").assertIsDisplayed()
            assertNull(c.state.value.error)
        }
    }
}
