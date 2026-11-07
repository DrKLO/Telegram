package org.telegram.widgetsandbox.demo

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.telegram.widgetsandbox.BubbleAction
import org.telegram.widgetsandbox.GestureArbiter
import org.telegram.widgetsandbox.GesturePolicy
import org.telegram.widgetsandbox.GestureVerdict
import org.telegram.widgetsandbox.PointerKind
import org.telegram.widgetsandbox.WidgetGestureClaim

/**
 * The demo bubble's recognizers over the widget (docs/sdk-v1.md §5.5): what a client does with
 * WidgetHost.onGesture and a GestureArbiter, the bubble's own actions reduced to `onBubble`. It sees
 * every event of a press before the renderer's view (dispatchTouchEvent; the renderer's view keeps the
 * list from intercepting until the claim arrives, and on a `hold` claim until the press leaves the slop);
 * a press the bubble takes (the message menu on a hold or a right click, swipe-to-reply or back; a double
 * tap over the widget is always the widget's) ends for the widget with ACTION_CANCEL, its `pointercancel`,
 * and the rest of it is swallowed. A vertical drag is always the list's own scroll, over `touch-action: none`
 * too (v0.1.3): the list intercepts it and the widget gets ACTION_CANCEL. The claim comes a few ms after the
 * touch: the press waits for it, at most [CLAIM_WAIT_MS], then it is no claim.
 */
@SuppressLint("ViewConstructor")
class BubbleGestureLayout(context: Context, widget: View, private val onBubble: (BubbleAction) -> Unit) : FrameLayout(context) {
    private val arbiter = GestureArbiter()
    private val density = resources.displayMetrics.density.toDouble()
    /** The press waiting for its claim: its down and the moves since, each [t, x, y]. */
    private var waiting: ArrayList<DoubleArray>? = null
    private var pointer = PointerKind.TOUCH
    private var button = 0
    private var pointerId = -1
    private var downTime = 0L
    private var source = 0
    /** The bubble took this press: nothing more of it reaches the widget. */
    private var taken = false
    private var holdAt = 0.0
    private val hold = Runnable { act(arbiter.tick(maxOf(SystemClock.uptimeMillis().toDouble(), holdAt))) }
    private val noClaim = Runnable { start(GesturePolicy.NO_CLAIM) }

    init {
        addView(widget, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** WidgetHost.onGesture: the claim of the press that waits for it (a late one finds none and is dropped). */
    fun claim(claim: WidgetGestureClaim) = start(claim)

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                taken = false
                downTime = e.downTime
                source = e.source
                pointerId = e.getPointerId(0)
                pointer = when (e.getToolType(0)) {
                    MotionEvent.TOOL_TYPE_MOUSE -> PointerKind.MOUSE
                    MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> PointerKind.PEN
                    else -> PointerKind.TOUCH
                }
                button = if (pointer == PointerKind.MOUSE && e.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) 2 else 0
                waiting = arrayListOf(point(e, 0))
                removeCallbacks(hold)
                removeCallbacks(noClaim)
                postDelayed(noClaim, CLAIM_WAIT_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(pointerId)
                if (i >= 0) {
                    val p = point(e, i)
                    waiting?.add(p) ?: act(arbiter.move(p[0], p[1], p[2]))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (e.getPointerId(e.actionIndex) == pointerId) {
                start(GesturePolicy.NO_CLAIM)
                val p = point(e, e.actionIndex)
                pointerId = -1
                act(arbiter.up(p[0], p[1], p[2]))
            }
            MotionEvent.ACTION_CANCEL -> {
                // The list took the press (its scroll), or the window did.
                waiting = null
                removeCallbacks(noClaim)
                removeCallbacks(hold)
                arbiter.cancel()
            }
        }
        return if (taken) true else super.dispatchTouchEvent(e)
    }

    /** [t, x, y]: the event's time (uptime ms, the hold timer's clock), CSS px from the leading edge and from the top. */
    private fun point(e: MotionEvent, i: Int): DoubleArray {
        val x = e.getX(i) / density
        return doubleArrayOf(e.eventTime.toDouble(), if (layoutDirection == LAYOUT_DIRECTION_RTL) width / density - x else x, e.getY(i) / density)
    }

    /** The waiting press goes to the arbiter with its claim, then the moves it made meanwhile. */
    private fun start(claim: WidgetGestureClaim) {
        val w = waiting ?: return
        waiting = null
        removeCallbacks(noClaim)
        val d = w[0]
        act(arbiter.down(d[0], d[1], d[2], claim, pointer, button))
        arbiter.holdDeadline()?.let { at ->
            holdAt = at
            postDelayed(hold, maxOf(0L, at.toLong() - SystemClock.uptimeMillis() + 1))
        }
        for (m in w.drop(1)) if (!taken) act(arbiter.move(m[0], m[1], m[2]))
    }

    private fun act(v: GestureVerdict?) {
        if (arbiter.holdDeadline() == null) removeCallbacks(hold)
        val action = v?.action
        // A vertical drag is the list's own whatever the claim (v0.1.3): the renderer's view lets it intercept
        // (RendererWebView.gestureDecision), which cancels the widget's press; the bubble does nothing more.
        if (v == null || !v.cancel || action == null || action == BubbleAction.SCROLL || taken) return
        taken = true
        val cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        cancel.source = source
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        // The rest of the press is the bubble's: the list does not scroll under the menu or the swipe.
        parent?.requestDisallowInterceptTouchEvent(true)
        onBubble(action)
    }

    companion object {
        /** As long as RendererWebView waits for the same answer before it lets the list intercept. */
        const val CLAIM_WAIT_MS = 150L

        /** What the demo shows for what the bubble did. */
        fun label(action: BubbleAction): String = when (action) {
            BubbleAction.MENU -> "message menu"
            BubbleAction.SWIPE -> "swipe → reply"
            BubbleAction.BACK -> "back"
            BubbleAction.SCROLL -> "scroll"
        }
    }
}
