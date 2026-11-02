package org.telegram.ui.Wallet;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.WalletDiamondTextureView;

/**
 * The gram in the middle of the wallet QR: a slowly spinning 3D diamond that grows and follows the finger
 * while held, like the one on the chat payment card. Static on low-end devices and in power saving.
 */
final class WalletQrDiamond extends FrameLayout {
    private static final float IDLE_SPEED = (float) (2 * Math.PI / 18 * 1.70775);
    private static final float ENLARGED_SCALE = 3f;
    /** How much larger than the blank QR center this view is laid out. */
    static final float OVERHANG = 1.35f;

    /** Whether to keep the gram drawn into the QR bitmap instead of placing this view over it. */
    static boolean isStatic(Context context) {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW || LiteMode.isPowerSaverApplied()) {
            return true;
        }
        final ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return manager == null || manager.getDeviceConfigurationInfo().reqGlEsVersion < 0x30000;
    }

    private final ImageView fallback;
    private final WalletDiamondTextureView surface;
    private float yaw, pitch, scale = 1;
    private float spinOffset;
    private int pointer = MotionEvent.INVALID_POINTER_ID;
    private float lastX, lastY;
    private boolean returning;
    private ValueAnimator returnAnimator;
    private SpringAnimation scaleSpring;

    WalletQrDiamond(Context context) {
        super(context);
        setClipChildren(false);
        fallback = new ImageView(context);
        fallback.setImageResource(R.drawable.mini_gram_72);
        // Until the diamond draws, match the static gram the QR would have had.
        fallback.setScaleX(1f / OVERHANG);
        fallback.setScaleY(1f / OVERHANG);
        addView(fallback, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        surface = new WalletDiamondTextureView(context, this::updatePose, GLIconRenderer.DIAMOND_BLUE) {
            @Override
            protected void onRenderError() {
                setPaused(true);
                setVisibility(View.GONE);
                fallback.setVisibility(View.VISIBLE);
            }

            @Override
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture texture) {
                super.onSurfaceTextureUpdated(texture);
                // Swap the static gram for the diamond once it has drawn its first frame.
                if (isReady() && fallback.getVisibility() == View.VISIBLE) fallback.setVisibility(View.INVISIBLE);
            }
        };
        addView(surface, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        surface.setPaused(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        surface.setPaused(true);
        if (returnAnimator != null) returnAnimator.cancel();
        if (scaleSpring != null) scaleSpring.cancel();
        super.onDetachedFromWindow();
    }

    private float restingYaw() {
        return (float) ((SystemClock.uptimeMillis() / 1000.0 * IDLE_SPEED) % (Math.PI * 2)) + spinOffset;
    }

    private void updatePose() {
        final boolean dragging = pointer != MotionEvent.INVALID_POINTER_ID;
        if (!dragging && !returning) {
            yaw = restingYaw();
            pitch = 0;
        }
        final boolean interacting = dragging || returning || scale > 1.001f;
        surface.setPose(yaw, pitch, interacting, interacting);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!surface.isReady() || surface.getVisibility() != View.VISIBLE) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!returning && scale <= 1.001f) {
                    yaw = restingYaw();
                    pitch = 0;
                }
                if (returnAnimator != null) {
                    returnAnimator.cancel();
                    returnAnimator = null;
                }
                returning = false;
                pointer = event.getPointerId(0);
                lastX = event.getX();
                lastY = event.getY();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                animateScale(ENLARGED_SCALE);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (pointer == MotionEvent.INVALID_POINTER_ID) return false;
                final int index = event.findPointerIndex(pointer);
                if (index < 0) {
                    release();
                    return true;
                }
                final float radiansPerPixel = (float) Math.toRadians(.8f) / AndroidUtilities.density;
                yaw += (event.getX(index) - lastX) * radiansPerPixel;
                pitch += (event.getY(index) - lastY) * radiansPerPixel;
                lastX = event.getX(index);
                lastY = event.getY(index);
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == pointer) {
                    final int next = event.getActionIndex() == 0 ? 1 : 0;
                    pointer = event.getPointerId(next);
                    lastX = event.getX(next);
                    lastY = event.getY(next);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (pointer != MotionEvent.INVALID_POINTER_ID) release();
                return true;
        }
        return pointer != MotionEvent.INVALID_POINTER_ID;
    }

    private void release() {
        pointer = MotionEvent.INVALID_POINTER_ID;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        // Spin on from wherever it was let go, easing back upright.
        spinOffset = (float) Math.IEEEremainder(spinOffset + yaw - restingYaw(), Math.PI * 2);
        final float pitchOffset = (float) Math.IEEEremainder(pitch, Math.PI * 2);
        returning = true;
        returnAnimator = ValueAnimator.ofFloat(0, 1);
        returnAnimator.setDuration(350);
        returnAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        returnAnimator.addUpdateListener(animation -> {
            final float remaining = 1f - (float) animation.getAnimatedValue();
            yaw = restingYaw();
            pitch = pitchOffset * remaining;
            returning = remaining > 0;
        });
        returnAnimator.start();
        animateScale(1f);
    }

    private void animateScale(float target) {
        if (scaleSpring == null) {
            scaleSpring = new SpringAnimation(new FloatValueHolder(scale));
            scaleSpring.setSpring(new SpringForce(target).setDampingRatio(.55f).setStiffness(280f));
            scaleSpring.setMinimumVisibleChange(.001f);
            scaleSpring.addUpdateListener((animation, value, velocity) -> {
                scale = value;
                setScaleX(value);
                setScaleY(value);
            });
        }
        scaleSpring.animateToFinalPosition(target);
    }
}
