package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.Build;
import android.text.Layout;
import android.view.Choreographer;
import android.view.View;

import androidx.core.math.MathUtils;

import org.telegram.messenger.MessageObject;

import java.util.ArrayList;

public final class MultiLayoutTypingAnimator implements Choreographer.FrameCallback {

    /** Minimum speed in dp/s — speed will never drop below this. */
    public static final float MIN_SPEED_DP_PER_SEC = 40f;
    /** Target duration for the remaining path (seconds). Adjust as you like. */
    public static final float TARGET_DURATION_SEC = 1.05f;

    private static final float EPS = 0.001f;

    private View invalidateTarget;
    private final Choreographer choreo = Choreographer.getInstance();

    private ArrayList<MessageObject.TextLayoutBlock> blocks = new ArrayList<>();

    // Current caret state (block/line/position within the visual line)
    private int   curBlockIdx = 0;
    private int   curLineIdx  = 0;
    private float xPosition   = 0f;  // px from visual line start

    private boolean running   = false;
    private boolean finished  = true;
    private long lastFrameNs  = 0L;

    // Current chosen speed (px/s); recalculated in setBlocks()
    private float speedPxPerSec = dp(MIN_SPEED_DP_PER_SEC);

    public void setInvalidateTarget(View invalidateTarget) {
        this.invalidateTarget = invalidateTarget;
    }

    /** Full replacement/update of blocks. Can be called frequently. */
    public void setBlocks(ArrayList<MessageObject.TextLayoutBlock> newBlocks) {
        if (!blocks.isEmpty() && curBlockIdx >= blocks.size()) {
            curBlockIdx = blocks.size() - 1;
            curLineIdx = Math.max(0, blocks.get(curBlockIdx).textLayout.getLineCount() - 1);
            xPosition = blocks.get(curBlockIdx).textLayout.getLineWidth(curLineIdx);
        }

        this.blocks = (newBlocks != null) ? newBlocks : new ArrayList<>();

        recalcSpeed();

        finished = isAtAbsoluteEnd();
        if (!finished && !running) start();

        if (invalidateTarget != null) {
            invalidateTarget.invalidate();
        }
    }

    /** Reset animation to the very beginning. */
    public void reset() {
        curBlockIdx = 0;
        curLineIdx  = 0;
        xPosition   = 0f;
        finished    = blocks.isEmpty();
        speedPxPerSec = dp(MIN_SPEED_DP_PER_SEC);
        if (invalidateTarget != null) {
            invalidateTarget.invalidate();
        }
    }

    /** Explicit start (usually not needed — setBlocks() will start automatically). */
    public void start() {
        if (running) return;
        running = true;
        if (isAtAbsoluteEnd()) finished = true;
        lastFrameNs = 0L;
        choreo.postFrameCallback(this);
    }

    /** Stop animation. */
    public void stop() {
        if (!running) return;
        running = false;
        choreo.removeFrameCallback(this);
        lastFrameNs = 0L;
        if (onFinishRunnable != null) {
            onFinishRunnable.run();
            onFinishRunnable = null;
        }
    }

    public boolean isRunning()  { return running; }
    public boolean isFinished() { return finished; }

    private Runnable onFinishRunnable;

    public void setOnFinishListener(Runnable onFinishRunnable) {
        this.onFinishRunnable = onFinishRunnable;
    }

    /** Index of the current active block (the one with fade). */
    public int getCurrentBlockIndex() { return curBlockIdx; }

    /** Fade parameters for the active line inside a given block. */
    public int   getFadeLineIndex(MessageObject.TextLayoutBlock block) {
        return isFadeBlock(block) ? curLineIdx : -1;
    }
    public float getFadeXPosition(MessageObject.TextLayoutBlock block) {
        return isFadeBlock(block) ? xPosition : 0f;
    }

    /**
     * Whether a block needs to be drawn at all.
     * Returns false only if the block is completely hidden (fully after the caret).
     */
    public boolean needDraw(MessageObject.TextLayoutBlock block) {
        int idx = indexOf(block);
        if (idx < 0 || blocks.isEmpty()) return false;

        if (idx < curBlockIdx) return true;   // everything before current is fully visible
        if (idx > curBlockIdx) return false;  // future blocks are still fully hidden

        // Current block is partially visible or fading
        return true;
    }

    /** Is this the block we should draw with a fade? */
    public boolean isFadeBlock(MessageObject.TextLayoutBlock block) {
        int idx = indexOf(block);
        if (idx != curBlockIdx) return false;
        Layout l = block.textLayout;
        if (l == null || curLineIdx >= l.getLineCount()) return false;
        return true;
    }

    // ---------------- Choreographer ----------------

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;

        if (lastFrameNs != 0L) {
            float dt = (frameTimeNanos - lastFrameNs) * 1e-9f;
            advance(dt);
        }
        lastFrameNs = frameTimeNanos;
        if (invalidateTarget != null) {
            invalidateTarget.invalidate();
        }

        if (finished) {
            running = false;
            if (onFinishRunnable != null) {
                onFinishRunnable.run();
                onFinishRunnable = null;
            }
            return;
        }
        choreo.postFrameCallback(this);
    }

    // ---------------- Engine ----------------

    private void advance(float dtSec) {
        if (blocks.isEmpty() || dtSec <= 0f) {
            finished = blocks.isEmpty();
            return;
        }

        float delta = speedPxPerSec * dtSec;

        while (delta > 0f) {
            if (curBlockIdx >= blocks.size()) { finished = true; break; }
            Layout lay = blocks.get(curBlockIdx).textLayout;
            if (lay == null || lay.getLineCount() == 0) {
                // Empty block — skip to next
                curBlockIdx++;
                curLineIdx = 0;
                xPosition = 0f;
                continue;
            }

            if (curLineIdx >= lay.getLineCount()) {
                // Clamp to the end if we somehow stepped out of bounds
                curLineIdx = lay.getLineCount() - 1;
                xPosition = lineWidth(lay, curLineIdx);
            }

            float w = lineWidth(lay, curLineIdx);
            if (w <= EPS) {
                // Zero-width line — move on
                if (!nextLineOrBlock(lay)) continue;
                else break;
            }

            float remain = w - xPosition;
            if (remain <= EPS) {
                // Jump to next line/block
                if (!nextLineOrBlock(lay)) continue;
                else break;
            }

            float step = (delta < remain) ? delta : remain;
            xPosition += step;
            delta -= step;

            if (w - xPosition <= EPS) {
                if (!nextLineOrBlock(lay)) {
                    delta = 0f; // ran out of blocks
                }
            }
        }

        finished = isAtAbsoluteEnd();
    }

    /** Advance to next line or next block. Returns true if there are no more blocks. */
    private boolean nextLineOrBlock(Layout lay) {
        curLineIdx++;
        xPosition = 0f;
        if (curLineIdx >= lay.getLineCount()) {
            curBlockIdx++;
            curLineIdx = 0;
            xPosition = 0f;
            return curBlockIdx >= blocks.size();
        }
        return false;
    }

    // ---------------- Dynamic speed ----------------

    /** Recalculate speed so that remaining distance fits TARGET_DURATION_SEC, but not below MIN_SPEED. */
    private void recalcSpeed() {
        float remainingPx = computeRemainingPixels();
        float minPxPerSec = dp(MIN_SPEED_DP_PER_SEC);

        if (remainingPx <= EPS) {
            speedPxPerSec = minPxPerSec;
            return;
        }

        float desired = remainingPx / TARGET_DURATION_SEC;
        speedPxPerSec = Math.max(minPxPerSec, desired);
    }

    /** Sum of remaining pixel widths from the current caret to the end across all blocks. */
    private float computeRemainingPixels() {
        if (blocks.isEmpty()) return 0f;

        float total = 0f;

        for (int bi = curBlockIdx; bi < blocks.size(); bi++) {
            Layout l = blocks.get(bi).textLayout;
            if (l == null) continue;

            int startLine = 0;
            if (bi == curBlockIdx) {
                startLine = Math.min(Math.max(curLineIdx, 0), Math.max(0, l.getLineCount() - 1));
            }

            for (int li = startLine; li < l.getLineCount(); li++) {
                float w = lineWidth(l, li);
                if (w <= EPS) continue;

                if (bi == curBlockIdx && li == startLine) {
                    float remain = w - xPosition; // tail of the current line
                    if (remain > EPS) total += remain;
                } else {
                    total += w;
                }
            }
        }

        return total;
    }

    // ---------------- Utils ----------------

    private boolean isAtAbsoluteEnd() {
        if (blocks.isEmpty()) return true;
        int lastIdx = blocks.size() - 1;

        int i = lastIdx;
        Layout lay = null;
        for (; i >= 0; i--) {
            lay = blocks.get(i).textLayout;
            if (lay != null && lay.getLineCount() > 0) break;
        }
        if (i < 0 || lay == null) return true;

        if (curBlockIdx < i) return false;
        if (curBlockIdx > i) return true;

        int lastLine = lay.getLineCount() - 1;
        float w = lineWidth(lay, lastLine);
        if (curLineIdx < lastLine) return false;
        return xPosition >= w - EPS;
    }

    private float lineWidth(Layout l, int line) {
        float w = l.getLineRight(line) - l.getLineLeft(line);
        return (w >= 0f) ? w : -w;
    }

    public int indexOf(MessageObject.TextLayoutBlock block) {
        for (int i = 0, n = blocks.size(); i < n; i++) {
            if (blocks.get(i) == block) return i;
        }
        return -1;
    }






    /* */

    private static final float FADE_WIDTH_DP = 50f;
    private static final Paint MASK_PAINT;
    private static final LinearGradient GRADIENT;
    private static final Matrix GRAD_MTX = new Matrix();

    static {
        MASK_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (Build.VERSION.SDK_INT >= 29) {
            MASK_PAINT.setBlendMode(BlendMode.DST_IN);
        } else {
            MASK_PAINT.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }
        GRADIENT = new LinearGradient(0f, 0f, 1f, 0f, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        MASK_PAINT.setShader(GRADIENT);
    }

    /**
     * Draws the entire layout, but renders the specified line (lineIndex) with horizontal fading.
     *
     * @param canvas     the target Canvas (already positioned; no offsets are applied)
     * @param layout     the text Layout
     * @param lineIndex  the index of the line to be faded (required)
     * @param xPosition  the fading position in pixels from the visual start of the line:
     *                   0          → the line is fully hidden;
     *                   lineWidth  → the line is fully visible.
     */
    public static void drawLayoutWithLastLineFade(
            Canvas canvas,
            Layout layout,
            int lineIndex,
            float xPosition
    ) {
        if (layout == null) return;
        final int lineCount = layout.getLineCount();
        if (lineIndex < 0 || lineIndex >= lineCount) return;

        final int width  = layout.getWidth();
        final int height = layout.getHeight();

        final int topLine    = layout.getLineTop(lineIndex);
        final int bottomLine = layout.getLineBottom(lineIndex);

        if (topLine > 0) {
            canvas.save();
            canvas.clipRect(0f, 0f, width, topLine);
            layout.draw(canvas);
            canvas.restore();
        }

        final float rawL = layout.getLineLeft(lineIndex);
        final float rawR = layout.getLineRight(lineIndex);
        final float lineL = Math.min(rawL, rawR);
        final float lineR = Math.max(rawL, rawR);
        if (lineR <= lineL) return;

        final int dir = layout.getParagraphDirection(lineIndex); // 1=LTR, -1=RTL
        final float lineW = (lineR - lineL);

        final float x = MathUtils.clamp(xPosition, 0f, lineW);
        final float position = x / lineW;

        if (x <= 0f) {
            return;
        }
        if (x >= lineW) {
            canvas.save();
            canvas.clipRect(0f, topLine, width, bottomLine);
            layout.draw(canvas);
            canvas.restore();
            return;
        }


        final float gradX1 = lerp(-dp(FADE_WIDTH_DP), lineW, position);
        final float gradX2 = gradX1 + dp(FADE_WIDTH_DP);

        final int sc = canvas.saveLayer(lineL, topLine, lineR, bottomLine, null);

        canvas.save();
        canvas.clipRect(lineL, topLine, lineR, bottomLine);
        layout.draw(canvas);
        canvas.restore();

        GRAD_MTX.reset();
        if (dir >= 0) {
            GRAD_MTX.setScale(dp(FADE_WIDTH_DP), 1f);
            GRAD_MTX.postTranslate(gradX1, 0f);
        } else {
            GRAD_MTX.setScale(-dp(FADE_WIDTH_DP), 1f);
            GRAD_MTX.postTranslate(lineW - gradX1, 0f);
        }
        GRADIENT.setLocalMatrix(GRAD_MTX);

        canvas.drawRect(lineL, topLine, lineR, bottomLine, MASK_PAINT);
        canvas.restoreToCount(sc);

    }
}
