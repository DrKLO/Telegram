package org.telegram.widgetsandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The paint limit's codes and reasons (paint-policy.ts `paintLimitMessage`, the `limit` message of docs/protocol.md). */
class PaintLimitTest {
    @Test
    fun messagesAndCodes() {
        assertEquals("Paint limit exceeded: 17 boxes with shadows, filters or transparency (max 16)", WidgetPaintLimit.PAINT.message(17))
        assertEquals("Paint limit exceeded: 3 boxes with backdrop-filter (max 2)", WidgetPaintLimit.BACKDROP.message(3))
        assertEquals(WidgetPaintLimit.PAINT, WidgetPaintLimit.of("paint-limit"))
        assertEquals(WidgetPaintLimit.BACKDROP, WidgetPaintLimit.of("backdrop-limit"))
        assertNull(WidgetPaintLimit.of("render-budget"))
        assertEquals(16, WidgetPaintLimit.MAX_HEAVY_BOXES)
        assertEquals(2, WidgetPaintLimit.MAX_BACKDROP_BOXES)
    }
}
