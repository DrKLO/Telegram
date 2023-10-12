package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.rectTmp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class StoryLinesDrawable {

    private final View view;
    private final PeerStoriesView.SharedResources sharedResources;

    private final AnimatedFloat scrollT;

    public StoryLinesDrawable(View view, PeerStoriesView.SharedResources sharedResources) {
        this.view = view;
        this.sharedResources = sharedResources;
        scrollT = new AnimatedFloat(view, 0, 230, CubicBezierInterpolator.EASE_OUT_QUINT);
    }


    float bufferingProgress;
    boolean incrementBuffering;
    int lastPosition;


    public void draw(
            Canvas canvas,
            int fullWidth,
            int index,
            float progress,
            int count,
            float hideInterfaceAlpha,
            float alpha,
            boolean buffering
    ) {
        if (count <= 0) {
            return;
        }
        if (lastPosition != index) {
            bufferingProgress = 0;
            incrementBuffering = true;
        }
        lastPosition = index;

        Paint barPaint = sharedResources.barPaint;
        Paint selectedBarPaint = sharedResources.selectedBarPaint;

        //final int minStoryWidth = AndroidUtilities.dp(4);
        int gapWidth;
        if (count > 100) {
            gapWidth = 1;
        } else if (count >= 50) {
            gapWidth = dp(1);
        } else {
            gapWidth = dp(2);
        }
        final float sectionWidth = (fullWidth - dp(5 * 2) - gapWidth * (count - 1)) / (float) count;
       // final int scrollWidth = sectionWidth * count + dp(2) * (count - 1);
        final float indexX = dp(5) + gapWidth * index + sectionWidth * (index + .5f);
        final float scrollX = 0;//scrollT.set(Utilities.clamp(indexX - fullWidth / 2f, scrollWidth - (fullWidth - dp(10)), 0));

        float roundRadius = Math.min(sectionWidth / 2f, dp(1));
        selectedBarPaint.setAlpha((int) (255 * alpha * hideInterfaceAlpha));
        for (int a = 0; a < count; a++) {
            float x = -scrollX + dp(5) + gapWidth * a + sectionWidth * a;
            if (x > fullWidth || x + sectionWidth < 0) {
                continue;
            }
            float currentProgress;
            int baseAlpha = 0x55;
            if (a <= index) {
                if (a == index) {
                    currentProgress = progress;
                    rectTmp.set(x, 0, x + sectionWidth, dp(2));
                    int bufferingAlpha = 0;
                    if (buffering) {
                        if (incrementBuffering) {
                            bufferingProgress += 16 / 600f;
                            if (bufferingProgress > 0.5f) {
                                incrementBuffering = false;
                            }
                        } else {
                            bufferingProgress -= 16 / 600f;
                            if (bufferingProgress < -0.5f) {
                                incrementBuffering = true;
                            }
                        }
                        bufferingAlpha = (int) ((0x33) * alpha * hideInterfaceAlpha * bufferingProgress);
                    }
                    barPaint.setAlpha((int) ((0x55) * alpha * hideInterfaceAlpha) + bufferingAlpha);
                    canvas.drawRoundRect(rectTmp, roundRadius, roundRadius, barPaint);
                    // baseAlpha = 0x50;
                } else {
                    currentProgress = 1.0f;
                }
            } else {
                currentProgress = 1.0f;
            }
            rectTmp.set(x, 0, x + sectionWidth * currentProgress, dp(2));

            if (a > index) {
                barPaint.setAlpha((int) (baseAlpha * alpha * hideInterfaceAlpha));
            }
            canvas.drawRoundRect(rectTmp, roundRadius, roundRadius, a <= index ? selectedBarPaint : barPaint);
        }
    }
}
