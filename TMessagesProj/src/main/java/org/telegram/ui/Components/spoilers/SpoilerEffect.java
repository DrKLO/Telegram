package org.telegram.ui.Components.spoilers;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CachedStaticLayout;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.QuoteSpan;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TextStyleSpan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class SpoilerEffect extends Drawable {
    public final static int MAX_PARTICLES_PER_ENTITY = measureMaxParticlesCount();
    public final static int PARTICLES_PER_CHARACTER = measureParticlesPerCharacter();
    private final static float VERTICAL_PADDING_DP = 2.5f;
    private final static int RAND_REPEAT = 14;
    private final static int FPS = 30;
    private final static int renderDelayMs = 1000 / FPS + 1;
    public final static float[] ALPHAS = {
            0.3f, 0.6f, 1.0f
    };
    private Paint[] particlePaints = new Paint[ALPHAS.length];

    private Stack<Particle> particlesPool = new Stack<>();
    private int maxParticles;
    float[][] particlePoints = new float[ALPHAS.length][MAX_PARTICLES_PER_ENTITY * 5];
    private float[] particleRands = new float[RAND_REPEAT];
    private int[] renderCount = new int[ALPHAS.length];

    private static Path tempPath = new Path();

    private RectF visibleRect;

    private ArrayList<Particle> particles = new ArrayList<>();
    private View mParent;

    private long lastDrawTime;

    private float rippleX, rippleY;
    private float rippleMaxRadius;
    private float rippleProgress = -1;
    private boolean reverseAnimator;
    private boolean shouldInvalidateColor;
    private Runnable onRippleEndCallback;
    private ValueAnimator rippleAnimator;

    private List<RectF> spaces = new ArrayList<>();
    private int mAlpha = 0xFF;

    private TimeInterpolator rippleInterpolator = input -> input;

    private boolean invalidateParent;
    private boolean suppressUpdates;
    private boolean isLowDevice;
    private boolean enableAlpha;

    private int lastColor;
    public boolean drawPoints;
    private static Paint xRefPaint;
    private int bitmapSize;

    public boolean insideQuote;

    private static int measureParticlesPerCharacter() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            default:
            case SharedConfig.PERFORMANCE_CLASS_LOW:
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return 10;
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return 30;
        }
    }

    private static int measureMaxParticlesCount() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            default:
            case SharedConfig.PERFORMANCE_CLASS_LOW:
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return 100;
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return 150;
        }
    }

    public SpoilerEffect() {
        for (int i = 0; i < ALPHAS.length; i++) {
            particlePaints[i] = new Paint();
            if (i == 0) {
                particlePaints[i].setStrokeWidth(AndroidUtilities.dp(1.4f));
                particlePaints[i].setStyle(Paint.Style.STROKE);
                particlePaints[i].setStrokeCap(Paint.Cap.ROUND);
            } else {
                particlePaints[i].setStrokeWidth(AndroidUtilities.dp(1.2f));
                particlePaints[i].setStyle(Paint.Style.STROKE);
                particlePaints[i].setStrokeCap(Paint.Cap.ROUND);
            }
        }

        isLowDevice = SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW;
        enableAlpha = true;
        setColor(Color.TRANSPARENT);
    }

    /**
     * Sets if we should suppress updates or not
     */
    public void setSuppressUpdates(boolean suppressUpdates) {
        this.suppressUpdates = suppressUpdates;
        invalidateSelf();
    }

    /**
     * Sets if we should invalidate parent instead
     */
    public void setInvalidateParent(boolean invalidateParent) {
        this.invalidateParent = invalidateParent;
    }

    /**
     * Updates max particles count
     */
    public void updateMaxParticles() {
        setMaxParticlesCount(MathUtils.clamp((getBounds().width() / AndroidUtilities.dp(6)) * PARTICLES_PER_CHARACTER, PARTICLES_PER_CHARACTER, MAX_PARTICLES_PER_ENTITY));
    }

    /**
     * Sets callback to be run after ripple animation ends
     */
    public void setOnRippleEndCallback(@Nullable Runnable onRippleEndCallback) {
        this.onRippleEndCallback = onRippleEndCallback;
    }

    /**
     * Starts ripple
     *
     * @param rX     Ripple center x
     * @param rY     Ripple center y
     * @param radMax Max ripple radius
     */
    public void startRipple(float rX, float rY, float radMax) {
        startRipple(rX, rY, radMax, false);
    }

    /**
     * Starts ripple
     *
     * @param rX      Ripple center x
     * @param rY      Ripple center y
     * @param radMax  Max ripple radius
     * @param reverse If we should start reverse ripple
     */
    public void startRipple(float rX, float rY, float radMax, boolean reverse) {
        rippleX = rX;
        rippleY = rY;
        rippleMaxRadius = radMax;
        rippleProgress = reverse ? 1 : 0;
        reverseAnimator = reverse;

        if (rippleAnimator != null)
            rippleAnimator.cancel();
        int startAlpha = reverseAnimator ? 0xFF : particlePaints[ALPHAS.length - 1].getAlpha();
        rippleAnimator = ValueAnimator.ofFloat(rippleProgress, reverse ? 0 : 1).setDuration((long) MathUtils.clamp(rippleMaxRadius * 0.3f, 250, 550));
        rippleAnimator.setInterpolator(rippleInterpolator);
        rippleAnimator.addUpdateListener(animation -> {
            rippleProgress = (float) animation.getAnimatedValue();
            setAlpha((int) (startAlpha * (1f - rippleProgress)));
            shouldInvalidateColor = true;
            invalidateSelf();
        });
        rippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Iterator<Particle> it = particles.iterator();
                while (it.hasNext()) {
                    Particle p = it.next();
                    if (particlesPool.size() < maxParticles) {
                        particlesPool.push(p);
                    }
                    it.remove();
                }

                if (onRippleEndCallback != null) {
                    onRippleEndCallback.run();
                    onRippleEndCallback = null;
                }

                rippleAnimator = null;
                invalidateSelf();
            }
        });
        rippleAnimator.start();

        invalidateSelf();
    }

    /**
     * Sets new ripple interpolator
     *
     * @param rippleInterpolator New interpolator
     */
    public void setRippleInterpolator(@NonNull TimeInterpolator rippleInterpolator) {
        this.rippleInterpolator = rippleInterpolator;
    }

    /**
     * Gets ripple path
     */
    public void getRipplePath(Path path) {
        path.addCircle(rippleX, rippleY, rippleMaxRadius * MathUtils.clamp(rippleProgress, 0, 1), Path.Direction.CW);
    }

    /**
     * @return Current ripple progress
     */
    public float getRippleProgress() {
        return rippleProgress;
    }

    /**
     * @return If we should invalidate color
     */
    public boolean shouldInvalidateColor() {
        boolean b = shouldInvalidateColor;
        shouldInvalidateColor = false;
        return b;
    }

    /**
     * Sets new ripple progress
     */
    public void setRippleProgress(float rippleProgress) {
        this.rippleProgress = rippleProgress;
        if (rippleProgress == -1 && rippleAnimator != null) {
            rippleAnimator.cancel();
        }
        shouldInvalidateColor = true;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            if (!getBounds().contains((int) p.x, (int) p.y)) {
                it.remove();
            }
            if (particlesPool.size() < maxParticles) {
                particlesPool.push(p);
            }
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (drawPoints) {
            long curTime = System.currentTimeMillis();
            int dt = (int) Math.min(curTime - lastDrawTime, renderDelayMs);
            boolean hasAnimator = false;

            lastDrawTime = curTime;

            int left = getBounds().left, top = getBounds().top, right = getBounds().right, bottom = getBounds().bottom;
            for (int i = 0; i < ALPHAS.length; i++) {
                renderCount[i] = 0;
            }
            for (int i = 0; i < particles.size(); i++) {
                Particle particle = particles.get(i);

                particle.currentTime = Math.min(particle.currentTime + dt, particle.lifeTime);
                if (particle.currentTime >= particle.lifeTime || isOutOfBounds(left, top, right, bottom, particle.x, particle.y)) {
                    if (particlesPool.size() < maxParticles) {
                        particlesPool.push(particle);
                    }
                    particles.remove(i);
                    i--;
                    continue;
                }

                float hdt = particle.velocity * dt / 500f;
                particle.x += particle.vecX * hdt;
                particle.y += particle.vecY * hdt;
            }

            if (particles.size() < maxParticles) {
                int np = maxParticles - particles.size();
                Arrays.fill(particleRands, -1);
                for (int i = 0; i < np; i++) {
                    float rf = particleRands[i % RAND_REPEAT];
                    if (rf == -1) {
                        particleRands[i % RAND_REPEAT] = rf = Utilities.fastRandom.nextFloat();
                    }

                    Particle newParticle = !particlesPool.isEmpty() ? particlesPool.pop() : new Particle();
                    int attempts = 0;
                    do {
                        generateRandomLocation(newParticle, i);
                        attempts++;
                    } while (isOutOfBounds(left, top, right, bottom, newParticle.x, newParticle.y) && attempts < 4);


                    double angleRad = rf * Math.PI * 2 - Math.PI;
                    float vx = (float) Math.cos(angleRad);
                    float vy = (float) Math.sin(angleRad);

                    newParticle.vecX = vx;
                    newParticle.vecY = vy;

                    newParticle.currentTime = 0;

                    newParticle.lifeTime = 1000 + Math.abs(Utilities.fastRandom.nextInt(2000)); // [1000;3000]
                    newParticle.velocity = 4 + rf * 6;
                    newParticle.alpha = Utilities.fastRandom.nextInt(ALPHAS.length);
                    particles.add(newParticle);

                }
            }

            for (int a = enableAlpha ? 0 : ALPHAS.length - 1; a < ALPHAS.length; a++) {
                int renderCount = 0;
                float paintW = particlePaints[a].getStrokeWidth() / 2f;
                for (int i = 0; i < particles.size(); i++) {
                    Particle p = particles.get(i);

                    if (visibleRect != null && !visibleRect.contains(p.x, p.y) || p.alpha != a && enableAlpha) {
                        continue;
                    }

                    if (renderCount >= particlePoints[a].length - 2) {
                        continue;
                    }
                    particlePoints[a][renderCount] = p.x;
                    particlePoints[a][renderCount + 1] = p.y;
                    renderCount += 2;
                    if (p.x < paintW) {
                        if (renderCount >= particlePoints[a].length - 2) {
                            continue;
                        }
                        particlePoints[a][renderCount] = p.x + bitmapSize;
                        particlePoints[a][renderCount + 1] = p.y;
                        renderCount += 2;
                    }
                    if (p.x > bitmapSize - paintW) {
                        if (renderCount >= particlePoints[a].length - 2) {
                            continue;
                        }
                        particlePoints[a][renderCount] = p.x - bitmapSize;
                        particlePoints[a][renderCount + 1] = p.y;
                        renderCount += 2;
                    }
                    if (p.y < paintW) {
                        if (renderCount >= particlePoints[a].length - 2) {
                            continue;
                        }
                        particlePoints[a][renderCount] = p.x;
                        particlePoints[a][renderCount + 1] = p.y + bitmapSize;
                        renderCount += 2;
                    }
                    if (p.y > bitmapSize - paintW) {
                        if (renderCount >= particlePoints[a].length - 2) {
                            continue;
                        }
                        particlePoints[a][renderCount] = p.x;
                        particlePoints[a][renderCount + 1] = p.y - bitmapSize;
                        renderCount += 2;
                    }


                }
                canvas.drawPoints(particlePoints[a], 0, renderCount, particlePaints[a]);
            }
        } else {
            Paint shaderPaint = SpoilerEffectBitmapFactory.getInstance().getPaint();
            shaderPaint.setColorFilter(new PorterDuffColorFilter(lastColor, PorterDuff.Mode.SRC_IN));
            canvas.drawRect(getBounds().left, getBounds().top, getBounds().right, getBounds().bottom, SpoilerEffectBitmapFactory.getInstance().getPaint());
            if (LiteMode.isEnabled(LiteMode.FLAG_CHAT_SPOILER)) {
                invalidateSelf();
                SpoilerEffectBitmapFactory.getInstance().checkUpdate();
            }
        }
    }

    /**
     * Updates visible bounds to update particles
     */
    public void setVisibleBounds(float left, float top, float right, float bottom) {
        if (visibleRect == null)
            visibleRect = new RectF();
        if (visibleRect.left != left || visibleRect.right != right || visibleRect.top != top || visibleRect.bottom != bottom) {
            visibleRect.left = left;
            visibleRect.top = top;
            visibleRect.right = right;
            visibleRect.bottom = bottom;
            invalidateSelf();
        }
    }

    private boolean isOutOfBounds(int left, int top, int right, int bottom, float x, float y) {
        if (x < left || x > right || y < top + AndroidUtilities.dp(VERTICAL_PADDING_DP) ||
                y > bottom - AndroidUtilities.dp(VERTICAL_PADDING_DP))
            return true;

        for (int i = 0; i < spaces.size(); i++) {
            if (spaces.get(i).contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private void generateRandomLocation(Particle newParticle, int i) {
        newParticle.x = getBounds().left + Utilities.fastRandom.nextFloat() * getBounds().width();
        newParticle.y = getBounds().top + Utilities.fastRandom.nextFloat() * getBounds().height();
    }

    @Override
    public void invalidateSelf() {
        super.invalidateSelf();

        if (mParent != null) {
            View v = mParent;
            if (v.getParent() != null && invalidateParent) {
                ((View) v.getParent()).invalidate();
            } else if (v instanceof BaseCell) {
                ((BaseCell) v).invalidateLite();
            } else if (v != null) {
                v.invalidate();
            }
        }
    }

    /**
     * Attaches to the parent view
     *
     * @param parentView Parent view
     */
    public void setParentView(View parentView) {
        this.mParent = parentView;
    }

    /**
     * @return Currently used parent view
     */
    public View getParentView() {
        return mParent;
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
        for (int i = 0; i < ALPHAS.length; i++) {
            particlePaints[i].setAlpha((int) (ALPHAS[i] * alpha));
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        for (Paint p : particlePaints) {
            p.setColorFilter(colorFilter);
        }
    }

    /**
     * Sets particles color
     *
     * @param color New color
     */
    public void setColor(int color) {
        if (lastColor != color) {
            for (int i = 0; i < ALPHAS.length; i++) {
                particlePaints[i].setColor(ColorUtils.setAlphaComponent(color, (int) (mAlpha * ALPHAS[i])));
            }
            lastColor = color;
        }
    }

    /**
     * @return If effect has color
     */
    public boolean hasColor() {
        return lastColor != Color.TRANSPARENT;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    /**
     * @return Max particles count
     */
    public int getMaxParticlesCount() {
        return maxParticles;
    }

    /**
     * Sets new max particles count
     */
    public void setMaxParticlesCount(int maxParticles) {
        this.maxParticles = maxParticles;
        while (particlesPool.size() + particles.size() < maxParticles) {
            particlesPool.push(new Particle());
        }
    }

    /**
     * Alias for it's big bro
     *
     * @param tv           Text view to use as a parent view
     * @param spoilersPool Cached spoilers pool
     * @param spoilers     Spoilers list to populate
     */
    public static void addSpoilers(TextView tv, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers) {
        int width = tv.getMeasuredWidth();
        addSpoilers(tv, tv.getLayout(), 0, width > 0 ? width : -2, (Spanned) tv.getText(), spoilersPool, spoilers, null);
    }

    public static void addSpoilers(TextView tv, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers, ArrayList<QuoteSpan.Block> quoteBlocks) {
        int width = tv.getMeasuredWidth();
        addSpoilers(tv, tv.getLayout(), 0, width > 0 ? width : -2, (Spanned) tv.getText(), spoilersPool, spoilers, quoteBlocks);
    }

    /**
     * Alias for it's big bro
     *
     * @param v            View to use as a parent view
     * @param textLayout   Text layout to measure
     * @param spoilersPool Cached spoilers pool, could be null, but highly recommended
     * @param spoilers     Spoilers list to populate
     */
    public static void addSpoilers(@Nullable View v, Layout textLayout, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers) {
        if (textLayout.getText() instanceof Spanned) {
            addSpoilers(v, textLayout, (Spanned) textLayout.getText(), spoilersPool, spoilers);
        }
    }

    public static void addSpoilers(@Nullable View v, Layout textLayout, int left, int right, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers) {
        if (textLayout.getText() instanceof Spanned) {
            addSpoilers(v, textLayout, left, right, (Spanned) textLayout.getText(), spoilersPool, spoilers, null);
        }
    }

    public static void addSpoilers(@Nullable View v, Layout textLayout, Spanned spannable, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers) {
        if (textLayout == null) {
            return;
        }
        addSpoilers(v, textLayout, -1, -1, spannable, spoilersPool, spoilers, null);
    }

    /**
     * Parses spoilers from spannable
     *
     * @param v            View to use as a parent view
     * @param textLayout   Text layout to measure
     * @param layoutLeft   The minimum left bound to limit spoilers in
     * @param layoutRight  The maximum right bound to limit spoilers in. Use -1 when
     *                     needed calculation, use -2 (or any other negative) when
     *                     you don't want to limit anyway
     * @param spannable    Text to parse
     * @param spoilersPool Cached spoilers pool, could be null, but highly recommended
     * @param spoilers     Spoilers list to populate
     */
    public static void addSpoilers(@Nullable View v, Layout textLayout, int layoutLeft, int layoutRight, Spanned spannable, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers, ArrayList<QuoteSpan.Block> quoteBlocks) {
        if (textLayout == null) {
            return;
        }
        TextStyleSpan[] spans = spannable.getSpans(0, textLayout.getText().length(), TextStyleSpan.class);
        for (int i = 0; i < spans.length; ++i) {
            if (spans[i].isSpoiler()) {
                final int start = spannable.getSpanStart(spans[i]);
                final int end = spannable.getSpanEnd(spans[i]);
                int left = layoutLeft, right = layoutRight;
                if (left == -1 && right == -1) {
                    left = Integer.MAX_VALUE;
                    right = Integer.MIN_VALUE;
                    int linestart = textLayout.getLineForOffset(start);
                    int lineend = textLayout.getLineForOffset(end);
                    for (int l = linestart; l <= lineend; ++l) {
                        left = Math.min(left, (int) textLayout.getLineLeft(l));
                        right = Math.max(right, (int) textLayout.getLineRight(l));
                    }
                }
                addSpoilerRangesInternal(v, textLayout, left, right, start, end, spoilersPool, spoilers, quoteBlocks);
            }
        }
        if (v instanceof TextView && spoilersPool != null) {
            spoilersPool.clear();
        }
    }

    private static void addSpoilerRangesInternal(@Nullable View v, @NonNull Layout textLayout, int mostleft, int mostright, int start, int end, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers, ArrayList<QuoteSpan.Block> quoteBlocks) {
        textLayout.getSelectionPath(start, end, new Path() {
            @Override
            public void addRect(float left, float top, float right, float bottom, @NonNull Direction dir) {
                addSpoilerRangeInternal(v, textLayout, left, top, right, bottom, spoilersPool, spoilers, mostleft, mostright, quoteBlocks);
            }
        });
    }

    private static void addSpoilerRangeInternal(@Nullable View v, @NonNull Layout textLayout, float left, float top, float right, float bottom, @Nullable Stack<SpoilerEffect> spoilersPool, List<SpoilerEffect> spoilers, int mostleft, int mostright, ArrayList<QuoteSpan.Block> quote) {
        SpoilerEffect spoilerEffect = spoilersPool == null || spoilersPool.isEmpty() ? new SpoilerEffect() : spoilersPool.remove(0);
        spoilerEffect.insideQuote = false;
        if (quote != null) {
            final float cy = (top + bottom) / 2f;
            for (int j = 0; j < quote.size(); ++j) {
                QuoteSpan.Block block = quote.get(j);
                if (cy >= block.top && cy <= block.bottom) {
                    spoilerEffect.insideQuote = true;
                    break;
                }
            }
        }
        spoilerEffect.setRippleProgress(-1);
        spoilerEffect.setBounds((int) Math.max(left, mostleft), (int) top, (int) Math.min(right, mostright <= 0 ? Integer.MAX_VALUE : mostright), (int) bottom);
        spoilerEffect.setColor(textLayout.getPaint().getColor());
        spoilerEffect.setRippleInterpolator(Easings.easeInQuad);
        spoilerEffect.updateMaxParticles();
        if (v != null) {
            spoilerEffect.setParentView(v);
        }
        spoilers.add(spoilerEffect);
    }

    /**
     * Clips out spoilers from canvas
     */
    public static void clipOutCanvas(Canvas canvas, List<SpoilerEffect> spoilers) {
        tempPath.rewind();
        for (int i = 0; i < spoilers.size(); i++) {
            SpoilerEffect eff = spoilers.get(i);
            Rect b = eff.getBounds();
            tempPath.addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
        }
        canvas.clipPath(tempPath, Region.Op.DIFFERENCE);
    }

    private static WeakHashMap<Layout, ArrayList<RectF>> lazyLayoutLines;
    public static void layoutDrawMaybe(Layout layout, Canvas canvas) {
        if (canvas instanceof SizeNotifierFrameLayout.SimplerCanvas) {
            final int wasAlpha = layout.getPaint().getAlpha();
            layout.getPaint().setAlpha((int) (wasAlpha * .4f));
            if (lazyLayoutLines == null) {
                lazyLayoutLines = new WeakHashMap<>();
            }
            ArrayList<RectF> linesRect = lazyLayoutLines.get(layout);
            if (linesRect == null) {
                linesRect = new ArrayList<>();
                final int lineCount = layout.getLineCount();
                for (int i = 0; i < lineCount; ++i) {
                    linesRect.add(new RectF(
                        layout.getLineLeft(i),
                        layout.getLineTop(i),
                        layout.getLineRight(i),
                        layout.getLineBottom(i)
                    ));
                }
                lazyLayoutLines.put(layout, linesRect);
            }
            if (linesRect != null) {
                for (int i = 0; i < linesRect.size(); ++i) {
                    canvas.drawRect(linesRect.get(i), layout.getPaint());
                }
            }
            layout.getPaint().setAlpha(wasAlpha);
        } else {
            layout.draw(canvas);
        }
    }

    /**
     * Optimized version of text layout double-render
     *
     * @param v                        View to use as a parent view
     * @param invalidateSpoilersParent Set to invalidate parent or not
     * @param spoilersColor            Spoilers' color
     * @param verticalOffset           Additional vertical offset
     * @param patchedLayoutRef         Patched layout reference
     * @param patchedLayoutType
     * @param textLayout               Layout to render
     * @param spoilers                 Spoilers list to render
     * @param canvas                   Canvas to render
     * @param useParentWidth
     */
    @SuppressLint("WrongConstant")
    @MainThread
    public static void renderWithRipple(View v, boolean invalidateSpoilersParent, int spoilersColor, int verticalOffset, AtomicReference<Layout> patchedLayoutRef, int patchedLayoutType, Layout textLayout, List<SpoilerEffect> spoilers, Canvas canvas, boolean useParentWidth) {
        if (spoilers == null || spoilers.isEmpty()) {
            layoutDrawMaybe(textLayout, canvas);
            return;
        }
        Layout pl = patchedLayoutRef.get();

        if (pl == null || !textLayout.getText().toString().equals(pl.getText().toString()) || textLayout.getWidth() != pl.getWidth() || textLayout.getHeight() != pl.getHeight()) {
            SpannableStringBuilder sb = new SpannableStringBuilder(textLayout.getText());
            if (textLayout.getText() instanceof Spanned) {
                Spanned sp = (Spanned) textLayout.getText();
                for (TextStyleSpan ss : sp.getSpans(0, sp.length(), TextStyleSpan.class)) {
                    if (ss.isSpoiler()) {
                        int start = sp.getSpanStart(ss), end = sp.getSpanEnd(ss);
                        for (Emoji.EmojiSpan e : sp.getSpans(start, end, Emoji.EmojiSpan.class)) {
                            sb.setSpan(new ReplacementSpan() {
                                @Override
                                public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                                    return e.getSize(paint, text, start, end, fm);
                                }

                                @Override
                                public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                                }
                            }, sp.getSpanStart(e), sp.getSpanEnd(e), sp.getSpanFlags(ss));
                            sb.removeSpan(e);
                        }

                        sb.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), start, end, sp.getSpanFlags(ss));
                        sb.removeSpan(ss);
                    }
                }
            }

            Layout layout;
            if (patchedLayoutType == 1) {
                layout = new StaticLayout(sb, textLayout.getPaint(), textLayout.getWidth(), Layout.Alignment.ALIGN_CENTER, 1.0f, dp(1.66f), false);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                layout = StaticLayout.Builder.obtain(sb, 0, sb.length(), textLayout.getPaint(), textLayout.getWidth())
                        .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                        .setAlignment(textLayout.getAlignment())
                        .setLineSpacing(textLayout.getSpacingAdd(), textLayout.getSpacingMultiplier())
                        .build();
            } else {
                layout = new StaticLayout(sb, textLayout.getPaint(), textLayout.getWidth(), textLayout.getAlignment(), textLayout.getSpacingMultiplier(), textLayout.getSpacingAdd(), false);
            }
            patchedLayoutRef.set(pl = layout);
        }

        if (!spoilers.isEmpty()) {
            canvas.save();
            canvas.translate(0, verticalOffset);
            pl.draw(canvas);
            canvas.restore();
        } else {
            layoutDrawMaybe(textLayout, canvas);
        }

        if (!spoilers.isEmpty()) {
            tempPath.rewind();
            for (SpoilerEffect eff : spoilers) {
                Rect b = eff.getBounds();
                tempPath.addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
            }
            if (!spoilers.isEmpty() && spoilers.get(0).rippleProgress != -1) {
                canvas.save();
                canvas.clipPath(tempPath);
                tempPath.rewind();
                if (!spoilers.isEmpty()) {
                    spoilers.get(0).getRipplePath(tempPath);
                }
                canvas.clipPath(tempPath);
                canvas.translate(0, -v.getPaddingTop());
                layoutDrawMaybe(textLayout, canvas);
                canvas.restore();
            }


            boolean useAlphaLayer = spoilers.get(0).rippleProgress != -1;
            if (useAlphaLayer) {
                int w = v.getMeasuredWidth();
                if (useParentWidth && v.getParent() instanceof View) {
                    w = ((View) v.getParent()).getMeasuredWidth();
                }
                canvas.saveLayer(0, 0, w, v.getMeasuredHeight(), null, canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            canvas.translate(0, -v.getPaddingTop());
            for (SpoilerEffect eff : spoilers) {
                eff.setInvalidateParent(invalidateSpoilersParent);
                if (eff.getParentView() != v) eff.setParentView(v);
                if (eff.shouldInvalidateColor()) {
                    eff.setColor(ColorUtils.blendARGB(spoilersColor, patchedLayoutType == 1 ? textLayout.getPaint().getColor() : Theme.chat_msgTextPaint.getColor(), Math.max(0, eff.getRippleProgress())));
                } else {
                    eff.setColor(spoilersColor);
                }
                eff.draw(canvas);
            }

            if (useAlphaLayer) {
                tempPath.rewind();
                spoilers.get(0).getRipplePath(tempPath);
                if (xRefPaint == null) {
                    xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    xRefPaint.setColor(0xff000000);
                    xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                }
                canvas.drawPath(tempPath, xRefPaint);
            }
            canvas.restore();
        }
    }


    /**
     * Optimized version of text layout double-render
     *  @param v                        View to use as a parent view
     * @param invalidateSpoilersParent Set to invalidate parent or not
     * @param spoilersColor            Spoilers' color
     * @param verticalOffset           Additional vertical offset
     * @param patchedLayoutRef         Patched layout reference
     * @param textLayout               Layout to render
     * @param spoilers                 Spoilers list to render
     * @param canvas                   Canvas to render
     * @param useParentWidth
     */
    @SuppressLint("WrongConstant")
    @MainThread
    public static void renderWithRipple(View v, boolean invalidateSpoilersParent, int spoilersColor, int verticalOffset, AtomicReference<CachedStaticLayout> patchedLayoutRef, CachedStaticLayout textLayout, List<SpoilerEffect> spoilers, Canvas canvas, boolean useParentWidth) {
        if (spoilers.isEmpty()) {
            textLayout.draw(canvas);
            return;
        }
        CachedStaticLayout pl = patchedLayoutRef.get();
        if (pl == null || !textLayout.getText().toString().equals(pl.getText().toString()) || textLayout.layout.getWidth() != pl.layout.getWidth() || textLayout.layout.getHeight() != pl.layout.getHeight()) {
            SpannableStringBuilder sb = new SpannableStringBuilder(textLayout.getText());
            if (textLayout.getText() instanceof Spanned) {
                Spanned sp = (Spanned) textLayout.getText();
                for (TextStyleSpan ss : sp.getSpans(0, sp.length(), TextStyleSpan.class)) {
                    if (ss.isSpoiler()) {
                        int start = sp.getSpanStart(ss), end = sp.getSpanEnd(ss);
                        for (Emoji.EmojiSpan e : sp.getSpans(start, end, Emoji.EmojiSpan.class)) {
                            sb.setSpan(new ReplacementSpan() {
                                @Override
                                public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                                    return e.getSize(paint, text, start, end, fm);
                                }
                                @Override
                                public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                                }
                            }, sp.getSpanStart(e), sp.getSpanEnd(e), sp.getSpanFlags(ss));
                            sb.removeSpan(e);
                        }
                        sb.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), start, end, sp.getSpanFlags(ss));
                        sb.removeSpan(ss);
                    }
                }
            }
            StaticLayout layout;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                layout = StaticLayout.Builder.obtain(sb, 0, sb.length(), textLayout.layout.getPaint(), textLayout.layout.getWidth())
                        .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                        .setAlignment(textLayout.layout.getAlignment())
                        .setLineSpacing(textLayout.layout.getSpacingAdd(), textLayout.layout.getSpacingMultiplier())
                        .build();
            } else {
                layout = new StaticLayout(sb, textLayout.layout.getPaint(), textLayout.layout.getWidth(), textLayout.layout.getAlignment(), textLayout.layout.getSpacingMultiplier(), textLayout.layout.getSpacingAdd(), false);
            }
            patchedLayoutRef.set(pl = new CachedStaticLayout(layout));
        }
        if (!spoilers.isEmpty()) {
            canvas.save();
            canvas.translate(0, verticalOffset);
            pl.draw(canvas);
            canvas.restore();
        } else {
            textLayout.draw(canvas);
        }
        if (!spoilers.isEmpty()) {
            tempPath.rewind();
            for (SpoilerEffect eff : spoilers) {
                Rect b = eff.getBounds();
                tempPath.addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
            }
            if (!spoilers.isEmpty() && spoilers.get(0).rippleProgress != -1) {
                canvas.save();
                canvas.clipPath(tempPath);
                tempPath.rewind();
                if (!spoilers.isEmpty()) {
                    spoilers.get(0).getRipplePath(tempPath);
                }
                canvas.clipPath(tempPath);
                canvas.translate(0, -v.getPaddingTop());
                textLayout.draw(canvas);
                canvas.restore();
            }
            boolean useAlphaLayer = spoilers.get(0).rippleProgress != -1;
            if (useAlphaLayer) {
                int w = v.getMeasuredWidth();
                if (useParentWidth && v.getParent() instanceof View) {
                    w = ((View) v.getParent()).getMeasuredWidth();
                }
                canvas.saveLayer(0, 0, w, v.getMeasuredHeight(), null, canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            canvas.translate(0, -v.getPaddingTop());
            for (SpoilerEffect eff : spoilers) {
                eff.setInvalidateParent(invalidateSpoilersParent);
                if (eff.getParentView() != v) eff.setParentView(v);
                if (eff.shouldInvalidateColor()) {
                    eff.setColor(ColorUtils.blendARGB(spoilersColor, Theme.chat_msgTextPaint.getColor(), Math.max(0, eff.getRippleProgress())));
                } else {
                    eff.setColor(spoilersColor);
                }
                eff.draw(canvas);
            }
            if (useAlphaLayer) {
                tempPath.rewind();
                spoilers.get(0).getRipplePath(tempPath);
                if (xRefPaint == null) {
                    xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    xRefPaint.setColor(0xff000000);
                    xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                }
                canvas.drawPath(tempPath, xRefPaint);
            }
            canvas.restore();
        }
    }

    public void setSize(int bitmapSize) {
        this.bitmapSize = bitmapSize;
    }

    private static class Particle {
        private float x, y;
        private float vecX, vecY;
        private float velocity;
        private float lifeTime, currentTime;
        private int alpha;
    }
}
