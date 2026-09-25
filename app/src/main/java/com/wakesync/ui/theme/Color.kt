package com.wakesync.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color palette per `wear-design-system` SKILL.md section 2 ("reloj clásico de lujo,
 * calmado"). Contrast ratios against WCAG AA (relative luminance formula) are documented
 * in section 2.3-2.5 of that skill, not repeated here.
 */
object WakeSyncColors {
    // 1. Base palette (section 2.1/2.2)
    val NavyDeep = Color(0xFF1B2A45) // background / surface
    val BlueDeep = Color(0xFF164068) // surfaceContainer
    val CreamSoft = Color(0xFFF9E3CC) // onSurface
    val RoseGold = Color(0xFFDAAA8D) // primary
    val TanMuted = Color(0xFFC2AC98) // onSurfaceVariant
    val BronzeMuted = Color(0xFF8A6F5C) // outline
    val PureBlack = Color(0xFF000000) // ambient background only

    // 2. Session state accents (section 2.4)
    val SteelBlue = Color(0xFF7FA8C4) // Calibrando
    val SteelBlueMuted = Color(0xFF2A4A5E)
    val AmberSand = Color(0xFFD9A56A) // Monitoreando
    val AmberSandMuted = Color(0xFF5C4326)
    val PlumLavender = Color(0xFFA692C4) // Reposo profundo confirmado
    val PlumLavenderMuted = Color(0xFF362B4A)
    val SageTeal = Color(0xFF7FA893) // Transporte en seguimiento
    val SageTealMuted = Color(0xFF2C4038)
    val EmberRose = Color(0xFFD47F68) // Alerta activa (4.81:1 sobre NavyDeep)
    val EmberRoseMuted = Color(0xFF4A241C)

    // 3. Semantic (section 2.5) — warning y offline son tokens propios,
    //    error/success reutilizan EmberRose/SageTeal a propósito.
    val WarningOchre = Color(0xFFC98A4B)
    val WarningOchreMuted = Color(0xFF4A3319)
    val SlateMist = Color(0xFF8B99A8) // offline
    val SlateMistMuted = Color(0xFF2A323C)
}
