package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;

@SuppressLint("ViewConstructor")
public class ProfileBlurAvatar extends FrameLayout {

    protected final BlurringShader.StoryBlurDrawer backgroundBlur;
    private final BlurringShader.BlurManager blurManager;
    private final FallbackBlurManager2 fallbackBlurManager2;

    public ProfileBlurAvatar(Context context, BlurringShader.BlurManager blurManager, FallbackBlurManager2 fallbackBlurManager2) {
        super(context);
        this.blurManager = blurManager;
        this.fallbackBlurManager2 = fallbackBlurManager2;
        backgroundBlur = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_BACKGROUND, !customBlur());
        blurManager.invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        RectF bounds = new RectF(0, 0, this.getWidth(), this.getHeight());
        if (customBlur()) {
            drawBlur(backgroundBlur, canvas, bounds, 0, 0, 1.0f);
        } else {
            Paint[] blurPaints = backgroundBlur.getPaints(1f, 0, 0);
            android.util.Log.d("wwttff", "blurpaints " +  blurPaints[0] + " " + blurPaints[1]);
            if (blurPaints == null || blurPaints[1] == null) {
                drawBlur(backgroundBlur, canvas, bounds, 0, getTranslationY(), 1.0f);
            } else {
                // todo?
                if (blurPaints[0] != null) {
                    canvas.drawRoundRect(bounds, 0, 0, blurPaints[0]);
                }
                if (blurPaints[1] != null) {
                    canvas.drawRoundRect(bounds, 0, 0, blurPaints[1]);
                }
            }
        }
        super.dispatchDraw(canvas);
        invalidate();
    }

    protected boolean customBlur() {
        return blurManager.hasRenderNode();
    }

    protected void drawBlur(BlurringShader.StoryBlurDrawer blur, Canvas canvas, RectF rect, float ox, float oy, float alpha) {

    }
}
