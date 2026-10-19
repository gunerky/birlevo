package com.example.contentstudio.advertising

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBuildPolicyTest {
    @Test fun signedSampleModeAllowsPhysicalDevicesAndEmulators() {
        for (emulator in listOf(false, true))
            assertTrue(homeAdvertisingAvailable(false, true, true, true, false, emulator))
    }

    @Test fun signedModeRequiresAllSampleGates() {
        assertFalse(homeAdvertisingAvailable(false, false, true, true, false, false))
        assertFalse(homeAdvertisingAvailable(false, true, false, true, false, false))
        assertFalse(homeAdvertisingAvailable(false, true, true, false, false, false))
        assertFalse(homeAdvertisingAvailable(false, false, false, false, false, true))
    }

    @Test fun debugPreviewRetainsItsEmulatorBoundary() {
        assertTrue(homeAdvertisingAvailable(false, true, true, false, true, true))
        assertFalse(homeAdvertisingAvailable(false, true, true, false, true, false))
        assertFalse(homeAdvertisingAvailable(false, true, true, true, true, false))
    }
}
