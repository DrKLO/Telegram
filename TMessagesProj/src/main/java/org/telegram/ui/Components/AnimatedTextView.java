package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.checkerframework.checker.units.qual.A;
import org.checkerframework.checker.units.qual.C;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public class AnimatedTextView extends View {

    public static class AnimatedTextDrawable extends Drawable {

        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private int gravity = 0;

        private boolean isRTL = false;

        private float currentWidth, currentHeight;
        private Part[] currentParts;
        private CharSequence currentText;

        private float oldWidth, oldHeight;
        private Part[] oldParts;
        private CharSequence oldText;

        public void setSplitByWords(boolean b) {
            splitByWords = b;
        }

        private class Part {

            AnimatedEmojiSpan.EmojiGroupedSpans emoji;
            StaticLayout layout;
            float offset;
            int toOppositeIndex;
            float left, width;

            public Part(StaticLayout layout, float offset, int toOppositeIndex) {
                this.layout = layout;
                this.toOppositeIndex = toOppositeIndex;
                layout(offset);

                if (getCallback() instanceof View) {
                    View view = (View) getCallback();
                    emoji = AnimatedEmojiSpan.update(emojiCacheType, view, emoji, layout);
                }
            }

            public void detach() {
                if (getCallback() instanceof View) {
                    View view = (View) getCallback();
                    AnimatedEmojiSpan.release(view, emoji);
                }
            }

            public void layout(float offset) {
                this.offset = offset;
                this.left = layout == null || layout.getLineCount() <= 0 ? 0 : layout.getLineLeft(0);
                this.width = layout == null || layout.getLineCount() <= 0 ? 0 : layout.getLineWidth(0);
            }

            public void draw(Canvas canvas, float alpha) {
                layout.draw(canvas);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, emoji, 0, null, 0, 0, 0, alpha, emojiColorFilter);
            }
        }

        private int emojiCacheType = AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES;
        public void setEmojiCacheType(int cacheType) {
            this.emojiCacheType = cacheType;
        }

        private float t = 0;
        private boolean moveDown = true;
        private ValueAnimator animator;
        private CharSequence toSetText;
        private boolean toSetTextMoveDown;

        private long animateDelay = 0;
        private long animateDuration = 450;
        private TimeInterpolator animateInterpolator = CubicBezierInterpolator.EASE_OUT_QUINT;
        private float moveAmplitude = 1f;

        private float scaleAmplitude = 0;

        private int alpha = 255;
        private final Rect bounds = new Rect();

        private boolean splitByWords;
        private boolean preserveIndex;
        private boolean startFromEnd;
        public void setHacks(boolean splitByWords, boolean preserveIndex, boolean startFromEnd) {
            this.splitByWords = splitByWords;
            this.preserveIndex = preserveIndex;
            this.startFromEnd = startFromEnd;
        }

        private Runnable onAnimationFinishListener;
        private boolean allowCancel;
        public boolean ignoreRTL;
        public boolean updateAll;

        private int overrideFullWidth;
        public void setOverrideFullWidth(int value) {
            overrideFullWidth = value;
        }

        private float rightPadding;
        private boolean ellipsizeByGradient;
        private LinearGradient ellipsizeGradient;
        private Matrix ellipsizeGradientMatrix;
        private Paint ellipsizePaint;

        public AnimatedTextDrawable() {
            this(false, false, false);
        }

        public AnimatedTextDrawable(boolean splitByWords, boolean preserveIndex, boolean startFromEnd) {
            this.splitByWords = splitByWords;
            this.preserveIndex = preserveIndex;
            this.startFromEnd = startFromEnd;
        }

        public void setAllowCancel(boolean allowCancel) {
            this.allowCancel = allowCancel;
        }

        public void setEllipsizeByGradient(boolean enabled) {
            ellipsizeByGradient = enabled;
            invalidateSelf();
        }

        public void setOnAnimationFinishListener(Runnable listener) {
            onAnimationFinishListener = listener;
        }

        private void applyAlphaInternal(float t) {
            textPaint.setAlpha((int) (alpha * t));
            if (shadowed) {
                textPaint.setShadowLayer(shadowRadius, shadowDx, shadowDy, Theme.multAlpha(shadowColor, t));
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (ellipsizeByGradient) {
                AndroidUtilities.rectTmp.set(bounds);
                AndroidUtilities.rectTmp.right -= rightPadding;
                canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 255, Canvas.ALL_SAVE_FLAG);
            }
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            int fullWidth = bounds.width();
            int fullHeight = bounds.height();
            if (currentParts != null && oldParts != null && t != 1) {
                float width = lerp(oldWidth, currentWidth, t);
                float height = lerp(oldHeight, currentHeight, t);
                canvas.translate(0, (fullHeight - height) / 2f);
                for (int i = 0; i < currentParts.length; ++i) {
                    Part current = currentParts[i];
                    int j = current.toOppositeIndex;
                    float x = current.offset, y = 0;
                    if (isRTL && !ignoreRTL) {
                        x = currentWidth - (x + current.width);
                    }
                    if (j >= 0) {
                        Part old = oldParts[j];
                        float oldX = old.offset;
                        if (isRTL && !ignoreRTL) {
                            oldX = oldWidth - (oldX + old.width);
                        }
                        x = lerp(oldX - old.left, x - current.left, t);
                        applyAlphaInternal(1f);
                    } else {
                        x -= current.left;
                        y = -textPaint.getTextSize() * moveAmplitude * (1f - t) * (moveDown ? 1f : -1f);
                        applyAlphaInternal(t);
                    }
                    canvas.save();
                    float lwidth = j >= 0 ? width : currentWidth;
                    if ((gravity | ~Gravity.LEFT) != ~0) {
                        if ((gravity | ~Gravity.RIGHT) == ~0) {
                            x += fullWidth - lwidth;
                        } else if ((gravity | ~Gravity.CENTER_HORIZONTAL) == ~0) {
                            x += (fullWidth - lwidth) / 2f;
                        } else if (isRTL && !ignoreRTL) {
                            x += fullWidth - lwidth;
                        }
                    }
                    canvas.translate(x, y);
                    if (j < 0 && scaleAmplitude > 0) {
                        final float s = lerp(1f - scaleAmplitude, 1f, t);
                        canvas.scale(s, s, current.width / 2f, current.layout.getHeight() / 2f);
                    }
                    current.draw(canvas, j >= 0 ? 1f : t);
                    canvas.restore();
                }
                for (int i = 0; i < oldParts.length; ++i) {
                    Part old = oldParts[i];
                    int j = old.toOppositeIndex;
                    if (j >= 0) {
                        continue;
                    }
                    float x = old.offset;
                    float y = textPaint.getTextSize() * moveAmplitude * t * (moveDown ? 1f : -1f);
                    applyAlphaInternal(1f - t);
                    canvas.save();
                    if (isRTL && !ignoreRTL) {
                        x = oldWidth - (x + old.width);
                    }
                    x -= old.left;
                    if ((gravity | ~Gravity.LEFT) != ~0) {
                        if ((gravity | ~Gravity.RIGHT) == ~0) {
                            x += fullWidth - oldWidth;
                        } else if ((gravity | ~Gravity.CENTER_HORIZONTAL) == ~0) {
                            x += (fullWidth - oldWidth) / 2f;
                        } else if (isRTL && !ignoreRTL) {
                            x += fullWidth - oldWidth;
                        }
                    }
                    canvas.translate(x, y);
                    if (scaleAmplitude > 0) {
                        final float s = lerp(1f, 1f - scaleAmplitude, t);
                        canvas.scale(s, s, old.width / 2f, old.layout.getHeight() / 2f);
                    }
                    old.draw(canvas, 1f - t);
                    canvas.restore();
                }
            } else {
                canvas.translate(0, (fullHeight - currentHeight) / 2f);
                if (currentParts != null) {
                    applyAlphaInternal(1f);
                    for (int i = 0; i < currentParts.length; ++i) {
                        canvas.save();
                        Part current = currentParts[i];
                        float x = current.offset;
                        if (isRTL && !ignoreRTL) {
                            x = currentWidth - (x + current.width);
                        }
                        x -= current.left;
                        if ((gravity | ~Gravity.LEFT) != ~0) {
                            if ((gravity | ~Gravity.RIGHT) == ~0) {
                                x += fullWidth - currentWidth;
                            } else if ((gravity | ~Gravity.CENTER_HORIZONTAL) == ~0) {
                                x += (fullWidth - currentWidth) / 2f;
                            } else if (isRTL && !ignoreRTL) {
                                x += fullWidth - currentWidth;
                            }
                        }
                        canvas.translate(x, 0);
                        current.draw(canvas, 1f);
                        canvas.restore();
                    }
                }
            }
            canvas.restore();
            if (ellipsizeByGradient) {
                final float w = AndroidUtilities.dp(16);
                if (ellipsizeGradient == null) {
                    ellipsizeGradient = new LinearGradient(0, 0, w, 0, new int[] {0x00ff0000, 0xffff0000}, new float[] {0, 1}, Shader.TileMode.CLAMP);
                    ellipsizeGradientMatrix = new Matrix();
                    ellipsizePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    ellipsizePaint.setShader(ellipsizeGradient);
                    ellipsizePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                }
                ellipsizeGradientMatrix.reset();
                ellipsizeGradientMatrix.postTranslate(bounds.right - rightPadding - w, 0);
                ellipsizeGradient.setLocalMatrix(ellipsizeGradientMatrix);
                canvas.save();
                canvas.drawRect(bounds.right - rightPadding - w, bounds.top, bounds.right - rightPadding + AndroidUtilities.dp(1), bounds.bottom, ellipsizePaint);
                canvas.restore();
                canvas.restore();
            }
        }

        public void setRightPadding(float rightPadding) {
            this.rightPadding = rightPadding;
            invalidateSelf();
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
            final int width = overrideFullWidth > 0 ? overrideFullWidth : bounds.width();
            if (animated) {
                if (allowCancel) {
                    if (animator != null) {
                        animator.cancel();
                        animator = null;
                    }
                } else if (isAnimating()) {
                    toSetText = text;
                    toSetTextMoveDown = moveDown;
                    return;
                }

                if (text.equals(currentText)) {
                    return;
                }

                oldText = currentText;
                currentText = text;

                ArrayList<Part> currentParts = new ArrayList<>();
                ArrayList<Part> oldParts = new ArrayList<>();

                currentWidth = currentHeight = 0;
                oldWidth = oldHeight = 0;
                isRTL = AndroidUtilities.isRTL(currentText);

                // order execution matters
                RegionCallback onEqualRegion = (part, from, to) -> {
                    StaticLayout layout = makeLayout(part, width - (int) Math.ceil(Math.min(currentWidth, oldWidth)));
                    final Part currentPart = new Part(layout, currentWidth, oldParts.size());
                    final Part oldPart = new Part(layout, oldWidth, oldParts.size());
                    currentParts.add(currentPart);
                    oldParts.add(oldPart);
                    float partWidth = currentPart.width;
                    currentWidth += partWidth;
                    oldWidth += partWidth;
                    currentHeight = Math.max(currentHeight, layout.getHeight());
                    oldHeight = Math.max(oldHeight, layout.getHeight());
                };
                RegionCallback onNewPart = (part, from, to) -> {
                    StaticLayout layout = makeLayout(part, width - (int) Math.ceil(currentWidth));
                    final Part currentPart = new Part(layout, currentWidth, -1);
                    currentParts.add(currentPart);
                    currentWidth += currentPart.width;
                    currentHeight = Math.max(currentHeight, layout.getHeight());
                };
                RegionCallback onOldPart = (part, from, to) -> {
                    StaticLayout layout = makeLayout(part, width - (int) Math.ceil(oldWidth));
                    final Part oldPart = new Part(layout, oldWidth, -1);
                    oldParts.add(oldPart);
                    oldWidth += oldPart.width;
                    oldHeight = Math.max(oldHeight, layout.getHeight());
                };

                CharSequence from = splitByWords ? new WordSequence(oldText) : oldText;
                CharSequence to = splitByWords ? new WordSequence(currentText) : currentText;

                diff(from, to, onEqualRegion, onNewPart, onOldPart);
//                betterDiff(from, to, onEqualRegion, onNewPart, onOldPart);

                clearCurrentParts();
                if (this.currentParts == null || this.currentParts.length != currentParts.size()) {
                    this.currentParts = new Part[currentParts.size()];
                }
                currentParts.toArray(this.currentParts);
                clearOldParts();
                if (this.oldParts == null || this.oldParts.length != oldParts.size()) {
                    this.oldParts = new Part[oldParts.size()];
                }
                oldParts.toArray(this.oldParts);
                if (animator != null) {
                    animator.cancel();
                }

                this.moveDown = moveDown;
                animator = ValueAnimator.ofFloat(t = 0f, 1f);
                if (widthUpdatedListener != null) {
                    widthUpdatedListener.run();
                }
                animator.addUpdateListener(anm -> {
                    t = (float) anm.getAnimatedValue();
                    invalidateSelf();
                    if (widthUpdatedListener != null) {
                        widthUpdatedListener.run();
                    }
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        clearOldParts();
                        oldText = null;
                        oldWidth = 0;
                        t = 0;
                        invalidateSelf();
                        if (widthUpdatedListener != null) {
                            widthUpdatedListener.run();
                        }
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

                if (!text.equals(currentText)) {
                    clearCurrentParts();
                    currentParts = new Part[1];
                    currentParts[0] = new Part(makeLayout(currentText = text, width), 0, -1);
                    currentWidth = currentParts[0].width;
                    currentHeight = currentParts[0].layout.getHeight();
                    isRTL = AndroidUtilities.isRTL(currentText);
                }

                clearOldParts();
                oldText = null;
                oldWidth = 0;
                oldHeight = 0;

                invalidateSelf();
                if (widthUpdatedListener != null) {
                    widthUpdatedListener.run();
                }
            }
        }

        private void clearOldParts() {
            if (oldParts != null) {
                for (int i = 0; i < oldParts.length; ++i) {
                    oldParts[i].detach();
                }
            }
            oldParts = null;
        }

        private void clearCurrentParts() {
            if (oldParts != null) {
                for (int i = 0; i < oldParts.length; ++i) {
                    oldParts[i].detach();
                }
            }
            oldParts = null;
        }

        public CharSequence getText() {
            return currentText;
        }

        public float getWidth() {
            return Math.max(currentWidth, oldWidth);
        }

        public float getCurrentWidth() {
            if (currentParts != null && oldParts != null) {
                return lerp(oldWidth, currentWidth, t);
            }
            return currentWidth;
        }

        public float getAnimateToWidth() {
            return currentWidth;
        }

        public float getMaxWidth(AnimatedTextDrawable otherTextDrawable) {
            if (oldParts == null || otherTextDrawable.oldParts == null) {
                return Math.max(getCurrentWidth(), otherTextDrawable.getCurrentWidth());
            }
            return lerp(
                Math.max(oldWidth, otherTextDrawable.oldWidth),
                Math.max(currentWidth, otherTextDrawable.currentWidth),
                Math.max(t, otherTextDrawable.t)
            );
        }

        public float getHeight() {
            return currentHeight;
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
            } else {
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
        }

        private static class WordSequence implements CharSequence {
            private static final char SPACE = ' ';

            private final CharSequence[] words;
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
            @NonNull
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

        private void betterDiff(final CharSequence oldText, final CharSequence newText,
                                RegionCallback onEqualPart, RegionCallback onNewPart, RegionCallback onOldPart) {
            int m = oldText.length();
            int n = newText.length();

            int[][] dp = new int[m+1][n+1];
            for (int i = 0; i <= m; i++) {
                for (int j = 0; j <= n; j++) {
                    if (i == 0 || j == 0)
                        dp[i][j] = 0;
                    else if (partEquals(oldText, newText, i - 1, j - 1))
                        dp[i][j] = dp[i - 1][j - 1] + 1;
                    else
                        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }

            List<Runnable> parts = new ArrayList<>();
            int i = m, j = n;
            while (i > 0 && j > 0) {
                if (partEquals(oldText, newText, i - 1, j - 1)) {
                    int start = i-1;
                    while (i > 1 && j > 1 && partEquals(oldText, newText, i - 2, j - 2)) {
                        i--;
                        j--;
                    }
                    final int end = i - 1;
                    parts.add(() -> onEqualPart.run(oldText.subSequence(end, start + 1), end, start + 1));
                    i--;
                    j--;
                } else if (dp[i - 1][j] > dp[i][j - 1]) {
                    int start = i-1;
                    while (i > 1 && dp[i - 2][j] > dp[i - 1][j - 1]) {
                        i--;
                    }
                    final int end = i - 1;
                    parts.add(() -> onOldPart.run(oldText.subSequence(end, start + 1), end, start + 1));
                    i--;
                } else {
                    int start = j - 1;
                    while (j > 1 && dp[i][j - 2] > dp[i - 1][j - 1]) {
                        j--;
                    }
                    final int end = j - 1;
                    parts.add(() -> onNewPart.run(newText.subSequence(end, start + 1), end, start + 1));
                    j--;
                }
            }

            while (i > 0) {
                final int start = i - 1;
                while (i > 1 && dp[i - 2][j] >= dp[i - 1][j]) {
                    i--;
                }
                final int end = i - 1;
                parts.add(() -> onOldPart.run(oldText.subSequence(end, start + 1), end, start + 1));
                i--;
            }
            while (j > 0) {
                final int start = j - 1;
                while (j > 1 && dp[i][j - 2] >= dp[i][j - 1]) {
                    j--;
                }
                final int end = j - 1;
                parts.add(() -> onNewPart.run(newText.subSequence(end, start + 1), end, start + 1));
                j--;
            }

            Collections.reverse(parts);
            for (Runnable part : parts) {
                part.run();
            }
        }


        private void diff(final CharSequence oldText, final CharSequence newText, RegionCallback onEqualPart, RegionCallback onNewPart, RegionCallback onOldPart) {
            if (updateAll) {
                onOldPart.run(oldText, 0, oldText.length());
                onNewPart.run(newText, 0, newText.length());
                return;
            }
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
                        if ((i % 2 == 0) == eq) {
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
            final float lastTextPaint = textPaint.getTextSize();
            textPaint.setTextSize(textSizePx);
            if (Math.abs(lastTextPaint - textSizePx) > 0.5f) {
                final int width = overrideFullWidth > 0 ? overrideFullWidth : bounds.width();
                if (currentParts != null) {
                    // relayout parts:
                    currentWidth = 0;
                    currentHeight = 0;
                    for (int i = 0; i < currentParts.length; ++i) {
                        StaticLayout layout = makeLayout(currentParts[i].layout.getText(), width - (int) Math.ceil(Math.min(currentWidth, oldWidth)));
                        currentParts[i] = new Part(layout, currentParts[i].offset, currentParts[i].toOppositeIndex);
                        currentWidth += currentParts[i].width;
                        currentHeight = Math.max(currentHeight, currentParts[i].layout.getHeight());
                    }
                }
                if (oldParts != null) {
                    oldWidth = 0;
                    oldHeight = 0;
                    for (int i = 0; i < oldParts.length; ++i) {
                        StaticLayout layout = makeLayout(oldParts[i].layout.getText(), width - (int) Math.ceil(Math.min(currentWidth, oldWidth)));
                        oldParts[i] = new Part(layout, oldParts[i].offset, oldParts[i].toOppositeIndex);
                        oldWidth += oldParts[i].width;
                        oldHeight = Math.max(oldHeight, oldParts[i].layout.getHeight());
                    }
                }
                invalidateSelf();
            }
        }

        public float getTextSize() {
            return textPaint.getTextSize();
        }

        public void setTextColor(int color) {
            textPaint.setColor(color);
            alpha = Color.alpha(color);
        }

        private boolean shadowed = false;
        private float shadowRadius, shadowDx, shadowDy;
        private int shadowColor;
        public void setShadowLayer(float radius, float dx, float dy, int shadowColor) {
            shadowed = true;
            textPaint.setShadowLayer(shadowRadius = radius, shadowDx = dx, shadowDy = dy, this.shadowColor = shadowColor);
        }

        public int getTextColor() {
            return textPaint.getColor();
        }

        private ValueAnimator colorAnimator;
        public void setTextColor(int color, boolean animated) {
            if (colorAnimator != null) {
                colorAnimator.cancel();
                colorAnimator = null;
            }
            if (!animated) {
                setTextColor(color);
            } else {
                final int from = getTextColor();
                final int to = color;
                colorAnimator = ValueAnimator.ofFloat(0, 1);
                colorAnimator.addUpdateListener(anm -> {
                    setTextColor(ColorUtils.blendARGB(from, to, (float) anm.getAnimatedValue()));
                    invalidateSelf();
                });
                colorAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setTextColor(to);
                    }
                });
                colorAnimator.setDuration(240);
                colorAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                colorAnimator.start();
            }
        }

        private int emojiColor;
        private ColorFilter emojiColorFilter;

        public void setEmojiColorFilter(ColorFilter colorFilter) {
            emojiColorFilter = colorFilter;
        }

        public void setEmojiColor(int emojiColor) {
            if (this.emojiColor != emojiColor) {
                emojiColorFilter = new PorterDuffColorFilter(this.emojiColor = emojiColor, PorterDuff.Mode.MULTIPLY);
            }
        }

        private ValueAnimator emojiColorAnimator;
        public void setEmojiColor(int color, boolean animated) {
            if (emojiColorAnimator != null) {
                emojiColorAnimator.cancel();
                emojiColorAnimator = null;
            }
            if (!animated) {
                setEmojiColor(color);
            } else if (emojiColor != color) {
                final int from = getTextColor();
                final int to = color;
                emojiColorAnimator = ValueAnimator.ofFloat(0, 1);
                emojiColorAnimator.addUpdateListener(anm -> {
                    setEmojiColor(ColorUtils.blendARGB(from, to, (float) anm.getAnimatedValue()));
                    invalidateSelf();
                });
                emojiColorAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setTextColor(to);
                    }
                });
                emojiColorAnimator.setDuration(240);
                emojiColorAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                emojiColorAnimator.start();
            }
        }

        public void setTypeface(Typeface typeface) {
            textPaint.setTypeface(typeface);
        }

        public void setGravity(int gravity) {
            this.gravity = gravity;
        }

        public int getGravity() {
            return this.gravity;
        }

        public void setAnimationProperties(float moveAmplitude, long startDelay, long duration, TimeInterpolator interpolator) {
            this.moveAmplitude = moveAmplitude;
            animateDelay = startDelay;
            animateDuration = duration;
            animateInterpolator = interpolator;
        }

        public void setScaleProperty(float scale) {
            this.scaleAmplitude = scale;
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
            void run(CharSequence part, int start, int end);
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

        @NonNull
        @Override
        public Rect getDirtyBounds() {
            return this.bounds;
        }

        public float isNotEmpty() {
            return lerp(
                oldText == null || oldText.length() <= 0 ? 0f : 1f,
                currentText == null || currentText.length() <= 0 ? 0f : 1f,
                oldText == null ? 1f : t
            );
        }

        private Runnable widthUpdatedListener;
        public void setOnWidthUpdatedListener(Runnable listener) {
            widthUpdatedListener = listener;
        }
    }

    private final AnimatedTextDrawable drawable;
    private int lastMaxWidth, maxWidth;

    private CharSequence toSetText;
    private boolean toSetMoveDown;
    public boolean adaptWidth = true;

    public AnimatedTextView(Context context) {
        this(context, false, false, false);
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

    public void setMaxWidth(int width) {
        maxWidth = width;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (maxWidth > 0) {
            width = Math.min(width, maxWidth);
        }
        if (lastMaxWidth != width && getLayoutParams().width != 0) {
            drawable.setBounds(getPaddingLeft(), getPaddingTop(), width - getPaddingRight(), height - getPaddingBottom());
            setText(drawable.getText(), false);
        }
        lastMaxWidth = width;
        if (adaptWidth && MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
            width = getPaddingLeft() + (int) Math.ceil(drawable.getWidth()) + getPaddingRight();
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

    public void setIgnoreRTL(boolean value) {
        drawable.ignoreRTL = value;
    }

    private boolean first = true;
    public void setText(CharSequence text, boolean animated, boolean moveDown) {
        animated = !first && animated;
        first = false;
        if (animated) {
            if (drawable.allowCancel) {
                if (drawable.animator != null) {
                    drawable.animator.cancel();
                    drawable.animator = null;
                }
            } else if (drawable.isAnimating()) {
                toSetText = text;
                toSetMoveDown = moveDown;
                return;
            }
        }
        int wasWidth = (int) drawable.getWidth();
        drawable.setBounds(getPaddingLeft(), getPaddingTop(), lastMaxWidth - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
        drawable.setText(text, animated, moveDown);
        if (wasWidth < drawable.getWidth() || !animated && wasWidth != drawable.getWidth()) {
            requestLayout();
        }
    }

    public int width() {
        return getPaddingLeft() + (int) Math.ceil(drawable.getCurrentWidth()) + getPaddingRight();
    }

    public CharSequence getText() {
        return drawable.getText();
    }

    public int getTextHeight() {
        return getPaint().getFontMetricsInt().descent - getPaint().getFontMetricsInt().ascent;
    }

    public void setTextSize(float textSizePx) {
        drawable.setTextSize(textSizePx);
    }

    public void setTextColor(int color) {
        drawable.setTextColor(color);
        invalidate();
    }

    public void setTextColor(int color, boolean animated) {
        drawable.setTextColor(color, animated);
        invalidate();
    }

    public void setEmojiCacheType(int cacheType) {
        drawable.setEmojiCacheType(cacheType);
    }

    public void setEmojiColor(int color) {
        drawable.setEmojiColor(color);
        invalidate();
    }

    public void setEmojiColor(int color, boolean animated) {
        drawable.setEmojiColor(color, animated);
        invalidate();
    }
    public void setEmojiColorFilter(ColorFilter emojiColorFilter) {
        drawable.setEmojiColorFilter(emojiColorFilter);
        invalidate();
    }
    public int getTextColor() {
        return drawable.getTextColor();
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

    public void setScaleProperty(float scale) {
        drawable.setScaleProperty(scale);
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

    public void setEllipsizeByGradient(boolean enabled) {
        drawable.setEllipsizeByGradient(enabled);
    }

    public void setRightPadding(float rightPadding) {
        drawable.setRightPadding(rightPadding);
    }

    private Runnable widthUpdatedListener;
    public void setOnWidthUpdatedListener(Runnable listener) {
        drawable.setOnWidthUpdatedListener(listener);
    }
}