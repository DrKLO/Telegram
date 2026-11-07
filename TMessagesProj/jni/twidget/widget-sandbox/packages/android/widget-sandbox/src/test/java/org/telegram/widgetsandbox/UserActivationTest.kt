package org.telegram.widgetsandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserActivationTest {
    @Test
    fun pointerRulesNoKeys() {
        assertTrue(UserActivation.isUserActivation("click", 1.0))
        assertTrue(UserActivation.isUserActivation("pointerdown"))
        assertFalse("the click Enter or Space activates", UserActivation.isUserActivation("click", 0.0))
        assertFalse(UserActivation.isUserActivation("keydown"))
        assertFalse(UserActivation.isUserActivation("mousemove"))
        assertFalse(UserActivation.isUserActivation("scroll"))
    }

    @Test
    fun onePhysicalPressIsOneGesture() {
        val c = GestureCounter()
        val press = listOf("pointerdown", "mousedown", "pointerup", "mouseup", "click").map { c.gestureFor(it) }
        assertEquals(listOf(1, 1, 1, 1, 1), press)
        assertEquals(1, c.gestureFor("dblclick"))
        assertEquals(2, c.gestureFor("pointerdown"))
        assertEquals(2, c.gestureFor("click", 1.0))
        assertEquals(0, c.gestureFor("mousemove"))
        assertEquals(3, c.gestureFor("click", 1.0))
    }

    /** A press the renderer reports (its `gesture` claim) begins a new gesture: a finger's release after a drag is not the drag's (user-activation.test.ts). */
    @Test
    fun aPressTheRendererReportsBeginsANewGesture() {
        val c = GestureCounter()
        // a drag's release (its pointerdown not trusted: a finger), no click after it
        assertEquals(1, c.gestureFor("pointerup"))
        // the next press: the claim, its pointerdown again not trusted, then its release and its click
        c.pressBegan()
        assertEquals(2, c.gestureFor("pointerup"))
        assertEquals(2, c.gestureFor("click", 1.0))
        // a mouse's press after a claim is one gesture, as before
        c.pressBegan()
        assertEquals(listOf(3, 3, 3, 3, 3), listOf("pointerdown", "mousedown", "pointerup", "mouseup", "click").map { c.gestureFor(it, 1.0) })
    }

    /**
     * A second finger's pointerdown is neither trusted (it may become the chat's scroll) nor claimed (`gesture` is the primary
     * pointer's): WidgetHost calls `pressBegan` for every forwarded pointerdown, so each of its taps is a gesture of its own,
     * not the spent one before it (desktop `an_untrusted_pointerdown_begins_a_new_gesture`).
     */
    @Test
    fun anUntrustedPointerdownBeginsANewGesture() {
        val c = GestureCounter()
        c.pressBegan() // the second finger's pointerdown, not trusted: no gestureFor
        assertEquals(1, c.gestureFor("pointerup"))
        c.pressBegan() // its next pointerdown
        assertEquals(2, c.gestureFor("pointerup"))
        // without it the release continues the open press, whose gesture the first pointerup handler spent
        assertEquals(2, c.gestureFor("pointerup"))
    }

    @Test
    fun keysAndKeyboardClicksAreNeverAGesture() {
        val c = GestureCounter()
        assertEquals(0, c.gestureFor("keydown"))
        assertEquals(0, c.gestureFor("click", 0.0))
        assertEquals(0, c.gestureFor("dblclick", 0.0))
        assertEquals(1, c.gestureFor("click", 1.0))
        assertEquals(0, c.gestureFor("click", 0.0))
    }

    @Test
    fun gateLetsOneCallThroughPerGestureInsideItsTask() {
        val g = ActivationGate()
        assertFalse(g.consume(true))
        g.begin(1, true)
        assertTrue(g.consume(true))
        assertFalse(g.consume(true))
        g.end()
        g.begin(1, true)
        assertFalse(g.consume(true))
        g.end()
        g.begin(2, false)
        assertFalse(g.consume(true))
        g.end()
        g.begin(3, true)
        g.end()
        assertFalse(g.consume(true))
        g.begin(4, true)
        assertFalse(g.consume(false))
        assertTrue(g.consume(true))
    }

    /** A request to the bot peeks at the press (`available`) and consumes it only when it goes out as the press's. */
    @Test
    fun availablePeeksWithoutConsuming() {
        val g = ActivationGate()
        assertFalse(g.available(true))
        g.begin(1, true)
        assertFalse("hidden: no press", g.available(false))
        assertTrue(g.available(true))
        assertTrue("a peek leaves the press", g.available(true))
        assertTrue(g.consume(true))
        assertFalse(g.available(true))
        g.end()
        g.begin(2, true)
        g.end()
        assertFalse("outside its task", g.available(true))
    }
}
