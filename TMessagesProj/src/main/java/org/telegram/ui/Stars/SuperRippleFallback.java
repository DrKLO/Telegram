package org.telegram.ui.Stars;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RenderEffect;
import android.os.Build;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import androidx.annotation.RequiresApi;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;

// for Android that doesn't support AGSL
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class SuperRippleFallback extends ISuperRipple {

    public static class Effect {
        public final ValueAnimator animator;
        public final float cx, cy;
        public final float intensity;
        public float t;
        public float duration;

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

    public final float[] radii = new float[8];
    private final Path outlineProviderPath = new Path();
    private final ViewOutlineProvider outlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radii[0]);
            }
        }
    };

    public final ArrayList<Effect> effects = new ArrayList<>();
    public final int MAX_COUNT = 10;

    public SuperRippleFallback(View view) {
        super(view);

//        radii[0] = radii[1] = // top left
//        radii[2] = radii[3] = // top right
//        radii[4] = radii[5] = // bottom right
//        radii[6] = radii[7] = // bottom left

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            final WindowInsets insets = view.getRootWindowInsets();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                final RoundedCorner topLeftCorner = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
                final RoundedCorner topRightCorner = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
                final RoundedCorner bottomLeftCorner = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
                final RoundedCorner bottomRightCorner = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);

                radii[0] = radii[1] = topLeftCorner == null ? 0 : topLeftCorner.getRadius();
                radii[2] = radii[3] = topRightCorner == null ? 0 : topRightCorner.getRadius();
                radii[4] = radii[5] = bottomRightCorner == null || view != view.getRootView() && AndroidUtilities.navigationBarHeight > 0 ? 0 : bottomRightCorner.getRadius();
                radii[6] = radii[7] = bottomLeftCorner == null || view != view.getRootView() && AndroidUtilities.navigationBarHeight > 0 ? 0 : bottomLeftCorner.getRadius();
            } else {

            }
        }

        outlineProviderPath.rewind();
        outlineProviderPath.addRoundRect(0, 0, view.getWidth(), view.getHeight(), radii, Path.Direction.CW);
    }

    @Override
    public void animate(float cx, float cy, float intensity) {
        if (effects.size() >= MAX_COUNT) return;

        final float duration = 0.5f;

        final ValueAnimator animator = ValueAnimator.ofFloat(0f, duration);
        final Effect effect = new Effect(cx, cy, intensity, animator);
        effect.duration = duration;

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
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.setDuration((long) (duration * 1000L));

        effects.add(effect);
        updateProperties();

        animator.start();
    }

    private void updateProperties() {
        float s = 1f, px = 0, py = 0, ps = 0;
        for (Effect effect : effects) {
            float t = effect.t / effect.duration;
            float x = (1f - (float) Math.sin(Math.PI * t));
            s *= (1f - .04f * effect.intensity) + .04f * effect.intensity * x;
            px += effect.cx * 1f;
            py += effect.cy * 1f;
            ps += 1f;
        }
        if (ps < 1) {
            px += view.getWidth() / 2f * (1f - ps);
            py += view.getHeight() / 2f * (1f - ps);
            ps = 1f;
        }
        view.setScaleX(s);
        view.setScaleY(s);
        view.setPivotX(px / ps);
        view.setPivotY(py / ps);
        if (view.getOutlineProvider() != (effects.isEmpty() ? null : outlineProvider)) {
            view.setOutlineProvider(effects.isEmpty() ? null : outlineProvider);
            view.invalidate();
        }
    }

}
