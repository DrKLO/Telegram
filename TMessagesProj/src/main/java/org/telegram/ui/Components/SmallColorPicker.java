package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class SmallColorPicker extends FrameLayout {

    private final ColorPickerDelegate delegate;

    private Paint colorWheelPaint;
    private Paint valueSliderPaint;
    private Paint circlePaint;
    private Paint linePaint;
    private Paint bgPaint;
    private Paint editTextCirclePaint;
    private Drawable circleDrawable;

    private RectF sliderRect = new RectF();

    private Bitmap colorWheelBitmap;

    private int originalFirstColor;

    private int colorWheelWidth;

    private float[] colorHSV = new float[] { 0.0f, 0.0f, 1.0f };

    private float[] hsvTemp = new float[3];
    private LinearGradient colorGradient;

    private boolean circlePressed;
    private boolean colorPressed;

    private float pressedMoveProgress = 1.0f;
    private long lastUpdateTime;

    private float minBrightness = 0f;
    private float maxBrightness = 1f;

    private float minHsvBrightness = 0f;
    private float maxHsvBrightness = 1f;

    public SmallColorPicker(Context context, ColorPickerDelegate colorPickerDelegate) {
        super(context);

        delegate = colorPickerDelegate;

        setWillNotDraw(false);

        circleDrawable = context.getResources().getDrawable(R.drawable.knob_shadow).mutate();

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        colorWheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        valueSliderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        editTextCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint = new Paint();
        linePaint.setColor(0x12000000);
        bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);

        FrameLayout buttonsLayout = new FrameLayout(context);
        TextView cancelButton = createActionButton(context);
        cancelButton.setOnClickListener(v -> delegate.onClose());
        cancelButton.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        cancelButton.setText("CANCEL");
        TextView applyButton = createActionButton(context);
        applyButton.setOnClickListener(v -> delegate.setColor(getColor(), true));
        applyButton.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        applyButton.setText("APPLY");
        buttonsLayout.addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT));
        buttonsLayout.addView(applyButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT));
        addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 54, Gravity.BOTTOM));
    }

    private TextView createActionButton(Context context) {
        TextView button = new TextView(context);
        button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        button.setTextSize(16);
        button.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        if (Build.VERSION.SDK_INT >= 21) {
            button.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_chat_addContact) & 0x19ffffff, 2));
        }
        return button;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0,0,colorWheelBitmap.getWidth(), colorWheelBitmap.getHeight(), bgPaint);
        canvas.drawBitmap(colorWheelBitmap, 0, 0, null);
        int y = colorWheelBitmap.getHeight();
        canvas.drawRect(0, 0, getMeasuredWidth(), 1, linePaint);
        canvas.drawRect(0, y - 1, getMeasuredWidth(), y, linePaint);
        canvas.drawRect(0, getMeasuredHeight() - AndroidUtilities.dpf2(54), getMeasuredWidth(), 1, linePaint);

        hsvTemp[0] = colorHSV[0];
        hsvTemp[1] = colorHSV[1];
        hsvTemp[2] = 1f;

        int colorPointX = (int) (colorHSV[0] * getMeasuredWidth() / 360);
        int colorPointY = (int) (colorWheelBitmap.getHeight() * (1.0f - colorHSV[1]));
        if (!circlePressed) {
            int minD = AndroidUtilities.dp(16);
            float progress = CubicBezierInterpolator.EASE_OUT.getInterpolation(pressedMoveProgress);
            if (colorPointX < minD) {
                colorPointX += progress * (minD - colorPointX);
            } else if (colorPointX > getMeasuredWidth() - minD) {
                colorPointX -= progress * (colorPointX - (getMeasuredWidth() - minD));
            }
            if (colorPointY < minD) {
                colorPointY += progress * (minD - colorPointY);
            } else if (colorPointY > colorWheelBitmap.getHeight() - minD) {
                colorPointY -= progress * (colorPointY - (colorWheelBitmap.getHeight() - minD));
            }
        }
        drawPointerArrow(canvas, colorPointX, colorPointY, Color.HSVToColor(hsvTemp), false);

        sliderRect.set(AndroidUtilities.dp(22), y + AndroidUtilities.dp(26), getMeasuredWidth() - AndroidUtilities.dp(22), y + AndroidUtilities.dp(26 + 8));
        if (colorGradient == null) {
            hsvTemp[2] = minHsvBrightness;
            int minColor = Color.HSVToColor(hsvTemp);
            hsvTemp[2] = maxHsvBrightness;
            int maxColor = Color.HSVToColor(hsvTemp);

            colorGradient = new LinearGradient(sliderRect.left, sliderRect.top, sliderRect.right, sliderRect.top, new int[]{maxColor, minColor}, null, Shader.TileMode.CLAMP);
            valueSliderPaint.setShader(colorGradient);
        }
        canvas.drawRoundRect(sliderRect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), valueSliderPaint);
        float value = minHsvBrightness == maxHsvBrightness ? 0.5f : (getBrightness() - minHsvBrightness) / (maxHsvBrightness - minHsvBrightness);
        drawPointerArrow(canvas, (int) (sliderRect.left + (1.0f - value) * sliderRect.width()), (int) sliderRect.centerY(), getColor(), true);

        if (!circlePressed && pressedMoveProgress < 1.0f) {
            long newTime = SystemClock.elapsedRealtime();
            long dt = newTime - lastUpdateTime;
            lastUpdateTime = newTime;
            pressedMoveProgress += dt / 180.0f;
            if (pressedMoveProgress > 1.0f) {
                pressedMoveProgress = 1.0f;
            }
            invalidate();
        }
    }

    private void drawPointerArrow(Canvas canvas, int x, int y, int color, boolean small) {
        int side = AndroidUtilities.dp(small ? 12 : 16);
        circleDrawable.setBounds(x - side, y - side, x + side, y + side);
        circleDrawable.draw(canvas);

        circlePaint.setColor(0xffffffff);
        canvas.drawCircle(x, y, AndroidUtilities.dp(small ? 11 : 15), circlePaint);
        circlePaint.setColor(color);
        canvas.drawCircle(x, y, AndroidUtilities.dp(small ? 9 : 13), circlePaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(180 + 54 + 54), MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        if (colorWheelWidth != width) {
            colorWheelWidth = width;
            colorWheelBitmap = createColorWheelBitmap(colorWheelWidth, AndroidUtilities.dp(180));
            colorGradient = null;
        }
    }

    private Bitmap createColorWheelBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        LinearGradient gradientShader = new LinearGradient(0, 0, width, 0, new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED}, null, Shader.TileMode.CLAMP);
        LinearGradient alphaShader = new LinearGradient(0, (height / 3), 0, height, new int[]{Color.WHITE, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
        ComposeShader composeShader = new ComposeShader(alphaShader, gradientShader, PorterDuff.Mode.MULTIPLY);

        colorWheelPaint.setShader(composeShader);

        Canvas canvas = new Canvas(bitmap);
        canvas.drawRect(0, 0, width, height, colorWheelPaint);

        return bitmap;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                int x = (int) event.getX();
                int y = (int) event.getY();

                if (circlePressed || !colorPressed && y >= 0 && y <= colorWheelBitmap.getHeight()) {
                    if (!circlePressed) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    circlePressed = true;
                    pressedMoveProgress = 0.0f;
                    lastUpdateTime = SystemClock.elapsedRealtime();

                    x = Math.max(0, Math.min(x, colorWheelBitmap.getWidth()));
                    y = Math.max(0, Math.min(y, colorWheelBitmap.getHeight()));

                    float oldBrightnessPos = minHsvBrightness == maxHsvBrightness ? 0.5f : (getBrightness() - minHsvBrightness) / (maxHsvBrightness - minHsvBrightness);
                    colorHSV[0] = x * 360f / colorWheelBitmap.getWidth();
                    colorHSV[1] = 1.0f - (1.0f / colorWheelBitmap.getHeight() * y);
                    updateHsvMinMaxBrightness();
                    colorHSV[2] = minHsvBrightness * (1 - oldBrightnessPos) + maxHsvBrightness * oldBrightnessPos;
                    colorGradient = null;
                }
                if (colorPressed || !circlePressed && x >= sliderRect.left && x <= sliderRect.right && y >= sliderRect.top - AndroidUtilities.dp(7) && y <= sliderRect.bottom + AndroidUtilities.dp(7)) {
                    float value = 1.0f - (x - sliderRect.left) / sliderRect.width();
                    if (value < 0.0f) {
                        value = 0.0f;
                    } else if (value > 1.0f) {
                        value = 1.0f;
                    }
                    colorHSV[2] = minHsvBrightness * (1 - value) + maxHsvBrightness * value;
                    if (!colorPressed) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    colorPressed = true;
                }
                if (colorPressed || circlePressed) {
                    int color = getColor();
                    delegate.setColor(color, false);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                colorPressed = false;
                circlePressed = false;
                lastUpdateTime = SystemClock.elapsedRealtime();
                invalidate();
                break;
        }
        return super.onTouchEvent(event);
    }

    private void setColorInner(int color) {
        Color.colorToHSV(color, colorHSV);
        int defaultColor = delegate.getDefaultColor();
        if (defaultColor == 0 || defaultColor != color) {
            updateHsvMinMaxBrightness();
        }
        colorGradient = null;
        invalidate();
    }

    public void setColor(int color) {
        setColorInner(color);
    }

    public int getColor() {
        hsvTemp[0] = colorHSV[0];
        hsvTemp[1] = colorHSV[1];
        hsvTemp[2] = getBrightness();
        return (Color.HSVToColor(hsvTemp) & 0x00ffffff) | 0xff000000;
    }

    private float getBrightness() {
        return Math.max(minHsvBrightness, Math.min(colorHSV[2], maxHsvBrightness));
    }

    private void updateHsvMinMaxBrightness() {
        float min = 0f;
        float max = 1f;
        float hsvBrightness = colorHSV[2];

        if (min == 0f && max == 1f) {
            minHsvBrightness = 0f;
            maxHsvBrightness = 1f;
            return;
        }

        colorHSV[2] = 1f;
        int maxColor = Color.HSVToColor(colorHSV);
        colorHSV[2] = hsvBrightness;

        float maxPerceivedBrightness = AndroidUtilities.computePerceivedBrightness(maxColor);

        minHsvBrightness = Math.max(0f, Math.min(min / maxPerceivedBrightness, 1f));
        maxHsvBrightness = Math.max(minHsvBrightness, Math.min(max / maxPerceivedBrightness, 1f));
    }

    public void setMinBrightness(float limit) {
        minBrightness = limit;
        updateHsvMinMaxBrightness();
    }

    public void setMaxBrightness(float limit) {
        maxBrightness = limit;
        updateHsvMinMaxBrightness();
    }

    public interface ColorPickerDelegate {
        void setColor(int color, boolean applyNow);

        void onClose();

        default int getDefaultColor() {
            return 0;
        }

        default boolean hasChanges() {
            return true;
        }
    }

    private int generateGradientColors(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[1] > 0.5f) {
            hsv[1] -= 0.15f;
        } else {
            hsv[1] += 0.15f;
        }
        if (hsv[0] > 180) {
            hsv[0] -= 20;
        } else {
            hsv[0] += 20;
        }
        return Color.HSVToColor(255, hsv);
    }
}