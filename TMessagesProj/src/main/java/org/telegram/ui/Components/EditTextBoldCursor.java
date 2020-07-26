/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import androidx.annotation.Keep;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.FloatingActionMode;
import org.telegram.ui.ActionBar.FloatingToolbar;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class EditTextBoldCursor extends EditText {

    private static Field mEditor;
    private static Field mShowCursorField;
    private static Field mScrollYField;
    private static Method getVerticalOffsetMethod;
    private static Class editorClass;
    private static Field mCursorDrawableResField;

    private Drawable mCursorDrawable;
    private Object editor;

    private GradientDrawable gradientDrawable;

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
    private TextPaint errorPaint;

    private int cursorSize;
    private int ignoreTopCount;
    private int ignoreBottomCount;
    private int scrollY;
    private float lineSpacingExtra;
    private Rect rect = new Rect();
    private StaticLayout hintLayout;
    private StaticLayout errorLayout;
    private CharSequence errorText;
    private int hintColor;
    private int headerHintColor;
    private boolean hintVisible = true;
    private float hintAlpha = 1.0f;
    private long lastUpdateTime;
    private boolean allowDrawCursor = true;
    private float cursorWidth = 2.0f;
    private boolean supportRtlHint;

    private int lineColor;
    private int activeLineColor;
    private int errorLineColor;
    private float lineY;

    private boolean nextSetTextAnimated;
    private boolean transformHintToHeader;
    private boolean currentDrawHintAsHeader;
    private AnimatorSet headerTransformAnimation;
    private float headerAnimationProgress;

    private boolean fixed;
    private ViewTreeObserver.OnPreDrawListener listenerFixer;

    private FloatingToolbar floatingToolbar;
    private FloatingActionMode floatingActionMode;
    private ViewTreeObserver.OnPreDrawListener floatingToolbarPreDrawListener;
    private View windowView;
    private View attachedToWindow;

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

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public int getAutofillType() {
        return AUTOFILL_TYPE_NONE;
    }

    @SuppressLint("PrivateApi")
    private void init() {
        linePaint = new Paint();
        errorPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        errorPaint.setTextSize(AndroidUtilities.dp(11));
        if (Build.VERSION.SDK_INT >= 26) {
            setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }

        try {
            if (mScrollYField == null) {
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
                mShowCursorField = editorClass.getDeclaredField("mShowCursor");
                mShowCursorField.setAccessible(true);
                getVerticalOffsetMethod = TextView.class.getDeclaredMethod("getVerticalOffset", boolean.class);
                getVerticalOffsetMethod.setAccessible(true);
                mShowCursorField = editorClass.getDeclaredField("mShowCursor");
                mShowCursorField.setAccessible(true);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        try {
            gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xff54a1db, 0xff54a1db});
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
        cursorSize = AndroidUtilities.dp(24);
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

    public void setCursorWidth(float width) {
        cursorWidth = width;
    }

    public void setCursorColor(int color) {
        gradientDrawable.setColor(color);
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

    public void setLineColors(int color, int active, int error) {
        lineColor = color;
        activeLineColor = active;
        errorLineColor = error;
        errorPaint.setColor(errorLineColor);
        invalidate();
    }

    public void setHintVisible(boolean value) {
        if (hintVisible == value) {
            return;
        }
        lastUpdateTime = System.currentTimeMillis();
        hintVisible = value;
        invalidate();
    }

    public void setHintColor(int value) {
        hintColor = value;
        invalidate();
    }

    public void setHeaderHintColor(int value) {
        headerHintColor = value;
        invalidate();
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

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        return super.requestFocus(direction, previouslyFocusedRect);
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
        if (hintLayout != null) {
            lineY = (getMeasuredHeight() - hintLayout.getHeight()) / 2.0f + hintLayout.getHeight() + AndroidUtilities.dp(6);
        }
    }

    public void setHintText(CharSequence text) {
        if (text == null) {
            text = "";
        }
        hintLayout = new StaticLayout(text, getPaint(), AndroidUtilities.dp(1000), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    }

    public Layout getHintLayoutEx() {
        return hintLayout;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
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

    @Override
    protected void onDraw(Canvas canvas) {
        int topPadding = getExtendedPaddingTop();
        scrollY = Integer.MAX_VALUE;
        try {
            scrollY = mScrollYField.getInt(this);
            mScrollYField.set(this, 0);
        } catch (Exception e) {
            //
        }
        ignoreTopCount = 1;
        ignoreBottomCount = 1;
        canvas.save();
        canvas.translate(0, topPadding);
        try {
            super.onDraw(canvas);
        } catch (Exception e) {
            //
        }
        if (scrollY != Integer.MAX_VALUE) {
            try {
                mScrollYField.set(this, scrollY);
            } catch (Exception e) {
                //
            }
        }
        canvas.restore();
        if ((length() == 0 || transformHintToHeader) && hintLayout != null && (hintVisible || hintAlpha != 0)) {
            if (hintVisible && hintAlpha != 1.0f || !hintVisible && hintAlpha != 0.0f) {
                long newTime = System.currentTimeMillis();
                long dt = newTime - lastUpdateTime;
                if (dt < 0 || dt > 17) {
                    dt = 17;
                }
                lastUpdateTime = newTime;
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
                canvas.translate(left + getScrollX() + offset, lineY - hintLayout.getHeight() - AndroidUtilities.dp(6));
            } else {
                canvas.translate(left + getScrollX(), lineY - hintLayout.getHeight() - AndroidUtilities.dp(6));
            }
            if (transformHintToHeader) {
                float scale = 1.0f - 0.3f * headerAnimationProgress;
                float translation = -AndroidUtilities.dp(22) * headerAnimationProgress;
                int rF = Color.red(headerHintColor);
                int gF = Color.green(headerHintColor);
                int bF = Color.blue(headerHintColor);
                int aF = Color.alpha(headerHintColor);
                int rS = Color.red(hintColor);
                int gS = Color.green(hintColor);
                int bS = Color.blue(hintColor);
                int aS = Color.alpha(hintColor);

                if (supportRtlHint && LocaleController.isRTL) {
                    canvas.translate((hintWidth + lineLeft) - (hintWidth + lineLeft) * scale, 0);
                } else if (lineLeft != 0) {
                    canvas.translate(lineLeft * (1.0f - scale), 0);
                }
                canvas.scale(scale, scale);
                canvas.translate(0, translation);
                getPaint().setColor(Color.argb((int) (aS + (aF - aS) * headerAnimationProgress), (int) (rS + (rF - rS) * headerAnimationProgress), (int) (gS + (gF - gS) * headerAnimationProgress), (int) (bS + (bF - bS) * headerAnimationProgress)));
            } else {
                getPaint().setColor(hintColor);
                getPaint().setAlpha((int) (255 * hintAlpha * (Color.alpha(hintColor) / 255.0f)));
            }
            hintLayout.draw(canvas);
            getPaint().setColor(oldColor);
            canvas.restore();
        }
        try {
            if (allowDrawCursor && mShowCursorField != null) {
                long mShowCursor = mShowCursorField.getLong(editor);
                boolean showCursor = (SystemClock.uptimeMillis() - mShowCursor) % (2 * 500) < 500 && isFocused();
                if (showCursor) {
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
                    rect.right = bounds.left + AndroidUtilities.dp(cursorWidth);
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
            }
        } catch (Throwable ignore) {

        }
        if (lineColor != 0 && hintLayout != null) {
            int h;
            if (!TextUtils.isEmpty(errorText)) {
                linePaint.setColor(errorLineColor);
                h = AndroidUtilities.dp(2);
            } else if (isFocused()) {
                linePaint.setColor(activeLineColor);
                h = AndroidUtilities.dp(2);
            } else {
                linePaint.setColor(lineColor);
                h = AndroidUtilities.dp(1);
            }
            canvas.drawRect(getScrollX(), (int) lineY, getScrollX() + getMeasuredWidth(), lineY + h, linePaint);
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
        final int offset = getSelectionStart();
        final int line = layout.getLineForOffset(offset);
        final int top = layout.getLineTop(line);
        final int bottom = layout.getLineTop(line + 1);
        updateCursorPosition(top, bottom, layout.getPrimaryHorizontal(offset));
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
        final int width = AndroidUtilities.dp(cursorWidth);
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
        super.onAttachedToWindow();
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
            floatingToolbar = new FloatingToolbar(getContext(), windowView != null ? windowView : attachedToWindow, getActionModeStyle());
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
            AccessibilityNodeInfoCompat.wrap(info).setHintText(hintLayout.getText());
        }
    }
}
