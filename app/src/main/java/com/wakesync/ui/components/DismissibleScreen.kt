package com.wakesync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.SwipeToDismissBox
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Wraps a sub-screen with edge swipe-to-dismiss (`wear-design-system` SKILL.md section 5.2).
 *
 * WakeSync has no `SwipeDismissableNavHost` yet — [com.wakesync.ui.navigation.WakeSyncNavHost]
 * is a single `Activity` whose screens are a plain `when` over state flags (see its KDoc). On
 * Wear OS, `android:windowSwipeToDismiss` is on by default at the window level: an edge swipe
 * nobody intercepts finishes the *whole* `Activity`, not just the current screen — verified
 * empirically (`dumpsys activity activities` showed the window returning to
 * `com.google.android.wearable.sysui` after a swipe from Inicio itself, not only from a
 * sub-screen). This composable is the per-screen fix: it intercepts the gesture with
 * [androidx.wear.compose.material.SwipeToDismissBox] and maps a completed swipe to [onBack],
 * so it never reaches the system-level dismiss.
 *
 * Root screens (Inicio) are deliberately left unwrapped so an edge swipe there still falls
 * through to the system default (exits the app) — that is the desired behavior, not a bug.
 *
 * @param enabled When false (active Siesta/Transporte session), the gesture is still consumed
 * here so it can't reach the system-level dismiss and accidentally exit the app, but it does
 * nothing — no [onBack] call, no screen change. This is a deliberate simplification: it stops
 * a stray edge swipe from ending an active session by accident, at the cost of the swipe
 * feeling inert rather than showing a "locked" animation.
 *
 * The background slot shown mid-swipe is a flat [WakeSyncColors.NavyDeep] scrim, not a preview
 * of the previous screen (`SwipeToDismissBox`'s usual "peek behind" pattern) — wiring the real
 * previous-screen content per branch is straightforward for Ajustes/Historial (their parent is
 * always Inicio) but ambiguous for the Buscar/Mapa/Confirmar flow (the parent differs by step),
 * and is left for whenever that flow's own redesign (Etapa 5) revisits its navigation.
 */
@Composable
fun DismissibleScreen(
    onBack: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissValue.Dismissed) {
            if (enabled) {
                onBack()
            }
            dismissState.snapTo(SwipeToDismissValue.Default)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundScrimColor = WakeSyncColors.NavyDeep,
        contentScrimColor = WakeSyncColors.NavyDeep
    ) { isBackground ->
        if (isBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WakeSyncColors.NavyDeep)
            )
        } else {
            content()
        }
    }
}
