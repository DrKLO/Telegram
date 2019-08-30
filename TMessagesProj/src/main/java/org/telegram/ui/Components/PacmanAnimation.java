package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

public class PacmanAnimation {

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private View parentView;
    private Runnable finishRunnable;

    private long lastUpdateTime = 0;
    private RectF rect = new RectF();
    private float progress;
    private float translationProgress;
    private float ghostProgress;
    private Path ghostPath;
    private boolean ghostWalk;
    private boolean currentGhostWalk;

    public PacmanAnimation(View parent) {
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(AndroidUtilities.dp(2));
        parentView = parent;
    }

    public void setFinishRunnable(Runnable onAnimationFinished) {
        finishRunnable = onAnimationFinished;
    }

    private void update() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;
        if (dt > 17) {
            dt = 17;
        }
        if (progress >= 1.0f) {
            progress = 0.0f;
        }
        progress += dt / 400.0f;
        if (progress > 1.0f) {
            progress = 1.0f;
        }
        translationProgress += dt / 2000.0f;
        if (translationProgress > 1.0f) {
            translationProgress = 1.0f;
        }
        ghostProgress += dt / 200.0f;
        if (ghostProgress >= 1.0f) {
            ghostWalk = !ghostWalk;
            ghostProgress = 0.0f;
        }
        parentView.invalidate();
    }

    public void start() {
        translationProgress = 0.0f;
        progress = 0.0f;
        lastUpdateTime = System.currentTimeMillis();
        parentView.invalidate();
    }

    private void drawGhost(Canvas canvas, int num) {
        if (ghostPath == null || ghostWalk != currentGhostWalk) {
            if (ghostPath == null) {
                ghostPath = new Path();
            }
            ghostPath.reset();
            currentGhostWalk = ghostWalk;
            if (currentGhostWalk) {
                ghostPath.moveTo(0, AndroidUtilities.dp(50));
                ghostPath.lineTo(0, AndroidUtilities.dp(24));
                rect.set(0, 0, AndroidUtilities.dp(42), AndroidUtilities.dp(24));
                ghostPath.arcTo(rect, 180, 180, false);
                ghostPath.lineTo(AndroidUtilities.dp(42), AndroidUtilities.dp(50));
                ghostPath.lineTo(AndroidUtilities.dp(35), AndroidUtilities.dp(43));
                ghostPath.lineTo(AndroidUtilities.dp(28), AndroidUtilities.dp(50));
                ghostPath.lineTo(AndroidUtilities.dp(21), AndroidUtilities.dp(43));
                ghostPath.lineTo(AndroidUtilities.dp(14), AndroidUtilities.dp(50));
                ghostPath.lineTo(AndroidUtilities.dp(7), AndroidUtilities.dp(43));
            } else {
                ghostPath.moveTo(0, AndroidUtilities.dp(43));
                ghostPath.lineTo(0, AndroidUtilities.dp(24));
                rect.set(0, 0, AndroidUtilities.dp(42), AndroidUtilities.dp(24));
                ghostPath.arcTo(rect, 180, 180, false);
                ghostPath.lineTo(AndroidUtilities.dp(42), AndroidUtilities.dp(43));
                ghostPath.lineTo(AndroidUtilities.dp(35), AndroidUtilities.dp(50));
                ghostPath.lineTo(AndroidUtilities.dp(28), AndroidUtilities.dp(43));
                ghostPath.lineTo(AndroidUtilities.dp(21), AndroidUtilities.dp(50));
                ghostPath.lineTo(AndroidUtilities.dp(14), AndroidUtilities.dp(43));
                ghostPath.lineTo(AndroidUtilities.dp(7), AndroidUtilities.dp(50));
            }
            ghostPath.close();
        }
        canvas.drawPath(ghostPath, edgePaint);
        if (num == 0) {
            paint.setColor(0xfffea000);
        } else if (num == 1) {
            paint.setColor(0xfffeb2b2);
        } else {
            paint.setColor(0xff00dedf);
        }
        canvas.drawPath(ghostPath, paint);
        paint.setColor(0xffffffff);
        rect.set(AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(28));
        canvas.drawOval(rect, paint);
        rect.set(AndroidUtilities.dp(8 + 16), AndroidUtilities.dp(14), AndroidUtilities.dp(20 + 16), AndroidUtilities.dp(28));
        canvas.drawOval(rect, paint);

        paint.setColor(0xff000000);
        rect.set(AndroidUtilities.dp(14), AndroidUtilities.dp(18), AndroidUtilities.dp(19), AndroidUtilities.dp(24));
        canvas.drawOval(rect, paint);
        rect.set(AndroidUtilities.dp(14 + 16), AndroidUtilities.dp(18), AndroidUtilities.dp(19 + 16), AndroidUtilities.dp(24));
        canvas.drawOval(rect, paint);
    }

    public void draw(Canvas canvas, int cy) {
        int size = AndroidUtilities.dp(110);
        int height = AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72);
        int additionalSize = size + AndroidUtilities.dp(42 + 20) * 3;
        int width = parentView.getMeasuredWidth() + additionalSize;
        float translation = width * translationProgress - additionalSize;

        int y = cy - size / 2;

        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        canvas.drawRect(0, cy - height / 2, translation + size / 2, cy + height / 2 + 1, paint);

        paint.setColor(0xfffef200);
        rect.set(translation, y, translation + size, y + size);
        int rad;
        if (progress < 0.5f) {
            rad = (int) (35 * (1.0f - progress / 0.5f));
        } else {
            rad = (int) (35 * (progress - 0.5f) / 0.5f);
        }

        canvas.drawArc(rect, rad, 360 - rad * 2, true, edgePaint);
        canvas.drawArc(rect, rad, 360 - rad * 2, true, paint);

        paint.setColor(0xff000000);
        canvas.drawCircle(translation + size / 2 - AndroidUtilities.dp(8), y + size / 4, AndroidUtilities.dp(8), paint);

        canvas.save();
        canvas.translate(translation + size + AndroidUtilities.dp(20), cy - AndroidUtilities.dp(25));
        for (int a = 0; a < 3; a++) {
            drawGhost(canvas, a);
            canvas.translate(AndroidUtilities.dp(42 + 20), 0);
        }
        canvas.restore();

        if (translationProgress >= 1.0f) {
            finishRunnable.run();
        }

        update();
    }
}
