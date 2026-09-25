package com.wakesync.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Escala tipográfica única (`wear-design-system` SKILL.md sección 3.1): 4 niveles,
 * máximo 3 visibles por pantalla. Reemplaza los tamaños 9/10/11/12/13/14/18/24sp sueltos
 * usados hoy directamente en cada `Text(fontSize = ...)`. Toda pantalla rediseñada debe
 * usar estos 4 niveles — prohibido pasar un `fontSize` suelto (regla de la skill).
 *
 * No se wirea a `MaterialTheme.typography` (androidx.wear.compose.material3.Typography)
 * en esta etapa: ningún `Text()` de pantalla lee `MaterialTheme.typography.*` hoy — todos
 * pasan `fontSize`/`fontWeight` inline — y esa clase no tiene constructor sin argumentos,
 * así que fijar sus 13 slots sin necesidad real habría sido riesgo de build sin beneficio.
 * Cada pantalla migra a estos 4 niveles (`style = WakeSyncTextStyles.X` o sus valores
 * `fontSize`/`fontWeight` inline) en su propia etapa de implementación.
 */
object WakeSyncTextStyles {
    val Display = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold)
    val Title = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val Label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
}
