/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Keep;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.TextColorThemeCell;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class ThemeEditorView {

    private FrameLayout windowView;
    private Activity parentActivity;

    private boolean hidden;

    private ArrayList<ThemeDescription> currentThemeDesription;
    private int currentThemeDesriptionPosition;

    private final int editorWidth = AndroidUtilities.dp(54);
    private final int editorHeight = AndroidUtilities.dp(54);

    private WindowManager.LayoutParams windowLayoutParams;
    private WindowManager windowManager;
    private DecelerateInterpolator decelerateInterpolator;
    private SharedPreferences preferences;
    private WallpaperUpdater wallpaperUpdater;
    private EditorAlert editorAlert;

    private String currentThemeName;

    @SuppressLint("StaticFieldLeak")
    private static volatile ThemeEditorView Instance = null;
    public static ThemeEditorView getInstance() {
        return Instance;
    }

    public void destroy() {
        wallpaperUpdater.cleanup();
        if (parentActivity == null || windowView == null) {
            return;
        }
        try {
            windowManager.removeViewImmediate(windowView);
            windowView = null;
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            if (editorAlert != null) {
                editorAlert.dismiss();
                editorAlert = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        parentActivity = null;
        Instance = null;
    }

    public class EditorAlert extends BottomSheet {

        private ColorPicker colorPicker;
        private RecyclerListView listView;
        private LinearLayoutManager layoutManager;
        private ListAdapter listAdapter;
        private FrameLayout bottomSaveLayout;
        private FrameLayout bottomLayout;
        private View shadow;
        private TextView cancelButton;
        private TextView defaultButtom;
        private TextView saveButton;

        private Drawable shadowDrawable;

        private int scrollOffsetY;
        private int topBeforeSwitch;
        private int previousScrollPosition;

        private boolean animationInProgress;

        private AnimatorSet colorChangeAnimation;
        private boolean startedColorChange;
        private boolean ignoreTextChange;

        private class ColorPicker extends FrameLayout {

            private LinearLayout linearLayout;

            private final int paramValueSliderWidth = AndroidUtilities.dp(20);

            private Paint colorWheelPaint;
            private Paint valueSliderPaint;
            private Paint circlePaint;
            private Drawable circleDrawable;

            private Bitmap colorWheelBitmap;

            private EditTextBoldCursor colorEditText[] = new EditTextBoldCursor[4];

            private int colorWheelRadius;

            private float[] colorHSV = new float[] { 0.0f, 0.0f, 1.0f };
            private float alpha = 1.0f;

            private float[] hsvTemp = new float[3];
            private LinearGradient colorGradient;
            private LinearGradient alphaGradient;

            private boolean circlePressed;
            private boolean colorPressed;
            private boolean alphaPressed;

            private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

            public ColorPicker(Context context) {
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
                addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
                for (int a = 0; a < 4; a++){
                    colorEditText[a] = new EditTextBoldCursor(context);
                    colorEditText[a].setInputType(InputType.TYPE_CLASS_NUMBER);
                    colorEditText[a].setTextColor(0xff212121);
                    colorEditText[a].setCursorColor(0xff212121);
                    colorEditText[a].setCursorSize(AndroidUtilities.dp(20));
                    colorEditText[a].setCursorWidth(1.5f);
                    colorEditText[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                    colorEditText[a].setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
                    colorEditText[a].setMaxLines(1);
                    colorEditText[a].setTag(a);
                    colorEditText[a].setGravity(Gravity.CENTER);
                    if (a == 0) {
                        colorEditText[a].setHint("red");
                    } else if (a == 1) {
                        colorEditText[a].setHint("green");
                    } else if (a == 2) {
                        colorEditText[a].setHint("blue");
                    } else if (a == 3) {
                        colorEditText[a].setHint("alpha");
                    }
                    colorEditText[a].setImeOptions((a == 3 ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT) | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                    InputFilter[] inputFilters = new InputFilter[1];
                    inputFilters[0] = new InputFilter.LengthFilter(3);
                    colorEditText[a].setFilters(inputFilters);
                    final int num = a;
                    linearLayout.addView(colorEditText[a], LayoutHelper.createLinear(55, 36, 0, 0, a != 3 ? 16 : 0, 0));
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
                            int color = Utilities.parseInt(editable.toString());
                            if (color < 0) {
                                color = 0;
                                colorEditText[num].setText("" + color);
                                colorEditText[num].setSelection(colorEditText[num].length());
                            } else if (color > 255) {
                                color = 255;
                                colorEditText[num].setText("" + color);
                                colorEditText[num].setSelection(colorEditText[num].length());
                            }
                            int currentColor = getColor();
                            if (num == 2) {
                                currentColor = (currentColor & 0xffffff00) | (color & 0xff);
                            } else if (num == 1) {
                                currentColor = (currentColor & 0xffff00ff) | ((color & 0xff) << 8);
                            } else if (num == 0) {
                                currentColor = (currentColor & 0xff00ffff) | ((color & 0xff) << 16);
                            } else if (num == 3) {
                                currentColor = (currentColor & 0x00ffffff) | ((color & 0xff) << 24);
                            }
                            setColor(currentColor);
                            for (int a = 0; a < currentThemeDesription.size(); a++) {
                                currentThemeDesription.get(a).setColor(getColor(), false);
                            }

                            ignoreTextChange = false;
                        }
                    });
                    colorEditText[a].setOnEditorActionListener(new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                            if (i == EditorInfo.IME_ACTION_DONE) {
                                AndroidUtilities.hideKeyboard(textView);
                                return true;
                            }
                            return false;
                        }
                    });
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);
                int size = Math.min(widthSize, heightSize);
                measureChild(linearLayout, widthMeasureSpec, heightMeasureSpec);
                setMeasuredDimension(size, size);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int centerX = getWidth() / 2 - paramValueSliderWidth * 2;
                int centerY = getHeight() / 2 - AndroidUtilities.dp(8);

                canvas.drawBitmap(colorWheelBitmap, centerX - colorWheelRadius, centerY - colorWheelRadius, null);

                float hueAngle = (float) Math.toRadians(colorHSV[0]);
                int colorPointX = (int) (-Math.cos(hueAngle) * colorHSV[1] * colorWheelRadius) + centerX;
                int colorPointY = (int) (-Math.sin(hueAngle) * colorHSV[1] * colorWheelRadius) + centerY;

                float pointerRadius = 0.075f * colorWheelRadius;

                hsvTemp[0] = colorHSV[0];
                hsvTemp[1] = colorHSV[1];
                hsvTemp[2] = 1.0f;

                drawPointerArrow(canvas, colorPointX, colorPointY, Color.HSVToColor(hsvTemp));

                int x = centerX + colorWheelRadius + paramValueSliderWidth;
                int y = centerY - colorWheelRadius;
                int width = AndroidUtilities.dp(9);
                int height = colorWheelRadius * 2;
                if (colorGradient == null) {
                    colorGradient = new LinearGradient(x, y, x + width, y + height, new int[]{Color.BLACK, Color.HSVToColor(hsvTemp)}, null, Shader.TileMode.CLAMP);
                }
                valueSliderPaint.setShader(colorGradient);
                canvas.drawRect(x, y, x + width, y + height, valueSliderPaint);
                drawPointerArrow(canvas, x + width / 2, (int) (y + colorHSV[2] * height), Color.HSVToColor(colorHSV));

                x += paramValueSliderWidth * 2;
                if (alphaGradient == null) {
                    int color = Color.HSVToColor(hsvTemp);
                    alphaGradient = new LinearGradient(x, y, x + width, y + height, new int[]{color, color & 0x00ffffff}, null, Shader.TileMode.CLAMP);
                }
                valueSliderPaint.setShader(alphaGradient);
                canvas.drawRect(x, y, x + width, y + height, valueSliderPaint);
                drawPointerArrow(canvas, x + width / 2, (int) (y + (1.0f - alpha) * height), (Color.HSVToColor(colorHSV) & 0x00ffffff) | ((int) (255 * alpha) << 24));
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
                colorWheelRadius = Math.max(1, width / 2 - paramValueSliderWidth * 2 - AndroidUtilities.dp(20));
                colorWheelBitmap = createColorWheelBitmap(colorWheelRadius * 2, colorWheelRadius * 2);
                //linearLayout.setTranslationY(colorWheelRadius * 2 + AndroidUtilities.dp(20));
                colorGradient = null;
                alphaGradient = null;
            }

            private Bitmap createColorWheelBitmap(int width, int height) {
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                int colorCount = 12;
                int colorAngleStep = 360 / 12;
                int colors[] = new int[colorCount + 1];
                float hsv[] = new float[] { 0.0f, 1.0f, 1.0f };
                for (int i = 0; i < colors.length; i++) {
                    hsv[0] = (i * colorAngleStep + 180) % 360;
                    colors[i] = Color.HSVToColor(hsv);
                }
                colors[colorCount] = colors[0];

                SweepGradient sweepGradient = new SweepGradient(width / 2, height / 2, colors, null);
                RadialGradient radialGradient = new RadialGradient(width / 2, height / 2, colorWheelRadius, 0xffffffff, 0x00ffffff, Shader.TileMode.CLAMP);
                ComposeShader composeShader = new ComposeShader(sweepGradient, radialGradient, PorterDuff.Mode.SRC_OVER);

                colorWheelPaint.setShader(composeShader);

                Canvas canvas = new Canvas(bitmap);
                canvas.drawCircle(width / 2, height / 2, colorWheelRadius, colorWheelPaint);

                return bitmap;
            }

            private void startColorChange(boolean start) {
                if (startedColorChange == start) {
                    return;
                }
                if (colorChangeAnimation != null) {
                    colorChangeAnimation.cancel();
                }
                startedColorChange = start;
                colorChangeAnimation = new AnimatorSet();
                colorChangeAnimation.playTogether(
                        ObjectAnimator.ofInt(backDrawable, "alpha", start ? 0 : 51),
                        ObjectAnimator.ofFloat(containerView, "alpha", start ? 0.2f : 1.0f));
                colorChangeAnimation.setDuration(150);
                colorChangeAnimation.setInterpolator(decelerateInterpolator);
                colorChangeAnimation.start();
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:

                        int x = (int) event.getX();
                        int y = (int) event.getY();
                        int centerX = getWidth() / 2 - paramValueSliderWidth * 2;
                        int centerY = getHeight() / 2 - AndroidUtilities.dp(8);
                        int cx = x - centerX;
                        int cy = y - centerY;
                        double d = Math.sqrt(cx * cx + cy * cy);

                        if (circlePressed || !alphaPressed && !colorPressed && d <= colorWheelRadius) {
                            if (d > colorWheelRadius) {
                                d = colorWheelRadius;
                            }
                            circlePressed = true;
                            colorHSV[0] = (float) (Math.toDegrees(Math.atan2(cy, cx)) + 180.0f);
                            colorHSV[1] = Math.max(0.0f, Math.min(1.0f, (float) (d / colorWheelRadius)));
                            colorGradient = null;
                            alphaGradient = null;
                        }
                        if (colorPressed || !circlePressed && !alphaPressed && x >= centerX + colorWheelRadius + paramValueSliderWidth && x <= centerX + colorWheelRadius + paramValueSliderWidth * 2 && y >= centerY - colorWheelRadius && y <= centerY + colorWheelRadius) {
                            float value = (y - (centerY - colorWheelRadius)) / (colorWheelRadius * 2.0f);
                            if (value < 0.0f) {
                                value = 0.0f;
                            } else if (value > 1.0f) {
                                value = 1.0f;
                            }
                            colorHSV[2] = value;
                            colorPressed = true;
                        }
                        if (alphaPressed || !circlePressed && !colorPressed && x >= centerX + colorWheelRadius + paramValueSliderWidth * 3 && x <= centerX + colorWheelRadius + paramValueSliderWidth * 4 && y >= centerY - colorWheelRadius && y <= centerY + colorWheelRadius) {
                            alpha = 1.0f - (y - (centerY - colorWheelRadius)) / (colorWheelRadius * 2.0f);
                            if (alpha < 0.0f) {
                                alpha = 0.0f;
                            } else if (alpha > 1.0f) {
                                alpha = 1.0f;
                            }
                            alphaPressed = true;
                        }
                        if (alphaPressed || colorPressed || circlePressed) {
                            startColorChange(true);
                            int color = getColor();
                            for (int a = 0; a < currentThemeDesription.size(); a++) {
                                currentThemeDesription.get(a).setColor(color, false);
                            }
                            int red = Color.red(color);
                            int green = Color.green(color);
                            int blue = Color.blue(color);
                            int a = Color.alpha(color);
                            if (!ignoreTextChange) {
                                ignoreTextChange = true;
                                colorEditText[0].setText("" + red);
                                colorEditText[1].setText("" + green);
                                colorEditText[2].setText("" + blue);
                                colorEditText[3].setText("" + a);
                                for (int b = 0; b < 4; b++) {
                                    colorEditText[b].setSelection(colorEditText[b].length());
                                }
                                ignoreTextChange = false;
                            }
                            invalidate();
                        }

                        return true;
                    case MotionEvent.ACTION_UP:
                        alphaPressed = false;
                        colorPressed = false;
                        circlePressed = false;
                        startColorChange(false);
                        break;
                }
                return super.onTouchEvent(event);
            }

            public void setColor(int color) {
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                int a = Color.alpha(color);
                if (!ignoreTextChange) {
                    ignoreTextChange = true;
                    colorEditText[0].setText("" + red);
                    colorEditText[1].setText("" + green);
                    colorEditText[2].setText("" + blue);
                    colorEditText[3].setText("" + a);
                    for (int b = 0; b < 4; b++) {
                        colorEditText[b].setSelection(colorEditText[b].length());
                    }
                    ignoreTextChange = false;
                }
                alphaGradient = null;
                colorGradient = null;
                alpha = a / 255.0f;
                Color.colorToHSV(color, colorHSV);
                invalidate();
            }

            public int getColor() {
                return (Color.HSVToColor(colorHSV) & 0x00ffffff) | ((int) (alpha * 255) << 24);
            }
        }

        public EditorAlert(final Context context, ThemeDescription[] items) {
            super(context, true);

            shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow).mutate();

            containerView = new FrameLayout(context) {

                private boolean ignoreLayout = false;

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
                        dismiss();
                        return true;
                    }
                    return super.onInterceptTouchEvent(ev);
                }

                @Override
                public boolean onTouchEvent(MotionEvent e) {
                    return !isDismissed() && super.onTouchEvent(e);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int width = MeasureSpec.getSize(widthMeasureSpec);
                    int height = MeasureSpec.getSize(heightMeasureSpec);
                    if (Build.VERSION.SDK_INT >= 21) {
                        height -= AndroidUtilities.statusBarHeight;
                    }

                    int pickerSize = Math.min(width, height);

                    int padding = height - pickerSize;
                    if (listView.getPaddingTop() != padding) {
                        ignoreLayout = true;
                        int previousPadding = listView.getPaddingTop();
                        listView.setPadding(0, padding, 0, AndroidUtilities.dp(48));
                        if (colorPicker.getVisibility() == VISIBLE) {
                            //previousScrollPosition += previousPadding;
                            scrollOffsetY = listView.getPaddingTop();
                            listView.setTopGlowOffset(scrollOffsetY);
                            colorPicker.setTranslationY(scrollOffsetY);
                            previousScrollPosition = 0;
                        }
                        ignoreLayout = false;
                    }
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    updateLayout();
                }

                @Override
                public void requestLayout() {
                    if (ignoreLayout) {
                        return;
                    }
                    super.requestLayout();
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    shadowDrawable.setBounds(0, scrollOffsetY - backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight());
                    shadowDrawable.draw(canvas);
                }
            };
            containerView.setWillNotDraw(false);
            containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

            listView = new RecyclerListView(context);
            listView.setPadding(0, 0, 0, AndroidUtilities.dp(48));
            listView.setClipToPadding(false);
            listView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext()));
            listView.setHorizontalScrollBarEnabled(false);
            listView.setVerticalScrollBarEnabled(false);
            containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            listView.setAdapter(listAdapter = new ListAdapter(context, items));
            listView.setGlowColor(0xfff5f6f7);
            listView.setItemAnimator(null);
            listView.setLayoutAnimation(null);
            listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
                @Override
                public void onItemClick(View view, int position) {
                    currentThemeDesription = listAdapter.getItem(position);
                    currentThemeDesriptionPosition = position;
                    for (int a = 0; a < currentThemeDesription.size(); a++) {
                        ThemeDescription description = currentThemeDesription.get(a);
                        if (description.getCurrentKey().equals(Theme.key_chat_wallpaper)) {
                            wallpaperUpdater.showAlert(true);
                            return;
                        }
                        description.startEditing();
                        if (a == 0) {
                            colorPicker.setColor(description.getCurrentColor());
                        }
                    }
                    setColorPickerVisible(true);
                }
            });
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    updateLayout();
                }
            });

            colorPicker = new ColorPicker(context);
            colorPicker.setVisibility(View.GONE);
            containerView.addView(colorPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

            shadow = new View(context);
            shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
            containerView.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));

            bottomSaveLayout = new FrameLayout(context);
            bottomSaveLayout.setBackgroundColor(0xffffffff);
            containerView.addView(bottomSaveLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));

            TextView closeButton = new TextView(context);
            closeButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            closeButton.setTextColor(0xff19a7e8);
            closeButton.setGravity(Gravity.CENTER);
            closeButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
            closeButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            closeButton.setText(LocaleController.getString("CloseEditor", R.string.CloseEditor).toUpperCase());
            closeButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            bottomSaveLayout.addView(closeButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            closeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            });

            TextView saveButton = new TextView(context);
            saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            saveButton.setTextColor(0xff19a7e8);
            saveButton.setGravity(Gravity.CENTER);
            saveButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
            saveButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            saveButton.setText(LocaleController.getString("SaveTheme", R.string.SaveTheme).toUpperCase());
            saveButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            bottomSaveLayout.addView(saveButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Theme.saveCurrentTheme(currentThemeName, true);
                    setOnDismissListener(null);
                    dismiss();
                    close();
                }
            });

            bottomLayout = new FrameLayout(context);
            bottomLayout.setVisibility(View.GONE);
            bottomLayout.setBackgroundColor(0xffffffff);
            containerView.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));

            cancelButton = new TextView(context);
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelButton.setTextColor(0xff19a7e8);
            cancelButton.setGravity(Gravity.CENTER);
            cancelButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
            cancelButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
            cancelButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            bottomLayout.addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int a = 0; a < currentThemeDesription.size(); a++) {
                        currentThemeDesription.get(a).setPreviousColor();
                    }
                    setColorPickerVisible(false);
                }
            });

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            bottomLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

            defaultButtom = new TextView(context);
            defaultButtom.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            defaultButtom.setTextColor(0xff19a7e8);
            defaultButtom.setGravity(Gravity.CENTER);
            defaultButtom.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
            defaultButtom.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            defaultButtom.setText(LocaleController.getString("Default", R.string.Default).toUpperCase());
            defaultButtom.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            linearLayout.addView(defaultButtom, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            defaultButtom.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int a = 0; a < currentThemeDesription.size(); a++) {
                        currentThemeDesription.get(a).setDefaultColor();
                    }
                    setColorPickerVisible(false);
                }
            });

            saveButton = new TextView(context);
            saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            saveButton.setTextColor(0xff19a7e8);
            saveButton.setGravity(Gravity.CENTER);
            saveButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
            saveButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            saveButton.setText(LocaleController.getString("Save", R.string.Save).toUpperCase());
            saveButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            linearLayout.addView(saveButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setColorPickerVisible(false);
                }
            });
        }

        private void setColorPickerVisible(boolean visible) {
            if (visible) {
                animationInProgress = true;
                colorPicker.setVisibility(View.VISIBLE);
                bottomLayout.setVisibility(View.VISIBLE);
                colorPicker.setAlpha(0.0f);
                bottomLayout.setAlpha(0.0f);

                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(colorPicker, "alpha", 1.0f),
                        ObjectAnimator.ofFloat(bottomLayout, "alpha", 1.0f),
                        ObjectAnimator.ofFloat(listView, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(bottomSaveLayout, "alpha", 0.0f),
                        ObjectAnimator.ofInt(this, "scrollOffsetY", listView.getPaddingTop()));
                animatorSet.setDuration(150);
                animatorSet.setInterpolator(decelerateInterpolator);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        listView.setVisibility(View.INVISIBLE);
                        bottomSaveLayout.setVisibility(View.INVISIBLE);
                        animationInProgress = false;
                    }
                });
                animatorSet.start();
                previousScrollPosition = scrollOffsetY;
            } else {
                if (parentActivity != null) {
                    ((LaunchActivity) parentActivity).rebuildAllFragments(false);
                }
                Theme.saveCurrentTheme(currentThemeName, false);
                AndroidUtilities.hideKeyboard(getCurrentFocus());
                animationInProgress = true;
                listView.setVisibility(View.VISIBLE);
                bottomSaveLayout.setVisibility(View.VISIBLE);
                listView.setAlpha(0.0f);
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(colorPicker, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(bottomLayout, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(listView, "alpha", 1.0f),
                        ObjectAnimator.ofFloat(bottomSaveLayout, "alpha", 1.0f),
                        ObjectAnimator.ofInt(this, "scrollOffsetY", previousScrollPosition));
                animatorSet.setDuration(150);
                animatorSet.setInterpolator(decelerateInterpolator);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        colorPicker.setVisibility(View.GONE);
                        bottomLayout.setVisibility(View.GONE);
                        animationInProgress = false;
                    }
                });
                animatorSet.start();
                listAdapter.notifyItemChanged(currentThemeDesriptionPosition);
            }
        }

        private int getCurrentTop() {
            if (listView.getChildCount() != 0) {
                View child = listView.getChildAt(0);
                RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
                if (holder != null) {
                    return listView.getPaddingTop() - (holder.getAdapterPosition() == 0 && child.getTop() >= 0 ? child.getTop() : 0);
                }
            }
            return -1000;
        }

        @Override
        protected boolean canDismissWithSwipe() {
            return false;
        }

        @SuppressLint("NewApi")
        private void updateLayout() {
            if (listView.getChildCount() <= 0 || listView.getVisibility() != View.VISIBLE || animationInProgress) {
                return;
            }
            View child = listView.getChildAt(0);
            RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
            int top;
            if (listView.getVisibility() != View.VISIBLE || animationInProgress) {
                top = listView.getPaddingTop();
            } else {
                top = child.getTop() - AndroidUtilities.dp(8);
            }
            int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
            if (scrollOffsetY != newOffset) {
                setScrollOffsetY(newOffset);
            }
        }

        public int getScrollOffsetY() {
            return scrollOffsetY;
        }

        @Keep
        public void setScrollOffsetY(int value) {
            listView.setTopGlowOffset(scrollOffsetY = value);
            colorPicker.setTranslationY(scrollOffsetY);
            containerView.invalidate();
        }

        private class ListAdapter extends RecyclerListView.SelectionAdapter {

            private Context context;
            private int currentCount;
            private ArrayList<ArrayList<ThemeDescription>> items = new ArrayList<>();
            private HashMap<String, ArrayList<ThemeDescription>> itemsMap = new HashMap<>();

            public ListAdapter(Context context, ThemeDescription[] descriptions) {
                this.context = context;
                for (int a = 0; a < descriptions.length; a++) {
                    ThemeDescription description = descriptions[a];
                    String key = description.getCurrentKey();
                    ArrayList<ThemeDescription> arrayList = itemsMap.get(key);
                    if (arrayList == null) {
                        arrayList = new ArrayList<>();
                        itemsMap.put(key, arrayList);
                        items.add(arrayList);
                    }
                    arrayList.add(description);
                }
            }

            @Override
            public int getItemCount() {
                return items.size();
            }

            public ArrayList<ThemeDescription> getItem(int i) {
                if (i < 0 || i >= items.size()) {
                    return null;
                }
                return items.get(i);
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View view = new TextColorThemeCell(context);
                view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ArrayList<ThemeDescription> arrayList = items.get(position);
                ThemeDescription description = arrayList.get(0);
                int color;
                if (description.getCurrentKey().equals(Theme.key_chat_wallpaper)) {
                    color = 0;
                } else {
                    color = description.getSetColor();
                }
                ((TextColorThemeCell) holder.itemView).setTextAndColor(description.getTitle(), color);
            }

            @Override
            public int getItemViewType(int i) {
                return 0;
            }
        }
    }

    public void show(Activity activity, final String themeName) {
        if (Instance != null) {
            Instance.destroy();
        }
        hidden = false;
        currentThemeName = themeName;
        windowView = new FrameLayout(activity) {

            private float startX;
            private float startY;
            private boolean dragging;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                float x = event.getRawX();
                float y = event.getRawY();
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = x;
                    startY = y;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE && !dragging) {
                    if (Math.abs(startX - x) >= AndroidUtilities.getPixelsInCM(0.3f, true) || Math.abs(startY - y) >= AndroidUtilities.getPixelsInCM(0.3f, false)) {
                        dragging = true;
                        startX = x;
                        startY = y;
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (!dragging) {
                        if (editorAlert == null) {
                            LaunchActivity launchActivity = (LaunchActivity) parentActivity;

                            ActionBarLayout actionBarLayout = null;

                            if (AndroidUtilities.isTablet()) {
                                actionBarLayout = launchActivity.getLayersActionBarLayout();
                                if (actionBarLayout != null && actionBarLayout.fragmentsStack.isEmpty()) {
                                    actionBarLayout = null;
                                }
                                if (actionBarLayout == null) {
                                    actionBarLayout = launchActivity.getRightActionBarLayout();
                                    if (actionBarLayout != null && actionBarLayout.fragmentsStack.isEmpty()) {
                                        actionBarLayout = null;
                                    }
                                }
                            }
                            if (actionBarLayout == null) {
                                actionBarLayout = launchActivity.getActionBarLayout();
                            }
                            if (actionBarLayout != null) {
                                BaseFragment fragment;
                                if (!actionBarLayout.fragmentsStack.isEmpty()) {
                                    fragment = actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1);
                                } else {
                                    fragment = null;
                                }
                                if (fragment != null) {
                                    ThemeDescription[] items = fragment.getThemeDescriptions();
                                    if (items != null) {
                                        editorAlert = new EditorAlert(parentActivity, items);
                                        editorAlert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                            @Override
                                            public void onDismiss(DialogInterface dialog) {

                                            }
                                        });
                                        editorAlert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                            @Override
                                            public void onDismiss(DialogInterface dialog) {
                                                editorAlert = null;
                                                show();
                                            }
                                        });
                                        editorAlert.show();
                                        hide();
                                    }
                                }
                            }
                        }
                    }
                }
                if (dragging) {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        float dx = (x - startX);
                        float dy = (y - startY);
                        windowLayoutParams.x += dx;
                        windowLayoutParams.y += dy;
                        int maxDiff = editorWidth / 2;
                        if (windowLayoutParams.x < -maxDiff) {
                            windowLayoutParams.x = -maxDiff;
                        } else if (windowLayoutParams.x > AndroidUtilities.displaySize.x - windowLayoutParams.width + maxDiff) {
                            windowLayoutParams.x = AndroidUtilities.displaySize.x - windowLayoutParams.width + maxDiff;
                        }
                        float alpha = 1.0f;
                        if (windowLayoutParams.x < 0) {
                            alpha = 1.0f + windowLayoutParams.x / (float) maxDiff * 0.5f;
                        } else if (windowLayoutParams.x > AndroidUtilities.displaySize.x - windowLayoutParams.width) {
                            alpha = 1.0f - (windowLayoutParams.x - AndroidUtilities.displaySize.x + windowLayoutParams.width) / (float) maxDiff * 0.5f;
                        }
                        if (windowView.getAlpha() != alpha) {
                            windowView.setAlpha(alpha);
                        }
                        maxDiff = 0;
                        if (windowLayoutParams.y < -maxDiff) {
                            windowLayoutParams.y = -maxDiff;
                        } else if (windowLayoutParams.y > AndroidUtilities.displaySize.y - windowLayoutParams.height + maxDiff) {
                            windowLayoutParams.y = AndroidUtilities.displaySize.y - windowLayoutParams.height + maxDiff;
                        }
                        windowManager.updateViewLayout(windowView, windowLayoutParams);
                        startX = x;
                        startY = y;
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        dragging = false;
                        animateToBoundsMaybe();
                    }
                }
                return true;
            }
        };
        windowView.setBackgroundResource(R.drawable.theme_picker);
        windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

        preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Context.MODE_PRIVATE);

        int sidex = preferences.getInt("sidex", 1);
        int sidey = preferences.getInt("sidey", 0);
        float px = preferences.getFloat("px", 0);
        float py = preferences.getFloat("py", 0);

        try {
            windowLayoutParams = new WindowManager.LayoutParams();
            windowLayoutParams.width = editorWidth;
            windowLayoutParams.height = editorHeight;
            windowLayoutParams.x = getSideCoord(true, sidex, px, editorWidth);
            windowLayoutParams.y = getSideCoord(false, sidey, py, editorHeight);
            windowLayoutParams.format = PixelFormat.TRANSLUCENT;
            windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            windowManager.addView(windowView, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        wallpaperUpdater = new WallpaperUpdater(activity, new WallpaperUpdater.WallpaperUpdaterDelegate() {
            @Override
            public void didSelectWallpaper(File file, Bitmap bitmap) {
                Theme.setThemeWallpaper(themeName, bitmap, file);
            }

            @Override
            public void needOpenColorPicker() {
                for (int a = 0; a < currentThemeDesription.size(); a++) {
                    ThemeDescription description = currentThemeDesription.get(a);
                    description.startEditing();
                    if (a == 0) {
                        editorAlert.colorPicker.setColor(description.getCurrentColor());
                    }
                }
                editorAlert.setColorPickerVisible(true);
            }
        });
        Instance = this;
        parentActivity = activity;
        showWithAnimation();
    }

    private void showWithAnimation() {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(windowView, "alpha", 0.0f, 1.0f),
                ObjectAnimator.ofFloat(windowView, "scaleX", 0.0f, 1.0f),
                ObjectAnimator.ofFloat(windowView, "scaleY", 0.0f, 1.0f));
        animatorSet.setInterpolator(decelerateInterpolator);
        animatorSet.setDuration(150);
        animatorSet.start();
    }

    private static int getSideCoord(boolean isX, int side, float p, int sideSize) {
        int total;
        if (isX) {
            total = AndroidUtilities.displaySize.x - sideSize;
        } else {
            total = AndroidUtilities.displaySize.y - sideSize - ActionBar.getCurrentActionBarHeight();
        }
        int result;
        if (side == 0) {
            result = AndroidUtilities.dp(10);
        } else if (side == 1) {
            result = total - AndroidUtilities.dp(10);
        } else {
            result = Math.round((total - AndroidUtilities.dp(20)) * p) + AndroidUtilities.dp(10);
        }
        if (!isX) {
            result += ActionBar.getCurrentActionBarHeight();
        }
        return result;
    }

    private void hide() {
        if (parentActivity == null) {
            return;
        }
        try {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(ObjectAnimator.ofFloat(windowView, "alpha", 1.0f, 0.0f),
                    ObjectAnimator.ofFloat(windowView, "scaleX", 1.0f, 0.0f),
                    ObjectAnimator.ofFloat(windowView, "scaleY", 1.0f, 0.0f));
            animatorSet.setInterpolator(decelerateInterpolator);
            animatorSet.setDuration(150);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (windowView != null) {
                        windowManager.removeView(windowView);
                    }
                }
            });
            animatorSet.start();
            hidden = true;
        } catch (Exception e) {
            //don't promt
        }
    }

    private void show() {
        if (parentActivity == null) {
            return;
        }
        try {
            windowManager.addView(windowView, windowLayoutParams);
            hidden = false;
            showWithAnimation();
        } catch (Exception e) {
            //don't promt
        }
    }

    public void close() {
        try {
            windowManager.removeView(windowView);
        } catch (Exception e) {
            //don't promt
        }
        parentActivity = null;
    }

    public void onConfigurationChanged() {
        int sidex = preferences.getInt("sidex", 1);
        int sidey = preferences.getInt("sidey", 0);
        float px = preferences.getFloat("px", 0);
        float py = preferences.getFloat("py", 0);
        windowLayoutParams.x = getSideCoord(true, sidex, px, editorWidth);
        windowLayoutParams.y = getSideCoord(false, sidey, py, editorHeight);
        try {
            if (windowView.getParent() != null) {
                windowManager.updateViewLayout(windowView, windowLayoutParams);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (wallpaperUpdater != null) {
            wallpaperUpdater.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void animateToBoundsMaybe() {
        int startX = getSideCoord(true, 0, 0, editorWidth);
        int endX = getSideCoord(true, 1, 0, editorWidth);
        int startY = getSideCoord(false, 0, 0, editorHeight);
        int endY = getSideCoord(false, 1, 0, editorHeight);
        ArrayList<Animator> animators = null;
        SharedPreferences.Editor editor = preferences.edit();
        int maxDiff = AndroidUtilities.dp(20);
        boolean slideOut = false;
        if (Math.abs(startX - windowLayoutParams.x) <= maxDiff || windowLayoutParams.x < 0 && windowLayoutParams.x > -editorWidth / 4) {
            if (animators == null) {
                animators = new ArrayList<>();
            }
            editor.putInt("sidex", 0);
            if (windowView.getAlpha() != 1.0f) {
                animators.add(ObjectAnimator.ofFloat(windowView, "alpha", 1.0f));
            }
            animators.add(ObjectAnimator.ofInt(this, "x", startX));
        } else if (Math.abs(endX - windowLayoutParams.x) <= maxDiff || windowLayoutParams.x > AndroidUtilities.displaySize.x - editorWidth && windowLayoutParams.x < AndroidUtilities.displaySize.x - editorWidth / 4 * 3) {
            if (animators == null) {
                animators = new ArrayList<>();
            }
            editor.putInt("sidex", 1);
            if (windowView.getAlpha() != 1.0f) {
                animators.add(ObjectAnimator.ofFloat(windowView, "alpha", 1.0f));
            }
            animators.add(ObjectAnimator.ofInt(this, "x", endX));
        } else if (windowView.getAlpha() != 1.0f) {
            if (animators == null) {
                animators = new ArrayList<>();
            }
            if (windowLayoutParams.x < 0) {
                animators.add(ObjectAnimator.ofInt(this, "x", -editorWidth));
            } else {
                animators.add(ObjectAnimator.ofInt(this, "x", AndroidUtilities.displaySize.x));
            }
            slideOut = true;
        } else {
            editor.putFloat("px", (windowLayoutParams.x - startX) / (float) (endX - startX));
            editor.putInt("sidex", 2);
        }
        if (!slideOut) {
            if (Math.abs(startY - windowLayoutParams.y) <= maxDiff || windowLayoutParams.y <= ActionBar.getCurrentActionBarHeight()) {
                if (animators == null) {
                    animators = new ArrayList<>();
                }
                editor.putInt("sidey", 0);
                animators.add(ObjectAnimator.ofInt(this, "y", startY));
            } else if (Math.abs(endY - windowLayoutParams.y) <= maxDiff) {
                if (animators == null) {
                    animators = new ArrayList<>();
                }
                editor.putInt("sidey", 1);
                animators.add(ObjectAnimator.ofInt(this, "y", endY));
            } else {
                editor.putFloat("py", (windowLayoutParams.y - startY) / (float) (endY - startY));
                editor.putInt("sidey", 2);
            }
            editor.commit();
        }
        if (animators != null) {
            if (decelerateInterpolator == null) {
                decelerateInterpolator = new DecelerateInterpolator();
            }
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.setInterpolator(decelerateInterpolator);
            animatorSet.setDuration(150);
            if (slideOut) {
                animators.add(ObjectAnimator.ofFloat(windowView, "alpha", 0.0f));
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        Theme.saveCurrentTheme(currentThemeName, true);
                        destroy();
                    }
                });
            }
            animatorSet.playTogether(animators);
            animatorSet.start();
        }
    }

    public int getX() {
        return windowLayoutParams.x;
    }

    public int getY() {
        return windowLayoutParams.y;
    }

    @Keep
    public void setX(int value) {
        windowLayoutParams.x = value;
        windowManager.updateViewLayout(windowView, windowLayoutParams);
    }

    @Keep
    public void setY(int value) {
        windowLayoutParams.y = value;
        windowManager.updateViewLayout(windowView, windowLayoutParams);
    }
}
