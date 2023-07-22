package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class DraftSavedHint extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final StaticLayout layout;
    private final float layoutWidth, layoutLeft;

    private final Path path = new Path();

    public DraftSavedHint(Context context) {
        super(context);

        backgroundPaint.setColor(0xcc282828);
        backgroundPaint.setPathEffect(new CornerPathEffect(dp(6)));

        textPaint.setTextSize(dp(14));
        textPaint.setColor(0xffffffff);

        CharSequence text = LocaleController.getString("StoryDraftSaved");
        text = TextUtils.ellipsize(text, textPaint, AndroidUtilities.displaySize.x, TextUtils.TruncateAt.END);
        layout = new StaticLayout(text, textPaint, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        layoutWidth = layout.getLineCount() > 0 ? layout.getLineWidth(0) : 0;
        layoutLeft = layout.getLineCount() > 0 ? layout.getLineLeft(0) : 0;

        showT.set(0, true);
    }

    private Runnable hideRunnable;

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

    public void show() {
        showT.set(0, true);
        show(true, true);
        if (hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
        }
        AndroidUtilities.runOnUIThread(hideRunnable = () -> hide(true), 3500);
    }

    private boolean shown;
    private final AnimatedFloat showT = new AnimatedFloat(this);

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float showT = this.showT.set(shown);

        if (showT <= 0) {
            return;
        }

        canvas.save();
        canvas.translate(0, (shown ? CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(showT) : 1) * dp(12));

        showT = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(showT);

        final float W = getMeasuredWidth(), H = getMeasuredHeight();
        final float width = dp(11 + 11) + layoutWidth;

        final float dist = Math.min(dp(135), W * .35f);
        final float cx = W / 2f - dist;

        final float x = Math.max(dp(8), cx - width / 2f);

        path.rewind();
        path.moveTo(x, 0);
        path.lineTo(x + width, 0);
        path.lineTo(x + width, H - dp(6 + 12));
        path.lineTo(cx + dp(7), H - dp(6 + 12));
        path.lineTo(cx + dp(1), H - dp(12));
        path.lineTo(cx - dp(1), H - dp(12));
        path.lineTo(cx - dp(7), H - dp(6 + 12));
        path.lineTo(x, H - dp(6 + 12));
        path.close();

        backgroundPaint.setAlpha((int) (0xcc * showT));
        canvas.drawPath(path, backgroundPaint);

        canvas.save();
        canvas.translate(x + dp(11) - layoutLeft, (H - dp(6 + 12) - layout.getHeight()) / 2f);
        textPaint.setAlpha((int) (0xFF * showT));
        layout.draw(canvas);
        canvas.restore();

        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(38 + 12));
    }
}