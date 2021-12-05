package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

import java.util.Random;

public class SharedMediaFastScrollTooltip extends FrameLayout {

    public SharedMediaFastScrollTooltip(Context context) {
        super(context);
        TextView textView = new TextView(context);
        textView.setText(LocaleController.getString("SharedMediaFastScrollHint", R.string.SharedMediaFastScrollHint));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setMaxLines(3);
        textView.setTextColor(Theme.getColor(Theme.key_chat_gifSaveHintText));

        setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_chat_gifSaveHintBackground)));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 46, 8, 8, 8));

        TooltipDrawableView hintView = new TooltipDrawableView(context);
        addView(hintView, LayoutHelper.createFrame(29, 32, 0, 8, 8, 8, 8));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(300), MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(32)), MeasureSpec.AT_MOST), heightMeasureSpec);
    }

    private class TooltipDrawableView extends View {

        Random random = new Random();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint fadePaint;
        Paint fadePaintBack;

        public TooltipDrawableView(Context context) {
            super(context);
            paint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_chat_gifSaveHintText), (int) (255 * 0.3f)));
            paint2.setColor(Theme.getColor(Theme.key_chat_gifSaveHintText));

            fadePaint = new Paint();
            LinearGradient gradient = new LinearGradient(0, AndroidUtilities.dp(4), 0, 0, new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
            fadePaint.setShader(gradient);
            fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            fadePaintBack = new Paint();
            gradient = new LinearGradient(0, 0, 0, AndroidUtilities.dp(4), new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
            fadePaintBack.setShader(gradient);
            fadePaintBack.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        }

        float progress = 1f;
        float fromProgress = 0;
        float toProgress;


        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
            int rectSize = getMeasuredWidth() / 2 - AndroidUtilities.dp(3);
            int totalHeight = (rectSize + AndroidUtilities.dp(1)) * 7 + AndroidUtilities.dp(1);
            float progress = CubicBezierInterpolator.EASE_OUT.getInterpolation(this.progress > 0.4f ? (this.progress - 0.4f) / 0.6f : 0);
            float p = fromProgress * (1f - progress) + toProgress * progress;
            canvas.save();
            canvas.translate(0, -(totalHeight - (getMeasuredHeight() - AndroidUtilities.dp(4))) * p);
            for (int i = 0; i < 7; i++) {
                int y = AndroidUtilities.dp(3) + i * (rectSize + AndroidUtilities.dp(1));
                AndroidUtilities.rectTmp.set(0, y, rectSize, y + rectSize);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(2), AndroidUtilities.dp(2), paint);
                AndroidUtilities.rectTmp.set(rectSize + AndroidUtilities.dp(1), y, rectSize + AndroidUtilities.dp(1) + rectSize, y + rectSize);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(2), AndroidUtilities.dp(2), paint);
            }
            canvas.restore();

            canvas.drawRect(0, 0, getMeasuredWidth(), AndroidUtilities.dp(4), fadePaint);
            canvas.translate(0, getMeasuredHeight() - AndroidUtilities.dp(4));
            canvas.drawRect(0, 0, getMeasuredWidth(), AndroidUtilities.dp(4), fadePaintBack);

            canvas.restore();

            float y = AndroidUtilities.dp(3) + (getMeasuredHeight() - AndroidUtilities.dp(15 + 6)) * p;
            AndroidUtilities.rectTmp.set(getMeasuredWidth() - AndroidUtilities.dp(3), y, getMeasuredWidth(), y + AndroidUtilities.dp(15));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(1.5f), AndroidUtilities.dp(1.5f), paint2);
            float cy = AndroidUtilities.rectTmp.centerY();
            float cx = rectSize + AndroidUtilities.dp(0.5f);
            AndroidUtilities.rectTmp.set(cx - AndroidUtilities.dp(8), cy - AndroidUtilities.dp(3), cx + AndroidUtilities.dp(8), cy + AndroidUtilities.dp(3));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3), AndroidUtilities.dp(3), paint2);

            this.progress += 16 / 1000f;
            if (this.progress > 1f) {
                fromProgress = toProgress;
                toProgress = Math.abs(random.nextInt() % 1001) / 1000f;
                if (toProgress > fromProgress) {
                    toProgress += 0.3f;
                } else {
                    toProgress -= 0.3f;
                }
                toProgress = Math.max(0, Math.min(1 ,toProgress));
                this.progress = 0;
            }
            invalidate();
        }
    }
}
