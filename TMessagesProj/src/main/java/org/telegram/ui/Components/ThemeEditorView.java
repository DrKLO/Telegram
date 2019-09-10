/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
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
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.annotation.Keep;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
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

    private Theme.ThemeInfo themeInfo;

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
        private FrameLayout frameLayout;
        private EmptyTextProgressView searchEmptyView;
        private SearchField searchField;
        private LinearLayoutManager layoutManager;
        private ListAdapter listAdapter;
        private SearchAdapter searchAdapter;
        private FrameLayout bottomSaveLayout;
        private FrameLayout bottomLayout;
        private View[] shadow = new View[2];
        private AnimatorSet[] shadowAnimation = new AnimatorSet[2];
        private TextView saveButton;

        private Drawable shadowDrawable;

        private int scrollOffsetY;
        private int topBeforeSwitch;
        private int previousScrollPosition;

        private boolean animationInProgress;

        private AnimatorSet colorChangeAnimation;
        private boolean startedColorChange;
        private boolean ignoreTextChange;

        private class SearchField extends FrameLayout {

            private ImageView clearSearchImageView;
            private EditTextBoldCursor searchEditText;
            private View backgroundView;

            public SearchField(Context context) {
                super(context);

                View searchBackground = new View(context);
                searchBackground.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), 0xfff2f4f5));
                addView(searchBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 14, 11, 14, 0));

                ImageView searchIconImageView = new ImageView(context);
                searchIconImageView.setScaleType(ImageView.ScaleType.CENTER);
                searchIconImageView.setImageResource(R.drawable.smiles_inputsearch);
                searchIconImageView.setColorFilter(new PorterDuffColorFilter(0xffa1a8af, PorterDuff.Mode.MULTIPLY));
                addView(searchIconImageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 16, 11, 0, 0));

                clearSearchImageView = new ImageView(context);
                clearSearchImageView.setScaleType(ImageView.ScaleType.CENTER);
                CloseProgressDrawable2 progressDrawable;
                clearSearchImageView.setImageDrawable(progressDrawable = new CloseProgressDrawable2());
                progressDrawable.setSide(AndroidUtilities.dp(7));
                clearSearchImageView.setScaleX(0.1f);
                clearSearchImageView.setScaleY(0.1f);
                clearSearchImageView.setAlpha(0.0f);
                clearSearchImageView.setColorFilter(new PorterDuffColorFilter(0xffa1a8af, PorterDuff.Mode.MULTIPLY));
                addView(clearSearchImageView, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 14, 11, 14, 0));
                clearSearchImageView.setOnClickListener(v -> {
                    searchEditText.setText("");
                    AndroidUtilities.showKeyboard(searchEditText);
                });

                searchEditText = new EditTextBoldCursor(context) {
                    @Override
                    public boolean dispatchTouchEvent(MotionEvent event) {
                        MotionEvent e = MotionEvent.obtain(event);
                        e.setLocation(e.getRawX(), e.getRawY() - containerView.getTranslationY());
                        listView.dispatchTouchEvent(e);
                        e.recycle();
                        return super.dispatchTouchEvent(event);
                    }
                };
                searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                searchEditText.setHintTextColor(0xff98a0a7);
                searchEditText.setTextColor(0xff222222);
                searchEditText.setBackgroundDrawable(null);
                searchEditText.setPadding(0, 0, 0, 0);
                searchEditText.setMaxLines(1);
                searchEditText.setLines(1);
                searchEditText.setSingleLine(true);
                searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                searchEditText.setHint(LocaleController.getString("Search", R.string.Search));
                searchEditText.setCursorColor(0xff50a8eb);
                searchEditText.setCursorSize(AndroidUtilities.dp(20));
                searchEditText.setCursorWidth(1.5f);
                addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 16 + 38, 9, 16 + 30, 0));
                searchEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        boolean show = searchEditText.length() > 0;
                        boolean showed = clearSearchImageView.getAlpha() != 0;
                        if (show != showed) {
                            clearSearchImageView.animate()
                                    .alpha(show ? 1.0f : 0.0f)
                                    .setDuration(150)
                                    .scaleX(show ? 1.0f : 0.1f)
                                    .scaleY(show ? 1.0f : 0.1f)
                                    .start();
                        }
                        String text = searchEditText.getText().toString();
                        if (text.length() != 0) {
                            if (searchEmptyView != null) {
                                searchEmptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                            }
                        } else {
                            if (listView.getAdapter() != listAdapter) {
                                int top = getCurrentTop();
                                searchEmptyView.setText(LocaleController.getString("NoChats", R.string.NoChats));
                                searchEmptyView.showTextView();
                                listView.setAdapter(listAdapter);
                                listAdapter.notifyDataSetChanged();
                                if (top > 0) {
                                    layoutManager.scrollToPositionWithOffset(0, -top);
                                }
                            }
                        }
                        if (searchAdapter != null) {
                            searchAdapter.searchDialogs(text);
                        }
                    }
                });
                searchEditText.setOnEditorActionListener((v, actionId, event) -> {
                    if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                        AndroidUtilities.hideKeyboard(searchEditText);
                    }
                    return false;
                });
            }

            public void hideKeyboard() {
                AndroidUtilities.hideKeyboard(searchEditText);
            }

            public void showKeyboard() {
                searchEditText.requestFocus();
                AndroidUtilities.showKeyboard(searchEditText);
            }

            @Override
            public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                super.requestDisallowInterceptTouchEvent(disallowIntercept);
            }
        }

        private class ColorPicker extends FrameLayout {

            private LinearLayout linearLayout;

            private final int paramValueSliderWidth = AndroidUtilities.dp(20);

            private Paint colorWheelPaint;
            private Paint valueSliderPaint;
            private Paint circlePaint;
            private Drawable circleDrawable;

            private Bitmap colorWheelBitmap;

            private EditTextBoldCursor[] colorEditText = new EditTextBoldCursor[4];

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
                for (int a = 0; a < 4; a++) {
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
                        ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, start ? 0 : 51),
                        ObjectAnimator.ofFloat(containerView, View.ALPHA, start ? 0.2f : 1.0f));
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
            super(context, true, 1);

            shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();

            containerView = new FrameLayout(context) {

                private boolean ignoreLayout = false;
                private RectF rect1 = new RectF();

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
                    if (Build.VERSION.SDK_INT >= 21 && !isFullscreen) {
                        ignoreLayout = true;
                        setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                        ignoreLayout = false;
                    }

                    int pickerSize = Math.min(width, height - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0));

                    int padding = height - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0) + AndroidUtilities.dp(8) - pickerSize;
                    if (listView.getPaddingTop() != padding) {
                        ignoreLayout = true;
                        int previousPadding = listView.getPaddingTop();
                        listView.setPadding(0, padding, 0, AndroidUtilities.dp(48));
                        if (colorPicker.getVisibility() == VISIBLE) {
                            //previousScrollPosition += previousPadding;
                            setScrollOffsetY(listView.getPaddingTop());
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
                    int y = scrollOffsetY - backgroundPaddingTop + AndroidUtilities.dp(6);
                    int top = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(13);
                    int height = getMeasuredHeight() + AndroidUtilities.dp(30) + backgroundPaddingTop;
                    int statusBarHeight = 0;
                    float radProgress = 1.0f;
                    if (!isFullscreen && Build.VERSION.SDK_INT >= 21) {
                        top += AndroidUtilities.statusBarHeight;
                        y += AndroidUtilities.statusBarHeight;
                        height -= AndroidUtilities.statusBarHeight;

                        if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight * 2) {
                            int diff = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop);
                            top -= diff;
                            height += diff;
                            radProgress = 1.0f - Math.min(1.0f, (diff * 2) / (float) AndroidUtilities.statusBarHeight);
                        }
                        if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight) {
                            statusBarHeight = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop);
                        }
                    }

                    shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                    shadowDrawable.draw(canvas);

                    if (radProgress != 1.0f) {
                        Theme.dialogs_onlineCirclePaint.setColor(0xffffffff);
                        rect1.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                        canvas.drawRoundRect(rect1, AndroidUtilities.dp(12) * radProgress, AndroidUtilities.dp(12) * radProgress, Theme.dialogs_onlineCirclePaint);
                    }

                    int w = AndroidUtilities.dp(36);
                    rect1.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
                    Theme.dialogs_onlineCirclePaint.setColor(0xffe1e4e8);
                    Theme.dialogs_onlineCirclePaint.setAlpha((int) (255 * listView.getAlpha()));
                    canvas.drawRoundRect(rect1, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);

                    if (statusBarHeight > 0) {
                        int color1 = 0xffffffff;
                        int finalColor = Color.argb(0xff, (int) (Color.red(color1) * 0.8f), (int) (Color.green(color1) * 0.8f), (int) (Color.blue(color1) * 0.8f));
                        Theme.dialogs_onlineCirclePaint.setColor(finalColor);
                        canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight - statusBarHeight, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
                    }
                }
            };
            containerView.setWillNotDraw(false);
            containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

            frameLayout = new FrameLayout(context);
            frameLayout.setBackgroundColor(0xffffffff);

            searchField = new SearchField(context);
            frameLayout.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

            listView = new RecyclerListView(context) {
                @Override
                protected boolean allowSelectChildAtPosition(float x, float y) {
                    return y >= scrollOffsetY + AndroidUtilities.dp(48) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                }
            };
            listView.setSelectorDrawableColor(0x0f000000);
            listView.setPadding(0, 0, 0, AndroidUtilities.dp(48));
            listView.setClipToPadding(false);
            listView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext()));
            listView.setHorizontalScrollBarEnabled(false);
            listView.setVerticalScrollBarEnabled(false);
            containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            listView.setAdapter(listAdapter = new ListAdapter(context, items));
            searchAdapter = new SearchAdapter(context);
            listView.setGlowColor(0xfff5f6f7);
            listView.setItemAnimator(null);
            listView.setLayoutAnimation(null);
            listView.setOnItemClickListener((view, position) -> {
                if (position == 0) {
                    return;
                }
                if (listView.getAdapter() == listAdapter) {
                    currentThemeDesription = listAdapter.getItem(position - 1);
                } else {
                    currentThemeDesription = searchAdapter.getItem(position - 1);
                }
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
            });
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    updateLayout();
                }
            });

            searchEmptyView = new EmptyTextProgressView(context);
            searchEmptyView.setShowAtCenter(true);
            searchEmptyView.showTextView();
            searchEmptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
            listView.setEmptyView(searchEmptyView);
            containerView.addView(searchEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 52, 0, 0));

            FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
            frameLayoutParams.topMargin = AndroidUtilities.dp(58);
            shadow[0] = new View(context);
            shadow[0].setBackgroundColor(0x12000000);
            shadow[0].setAlpha(0.0f);
            shadow[0].setTag(1);
            containerView.addView(shadow[0], frameLayoutParams);

            containerView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP));

            colorPicker = new ColorPicker(context);
            colorPicker.setVisibility(View.GONE);
            containerView.addView(colorPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

            frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
            frameLayoutParams.bottomMargin = AndroidUtilities.dp(48);
            shadow[1] = new View(context);
            shadow[1].setBackgroundColor(0x12000000);
            containerView.addView(shadow[1], frameLayoutParams);

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
            closeButton.setOnClickListener(v -> dismiss());

            TextView saveButton = new TextView(context);
            saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            saveButton.setTextColor(0xff19a7e8);
            saveButton.setGravity(Gravity.CENTER);
            saveButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
            saveButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            saveButton.setText(LocaleController.getString("SaveTheme", R.string.SaveTheme).toUpperCase());
            saveButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            bottomSaveLayout.addView(saveButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));
            saveButton.setOnClickListener(v -> {
                Theme.saveCurrentTheme(themeInfo, true, false, false);
                setOnDismissListener(null);
                dismiss();
                close();
            });

            bottomLayout = new FrameLayout(context);
            bottomLayout.setVisibility(View.GONE);
            bottomLayout.setBackgroundColor(0xffffffff);
            containerView.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));

            TextView cancelButton = new TextView(context);
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelButton.setTextColor(0xff19a7e8);
            cancelButton.setGravity(Gravity.CENTER);
            cancelButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
            cancelButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
            cancelButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            bottomLayout.addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            cancelButton.setOnClickListener(v -> {
                for (int a = 0; a < currentThemeDesription.size(); a++) {
                    currentThemeDesription.get(a).setPreviousColor();
                }
                setColorPickerVisible(false);
            });

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            bottomLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

            TextView defaultButtom = new TextView(context);
            defaultButtom.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            defaultButtom.setTextColor(0xff19a7e8);
            defaultButtom.setGravity(Gravity.CENTER);
            defaultButtom.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
            defaultButtom.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            defaultButtom.setText(LocaleController.getString("Default", R.string.Default).toUpperCase());
            defaultButtom.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            linearLayout.addView(defaultButtom, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            defaultButtom.setOnClickListener(v -> {
                for (int a = 0; a < currentThemeDesription.size(); a++) {
                    currentThemeDesription.get(a).setDefaultColor();
                }
                setColorPickerVisible(false);
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
            saveButton.setOnClickListener(v -> setColorPickerVisible(false));
        }

        private void runShadowAnimation(final int num, final boolean show) {
            if (show && shadow[num].getTag() != null || !show && shadow[num].getTag() == null) {
                shadow[num].setTag(show ? null : 1);
                if (show) {
                    shadow[num].setVisibility(View.VISIBLE);
                }
                if (shadowAnimation[num] != null) {
                    shadowAnimation[num].cancel();
                }
                shadowAnimation[num] = new AnimatorSet();
                shadowAnimation[num].playTogether(ObjectAnimator.ofFloat(shadow[num], View.ALPHA, show ? 1.0f : 0.0f));
                shadowAnimation[num].setDuration(150);
                shadowAnimation[num].addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                            if (!show) {
                                shadow[num].setVisibility(View.INVISIBLE);
                            }
                            shadowAnimation[num] = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                            shadowAnimation[num] = null;
                        }
                    }
                });
                shadowAnimation[num].start();
            }
        }

        @Override
        public void dismissInternal() {
            super.dismissInternal();
            if (searchField.searchEditText.isFocused()) {
                AndroidUtilities.hideKeyboard(searchField.searchEditText);
            }
        }

        private void setColorPickerVisible(boolean visible) {
            if (visible) {
                animationInProgress = true;
                colorPicker.setVisibility(View.VISIBLE);
                bottomLayout.setVisibility(View.VISIBLE);
                colorPicker.setAlpha(0.0f);
                bottomLayout.setAlpha(0.0f);

                previousScrollPosition = scrollOffsetY;
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(colorPicker, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(bottomLayout, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(listView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(frameLayout, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(shadow[0], View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(searchEmptyView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(bottomSaveLayout, View.ALPHA, 0.0f),
                        ObjectAnimator.ofInt(this, "scrollOffsetY", listView.getPaddingTop()));
                animatorSet.setDuration(150);
                animatorSet.setInterpolator(decelerateInterpolator);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        listView.setVisibility(View.INVISIBLE);
                        searchField.setVisibility(View.INVISIBLE);
                        bottomSaveLayout.setVisibility(View.INVISIBLE);
                        animationInProgress = false;
                    }
                });
                animatorSet.start();
            } else {
                if (parentActivity != null) {
                    ((LaunchActivity) parentActivity).rebuildAllFragments(false);
                }
                Theme.saveCurrentTheme(themeInfo, false, false, false);
                if (listView.getAdapter() == listAdapter) {
                    AndroidUtilities.hideKeyboard(getCurrentFocus());
                }
                animationInProgress = true;
                listView.setVisibility(View.VISIBLE);
                bottomSaveLayout.setVisibility(View.VISIBLE);
                searchField.setVisibility(View.VISIBLE);
                listView.setAlpha(0.0f);
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(colorPicker, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(bottomLayout, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(listView, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(frameLayout, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(shadow[0], View.ALPHA, shadow[0].getTag() != null ? 0.0f : 1.0f),
                        ObjectAnimator.ofFloat(searchEmptyView, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(bottomSaveLayout, View.ALPHA, 1.0f),
                        ObjectAnimator.ofInt(this, "scrollOffsetY", previousScrollPosition));
                animatorSet.setDuration(150);
                animatorSet.setInterpolator(decelerateInterpolator);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (listView.getAdapter() == searchAdapter) {
                            searchField.showKeyboard();
                        }
                        colorPicker.setVisibility(View.GONE);
                        bottomLayout.setVisibility(View.GONE);
                        animationInProgress = false;
                    }
                });
                animatorSet.start();
                listView.getAdapter().notifyItemChanged(currentThemeDesriptionPosition);
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
            int newOffset;
            if (top > -AndroidUtilities.dp(1) && holder != null && holder.getAdapterPosition() == 0) {
                newOffset = top;
                runShadowAnimation(0, false);
            } else {
                newOffset = 0;
                runShadowAnimation(0, true);
            }
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
            frameLayout.setTranslationY(scrollOffsetY);
            colorPicker.setTranslationY(scrollOffsetY);
            searchEmptyView.setTranslationY(scrollOffsetY);
            containerView.invalidate();
        }

        public class SearchAdapter extends RecyclerListView.SelectionAdapter {

            private Context context;
            private int lastSearchId;
            private int currentCount;
            private ArrayList<ArrayList<ThemeDescription>> searchResult = new ArrayList<>();
            private ArrayList<CharSequence> searchNames = new ArrayList<>();
            private Runnable searchRunnable;
            private String lastSearchText;

            public SearchAdapter(Context context) {
                this.context = context;
            }

            public CharSequence generateSearchName(String name, String q) {
                if (TextUtils.isEmpty(name)) {
                    return "";
                }
                SpannableStringBuilder builder = new SpannableStringBuilder();
                String wholeString = name.trim();
                String lower = wholeString.toLowerCase();

                int index;
                int lastIndex = 0;
                while ((index = lower.indexOf(q, lastIndex)) != -1) {
                    int end = q.length() + index;

                    if (lastIndex != 0 && lastIndex != index + 1) {
                        builder.append(wholeString.substring(lastIndex, index));
                    } else if (lastIndex == 0 && index != 0) {
                        builder.append(wholeString.substring(0, index));
                    }

                    String query = wholeString.substring(index, Math.min(wholeString.length(), end));
                    if (query.startsWith(" ")) {
                        builder.append(" ");
                    }
                    query = query.trim();

                    int start = builder.length();
                    builder.append(query);
                    builder.setSpan(new ForegroundColorSpan(0xff4d83b3), start, start + query.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    lastIndex = end;
                }

                if (lastIndex != -1 && lastIndex < wholeString.length()) {
                    builder.append(wholeString.substring(lastIndex));
                }

                return builder;
            }

            private void searchDialogsInternal(final String query, final int searchId) {
                try {
                    String search1 = query.trim().toLowerCase();
                    if (search1.length() == 0) {
                        lastSearchId = -1;
                        updateSearchResults(new ArrayList<>(), new ArrayList<>(), lastSearchId);
                        return;
                    }
                    String search2 = LocaleController.getInstance().getTranslitString(search1);
                    if (search1.equals(search2) || search2.length() == 0) {
                        search2 = null;
                    }
                    String[] search = new String[1 + (search2 != null ? 1 : 0)];
                    search[0] = search1;
                    if (search2 != null) {
                        search[1] = search2;
                    }

                    ArrayList<ArrayList<ThemeDescription>> searchResults = new ArrayList<>();
                    ArrayList<CharSequence> names = new ArrayList<>();
                    for (int a = 0, N = listAdapter.items.size(); a < N; a++) {
                        ArrayList<ThemeDescription> themeDescriptions = listAdapter.items.get(a);
                        String key = themeDescriptions.get(0).getCurrentKey();
                        String name = key.toLowerCase();
                        int found = 0;
                        for (String q : search) {
                            if (name.contains(q)) {
                                searchResults.add(themeDescriptions);
                                names.add(generateSearchName(key, q));
                                break;
                            }
                        }
                    }
                    updateSearchResults(searchResults, names, searchId);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            private void updateSearchResults(final ArrayList<ArrayList<ThemeDescription>> result, ArrayList<CharSequence> names, final int searchId) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (searchId != lastSearchId) {
                        return;
                    }
                    if (listView.getAdapter() != searchAdapter) {
                        topBeforeSwitch = getCurrentTop();
                        listView.setAdapter(searchAdapter);
                        searchAdapter.notifyDataSetChanged();
                    }
                    boolean becomeEmpty = !searchResult.isEmpty() && result.isEmpty();
                    boolean isEmpty = searchResult.isEmpty() && result.isEmpty();
                    if (becomeEmpty) {
                        topBeforeSwitch = getCurrentTop();
                    }
                    searchResult = result;
                    searchNames = names;
                    notifyDataSetChanged();
                    if (!isEmpty && !becomeEmpty && topBeforeSwitch > 0) {
                        layoutManager.scrollToPositionWithOffset(0, -topBeforeSwitch);
                        topBeforeSwitch = -1000;
                    }
                    searchEmptyView.showTextView();
                });
            }

            public void searchDialogs(final String query) {
                if (query != null && query.equals(lastSearchText)) {
                    return;
                }
                lastSearchText = query;
                if (searchRunnable != null) {
                    Utilities.searchQueue.cancelRunnable(searchRunnable);
                    searchRunnable = null;
                }
                if (query == null || query.length() == 0) {
                    searchResult.clear();
                    topBeforeSwitch = getCurrentTop();
                    lastSearchId = -1;
                    notifyDataSetChanged();
                } else {
                    final int searchId = ++lastSearchId;
                    searchRunnable = () -> searchDialogsInternal(query, searchId);
                    Utilities.searchQueue.postRunnable(searchRunnable, 300);
                }
            }

            @Override
            public int getItemCount() {
                return searchResult.isEmpty() ? 0 : (searchResult.size() + 1);
            }

            public ArrayList<ThemeDescription> getItem(int i) {
                if (i < 0 || i >= searchResult.size()) {
                    return null;
                }
                return searchResult.get(i);
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View view;
                switch (viewType) {
                    case 0:
                        view = new TextColorThemeCell(context);
                        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                        break;
                    case 1:
                    default:
                        view = new View(context);
                        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                        break;
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                if (holder.getItemViewType() == 0) {
                    ArrayList<ThemeDescription> arrayList = searchResult.get(position - 1);
                    ThemeDescription description = arrayList.get(0);
                    int color;
                    if (description.getCurrentKey().equals(Theme.key_chat_wallpaper)) {
                        color = 0;
                    } else {
                        color = description.getSetColor();
                    }
                    ((TextColorThemeCell) holder.itemView).setTextAndColor(searchNames.get(position - 1), color);
                }
            }

            @Override
            public int getItemViewType(int i) {
                if (i == 0) {
                    return 1;
                }
                return 0;
            }
        }

        private class ListAdapter extends RecyclerListView.SelectionAdapter {

            private Context context;
            private int currentCount;
            private ArrayList<ArrayList<ThemeDescription>> items = new ArrayList<>();

            public ListAdapter(Context context, ThemeDescription[] descriptions) {
                this.context = context;
                HashMap<String, ArrayList<ThemeDescription>> itemsMap = new HashMap<>();
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
                return items.isEmpty() ? 0 : (items.size() + 1);
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
                View view;
                switch (viewType) {
                    case 0:
                        view = new TextColorThemeCell(context);
                        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                        break;
                    case 1:
                    default:
                        view = new View(context);
                        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                        break;
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                if (holder.getItemViewType() == 0) {
                    ArrayList<ThemeDescription> arrayList = items.get(position - 1);
                    ThemeDescription description = arrayList.get(0);
                    int color;
                    if (description.getCurrentKey().equals(Theme.key_chat_wallpaper)) {
                        color = 0;
                    } else {
                        color = description.getSetColor();
                    }
                    ((TextColorThemeCell) holder.itemView).setTextAndColor(description.getTitle(), color);
                }
            }

            @Override
            public int getItemViewType(int i) {
                if (i == 0) {
                    return 1;
                }
                return 0;
            }
        }
    }

    public void show(Activity activity, final Theme.ThemeInfo theme) {
        if (Instance != null) {
            Instance.destroy();
        }
        hidden = false;
        themeInfo = theme;
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
                                        editorAlert.setOnDismissListener(dialog -> {

                                        });
                                        editorAlert.setOnDismissListener(dialog -> {
                                            editorAlert = null;
                                            show();
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
        wallpaperUpdater = new WallpaperUpdater(activity, null, new WallpaperUpdater.WallpaperUpdaterDelegate() {
            @Override
            public void didSelectWallpaper(File file, Bitmap bitmap, boolean gallery) {
                Theme.setThemeWallpaper(themeInfo, bitmap, file);
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
        animatorSet.playTogether(ObjectAnimator.ofFloat(windowView, View.ALPHA, 0.0f, 1.0f),
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
            animatorSet.playTogether(ObjectAnimator.ofFloat(windowView, View.ALPHA, 1.0f, 0.0f),
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
                animators.add(ObjectAnimator.ofFloat(windowView, View.ALPHA, 1.0f));
            }
            animators.add(ObjectAnimator.ofInt(this, "x", startX));
        } else if (Math.abs(endX - windowLayoutParams.x) <= maxDiff || windowLayoutParams.x > AndroidUtilities.displaySize.x - editorWidth && windowLayoutParams.x < AndroidUtilities.displaySize.x - editorWidth / 4 * 3) {
            if (animators == null) {
                animators = new ArrayList<>();
            }
            editor.putInt("sidex", 1);
            if (windowView.getAlpha() != 1.0f) {
                animators.add(ObjectAnimator.ofFloat(windowView, View.ALPHA, 1.0f));
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
                animators.add(ObjectAnimator.ofFloat(windowView, View.ALPHA, 0.0f));
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        Theme.saveCurrentTheme(themeInfo, true, false, false);
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
