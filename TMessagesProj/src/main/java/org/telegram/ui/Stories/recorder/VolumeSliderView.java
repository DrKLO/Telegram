package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class VolumeSliderView extends View {

    private float minVolume = 0;
    private float maxVolume = 1.5f;
    private float value;
    private Utilities.Callback<Float> onValueChange;

    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speaker1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speaker2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speakerWave1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speakerWave2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final AnimatedTextView.AnimatedTextDrawable text = new AnimatedTextView.AnimatedTextDrawable(false, true, true);

    public VolumeSliderView(Context context) {
        super(context);

        text.setTextSize(dp(15));
        text.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        text.setAnimationProperties(.3f, 0, 40, CubicBezierInterpolator.EASE_OUT_QUINT);
        text.setCallback(this);
        text.setTextColor(0xffffffff);
        text.setText("");

        speaker1Paint.setColor(0xffffffff);
        speaker2Paint.setColor(0xffffffff);
        speakerWave1Paint.setColor(0xffffffff);
        speakerWave2Paint.setColor(0xffffffff);
        speakerWave2Paint.setStyle(Paint.Style.STROKE);
        speakerWave2Paint.setStrokeCap(Paint.Cap.ROUND);

        whitePaint.setColor(0xffffffff);
        whitePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.XOR));
    }

    public VolumeSliderView setMinMax(float min, float max) {
        this.minVolume = min;
        this.maxVolume = max;
        return this;
    }

    public VolumeSliderView setVolume(float volume) {
        this.value = (volume - this.minVolume) / (this.maxVolume - this.minVolume);
        updateText(volume);
        return this;
    }

    public VolumeSliderView setOnValueChange(Utilities.Callback<Float> listener) {
        onValueChange = listener;
        return this;
    }

    private final Path clipPath = new Path();
    private final Path speaker1Path = new Path();
    private final Path speaker2Path = new Path();
    private final Path speakerWave1Path = new Path();
    private final Path speakerWave2Path = new Path();

    private final AnimatedFloat wave1Alpha = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat wave2Alpha = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        canvas.save();
        AndroidUtilities.rectTmp.set(0, 0, w, h);
        clipPath.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);

        canvas.saveLayerAlpha(0, 0, w, h, 0xFF, Canvas.ALL_SAVE_FLAG);

        text.setBounds(dp(42), -dp(1), w, h - dp(1));
        text.draw(canvas);

        canvas.drawPath(speaker1Path, speaker1Paint);
        canvas.drawPath(speaker2Path, speaker2Paint);

        final float volume = this.maxVolume - this.minVolume != 0 ? this.minVolume + this.value * (this.maxVolume - this.minVolume) : 0;
        final float wave1Alpha = this.wave1Alpha.set(volume > .25);
        canvas.save();
        canvas.translate(-dpf2(0.33f) * (1f - wave1Alpha), 0);
        speakerWave1Paint.setAlpha((int) (0xFF * wave1Alpha));
        canvas.drawPath(speakerWave1Path, speakerWave1Paint);
        canvas.restore();

        final float wave2Alpha = this.wave2Alpha.set(volume > .5);
        canvas.save();
        canvas.translate(-dpf2(0.66f) * (1f - wave2Alpha), 0);
        speakerWave2Paint.setAlpha((int) (0xFF * wave2Alpha));
        canvas.drawPath(speakerWave2Path, speakerWave2Paint);
        canvas.restore();

        canvas.save();
        canvas.drawRect(0, 0, w * value, h, whitePaint);
        canvas.restore();
        canvas.restore();

        canvas.restore();
    }


    private float lastTouchX;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (w <= 0) {
            return false;
        }

        final float x = event.getX();
        if (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP) {
            float pastVolume = this.maxVolume - this.minVolume != 0 ? this.minVolume + this.value * (this.maxVolume - this.minVolume) : 0;
            value = Utilities.clamp(value + (x - lastTouchX) / w, 1, 0);
            final float volume = this.maxVolume - this.minVolume != 0 ? this.minVolume + this.value * (this.maxVolume - this.minVolume) : 0;
            if (volume <= this.minVolume && pastVolume > volume || volume >= this.maxVolume && pastVolume < volume) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception ignore) {}
            } else if (Math.floor(pastVolume * 5) != Math.floor(volume * 5)) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception ignore) {}
            }
            updateText(volume);
            if (onValueChange != null) {
                onValueChange.run(volume);
            }
            invalidate();
        }
        lastTouchX = x;
        return true;
    }

    private void updateText(float volume) {
        String string = Math.round(volume * 100) + "%";
        if (!TextUtils.equals(text.getText(), string)) {
            text.cancelAnimation();
            text.setText(string);
        }
    }

    private float r;
    private int w, h;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        r = dpf2(6.33f);
        textPaint.setTextSize(dp(16));
        text.setTextSize(dp(15));
        // TODO: fix this nonsense
        w = (int) Math.min(textPaint.measureText(LocaleController.getString(R.string.StoryAudioRemove)) + dp(88), MeasureSpec.getSize(widthMeasureSpec));
        h = dp(48);
        setMeasuredDimension(w, h);

        final float cx = dp(25), cy = h / 2f;

        speaker1Paint.setPathEffect(new CornerPathEffect(dpf2(1.33f)));
        speaker1Path.rewind();
        speaker1Path.moveTo(cx - dpf2(8.66f), cy - dpf2(2.9f));
        speaker1Path.lineTo(cx - dpf2(3f), cy - dpf2(2.9f));
        speaker1Path.lineTo(cx - dpf2(3f), cy + dpf2(2.9f));
        speaker1Path.lineTo(cx - dpf2(8.66f), cy + dpf2(2.9f));
        speaker1Path.close();

        speaker2Paint.setPathEffect(new CornerPathEffect(dpf2(2.66f)));
        speaker2Path.rewind();
        speaker2Path.moveTo(cx - dpf2(7.5f), cy);
        speaker2Path.lineTo(cx, cy - dpf2(7.33f));
        speaker2Path.lineTo(cx, cy + dpf2(7.33f));
        speaker2Path.close();

        speakerWave1Path.rewind();
        AndroidUtilities.rectTmp.set(cx - dpf2(0.33f) - dp(4.33f), cy - dp(4.33f), cx - dpf2(0.33f) + dp(4.33f), cy + dp(4.33f));
        speakerWave1Path.arcTo(AndroidUtilities.rectTmp, -60, 120);
        speakerWave1Path.close();

        speakerWave2Paint.setStyle(Paint.Style.STROKE);
        speakerWave2Paint.setStrokeWidth(dp(2));
        speakerWave2Path.rewind();
        AndroidUtilities.rectTmp.set(cx - dpf2(0.33f) - dp(8), cy - dp(8), cx - dpf2(0.33f) + dp(8), cy + dp(8));
        speakerWave2Path.arcTo(AndroidUtilities.rectTmp, -70, 140);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == text || super.verifyDrawable(who);
    }
}
