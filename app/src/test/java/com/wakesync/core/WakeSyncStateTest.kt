package com.wakesync.core

import com.wakesync.core.model.AlertLevel
import com.wakesync.core.model.BiometricMetrics
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.model.NapPhase
import com.wakesync.core.model.NapSessionState
import com.wakesync.core.model.RestState
import com.wakesync.core.model.SessionConflict
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionRecord
import com.wakesync.core.model.SessionType
import com.wakesync.core.model.TransitPhase
import com.wakesync.core.model.TransitSessionState
import com.wakesync.core.model.WakeSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests validating core model integrity, state immutability, and default contracts.
 */
class WakeSyncStateTest {

    @Test
    fun defaultWakeSyncState_isInitializedWithSafeDefaults() {
        val state = WakeSyncState()

        assertEquals(SessionType.NONE, state.sessionType)
        assertEquals(NapPhase.IDLE, state.napState.phase)
        assertEquals(TransitPhase.IDLE, state.transitState.phase)
        assertEquals(AlertLevel.NONE, state.activeAlertLevel)
        assertEquals(RestState.UNKNOWN, state.biometricMetrics.restState)
        assertNull(state.pendingConflict)
        assertEquals(false, state.isSimulated)
        assertEquals(emptyList<String>(), state.missingPermissions)
        assertNull(state.confirmedDestination)
    }

    @Test
    fun confirmedDestination_resolvesDynamicallyAccordingToSessionType() {
        val destTransit = GeoPoint(6.2518, -75.5684, "Campus UCC")
        val destNap = GeoPoint(6.1720, -75.5890, "Parque Envigado")

        // Case 1: SessionType.NONE -> null
        val stateNone = WakeSyncState(sessionType = SessionType.NONE)
        assertNull(stateNone.confirmedDestination)

        // Case 2: SessionType.TRANSIT -> transitState.destination
        val stateTransit = WakeSyncState(
            sessionType = SessionType.TRANSIT,
            transitState = TransitSessionState(destination = destTransit)
        )
        assertEquals(destTransit, stateTransit.confirmedDestination)
        assertEquals("Campus UCC", stateTransit.confirmedDestination?.name)

        // Case 3: SessionType.NAP -> napState.destination
        val stateNap = WakeSyncState(
            sessionType = SessionType.NAP,
            napState = NapSessionState(destination = destNap)
        )
        assertEquals(destNap, stateNap.confirmedDestination)
        assertEquals("Parque Envigado", stateNap.confirmedDestination?.name)
    }

    @Test
    fun sessionConflict_encapsulatesRunningAndRequestedModes() {
        val conflict = SessionConflict(
            runningSession = SessionType.NAP,
            requestedSession = SessionType.TRANSIT
        )

        assertEquals(SessionType.NAP, conflict.runningSession)
        assertEquals(SessionType.TRANSIT, conflict.requestedSession)
    }

    @Test
    fun sessionRecord_preservesAllFieldsAndGeneratesUniqueId() {
        val record = SessionRecord(
            sessionType = SessionType.NAP,
            startTimestamp = 1700000000000L,
            durationSeconds = 900,
            restLatencySeconds = 180,
            outcome = SessionOutcome.COMPLETED
        )

        assertNotNull(record.id)
        assertEquals(SessionType.NAP, record.sessionType)
        assertEquals(1700000000000L, record.startTimestamp)
        assertEquals(900, record.durationSeconds)
        assertEquals(180, record.restLatencySeconds)
        assertEquals(SessionOutcome.COMPLETED, record.outcome)
    }

    @Test
    fun transitState_enforcesMinimumAlertRadiusFloor() {
        val defaultState = TransitSessionState()
        assertEquals(250f, defaultState.dynamicAlertRadiusMeters, 0.001f)

        val destination = GeoPoint(latitude = 4.6097, longitude = -74.0817, name = "Campus UCC")
        val stateWithDest = TransitSessionState(
            phase = TransitPhase.TRACKING,
            destination = destination,
            currentDistanceMeters = 1200f,
            dynamicAlertRadiusMeters = 350f,
            estimatedSpeedMps = 12.5f
        )

        assertEquals("Campus UCC", stateWithDest.destination?.name)
        assertEquals(1200f, stateWithDest.currentDistanceMeters)
        assertEquals(350f, stateWithDest.dynamicAlertRadiusMeters)
    }

    @Test
    fun alertLevel_supportsAllNormativeLevels() {
        val expectedLevels = listOf(AlertLevel.NONE, AlertLevel.SOFT, AlertLevel.MODERATE, AlertLevel.URGENT)
        assertEquals(4, AlertLevel.entries.size)
        assertEquals(expectedLevels, AlertLevel.entries)

        val stateSoft = WakeSyncState(activeAlertLevel = AlertLevel.SOFT)
        val stateModerate = WakeSyncState(activeAlertLevel = AlertLevel.MODERATE)
        val stateUrgent = WakeSyncState(activeAlertLevel = AlertLevel.URGENT)

        assertEquals(AlertLevel.SOFT, stateSoft.activeAlertLevel)
        assertEquals(AlertLevel.MODERATE, stateModerate.activeAlertLevel)
        assertEquals(AlertLevel.URGENT, stateUrgent.activeAlertLevel)
    }
}
