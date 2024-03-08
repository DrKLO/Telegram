package org.telegram.ui.Components.Premium;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Xfermode;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.GLIconSettingsView;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;

public class StarParticlesView extends View {


    public boolean doNotFling;
    public Drawable drawable;
    int size;
    public final static int TYPE_APP_ICON_REACT = 1001;
    public static final int TYPE_APP_ICON_STAR_PREMIUM = 1002;

    public StarParticlesView(Context context) {
        this(context, (
            SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH ?    200 :
            SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_AVERAGE ? 100 :
            50
        ));
    }

    public StarParticlesView(Context context, int particlesCount) {
        super(context);

        drawable = new Drawable(particlesCount);
        configure();
    }

    protected void configure() {
        drawable.type = 100;
        drawable.roundEffect = true;
        drawable.useRotate = true;
        drawable.useBlur = true;
        drawable.checkBounds = true;
        drawable.size1 = 4;
        drawable.k1 = drawable.k2 = drawable.k3 = 0.98f;
        drawable.init();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int sizeInternal = getMeasuredWidth() << 16 + getMeasuredHeight();
        drawable.rect.set(0, 0, getStarsRectWidth(), dp(140));
        drawable.rect.offset((getMeasuredWidth() - drawable.rect.width()) / 2, (getMeasuredHeight() - drawable.rect.height()) / 2);
        drawable.rect2.set(-dp(15), -dp(15), getMeasuredWidth() + dp(15), getMeasuredHeight() + dp(15));
        if (size != sizeInternal) {
            size = sizeInternal;
            drawable.resetPositions();
        }
    }

    protected int getStarsRectWidth() {
        return dp(140);
    }

    private Paint clipGradientPaint;
    private LinearGradient clipGradient;
    private Matrix clipGradientMatrix;

    public void setClipWithGradient() {
        clipGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clipGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        clipGradient = new LinearGradient(0, 0, 0, dp(12), new int[] { 0x00FFFFFF, 0xFFFFFFFF }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
        clipGradientPaint.setShader(clipGradient);
        clipGradientMatrix = new Matrix();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (clipGradientPaint != null) {
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
        }
        drawable.onDraw(canvas);
        if (clipGradientPaint != null) {
            canvas.save();
            clipGradientMatrix.reset();
            clipGradientMatrix.postTranslate(0, 1 + getHeight() - dp(12));
            clipGradient.setLocalMatrix(clipGradientMatrix);
            canvas.drawRect(0, 0, getWidth(), getHeight(), clipGradientPaint);
            canvas.restore();
            canvas.restore();
        }
        if (!drawable.paused) {
            invalidate();
        }
    }

    public void flingParticles(float sum) {
        if (doNotFling) return;
        float maxSpeed = 15f;
        if (sum < 60) {
            maxSpeed = 5f;
        } else if (sum < 180) {
            maxSpeed = 9f;
        }
        AnimatorSet animatorSet = new AnimatorSet();
        ValueAnimator.AnimatorUpdateListener updateListener = animation -> drawable.speedScale = (float) animation.getAnimatedValue();

        ValueAnimator a1 = ValueAnimator.ofFloat(1f, maxSpeed);
        a1.addUpdateListener(updateListener);
        a1.setDuration(600);

        ValueAnimator a2 = ValueAnimator.ofFloat(maxSpeed, 1f);
        a2.addUpdateListener(updateListener);
        a2.setDuration(2000);
        animatorSet.playTogether(a1, a2);
        animatorSet.start();
    }

    public static class Drawable {

        public RectF rect = new RectF();
        public RectF rect2 = new RectF();
        public RectF excludeRect = new RectF();
        private final Bitmap[] stars = new Bitmap[3];
        public boolean paused;
        public boolean startFromCenter;
        public Paint paint = new Paint();
        public float excludeRadius = 0;
        public float centerOffsetX = 0, centerOffsetY = 0;
        public Paint overridePaint;

        public ArrayList<Particle> particles = new ArrayList<>();
        public float speedScale = 1f;

        public final int count;
        public boolean useGradient;
        public int size1 = 14, size2 = 12, size3 = 10;
        public float k1 = 0.85f, k2 = 0.85f, k3 = 0.9f;
        public long minLifeTime = 2000;
        public int randLifeTime = 1000;
        private int lastColor;
        private final float dt = 1000 / AndroidUtilities.screenRefreshRate;
        public boolean distributionAlgorithm;
        Matrix matrix = new Matrix();
        Matrix matrix2 = new Matrix();
        Matrix matrix3 = new Matrix();
        float[] points1;
        float[] points2;
        float[] points3;
        int pointsCount1, pointsCount2, pointsCount3;
        public boolean useRotate;
        public boolean checkBounds = false;
        public boolean checkTime = true;
        public boolean isCircle = true;
        public boolean useBlur = false;
        public boolean forceMaxAlpha = false;
        public boolean roundEffect = true;
        public int type = -1;
        public Theme.ResourcesProvider resourcesProvider;
        public int colorKey = Theme.key_premiumStartSmallStarsColor;
        public final boolean[] svg = new boolean[3];
        public final boolean[] flip = new boolean[3];

        public long pausedTime;

        float a;
        float a1;
        float a2;

        public final static int TYPE_SETTINGS = 101;

        public Drawable(int count) {
            this.count = count;
            distributionAlgorithm = count < 50;
        }

        public void init() {
            if (useRotate) {
                points1 = new float[count * 2];
                points2 = new float[count * 2];
                points3 = new float[count * 2];
            }
            generateBitmaps();
            if (particles.isEmpty()) {
                for (int i = 0; i < count; i++) {
                    particles.add(new Particle());
                }
            }
        }

        public void updateColors() {
            int c = Theme.getColor(colorKey, resourcesProvider);
            if (lastColor != c) {
                lastColor = c;
                generateBitmaps();
            }
        }

        private void generateBitmaps() {
            for (int i = 0; i < 3; i++) {
                int size;
                float k = k1;
                Bitmap bitmap;
                if (i == 0) {
                    size = dp(size1);
                } else if (i == 1) {
                    k = k2;
                    size = dp(size2);
                } else {
                    k = k3;
                    size = dp(size3);
                }

                if (type == PremiumPreviewFragment.PREMIUM_FEATURE_ADVANCED_CHAT_MANAGEMENT) {
                    int res;
                    if (i == 0) {
                        res = R.raw.premium_object_folder;
                    } else if (i == 1) {
                        res = R.raw.premium_object_bubble;
                    } else {
                        res = R.raw.premium_object_settings;
                    }
                    stars[i] = SvgHelper.getBitmap(res, size, size, ColorUtils.setAlphaComponent(Theme.getColor(colorKey, resourcesProvider), 30));
                    svg[i] = true;
                    continue;
                } else if (type == PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI || type == PremiumPreviewFragment.PREMIUM_FEATURE_REACTIONS) {
                    int res;
                    if (i == 0) {
                        res = R.raw.premium_object_smile1;
                    } else if (i == 1) {
                        res = R.raw.premium_object_smile2;
                    } else {
                        res = R.raw.premium_object_like;
                    }
                    stars[i] = SvgHelper.getBitmap(res, size, size, ColorUtils.setAlphaComponent(Theme.getColor(colorKey, resourcesProvider), 30));
                    svg[i] = true;
                    continue;
                } else if (type == PremiumPreviewFragment.PREMIUM_FEATURE_WALLPAPER) {
                    int res;
                    if (i == 0) {
                        res = R.raw.premium_object_user;
                    } else if (i == 1) {
                        res = R.raw.cache_photos;
                    } else {
                        res = R.raw.cache_profile_photos;
                    }
                    stars[i] = SvgHelper.getBitmap(res, size, size, ColorUtils.setAlphaComponent(Theme.getColor(colorKey, resourcesProvider), 30));
                    svg[i] = true;
                    continue;
                } else if (type == PremiumPreviewFragment.PREMIUM_FEATURE_ADS) {
                    int res;
                    if (i == 0) {
                        res = R.raw.premium_object_adsbubble;
                    } else if (i == 1) {
                        res = R.raw.premium_object_like;
                    } else {
                        res = R.raw.premium_object_noads;
                    }
                    stars[i] = SvgHelper.getBitmap(res, size, size, ColorUtils.setAlphaComponent(Theme.getColor(colorKey, resourcesProvider), 30));
                    svg[i] = true;
                    continue;
                } else if (type == PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_AVATARS) {
                    int res;
                    if (i == 0) {
                        res = R.raw.premium_object_video2;
                    } else if (i == 1) {
                        res = R.raw.premium_object_video;
                    } else {
                        res = R.raw.premium_object_user;
                    }
                    stars[i] = SvgHelper.getBitmap(res, size, size, ColorUtils.setAlphaComponent(Theme.getColor(colorKey, resourcesProvider), 30));
                    svg[i] = true;
                    continue;
                } else if (type == TYPE_APP_ICON_REACT) {
                    stars[i] = SvgHelper.getBitmap(R.raw.premium_object_fire, size, size, ColorUtils.setAlphaComponent(Theme.getColor(colorKey, resourcesProvider), 30));
                    svg[i] = true;
                    continue;
                } else if (type == TYPE_APP_ICON_STAR_PREMIUM) {
                    stars[i] = SvgHelper.getBitmap(R.raw.premium_object_star2, size, size, ColorUtils.setAlphaComponent(Theme.getColor(colorKey, resourcesProvider), 30));
                    svg[i] = true;
                    continue;
                } else if (type == PremiumPreviewFragment.PREMIUM_FEATURE_SAVED_TAGS) {
                    int res;
                    if (i == 0) {
                        res = R.raw.premium_object_tag;
                    } else if (i == 1) {
                        res = R.raw.premium_object_check;
                    } else {
                        res = R.raw.premium_object_star;
                    }
                    stars[i] = SvgHelper.getBitmap(res, size, size, ColorUtils.setAlphaComponent(Theme.getColor(colorKey, resourcesProvider), 30));
                    svg[i] = true;
                    continue;
                } else if (type == PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS) {
                    int res;
                    if (i == 0) {
                        res = R.raw.filled_premium_dollar;
                        stars[i] = SvgHelper.getBitmap(res, size, size, ColorUtils.setAlphaComponent(Theme.getColor(colorKey, resourcesProvider), 0xFF));
                        flip[i] = true;
                        continue;
                    }
                }

                bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                stars[i] = bitmap;

                Canvas canvas = new Canvas(bitmap);

                if (type == PremiumPreviewFragment.PREMIUM_FEATURE_PROFILE_BADGE && (i == 1 || i == 2)) {
                    android.graphics.drawable.Drawable drawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_premium_liststar);
                    drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(colorKey, resourcesProvider), PorterDuff.Mode.MULTIPLY));
                    drawable.setBounds(0, 0, size, size);
                    drawable.draw(canvas);
                    continue;
                }
                Path path = new Path();
                int sizeHalf = size >> 1;
                int mid = (int) (sizeHalf * k);
                path.moveTo(0, sizeHalf);
                path.lineTo(mid, mid);
                path.lineTo(sizeHalf, 0);
                path.lineTo(size - mid, mid);
                path.lineTo(size, sizeHalf);
                path.lineTo(size - mid, size - mid);
                path.lineTo(sizeHalf, size);
                path.lineTo(mid, size - mid);
                path.lineTo(0, sizeHalf);
                path.close();

                Paint paint = new Paint();
                if (useGradient) {
                    if (size >= dp(10)) {
                        PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, size, size, -2 * size, 0);
                    } else {
                        PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, size, size, -4 * size, 0);
                    }
                    Paint paint1 = PremiumGradient.getInstance().getMainGradientPaint();
                    if (roundEffect) {
                        paint1.setPathEffect(new CornerPathEffect(AndroidUtilities.dpf2(size1 / 5f)));
                    }
                    if (forceMaxAlpha) {
                        paint1.setAlpha(0xFF);
                    } else if (useBlur) {
                        paint1.setAlpha(60);
                    } else {
                        paint1.setAlpha(120);
                    }
                    canvas.drawPath(path, paint1);
                    paint1.setPathEffect(null);
                    paint1.setAlpha(255);
                } else {
                    paint.setColor(getPathColor());
                    if (roundEffect) {
                        paint.setPathEffect(new CornerPathEffect(AndroidUtilities.dpf2(size1 / 5f)));
                    }
                    canvas.drawPath(path, paint);
                }
                if (useBlur) {
                    Utilities.stackBlurBitmap(bitmap, 2);
                }
            }
        }

        protected int getPathColor() {
            if (type == 100) {
                return ColorUtils.setAlphaComponent(Theme.getColor(colorKey, resourcesProvider), 200);
            } else {
                return Theme.getColor(colorKey, resourcesProvider);
            }
        }

        public void resetPositions() {
            long time = System.currentTimeMillis();
            for (int i = 0; i < particles.size(); i++) {
                particles.get(i).genPosition(time);
            }
        }

        public void onDraw(Canvas canvas) {
            onDraw(canvas, 1f);
        }

        private long prevTime;
        public void onDraw(Canvas canvas, float alpha) {
            long time = System.currentTimeMillis();
            long diff = MathUtils.clamp(time - prevTime, 4, 50);
            if (useRotate) {
                matrix.reset();
                a += 360f * (diff / 40000f);
                a1 += 360f * (diff / 50000f);
                a2 += 360f * (diff / 60000f);
                matrix.setRotate(a, rect.centerX() + centerOffsetX, rect.centerY() + centerOffsetY);
                matrix2.setRotate(a1, rect.centerX() + centerOffsetX, rect.centerY() + centerOffsetY);
                matrix3.setRotate(a2, rect.centerX() + centerOffsetX, rect.centerY() + centerOffsetY);

                pointsCount1 = 0;
                pointsCount2 = 0;
                pointsCount3 = 0;
                for (int i = 0; i < particles.size(); i++) {
                    Particle particle = particles.get(i);
                    particle.updatePoint();
                }
                matrix.mapPoints(points1, 0, points1, 0, pointsCount1);
                matrix2.mapPoints(points2, 0, points2, 0, pointsCount2);
                matrix3.mapPoints(points3, 0, points3, 0, pointsCount3);
                pointsCount1 = 0;
                pointsCount2 = 0;
                pointsCount3 = 0;
            }

            for (int i = 0; i < particles.size(); i++) {
                Particle particle = particles.get(i);
                if (paused) {
                    particle.draw(canvas, pausedTime, alpha);
                } else {
                    particle.draw(canvas, time, alpha);
                }
                if (checkTime) {
                    if (time > particle.lifeTime) {
                        particle.genPosition(time);
                    }
                }
                if (checkBounds) {
                    if (!rect2.contains(particle.drawingX, particle.drawingY)) {
                        particle.genPosition(time);
                    }
                }
            }
            prevTime = time;
        }

        public class Particle {
            public long lifeTime;

            private float x, y;
            private float x2, y2;
            private float drawingX, drawingY;
            private float vecX, vecY;
            private int starIndex;
            private int alpha;
            private float randomRotate;
            float inProgress;
            float flipProgress;

            public void updatePoint() {
                if (starIndex == 0) {
                    points1[2 * pointsCount1] = x;
                    points1[2 * pointsCount1 + 1] = y;
                    pointsCount1++;
                } else if (starIndex == 1) {
                    points2[2 * pointsCount2] = x;
                    points2[2 * pointsCount2 + 1] = y;
                    pointsCount2++;
                } else if (starIndex == 2) {
                    points3[2 * pointsCount3] = x;
                    points3[2 * pointsCount3 + 1] = y;
                    pointsCount3++;
                }
            }

            public void draw(Canvas canvas, long time, float alpha) {
                if (useRotate) {
                    if (starIndex == 0) {
                        drawingX = points1[2 * pointsCount1];
                        drawingY = points1[2 * pointsCount1 + 1];
                        pointsCount1++;
                    } else if (starIndex == 1) {
                        drawingX = points2[2 * pointsCount2];
                        drawingY = points2[2 * pointsCount2 + 1];
                        pointsCount2++;
                    } else if (starIndex == 2) {
                        drawingX = points3[2 * pointsCount3];
                        drawingY = points3[2 * pointsCount3 + 1];
                        pointsCount3++;
                    }
                } else {
                    drawingX = x;
                    drawingY = y;
                }
                boolean skipDraw = false;
                if (!excludeRect.isEmpty() && excludeRect.contains(drawingX, drawingY)) {
                    skipDraw = true;
                }
                if (!skipDraw) {
                    canvas.save();
                    canvas.translate(drawingX, drawingY);
                    if (randomRotate != 0) {
                        canvas.rotate(randomRotate, stars[starIndex].getWidth() / 2f, stars[starIndex].getHeight() / 2f);
                    }
                    float outProgress = 0f;
                    if (checkTime && lifeTime - time < 200) {
                        outProgress = 1f - (lifeTime - time) / 150f;
                        outProgress = Utilities.clamp(outProgress, 1f, 0f);
                    }
                    if (inProgress < 1f || GLIconSettingsView.smallStarsSize != 1f) {
                        float s = AndroidUtilities.overshootInterpolator.getInterpolation(inProgress) * GLIconSettingsView.smallStarsSize;
                        canvas.scale(s, s, 0, 0);
                    }
                    if (flip[starIndex]) {
                        flipProgress += dt / 1000f * Math.min(speedScale, 3.5f);
                        canvas.scale((float) Math.cos(Math.PI * flipProgress), 1f, 0, 0);
                    }
                    Paint paint = overridePaint != null ? overridePaint : Drawable.this.paint;
                    paint.setAlpha((int) (this.alpha * (1f - outProgress) * alpha));
                    canvas.drawBitmap(stars[starIndex], -(stars[starIndex].getWidth() >> 1), -(stars[starIndex].getHeight() >> 1), paint);
                    canvas.restore();
                }
                if (!paused) {
                    float speed = dp(4) * (dt / 660f);
                    if (flip[starIndex]) {
                        speed *= 4 * Math.min(speedScale, 3.5f);
                    } else {
                        speed *= speedScale;
                    }
                    x += vecX * speed;
                    y += vecY * speed;

                    if (inProgress != 1f) {
                        inProgress += dt / 200;
                        if (inProgress > 1f) {
                            inProgress = 1f;
                        }
                    }
                }
            }

            private boolean first = true;
            public void genPosition(long time) {
                if (type == PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS) {
                    final float rand = Utilities.fastRandom.nextFloat();
                    if (rand < .13f) starIndex = 0;
                    else starIndex = (int) Math.floor(1 + rand * (stars.length - 1));
                } else {
                    starIndex = Math.abs(Utilities.fastRandom.nextInt() % stars.length);
                }
                lifeTime = time + minLifeTime + Utilities.fastRandom.nextInt(randLifeTime * (flip[starIndex] ? 3 : 1));
                randomRotate = 0;

                if (distributionAlgorithm) {
                    float bestDistance = 0;
                    float bestX = rect.left + Math.abs(Utilities.fastRandom.nextInt() % rect.width());
                    float bestY = rect.top + Math.abs(Utilities.fastRandom.nextInt() % rect.height());
                    for (int k = 0; k < 10; k++) {
                        float randX = rect.left + Math.abs(Utilities.fastRandom.nextInt() % rect.width());
                        float randY = rect.top + Math.abs(Utilities.fastRandom.nextInt() % rect.height());
                        float minDistance = Integer.MAX_VALUE;
                        for (int j = 0; j < particles.size(); j++) {
                            float rx;
                            float ry;
                            if (startFromCenter) {
                                rx = particles.get(j).x2 - randX;
                                ry = particles.get(j).y2 - randY;
                            } else {
                                rx = particles.get(j).x - randX;
                                ry = particles.get(j).y - randY;
                            }
                            float distance = rx * rx + ry * ry;
                            if (distance < minDistance) {
                                minDistance = distance;
                            }
                        }
                        if (minDistance > bestDistance) {
                            bestDistance = minDistance;
                            bestX = randX;
                            bestY = randY;
                        }
                    }

                    x = bestX;
                    y = bestY;
                } else {
                    if (isCircle) {
                        float r = (Math.abs(Utilities.fastRandom.nextInt() % 1000) / 1000f) * (rect.width() - excludeRadius) + excludeRadius;
                        float a = Math.abs(Utilities.fastRandom.nextInt() % 360);
                        float oy = 0;
                        if (flip[starIndex] && !first) {
                            r = Math.min(r, dp(10));
                            oy += dp(30);
                        }
                        x = rect.centerX() + centerOffsetX + (float) (r * Math.sin(Math.toRadians(a)));
                        y = rect.centerY() + oy + centerOffsetY + (float) (r * Math.cos(Math.toRadians(a)));
                    } else {
                        x = rect.left + Math.abs(Utilities.fastRandom.nextInt() % rect.width());
                        y = rect.top + Math.abs(Utilities.fastRandom.nextInt() % rect.height());
                    }
                }
                if (flip[starIndex]) {
                    flipProgress = Math.abs(Utilities.fastRandom.nextFloat() * 2);
                }

                double a;
                if (flip[starIndex]) {
                    float d = 100;
                    a = Math.toRadians(180 + d - 2 * d * Utilities.fastRandom.nextFloat());
                } else {
                    a = Math.atan2(x - (rect.centerX() + centerOffsetX), y - (rect.centerY() + centerOffsetY));
                }
                vecX = (float) Math.sin(a);
                vecY = (float) Math.cos(a);
                if (svg[starIndex]) {
                    alpha = (int) (120 * ((50 + Utilities.fastRandom.nextInt(50)) / 100f));
                } else {
                    alpha = (int) (255 * ((50 + Utilities.fastRandom.nextInt(50)) / 100f));
                }
                if ((type == PremiumPreviewFragment.PREMIUM_FEATURE_PROFILE_BADGE && (starIndex == 1 || starIndex == 2)) ||
                        type == PremiumPreviewFragment.PREMIUM_FEATURE_ADVANCED_CHAT_MANAGEMENT ||
                        type == PremiumPreviewFragment.PREMIUM_FEATURE_ADS ||
                        type == PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_AVATARS ||
                        type == PremiumPreviewFragment.PREMIUM_FEATURE_SAVED_TAGS ||
                        type == PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI ||
                        type == PremiumPreviewFragment.PREMIUM_FEATURE_WALLPAPER ||
                        type == PremiumPreviewFragment.PREMIUM_FEATURE_REACTIONS
                ) {
                    randomRotate = (int) (45 * ((Utilities.fastRandom.nextInt() % 100) / 100f));
                }
                if (type != TYPE_SETTINGS) {
                    inProgress = 0;
                }
                if (startFromCenter) {
                    x2 = x;
                    y2 = y;
                    x = rect.centerX() + centerOffsetX;// + (x - rect.centerX()) * 0.3f;
                    y = rect.centerY() + centerOffsetY;// + (y - rect.centerY()) * 0.3f;
                }
                first = false;
            }
        }
    }

    public void setPaused(boolean paused) {
        if (paused == drawable.paused) {
            return;
        }
        drawable.paused = paused;
        if (paused) {
            drawable.pausedTime = System.currentTimeMillis();
        } else {
            for (int i = 0; i < drawable.particles.size(); i++) {
                drawable.particles.get(i).lifeTime += System.currentTimeMillis() - drawable.pausedTime;
            }
            invalidate();
        }
    }
}

