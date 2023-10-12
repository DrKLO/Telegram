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
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;

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
    private Drawable circleDrawable;

    private boolean myMessagesColor;

    private RectF sliderRect = new RectF();

    boolean ignoreTextChange;

    private Bitmap colorWheelBitmap;

    private RadioButton[] radioButton = new RadioButton[4];
    private FrameLayout radioContainer;

    private LinearLayout linearLayout;

    private AnimatorSet colorsAnimator;

    private EditTextBoldCursor[] colorEditText;
    private ImageView clearButton;
    private ImageView addButton;
    private TextView resetButton;
    private ActionBarMenuItem menuItem;

    private int originalFirstColor;
    private int currentResetType;

    private int colorsCount = 1;
    private int maxColorsCount = 1;

    private int colorWheelWidth;

    private float[] colorHSV = new float[] { 0.0f, 0.0f, 1.0f };

    private float[] hsvTemp = new float[3];
    private LinearGradient colorGradient;

    private boolean circlePressed;
    private boolean colorPressed;

    private int selectedColor;
    private int prevSelectedColor;

    private float pressedMoveProgress = 1.0f;
    private long lastUpdateTime;

    private float minBrightness = 0f;
    private float maxBrightness = 1f;

    private float minHsvBrightness = 0f;
    private float maxHsvBrightness = 1f;

    private static final int item_edit = 1;
    private static final int item_share = 2;
    private static final int item_delete = 3;

    Theme.ResourcesProvider resourcesProvider;

    private static class RadioButton extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ObjectAnimator checkAnimator;
        private float checkedState;
        private boolean checked;
        private int currentColor;

        public RadioButton(Context context) {
            super(context);
        }

        void updateCheckedState(boolean animate) {
            if (checkAnimator != null) {
                checkAnimator.cancel();
            }

            if (animate) {
                checkAnimator = ObjectAnimator.ofFloat(this, "checkedState", checked ? 1f : 0f);
                checkAnimator.setDuration(200);
                checkAnimator.start();
            } else {
                setCheckedState(checked ? 1f : 0f);
            }
        }

        public void setChecked(boolean value, boolean animated) {
            checked = value;
            updateCheckedState(animated);
        }

        public void setColor(int color) {
            currentColor = color;
            invalidate();
        }

        public int getColor() {
            return currentColor;
        }

        @Keep
        public void setCheckedState(float state) {
            checkedState = state;
            invalidate();
        }

        @Keep
        public float getCheckedState() {
            return checkedState;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateCheckedState(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float radius = AndroidUtilities.dp(15);

            float cx = 0.5f * getMeasuredWidth();
            float cy = 0.5f * getMeasuredHeight();

            paint.setColor(currentColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(3));
            paint.setAlpha(Math.round(255f * checkedState));
            canvas.drawCircle(cx, cy, radius - 0.5f * paint.getStrokeWidth(), paint);

            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, radius - AndroidUtilities.dp(5) * checkedState, paint);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setText(LocaleController.getString("ColorPickerMainColor", R.string.ColorPickerMainColor));
            info.setClassName(Button.class.getName());
            info.setChecked(checked);
            info.setCheckable(true);
            info.setEnabled(true);
        }
    }

    public ColorPicker(Context context, boolean hasMenu, ColorPickerDelegate colorPickerDelegate) {
        super(context);

        delegate = colorPickerDelegate;
        colorEditText = new EditTextBoldCursor[2];

        setWillNotDraw(false);

        circleDrawable = context.getResources().getDrawable(R.drawable.knob_shadow).mutate();

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        colorWheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        valueSliderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        linePaint = new Paint();
        linePaint.setColor(0x12000000);

        setClipChildren(false);

        linearLayout = new LinearLayout(context) {

            private RectF rect = new RectF();
            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                paint.setColor(getThemedColor(Theme.key_dialogBackgroundGray));
                int left = colorEditText[0].getLeft() - AndroidUtilities.dp(13);
                int width = (int) (AndroidUtilities.dp(91) + (clearButton.getVisibility() == VISIBLE ? AndroidUtilities.dp(25) * clearButton.getAlpha() : 0));
                rect.set(left, AndroidUtilities.dp(5), left + width, AndroidUtilities.dp(5 + 32));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), paint);
            }
        };
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 54, Gravity.LEFT | Gravity.TOP, 27, -6, 17, 0));
        linearLayout.setWillNotDraw(false);

        radioContainer = new FrameLayout(context);
        radioContainer.setClipChildren(false);
        addView(radioContainer, LayoutHelper.createFrame(174, 30, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 72, 1, 0, 0));

        for (int a = 0; a < 4; a++) {
            radioButton[a] = new RadioButton(context);
            radioButton[a].setChecked(selectedColor == a, false);
            radioContainer.addView(radioButton[a], LayoutHelper.createFrame(30, 30, Gravity.TOP, 0, 0, 0, 0));
            radioButton[a].setOnClickListener(v -> {
                RadioButton radioButton1 = (RadioButton) v;
                for (int b = 0; b < radioButton.length; b++) {
                    boolean checked = radioButton[b] == radioButton1;
                    radioButton[b].setChecked(checked, true);
                    if (checked) {
                        prevSelectedColor = selectedColor;
                        selectedColor = b;
                    }
                }
                int color = radioButton1.getColor();
                setColorInner(color);
                colorEditText[1].setText(String.format("%02x%02x%02x", (byte) Color.red(color), (byte) Color.green(color), (byte) Color.blue(color)).toUpperCase());
            });
        }

        for (int a = 0; a < colorEditText.length; a++) {
            final int num = a;
            if (a % 2 == 0) {
                colorEditText[a] = new EditTextBoldCursor(context) {
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
                };
                colorEditText[a].setBackgroundDrawable(null);
                colorEditText[a].setText("#");
                colorEditText[a].setEnabled(false);
                colorEditText[a].setFocusable(false);
                colorEditText[a].setPadding(0, AndroidUtilities.dp(5), 0, AndroidUtilities.dp(16));
                linearLayout.addView(colorEditText[a], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0, 0, 0, 0));
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
                colorEditText[a].setHint("8BC6ED");
                colorEditText[a].setPadding(0, AndroidUtilities.dp(5), 0, AndroidUtilities.dp(16));
                linearLayout.addView(colorEditText[a], LayoutHelper.createLinear(71, LayoutHelper.MATCH_PARENT, 0, 0, 0, 0));
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
                        radioButton[selectedColor].setColor(color);
                        delegate.setColor(color, selectedColor, true);

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
            colorEditText[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            colorEditText[a].setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
            colorEditText[a].setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            colorEditText[a].setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            colorEditText[a].setCursorSize(AndroidUtilities.dp(18));
            colorEditText[a].setCursorWidth(1.5f);
            colorEditText[a].setSingleLine(true);
            colorEditText[a].setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            colorEditText[a].setHeaderHintColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
            colorEditText[a].setTransformHintToHeader(true);
            colorEditText[a].setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            colorEditText[a].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            if (a == 1) {
                colorEditText[a].requestFocus();
            } else if (a == 2 || a == 3) {
                colorEditText[a].setVisibility(GONE);
            }
        }

        addButton = new ImageView(getContext());
        addButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 1));
        addButton.setImageResource(R.drawable.msg_add);
        addButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        addButton.setScaleType(ImageView.ScaleType.CENTER);
        addButton.setOnClickListener(v -> {
            if (colorsAnimator != null) {
                return;
            }
            ArrayList<Animator> animators;
            if (colorsCount == 1) {
                if (radioButton[1].getColor() == 0) {
                    radioButton[1].setColor(generateGradientColors(radioButton[0].getColor()));
                }
                if (myMessagesColor) {
                    delegate.setColor(radioButton[0].getColor(), 0, true);
                }
                delegate.setColor(radioButton[1].getColor(), 1, true);
                colorsCount = 2;
            } else if (colorsCount == 2) {
                colorsCount = 3;
                if (radioButton[2].getColor() == 0) {
                    int color = radioButton[0].getColor();
                    float[] hsv = new float[3];
                    Color.colorToHSV(color, hsv);
                    if (hsv[0] > 180) {
                        hsv[0] -= 60;
                    } else {
                        hsv[0] += 60;
                    }
                    radioButton[2].setColor(Color.HSVToColor(255, hsv));
                }
                delegate.setColor(radioButton[2].getColor(), 2, true);
            } else if (colorsCount == 3) {
                colorsCount = 4;
                if (radioButton[3].getColor() == 0) {
                    radioButton[3].setColor(generateGradientColors(radioButton[2].getColor()));
                }
                delegate.setColor(radioButton[3].getColor(), 3, true);
            } else {
                return;
            }

            animators = new ArrayList<>();
            if (colorsCount < maxColorsCount) {
                animators.add(ObjectAnimator.ofFloat(addButton, View.ALPHA, 1.0f));
                animators.add(ObjectAnimator.ofFloat(addButton, View.SCALE_X, 1.0f));
                animators.add(ObjectAnimator.ofFloat(addButton, View.SCALE_Y, 1.0f));
                animators.add(ObjectAnimator.ofFloat(addButton, View.TRANSLATION_X, AndroidUtilities.dp(30) * (colorsCount - 1) + AndroidUtilities.dp(13) * (colorsCount - 1)));
            } else {
                animators.add(ObjectAnimator.ofFloat(addButton, View.TRANSLATION_X, AndroidUtilities.dp(30) * (colorsCount - 1) + AndroidUtilities.dp(13) * (colorsCount - 1)));
                animators.add(ObjectAnimator.ofFloat(addButton, View.ALPHA, 0.0f));
                animators.add(ObjectAnimator.ofFloat(addButton, View.SCALE_X, 0.0f));
                animators.add(ObjectAnimator.ofFloat(addButton, View.SCALE_Y, 0.0f));
            }

            if (colorsCount > 1) {
                if (clearButton.getVisibility() != View.VISIBLE) {
                    clearButton.setScaleX(0f);
                    clearButton.setScaleY(0f);
                }
                clearButton.setVisibility(VISIBLE);

                animators.add(ObjectAnimator.ofFloat(clearButton, View.ALPHA, 1.0f));
                animators.add(ObjectAnimator.ofFloat(clearButton, View.SCALE_X, 1.0f));
                animators.add(ObjectAnimator.ofFloat(clearButton, View.SCALE_Y, 1.0f));
            }

            radioButton[colorsCount - 1].callOnClick();
            colorsAnimator = new AnimatorSet();
            updateColorsPosition(animators, 0, false, getMeasuredWidth());
            colorsAnimator.playTogether(animators);
            colorsAnimator.setDuration(180);
            colorsAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            colorsAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (colorsCount == maxColorsCount) {
                        addButton.setVisibility(INVISIBLE);
                    }
                    colorsAnimator = null;
                }
            });
            colorsAnimator.start();
        });
        addButton.setContentDescription(LocaleController.getString("Add", R.string.Add));
        addView(addButton, LayoutHelper.createFrame(30, 30, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 36, 1, 0, 0));

        clearButton = new ImageView(getContext()) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                linearLayout.invalidate();
            }
        };
        clearButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 1));
        clearButton.setImageResource(R.drawable.msg_close);
        clearButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        clearButton.setAlpha(0.0f);
        clearButton.setScaleX(0.0f);
        clearButton.setScaleY(0.0f);
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setVisibility(INVISIBLE);
        clearButton.setOnClickListener(v -> {
            if (colorsAnimator != null) {
                return;
            }
            ArrayList<Animator> animators = new ArrayList<>();
            if (colorsCount == 2) {
                colorsCount = 1;
                animators.add(ObjectAnimator.ofFloat(clearButton, View.ALPHA, 0.0f));
                animators.add(ObjectAnimator.ofFloat(clearButton, View.SCALE_X, 0.0f));
                animators.add(ObjectAnimator.ofFloat(clearButton, View.SCALE_Y, 0.0f));
                animators.add(ObjectAnimator.ofFloat(addButton, View.TRANSLATION_X, 0));
            } else if (colorsCount == 3) {
                colorsCount = 2;
                animators.add(ObjectAnimator.ofFloat(addButton, View.TRANSLATION_X, AndroidUtilities.dp(30) + AndroidUtilities.dp(13)));
            } else if (colorsCount == 4) {
                colorsCount = 3;
                animators.add(ObjectAnimator.ofFloat(addButton, View.TRANSLATION_X, AndroidUtilities.dp(30) * 2 + AndroidUtilities.dp(13) * 2));
            } else {
                return;
            }
            if (colorsCount < maxColorsCount) {
                addButton.setVisibility(VISIBLE);
                animators.add(ObjectAnimator.ofFloat(addButton, View.ALPHA, 1.0f));
                animators.add(ObjectAnimator.ofFloat(addButton, View.SCALE_X, 1.0f));
                animators.add(ObjectAnimator.ofFloat(addButton, View.SCALE_Y, 1.0f));
            } else {
                animators.add(ObjectAnimator.ofFloat(addButton, View.ALPHA, 0f));
                animators.add(ObjectAnimator.ofFloat(addButton, View.SCALE_X, 0f));
                animators.add(ObjectAnimator.ofFloat(addButton, View.SCALE_Y, 0f));
            }
            if (selectedColor != 3) {
                RadioButton button = radioButton[selectedColor];
                for (int a = selectedColor + 1; a < radioButton.length; a++) {
                    radioButton[a - 1] = radioButton[a];
                }
                radioButton[3] = button;
            }
            if (prevSelectedColor >= 0 && prevSelectedColor < selectedColor) {
                radioButton[prevSelectedColor].callOnClick();
            } else {
                radioButton[colorsCount - 1].callOnClick();
            }
            for (int a = 0; a < radioButton.length; a++) {
                if (a < colorsCount) {
                    delegate.setColor(radioButton[a].getColor(), a, a == radioButton.length - 1);
                } else {
                    delegate.setColor(0, a, a == radioButton.length - 1);
                }
            }
            colorsAnimator = new AnimatorSet();
            updateColorsPosition(animators, selectedColor, true, getMeasuredWidth());
            colorsAnimator.playTogether(animators);
            colorsAnimator.setDuration(180);
            colorsAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            colorsAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (colorsCount == 1) {
                        clearButton.setVisibility(INVISIBLE);
                    }
                    for (int a = 0; a < radioButton.length; a++) {
                        if (radioButton[a].getTag(R.id.index_tag) == null) {
                            radioButton[a].setVisibility(INVISIBLE);
                        }
                    }
                    colorsAnimator = null;
                }
            });
            colorsAnimator.start();
        });
        clearButton.setContentDescription(LocaleController.getString("ClearButton", R.string.ClearButton));
        addView(clearButton, LayoutHelper.createFrame(30, 30, Gravity.TOP | Gravity.LEFT, 97, 1, 0, 0));

        resetButton = new TextView(context);
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        resetButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        resetButton.setGravity(Gravity.CENTER);
        resetButton.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
        resetButton.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(resetButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.RIGHT, 0, 3, 14, 0));
        resetButton.setOnClickListener(v -> {
            /*if (resetButton.getAlpha() != 1.0f) { TODO
                return;
            }
            delegate.setColor(0, -1, true);
            resetButton.animate().alpha(0.0f).setDuration(180).start();
            resetButton.setTag(null);*/
        });

        if (hasMenu) {
            menuItem = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
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
            menuItem.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 1));
            addView(menuItem, LayoutHelper.createFrame(30, 30, Gravity.TOP | Gravity.RIGHT, 0, 2, 10, 0));
            menuItem.setOnClickListener(v -> menuItem.toggleSubMenu());
        }
        updateColorsPosition(null, 0, false, getMeasuredWidth());
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateColorsPosition(null, 0, false, getMeasuredWidth());
    }

    private void updateColorsPosition(ArrayList<Animator> animators, int hidingIndex, boolean hiding, int width) {
        int allX = 0;
        int count = colorsCount;
        int visibleX = count * AndroidUtilities.dp(30) + (count - 1) * AndroidUtilities.dp(13);
        int left = radioContainer.getLeft() + visibleX;
        int w = width - AndroidUtilities.dp(currentResetType == 1 ? 50 : 0);
        float tr;
        if (left > w) {
            tr = left - w;
        } else {
            tr = 0;
        }
        if (animators != null) {
            animators.add(ObjectAnimator.ofFloat(radioContainer, View.TRANSLATION_X, -tr));
        } else {
            radioContainer.setTranslationX(-tr);
        }
        for (int a = 0; a < radioButton.length; a++) {
            boolean wasVisible = radioButton[a].getTag(R.id.index_tag) != null;
            if (a < colorsCount) {
                radioButton[a].setVisibility(VISIBLE);
                if (animators != null) {
                    if (!wasVisible) {
                        animators.add(ObjectAnimator.ofFloat(radioButton[a], View.ALPHA, 1.0f));
                        animators.add(ObjectAnimator.ofFloat(radioButton[a], View.SCALE_X, 1.0f));
                        animators.add(ObjectAnimator.ofFloat(radioButton[a], View.SCALE_Y, 1.0f));
                    }
                    if (hiding || !hiding && a != colorsCount - 1) {
                        animators.add(ObjectAnimator.ofFloat(radioButton[a], View.TRANSLATION_X, allX));
                    } else {
                        radioButton[a].setTranslationX(allX);
                    }
                } else {
                    radioButton[a].setVisibility(VISIBLE);
                    if (colorsAnimator == null) {
                        radioButton[a].setAlpha(1.0f);
                        radioButton[a].setScaleX(1.0f);
                        radioButton[a].setScaleY(1.0f);
                    }
                    radioButton[a].setTranslationX(allX);
                }
                radioButton[a].setTag(R.id.index_tag, 1);
            } else {
                if (animators != null) {
                    if (wasVisible) {
                        animators.add(ObjectAnimator.ofFloat(radioButton[a], View.ALPHA, 0.0f));
                        animators.add(ObjectAnimator.ofFloat(radioButton[a], View.SCALE_X, 0.0f));
                        animators.add(ObjectAnimator.ofFloat(radioButton[a], View.SCALE_Y, 0.0f));
                    }
                } else {
                    radioButton[a].setVisibility(INVISIBLE);
                    if (colorsAnimator == null) {
                        radioButton[a].setAlpha(0.0f);
                        radioButton[a].setScaleX(0.0f);
                        radioButton[a].setScaleY(0.0f);
                    }
                }
                if (!hiding) {
                    radioButton[a].setTranslationX(allX);
                }
                radioButton[a].setTag(R.id.index_tag, null);
            }
            allX += AndroidUtilities.dp(30) + AndroidUtilities.dp(13);
        }
    }

    public void hideKeyboard() {
        AndroidUtilities.hideKeyboard(colorEditText[1]);
    }

    private int getIndex(int num) {
        if (num == 1) {
            return 0;
        } else if (num == 3) {
            return 1;
        } else if (num == 5) {
            return 2;
        } else {
            return 3;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int top = AndroidUtilities.dp(45);
        canvas.drawBitmap(colorWheelBitmap, 0, top, null);
        int y = top + colorWheelBitmap.getHeight();
        canvas.drawRect(0, top, getMeasuredWidth(), top + 1, linePaint);
        canvas.drawRect(0, y - 1, getMeasuredWidth(), y, linePaint);

        hsvTemp[0] = colorHSV[0];
        hsvTemp[1] = colorHSV[1];
        hsvTemp[2] = 1f;

        int colorPointX = (int) (colorHSV[0] * getMeasuredWidth() / 360);
        int colorPointY = (int) (top + (colorWheelBitmap.getHeight() * (1.0f - colorHSV[1])));
        if (!circlePressed) {
            int minD = AndroidUtilities.dp(16);
            float progress = CubicBezierInterpolator.EASE_OUT.getInterpolation(pressedMoveProgress);
            if (colorPointX < minD) {
                colorPointX += progress * (minD - colorPointX);
            } else if (colorPointX > getMeasuredWidth() - minD) {
                colorPointX -= progress * (colorPointX - (getMeasuredWidth() - minD));
            }
            if (colorPointY < top + minD) {
                colorPointY += progress * (top + minD - colorPointY);
            } else if (colorPointY > top + colorWheelBitmap.getHeight() - minD) {
                colorPointY -= progress * (colorPointY - (top+ colorWheelBitmap.getHeight() - minD));
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
                int top = AndroidUtilities.dp(45);
                if (circlePressed || !colorPressed && y >= top && y <= top + colorWheelBitmap.getHeight()) {
                    if (!circlePressed) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    circlePressed = true;
                    pressedMoveProgress = 0.0f;
                    lastUpdateTime = SystemClock.elapsedRealtime();

                    x = Math.max(0, Math.min(x, colorWheelBitmap.getWidth()));
                    y = Math.max(top, Math.min(y, top + colorWheelBitmap.getHeight()));

                    float oldBrightnessPos = minHsvBrightness == maxHsvBrightness ? 0.5f : (getBrightness() - minHsvBrightness) / (maxHsvBrightness - minHsvBrightness);
                    colorHSV[0] = x * 360f / colorWheelBitmap.getWidth();
                    colorHSV[1] = 1.0f - (1.0f / colorWheelBitmap.getHeight() * (y - top));
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
                        Editable editable = colorEditText[1].getText();
                        editable.replace(0, editable.length(), text);
                        radioButton[selectedColor].setColor(color);
                        ignoreTextChange = false;
                    }
                    delegate.setColor(color, selectedColor, false);
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
        int defaultColor = delegate.getDefaultColor(selectedColor);
        if (defaultColor == 0 || defaultColor != color) {
            updateHsvMinMaxBrightness();
        }
        colorGradient = null;
        invalidate();
    }

    public void setColor(int color, int num) {
        if (!ignoreTextChange) {
            ignoreTextChange = true;
            if (selectedColor == num) {
                String text = String.format("%02x%02x%02x", (byte) Color.red(color), (byte) Color.green(color), (byte) Color.blue(color)).toUpperCase();
                colorEditText[1].setText(text);
                colorEditText[1].setSelection(text.length());
            }
            radioButton[num].setColor(color);
            ignoreTextChange = false;
        }
        setColorInner(color);
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

    public void setType(int resetType, boolean hasChanges, int maxColorsCount, int newColorsCount, boolean myMessages, int angle, boolean animated) {
        if (resetType != currentResetType) {
            prevSelectedColor = 0;
            selectedColor = 0;
            for (int i = 0; i < 4; i++) {
                radioButton[i].setChecked(i == selectedColor, true);
            }
        }
        this.maxColorsCount = maxColorsCount;
        currentResetType = resetType;
        myMessagesColor = myMessages;
        colorsCount = newColorsCount;

        if (newColorsCount == 1) {
            addButton.setTranslationX(0);
        } else if (newColorsCount == 2) {
            addButton.setTranslationX(AndroidUtilities.dp(30) + AndroidUtilities.dp(13));
        } else if (newColorsCount == 3) {
            addButton.setTranslationX(AndroidUtilities.dp(30) * 2 + AndroidUtilities.dp(13) * 2);
        } else {
            addButton.setTranslationX(AndroidUtilities.dp(30) * 3 + AndroidUtilities.dp(13) * 3);
        }

        if (menuItem != null) {
            if (resetType == 1) {
                menuItem.setVisibility(VISIBLE);
            } else {
                menuItem.setVisibility(GONE);
                clearButton.setTranslationX(0);
            }
        }
        if (maxColorsCount <= 1) {
            addButton.setVisibility(GONE);
            clearButton.setVisibility(GONE);
        } else {
            if (newColorsCount < maxColorsCount) {
                addButton.setVisibility(VISIBLE);
                addButton.setScaleX(1.0f);
                addButton.setScaleY(1.0f);
                addButton.setAlpha(1.0f);
            } else {
                addButton.setVisibility(GONE);
            }
            if (newColorsCount > 1) {
                clearButton.setVisibility(VISIBLE);
                clearButton.setScaleX(1.0f);
                clearButton.setScaleY(1.0f);
                clearButton.setAlpha(1.0f);
            } else {
                clearButton.setVisibility(GONE);
            }
        }
        linearLayout.invalidate();
        updateColorsPosition(null, 0, false, getMeasuredWidth());

        ArrayList<Animator> animators;
        if (animated) {
            animators = new ArrayList<>();
        } else {
            animators = null;
        }

        if (animators != null && !animators.isEmpty()) {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.setDuration(180);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (maxColorsCount <= 1) {
                        clearButton.setVisibility(GONE);
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
        if (clearButton == null) {
            return;
        }
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
        if (menuItem != null) {
            ThemeDescription.ThemeDescriptionDelegate delegate = () -> {
                menuItem.setIconColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                Theme.setDrawableColor(menuItem.getBackground(), getThemedColor(Theme.key_dialogButtonSelector));
                menuItem.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), false);
                menuItem.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon), true);
                menuItem.redrawPopup(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
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

    public static int generateGradientColors(int color) {
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

    public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        linearLayout.invalidate();
    }
}
