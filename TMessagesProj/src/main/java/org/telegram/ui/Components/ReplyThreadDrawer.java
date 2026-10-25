package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;

/**
 * "Reply thread": tapping a reply quote pulls a living thread out of the quote. The thread is a
 * damped elastic string (a 1D wave equation on {@link #N} nodes) laid over a Bezier guide that runs
 * along the left gutter of the chat. It is launched with a whip, lags behind the messages while the
 * list scrolls (inertia is derived from how the two anchors really move, so it also works with the
 * translation-based fast scroll), snaps taut when it ties to the original message (a wave runs back
 * along the string, the knot pops on a spring, sparks fly, a glint runs through the thread) and
 * then fades out. A mini-map rail on the right edge shows the whole chain of replies and the
 * visible window.
 *
 * The drawer is not a View: the chat list calls {@link #draw(Canvas)} at the end of its dispatchDraw
 * and {@link #onScrolled(int)} from its scroll listener. Nothing is allocated while drawing.
 */
public class ReplyThreadDrawer {

    /** Gives the drawer what it cannot know itself: the chat contents. */
    public interface ChainProvider {
        /** @return id of the message that {@code messageId} replies to, or 0 if unknown. */
        int getReplyTarget(int messageId);

        /** @return position of the message in the whole chat: 0 = oldest/top, 1 = newest/bottom, -1 = unknown. */
        float getMessageFraction(int messageId);

        /** Fills out[0], out[1] with the top and bottom of the visible window in the same units. */
        void getViewport(float[] out);
    }

    private static final int N = 36;              // string nodes
    private static final int MAX_CHAIN = 8;
    private static final int BURST = 14;          // sparks of the snap
    private static final int PARTICLES = 48;      // burst + friction sparks
    private static final int SAMPLES = 72;        // samples of the smoothed rope for the tapered body

    // string physics (units: px, seconds)
    private static final float STEP = 1f / 120f;
    private static final float WAVE = 2400f;      // neighbour coupling: wave speed^2
    private static final float RESTORE = 34f;     // pull back to the guide
    private static final float DAMPING = 4.0f;
    private static final float BEND = 420f;       // bending stiffness (beam term): ripples disperse like on a cable
    private static final float VISCOSITY = 30f;   // neighbours share velocity: smooth, not jittery
    private static final float INERTIA = 0.45f;   // how much the string lags behind a moving anchor

    private static final long DRAW_IN = 520;
    private static final long HOLD = 1300;
    private static final long FADE = 450;
    private static final long GLINT = 620;
    private static final long PULSE = 640;
    private static final long RAIL_EXTRA = 700;
    private static final long CHAIN_REFRESH = 400;
    private static final long GIVE_UP_NOT_FOUND = 4500;
    private static final long GIVE_UP_ABSOLUTE = 12000;

    private final RecyclerListView list;
    private final Theme.ResourcesProvider resourcesProvider;
    private final ChainProvider chainProvider;

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path path = new Path();
    private final Path glint = new Path();
    private final Path rail = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final RectF rect = new RectF();
    private final float[] pos = new float[2];

    // string state: offsets from the guide and their velocities, and the resulting points
    private final float[] ox = new float[N];
    private final float[] oy = new float[N];
    private final float[] vx = new float[N];
    private final float[] vy = new float[N];
    private final float[] px = new float[N];
    private final float[] py = new float[N];
    private float accumulator;

    // sparks
    private final float[] sx = new float[PARTICLES];
    private final float[] sy = new float[PARTICLES];
    private final float[] svx = new float[PARTICLES];
    private final float[] svy = new float[PARTICLES];
    private final float[] slife = new float[PARTICLES];
    private float sparkCooldown;

    // samples of the smoothed rope
    private final float[] spx = new float[SAMPLES];
    private final float[] spy = new float[SAMPLES];

    // chain of replies for the mini-map
    private final int[] chainIds = new int[MAX_CHAIN];
    private final float[] chainFrac = new float[MAX_CHAIN];
    private final float[] viewport = new float[2];
    private int chainCount;
    private long lastChainRefresh;

    private boolean active;
    private int sourceId;
    private int targetId;
    private boolean targetAbove;

    private long startTime;
    private long lastScrollTime;
    private long connectedTime;
    private long lastFrameTime;

    // anchor of the source (list coordinates) and the previous anchors, for the inertia
    private float srcX, srcY;
    private float prevSrcX, prevSrcY, prevTx, prevTy;
    private boolean hasPrev;

    // knot spring (0 -> 1 with an overshoot)
    private float knot, knotVelocity;

    public ReplyThreadDrawer(RecyclerListView list, Theme.ResourcesProvider resourcesProvider, ChainProvider chainProvider) {
        this.list = list;
        this.resourcesProvider = resourcesProvider;
        this.chainProvider = chainProvider;

        for (Paint p : new Paint[]{shadowPaint, glowPaint, bodyPaint, lightPaint, ringPaint, railPaint}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        fillPaint.setStyle(Paint.Style.FILL);
    }

    public boolean isActive() {
        return active;
    }

    public void cancel() {
        if (active) {
            active = false;
            list.invalidate();
        }
    }

    /**
     * @param cell        the message that contains the tapped reply quote
     * @param targetId    id of the message the quote points to
     * @param targetAbove true if the target is above the source in the list
     */
    public void start(ChatMessageCell cell, int targetId, boolean targetAbove) {
        MessageObject object = cell == null ? null : cell.getMessageObject();
        if (object == null || cell.replyNameLayout == null && cell.replyTextLayout == null) {
            return;
        }
        this.sourceId = object.getId();
        this.targetId = targetId;
        this.targetAbove = targetAbove;
        final long now = SystemClock.uptimeMillis();
        this.startTime = now;
        this.lastScrollTime = now;
        this.lastFrameTime = 0;
        this.connectedTime = 0;
        this.hasPrev = false;
        this.accumulator = 0;
        this.knot = 0;
        this.knotVelocity = 0;
        for (int i = 0; i < PARTICLES; i++) {
            slife[i] = 0;
        }
        // the whip: the whole string is flicked sideways and rings down on its own
        for (int i = 0; i < N; i++) {
            final float t = i / (float) (N - 1);
            ox[i] = 0;
            oy[i] = 0;
            vx[i] = dp(260) * (float) Math.sin(Math.PI * t);
            vy[i] = 0;
        }
        updateSource(cell);
        buildChain();
        this.active = true;
        list.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        list.invalidate();
    }

    public void onScrolled(int dy) {
        if (!active) {
            return;
        }
        lastScrollTime = SystemClock.uptimeMillis();
        srcY -= dy;
    }

    private void updateSource(ChatMessageCell cell) {
        srcX = cell.getX() + cell.replyStartX;
        srcY = cell.getY() + cell.replyStartY + cell.replyHeight / 2f;
    }

    private ChatMessageCell findCell(int messageId) {
        final int count = list.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = list.getChildAt(i);
            if (child instanceof ChatMessageCell) {
                final ChatMessageCell cell = (ChatMessageCell) child;
                final MessageObject object = cell.getMessageObject();
                if (object != null && object.getId() == messageId) {
                    return cell;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- chain / mini-map

    private void buildChain() {
        chainCount = 0;
        if (chainProvider == null) {
            return;
        }
        chainIds[chainCount++] = sourceId;
        chainIds[chainCount++] = targetId;
        while (chainCount < MAX_CHAIN) {
            final int next = chainProvider.getReplyTarget(chainIds[chainCount - 1]);
            if (next == 0) {
                break;
            }
            boolean seen = false;
            for (int i = 0; i < chainCount; i++) {
                if (chainIds[i] == next) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                break;
            }
            chainIds[chainCount++] = next;
        }
        refreshChain(SystemClock.uptimeMillis(), true);
    }

    private void refreshChain(long now, boolean force) {
        if (chainProvider == null || chainCount == 0) {
            return;
        }
        if (!force && now - lastChainRefresh < CHAIN_REFRESH) {
            return;
        }
        lastChainRefresh = now;
        for (int i = 0; i < chainCount; i++) {
            chainFrac[i] = chainProvider.getMessageFraction(chainIds[i]);
        }
        chainProvider.getViewport(viewport);
    }

    private void drawRail(Canvas canvas, int color, float alpha, long now, float reveal) {
        if (chainCount < 2 || chainProvider == null) {
            return;
        }
        refreshChain(now, false);
        final float top = list.getPaddingTop() + dp(14);
        final float bottom = list.getMeasuredHeight() - list.getPaddingBottom() - dp(14);
        // the narrow margin to the right of the bubbles, light on dark so it never melts into a blue bubble
        final float x = list.getMeasuredWidth() - dp(4.5f);
        if (bottom - top < dp(60)) {
            return;
        }
        final int light = ColorUtils.blendARGB(color, 0xFFFFFFFF, 0.8f);
        final float length = bottom - top;

        // track with a dark underlay
        railPaint.setStrokeWidth(dp(4));
        railPaint.setColor(ColorUtils.setAlphaComponent(0xFF000000, (int) (70 * alpha)));
        canvas.drawLine(x, top, x, bottom, railPaint);
        railPaint.setStrokeWidth(dp(2));
        railPaint.setColor(ColorUtils.setAlphaComponent(light, (int) (120 * alpha)));
        canvas.drawLine(x, top, x, bottom, railPaint);

        // visible window
        final float vTop = top + clamp01(viewport[0]) * length;
        final float vBottom = Math.max(top + clamp01(viewport[1]) * length, vTop + dp(3));
        railPaint.setStrokeWidth(dp(5.5f));
        railPaint.setColor(ColorUtils.setAlphaComponent(0xFF000000, (int) (80 * alpha)));
        canvas.drawLine(x, vTop, x, vBottom, railPaint);
        railPaint.setStrokeWidth(dp(3.5f));
        railPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, (int) (210 * alpha)));
        canvas.drawLine(x, vTop, x, vBottom, railPaint);

        // links between the neighbours of the chain, they appear one after another
        for (int i = 0; i + 1 < chainCount; i++) {
            if (chainFrac[i] < 0 || chainFrac[i + 1] < 0) {
                continue;
            }
            final float local = clamp01(reveal * (chainCount - 1) - i);
            if (local <= 0f) {
                continue;
            }
            final float y0 = top + chainFrac[i] * length;
            final float y1 = top + chainFrac[i + 1] * length;
            final float bulge = dp(18) + Math.min(dp(26), Math.abs(y1 - y0) * 0.14f);
            rail.reset();
            rail.moveTo(x, y0);
            rail.quadTo(x - bulge, (y0 + y1) / 2f, x, y1);
            measure.setPath(rail, false);
            final float arc = measure.getLength();
            if (arc < 1f) {
                continue;
            }
            glint.reset();
            measure.getSegment(0, arc * local, glint, true);
            glint.rLineTo(0, 0);
            railPaint.setStrokeWidth(dp(4));
            railPaint.setColor(ColorUtils.setAlphaComponent(0xFF000000, (int) (70 * alpha)));
            canvas.drawPath(glint, railPaint);
            railPaint.setStrokeWidth(dp(2.2f));
            railPaint.setColor(ColorUtils.setAlphaComponent(light, (int) (240 * alpha)));
            canvas.drawPath(glint, railPaint);
        }

        // dots: white with an accent core, dark outline
        for (int i = 0; i < chainCount; i++) {
            if (chainFrac[i] < 0) {
                continue;
            }
            final float local = clamp01(reveal * (chainCount - 1) - (i - 1));
            final float y = top + chainFrac[i] * length;
            final float r = (i == 1 ? dp(6f) : dp(4.6f)) * (i == 0 ? 1f : local);
            if (r <= 0f) {
                continue;
            }
            fillPaint.setColor(ColorUtils.setAlphaComponent(0xFF000000, (int) (90 * alpha)));
            canvas.drawCircle(x, y, r + dp(1.5f), fillPaint);
            fillPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, (int) (255 * alpha)));
            canvas.drawCircle(x, y, r, fillPaint);
            fillPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (255 * alpha)));
            canvas.drawCircle(x, y, r * 0.52f, fillPaint);
        }
    }

    // ---------------------------------------------------------------- physics

    private void step(float dt) {
        for (int i = 1; i < N - 1; i++) {
            final float lapX = ox[i - 1] + ox[i + 1] - 2f * ox[i];
            final float lapY = oy[i - 1] + oy[i + 1] - 2f * oy[i];
            // fourth difference: the bending stiffness of a real cable (the ends stay pinned at zero)
            final float farLx = i >= 2 ? ox[i - 2] : 0f;
            final float farRx = i <= N - 3 ? ox[i + 2] : 0f;
            final float farLy = i >= 2 ? oy[i - 2] : 0f;
            final float farRy = i <= N - 3 ? oy[i + 2] : 0f;
            final float bendX = farLx - 4f * ox[i - 1] + 6f * ox[i] - 4f * ox[i + 1] + farRx;
            final float bendY = farLy - 4f * oy[i - 1] + 6f * oy[i] - 4f * oy[i + 1] + farRy;
            vx[i] += (WAVE * lapX - BEND * bendX - RESTORE * ox[i]) * dt;
            vy[i] += (WAVE * lapY - BEND * bendY - RESTORE * oy[i]) * dt;
        }
        for (int i = 1; i < N - 1; i++) {
            final float damp = 1f - DAMPING * dt;
            vx[i] = vx[i] * damp + VISCOSITY * (vx[i - 1] + vx[i + 1] - 2f * vx[i]) * dt;
            vy[i] = vy[i] * damp + VISCOSITY * (vy[i - 1] + vy[i + 1] - 2f * vy[i]) * dt;
        }
        final float maxOffset = dp(72);
        for (int i = 1; i < N - 1; i++) {
            ox[i] += vx[i] * dt;
            oy[i] += vy[i] * dt;
            final float len2 = ox[i] * ox[i] + oy[i] * oy[i];
            if (len2 > maxOffset * maxOffset) {
                final float k = maxOffset / (float) Math.sqrt(len2);
                ox[i] *= k;
                oy[i] *= k;
                vx[i] *= 0.85f;
                vy[i] *= 0.85f;
            }
        }
        // the knot on the original message pops on a spring
        if (connectedTime != 0) {
            knotVelocity += (-380f * (knot - 1f) - 20f * knotVelocity) * dt;
            knot += knotVelocity * dt;
        }
    }

    private void spawnOne(float x, float y, float velX, float velY, float life) {
        for (int i = BURST; i < PARTICLES; i++) {
            if (slife[i] <= 0f) {
                sx[i] = x;
                sy[i] = y;
                svx[i] = velX;
                svy[i] = velY;
                slife[i] = life;
                return;
            }
        }
    }

    private void spawnSparks(float x, float y) {
        for (int i = 0; i < BURST; i++) {
            final double angle = (i + Math.random() * 0.7) / BURST * Math.PI * 2;
            final float speed = dp(80) + (float) Math.random() * dp(150);
            sx[i] = x;
            sy[i] = y;
            svx[i] = (float) Math.cos(angle) * speed;
            svy[i] = (float) Math.sin(angle) * speed;
            slife[i] = 1f;
        }
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static float clamp(float v, float limit) {
        return v < -limit ? -limit : (v > limit ? limit : v);
    }

    // ---------------------------------------------------------------- drawing

    public void draw(Canvas canvas) {
        if (!active) {
            return;
        }
        final long now = SystemClock.uptimeMillis();
        final long elapsed = now - startTime;
        float dtFrame = lastFrameTime == 0 ? 0f : Math.min(0.05f, (now - lastFrameTime) / 1000f);
        lastFrameTime = now;

        final ChatMessageCell sourceCell = findCell(sourceId);
        if (sourceCell != null) {
            updateSource(sourceCell);
        }
        final ChatMessageCell targetCell = findCell(targetId);
        final boolean found = targetCell != null;

        if (elapsed > GIVE_UP_ABSOLUTE || (!found && elapsed > GIVE_UP_NOT_FOUND && now - lastScrollTime > 600)) {
            active = false;
            return;
        }

        final float height = list.getMeasuredHeight();
        final float edge = dp(80);

        final float age = connectedTime == 0 ? 0f : (now - connectedTime);
        // loose and swinging at first, pulled taut once it is tied
        final float settle = connectedTime == 0 ? 0f : CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(clamp01(age / 380f));
        final float gutter = dp(7) + dp(14) * settle;

        float tx;
        float ty;
        if (found) {
            tx = targetCell.getX() + targetCell.getBackgroundDrawableLeft();
            ty = targetCell.getY() + Math.min(targetCell.getHeight() / 2f, dp(28));
        } else {
            tx = gutter;
            ty = targetAbove ? -dp(40) : height + dp(40);
        }
        final float srcYc = Math.max(-edge, Math.min(height + edge, srcY));
        ty = Math.max(-edge, Math.min(height + edge, ty));

        // both ends beyond the same edge: nothing to show yet
        if ((srcYc <= -edge + 1 && ty <= -edge + 1) || (srcYc >= height + edge - 1 && ty >= height + edge - 1)) {
            hasPrev = false;
            list.postInvalidateOnAnimation();
            return;
        }

        // inertia: the string does not follow an anchor that jumps, it lags and then rings
        if (hasPrev) {
            final float dSx = clamp(srcX - prevSrcX, dp(140));
            final float dSy = clamp(srcYc - prevSrcY, dp(140));
            final float dTx = clamp(tx - prevTx, dp(140));
            final float dTy = clamp(ty - prevTy, dp(140));
            for (int i = 1; i < N - 1; i++) {
                final float t = i / (float) (N - 1);
                final float w = (float) Math.sin(Math.PI * t) * INERTIA;
                ox[i] -= ((1f - t) * dSx + t * dTx) * w;
                oy[i] -= ((1f - t) * dSy + t * dTy) * w;
            }
        }
        prevSrcX = srcX;
        prevSrcY = srcYc;
        prevTx = tx;
        prevTy = ty;
        hasPrev = true;

        // integrate the string with a fixed time step
        accumulator += dtFrame;
        int guard = 0;
        while (accumulator >= STEP && guard++ < 8) {
            step(STEP);
            accumulator -= STEP;
        }
        if (guard >= 8) {
            accumulator = 0;
        }

        final float drawIn = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(clamp01(elapsed / (float) DRAW_IN));

        // tied: the head reached the end, the list is at rest and the target is where it should be
        if (connectedTime == 0 && found && drawIn >= 1f && Math.abs(targetCell.getTranslationY()) < 1f && now - lastScrollTime > 90) {
            connectedTime = now;
            knot = 0;
            knotVelocity = 0;
            // a snap: a wave runs back along the string
            vx[N - 2] += dp(760);
            vx[N - 3] += dp(420);
            vy[N - 2] += targetAbove ? dp(220) : -dp(220);
            spawnSparks(tx, ty);
            list.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }

        float alpha = 1f;
        float railAlpha = 1f;
        if (connectedTime != 0) {
            final float fadeAge = now - connectedTime - HOLD;
            if (fadeAge > 0) {
                alpha = 1f - fadeAge / FADE;
                railAlpha = 1f - (fadeAge - RAIL_EXTRA) / FADE;
                if (railAlpha <= 0f && alpha <= 0f) {
                    active = false;
                    return;
                }
                alpha = Math.max(0f, alpha);
                railAlpha = clamp01(railAlpha);
            }
        }

        final int accent = Theme.getColor(Theme.key_chat_messagePanelSend, resourcesProvider);
        final int light = ColorUtils.blendARGB(accent, 0xFFFFFFFF, 0.62f);

        // the guide curve (cubic through the gutter) plus the offsets of the string
        for (int i = 0; i < N; i++) {
            final float t = drawIn * i / (float) (N - 1);
            final float u = 1f - t;
            final float b0 = u * u * u;
            final float b1 = 3f * u * u * t;
            final float b2 = 3f * u * t * t;
            final float b3 = t * t * t;
            px[i] = b0 * srcX + (b1 + b2) * gutter + b3 * tx + ox[i];
            py[i] = (b0 + b1) * srcYc + (b2 + b3) * ty + oy[i];
        }
        path.reset();
        path.moveTo(px[0], py[0]);
        for (int i = 1; i < N - 1; i++) {
            path.quadTo(px[i], py[i], (px[i] + px[i + 1]) / 2f, (py[i] + py[i + 1]) / 2f);
        }
        path.lineTo(px[N - 1], py[N - 1]);

        shadowPaint.setStrokeWidth(dp(6.5f));
        shadowPaint.setColor(ColorUtils.setAlphaComponent(0xFF000000, (int) (52 * alpha)));
        glowPaint.setStrokeWidth(dp(11));
        glowPaint.setColor(ColorUtils.setAlphaComponent(accent, (int) (34 * alpha)));
        bodyPaint.setStrokeWidth(dp(3.4f));
        bodyPaint.setColor(ColorUtils.setAlphaComponent(accent, (int) (255 * alpha)));
        lightPaint.setStrokeWidth(dp(1.3f));
        lightPaint.setColor(ColorUtils.setAlphaComponent(light, (int) (220 * alpha)));

        canvas.drawPath(path, shadowPaint);
        canvas.drawPath(path, glowPaint);
        glowPaint.setStrokeWidth(dp(7));
        glowPaint.setColor(ColorUtils.setAlphaComponent(accent, (int) (58 * alpha)));
        canvas.drawPath(path, glowPaint);

        measure.setPath(path, false);
        final float ropeLength = measure.getLength();
        if (ropeLength > 1f) {
            // body: thick in the middle, thin at both ends, drawn along the smoothed curve
            for (int k = 0; k < SAMPLES; k++) {
                measure.getPosTan(ropeLength * k / (SAMPLES - 1), pos, null);
                spx[k] = pos[0];
                spy[k] = pos[1];
            }
            for (int k = 0; k < SAMPLES - 1; k++) {
                final float u = (k + 0.5f) / (SAMPLES - 1);
                final float taper = 0.5f + 0.5f * (float) Math.pow(Math.sin(Math.PI * u), 0.6);
                bodyPaint.setStrokeWidth(dp(3.8f) * taper);
                canvas.drawLine(spx[k], spy[k], spx[k + 1], spy[k + 1], bodyPaint);
            }
            for (int k = 0; k < SAMPLES - 1; k++) {
                final float u = (k + 0.5f) / (SAMPLES - 1);
                final float taper = 0.5f + 0.5f * (float) Math.pow(Math.sin(Math.PI * u), 0.6);
                lightPaint.setStrokeWidth(dp(1.5f) * taper);
                canvas.drawLine(spx[k], spy[k], spx[k + 1], spy[k + 1], lightPaint);
            }
            // energy keeps flowing from the quote to the original
            final float flow = (now % 1500L) / 1500f;
            for (int k = 0; k < 3; k++) {
                final float u = (flow + k / 3f) % 1f;
                final float centre = ropeLength * u;
                glint.reset();
                measure.getSegment(Math.max(0f, centre - ropeLength * 0.045f), Math.min(ropeLength, centre + ropeLength * 0.045f), glint, true);
                glint.rLineTo(0, 0);
                lightPaint.setStrokeWidth(dp(2.4f));
                lightPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, (int) (190 * alpha * Math.sin(Math.PI * u))));
                canvas.drawPath(glint, lightPaint);
            }
            lightPaint.setColor(ColorUtils.setAlphaComponent(light, (int) (220 * alpha)));
        }

        // friction sparks fly off the fast parts of the string
        sparkCooldown -= dtFrame;
        if (sparkCooldown <= 0f) {
            final float fast = dp(600);
            for (int i = 3; i < N - 3; i += 3) {
                if (vx[i] * vx[i] + vy[i] * vy[i] > fast * fast) {
                    spawnOne(px[i], py[i],
                        vx[i] * 0.25f + (float) (Math.random() - 0.5) * dp(90),
                        vy[i] * 0.25f + (float) (Math.random() - 0.5) * dp(90), 0.7f);
                    sparkCooldown = 0.03f;
                    break;
                }
            }
        }

        // a glint runs through the tied thread, from the quote to the original
        if (connectedTime != 0 && age < GLINT) {
            measure.setPath(path, false);
            final float length = measure.getLength();
            if (length > 1f) {
                final float u = CubicBezierInterpolator.EASE_OUT.getInterpolation(clamp01(age / (float) GLINT));
                final float to = length * u;
                final float from = Math.max(0f, to - length * 0.22f);
                glint.reset();
                measure.getSegment(from, to, glint, true);
                glint.rLineTo(0, 0);
                lightPaint.setStrokeWidth(dp(2.6f));
                lightPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, (int) (255 * alpha * (1f - u * 0.4f))));
                canvas.drawPath(glint, lightPaint);
            }
        }

        // knot on the quote
        fillPaint.setColor(ColorUtils.setAlphaComponent(accent, (int) (255 * alpha)));
        canvas.drawCircle(srcX, srcYc, dp(3.6f), fillPaint);
        fillPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, (int) (235 * alpha)));
        canvas.drawCircle(srcX, srcYc, dp(1.5f), fillPaint);

        if (connectedTime == 0) {
            // head of the thread: a small comet
            final float hx = px[N - 1];
            final float hy = py[N - 1];
            fillPaint.setColor(ColorUtils.setAlphaComponent(accent, (int) (46 * alpha)));
            canvas.drawCircle(hx, hy, dp(13), fillPaint);
            fillPaint.setColor(ColorUtils.setAlphaComponent(accent, (int) (110 * alpha)));
            canvas.drawCircle(hx, hy, dp(7.5f), fillPaint);
            fillPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, (int) (255 * alpha)));
            canvas.drawCircle(hx, hy, dp(3.6f), fillPaint);
        } else {
            // knot on the original message, rings and sparks
            final float r = dp(4.2f) * Math.max(0f, knot);
            fillPaint.setColor(ColorUtils.setAlphaComponent(accent, (int) (255 * alpha)));
            canvas.drawCircle(tx, ty, r, fillPaint);
            fillPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, (int) (235 * alpha)));
            canvas.drawCircle(tx, ty, r * 0.4f, fillPaint);

            final float pulse = clamp01(age / (float) PULSE);
            if (pulse < 1f) {
                final float eased = CubicBezierInterpolator.EASE_OUT.getInterpolation(pulse);
                ringPaint.setStrokeWidth(dp(2.2f) * (1f - pulse) + dp(0.6f));
                ringPaint.setColor(ColorUtils.setAlphaComponent(light, (int) (200 * (1f - pulse) * alpha)));
                canvas.drawCircle(tx, ty, dp(5) + dp(24) * eased, ringPaint);
                final float second = clamp01((age - 110f) / (float) PULSE);
                if (second > 0f && second < 1f) {
                    ringPaint.setColor(ColorUtils.setAlphaComponent(accent, (int) (150 * (1f - second) * alpha)));
                    ringPaint.setStrokeWidth(dp(1.6f) * (1f - second) + dp(0.5f));
                    canvas.drawCircle(tx, ty, dp(5) + dp(34) * CubicBezierInterpolator.EASE_OUT.getInterpolation(second), ringPaint);
                }
            }
        }

        // sparks
        for (int i = 0; i < PARTICLES; i++) {
            if (slife[i] <= 0f) {
                continue;
            }
            slife[i] -= dtFrame * 1.9f;
            final float drag = Math.max(0f, 1f - 4.2f * dtFrame);
            svx[i] *= drag;
            svy[i] *= drag;
            sx[i] += svx[i] * dtFrame;
            sy[i] += svy[i] * dtFrame;
            if (slife[i] > 0f) {
                fillPaint.setColor(ColorUtils.setAlphaComponent(light, (int) (255 * slife[i] * alpha)));
                canvas.drawCircle(sx[i], sy[i], dp(2f) * (0.4f + slife[i] * 0.6f), fillPaint);
            }
        }

        drawRail(canvas, accent, railAlpha, now, clamp01(elapsed / 700f));

        list.postInvalidateOnAnimation();
    }
}
