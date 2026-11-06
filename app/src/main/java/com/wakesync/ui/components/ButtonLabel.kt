package com.wakesync.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.Text

/** Line height relative to font size for button labels (only visible between wrapped lines). */
private const val LABEL_LINE_HEIGHT_FACTOR = 1.15f

/**
 * Label for the content slot of a `wear.compose.material3.Button`, centered on both axes.
 *
 * The installed `compose-material3:1.0.0-alpha20` content slot does not vertically center its
 * children when the button is taller than the label (fixed `height`/`size`), so a bare [Text]
 * sits at the top of the button. This wraps the label in a [Box] that fills the slot's height
 * and centers it, and trims the font padding (same [LineHeightStyle] trick as
 * [PrimaryBottomButton]) so the glyphs are optically centered, not just the line box.
 *
 * The enclosing button must have a bounded height (e.g. `Modifier.height(48.dp)` or
 * `Modifier.size(...)`), otherwise [fillMaxHeight] would stretch it to the parent's height.
 *
 * Multi-line labels only wrap between words: hyphenation is disabled and [LineBreak.Heading]
 * balances the lines, so a long label such as "Descartar Alarma" breaks as "Descartar" /
 * "Alarma" instead of splitting a word — provided [modifier] gives it enough width.
 *
 * @param text label to show.
 * @param style text style from `WakeSyncTextStyles`.
 * @param modifier extra modifier for the centering box (e.g. `fillMaxWidth()` in a circular button).
 * @param maxLines maximum number of lines before ellipsizing.
 */
@Composable
fun CenteredButtonLabel(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = style.copy(
                // A little leading so two-line labels don't touch; Trim.Both removes the outer
                // padding, so single-line labels stay optically centered.
                lineHeight = style.fontSize * LABEL_LINE_HEIGHT_FACTOR,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                ),
                hyphens = Hyphens.None,
                lineBreak = LineBreak.Heading
            ),
            textAlign = TextAlign.Center,
            softWrap = maxLines > 1,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
