package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Premium.StarParticlesView;

import java.util.Arrays;

public class CacheChart extends View {

    private RectF chartBounds = new RectF();
    private RectF chartInnerBounds = new RectF();

    private static final int SECTIONS_COUNT = 9;
    private static final String[] colorKeys = new String[] {
        Theme.key_statisticChartLine_lightblue,
        Theme.key_statisticChartLine_blue,
        Theme.key_statisticChartLine_green,
        Theme.key_statisticChartLine_red,
        Theme.key_statisticChartLine_lightgreen,
        Theme.key_statisticChartLine_orange,
        Theme.key_statisticChartLine_cyan,
        Theme.key_statisticChartLine_purple,
        Theme.key_statisticChartLine_golden
    };

    private static final int[] particles = new int[] {
        R.raw.cache_photos,
        R.raw.cache_videos,
        R.raw.cache_documents,
        R.raw.cache_music,
        R.raw.cache_videos,
        R.raw.cache_stickers,
        R.raw.cache_profile_photos,
        R.raw.cache_other,
        R.raw.cache_other
    };

    private boolean loading = true;
    public AnimatedFloat loadingFloat = new AnimatedFloat(this, 750, CubicBezierInterpolator.EASE_OUT_QUINT);

    private boolean complete = false;
    private AnimatedFloat completeFloat = new AnimatedFloat(this, 750, CubicBezierInterpolator.EASE_OUT_QUINT);

    private Sector[] sectors = new Sector[SECTIONS_COUNT];

    private float[] segmentsTmp = new float[2];
    private RectF roundingRect = new RectF();
    private Paint loadingBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private RectF completePathBounds;
    private Path completePath = new Path();
    private Paint completePaintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint completePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient completeGradient;
    private Matrix completeGradientMatrix;

    private AnimatedTextView.AnimatedTextDrawable topText = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
    private AnimatedTextView.AnimatedTextDrawable bottomText = new AnimatedTextView.AnimatedTextDrawable(false, true, true);

    private StarParticlesView.Drawable completeDrawable;

    private static long particlesStart = -1;
    class Sector {

        Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap particle;

        float angleCenter, angleSize;
        AnimatedFloat angleCenterAnimated = new AnimatedFloat(CacheChart.this, 650, CubicBezierInterpolator.EASE_OUT_QUINT);
        AnimatedFloat angleSizeAnimated = new AnimatedFloat(CacheChart.this, 650, CubicBezierInterpolator.EASE_OUT_QUINT);
        float textAlpha;
        AnimatedFloat textAlphaAnimated = new AnimatedFloat(CacheChart.this, 0, 150, CubicBezierInterpolator.EASE_OUT);
        float textScale = 1;
        AnimatedFloat textScaleAnimated = new AnimatedFloat(CacheChart.this, 0, 150, CubicBezierInterpolator.EASE_OUT);
        AnimatedTextView.AnimatedTextDrawable text = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        float particlesAlpha;
        AnimatedFloat particlesAlphaAnimated = new AnimatedFloat(CacheChart.this, 0, 150, CubicBezierInterpolator.EASE_OUT);

        boolean selected;
        AnimatedFloat selectedAnimated = new AnimatedFloat(CacheChart.this, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);

        {
            text.setTextColor(Color.WHITE);
            text.setAnimationProperties(.35f, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
            text.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            text.setTextSize(AndroidUtilities.dp(15));
            text.setGravity(Gravity.CENTER);
        }

        Path path = new Path();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF pathBounds = new RectF();
        Paint uncut = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
        {
            cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            particlePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        }
        RectF rectF = new RectF();

        int gradientWidth;
        RadialGradient gradient;
        Matrix gradientMatrix;

        private float lastAngleCenter, lastAngleSize, lastRounding, lastThickness, lastWidth, lastCx, lastCy;

        private void setupPath(
            RectF outerRect,
            RectF innerRect,
            float angleCenter, float angleSize,
            float rounding
        ) {

            rounding = Math.min(rounding, (outerRect.width() - innerRect.width()) / 4);
            rounding = Math.min(rounding, (float) (Math.PI * (angleSize / 180f) * (innerRect.width() / 2f)));

            float thickness = (outerRect.width() - innerRect.width()) / 2f;
            if (lastAngleCenter == angleCenter &&
                lastAngleSize == angleSize &&
                lastRounding == rounding &&
                lastThickness == thickness &&
                lastWidth == outerRect.width() &&
                lastCx == outerRect.centerX() &&
                lastCy == outerRect.centerY()
            ) {
                return;
            }
            lastAngleCenter = angleCenter;
            lastAngleSize = angleSize;
            lastRounding = rounding;
            lastThickness = thickness;
            lastWidth = outerRect.width();
            lastCx = outerRect.centerX();
            lastCy = outerRect.centerY();

            float angleFrom = angleCenter - angleSize;
            float angleTo = angleCenter + angleSize;

            boolean hasRounding = rounding > 0;

            final float roundingOuterAngle = rounding / (float) (Math.PI * (outerRect.width() - rounding * 2)) * 360f;
            final float roundingInnerAngle = rounding / (float) (Math.PI * (innerRect.width() + rounding * 2)) * 360f + SEPARATOR_ANGLE / 4f * (angleSize > 175f ? 0 : 1);

            final float outerRadiusMinusRounding = outerRect.width() / 2 - rounding;
            final float innerRadiusPlusRounding = innerRect.width() / 2 + rounding;

            path.rewind();
            if (angleTo - angleFrom < SEPARATOR_ANGLE / 4f) {
                return;
            }
            if (hasRounding) {
                setCircleBounds(
                    roundingRect,
                    outerRect.centerX() + outerRadiusMinusRounding * Math.cos(toRad(angleFrom + roundingOuterAngle)),
                    outerRect.centerY() + outerRadiusMinusRounding * Math.sin(toRad(angleFrom + roundingOuterAngle)),
                    rounding
                );
                path.arcTo(roundingRect, angleFrom + roundingOuterAngle - 90, 90);
            }
            path.arcTo(outerRect, angleFrom + roundingOuterAngle, angleTo - angleFrom - roundingOuterAngle * 2);
            if (hasRounding) {
                setCircleBounds(
                    roundingRect,
                    outerRect.centerX() + outerRadiusMinusRounding * Math.cos(toRad(angleTo - roundingOuterAngle)),
                    outerRect.centerY() + outerRadiusMinusRounding * Math.sin(toRad(angleTo - roundingOuterAngle)),
                    rounding
                );
                path.arcTo(roundingRect, angleTo - roundingOuterAngle, 90);
                setCircleBounds(
                    roundingRect,
                    innerRect.centerX() + innerRadiusPlusRounding * Math.cos(toRad(angleTo - roundingInnerAngle)),
                    innerRect.centerY() + innerRadiusPlusRounding * Math.sin(toRad(angleTo - roundingInnerAngle)),
                    rounding
                );
                path.arcTo(roundingRect, angleTo - roundingInnerAngle + 90, 90);
            }
            path.arcTo(innerRect, angleTo - roundingInnerAngle, -(angleTo - angleFrom - roundingInnerAngle * 2));
            if (hasRounding) {
                setCircleBounds(
                    roundingRect,
                    innerRect.centerX() + innerRadiusPlusRounding * Math.cos(toRad(angleFrom + roundingInnerAngle)),
                    innerRect.centerY() + innerRadiusPlusRounding * Math.sin(toRad(angleFrom + roundingInnerAngle)),
                    rounding
                );
                path.arcTo(roundingRect, angleFrom + roundingInnerAngle + 180, 90);
            }
            path.close();

            path.computeBounds(pathBounds, false);
        }

        private void setGradientBounds(float centerX, float centerY, float radius, float angle) {
            gradientMatrix.reset();
            gradientMatrix.setTranslate(centerX, centerY);
//            gradientMatrix.preRotate(angle);
            gradient.setLocalMatrix(gradientMatrix);
        }

        private void drawParticles(
            Canvas canvas,
            float cx, float cy,
            float textX, float textY,
            float angleStart, float angleEnd,
            float innerRadius, float outerRadius,
            float textAlpha, float alpha
        ) {
            if (alpha <= 0) {
                return;
            }
            long now = System.currentTimeMillis();
            float sqrt2 = (float) Math.sqrt(2);
            if (particlesStart < 0) {
                particlesStart = now;
            }
            float time = (now - particlesStart) / 10000f;
            if (particle != null || !pathBounds.isEmpty()) {
                int sz = particle.getWidth();

                float stepangle = 7f;

                angleStart = angleStart % 360;
                angleEnd = angleEnd % 360;

                int fromAngle = (int) Math.floor(angleStart / stepangle);
                int toAngle = (int) Math.ceil(angleEnd / stepangle);

                for (int i = fromAngle; i <= toAngle; ++i) {
                    float angle = i * stepangle;

                    float t = (float) (((time + 100) * (1f + (Math.sin(angle * 2000) + 1) * .25f)) % 1);

                    float r = lerp(innerRadius - sz * sqrt2, outerRadius + sz * sqrt2, t);

                    float x = (float) (cx + r * Math.cos(toRad(angle)));
                    float y = (float) (cy + r * Math.sin(toRad(angle)));

                    float particleAlpha =
                        .65f
                        * alpha
                        * (-1.75f * Math.abs(t - .5f) + 1)
                        * (.25f * (float) (Math.sin(t * Math.PI) - 1) + 1)
                        * lerp(1, Math.min(MathUtils.distance(x, y, textX, textY) / AndroidUtilities.dpf2(64), 1), textAlpha);
                    particleAlpha = Math.max(0, Math.min(1, particleAlpha));
                    particlePaint.setAlpha((int) (0xFF * particleAlpha));

                    float s = (float) (.75f * (.25f * (float) (Math.sin(t * Math.PI) - 1) + 1) * (.8f + (Math.sin(angle) + 1) * .25f));

                    canvas.save();
                    canvas.translate(x, y);
                    canvas.scale(s, s);
                    canvas.drawBitmap(particle, -(sz >> 1), -(sz >> 1), particlePaint);
                    canvas.restore();
                }
            }
        }

        void draw(
            Canvas canvas,
            RectF outerRect, RectF innerRect,
            float angleCenter, float angleSize,
            float rounding,
            float alpha,
            float textAlpha
        ) {
            float selected = selectedAnimated.set(this.selected ? 1 : 0);
            rectF.set(outerRect);
            rectF.inset(selected * -AndroidUtilities.dp(9), selected * -AndroidUtilities.dp(9));

            float x = (float) (rectF.centerX() + Math.cos(toRad(angleCenter)) * (rectF.width() + innerRect.width()) / 4);
            float y = (float) (rectF.centerY() + Math.sin(toRad(angleCenter)) * (rectF.width() + innerRect.width()) / 4);
            float loading = textAlpha;
            textAlpha *= alpha * textAlphaAnimated.set(this.textAlpha);
            float particlesAlpha = particlesAlphaAnimated.set(this.particlesAlpha);

            paint.setAlpha((int) (0xFF * alpha));
            if (angleSize * 2 >= 359f) {
                canvas.saveLayerAlpha(rectF, 0xFF, Canvas.ALL_SAVE_FLAG);
                canvas.drawCircle(rectF.centerX(), rectF.centerY(), rectF.width() / 2, uncut);
                canvas.drawRect(rectF, paint);
                drawParticles(canvas, rectF.centerX(), rectF.centerY(), x, y, angleCenter - angleSize, angleCenter + angleSize, innerRect.width() / 2f, rectF.width() / 2f, textAlpha, Math.max(0, loading / .75f - .75f) * particlesAlpha);
                canvas.drawCircle(innerRect.centerX(), innerRect.centerY(), innerRect.width() / 2, cut);
                canvas.restore();
            } else {
                setupPath(rectF, innerRect, angleCenter, angleSize, rounding);
                setGradientBounds(rectF.centerX(), outerRect.centerY(), rectF.width() / 2, angleCenter);

                canvas.saveLayerAlpha(rectF, 0xFF, Canvas.ALL_SAVE_FLAG);
                canvas.drawPath(path, uncut);
                canvas.drawRect(rectF, paint);
                drawParticles(canvas, rectF.centerX(), rectF.centerY(), x, y, angleCenter - angleSize, angleCenter + angleSize, innerRect.width() / 2f, rectF.width() / 2f, textAlpha, Math.max(0, loading / .75f - .75f) * particlesAlpha);
                canvas.restore();
            }

            float textScale = textScaleAnimated.set(this.textScale);
            setCircleBounds(roundingRect, x, y, 0);
            if (textScale != 1) {
                canvas.save();
                canvas.scale(textScale, textScale, roundingRect.centerX(), roundingRect.centerY());
            }
            text.setAlpha((int) (255 * textAlpha));
            text.setBounds((int) roundingRect.left, (int) roundingRect.top, (int) roundingRect.right, (int) roundingRect.bottom);
            text.draw(canvas);
            if (textScale != 1) {
                canvas.restore();
            }
        }
    }

    public CacheChart(Context context) {
        super(context);

        loadingBackgroundPaint.setStyle(Paint.Style.STROKE);
        loadingBackgroundPaint.setColor(Theme.getColor(Theme.key_listSelector));

        completePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        completeGradient = new LinearGradient(0, 0, 0, AndroidUtilities.dp(200), new int[] { 0x006ED556, 0xFF6ED556, 0xFF41BA71, 0x0041BA71 }, new float[] { 0, .07f, .93f, 1 }, Shader.TileMode.CLAMP);
        completeGradientMatrix = new Matrix();
        completePaintStroke.setShader(completeGradient);
        completePaint.setShader(completeGradient);
        completePaintStroke.setStyle(Paint.Style.STROKE);
        completePaintStroke.setStrokeCap(Paint.Cap.ROUND);
        completePaintStroke.setStrokeJoin(Paint.Join.ROUND);

        topText.setAnimationProperties(.2f, 0, 450, CubicBezierInterpolator.EASE_OUT_QUINT);
        topText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        topText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        topText.setTextSize(AndroidUtilities.dp(32));
        topText.setGravity(Gravity.CENTER);

        bottomText.setAnimationProperties(.6f, 0, 450, CubicBezierInterpolator.EASE_OUT_QUINT);
        bottomText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        bottomText.setTextSize(AndroidUtilities.dp(12));
        bottomText.setGravity(Gravity.CENTER);

        for (int i = 0; i < SECTIONS_COUNT; ++i) {
            Sector sector = sectors[i] = new Sector();
            final int color2 = Theme.blendOver(Theme.getColor(colorKeys[i]), 0x03000000);
            final int color1 = Theme.blendOver(Theme.getColor(colorKeys[i]), 0x30ffffff);
            sector.gradientWidth = AndroidUtilities.dp(50);
            sector.gradient = new RadialGradient(0, 0, dp(86), new int[]{ color1, color2 }, new float[] { .3f, 1 }, Shader.TileMode.CLAMP);
            sector.gradient.setLocalMatrix(sector.gradientMatrix = new Matrix());
            sector.paint.setShader(sector.gradient);
            sector.particle = SvgHelper.getBitmap(particles[i], AndroidUtilities.dp(16), AndroidUtilities.dp(16), 0xffffffff);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        float r = MathUtils.distance(chartBounds.centerX(), chartBounds.centerY(), x, y);
        float a = (float) (Math.atan2(y - chartBounds.centerY(), x - chartBounds.centerX()) / Math.PI * 180f);
        if (a < 0) {
            a += 360;
        }

        int index = -1;
        if (r > chartInnerBounds.width() / 2 && r < chartBounds.width() / 2f + AndroidUtilities.dp(14)) {
            for (int i = 0; i < sectors.length; ++i) {
                if (a >= sectors[i].angleCenter - sectors[i].angleSize &&
                    a <= sectors[i].angleCenter + sectors[i].angleSize) {
                    index = i;
                    break;
                }
            }
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            setSelected(index);
            if (index >= 0) {
                onSectionDown(index, index != -1);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            onSectionDown(index, index != -1);
            setSelected(index);
            if (index != -1) {
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            boolean done = false;
            if (index != -1) {
                onSectionClick(index);
                done = true;
            }
            setSelected(-1);
            onSectionDown(index, false);
            if (done) {
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            setSelected(-1);
            onSectionDown(index, false);
        }

        return super.dispatchTouchEvent(event);
    }

    protected void onSectionDown(int index, boolean down) {

    }

    protected void onSectionClick(int index) {

    }

    private int selectedIndex = -1;
    public void setSelected(int index) {
        if (index == selectedIndex) {
            return;
        }
        for (int i = 0; i < sectors.length; ++i) {
            if (index == i && sectors[i].angleSize <= 0) {
                index = -1;
            }
            sectors[i].selected = index == i;
        }
        selectedIndex = index;
        invalidate();
    }

    private static final float SEPARATOR_ANGLE = 2;

    private int[] tempPercents;
    private float[] tempFloat;

    public static class SegmentSize {
        int index;
        boolean selected;
        long size;

        public static SegmentSize of(long size, boolean selected) {
            SegmentSize segment = new SegmentSize();
            segment.size = size;
            segment.selected = selected;
            return segment;
        }
    }

    public void setSegments(long totalSize, SegmentSize ...segments) {
        if (segments == null || segments.length == 0) {
            loading = true;
            complete = totalSize == 0;
            topText.setText("");
            bottomText.setText("");
            for (int i = 0; i < sectors.length; ++i) {
                sectors[i].textAlpha = 0;
            }
            invalidate();
            return;
        }

        loading = false;

        SpannableString percent = new SpannableString("%");
//        percent.setSpan(new RelativeSizeSpan(0.733f), 0, percent.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int segmentsCount = segments.length;
        long segmentsSum = 0;
        for (int i = 0; i < segments.length; ++i) {
            if (segments[i] == null) {
                segments[i] = new SegmentSize();
                segments[i].size = 0;
            }
            segments[i].index = i;
            if (segments[i] != null && segments[i].selected) {
                segmentsSum += segments[i].size;
            }
            if (segments[i] == null || segments[i].size <= 0 || !segments[i].selected) {
                segmentsCount--;
            }
        }

        if (segmentsSum <= 0) {
            loading = true;
            complete = totalSize <= 0;
            topText.setText("");
            bottomText.setText("");
            for (int i = 0; i < sectors.length; ++i) {
                sectors[i].textAlpha = 0;
            }
            invalidate();
            return;
        }

        int underCount = 0;
        float minus = 0;
        for (int i = 0; i < segments.length; ++i) {
            float progress = segments[i] == null || !segments[i].selected ? 0 : (float) segments[i].size / segmentsSum;
            if (progress > 0 && progress < .02f) {
                underCount++;
                minus += progress;
            }
        }
        final int count = Math.min(segments.length, sectors.length);

        if (tempPercents == null || tempPercents.length != segments.length) {
            tempPercents = new int[segments.length];
        }
        if (tempFloat == null || tempFloat.length != segments.length) {
            tempFloat = new float[segments.length];
        }
        for (int i = 0; i < segments.length; ++i) {
            tempFloat[i] = segments[i] == null || !segments[i].selected ? 0 : segments[i].size / (float) segmentsSum;
        }
        AndroidUtilities.roundPercents(tempFloat, tempPercents);
        Arrays.sort(segments, (a, b) -> Long.compare(a.size, b.size));
        for (int i = 0; i < segments.length - 1; ++i) {
            if (segments[i].index == segments.length - 1) {
                int from = i, to = 0;
                SegmentSize temp = segments[to];
                segments[to] = segments[from];
                segments[from] = temp;
                break;
            }
        }

        final float sum = 360 - SEPARATOR_ANGLE * (segmentsCount < 2 ? 0 : segmentsCount);
        float prev = 0;
        for (int index = 0, k = 0; index < segments.length; ++index) {
            int i = segments[index].index;
            float progress = segments[index] == null || !segments[index].selected ? 0 : (float) segments[index].size / segmentsSum;
            SpannableStringBuilder string = new SpannableStringBuilder();
            string.append(String.format("%d", tempPercents[i]));
            string.append(percent);
            sectors[i].textAlpha = progress > .05 && progress < 1 ? 1f : 0f;
            sectors[i].textScale = progress < .08f || tempPercents[i] >= 100 ? .85f : 1f;
            sectors[i].particlesAlpha = 1;
            if (sectors[i].textAlpha > 0) {
                sectors[i].text.setText(string);
            }
            if (progress < .02f && progress > 0) {
                progress = .02f;
            } else {
                progress *= 1f - (.02f * underCount - minus);
            }
            float angleFrom = (prev * sum + k * SEPARATOR_ANGLE);
            float angleTo = angleFrom + progress * sum;
            if (progress <= 0) {
                sectors[i].angleCenter = (angleFrom + angleTo) / 2;
                sectors[i].angleSize = Math.abs(angleTo - angleFrom) / 2;
                sectors[i].textAlpha = 0;
                continue;
            }
            sectors[i].angleCenter = (angleFrom + angleTo) / 2;
            sectors[i].angleSize = Math.abs(angleTo - angleFrom) / 2;
            prev += progress;
            k++;
        }

        String[] fileSize = AndroidUtilities.formatFileSize(segmentsSum).split(" ");
        String top = fileSize.length > 0 ? fileSize[0] : "";
        if (top.length() >= 4 && segmentsSum < 1024L * 1024L * 1024L) {
            top = top.split("\\.")[0];
        }
        topText.setText(top);
        bottomText.setText(fileSize.length > 1 ? fileSize[1] : "");

        invalidate();
    }

    private static float toRad(float angles) {
        return (float) (angles / 180f * Math.PI);
    }

    private static float toAngl(float rad) {
        return (float) (rad / Math.PI * 180f);
    }

    private static float lerpAngle(float a, float b, float f) {
        return (a + (((b - a + 360 + 180) % 360) - 180) * f + 360) % 360;
    }

    private static void setCircleBounds(RectF rect, float centerX, float centerY, float radius) {
        rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    }
    private static void setCircleBounds(RectF rect, double centerX, double centerY, float radius) {
        setCircleBounds(rect, (float) centerX, (float) centerY, radius);
    }

    private static Long start, loadedStart;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final float loading = loadingFloat.set(this.loading ? 1f : 0f);
        final float complete = completeFloat.set(this.complete ? 1f : 0f);

        chartInnerBounds.set(chartBounds);
        final float thickness = lerp(dpf2(38), dpf2(10), loading);
        chartInnerBounds.inset(thickness, thickness);

        final float rounding = lerp(0, dp(60), loading);

        if (start == null) {
            start = System.currentTimeMillis();
        }
        if (!this.loading && loadedStart == null) {
            loadedStart = System.currentTimeMillis();
        } else if (this.loading && loadedStart != null) {
            loadedStart = null;
        }
        float loadingTime = ((loadedStart == null ? System.currentTimeMillis() : loadedStart) - start) * 0.6f;

        CircularProgressDrawable.getSegments(loadingTime % 5400, segmentsTmp);
        float minAngle = segmentsTmp[0], maxAngle = segmentsTmp[1];

        if (loading > 0) {
            loadingBackgroundPaint.setStrokeWidth(thickness);
            int wasAlpha = loadingBackgroundPaint.getAlpha();
            loadingBackgroundPaint.setAlpha((int) (wasAlpha * loading));
            canvas.drawCircle(chartBounds.centerX(), chartBounds.centerY(), (chartBounds.width() - thickness) / 2, loadingBackgroundPaint);
            loadingBackgroundPaint.setAlpha(wasAlpha);
        }

        boolean wouldUpdate = loading > 0 || complete > 0;

        for (int i = 0; i < SECTIONS_COUNT; ++i) {
            Sector sector = sectors[i];

            CircularProgressDrawable.getSegments((loadingTime + i * 80) % 5400, segmentsTmp);
            float angleFrom = Math.min(Math.max(segmentsTmp[0], minAngle), maxAngle);
            float angleTo =   Math.min(Math.max(segmentsTmp[1], minAngle), maxAngle);
            if (loading >= 1 && angleFrom >= angleTo) {
                continue;
            }

            float angleCenter = (angleFrom + angleTo) / 2;
            float angleSize = Math.abs(angleTo - angleFrom) / 2;
            if (loading <= 0) {
                angleCenter = sector.angleCenterAnimated.set(sector.angleCenter);
                angleSize = sector.angleSizeAnimated.set(sector.angleSize);
            } else if (loading < 1) {
                float angleCenterSector = sector.angleCenterAnimated.set(sector.angleCenter);
                angleCenter = lerp(angleCenterSector + (float) Math.floor(maxAngle / 360) * 360, angleCenter, loading);
                angleSize = lerp(sector.angleSizeAnimated.set(sector.angleSize), angleSize, loading);
            }
            wouldUpdate = sector.angleCenterAnimated.isInProgress() || sector.angleSizeAnimated.isInProgress() || wouldUpdate;

            sector.draw(canvas, chartBounds, chartInnerBounds, angleCenter, angleSize, rounding, 1f - complete, 1f - loading);
        }

        topText.setAlpha((int) (255 * (1f - loading) * (1f - complete)));
        topText.setBounds((int) (chartBounds.centerX()), (int) (chartBounds.centerY() - AndroidUtilities.dp(5)), (int) (chartBounds.centerX()), (int) (chartBounds.centerY() - AndroidUtilities.dp(3)));
        topText.draw(canvas);
        wouldUpdate = topText.isAnimating() || wouldUpdate;

        bottomText.setAlpha((int) (255 * (1f - loading) * (1f - complete)));
        bottomText.setBounds((int) (chartBounds.centerX()), (int) (chartBounds.centerY() + AndroidUtilities.dp(22)), (int) (chartBounds.centerX()), (int) (chartBounds.centerY() + AndroidUtilities.dp(22)));
        bottomText.draw(canvas);
        wouldUpdate = bottomText.isAnimating() || wouldUpdate;

        if (complete > 0) {
            if (completeDrawable == null) {
                completeDrawable = new StarParticlesView.Drawable(25);
                completeDrawable.type = 100;
                completeDrawable.roundEffect = false;
                completeDrawable.useRotate = true;
                completeDrawable.useBlur = false;
                completeDrawable.checkBounds = true;
                completeDrawable.size1 = 18;
                completeDrawable.distributionAlgorithm = false;
                completeDrawable.excludeRadius = AndroidUtilities.dp(80);
                completeDrawable.k1 = completeDrawable.k2 = completeDrawable.k3 = 0.7f;
                completeDrawable.init();

                float d = Math.min(getMeasuredHeight(), Math.min(getMeasuredWidth(), AndroidUtilities.dp(150)));
                completeDrawable.rect.set(0, 0, d, d);
                completeDrawable.rect.offset((getMeasuredWidth() - completeDrawable.rect.width()) / 2, (getMeasuredHeight() - completeDrawable.rect.height()) / 2);
                completeDrawable.rect2.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                completeDrawable.resetPositions();
            }

            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 255, Canvas.ALL_SAVE_FLAG);
            completeDrawable.onDraw(canvas, complete);
            completePaint.setAlpha((int) (0xFF * complete));
            canvas.drawRect(0, 0, getWidth(), getHeight(), completePaint);
            canvas.restore();

            completePaintStroke.setStrokeWidth(thickness);
            completePaintStroke.setAlpha((int) (0xFF * complete));
            canvas.drawCircle(chartBounds.centerX(), chartBounds.centerY(), (chartBounds.width() - thickness) / 2, completePaintStroke);

            if (completePathBounds == null || completePathBounds.equals(chartBounds)) {
                if (completePathBounds == null) {
                    completePathBounds = new RectF();
                }
                completePathBounds.set(chartBounds);
                completePath.rewind();
                completePath.moveTo(chartBounds.left + chartBounds.width() * .348f, chartBounds.top + chartBounds.height() * .538f);
                completePath.lineTo(chartBounds.left + chartBounds.width() * .447f, chartBounds.top + chartBounds.height() * .636f);
                completePath.lineTo(chartBounds.left + chartBounds.width() * .678f, chartBounds.top + chartBounds.height() * .402f);
            }
            completePaintStroke.setStrokeWidth(AndroidUtilities.dp(10));
            canvas.drawPath(completePath, completePaintStroke);
        }

        if (wouldUpdate || true) {
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = dp(200);

        final int d = dp(172);
        chartBounds.set(
            (width - d) / 2f,
            (height - d) / 2f,
            (width + d) / 2f,
            (height + d) / 2f
        );

        completeGradientMatrix.reset();
        completeGradientMatrix.setTranslate(chartBounds.left, 0);
        completeGradient.setLocalMatrix(completeGradientMatrix);

        if (completeDrawable != null) {
            completeDrawable.rect.set(0, 0, AndroidUtilities.dp(140), AndroidUtilities.dp(140));
            completeDrawable.rect.offset((getMeasuredWidth() - completeDrawable.rect.width()) / 2, (getMeasuredHeight() - completeDrawable.rect.height()) / 2);
            completeDrawable.rect2.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            completeDrawable.resetPositions();
        }

        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        );
    }
}
