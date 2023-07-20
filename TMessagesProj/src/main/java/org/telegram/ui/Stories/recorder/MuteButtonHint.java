package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class MuteButtonHint extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedTextView.AnimatedTextDrawable textDrawable = new AnimatedTextView.AnimatedTextDrawable(false, false, false);

    private final Path path = new Path();

    public MuteButtonHint(Context context) {
        super(context);

        backgroundPaint.setColor(0xcc282828);
        backgroundPaint.setPathEffect(new CornerPathEffect(dp(6)));

        textDrawable.updateAll = true;
        textDrawable.setAnimationProperties(.4f, 0, 450, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setGravity(Gravity.RIGHT);
        textDrawable.setCallback(this);
        textDrawable.setTextSize(dp(14));
        textDrawable.setTextColor(0xffffffff);
        textDrawable.setOverrideFullWidth(AndroidUtilities.displaySize.x);
    }

    private Runnable hideRunnable;
    public void setMuted(boolean muted) {
        textDrawable.setText(muted ? LocaleController.getString("StorySoundMuted") : LocaleController.getString("StorySoundNotMuted"), !LocaleController.isRTL && shown);
        show(true, true);
        if (hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
        }
        AndroidUtilities.runOnUIThread(hideRunnable = () -> hide(true), 3500);
    }

    public void hide(boolean animated) {
        show(false, animated);
    }

    public void show(boolean show, boolean animated) {
        if (!show && hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            hideRunnable = null;
        }
        shown = show;
        if (!animated) {
            showT.set(show, true);
        }
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == textDrawable || super.verifyDrawable(who);
    }

    private boolean shown;
    private AnimatedFloat showT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final float showT = this.showT.set(shown);

        if (showT <= 0) {
            return;
        }

        final float W = getMeasuredWidth(), H = getMeasuredHeight();
        final float width = dp(11 + 11) + textDrawable.getCurrentWidth();

        final float cx = W - dp(56 + 28 - 12);
        path.rewind();
        path.moveTo(W - width - dp(8), dp(6));
        path.lineTo(W - width - dp(8), H);
        path.lineTo(W - dp(8), H);
        path.lineTo(W - dp(8), dp(6));
        path.lineTo(cx + dp(7), dp(6));
        path.lineTo(cx + dp(1), 0);
        path.lineTo(cx - dp(1), 0);
        path.lineTo(cx - dp(7), dp(6));
        path.close();

        backgroundPaint.setAlpha((int) (0xcc * showT));
        canvas.drawPath(path, backgroundPaint);

        textDrawable.setAlpha((int) (0xFF * showT));
        AndroidUtilities.rectTmp2.set((int) (W - width + dp(-8 + 11)), dp(6 + 7), (int) (W - dp(8 + 11)), (int) (H - dp(7)));
        canvas.save();
        canvas.clipRect(AndroidUtilities.rectTmp2);
        textDrawable.setBounds(AndroidUtilities.rectTmp2);
        textDrawable.draw(canvas);
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        textDrawable.setOverrideFullWidth(width - dp(22));
        setMeasuredDimension(width, dp(38));
    }
}
