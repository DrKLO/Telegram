package org.telegram.ui.QrShare;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Sits on top of the emoji keyboard inside the floating sheet:
 * - pick-order badges (1…6) on the picked emoji cells, popping in and cross-fading when they renumber;
 * - a small glass island at the bottom holding the picked emoji in order; a tap removes one.
 */
final class EmojiPickOverlay extends View {

    interface Host {
        List<String> pickedKeys();

        Drawable emojiFor(String key);

        void removePickAt(int index);

        int islandTint();

        /** the shared blurred backdrop at this overlay's origin offset (dx, dy from the QR view); false = none */
        boolean drawBlur(Canvas c, float dx, float dy);

        /** the QR view's screen position */
        void qrViewLocation(int[] out);
    }

    private final Host host;
    private ViewGroup emojiView;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint num = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int[] loc = new int[2], myLoc = new int[2];

    // badge state per key: shown number, previous number, change time, appear time
    private final HashMap<String, float[]> badges = new HashMap<>();

    // island items
    private static final class Item {
        String key;
        float x = Float.NaN, scale, target = 1f;
        boolean leaving;
    }

    private final ArrayList<Item> items = new ArrayList<>();
    private final ArrayList<Item> matched = new ArrayList<>(), next = new ArrayList<>();
    private float islandW, islandShow, islandAlpha;
    private long lastIslandFrame;

    private static float lerpF(float a, float b, float t) {return a + (b - a) * t;}

    private final RectF islandRect = new RectF();
    private static final int CELL = 34, PAD = 8;

    // Badge/selection times are kept in float arrays: an epoch in ms (~1.8e12) does not fit a float (steps of ~131 s),
    // so selection fades and digit cross-fades could stall for minutes (missing digits, stuck/bouncing selection).
    // All times here are ms since this class loaded.
    private static final long T0 = System.currentTimeMillis();

    private static long nowMs() {return System.currentTimeMillis() - T0;}

    EmojiPickOverlay(Context context, Host host) {
        super(context);
        this.host = host;
        num.setTypeface(AndroidUtilities.bold());
        num.setTextSize(dp(10));
        num.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
    }

    private float glassBottom = Float.NaN, lastCutShow = -1f;

    /** Where the grid's 10dp fade ends (overlay = keyboard coordinates): the glass bottom. The picks island no longer
     *  cuts a full-width band: the grid runs on around it and only the island's own shape is cut (see islandCut). */
    float gridCutY(float glassBottomY) {
        glassBottom = glassBottomY;
        return glassBottomY;
    }

    /** The island's shape to cut out of the grid (keyboard coordinates) and how much (0…1); false = no island. */
    boolean islandCut(RectF out, float[] radiusAlpha) {
        if (islandShow <= 0.003f || islandRect.isEmpty()) return false;
        out.set(islandRect);
        radiusAlpha[0] = islandRect.height() / 2f;
        radiusAlpha[1] = islandAlpha;
        return true;
    }

    private final RimPainter rim = new RimPainter();
    private final android.graphics.Path badgeClip = new android.graphics.Path();
    private float topClipY = Float.NaN;

    /** the packs island's bottom (keyboard coordinates): badges are clipped above it */
    void setTopClip(float y) {
        if (topClipY != y) {
            topClipY = y;
            invalidate();
        }
    }

    private final RectF lastCutRect = new RectF();

    @Override
    public boolean hasOverlappingRendering() {
        return false; // fades per draw (no offscreen layer), so the rim's blend sees the real pixels under it
    }

    /** Grid fade multiplier at y (overlay coordinates): badges fade with the cells they sit on (smoothstep, 10dp). */
    float fadeAt(float y) {
        if (Float.isNaN(glassBottom)) return 1f;
        final float cut = gridCutY(glassBottom), fh = dp(10);
        final float u = Math.max(0f, Math.min(1f, (y - (cut - fh)) / fh));
        return 1f - u * u * (3f - 2f * u);
    }

    void attach(ViewGroup v) {
        emojiView = v;
    }

    private static String keyOf(QrEmojiPanel.Cell v) {
        return v.getKey();
    }

    private static float easeOutBack(float u) {
        if (u >= 1f) return 1f;
        final float c1 = 1.4f, c3 = c1 + 1f;
        u -= 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    @Override
    protected void onDraw(Canvas c) {
        final List<String> picked = host.pickedKeys();
        final long now = nowMs();
        boolean animating = false;

        // ---- order badges on the visible picked cells
        if (emojiView != null) {
            getLocationOnScreen(myLoc);
            // badges belong to the grid layer: under the packs island (top) and under the picks island (its
            // translucent glass would show them through otherwise)
            c.save();
            if (!Float.isNaN(topClipY)) c.clipRect(0, topClipY, getWidth(), getHeight());
            if (islandShow > 0.003f && !islandRect.isEmpty()) {
                badgeClip.rewind();
                badgeClip.addRoundRect(islandRect, islandRect.height() / 2f, islandRect.height() / 2f, android.graphics.Path.Direction.CW);
                QrRender.clipOut(c, badgeClip);
            }
            animating |= drawBadges(c, emojiView, picked, now);
            c.restore();
        }

        // ---- our scrollbar (the grid's own is off)
        animating |= drawScrollbar(c, now);

        // ---- the island
        syncItems(picked);
        final int n = countTargets();
        final float targetW = n == 0 ? dp(CELL + PAD * 2) : n * dp(CELL) + dp(PAD * 2);
        islandW = Float.isNaN(islandW) || islandW == 0 ? targetW : islandW + (targetW - islandW) * 0.25f;
        final float showTarget = n > 0 ? 1f : 0f;
        // enter / exit about the island's centre, no vertical motion (260 ms, ease-in-out, interruptible from where it
        // is): exit = the width collapses to a circle (content shrinks with it), then the circle scales to 0.6 and
        // fades in the last 40%; enter is the mirror. Count changes animate the width around the centre too.
        final long islNow = nowMs();
        final float islDt = lastIslandFrame == 0 ? 16f : Math.min(64f, islNow - lastIslandFrame);
        lastIslandFrame = islNow;
        if (islandShow < showTarget) islandShow = Math.min(showTarget, islandShow + islDt / 260f);
        else if (islandShow > showTarget) islandShow = Math.max(showTarget, islandShow - islDt / 260f);
        if (islandShow > 0.003f || !items.isEmpty()) {
            final float h = dp(CELL + 10);
            final float cx = getWidth() / 2f;
            final float e = islandShow < 0.5f ? 2f * islandShow * islandShow : 1f - (float) Math.pow(-2f * islandShow + 2f, 2) / 2f;
            final float wv = e >= 0.4f ? lerpF(h, islandW, (e - 0.4f) / 0.6f) : h;
            final float sc = e >= 0.4f ? 1f : lerpF(0.6f, 1f, e / 0.4f);
            final float a = e >= 0.4f ? 1f : e / 0.4f;
            islandAlpha = a;
            final float contentK = sc * Math.min(1f, wv / Math.max(1f, islandW));
            // fully inside the sheet: 22dp above the glass bottom, lowered by 10 px on a 3.0-density phone (10/3 dp)
            final float bottom = getHeight() - dpf2(22f - 10f / 3f);
            final float cy = bottom - h / 2f;
            islandRect.set(cx - wv * sc / 2f, cy - h * sc / 2f, cx + wv * sc / 2f, cy + h * sc / 2f);
            final float r = islandRect.height() / 2f;
            // glass: backdrop-hue tint + see-through white + light rim (matches the screen's glass)
            fill.setColor(host.islandTint());
            fill.setAlpha((int) (Color.alpha(host.islandTint()) * a));
            c.drawRoundRect(islandRect, r, r, fill);
            fill.setColor(0x33FFFFFF);
            fill.setAlpha((int) (0x33 * a));
            c.drawRoundRect(islandRect, r, r, fill);
            // the Dark out rim, like every other glass button (Big group)
            rim.draw(c, islandRect, r, RimTune.BIG, a);
            // items: slide together, grow in, shrink out
            c.save();
            c.clipRect(islandRect);
            int k = 0;
            for (int i = 0; i < items.size(); i++) {
                final Item it = items.get(i);
                final float tx = cx - islandW / 2f + dp(PAD) + dp(CELL) * (k + 0.5f);
                if (!it.leaving) k++;
                if (Float.isNaN(it.x)) it.x = tx;
                if (!it.leaving) it.x += (tx - it.x) * 0.3f;
                it.scale += ((it.leaving ? 0f : 1f) - it.scale) * 0.28f;
                if (Math.abs(it.scale - (it.leaving ? 0f : 1f)) > 0.004f || Math.abs(tx - it.x) > 0.5f)
                    animating = true;
                final Drawable d = host.emojiFor(it.key);
                if (d == null) continue;
                final float s = dp(26) * (it.leaving ? it.scale : easeOutBack(it.scale)) * contentK;
                if (s < 1f) continue;
                d.setAlpha(255);
                final float ix = cx + (it.x - cx) * contentK; // positions collapse toward the centre with the width
                d.setBounds((int) (ix - s / 2), (int) (islandRect.centerY() - s / 2), (int) (ix + s / 2), (int) (islandRect.centerY() + s / 2));
                QrEmojiPanel.drawEmoji(c, d);
            }
            c.restore();
            for (int i = items.size() - 1; i >= 0; i--)
                if (items.get(i).leaving && items.get(i).scale < 0.02f) items.remove(i);
            if (Math.abs(islandW - targetW) > 0.5f || Math.abs(islandShow - showTarget) > 0.003f) animating = true;
        }
        if (islandShow != lastCutShow || !islandRect.equals(lastCutRect)) {
            lastCutShow = islandShow;
            lastCutRect.set(islandRect);
            if (emojiView != null) emojiView.invalidate(); // the grid's fade follows the island's top
        }
        if (animating || !items.isEmpty() || !picked.isEmpty()) postInvalidateOnAnimation();
    }

    // selection per key: {target(0/1), from, startTime, number, prevNumber, numberChangeTime}
    private static final long SEL_MS = 180;

    private static float easeOutCubic(float u) {
        u = Math.max(0f, Math.min(1f, u));
        return 1f - (1f - u) * (1f - u) * (1f - u);
    }

    /** Called by the emoji grid before its cells: the white 10% rounded backgrounds (r 10dp), under the emoji. */
    void drawUnder(Canvas c, ViewGroup grid) {
        final long now = nowMs();
        for (int i = 0; i < grid.getChildCount(); i++) {
            final View ch = grid.getChildAt(i);
            if (!(ch instanceof QrEmojiPanel.Cell) || ch.getVisibility() != VISIBLE) continue;
            // the tap ripple: white 18%, bounded to exactly the selection's rounded rect (it settles into it)
            if (!rippled.containsKey(ch)) {
                rippled.put(ch, Boolean.TRUE);
                final android.graphics.drawable.GradientDrawable m = new android.graphics.drawable.GradientDrawable();
                m.setColor(Color.WHITE);
                m.setCornerRadius(dp(SEL_R));
                ch.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(SEL_COLOR), null,
                    new android.graphics.drawable.InsetDrawable(m, (int) dpf2(SEL_INSET))));
            }
            final String key = keyOf((QrEmojiPanel.Cell) ch);
            if (key == null) continue;
            final float[] st = badges.get(key);
            if (st == null) continue;
            final float k = selK(st, now);
            if (k <= 0.004f) continue;
            // same bounds, radius and tone as the ripple; it only fades (the glyph goes to 90% on the same curve)
            // with the cell's translation: the jump to a far pack moves the cells by it, not by layout
            final float x0 = ch.getLeft() + ch.getTranslationX(), y0 = ch.getTop() + ch.getTranslationY(), cw = ch.getWidth(), chh = ch.getHeight();
            final float in = (int) dpf2(SEL_INSET);
            cellRect.set(x0 + in, y0 + in, x0 + cw - in, y0 + chh - in);
            // while the finger is down the ripple is the background; on release the ripple fades out as this fades in
            if (ch.isPressed()) continue;
            fill.setColor(SEL_COLOR);
            fill.setAlpha((int) (Color.alpha(SEL_COLOR) * k));
            c.drawRoundRect(cellRect, dp(SEL_R), dp(SEL_R), fill);
        }
    }

    private float selK(float[] st, long now) {
        final float u = Math.min(1f, (now - (long) st[2]) / (float) SEL_MS);
        return st[1] + (st[0] - st[1]) * easeOutCubic(u);
    }

    private final RectF cellRect = new RectF();
    private static final int SEL_COLOR = 0x2EFFFFFF, SEL_R = 10; // white 18%, r 10dp
    private static final float SEL_INSET = 2.5f;
    private final java.util.WeakHashMap<View, Boolean> rippled = new java.util.WeakHashMap<>();

    // counter badge: round glass (black 22% tint, 0.5dp rim white 45% at the top → 12% at the bottom), white digit
    private static final float BADGE_R = 8f;
    private final android.graphics.Path badgePath = new android.graphics.Path();
    private final int[] qrLoc = new int[2];
    private android.graphics.LinearGradient badgeRim;
    private final Paint badgeRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics badgeFm = new Paint.FontMetrics();
    private static final String[] DIGITS = new String[100];

    private static String digit(int n) {
        if (n < 0 || n >= DIGITS.length) return String.valueOf(n);
        String d = DIGITS[n];
        if (d == null) DIGITS[n] = d = String.valueOf(n);
        return d;
    }

    /** Under-damped spring step response from 0 to 1 (ζ, period in s), t in ms. */
    static float spring(float tMs, float zeta, float period) {
        final double t = tMs / 1000.0;
        if (t <= 0) return 0f;
        final double wd = 2 * Math.PI / period, wn = wd / Math.sqrt(1 - zeta * zeta);
        final double e = Math.exp(-zeta * wn * t);
        return (float) (1 - e * (Math.cos(wd * t) + zeta * wn / wd * Math.sin(wd * t)));
    }

    private void drawBadge(Canvas c, float bx, float by, float scale, float alpha, float[] st, long now) {
        if (alpha <= 0.004f || scale <= 0.01f) return;
        final float r = dp(BADGE_R);
        if (badgeRim == null) {
            badgeRim = new android.graphics.LinearGradient(0, -r, 0, r, 0x73FFFFFF, 0x1FFFFFFF, android.graphics.Shader.TileMode.CLAMP);
            badgeRimPaint.setStyle(Paint.Style.STROKE);
            badgeRimPaint.setShader(badgeRim);
        }
        c.save();
        c.translate(bx, by);
        c.scale(scale, scale);
        // glass: the shared blurred backdrop (clipped to the badge) + 6% white, then a dark tint so the white digit
        // reads over any emoji, then the Dark out rim (Small group)
        badgePath.rewind();
        badgePath.addCircle(0, 0, r, android.graphics.Path.Direction.CW);
        c.save();
        c.clipPath(badgePath);
        if (alpha < 0.999f) c.saveLayerAlpha(-r, -r, r, r, (int) (255 * alpha));
        host.qrViewLocation(qrLoc);
        // the canvas is translated + scaled here: map back to overlay space for the blur's offset
        c.scale(1f / scale, 1f / scale);
        c.translate(-bx, -by);
        host.drawBlur(c, myLoc[0] - qrLoc[0], myLoc[1] - qrLoc[1]);
        c.translate(bx, by);
        c.scale(scale, scale);
        // light glass like the buttons / packs island: the shared blur + 18% white, no dark tint, no Dark-out rim
        fill.setColor(0x2EFFFFFF);
        c.drawCircle(0, 0, r, fill);
        if (alpha < 0.999f) c.restore();
        c.restore();
        // one faint hairline (0.6dp, white 28%) so it separates from bright emoji
        stroke.setStrokeWidth(dpf2(0.6f));
        stroke.setColor(0x47FFFFFF);
        stroke.setAlpha((int) (0x47 * alpha));
        c.drawCircle(0, 0, r - dpf2(0.3f), stroke);
        num.setTextSize(dp(10));
        num.setColor(Color.WHITE);
        num.getFontMetrics(badgeFm);
        final Paint.FontMetrics fm = badgeFm;
        final float base = -(fm.ascent + fm.descent) / 2f;
        final float cross = st[5] == 0 ? 1f : Math.min(1f, (now - (long) st[5]) / 200f);
        if (cross < 1f) {
            final float ao = alpha * (1f - cross);
            num.setShadowLayer(dpf2(1.5f), 0, dpf2(0.5f), Color.argb((int) (0x59 * ao), 0, 0, 0));
            num.setAlpha((int) (255 * ao));
            c.drawText(digit((int) st[4]), 0, base - cross * dp(4), num);
        }
        final float an = alpha * cross;
        num.setShadowLayer(dpf2(1.5f), 0, dpf2(0.5f), Color.argb((int) (0x59 * an), 0, 0, 0)); // soft, keeps white digits legible on yellow
        num.setAlpha((int) (255 * an));
        c.drawText(digit((int) st[3]), 0, base + (1f - cross) * dp(4), num);
        num.clearShadowLayer();
        c.restore();
    }

    /**
     * Selected cells (all on one curve, deselect reverses): the cell keeps its emoji but at 0.9 scale (we hide the
     * cell's own draw and draw the same drawable — animated ones keep animating), a white 10% rounded background
     * and a round glass counter.
     */
    private boolean drawBadges(Canvas c, ViewGroup g, List<String> picked, long now) {
        boolean anim = false;
        for (int i = 0; i < g.getChildCount(); i++) {
            final View ch = g.getChildAt(i);
            if (ch.getVisibility() != VISIBLE) continue;
            if (ch instanceof QrEmojiPanel.Cell) {
                final String key = keyOf((QrEmojiPanel.Cell) ch);
                if (key == null) continue;
                final int first = picked.indexOf(key);
                float[] st = badges.get(key);
                if (first >= 0) {
                    int order = 0;
                    for (int k = 0; k < first; k++) if (picked.indexOf(picked.get(k)) == k) order++;
                    final int number = order + 1;
                    if (st == null) {
                        st = new float[]{1, 0, now, number, number, 0};
                        badges.put(key, st);
                    } else {
                        if (st[0] != 1) {
                            st[1] = selK(st, now);
                            st[0] = 1;
                            st[2] = now;
                        }
                        if (st[3] != number) {
                            st[4] = st[3];
                            st[3] = number;
                            st[5] = now;
                        }
                    }
                } else if (st != null && st[0] != 0) {
                    st[1] = selK(st, now);
                    st[0] = 0;
                    st[2] = now;
                }
                final float k = st == null ? 0f : selK(st, now);
                if (st != null && k < 0.001f && st[0] == 0) badges.remove(key);
                // the keyboard draws the emoji itself at 0.9 (standard and custom alike); we only drive the scale
                final QrEmojiPanel.Cell iv = (QrEmojiPanel.Cell) ch;
                final float want = 1f - 0.1f * k;
                if (Math.abs(iv.selectScale - want) > 0.0005f) {
                    iv.selectScale = want;
                    iv.invalidate();
                    if (iv.getParent() instanceof View) ((View) iv.getParent()).invalidate();
                }
                if (ch.getAlpha() != 1f) ch.setAlpha(1f);
                if (st != null && (now - (long) st[2] < 200 || (st[5] != 0 && now - (long) st[5] < 200))) anim = true;
                if (k <= 0.004f || st == null) continue;
                ch.getLocationOnScreen(loc);
                final float x0 = loc[0] - myLoc[0], y0 = loc[1] - myLoc[1], cw = ch.getWidth(), chh = ch.getHeight();
                // (the 10% background is drawn under the cells by drawUnder())
                // counter: round glass in the top-right; pops in with a spring (0.6 → 1), shrinks back on deselect
                final float ps = st[0] == 1 ? 0.6f + 0.4f * spring(now - (long) st[2], 0.55f, 0.3f) : 0.6f + 0.4f * k;
                if (st[0] == 1 && now - (long) st[2] < 600) anim = true;
                drawBadge(c, x0 + cw - dp(9.5f), y0 + dp(9.5f), ps, Math.min(1f, k * 1.2f) * fadeAt(y0 + dp(9.5f)), st, now);
            } else if (ch instanceof ViewGroup) {
                anim |= drawBadges(c, (ViewGroup) ch, picked, now);
            }
        }
        return anim;
    }

    // ---- scrollbar: 3dp white thumb (r 1.5), 4dp from the glass's right edge; track = the visible grid area;
    // fades in on scroll (120 ms), out 900 ms after it stops; 45% (60% while dragging)
    private androidx.recyclerview.widget.RecyclerView grid;
    private float glassRight = Float.NaN, sbAlpha, sbLevel = 0.45f;
    private long lastScroll, lastSbFrame;
    private boolean sbDragging;
    private final int[] gridLoc = new int[2];
    private final RectF sbRect = new RectF();

    void attachGrid(androidx.recyclerview.widget.RecyclerView g) {
        if (grid == g) return;
        grid = g;
        g.setVerticalScrollBarEnabled(false);
        g.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(androidx.recyclerview.widget.RecyclerView rv, int dx, int dy) {
                if (dy != 0) {
                    lastScroll = nowMs();
                    invalidate();
                }
            }

            @Override
            public void onScrollStateChanged(androidx.recyclerview.widget.RecyclerView rv, int state) {
                sbDragging = state == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING;
                if (state != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) lastScroll = nowMs();
                invalidate();
            }
        });
    }

    void setGlassRight(float x) {glassRight = x;}

    private boolean drawScrollbar(Canvas c, long now) {
        final long dt = lastSbFrame == 0 ? 16 : Math.min(64, now - lastSbFrame);
        // hidden for a while (panel closed): no stale bar fading in / out on reopen
        if (lastSbFrame != 0 && now - lastSbFrame > 200) sbAlpha = sbDragging || now - lastScroll < 900 ? 1f : 0f;
        lastSbFrame = now;
        if (grid == null || Float.isNaN(glassRight) || Float.isNaN(glassBottom) || !grid.isAttachedToWindow())
            return false;
        final boolean active = sbDragging || now - lastScroll < 900;
        final float target = active ? 1f : 0f;
        if (sbAlpha < target) sbAlpha = Math.min(target, sbAlpha + dt / 120f);
        else if (sbAlpha > target) sbAlpha = Math.max(target, sbAlpha - dt / 250f);
        final float levelTarget = sbDragging ? 0.6f : 0.45f;
        sbLevel += (levelTarget - sbLevel) * Math.min(1f, dt / 80f);
        final boolean anim = active || sbAlpha > 0f || Math.abs(sbLevel - levelTarget) > 0.004f;
        if (sbAlpha <= 0.004f) return anim;
        final int range = grid.computeVerticalScrollRange(), extent = grid.computeVerticalScrollExtent(), offset = grid.computeVerticalScrollOffset();
        if (range <= extent || extent <= 0) return anim;
        grid.getLocationOnScreen(gridLoc);
        getLocationOnScreen(myLoc); // also without picks (the badge pass that set it only runs with picks)
        final float top = gridLoc[1] - myLoc[1] + grid.getPaddingTop();
        // ends above the glass fade, or above the picks island while it shows
        final float bottom = islandShow > 0.5f && !islandRect.isEmpty() ? Math.min(glassBottom - dp(10), islandRect.top - dp(6)) : glassBottom - dp(10);
        final float span = bottom - top;
        if (span <= dp(30)) return anim;
        final float len = Math.max(dp(28), span * extent / (float) range);
        final float f = Math.max(0f, Math.min(1f, offset / (float) (range - extent)));
        final float y = top + (span - len) * f;
        final float right = getWidth() - dp(3); // the overlay spans the sheet glass exactly: 3dp in from its right edge
        sbRect.set(right - dp(3), y, right, y + len);
        fill.setColor(Color.WHITE);
        fill.setAlpha((int) (255 * sbLevel * sbAlpha));
        c.drawRoundRect(sbRect, dpf2(1.5f), dpf2(1.5f), fill);
        return anim;
    }

    /** Matches island items to the picked list (same key, in order); new ones grow in, removed ones shrink out. */
    private void syncItems(List<String> picked) {
        matched.clear();
        for (int p = 0; p < picked.size(); p++) {
            final String key = picked.get(p);
            Item found = null;
            for (int i = 0; i < items.size(); i++) {
                final Item it = items.get(i);
                if (!it.leaving && !matched.contains(it) && it.key.equals(key)) {
                    found = it;
                    break;
                }
            }
            if (found == null) {
                found = new Item();
                found.key = key;
            }
            matched.add(found);
        }
        // leaving items stay in place in the list (they shrink where they were)
        for (int i = 0; i < items.size(); i++) {
            final Item it = items.get(i);
            if (!it.leaving && !matched.contains(it)) it.leaving = true;
        }
        // rebuild order: matched in pick order, leaving ones kept after their neighbours
        next.clear();
        next.addAll(matched);
        for (int i = 0; i < items.size(); i++)
            if (items.get(i).leaving) next.add(Math.min(i, next.size()), items.get(i));
        items.clear();
        items.addAll(next);
    }

    private int countTargets() {
        int n = 0;
        for (int i = 0; i < items.size(); i++) if (!items.get(i).leaving) n++;
        return n;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // only the island takes touches; everything else goes to the keyboard below
        if (islandShow < 0.5f || !islandRect.contains(e.getX(), e.getY())) return false;
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            int k = 0;
            for (int i = 0; i < items.size(); i++) {
                final Item it = items.get(i);
                if (it.leaving) continue;
                if (Math.abs(e.getX() - it.x) < dp(CELL) / 2f) {
                    host.removePickAt(k);
                    invalidate();
                    break;
                }
                k++;
            }
        }
        return true;
    }
}
