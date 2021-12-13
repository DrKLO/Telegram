package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextSelectionHint extends View {

    StaticLayout textLayout;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    int padding = AndroidUtilities.dp(24);

    private Interpolator interpolator = new OvershootInterpolator();
    private final Theme.ResourcesProvider resourcesProvider;

    float enterValue;
    int start;
    int end;
    int animateToStart;
    int animateToEnd;

    float startOffsetValue;
    float endOffsetValue;
    int currentStart;
    int currentEnd;
    int lastW;

    boolean showing;

    float prepareProgress;
    Animator a;

    Runnable dismissTunnable = this::hideInternal;
    private boolean showOnMeasure;

    public TextSelectionHint(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        int textColor = getThemedColor(Theme.key_undo_infoColor);
        int alpha = Color.alpha(textColor);
        textPaint.setTextSize(AndroidUtilities.dp(15));
        textPaint.setColor(textColor);
        selectionPaint.setColor(textColor);
        selectionPaint.setAlpha((int) (alpha * 0.14));
        setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(6), getThemedColor(Theme.key_undo_background)));
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getMeasuredWidth() != lastW || textLayout == null) {
            if (a != null) {
                a.removeAllListeners();
                a.cancel();
            }

            String text = LocaleController.getString("TextSelectionHit", R.string.TextSelectionHit);
            Pattern pattern = Pattern.compile("\\*\\*.*\\*\\*");
            Matcher matcher = pattern.matcher(text);
            String word = null;
            if (matcher.matches()) {
                word = matcher.group();
            }

            text = text.replace("**", "");

            textLayout = new StaticLayout(text, textPaint, getMeasuredWidth() - padding * 2, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

            start = 0;
            end = 0;
            if (word != null) {
                start = text.indexOf(word);
            }
            if (start > 0) {
                end = start + word.length();
            } else {
                int k = 0;
                for (int i = 0; i < text.length(); i++) {
                    if (text.charAt(i) == ' ') {
                        k++;
                        if (k == 2) {
                            start = i + 1;
                        }
                        if (k == 3) {
                            end = i - 1;
                        }
                    }
                }
            }
            if (end == 0) {
                end = text.length();
            }
            animateToStart = 0;
            animateToEnd = textLayout.getOffsetForHorizontal(textLayout.getLineForOffset(end), textLayout.getWidth() - 1);

            currentStart = start;
            currentEnd = end;

            if (showing) {
                prepareProgress = 1f;
                enterValue = 1f;
                currentStart = animateToStart;
                currentEnd = animateToEnd;
                startOffsetValue = 0;
                endOffsetValue = 0;
            } else if (showOnMeasure) {
                show();
            }

            showOnMeasure = false;
            lastW = getMeasuredWidth();
        }
        int h = textLayout.getHeight() + AndroidUtilities.dp(8) * 2;
        if (h < AndroidUtilities.dp(56)) {
            h = AndroidUtilities.dp(56);
        }
        setMeasuredDimension(getMeasuredWidth(), h);
    }

    Path path = new Path();

    @Override
    protected void onDraw(Canvas canvas) {
        if (textLayout == null) {
            return;
        }
        super.onDraw(canvas);

        canvas.save();
        int topPadding = (getMeasuredHeight() - textLayout.getHeight()) >> 1;
        canvas.translate(padding, topPadding);
        if (enterValue != 0) {
            drawSelection(canvas, textLayout, currentStart, currentEnd);
        }
        textLayout.draw(canvas);

        int handleViewSize = AndroidUtilities.dp(14);

        int line = textLayout.getLineForOffset(currentEnd);
        float x = textLayout.getPrimaryHorizontal(currentEnd);
        int y = textLayout.getLineBottom(line);


        if (currentEnd == animateToEnd) {
            roundedRect(path, textLayout.getPrimaryHorizontal(animateToEnd), textLayout.getLineTop(line), textLayout.getPrimaryHorizontal(animateToEnd) + AndroidUtilities.dpf2(4), textLayout.getLineBottom(line), AndroidUtilities.dpf2(4), AndroidUtilities.dpf2(4), false, true);
            canvas.drawPath(path, selectionPaint);
        }

        float enterProgress = interpolator.getInterpolation(enterValue);
        int xOffset = (int) (textLayout.getPrimaryHorizontal(animateToEnd) + (AndroidUtilities.dpf2(4) * (1f - endOffsetValue)) + (textLayout.getPrimaryHorizontal(end) - textLayout.getPrimaryHorizontal(animateToEnd)) * endOffsetValue);
        canvas.save();
        canvas.translate(xOffset, y);

        canvas.scale(enterProgress, enterProgress, handleViewSize / 2f, handleViewSize / 2f);
        path.reset();
        path.addCircle(handleViewSize / 2f, handleViewSize / 2f, handleViewSize / 2f, Path.Direction.CCW);
        path.addRect(0, 0, handleViewSize / 2f, handleViewSize / 2f, Path.Direction.CCW);
        canvas.drawPath(path, textPaint);
        canvas.restore();

        line = textLayout.getLineForOffset(currentStart);
        x = textLayout.getPrimaryHorizontal(currentStart);
        y = textLayout.getLineBottom(line);

        if (currentStart == animateToStart) {
            roundedRect(path, -AndroidUtilities.dp(4), textLayout.getLineTop(line), 0, textLayout.getLineBottom(line), AndroidUtilities.dp(4), AndroidUtilities.dp(4), true, false);
            canvas.drawPath(path, selectionPaint);
        }

        canvas.save();
        xOffset = (int) (textLayout.getPrimaryHorizontal(animateToStart) - (AndroidUtilities.dp(4) * (1f - startOffsetValue)) + (textLayout.getPrimaryHorizontal(start) - textLayout.getPrimaryHorizontal(animateToStart)) * startOffsetValue);
        canvas.translate(xOffset - handleViewSize, y);


        canvas.scale(enterProgress, enterProgress, handleViewSize / 2f, handleViewSize / 2f);

        path.reset();
        path.addCircle(handleViewSize / 2f, handleViewSize / 2f, handleViewSize / 2f, Path.Direction.CCW);
        path.addRect(handleViewSize / 2f, 0, handleViewSize, handleViewSize / 2f, Path.Direction.CCW);
        canvas.drawPath(path, textPaint);
        canvas.restore();
        canvas.restore();

    }

    private void roundedRect(Path path, float left, float top, float right, float bottom, float rx, float ry,
                             boolean tl, boolean tr) {
        path.reset();
        if (rx < 0) rx = 0;
        if (ry < 0) ry = 0;
        float width = right - left;
        float height = bottom - top;
        if (rx > width / 2) rx = width / 2;
        if (ry > height / 2) ry = height / 2;
        float widthMinusCorners = (width - (2 * rx));
        float heightMinusCorners = (height - (2 * ry));

        path.moveTo(right, top + ry);
        if (tr)
            path.rQuadTo(0, -ry, -rx, -ry);
        else {
            path.rLineTo(0, -ry);
            path.rLineTo(-rx, 0);
        }
        path.rLineTo(-widthMinusCorners, 0);
        if (tl)
            path.rQuadTo(-rx, 0, -rx, ry);
        else {
            path.rLineTo(-rx, 0);
            path.rLineTo(0, ry);
        }
        path.rLineTo(0, heightMinusCorners);
        path.rLineTo(0, ry);
        path.rLineTo(rx, 0);
        path.rLineTo(widthMinusCorners, 0);
        path.rLineTo(rx, 0);
        path.rLineTo(0, -ry);
        path.rLineTo(0, -heightMinusCorners);
        path.close();
    }

    private void drawSelection(Canvas canvas, StaticLayout layout, int selectionStart, int selectionEnd) {
        int startLine = layout.getLineForOffset(selectionStart);
        int endLine = layout.getLineForOffset(selectionEnd);
        int startX = (int) layout.getPrimaryHorizontal(selectionStart);
        int endX = (int) layout.getPrimaryHorizontal(selectionEnd);
        if (startLine == endLine) {
            canvas.drawRect(startX, layout.getLineTop(startLine), endX, layout.getLineBottom(startLine), selectionPaint);
        } else {
            canvas.drawRect(startX, layout.getLineTop(startLine), layout.getLineWidth(startLine), layout.getLineBottom(startLine), selectionPaint);
            canvas.drawRect(0, layout.getLineTop(endLine), endX, layout.getLineBottom(endLine), selectionPaint);
            for (int i = startLine + 1; i < endLine; i++) {
                canvas.drawRect(0, layout.getLineTop(i), layout.getLineWidth(i), layout.getLineBottom(i), selectionPaint);
            }
        }
    }


    public void show() {
        AndroidUtilities.cancelRunOnUIThread(dismissTunnable);
        if (a != null) {
            a.removeAllListeners();
            a.cancel();
        }
        if (getMeasuredHeight() == 0 || getMeasuredWidth() == 0) {
            showOnMeasure = true;
            return;
        }
        showing = true;
        setVisibility(View.VISIBLE);
        prepareProgress = 0;
        enterValue = 0;
        currentStart = start;
        currentEnd = end;
        startOffsetValue = 1f;
        endOffsetValue = 1f;
        invalidate();

        ValueAnimator prepareAnimation = ValueAnimator.ofFloat(0, 1f);
        prepareAnimation.addUpdateListener(animation -> {
            prepareProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        prepareAnimation.setDuration(210);
        prepareAnimation.setInterpolator(new DecelerateInterpolator());

        ValueAnimator enterAnimation = ValueAnimator.ofFloat(0, 1f);
        enterAnimation.addUpdateListener(animation -> {
            enterValue = (float) animation.getAnimatedValue();
            invalidate();
        });
        enterAnimation.setStartDelay(600);
        enterAnimation.setDuration(250);

        ValueAnimator moveStart = ValueAnimator.ofFloat(1f, 0f);
        moveStart.setStartDelay(500);
        moveStart.addUpdateListener(animation -> {
            startOffsetValue = (float) animation.getAnimatedValue();
            currentStart = (int) (animateToStart + ((start - animateToStart) * startOffsetValue));
            invalidate();
        });

        moveStart.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        moveStart.setDuration(500);

        ValueAnimator moveEnd = ValueAnimator.ofFloat(1f, 0f);
        moveEnd.setStartDelay(400);
        moveEnd.addUpdateListener(animation -> {
            endOffsetValue = (float) animation.getAnimatedValue();
            currentEnd = animateToEnd + (int) Math.ceil((end - animateToEnd) * endOffsetValue);
            invalidate();
        });

        moveEnd.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        moveEnd.setDuration(900);

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(
                prepareAnimation,
                enterAnimation,
                moveStart,
                moveEnd
        );
        a = set;
        a.start();

        AndroidUtilities.runOnUIThread(dismissTunnable, 5000);
    }

    public void hide() {
        AndroidUtilities.cancelRunOnUIThread(dismissTunnable);
        hideInternal();
    }

    private void hideInternal() {
        if (a != null) {
            a.removeAllListeners();
            a.cancel();
        }
        showing = false;

        ValueAnimator animator = ValueAnimator.ofFloat(prepareProgress, 0f);
        animator.addUpdateListener(animation -> {
            prepareProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(View.INVISIBLE);
            }
        });

        a = animator;
        a.start();
    }

    public float getPrepareProgress() {
        return prepareProgress;
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
