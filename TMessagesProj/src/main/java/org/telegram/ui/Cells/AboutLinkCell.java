/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.concurrent.atomic.AtomicReference;

public class AboutLinkCell extends FrameLayout {

    private StaticLayout textLayout;
    private String oldText;
    private int textX;
    private int textY;
    private SpannableStringBuilder stringBuilder;
    private TextView valueTextView;
    private TextView showMoreTextView;
    private FrameLayout showMoreTextBackgroundView;
    private FrameLayout bottomShadow;
    private Drawable showMoreBackgroundDrawable;

    private ClickableSpan pressedLink;
    private Point urlPathOffset = new Point();
    private LinkPath urlPath = new LinkPath(true);

    private BaseFragment parentFragment;

    private FrameLayout container;
    private Drawable rippleBackground;

    public AboutLinkCell(Context context, BaseFragment fragment) {
        super(context);

        parentFragment = fragment;

        container = new FrameLayout(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                boolean result = false;
                if (textLayout != null || nextLinesLayouts != null) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN || pressedLink != null && event.getAction() == MotionEvent.ACTION_UP) {
                        if (x >= showMoreTextView.getLeft() && x <= showMoreTextView.getRight() &&
                            y >= showMoreTextView.getTop() &&  y <= showMoreTextView.getBottom()) {
                            return super.onTouchEvent(event);
                        }
                        if (getMeasuredWidth() > 0 && x > getMeasuredWidth() - AndroidUtilities.dp(23)) {
                            return super.onTouchEvent(event);
                        }
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            if (firstThreeLinesLayout != null && expandT < 1 && shouldExpand) {
                                if (checkTouchTextLayout(firstThreeLinesLayout, textX, textY, x, y)) {
                                    result = true;
                                } else if (nextLinesLayouts != null) {
                                    for (int i = 0; i < nextLinesLayouts.length; ++i) {
                                        if (checkTouchTextLayout(nextLinesLayouts[i], nextLinesLayoutsPositions[i].x, nextLinesLayoutsPositions[i].y, x, y)) {
                                            result = true;
                                            break;
                                        }
                                    }
                                }
                            } else if (checkTouchTextLayout(textLayout, textX, textY, x, y)) {
                                result = true;
                            }
                            if (!result) {
                                resetPressedLink();
                            }
                        } else if (pressedLink != null) {
                            try {
                                onLinkClick(pressedLink);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            resetPressedLink();
                            result = true;
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                        resetPressedLink();
                    }
                }
                return result || super.onTouchEvent(event);
            }
        };
        container.setClickable(true);
        rippleBackground = Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0, 0);

        valueTextView = new TextView(context);
        valueTextView.setVisibility(GONE);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        container.addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 23, 0, 23, 10));

        bottomShadow = new FrameLayout(context);
        Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.gradient_bottom).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhite), PorterDuff.Mode.SRC_ATOP));
        bottomShadow.setBackground(shadowDrawable);
        addView(bottomShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 12, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        showMoreTextView = new TextView(context) {
            private boolean pressed = false;
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                boolean wasPressed = pressed;
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    pressed = true;
                } else if (event.getAction() != MotionEvent.ACTION_MOVE) {
                    pressed = false;
                }
                if (wasPressed != pressed) {
                    invalidate();
                }
                return super.onTouchEvent(event);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (pressed) {
                    AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_urlPaint);
                }
                super.onDraw(canvas);
            }
        };
        showMoreTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        showMoreTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        showMoreTextView.setLines(1);
        showMoreTextView.setMaxLines(1);
        showMoreTextView.setSingleLine(true);
        showMoreTextView.setText(LocaleController.getString("DescriptionMore", R.string.DescriptionMore));
        showMoreTextView.setOnClickListener(e -> {
            updateCollapse(true, true);
        });
        showMoreTextView.setPadding(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(2), 0);
        showMoreTextBackgroundView = new FrameLayout(context);
        showMoreBackgroundDrawable = context.getResources().getDrawable(R.drawable.gradient_left).mutate();
        showMoreBackgroundDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhite), PorterDuff.Mode.MULTIPLY));
        showMoreTextBackgroundView.setBackground(showMoreBackgroundDrawable);
        showMoreTextBackgroundView.setPadding(
            showMoreTextBackgroundView.getPaddingLeft() + AndroidUtilities.dp(4),
            AndroidUtilities.dp(1),
            0,
            AndroidUtilities.dp(3)
        );
        showMoreTextBackgroundView.addView(showMoreTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        addView(showMoreTextBackgroundView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM, 22 - showMoreTextBackgroundView.getPaddingLeft() / AndroidUtilities.density, 0, 22 - showMoreTextBackgroundView.getPaddingRight() / AndroidUtilities.density, 6));
        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        setWillNotDraw(false);
    }

    private void setShowMoreMarginBottom(int marginBottom) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) showMoreTextBackgroundView.getLayoutParams();
        if (lp.bottomMargin != marginBottom) {
            lp.bottomMargin = marginBottom;
            showMoreTextBackgroundView.setLayoutParams(lp);
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        return false;
    }

    private Paint backgroundPaint = new Paint();
    @Override
    public void draw(Canvas canvas) {
        View parent = (View) getParent();
        float alpha = parent == null ? 1f : (float) Math.pow(parent.getAlpha(), 2f);

        drawText(canvas);

        float viewAlpha = showMoreTextBackgroundView.getAlpha();
        if (viewAlpha > 0) {
            canvas.save();
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (viewAlpha * 255), Canvas.ALL_SAVE_FLAG);
            showMoreBackgroundDrawable.setAlpha((int) (alpha * 255));
            canvas.translate(showMoreTextBackgroundView.getLeft(), showMoreTextBackgroundView.getTop());
            showMoreTextBackgroundView.draw(canvas);
            canvas.restore();
        }
        viewAlpha = bottomShadow.getAlpha();
        if (viewAlpha > 0) {
            canvas.save();
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (viewAlpha * 255), Canvas.ALL_SAVE_FLAG);
            canvas.translate(bottomShadow.getLeft(), bottomShadow.getTop());
            bottomShadow.draw(canvas);
            canvas.restore();
        }

        container.draw(canvas);

        super.draw(canvas);
    }

    final float SPACE = AndroidUtilities.dp(3f);
    private void drawText(Canvas canvas) {
        canvas.save();
        AndroidUtilities.rectTmp.set(AndroidUtilities.dp(23 - 8), AndroidUtilities.dp(8), getWidth() - AndroidUtilities.dp(23), getHeight());
        canvas.clipRect(AndroidUtilities.rectTmp);
        if (pressedLink != null) {
            canvas.save();
            canvas.translate(urlPathOffset.x, urlPathOffset.y);
            canvas.drawPath(urlPath, Theme.linkSelectionPaint);
            canvas.restore();
        }
        canvas.translate(textX = AndroidUtilities.dp(23), textY = AndroidUtilities.dp(8));

        try {
            if (firstThreeLinesLayout == null || !shouldExpand) {
                if (textLayout != null) {
                    textLayout.draw(canvas);
                }
            } else {
                firstThreeLinesLayout.draw(canvas);
                int lastLine = firstThreeLinesLayout.getLineCount() - 1;
                float top = firstThreeLinesLayout.getLineTop(lastLine) + firstThreeLinesLayout.getTopPadding();
                float x = firstThreeLinesLayout.getLineRight(lastLine) + (needSpace ? SPACE : 0),
                      y = firstThreeLinesLayout.getLineBottom(lastLine) - firstThreeLinesLayout.getLineTop(lastLine) - firstThreeLinesLayout.getBottomPadding();
                float t = easeInOutCubic(1f - (float) Math.pow(expandT, 0.25f));
                if (nextLinesLayouts != null) {
                    for (int line = 0; line < nextLinesLayouts.length; ++line) {
                        final StaticLayout layout = nextLinesLayouts[line];
                        if (layout != null) {
                            final int c = canvas.save();
                            if (nextLinesLayoutsPositions[line] != null) {
                                nextLinesLayoutsPositions[line].set((int) (textX + x * t), (int) (textY + top + y * (1f - t)));
                            }
                            if (lastInlineLine != -1 && lastInlineLine <= line) {
                                canvas.translate(0, top + y);
                                canvas.saveLayerAlpha(0, 0, layout.getWidth(), layout.getHeight(), (int) (255 * expandT), Canvas.ALL_SAVE_FLAG);
                            } else {
                                canvas.translate(x * t, top + y * (1f - t));
                            }
                            layout.draw(canvas);
                            canvas.restoreToCount(c);
                            x += layout.getLineRight(0) + SPACE;
                            y += layout.getLineBottom(0) + layout.getTopPadding();
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        canvas.restore();
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        container.setOnClickListener(l);
    }

    protected void didPressUrl(String url) {

    }
    protected void didResizeStart() {

    }
    protected void didResizeEnd() {

    }
    protected void didExtend() {}

    private void resetPressedLink() {
        if (pressedLink != null) {
            pressedLink = null;
            container.invalidate();
        }
        AndroidUtilities.cancelRunOnUIThread(longPressedRunnable);
        invalidate();
    }

    public void setText(String text, boolean parseLinks) {
        setTextAndValue(text, null, parseLinks);
    }

    public void setTextAndValue(String text, String value, boolean parseLinks) {
        if (TextUtils.isEmpty(text) || TextUtils.equals(text, oldText)) {
            return;
        }
        try {
            oldText = AndroidUtilities.getSafeString(text);
        } catch (Throwable e) {
            oldText = text;
        }
        stringBuilder = new SpannableStringBuilder(oldText);
        MessageObject.addLinks(false, stringBuilder, false, false, !parseLinks);
        Emoji.replaceEmoji(stringBuilder, Theme.profile_aboutTextPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
        if (lastMaxWidth <= 0) {
            lastMaxWidth = AndroidUtilities.displaySize.x - AndroidUtilities.dp(23 + 23);
        }
        checkTextLayout(lastMaxWidth, true);
        updateHeight();
        if (TextUtils.isEmpty(value)) {
            valueTextView.setVisibility(GONE);
        } else {
            valueTextView.setText(value);
            valueTextView.setVisibility(VISIBLE);
        }
        requestLayout();
    }

    Runnable longPressedRunnable = new Runnable() {
        @Override
        public void run() {
            if (pressedLink != null) {
                String url;
                if (pressedLink instanceof URLSpanNoUnderline) {
                    url = ((URLSpanNoUnderline) pressedLink).getURL();
                } else if (pressedLink instanceof URLSpan) {
                    url = ((URLSpan) pressedLink).getURL();
                } else {
                    url = pressedLink.toString();
                }

                ClickableSpan pressedLinkFinal = pressedLink;
                BottomSheet.Builder builder = new BottomSheet.Builder(parentFragment.getParentActivity());
                builder.setTitle(url);
                builder.setItems(new CharSequence[]{LocaleController.getString("Open", R.string.Open), LocaleController.getString("Copy", R.string.Copy)}, (dialog, which) -> {
                    if (which == 0) {
                        onLinkClick(pressedLinkFinal);
                    } else if (which == 1) {
                        AndroidUtilities.addToClipboard(url);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            if (url.startsWith("@")) {
                                BulletinFactory.of(parentFragment).createSimpleBulletin(R.raw.copy, LocaleController.getString("UsernameCopied", R.string.UsernameCopied)).show();
                            } else if (url.startsWith("#") || url.startsWith("$")) {
                                BulletinFactory.of(parentFragment).createSimpleBulletin(R.raw.copy, LocaleController.getString("HashtagCopied", R.string.HashtagCopied)).show();
                            } else {
                                BulletinFactory.of(parentFragment).createSimpleBulletin(R.raw.copy, LocaleController.getString("LinkCopied", R.string.LinkCopied)).show();
                            }
                        }
                    }
                });
                builder.show();
                resetPressedLink();
            }
        }
    };

    private boolean checkTouchTextLayout(StaticLayout textLayout, int textX, int textY, int ex, int ey) {
        try {
            int x = (int) (ex - textX);
            int y = (int) (ey - textY);
            final int line = textLayout.getLineForVertical(y);
            final int off = textLayout.getOffsetForHorizontal(line, x);

            final float left = textLayout.getLineLeft(line);
            if (left <= x && left + textLayout.getLineWidth(line) >= x && y >= 0 && y <= textLayout.getHeight()) {
                Spannable buffer = (Spannable) textLayout.getText();
                ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                if (link.length != 0) {
                    resetPressedLink();
                    pressedLink = link[0];
                    try {
                        int start = buffer.getSpanStart(pressedLink);
                        urlPathOffset.set(textX, textY);
                        urlPath.setCurrentLayout(textLayout, start, 0);
                        textLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink), urlPath);
                        urlPath.onPathEnd();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    container.invalidate();
                    AndroidUtilities.runOnUIThread(longPressedRunnable, ViewConfiguration.getLongPressTimeout());
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private void onLinkClick(ClickableSpan pressedLink) {
        if (pressedLink instanceof URLSpanNoUnderline) {
            String url = ((URLSpanNoUnderline) pressedLink).getURL();
            if (url.startsWith("@") || url.startsWith("#") || url.startsWith("/")) {
                didPressUrl(url);
            }
        } else {
            if (pressedLink instanceof URLSpan) {
                String url = ((URLSpan) pressedLink).getURL();
                if (AndroidUtilities.shouldShowUrlInAlert(url)) {
                    AlertsCreator.showOpenUrlAlert(parentFragment, url, true, true);
                } else {
                    Browser.openUrl(getContext(), url);
                }
            } else {
                pressedLink.onClick(this);
            }
        }
    }

    private static final int COLLAPSED_HEIGHT = AndroidUtilities.dp(8 + 20 * 3 + 8);
    private static final int MAX_OPEN_HEIGHT = COLLAPSED_HEIGHT;// + AndroidUtilities.dp(20);

    private class SpringInterpolator {
        public float tension;
        public float friction;
        public SpringInterpolator(float tension, float friction) {
            this.tension = tension;
            this.friction = friction;
        }

        private final float mass = 1f;
        private float position = 0, velocity = 0;
        public float getValue(float deltaTime) {
            deltaTime = Math.min(deltaTime, 250);
            final float MAX_DELTA_TIME = 18;
            while (deltaTime > 0) {
                final float step = Math.min(deltaTime, MAX_DELTA_TIME);
                step(step);
                deltaTime -= step;
            }
            return position;
        }

        private void step(float delta) {
            final float acceleration = (
                -tension * 0.000001f * (position - 1f) + // spring force
                -friction * 0.001f * velocity // damping force
            ) / mass; // pt/ms^2

            velocity = velocity + acceleration * delta; // pt/ms
            position = position + velocity * delta;
        }
    }

    private float expandT = 0f;
    private float rawCollapseT = 0f;
    private ValueAnimator collapseAnimator;
    private boolean expanded = false;
    public void updateCollapse(boolean value, boolean animated) {
        if (collapseAnimator != null) {
            collapseAnimator.cancel();
            collapseAnimator = null;
        }

        float fromValue = expandT,
              toValue   = value ? 1f : 0f;
        if (animated) {
            if (toValue > 0) {
                didExtend();
            }

            float fullHeight = textHeight();
            float collapsedHeight = Math.min(COLLAPSED_HEIGHT, fullHeight);
            float fromHeight = AndroidUtilities.lerp(collapsedHeight, fullHeight, fromValue);
            float toHeight = AndroidUtilities.lerp(collapsedHeight, fullHeight, toValue);
            float dHeight = Math.abs(toHeight - fromHeight);
//            float speedMultiplier = Math.min(Math.max(dHeight / AndroidUtilities.dp(76), 0.5f), 2f);

            collapseAnimator = ValueAnimator.ofFloat(0, 1);
            final float duration = Math.abs(fromValue - toValue) * 1250 * 2f;
            final SpringInterpolator spring = new SpringInterpolator(380f, 20.17f);
            final AtomicReference<Float> lastValue = new AtomicReference<>(fromValue);
            collapseAnimator.addUpdateListener(a -> {
                float now = (float) a.getAnimatedValue();
                float deltaTime = (now - lastValue.getAndSet(now)) * 1000 * 8f;

                rawCollapseT = AndroidUtilities.lerp(fromValue, toValue, (float) a.getAnimatedValue());
                expandT = AndroidUtilities.lerp(fromValue, toValue, spring.getValue(deltaTime));
                if (expandT > 0.8f && container.getBackground() == null) {
                    container.setBackground(rippleBackground);
                }
                showMoreTextBackgroundView.setAlpha(1f - expandT);
                bottomShadow.setAlpha((float) Math.pow(1f - expandT, 2f));

                updateHeight();
                container.invalidate();
            });
            collapseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    didResizeEnd();
                    if (container.getBackground() == null) {
                        container.setBackground(rippleBackground);
                    }
                    expanded = true;
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    didResizeStart();
                }
            });
            collapseAnimator.setDuration((long) duration);
            collapseAnimator.start();
        } else {
            expandT = toValue;
            forceLayout();
        }
    }

    private int updateHeight() {
        int textHeight = textHeight();
        float fromHeight = Math.min(COLLAPSED_HEIGHT, textHeight);
        int height = shouldExpand ? (int) AndroidUtilities.lerp(fromHeight, textHeight, expandT) : textHeight;
        setHeight(height);
        return height;
    }
    private void setHeight(int height) {
        RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) getLayoutParams();
        int wasHeight;
        boolean newHeight;
        if (lp == null) {
            newHeight = true;
            wasHeight = (getMinimumHeight() == 0 ? getHeight() : getMinimumHeight());
            lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        } else {
            wasHeight = lp.height;
            newHeight = wasHeight != height;
            lp.height = height;
        }
        if (newHeight) {
            setLayoutParams(lp);
        }
    }

    private static final int MOST_SPEC = View.MeasureSpec.makeMeasureSpec(999999, View.MeasureSpec.AT_MOST);
    private int lastMaxWidth = 0;
    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        checkTextLayout(MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(23 + 23), false);
        int height = updateHeight();
        super.onMeasure(
            widthMeasureSpec,
//            heightMeasureSpec
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        );
    }

    private StaticLayout makeTextLayout(CharSequence string, int width) {
        if (Build.VERSION.SDK_INT >= 24) {
            return StaticLayout.Builder.obtain(string, 0, string.length(), Theme.profile_aboutTextPaint, width)
                    .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                    .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                    .setAlignment(LocaleController.isRTL ? StaticLayoutEx.ALIGN_RIGHT() : StaticLayoutEx.ALIGN_LEFT())
                    .build();
        } else {
            return new StaticLayout(string, Theme.profile_aboutTextPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
    }

    private StaticLayout firstThreeLinesLayout;
    private StaticLayout[] nextLinesLayouts = null;
    private int lastInlineLine = -1;
    private Point[] nextLinesLayoutsPositions;
    private boolean needSpace = false;
    private void checkTextLayout(int maxWidth, boolean force) {
        if (stringBuilder != null && (maxWidth != lastMaxWidth || force)) {
            textLayout = makeTextLayout(stringBuilder, maxWidth);
            shouldExpand = textLayout.getLineCount() >= 4 && valueTextView.getVisibility() != View.VISIBLE;

            if (textLayout.getLineCount() >= 3 && shouldExpand) {
                int end = Math.max(textLayout.getLineStart(2), textLayout.getLineEnd(2));
                if (stringBuilder.charAt(end - 1) == '\n')
                    end -= 1;
                needSpace = stringBuilder.charAt(end - 1) != ' ' && stringBuilder.charAt(end - 1) != '\n';
                firstThreeLinesLayout = makeTextLayout(stringBuilder.subSequence(0, end), maxWidth);
                nextLinesLayouts = new StaticLayout[textLayout.getLineCount() - 3];
                nextLinesLayoutsPositions = new Point[textLayout.getLineCount() - 3];
                int lastLine = firstThreeLinesLayout.getLineCount() - 1;
                float x = firstThreeLinesLayout.getLineRight(lastLine) + (needSpace ? SPACE : 0);
                lastInlineLine = -1;
                if (showMoreTextBackgroundView.getMeasuredWidth() <= 0) {
                    showMoreTextBackgroundView.measure(MOST_SPEC, MOST_SPEC);
                }
                for (int line = 3; line < textLayout.getLineCount(); ++line) {
                    int s = textLayout.getLineStart(line),
                        e = textLayout.getLineEnd(line);
                    final StaticLayout layout = makeTextLayout(stringBuilder.subSequence(Math.min(s, e), Math.max(s, e)), maxWidth);
                    nextLinesLayouts[line - 3] = layout;
                    nextLinesLayoutsPositions[line - 3] = new Point();
                    if (lastInlineLine == -1 && x > maxWidth - showMoreTextBackgroundView.getMeasuredWidth() + showMoreTextBackgroundView.getPaddingLeft()) {
                        lastInlineLine = line - 3;
                    }
                    x += layout.getLineRight(0) + SPACE;
                }
                if (x < maxWidth - showMoreTextBackgroundView.getMeasuredWidth() + showMoreTextBackgroundView.getPaddingLeft()) {
                    shouldExpand = false;
                }
            }

            if (!shouldExpand) {
                firstThreeLinesLayout = null;
                nextLinesLayouts = null;
            }
            lastMaxWidth = maxWidth;

            container.setMinimumHeight(textHeight());

            if (shouldExpand) {
                setShowMoreMarginBottom(textHeight() - AndroidUtilities.dp(8) - textLayout.getLineBottom(textLayout.getLineCount() - 1) - showMoreTextBackgroundView.getPaddingBottom());
            }
        }
        showMoreTextView.setVisibility(shouldExpand ? View.VISIBLE : View.GONE);
        if (!shouldExpand && container.getBackground() == null) {
            container.setBackground(rippleBackground);
        }
        if (shouldExpand && expandT < 1 && container.getBackground() != null) {
            container.setBackground(null);
        }
    }

    private int textHeight() {
        int height = (textLayout != null ? textLayout.getHeight() : AndroidUtilities.dp(20)) + AndroidUtilities.dp(16);
        if (valueTextView.getVisibility() == VISIBLE) {
            height += AndroidUtilities.dp(23);
        }
        return height;
    }
    private boolean shouldExpand = false;
//    private boolean shouldCollapse() {
//        return textLayout != null && textLayout.getLineCount() > 4/* && valueTextView.getVisibility() != View.VISIBLE*/;
//    }

    public boolean onClick() {
        if (shouldExpand && expandT <= 0) {
            updateCollapse(true, true);
            return true;
        }
        return false;
    }
    private float easeInOutCubic(float x) {
        return x < 0.5 ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (textLayout != null) {
            final CharSequence text = textLayout.getText();
            final CharSequence valueText = valueTextView.getText();
            if (TextUtils.isEmpty(valueText)) {
                info.setText(text);
            } else {
                info.setText(valueText + ": " + text);
            }
        }
    }
}
