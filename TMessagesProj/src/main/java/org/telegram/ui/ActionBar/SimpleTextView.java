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
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.EmptyStubSpan;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SimpleTextView extends View implements Drawable.Callback {

    private Layout layout;
    private Layout firstLineLayout;
    private Layout fullLayout;
    private Layout partLayout;
    private TextPaint textPaint;
    private int gravity = Gravity.LEFT | Gravity.TOP;
    private int maxLines = 1;
    private CharSequence text;
    private SpannableStringBuilder spannableStringBuilder;
    private Drawable leftDrawable;
    private Drawable rightDrawable;
    private Drawable rightDrawable2;
    private Drawable replacedDrawable;
    private String replacedText;
    private int replacingDrawableTextIndex;
    private float replacingDrawableTextOffset;
    private float rightDrawableScale = 1.0f;
    private int drawablePadding = AndroidUtilities.dp(4);
    private int leftDrawableTopPadding;
    private int rightDrawableTopPadding;
    private boolean buildFullLayout;
    private float fullAlpha;
    private boolean widthWrapContent;

    private Drawable wrapBackgroundDrawable;

    private boolean scrollNonFitText;
    private boolean textDoesNotFit;
    private float scrollingOffset;
    private long lastUpdateTime;
    private int currentScrollDelay;
    private Paint fadePaint;
    private Paint fadePaintBack;
    private Paint fadeEllpsizePaint;
    private int fadeEllpsizePaintWidth;
    private int lastWidth;

    private int offsetX;
    private int offsetY;
    private int textWidth;
    private int totalWidth;
    private int textHeight;
    public int rightDrawableX;
    public int rightDrawableY;
    private boolean wasLayout;

    private boolean rightDrawableOutside;
    private boolean rightDrawableInside;
    private boolean ellipsizeByGradient, ellipsizeByGradientLeft;
    private Boolean forceEllipsizeByGradientLeft;
    private int ellipsizeByGradientWidthDp = 16;
    private int paddingRight;

    private int minWidth;

    private static final int PIXELS_PER_SECOND = 50;
    private static final int PIXELS_PER_SECOND_SLOW = 30;
    private static final int DIST_BETWEEN_SCROLLING_TEXT = 16;
    private static final int SCROLL_DELAY_MS = 500;
    private static final int SCROLL_SLOWDOWN_PX = 100;
    private int fullLayoutAdditionalWidth;
    private int fullLayoutLeftOffset;
    private float fullLayoutLeftCharactersOffset;

    private int minusWidth;
    private int fullTextMaxLines = 3;

    private List<SpoilerEffect> spoilers = new ArrayList<>();
    private Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private Path path = new Path();
    private boolean usaAlphaForEmoji;
    private boolean canHideRightDrawable;
    private boolean rightDrawableHidden;
    private OnClickListener rightDrawableOnClickListener;
    private boolean maybeClick;
    private float touchDownX, touchDownY;

    private AnimatedEmojiSpan.EmojiGroupedSpans emojiStack;
    private boolean attachedToWindow;

    private Layout.Alignment mAlignment = Layout.Alignment.ALIGN_NORMAL;

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
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        emojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, emojiStack, layout);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        AnimatedEmojiSpan.release(this, emojiStack);
        wasLayout = false;
    }

    public void setTextSize(int sizeInDp) {
        int newSize = AndroidUtilities.dp(sizeInDp);
        if (newSize == textPaint.getTextSize()) {
            return;
        }
        textPaint.setTextSize(newSize);
        if (!recreateLayoutMaybe()) {
            invalidate();
        }
    }

    public void setBuildFullLayout(boolean value) {
        buildFullLayout = value;
    }

    public void setFullAlpha(float value) {
        fullAlpha = value;
        invalidate();
    }

    public float getFullAlpha() {
        return fullAlpha;
    }

    public void setScrollNonFitText(boolean value) {
        if (scrollNonFitText == value) {
            return;
        }
        scrollNonFitText = value;
        updateFadePaints();
        requestLayout();
    }

    public void setEllipsizeByGradient(boolean value) {
        setEllipsizeByGradient(value, null);
    }

    public void setEllipsizeByGradient(int value) {
        setEllipsizeByGradient(value, null);
    }

    public void setEllipsizeByGradient(boolean value, Boolean forceLeft) {
        if (scrollNonFitText == value) {
            return;
        }
        ellipsizeByGradient = value;
        this.forceEllipsizeByGradientLeft = forceLeft;
        updateFadePaints();
    }

    public void setEllipsizeByGradient(int value, Boolean forceLeft) {
        setEllipsizeByGradient(true, forceLeft);
        ellipsizeByGradientWidthDp = value;
        updateFadePaints();
    }

    public void setWidthWrapContent(boolean value) {
        widthWrapContent = value;
    }

    private void updateFadePaints() {
        if ((fadePaint == null || fadePaintBack == null) && scrollNonFitText) {
            fadePaint = new Paint();
            fadePaint.setShader(new LinearGradient(0, 0, AndroidUtilities.dp(6), 0, new int[]{0xffffffff, 0}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            fadePaintBack = new Paint();
            fadePaintBack.setShader(new LinearGradient(0, 0, AndroidUtilities.dp(6), 0, new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            fadePaintBack.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        }
        boolean ellipsizeLeft;
        if (forceEllipsizeByGradientLeft != null) {
            ellipsizeLeft = forceEllipsizeByGradientLeft;
        } else {
            ellipsizeLeft = false;
//            ellipsizeLeft = getAlignment() == Layout.Alignment.ALIGN_NORMAL && LocaleController.isRTL || getAlignment() == Layout.Alignment.ALIGN_OPPOSITE && !LocaleController.isRTL;
        }
        if ((fadeEllpsizePaint == null || fadeEllpsizePaintWidth != AndroidUtilities.dp(ellipsizeByGradientWidthDp) || ellipsizeByGradientLeft != ellipsizeLeft) && ellipsizeByGradient) {
            if (fadeEllpsizePaint == null) {
                fadeEllpsizePaint = new Paint();
            }
            ellipsizeByGradientLeft = ellipsizeLeft;
            if (ellipsizeByGradientLeft) {
                fadeEllpsizePaint.setShader(new LinearGradient(0, 0, fadeEllpsizePaintWidth = AndroidUtilities.dp(ellipsizeByGradientWidthDp), 0, new int[]{0xffffffff, 0}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            } else {
                fadeEllpsizePaint.setShader(new LinearGradient(0, 0, fadeEllpsizePaintWidth = AndroidUtilities.dp(ellipsizeByGradientWidthDp), 0, new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            }
            fadeEllpsizePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        }
    }

    public void setMaxLines(int value) {
        maxLines = value;
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
        if (rightDrawable2 != null) {
            int dw = (int) (rightDrawable2.getIntrinsicWidth() * rightDrawableScale);
            size += dw + drawablePadding;
        }
        return size;
    }

    public Paint getPaint() {
        return textPaint;
    }

    private void calcOffset(int width) {
        if (layout == null) {
            return;
        }
        if (layout.getLineCount() > 0) {
            textWidth = (int) Math.ceil(layout.getLineWidth(0));
            if (fullLayout != null) {
                textHeight = fullLayout.getLineBottom(fullLayout.getLineCount() - 1);
            } else if (maxLines > 1 && layout.getLineCount() > 0) {
                textHeight = layout.getLineBottom(layout.getLineCount() - 1);
            } else {
                textHeight = layout.getLineBottom(0);
            }

            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
                offsetX = (width - textWidth) / 2 - (int) layout.getLineLeft(0);
            } else if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT) {
                if (firstLineLayout != null) {
                    offsetX = -(int) firstLineLayout.getLineLeft(0);
                } else {
                    offsetX = -(int) layout.getLineLeft(0);
                }
            } else if (layout.getLineLeft(0) == 0) {
                if (firstLineLayout != null) {
                    offsetX = (int) (width - firstLineLayout.getLineWidth(0));
                } else {
                    offsetX = width - textWidth;
                }
            } else {
                offsetX = -AndroidUtilities.dp(8);
            }
            offsetX += getPaddingLeft();
            int rightDrawableWidth = 0;
            if (rightDrawableInside) {
                if (rightDrawable != null && !rightDrawableOutside) {
                    rightDrawableWidth += (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale);
                }
                if (rightDrawable2 != null && !rightDrawableOutside) {
                    rightDrawableWidth += (int) (rightDrawable2.getIntrinsicWidth() * rightDrawableScale);
                }
            }
            textDoesNotFit = textWidth + rightDrawableWidth > (width - paddingRight);

            if (fullLayout != null && fullLayoutAdditionalWidth > 0) {
                fullLayoutLeftCharactersOffset = fullLayout.getPrimaryHorizontal(0) - firstLineLayout.getPrimaryHorizontal(0);
            }
        }

        if (replacingDrawableTextIndex >= 0) {
            replacingDrawableTextOffset = layout.getPrimaryHorizontal(replacingDrawableTextIndex);
        } else {
            replacingDrawableTextOffset = 0;
        }
    }

    protected boolean createLayout(int width) {
        CharSequence text = this.text;
        replacingDrawableTextIndex = -1;
        rightDrawableHidden = false;
        if (text != null) {
            try {
                if (leftDrawable != null) {
                    width -= leftDrawable.getIntrinsicWidth();
                    width -= drawablePadding;
                }
                int rightDrawableWidth = 0;
                if (!rightDrawableInside) {
                    if (rightDrawable != null && !rightDrawableOutside) {
                        rightDrawableWidth += (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale);
                        width -= rightDrawableWidth;
                        width -= drawablePadding;
                    }
                    if (rightDrawable2 != null && !rightDrawableOutside) {
                        rightDrawableWidth += (int) (rightDrawable2.getIntrinsicWidth() * rightDrawableScale);
                        width -= rightDrawableWidth;
                        width -= drawablePadding;
                    }
                }
                if (replacedText != null && replacedDrawable != null) {
                    replacingDrawableTextIndex = text.toString().indexOf(replacedText);
                    if (replacingDrawableTextIndex >= 0) {
                        SpannableStringBuilder builder = SpannableStringBuilder.valueOf(text);
                        builder.setSpan(new DialogCell.FixedWidthSpan(replacedDrawable.getIntrinsicWidth()), replacingDrawableTextIndex, replacingDrawableTextIndex + replacedText.length(), 0);
                        text = builder;
                    } else {
                        width -= replacedDrawable.getIntrinsicWidth();
                        width -= drawablePadding;
                    }
                }
                if (canHideRightDrawable && rightDrawableWidth != 0 && !rightDrawableOutside) {
                    CharSequence string = TextUtils.ellipsize(text, textPaint, width, TextUtils.TruncateAt.END);
                    if (!text.equals(string)) {
                        rightDrawableHidden = true;
                        width += rightDrawableWidth;
                        width += drawablePadding;
                    }
                }
                if (buildFullLayout) {
                    CharSequence string = text;
                    if (!ellipsizeByGradient) {
                        string = TextUtils.ellipsize(string, textPaint, width, TextUtils.TruncateAt.END);
                    }
                    if (!ellipsizeByGradient && !string.equals(text)) {
                        fullLayout = StaticLayoutEx.createStaticLayout(text, textPaint, width, getAlignment(), 1.0f, 0.0f, false, TextUtils.TruncateAt.END, width, fullTextMaxLines, false);
                        if (fullLayout != null) {
                            int end = fullLayout.getLineEnd(0);
                            int start = fullLayout.getLineStart(1);
                            CharSequence substr = text.subSequence(0, end);
                            SpannableStringBuilder full = SpannableStringBuilder.valueOf(text);
                            full.setSpan(new EmptyStubSpan(), 0, start, 0);
                            CharSequence part;
                            if (end < string.length()) {
                                part = string.subSequence(end, string.length());
                            } else {
                                part = "…";
                            }
                            firstLineLayout = new StaticLayout(string, 0, string.length(), textPaint, scrollNonFitText ? AndroidUtilities.dp(2000) : width + AndroidUtilities.dp(8), getAlignment(), 1.0f, 0.0f, false);
                            layout = new StaticLayout(substr, 0, substr.length(), textPaint, scrollNonFitText ? AndroidUtilities.dp(2000) : width + AndroidUtilities.dp(8), getAlignment(), 1.0f, 0.0f, false);
                            if (layout.getLineLeft(0) != 0) {
                                part = "\u200F" + part;
                            }
                            partLayout = new StaticLayout(part, 0, part.length(), textPaint, scrollNonFitText ? AndroidUtilities.dp(2000) : width + AndroidUtilities.dp(8), getAlignment(), 1.0f, 0.0f, false);
                            fullLayout = StaticLayoutEx.createStaticLayout(full, textPaint, width + AndroidUtilities.dp(8) + fullLayoutAdditionalWidth, getAlignment(), 1.0f, 0.0f, false, TextUtils.TruncateAt.END, width + fullLayoutAdditionalWidth, fullTextMaxLines, false);
                        }
                    } else {
                        layout = new StaticLayout(string, 0, string.length(), textPaint, scrollNonFitText || ellipsizeByGradient ? AndroidUtilities.dp(2000) : width + AndroidUtilities.dp(8), getAlignment(), 1.0f, 0.0f, false);
                        fullLayout = null;
                        partLayout = null;
                        firstLineLayout = null;
                    }
                } else if (maxLines > 1) {
                    layout = StaticLayoutEx.createStaticLayout(text, textPaint, width, getAlignment(), 1.0f, 0.0f, false, TextUtils.TruncateAt.END, width, maxLines, false);
                } else {
                    CharSequence string;
                    if (scrollNonFitText || ellipsizeByGradient) {
                        string = text;
                    } else {
                        string = TextUtils.ellipsize(text, textPaint, width, TextUtils.TruncateAt.END);
                    }
                    /*if (layout != null && TextUtils.equals(layout.getText(), string)) {
                        calcOffset(width);
                        return false;
                    }*/
                    layout = new StaticLayout(string, 0, string.length(), textPaint, scrollNonFitText || ellipsizeByGradient ? AndroidUtilities.dp(2000) : width + AndroidUtilities.dp(8), getAlignment(), 1.0f, 0.0f, false);
                }

                spoilersPool.addAll(spoilers);
                spoilers.clear();
                if (layout != null && layout.getText() instanceof Spannable) {
                    SpoilerEffect.addSpoilers(this, layout, -2, -2, spoilersPool, spoilers);
                }
                calcOffset(width);
            } catch (Exception ignore) {

            }
        } else {
            layout = null;
            textWidth = 0;
            textHeight = 0;
        }
        AnimatedEmojiSpan.release(this, emojiStack);
        if (attachedToWindow) {
            emojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, emojiStack, layout);
        }
        invalidate();
        return true;
    }

    public void setAlignment(Layout.Alignment alignment) {
        mAlignment = alignment;
        requestLayout();
    }

    private Layout.Alignment getAlignment() {
        return mAlignment;
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
        createLayout(width - getPaddingLeft() - getPaddingRight() - minusWidth - (rightDrawableOutside && rightDrawable != null ? rightDrawable.getIntrinsicWidth() + drawablePadding : 0) - (rightDrawableOutside && rightDrawable2 != null ? rightDrawable2.getIntrinsicWidth() + drawablePadding : 0));

        int finalHeight;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            finalHeight = height;
        } else {
            finalHeight = getPaddingTop() + textHeight + getPaddingBottom();
        }
        if (widthWrapContent) {
//            textWidth = (int) Math.ceil(layout.getLineWidth(0));
            width = Math.min(width, getPaddingLeft() + textWidth + getPaddingRight() + minusWidth + (rightDrawableOutside && rightDrawable != null ? rightDrawable.getIntrinsicWidth() + drawablePadding : 0) + (rightDrawableOutside && rightDrawable2 != null ? rightDrawable2.getIntrinsicWidth() + drawablePadding : 0));
        }
        setMeasuredDimension(width, finalHeight);

        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
            offsetY = getPaddingTop() + (getMeasuredHeight() - getPaddingTop() - getPaddingBottom() - textHeight) / 2;
        } else {
            offsetY = getPaddingTop();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        wasLayout = true;
    }

    public int getTextWidth() {
        return textWidth + (rightDrawableInside ? (rightDrawable != null ? (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale) : 0) + (rightDrawable2 != null ? (int) (rightDrawable2.getIntrinsicWidth() * rightDrawableScale) : 0) : 0);
    }

    public int getRightDrawableWidth() {
        if (rightDrawable == null)
            return 0;
        return (int) (drawablePadding + rightDrawable.getIntrinsicWidth() * rightDrawableScale);
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

    public Drawable getLeftDrawable() {
        return leftDrawable;
    }

    public void setRightDrawable(int resId) {
        setRightDrawable(resId == 0 ? null : getContext().getResources().getDrawable(resId));
    }

    public void setMinWidth(int width) {
        minWidth = width;
    }

    @Override
    public void setBackgroundDrawable(Drawable background) {
        if (maxLines > 1) {
            super.setBackgroundDrawable(background);
            return;
        }
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

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == rightDrawable || who == rightDrawable2 || who == leftDrawable || super.verifyDrawable(who);
    }

    public void replaceTextWithDrawable(Drawable drawable, String replacedText) {
        if (replacedDrawable == drawable) {
            return;
        }
        if (replacedDrawable != null) {
            replacedDrawable.setCallback(null);
        }
        replacedDrawable = drawable;
        if (drawable != null) {
            drawable.setCallback(this);
        }
        if (!recreateLayoutMaybe()) {
            invalidate();
        }
        this.replacedText = replacedText;
    }

    public void setMinusWidth(int value) {
        if (value == minusWidth) {
            return;
        }
        minusWidth = value;
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

    public void setRightDrawable2(Drawable drawable) {
        if (rightDrawable2 == drawable) {
            return;
        }
        if (rightDrawable2 != null) {
            rightDrawable2.setCallback(null);
        }
        rightDrawable2 = drawable;
        if (drawable != null) {
            drawable.setCallback(this);
        }
        if (!recreateLayoutMaybe()) {
            invalidate();
        }
    }

    public Drawable getRightDrawable2() {
        return rightDrawable2;
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
        currentScrollDelay = SCROLL_DELAY_MS;
        recreateLayoutMaybe();
        return true;
    }

    public void resetScrolling() {
        scrollingOffset = 0;
    }

    public void copyScrolling(SimpleTextView textView) {
        scrollingOffset = textView.scrollingOffset;
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
        if (wasLayout && getMeasuredHeight() != 0 && !buildFullLayout) {
            boolean result = createLayout(getMaxTextWidth() - getPaddingLeft() - getPaddingRight() - minusWidth);
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                offsetY = (getMeasuredHeight() - textHeight) / 2;
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

    public int getLineCount() {
        int count = 0;
        if (layout != null) {
            count += layout.getLineCount();
        }
        if (fullLayout != null) {
            count += fullLayout.getLineCount();
        }
        return count;
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
        if (replacedDrawable != null && replacingDrawableTextIndex < 0) {
            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT) {
                textOffsetX += drawablePadding + replacedDrawable.getIntrinsicWidth();
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

    public void setRightPadding(int padding) {
        if (paddingRight != padding) {
            paddingRight = padding;

            int width = getMaxTextWidth() - getPaddingLeft() - getPaddingRight() - minusWidth;
            if (leftDrawable != null) {
                width -= leftDrawable.getIntrinsicWidth();
                width -= drawablePadding;
            }
            int rightDrawableWidth = 0;
            if (!rightDrawableInside) {
                if (rightDrawable != null && !rightDrawableOutside) {
                    rightDrawableWidth = (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale);
                    width -= rightDrawableWidth;
                    width -= drawablePadding;
                }
                if (rightDrawable2 != null && !rightDrawableOutside) {
                    rightDrawableWidth = (int) (rightDrawable2.getIntrinsicWidth() * rightDrawableScale);
                    width -= rightDrawableWidth;
                    width -= drawablePadding;
                }
            }
            if (replacedText != null && replacedDrawable != null) {
                if ((replacingDrawableTextIndex = text.toString().indexOf(replacedText)) < 0) {
                    width -= replacedDrawable.getIntrinsicWidth();
                    width -= drawablePadding;
                }
            }
            if (canHideRightDrawable && rightDrawableWidth != 0 && !rightDrawableOutside) {
                CharSequence string = TextUtils.ellipsize(text, textPaint, width, TextUtils.TruncateAt.END);
                if (!text.equals(string)) {
                    rightDrawableHidden = true;
                    width += rightDrawableWidth;
                    width += drawablePadding;
                }
            }
            calcOffset(width);

            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int textOffsetX = 0;

        boolean fade = scrollNonFitText && (textDoesNotFit || scrollingOffset != 0);
        int restore = Integer.MIN_VALUE;
        if (fade || ellipsizeByGradient) {
            restore = canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
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
                y = getPaddingTop() + (textHeight - leftDrawable.getIntrinsicHeight()) / 2 + leftDrawableTopPadding;
            }
            leftDrawable.setBounds(x, y, x + leftDrawable.getIntrinsicWidth(), y + leftDrawable.getIntrinsicHeight());
            leftDrawable.draw(canvas);
            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT || (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
                textOffsetX += drawablePadding + leftDrawable.getIntrinsicWidth();
            }
            totalWidth += drawablePadding + leftDrawable.getIntrinsicWidth();
        }
        if (replacedDrawable != null && replacedText != null) {
            int x = (int) (-scrollingOffset + replacingDrawableTextOffset);
            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
                x += offsetX;
            }
            int y;
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                y = (getMeasuredHeight() - replacedDrawable.getIntrinsicHeight()) / 2 + leftDrawableTopPadding;
            } else {
                y = (textHeight - replacedDrawable.getIntrinsicHeight()) / 2 + leftDrawableTopPadding;
            }
            replacedDrawable.setBounds(x, y, x + replacedDrawable.getIntrinsicWidth(), y + replacedDrawable.getIntrinsicHeight());
            replacedDrawable.draw(canvas);
            if (replacingDrawableTextIndex < 0) {
                if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT || (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
                    textOffsetX += drawablePadding + replacedDrawable.getIntrinsicWidth();
                }
                totalWidth += drawablePadding + replacedDrawable.getIntrinsicWidth();
            }
        }

        if (rightDrawable != null && !rightDrawableHidden && rightDrawableScale > 0 && !rightDrawableOutside && !rightDrawableInside) {
            int x = textOffsetX + textWidth + drawablePadding + (int) -scrollingOffset;
            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL ||
                (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) {
                x += offsetX;
            }
            int dw = (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale);
            int dh = (int) (rightDrawable.getIntrinsicHeight() * rightDrawableScale);
            int y;
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                y = (getMeasuredHeight() - dh) / 2 + rightDrawableTopPadding;
            } else {
                y = getPaddingTop() + (textHeight - dh) / 2 + rightDrawableTopPadding;
            }
            rightDrawable.setBounds(x, y, x + dw, y + dh);
            rightDrawableX = x + (dw >> 1);
            rightDrawableY = y + (dh >> 1);
            rightDrawable.draw(canvas);
            totalWidth += drawablePadding + dw;
        }
        if (rightDrawable2 != null && !rightDrawableHidden && rightDrawableScale > 0 && !rightDrawableOutside && !rightDrawableInside) {
            int x = textOffsetX + textWidth + drawablePadding + (int) -scrollingOffset;
            if (rightDrawable != null) {
                x += (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale) + drawablePadding;
            }
            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL ||
                    (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) {
                x += offsetX;
            }
            int dw = (int) (rightDrawable2.getIntrinsicWidth() * rightDrawableScale);
            int dh = (int) (rightDrawable2.getIntrinsicHeight() * rightDrawableScale);
            int y;
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                y = (getMeasuredHeight() - dh) / 2 + rightDrawableTopPadding;
            } else {
                y = getPaddingTop() + (textHeight - dh) / 2 + rightDrawableTopPadding;
            }
            rightDrawable2.setBounds(x, y, x + dw, y + dh);
            rightDrawable2.draw(canvas);
            totalWidth += drawablePadding + dw;
        }
        int nextScrollX = totalWidth + AndroidUtilities.dp(DIST_BETWEEN_SCROLLING_TEXT);

        if (scrollingOffset != 0) {
            if (leftDrawable != null) {
                int x = (int) -scrollingOffset + nextScrollX;
                int y;
                if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                    y = (getMeasuredHeight() - leftDrawable.getIntrinsicHeight()) / 2 + leftDrawableTopPadding;
                } else {
                    y = getPaddingTop() + (textHeight - leftDrawable.getIntrinsicHeight()) / 2 + leftDrawableTopPadding;
                }
                leftDrawable.setBounds(x, y, x + leftDrawable.getIntrinsicWidth(), y + leftDrawable.getIntrinsicHeight());
                leftDrawable.draw(canvas);
            }
            if (rightDrawable != null && !rightDrawableOutside) {
                int dw = (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale);
                int dh = (int) (rightDrawable.getIntrinsicHeight() * rightDrawableScale);
                int x = textOffsetX + textWidth + drawablePadding + (int) -scrollingOffset + nextScrollX;
                int y;
                if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                    y = (getMeasuredHeight() - dh) / 2 + rightDrawableTopPadding;
                } else {
                    y = getPaddingTop() + (textHeight - dh) / 2 + rightDrawableTopPadding;
                }
                rightDrawable.setBounds(x, y, x + dw, y + dh);
                rightDrawable.draw(canvas);
            }
            if (rightDrawable2 != null && !rightDrawableOutside) {
                int dw = (int) (rightDrawable2.getIntrinsicWidth() * rightDrawableScale);
                int dh = (int) (rightDrawable2.getIntrinsicHeight() * rightDrawableScale);
                int x = textOffsetX + textWidth + drawablePadding + (int) -scrollingOffset + nextScrollX;
                if (rightDrawable != null) {
                    x += (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale) + drawablePadding;
                }
                int y;
                if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                    y = (getMeasuredHeight() - dh) / 2 + rightDrawableTopPadding;
                } else {
                    y = getPaddingTop() + (textHeight - dh) / 2 + rightDrawableTopPadding;
                }
                rightDrawable2.setBounds(x, y, x + dw, y + dh);
                rightDrawable2.draw(canvas);
            }
        }

        if (layout != null) {
            if (rightDrawableOutside || ellipsizeByGradient || paddingRight > 0) {
                canvas.save();
                canvas.clipRect(0, 0, getMaxTextWidth() - paddingRight - AndroidUtilities.dp(rightDrawable != null && !(rightDrawable instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) && rightDrawableOutside ? 2 : 0), getMeasuredHeight());
            }
            Emoji.emojiDrawingUseAlpha = usaAlphaForEmoji;
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
            drawLayout(canvas);
            if (partLayout != null && fullAlpha < 1.0f) {
                int prevAlpha = textPaint.getAlpha();
                textPaint.setAlpha((int) (255 * (1.0f - fullAlpha)));
                canvas.save();
                float partOffset = 0;
                if (partLayout.getText().length() == 1) {
                     partOffset = fullTextMaxLines == 1 ? AndroidUtilities.dp(0.5f) : AndroidUtilities.dp(4);
                }
                if (layout.getLineLeft(0) != 0) {
                    canvas.translate(-layout.getLineWidth(0) + partOffset, 0);
                } else {
                    canvas.translate(layout.getLineWidth(0) - partOffset, 0);
                }
                canvas.translate(-fullLayoutLeftOffset * fullAlpha + fullLayoutLeftCharactersOffset * fullAlpha, 0);
                partLayout.draw(canvas);
                canvas.restore();
                textPaint.setAlpha(prevAlpha);
            }
            if (fullLayout != null && fullAlpha > 0) {
                int prevAlpha = textPaint.getAlpha();
                textPaint.setAlpha((int) (255 * fullAlpha));

                canvas.translate(-fullLayoutLeftOffset * fullAlpha + fullLayoutLeftCharactersOffset * fullAlpha - fullLayoutLeftCharactersOffset, 0);
                fullLayout.draw(canvas);
                textPaint.setAlpha(prevAlpha);
            }
            if (scrollingOffset != 0) {
                canvas.translate(nextScrollX, 0);
                drawLayout(canvas);
            }
            if (offsetX + textOffsetX != 0 || offsetY != 0 || scrollingOffset != 0) {
                canvas.restore();
            }
            if (rightDrawable != null && !rightDrawableHidden && rightDrawableScale > 0 && !rightDrawableOutside && rightDrawableInside) {
                int x = textOffsetX + textWidth + drawablePadding + (int) -scrollingOffset;
                if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL ||
                        (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) {
                    x += offsetX;
                }
                int dw = (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale);
                int dh = (int) (rightDrawable.getIntrinsicHeight() * rightDrawableScale);
                int y;
                if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                    y = (getMeasuredHeight() - dh) / 2 + rightDrawableTopPadding;
                } else {
                    y = getPaddingTop() + (textHeight - dh) / 2 + rightDrawableTopPadding;
                }
                rightDrawable.setBounds(x, y, x + dw, y + dh);
                rightDrawableX = x + (dw >> 1);
                rightDrawableY = y + (dh >> 1);
                rightDrawable.draw(canvas);
                totalWidth += drawablePadding + dw;
            }
            if (rightDrawable2 != null && !rightDrawableHidden && rightDrawableScale > 0 && !rightDrawableOutside && rightDrawableInside) {
                int x = textOffsetX + textWidth + drawablePadding + (int) -scrollingOffset;
                if (rightDrawable != null) {
                    x += (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale) + drawablePadding;
                }
                if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL ||
                        (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) {
                    x += offsetX;
                }
                int dw = (int) (rightDrawable2.getIntrinsicWidth() * rightDrawableScale);
                int dh = (int) (rightDrawable2.getIntrinsicHeight() * rightDrawableScale);
                int y;
                if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                    y = (getMeasuredHeight() - dh) / 2 + rightDrawableTopPadding;
                } else {
                    y = getPaddingTop() + (textHeight - dh) / 2 + rightDrawableTopPadding;
                }
                rightDrawable2.setBounds(x, y, x + dw, y + dh);
                rightDrawable2.draw(canvas);
                totalWidth += drawablePadding + dw;
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
                canvas.translate(getMaxTextWidth() - paddingRight - AndroidUtilities.dp(6), 0);
                canvas.drawRect(0, 0, AndroidUtilities.dp(6), getMeasuredHeight(), fadePaintBack);
                canvas.restore();
            } else if (ellipsizeByGradient && textDoesNotFit && fadeEllpsizePaint != null) {
                canvas.save();
                updateFadePaints();
                if (!ellipsizeByGradientLeft) {
                    canvas.translate(getMaxTextWidth() - paddingRight - fadeEllpsizePaintWidth - AndroidUtilities.dp(rightDrawable != null && !(rightDrawable instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) && rightDrawableOutside ? +2 : 0), 0);
                }
                canvas.drawRect(0, 0, fadeEllpsizePaintWidth, getMeasuredHeight(), fadeEllpsizePaint);
                canvas.restore();
            }
            updateScrollAnimation();
            Emoji.emojiDrawingUseAlpha = true;
            if (rightDrawableOutside || ellipsizeByGradient || paddingRight > 0) {
                canvas.restore();
            }
        }
        if (fade || ellipsizeByGradient) {
            canvas.restoreToCount(restore);
        }

        if (rightDrawable != null && rightDrawableOutside) {
            int x = Math.min(textOffsetX + textWidth + drawablePadding + (scrollingOffset == 0 ? -nextScrollX : (int) -scrollingOffset) + nextScrollX, getMaxTextWidth() - paddingRight + drawablePadding);
            int dw = (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale);
            int dh = (int) (rightDrawable.getIntrinsicHeight() * rightDrawableScale);
            int y;
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                y = (getMeasuredHeight() - dh) / 2 + rightDrawableTopPadding;
            } else {
                y = getPaddingTop() + (textHeight - dh) / 2 + rightDrawableTopPadding;
            }
            rightDrawable.setBounds(x, y, x + dw, y + dh);
            rightDrawableX = x + (dw >> 1);
            rightDrawableY = y + (dh >> 1);
            rightDrawable.draw(canvas);
        }
        if (rightDrawable2 != null && rightDrawableOutside) {
            int x = Math.min(
                textOffsetX + textWidth + drawablePadding + (scrollingOffset == 0 ? -nextScrollX : (int) -scrollingOffset) + nextScrollX,
                getMaxTextWidth() - paddingRight + drawablePadding
            );
            if (rightDrawable != null) {
                x += (int) (rightDrawable.getIntrinsicWidth() * rightDrawableScale) + drawablePadding;
            }
            int dw = (int) (rightDrawable2.getIntrinsicWidth() * rightDrawableScale);
            int dh = (int) (rightDrawable2.getIntrinsicHeight() * rightDrawableScale);
            int y;
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                y = (getMeasuredHeight() - dh) / 2 + rightDrawableTopPadding;
            } else {
                y = getPaddingTop() + (textHeight - dh) / 2 + rightDrawableTopPadding;
            }
            rightDrawable2.setBounds(x, y, x + dw, y + dh);
            rightDrawable2.draw(canvas);
        }
    }

    public int getRightDrawableX() {
        return rightDrawableX;
    }

    public int getRightDrawableY() {
        return rightDrawableY;
    }

    public int getMaxTextWidth() {
        return getMeasuredWidth() - (rightDrawableOutside && rightDrawable != null ? rightDrawable.getIntrinsicWidth() + drawablePadding : 0) - (rightDrawableOutside && rightDrawable2 != null ? rightDrawable2.getIntrinsicWidth() + drawablePadding : 0);
    }

    private void drawLayout(Canvas canvas) {
        if (fullAlpha > 0 && fullLayoutLeftOffset != 0) {
            canvas.save();
            canvas.translate(-fullLayoutLeftOffset * fullAlpha + fullLayoutLeftCharactersOffset * fullAlpha, 0);

            canvas.save();
            clipOutSpoilers(canvas);
            if (emojiStack != null) {
                emojiStack.clearPositions();
            }
            layout.draw(canvas);
            canvas.restore();

            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, emojiStack, 0, null, 0, 0, 0, 1f);
            drawSpoilers(canvas);
            canvas.restore();
        } else {
            canvas.save();
            clipOutSpoilers(canvas);
            if (emojiStack != null) {
                emojiStack.clearPositions();
            }
            layout.draw(canvas);
            canvas.restore();

            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, emojiStack, 0, null, 0, 0, 0, 1f);
            drawSpoilers(canvas);
        }
    }

    private void clipOutSpoilers(Canvas canvas) {
        path.rewind();
        for (SpoilerEffect eff : spoilers) {
            Rect b = eff.getBounds();
            path.addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
        }
        canvas.clipPath(path, Region.Op.DIFFERENCE);
    }

    private void drawSpoilers(Canvas canvas) {
        for (SpoilerEffect eff : spoilers)
            eff.draw(canvas);
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
        } else if (who == rightDrawable2) {
            invalidate(rightDrawable2.getBounds());
        } else if (who == replacedDrawable) {
            invalidate(replacedDrawable.getBounds());
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

    public void setFullLayoutAdditionalWidth(int fullLayoutAdditionalWidth, int fullLayoutLeftOffset) {
        if (this.fullLayoutAdditionalWidth != fullLayoutAdditionalWidth || this.fullLayoutLeftOffset != fullLayoutLeftOffset) {
            this.fullLayoutAdditionalWidth = fullLayoutAdditionalWidth;
            this.fullLayoutLeftOffset = fullLayoutLeftOffset;
            createLayout(getMaxTextWidth() - getPaddingLeft() - getPaddingRight() - minusWidth);
        }
    }

    public void setFullTextMaxLines(int fullTextMaxLines) {
        this.fullTextMaxLines = fullTextMaxLines;
    }

    public int getTextColor() {
        return textPaint.getColor();
    }

    public void setCanHideRightDrawable(boolean b) {
        canHideRightDrawable = b;
    }

    public void setRightDrawableOutside(boolean outside) {
        rightDrawableOutside = outside;
    }

    // right drawable is ellipsized with text
    public void setRightDrawableInside(boolean inside) {
        rightDrawableInside = inside;
    }


    public boolean getRightDrawableOutside() {
        return rightDrawableOutside;
    }

    public void setRightDrawableOnClick(OnClickListener onClickListener) {
        rightDrawableOnClickListener = onClickListener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (rightDrawableOnClickListener != null && rightDrawable != null) {
            AndroidUtilities.rectTmp.set(rightDrawableX - AndroidUtilities.dp(16), rightDrawableY - AndroidUtilities.dp(16), rightDrawableX + AndroidUtilities.dp(16), rightDrawableY + AndroidUtilities.dp(16));
            if (event.getAction() == MotionEvent.ACTION_DOWN && AndroidUtilities.rectTmp.contains((int) event.getX(), (int) event.getY())) {
                maybeClick = true;
                touchDownX = event.getX();
                touchDownY = event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                if (rightDrawable instanceof PressableDrawable) {
                    ((PressableDrawable) rightDrawable).setPressed(true);
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE && maybeClick) {
                if (Math.abs(event.getX() - touchDownX) >= AndroidUtilities.touchSlop || Math.abs(event.getY() - touchDownY) >= AndroidUtilities.touchSlop) {
                    maybeClick = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if (rightDrawable instanceof PressableDrawable) {
                        ((PressableDrawable) rightDrawable).setPressed(false);
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (maybeClick && event.getAction() == MotionEvent.ACTION_UP) {
                    rightDrawableOnClickListener.onClick(this);
                    if (rightDrawable instanceof PressableDrawable) {
                        ((PressableDrawable) rightDrawable).setPressed(false);
                    }
                }
                maybeClick = false;
                getParent().requestDisallowInterceptTouchEvent(false);
            }
        }
        return super.onTouchEvent(event) || maybeClick;
    }

    public static interface PressableDrawable {
        public void setPressed(boolean value);
        public boolean isPressed();
    }
}
