package com.wakesync.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Espaciado en múltiplos de 4dp y márgenes seguros de pantalla redonda
 * (`wear-design-system` SKILL.md sección 3.2). El círculo de 454×454 es en **píxeles**
 * (densidad de referencia 2.0), es decir 227×227 **dp** — no 454dp como se asumió en un
 * borrador anterior de esta skill (esa versión anterior calculaba el 15% sobre 454dp,
 * dando 68dp, que es en realidad ~30% del diámetro real en dp).
 */
object WakeSyncSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp

    /** 15% de 227dp (diámetro real) ≈ 34dp de inset arriba/abajo. */
    val safeInsetVertical: Dp = 34.dp

    /** 7% de 227dp ≈ 16dp — dentro del rango 5–10% recomendado para listas en Wear. */
    val safeInsetHorizontal: Dp = 16.dp

    /**
     * Espacio inferior a reservar cuando el contenido centrado de una pantalla (arco + texto)
     * convive con un botón anclado al borde inferior (componente 4.2, `padding(bottom = 30dp)`
     * + alto del botón ~48dp + margen de aire). Sin esto, contenido centrado que crece (p. ej.
     * el aviso de sensor 4.7 o el badge de simulación 4.6 sumados al resto) puede invadir
     * visualmente el área del botón — caso real corregido en `NapScreen.kt` (Etapa 4).
     *
     * Se aplica solo a la columna de texto, nunca al arco de progreso (`progressArcDiameter`):
     * envolver también el arco en este padding lo descentra respecto al borde redondo.
     */
    val bottomButtonReserve: Dp = 64.dp

    /** Diámetro del arco de progreso central (componente 4.5) en las 3 fases de Siesta —
     * antes un `200.dp` suelto repetido en cada fase. */
    val progressArcDiameter: Dp = 200.dp

    /** `padding(bottom = ...)` del botón primario de borde inferior (componente 4.2). */
    val primaryButtonBottomPadding: Dp = 30.dp
}
