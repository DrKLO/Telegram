package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.rectTmp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class StoryLinesDrawable {

    private final View view;
    private final PeerStoriesView.SharedResources sharedResources;

    private final AnimatedFloat zoomT;

    private final TextPaint zoomHintPaint;
    private final StaticLayout zoomHintLayout;
    private final float zoomHintLayoutLeft, zoomHintLayoutWidth;

    public StoryLinesDrawable(View view, PeerStoriesView.SharedResources sharedResources) {
        this.view = view;
        this.sharedResources = sharedResources;
        zoomT = new AnimatedFloat(view, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);

        zoomHintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        zoomHintPaint.setTextSize(dp(14));
        zoomHintPaint.setColor(0xffffffff);
        zoomHintPaint.setShadowLayer(dp(3), 0, dp(1), 0x30000000);
        zoomHintLayout = new StaticLayout(LocaleController.getString(R.string.StorySeekHelp), zoomHintPaint, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        zoomHintLayoutLeft = zoomHintLayout.getLineCount() > 0 ? zoomHintLayout.getLineLeft(0) : 0;
        zoomHintLayoutWidth = zoomHintLayout.getLineCount() > 0 ? zoomHintLayout.getLineWidth(0) : 0;
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
        boolean buffering,
        boolean zoom,
        float zoomProgress
    ) {
        if (count <= 0) {
            return;
        }

        buffering = buffering && !zoom;
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

        // zoom
        float zoomT = this.zoomT.set(zoom);
        if (zoomT > 0) {
            progress = AndroidUtilities.lerp(progress, zoomProgress, zoomT);

            canvas.save();
            zoomHintPaint.setAlpha((int) (0xFF * zoomT));
            zoomHintPaint.setShadowLayer(dp(3), 0, dp(1), Theme.multAlpha(0x30000000, zoomT));
            canvas.translate((fullWidth - zoomHintLayoutWidth) / 2f - zoomHintLayoutLeft, AndroidUtilities.lerp(dp(4), dp(16), zoomT));
            zoomHintLayout.draw(canvas);
            canvas.restore();
        }

        for (int a = 0; a < count; a++) {
            float aalpha = alpha;
            float x = -scrollX + dp(5) + gapWidth * a + sectionWidth * a;
            if (zoomT > 0 && index != a) {
//                aalpha *= (1f - zoomT);
            }
            if (x > fullWidth || x + sectionWidth < 0 || aalpha <= 0) {
                continue;
            }
            float rr = AndroidUtilities.lerp(roundRadius, dpf2(2), zoomT);
            float currentProgress;
            int baseAlpha = 0x55;
            if (a <= index) {
                if (a == index) {
                    currentProgress = progress;
                    rectTmp.set(x, 0, x + sectionWidth, AndroidUtilities.lerp(dpf2(2), dpf2(5), zoomT * (index == a ? 1 : 0)));
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
                        bufferingAlpha = (int) ((0x33) * aalpha * hideInterfaceAlpha * bufferingProgress);
                    }
                    barPaint.setAlpha((int) ((0x55) * aalpha * hideInterfaceAlpha) + bufferingAlpha);
                    if (zoomT > 0) {
                        rectTmp.left =  Utilities.clamp(AndroidUtilities.lerp(rectTmp.left, (a - index) * fullWidth + dp(5), zoomT), fullWidth - dp(5), dp(5));
                        rectTmp.right = Utilities.clamp(AndroidUtilities.lerp(rectTmp.right, (a - index + 1) * fullWidth - dp(5), zoomT), fullWidth - dp(5), dp(5));
                    }
                    canvas.drawRoundRect(rectTmp, rr, rr, barPaint);
                    // baseAlpha = 0x50;
                } else {
                    currentProgress = 1.0f;
                }
            } else {
                currentProgress = 1.0f;
            }
            rectTmp.set(x, 0, x + sectionWidth, AndroidUtilities.lerp(dpf2(2), dpf2(5), zoomT * (index == a ? 1 : 0)));
            if (zoomT > 0) {
                rectTmp.left =  Utilities.clamp(AndroidUtilities.lerp(rectTmp.left, (a - index) * fullWidth + dp(5), zoomT), fullWidth - dp(5), dp(5));
                rectTmp.right = Utilities.clamp(AndroidUtilities.lerp(rectTmp.right, (a - index + 1) * fullWidth - dp(5), zoomT), fullWidth - dp(5), dp(5));
            }
            rectTmp.right = AndroidUtilities.lerp(rectTmp.left, rectTmp.right, currentProgress);

            Paint paint;
            if (a <= index) {
                selectedBarPaint.setAlpha((int) (0xFF * aalpha * hideInterfaceAlpha));
                paint = selectedBarPaint;
            } else {
                barPaint.setAlpha((int) (baseAlpha * aalpha * hideInterfaceAlpha));
                paint = barPaint;
            }
            canvas.drawRoundRect(rectTmp, rr, rr, paint);
        }
    }
}
