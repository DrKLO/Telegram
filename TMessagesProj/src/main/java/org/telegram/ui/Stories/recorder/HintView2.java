package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.TypefaceSpan;

public class HintView2 extends View {

    // direction of an arrow to point
    // the gravity of location would be the same, location of arrow opposite:
    // f.ex. hint with DIRECTION_LEFT has arrow on left of hint and would be forced to the left ({view} <{hint})
    public static final int DIRECTION_LEFT = 0;
    public static final int DIRECTION_TOP = 1;
    public static final int DIRECTION_RIGHT = 2;
    public static final int DIRECTION_BOTTOM = 3;
    // the layout (bounds of hint) are up to a user of this component

    private int direction;
    private float joint = .5f, jointTranslate = 0;

    private long duration = 3500;
    private boolean useScale = true;
    private boolean useTranslate = true;
    private boolean useAlpha = true;
    private boolean useBlur = false;
    private int textMaxWidth = -1;

    private Drawable closeButtonDrawable;
    private boolean closeButton;

    private boolean roundWithCornerEffect = true;
    protected float rounding = dp(8);
    private final RectF innerPadding = new RectF(dp(11), dp(6), dp(11), dp(7));
    private float closeButtonMargin = dp(2);
    private float arrowHalfWidth = dp(7);
    private float arrowHeight = dp(6);

    public HintView2 setArrowSize(float halfWidthDp, float heightDp) {
        this.arrowHalfWidth = dpf2(halfWidthDp);
        this.arrowHeight = dpf2(heightDp);
        return this;
    }

    protected final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint blurBackgroundPaint, blurCutPaint;

    private int blurBitmapWidth, blurBitmapHeight;
    private BitmapShader blurBitmapShader;
    private Matrix blurBitmapMatrix;
    private float blurScale = 12f;
    private float blurAlpha = .25f;
    private int[] blurPos;

    private CharSequence textToSet;
    private AnimatedTextView.AnimatedTextDrawable textDrawable;

    private boolean multiline;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Layout.Alignment textLayoutAlignment = Layout.Alignment.ALIGN_NORMAL;
    private StaticLayout textLayout;
    private AnimatedEmojiSpan.EmojiGroupedSpans emojiGroupedSpans;
    private float textLayoutLeft, textLayoutWidth, textLayoutHeight;
    private LinkSpanDrawable.LinkCollector links = new LinkSpanDrawable.LinkCollector();
    private float textX, textY;

    private boolean hideByTouch = true;
    private boolean repeatedBounce = true;

    private boolean shown;
    private AnimatedFloat show = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    private Drawable selectorDrawable;
    private Paint cutSelectorPaint;

    private Drawable icon;
    private float iconTx, iconTy;
    private int iconMargin = dp(2);
    private int iconWidth, iconHeight;
    private boolean iconLeft;

    public HintView2(Context context) {
        this(context, 0);
    }

    public HintView2(Context context, int direction) {
        super(context);
        this.direction = direction;

        backgroundPaint.setColor(0xe6282828);
        backgroundPaint.setPathEffect(new CornerPathEffect(rounding));

        textDrawable = new AnimatedTextView.AnimatedTextDrawable(true, true, false);
        textDrawable.setAnimationProperties(.4f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setCallback(this);

        setTextSize(14);
        setTextColor(0xffffffff);
    }

    public HintView2 setDirection(int direction) {
        this.direction = direction;
        return this;
    }

    public HintView2 setRounding(float roundingDp) {
        this.rounding = dp(roundingDp);
        backgroundPaint.setPathEffect(roundWithCornerEffect ? new CornerPathEffect(rounding) : null);
        if (cutSelectorPaint != null) {
            cutSelectorPaint.setPathEffect(roundWithCornerEffect ? new CornerPathEffect(rounding) : null);
        }
        if (blurCutPaint != null) {
            blurCutPaint.setPathEffect(roundWithCornerEffect ? new CornerPathEffect(rounding) : null);
        }
        return this;
    }

    public HintView2 setRoundingWithCornerEffect(boolean enable) {
        roundWithCornerEffect = enable;
        backgroundPaint.setPathEffect(roundWithCornerEffect ? new CornerPathEffect(rounding) : null);
        return this;
    }

    public HintView2 setFlicker(float lineWidthDp, int color) {
        this.flicker = true;
        flickerStart = System.currentTimeMillis();

        flickerStrokePath = new Path();
        flickerStrokePathExtrude = dpf2(lineWidthDp) / 2.0f;

        flickerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flickerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        flickerStrokeGradient = new LinearGradient(0, 0, dp(64), 0, new int[] { Theme.multAlpha(color, 0.0f), Theme.multAlpha(color, 1.0f), Theme.multAlpha(color, 0.0f) }, new float[] { 0, .5f, 1f }, Shader.TileMode.CLAMP);
        flickerStrokePaint.setShader(flickerStrokeGradient);

        flickerGradient = new LinearGradient(0, 0, dp(64), 0, new int[] { Theme.multAlpha(color, 0.0f), Theme.multAlpha(color, 0.5f), Theme.multAlpha(color, 0.0f) }, new float[] { 0, .5f, 1f }, Shader.TileMode.CLAMP);
        flickerGradientMatrix = new Matrix();
        flickerFillPaint.setShader(flickerGradient);

        flickerStrokePaint.setStyle(Paint.Style.STROKE);
        flickerStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        flickerStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        flickerStrokePaint.setStrokeWidth(dpf2(lineWidthDp));

        return this;
    }

    // mutliline disables text animations
    public HintView2 setMultilineText(boolean enable) {
        this.multiline = enable;
        if (enable) {
            innerPadding.set(dp(15), dp(8), dp(15), dp(8));
            closeButtonMargin = dp(6);
        } else {
            innerPadding.set(dp(11), dp(6), dp(closeButton ? 15 : 11), dp(7));
            closeButtonMargin = dp(2);
        }
        return this;
    }

    public HintView2 setText(CharSequence text) {
        if (getMeasuredWidth() < 0) {
            textToSet = text;
        } else if (!multiline) {
            this.textDrawable.setText(text, false);
        } else {
            makeLayout(text, getTextMaxWidth());
        }
        return this;
    }

    public CharSequence getText() {
        if (textToSet != null) {
            return textToSet;
        } else if (!multiline) {
            return this.textDrawable.getText();
        } else if (textLayout != null) {
            return textLayout.getText();
        }
        return null;
    }

    public HintView2 setText(CharSequence text, boolean animated) {
        if (getMeasuredWidth() < 0) {
            textToSet = text;
        } else {
            this.textDrawable.setText(text, !LocaleController.isRTL && animated);
        }
        return this;
    }

    public HintView2 allowBlur() {
        return allowBlur(true);
    }

    public HintView2 allowBlur(boolean allow) {
        useBlur = allow && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR);
        return this;
    }

    public HintView2 setTextSize(float sizeDp) {
        textDrawable.setTextSize(dpf2(sizeDp));
        textPaint.setTextSize(dpf2(sizeDp));
        return this;
    }

    public HintView2 setTextTypeface(Typeface typeface) {
        textDrawable.setTypeface(typeface);
        textPaint.setTypeface(typeface);
        return this;
    }

    public HintView2 setCloseButton(boolean show) {
        this.closeButton = show;
        if (!multiline) {
            innerPadding.set(dp(11), dp(6), dp(closeButton ? 15 : 11), dp(7));
        }
        return this;
    }

    public HintView2 setMaxWidth(float widthDp) {
        this.textMaxWidth = dp(widthDp);
        return this;
    }

    public HintView2 setMaxWidthPx(int widthPx) {
        this.textMaxWidth = widthPx;
        return this;
    }

    public HintView2 setIcon(int resId) {
        RLottieDrawable icon = new RLottieDrawable(resId, "" + resId, dp(34), dp(34));
        icon.start();
        return setIcon(icon);
    }

    public HintView2 setIcon(Drawable icon) {
        if (this.icon != null) {
            this.icon.setCallback(null);
        }
        this.icon = icon;
        if (this.icon != null) {
            this.icon.setCallback(this);
            if (this.icon instanceof RLottieDrawable) {
                duration = Math.max(duration, ((RLottieDrawable) this.icon).getDuration());
            }
            // TODO: to be custom
            this.iconWidth  = this.icon.getIntrinsicWidth();
            this.iconHeight = this.icon.getIntrinsicHeight();
            this.iconLeft = true;
        }
        return this;
    }

    public static float measureCorrectly(CharSequence text, TextPaint paint) {
        if (text == null) {
            return 0;
        }
        if (!(text instanceof Spanned)) {
            return paint.measureText(text.toString());
        }
        Spanned spanned = (Spanned) text;
        TypefaceSpan[] spans = spanned.getSpans(0, text.length(), TypefaceSpan.class);
        AnimatedEmojiSpan[] animatedSpans = spanned.getSpans(0, text.length(), AnimatedEmojiSpan.class);
        Emoji.EmojiSpan[] emojiSpans = spanned.getSpans(0, text.length(), Emoji.EmojiSpan.class);
        ColoredImageSpan[] imageSpans = spanned.getSpans(0, text.length(), ColoredImageSpan.class);
        int add = 0;
        for (int i = 0; i < emojiSpans.length; ++i) {
            Emoji.EmojiSpan span = emojiSpans[i];
            final int start = spanned.getSpanStart(span);
            final int end = spanned.getSpanEnd(span);
            add += Math.max(0, span.size - paint.measureText(spanned, start, end));
        }
        for (int i = 0; i < imageSpans.length; ++i) {
            ColoredImageSpan span = imageSpans[i];
            final int start = spanned.getSpanStart(span);
            final int end = spanned.getSpanEnd(span);
            add += Math.max(0, span.getSize(paint, text, start, end, paint.getFontMetricsInt()) - paint.measureText(spanned, start, end));
        }
        for (int i = 0; i < animatedSpans.length; ++i) {
            AnimatedEmojiSpan span = animatedSpans[i];
            final int start = spanned.getSpanStart(span);
            final int end = spanned.getSpanEnd(span);
            add += Math.max(0, span.getSize(paint, text, start, end, paint.getFontMetricsInt()) - paint.measureText(spanned, start, end));
        }
        if (spans == null || spans.length == 0) {
            return paint.measureText(text.toString()) + add;
        }
        float len = 0;
        int s = 0, e;
        for (int i = 0; i < spans.length; ++i) {
            final int spanstart = spanned.getSpanStart(spans[i]);
            final int spanend   = spanned.getSpanEnd(spans[i]);

            e = Math.max(s, spanstart);
            if (e - s > 0) {
                len += paint.measureText(spanned, s, e);
            }
            s = e;
            e = Math.max(s, spanend);
            if (e - s > 0) {
                Typeface oldTypeface = paint.getTypeface();
                paint.setTypeface(spans[i].getTypeface());
                len += paint.measureText(spanned, s, e);
                paint.setTypeface(oldTypeface);
            }
            s = e;
        }
        e = Math.max(s, text.length());
        if (e - s > 0) {
            len += paint.measureText(spanned, s, e);
        }
        return len + add;
    }

    // returns max width
    public static int cutInFancyHalf(CharSequence text, TextPaint paint) {
        int mid = text.length() / 2;
        float leftWidth = 0, rightWidth = 0;
        float prevLeftWidth = 0;
        float prevRightWidth = Float.MAX_VALUE;

        int dir = -1;
        for (int i = 0; i < 10; ++i) {
            // Adjust the mid to point to the nearest space on the left
            while (mid > 0 && mid < text.length() && text.charAt(mid) != ' ') {
                mid += dir;
            }


            leftWidth = measureCorrectly(text.subSequence(0, mid), paint);
            rightWidth = measureCorrectly(AndroidUtilities.getTrimmedString(text.subSequence(mid, text.length())), paint);

            // If we're not making progress, exit the loop.
            // (This is a basic way to ensure termination when we can't improve the result.)
            if (leftWidth == prevLeftWidth && rightWidth == prevRightWidth) {
                break;
            }

            prevLeftWidth = leftWidth;
            prevRightWidth = rightWidth;

            // If left side is shorter, move midpoint to the right.
            if (leftWidth < rightWidth) {
                dir = +1;
                mid += dir;
            }
            // If right side is shorter or equal, move midpoint to the left.
            else {
                dir = -1;
                mid += dir;
            }

            // Ensure mid doesn't go out of bounds
            if (mid <= 0 || mid >= text.length()) {
                break;
            }
        }

        // Return the max width of the two parts.
        return (int) Math.ceil(Math.max(leftWidth, rightWidth));
    }

    public HintView2 useScale(boolean enable) {
        this.useScale = enable;
        return this;
    }

    public HintView2 useTranslate(boolean enable) {
        this.useTranslate = enable;
        return this;
    }

    // duration < 0 means you would hide it on yourself
    public HintView2 setDuration(long duration) {
        this.duration = duration;
        return this;
    }

    public HintView2 setAnimatedTextHacks(boolean splitByWords, boolean preserveIndex, boolean startFromEnd) {
        this.textDrawable.setHacks(splitByWords, preserveIndex, startFromEnd);
        return this;
    }

    // distances from text to inner hint bounds
    // use setPadding() or custom layout to move possible bounds of the hint location
    // call last, as paddings are dependent on multiline and closeButton
    public HintView2 setInnerPadding(float leftDp, float topDp, float rightDp, float bottomDp) {
        innerPadding.set(dpf2(leftDp), dpf2(topDp), dpf2(rightDp), dpf2(bottomDp));
        return this;
    }

    public HintView2 setIconMargin(int marginDp) {
        this.iconMargin = dp(marginDp);
        return this;
    }

    public HintView2 setIconTranslate(float iconTx, float iconTy) {
        this.iconTx = iconTx;
        this.iconTy = iconTy;
        return this;
    }

    public HintView2 setCloseButtonMargin(int marginDp) {
        this.closeButtonMargin = dp(marginDp);
        return this;
    }

    public HintView2 setTextColor(int color) {
        textDrawable.setTextColor(color);
        textPaint.setColor(color);
        return this;
    }

    public HintView2 setHideByTouch(boolean enable) {
        hideByTouch = enable;
        return this;
    }

    public HintView2 setSelectorColor(int selectorColor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return this;
        }
        cutSelectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cutSelectorPaint.setPathEffect(new CornerPathEffect(rounding));
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{ StateSet.WILD_CARD },
                new int[]{ selectorColor }
        );
        selectorDrawable = new BaseCell.RippleDrawableSafe(colorStateList, null, new Drawable() {
            @Override
            public void draw(@NonNull Canvas canvas) {
                canvas.save();
//                canvas.translate(-boundsWithArrow.left, -boundsWithArrow.top);
                canvas.drawPath(path, cutSelectorPaint);
                canvas.restore();
            }
            @Override
            public void setAlpha(int alpha) {}
            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {}
            @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }
        });
        selectorDrawable.setCallback(this);
        return this;
    }

    public HintView2 setBounce(boolean enable) {
        repeatedBounce = enable;
        return this;
    }

    // works only for multiline=true
    public HintView2 setTextAlign(Layout.Alignment alignment) {
        textLayoutAlignment = alignment;
        return this;
    }

    public HintView2 setBgColor(int color) {
        if (backgroundPaint.getColor() != color) {
            backgroundPaint.setColor(color);
            invalidate();
        }
        return this;
    }

    private Runnable onHidden;
    public HintView2 setOnHiddenListener(Runnable listener) {
        this.onHidden = listener;
        return this;
    }

    // joint is where should be a connection with an arrow
    // f.ex. on DIRECTION_TOP: joint=0: left, joint=.5: at the center, joint=1: right
    // jointTranslate translates joint in pixels amount
    public HintView2 setJoint(float joint, float jointTranslateDp) {
        if (Math.abs(this.joint - joint) >= 1 || Math.abs(this.jointTranslate - dp(jointTranslateDp)) >= 1) {
            this.pathSet = false;
            invalidate();
        }
        this.joint = joint;
        this.jointTranslate = dp(jointTranslateDp);
        return this;
    }

    public HintView2 setJointPx(float joint, float jointTranslatePx) {
        if (Math.abs(this.joint - joint) >= 1 || Math.abs(this.jointTranslate - jointTranslatePx) >= 1) {
            this.pathSet = false;
            invalidate();
        }
        this.joint = joint;
        this.jointTranslate = jointTranslatePx;
        return this;
    }

    public TextPaint getTextPaint() {
        if (multiline) {
            return textPaint;
        } else {
            return textDrawable.getPaint();
        }
    }

    private final Runnable hideRunnable = this::hide;

    public void show(boolean show) {
        if (show) show();
        else hide();
    }

    public HintView2 show() {
        prepareBlur();
        if (shown) {
            bounceShow();
        }
        AndroidUtilities.makeAccessibilityAnnouncement(getText());
        shown = true;
        invalidate();

        AndroidUtilities.cancelRunOnUIThread(hideRunnable);
        if (duration > 0) {
            AndroidUtilities.runOnUIThread(hideRunnable, duration);
        }
        if (onHidden != null) {
            AndroidUtilities.cancelRunOnUIThread(onHidden);
        }

        return this;
    }

    private ValueAnimator bounceAnimator;
    private float bounceT = 1;
    private void bounceShow() {
        if (!repeatedBounce) {
            return;
        }
        if (bounceAnimator != null) {
            bounceAnimator.cancel();
            bounceAnimator = null;
        }
        bounceAnimator = ValueAnimator.ofFloat(0, 1);
        bounceAnimator.addUpdateListener(anm -> {
            bounceT = Math.max(1, (float) anm.getAnimatedValue());
            invalidate();
        });
        bounceAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                bounceT = 1;
                invalidate();
            }
        });
        bounceAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK);
        bounceAnimator.setDuration(300);
        bounceAnimator.start();
    }

    public void hide() {
        hide(true);
    }

    public void hide(boolean animated) {
        AndroidUtilities.cancelRunOnUIThread(hideRunnable);
        if (onHidden != null) {
            AndroidUtilities.cancelRunOnUIThread(onHidden);
        }
        shown = false;
        if (!animated) {
            show.set(shown, false);
        }
        invalidate();
        if (onHidden != null) {
            AndroidUtilities.runOnUIThread(onHidden, (long) (show.get() * show.getDuration()));
        }
        links.clear();
    }

    public void pause() {
        AndroidUtilities.cancelRunOnUIThread(hideRunnable);
    }

    public void unpause() {
        AndroidUtilities.cancelRunOnUIThread(hideRunnable);
        if (duration > 0) {
            AndroidUtilities.runOnUIThread(hideRunnable, duration);
        }
    }

    public boolean shown() {
        return shown;
    }

    private int getTextMaxWidth() {
        int textWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - (int) (innerPadding.left + innerPadding.right);
        if (textMaxWidth > 0) {
            textWidth = Math.min(textMaxWidth, textWidth);
        }
        return Math.max(0, textWidth);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        pathSet = false;

        final int textWidth = getTextMaxWidth();
        textDrawable.setOverrideFullWidth(textWidth);
        if (multiline) {
            CharSequence text;
            if (textToSet != null) {
                text = textToSet;
            } else if (textLayout != null) {
                text = textLayout.getText();
            } else {
                return;
            }
            if (textLayout == null || textLayout.getWidth() != textWidth) {
                makeLayout(text, textWidth);
            }
        } else if (textToSet != null) {
            textDrawable.setText(textToSet, false);
        }
        textToSet = null;
    }

    private void makeLayout(CharSequence text, int width) {
        textLayout = new StaticLayout(text, textPaint, width, textLayoutAlignment, 1f, 0f, false);
        float left = width, right = 0;
        for (int i = 0; i < textLayout.getLineCount(); ++i) {
            left = Math.min(left, textLayout.getLineLeft(i));
            right = Math.max(right, textLayout.getLineRight(i));
        }
        textLayoutWidth = Math.max(0, right - left);
        textLayoutHeight = textLayout.getHeight();
        textLayoutLeft = left;
        emojiGroupedSpans = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, emojiGroupedSpans, textLayout);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AnimatedEmojiSpan.release(this, emojiGroupedSpans);
    }

    private final ButtonBounce bounce = new ButtonBounce(this, 2f, 5f);
    private float bounceX, bounceY;

    private final Rect boundsWithArrow = new Rect();
    private final RectF bounds = new RectF();
    private final RectF flickerBounds = new RectF();
    protected final Path path = new Path();
    private float arrowX, arrowY;
    private float pathLastWidth, pathLastHeight;
    private boolean pathSet;
    private boolean firstDraw = true;

    private boolean flicker;
    private Path flickerStrokePath;
    private float flickerStrokePathExtrude;
    private Paint flickerFillPaint;
    private Paint flickerStrokePaint;
    private LinearGradient flickerGradient;
    private Matrix flickerGradientMatrix;
    private LinearGradient flickerStrokeGradient;
    private long flickerStart;

    protected void drawBgPath(Canvas canvas) {
        if (blurBackgroundPaint != null) {
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
            canvas.drawPath(path, blurBackgroundPaint);
            canvas.drawPath(path, blurCutPaint);
            canvas.restore();
        }
        canvas.drawPath(path, backgroundPaint);
        if (flicker) {
            final int delay = 4, duration = 1000;
            final int gradientWidth = dp(64);
            final float left = -gradientWidth + (System.currentTimeMillis() - flickerStart) % (duration * delay) / (float) (duration * delay) * (pathLastWidth * delay + gradientWidth * 2);

            flickerGradientMatrix.reset();
            flickerGradientMatrix.postTranslate(bounds.left + left, 0);
            flickerGradient.setLocalMatrix(flickerGradientMatrix);
            flickerStrokeGradient.setLocalMatrix(flickerGradientMatrix);

            canvas.drawPath(path, flickerFillPaint);
            canvas.drawPath(flickerStrokePath, flickerStrokePaint);
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (drawingMyBlur) {
            return;
        }
        if (multiline && textLayout == null) {
            return;
        }

        final float showT = show.set(shown && !firstDraw);
        if (firstDraw) {
            firstDraw = false;
            invalidate();
        }
        if (showT <= 0) {
            return;
        }

        float contentWidth = multiline ? textLayoutWidth : textDrawable.getCurrentWidth();
        float contentHeight = multiline ? textLayoutHeight : textDrawable.getHeight();
        if (closeButton) {
            if (closeButtonDrawable == null) {
                closeButtonDrawable = getContext().getResources().getDrawable(R.drawable.msg_mini_close_tooltip).mutate();
                closeButtonDrawable.setColorFilter(new PorterDuffColorFilter(0x7dffffff, PorterDuff.Mode.MULTIPLY));
            }
            contentWidth += closeButtonMargin + closeButtonDrawable.getIntrinsicWidth();
            contentHeight = Math.max(closeButtonDrawable.getIntrinsicHeight(), contentHeight);
        }
        if (icon != null) {
            contentWidth += iconWidth + iconMargin;
            contentHeight = Math.max(iconHeight, contentHeight);
        }

        final float width = innerPadding.left + contentWidth + innerPadding.right;
        final float height = innerPadding.top + contentHeight + innerPadding.bottom;
        if (!pathSet || Math.abs(width - pathLastWidth) > 0.1f || Math.abs(height - pathLastHeight) > 0.1f) {
            fillPath(path, pathLastWidth = width, pathLastHeight = height, 0.0f, bounds, boundsWithArrow);
            if (flicker) {
                fillPath(flickerStrokePath, width, height, flickerStrokePathExtrude, flickerBounds, null);
            }
        }

        float alpha = useAlpha ? showT : 1;
        canvas.save();
        if (showT < 1 && useScale) {
            final float scale = lerp(.5f, 1f, showT);
            canvas.scale(scale, scale, arrowX, arrowY);
        }
        float bounceScale = bounce.getScale(.025f);
        if (bounceScale != 1) {
            canvas.scale(bounceScale, bounceScale, arrowX, arrowY);
        }
        if (bounceT != 1) {
            if (direction == DIRECTION_BOTTOM || direction == DIRECTION_TOP) {
                int maxpad = direction == DIRECTION_BOTTOM ? getPaddingBottom() : getPaddingTop();
                canvas.translate(0, (bounceT - 1) * Math.max(maxpad, dp(24)) * (direction == DIRECTION_TOP ? -1 : 1));
            } else {
                int maxpad = direction == DIRECTION_LEFT ? getPaddingLeft() : getPaddingRight();
                canvas.translate((bounceT - 1) * Math.max(maxpad, dp(24)) * (direction == DIRECTION_LEFT ? -1 : 1), 0);
            }
        }

        updateBlurBounds();
        final int wasAlpha = backgroundPaint.getAlpha();
        AndroidUtilities.rectTmp.set(bounds);
        AndroidUtilities.rectTmp.inset(-arrowHeight, -arrowHeight);
        float backgroundAlpha = alpha;
        if (blurBackgroundPaint != null && useBlur) {
            backgroundAlpha *= (1f - blurAlpha);
            blurBackgroundPaint.setAlpha((int) (0xFF * alpha));
        }
        backgroundPaint.setAlpha((int) (wasAlpha * backgroundAlpha));
        drawBgPath(canvas);
        backgroundPaint.setAlpha(wasAlpha);

        if (selectorDrawable != null) {
            selectorDrawable.setAlpha((int) (0xFF * alpha));
            selectorDrawable.setBounds(boundsWithArrow);
            selectorDrawable.draw(canvas);
        }

        final float cy = ((bounds.bottom - innerPadding.bottom) + (bounds.top + innerPadding.top)) / 2f;
        float tx = 0;
        if (icon != null) {
            if (iconLeft) {
                icon.setBounds(
                    (int) (iconTx + bounds.left + innerPadding.left / 2f),
                    (int) (iconTy + cy - iconHeight / 2f),
                    (int) (iconTx + bounds.left + innerPadding.left / 2f + iconWidth),
                    (int) (iconTy + cy + iconHeight / 2f)
                );
                tx += iconWidth + iconMargin;
            } else {
                icon.setBounds(
                    (int) (iconTx + bounds.right - innerPadding.right / 2f - iconWidth),
                    (int) (iconTy + cy - iconHeight / 2f),
                    (int) (iconTx + bounds.right - innerPadding.right / 2f),
                    (int) (iconTy + cy + iconHeight / 2f)
                );
            }
            icon.setAlpha((int) (0xFF * alpha));
            icon.draw(canvas);
        }

        if (multiline) {
            canvas.saveLayerAlpha(0, 0, getWidth(), Math.max(getHeight(), height), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
            canvas.translate(textX = tx + bounds.left + innerPadding.left - textLayoutLeft, textY = cy - textLayoutHeight / 2f);
            if (links.draw(canvas)) {
                invalidate();
            }
            textLayout.draw(canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, textLayout, emojiGroupedSpans, 0, null, 0, 0, 0, 1f);
            canvas.restore();
        } else {
            if (textToSet != null) {
                textDrawable.setText(textToSet, shown);
                textToSet = null;
            }
            textDrawable.setBounds((int) (tx + bounds.left + innerPadding.left), (int) (cy - textLayoutHeight / 2f), (int) (bounds.left + innerPadding.left + contentWidth), (int) (cy + textLayoutHeight / 2f));
            textDrawable.setAlpha((int) (0xFF * alpha));
            textDrawable.draw(canvas);
        }

        if (closeButton) {
            if (closeButtonDrawable == null) {
                closeButtonDrawable = getContext().getResources().getDrawable(R.drawable.msg_mini_close_tooltip).mutate();
                closeButtonDrawable.setColorFilter(new PorterDuffColorFilter(0x7dffffff, PorterDuff.Mode.MULTIPLY));
            }
            closeButtonDrawable.setAlpha((int) (0xFF * alpha));
            closeButtonDrawable.setBounds(
                (int) (bounds.right - innerPadding.right * .66f - closeButtonDrawable.getIntrinsicWidth()),
                (int) (bounds.centerY() - closeButtonDrawable.getIntrinsicHeight() / 2f),
                (int) (bounds.right - innerPadding.right * .66f),
                (int) (bounds.centerY() + closeButtonDrawable.getIntrinsicHeight() / 2f)
            );
            closeButtonDrawable.draw(canvas);
        }
        canvas.restore();
    }

    private final RectF oval = new RectF();
    private void fillPath(Path path, float width, float height, float extrude, RectF bounds, Rect boundsWithArrow) {
        float arrowXY;
        final float r = Math.min(rounding, Math.min(width / 2, height / 2));
        if (direction == DIRECTION_TOP || direction == DIRECTION_BOTTOM) {
            if (roundWithCornerEffect) {
                arrowXY = lerp(getPaddingLeft(), getMeasuredWidth() - getPaddingRight(), joint);
            } else {
                arrowXY = lerp(getPaddingLeft() + r + arrowHalfWidth, getMeasuredWidth() - getPaddingRight() - r - arrowHalfWidth, joint);
            }
            arrowXY = Utilities.clamp(arrowXY + jointTranslate, getMeasuredWidth() - getPaddingRight(), getPaddingLeft());
            float left = Math.max(getPaddingLeft(), arrowXY - width / 2f);
            float right = Math.min(left + width, getMeasuredWidth() - getPaddingRight());
            left = right - width;
            arrowXY = Utilities.clamp(arrowXY, right - r - arrowHalfWidth, left + r + arrowHalfWidth);
            if (direction == DIRECTION_TOP) {
                bounds.set(left, getPaddingTop() + arrowHeight, right, getPaddingTop() + arrowHeight + height);
            } else {
                bounds.set(left, getMeasuredHeight() - arrowHeight - getPaddingBottom() - height, right, getMeasuredHeight() - arrowHeight - getPaddingBottom());
            }
        } else {
            if (roundWithCornerEffect) {
                arrowXY = lerp(getPaddingTop(), getMeasuredHeight() - getPaddingBottom(), joint);
            } else {
                arrowXY = lerp(getPaddingTop() + r + arrowHalfWidth, getMeasuredHeight() - getPaddingBottom() - r - arrowHalfWidth, joint);
            }
            arrowXY = Utilities.clamp(arrowXY + jointTranslate, getMeasuredHeight() - getPaddingBottom(), getPaddingTop());
            float top = Math.max(getPaddingTop(), arrowXY - height / 2f);
            float bottom = Math.min(top + height, getMeasuredHeight() - getPaddingBottom());
            top = bottom - height;
            arrowXY = Utilities.clamp(arrowXY, bottom - r - arrowHalfWidth, top + r + arrowHalfWidth);
            if (direction == DIRECTION_LEFT) {
                bounds.set(getPaddingLeft() + arrowHeight, top, getPaddingLeft() + arrowHeight + width, bottom);
            } else {
                bounds.set(getMeasuredWidth() - getPaddingRight() - arrowHeight - width, top, getMeasuredWidth() - getPaddingRight() - arrowHeight, bottom);
            }
        }
        bounds.inset(-extrude, -extrude);
        if (boundsWithArrow != null) {
            boundsWithArrow.set((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom);
        }

        path.rewind();
        if (roundWithCornerEffect) {
            path.moveTo(bounds.left, bounds.bottom);
        } else {
            oval.set(bounds.left, bounds.bottom - r * 2, bounds.left + r * 2, bounds.bottom);
            path.arcTo(oval, 90, 90);
        }
        if (direction == DIRECTION_LEFT) {
            path.lineTo(bounds.left, arrowXY + arrowHalfWidth + dp(2));
            path.lineTo(bounds.left, arrowXY + arrowHalfWidth);
            path.lineTo(bounds.left - arrowHeight, arrowXY + dp(1));
            arrowX = bounds.left - arrowHeight;
            arrowY = arrowXY;
            path.lineTo(bounds.left - arrowHeight, arrowXY - dp(1));
            path.lineTo(bounds.left, arrowXY - arrowHalfWidth);
            path.lineTo(bounds.left, arrowXY - arrowHalfWidth - dp(2));
            if (boundsWithArrow != null) {
                boundsWithArrow.left -= arrowHeight;
            }
        }
        if (roundWithCornerEffect) {
            path.lineTo(bounds.left, bounds.top);
        } else {
            oval.set(bounds.left, bounds.top, bounds.left + r * 2, bounds.top + r * 2);
            path.arcTo(oval, 180, 90);
        }
        if (direction == DIRECTION_TOP) {
            path.lineTo(arrowXY - arrowHalfWidth - dp(2), bounds.top);
            path.lineTo(arrowXY - arrowHalfWidth, bounds.top);
            path.lineTo(arrowXY - dp(1), bounds.top - arrowHeight);
            arrowX = arrowXY;
            arrowY = bounds.top - arrowHeight;
            path.lineTo(arrowXY + dp(1), bounds.top - arrowHeight);
            path.lineTo(arrowXY + arrowHalfWidth, bounds.top);
            path.lineTo(arrowXY + arrowHalfWidth + dp(2), bounds.top);
            if (boundsWithArrow != null) {
                boundsWithArrow.top -= arrowHeight;
            }
        }
        if (roundWithCornerEffect) {
            path.lineTo(bounds.right, bounds.top);
        } else {
            oval.set(bounds.right - r * 2, bounds.top, bounds.right, bounds.top + r * 2);
            path.arcTo(oval, 270, 90);
        }
        if (direction == DIRECTION_RIGHT) {
            path.lineTo(bounds.right, arrowXY - arrowHalfWidth - dp(2));
            path.lineTo(bounds.right, arrowXY - arrowHalfWidth);
            path.lineTo(bounds.right + arrowHeight, arrowXY - dp(1));
            arrowX = bounds.right + arrowHeight;
            arrowY = arrowXY;
            path.lineTo(bounds.right + arrowHeight, arrowXY + dp(1));
            path.lineTo(bounds.right, arrowXY + arrowHalfWidth);
            path.lineTo(bounds.right, arrowXY + arrowHalfWidth + dp(2));
            if (boundsWithArrow != null) {
                boundsWithArrow.right += arrowHeight;
            }
        }
        if (roundWithCornerEffect) {
            path.lineTo(bounds.right, bounds.bottom);
        } else {
            oval.set(bounds.right - r * 2, bounds.bottom - r * 2, bounds.right, bounds.bottom);
            path.arcTo(oval, 0, 90);
        }
        if (direction == DIRECTION_BOTTOM) {
            path.lineTo(arrowXY + arrowHalfWidth + dp(2), bounds.bottom);
            path.lineTo(arrowXY + arrowHalfWidth, bounds.bottom);
            path.lineTo(arrowXY + dp(1), bounds.bottom + arrowHeight);
            arrowX = arrowXY;
            arrowY = bounds.bottom + arrowHeight;
            path.lineTo(arrowXY - dp(1), bounds.bottom + arrowHeight);
            path.lineTo(arrowXY - arrowHalfWidth, bounds.bottom);
            path.lineTo(arrowXY - arrowHalfWidth - dp(2), bounds.bottom);
            if (boundsWithArrow != null) {
                boundsWithArrow.bottom += arrowHeight;
            }
        }
        path.close();
        pathSet = true;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == textDrawable || who == selectorDrawable || who == icon || super.verifyDrawable(who);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!hideByTouch && !hasOnClickListeners() || !shown) {
            return false;
        }
        if (checkTouchLinks(event)) {
            return true;
        } else if (checkTouchTap(event)) {
            return true;
        }
        return false;
    }

    public boolean containsTouch(MotionEvent ev, float ox, float oy) {
        return bounds.contains(ev.getX() - ox, ev.getY() - oy);
    }

    private boolean checkTouchTap(MotionEvent event) {
        final float x = event.getX(), y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN && containsTouch(event, 0, 0)) {
            bounceX = x;
            bounceY = y;
            bounce.setPressed(true);
            if (selectorDrawable != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                selectorDrawable.setHotspot(x, y);
                selectorDrawable.setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
            }
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (hasOnClickListeners()) {
                performClick();
            } else if (hideByTouch) {
                hide();
            }
            bounce.setPressed(false);
            if (selectorDrawable != null) {
                selectorDrawable.setState(new int[]{});
            }
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            bounce.setPressed(false);
            if (selectorDrawable != null) {
                selectorDrawable.setState(new int[]{});
            }
            return true;
        }
        return false;
    }

    private LinkSpanDrawable.LinksTextView.OnLinkPress onPressListener;
    private LinkSpanDrawable.LinksTextView.OnLinkPress onLongPressListener;

    private LinkSpanDrawable<ClickableSpan> pressedLink;
    private boolean checkTouchLinks(MotionEvent event) {
        if (textLayout != null) {
            ClickableSpan span;
            if ((span = hitLink((int) event.getX(), (int) event.getY())) != null) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    final LinkSpanDrawable link = new LinkSpanDrawable<ClickableSpan>(span, null, event.getX(), event.getY());
                    pressedLink = link;
                    links.addLink(pressedLink);
                    Spannable buffer = new SpannableString(textLayout.getText());
                    int start = buffer.getSpanStart(pressedLink.getSpan());
                    int end = buffer.getSpanEnd(pressedLink.getSpan());
                    LinkPath path = pressedLink.obtainNewPath();
                    path.setCurrentLayout(textLayout, start, 0);
                    textLayout.getSelectionPath(start, end, path);
                    invalidate();
                    AndroidUtilities.runOnUIThread(() -> {
                        if (onLongPressListener != null && pressedLink == link) {
                            onLongPressListener.run(span);
                            pressedLink = null;
                            links.clear();
                        }
                    }, ViewConfiguration.getLongPressTimeout());
                    pause();
                    return true;
                }
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                links.clear();
                invalidate();
                unpause();
                if (pressedLink != null && pressedLink.getSpan() == span) {
                    if (onPressListener != null) {
                        onPressListener.run(pressedLink.getSpan());
                    } else if (pressedLink.getSpan() != null) {
                        pressedLink.getSpan().onClick(this);
                    }
                    pressedLink = null;
                    return true;
                }
                pressedLink = null;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                links.clear();
                invalidate();
                unpause();
                pressedLink = null;
            }
        }
        return pressedLink != null;
    }

    private ClickableSpan hitLink(int x, int y) {
        if (textLayout == null) {
            return null;
        }
        x -= textX;
        y -= textY;
        final int line = textLayout.getLineForVertical(y);
        final int off = textLayout.getOffsetForHorizontal(line, x);
        final float left = textLayout.getLineLeft(line);
        if (left <= x && left + textLayout.getLineWidth(line) >= x && y >= 0 && y <= textLayout.getHeight()) {
            Spannable buffer = new SpannableString(textLayout.getText());
            ClickableSpan[] spans = buffer.getSpans(off, off, ClickableSpan.class);
            if (spans.length != 0 && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
                return spans[0];
            }
        }
        return null;
    }

    private boolean drawingMyBlur;

    private void prepareBlur() {
        if (!useBlur) return;

        drawingMyBlur = true;
        AndroidUtilities.makeGlobalBlurBitmap(bitmap -> {
            drawingMyBlur = false;
            blurBitmapWidth = bitmap.getWidth();
            blurBitmapHeight = bitmap.getHeight();
            blurBitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            blurBitmapMatrix = new Matrix();
            blurBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            blurBackgroundPaint.setShader(blurBitmapShader);
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(1.5f);
            AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? +.12f : -.08f);
            blurBackgroundPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            blurCutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            blurCutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            blurCutPaint.setPathEffect(new CornerPathEffect(rounding));
        }, blurScale);
    }

    private void updateBlurBounds() {
        if (!useBlur) return;
        if (blurBitmapShader == null || blurBitmapMatrix == null) return;

        if (blurPos == null) {
            blurPos = new int[2];
        }
        getLocationOnScreen(blurPos);
        blurBitmapMatrix.reset();
        blurBitmapMatrix.postScale(AndroidUtilities.displaySize.x / (float) blurBitmapWidth, (AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight) / (float) blurBitmapHeight);
        blurBitmapMatrix.postTranslate(-blurPos[0], -blurPos[1]);
        if (show.get() < 1 && useScale) {
            final float scale = 1f / lerp(.5f, 1f, show.get());
            blurBitmapMatrix.postScale(scale, scale, arrowX, arrowY);
        }
        blurBitmapShader.setLocalMatrix(blurBitmapMatrix);
    }

}
