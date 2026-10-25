package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;

/**
 * "Reply thread": when the user taps a reply quote, a thread is pulled out of the quote,
 * runs along the left gutter of the chat and ties itself to the original message while
 * the list scrolls to it.
 *
 * The drawer is not a View. The chat list calls {@link #draw(Canvas)} at the end of its
 * dispatchDraw and {@link #onScrolled(int)} from its scroll listener. Both ends of the
 * thread are looked up among the visible cells on every frame, so it stays glued to the
 * messages while the list is animated (including the translation-based fast scroll).
 */
public class ReplyThreadDrawer {

    private static final long DRAW_IN = 420;
    private static final long HOLD = 900;
    private static final long FADE = 380;
    private static final long PULSE = 560;
    private static final long GIVE_UP_NOT_FOUND = 4500;
    private static final long GIVE_UP_ABSOLUTE = 12000;

    private final RecyclerListView list;
    private final Theme.ResourcesProvider resourcesProvider;

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path path = new Path();
    private final Path segment = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float[] pos = new float[2];

    private boolean active;
    private int sourceId;
    private boolean sourceOut;
    private int targetId;
    private boolean targetAbove;

    private long startTime;
    private long lastScrollTime;
    private long connectedTime;

    // anchor of the source in list coordinates; kept up to date from the cell
    // and extrapolated with the scroll delta when the cell is gone
    private float srcX, srcY;

    public ReplyThreadDrawer(RecyclerListView list, Theme.ResourcesProvider resourcesProvider) {
        this.list = list;
        this.resourcesProvider = resourcesProvider;

        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeCap(Paint.Cap.ROUND);
        shadowPaint.setStrokeJoin(Paint.Join.ROUND);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);
        corePaint.setStyle(Paint.Style.STROKE);
        corePaint.setStrokeCap(Paint.Cap.ROUND);
        corePaint.setStrokeJoin(Paint.Join.ROUND);
        dotPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
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
        this.sourceOut = object.isOutOwner();
        this.targetId = targetId;
        this.targetAbove = targetAbove;
        final long now = SystemClock.uptimeMillis();
        this.startTime = now;
        this.lastScrollTime = now;
        this.connectedTime = 0;
        updateSource(cell);
        this.active = true;
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

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public void draw(Canvas canvas) {
        if (!active) {
            return;
        }
        final long now = SystemClock.uptimeMillis();
        final long elapsed = now - startTime;

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

        // the thread sways while it is pulled and settles once it is tied
        final float age = connectedTime == 0 ? 0 : (now - connectedTime);
        final float settle = connectedTime == 0 ? 0f : clamp01(age / 320f);
        final float gutter = dp(10) + dp(5) * (float) Math.sin(elapsed / 240.0) * (1f - settle);

        float tx;
        float ty;
        if (found) {
            tx = targetCell.getX() + targetCell.getBackgroundDrawableLeft();
            ty = targetCell.getY() + Math.min(targetCell.getHeight() / 2f, dp(28));
        } else {
            tx = gutter;
            ty = targetAbove ? -dp(40) : height + dp(40);
        }
        final float sy = Math.max(-edge, Math.min(height + edge, srcY));
        ty = Math.max(-edge, Math.min(height + edge, ty));

        // both ends beyond the same edge: nothing to show yet
        if ((sy <= -edge + 1 && ty <= -edge + 1) || (sy >= height + edge - 1 && ty >= height + edge - 1)) {
            list.postInvalidateOnAnimation();
            return;
        }

        path.reset();
        path.moveTo(srcX, sy);
        path.cubicTo(gutter, sy, gutter, ty, tx, ty);
        measure.setPath(path, false);
        final float length = measure.getLength();
        if (length < 1f) {
            list.postInvalidateOnAnimation();
            return;
        }

        final float drawIn = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(clamp01(elapsed / (float) DRAW_IN));
        segment.reset();
        measure.getSegment(0, length * drawIn, segment, true);
        segment.rLineTo(0, 0);

        // tied: the head reached the end, the list is at rest and the target is where it should be
        if (connectedTime == 0 && found && drawIn >= 1f && Math.abs(targetCell.getTranslationY()) < 1f && now - lastScrollTime > 90) {
            connectedTime = now;
            list.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }

        float alpha = 1f;
        if (connectedTime != 0) {
            final float fadeAge = now - connectedTime - HOLD;
            if (fadeAge > 0) {
                alpha = 1f - fadeAge / FADE;
                if (alpha <= 0f) {
                    active = false;
                    return;
                }
            }
        }

        final int color = Theme.getColor(sourceOut ? Theme.key_chat_outReplyLine : Theme.key_chat_inReplyLine, resourcesProvider);

        shadowPaint.setStrokeWidth(dp(5));
        shadowPaint.setColor(ColorUtils.setAlphaComponent(0xFF000000, (int) (46 * alpha)));
        glowPaint.setStrokeWidth(dp(7));
        glowPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (60 * alpha)));
        corePaint.setStrokeWidth(dp(2.25f));
        corePaint.setColor(ColorUtils.setAlphaComponent(color, (int) (255 * alpha)));

        canvas.drawPath(segment, shadowPaint);
        canvas.drawPath(segment, glowPaint);
        canvas.drawPath(segment, corePaint);

        // knot on the quote
        dotPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (255 * alpha)));
        canvas.drawCircle(srcX, sy, dp(3), dotPaint);

        // bead at the head of the thread
        measure.getPosTan(length * drawIn, pos, null);
        if (connectedTime == 0) {
            dotPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (70 * alpha)));
            canvas.drawCircle(pos[0], pos[1], dp(9), dotPaint);
            dotPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (255 * alpha)));
            canvas.drawCircle(pos[0], pos[1], dp(4), dotPaint);
        } else {
            // knot on the original message and a ring that spreads from it
            dotPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (255 * alpha)));
            canvas.drawCircle(tx, ty, dp(3.5f), dotPaint);
            final float pulse = clamp01(age / (float) PULSE);
            if (pulse < 1f) {
                final float eased = CubicBezierInterpolator.EASE_OUT.getInterpolation(pulse);
                ringPaint.setStrokeWidth(dp(2));
                ringPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (190 * (1f - pulse) * alpha)));
                canvas.drawCircle(tx, ty, dp(5) + dp(20) * eased, ringPaint);
            }
        }

        list.postInvalidateOnAnimation();
    }
}
