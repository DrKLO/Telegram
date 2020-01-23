package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;

import java.util.ArrayList;
import java.util.List;

public class ColorPicker extends FrameLayout {

    private final ColorPickerDelegate delegate;

    private Paint colorWheelPaint;
    private Paint valueSliderPaint;
    private Paint circlePaint;
    private Paint linePaint;
    private Paint editTextCirclePaint;
    private Drawable circleDrawable;

    private boolean myMessagesColor;

    private RectF sliderRect = new RectF();

    boolean ignoreTextChange;

    private Bitmap colorWheelBitmap;

    private int selectedEditText;

    private EditTextBoldCursor[] colorEditText = new EditTextBoldCursor[4];
    private ImageView clearButton;
    private ImageView exchangeButton;
    private TextView resetButton;
    private ActionBarMenuItem menuItem;

    private int originalFirstColor;
    private int currentResetType;

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

    private static final int item_edit = 1;
    private static final int item_share = 2;
    private static final int item_delete = 3;

    public ColorPicker(Context context, boolean hasMenu, ColorPickerDelegate colorPickerDelegate) {
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

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 54, Gravity.LEFT | Gravity.TOP, 22, 0, 22, 0));
        for (int a = 0; a < 4; a++) {
            final int num = a;
            if (a == 0 || a == 2) {
                colorEditText[a] = new EditTextBoldCursor(context) {

                    private int lastColor = 0xffffffff;

                    @Override
                    public boolean onTouchEvent(MotionEvent event) {
                        if (getAlpha() != 1.0f) {
                            return false;
                        }
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            if (colorEditText[num + 1].isFocused()) {
                                AndroidUtilities.showKeyboard(colorEditText[num + 1]);
                            } else {
                                colorEditText[num + 1].requestFocus();
                            }
                        }
                        return false;
                    }

                    @Override
                    protected void onDraw(Canvas canvas) {
                        super.onDraw(canvas);

                        int color = lastColor = getFieldColor(num + 1, lastColor);
                        editTextCirclePaint.setColor(color);
                        canvas.drawCircle(AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(10), editTextCirclePaint);
                    }
                };
                colorEditText[a].setBackgroundDrawable(null);
                colorEditText[a].setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(5), 0, AndroidUtilities.dp(18));
                colorEditText[a].setText("#");
                colorEditText[a].setEnabled(false);
                colorEditText[a].setFocusable(false);
                linearLayout.addView(colorEditText[a], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, a == 2 ? 39 : 0, 0, 0, 0));
            } else {
                colorEditText[a] = new EditTextBoldCursor(context) {
                    @Override
                    public boolean onTouchEvent(MotionEvent event) {
                        if (getAlpha() != 1.0f) {
                            return false;
                        }
                        if (!isFocused()) {
                            requestFocus();
                            return false;
                        } else {
                            AndroidUtilities.showKeyboard(this);
                        }
                        return super.onTouchEvent(event);
                    }

                    @Override
                    public boolean getGlobalVisibleRect(Rect r, Point globalOffset) {
                        boolean value = super.getGlobalVisibleRect(r, globalOffset);
                        r.bottom += AndroidUtilities.dp(40);
                        return value;
                    }

                    @Override
                    public void invalidate() {
                        super.invalidate();
                        colorEditText[num - 1].invalidate();
                    }
                };
                colorEditText[a].setBackgroundDrawable(null);
                colorEditText[a].setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
                colorEditText[a].setPadding(0, AndroidUtilities.dp(5), 0, AndroidUtilities.dp(18));
                colorEditText[a].setHint("8BC6ED");
                linearLayout.addView(colorEditText[a], LayoutHelper.createLinear(71, LayoutHelper.MATCH_PARENT));
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
                        for (int a = 0; a < editable.length(); a++) {
                            char ch = editable.charAt(a);
                            if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F')) {
                                editable.replace(a, a + 1, "");
                                a--;
                            }
                        }

                        if (editable.length() == 0) {
                            ignoreTextChange = false;
                            return;
                        }

                        setColorInner(getFieldColor(num, 0xffffffff));
                        int color = getColor();
                        if (editable.length() == 6) {
                            editable.replace(0, editable.length(), String.format("%02x%02x%02x", (byte) Color.red(color), (byte) Color.green(color), (byte) Color.blue(color)).toUpperCase());
                            colorEditText[num].setSelection(editable.length());
                        }
                        delegate.setColor(color, num == 1 ? 0 : 1, true);

                        ignoreTextChange = false;
                    }
                });
                colorEditText[a].setOnFocusChangeListener((v, hasFocus) -> {
                    if (colorEditText[3] == null) {
                        return;
                    }
                    selectedEditText = num == 1 ? 0 : 1;
                    setColorInner(getFieldColor(num, 0xffffffff));
                });
                colorEditText[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (i == EditorInfo.IME_ACTION_DONE) {
                        AndroidUtilities.hideKeyboard(textView);
                        return true;
                    }
                    return false;
                });
            }
            colorEditText[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            colorEditText[a].setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            colorEditText[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            colorEditText[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            colorEditText[a].setCursorSize(AndroidUtilities.dp(20));
            colorEditText[a].setCursorWidth(1.5f);
            colorEditText[a].setSingleLine(true);
            colorEditText[a].setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            colorEditText[a].setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            colorEditText[a].setTransformHintToHeader(true);
            colorEditText[a].setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            colorEditText[a].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            if (a == 1) {
                colorEditText[a].requestFocus();
            } else if (a == 2 || a == 3) {
                colorEditText[a].setVisibility(GONE);
            }
        }

        exchangeButton = new ImageView(getContext());
        exchangeButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 1));
        exchangeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        exchangeButton.setScaleType(ImageView.ScaleType.CENTER);
        exchangeButton.setVisibility(GONE);
        exchangeButton.setOnClickListener(v -> {
            if (exchangeButton.getAlpha() != 1.0f) {
                return;
            }
            if (myMessagesColor) {
                String text1 = colorEditText[1].getText().toString();
                String text2 = colorEditText[3].getText().toString();
                colorEditText[1].setText(text2);
                colorEditText[1].setSelection(text2.length());
                colorEditText[3].setText(text1);
                colorEditText[3].setSelection(text1.length());
            } else {
                delegate.rotateColors();
                exchangeButton.animate().rotation(exchangeButton.getRotation() + 45).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
            }
        });
        addView(exchangeButton, LayoutHelper.createFrame(42, 42, Gravity.LEFT | Gravity.TOP, 126, 0, 0, 0));

        clearButton = new ImageView(getContext());
        clearButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 1));
        clearButton.setImageDrawable(new CloseProgressDrawable2());
        clearButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setVisibility(GONE);
        clearButton.setOnClickListener(v -> {
            boolean hide = clearButton.getTag() != null;
            if (myMessagesColor && hide) {
                colorEditText[1].setText(String.format("%02x%02x%02x", (byte) Color.red(originalFirstColor), (byte) Color.green(originalFirstColor), (byte) Color.blue(originalFirstColor)).toUpperCase());
                colorEditText[1].setSelection(colorEditText[1].length());
            }
            toggleSecondField();
            if (myMessagesColor && !hide) {
                originalFirstColor = getFieldColor(1, 0xffffffff);
                int color = Theme.getColor(Theme.key_chat_outBubble);
                colorEditText[1].setText(String.format("%02x%02x%02x", (byte) Color.red(color), (byte) Color.green(color), (byte) Color.blue(color)).toUpperCase());
                colorEditText[1].setSelection(colorEditText[1].length());
            }
            int color2 = getFieldColor(3, 0xff000000);
            if (!hide) {
                color2 = generateGradientColors(getFieldColor(1, 0));
                String text = String.format("%02x%02x%02x", (byte) Color.red(color2), (byte) Color.green(color2), (byte) Color.blue(color2)).toUpperCase();
                colorEditText[3].setText(text);
                colorEditText[3].setSelection(text.length());
            }
            delegate.setColor(hide ? 0 : color2, 1, true);
            if (hide) {
                if (colorEditText[3].isFocused()){
                    colorEditText[1].requestFocus();
                }
            } else {
                colorEditText[3].requestFocus();
            }
        });
        clearButton.setContentDescription(LocaleController.getString("ClearButton", R.string.ClearButton));
        addView(clearButton, LayoutHelper.createFrame(42, 42, Gravity.TOP | Gravity.RIGHT, 0, 0, 9, 0));

        resetButton = new TextView(context);
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        resetButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        resetButton.setGravity(Gravity.CENTER);
        resetButton.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
        resetButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(resetButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.RIGHT, 0, 3, 14, 0));
        resetButton.setOnClickListener(v -> {
            if (resetButton.getAlpha() != 1.0f) {
                return;
            }
            delegate.setColor(0, -1, true);
            resetButton.animate().alpha(0.0f).setDuration(180).start();
            resetButton.setTag(null);
        });

        if (hasMenu) {
            menuItem = new ActionBarMenuItem(context, null, 0, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            menuItem.setLongClickEnabled(false);
            menuItem.setIcon(R.drawable.ic_ab_other);
            menuItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
            menuItem.addSubItem(item_edit, R.drawable.msg_edit, LocaleController.getString("OpenInEditor", R.string.OpenInEditor));
            menuItem.addSubItem(item_share, R.drawable.msg_share, LocaleController.getString("ShareTheme", R.string.ShareTheme));
            menuItem.addSubItem(item_delete, R.drawable.msg_delete, LocaleController.getString("DeleteTheme", R.string.DeleteTheme));
            menuItem.setMenuYOffset(-AndroidUtilities.dp(80));
            menuItem.setSubMenuOpenSide(2);
            menuItem.setDelegate(id -> {
                if (id == item_edit || id == item_share) {
                    delegate.openThemeCreate(id == item_share);
                } else if (id == item_delete) {
                    delegate.deleteTheme();
                }
            });
            menuItem.setAdditionalYOffset(AndroidUtilities.dp(72));
            menuItem.setTranslationX(AndroidUtilities.dp(6));
            menuItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 1));
            addView(menuItem, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.RIGHT, 0, -3, 7, 0));
            menuItem.setOnClickListener(v -> menuItem.toggleSubMenu());
        }
    }

    public void hideKeyboard() {
        AndroidUtilities.hideKeyboard(colorEditText[selectedEditText == 0 ? 1 : 3]);
    }

    private void toggleSecondField() {
        boolean hide = clearButton.getTag() != null;
        clearButton.setTag(hide ? null : 1);
        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(ObjectAnimator.ofFloat(clearButton, View.ROTATION, hide ? 45 : 0));
        animators.add(ObjectAnimator.ofFloat(colorEditText[2], View.ALPHA, hide ? 0.0f : 1.0f));
        animators.add(ObjectAnimator.ofFloat(colorEditText[3], View.ALPHA, hide ? 0.0f : 1.0f));
        animators.add(ObjectAnimator.ofFloat(exchangeButton, View.ALPHA, hide ? 0.0f : 1.0f));
        if (currentResetType == 2 && !hide) {
            animators.add(ObjectAnimator.ofFloat(resetButton, View.ALPHA, 0.0f));
        }
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentResetType == 2 && !hide) {
                    resetButton.setVisibility(GONE);
                    resetButton.setTag(null);
                }
            }
        });
        animatorSet.playTogether(animators);
        animatorSet.setDuration(180);
        animatorSet.start();

        if (hide && !ignoreTextChange && (minBrightness > 0f || maxBrightness < 1f)) {
            setColorInner(getFieldColor(1, 0xffffffff));
            int color = getColor();
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            ignoreTextChange = true;
            String text = String.format("%02x%02x%02x", (byte) red, (byte) green, (byte) blue).toUpperCase();
            colorEditText[1].setText(text);
            colorEditText[1].setSelection(text.length());
            ignoreTextChange = false;
            delegate.setColor(color, 0, true);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(colorWheelBitmap, 0, AndroidUtilities.dp(54), null);
        int y = AndroidUtilities.dp(54) + colorWheelBitmap.getHeight();
        canvas.drawRect(0, AndroidUtilities.dp(54), getMeasuredWidth(), AndroidUtilities.dp(54) + 1, linePaint);
        canvas.drawRect(0, y - 1, getMeasuredWidth(), y, linePaint);

        hsvTemp[0] = colorHSV[0];
        hsvTemp[1] = colorHSV[1];
        hsvTemp[2] = 1f;

        int colorPointX = (int) (colorHSV[0] * getMeasuredWidth() / 360);
        int colorPointY = (int) (AndroidUtilities.dp(54) + (colorWheelBitmap.getHeight() * (1.0f - colorHSV[1])));
        if (!circlePressed) {
            int minD = AndroidUtilities.dp(16);
            float progress = CubicBezierInterpolator.EASE_OUT.getInterpolation(pressedMoveProgress);
            if (colorPointX < minD) {
                colorPointX += progress * (minD - colorPointX);
            } else if (colorPointX > getMeasuredWidth() - minD) {
                colorPointX -= progress * (colorPointX - (getMeasuredWidth() - minD));
            }
            if (colorPointY < AndroidUtilities.dp(54) + minD) {
                colorPointY += progress * (AndroidUtilities.dp(54) + minD - colorPointY);
            } else if (colorPointY > AndroidUtilities.dp(54) + colorWheelBitmap.getHeight() - minD) {
                colorPointY -= progress * (colorPointY - (AndroidUtilities.dp(54) + colorWheelBitmap.getHeight() - minD));
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

    private int getFieldColor(int num, int defaultColor) {
        try {
            return Integer.parseInt(colorEditText[num].getText().toString(), 16) | 0xff000000;
        } catch (Exception ignore) {
            return defaultColor;
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

                if (circlePressed || !colorPressed && y >= AndroidUtilities.dp(54) && y <= AndroidUtilities.dp(54) + colorWheelBitmap.getHeight()) {
                    if (!circlePressed) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    circlePressed = true;
                    pressedMoveProgress = 0.0f;
                    lastUpdateTime = SystemClock.elapsedRealtime();

                    x = Math.max(0, Math.min(x, colorWheelBitmap.getWidth()));
                    y = Math.max(AndroidUtilities.dp(54), Math.min(y, AndroidUtilities.dp(54) + colorWheelBitmap.getHeight()));

                    float oldBrightnessPos = minHsvBrightness == maxHsvBrightness ? 0.5f : (getBrightness() - minHsvBrightness) / (maxHsvBrightness - minHsvBrightness);
                    colorHSV[0] = x * 360f / colorWheelBitmap.getWidth();
                    colorHSV[1] = 1.0f - (1.0f / colorWheelBitmap.getHeight() * (y - AndroidUtilities.dp(54)));
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
                    if (!ignoreTextChange) {
                        int red = Color.red(color);
                        int green = Color.green(color);
                        int blue = Color.blue(color);
                        ignoreTextChange = true;
                        String text = String.format("%02x%02x%02x", (byte) red, (byte) green, (byte) blue).toUpperCase();
                        Editable editable = colorEditText[selectedEditText == 0 ? 1 : 3].getText();
                        editable.replace(0, editable.length(), text);
                        ignoreTextChange = false;
                    }
                    delegate.setColor(color, selectedEditText, false);
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
        int defaultColor = delegate.getDefaultColor(selectedEditText);
        if (defaultColor == 0 || defaultColor != color) {
            updateHsvMinMaxBrightness();
        }
        colorGradient = null;
        invalidate();
    }

    public void setColor(int color, int num) {
        if (!ignoreTextChange) {
            ignoreTextChange = true;
            String text = String.format("%02x%02x%02x", (byte) Color.red(color), (byte) Color.green(color), (byte) Color.blue(color)).toUpperCase();
            colorEditText[num == 0 ? 1 : 3].setText(text);
            colorEditText[num == 0 ? 1 : 3].setSelection(text.length());
            ignoreTextChange = false;
        }
        setColorInner(color);
        if (num == 1 && color != 0 && clearButton.getTag() == null) {
            toggleSecondField();
        }
    }

    public void setHasChanges(boolean value) {
        if (value && resetButton.getTag() != null || !value && resetButton.getTag() == null || clearButton.getTag() != null) {
            return;
        }
        resetButton.setTag(value ? 1 : null);
        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        if (value) {
            resetButton.setVisibility(VISIBLE);
        }
        animators.add(ObjectAnimator.ofFloat(resetButton, View.ALPHA, value ? 1.0f : 0.0f));
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!value) {
                    resetButton.setVisibility(GONE);
                }
            }
        });
        animatorSet.playTogether(animators);
        animatorSet.setDuration(180);
        animatorSet.start();
    }

    public void setType(int resetType, boolean hasChanges, boolean twoColors, boolean hasSecondColor, boolean myMessages, int angle, boolean animated) {
        currentResetType = resetType;
        myMessagesColor = myMessages;
        if (myMessagesColor) {
            exchangeButton.setImageResource(R.drawable.menu_switch);
            exchangeButton.setRotation(0);
        } else {
            exchangeButton.setImageResource(R.drawable.editor_rotate);
            exchangeButton.setRotation(angle - 45);
        }
        if (menuItem != null) {
            if (resetType == 1) {
                menuItem.setVisibility(VISIBLE);
            } else {
                menuItem.setVisibility(GONE);
            }
        }

        ArrayList<Animator> animators;
        if (animated) {
            animators = new ArrayList<>();
        } else {
            animators = null;
        }

        if (!twoColors || !hasSecondColor) {
            colorEditText[1].requestFocus();
        }
        for (int a = 2; a < 4; a++) {
            if (animated) {
                if (twoColors) {
                    colorEditText[a].setVisibility(VISIBLE);
                }
                animators.add(ObjectAnimator.ofFloat(colorEditText[a], View.ALPHA, twoColors && hasSecondColor ? 1.0f : 0.0f));
            } else {
                colorEditText[a].setVisibility(twoColors ? VISIBLE : GONE);
                colorEditText[a].setAlpha(twoColors && hasSecondColor ? 1.0f : 0.0f);
            }
            colorEditText[a].setTag(twoColors ? 1 : null);
        }
        if (animated) {
            if (twoColors) {
                exchangeButton.setVisibility(VISIBLE);
            }
            animators.add(ObjectAnimator.ofFloat(exchangeButton, View.ALPHA, twoColors && hasSecondColor ? 1.0f : 0.0f));
        } else {
            exchangeButton.setVisibility(twoColors ? VISIBLE : GONE);
            exchangeButton.setAlpha(twoColors && hasSecondColor ? 1.0f : 0.0f);
        }
        if (twoColors) {
            clearButton.setTag(hasSecondColor ? 1 : null);
            clearButton.setRotation(hasSecondColor ? 0 : 45);
        }
        if (animated) {
            if (twoColors) {
                clearButton.setVisibility(VISIBLE);
            }
            animators.add(ObjectAnimator.ofFloat(clearButton, View.ALPHA, twoColors ? 1.0f : 0.0f));
        } else {
            clearButton.setVisibility(twoColors ? VISIBLE : GONE);
            clearButton.setAlpha(twoColors ? 1.0f : 0.0f);
        }

        resetButton.setTag(hasChanges ? 1 : null);
        resetButton.setText(resetType == 1 ? LocaleController.getString("ColorPickerResetAll", R.string.ColorPickerResetAll) : LocaleController.getString("ColorPickerReset", R.string.ColorPickerReset));
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) resetButton.getLayoutParams();
        layoutParams.rightMargin = AndroidUtilities.dp(resetType == 1 ? 14 : (14 + 47));
        if (animated) {
            if (!hasChanges || resetButton.getVisibility() == VISIBLE && hasSecondColor) {
                animators.add(ObjectAnimator.ofFloat(resetButton, View.ALPHA, 0.0f));
            } else if (resetButton.getVisibility() != VISIBLE && !hasSecondColor) {
                resetButton.setVisibility(VISIBLE);
                animators.add(ObjectAnimator.ofFloat(resetButton, View.ALPHA, 1.0f));
            }
        } else {
            resetButton.setAlpha(!hasChanges || hasSecondColor ? 0.0f : 1.0f);
            resetButton.setVisibility(!hasChanges || hasSecondColor ? GONE : VISIBLE);
        }

        if (animators != null && !animators.isEmpty()) {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.setDuration(180);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!hasChanges || hasSecondColor) {
                        resetButton.setVisibility(GONE);
                    }
                    if (!twoColors) {
                        clearButton.setVisibility(GONE);
                        exchangeButton.setVisibility(GONE);
                        for (int a = 2; a < 4; a++) {
                            colorEditText[a].setVisibility(GONE);
                        }
                    }
                }
            });
            animatorSet.start();
        }
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
        float min = clearButton.getTag() != null ? 0f : minBrightness;
        float max = clearButton.getTag() != null ? 1f : maxBrightness;
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

    public void provideThemeDescriptions(List<ThemeDescription> arrayList) {
        for (int a = 0; a < colorEditText.length; a++) {
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_HINTTEXTCOLOR | ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
            arrayList.add(new ThemeDescription(colorEditText[a], ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        }
        arrayList.add(new ThemeDescription(clearButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(clearButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_dialogButtonSelector));
        arrayList.add(new ThemeDescription(exchangeButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(exchangeButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_dialogButtonSelector));
        if (menuItem != null) {
            ThemeDescription.ThemeDescriptionDelegate delegate = () -> {
                menuItem.setIconColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                Theme.setDrawableColor(menuItem.getBackground(), Theme.getColor(Theme.key_dialogButtonSelector));
                menuItem.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), false);
                menuItem.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), true);
                menuItem.redrawPopup(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
            };
            arrayList.add(new ThemeDescription(menuItem, 0, null, null, null, delegate, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(menuItem, 0, null, null, null, delegate, Theme.key_dialogButtonSelector));
            arrayList.add(new ThemeDescription(menuItem, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuItem));
            arrayList.add(new ThemeDescription(menuItem, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuItemIcon));
            arrayList.add(new ThemeDescription(menuItem, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuBackground));
        }
    }

    public interface ColorPickerDelegate {
        void setColor(int color, int num, boolean applyNow);

        default void openThemeCreate(boolean share) {

        }

        default void deleteTheme() {

        }

        default void rotateColors() {

        }

        default int getDefaultColor(int num) {
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
