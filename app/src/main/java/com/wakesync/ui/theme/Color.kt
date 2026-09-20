package com.wakesync.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Normative color palette according to Section 3.1 of WAKESYNC_MASTER_DOCUMENTATION.md.
 * No custom or unlisted colors are used to ensure strict compliance with UI/UX specs.
 */
object WakeSyncColors {
    // 1. Inicio / Default (Blanco / Carbón)
    val White = Color(0xFFFFFFFF)
    val Carbon = Color(0xFF1C1B1F)
    val CarbonSurface = Color(0xFF2B2930)
    val BackgroundDark = Color(0xFF121212)
    val TextMuted = Color(0xFFE6E1E5)
    val PureBlack = Color(0xFF000000)

    // 2. Calibrando Basal (Cian #00E5FF)
    val CyanBasal = Color(0xFF00E5FF)
    val CyanMuted = Color(0xFF004D54)

    // 3. Siesta: Monitoreando (Ámbar #FFD600)
    val AmberMonitoring = Color(0xFFFFD600)
    val AmberMuted = Color(0xFF5A4800)

    // 4. Siesta: Reposo Confirmado (Índigo #7C4DFF)
    val IndigoDeepRest = Color(0xFF7C4DFF)
    val IndigoMuted = Color(0xFF2E1960)

    // 5. Transporte: En Ruta (Verde #00E676)
    val GreenTransit = Color(0xFF00E676)
    val GreenMuted = Color(0xFF004D27)

    // 6. Alerta Activa (Coral #FF1744)
    val CoralAlert = Color(0xFFFF1744)
    val CoralMuted = Color(0xFF6B0014)

    // 7. Sensor No Disponible (Naranja #FF9100)
    val OrangeWarning = Color(0xFFFF9100)
    val OrangeMuted = Color(0xFF5E3500)
}
