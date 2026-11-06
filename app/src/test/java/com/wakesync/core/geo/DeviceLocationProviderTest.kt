package com.wakesync.core.geo

import com.wakesync.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the provider-selection logic of [DeviceLocationProvider].
 */
class DeviceLocationProviderTest {

    @Test
    fun selectFreshest_returnsNullWhenNoProviderHasAFix() {
        assertNull(DeviceLocationProvider.selectFreshest(emptyList()))
    }

    @Test
    fun selectFreshest_returnsMostRecentFixAcrossProviders() {
        val stale = GeoPoint(1.0, -77.0)
        val fresh = GeoPoint(1.2, -77.2)
        val older = GeoPoint(4.6, -74.0)

        val result = DeviceLocationProvider.selectFreshest(
            listOf(stale to 1_000L, fresh to 5_000L, older to 500L)
        )

        assertEquals(fresh, result)
    }
}
