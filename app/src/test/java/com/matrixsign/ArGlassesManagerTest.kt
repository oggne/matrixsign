package com.matrixsign

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for AR Glasses Manager
 * Tests display selection and transparent mode flags without requiring real AR hardware
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class ArGlassesManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockDisplayManager: DisplayManager

    @Mock
    private lateinit var mockDefaultDisplay: Display

    @Mock
    private lateinit var mockExternalDisplay: Display

    private lateinit var arGlassesManager: ArGlassesManager

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        mockDisplayManager = mock(DisplayManager::class.java)
        mockDefaultDisplay = mock(Display::class.java)
        mockExternalDisplay = mock(Display::class.java)

        `when`(mockContext.getSystemService(Context.DISPLAY_SERVICE))
            .thenReturn(mockDisplayManager)
        
        `when`(mockDefaultDisplay.displayId).thenReturn(Display.DEFAULT_DISPLAY)
        `when`(mockExternalDisplay.displayId).thenReturn(1)
        `when`(mockExternalDisplay.name).thenReturn("External Display")
    }

    @Test
    fun testInitialState_noExternalDisplay_notConnected() = runTest {
        // Given: only default display available
        `when`(mockDisplayManager.displays).thenReturn(arrayOf(mockDefaultDisplay))
        
        // When: ArGlassesManager is created
        arGlassesManager = ArGlassesManager(mockContext)

        // Then: should not be connected
        assertFalse(arGlassesManager.isConnected.first())
    }

    @Test
    fun testInitialState_externalDisplayPresent_connected() = runTest {
        // Given: external display is present
        `when`(mockDisplayManager.displays).thenReturn(
            arrayOf(mockDefaultDisplay, mockExternalDisplay)
        )
        
        // When: ArGlassesManager is created
        arGlassesManager = ArGlassesManager(mockContext)

        // Then: should be connected
        assertTrue(arGlassesManager.isConnected.first())
    }

    @Test
    fun testConnect_externalDisplayAvailable_setsConnected() = runTest {
        // Given: external display is available
        `when`(mockDisplayManager.displays).thenReturn(
            arrayOf(mockDefaultDisplay, mockExternalDisplay)
        )
        arGlassesManager = ArGlassesManager(mockContext)

        // When: connect is called
        arGlassesManager.connect()

        // Then: should be connected
        assertTrue(arGlassesManager.isConnected.first())
    }

    @Test
    fun testConnect_noExternalDisplay_notConnected() = runTest {
        // Given: no external display
        `when`(mockDisplayManager.displays).thenReturn(arrayOf(mockDefaultDisplay))
        arGlassesManager = ArGlassesManager(mockContext)

        // When: connect is called
        arGlassesManager.connect()

        // Then: should not be connected
        assertFalse(arGlassesManager.isConnected.first())
    }

    @Test
    fun testTransparentMode_initiallyDisabled() = runTest {
        // Given: ArGlassesManager
        `when`(mockDisplayManager.displays).thenReturn(arrayOf(mockDefaultDisplay))
        arGlassesManager = ArGlassesManager(mockContext)

        // Then: transparent mode should be disabled initially
        assertFalse(arGlassesManager.transparentModeEnabled.first())
    }

    @Test
    fun testStartTransparentMode_connected_enablesMode() = runTest {
        // Given: connected ArGlassesManager
        `when`(mockDisplayManager.displays).thenReturn(
            arrayOf(mockDefaultDisplay, mockExternalDisplay)
        )
        arGlassesManager = ArGlassesManager(mockContext)

        // When: transparent mode is started
        arGlassesManager.startTransparentMode()

        // Then: transparent mode should be enabled
        assertTrue(arGlassesManager.transparentModeEnabled.first())
    }

    @Test
    fun testStopTransparentMode_disablesMode() = runTest {
        // Given: ArGlassesManager with transparent mode enabled
        `when`(mockDisplayManager.displays).thenReturn(
            arrayOf(mockDefaultDisplay, mockExternalDisplay)
        )
        arGlassesManager = ArGlassesManager(mockContext)
        arGlassesManager.startTransparentMode()

        // When: transparent mode is stopped
        arGlassesManager.stopTransparentMode()

        // Then: transparent mode should be disabled
        assertFalse(arGlassesManager.transparentModeEnabled.first())
    }

    @Test
    fun testDisconnect_resetsState() = runTest {
        // Given: connected ArGlassesManager with transparent mode
        `when`(mockDisplayManager.displays).thenReturn(
            arrayOf(mockDefaultDisplay, mockExternalDisplay)
        )
        arGlassesManager = ArGlassesManager(mockContext)
        arGlassesManager.startTransparentMode()

        // When: disconnect is called
        arGlassesManager.disconnect()

        // Then: state should be reset
        assertFalse(arGlassesManager.isConnected.first())
        assertFalse(arGlassesManager.transparentModeEnabled.first())
    }

    @Test
    fun testIsUsingArCamera_connected_returnsTrue() {
        // Given: connected ArGlassesManager
        `when`(mockDisplayManager.displays).thenReturn(
            arrayOf(mockDefaultDisplay, mockExternalDisplay)
        )
        arGlassesManager = ArGlassesManager(mockContext)

        // When: checking if using AR camera
        val result = arGlassesManager.isUsingArCamera()

        // Then: should return true (connected and device type is not NONE)
        assertTrue(result)
    }

    @Test
    fun testIsUsingArCamera_notConnected_returnsFalse() {
        // Given: not connected ArGlassesManager
        `when`(mockDisplayManager.displays).thenReturn(arrayOf(mockDefaultDisplay))
        arGlassesManager = ArGlassesManager(mockContext)

        // When: checking if using AR camera
        val result = arGlassesManager.isUsingArCamera()

        // Then: should return false
        assertFalse(result)
    }

    @Test
    fun testIsExternalDisplayAvailable_withExternalDisplay_returnsTrue() {
        // Given: external display is present
        `when`(mockDisplayManager.displays).thenReturn(
            arrayOf(mockDefaultDisplay, mockExternalDisplay)
        )
        arGlassesManager = ArGlassesManager(mockContext)

        // When: checking if external display is available
        val result = arGlassesManager.isExternalDisplayAvailable()

        // Then: should return true
        assertTrue(result)
    }

    @Test
    fun testIsExternalDisplayAvailable_noExternalDisplay_returnsFalse() {
        // Given: no external display
        `when`(mockDisplayManager.displays).thenReturn(arrayOf(mockDefaultDisplay))
        arGlassesManager = ArGlassesManager(mockContext)

        // When: checking if external display is available
        val result = arGlassesManager.isExternalDisplayAvailable()

        // Then: should return false
        assertFalse(result)
    }
}
