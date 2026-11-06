package com.wakesync.insights

/** Single-character ellipsis appended to an insight that had to be shortened (RF-INS-04). */
internal const val INSIGHT_ELLIPSIS = '…'

private val SENTENCE_END_CHARS = setOf('.', '!', '?', INSIGHT_ELLIPSIS)
private val TRAILING_CUT_CHARS = setOf(',', ';', ':', '.', '-', '—', '–', INSIGHT_ELLIPSIS)

/**
 * Clamps an AI insight to at most [max] characters without cutting a word in half (RF-INS-04).
 *
 * The text is returned unchanged (trimmed) when it is shorter than [max], or exactly [max] long and
 * ending in sentence punctuation. Otherwise it is treated as truncated — including text the backend
 * already hard-cut at exactly [max] characters mid-word — and is cut at the last word boundary,
 * stripped of trailing punctuation and spaces and suffixed with a single "…", never exceeding [max].
 * A single word longer than the limit falls back to a hard cut of `max - 1` characters plus "…".
 *
 * @param raw insight text as received from the backend.
 * @param max maximum number of characters allowed; must be at least 2.
 * @return the clamped insight text.
 */
fun clampInsightText(raw: String, max: Int): String {
    require(max >= 2) { "max must be at least 2" }
    val text = raw.trim()
    val looksComplete = text.length < max || (text.length == max && text.last() in SENTENCE_END_CHARS)
    if (looksComplete) return text

    val limit = max - 1
    val hardCut = text.take(limit)
    val wordCut = if (text[limit].isWhitespace()) {
        hardCut
    } else {
        val lastSpace = hardCut.indexOfLast { it.isWhitespace() }
        if (lastSpace > 0) hardCut.substring(0, lastSpace) else hardCut
    }
    val cleaned = wordCut.trimEnd { it.isWhitespace() || it in TRAILING_CUT_CHARS }
    val body = cleaned.ifEmpty { hardCut.trimEnd() }
    return body + INSIGHT_ELLIPSIS
}
