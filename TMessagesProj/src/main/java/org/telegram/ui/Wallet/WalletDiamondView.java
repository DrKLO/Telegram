package org.telegram.ui.Wallet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.SurfaceTexture;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Premium.GLIcon.DiamondSurfaceView;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.GLIcon.Icon3D;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RLottieImageView;

/** A square, transparently padded Wallet diamond, with a lightweight device fallback. */
public class WalletDiamondView extends FrameLayout {
    private final int preferredSize;
    private final int animationSizeDp;
    private View iconView;
    private DiamondSurfaceView glView;
    private GLIconTextureView textureView;
    private GLIconRenderer renderer;
    private RLottieImageView lottieView;
    private boolean attached;
    private boolean running;
    private boolean paused;
    private boolean startedLottie;
    private boolean resumeLottie;
    private boolean inputDrivenRotation;
    private int dragPointerId = MotionEvent.INVALID_POINTER_ID;
    private float dragX, dragY;
    private float restingAngleX, restingAngleY;
    private ValueAnimator returnAnimator;
    private SpringAnimation dragScaleSpring;
    private float conversionWobble;
    private float introYaw, introRoll;
    private float swipeProgress;
    private SpringAnimation swipeTranslationSpring;
    private float swipeTranslationVelocity;
    private float previousSwipeVelocity;
    private long lastSwipeSample;
    private boolean hasSwipeProgress;
    private float swipeYaw;
    private float swipeAngularVelocity;
    private long lastSwipeFrame;
    private boolean swipeMotionRunning;
    private final Choreographer.FrameCallback swipeMotion = this::advanceSwipeMotion;
    private static final float IDLE_SPEED = -18f;
    private float continuousSpeed;
    private Runnable readyCallback;
    private static final float MOMENTUM_DECAY_SECONDS = .75f;
    private float editMomentum;
    private double tiltPhase;
    private long lastMotionFrame;
    private float returnOffsetX, returnOffsetY, returnProgress = 1f;
    private final Choreographer.FrameCallback amountMotion = this::advanceAmountMotion;
    private int visibilityGeneration;
    private final Runnable startIdleRotation = this::startIdleRotation;

    private void startIdleRotation() {
        if (renderer != null && running && !inputDrivenRotation) {
            setIdleAnimationEnabled(true);
        }
    }

    public WalletDiamondView(Context context, int sizeDp) {
        this(context, sizeDp, false);
    }

    /** Use view composition when this diamond must appear behind in-window dialogs. */
    public WalletDiamondView(Context context, int sizeDp, boolean useTextureView) {
        super(context);
        preferredSize = AndroidUtilities.dp(sizeDp);
        animationSizeDp = sizeDp;
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW || !supportsGles3(context)) {
            createLottieView();
        } else if (useTextureView) {
            textureView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_DIAMOND) {
                // Reserve enough pixels for the drag spring without resizing buffers mid-gesture.
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    int side = Math.round(Math.min(width, height) * 3.5f);
                    surface.setDefaultBufferSize(side, side);
                    super.onSurfaceTextureAvailable(surface, side, side);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    int side = Math.round(Math.min(width, height) * 3.5f);
                    surface.setDefaultBufferSize(side, side);
                    setDimensions(side, side);
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                    super.onSurfaceTextureUpdated(surface);
                    // Hardware-layer recreation can reset the buffer to the view size
                    // without a size callback. Keep it matched to the GL viewport.
                    int side = Math.round(Math.min(getWidth(), getHeight()) * 3.5f);
                    if (side > 0) surface.setDefaultBufferSize(side, side);
                }

                @Override
                protected void onRenderError() {
                    if (textureView == this) showLottieFallback();
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    return !inputDrivenRotation && super.onTouchEvent(event);
                }
            };
            renderer = textureView.mRenderer;
            renderer.diamondResolutionScale = 1f / 3.5f;
            iconView = textureView;
        } else {
            glView = new DiamondSurfaceView(context) {
                @Override
                protected void onRenderError() {
                    if (glView == this) showLottieFallback();
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    return !inputDrivenRotation && super.onTouchEvent(event);
                }
            };
            renderer = glView.mRenderer;
            iconView = glView;
        }
        if (renderer != null) {
            renderer.colorKey1 = Theme.key_starsGradient1;
            renderer.colorKey2 = Theme.key_starsGradient2;
            renderer.updateColors();
            setIdleAnimationEnabled(false);
        }
        addView(iconView, new LayoutParams(preferredSize, preferredSize, Gravity.CENTER));
    }

    private void setIdleAnimationEnabled(boolean enabled) {
        if (glView != null) glView.setIdleAnimationEnabled(enabled);
        if (textureView != null) textureView.setIdleAnimationEnabled(enabled);
    }

    private void setRendererPaused(boolean paused) {
        if (glView != null) glView.setPaused(paused);
        if (textureView != null) textureView.setPaused(paused);
    }

    private void whenRendererReady(Runnable callback) {
        if (glView != null) glView.whenReady(callback);
        if (textureView != null) textureView.whenReady(callback);
    }

    private void setRenderScale(float scale) {
        if (glView != null) glView.setRenderScale(scale);
        if (textureView != null) renderer.diamondResolutionScale = scale / 3.5f;
    }

    private void createLottieView() {
        lottieView = new RLottieImageView(getContext());
        lottieView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        lottieView.setAnimation(R.raw.wallet_diamond_blue, animationSizeDp, animationSizeDp);
        lottieView.getAnimatedDrawable().setAutoRepeat(0);
        iconView = lottieView;
        setOnClickListener(v -> {
            if (inputDrivenRotation || !running || lottieView.isPlaying()) return;
            lottieView.setProgress(0);
            lottieView.playAnimation();
        });
        setClickable(!inputDrivenRotation);
    }

    private void showLottieFallback() {
        if (renderer == null) return;
        endDrag(false);
        ++visibilityGeneration;
        removeCallbacks(startIdleRotation);
        stopAmountMotion();
        whenRendererReady(null);
        setIdleAnimationEnabled(false);
        setRendererPaused(true);
        if (glView != null) glView.stopThread();
        if (textureView != null) textureView.stopThread();
        removeView(iconView);
        glView = null;
        textureView = null;
        renderer = null;
        createLottieView();
        addView(iconView, new LayoutParams(preferredSize, preferredSize, Gravity.CENTER));
        running = false;
        if (continuousSpeed != 0) lottieView.getAnimatedDrawable().setAutoRepeat(1);
        updateAnimationState();
        notifyReady();
    }

    private static boolean supportsGles3(Context context) {
        // Query the advertised driver capability without creating an EGL context.
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configuration = manager == null ? null : manager.getDeviceConfigurationInfo();
        return configuration != null && configuration.reqGlEsVersion >= 0x00030000;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        updateAnimationState();
    }

    /** Used by the send flight and pending row; keeps the same pose across the handoff. */
    public void setContinuousRotation(float degreesPerSecond) {
        continuousSpeed = degreesPerSecond;
        setInputDrivenRotation();
        setEnabled(false);
        if (lottieView != null) lottieView.getAnimatedDrawable().setAutoRepeat(1);
    }

    public void copyRotationFrom(WalletDiamondView source) {
        if (source != null && source.renderer != null && renderer != null) {
            restingAngleX = source.renderer.angleX;
            restingAngleY = source.renderer.angleY;
            applyAmountRotation();
        }
    }

    public boolean canAnimateFlight() {
        return renderer != null && isShown() && getAlpha() > .01f;
    }

    float getFlightYaw() {
        return renderer == null ? 0f : (float) Math.toRadians(renderer.angleX + renderer.angleX3);
    }

    float getFlightPitch() {
        return renderer == null ? 0f : (float) Math.toRadians(renderer.angleY + renderer.headerTilt);
    }

    public void whenReady(Runnable callback) {
        readyCallback = callback;
        if (renderer != null) whenRendererReady(this::notifyReady);
        else if (callback != null) post(this::notifyReady);
    }

    private void notifyReady() {
        Runnable callback = readyCallback;
        readyCallback = null;
        if (callback != null) callback.run();
    }

    public void setHeaderTilt(float degrees) {
        if (renderer != null) renderer.headerTilt = degrees;
    }

    public float getConversionWobble() {
        return conversionWobble;
    }

    public void setConversionWobble(float degrees) {
        conversionWobble = degrees;
        // Roll the mesh left/right like the currency sign, independently of swipe/typing.
        updateRoll();
    }

    public void setSwipeProgress(float progress, boolean dragging) {
        final long now = System.nanoTime();
        if (!hasSwipeProgress) {
            hasSwipeProgress = true;
            swipeProgress = progress;
            lastSwipeSample = now;
            return;
        }
        final float delta = progress - swipeProgress;
        swipeProgress = progress;
        if (!dragging) {
            // The pager's settling animation must not keep pushing the diamond.
            previousSwipeVelocity = 0;
            lastSwipeSample = now;
        } else if (delta != 0) {
            final float dt = Math.max(1f / 120f, Math.min(1f / 30f, (now - lastSwipeSample) / 1e9f));
            lastSwipeSample = now;
            final float velocity = delta / dt;
            final float impulse = -(velocity - previousSwipeVelocity) * AndroidUtilities.dp(140);
            previousSwipeVelocity = velocity;
            if (swipeTranslationSpring == null) {
                swipeTranslationSpring = new SpringAnimation(this, SpringAnimation.TRANSLATION_X);
                swipeTranslationSpring.setSpring(new SpringForce(0).setDampingRatio(.32f).setStiffness(300f));
                swipeTranslationSpring.addUpdateListener((animation, value, springVelocity) ->
                        swipeTranslationVelocity = springVelocity);
            }
            // Update velocity in place: restarting every touch frame prevents the spring from advancing.
            swipeTranslationVelocity = Math.max(-AndroidUtilities.dp(1600),
                    Math.min(AndroidUtilities.dp(1600), swipeTranslationVelocity + impulse));
            swipeTranslationSpring.setStartVelocity(swipeTranslationVelocity);
            swipeTranslationSpring.animateToFinalPosition(0);
        }
        if (delta == 0) return;
        // Page movement adds angular momentum; there is no spring pulling back to a resting angle.
        swipeAngularVelocity = Math.max(-720f, Math.min(720f, swipeAngularVelocity - delta * 300f));
        if (!swipeMotionRunning && attached) {
            swipeMotionRunning = true;
            lastSwipeFrame = 0;
            Choreographer.getInstance().postFrameCallback(swipeMotion);
        }
    }

    private void advanceSwipeMotion(long frameTimeNanos) {
        if (!attached) return;
        final float dt = lastSwipeFrame == 0 ? 1f / 60f
                : Math.min(.05f, (frameTimeNanos - lastSwipeFrame) / 1e9f);
        lastSwipeFrame = frameTimeNanos;
        final float decaySeconds = .55f;
        final float decay = (float) Math.exp(-dt / decaySeconds);
        swipeYaw = (swipeYaw + swipeAngularVelocity * decaySeconds * (1f - decay)) % 360f;
        swipeAngularVelocity *= decay;
        updateSwipeRotation();
        if (Math.abs(swipeAngularVelocity) > .5f) {
            Choreographer.getInstance().postFrameCallback(swipeMotion);
        } else {
            swipeAngularVelocity = 0;
            swipeMotionRunning = false;
            lastSwipeFrame = 0;
        }
    }

    private void updateSwipeRotation() {
        // angleX3 is the renderer's additional yaw (rotation around the Y axis).
        if (renderer != null) renderer.angleX3 = swipeYaw + introYaw;
        else if (lottieView != null) lottieView.setRotationY(swipeYaw + introYaw);
    }

    private void updateRoll() {
        if (renderer != null) renderer.diamondRoll = conversionWobble + introRoll;
        else if (lottieView != null) lottieView.setRotation(conversionWobble + introRoll);
    }

    public void setStarParticlesView(StarParticlesView particlesView) {
        if (glView != null) glView.setStarParticlesView(particlesView);
        if (textureView != null) textureView.setStarParticlesView(particlesView);
    }

    public void setInputDrivenRotation() {
        inputDrivenRotation = true;
        setClipChildren(false);
        setClipToPadding(false);
        removeCallbacks(startIdleRotation);
        setClickable(false);
        if (renderer != null) {
            if (glView != null) glView.reserveRenderScale(3.5f);
            setIdleAnimationEnabled(false);
            restingAngleX = renderer.angleX;
            restingAngleY = renderer.angleY;
            if (running) startAmountMotion();
        }
    }

    public void onAmountEdited(boolean added) {
        if (!inputDrivenRotation || !running || dragPointerId != MotionEvent.INVALID_POINTER_ID) return;
        if (lottieView != null) {
            if (!lottieView.isPlaying()) {
                lottieView.setProgress(0);
                lottieView.playAnimation();
            }   
            return;
        }
        // Add/delete impulses oppose each other and decay back to the idle spin.
        editMomentum = Math.max(-360f, Math.min(360f, editMomentum + (added ? -160f : 160f)));
    }

    private void startAmountMotion() {
        Choreographer.getInstance().removeFrameCallback(amountMotion);
        lastMotionFrame = 0;
        Choreographer.getInstance().postFrameCallback(amountMotion);
    }

    private void stopAmountMotion() {
        Choreographer.getInstance().removeFrameCallback(amountMotion);
        lastMotionFrame = 0;
        editMomentum = 0f;
    }

    private void advanceAmountMotion(long frameTimeNanos) {
        if (!running || !inputDrivenRotation || renderer == null) return;
        float dt = lastMotionFrame == 0 ? 0f : Math.min(.05f, (frameTimeNanos - lastMotionFrame) / 1e9f);
        lastMotionFrame = frameTimeNanos;
        if (dragPointerId == MotionEvent.INVALID_POINTER_ID) {
            float decay = (float) Math.exp(-dt / MOMENTUM_DECAY_SECONDS);
            restingAngleX = (restingAngleX + (continuousSpeed == 0 ? IDLE_SPEED : continuousSpeed) * dt
                    + editMomentum * MOMENTUM_DECAY_SECONDS * (1f - decay)) % 360f;
            editMomentum *= decay;
            tiltPhase = (tiltPhase + dt * Math.PI * 2 / 8) % (Math.PI * 2);
            restingAngleY = 9f * (float) Math.sin(tiltPhase);
            applyAmountRotation();
        }
        Choreographer.getInstance().postFrameCallback(amountMotion);
    }

    private void applyAmountRotation() {
        if (renderer == null) return;
        renderer.angleX = restingAngleX + returnOffsetX * (1f - returnProgress);
        renderer.angleY = restingAngleY + returnOffsetY * (1f - returnProgress);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return (isEnabled() && inputDrivenRotation && renderer != null && running) || super.onInterceptTouchEvent(event);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) endDrag(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || !inputDrivenRotation || renderer == null || !running) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (returnAnimator != null) {
                    returnAnimator.removeAllListeners();
                    returnAnimator.cancel();
                    returnAnimator = null;
                }
                // Freeze the autonomous pose and discard any previous typing impulse.
                editMomentum = 0f;
                dragPointerId = event.getPointerId(0);
                dragX = event.getX();
                dragY = event.getY();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                animateDragScale(3f);
                return true;
            case MotionEvent.ACTION_MOVE:
                int index = event.findPointerIndex(dragPointerId);
                if (index < 0) {
                    endDrag(true);
                    return true;
                }
                // Measure movement in the unscaled container, in density-independent units.
                float degreesPerPixel = .8f / AndroidUtilities.density;
                renderer.angleX += (event.getX(index) - dragX) * degreesPerPixel;
                renderer.angleY += (event.getY(index) - dragY) * degreesPerPixel;
                dragX = event.getX(index);
                dragY = event.getY(index);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == dragPointerId) {
                    int next = event.getActionIndex() == 0 ? 1 : 0;
                    dragPointerId = event.getPointerId(next);
                    dragX = event.getX(next);
                    dragY = event.getY(next);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                endDrag(true);
                return true;
        }
        return dragPointerId != MotionEvent.INVALID_POINTER_ID;
    }

    public void setIntroProgress(float progress) {
        final float scale = 1f + 2f * (1f - progress);
        iconView.setScaleX(scale);
        iconView.setScaleY(scale);
        if (renderer != null) setRenderScale(scale > 1f ? 3.5f : 1f);
        introYaw = -720f * (1f - progress);
        introRoll = 35f * (float) Math.sin(Math.PI * progress);
        updateSwipeRotation();
        updateRoll();
    }

    private void animateDragScale(float scale) {
        // Raise offscreen resolution for the expanded size and spring overshoot.
        // The reserved surface buffer stays fixed throughout both animations.
        if (scale > 1f && renderer != null) setRenderScale(3.5f);
        if (dragScaleSpring == null) {
            dragScaleSpring = new SpringAnimation(new FloatValueHolder(iconView.getScaleX()));
            dragScaleSpring.setSpring(new SpringForce(scale).setDampingRatio(.55f).setStiffness(280f));
            dragScaleSpring.setMinimumVisibleChange(.001f);
            dragScaleSpring.addUpdateListener((animation, value, velocity) -> {
                iconView.setScaleX(value);
                iconView.setScaleY(value);
            });
            dragScaleSpring.addEndListener((animation, canceled, value, velocity) -> {
                if (!canceled && value <= 1.001f && renderer != null) setRenderScale(1f);
            });
        }
        // Retarget the running spring so rapid presses preserve momentum.
        dragScaleSpring.animateToFinalPosition(scale);
    }

    private void endDrag(boolean animated) {
        if (dragPointerId != MotionEvent.INVALID_POINTER_ID) {
            dragPointerId = MotionEvent.INVALID_POINTER_ID;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            if (renderer != null) {
                if (animated) {
                    // Blend away the swipe offset while the underlying idle spin resumes.
                    returnOffsetX = (float) Math.IEEEremainder(renderer.angleX - restingAngleX, 360f);
                    returnOffsetY = (float) Math.IEEEremainder(renderer.angleY - restingAngleY, 360f);
                    returnProgress = 0f;
                    returnAnimator = ValueAnimator.ofFloat(0f, 1f);
                    returnAnimator.setDuration(350);
                    returnAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    returnAnimator.addUpdateListener(a -> {
                        returnProgress = (float) a.getAnimatedValue();
                        applyAmountRotation();
                    });
                    returnAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            returnAnimator = null;
                            restoreRestingRotation();
                        }
                    });
                    returnAnimator.start();
                } else {
                    restoreRestingRotation();
                }
            }
        }
        if (!animated) finishDragReturn();
        if (iconView == null) return;
        if (animated) {
            animateDragScale(1f);
        } else {
            if (dragScaleSpring != null) {
                dragScaleSpring.cancel();
                dragScaleSpring = null;
            }
            iconView.setScaleX(1f);
            iconView.setScaleY(1f);
            if (renderer != null) setRenderScale(1f);
        }
    }

    private void restoreRestingRotation() {
        returnProgress = 1f;
        returnOffsetX = returnOffsetY = 0f;
        applyAmountRotation();
    }

    private void finishDragReturn() {
        if (returnAnimator == null) return;
        returnAnimator.removeAllListeners();
        returnAnimator.cancel();
        returnAnimator = null;
        restoreRestingRotation();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(preferredSize + getPaddingLeft() + getPaddingRight(), widthMeasureSpec);
        int height = resolveSize(preferredSize + getPaddingTop() + getPaddingBottom(), heightMeasureSpec);
        setMeasuredDimension(width, height);
        int side = Math.max(0, Math.min(width - getPaddingLeft() - getPaddingRight(),
                height - getPaddingTop() - getPaddingBottom()));
        int spec = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY);
        iconView.measure(spec, spec);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        updateAnimationState();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        Choreographer.getInstance().removeFrameCallback(swipeMotion);
        if (swipeTranslationSpring != null) {
            swipeTranslationSpring.cancel();
            swipeTranslationSpring = null;
            setTranslationX(0);
        }
        swipeTranslationVelocity = 0;
        previousSwipeVelocity = 0;
        lastSwipeSample = 0;
        swipeMotionRunning = false;
        hasSwipeProgress = false;
        lastSwipeFrame = 0;
        swipeAngularVelocity = 0;
        swipeYaw = 0;
        updateSwipeRotation();
        updateAnimationState();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimationState();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimationState();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        updateAnimationState();
    }

    private void updateAnimationState() {
        boolean visible = !paused && attached && isShown() && getWindowVisibility() == VISIBLE && hasWindowFocus();
        if (running == visible) return;
        running = visible;
        if (!visible) endDrag(false);
        final int generation = ++visibilityGeneration;
        if (renderer != null) {
            removeCallbacks(startIdleRotation);
            setIdleAnimationEnabled(false);
            if (inputDrivenRotation) {
                if (visible) startAmountMotion();
                else stopAmountMotion();
            } else {
                if (visible) {
                    renderer.angleX = 0;
                    renderer.angleY = 0;
                    // Shader initialization can take longer than the sheet entrance.
                    // Keep the first rendered pose facing forward, then begin rotating.
                    whenRendererReady(() -> {
                        if (running && !inputDrivenRotation && generation == visibilityGeneration) {
                            postDelayed(startIdleRotation, 500);
                        }
                    });
                }
            }
            setRendererPaused(!visible);
        } else if (lottieView != null) {
            if (visible) {
                if (!startedLottie || resumeLottie) lottieView.playAnimation();
                startedLottie = true;
            } else {
                resumeLottie = lottieView.isPlaying();
                lottieView.stopAnimation();
            }
        }
    }
}
