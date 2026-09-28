package com.wakesync.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Shapes

/**
 * Formas por tipo de componente (`wear-design-system` SKILL.md sección 3.3). Los 5 slots
 * de `androidx.wear.compose.material3.Shapes` se mapean a los radios de la skill:
 * extraSmall = aviso de estado compacto / fila de historial (12dp), small = chip de
 * acción / tarjeta (16dp), medium = tarjeta (16dp, mismo radio que small en esta escala),
 * large = diálogo (20dp), extraLarge = badge pill (50%).
 */
val WakeSyncShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(percent = 50)
)
