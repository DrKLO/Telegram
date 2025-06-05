package org.telegram.ui.Stars;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.Build;
import android.view.RoundedCorner;
import android.view.View;
import android.view.WindowInsets;

import androidx.annotation.RequiresApi;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public class SuperRipple extends ISuperRipple {

    public static boolean supports() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    public static class Effect {
        public final ValueAnimator animator;
        public final float cx, cy;
        public final float intensity;
        public float t;

        private Effect(
            float cx, float cy,
            float intensity,
            ValueAnimator animator
        ) {
            this.cx = cx;
            this.cy = cy;
            this.intensity = intensity;
            this.animator = animator;
        }
    }

    public final ArrayList<Effect> effects = new ArrayList<>();

    public final RuntimeShader shader;
    public RenderEffect effect;

    public final int MAX_COUNT = 7;
    public int count;
    public int width, height;
    public float density;
    public final float[] t = new float[MAX_COUNT];
    public final float[] centerX = new float[MAX_COUNT];
    public final float[] centerY = new float[MAX_COUNT];
    public final float[] intensity = new float[MAX_COUNT];

    public SuperRipple(View view) {
        super(view);

        final String code = AndroidUtilities.readRes(R.raw.superripple_effect);
        shader = new RuntimeShader(code);
        setupSizeUniforms(true);

        effect = RenderEffect.createRuntimeShaderEffect(shader, "img");
    }

    private void setupSizeUniforms(boolean force) {
        if (force || width != view.getWidth() || height != view.getHeight() || Math.abs(density - AndroidUtilities.density) > 0.01f) {
            shader.setFloatUniform("size", width = view.getWidth(), height = view.getHeight());
            shader.setFloatUniform("density", density = AndroidUtilities.density);

            final WindowInsets insets = view.getRootWindowInsets();
            final RoundedCorner topLeftCorner = insets == null ? null : insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
            final RoundedCorner topRightCorner = insets == null ? null : insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
            final RoundedCorner bottomLeftCorner = insets == null ? null : insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
            final RoundedCorner bottomRightCorner = insets == null ? null : insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);

            shader.setFloatUniform(
                "radius",
                bottomRightCorner == null || view != view.getRootView() && AndroidUtilities.navigationBarHeight > 0 ? 0 : bottomRightCorner.getRadius(),
                topRightCorner == null ? 0 : topRightCorner.getRadius(),
                bottomLeftCorner == null || view != view.getRootView() && AndroidUtilities.navigationBarHeight > 0 ? 0 : bottomLeftCorner.getRadius(),
                topLeftCorner == null ? 0 : topLeftCorner.getRadius()
            );
        }
    }

    @Override
    public void animate(float cx, float cy, float intensity) {
        if (effects.size() >= MAX_COUNT) return;

        final float speed = 1200 * AndroidUtilities.density;
        final float max_dist = Math.max(
            Math.max(
                MathUtils.distance(0, 0, cx, cy),
                MathUtils.distance(view.getWidth(), 0, cx, cy)
            ), Math.max(
                MathUtils.distance(0, view.getHeight(), cx, cy),
                MathUtils.distance(view.getWidth(), view.getHeight(), cx, cy)
            )
        );
        final float duration = 2.0f * max_dist / speed;

        final ValueAnimator animator = ValueAnimator.ofFloat(0f, duration);
        final Effect effect = new Effect(cx, cy, intensity, animator);

        animator.addUpdateListener(anm -> {
            effect.t = (float) anm.getAnimatedValue();
            updateProperties();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                effects.remove(effect);
                updateProperties();
            }
        });
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animator.setDuration((long) (duration * 1000L));

        effects.add(effect);
        updateProperties();

        animator.start();
    }

    private void updateProperties() {
        boolean changed = false;
        if (!effects.isEmpty()) {
            changed = changed || count != Math.min(MAX_COUNT, effects.size());

            count = Math.min(MAX_COUNT, effects.size());
            for (int i = 0; i < count; ++i) {
                Effect e = effects.get(i);

                changed = changed || Math.abs(t[i] - e.t) > 0.001f;
                t[i] = e.t;
                changed = changed || Math.abs(centerX[i] - e.cx) > 0.001f;
                centerX[i] = e.cx;
                changed = changed || Math.abs(centerY[i] - e.cy) > 0.001f;
                centerY[i] = e.cy;
                changed = changed || Math.abs(intensity[i] - e.intensity) > 0.001f;
                intensity[i] = e.intensity;
            }

            changed = changed || width != view.getWidth() || height != view.getHeight() || Math.abs(density - AndroidUtilities.density) > 0.01f;
            if (changed) {
                shader.setIntUniform("count", count);
                shader.setFloatUniform("t", t);
                shader.setFloatUniform("centerX", centerX);
                shader.setFloatUniform("centerY", centerY);
                shader.setFloatUniform("intensity", intensity);
                setupSizeUniforms(false);
                effect = RenderEffect.createRuntimeShaderEffect(shader, "img");
            }
        }
        view.setRenderEffect(effects.isEmpty() ? null : effect);
        if (changed) {
            view.invalidate();
        }
    }

}
