/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class SeekBarWaveform {

    private static Paint paintInner;
    private static Paint paintOuter;
    private int thumbX = 0;
    private int thumbDX = 0;
    private float progress;
    private float startX;
    private boolean startDraging = false;
    private boolean pressed = false;
    private int width;
    private int height;
    private int fromWidth, toWidth;
    private SeekBar.SeekBarDelegate delegate;
    private byte[] waveformBytes;
    private MessageObject messageObject;
    private View parentView;
    private boolean selected;

    private int innerColor;
    private int outerColor;
    private int selectedColor;
    private float alpha = 1f;

    private float clearProgress = 1f;
    private boolean isUnread;
    private AnimatedFloat appearFloat = new AnimatedFloat(125, 600, CubicBezierInterpolator.EASE_OUT_QUINT);

    private float waveScaling = 1f;

    private Path path;
    private Path alphaPath;
    private boolean loading;
    private long loadingStart;
    private AnimatedFloat loadingFloat = new AnimatedFloat(150, CubicBezierInterpolator.DEFAULT);
    private Paint loadingPaint;
    private float loadingPaintWidth;
    private int loadingPaintColor1, loadingPaintColor2;

    private ArrayList<Float> animatedValues;
    private float[] heights;
    private float[] fromHeights;
    private float[] toHeights;

    public SeekBarWaveform(Context context) {
        if (paintInner == null) {
            paintInner = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintOuter = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintInner.setStyle(Paint.Style.FILL);
            paintOuter.setStyle(Paint.Style.FILL);
        }
    }

    public void setDelegate(SeekBar.SeekBarDelegate seekBarDelegate) {
        delegate = seekBarDelegate;
    }

    public void setColors(int inner, int outer, int selected) {
        innerColor = inner;
        outerColor = outer;
        selectedColor = selected;
    }

    public void setWaveform(byte[] waveform) {
        waveformBytes = waveform;
        heights = calculateHeights((int) (width / AndroidUtilities.dpf2(3)));
        if (!delegate.isSeekBarDragAllowed()) {
            this.progress = 1f;
        }
    }

    public void setSelected(boolean value) {
        selected = value;
    }

    public void setMessageObject(MessageObject object) {
        if (animatedValues != null && messageObject != null && object != null && messageObject.getId() != object.getId()) {
            animatedValues.clear();
        }
        messageObject = object;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public void setParentView(View view) {
        parentView = view;
        loadingFloat.setParent(view);
        appearFloat.setParent(view);
    }

    public void invalidate() {
        if (parentView != null) {
            parentView.invalidate();
        }
    }

    public boolean isStartDraging() {
        return startDraging;
    }

    public boolean onTouch(int action, float x, float y) {
        if (!delegate.isSeekBarDragAllowed()) {
            this.progress = 1f;
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            if (0 <= x && x <= width && y >= 0 && y <= height) {
                startX = x;
                pressed = true;
                thumbDX = (int) (x - thumbX);
                startDraging = false;
                delegate.onSeekBarPressed();
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (pressed) {
                if (action == MotionEvent.ACTION_UP && delegate != null) {
                    delegate.onSeekBarDrag((float) thumbX / (float) width);
                }
                pressed = false;
                delegate.onSeekBarReleased();
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (pressed) {
                if (startDraging) {
                    thumbX = (int) (x - thumbDX);
                    if (thumbX < 0) {
                        thumbX = 0;
                    } else if (thumbX > width) {
                        thumbX = width;
                    }
                    this.progress = thumbX / (float) width;
                }
                if (startX != -1 && Math.abs(x - startX) > AndroidUtilities.getPixelsInCM(0.2f, true)) {
                    if (parentView != null && parentView.getParent() != null) {
                        parentView.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    startDraging = true;
                    startX = -1;
                }
                return true;
            }
        }
        return false;
    }

    public float getProgress() {
        return thumbX / (float) width;
    }

    public void setProgress(float progress) {
        setProgress(progress, false);
    }

    public void setProgress(float progress, boolean animated) {
        if (!delegate.isSeekBarDragAllowed()) {
            this.progress = 1f;
            return;
        }
        this.progress = isUnread ? 1f : progress;
        int currentThumbX = isUnread ? width : thumbX;
        if (animated && currentThumbX != 0 && progress == 0) {
            clearProgress = 0f;
        } else if (!animated) {
            clearProgress = 1f;
        }
        thumbX = (int) Math.ceil(width * progress);
        if (thumbX < 0) {
            thumbX = 0;
        } else if (thumbX > width) {
            thumbX = width;
        }
    }

    public boolean isDragging() {
        return pressed;
    }

    public void setSize(int w, int h) {
        setSize(w, h, w, w);
    }

    public void setSize(int w, int h, int fromW, int toW) {
        int wasWidth = width;
        width = w;
        height = h;
        if (heights == null || heights.length != (int) (width / AndroidUtilities.dpf2(3))) {
            heights = calculateHeights((int) (width / AndroidUtilities.dpf2(3)));
        }
        if (fromW != toW && (fromWidth != fromW || toWidth != toW)) {
            fromWidth = fromW;
            toWidth = toW;
            fromHeights = calculateHeights((int) (fromWidth / AndroidUtilities.dpf2(3)));
            toHeights = calculateHeights((int) (toWidth / AndroidUtilities.dpf2(3)));
        } else if (fromW == toW) {
            fromHeights = toHeights = null;
        }
    }

    public void setSent() {
        appearFloat.set(0, true);
        if (parentView != null) {
            parentView.invalidate();
        }
    }

    private float[] calculateHeights(int count) {
        if (waveformBytes == null || count <= 0) {
            return null;
        }
        float[] heights = new float[count];
        byte value;
        int samplesCount = (waveformBytes.length * 8 / 5);
        float samplesPerBar = samplesCount / (float) count;
        float barCounter = 0;
        int nextBarNum = 0;

        int barNum = 0;
        int lastBarNum;
        int drawBarCount;
        for (int a = 0; a < samplesCount; a++) {
            if (a != nextBarNum) {
                continue;
            }
            drawBarCount = 0;
            lastBarNum = nextBarNum;
            while (lastBarNum == nextBarNum) {
                barCounter += samplesPerBar;
                nextBarNum = (int) barCounter;
                drawBarCount++;
            }

            int bitPointer = a * 5;
            int byteNum = bitPointer / 8;
            int byteBitOffset = bitPointer - byteNum * 8;
            int currentByteCount = 8 - byteBitOffset;
            int nextByteRest = 5 - currentByteCount;
            value = (byte) ((waveformBytes[byteNum] >> byteBitOffset) & ((2 << (Math.min(5, currentByteCount) - 1)) - 1));
            if (nextByteRest > 0 && byteNum + 1 < waveformBytes.length) {
                value <<= nextByteRest;
                value |= waveformBytes[byteNum + 1] & ((2 << (nextByteRest - 1)) - 1);
            }

            for (int b = 0; b < drawBarCount; b++) {
                if (barNum >= heights.length) {
                    return heights;
                }
                heights[barNum++] = Math.max(0, 7 * value / 31.0f);
            }
        }
        return heights;
    }

    private boolean exploding = false;
    public float explodeProgress;
    public void explodeAt(float progress) {
        exploding = true;
        explodeProgress = progress;
        invalidate();
    }
    public float explosionRate;
    public void setExplosionRate(float explosionRate) {
        this.explosionRate = explosionRate;
        invalidate();
    }
    public void stopExploding() {
        exploding = false;
        if (particles != null) {
            particles.clear();
        }
        invalidate();
    }

    private Particles particles;

    public void draw(Canvas canvas, View parentView) {
        if (waveformBytes == null || width == 0 || alpha <= 0) {
            return;
        }
        float totalBarsCount = width / AndroidUtilities.dpf2(3);
        if (totalBarsCount <= 0.1f) {
            return;
        }
        if (clearProgress != 1f) {
            clearProgress += 16 / 150f;
            if (clearProgress > 1f) {
                clearProgress = 1f;
            } else {
                parentView.invalidate();
            }
        }

        float appearProgress = appearFloat.set(1f);

        if (path == null) {
            path = new Path();
        } else {
            path.reset();
        }

        float alpha = 0;
        if (alphaPath == null) {
            alphaPath = new Path();
        } else {
            alphaPath.reset();
        }

        final boolean reverse = delegate != null && delegate.reverseWaveform();
        if (fromHeights != null && toHeights != null) {
            float t = (width - fromWidth) / (float) (toWidth - fromWidth);
            int maxlen = Math.max(fromHeights.length, toHeights.length);
            int minlen = Math.min(fromHeights.length, toHeights.length);
            float[] minarr = fromHeights.length < toHeights.length ? fromHeights : toHeights;
            float[] maxarr = fromHeights.length < toHeights.length ? toHeights : fromHeights;
//            t = CubicBezierInterpolator.EASE_OUT.getInterpolation(t);
            float T = fromHeights.length < toHeights.length ? t : 1f - t;
            int k = -1;
            for (int barNum = 0; barNum < maxlen; ++barNum) {
                int l = MathUtils.clamp((int) Math.floor(barNum / (float) maxlen * minlen), 0, minlen - 1);
                if (k < l) {
                    float x = AndroidUtilities.lerp((float) l, (float) barNum, T) * AndroidUtilities.dpf2(3);
                    float h = AndroidUtilities.dpf2(AndroidUtilities.lerp(minarr[reverse ? minarr.length - 1 - l : l], maxarr[reverse ? maxarr.length - 1 - barNum : barNum], T));
                    addBar(path, x, h);
                    k = l;
                } else {
                    float x = AndroidUtilities.lerp((float) l, (float) barNum, T) * AndroidUtilities.dpf2(3);
                    float h = AndroidUtilities.dpf2(AndroidUtilities.lerp(minarr[reverse ? minarr.length - 1 - l : l], maxarr[reverse ? maxarr.length - 1 - barNum : barNum], T));
                    addBar(alphaPath, x, h);
                    alpha = T;
                }
            }
        } else if (heights != null) {
            for (int barNum = 0; barNum < totalBarsCount; barNum++) {
                if (barNum >= heights.length) {
                    break;
                }
                float x = barNum * AndroidUtilities.dpf2(3);
                float bart = MathUtils.clamp(appearProgress * totalBarsCount - barNum, 0, 1);
                float h = AndroidUtilities.dpf2(heights[reverse ? heights.length - 1 - barNum : barNum]) * bart;
                h -= AndroidUtilities.dpf2(1) * (1f - bart);
                addBar(path, x, h);
            }
        }

        if (exploding || explosionRate > 0) {
            canvas.save();
            final float w = totalBarsCount * AndroidUtilities.dpf2(3);
            canvas.clipRect(0, 0, w * (1f - explodeProgress * explosionRate), height);
        }

        if (alpha > 0) {
            canvas.save();
            canvas.clipPath(alphaPath);
            drawFill(canvas, alpha * this.alpha);
            canvas.restore();
        }

        canvas.save();
        canvas.clipPath(path);
        drawFill(canvas, this.alpha);
        canvas.restore();

        if (exploding || explosionRate > 0) {
            canvas.restore();
            if (particles == null) {
                particles = new Particles(250, this::invalidate);
            }
            RectF emitArea = null;
            if (explodeProgress < .99f && heights != null) {
                int barNum = (int) (totalBarsCount * (1f - explodeProgress));
                if (reverse) {
                    barNum = (int) (totalBarsCount - 1 - barNum);
                }
                if (barNum >= 0 && barNum < heights.length) {
                    float bart = MathUtils.clamp(appearProgress * totalBarsCount - barNum, 0, 1);
                    float h = AndroidUtilities.dpf2(heights[barNum]) * bart;
                    emitArea = AndroidUtilities.rectTmp;
                    final float x = (totalBarsCount * (1f - explodeProgress)) * AndroidUtilities.dpf2(3);
                    final float strokeWidth = AndroidUtilities.dpf2(2);
                    final int y = (height - dp(14)) / 2;
                    h *= waveScaling;
                    AndroidUtilities.rectTmp.set(
                        x + AndroidUtilities.dpf2(1) - strokeWidth / 2f,
                        y + dp(7) + (-h - strokeWidth / 2f),
                        x + AndroidUtilities.dpf2(1) + strokeWidth / 2f,
                        y + dp(7) + (h + strokeWidth / 2f)
                    );
                }
            }
            particles
                .setColor(outerColor)
                .setEmitArea(emitArea)
                .draw(canvas, explosionRate);
        }
    }

    private void drawFill(Canvas canvas, float alpha) {
        final float strokeWidth = AndroidUtilities.dpf2(2);

        isUnread = messageObject != null && messageObject.isContentUnread() && !messageObject.isOut() && this.progress <= 0;
        paintInner.setColor(isUnread ? outerColor : (selected ? selectedColor : innerColor));
        paintOuter.setColor(outerColor);

        loadingFloat.setParent(parentView);
        boolean isPlaying = MediaController.getInstance().isPlayingMessage(messageObject);
        float loadingT = loadingFloat.set(loading && !isPlaying ? 1f : 0f);
        paintInner.setColor(ColorUtils.blendARGB(paintInner.getColor(), innerColor, loadingT));
        paintOuter.setAlpha((int) (paintOuter.getAlpha() * (1f - loadingT) * alpha));
        paintInner.setAlpha((int) (paintInner.getAlpha() * alpha));

        canvas.drawRect(0, 0, width + strokeWidth, height, paintInner);
        if (loadingT < 1f) {
            canvas.drawRect(0, 0, (this.progress * (width + strokeWidth)) * (1f - loadingT), height, paintOuter);
        }

        if (loadingT > 0f) {
            if (loadingPaint == null || Math.abs(loadingPaintWidth - width) > dp(8) || loadingPaintColor1 != innerColor || loadingPaintColor2 != outerColor) {
                if (loadingPaint == null) {
                    loadingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                }
                loadingPaintColor1 = innerColor;
                loadingPaintColor2 = outerColor;
                loadingPaint.setShader(new LinearGradient(0, 0, loadingPaintWidth = width, 0, new int[]{ loadingPaintColor1, loadingPaintColor2, loadingPaintColor1 }, new float[]{0, 0.2f, 0.4f}, Shader.TileMode.CLAMP));
            }
            loadingPaint.setAlpha((int) (255 * loadingT * alpha));
            canvas.save();
            float t = (SystemClock.elapsedRealtime() - loadingStart) / 270f;
            t = (float) Math.pow(t, 0.75f);
            float dx = (t % 1.6f - .6f) * loadingPaintWidth;
            canvas.translate(dx, 0);
            canvas.drawRect(-dx, 0, width + 5 - dx, height, loadingPaint);
            canvas.restore();

            if (parentView != null) {
                parentView.invalidate();
            }
        }
    }

    private void addBar(Path path, float x, float h) {
        final float strokeWidth = AndroidUtilities.dpf2(2);
        final int y = (height - dp(14)) / 2;
        h *= waveScaling;
        AndroidUtilities.rectTmp.set(
            x + AndroidUtilities.dpf2(1) - strokeWidth / 2f,
            y + dp(7) + (-h - strokeWidth / 2f),
            x + AndroidUtilities.dpf2(1) + strokeWidth / 2f,
            y + dp(7) + (h + strokeWidth / 2f)
        );
        path.addRoundRect(AndroidUtilities.rectTmp, strokeWidth, strokeWidth, Path.Direction.CW);
    }

    public void setWaveScaling(float waveScaling) {
        this.waveScaling = waveScaling;
    }

    public void setLoading(boolean loading) {
        if (!this.loading && loading && this.loadingFloat.get() <= 0) {
            loadingStart = SystemClock.elapsedRealtime();
        }
        this.loading = loading;
        if (parentView != null) {
            parentView.invalidate();
        }
    }

    public static class Particles {
        private final int count;
        private Runnable invalidate;
        private final ArrayList<Particle> particles = new ArrayList<>(50);
        private final ArrayList<Particle> deadParticles = new ArrayList<>(50);

        public Particles(int count, Runnable invalidate) {
            this.count = count;
            this.invalidate = invalidate;
            paint.setStrokeWidth(dp(1.33f));
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private class Particle {
            float x, y, v, vx, vy, t, d;
        }

        public Particles setColor(int color) {
            paint.setColor(color);
            return this;
        }

        private RectF emitArea;
        public Particles setEmitArea(RectF emitArea) {
            this.emitArea = emitArea;
            return this;
        }

        public void clear() {
            deadParticles.addAll(particles);
            particles.clear();
        }

        private long lastTime;
        public void draw(Canvas canvas, float alpha) {
            final long now = System.currentTimeMillis();
            final long dt = Math.min(20, (now - lastTime));
            lastTime = now;
            for (int i = 0; i < particles.size(); ++i) {
                Particle p = particles.get(i);
                p.t -= dt / p.d;
                if (p.t < 0) {
                    deadParticles.add(p);
                    particles.remove(i);
                    i--;
                } else {
                    p.x += p.vx * p.v * dt / 500.0f;
                    p.y += p.vy * p.v * dt / 500.0f;
                    p.vy -= dp(0.33f) * dt / 500.0f;
                }
            }
            if (emitArea != null) {
                int count = Math.min(4, this.count - particles.size());
//                double vx = Math.sin(Math.PI / 180.0 * (0 - 90));
//                double vy = -Math.cos(Math.PI / 180.0 * (0 - 90));
                for (int i = 0; i < count; ++i) {
                    Particle p = deadParticles.isEmpty() ? new Particle() :deadParticles.remove(0);
                    p.x = emitArea.left + emitArea.width() * Utilities.random.nextFloat();
                    p.y = emitArea.top + emitArea.height() * Utilities.random.nextFloat();
                    double angle = (Math.PI / 180.0) * (Utilities.random.nextInt(200) - 125);
                    p.vx = (float) (Math.cos(angle) - Math.sin(angle)) * .8f;
                    p.vy = (float) (Math.sin(angle) + Math.cos(angle)) - .2f;
                    p.t = 1f;
                    p.v = AndroidUtilities.dp(10 + Utilities.random.nextFloat() * 7);
                    p.d = AndroidUtilities.lerp(420, 550, Utilities.random.nextFloat());
                    particles.add(p);
                }
            }
            for (int i = 0; i < particles.size(); ++i) {
                Particle p = particles.get(i);
                paint.setAlpha((int) (0xFF * alpha * p.t));
                canvas.drawPoint(p.x, p.y, paint);
            }
            if (invalidate != null) {
                invalidate.run();
            }
        }
    }
}
