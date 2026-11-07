package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

public class WalletCard2DView extends FrameLayout implements SensorEventListener {

    static final Object BACK_PATTERN = new Object();
    private boolean backFacing;

    boolean isBackFacing() { return backFacing; }

    private static final float CARD_ASPECT = 1.64f;
    private static final float MAX_LIGHT_TILT = 0.82f;
    private static final float MAX_CARD_ROTATION_DEGREES = 15f;
    private static final float ROTATION_SMOOTHING = 1.0f;
    private static final float GYROSCOPE_SENSITIVITY = -0.75f;
    private static final float GYRO_HORIZONTAL_TILT_MULTIPLIER = 1.5f;
    private static final float DRAG_X_SENSITIVITY = 0.03f;
    private static final float DRAG_Y_SENSITIVITY = DRAG_X_SENSITIVITY;
    private static final float MAX_DRAG_X_ROTATION = 22.0f;
    private static final float MAX_DRAG_Y_ROTATION = 32.0f;
    private static final long PRESS_ANIMATION_DURATION = 80L;
    private static final int DEFAULT_QR_BUTTON_RIGHT_PADDING = 46;

    void setStickerStyle(float[] style) {
        gradientSurface.style = style;
        projectedCard.invalidate();
        gradientSurface.invalidate();
        cardSurface.invalidate();
    }

    private WalletCard2DMaterial material;
    private int requestedCardIcon;
    private Runnable onMaterialReady;

    void whenMaterialReady(Runnable action) {
        if (material != null) action.run();
        else onMaterialReady = action;
    }

    private final boolean externallyControlled;
    private final ProjectedCardLayout projectedCard;
    private final FaceLayout faceContent;
    private final GradientSurface gradientSurface;
    private final CardSurface cardSurface;
    private final FrameLayout frontFace;
    private final WalletCardProjection faceProjection =
            new WalletCardProjection();
    private final float[] projectedCorners = new float[8];
    private final SensorManager sensorManager;
    private final Sensor motionSensor;
    private final boolean usesRotationVector;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];
    private final WalletCardTiltBaseline tiltBaseline =
            new WalletCardTiltBaseline(MAX_CARD_ROTATION_DEGREES);
    private final int touchSlop;

    private float lightDirectionX = -0.22f;
    private float lightDirectionY = -0.28f;
    private float sensorRotationX;
    private float sensorRotationY;
    private float appliedSensorRotationX;
    private float appliedSensorRotationY;
    private float dragRotationX;
    private float dragRotationY;
    private int activeDragPointer = MotionEvent.INVALID_POINTER_ID;
    private float touchDownX;
    private float touchDownY;
    private float dragStartRotationX;
    private float dragStartRotationY;
    private boolean touchMoved;
    private long lastGyroscopeTimestamp;
    private boolean cardAttached, trackingMotion;
    private ValueAnimator dragReleaseAnimator;
    private float currentRotationX;
    private float additionalTilt;
    private float gyroscopeTiltScale = 1.0f;
    private float currentRotationY;
    private float renderedX = Float.NaN, renderedY = Float.NaN;
    private int renderedWidth, renderedHeight;

    private final Runnable transformUpdater = new Runnable() {
        @Override
        public void run() {
            if (!trackingMotion) {
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
            applyRotation(
                    appliedSensorRotationX * gyroscopeTiltScale + dragRotationX + additionalTilt,
                    appliedSensorRotationY * gyroscopeTiltScale + dragRotationY
            );
            postOnAnimation(this);
        }
    };

    public WalletCard2DView(Context context) {
        this(context, org.telegram.messenger.R.drawable.wallet_card_qr);
    }

    public WalletCard2DView(Context context, int cardIconResId) {
        this(context, cardIconResId, DEFAULT_QR_BUTTON_RIGHT_PADDING);
    }

    public WalletCard2DView(Context context, int cardIconResId, int qrButtonRightPadding) {
        this(context, cardIconResId, qrButtonRightPadding, false);
    }

    WalletCard2DView(Context context, int cardIconResId, int qrButtonRightPadding, boolean externallyControlled) {
        super(context);
        this.externallyControlled = externallyControlled;
        requestedCardIcon = cardIconResId;
        setClipChildren(false);
        setClipToPadding(false);
        setWillNotDraw(false);
        setCameraDistance(dp(4800));
        ScaleStateListAnimator.apply(this, 0.02f, 1.2f);

        projectedCard = new ProjectedCardLayout(context);
        addView(projectedCard, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.CENTER
        ));

        // Card-space layer: clipping before the perspective keeps Skia from rasterizing
        // software clip masks on every tilt. The transparent border keeps the projected
        // layer edges filtered instead of aliased.
        faceContent = new FaceLayout(context);
        faceContent.setPadding(FACE_LAYER_PADDING, FACE_LAYER_PADDING, FACE_LAYER_PADDING, FACE_LAYER_PADDING);
        faceContent.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        FrameLayout.LayoutParams faceParams = new FrameLayout.LayoutParams(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
        faceParams.setMargins(-FACE_LAYER_PADDING, -FACE_LAYER_PADDING, -FACE_LAYER_PADDING, -FACE_LAYER_PADDING);
        projectedCard.addView(faceContent, faceParams);

        gradientSurface = new GradientSurface(context);
        faceContent.addView(gradientSurface, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.CENTER
        ));

        cardSurface = new CardSurface(context, cardIconResId, qrButtonRightPadding);
        faceContent.addView(cardSurface, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.CENTER
        ));

        frontFace = new FrameLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (backFacing && child.getTag() != BACK_PATTERN) return false;
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                return backFacing || super.onInterceptTouchEvent(event);
            }
        };
        frontFace.setClipChildren(false);
        frontFace.setClipToPadding(false);
        // Rasterize text/shadows in card space, before the parent's perspective.
        // Otherwise Skia falls back to drawing glyph paths and blurred masks on
        // every tilt. Content invalidations still refresh this hardware layer.
        frontFace.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        frontFace.setPivotX(0.0f);
        frontFace.setPivotY(0.0f);
        faceContent.addView(frontFace, LayoutHelper.createFrame(
                336,
                205,
                Gravity.LEFT | Gravity.TOP
        ));

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        sensorManager = (SensorManager) context.getSystemService(
                Context.SENSOR_SERVICE
        );
        Sensor selectedSensor = sensorManager.getDefaultSensor(
                Sensor.TYPE_GAME_ROTATION_VECTOR
        );
        if (selectedSensor == null) {
            selectedSensor = sensorManager.getDefaultSensor(
                    Sensor.TYPE_ROTATION_VECTOR
            );
        }
        if (selectedSensor == null) {
            selectedSensor = sensorManager.getDefaultSensor(
                    Sensor.TYPE_GYROSCOPE
            );
        }
        motionSensor = selectedSensor;
        usesRotationVector = motionSensor != null
                && motionSensor.getType() != Sensor.TYPE_GYROSCOPE;
        Runnable redrawMaterial = () -> {
            gradientSurface.invalidate();
            cardSurface.invalidate();
            projectedCard.invalidate();
        };
        if (WalletCard2DMaterial.isPrepared(cardIconResId)) {
            material = new WalletCard2DMaterial(context.getApplicationContext(), cardIconResId,
                    qrButtonRightPadding, redrawMaterial);
        } else {
            // Publish the complete material together; never reveal a flat face or missing QR layers.
            projectedCard.setVisibility(INVISIBLE);
            WalletCard2DMaterial.ASSET_QUEUE.postRunnable(() -> {
                WalletCard2DMaterial ready = new WalletCard2DMaterial(context.getApplicationContext(),
                        cardIconResId, qrButtonRightPadding, redrawMaterial);
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                    material = ready;
                    material.setIcon(requestedCardIcon);
                    renderedX = Float.NaN;
                    applyRotation(currentRotationX, currentRotationY);
                    projectedCard.setVisibility(VISIBLE);
                    redrawMaterial.run();
                    if (onMaterialReady != null) {
                        Runnable action = onMaterialReady;
                        onMaterialReady = null;
                        action.run();
                    }
                });
            });
        }
    }

    /** Maps the face layer's coordinates into projectedCard's. */
    void getFrontFaceProjection(Matrix result) {
        result.set(projectedCard.projection);
        result.preTranslate(faceContent.getLeft(), faceContent.getTop());
    }

    public FrameLayout getFrontFace() {
        return frontFace;
    }

    public void setAdditionalTilt(float tilt) {
        if (additionalTilt == tilt) return;
        final float rotationX = currentRotationX + tilt - additionalTilt;
        additionalTilt = tilt;
        applyRotation(rotationX, currentRotationY);
    }

    public void setUseGyroscope(float t) {
        if (gyroscopeTiltScale == t) return;
        gyroscopeTiltScale = t;
        applyRotation(
                appliedSensorRotationX * gyroscopeTiltScale + dragRotationX + additionalTilt,
                appliedSensorRotationY * gyroscopeTiltScale + dragRotationY
        );
    }

    public float getCardRotationX() {
        return currentRotationX;
    }

    public float getCardRotationY() {
        return currentRotationY;
    }

    public void setHasCardNumber(boolean has) {
        cardSurface.setHasCardNumber(has);
    }

    public void setCardIcon(int drawableResId) {
        cardSurface.setCardIcon(drawableResId);
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
        frontFace.setScaleX(w / (float) dp(336));
        frontFace.setScaleY(h / (float) dp(205));
        applyRotation(currentRotationX, currentRotationY);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (externallyControlled) return;
        cardAttached = true;
        lastGyroscopeTimestamp = 0L;
        tiltBaseline.reset();
        sensorRotationX = 0.0f;
        sensorRotationY = 0.0f;
        appliedSensorRotationX = 0.0f;
        appliedSensorRotationY = 0.0f;
        updateMotionTracking();
    }

    @Override
    protected void onDetachedFromWindow() {
        cardAttached = false;
        updateMotionTracking();
        cancelDragRelease();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateMotionTracking();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateMotionTracking();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        updateMotionTracking();
    }

    private void updateMotionTracking() {
        // Visibility callbacks can arrive while the View constructor is running.
        if (sensorManager == null || externallyControlled) return;
        boolean active = cardAttached && isShown()
                && getWindowVisibility() == VISIBLE && hasWindowFocus();
        if (trackingMotion == active) return;
        trackingMotion = active;
        removeCallbacks(transformUpdater);
        if (active) {
            // Keep the established neutral pose across focus/visibility changes,
            // but never integrate raw gyro samples across the paused interval.
            lastGyroscopeTimestamp = 0L;
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
            if (Float.isNaN(orientation[1]) || Float.isNaN(orientation[2])) return; // asin() out of range at ±90° pitch
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (externallyControlled) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelDragRelease();
                getParent().requestDisallowInterceptTouchEvent(true);
                rebaseDrag(event, 0);
                touchMoved = false;
                setPressed(true);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // A fresh finger takes over at the current pose, extending the drag range.
                rebaseDrag(event, event.getActionIndex());
                touchMoved = true;
                setPressed(false);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == activeDragPointer) {
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        if (i != event.getActionIndex()) {
                            rebaseDrag(event, i);
                            break;
                        }
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                int pointerIndex = event.findPointerIndex(activeDragPointer);
                if (pointerIndex < 0) return true;
                float pointerX = event.getX(pointerIndex), pointerY = event.getY(pointerIndex);
                if (Math.abs(pointerX - touchDownX) > touchSlop
                        || Math.abs(pointerY - touchDownY) > touchSlop) {
                    touchMoved = true;
                    setPressed(false);
                }
                if (touchMoved) {
                    dragRotationY = clamp(
                            dragStartRotationY
                                    - (pointerX - touchDownX)
                                    * DRAG_X_SENSITIVITY,
                            dragStartRotationY - MAX_DRAG_Y_ROTATION,
                            dragStartRotationY + MAX_DRAG_Y_ROTATION
                    );
                    dragRotationX = clamp(
                            dragStartRotationX
                                    - (pointerY - touchDownY)
                                    * DRAG_Y_SENSITIVITY,
                            dragStartRotationX - MAX_DRAG_X_ROTATION,
                            dragStartRotationX + MAX_DRAG_X_ROTATION
                    );
                }
                return true;
            case MotionEvent.ACTION_UP:
                activeDragPointer = MotionEvent.INVALID_POINTER_ID;
                getParent().requestDisallowInterceptTouchEvent(false);
                setPressed(false);
                if (touchMoved) {
                    animateBackFromDrag();
                } else {
                    long elapsed = event.getEventTime() - event.getDownTime();
                    postDelayed(
                            this::performClick,
                            Math.max(0L, PRESS_ANIMATION_DURATION - elapsed)
                    );
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                activeDragPointer = MotionEvent.INVALID_POINTER_ID;
                getParent().requestDisallowInterceptTouchEvent(false);
                setPressed(false);
                if (touchMoved) {
                    animateBackFromDrag();
                }
                return true;
            default:
                return true;
        }
    }

    private void rebaseDrag(MotionEvent event, int index) {
        activeDragPointer = event.getPointerId(index);
        touchDownX = event.getX(index);
        touchDownY = event.getY(index);
        dragStartRotationX = dragRotationX;
        dragStartRotationY = dragRotationY;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float stickerKickX, stickerKickY;

    /** Extra tilt in degrees from a sticker landing on the card. */
    void setStickerKick(float x, float y) {
        if (stickerKickX == x && stickerKickY == y) return;
        stickerKickX = x;
        stickerKickY = y;
        applyRotation(currentRotationX, currentRotationY);
    }

    void applyRotation(float rotationX, float rotationY) {
        currentRotationX = rotationX;
        currentRotationY = rotationY;
        rotationX += stickerKickX;
        rotationY += stickerKickY;
        // The transformed face normal points away after either axis turns through 90 degrees.
        boolean back = Math.cos(Math.toRadians(rotationX)) * Math.cos(Math.toRadians(rotationY)) < 0;
        if (backFacing != back) {
            backFacing = back;
            frontFace.invalidate();
            cardSurface.invalidate();
        }
        int width = getWidth();
        int height = getHeight();
        if (renderedX == rotationX && renderedY == rotationY
                && renderedWidth == width && renderedHeight == height) return;
        renderedX = rotationX; renderedY = rotationY;
        renderedWidth = width; renderedHeight = height;
        if (width > 0 && height > 0) {
            faceProjection.project(
                    width,
                    height,
                    rotationX,
                    rotationY,
                    back,
                    projectedCorners
            );
            if (material != null) material.updateDepthProjection(faceProjection, width, height, back);
            projectedCard.setProjectedCorners(projectedCorners);
        }
        if (material != null) material.setRotation(rotationX, rotationY);
        gradientSurface.setTilt(rotationX, rotationY);
        cardSurface.setTilt(rotationX, rotationY);
    }

    private void animateBackFromDrag() {
        cancelDragRelease();
        float fromX = dragRotationX;
        float fromY = dragRotationY;
        dragReleaseAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        dragReleaseAnimator.setDuration(600L);
        dragReleaseAnimator.setInterpolator(new OvershootInterpolator(1.2f));
        dragReleaseAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            dragRotationX = fromX * (1.0f - progress);
            dragRotationY = fromY * (1.0f - progress);
        });
        dragReleaseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                dragRotationX = 0.0f;
                dragRotationY = 0.0f;
                dragReleaseAnimator = null;
            }
        });
        dragReleaseAnimator.start();
    }

    private void cancelDragRelease() {
        if (dragReleaseAnimator != null) {
            dragReleaseAnimator.removeAllListeners();
            dragReleaseAnimator.cancel();
            dragReleaseAnimator = null;
        }
    }

    private void updateFromRawGyroscope(SensorEvent event) {
        if (lastGyroscopeTimestamp != 0L) {
            float seconds = (event.timestamp - lastGyroscopeTimestamp)
                    * 0.000000001f;
            if (seconds > 0.0f && seconds < 0.1f) {
                lightDirectionX = clamp(
                        lightDirectionX
                                - event.values[1] * seconds
                                * GYROSCOPE_SENSITIVITY,
                        -MAX_LIGHT_TILT,
                        MAX_LIGHT_TILT
                );
                lightDirectionY = clamp(
                        lightDirectionY
                                + event.values[0] * seconds
                                * GYROSCOPE_SENSITIVITY,
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

    private final class ProjectedCardLayout extends FrameLayout {
        private final Matrix projection = new Matrix();
        private boolean validProjection;
        private final float[] sourceCorners = new float[8];

        ProjectedCardLayout(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            renderedWidth = -1;
            applyRotation(currentRotationX, currentRotationY);
        }

        void setProjectedCorners(float[] corners) {
            int width = getWidth();
            int height = getHeight();
            if (width == 0 || height == 0) {
                return;
            }
            sourceCorners[0] = 0.0f;
            sourceCorners[1] = 0.0f;
            sourceCorners[2] = width;
            sourceCorners[3] = 0.0f;
            sourceCorners[4] = width;
            sourceCorners[5] = height;
            sourceCorners[6] = 0.0f;
            sourceCorners[7] = height;
            validProjection = projection.setPolyToPoly(
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
            // Depth is already projected in screen space, independently of the front face.
            if (material != null) material.drawDepth(canvas, gradientSurface.style);
            if (!validProjection) return;
            int save = canvas.save();
            canvas.concat(projection);
            // Project the cached material and live content together.
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
        }
    }

    private static final int FACE_LAYER_PADDING = 2;

    private static final class FaceLayout extends FrameLayout {
        private final Path facePath = new Path();

        FaceLayout(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            WalletCardGeometry.setFacePath(facePath, w - getPaddingLeft() - getPaddingRight(),
                    h - getPaddingTop() - getPaddingBottom());
            facePath.offset(getPaddingLeft(), getPaddingTop());
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int save = canvas.save();
            canvas.clipPath(facePath);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
        }
    }

    private final class GradientSurface extends View {
        private float[] style;
        GradientSurface(Context context) { super(context); }
        void setTilt(float x, float y) { invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int save = canvas.save();
            canvas.scale(getWidth() / 336f, getHeight() / 205f);
            if (material != null) material.drawSurface(canvas, style);
            else canvas.drawColor(WalletGiftPalette.card2DEdgeTone(0xff088fee, style));
            canvas.restoreToCount(save);
        }
    }

    private final class CardSurface extends View {
        private boolean hasCardNumber;
        CardSurface(Context context, int icon, int rightPadding) {
            super(context);
        }
        void setCardIcon(int icon) { requestedCardIcon = icon; if (material != null) material.setIcon(icon); invalidate(); }
        void setHasCardNumber(boolean has) { hasCardNumber = has; invalidate(); }
        void setTilt(float x, float y) { invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int save = canvas.save();
            canvas.scale(getWidth() / 336f, getHeight() / 205f);
            if (material != null) material.drawStars(canvas, !backFacing && hasCardNumber);
            if (material != null && !backFacing) material.drawQr(canvas);
            if (material != null) material.drawRim(canvas);
            canvas.restoreToCount(save);
        }
    }
}
