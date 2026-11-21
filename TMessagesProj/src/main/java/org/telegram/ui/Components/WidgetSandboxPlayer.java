package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.utils.twidget.WidgetGestureArbiter;
import org.telegram.utils.twidget.WidgetSandboxMessage;
import org.telegram.utils.twidget.WidgetSandboxSession;

/**
 * Plays a {@link WidgetSandboxMessage} into a host's canvas: its published frame (or snapshot),
 * the compositor layers above it, and touches back into the widget. Owns no native session and no
 * View: a block inside a chat bubble (RichWidgetBlock) is its host.
 */
public final class WidgetSandboxPlayer {
    /** What the player needs from where it is drawn. All calls on the UI thread. */
    public interface Host {
        /** The widget's height changed: lay it out again. */
        void onWidgetResized();

        void invalidateWidget();

        /** True while the widget may own the gesture, false to give it back to the parents. */
        void disallowParentIntercept(boolean disallow);

        /**
         * The gesture policy gave a hold over the widget to the bubble (touch-action: auto): the message menu,
         * which the chat list's own long press cannot open, as the widget held the parents for its claim.
         */
        default void onBubbleHold() {}

        /** The CSS cursor over the widget for the mouse pointer: "pointer", "text", …; "auto" for the default. */
        default void setCursor(String cursor) {}
    }

    /** How long a press waits for the widget's claim before it counts as none (docs/sdk-v1.md §5.5). */
    private static final long CLAIM_WAIT_MS = 150;
    /**
     * Experimental (SharedConfig#widgetStaticLayers): a document frame shown this long without a new one is
     * drawn from a compositing layer (WidgetFrameNode#setLayered). The first frame a player shows goes to one
     * at once, a widget being static as a rule; a new frame comes without, until it holds this long again.
     */
    private static final long STATIC_LAYER_MS = 333;
    private static final long DUPLICATE_DOWN_MS = 100;

    private final Host host;
    private final float density;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix layerMatrix = new Matrix();
    private final float[] layerValues = new float[9];
    private final float[] layerKey = new float[7];
    private final Path layerClip = new Path();
    private final RectF layerRect = new RectF();
    private final float[] layerRadii = new float[8];
    private final Runnable claimTimeout = () -> onClaim(false, false, false);
    private final Runnable holdTick = this::onHoldTick;
    // Who gets each press (docs/sdk-v1.md §5.5): the shared policy over this widget's presses.
    private final WidgetGestureArbiter arbiter = new WidgetGestureArbiter();
    private final Runnable staticLayer = this::onStaticFrame;
    // The document frame drawn last, and since when (STATIC_LAYER_MS).
    private WidgetSandboxSession.Frame shownFrame;
    private WidgetSandboxMessage message;
    private boolean visible, previewing;
    private long lastDownAt, lastDownTime;
    private float lastDownX, lastDownY;
    // The primary press: open, its claim known (the arbiter has it), the effective claim (none in the edge
    // strip), the last verdict, whether the widget lost it (cancelled) and whether the parents may intercept.
    private boolean pressOpen, claimed, claimX, claimHold, widgetCancelled, parentsHeld, parentsFree;
    // The bubble's hold was told for this press: the arbiter repeats its verdict with every move and tick after it.
    private boolean holdTold;
    // The press is a mouse's (its drags and holds are the widget's, docs/sdk-v1.md §5.5), with this button (2: the
    // secondary) and these modifiers; a mouse is over the widget with no button down (hover).
    private boolean pressMouse, hovering;
    // The press began with the list's copy of its DOWN (no tool type): the widget gets the DOWN with the real one.
    private boolean downPending;
    private int pressButton, pressModifiers;
    private float hoverX, hoverY;
    private int verdict = WidgetGestureArbiter.NONE;
    private long pressAt;
    // The press in widget CSS px: from the top left (what the widget gets), and the last point.
    private float pressX, pressY, lastX, lastY;
    // The width the widget was last drawn at, px: the arbiter's x is from the leading edge.
    private int drawnWidth;

    public WidgetSandboxPlayer(Host host, float density) {
        this.host = host;
        this.density = density;
        placeholderPaint.setTextSize(14 * density);
        placeholderPaint.setColor(0xff808080);
    }

    public void bind(WidgetSandboxMessage message) {
        if (this.message == message) return;
        unbind();
        this.message = message;
        if (message == null) return;
        message.bind(this);
        message.setVisible(visible);
    }

    /**
     * Shows `message`'s frames without running it: no visibility, touches or frame callbacks. For a
     * layout that only animates out while a newer one runs the widget.
     */
    public void preview(WidgetSandboxMessage message) {
        unbind();
        this.message = message;
        previewing = message != null;
    }

    public void unbind() {
        if (message == null) return;
        if (previewing) {
            previewing = false;
            message = null;
            return;
        }
        // A press this player can no longer finish (the cell went, a newer layout took the widget) ends for the
        // widget now: no element stays :active without the release it never gets.
        endWidgetPress();
        endHover();
        message.setVisible(false);
        message.unbind(this);
        message = null;
        main.removeCallbacks(staticLayer);
        shownFrame = null;
        endPress();
        arbiter.release();
    }

    /** Milliseconds since the last press on the widget, or -1 for none (the height log). */
    long msSinceTouch() {
        return lastDownAt == 0 ? -1 : SystemClock.uptimeMillis() - lastDownAt;
    }

    public WidgetSandboxMessage message() {
        return message;
    }

    /** The host shows the widget: sessions tick only while visible. */
    public void setVisible(boolean visible) {
        if (this.visible == visible) return;
        this.visible = visible;
        if (message != null && !previewing) message.setVisible(visible);
    }

    public boolean isVisible() {
        return visible;
    }

    /** The message's height, or the placeholder's before it has one. */
    public int heightPx() {
        return message != null && message.heightPx() > 0
                ? message.heightPx()
                : Math.round(WidgetSandboxMessage.PLACEHOLDER_DP * density);
    }

    /**
     * Draws the widget `width` px wide at the canvas origin. True while compositor layers still
     * move: the host invalidates on the next frame.
     */
    public boolean draw(Canvas canvas, int width) {
        drawnWidth = width;
        WidgetSandboxSession.Frame frame = message == null ? null : message.frame();
        if (frame != null && frame.width > 0 && frame.node.canDraw(canvas)) {
            if (!frame.recorded()) message.ensureDisplayList();
            if (frame != shownFrame) onNewFrame(frame);
            // A frame of another width (the chat's width changed, the new one is on its way) is scaled to
            // this one rather than left out: the bubble keeps its picture through the change.
            boolean scaled = Math.abs(frame.width - width) > 1;
            if (scaled) {
                canvas.save();
                float scale = width / (float) frame.width;
                canvas.scale(scale, scale);
            }
            frame.node.draw(canvas, 1);
            boolean moving = frame.layers.length > 0 && drawLayers(canvas, frame) && visible;
            if (scaled) canvas.restore();
            return moving;
        }
        // A preview has no placeholder: the running layout shows it.
        if (previewing) return false;
        if (message != null) message.ensureDisplayList();
        String failure = message == null ? null : message.failure();
        canvas.drawText(
                LocaleController.getString(failure == null ? R.string.WidgetLoading : R.string.WidgetLoadFailed),
                12 * density,
                28 * density,
                placeholderPaint);
        return false;
    }

    /**
     * Compositor layers above the document, bottom first. Each key holds the layer's full matrix
     * (document CSS px) and opacity; values between keys are linear. True while any still moves.
     */
    private boolean drawLayers(Canvas canvas, WidgetSandboxSession.Frame frame) {
        double now = System.nanoTime() / 1e9 + frame.clockOffset;
        boolean moving = false;
        for (WidgetSandboxSession.Layer layer : frame.layers) {
            int keys = layer.keys.length / 8;
            if (keys == 0 || !layer.node.recorded()) continue;
            double elapsed = Math.max(0, now - layer.start);
            double phase;
            if (layer.duration <= 0) phase = 1;
            else if (layer.repeat) {
                phase = (elapsed % layer.duration) / layer.duration;
                moving = true;
            } else {
                phase = Math.min(1, elapsed / layer.duration);
                moving |= phase < 1;
            }
            sample(layer.keys, keys, (float) phase);
            canvas.save();
            if (layer.clip != null) {
                // An ancestor's overflow clip: it does not move with the layer.
                layerRect.set(
                        layer.clip[0] * density,
                        layer.clip[1] * density,
                        (layer.clip[0] + layer.clip[2]) * density,
                        (layer.clip[1] + layer.clip[3]) * density);
                if (layer.clipRadii == null) canvas.clipRect(layerRect);
                else {
                    for (int i = 0; i < 8; i++) layerRadii[i] = layer.clipRadii[i] * density;
                    layerClip.rewind();
                    layerClip.addRoundRect(layerRect, layerRadii, Path.Direction.CW);
                    canvas.clipPath(layerClip);
                }
            }
            // The key maps document CSS px to document CSS px; the canvas is in device px.
            layerValues[0] = layerKey[0];
            layerValues[1] = layerKey[2];
            layerValues[2] = layerKey[4] * density;
            layerValues[3] = layerKey[1];
            layerValues[4] = layerKey[3];
            layerValues[5] = layerKey[5] * density;
            layerValues[6] = 0;
            layerValues[7] = 0;
            layerValues[8] = 1;
            layerMatrix.setValues(layerValues);
            canvas.concat(layerMatrix);
            layer.node.draw(canvas, Math.max(0, Math.min(1, layerKey[6])));
            canvas.restore();
        }
        return moving;
    }

    /** The matrix (a, b, c, d, e, f) and opacity at `phase` into layerKey. */
    private void sample(float[] keys, int count, float phase) {
        int from = 0;
        while (from + 1 < count && keys[(from + 1) * 8] <= phase) from++;
        int to = Math.min(from + 1, count - 1);
        float t0 = keys[from * 8], t1 = keys[to * 8];
        float mix = to == from || t1 <= t0 ? 0 : (phase - t0) / (t1 - t0);
        mix = Math.max(0, Math.min(1, mix));
        for (int i = 0; i < 7; i++) {
            float a = keys[from * 8 + 1 + i], b = keys[to * 8 + 1 + i];
            layerKey[i] = a + (b - a) * mix;
        }
    }

    /** UI: `frame` is drawn for the first time: the first a player shows gets a layer, a later one waits. */
    private void onNewFrame(WidgetSandboxSession.Frame frame) {
        boolean first = shownFrame == null;
        shownFrame = frame;
        main.removeCallbacks(staticLayer);
        if (!SharedConfig.widgetStaticLayers) return;
        if (first) frame.node.setLayered(true);
        else main.postDelayed(staticLayer, STATIC_LAYER_MS);
    }

    /** UI: the frame held STATIC_LAYER_MS: from now on it is drawn from a layer. */
    private void onStaticFrame() {
        if (shownFrame == null || message == null || message.frame() != shownFrame) return;
        shownFrame.node.setLayered(true);
        host.invalidateWidget();
    }

    // ── message events ──

    public void onMessageFrame(boolean resized) {
        if (resized) host.onWidgetResized();
        host.invalidateWidget();
    }

    public void onMessageFailure() {
        host.invalidateWidget();
    }

    /** The widget's claim of the press that started with the last DOWN (docs/sdk-v1.md §5.5). */
    public void onGesture(boolean x, boolean y, boolean hold) {
        if (pressOpen && !claimed) onClaim(x, y, hold);
    }

    /**
     * The claim is known (or {@link #CLAIM_WAIT_MS} passed: none): the arbiter gets the press. With nothing
     * claimed the chat's recognizers have it at once (the list scrolls, a swipe replies, a hold opens the
     * menu); otherwise they wait for the arbiter's verdict.
     */
    private void onClaim(boolean x, boolean y, boolean hold) {
        if (!pressOpen || claimed) return;
        main.removeCallbacks(claimTimeout);
        claimed = true;
        int v = arbiter.down(pressAt, leading(pressX), pressY, x, y, hold, pressMouse, pressButton);
        boolean edge = WidgetGestureArbiter.edge(v);
        claimX = x && !edge;
        claimHold = hold && !edge;
        // No `y`: vertical drags are always the chat's (v0.1.3), the host reports it false and the arbiter ignores it.
        apply(v);
        if (lastX != pressX || lastY != pressY) apply(arbiter.move(SystemClock.uptimeMillis(), leading(lastX), lastY));
        // A mouse's drags and holds are the widget's whatever it claims: the list never pans or swipes under it.
        if (!claimX && !claimHold && !pressMouse) {
            freeParents();
        } else {
            holdParents();
            // A swipe can no longer be the bubble's: the list lets go of it (ChatMessageCell#widgetMayGiveSwipe).
            if ((claimX || pressMouse) && !parentsFree) host.disallowParentIntercept(true);
        }
        scheduleHold();
    }

    /**
     * A verdict of the arbiter: the bubble taking a press ends it for the widget (pointercancel) and leaves it
     * to the chat's own recognizers; the widget keeping a drag or a hold keeps the parents out.
     */
    private void apply(int v) {
        if (v == WidgetGestureArbiter.NONE) return;
        verdict = v;
        if (WidgetGestureArbiter.cancel(v) && !widgetCancelled) {
            widgetCancelled = true;
            message.touch(3, lastX, lastY, 0, true, pressMouse, pressModifiers, pressButton);
        }
        int owner = WidgetGestureArbiter.owner(v);
        if (owner == WidgetGestureArbiter.OWNER_BUBBLE) {
            freeParents();
            // The bubble's hold, at the policy's 500 ms: the message menu comes from here (the cell's own long press
            // skips presses over the widget, ChatMessageCell#onLongPress).
            // Once per press: the menu may not open (a message still being sent) and the press goes on.
            if (WidgetGestureArbiter.action(v) == WidgetGestureArbiter.ACTION_MENU && !holdTold) {
                holdTold = true;
                host.onBubbleHold();
            }
        } else if (owner == WidgetGestureArbiter.OWNER_WIDGET && ownsDragOrHold() && !parentsFree) {
            // Already held: once more, so the list lets go of a swipe it was still tracking.
            if (parentsHeld) host.disallowParentIntercept(true);
            else holdParents();
        }
    }

    private boolean ownsDragOrHold() {
        int gesture = WidgetGestureArbiter.gesture(verdict);
        return WidgetGestureArbiter.owner(verdict) == WidgetGestureArbiter.OWNER_WIDGET
                && (gesture == WidgetGestureArbiter.DRAG_X || gesture == WidgetGestureArbiter.DRAG_Y || gesture == WidgetGestureArbiter.HOLD);
    }

    private void scheduleHold() {
        main.removeCallbacks(holdTick);
        double deadline = arbiter.holdDeadline();
        if (pressOpen && deadline >= 0) main.postDelayed(holdTick, Math.max(0, (long) Math.ceil(deadline) - SystemClock.uptimeMillis()));
    }

    private void onHoldTick() {
        if (!pressOpen || !claimed) return;
        apply(arbiter.tick(SystemClock.uptimeMillis()));
        scheduleHold();
    }

    /**
     * The chat's recognizers wait: not on the DOWN, which the list hands to its cell before it starts tracking a
     * swipe or a long press of its own, but from the first move, or once the widget claims something.
     */
    private void holdParents() {
        if (parentsHeld || parentsFree) return;
        parentsHeld = true;
        host.disallowParentIntercept(true);
    }

    private void freeParents() {
        if (parentsFree) return;
        parentsFree = true;
        if (parentsHeld) host.disallowParentIntercept(false);
    }

    /** The open press ends for the widget with a cancel, unless it ended for it already. */
    private void endWidgetPress() {
        // The widget never got this press's DOWN: nothing to cancel.
        if (downPending) {
            downPending = false;
            widgetCancelled = true;
        }
        if (pressOpen && !widgetCancelled && message != null) {
            widgetCancelled = true;
            message.touch(3, lastX, lastY, 0, true, pressMouse, pressModifiers, pressButton);
        }
    }

    private void endPress() {
        main.removeCallbacks(claimTimeout);
        main.removeCallbacks(holdTick);
        pressOpen = false;
        claimed = false;
    }

    /** The press's DOWN, held for the real event (downPending), goes to the widget now. */
    private void flushDown() {
        if (!downPending) return;
        downPending = false;
        if (message != null) message.touch(0, pressX, pressY, 0, true, pressMouse, pressModifiers, pressButton);
    }

    /** The arbiter's x: from the widget's leading edge, the right one in a right-to-left UI. */
    private float leading(float x) {
        return LocaleController.isRTL && drawnWidth > 0 ? drawnWidth / density - x : x;
    }

    /**
     * The widget keeps the current press from the message menu: it claimed holds (touch-action: none), or
     * it has a drag or a hold the arbiter gave it.
     */
    public boolean blocksMessageMenu() {
        if (!pressOpen || WidgetGestureArbiter.owner(verdict) == WidgetGestureArbiter.OWNER_BUBBLE) return false;
        return claimHold || ownsDragOrHold();
    }

    /**
     * A horizontal drag of the current press may still be the bubble's swipe-to-reply: its claim is not known
     * yet, or it claims no horizontal drags and the arbiter has not given the press to the widget.
     */
    public boolean mayGiveSwipe() {
        // A mouse drag never pans the chat (docs/sdk-v1.md §5.5).
        if (!pressOpen || pressMouse) return false;
        if (!claimed) return true;
        return !claimX && WidgetGestureArbiter.owner(verdict) != WidgetGestureArbiter.OWNER_WIDGET;
    }

    /** MotionEvent#getDownTime of the last press on the widget: the cell tells its presses apart with it. */
    public long lastDownTime() {
        return lastDownTime;
    }

    // ── touches ──

    /**
     * A touch in widget px (the origin of {@link #draw}). From its first move the parents may not intercept until
     * the widget's claim is known, at most {@link #CLAIM_WAIT_MS}, then as the gesture policy decides; false when
     * its scripts do not run.
     */
    public boolean onTouchEvent(MotionEvent event) {
        int masked = event.getActionMasked();
        // The release of an open press goes through even while the widget restarts: it ends what the DOWN began.
        boolean ending = pressOpen && (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL);
        if (message == null || previewing || !message.scriptsRunning() && !ending) return false;
        // A cancel after the press ended (its release went through): nothing of it is left to cancel.
        if (masked == MotionEvent.ACTION_CANCEL && !pressOpen) return false;
        int action;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                action = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                action = 1;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                action = 2;
                break;
            case MotionEvent.ACTION_CANCEL:
                action = 3;
                break;
            default:
                return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // A chat list hands a DOWN to its cell twice (item touch listener, then dispatch).
            long now = SystemClock.uptimeMillis();
            if (now - lastDownAt < DUPLICATE_DOWN_MS
                    && Math.abs(event.getX() - lastDownX) < 1
                    && Math.abs(event.getY() - lastDownY) < 1) {
                // The list's copy came first (RecyclerListView: no down time, no tool type): the real one tells the
                // press's down time (ChatMessageCell#isWidgetPress) and whether it is a mouse's.
                if (event.getDownTime() != 0) lastDownTime = event.getDownTime();
                if (!pressMouse && event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE && !claimed) {
                    pressMouse = true;
                    pressButton = (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0 ? 2 : 0;
                    pressModifiers = modifiers(event);
                    endHover();
                }
                flushDown();
                return true;
            }
            // A DOWN while the last press never saw its release: that press ends first.
            endWidgetPress();
            lastDownAt = now;
            lastDownTime = event.getDownTime();
            lastDownX = event.getX();
            lastDownY = event.getY();
            endPress();
            pressOpen = true;
            pressMouse = event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE;
            pressButton = pressMouse && (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0 ? 2 : 0;
            pressModifiers = modifiers(event);
            if (pressMouse) endHover();
            downPending = event.getDownTime() == 0 && event.getToolType(0) == MotionEvent.TOOL_TYPE_UNKNOWN;
            widgetCancelled = false;
            holdTold = false;
            parentsHeld = false;
            parentsFree = false;
            verdict = WidgetGestureArbiter.NONE;
            pressAt = now;
            pressX = lastX = event.getX() / density;
            pressY = lastY = event.getY() / density;
            main.postDelayed(claimTimeout, CLAIM_WAIT_MS);
        } else if (pressOpen && event.getActionMasked() != MotionEvent.ACTION_POINTER_DOWN && event.getActionMasked() != MotionEvent.ACTION_POINTER_UP) {
            lastX = event.getX() / density;
            lastY = event.getY() / density;
        }
        // The bubble took the press: the widget heard its pointercancel and gets nothing more of it.
        if (masked != MotionEvent.ACTION_DOWN) flushDown();
        if (!downPending && (!widgetCancelled || masked == MotionEvent.ACTION_DOWN)) {
            int first = action == 1 || action == 3 ? 0 : event.getActionIndex();
            int end = action == 1 || action == 3 ? event.getPointerCount() : first + 1;
            for (int i = first; i < end; i++) {
                boolean mouse = i == 0 && pressOpen ? pressMouse : event.getToolType(i) == MotionEvent.TOOL_TYPE_MOUSE;
                message.touch(
                        action,
                        event.getX(i) / density,
                        event.getY(i) / density,
                        event.getPointerId(i),
                        i == 0,
                        mouse,
                        modifiers(event),
                        mouse && i == 0 && pressOpen ? pressButton : 0);
            }
        }
        if (pressOpen) {
            long now = SystemClock.uptimeMillis();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (claimed) apply(arbiter.move(now, leading(lastX), lastY));
                    else holdParents();
                    break;
                case MotionEvent.ACTION_UP:
                    // A tap faster than its claim: the arbiter still counts it, for a double tap.
                    if (!claimed) onClaim(false, false, false);
                    apply(arbiter.up(now, leading(lastX), lastY));
                    endPress();
                    freeParents();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    arbiter.cancel();
                    endPress();
                    freeParents();
                    break;
            }
        }
        return true;
    }

    // ── a mouse over the widget ──

    /**
     * A hover event of a mouse in widget px (the origin of {@link #draw}), `inside` the widget or not: :hover and the
     * over/out events while it moves over the widget with no button down, and the cursor (docs/sdk-v1.md §5.5).
     */
    public void onHoverEvent(MotionEvent event, boolean inside) {
        int masked = event.getActionMasked();
        boolean over = inside && masked != MotionEvent.ACTION_HOVER_EXIT && message != null && !previewing && message.scriptsRunning();
        if (!over) {
            endHover();
            return;
        }
        hovering = true;
        hoverX = event.getX() / density;
        hoverY = event.getY() / density;
        message.hover(0, hoverX, hoverY, modifiers(event));
    }

    /** The mouse left the widget (or its press began, or the widget went): it hears so, the cursor is the cell's again. */
    private void endHover() {
        if (!hovering) return;
        hovering = false;
        if (message != null && !previewing) message.hover(1, hoverX, hoverY, 0);
        host.setCursor("auto");
    }

    /** The widget's CSS cursor where the mouse is (WidgetSandboxSession.Listener#onCursor). */
    public void onCursor(String cursor) {
        if (hovering || pressOpen && pressMouse) host.setCursor(cursor);
    }

    /** A MotionEvent's modifier keys as the renderer takes them: bit0 alt, bit1 ctrl, bit2 meta, bit3 shift. */
    private static int modifiers(MotionEvent event) {
        int meta = event.getMetaState();
        return ((meta & android.view.KeyEvent.META_ALT_ON) != 0 ? 1 : 0)
                | ((meta & android.view.KeyEvent.META_CTRL_ON) != 0 ? 2 : 0)
                | ((meta & android.view.KeyEvent.META_META_ON) != 0 ? 4 : 0)
                | ((meta & android.view.KeyEvent.META_SHIFT_ON) != 0 ? 8 : 0);
    }
}
