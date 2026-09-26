package com.wakesync.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.wakesync.R
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionType

/**
 * Shared session-type / outcome labels for Resumen, Historial and Detalle, so the three screens
 * never word the same record differently.
 */
@StringRes
fun sessionTypeStringRes(type: SessionType): Int = when (type) {
    SessionType.TRANSIT -> R.string.title_transit
    else -> R.string.title_nap
}

/**
 * Icon for a session type — the same pair Inicio uses for its chips (section 6.1).
 */
@DrawableRes
fun sessionTypeIconRes(type: SessionType): Int = when (type) {
    SessionType.TRANSIT -> R.drawable.ic_directions
    else -> R.drawable.ic_bedtime
}

/**
 * Outcome label; an arrival-triggered end reads as "Completada" (the user got where they were going).
 */
@StringRes
fun sessionOutcomeStringRes(outcome: SessionOutcome): Int = when (outcome) {
    SessionOutcome.COMPLETED -> R.string.session_outcome_completed
    SessionOutcome.INTERRUPTED_BY_ARRIVAL -> R.string.session_outcome_completed
    SessionOutcome.TIMED_OUT -> R.string.session_outcome_timed_out
    SessionOutcome.CANCELLED -> R.string.session_outcome_cancelled
}
