package com.wakesync.core

import android.Manifest
import com.wakesync.core.permission.PermissionManager
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating permission declarations in PermissionManager.
 */
class PermissionManagerTest {

    @Test
    fun requiredPermissions_containsMandatoryWearPermissions() {
        val permissions = PermissionManager.REQUIRED_PERMISSIONS

        assertTrue(permissions.contains(Manifest.permission.BODY_SENSORS))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.WAKE_LOCK))
    }
}
