/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.getWallpaperRotation;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.primitives.Chars;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.ui.ActionBar.FloatingActionMode;
import org.telegram.ui.ActionBar.FloatingToolbar;
import org.telegram.ui.ActionBar.Theme;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class EditTextBoldCursor extends EditTextEffects {

    private static Field mEditor;
    private static Field mShowCursorField;
    private static Field mScrollYField;
    private static boolean mScrollYGet;
    private static Method getVerticalOffsetMethod;
    private static Class editorClass;
    private static Field mCursorDrawableResField;
    private static Method mEditorInvalidateDisplayList;

    private Drawable mCursorDrawable;
    private Object editor;

    private GradientDrawable gradientDrawable;
    private SubstringLayoutAnimator hintAnimator;
    float rightHintOffset;

    private Runnable invalidateRunnable = new Runnable() {
        @Override
        public void run() {
            invalidate();
            if (attachedToWindow != null) {
                AndroidUtilities.runOnUIThread(this, 500);
            }
        }
    };

    private Paint linePaint;
    private Paint activeLinePaint;
    private TextPaint errorPaint;

    private int cursorSize;
    private int ignoreTopCount;
    private int ignoreBottomCount;
    private int scrollY;
    private float lineSpacingExtra;
    private Rect rect = new Rect();
    private StaticLayout hintLayout;
    public float hintLayoutX, hintLayoutY;
    public boolean hintLayoutYFix;
    public boolean lineYFix;
    public Utilities.Callback2<Canvas, Runnable> drawHint;
    private AnimatedTextView.AnimatedTextDrawable hintAnimatedDrawable;
    private AnimatedTextView.AnimatedTextDrawable hintAnimatedDrawable2;
    private CharSequence hint;
    private StaticLayout errorLayout;
    private CharSequence errorText;
    private int hintColor;
    private int headerHintColor;
    private boolean hintVisible = true;
    private float hintAlpha = 1.0f;
    private long hintLastUpdateTime;
    private boolean allowDrawCursor = true;
    private boolean forceCursorEnd = false;
    private float cursorWidth = 2.0f;
    private boolean supportRtlHint;

    public boolean ignoreClipTop;
    private boolean cursorDrawn;

    private boolean lineVisible = false;
    private int lineColor;
    private int activeLineColor;
    private int errorLineColor;
    private float lineY;
    private boolean lineActive = false;
    private float lineActiveness = 0;
    private long lineLastUpdateTime;
    private float lastLineActiveness = 0;
    private float activeLineWidth = 0;

    private boolean nextSetTextAnimated;
    private boolean transformHintToHeader;
    private boolean currentDrawHintAsHeader;
    private AnimatorSet headerTransformAnimation;
    private float headerAnimationProgress;

    private boolean fixed;
    private ViewTreeObserver.OnPreDrawListener listenerFixer;

    private FloatingToolbar floatingToolbar;
    public FloatingActionMode floatingActionMode;
    private ViewTreeObserver.OnPreDrawListener floatingToolbarPreDrawListener;
    private View windowView;
    private View attachedToWindow;
    private int lastSize;
    int lastOffset = -1;
    CharSequence lastText;

    boolean drawInMaim;
    ShapeDrawable cursorDrawable;

    private List<TextWatcher> registeredTextWatchers = new ArrayList<>();
    private boolean isTextWatchersSuppressed = false;

    public void setHintText2(CharSequence text, boolean animated) {
        if (hintAnimatedDrawable2 != null) {
            hintAnimatedDrawable2.setText(text, !LocaleController.isRTL && animated);
        }
    }

    public void setHintRightOffset(int rightHintOffset) {
        if (this.rightHintOffset == rightHintOffset) {
            return;
        }
        this.rightHintOffset = rightHintOffset;
        invalidate();
    }

    @TargetApi(23)
    private class ActionModeCallback2Wrapper extends ActionMode.Callback2 {
        private final ActionMode.Callback mWrapped;

        public ActionModeCallback2Wrapper(ActionMode.Callback wrapped) {
            mWrapped = wrapped;
        }

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return mWrapped.onCreateActionMode(mode, menu);
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return mWrapped.onPrepareActionMode(mode, menu);
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return mWrapped.onActionItemClicked(mode, item);
        }

        public void onDestroyActionMode(ActionMode mode) {
            mWrapped.onDestroyActionMode(mode);
            cleanupFloatingActionModeViews();
            floatingActionMode = null;
        }

        @Override
        public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
            if (mWrapped instanceof ActionMode.Callback2) {
                ((ActionMode.Callback2) mWrapped).onGetContentRect(mode, view, outRect);
            } else {
                super.onGetContentRect(mode, view, outRect);
            }
        }
    }

    public EditTextBoldCursor(Context context) {
        super(context);
        if (Build.VERSION.SDK_INT >= 26) {
            setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        init();
    }

    public void useAnimatedTextDrawable() {
        hintAnimatedDrawable = new AnimatedTextView.AnimatedTextDrawable() {
            @Override
            public void invalidateSelf() {
                invalidate();
            }
        };
        hintAnimatedDrawable.setEllipsizeByGradient(true);
        hintAnimatedDrawable.setTextColor(hintColor);
        hintAnimatedDrawable.setTextSize(getPaint().getTextSize());

        hintAnimatedDrawable2 = new AnimatedTextView.AnimatedTextDrawable() {
            @Override
            public void invalidateSelf() {
                invalidate();
            }
        };
        hintAnimatedDrawable2.setGravity(Gravity.RIGHT);
        hintAnimatedDrawable2.setTextColor(hintColor);
        hintAnimatedDrawable2.setTextSize(getPaint().getTextSize());
    }

    @Override
    public void addTextChangedListener(TextWatcher watcher) {
        registeredTextWatchers.add(watcher);
        if (isTextWatchersSuppressed) {
            return;
        }
        super.addTextChangedListener(watcher);
    }

    @Override
    public void removeTextChangedListener(TextWatcher watcher) {
        registeredTextWatchers.remove(watcher);
        if (isTextWatchersSuppressed) {
            return;
        }
        super.removeTextChangedListener(watcher);
    }

    /**
     * Dispatches text changed event to all text watchers
     */
    public void dispatchTextWatchersTextChanged() {
        for (TextWatcher w : registeredTextWatchers) {
            w.beforeTextChanged("", 0, length(), length());
            w.onTextChanged(getText(), 0, length(), length());
            w.afterTextChanged(getText());
        }
    }

    /**
     * Sets text watchers suppress state
     *
     * @param textWatchersSuppressed    Suppress flag
     * @param dispatchChanged           If we should notify watchers about text changed. Works only if textWatchersSuppressed = false
     */
    public void setTextWatchersSuppressed(boolean textWatchersSuppressed, boolean dispatchChanged) {
        if (isTextWatchersSuppressed == textWatchersSuppressed) return;
        isTextWatchersSuppressed = textWatchersSuppressed;

        if (textWatchersSuppressed) {
            for (TextWatcher w : registeredTextWatchers) {
                super.removeTextChangedListener(w);
            }
        } else {
            for (TextWatcher w : registeredTextWatchers) {
                super.addTextChangedListener(w);
                if (dispatchChanged) {
                    w.beforeTextChanged("", 0, length(), length());
                    w.onTextChanged(getText(), 0, length(), length());
                    w.afterTextChanged(getText());
                }
            }
        }
    }

    /**
     * @return  If text watchers are suppressed (Not listening to events)
     */
    public boolean isTextWatchersSuppressed() {
        return isTextWatchersSuppressed;
    }

    @Nullable
    @Override
    public Drawable getTextCursorDrawable() {
        if (cursorDrawable != null) {
            return super.getTextCursorDrawable();
        }
        ShapeDrawable shapeDrawable = new ShapeDrawable(new RectShape()) {
            @Override
            public void draw(Canvas canvas) {
                super.draw(canvas);
                cursorDrawn = true;
            }
        };
        shapeDrawable.getPaint().setColor(0);
        return shapeDrawable;
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public int getAutofillType() {
        return AUTOFILL_TYPE_NONE;
    }

    @SuppressLint("PrivateApi")
    private void init() {
        linePaint = new Paint();
        activeLinePaint = new Paint();
        errorPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        errorPaint.setTextSize(dp(11));
        if (Build.VERSION.SDK_INT >= 26) {
            setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursorDrawable = new ShapeDrawable() {

                @Override
                public void draw(Canvas canvas) {
                    if (drawInMaim) {
                        cursorDrawn = true;
                    } else {
                        super.draw(canvas);
                    }
                }

                @Override
                public int getIntrinsicHeight() {
                    return dp(cursorSize + 20);
                }

                @Override
                public int getIntrinsicWidth() {
                    return dp(cursorWidth);
                }
            };
            cursorDrawable.setShape(new RectShape());
            gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xff54a1db, 0xff54a1db});

            setTextCursorDrawable(cursorDrawable);
        }


        try {
            if (!mScrollYGet && mScrollYField == null) {
                mScrollYGet = true;
                mScrollYField = View.class.getDeclaredField("mScrollY");
                mScrollYField.setAccessible(true);
            }
        } catch (Throwable ignore) {

        }
        try {
            if (editorClass == null) {
                mEditor = TextView.class.getDeclaredField("mEditor");
                mEditor.setAccessible(true);
                editorClass = Class.forName("android.widget.Editor");
                try {
                    mShowCursorField = editorClass.getDeclaredField("mShowCursor");
                    mShowCursorField.setAccessible(true);
                } catch (Exception ignore) {}
                try {
                    mEditorInvalidateDisplayList = editorClass.getDeclaredMethod("invalidateTextDisplayList");
                    mEditorInvalidateDisplayList.setAccessible(true);
                } catch (Exception ignore) {}
                getVerticalOffsetMethod = TextView.class.getDeclaredMethod("getVerticalOffset", boolean.class);
                getVerticalOffsetMethod.setAccessible(true);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (cursorDrawable == null) {
            try {
                gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xff54a1db, 0xff54a1db});
                if (Build.VERSION.SDK_INT >= 29) {
                    setTextCursorDrawable(gradientDrawable);
                }
                editor = mEditor.get(this);
            } catch (Throwable ignore) {

            }
            try {
                if (mCursorDrawableResField == null) {
                    mCursorDrawableResField = TextView.class.getDeclaredField("mCursorDrawableRes");
                    mCursorDrawableResField.setAccessible(true);
                }
                if (mCursorDrawableResField != null) {
                    mCursorDrawableResField.set(this, R.drawable.field_carret_empty);
                }
            } catch (Throwable ignore) {

            }
        }
        cursorSize = dp(24);
    }

    @SuppressLint("PrivateApi")
    public void fixHandleView(boolean reset) {
        if (reset) {
            fixed = false;
        } else if (!fixed) {
            try {
                if (editorClass == null) {
                    editorClass = Class.forName("android.widget.Editor");
                    mEditor = TextView.class.getDeclaredField("mEditor");
                    mEditor.setAccessible(true);
                    editor = mEditor.get(this);
                }
                if (listenerFixer == null) {
                    Method initDrawablesMethod = editorClass.getDeclaredMethod("getPositionListener");
                    initDrawablesMethod.setAccessible(true);
                    listenerFixer = (ViewTreeObserver.OnPreDrawListener) initDrawablesMethod.invoke(editor);
                }
                AndroidUtilities.runOnUIThread(listenerFixer::onPreDraw, 500);
            } catch (Throwable ignore) {

            }
            fixed = true;
        }
    }

    public void setTransformHintToHeader(boolean value) {
        if (transformHintToHeader == value) {
            return;
        }
        transformHintToHeader = value;
        if (headerTransformAnimation != null) {
            headerTransformAnimation.cancel();
            headerTransformAnimation = null;
        }
    }

    public void setAllowDrawCursor(boolean value) {
        allowDrawCursor = value;
        invalidate();
    }

    public void setForceCursorEnd(boolean cursorEnd) {
        this.forceCursorEnd = cursorEnd;
        invalidate();
    }

    public void setCursorWidth(float width) {
        cursorWidth = width;
    }

    public void setCursorColor(int color) {
        if (cursorDrawable != null) {
            cursorDrawable.getPaint().setColor(color);
        }
        if (gradientDrawable != null) {
            gradientDrawable.setColor(color);
        }
        invalidate();
    }

    public void setCursorSize(int value) {
        cursorSize = value;
    }

    public void setErrorLineColor(int error) {
        errorLineColor = error;
        errorPaint.setColor(errorLineColor);
        invalidate();
    }

    private Rect padding = new Rect();
    public void setLineColors(int color, int active, int error) {
        lineVisible = true;
        getContext().getResources().getDrawable(R.drawable.search_dark).getPadding(padding);
        setPadding(padding.left, padding.top, padding.right, padding.bottom);
        lineColor = color;
        activeLineColor = active;
        activeLinePaint.setColor(activeLineColor);
        errorLineColor = error;
        errorPaint.setColor(errorLineColor);
        invalidate();
    }

    public void setHintVisible(boolean value, boolean animated) {
        if (hintVisible == value) {
            return;
        }
        hintLastUpdateTime = System.currentTimeMillis();
        hintVisible = value;
        if (!animated) {
            hintAlpha = hintVisible ? 1f : 0;
        }
        invalidate();
    }

    public void setHintColor(int value) {
        hintColor = value;
        if (hintAnimatedDrawable != null) {
            hintAnimatedDrawable.setTextColor(hintColor);
        }
        if (hintAnimatedDrawable2 != null) {
            hintAnimatedDrawable2.setTextColor(hintColor);
        }
        invalidate();
    }

    public void setHeaderHintColor(int value) {
        headerHintColor = value;
        invalidate();
    }

    @Override
    public void setTextSize(int unit, float size) {
        if (hintAnimatedDrawable != null) {
            hintAnimatedDrawable.setTextSize(dp(size));
        }
        if (hintAnimatedDrawable2 != null) {
            hintAnimatedDrawable2.setTextSize(dp(size));
        }
        super.setTextSize(unit, size);
    }

    public void setNextSetTextAnimated(boolean value) {
        nextSetTextAnimated = value;
    }

    public void setErrorText(CharSequence text) {
        if (TextUtils.equals(text, errorText)) {
            return;
        }
        errorText = text;
        requestLayout();
    }

    public boolean hasErrorText() {
        return !TextUtils.isEmpty(errorText);
    }

    public StaticLayout getErrorLayout(int width) {
        if (TextUtils.isEmpty(errorText)) {
            return null;
        } else {
            return new StaticLayout(errorText, errorPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
    }

    public float getLineY() {
        return lineY;
    }

    public void setSupportRtlHint(boolean value) {
        supportRtlHint = value;
    }

    @Override
    protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
        if (horiz != oldHoriz) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        checkHeaderVisibility(nextSetTextAnimated);
        nextSetTextAnimated = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int currentSize = getMeasuredHeight() + (getMeasuredWidth() << 16);
        if (hintAnimatedDrawable != null) {
            hintAnimatedDrawable.setBounds(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
        }
        if (hintAnimatedDrawable2 != null) {
            hintAnimatedDrawable2.setBounds(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
        }
        if (hintLayout != null && hintAnimatedDrawable == null) {
            if (lastSize != currentSize) {
                setHintText(hint, false, hintLayout.getPaint());
            }
            if (hintLayoutYFix) {
                lineY = getExtendedPaddingTop() + getPaddingTop() + (getMeasuredHeight() - getPaddingTop() - getPaddingBottom() - hintLayout.getHeight()) / 2.0f + hintLayout.getHeight() - dp(1);
            } else {
                lineY = (getMeasuredHeight() - hintLayout.getHeight()) / 2.0f + hintLayout.getHeight() + dp(6);
            }
        } else {
            lineY = getMeasuredHeight() - dp(2);
        }
        lastSize = currentSize;
    }

    public void setHintText(CharSequence text) {
        setHintText(text, false, getPaint());
    }

    public void setHintText(CharSequence text, boolean animated) {
        setHintText(text, animated, getPaint());
    }

    public void setHintText(CharSequence text, boolean animated, TextPaint paint) {
        if (hintAnimatedDrawable != null) {
            hintAnimatedDrawable.setText(text, !LocaleController.isRTL);
        } else {
            if (text == null) {
                text = "";
            }
            if (getMeasuredWidth() == 0) {
                animated = false;
            }
            if (animated) {
                if (hintAnimator == null) {
                    hintAnimator = new SubstringLayoutAnimator(this);
                }
                hintAnimator.create(hintLayout, hint, text, paint);
            } else {
                if (hintAnimator != null) {
                    hintAnimator.cancel();
                }
            }
            hint = text;
            if (getMeasuredWidth() != 0) {
                text = TextUtils.ellipsize(text, paint, getMeasuredWidth(), TextUtils.TruncateAt.END);
                if (hintLayout != null && TextUtils.equals(hintLayout.getText(), text)) {
                    return;
                }
            }
            hintLayout = new StaticLayout(text, paint, dp(1000), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            invalidate();
        }
    }

    public Layout getHintLayoutEx() {
        return hintLayout;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        try {
            super.onFocusChanged(focused, direction, previouslyFocusedRect);
        } catch (Exception e) {
            FileLog.e(e);
        }
        checkHeaderVisibility(true);
    }

    private void checkHeaderVisibility(boolean animated) {
        boolean newHintHeader = transformHintToHeader && (isFocused() || getText().length() > 0);
        if (currentDrawHintAsHeader != newHintHeader) {
            if (headerTransformAnimation != null) {
                headerTransformAnimation.cancel();
                headerTransformAnimation = null;
            }
            currentDrawHintAsHeader = newHintHeader;
            if (animated) {
                headerTransformAnimation = new AnimatorSet();
                headerTransformAnimation.playTogether(ObjectAnimator.ofFloat(this, "headerAnimationProgress", newHintHeader ? 1.0f : 0.0f));
                headerTransformAnimation.setDuration(200);
                headerTransformAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                headerTransformAnimation.start();
            } else {
                headerAnimationProgress = newHintHeader ? 1.0f : 0.0f;
            }
            invalidate();
        }
    }

    @Keep
    public void setHeaderAnimationProgress(float value) {
        headerAnimationProgress = value;
        invalidate();
    }

    @Keep
    public float getHeaderAnimationProgress() {
        return headerAnimationProgress;
    }

    @Override
    public void setLineSpacing(float add, float mult) {
        super.setLineSpacing(add, mult);
        lineSpacingExtra = add;
    }

    @Override
    public int getExtendedPaddingTop() {
        if (ignoreTopCount != 0) {
            ignoreTopCount--;
            return 0;
        }
        return super.getExtendedPaddingTop();
    }

    @Override
    public int getExtendedPaddingBottom() {
        if (ignoreBottomCount != 0) {
            ignoreBottomCount--;
            return scrollY != Integer.MAX_VALUE ? -scrollY : 0;
        }
        return super.getExtendedPaddingBottom();
    }

    private int lastTouchX = -1;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            lastTouchX = (int) event.getX();
        }
        return super.onTouchEvent(event);
    }

    public void invalidateForce() {
        invalidate();
        if (!isHardwareAccelerated()) {
            return;
        }
        try {
            // on hardware accelerated edittext to invalidate imagespan display list must be invalidated
            if (mEditorInvalidateDisplayList != null) {
                if (editor == null) {
                    editor = mEditor.get(this);
                }
                if (editor != null) {
                    mEditorInvalidateDisplayList.invoke(editor);
                }
            }
        } catch (Exception ignore) {};
    }

    private void drawHint(Canvas canvas) {
        if (length() != 0 && !transformHintToHeader) {
            return;
        }
        if (hintVisible && hintAlpha != 1.0f || !hintVisible && hintAlpha != 0.0f) {
            long newTime = System.currentTimeMillis();
            long dt = newTime - hintLastUpdateTime;
            if (dt < 0 || dt > 17) {
                dt = 17;
            }
            hintLastUpdateTime = newTime;
            if (hintVisible) {
                hintAlpha += dt / 150.0f;
                if (hintAlpha > 1.0f) {
                    hintAlpha = 1.0f;
                }
            } else {
                hintAlpha -= dt / 150.0f;
                if (hintAlpha < 0.0f) {
                    hintAlpha = 0.0f;
                }
            }
            invalidate();
        }
        if (hintAnimatedDrawable != null && !TextUtils.isEmpty(hintAnimatedDrawable.getText()) && (hintVisible || hintAlpha != 0)) {
            if (hintAnimatedDrawable2 != null) {
                if (hintAnimatedDrawable.getCurrentWidth() + hintAnimatedDrawable2.getCurrentWidth() < getMeasuredWidth()) {
                    canvas.save();
                    canvas.translate(hintAnimatedDrawable2.getCurrentWidth() - getMeasuredWidth() + hintAnimatedDrawable.getCurrentWidth(), 0);
                    hintAnimatedDrawable2.setAlpha((int) (Color.alpha(hintColor) * hintAlpha));
                    hintAnimatedDrawable2.draw(canvas);
                    canvas.restore();
                    hintAnimatedDrawable.setRightPadding(0);
                } else {
                    canvas.save();
                    canvas.translate(rightHintOffset, 0);
                    hintAnimatedDrawable2.setAlpha((int) (Color.alpha(hintColor) * hintAlpha));
                    hintAnimatedDrawable2.draw(canvas);
                    canvas.restore();
                    hintAnimatedDrawable.setRightPadding(hintAnimatedDrawable2.getCurrentWidth() + dp(2) - rightHintOffset);
                }
            } else {
                hintAnimatedDrawable.setRightPadding(0);
            }
            hintAnimatedDrawable.setAlpha((int) (Color.alpha(hintColor) * hintAlpha));
            hintAnimatedDrawable.draw(canvas);
        } else if (hintLayout != null && (hintVisible || hintAlpha != 0)) {
            int oldColor = getPaint().getColor();

            canvas.save();
            int left = 0;
            float lineLeft = hintLayout.getLineLeft(0);
            float hintWidth = hintLayout.getLineWidth(0);
            if (lineLeft != 0) {
                left -= lineLeft;
            }
            if (supportRtlHint && LocaleController.isRTL) {
                float offset = getMeasuredWidth() - hintWidth;
                canvas.translate(hintLayoutX = left + getScrollX() + offset, hintLayoutY = lineY - hintLayout.getHeight() - dp(7));
            } else {
                canvas.translate(hintLayoutX = left + getScrollX(), hintLayoutY = lineY - hintLayout.getHeight() - AndroidUtilities.dp2(7));
            }
            if (transformHintToHeader) {
                float scale = 1.0f - 0.3f * headerAnimationProgress;

                if (supportRtlHint && LocaleController.isRTL) {
                    canvas.translate((hintWidth + lineLeft) - (hintWidth + lineLeft) * scale, 0);
                } else if (lineLeft != 0) {
                    canvas.translate(lineLeft * (1.0f - scale), 0);
                }
                canvas.scale(scale, scale);
                canvas.translate(0, -dp(22) * headerAnimationProgress);
                getPaint().setColor(ColorUtils.blendARGB(hintColor, headerHintColor, headerAnimationProgress));
            } else {
                getPaint().setColor(hintColor);
                getPaint().setAlpha((int) (255 * hintAlpha * (Color.alpha(hintColor) / 255.0f)));
            }
            if (hintAnimator != null && hintAnimator.animateTextChange) {
                canvas.save();
                canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
                hintAnimator.draw(canvas, getPaint());
                canvas.restore();
            } else {
                if (drawHint != null) {
                    drawHint.run(canvas, () -> hintLayout.draw(canvas));
                } else {
                    hintLayout.draw(canvas);
                }
            }
            getPaint().setColor(oldColor);
            canvas.restore();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawHint(canvas);

        if (ellipsizeByGradient) {
            canvas.saveLayerAlpha(getScrollX() + getPaddingLeft() - ellipsizeWidth, 0, getScrollX() + getWidth() - getPaddingRight() + ellipsizeWidth, getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
        }

        int topPadding = getExtendedPaddingTop();
        scrollY = Integer.MAX_VALUE;
        try {
            if (mScrollYField != null) {
                scrollY = mScrollYField.getInt(this);
                mScrollYField.set(this, 0);
            } else {
                scrollY = getScrollX();
            }
        } catch (Exception e) {
            if (BuildVars.DEBUG_PRIVATE_VERSION) {
                throw new RuntimeException(e);
            }
        }
        ignoreTopCount = 1;
        ignoreBottomCount = 1;
        canvas.save();
        canvas.translate(0, topPadding);
        try {
            drawInMaim = true;
            super.onDraw(canvas);
            drawInMaim = false;
        } catch (Exception e) {
            if (BuildVars.DEBUG_PRIVATE_VERSION) {
                throw new RuntimeException(e);
            }
        }
        if (mScrollYField != null && scrollY != Integer.MAX_VALUE) {
            try {
                mScrollYField.set(this, scrollY);
            } catch (Exception e) {
                if (BuildVars.DEBUG_PRIVATE_VERSION) {
                    throw new RuntimeException(e);
                }
            }
        }
        canvas.restore();
        if (cursorDrawable == null) {
            try {
                boolean showCursor;
                if (mShowCursorField != null && editor != null) {
                    long mShowCursor = mShowCursorField.getLong(editor);
                    showCursor = (SystemClock.uptimeMillis() - mShowCursor) % (2 * 500) < 500 && isFocused();
                } else {
                    showCursor = cursorDrawn;
                    cursorDrawn = false;
                }
                if (allowDrawCursor && showCursor) {
                    canvas.save();
                    int voffsetCursor = 0;
                    if (getVerticalOffsetMethod != null) {
                        if ((getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
                            voffsetCursor = (int) getVerticalOffsetMethod.invoke(this, true);
                        }
                    } else {
                        if ((getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
                            voffsetCursor = getTotalPaddingTop() - getExtendedPaddingTop();
                        }
                    }
                    canvas.translate(getPaddingLeft(), getExtendedPaddingTop() + voffsetCursor);
                    Layout layout = getLayout();
                    int line = layout.getLineForOffset(getSelectionStart());
                    int lineCount = layout.getLineCount();
                    updateCursorPosition();
                    Rect bounds = gradientDrawable.getBounds();
                    rect.left = bounds.left;
                    rect.right = bounds.left + dp(cursorWidth);
                    rect.bottom = bounds.bottom;
                    rect.top = bounds.top;
                    if (lineSpacingExtra != 0 && line < lineCount - 1) {
                        rect.bottom -= lineSpacingExtra;
                    }
                    rect.top = rect.centerY() - cursorSize / 2;
                    rect.bottom = rect.top + cursorSize;
                    gradientDrawable.setBounds(rect);
                    gradientDrawable.draw(canvas);
                    canvas.restore();
                }
            } catch (Throwable exception) {
                if (BuildVars.DEBUG_PRIVATE_VERSION) {
                    throw new RuntimeException(exception);
                }
            }
        } else {
            if (cursorDrawn) {
                try {
                    canvas.save();
                    int voffsetCursor = 0;
                    if (getVerticalOffsetMethod != null) {
                        if ((getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
                            voffsetCursor = (int) getVerticalOffsetMethod.invoke(this, true);
                        }
                    } else {
                        if ((getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
                            voffsetCursor = getTotalPaddingTop() - getExtendedPaddingTop();
                        }
                    }
                    canvas.translate(getPaddingLeft(), getExtendedPaddingTop() + voffsetCursor);
                    Layout layout = getLayout();
                    int line = layout.getLineForOffset(getSelectionStart());
                    int lineCount = layout.getLineCount();
                    updateCursorPosition();
                    Rect bounds = gradientDrawable.getBounds();
                    rect.left = bounds.left;
                    rect.right = bounds.left + dp(cursorWidth);
                    rect.bottom = bounds.bottom;
                    rect.top = bounds.top;
                    if (lineSpacingExtra != 0 && line < lineCount - 1) {
                        rect.bottom -= lineSpacingExtra;
                    }
                    rect.top = rect.centerY() - cursorSize / 2;
                    rect.bottom = rect.top + cursorSize;
                    gradientDrawable.setBounds(rect);
                    gradientDrawable.draw(canvas);
                    canvas.restore();
                    cursorDrawn = false;
                } catch (Throwable exception) {
                    if (BuildVars.DEBUG_PRIVATE_VERSION) {
                        throw new RuntimeException(exception);
                    }
                }
            }
        }
        if (lineVisible && lineColor != 0) {
            int lineWidth = dp(1);
            boolean wasLineActive = lineActive;
            if (!TextUtils.isEmpty(errorText)) {
                linePaint.setColor(errorLineColor);
                lineWidth = dp(2);
                lineActive = false;
            } else if (isFocused()) {
                lineActive = true;
            } else {
                linePaint.setColor(lineColor);
                lineActive = false;
            }
            if (lineActive != wasLineActive) {
                lineLastUpdateTime = SystemClock.elapsedRealtime();
                lastLineActiveness = lineActiveness;
            }
            float t = (SystemClock.elapsedRealtime() - lineLastUpdateTime) / 150.0f;
            if (t < 1f || lineActive && lineActiveness != 1.0f || !lineActive && lineActiveness != 0.0f) {
                lineActiveness = AndroidUtilities.lerp(lastLineActiveness, lineActive ? 1 : 0, Math.max(0, Math.min(1, t)));
                if (t < 1f) {
                    invalidate();
                }
            }

            int scrollHeight = (getLayout() == null ? 0 : getLayout().getHeight()) - getMeasuredHeight() + getPaddingBottom() + getPaddingTop();
            int bottom = lineYFix ? getMeasuredHeight() - dp(2) : (int) lineY + getScrollY() + Math.min(Math.max(0, scrollHeight - getScrollY()), dp(2));
            int centerX = lastTouchX < 0 ? getMeasuredWidth() / 2 : lastTouchX,
                maxWidth = Math.max(centerX, getMeasuredWidth() - centerX) * 2;
            if (lineActiveness < 1f) {
                canvas.drawRect(getScrollX(), bottom - lineWidth, getScrollX() + getMeasuredWidth(), bottom, linePaint);
            }
            if (lineActiveness > 0f) {
                float lineActivenessT = CubicBezierInterpolator.EASE_BOTH.getInterpolation(lineActiveness);
                if (lineActive) {
                    activeLineWidth = maxWidth * lineActivenessT;
                }
                int lineThickness = (int) ((lineActive ? 1 : lineActivenessT) * dp(2));
                canvas.drawRect(
                    getScrollX() + Math.max(0, centerX - activeLineWidth / 2),
                    bottom - lineThickness,
                    getScrollX() + Math.min(centerX + activeLineWidth / 2, getMeasuredWidth()),
                    bottom,
                    activeLinePaint
                );
            }
        }
        if (ellipsizeByGradient) {
            canvas.save();
            canvas.translate(getScrollX(), 0);
            ellipsizePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            ellipsizeMatrix.reset();
            ellipsizeGradient.setLocalMatrix(ellipsizeMatrix);
            canvas.drawRect(getPaddingLeft() - ellipsizeWidth, 0, getPaddingLeft(), getHeight(), ellipsizePaint);

            ellipsizeMatrix.reset();
            ellipsizeMatrix.postScale(-1, 1, ellipsizeWidth / 2f, 0);
            ellipsizeMatrix.postTranslate(getWidth() - getPaddingRight(), 0);
            ellipsizeGradient.setLocalMatrix(ellipsizeMatrix);
            canvas.drawRect(getWidth() - getPaddingRight(), 0, getWidth() - getPaddingRight() + ellipsizeWidth, getHeight(), ellipsizePaint);
            canvas.restore();
            canvas.restore();
        }
        /*if (errorLayout != null) {
            canvas.save();
            canvas.translate(getScrollX(), lineY + AndroidUtilities.dp(3));
            errorLayout.draw(canvas);
            canvas.restore();
        }*/
    }

    public void setWindowView(View view) {
        windowView = view;
    }

    private boolean updateCursorPosition() {
        final Layout layout = getLayout();
        final int offset = forceCursorEnd ? layout.getText().length() : getSelectionStart();
        final int line = layout.getLineForOffset(offset);
        final int top = layout.getLineTop(line);
        final int bottom = layout.getLineTop(line + 1);
        updateCursorPosition(top, bottom, layout.getPrimaryHorizontal(offset));

        lastText = layout.getText();
        lastOffset = offset;
        return true;
    }

    private Rect mTempRect;

    private int clampHorizontalPosition(final Drawable drawable, float horizontal) {
        horizontal = Math.max(0.5f, horizontal - 0.5f);
        if (mTempRect == null) {
            mTempRect = new Rect();
        }
        int drawableWidth = 0;
        if (drawable != null) {
            drawable.getPadding(mTempRect);
            drawableWidth = drawable.getIntrinsicWidth();
        } else {
            mTempRect.setEmpty();
        }
        int scrollX = getScrollX();
        float horizontalDiff = horizontal - scrollX;
        int viewClippedWidth = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        final int left;
        if (horizontalDiff >= (viewClippedWidth - 1f)) {
            left = viewClippedWidth + scrollX - (drawableWidth - mTempRect.right);
        } else if (Math.abs(horizontalDiff) <= 1f || (TextUtils.isEmpty(getText()) && (1024 * 1024 - scrollX) <= (viewClippedWidth + 1f) && horizontal <= 1f)) {
            left = scrollX - mTempRect.left;
        } else {
            left = (int) horizontal - mTempRect.left;
        }
        return left;
    }

    private void updateCursorPosition(int top, int bottom, float horizontal) {
        final int left = clampHorizontalPosition(gradientDrawable, horizontal);
        final int width = dp(cursorWidth);
        gradientDrawable.setBounds(left, top - mTempRect.top, left + width, bottom + mTempRect.bottom);
    }

    @Override
    public float getLineSpacingExtra() {
        return super.getLineSpacingExtra();
    }

    private void cleanupFloatingActionModeViews() {
        if (floatingToolbar != null) {
            floatingToolbar.dismiss();
            floatingToolbar = null;
        }
        if (floatingToolbarPreDrawListener != null) {
            getViewTreeObserver().removeOnPreDrawListener(floatingToolbarPreDrawListener);
            floatingToolbarPreDrawListener = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        try {
            super.onAttachedToWindow();
        } catch (Exception e) {
            FileLog.e(e);
        }
        attachedToWindow = getRootView();
        AndroidUtilities.runOnUIThread(invalidateRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = null;
        AndroidUtilities.cancelRunOnUIThread(invalidateRunnable);
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback) {
        if (Build.VERSION.SDK_INT >= 23 && (windowView != null || attachedToWindow != null)) {
            if (floatingActionMode != null) {
                floatingActionMode.finish();
            }
            cleanupFloatingActionModeViews();
            floatingToolbar = new FloatingToolbar(getContext(), windowView != null ? windowView : attachedToWindow, getActionModeStyle(), getResourcesProvider());
            floatingToolbar.setOnPremiumLockClick(onPremiumMenuLockClickListener);
            floatingToolbar.setQuoteShowVisible(this::shouldShowQuoteButton);
            floatingActionMode = new FloatingActionMode(getContext(), new ActionModeCallback2Wrapper(callback), this, floatingToolbar);
            floatingToolbarPreDrawListener = () -> {
                if (floatingActionMode != null) {
                    floatingActionMode.updateViewLocationInWindow();
                }
                return true;
            };
            callback.onCreateActionMode(floatingActionMode, floatingActionMode.getMenu());
            extendActionMode(floatingActionMode, floatingActionMode.getMenu());
            floatingActionMode.invalidate();
            getViewTreeObserver().addOnPreDrawListener(floatingToolbarPreDrawListener);
            invalidate();
            return floatingActionMode;
        } else {
            return super.startActionMode(callback);
        }
    }

    private boolean shouldShowQuoteButton() {
        if (!hasSelection() || getSelectionStart() < 0 || getSelectionEnd() < 0 || getSelectionStart() == getSelectionEnd()) {
            return false;
        }
        Editable text = getText();
        if (text == null) {
            return false;
        }
        QuoteSpan.QuoteStyleSpan[] spans = ((Spanned) text).getSpans(getSelectionStart(), getSelectionEnd(), QuoteSpan.QuoteStyleSpan.class);
        return spans == null || spans.length == 0;
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback, int type) {
        if (Build.VERSION.SDK_INT >= 23 && (windowView != null || attachedToWindow != null)) {
            return startActionMode(callback);
        } else {
            return super.startActionMode(callback, type);
        }
    }

    protected void extendActionMode(ActionMode actionMode, Menu menu) {

    }

    protected int getActionModeStyle() {
        return FloatingToolbar.STYLE_THEME;
    }

    public void hideActionMode() {
        cleanupFloatingActionModeViews();
    }

    @Override
    public void setSelection(int start, int stop) {
        try {
            super.setSelection(start, stop);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void setSelection(int index) {
        try {
            super.setSelection(index);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.EditText");
        if (hintLayout != null) {
            if (getText().length() <= 0) {
                info.setText(hintLayout.getText());
            } else {
                AccessibilityNodeInfoCompat.wrap(info).setHintText(hintLayout.getText());
            }
        }
    }

    protected Theme.ResourcesProvider getResourcesProvider() {
        return null;
    }

    public void setHandlesColor(int color) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || XiaomiUtilities.isMIUI()) {
            return;
        }
        try {
            Drawable left = getTextSelectHandleLeft();
            left.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            setTextSelectHandleLeft(left);

            Drawable middle = getTextSelectHandle();
            middle.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            setTextSelectHandle(middle);

            Drawable right = getTextSelectHandleRight();
            right.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            setTextSelectHandleRight(right);
        } catch (Exception ignore) {}
    }

    private Runnable onPremiumMenuLockClickListener;
    public void setOnPremiumMenuLockClickListener(Runnable listener) {
        onPremiumMenuLockClickListener = listener;
    }

    public Runnable getOnPremiumMenuLockClickListener() {
        return onPremiumMenuLockClickListener;
    }

    public boolean ellipsizeByGradient;
    private Paint ellipsizePaint;
    private LinearGradient ellipsizeGradient;
    private Matrix ellipsizeMatrix;
    private int ellipsizeWidth;
    public void setEllipsizeByGradient(boolean value) {
        if (ellipsizeByGradient = value) {
            ellipsizeWidth = dp(12);
            ellipsizePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ellipsizeGradient = new LinearGradient(0, 0, ellipsizeWidth, 0, new int[] {0xFFFFFFFF, 0x00FFFFFF}, new float[] {0.4f, 1}, Shader.TileMode.CLAMP);
            ellipsizePaint.setShader(ellipsizeGradient);
            ellipsizeMatrix = new Matrix();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
    }
}
