package org.telegram.ui.Components.voip;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;

import org.telegram.ui.Stories.recorder.HintView2;

@SuppressLint("ViewConstructor")
public class VoIpHintView extends HintView2 {

    private final Paint mainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final VoIPBackgroundProvider backgroundProvider;

    public VoIpHintView(Context context, int direction, VoIPBackgroundProvider backgroundProvider, boolean withCloseBtn) {
        super(context, direction);
        this.backgroundProvider = backgroundProvider;
        backgroundProvider.attach(this);
        mainPaint.setPathEffect(new CornerPathEffect(rounding));
        if (withCloseBtn) {
            setCloseButton(true);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        backgroundProvider.setDarkTranslation(getX(), getY());
        super.dispatchDraw(canvas);
    }

    protected void drawBgPath(Canvas canvas) {
        mainPaint.setShader(backgroundProvider.getDarkPaint().getShader());
        int alpha = Math.min(backgroundPaint.getAlpha(), backgroundProvider.getDarkPaint().getAlpha());
        canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), alpha, Canvas.ALL_SAVE_FLAG);
        canvas.drawPath(path, mainPaint);
        if (backgroundProvider.isReveal()) {
            mainPaint.setShader(backgroundProvider.getRevealDarkPaint().getShader());
            canvas.drawPath(path, mainPaint);
        }
        canvas.restore();
    }
}
