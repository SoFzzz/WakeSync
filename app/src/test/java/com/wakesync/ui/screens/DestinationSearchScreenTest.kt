package com.wakesync.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** B1: the RemoteInput result must never be parsed after a cancel or with a null Intent. */
class DestinationSearchScreenTest {

    private val readText: (String) -> CharSequence? = { it }

    @Test
    fun `cancelled result is ignored even if it carries data`() {
        assertNull(resolveRemoteInputQuery(android.app.Activity.RESULT_CANCELED, "universidad", readText))
    }

    @Test
    fun `OK result with null data is ignored without reading`() {
        var read = false
        val result = resolveRemoteInputQuery<String>(android.app.Activity.RESULT_OK, null) { read = true; it }
        assertNull(result)
        assertEquals(false, read)
    }

    @Test
    fun `OK result returns the trimmed text`() {
        assertEquals("parque berrio", resolveRemoteInputQuery(android.app.Activity.RESULT_OK, "  parque berrio ", readText))
    }

    @Test
    fun `OK result with blank text is ignored`() {
        assertNull(resolveRemoteInputQuery(android.app.Activity.RESULT_OK, "   ", readText))
    }
}
