package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;

import java.util.List;

public class ColorPicker extends FrameLayout {

    private final ColorPickerDelegate delegate;

    private LinearLayout linearLayout;

    private final int paramValueSliderWidth = AndroidUtilities.dp(20);

    private Paint colorWheelPaint;
    private Paint valueSliderPaint;
    private Paint circlePaint;
    private Drawable circleDrawable;

    private int centerX;
    private int centerY;
    private int lx;
    private int ly;

    boolean ignoreTextChange;

    private Bitmap colorWheelBitmap;

    private EditTextBoldCursor[] colorEditText = new EditTextBoldCursor[2];

    private int colorWheelRadius;

    private float[] colorHSV = new float[] { 0.0f, 0.0f, 1.0f };

    private float[] hsvTemp = new float[3];
    private LinearGradient colorGradient;

    private boolean circlePressed;
    private boolean colorPressed;

    private BrightnessLimit minBrightness;
    private BrightnessLimit maxBrightness;

    public ColorPicker(Context context, ColorPickerDelegate delegate) {
        super(context);

        this.delegate = delegate;

        setWillNotDraw(false);

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circleDrawable = context.getResources().getDrawable(R.drawable.knob_shadow).mutate();

        colorWheelPaint = new Paint();
        colorWheelPaint.setAntiAlias(true);
        colorWheelPaint.setDither(true);

        valueSliderPaint = new Paint();
        valueSliderPaint.setAntiAlias(true);
        valueSliderPaint.setDither(true);

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 46, Gravity.LEFT | Gravity.TOP, 12, 20, 21, 14));
        for (int a = 0; a < 2; a++) {
            final int num = a;

            colorEditText[a] = new EditTextBoldCursor(context);
            colorEditText[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            colorEditText[a].setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            colorEditText[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            colorEditText[a].setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            colorEditText[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            colorEditText[a].setCursorSize(AndroidUtilities.dp(20));
            colorEditText[a].setCursorWidth(1.5f);
            colorEditText[a].setSingleLine(true);
            colorEditText[a].setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            colorEditText[a].setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            colorEditText[a].setTransformHintToHeader(true);
            if (a == 0) {
                colorEditText[a].setInputType(InputType.TYPE_CLASS_TEXT);
                colorEditText[a].setHintText(
                        LocaleController.getString("BackgroundHexColorCode", R.string.BackgroundHexColorCode));
            } else {
                colorEditText[a].setInputType(InputType.TYPE_CLASS_NUMBER);
                colorEditText[a].setHintText(LocaleController.getString("BackgroundBrightness", R.string.BackgroundBrightness));
            }
            colorEditText[a].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            InputFilter[] inputFilters = new InputFilter[1];
            inputFilters[0] = new InputFilter.LengthFilter(a == 0 ? 7 : 3);
            colorEditText[a].setFilters(inputFilters);
            colorEditText[a].setPadding(0, AndroidUtilities.dp(6), 0, 0);
            linearLayout.addView(colorEditText[a], LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, a == 0 ? 0.67f : 0.31f, 0, 0, a != 1 ? 23 : 0, 0));
            colorEditText[a].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void afterTextChanged(Editable editable) {
                    if (ignoreTextChange) {
                        return;
                    }
                    ignoreTextChange = true;
                    if (num == 0) {
                        for (int a = 0; a < editable.length(); a++) {
                            char ch = editable.charAt(a);
                            if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F' || ch == '#' && a == 0)) {
                                editable.replace(a, a + 1, "");
                                a--;
                            }
                        }
                        if (editable.length() == 0) {
                            editable.append("#");
                        } else if (editable.charAt(0) != '#') {
                            editable.insert(0, "#");
                        }
                        if (editable.length() != 7) {
                            ignoreTextChange = false;
                            return;
                        }

                        try {
                            setColor(Integer.parseInt(editable.toString().substring(1), 16) | 0xff000000);
                        } catch (Exception e) {
                            setColor(0xffffffff);
                        }
                    } else {
                        int value = Utilities.parseInt(editable.toString());
                        if (value > 255 || value < 0) {
                            if (value > 255) {
                                value = 255;
                            } else {
                                value = 0;
                            }
                            editable.replace(0, editable.length(), "" + value);
                        }
                        colorHSV[2] = value / 255.0f;
                    }

                    int color = getColor();
                    int red = Color.red(color);
                    int green = Color.green(color);
                    int blue = Color.blue(color);
                    colorEditText[0].setTextKeepState(String.format("#%02x%02x%02x", (byte) red, (byte) green, (byte) blue).toUpperCase());
                    colorEditText[1].setTextKeepState(String.valueOf((int) (255 * getBrightness())));
                    delegate.setColor(color);

                    ignoreTextChange = false;
                }
            });
            colorEditText[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    AndroidUtilities.hideKeyboard(textView);
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(widthSize, heightSize);
        measureChild(linearLayout, MeasureSpec.makeMeasureSpec(widthSize - AndroidUtilities.dp(42), MeasureSpec.EXACTLY), heightMeasureSpec);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        centerX = getWidth() / 2 - paramValueSliderWidth * 2 + AndroidUtilities.dp(11);
        centerY = getHeight() / 2 + AndroidUtilities.dp(34);

        canvas.drawBitmap(colorWheelBitmap, centerX - colorWheelRadius, centerY - colorWheelRadius, null);

        float hueAngle = (float) Math.toRadians(colorHSV[0]);
        int colorPointX = (int) (-Math.cos(hueAngle) * colorHSV[1] * colorWheelRadius) + centerX;
        int colorPointY = (int) (-Math.sin(hueAngle) * colorHSV[1] * colorWheelRadius) + centerY;

        hsvTemp[0] = colorHSV[0];
        hsvTemp[1] = colorHSV[1];
        hsvTemp[2] = 1.0f;

        drawPointerArrow(canvas, colorPointX, colorPointY, Color.HSVToColor(hsvTemp));

        lx = centerX + colorWheelRadius + paramValueSliderWidth * 2;
        ly = centerY - colorWheelRadius;
        int width = AndroidUtilities.dp(9);
        int height = colorWheelRadius * 2;
        if (colorGradient == null) {
            colorGradient = new LinearGradient(lx, ly, lx + width, ly + height, new int[]{Color.BLACK, Color.HSVToColor(hsvTemp)}, null, Shader.TileMode.CLAMP);
        }
        valueSliderPaint.setShader(colorGradient);
        canvas.drawRect(lx, ly, lx + width, ly + height, valueSliderPaint);
        drawPointerArrow(canvas, lx + width / 2, (int) (ly + getBrightness() * height), getColor());
    }

    private void drawPointerArrow(Canvas canvas, int x, int y, int color) {
        int side = AndroidUtilities.dp(13);
        circleDrawable.setBounds(x - side, y - side, x + side, y + side);
        circleDrawable.draw(canvas);

        circlePaint.setColor(0xffffffff);
        canvas.drawCircle(x, y, AndroidUtilities.dp(11), circlePaint);
        circlePaint.setColor(color);
        canvas.drawCircle(x, y, AndroidUtilities.dp(9), circlePaint);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        if (colorWheelRadius != AndroidUtilities.dp(120)) {
            colorWheelRadius = AndroidUtilities.dp(120);//Math.max(1, width / 2 - paramValueSliderWidth - AndroidUtilities.dp(20));
            colorWheelBitmap = createColorWheelBitmap(colorWheelRadius * 2, colorWheelRadius * 2);
            colorGradient = null;
        }
    }

    private Bitmap createColorWheelBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int colorCount = 12;
        int colorAngleStep = 360 / 12;
        int[] colors = new int[colorCount + 1];
        float[] hsv = new float[]{0.0f, 1.0f, 1.0f};
        for (int i = 0; i < colors.length; i++) {
            hsv[0] = (i * colorAngleStep + 180) % 360;
            colors[i] = Color.HSVToColor(hsv);
        }
        colors[colorCount] = colors[0];

        SweepGradient sweepGradient = new SweepGradient(0.5f * width, 0.5f * height, colors, null);
        RadialGradient radialGradient = new RadialGradient(0.5f * width, 0.5f * height, colorWheelRadius, 0xffffffff, 0x00ffffff, Shader.TileMode.CLAMP);
        ComposeShader composeShader = new ComposeShader(sweepGradient, radialGradient, PorterDuff.Mode.SRC_OVER);

        colorWheelPaint.setShader(composeShader);

        Canvas canvas = new Canvas(bitmap);
        canvas.drawCircle(0.5f * width, 0.5f * height, colorWheelRadius, colorWheelPaint);

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
                int cx = x - centerX;
                int cy = y - centerY;
                double d = Math.sqrt(cx * cx + cy * cy);

                if (circlePressed || !colorPressed && d <= colorWheelRadius) {
                    if (d > colorWheelRadius) {
                        d = colorWheelRadius;
                    }
                    if (!circlePressed) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    circlePressed = true;
                    colorHSV[0] = (float) (Math.toDegrees(Math.atan2(cy, cx)) + 180.0f);
                    colorHSV[1] = Math.max(0.0f, Math.min(1.0f, (float) (d / colorWheelRadius)));
                    colorGradient = null;
                }
                if (colorPressed || !circlePressed && x >= lx && x <= lx + paramValueSliderWidth && y >= ly && y <= ly + colorWheelRadius * 2) {
                    float value = (y - ly) / (colorWheelRadius * 2.0f);
                    if (value < 0.0f) {
                        value = 0.0f;
                    } else if (value > 1.0f) {
                        value = 1.0f;
                    }
                    colorHSV[2] = value;
                    if (!colorPressed) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    colorPressed = true;
                }
                if (colorPressed || circlePressed) {
                    int color = getColor();
                    if (!ignoreTextChange) {
                        int red = Color.red(color);
                        int green = Color.green(color);
                        int blue = Color.blue(color);
                        ignoreTextChange = true;
                        colorEditText[0].setText(String.format("#%02x%02x%02x", (byte) red, (byte) green, (byte) blue).toUpperCase());
                        colorEditText[1].setText(String.valueOf((int) (255 * getBrightness())));
                        for (int b = 0; b < 2; b++) {
                            colorEditText[b].setSelection(colorEditText[b].length());
                        }
                        ignoreTextChange = false;
                    }
                    delegate.setColor(color);
                    invalidate();
                }

                return true;
            case MotionEvent.ACTION_UP:
                colorPressed = false;
                circlePressed = false;
                break;
        }
        return super.onTouchEvent(event);
    }

    public void setColor(int color) {
        if (!ignoreTextChange) {
            ignoreTextChange = true;
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            Color.colorToHSV(color, colorHSV);
            colorEditText[0].setText(String.format("#%02x%02x%02x", (byte) red, (byte) green, (byte) blue).toUpperCase());
            colorEditText[1].setText(String.valueOf((int) (255 * getBrightness())));
            for (int b = 0; b < 2; b++) {
                colorEditText[b].setSelection(colorEditText[b].length());
            }
            ignoreTextChange = false;
        } else {
            Color.colorToHSV(color, colorHSV);
        }
        colorGradient = null;
        invalidate();
    }

    public int getColor() {
        hsvTemp[0] = colorHSV[0];
        hsvTemp[1] = colorHSV[1];
        hsvTemp[2] = getBrightness();
        return (Color.HSVToColor(hsvTemp) & 0x00ffffff) | 0xff000000;
    }

    private float getBrightness() {
        float brightness = colorHSV[2];
        colorHSV[2] = 1f;
        int color = Color.HSVToColor(colorHSV);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        colorHSV[2] = brightness;

        float min = minBrightness == null ? 0f : minBrightness.getLimit(red, green, blue);
        float max = maxBrightness == null ? 1f : maxBrightness.getLimit(red, green, blue);
        return Math.max(min, Math.min(brightness, max));
    }

    public void setMinBrightness(BrightnessLimit limit) {
        minBrightness = limit;
    }

    public void setMaxBrightness(BrightnessLimit limit) {
        maxBrightness = limit;
    }


    public void provideThemeDescriptions(List<ThemeDescription> arrayList) {
        for (int a = 0; a < colorEditText.length; a++) {
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_HINTTEXTCOLOR | ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        }
    }


    public interface ColorPickerDelegate {
        void setColor(int color);
    }

    public interface BrightnessLimit {
        float getLimit(int r, int g, int b);
    }

}
