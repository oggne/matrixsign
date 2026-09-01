package com.matrixsign

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
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
 * Unit tests for Mudra Manager
 * Tests BLE connection state machine without requiring real hardware
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class MudraManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockBluetoothManager: BluetoothManager

    @Mock
    private lateinit var mockBluetoothAdapter: BluetoothAdapter

    private lateinit var mudraManager: MudraManager

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        mockBluetoothManager = mock(BluetoothManager::class.java)
        mockBluetoothAdapter = mock(BluetoothAdapter::class.java)

        `when`(mockContext.getSystemService(Context.BLUETOOTH_SERVICE))
            .thenReturn(mockBluetoothManager)
        `when`(mockBluetoothManager.adapter).thenReturn(mockBluetoothAdapter)
    }

    @Test
    fun testInitialState_isDisconnected() = runTest {
        // Given: newly created MudraManager
        mudraManager = MudraManager(mockContext)

        // Then: initial state should be disconnected
        assertEquals(MudraManager.ConnectionState.DISCONNECTED, mudraManager.connectionState.first())
        assertFalse(mudraManager.isConnected.first())
    }

    @Test
    fun testBluetoothEnabled_returnsTrue() {
        // Given: Bluetooth is enabled
        `when`(mockBluetoothAdapter.isEnabled).thenReturn(true)
        mudraManager = MudraManager(mockContext)

        // When: checking if Bluetooth is enabled
        val result = mudraManager.isBluetoothEnabled()

        // Then: should return true
        assertTrue(result)
    }

    @Test
    fun testBluetoothDisabled_returnsFalse() {
        // Given: Bluetooth is disabled
        `when`(mockBluetoothAdapter.isEnabled).thenReturn(false)
        mudraManager = MudraManager(mockContext)

        // When: checking if Bluetooth is enabled
        val result = mudraManager.isBluetoothEnabled()

        // Then: should return false
        assertFalse(result)
    }

    @Test
    fun testProcessMudraGesture_T9Confirm_triggersCallback() {
        // Given: MudraManager with T9 confirm callback
        mudraManager = MudraManager(mockContext)
        var callbackTriggered = false
        mudraManager.setT9ConfirmCallback {
            callbackTriggered = true
        }

        // When: T9_CONFIRM gesture is processed
        mudraManager.processMudraGesture(MudraManager.T9_CONFIRM_GESTURE)

        // Then: callback should be triggered
        assertTrue(callbackTriggered)
    }

    @Test
    fun testProcessMudraGesture_normalGesture_doesNotTriggerCallback() {
        // Given: MudraManager with T9 confirm callback
        mudraManager = MudraManager(mockContext)
        var callbackTriggered = false
        mudraManager.setT9ConfirmCallback {
            callbackTriggered = true
        }

        // When: normal gesture is processed
        mudraManager.processMudraGesture("SOME_OTHER_GESTURE")

        // Then: callback should not be triggered
        assertFalse(callbackTriggered)
    }

    @Test
    fun testIsT9ConfirmGesture_variousGestures() {
        mudraManager = MudraManager(mockContext)

        // Test T9_CONFIRM
        assertTrue(mudraManager.isT9ConfirmGesture(MudraManager.T9_CONFIRM_GESTURE))

        // Test Closed_Fist
        assertTrue(mudraManager.isT9ConfirmGesture("Closed_Fist"))

        // Test FIST
        assertTrue(mudraManager.isT9ConfirmGesture("FIST"))

        // Test non-confirm gesture
        assertFalse(mudraManager.isT9ConfirmGesture("Open_Palm"))
    }

    @Test
    fun testDisconnect_resetsState() = runTest {
        // Given: MudraManager
        mudraManager = MudraManager(mockContext)

        // When: disconnect is called
        mudraManager.disconnect()

        // Then: state should be reset
        assertEquals(MudraManager.ConnectionState.DISCONNECTED, mudraManager.connectionState.first())
        assertFalse(mudraManager.isConnected.first())
    }

    @Test
    fun testConnectToMudra_bluetoothDisabled_setsErrorState() = runTest {
        // Given: Bluetooth is disabled
        `when`(mockBluetoothAdapter.isEnabled).thenReturn(false)
        mudraManager = MudraManager(mockContext)

        // When: attempting to connect
        mudraManager.connectToMudra()

        // Then: state should be ERROR
        assertEquals(MudraManager.ConnectionState.ERROR, mudraManager.connectionState.first())
    }
}
