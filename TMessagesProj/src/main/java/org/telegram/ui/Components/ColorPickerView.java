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
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.WallpaperActivity;

import java.util.ArrayList;

public class ColorPickerView extends FrameLayout {

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

    private onColorChangeListener listener;

    public ColorPickerView(Context context) {
        super(context);

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
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 64, Gravity.LEFT | Gravity.TOP, 12, 14, 21, 0));
        for (int a = 0; a < 2; a++) {
            final int num = a;

            colorEditText[a] = new EditTextBoldCursor(context) {
                @Override
                public void setTextColor(int color) {
                    super.setTextColor(color);
                    setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
                }
            };
            colorEditText[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            colorEditText[a].setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            colorEditText[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            colorEditText[a].setBackgroundDrawable(null);
            colorEditText[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            colorEditText[a].setCursorSize(AndroidUtilities.dp(20));
            colorEditText[a].setCursorWidth(1.5f);
            colorEditText[a].setSingleLine(true);
            colorEditText[a].setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            colorEditText[a].setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            colorEditText[a].setTransformHintToHeader(true);
            colorEditText[a].setPadding(0, 0, 0, 0);
            if (a == 0) {
                colorEditText[a].setInputType(InputType.TYPE_CLASS_TEXT);
                colorEditText[a].setHintText(LocaleController.getString("BackgroundHexColorCode", R.string.BackgroundHexColorCode));
            } else {
                colorEditText[a].setInputType(InputType.TYPE_CLASS_NUMBER);
                colorEditText[a].setHintText(LocaleController.getString("BackgroundBrightness", R.string.BackgroundBrightness));
            }
            colorEditText[a].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            InputFilter[] inputFilters = new InputFilter[1];
            inputFilters[0] = new InputFilter.LengthFilter(a == 0 ? 7 : 3);
            colorEditText[a].setFilters(inputFilters);
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
                        try {
                            setColor(Integer.parseInt(editable.toString().substring(1), 16) | 0xff000000);
                        } catch (Exception e) {
                            setColor(0xffffffff);
                        }
                        listener.onColorChanged(getColor());
                        colorEditText[1].setText("" + (int) (255 * colorHSV[2]));
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
                        listener.onColorChanged(getColor());
                        int red = Color.red(getColor());
                        int green = Color.green(getColor());
                        int blue = Color.blue(getColor());
                        colorEditText[0].setText(String.format("#%02x%02x%02x", (byte) red, (byte) green, (byte) blue));
                    }
                    invalidate();
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

        float pointerRadius = 0.075f * colorWheelRadius;

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
        drawPointerArrow(canvas, lx + width / 2, (int) (ly + colorHSV[2] * height), Color.HSVToColor(colorHSV));
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
            colorWheelBitmap = createColorWheelBitmap(colorWheelRadius * 2, colorWheelRadius * 2, colorWheelRadius, colorWheelPaint);
            colorGradient = null;
        }
    }

    public static Bitmap createColorWheelBitmap(int width, int height,int colorWheelRadius,Paint paint) {
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

        SweepGradient sweepGradient = new SweepGradient(width / 2, height / 2, colors, null);
        RadialGradient radialGradient = new RadialGradient(width / 2, height / 2, colorWheelRadius, 0xffffffff, 0x00ffffff, Shader.TileMode.CLAMP);
        ComposeShader composeShader = new ComposeShader(sweepGradient, radialGradient, PorterDuff.Mode.SRC_OVER);

        paint.setShader(composeShader);

        Canvas canvas = new Canvas(bitmap);
        canvas.drawCircle(width / 2, height / 2, colorWheelRadius, paint);

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
                    if(listener != null) listener.onColorChanged(getColor());
                    if (!ignoreTextChange) {
                        int red = Color.red(getColor());
                        int green = Color.green(getColor());
                        int blue = Color.blue(getColor());
                        ignoreTextChange = true;
                        colorEditText[0].setText(String.format("#%02x%02x%02x", (byte) red, (byte) green, (byte) blue));
                        colorEditText[1].setText("" + (int) (255 * colorHSV[2]));
                        for (int b = 0; b < 2; b++) {
                            colorEditText[b].setSelection(colorEditText[b].length());
                        }
                        ignoreTextChange = false;
                    }
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
            colorEditText[0].setText(String.format("#%02x%02x%02x", (byte) red, (byte) green, (byte) blue));
            colorEditText[1].setText("" + (int) (255 * colorHSV[2]));
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
        return (Color.HSVToColor(colorHSV) & 0x00ffffff) | 0xff000000;
    }

    public void addThemeDescriptions(ArrayList<ThemeDescription> arrayList) {
        for (int a = 0; a < colorEditText.length; a++) {
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_HINTTEXTCOLOR | ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_windowBackgroundWhiteRedText3));
        }
    }

    public interface onColorChangeListener {
        void onColorChanged(int color);
    }

    public void setListener(onColorChangeListener listener) {
        this.listener = listener;
    }


    public static class ColorPickerDialogView extends FrameLayout {

        public ColorPickerView colorPickerView;
        public TextView saveButton;
        public TextView cancelButton;

        public ColorPickerDialogView(@NonNull Context context) {
            super(context);

            colorPickerView = new ColorPickerView(context);
            colorPickerView.setColor(Theme.getCurrentTheme().accentColor);

            addView(colorPickerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) colorPickerView.getLayoutParams();
            lp.bottomMargin = AndroidUtilities.dp(40);

            FrameLayout buttonsContainer = new FrameLayout(context) {
                @Override
                public void onDraw(Canvas canvas) {
                    int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                    Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                    Theme.chat_composeShadowDrawable.draw(canvas);
                }
            };

            buttonsContainer.setWillNotDraw(false);
            buttonsContainer.setPadding(0, AndroidUtilities.dp(3), 0, 0);
            addView(buttonsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));


            cancelButton = new TextView(context);
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            cancelButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            cancelButton.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
            cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
            cancelButton.setGravity(Gravity.CENTER);
            cancelButton.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
            cancelButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0));
            buttonsContainer.addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));


            saveButton = new TextView(context);
            saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            saveButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            saveButton.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
            saveButton.setText(LocaleController.getString("Save", R.string.Save).toUpperCase());
            saveButton.setGravity(Gravity.CENTER);
            saveButton.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
            saveButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0));
            buttonsContainer.addView(saveButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP));
        }

        public void addThemeDescriptions(ArrayList<ThemeDescription> list) {
            colorPickerView.addThemeDescriptions(list);
            list.add(new ThemeDescription(saveButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));
            list.add(new ThemeDescription(cancelButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));
        }
    }
}