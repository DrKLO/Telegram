package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.ScaleStateListAnimator;

public class WalletCardView extends FrameLayout implements SensorEventListener {

    private static final float CARD_ASPECT = 1.64f;
    private static final float MAX_LIGHT_TILT = 0.82f;
    private static final float MAX_CARD_ROTATION_DEGREES = 15f;
    private static final float ROTATION_SMOOTHING = 1.0f;
    private static final float GYROSCOPE_SENSITIVITY = -0.75f;
    private static final float GYRO_HORIZONTAL_TILT_MULTIPLIER = 1.5f;
    private static final float RENDER_OVERSCAN_DP = 24.0f;
    private static final int DEFAULT_QR_BUTTON_RIGHT_PADDING = 45;

    private final WalletCardTextureView textureView;
    private final WalletCard2DView loadingCard;
    private boolean showingLoadingCard = true;
    private boolean capturingGlContent;
    private boolean hiddenSinceLastFrame;
    private long loadingFrontRevision = 1;
    private long awaitedFrontRevision;
    private Runnable onFrontContentPresented;
    private boolean cardAttached;
    private boolean rendering;
    private final ProjectedFaceLayout frontFace;
    private final ProjectedFaceLayout backFace;
    private final WalletCardGLRenderer.FaceProjection faceProjection =
            new WalletCardGLRenderer.FaceProjection();
    private final float[] frontCorners = new float[8];
    private final float[] backCorners = new float[8];
    private final SensorManager sensorManager;
    private final Sensor motionSensor;
    private final boolean usesRotationVector;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];
    private final WalletCardTiltBaseline tiltBaseline =
            new WalletCardTiltBaseline(MAX_CARD_ROTATION_DEGREES);

    private float lightDirectionX = -0.22f;
    private float lightDirectionY = -0.28f;
    private float cardRotationX;
    private float additionalTilt;
    private float gyroscopeTiltScale = 1.0f;
    private float cardRotationY;
    private float sensorRotationX;
    private float sensorRotationY;
    private float appliedSensorRotationX;
    private float appliedSensorRotationY;
    private long lastGyroscopeTimestamp;
    private final Runnable transformUpdater = new Runnable() {
        @Override
        public void run() {
            if (!rendering) {
                return;
            }
            tiltBaseline.update(System.nanoTime());
            sensorRotationX = tiltBaseline.getRotationX();
            sensorRotationY = clamp(
                    tiltBaseline.getRotationY() * GYRO_HORIZONTAL_TILT_MULTIPLIER,
                    -MAX_CARD_ROTATION_DEGREES,
                    MAX_CARD_ROTATION_DEGREES
            );
            appliedSensorRotationX += (
                    sensorRotationX - appliedSensorRotationX
            ) * ROTATION_SMOOTHING;
            appliedSensorRotationY += (
                    sensorRotationY - appliedSensorRotationY
            ) * ROTATION_SMOOTHING;

            // GLIconTextureView owns the same drag/release behavior as the
            // premium star. Its angles are offsets over the live gyro pose.
            cardRotationX = appliedSensorRotationX * gyroscopeTiltScale + textureView.renderer.angleY + additionalTilt;
            cardRotationY = appliedSensorRotationY * gyroscopeTiltScale + textureView.renderer.angleX;
            applyRotation(cardRotationX, cardRotationY);
            postOnAnimation(this);
        }
    };

    public WalletCardView(Context context) {
        this(context, org.telegram.messenger.R.drawable.wallet_card_qr);
    }

    public WalletCardView(Context context, int cardIconResId) {
        this(context, cardIconResId, DEFAULT_QR_BUTTON_RIGHT_PADDING);
    }

    public WalletCardView(Context context, int cardIconResId, int qrButtonRightPadding) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);

        ScaleStateListAnimator.apply(this, 0.02f, 1.2f);
        Context appContext = context.getApplicationContext();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                WalletCard3D.prepareTextures(appContext, cardIconResId, qrButtonRightPadding);
            } catch (RuntimeException e) {
                // Rendering can retry preparation if an eager resource load failed.
                FileLog.e(e);
            }
        });

        textureView = new WalletCardTextureView(
                context,
                this,
                cardIconResId,
                qrButtonRightPadding
        );
        addView(textureView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.CENTER,
                -RENDER_OVERSCAN_DP,
                -RENDER_OVERSCAN_DP,
                -RENDER_OVERSCAN_DP,
                -RENDER_OVERSCAN_DP
        ));

        // The GL surface stays visible and rendering underneath the loading card.
        loadingCard = new WalletCard2DView(context, cardIconResId, qrButtonRightPadding, true);
        addView(loadingCard, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        frontFace = createFace(context);
        backFace = createFace(context);
        addView(backFace, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.CENTER
        ));
        addView(frontFace, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.CENTER
        ));

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor selectedSensor = sensorManager.getDefaultSensor(
                Sensor.TYPE_GAME_ROTATION_VECTOR
        );
        if (selectedSensor == null) {
            selectedSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        if (selectedSensor == null) {
            selectedSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
        motionSensor = selectedSensor;
        usesRotationVector = motionSensor != null
                && motionSensor.getType() != Sensor.TYPE_GYROSCOPE;
    }

    private void showLoadingCard() {
        showingLoadingCard = true;
        loadingFrontRevision = textureView.renderer.nextFrontRevision();
        // Keep TextureView drawing/latching, but cover its stale pose until a
        // fresh frame is available. Alpha zero would stop its draw callbacks.
        textureView.setAlpha(1f / 255f);
        frontFace.contentDirty = true;
        frontFace.drawContentInView = true;
        applyRotation(cardRotationX, cardRotationY);
    }

    private ProjectedFaceLayout createFace(Context context) {
        return new ProjectedFaceLayout(context);
    }

    void getFrontFaceProjection(Matrix result) {
        result.set(frontFace.projection);
    }

    void setOnFrontContentPresented(Runnable callback) {
        onFrontContentPresented = callback;
        if (callback != null) {
            awaitedFrontRevision = textureView.renderer.nextFrontRevision();
            frontFace.contentDirty = true;
            invalidate();
        }
    }

    public FrameLayout getFrontFace() {
        return frontFace.getContent();
    }

    public FrameLayout getBackFace() {
        return backFace.getContent();
    }

    public void setAdditionalTilt(float tilt) {
        if (additionalTilt == tilt) return;
        cardRotationX += tilt - additionalTilt;
        additionalTilt = tilt;
        applyRotation(cardRotationX, cardRotationY);
    }

    public void setUseGyroscope(float t) {
        if (gyroscopeTiltScale == t) return;
        gyroscopeTiltScale = t;
        cardRotationX = appliedSensorRotationX * gyroscopeTiltScale + textureView.renderer.angleY + additionalTilt;
        cardRotationY = appliedSensorRotationY * gyroscopeTiltScale + textureView.renderer.angleX;
        applyRotation(cardRotationX, cardRotationY);
    }

    public float getCardRotationX() {
        return cardRotationX;
    }

    public float getCardRotationY() {
        return cardRotationY;
    }

    boolean isCardMaterialReady() {
        return capturingGlContent || !showingLoadingCard;
    }

    void setDiamondAlpha(float alpha) {
        textureView.renderer.setDiamondAlpha(alpha);
    }

    void setDiamondBounds(float x, float y, float width, float height) {
        textureView.renderer.setDiamondBounds(x, y, width, height);
    }

    public void setEngravingBitmap(Bitmap bitmap) {
        loadingCard.setEngravingBitmap(bitmap);
        textureView.renderer.setEngravingBitmap(bitmap);
    }

    public void setCardIcon(int drawableResId) {
        loadingCard.setCardIcon(drawableResId);
        textureView.renderer.setCardIcon(drawableResId);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Capture unprojected content only when its views change. Pose changes
        // reuse the texture; the GL body and text share one MVP and one buffer.
        capturingGlContent = true;
        try {
            frontFace.captureContent(textureView.renderer);
        } finally {
            capturingGlContent = false;
        }
        frontFace.drawContentInView = showingLoadingCard;
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(width / CARD_ASPECT);
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateFaceProjection();
    }

    @Override
    protected void onLayout(
            boolean changed,
            int left,
            int top,
            int right,
            int bottom
    ) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            updateFaceProjection();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        cardAttached = true;
        lastGyroscopeTimestamp = 0;
        tiltBaseline.reset();
        sensorRotationX = 0.0f;
        sensorRotationY = 0.0f;
        appliedSensorRotationX = 0.0f;
        appliedSensorRotationY = 0.0f;
        updateRenderingState();
    }

    @Override
    protected void onDetachedFromWindow() {
        cardAttached = false;
        updateRenderingState();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateRenderingState();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateRenderingState();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        updateRenderingState();
    }

    private void updateRenderingState() {
        if (textureView == null || sensorManager == null) return;
        if (!isShown() || getWindowVisibility() != VISIBLE) hiddenSinceLastFrame = true;
        boolean visible = cardAttached && isShown() && getWindowVisibility() == VISIBLE && hasWindowFocus();
        if (rendering == visible) return;
        rendering = visible;
        textureView.setPaused(!visible);
        removeCallbacks(transformUpdater);
        if (visible) {
            if (hiddenSinceLastFrame) {
                hiddenSinceLastFrame = false;
                showLoadingCard();
            }
            lastGyroscopeTimestamp = 0;
            if (motionSensor != null) {
                sensorManager.registerListener(this, motionSensor, SensorManager.SENSOR_DELAY_GAME);
            }
            postOnAnimation(transformUpdater);
        } else {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (usesRotationVector) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            lightDirectionX = clamp(
                    (float) Math.sin(orientation[2]),
                    -MAX_LIGHT_TILT,
                    MAX_LIGHT_TILT
            );
            lightDirectionY = clamp(
                    -(float) Math.sin(orientation[1]),
                    -MAX_LIGHT_TILT,
                    MAX_LIGHT_TILT
            );
        } else {
            updateFromRawGyroscope(event);
        }

        tiltBaseline.setSensorRotation(
                -lightDirectionY / MAX_LIGHT_TILT
                        * MAX_CARD_ROTATION_DEGREES,
                lightDirectionX / MAX_LIGHT_TILT
                        * MAX_CARD_ROTATION_DEGREES,
                event.timestamp
        );
    }

    private void applyRotation(float rotationX, float rotationY) {
        textureView.renderer.setRotation(rotationX, rotationY);
        updateFaceProjection();

        double facing = Math.cos(Math.toRadians(rotationX))
                * Math.cos(Math.toRadians(rotationY));
        if (showingLoadingCard) {
            loadingCard.applyRotation(rotationX, rotationY);
            loadingCard.setVisibility(facing >= 0.0 ? VISIBLE : INVISIBLE);
        }
        frontFace.setVisibility(facing >= 0.0 ? VISIBLE : INVISIBLE);
        backFace.setVisibility(facing < 0.0 ? VISIBLE : INVISIBLE);
        postInvalidateOnAnimation();
    }

    private void updateFaceProjection() {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }
        faceProjection.project(
                width,
                height,
                cardRotationX,
                cardRotationY,
                false,
                frontCorners
        );
        faceProjection.project(
                width,
                height,
                cardRotationX,
                cardRotationY,
                true,
                backCorners
        );
        frontFace.setProjectedCorners(frontCorners);
        backFace.setProjectedCorners(backCorners);
    }

    private void updateFromRawGyroscope(SensorEvent event) {
        if (lastGyroscopeTimestamp != 0) {
            float seconds = (event.timestamp - lastGyroscopeTimestamp) * 0.000000001f;
            if (seconds > 0.0f && seconds < 0.1f) {
                lightDirectionX = clamp(
                        lightDirectionX
                                - event.values[1] * seconds * GYROSCOPE_SENSITIVITY,
                        -MAX_LIGHT_TILT,
                        MAX_LIGHT_TILT
                );
                lightDirectionY = clamp(
                        lightDirectionY
                                + event.values[0] * seconds * GYROSCOPE_SENSITIVITY,
                        -MAX_LIGHT_TILT,
                        MAX_LIGHT_TILT
                );
            }
        }
        lastGyroscopeTimestamp = event.timestamp;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private static class WalletCardTextureView extends GLIconTextureView {
        private static final long PRESS_ANIMATION_DURATION = 80L;
        private static final float DRAG_X_SENSITIVITY = 0.03f;
        private static final float DRAG_Y_SENSITIVITY = DRAG_X_SENSITIVITY;
        private static final float MAX_DRAG_Y_ROTATION = 32.0f;
        private static final float MAX_DRAG_X_ROTATION = 22.0f;

        final WalletCardGLRenderer renderer;
        private final WalletCardView pressTarget;
        private final int touchSlop;
        private final Runnable clickRunnable;
        private float touchDownX;
        private float touchDownY;
        private float dragStartAngleX;
        private float dragStartAngleY;
        private boolean touchMoved;
        private ValueAnimator dragReleaseAnimator;

        WalletCardTextureView(
                Context context,
                WalletCardView pressTarget,
                int cardIconResId,
                int qrButtonRightPadding
        ) {
            super(context, GLIconRenderer.FRAGMENT_STYLE);
            this.pressTarget = pressTarget;
            clickRunnable = pressTarget::performClick;
            renderer = new WalletCardGLRenderer(
                    context,
                    cardIconResId,
                    qrButtonRightPadding
            );
            setRenderer(renderer);
            setOpaque(false);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        protected int getMaxFrameRate() {
            return 60;
        }

        @Override
        public void setPaused(boolean paused) {
            renderer.setLightOrbitPaused(paused);
            super.setPaused(paused);
        }

        @Override
        protected boolean preferGles3() {
            return true;
        }

        @Override
        protected boolean renderFirstFrameWhilePaused() {
            // Prepare the loading-card replacement during the window transition.
            // Subsequent frames still pause when an alert takes focus.
            return true;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            renderer.frameDrawn = false;
            pressTarget.showLoadingCard();
            super.onSurfaceTextureAvailable(surface, width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            pressTarget.showLoadingCard();
            return super.onSurfaceTextureDestroyed(surface);
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            super.onSurfaceTextureUpdated(surface);
            final long revision = renderer.presentedFrontRevision(surface.getTimestamp());
            if (revision >= pressTarget.loadingFrontRevision && pressTarget.showingLoadingCard) {
                pressTarget.showingLoadingCard = false;
                setAlpha(1f);
                pressTarget.loadingCard.setVisibility(INVISIBLE);
                // Re-record spans that switch from the loading artwork to GL.
                pressTarget.frontFace.contentDirty = true;
                pressTarget.frontFace.drawContentInView = false;
                pressTarget.frontFace.invalidate();
                pressTarget.invalidate();
            }
            if (pressTarget.onFrontContentPresented != null
                    && revision >= pressTarget.awaitedFrontRevision) {
                Runnable callback = pressTarget.onFrontContentPresented;
                pressTarget.onFrontContentPresented = null;
                callback.run();
            }
        }

        @Override
        protected void startIdleAnimation() {
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelRotationAnimations();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    touchDownX = event.getX();
                    touchDownY = event.getY();
                    dragStartAngleX = renderer.angleX;
                    dragStartAngleY = renderer.angleY;
                    touchMoved = false;
                    pressTarget.setPressed(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getX() - touchDownX) > touchSlop
                            || Math.abs(event.getY() - touchDownY) > touchSlop) {
                        touchMoved = true;
                        pressTarget.setPressed(false);
                    }
                    if (touchMoved) {
                        renderer.angleX = clamp(
                                dragStartAngleX
                                        - (event.getX() - touchDownX)
                                        * DRAG_X_SENSITIVITY,
                                dragStartAngleX - MAX_DRAG_Y_ROTATION,
                                dragStartAngleX + MAX_DRAG_Y_ROTATION
                        );
                        renderer.angleY = clamp(
                                dragStartAngleY
                                        - (event.getY() - touchDownY)
                                        * DRAG_Y_SENSITIVITY,
                                dragStartAngleY - MAX_DRAG_X_ROTATION,
                                dragStartAngleY + MAX_DRAG_X_ROTATION
                        );
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    pressTarget.setPressed(false);
                    if (touchMoved) {
                        animateBackFromDrag();
                    } else {
                        long pressElapsed = event.getEventTime() - event.getDownTime();
                        postDelayed(
                                clickRunnable,
                                Math.max(0L, PRESS_ANIMATION_DURATION - pressElapsed)
                        );
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    pressTarget.setPressed(false);
                    if (touchMoved) {
                        animateBackFromDrag();
                    }
                    touchMoved = true;
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        private void animateBackFromDrag() {
            cancelRotationAnimations();
            float fromAngleX = renderer.angleX;
            float fromAngleY = renderer.angleY;
            dragReleaseAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
            dragReleaseAnimator.setDuration(600L);
            dragReleaseAnimator.setInterpolator(new OvershootInterpolator(1.2f));
            dragReleaseAnimator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                renderer.angleX = fromAngleX * (1.0f - progress);
                renderer.angleY = fromAngleY * (1.0f - progress);
            });
            dragReleaseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    renderer.angleX = 0.0f;
                    renderer.angleY = 0.0f;
                    dragReleaseAnimator = null;
                }
            });
            dragReleaseAnimator.start();
        }

        private void cancelRotationAnimations() {
            if (dragReleaseAnimator != null) {
                dragReleaseAnimator.removeAllListeners();
                dragReleaseAnimator.cancel();
                dragReleaseAnimator = null;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(clickRunnable);
            pressTarget.setPressed(false);
            cancelRotationAnimations();
            super.onDetachedFromWindow();
        }
    }

    private static class ProjectedFaceLayout extends FrameLayout {
        private static final float DESIGN_WIDTH = 336.0f;
        private static final float DESIGN_HEIGHT = 205.0f;

        private final Matrix projection = new Matrix();
        private final float[] sourceCorners = new float[8];
        private final FrameLayout content;
        private final FrameLayout cachedFace;
        private boolean contentDirty = true;
        private boolean drawContentInView = true;

        ProjectedFaceLayout(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);

            // Cache at the face's actual pixel size, after design-space scaling
            // but before perspective. HWUI otherwise reuploads glyph textures
            // on every rotated frame; caching the smaller design layout also
            // softens text when the card is larger than that layout.
            cachedFace = new FrameLayout(context) {
                private final Path clipPath = new Path();

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    // Cache the rounded mask together with the text, before perspective.
                    int save = canvas.save();
                    WalletCardGeometry.setFacePath(clipPath, getWidth(), getHeight());
                    canvas.clipPath(clipPath);
                    super.dispatchDraw(canvas);
                    canvas.restoreToCount(save);
                }
            };
            cachedFace.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            addView(cachedFace, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            content = new FrameLayout(context);
            content.setPivotX(0);
            content.setPivotY(0);
            cachedFace.addView(content, new FrameLayout.LayoutParams(
                    dp(DESIGN_WIDTH),
                    dp(DESIGN_HEIGHT)
            ));
        }

        FrameLayout getContent() {
            return content;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            contentDirty = true;
            content.setScaleX(w / (float) dp(DESIGN_WIDTH));
            content.setScaleY(h / (float) dp(DESIGN_HEIGHT));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            // Also catches child insertion/removal (e.g. balance reparenting).
            contentDirty = true;
        }

        @Override
        public void onDescendantInvalidated(View child, View target) {
            contentDirty = true;
            super.onDescendantInvalidated(child, target);
        }

        @Override
        public ViewParent invalidateChildInParent(int[] location, Rect dirty) {
            contentDirty = true;
            return super.invalidateChildInParent(location, dirty);
        }

        void captureContent(WalletCardGLRenderer renderer) {
            if (!contentDirty || getWidth() == 0 || getHeight() == 0) return;
            // Clear before drawing: animated drawables may invalidate again.
            contentDirty = false;
            Bitmap bitmap = renderer.acquireFrontBitmap(getWidth(), getHeight());
            bitmap.eraseColor(Color.TRANSPARENT);
            Canvas canvas = new Canvas(bitmap);
            cachedFace.draw(canvas);
            canvas.setBitmap(null);
            renderer.submitFrontBitmap(bitmap);
        }

        void setProjectedCorners(float[] corners) {
            int width = getWidth();
            int height = getHeight();
            if (width == 0 || height == 0) {
                return;
            }
            sourceCorners[0] = 0;
            sourceCorners[1] = 0;
            sourceCorners[2] = width;
            sourceCorners[3] = 0;
            sourceCorners[4] = width;
            sourceCorners[5] = height;
            sourceCorners[6] = 0;
            sourceCorners[7] = height;
            projection.setPolyToPoly(
                    sourceCorners,
                    0,
                    corners,
                    0,
                    4
            );
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (!drawContentInView) return;
            int save = canvas.save();
            canvas.concat(projection);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
        }
    }
}
