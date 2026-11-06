package com.wakesync.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightTextClampTest {

    private val max = InsightRepository.INSIGHTS_MAX_CHARS

    @Test
    fun `short text is returned unchanged`() {
        val text = "Buen descanso, intenta repetir la siesta a la misma hora."
        assertEquals(text, clampInsightText("  $text  ", max))
    }

    @Test
    fun `text of exactly max ending with a period is returned unchanged`() {
        val text = "a".repeat(max - 1) + "."
        assertEquals(text, clampInsightText(text, max))
    }

    @Test
    fun `long text is cut at a word boundary with an ellipsis`() {
        val text = "Lograste descansar rapido durante el viaje, sigue asi y procura mantener " +
            "una rutina de sueno estable para aprovechar mejor el tiempo en el proximo trayecto en bus."
        val result = clampInsightText(text, max)

        assertTrue(result.length <= max)
        assertTrue(result.endsWith(INSIGHT_ELLIPSIS))
        val body = result.dropLast(1)
        assertTrue(text.startsWith(body))
        assertTrue(text[body.length].isWhitespace() || text[body.length] in ",;:.")
    }

    @Test
    fun `text already cut by backend at exactly max mid-word is shortened to the last whole word`() {
        val full = "Lograste descansar rapido durante el viaje, sigue asi y procura mantener " +
            "una rutina de sueno estable para aprovechar mejor el proximo trayecto."
        val backendCut = full.take(max)
        assertEquals(max, backendCut.length)

        val result = clampInsightText(backendCut, max)

        assertTrue(result.length <= max)
        assertEquals("${backendCut.substringBeforeLast(' ').trimEnd(',')}$INSIGHT_ELLIPSIS", result)
    }

    @Test
    fun `trailing punctuation before the ellipsis is removed`() {
        val text = "Uno dos, " + "x".repeat(max)
        assertEquals("Uno dos$INSIGHT_ELLIPSIS", clampInsightText(text, max))
    }

    @Test
    fun `single very long word falls back to a hard cut plus ellipsis`() {
        val text = "a".repeat(max + 20)
        val result = clampInsightText(text, max)

        assertEquals(max, result.length)
        assertEquals("a".repeat(max - 1) + INSIGHT_ELLIPSIS, result)
    }
}
