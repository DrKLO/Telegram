package org.telegram.widgetsandbox

import android.os.SystemClock
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A widget inside a scrolling container (the chat list). A vertical swipe always
 * scrolls the list (v0.1.3: vertical drags are the chat's), whatever `touch-action`
 * says; on `none` the widget gets `pointercancel`. A press on `none` that first
 * leaves the slop horizontally stays the widget's: RendererWebView keeps the parent
 * from intercepting for the rest of it (the `gesture` message's `hold`), except in
 * the leading edge strip. `onGesture` gets the claim: holds only with `none`, `y`
 * never.
 */
@RunWith(AndroidJUnit4::class)
class GestureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** `claims`: what `onGesture` got, one per primary pointerdown. */
    private class Fixture(val host: WidgetHost, val scroll: ScrollView, val latest: AtomicReference<WidgetDiagnostics>, val claims: MutableList<WidgetGestureClaim>)

    private fun mount(scenario: ActivityScenario<TestActivity>, html: String): Fixture {
        lateinit var f: Fixture
        scenario.onActivity { a ->
            val d = a.resources.displayMetrics.density
            val host = WidgetHost(a, html, WidgetTheme(), 360.0)
            val latest = AtomicReference(WidgetDiagnostics())
            val claims = java.util.Collections.synchronizedList(ArrayList<WidgetGestureClaim>())
            host.onChange = { latest.set(it) }
            host.onGesture = { claims.add(it) }
            val column = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            column.addView(host.view, LinearLayout.LayoutParams((360 * d).toInt(), (300 * d).toInt()))
            column.addView(Space(a), LinearLayout.LayoutParams(1, (4000 * d).toInt()))
            val scroll = ScrollView(a)
            scroll.addView(column)
            a.container.removeAllViews()
            a.container.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
            f = Fixture(host, scroll, latest, claims)
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline && !f.latest.get().loaded) Thread.sleep(50)
        assertTrue("widget never loaded", f.latest.get().loaded)
        Thread.sleep(300)
        return f
    }

    /**
     * One press at (xDp, yDp) inside the widget (dp, widget coordinates): held
     * `holdMs` first, then moved by each (dx, dy) in dp, one step every 16 ms, and
     * lifted. Dispatched straight into the view hierarchy from the ScrollView down,
     * which is exactly the interception path under test (system-level injection is
     * refused on a device when another app's window overlaps).
     */
    private fun press(f: Fixture, xDp: Float, yDp: Float, steps: List<Pair<Float, Float>>, holdMs: Long = 0) {
        val d = f.host.view.resources.displayMetrics.density
        var x = xDp * d
        var y = yDp * d
        val t0 = SystemClock.uptimeMillis()
        fun send(action: Int) {
            val e = MotionEvent.obtain(t0, SystemClock.uptimeMillis(), action, x, y, 0)
            e.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.runOnMainSync { f.scroll.dispatchTouchEvent(e) }
            e.recycle()
        }
        send(MotionEvent.ACTION_DOWN)
        if (holdMs > 0) Thread.sleep(holdMs)
        for ((dx, dy) in steps) {
            Thread.sleep(16)
            x += dx * d
            y += dy * d
            send(MotionEvent.ACTION_MOVE)
        }
        Thread.sleep(16)
        send(MotionEvent.ACTION_UP)
        Thread.sleep(400)
    }

    /** Vertical swipe, ~76 dp up over ~176 ms. */
    private fun swipeUp(f: Fixture, xDp: Float, yDp: Float) = press(f, xDp, yDp, List(10) { 0f to -7.6f })

    /** Sideways first (24 dp, past the slop), then ~76 dp up: the axis is locked horizontal. */
    private fun sidewaysThenUp(f: Fixture, xDp: Float, yDp: Float) = press(f, xDp, yDp, List(3) { 8f to 0f } + List(10) { 0f to -7.6f })

    private fun scrollY(f: Fixture): Int {
        var y = 0
        instrumentation.runOnMainSync { y = f.scroll.scrollY }
        return y
    }

    @Test
    fun plainWidgetLetsTheListScroll() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            val f = mount(scenario, "<div style='height:280px;background:#eee'>plain content</div>")
            swipeUp(f, 180f, 150f)
            assertTrue("list should have scrolled, scrollY=${scrollY(f)}", scrollY(f) > 0)
            assertEquals("the client keeps both drags, holds and double taps", listOf(WidgetGestureClaim(x = false, y = false)), f.claims.toList())
            instrumentation.runOnMainSync { f.host.destroy() }
        }
    }

    /** A `none` area that logs what the widget gets of a press: `down`, the fifth `pointermove` (`moved5`), `cancelled`. */
    private val noneArea = """
        <div id="area" style="height:280px;touch-action:none;background:#dde"><div id="box">drag</div></div>
        <script>
          var n = 0;
          document.getElementById('area').addEventListener('pointerdown', function (e) { n = 0; e.preventDefault(); console.log('down'); });
          document.getElementById('area').addEventListener('pointermove', function (e) { if (++n === 5) console.log('moved5'); });
          document.getElementById('area').addEventListener('pointercancel', function () { console.log('cancelled'); });
        </script>
    """.trimIndent()

    private fun logs(f: Fixture) = f.latest.get().logs.map { it.second }

    /** v0.1.3: a vertical drag over `none` is the chat's: the list scrolls and the widget's press ends with `pointercancel`. */
    @Test
    fun touchActionNoneLetsAVerticalDragScrollTheList() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            val f = mount(scenario, noneArea)
            swipeUp(f, 180f, 150f)
            assertTrue("list should have scrolled, scrollY=${scrollY(f)} logs=${logs(f)}", scrollY(f) > 0)
            assertTrue("the widget's press is cancelled: ${logs(f)}", logs(f).contains("cancelled"))
            assertEquals("onGesture: horizontal drags and holds, never vertical ones", listOf(WidgetGestureClaim(x = true, y = false, hold = true)), f.claims.toList())
            instrumentation.runOnMainSync { f.host.destroy() }
        }
    }

    /** On `none`, a press that leaves the slop horizontally is the widget's to the end: moving up afterwards scrolls nothing. */
    @Test
    fun touchActionNoneKeepsAPressLockedHorizontally() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            val f = mount(scenario, noneArea)
            sidewaysThenUp(f, 180f, 150f)
            assertEquals("list must not scroll, logs=${logs(f)}", 0, scrollY(f))
            assertTrue("widget should have seen the moves: ${logs(f)}", logs(f).contains("moved5"))
            assertTrue("the press must not be cancelled: ${logs(f)}", !logs(f).contains("cancelled"))
            instrumentation.runOnMainSync { f.host.destroy() }
        }
    }

    /**
     * On `none` a hold is the widget's (no message menu), and its first move past the slop still locks the axis, as
     * GestureArbiter says (v0.1.3 addendum): sideways it stays the widget's; up, the list scrolls and the widget gets
     * `pointercancel` (from Chromium too: the engine's touch-action pans vertically, touch-action.ts).
     */
    @Test
    fun touchActionNoneAfterAHold() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            val f = mount(scenario, noneArea)
            val hold = GesturePolicy.HOLD_MS.toLong() + 150
            press(f, 180f, 150f, List(3) { 8f to 0f } + List(10) { 0f to -7.6f }, holdMs = hold)
            assertEquals("sideways after the hold: list must not scroll, logs=${logs(f)}", 0, scrollY(f))
            assertTrue("widget should have seen the moves: ${logs(f)}", logs(f).contains("moved5"))
            assertTrue("the press must not be cancelled: ${logs(f)}", !logs(f).contains("cancelled"))
            press(f, 180f, 150f, List(10) { 0f to -7.6f }, holdMs = hold)
            assertTrue("up after the hold: list should have scrolled, scrollY=${scrollY(f)} logs=${logs(f)}", scrollY(f) > 0)
            assertTrue("the widget's press is cancelled: ${logs(f)}", logs(f).contains("cancelled"))
            instrumentation.runOnMainSync { f.host.destroy() }
        }
    }

    /** The leading edge strip takes no claim (the system back gesture): there even a press locked horizontally on `none` leaves the parent free. */
    @Test
    fun theLeadingEdgeStripTakesNoClaim() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            val f = mount(scenario, noneArea)
            sidewaysThenUp(f, (GesturePolicy.EDGE_STRIP_PX / 2).toFloat(), 150f)
            assertTrue("list should have scrolled, scrollY=${scrollY(f)} logs=${logs(f)}", scrollY(f) > 0)
            instrumentation.runOnMainSync { f.host.destroy() }
        }
    }

    /** `pan-x` claims nothing since v0.1.3 (it used to take vertical drags), `pan-y` horizontal drags: the list scrolls over both. */
    @Test
    fun panXAndPanYLeaveVerticalDragsToTheList() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            for ((ta, claim) in listOf("pan-x" to GesturePolicy.NO_CLAIM, "pan-y" to WidgetGestureClaim(x = true, y = false))) {
                val f = mount(scenario, "<div style='height:280px;touch-action:$ta;background:#ede'>$ta</div>")
                swipeUp(f, 180f, 150f)
                assertTrue("$ta: list should have scrolled, scrollY=${scrollY(f)}", scrollY(f) > 0)
                assertEquals(ta, listOf(claim), f.claims.toList())
                instrumentation.runOnMainSync { f.host.destroy() }
            }
        }
    }

    /** `touch-action` on a container covers its children, as in CSS: a plain element inside a `none` container claims horizontal drags and holds. */
    @Test
    fun touchActionOnAContainerCoversItsChildren() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            val f = mount(scenario, "<div style='touch-action:none'><div style='height:280px;background:#dee'>child</div></div>")
            sidewaysThenUp(f, 180f, 150f)
            assertEquals("locked horizontally on the child: list must not scroll", 0, scrollY(f))
            swipeUp(f, 180f, 150f)
            assertTrue("a vertical drag scrolls the list, scrollY=${scrollY(f)}", scrollY(f) > 0)
            assertEquals(List(2) { WidgetGestureClaim(x = true, y = false, hold = true) }, f.claims.toList())
            instrumentation.runOnMainSync { f.host.destroy() }
        }
    }

    /**
     * Profile 1 has no range sliders (form controls are dropped) and no scrolling inside a widget
     * (`overflow: auto` becomes `hidden`), so neither claims the swipe any more: the list scrolls.
     */
    @Test
    fun outOfProfileSlidersAndScrollersLeaveTheSwipeToTheList() {
        val cases = listOf(
            "range" to "<div style='padding:40px 0'><input type=range min=0 max=100 value=50 style='width:300px'></div>",
            "scroller" to "<div style='height:200px;overflow-y:auto'><div style='height:2000px'>tall</div></div>",
        )
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            for ((name, html) in cases) {
                val f = mount(scenario, html)
                swipeUp(f, 150f, if (name == "range") 50f else 100f)
                assertTrue("$name: the list scrolls", scrollY(f) > 0)
                instrumentation.runOnMainSync { f.host.destroy() }
            }
        }
    }
}
