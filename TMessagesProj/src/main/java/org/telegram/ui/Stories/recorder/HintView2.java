package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;

public class HintView2 extends View {

    // direction of an arrow to point
    // the gravity of location would be the same, location of arrow opposite:
    // f.ex. hint with DIRECTION_LEFT has arrow on right and would be forced to the left ({view} <{hint})
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
    private int textMaxWidth = -1;

    private Drawable closeButtonDrawable;
    private boolean closeButton;

    private float rounding = dp(8);
    private final RectF innerPadding = new RectF(dp(11), dp(6), dp(11), dp(7));
    private float closeButtonMargin = dp(2);
    private float arrowHalfWidth = dp(7);
    private float arrowHeight = dp(6);

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private CharSequence textToSet;
    private AnimatedTextView.AnimatedTextDrawable textDrawable;

    private boolean multiline;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Layout.Alignment textLayoutAlignment = Layout.Alignment.ALIGN_NORMAL;
    private StaticLayout textLayout;
    private float textLayoutLeft, textLayoutWidth;
    private LinkSpanDrawable.LinkCollector links = new LinkSpanDrawable.LinkCollector();
    private float textX, textY;

    private boolean hideByTouch = true;
    private boolean repeatedBounce = true;

    private boolean shown;
    private AnimatedFloat show = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    public HintView2(Context context, int direction) {
        super(context);
        this.direction = direction;

        backgroundPaint.setColor(0xcc282828);
        backgroundPaint.setPathEffect(new CornerPathEffect(rounding));

        textDrawable = new AnimatedTextView.AnimatedTextDrawable(true, true, false);
        textDrawable.setAnimationProperties(.4f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setCallback(this);

        setTextSize(14);
        setTextColor(0xffffffff);
    }

    public HintView2 setRounding(float roundingDp) {
        this.rounding = dp(roundingDp);
        backgroundPaint.setPathEffect(new CornerPathEffect(rounding));
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

    public HintView2 setTextSize(int sizeDp) {
        textDrawable.setTextSize(dp(sizeDp));
        textPaint.setTextSize(dp(sizeDp));
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

    private static boolean contains(CharSequence text, char c) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); ++i) {
            if (text.charAt(i) == c) {
                return true;
            }
        }
        return false;
    }

    private static int getTextWidth(CharSequence text, TextPaint paint) {
        if (text instanceof Spannable) {
            StaticLayout layout = new StaticLayout(text, paint, 99999, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            if (layout.getLineCount() > 0)
                return (int) Math.ceil(layout.getLineWidth(0));
            return 0;
        }
        return (int) paint.measureText(text.toString());
    }

    // returns max width
    public static int cutInFancyHalf(CharSequence text, TextPaint paint) {
        if (text == null) {
            return 0;
        }
        float fullLineWidth = getTextWidth(text, paint);
        final int L = text.toString().length(), m = L / 2;
        if (L <= 0 || contains(text, '\n')) {
            return (int) Math.ceil(fullLineWidth);
        }
        int l = m - 1, r = m + 1;
        int c = m;
        while (l >= 0 && r < L) {
            if (text.charAt(l) == ' ') {
                c = l;
                break;
            }
            if (text.charAt(r) == ' ') {
                c = r;
                break;
            }
            l--;
            r++;
        }
        return (int) Math.ceil(Math.max(fullLineWidth * .3f, Math.max(c + .5f, L - c + .5f) / (float) L * fullLineWidth));
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
    public HintView2 setInnerPadding(int leftDp, int topDp, int rightDp, int bottomDp) {
        innerPadding.set(dp(leftDp), dp(topDp), dp(rightDp), dp(bottomDp));
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
        backgroundPaint.setColor(color);
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

    public TextPaint getTextPaint() {
        if (multiline) {
            return textPaint;
        } else {
            return textDrawable.getPaint();
        }
    }

    private final Runnable hideRunnable = this::hide;

    public HintView2 show() {
        if (shown) {
            bounceShow();
        }
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
        textLayoutLeft = left;
    }

    private final ButtonBounce bounce = new ButtonBounce(this, 2f);
    private float bounceX, bounceY;

    private final RectF bounds = new RectF();
    private final Path path = new Path();
    private float arrowX, arrowY;
    private float pathLastWidth, pathLastHeight;
    private boolean pathSet;
    private boolean firstDraw = true;

    @Override
    protected void dispatchDraw(Canvas canvas) {
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
        float contentHeight = multiline ? (textLayout == null ? 0 : textLayout.getHeight()) : textDrawable.getHeight();
        if (closeButton) {
            if (closeButtonDrawable == null) {
                closeButtonDrawable = getContext().getResources().getDrawable(R.drawable.msg_mini_close_tooltip).mutate();
                closeButtonDrawable.setColorFilter(new PorterDuffColorFilter(0x7dffffff, PorterDuff.Mode.MULTIPLY));
            }
            contentWidth += closeButtonMargin + closeButtonDrawable.getIntrinsicWidth();
            contentHeight = Math.max(closeButtonDrawable.getIntrinsicHeight(), contentHeight);
        }

        final float width = innerPadding.left + contentWidth + innerPadding.right;
        final float height = innerPadding.top + contentHeight + innerPadding.bottom;
        if (!pathSet || Math.abs(width - pathLastWidth) > 0.1f || Math.abs(height - pathLastHeight) > 0.1f) {
            rewindPath(pathLastWidth = width, pathLastHeight = height);
        }

        float alpha = useAlpha ? showT : 1;
        canvas.save();
        if (showT < 1 && useScale) {
            final float scale = lerp(.5f, 1f, showT);
            canvas.scale(scale, scale, arrowX, arrowY);
        }
        float bounceScale = bounce.getScale(.025f);
        if (bounceScale != 1) {
//            canvas.scale(bounceScale, bounceScale, bounceX, bounceY);
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

        final int wasAlpha = backgroundPaint.getAlpha();
        backgroundPaint.setAlpha((int) (wasAlpha * alpha));
        canvas.drawPath(path, backgroundPaint);
        backgroundPaint.setAlpha(wasAlpha);

        if (multiline) {
            canvas.saveLayerAlpha(0, 0, getWidth(), Math.max(getHeight(), height), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
            canvas.translate(textX = bounds.left + innerPadding.left - textLayoutLeft, textY = bounds.top + innerPadding.top);
            if (links.draw(canvas)) {
                invalidate();
            }
            textLayout.draw(canvas);
            canvas.restore();
        } else {
            if (textToSet != null) {
                textDrawable.setText(textToSet, shown);
                textToSet = null;
            }
            textDrawable.setBounds((int) (bounds.left + innerPadding.left), (int) (bounds.top + innerPadding.top), (int) (bounds.left + innerPadding.left + contentWidth), (int) (bounds.bottom - innerPadding.bottom));
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

    private void rewindPath(float width, float height) {
        float arrowXY;
        if (direction == DIRECTION_TOP || direction == DIRECTION_BOTTOM) {
            arrowXY = lerp(getPaddingLeft(), getMeasuredWidth() - getPaddingRight(), joint);
            arrowXY = Utilities.clamp(arrowXY + jointTranslate, getMeasuredWidth() - getPaddingRight(), getPaddingLeft());
            float left = Math.max(getPaddingLeft(), arrowXY - width / 2f);
            float right = Math.min(left + width, getMeasuredWidth() - getPaddingRight());
            left = right - width;
            arrowXY = Utilities.clamp(arrowXY, right - rounding - arrowHalfWidth, left + rounding + arrowHalfWidth);
            if (direction == DIRECTION_TOP) {
                bounds.set(left, getPaddingTop() + arrowHeight, right, getPaddingTop() + arrowHeight + height);
            } else {
                bounds.set(left, getMeasuredHeight() - arrowHeight - getPaddingBottom() - height, right, getMeasuredHeight() - arrowHeight - getPaddingBottom());
            }
        } else {
            arrowXY = lerp(getPaddingTop(), getMeasuredHeight() - getPaddingBottom(), joint);
            arrowXY = Utilities.clamp(arrowXY + jointTranslate, getMeasuredHeight() - getPaddingBottom(), getPaddingTop());
            float top = Math.max(getPaddingTop(), arrowXY - height / 2f);
            float bottom = Math.min(top + height, getMeasuredHeight() - getPaddingBottom());
            top = bottom - height;
            arrowXY = Utilities.clamp(arrowXY, bottom - rounding - arrowHalfWidth, top + rounding + arrowHalfWidth);
            if (direction == DIRECTION_LEFT) {
                bounds.set(getPaddingLeft() + arrowHeight, top, getPaddingLeft() + arrowHeight + width, bottom);
            } else {
                bounds.set(getMeasuredWidth() - getPaddingRight() - arrowHeight - width, top, getMeasuredWidth() - getPaddingRight() - arrowHeight, bottom);
            }
        }

        path.rewind();
        path.moveTo(bounds.left, bounds.bottom);
        if (direction == DIRECTION_LEFT) {
            path.lineTo(bounds.left, arrowXY + arrowHalfWidth + dp(2));
            path.lineTo(bounds.left, arrowXY + arrowHalfWidth);
            path.lineTo(bounds.left - arrowHeight, arrowXY + dp(1));
            arrowX = bounds.left - arrowHeight;
            arrowY = arrowXY;
            path.lineTo(bounds.left - arrowHeight, arrowXY - dp(1));
            path.lineTo(bounds.left, arrowXY - arrowHalfWidth);
            path.lineTo(bounds.left, arrowXY - arrowHalfWidth - dp(2));
        }
        path.lineTo(bounds.left, bounds.top);
        if (direction == DIRECTION_TOP) {
            path.lineTo(arrowXY - arrowHalfWidth - dp(2), bounds.top);
            path.lineTo(arrowXY - arrowHalfWidth, bounds.top);
            path.lineTo(arrowXY - dp(1), bounds.top - arrowHeight);
            arrowX = arrowXY;
            arrowY = bounds.top - arrowHeight;
            path.lineTo(arrowXY + dp(1), bounds.top - arrowHeight);
            path.lineTo(arrowXY + arrowHalfWidth, bounds.top);
            path.lineTo(arrowXY + arrowHalfWidth + dp(2), bounds.top);
        }
        path.lineTo(bounds.right, bounds.top);
        if (direction == DIRECTION_RIGHT) {
            path.lineTo(bounds.right, arrowXY - arrowHalfWidth - dp(2));
            path.lineTo(bounds.right, arrowXY - arrowHalfWidth);
            path.lineTo(bounds.right + arrowHeight, arrowXY - dp(1));
            arrowX = bounds.right + arrowHeight;
            arrowY = arrowXY;
            path.lineTo(bounds.right + arrowHeight, arrowXY + dp(1));
            path.lineTo(bounds.right, arrowXY + arrowHalfWidth);
            path.lineTo(bounds.right, arrowXY + arrowHalfWidth + dp(2));
        }
        path.lineTo(bounds.right, bounds.bottom);
        if (direction == DIRECTION_BOTTOM) {
            path.lineTo(arrowXY + arrowHalfWidth + dp(2), bounds.bottom);
            path.lineTo(arrowXY + arrowHalfWidth, bounds.bottom);
            path.lineTo(arrowXY + dp(1), bounds.bottom + arrowHeight);
            arrowX = arrowXY;
            arrowY = bounds.bottom + arrowHeight;
            path.lineTo(arrowXY - dp(1), bounds.bottom + arrowHeight);
            path.lineTo(arrowXY - arrowHalfWidth, bounds.bottom);
            path.lineTo(arrowXY - arrowHalfWidth - dp(2), bounds.bottom);
        }
        path.close();
        pathSet = true;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == textDrawable || super.verifyDrawable(who);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!hideByTouch || !shown) {
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
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            hide();
            bounce.setPressed(false);
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            bounce.setPressed(false);
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
}
