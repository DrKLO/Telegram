package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.TimeInterpolator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.utils.Choreographer60FpsContent;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.Random;

/**
 * A self-animating {@link Drawable} that draws a "breathing" blob wobbling around a
 * rounded rectangle. When the corner radius equals half the height the base shape is a
 * capsule (two semicircular caps), which is the intended look for a voice-recording bar.
 *
 * The blob is composed of two overlapping layers (a faint outer one and a denser inner
 * one) that share the base outline but use different push, wave depth and breathing so the
 * result has a bit of parallax.
 *
 * Model (and how it differs from {@link BlobDrawable}, which wobbles around a circle):
 *
 *  - Points are spaced by *curvature-weighted* arc length. Straight sides get weight 1, the
 *    caps get {@link #curvatureBoost}, so the tightly curved caps receive proportionally
 *    more points. On a stretched shape, plain angle stepping (as in BlobDrawable) starves
 *    the long sides and bunches points at the ends.
 *  - The number of points is derived from the outline perimeter
 *    rather than being fixed, so a wider bar automatically gets more points.
 *  - Each point is displaced along the outline normal, not by scaling a radius, so the wave
 *    depth is uniform around the whole outline.
 *  - Bezier control arms follow the outline tangent with length |P(i)P(i+1)| / 3. The
 *    L = 4/3 * tan(PI / 2N) constant of BlobDrawable is only correct for a circle sampled at
 *    equal angles.
 *  - progress is passed through smootherstep (quintic, zero first and second derivative at
 *    the ends) before interpolation, so the instant when a point's target is swapped is not
 *    visible as a velocity snap.
 *  - The next per-point target is a bounded random walk from the current value, so crests
 *    drift instead of flickering between independent samples.
 *  - On top of that per-point texture there is a single coherent breathing term shared by
 *    all points, which is what actually reads as "breathing".
 *
 * The outline is evaluated analytically in {@link #pointAt}, so resizing costs nothing and
 * there is no per-frame allocation once the point count is stable.
 *
 * Usage:
 * <pre>
 *     CapsuleBlobDrawable blob = new CapsuleBlobDrawable();
 *     blob.setState(CapsuleBlobDrawable.STATE_UNMUTE);
 *     view.setBackground(blob);   // or draw it manually in onDraw
 *     blob.start();
 *     // feed audio:
 *     blob.setAmplitude(level01, true);
 * </pre>
 */
public class CapsuleBlobDrawable extends Drawable {

    public static float MAX_SPEED = 8.2f;
    public static float MIN_SPEED = 0.8f;

    // Voice-state colors, mirroring FragmentContextViewWavesDrawable. Each state's radial
    // gradient is collapsed to a single blended color (the same blend the old code used when
    // LiteMode disabled the gradient).
    public static final int STATE_UNMUTE = 0;          // green
    public static final int STATE_MUTE = 1;            // blue
    public static final int STATE_CONNECTING = 2;      // gray
    public static final int STATE_MUTED_BY_ADMIN = 3;  // muted-by-admin gradient

    // Amplitude smoothing, copied from BlobDrawable so audio feels the same.
    private static final float ANIMATION_SPEED_WAVE_HUGE = 0.65f;
    private static final float ANIMATION_SPEED_WAVE_SMALL = 0.45f;
    private static final float animationSpeed = 1f - ANIMATION_SPEED_WAVE_HUGE;
    private static final float animationSpeedTiny = 1f - ANIMATION_SPEED_WAVE_SMALL;

    private static final TimeInterpolator SMOOTHER = t -> t * t * t * (t * (t * 6f - 15f) + 10f);

    // Fraction of the wave depth present at zero amplitude; the rest is driven by amplitude.
    // Lower value => the waves respond more strongly to the voice level.
    private static final float WAVE_IDLE_FRACTION = 0f;
    // How much the breathing recedes at full amplitude (0 = unaffected, 1 = fully gone).
    private static final float BREATH_AMPLITUDE_FALLOFF = 0.7f;
    // Number of ring-Laplacian passes applied to point depths for spatial coherence.
    private static final int SMOOTHING_ITERATIONS = 2;

    // ---- configuration ----
    private float cornerRadius = dp(18);
    private float targetSpacing = dp(22);
    private float curvatureBoost = 2.4f;

    private float waveDepth = dp(12f);
    private float breathDepth = dp(1.5f);
    private float breathPeriodMs = 3600f;
    private float neighborCoherence = 0.25f; // 0 = independent points, 1 = strongly smoothed toward neighbors

    // ---- geometry (px, relative to bounds center), rebuilt on bounds change ----
    private float halfWidth;
    private float halfHeight;
    private float radius;
    private float innerX;
    private float innerY;
    private float straightH;
    private float straightV;
    private float weightedArc;   // weighted length of one quarter arc
    private float weightedTotal;
    private float perimeter;

    // ---- animation ----
    private final Layer big;
    private final Layer small;
    private float breathPhase;
    private long lastFrameTime;
    private boolean running;

    private float amplitude;
    private float animateToAmplitude;
    private float animateAmplitudeDiff;

    private final Path path = new Path();
    private final float[] sample = new float[4]; // x, y, tangentX, tangentY

    public CapsuleBlobDrawable() {
        big = new Layer();
        big.speedScale = 1f;
        big.phaseOffset = 0f;
        big.pushMin = dp(0.5f);
        big.pushMax = dp(8.5f);
        big.waveScale = 1f;
        big.breathScale = 1f;
        big.baseAlpha = 61; // ~0.24

        small = new Layer();
        small.speedScale = 0.82f;
        small.phaseOffset = 0.6f;
        small.pushMin = dp(0);
        small.pushMax = dp(4.25f);
        small.waveScale = 0.55f;
        small.breathScale = 0.55f;
        small.baseAlpha = 128; // ~0.5

        setState(STATE_UNMUTE);
    }

    // ---- public configuration ----

    private int colorState = -1;
    private int currentColor;
    private int fromColor;
    private int toColor;
    private float colorProgress = 1f; // 1 == settled on currentColor
    private static final float COLOR_TRANSITION_MS = 250f;

    /**
     * Apply the color for a voice state, resolving it from the current theme and collapsing
     * that state's gradient to a single blended color. Call {@link #updateColors()} after a
     * theme change to re-resolve.
     */
    public void setState(int state) {
        setState(state, false);
    }

    /**
     * Apply a voice state, optionally cross-fading the color from the current one over
     * {@link #COLOR_TRANSITION_MS}. Mirrors the animated state switch of
     * FragmentContextViewWavesDrawable, but on a single collapsed color instead of two
     * cross-faded gradients.
     */
    public void setState(int state, boolean animated) {
        if (state == colorState && colorProgress >= 1f) {
            return;
        }
        colorState = state;
        int target = resolveStateColor(state);
        if (animated && currentColor != 0 && LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS)) {
            fromColor = currentColor;
            toColor = target;
            colorProgress = 0f;
        } else {
            colorProgress = 1f;
            applyColor(target);
        }
        invalidateSelf();
    }

    /*
    public int getState() {
        return colorState >= 0 ? colorState : STATE_UNMUTE;
    }
    */

    /**
     * Resolve the voice state from the active {@link VoIPService} and apply it, mirroring
     * FragmentContextViewWavesDrawable#updateState. No-op when there is no active service.
     */
    public void updateState(boolean animated) {
        VoIPService voIPService = VoIPService.getSharedInstance();
        if (voIPService == null) {
            return;
        }
        int callState = voIPService.getCallState();
        if (!voIPService.isSwitchingStream()
                && (callState == VoIPService.STATE_WAIT_INIT
                || callState == VoIPService.STATE_WAIT_INIT_ACK
                || callState == VoIPService.STATE_CREATING
                || callState == VoIPService.STATE_RECONNECTING)) {
            setState(STATE_CONNECTING, animated);
        } else if (voIPService.groupCall != null) {
            TLRPC.GroupCallParticipant participant =
                    voIPService.groupCall.participants.get(voIPService.getSelfId());
            if (participant != null && !participant.can_self_unmute && participant.muted
                    && !ChatObject.canManageCalls(voIPService.getChat())
                    || voIPService.groupCall.call.rtmp_stream) {
                voIPService.setMicMute(true, false, false);
                setState(STATE_MUTED_BY_ADMIN, animated);
            } else {
                setState(voIPService.isMicMute() ? STATE_MUTE : STATE_UNMUTE, animated);
            }
        } else {
            setState(voIPService.isMicMute() ? STATE_MUTE : STATE_UNMUTE, animated);
        }
    }

    /** Re-resolve the current state color from the theme (e.g. after a theme switch). */
    public void updateColors() {
        if (colorState >= 0) {
            colorProgress = 1f;
            applyColor(resolveStateColor(colorState));
        }
    }

    private static int resolveStateColor(int state) {
        switch (state) {
            case STATE_UNMUTE:
                return ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_voipgroup_topPanelGreen1),
                        Theme.getColor(Theme.key_voipgroup_topPanelGreen2), 0.5f);
            case STATE_MUTE:
                return ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_voipgroup_topPanelBlue1),
                        Theme.getColor(Theme.key_voipgroup_topPanelBlue2), 0.5f);
            case STATE_MUTED_BY_ADMIN:
                return ColorUtils.blendARGB(
                        ColorUtils.blendARGB(
                                Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient),
                                Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient2), 0.5f),
                        Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient3), 0.5f);
            case STATE_CONNECTING:
            default:
                return Theme.getColor(Theme.key_voipgroup_topPanelGray);
        }
    }

    /** Set a flat color directly, cancelling any in-flight state transition. */
    public void setColor(int color) {
        colorProgress = 1f;
        applyColor(color);
        invalidateSelf();
    }

    private void applyColor(int color) {
        currentColor = color;
        big.color = color;
        small.color = color;
        big.applyColor();
        small.applyColor();
    }

    public void setCornerRadius(float radiusPx) {
        cornerRadius = radiusPx;
        rebuildGeometry();
    }

    public void setTargetSpacing(float spacingPx) {
        targetSpacing = spacingPx;
        rebuildGeometry();
    }

    public void setCurvatureBoost(float boost) {
        curvatureBoost = Math.max(1f, boost);
        rebuildGeometry();
    }

    public void setWaveDepth(float depthPx) {
        waveDepth = depthPx;
        rebuildGeometry();
    }

    /**
     * The largest distance (px) a point can sit outside the base outline: floor + breathing
     * + full wave, taken over both layers. The bounds must be at least this much larger than
     * the desired capsule on every side, or crests will clip. Size bounds as
     * {@code capsuleSize + 2 * getRequiredInset()}.
     */
    public int getRequiredInset() {
        return (int) maxOutwardExcursion() + dp(1);
    }

    private float maxOutwardExcursion() {
        float bigOff = big.pushMax + breathDepth * big.breathScale + waveDepth * big.waveScale;
        float smallOff = small.pushMax + breathDepth * small.breathScale + waveDepth * small.waveScale;
        return Math.max(bigOff, smallOff);
    }

    /**
     * Spatial coherence between neighboring points, in [0, 1]. Higher values pull each
     * point's depth toward the average of its two neighbors, preventing adjacent points from
     * sitting at opposite extremes at large waveDepth. 0 disables smoothing.
     */
    public void setNeighborCoherence(float coherence) {
        neighborCoherence = Math.max(0f, Math.min(1f, coherence));
    }

    public void setBreathing(float depthPx, float periodMs) {
        breathDepth = depthPx;
        breathPeriodMs = Math.max(1f, periodMs);
        rebuildGeometry();
    }

    // ---- lifecycle ----

    private final Runnable mInvalidateSelf = () -> {
        if (LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS)) {
            invalidateSelf();
        }
    };

    public void start() {
        if (running) {
            return;
        }
        running = true;
        lastFrameTime = SystemClock.elapsedRealtime();
        Choreographer60FpsContent.getInstance().addFrameCallback(mInvalidateSelf, 60);
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        Choreographer60FpsContent.getInstance().removeFrameCallback(mInvalidateSelf);
    }

    public boolean isRunning() {
        return running;
    }

    public void setAmplitude(float value) {
        setAmplitude(value, false);
    }

    /** Feed an audio level in [0, 1]. isBig selects the smoothing curve, as in BlobDrawable. */
    public void setAmplitude(float value, boolean isBig) {
        animateToAmplitude = value;
        if (!LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS)) {
            return;
        }
        float diff = animateToAmplitude - amplitude;
        if (isBig) {
            animateAmplitudeDiff = diff / (100f + (diff > 0 ? 300f : 500f) * animationSpeed);
        } else {
            animateAmplitudeDiff = diff / (100f + (diff > 0 ? 400f : 500f) * animationSpeedTiny);
        }
    }

    // ---- Drawable ----

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        rebuildGeometry();
    }

    private void rebuildGeometry() {
        Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }
        // Reserve room for the largest possible outward excursion (floor + breath + wave) so
        // crests never clip. If the bounds are too small to hold it, clamp so the geometry
        // stays valid (waves will clip) rather than collapsing.
        float inset = maxOutwardExcursion() + dp(1);
        float maxInset = Math.min(b.width(), b.height()) / 2f - dp(2);
        if (inset > maxInset) {
            inset = Math.max(0f, maxInset);
        }
        halfWidth = b.width() / 2f - inset;
        halfHeight = b.height() / 2f - inset;
        if (halfWidth < 1f || halfHeight < 1f) {
            return;
        }
        radius = Math.min(cornerRadius, Math.min(halfWidth, halfHeight));

        innerX = halfWidth - radius;
        innerY = halfHeight - radius;
        straightH = 2f * innerX;
        straightV = 2f * innerY;

        weightedArc = curvatureBoost * (float) (Math.PI / 2.0) * radius;
        weightedTotal = 2f * straightH + 2f * straightV + 4f * weightedArc;
        perimeter = 2f * straightH + 2f * straightV + (float) (2.0 * Math.PI) * radius;

        int n = Math.max(12, Math.min(80, Math.round(perimeter / targetSpacing)));
        if (n != big.count()) {
            big.resize(n);
            small.resize(n);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty() || halfWidth < 1f) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        boolean animating = running || colorProgress < 1f;
        long dt = animating ? Math.min(40L, Math.max(0L, now - lastFrameTime)) : 0L;
        lastFrameTime = now;

        boolean lite = LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS);
        if (lite && dt > 0) {
            updateAmplitude(dt);
            breathPhase += (dt / breathPeriodMs) * (float) (2.0 * Math.PI);
            big.update(amplitude);
            small.update(amplitude);
        }

        if (colorProgress < 1f && dt > 0) {
            colorProgress += dt / COLOR_TRANSITION_MS;
            if (colorProgress > 1f) {
                colorProgress = 1f;
            }
            applyColor(ColorUtils.blendARGB(fromColor, toColor, colorProgress));
        }

        float cX = b.exactCenterX();
        float cY = b.exactCenterY();

        // Breathing recedes as the voice level rises, so at speech the amplitude-driven
        // waves dominate and at idle the breathing carries the motion.
        float breathFade = 1f - BREATH_AMPLITUDE_FALLOFF * amplitude;
        float breathBig = breathDepth * big.breathScale * breathFade
                * (0.5f + 0.5f * (float) Math.sin(breathPhase));
        float breathSmall = breathDepth * small.breathScale * breathFade
                * (0.5f + 0.5f * (float) Math.sin(breathPhase + small.phaseOffset));

        drawLayer(canvas, big, cX, cY, breathBig);
        drawLayer(canvas, small, cX, cY, breathSmall);

        if (colorProgress < 1f) {
            invalidateSelf();
        }
    }

    private void drawLayer(Canvas canvas, Layer layer, float cX, float cY, float breath) {
        int n = layer.count();
        if (n == 0) {
            return;
        }
        float push = layer.pushMin + (layer.pushMax - layer.pushMin) * amplitude;
        float wave = waveDepth * layer.waveScale * (WAVE_IDLE_FRACTION + (1f - WAVE_IDLE_FRACTION) * amplitude);

        // Pass 1: effective depth and tangential offset for this frame.
        float[] dEff = layer.depthEff;
        float[] dTmp = layer.depthTmp;
        float[] oEff = layer.offsetEff;
        for (int i = 0; i < n; i++) {
            float pr = SMOOTHER.getInterpolation(layer.progress[i]);
            dEff[i] = layer.depth[i] * (1f - pr) + layer.depthNext[i] * pr;
            oEff[i] = layer.offset[i] * (1f - pr) + layer.offsetNext[i] * pr;
        }

        // Spatial smoothing: a ring Laplacian pulls each depth toward the average of its two
        // neighbors, so adjacent points can't sit at opposite extremes. This removes the
        // high-frequency jaggedness that appears at large waveDepth without flattening the
        // low-frequency lumpiness that gives the blob its organic shape.
        if (neighborCoherence > 0f) {
            for (int it = 0; it < SMOOTHING_ITERATIONS; it++) {
                for (int i = 0; i < n; i++) {
                    int p = i == 0 ? n - 1 : i - 1;
                    int q = i + 1 == n ? 0 : i + 1;
                    float avg = (dEff[p] + dEff[q]) * 0.5f;
                    dTmp[i] = dEff[i] + neighborCoherence * (avg - dEff[i]);
                }
                float[] swap = dEff;
                dEff = dTmp;
                dTmp = swap;
            }
        }

        // Pass 2: place points and build the path.
        for (int i = 0; i < n; i++) {
            pointAt((float) i / n + oEff[i], sample);

            float nx = sample[3];   // outward normal = (tangentY, -tangentX)
            float ny = -sample[2];
            // Waves bulge outward from the floor (push + breath). The floor grows with
            // amplitude, so at high volume every point is lifted well clear of the base
            // rectangle and the whole ring inflates roughly uniformly, instead of some
            // points dipping inward to the rectangle as inward troughs would.
            float off = push + breath + dEff[i] * wave;

            layer.px[i] = cX + sample[0] + nx * off;
            layer.py[i] = cY + sample[1] + ny * off;
            layer.tx[i] = sample[2];
            layer.ty[i] = sample[3];
        }

        path.rewind();
        path.moveTo(layer.px[0], layer.py[0]);
        for (int i = 0; i < n; i++) {
            int j = i + 1 < n ? i + 1 : 0;
            float dx = layer.px[j] - layer.px[i];
            float dy = layer.py[j] - layer.py[i];
            float arm = (float) Math.sqrt(dx * dx + dy * dy) / 3f;
            path.cubicTo(
                    layer.px[i] + layer.tx[i] * arm, layer.py[i] + layer.ty[i] * arm,
                    layer.px[j] - layer.tx[j] * arm, layer.py[j] - layer.ty[j] * arm,
                    layer.px[j], layer.py[j]
            );
        }
        path.close();
        canvas.drawPath(path, layer.paint);
    }

    /**
     * Position and unit tangent on the base outline at the curvature-weighted fraction f,
     * measured clockwise from the left end of the top edge.
     * out = { x, y, tangentX, tangentY }, relative to the center.
     * The outward normal is (tangentY, -tangentX).
     */
    private void pointAt(float f, float[] out) {
        float t = (f - (float) Math.floor(f)) * weightedTotal;

        // 1. top straight
        if (t < straightH) {
            out[0] = -innerX + t;
            out[1] = -halfHeight;
            out[2] = 1f;
            out[3] = 0f;
            return;
        }
        t -= straightH;
        // 2. top-right arc
        if (t < weightedArc) {
            arc(innerX, -innerY, -(float) (Math.PI / 2.0) + t / (curvatureBoost * radius), out);
            return;
        }
        t -= weightedArc;
        // 3. right straight
        if (t < straightV) {
            out[0] = halfWidth;
            out[1] = -innerY + t;
            out[2] = 0f;
            out[3] = 1f;
            return;
        }
        t -= straightV;
        // 4. bottom-right arc
        if (t < weightedArc) {
            arc(innerX, innerY, t / (curvatureBoost * radius), out);
            return;
        }
        t -= weightedArc;
        // 5. bottom straight
        if (t < straightH) {
            out[0] = innerX - t;
            out[1] = halfHeight;
            out[2] = -1f;
            out[3] = 0f;
            return;
        }
        t -= straightH;
        // 6. bottom-left arc
        if (t < weightedArc) {
            arc(-innerX, innerY, (float) (Math.PI / 2.0) + t / (curvatureBoost * radius), out);
            return;
        }
        t -= weightedArc;
        // 7. left straight
        if (t < straightV) {
            out[0] = -halfWidth;
            out[1] = innerY - t;
            out[2] = 0f;
            out[3] = -1f;
            return;
        }
        t -= straightV;
        // 8. top-left arc
        arc(-innerX, -innerY, (float) Math.PI + t / (curvatureBoost * radius), out);
    }

    private void arc(float centerX, float centerY, float angle, float[] out) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        out[0] = centerX + radius * cos;
        out[1] = centerY + radius * sin;
        out[2] = -sin;
        out[3] = cos;
    }

    private void updateAmplitude(long dt) {
        if (animateToAmplitude != amplitude) {
            amplitude += animateAmplitudeDiff * dt;
            if (animateAmplitudeDiff > 0) {
                if (amplitude > animateToAmplitude) {
                    amplitude = animateToAmplitude;
                }
            } else {
                if (amplitude < animateToAmplitude) {
                    amplitude = animateToAmplitude;
                }
            }
        }
    }

    private int mAlpha = 255;

    @Override
    public void setAlpha(int alpha) {
        if (mAlpha != alpha) {
            mAlpha = alpha;
            big.setLayerAlpha(alpha);
            small.setLayerAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return mAlpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        big.paint.setColorFilter(colorFilter);
        small.paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /** One blob layer: its own point arrays, animation state and paint. */
    private static final class Layer {
        float speedScale;
        float phaseOffset;
        float pushMin;
        float pushMax;
        float waveScale;
        float breathScale;
        int baseAlpha;
        int color = 0xFF534AB7;

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Random random = new Random();

        float[] depth;
        float[] depthNext;
        float[] offset;
        float[] offsetNext;
        float[] progress;
        float[] speed;

        float[] px;
        float[] py;
        float[] tx;
        float[] ty;

        float[] depthEff;   // scratch: effective depth this frame (before/after smoothing)
        float[] depthTmp;   // scratch: ping-pong buffer for the Laplacian
        float[] offsetEff;  // scratch: effective tangential offset this frame

        private int n;
        private static final float WALK_STEP = 0.35f;
        private static final float MAX_JITTER = 0.18f;

        int count() {
            return n;
        }

        private int mAlpha = 255;

        void applyColor() {
            paint.setColor(color);
            paint.setAlpha(baseAlpha * mAlpha / 255);
        }

        void setLayerAlpha(int alpha) {
            mAlpha = alpha;
            applyColor();
        }

        void resize(int newN) {
            n = newN;
            depth = new float[n];
            depthNext = new float[n];
            offset = new float[n];
            offsetNext = new float[n];
            progress = new float[n];
            speed = new float[n];
            px = new float[n];
            py = new float[n];
            tx = new float[n];
            ty = new float[n];
            depthEff = new float[n];
            depthTmp = new float[n];
            offsetEff = new float[n];
            for (int i = 0; i < n; i++) {
                depth[i] = random.nextFloat();
                offset[i] = (random.nextFloat() - 0.5f) * 2f * MAX_JITTER / n;
                next(i);
                progress[i] = random.nextFloat();
            }
            applyColor();
        }

        void next(int i) {
            float jitterLimit = MAX_JITTER / n;
            float d = depth[i] + (random.nextFloat() - 0.5f) * 2f * WALK_STEP;
            depthNext[i] = clamp(d, 0f, 1f);
            float o = offset[i] + (random.nextFloat() - 0.5f) * 2f * jitterLimit * WALK_STEP;
            offsetNext[i] = clamp(o, -jitterLimit, jitterLimit);
            speed[i] = (0.017f + 0.003f * random.nextFloat()) * speedScale;
        }

        void update(float amplitude) {
            for (int i = 0; i < n; i++) {
                progress[i] += (speed[i] * MIN_SPEED) + amplitude * speed[i] * MAX_SPEED;
                if (progress[i] >= 1f) {
                    progress[i] = 0f;
                    depth[i] = depthNext[i];
                    offset[i] = offsetNext[i];
                    next(i);
                }
            }
        }

        static float clamp(float v, float min, float max) {
            return v < min ? min : (v > max ? max : v);
        }
    }
}