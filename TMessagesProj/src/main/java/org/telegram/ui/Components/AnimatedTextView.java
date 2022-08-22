package org.telegram.ui.Components;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

public class AnimatedTextView extends View {

    public static class AnimatedTextDrawable extends Drawable {

        private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private int gravity = 0;

        private boolean isRTL = false;

        private int currentWidth, currentHeight;
        private Integer[] currentLayoutOffsets;
        private Integer[] currentLayoutToOldIndex;
        private StaticLayout[] currentLayout;
        private CharSequence currentText;

        private int oldWidth, oldHeight;
        private Integer[] oldLayoutOffsets;
        private Integer[] oldLayoutToCurrentIndex;
        private StaticLayout[] oldLayout;
        private CharSequence oldText;

        private float t = 0;
        private boolean moveDown = true;
        private ValueAnimator animator;
        private CharSequence toSetText;
        private boolean toSetTextMoveDown;

        private long animateDelay = 0;
        private long animateDuration = 450;
        private TimeInterpolator animateInterpolator = CubicBezierInterpolator.EASE_OUT_QUINT;
        private float moveAmplitude = 1f;

        private int alpha = 255;
        private Rect bounds = new Rect();

        private boolean splitByWords;
        private boolean preserveIndex;
        private boolean startFromEnd;

        private Runnable onAnimationFinishListener;

        public AnimatedTextDrawable() {
            this(false, false, false);
        }

        public AnimatedTextDrawable(boolean splitByWords, boolean preserveIndex, boolean startFromEnd) {
            this.splitByWords = splitByWords;
            this.preserveIndex = preserveIndex;
            this.startFromEnd = startFromEnd;
        }

        public void setOnAnimationFinishListener(Runnable listener) {
            onAnimationFinishListener = listener;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            int fullWidth = bounds.width();
            int fullHeight = bounds.height();
            if (currentLayout != null && oldLayout != null) {
                int width = AndroidUtilities.lerp(oldWidth, currentWidth, t);
                int height = AndroidUtilities.lerp(oldHeight, currentHeight, t);
                canvas.translate(0, (fullHeight - height) / 2f);
                for (int i = 0; i < currentLayout.length; ++i) {
                    int j = currentLayoutToOldIndex[i];
                    float x = currentLayoutOffsets[i], y = 0;
                    if (j >= 0) {
                        float oldX = oldLayoutOffsets[j];
                        x = AndroidUtilities.lerp(oldX, x, t);
                        textPaint.setAlpha(alpha);
                    } else {
                        y = -textPaint.getTextSize() * moveAmplitude * (1f - t) * (moveDown ? 1f : -1f);
                        textPaint.setAlpha((int) (alpha * t));
                    }
                    canvas.save();
                    int lwidth = j >= 0 ? width : currentWidth;
                    if (isRTL) {
                        x = lwidth - x - currentLayout[i].getWidth();
                        x -= fullWidth - lwidth;
                    }
                    if ((gravity & Gravity.CENTER_HORIZONTAL) > 0) {
                        x += (fullWidth - lwidth) / 2f;
                    } else if ((gravity & Gravity.RIGHT) > 0) {
                        x += fullWidth - lwidth;
                    }
                    canvas.translate(x, y);
                    currentLayout[i].draw(canvas);
                    canvas.restore();
                }
                for (int i = 0; i < oldLayout.length; ++i) {
                    int j = oldLayoutToCurrentIndex[i];
                    if (j >= 0) {
                        continue;
                    }
                    float x = oldLayoutOffsets[i];
                    float y = textPaint.getTextSize() * moveAmplitude * t * (moveDown ? 1f : -1f);
                    textPaint.setAlpha((int) (alpha * (1f - t)));
                    canvas.save();
                    if (isRTL) {
                        x = oldWidth - x - oldLayout[i].getWidth();
                        x -= fullWidth - oldWidth;
                    }
                    if ((gravity & Gravity.CENTER_HORIZONTAL) > 0) {
                        x += (fullWidth - oldWidth) / 2f;
                    } else if ((gravity & Gravity.RIGHT) > 0) {
                        x += fullWidth - oldWidth;
                    }
                    canvas.translate(x, y);
                    oldLayout[i].draw(canvas);
                    canvas.restore();
                }
            } else {
                canvas.translate(0, (fullHeight - currentHeight) / 2f);
                if (currentLayout != null) {
                    for (int i = 0; i < currentLayout.length; ++i) {
                        textPaint.setAlpha(alpha);
                        canvas.save();
                        float x = currentLayoutOffsets[i];
                        if (isRTL) {
                            x = currentWidth - x - currentLayout[i].getWidth();
                            x -= fullWidth - currentWidth;
                        }
                        if ((gravity & Gravity.CENTER_HORIZONTAL) > 0) {
                            x += (fullWidth - currentWidth) / 2f;
                        } else if ((gravity & Gravity.RIGHT) > 0) {
                            x += fullWidth - currentWidth;
                        }
                        canvas.translate(x, 0);
                        currentLayout[i].draw(canvas);
                        canvas.restore();
                    }
                }
            }
            canvas.restore();
        }

        public void cancelAnimation() {
            if (animator != null) {
                animator.cancel();
            }
        }

        public boolean isAnimating() {
            return animator != null && animator.isRunning();
        }

        public void setText(CharSequence text) {
            setText(text, true);
        }

        public void setText(CharSequence text, boolean animated) {
            setText(text, animated, true);
        }

        public void setText(CharSequence text, boolean animated, boolean moveDown) {
            if (this.currentText == null || text == null) {
                animated = false;
            }
            if (text == null) {
                text = "";
            }
            if (animated) {
                if (isAnimating()) {
                    toSetText = text;
                    toSetTextMoveDown = moveDown;
                    return;
                }

                if (text.equals(currentText)) {
                    return;
                }

                oldText = currentText;
                currentText = text;
                currentLayout = null;
                oldLayout = null;

                ArrayList<Integer> currentLayoutOffsets = new ArrayList<>();
                ArrayList<Integer> currentLayoutToOldIndex = new ArrayList<>();
                ArrayList<StaticLayout> currentLayoutList = new ArrayList<>();
                ArrayList<Integer> oldLayoutOffsets = new ArrayList<>();
                ArrayList<Integer> oldLayoutToCurrentIndex = new ArrayList<>();
                ArrayList<StaticLayout> oldLayoutList = new ArrayList<>();

                currentWidth = currentHeight = 0;
                oldWidth = oldHeight = 0;

                // order execution matters
                RegionCallback onEqualRegion = (part, from, to) -> {
                    StaticLayout layout = makeLayout(part, bounds.width() - Math.min(currentWidth, oldWidth));
                    oldLayoutToCurrentIndex.add(currentLayoutList.size());
                    currentLayoutToOldIndex.add(oldLayoutList.size());
                    currentLayoutOffsets.add(currentWidth);
                    currentLayoutList.add(layout);
                    oldLayoutOffsets.add(oldWidth);
                    oldLayoutList.add(layout);
                    float partWidth = layout.getLineWidth(0);
                    currentWidth += partWidth;
                    oldWidth += partWidth;
                    currentHeight = Math.max(currentHeight, layout.getHeight());
                    oldHeight = Math.max(oldHeight, layout.getHeight());
                };
                RegionCallback onNewPart = (part, from, to) -> {
                    StaticLayout layout = makeLayout(part, bounds.width() - currentWidth);
                    currentLayoutOffsets.add(currentWidth);
                    currentLayoutList.add(layout);
                    currentLayoutToOldIndex.add(-1);
                    currentWidth += layout.getLineWidth(0);
                    currentHeight = Math.max(currentHeight, layout.getHeight());
                };
                RegionCallback onOldPart = (part, from, to) -> {
                    StaticLayout layout = makeLayout(part, bounds.width() - oldWidth);
                    oldLayoutOffsets.add(oldWidth);
                    oldLayoutList.add(layout);
                    oldLayoutToCurrentIndex.add(-1);
                    oldWidth += layout.getLineWidth(0);
                    oldHeight = Math.max(oldHeight, layout.getHeight());
                };

                CharSequence from = splitByWords ? new WordSequence(oldText) : oldText;
                CharSequence to = splitByWords ? new WordSequence(currentText) : currentText;

                diff(from, to, onEqualRegion, onNewPart, onOldPart);

                if (this.currentLayout == null || this.currentLayout.length != currentLayoutList.size()) {
                    this.currentLayout = new StaticLayout[currentLayoutList.size()];
                }
                currentLayoutList.toArray(currentLayout);
                if (this.currentLayoutOffsets == null || this.currentLayoutOffsets.length != currentLayoutOffsets.size()) {
                    this.currentLayoutOffsets = new Integer[currentLayoutOffsets.size()];
                }
                currentLayoutOffsets.toArray(this.currentLayoutOffsets);
                if (this.currentLayoutToOldIndex == null || this.currentLayoutToOldIndex.length != currentLayoutToOldIndex.size()) {
                    this.currentLayoutToOldIndex = new Integer[currentLayoutToOldIndex.size()];
                }
                currentLayoutToOldIndex.toArray(this.currentLayoutToOldIndex);

                if (this.oldLayout == null || this.oldLayout.length != oldLayoutList.size()) {
                    this.oldLayout = new StaticLayout[oldLayoutList.size()];
                }
                oldLayoutList.toArray(oldLayout);
                if (this.oldLayoutOffsets == null || this.oldLayoutOffsets.length != oldLayoutOffsets.size()) {
                    this.oldLayoutOffsets = new Integer[oldLayoutOffsets.size()];
                }
                oldLayoutOffsets.toArray(this.oldLayoutOffsets);
                if (this.oldLayoutToCurrentIndex == null || this.oldLayoutToCurrentIndex.length != oldLayoutToCurrentIndex.size()) {
                    this.oldLayoutToCurrentIndex = new Integer[oldLayoutToCurrentIndex.size()];
                }
                oldLayoutToCurrentIndex.toArray(this.oldLayoutToCurrentIndex);

                if (this.currentLayout.length > 0) {
                    isRTL = this.currentLayout[0].isRtlCharAt(0);
                } else if (this.oldLayout.length > 0) {
                    isRTL = this.oldLayout[0].isRtlCharAt(0);
                }

                this.moveDown = moveDown;
                animator = ValueAnimator.ofFloat(t = 0f, 1f);
                animator.addUpdateListener(anm -> {
                    t = (float) anm.getAnimatedValue();
                    invalidateSelf();
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        oldLayout = null;
                        AnimatedTextDrawable.this.oldLayoutOffsets = null;
                        AnimatedTextDrawable.this.oldLayoutToCurrentIndex = null;
                        oldText = null;
                        oldWidth = 0;
                        t = 0;
                        invalidateSelf();
                        animator = null;

                        if (toSetText != null) {
                            setText(toSetText, true, toSetTextMoveDown);
                            toSetText = null;
                            toSetTextMoveDown = false;
                        } else if (onAnimationFinishListener != null) {
                            onAnimationFinishListener.run();
                        }
                    }
                });
                animator.setStartDelay(animateDelay);
                animator.setDuration(animateDuration);
                animator.setInterpolator(animateInterpolator);
                animator.start();
            } else {
                if (animator != null) {
                    animator.cancel();
                }
                animator = null;
                toSetText = null;
                toSetTextMoveDown = false;
                t = 0;

                currentLayout = new StaticLayout[1];
                currentLayout[0] = makeLayout(currentText = text, bounds.width());
                currentWidth = (int) currentLayout[0].getLineWidth(0);
                currentHeight = currentLayout[0].getHeight();
                currentLayoutOffsets = new Integer[1];
                currentLayoutOffsets[0] = 0;
                currentLayoutToOldIndex = new Integer[1];
                currentLayoutToOldIndex[0] = -1;

                if (this.currentLayout.length > 0) {
                    isRTL = this.currentLayout[0].isRtlCharAt(0);
                }

                oldLayout = null;
                oldLayoutOffsets = null;
                oldLayoutToCurrentIndex = null;
                oldText = null;
                oldWidth = 0;
                oldHeight = 0;

                invalidateSelf();
            }
        }

        public CharSequence getText() {
            return currentText;
        }

        public int getWidth() {
            return Math.max(currentWidth, oldWidth);
        }

        public int getCurrentWidth() {
            if (currentLayout != null && oldLayout != null) {
                return AndroidUtilities.lerp(oldWidth, currentWidth, t);
            }
            return currentWidth;
        }

        private StaticLayout makeLayout(CharSequence textPart, int width) {
            if (width <= 0) {
                width = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return StaticLayout.Builder.obtain(textPart, 0, textPart.length(), textPaint, width)
                        .setMaxLines(1)
                        .setLineSpacing(0, 1)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .setEllipsizedWidth(width)
                        .build();
            }
            return new StaticLayout(
                    textPart,
                    0, textPart.length(),
                    textPaint,
                    width,
                    Layout.Alignment.ALIGN_NORMAL,
                    1,
                    0,
                    false,
                    TextUtils.TruncateAt.END,
                    width
            );
        }

        private static class WordSequence implements CharSequence {
            private static final char SPACE = ' ';

            private CharSequence words[];
            private final int length;

            public WordSequence(CharSequence text) {
                if (text == null) {
                    words = new CharSequence[0];
                    length = 0;
                    return;
                }
                length = text.length();
                int spacesCount = 0;
                for (int i = 0; i < length; ++i) {
                    if (text.charAt(i) == SPACE) {
                        spacesCount++;
                    }
                }
                int j = 0;
                words = new CharSequence[spacesCount + 1];
                int start = 0;
                for (int i = 0; i <= length; ++i) {
                    if (i == length || text.charAt(i) == SPACE) {
                        words[j++] = text.subSequence(start, i + (i < length ? 1 : 0));
                        start = i + 1;
                    }
                }
            }

            public WordSequence(CharSequence[] words) {
                if (words == null) {
                    this.words = new CharSequence[0];
                    length = 0;
                    return;
                }
                this.words = words;
                int length = 0;
                for (int i = 0; i < this.words.length; ++i) {
                    if (this.words[i] != null) {
                        length += this.words[i].length();
                    }
                }
                this.length = length;
            }

            public CharSequence wordAt(int i) {
                if (i < 0 || i >= words.length) {
                    return null;
                }
                return words[i];
            }

            @Override
            public int length() {
                return words.length;
            }

            @Override
            public char charAt(int i) {
                for (int j = 0; j < words.length; ++j) {
                    if (i < words[j].length())
                        return words[j].charAt(i);
                    i -= words[j].length();
                }
                return 0;
            }

            @NonNull
            @Override
            public CharSequence subSequence(int from, int to) {
                return TextUtils.concat(Arrays.copyOfRange(words, from, to));
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < words.length; ++i) {
                    sb.append(words[i]);
                }
                return sb.toString();
            }

            public CharSequence toCharSequence() {
                return TextUtils.concat(words);
            }

            @Override
            public IntStream chars() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return toCharSequence().chars();
                }
                return null;
            }

            @Override
            public IntStream codePoints() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return toCharSequence().codePoints();
                }
                return null;
            }
        }

        public static boolean partEquals(CharSequence a, CharSequence b, int aIndex, int bIndex) {
            if (a instanceof WordSequence && b instanceof WordSequence) {
                CharSequence wordA = ((WordSequence) a).wordAt(aIndex);
                CharSequence wordB = ((WordSequence) b).wordAt(bIndex);
                return wordA == null && wordB == null || wordA != null && wordA.equals(wordB);
            }
            return (a == null && b == null || a != null && b != null && a.charAt(aIndex) == b.charAt(bIndex));
        }

        private void diff(final CharSequence oldText, final CharSequence newText, RegionCallback onEqualPart, RegionCallback onNewPart, RegionCallback onOldPart) {
            if (preserveIndex) {
                boolean equal = true;
                int start = 0;
                int minLength = Math.min(newText.length(), oldText.length());
                if (startFromEnd) {
                    ArrayList<Integer> indexes = new ArrayList<>();
                    boolean eq = true;
                    for (int i = 0; i <= minLength; ++i) {
                        int a = newText.length() - i - 1;
                        int b = oldText.length() - i - 1;
                        boolean thisEqual = a >= 0 && b >= 0 && partEquals(newText, oldText, a, b);
                        if (equal != thisEqual || i == minLength) {
                            if (i - start > 0) {
                                if (indexes.size() == 0) {
                                    eq = equal;
                                }
                                indexes.add(i - start);
                            }
                            equal = thisEqual;
                            start = i;
                        }
                    }
                    int a = newText.length() - minLength;
                    int b = oldText.length() - minLength;
                    if (a > 0) {
                        onNewPart.run(newText.subSequence(0, a), 0, a);
                    }
                    if (b > 0) {
                        onOldPart.run(oldText.subSequence(0, b), 0, b);
                    }
                    for (int i = indexes.size() - 1; i >= 0; --i) {
                        int count = indexes.get(i);
                        if (i % 2 == 0 ? eq : !eq) {
                            if (newText.length() > oldText.length()) {
                                onEqualPart.run(newText.subSequence(a, a + count), a, a + count);
                            } else {
                                onEqualPart.run(oldText.subSequence(b, b + count), b, b + count);
                            }
                        } else {
                            onNewPart.run(newText.subSequence(a, a + count), a, a + count);
                            onOldPart.run(oldText.subSequence(b, b + count), b, b + count);
                        }
                        a += count;
                        b += count;
                    }
                } else {
                    for (int i = 0; i <= minLength; ++i) {
                        boolean thisEqual = i < minLength && partEquals(newText, oldText, i, i);
                        if (equal != thisEqual || i == minLength) {
                            if (i - start > 0) {
                                if (equal) {
                                    onEqualPart.run(newText.subSequence(start, i), start, i);
                                } else {
                                    onNewPart.run(newText.subSequence(start, i), start, i);
                                    onOldPart.run(oldText.subSequence(start, i), start, i);
                                }
                            }
                            equal = thisEqual;
                            start = i;
                        }
                    }
                    if (newText.length() - minLength > 0) {
                        onNewPart.run(newText.subSequence(minLength, newText.length()), minLength, newText.length());
                    }
                    if (oldText.length() - minLength > 0) {
                        onOldPart.run(oldText.subSequence(minLength, oldText.length()), minLength, oldText.length());
                    }
                }
            } else {
                int astart = 0, bstart = 0;
                boolean equal = true;
                int a = 0, b = 0;
                int minLength = Math.min(newText.length(), oldText.length());
                for (; a <= minLength; ++a) {
                    boolean thisEqual = a < minLength && partEquals(newText, oldText, a, b);
                    if (equal != thisEqual || a == minLength) {
                        if (a == minLength) {
                            a = newText.length();
                            b = oldText.length();
                        }
                        int alen = a - astart, blen = b - bstart;
                        if (alen > 0 || blen > 0) {
                            if (alen == blen && equal) {
                                // equal part on [astart, a)
                                onEqualPart.run(newText.subSequence(astart, a), astart, a);
                            } else {
                                if (alen > 0) {
                                    // new part on [astart, a)
                                    onNewPart.run(newText.subSequence(astart, a), astart, a);
                                }
                                if (blen > 0) {
                                    // old part on [bstart, b)
                                    onOldPart.run(oldText.subSequence(bstart, b), bstart, b);
                                }
                            }
                        }
                        equal = thisEqual;
                        astart = a;
                        bstart = b;
                    }
                    if (thisEqual) {
                        b++;
                    }
                }
            }
        }

        public void setTextSize(float textSizePx) {
            textPaint.setTextSize(textSizePx);
        }

        public void setTextColor(int color) {
            textPaint.setColor(color);
        }

        public void setTypeface(Typeface typeface) {
            textPaint.setTypeface(typeface);
        }

        public void setGravity(int gravity) {
            this.gravity = gravity;
        }

        public void setAnimationProperties(float moveAmplitude, long startDelay, long duration, TimeInterpolator interpolator) {
            this.moveAmplitude = moveAmplitude;
            animateDelay = startDelay;
            animateDuration = duration;
            animateInterpolator = interpolator;
        }

        public void copyStylesFrom(TextPaint paint) {
            setTextColor(paint.getColor());
            setTextSize(paint.getTextSize());
            setTypeface(paint.getTypeface());
        }

        public TextPaint getPaint() {
            return textPaint;
        }

        private interface RegionCallback {
            public void run(CharSequence part, int start, int end);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            textPaint.setColorFilter(colorFilter);
        }

        @Deprecated @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public void setBounds(@NonNull Rect bounds) {
            super.setBounds(bounds);
            this.bounds.set(bounds);
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(left, top, right, bottom);
            this.bounds.set(left, top, right, bottom);
        }
    }

    private AnimatedTextDrawable drawable;
    private int lastMaxWidth;

    private CharSequence toSetText;
    private boolean toSetMoveDown;

    public AnimatedTextView(Context context) {
        super(context);
        drawable = new AnimatedTextDrawable();
        drawable.setCallback(this);
    }

    public AnimatedTextView(Context context, boolean splitByWords, boolean preserveIndex, boolean startFromEnd) {
        super(context);
        drawable = new AnimatedTextDrawable(splitByWords, preserveIndex, startFromEnd);
        drawable.setCallback(this);
        drawable.setOnAnimationFinishListener(() -> {
            if (toSetText != null) {
                // wrapped toSetText here to do requestLayout()
                AnimatedTextView.this.setText(toSetText, toSetMoveDown, true);
                toSetText = null;
                toSetMoveDown = false;
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (lastMaxWidth != width) {
            drawable.setBounds(getPaddingLeft(), getPaddingTop(), width - getPaddingRight(), height - getPaddingBottom());
            setText(drawable.getText(), false);
        }
        lastMaxWidth = width;
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
            width = getPaddingLeft() + drawable.getWidth() + getPaddingRight();
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawable.setBounds(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
        drawable.draw(canvas);
    }

    public void setText(CharSequence text) {
        setText(text, true, true);
    }

    public void setText(CharSequence text, boolean animated) {
        setText(text, animated, true);
    }

    public void cancelAnimation() {
        drawable.cancelAnimation();
    }

    public boolean isAnimating() {
        return drawable.isAnimating();
    }

    private boolean first = true;
    public void setText(CharSequence text, boolean animated, boolean moveDown) {
        animated = !first && animated;
        first = false;
        if (animated && drawable.isAnimating()) {
            toSetText = text;
            toSetMoveDown = moveDown;
            return;
        }
        int wasWidth = drawable.getWidth();
        drawable.setBounds(getPaddingLeft(), getPaddingTop(), lastMaxWidth - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
        drawable.setText(text, animated, moveDown);
        if (wasWidth < drawable.getWidth() || !animated && wasWidth != drawable.getWidth()) {
            requestLayout();
        }
    }

    public int width() {
        return getPaddingLeft() + drawable.getCurrentWidth() + getPaddingRight();
    }

    public CharSequence getText() {
        return drawable.getText();
    }

    public void setTextSize(float textSizePx) {
        drawable.setTextSize(textSizePx);
    }

    public void setTextColor(int color) {
        drawable.setTextColor(color);
    }

    public void setTypeface(Typeface typeface) {
        drawable.setTypeface(typeface);
    }

    public void setGravity(int gravity) {
        drawable.setGravity(gravity);
    }

    public void setAnimationProperties(float moveAmplitude, long startDelay, long duration, TimeInterpolator interpolator) {
        drawable.setAnimationProperties(moveAmplitude, startDelay, duration, interpolator);
    }

    public AnimatedTextDrawable getDrawable() {
        return drawable;
    }

    public TextPaint getPaint() {
        return drawable.getPaint();
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        super.invalidateDrawable(drawable);
        invalidate();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.TextView");
        info.setText(getText());
    }
}