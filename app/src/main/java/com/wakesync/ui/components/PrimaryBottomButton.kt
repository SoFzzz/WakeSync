package com.wakesync.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Design system component 4.2 (`wear-design-system` SKILL.md): the primary action of a flow
 * screen (Detener, Confirmar, Volver a Inicio). Not the native `EdgeButton` — that API does
 * not exist in the installed `compose-material3:1.0.0-alpha20` (see SKILL.md section 9.1) —
 * this is a normal [Button] shaped and colored to imitate it: filled `primary` (RoseGold),
 * `onPrimary` (Navy) text, large pill radius.
 *
 * This composable is only the button itself; callers anchor it with
 * `Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp)` inside a `Box`, either
 * alone or next to a [SecondaryIconChip] in a `Row` with that same modifier when a screen
 * needs a secondary action beside it (e.g. Siesta's "Simular").
 */
@Composable
fun PrimaryBottomButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    iconContentDescription: String? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.sizeIn(minHeight = 48.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = WakeSyncColors.RoseGold,
            contentColor = WakeSyncColors.NavyDeep
        ),
        contentPadding = PaddingValues(horizontal = WakeSyncSpacing.lg, vertical = WakeSyncSpacing.sm)
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = iconContentDescription,
                tint = WakeSyncColors.NavyDeep,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(WakeSyncSpacing.xs))
        }
        Text(text = text, style = WakeSyncTextStyles.Title)
    }
}
