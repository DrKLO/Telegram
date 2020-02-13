/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.AndroidUtilities;

public class SimpleTextView extends View implements Drawable.Callback {

    private Layout layout;
    private TextPaint textPaint;
    private int gravity = Gravity.LEFT | Gravity.TOP;
    private CharSequence text;
    private SpannableStringBuilder spannableStringBuilder;
    private Drawable leftDrawable;
    private Drawable rightDrawable;
    private float rightDrawableScale = 1.0f;
    private int drawablePadding = AndroidUtilities.dp(4);
    private int leftDrawableTopPadding;
    private int rightDrawableTopPadding;

    private Drawable wrapBackgroundDrawable;

    private boolean scrollNonFitText;
    private boolean textDoesNotFit;
    private float scrollingOffset;
    private long lastUpdateTime;
    private int currentScrollDelay;
    private Paint fadePaint;
    private Paint fadePaintBack;
    private int lastWidth;

    private int offsetX;
    private int offsetY;
    private int textWidth;
    private int totalWidth;
    private int textHeight;
    private boolean wasLayout;

    private int minWidth;

    private static final int PIXELS_PER_SECOND = 50;
    private static final int PIXELS_PER_SECOND_SLOW = 30;
    private static final int DIST_BETWEEN_SCROLLING_TEXT = 16;
    private static final int SCROLL_DELAY_MS = 500;
    private static final int SCROLL_SLOWDOWN_PX = 100;

    public SimpleTextView(Context context) {
        super(context);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setTextColor(int color) {
        textPaint.setColor(color);
        invalidate();
    }

    public void setLinkTextColor(int color) {
        textPaint.linkColor = color;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        wasLayout = false;
    }

    public void setTextSize(int size) {
        int newSize = AndroidUtilities.dp(size);
        if (newSize == textPaint.getTextSize()) {
            return;
        }
        textPaint.setTextSize(newSize);
        if (!recreateLayoutMaybe()) {
            invalidate();
        }
    }

    public void setScrollNonFitText(boolean value) {
        if (scrollNonFitText == value) {
            return;
        }
        scrollNonFitText = value;
        if (scrollNonFitText) {
            fadePaint = new Paint();
            LinearGradient gradient = new LinearGradient(0, 0, AndroidUtilities.dp(6), 0, new int[]{0xffffffff, 0}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
            fadePaint.setShader(gradient);
            fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            fadePaintBack = new Paint();
            gradient = new LinearGradient(0, 0, AndroidUtilities.dp(6), 0, new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
            fadePaintBack.setShader(gradient);
            fadePaintBack.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        }
        requestLayout();
    }

    public void setGravity(int value) {
        gravity = value;
    }

    public void setTypeface(Typeface typeface) {
        textPaint.setTypeface(typeface);
    }

    public int getSideDrawablesSize() {
        int size = 0;
        if (leftDrawable != null) {
            size += leftDrawable.getIntrinsicWidth() + drawablePadding;
        }
        if (rightDrawable != null) {
            int dw = (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale);
            size += dw + drawablePadding;
        }
        return size;
    }

    public Paint getPaint() {
        return textPaint;
    }

    private void calcOffset(int width) {
        if (layout.getLineCount() > 0) {
            textWidth = (int) Math.ceil(layout.getLineWidth(0));
            textHeight = layout.getLineBottom(0);

            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
                offsetX = (width - textWidth) / 2;
            } else if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT) {
                offsetX = -(int) layout.getLineLeft(0);
            } else if (layout.getLineLeft(0) == 0) {
                offsetX = width - textWidth;
            } else {
                offsetX = -AndroidUtilities.dp(8);
            }
            offsetX += getPaddingLeft();
            textDoesNotFit = textWidth > width;
        }
    }

    private boolean createLayout(int width) {
        if (text != null) {
            try {
                if (leftDrawable != null) {
                    width -= leftDrawable.getIntrinsicWidth();
                    width -= drawablePadding;
                }
                if (rightDrawable != null) {
                    int dw = (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale);
                    width -= dw;
                    width -= drawablePadding;
                }
                CharSequence string;
                if (scrollNonFitText) {
                    string = text;
                } else {
                    string = TextUtils.ellipsize(text, textPaint, width, TextUtils.TruncateAt.END);
                }
                /*if (layout != null && TextUtils.equals(layout.getText(), string)) {
                    calcOffset(width);
                    return false;
                }*/
                layout = new StaticLayout(string, 0, string.length(), textPaint, scrollNonFitText ? AndroidUtilities.dp(2000) : width + AndroidUtilities.dp(8), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                calcOffset(width);
            } catch (Exception ignore) {

            }
        } else {
            layout = null;
            textWidth = 0;
            textHeight = 0;
        }
        invalidate();
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (lastWidth != AndroidUtilities.displaySize.x) {
            lastWidth = AndroidUtilities.displaySize.x;
            scrollingOffset = 0;
            currentScrollDelay = SCROLL_DELAY_MS;
        }
        createLayout(width - getPaddingLeft() - getPaddingRight());

        int finalHeight;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            finalHeight = height;
        } else {
            finalHeight = textHeight;
        }
        setMeasuredDimension(width, finalHeight);

        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
            offsetY = (getMeasuredHeight() - textHeight) / 2 + getPaddingTop();
        } else {
            offsetY = getPaddingTop();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        wasLayout = true;
    }

    public int getTextWidth() {
        return textWidth;
    }

    public int getTextHeight() {
        return textHeight;
    }

    public void setLeftDrawableTopPadding(int value) {
        leftDrawableTopPadding = value;
    }

    public void setRightDrawableTopPadding(int value) {
        rightDrawableTopPadding = value;
    }

    public void setLeftDrawable(int resId) {
        setLeftDrawable(resId == 0 ? null : getContext().getResources().getDrawable(resId));
    }

    public void setRightDrawable(int resId) {
        setRightDrawable(resId == 0 ? null : getContext().getResources().getDrawable(resId));
    }

    public void setMinWidth(int width) {
        minWidth = width;
    }

    @Override
    public void setBackgroundDrawable(Drawable background) {
        wrapBackgroundDrawable = background;
    }

    @Override
    public Drawable getBackground() {
        if (wrapBackgroundDrawable != null) {
            return wrapBackgroundDrawable;
        }
        return super.getBackground();
    }

    public void setLeftDrawable(Drawable drawable) {
        if (leftDrawable == drawable) {
            return;
        }
        if (leftDrawable != null) {
            leftDrawable.setCallback(null);
        }
        leftDrawable = drawable;
        if (drawable != null) {
            drawable.setCallback(this);
        }
        if (!recreateLayoutMaybe()) {
            invalidate();
        }
    }

    public Drawable getRightDrawable() {
        return rightDrawable;
    }

    public void setRightDrawable(Drawable drawable) {
        if (rightDrawable == drawable) {
            return;
        }
        if (rightDrawable != null) {
            rightDrawable.setCallback(null);
        }
        rightDrawable = drawable;
        if (drawable != null) {
            drawable.setCallback(this);
        }
        if (!recreateLayoutMaybe()) {
            invalidate();
        }
    }

    public void setRightDrawableScale(float scale) {
        rightDrawableScale = scale;
    }

    public void setSideDrawablesColor(int color) {
        Theme.setDrawableColor(rightDrawable, color);
        Theme.setDrawableColor(leftDrawable, color);
    }

    public boolean setText(CharSequence value) {
        return setText(value, false);
    }

    public boolean setText(CharSequence value, boolean force) {
        if (text == null && value == null || !force && text != null && text.equals(value)) {
            return false;
        }
        text = value;
        scrollingOffset = 0;
        currentScrollDelay = SCROLL_DELAY_MS;
        recreateLayoutMaybe();
        return true;
    }

    public void setDrawablePadding(int value) {
        if (drawablePadding == value) {
            return;
        }
        drawablePadding = value;
        if (!recreateLayoutMaybe()) {
            invalidate();
        }
    }

    private boolean recreateLayoutMaybe() {
        if (wasLayout && getMeasuredHeight() != 0) {
            boolean result = createLayout(getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                offsetY = (getMeasuredHeight() - textHeight) / 2 + getPaddingTop();
            } else {
                offsetY = getPaddingTop();
            }
            return result;
        } else {
            requestLayout();
        }
        return true;
    }

    public CharSequence getText() {
        if (text == null) {
            return "";
        }
        return text;
    }

    public int getTextStartX() {
        if (layout == null) {
            return 0;
        }
        int textOffsetX = 0;
        if (leftDrawable != null) {
            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT) {
                textOffsetX += drawablePadding + leftDrawable.getIntrinsicWidth();
            }
        }
        return (int) getX() + offsetX + textOffsetX;
    }

    public TextPaint getTextPaint() {
        return textPaint;
    }

    public int getTextStartY() {
        if (layout == null) {
            return 0;
        }
        return (int) getY();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int textOffsetX = 0;

        boolean fade = scrollNonFitText && (textDoesNotFit || scrollingOffset != 0);
        if (fade) {
            canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
        }

        totalWidth = textWidth;
        if (leftDrawable != null) {
            int x = (int) -scrollingOffset;
            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
                x += offsetX;
            }
            int y;
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                y = (getMeasuredHeight() - leftDrawable.getIntrinsicHeight()) / 2 + leftDrawableTopPadding;
            } else {
                y = (textHeight - leftDrawable.getIntrinsicHeight()) / 2 + leftDrawableTopPadding;
            }
            leftDrawable.setBounds(x, y, x + leftDrawable.getIntrinsicWidth(), y + leftDrawable.getIntrinsicHeight());
            leftDrawable.draw(canvas);
            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT || (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
                textOffsetX += drawablePadding + leftDrawable.getIntrinsicWidth();
            }
            totalWidth += drawablePadding + leftDrawable.getIntrinsicWidth();
        }
        if (rightDrawable != null) {
            int x = textOffsetX + textWidth + drawablePadding + (int) -scrollingOffset;
            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
                x += offsetX;
            } else if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) {
                x += offsetX;
            }
            int dw = (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale);
            int dh = (int) (rightDrawable.getIntrinsicHeight() * rightDrawableScale);
            int y = (textHeight - dh) / 2 + rightDrawableTopPadding;
            rightDrawable.setBounds(x, y, x + dw, y + dh);
            rightDrawable.draw(canvas);
            totalWidth += drawablePadding + dw;
        }
        int nextScrollX = totalWidth + AndroidUtilities.dp(DIST_BETWEEN_SCROLLING_TEXT);

        if (scrollingOffset != 0) {
            if (leftDrawable != null) {
                int x = (int) -scrollingOffset + nextScrollX;
                int y = (textHeight - leftDrawable.getIntrinsicHeight()) / 2 + leftDrawableTopPadding;
                leftDrawable.setBounds(x, y, x + leftDrawable.getIntrinsicWidth(), y + leftDrawable.getIntrinsicHeight());
                leftDrawable.draw(canvas);
            }
            if (rightDrawable != null) {
                int x = textOffsetX + textWidth + drawablePadding + (int) -scrollingOffset + nextScrollX;
                int y = (textHeight - rightDrawable.getIntrinsicHeight()) / 2 + rightDrawableTopPadding;
                rightDrawable.setBounds(x, y, x + rightDrawable.getIntrinsicWidth(), y + rightDrawable.getIntrinsicHeight());
                rightDrawable.draw(canvas);
            }
        }

        if (layout != null) {
            if (wrapBackgroundDrawable != null) {
                int cx = (int) (offsetX + textOffsetX - scrollingOffset) + textWidth / 2;
                int w = Math.max(textWidth + getPaddingLeft() + getPaddingRight(), minWidth);
                int x = cx - w / 2;
                wrapBackgroundDrawable.setBounds(x, 0, x + w, getMeasuredHeight());
                wrapBackgroundDrawable.draw(canvas);
            }
            if (offsetX + textOffsetX != 0 || offsetY != 0 || scrollingOffset != 0) {
                canvas.save();
                canvas.translate(offsetX + textOffsetX - scrollingOffset, offsetY);
                if (scrollingOffset != 0) {
                    //canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
                }
            }
            layout.draw(canvas);
            if (scrollingOffset != 0) {
                canvas.translate(nextScrollX, 0);
                layout.draw(canvas);
            }
            if (offsetX + textOffsetX != 0 || offsetY != 0 || scrollingOffset != 0) {
                canvas.restore();
            }
            if (fade) {
                if (scrollingOffset < AndroidUtilities.dp(10)) {
                    fadePaint.setAlpha((int) (255 * (scrollingOffset / AndroidUtilities.dp(10))));
                } else if (scrollingOffset > totalWidth + AndroidUtilities.dp(DIST_BETWEEN_SCROLLING_TEXT) - AndroidUtilities.dp(10)) {
                    float dist = scrollingOffset - (totalWidth + AndroidUtilities.dp(DIST_BETWEEN_SCROLLING_TEXT) - AndroidUtilities.dp(10));
                    fadePaint.setAlpha((int) (255 * (1.0f - dist / AndroidUtilities.dp(10))));
                } else {
                    fadePaint.setAlpha(255);
                }
                canvas.drawRect(0, 0, AndroidUtilities.dp(6), getMeasuredHeight(), fadePaint);
                canvas.save();
                canvas.translate(getMeasuredWidth() - AndroidUtilities.dp(6), 0);
                canvas.drawRect(0, 0, AndroidUtilities.dp(6), getMeasuredHeight(), fadePaintBack);
                canvas.restore();
            }
            updateScrollAnimation();
        }
    }

    private void updateScrollAnimation() {
        if (!scrollNonFitText || !textDoesNotFit && scrollingOffset == 0) {
            return;
        }
        long newUpdateTime = SystemClock.elapsedRealtime();
        long dt = newUpdateTime - lastUpdateTime;
        if (dt > 17) {
            dt = 17;
        }
        if (currentScrollDelay > 0) {
            currentScrollDelay -= dt;
        } else {
            int totalDistance = totalWidth + AndroidUtilities.dp(DIST_BETWEEN_SCROLLING_TEXT);
            float pixelsPerSecond;
            if (scrollingOffset < AndroidUtilities.dp(SCROLL_SLOWDOWN_PX)) {
                pixelsPerSecond = PIXELS_PER_SECOND_SLOW + (PIXELS_PER_SECOND - PIXELS_PER_SECOND_SLOW) * (scrollingOffset / AndroidUtilities.dp(SCROLL_SLOWDOWN_PX));
            } else if (scrollingOffset >= totalDistance - AndroidUtilities.dp(SCROLL_SLOWDOWN_PX)) {
                float dist = scrollingOffset - (totalDistance - AndroidUtilities.dp(SCROLL_SLOWDOWN_PX));
                pixelsPerSecond = PIXELS_PER_SECOND - (PIXELS_PER_SECOND - PIXELS_PER_SECOND_SLOW) * (dist / AndroidUtilities.dp(SCROLL_SLOWDOWN_PX));
            } else {
                pixelsPerSecond = PIXELS_PER_SECOND;
            }
            scrollingOffset += dt / 1000.0f * AndroidUtilities.dp(pixelsPerSecond);
            lastUpdateTime = newUpdateTime;
            if (scrollingOffset > totalDistance) {
                scrollingOffset = 0;
                currentScrollDelay = SCROLL_DELAY_MS;
            }
        }
        invalidate();
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        if (who == leftDrawable) {
            invalidate(leftDrawable.getBounds());
        } else if (who == rightDrawable) {
            invalidate(rightDrawable.getBounds());
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setVisibleToUser(true);
        info.setClassName("android.widget.TextView");
        info.setText(text);
    }
}
