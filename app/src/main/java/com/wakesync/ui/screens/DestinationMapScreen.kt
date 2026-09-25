package com.wakesync.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.painterResource
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

/**
 * Keeps the pin button above the map attribution line drawn at the bottom of the round screen
 * (RNF-PLC-03). The backend requests the image without logo/attribution because the square
 * image's corners are clipped by the round display, so the app draws the attribution itself.
 */
private val BOTTOM_SAFE_ZONE_DP = 30.dp

/** Bottom inset of the attribution line; at this height the 454x454 circle is still ~110dp wide. */
private val ATTRIBUTION_BOTTOM_PADDING_DP = 9.dp

/**
 * Mapbox logo height: brand guidelines require at least 30 px, i.e. 15dp on the 454x454
 * reference device. Drawn at the top center, where the circle is still ~93dp wide.
 */
private val MAPBOX_LOGO_HEIGHT_DP = 15.dp
private val MAPBOX_LOGO_TOP_PADDING_DP = 12.dp

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
                        .background(WakeSyncColors.BlueDeep)
                )
            }
        }

        // Fixed center pin drawn by the app (RF-PLC-03).
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(WakeSyncColors.SageTeal, CircleShape)
        )

        // Required Mapbox logo (official black wordmark + icon, on a light backing), always visible.
        Image(
            painter = painterResource(R.drawable.mapbox_logo),
            contentDescription = stringResource(R.string.map_logo_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = MAPBOX_LOGO_TOP_PADDING_DP)
                .background(WakeSyncColors.CreamSoft.copy(alpha = 0.8f), CircleShape)
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .height(MAPBOX_LOGO_HEIGHT_DP)
        )

        // Required map data attribution (Mapbox / OpenStreetMap), always visible, even in ambient.
        Text(
            text = stringResource(R.string.map_attribution),
            fontSize = 8.sp,
            color = WakeSyncColors.CreamSoft,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = ATTRIBUTION_BOTTOM_PADDING_DP)
                .background(WakeSyncColors.PureBlack.copy(alpha = 0.6f), CircleShape)
                .padding(horizontal = 4.dp)
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
                    containerColor = WakeSyncColors.BlueDeep.copy(alpha = 0.75f),
                    contentColor = WakeSyncColors.CreamSoft
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
                    containerColor = WakeSyncColors.SageTealMuted,
                    contentColor = WakeSyncColors.SageTeal
                )
            ) {
                Text(text = stringResource(R.string.btn_pin_destination), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
