package com.wakesync.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Mirrors [com.wakesync.places.DestinationSearchRepository.MAP_REQUEST_SIZE_PX] (227 logical px,
 * scale=2). com.wakesync.ui cannot import com.wakesync.places directly (wakesync-architecture),
 * so this is duplicated here purely for layout sizing.
 */
private val MAP_IMAGE_SIZE_DP = 227.dp

/** Keeps floating controls clear of Google's logo/attribution baked into the image's bottom edge (RNF-PLC-03). */
private val BOTTOM_SAFE_ZONE_DP = 30.dp

/**
 * Mapa de Destino (CR-01, RF-PLC-03).
 *
 * ASSUMPTION (verify on-device before trusting drag/zoom accuracy): the backend renders the
 * static map at [MAP_IMAGE_SIZE_DP] logical px with scale=2, which decodes to a 454x454
 * physical-pixel bitmap. Drawing it with [ContentScale.None] inside a 227dp box reproduces the
 * "1 logical px = 2 physical px" mapping used by [com.wakesync.places.WebMercatorProjection]
 * only when the display density matches the Wear OS **Large Round (454x454 px)** reference
 * device. Run the emulator on that exact AVD profile — a different profile (e.g. Small Round
 * 384x384) will desync the drag gesture from what the image actually shows.
 *
 * The pin is always drawn by this screen at the exact center; the returned image never
 * contains a marker (RF-PLC-03).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DestinationMapScreen(
    image: ImageBitmap?,
    onPan: (dxScreenPx: Float, dyScreenPx: Float) -> Unit,
    onZoom: (delta: Int) -> Unit,
    onPinCenter: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WakeSyncColors.PureBlack)
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                // Sign convention verified informally; flip if the emulator's crown zooms inverted.
                onZoom(if (event.verticalScrollPixels > 0f) -1 else 1)
                true
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onPan(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(MAP_IMAGE_SIZE_DP), contentAlignment = Alignment.Center) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.None,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WakeSyncColors.CarbonSurface)
                )
            }
        }

        // Fixed center pin drawn by the app (RF-PLC-03).
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(WakeSyncColors.GreenTransit, CircleShape)
        )

        if (!isAmbient) {
            Button(
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 8.dp, start = 8.dp)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WakeSyncColors.CarbonSurface.copy(alpha = 0.75f),
                    contentColor = WakeSyncColors.White
                )
            ) {
                Text(text = "←", fontSize = 14.sp)
            }

            Button(
                onClick = onPinCenter,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = BOTTOM_SAFE_ZONE_DP)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WakeSyncColors.GreenMuted,
                    contentColor = WakeSyncColors.GreenTransit
                )
            ) {
                Text(text = stringResource(R.string.btn_pin_destination), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
