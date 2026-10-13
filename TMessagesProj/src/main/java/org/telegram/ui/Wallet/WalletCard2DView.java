package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.Random;

public class WalletCard2DView extends FrameLayout implements SensorEventListener {

    private static final float CARD_ASPECT = 1.64f;
    private static final float MAX_LIGHT_TILT = 0.82f;
    private static final float MAX_CARD_ROTATION_DEGREES = 15f;
    private static final float ROTATION_SMOOTHING = 1.0f;
    private static final float GYROSCOPE_SENSITIVITY = 0.75f;
    private static final float DRAG_X_SENSITIVITY = 0.03f;
    private static final float DRAG_Y_SENSITIVITY = DRAG_X_SENSITIVITY;
    private static final float MAX_DRAG_X_ROTATION = 22.0f;
    private static final float MAX_DRAG_Y_ROTATION = 32.0f;
    private static final long PRESS_ANIMATION_DURATION = 80L;
    private static final int DEFAULT_QR_BUTTON_RIGHT_PADDING = 45;

    private final boolean externallyControlled;
    private final ProjectedCardLayout projectedCard;
    private final GradientSurface gradientSurface;
    private final CardSurface cardSurface;
    private final FrameLayout frontFace;
    private final WalletCardGLRenderer.FaceProjection faceProjection =
            new WalletCardGLRenderer.FaceProjection();
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
    private float touchDownX;
    private float touchDownY;
    private float dragStartRotationX;
    private float dragStartRotationY;
    private boolean touchMoved;
    private long lastGyroscopeTimestamp;
    private ValueAnimator dragReleaseAnimator;
    private float currentRotationX;
    private float additionalTilt;
    private float gyroscopeTiltScale = 1.0f;
    private float currentRotationY;

    private final Runnable transformUpdater = new Runnable() {
        @Override
        public void run() {
            if (!isAttachedToWindow()) {
                return;
            }
            tiltBaseline.update(System.nanoTime());
            sensorRotationX = tiltBaseline.getRotationX();
            sensorRotationY = tiltBaseline.getRotationY();
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

        gradientSurface = new GradientSurface(context);
        projectedCard.addView(gradientSurface, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.CENTER
        ));

        cardSurface = new CardSurface(context, cardIconResId, qrButtonRightPadding);
        projectedCard.addView(cardSurface, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.CENTER
        ));

        frontFace = new FrameLayout(context);
        frontFace.setClipChildren(false);
        frontFace.setClipToPadding(false);
        frontFace.setPivotX(0.0f);
        frontFace.setPivotY(0.0f);
        projectedCard.addView(frontFace, LayoutHelper.createFrame(
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
    }

    void getFrontFaceProjection(Matrix result) {
        result.set(projectedCard.projection);
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

    public void setEngravingBitmap(Bitmap bitmap) {
        cardSurface.setEngravingBitmap(bitmap);
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
        lastGyroscopeTimestamp = 0L;
        tiltBaseline.reset();
        sensorRotationX = 0.0f;
        sensorRotationY = 0.0f;
        appliedSensorRotationX = 0.0f;
        appliedSensorRotationY = 0.0f;
        if (motionSensor != null) {
            sensorManager.registerListener(
                    this,
                    motionSensor,
                    SensorManager.SENSOR_DELAY_GAME
            );
        }
        removeCallbacks(transformUpdater);
        postOnAnimation(transformUpdater);
    }

    @Override
    protected void onDetachedFromWindow() {
        sensorManager.unregisterListener(this);
        removeCallbacks(transformUpdater);
        cancelDragRelease();
        super.onDetachedFromWindow();
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
                touchDownX = event.getX();
                touchDownY = event.getY();
                dragStartRotationX = dragRotationX;
                dragStartRotationY = dragRotationY;
                touchMoved = false;
                setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - touchDownX) > touchSlop
                        || Math.abs(event.getY() - touchDownY) > touchSlop) {
                    touchMoved = true;
                    setPressed(false);
                }
                if (touchMoved) {
                    dragRotationY = clamp(
                            dragStartRotationY
                                    - (event.getX() - touchDownX)
                                    * DRAG_X_SENSITIVITY,
                            dragStartRotationY - MAX_DRAG_Y_ROTATION,
                            dragStartRotationY + MAX_DRAG_Y_ROTATION
                    );
                    dragRotationX = clamp(
                            dragStartRotationX
                                    - (event.getY() - touchDownY)
                                    * DRAG_Y_SENSITIVITY,
                            dragStartRotationX - MAX_DRAG_X_ROTATION,
                            dragStartRotationX + MAX_DRAG_X_ROTATION
                    );
                }
                return true;
            case MotionEvent.ACTION_UP:
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

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    void applyRotation(float rotationX, float rotationY) {
        currentRotationX = rotationX;
        currentRotationY = rotationY;
        int width = getWidth();
        int height = getHeight();
        if (width > 0 && height > 0) {
            faceProjection.project(
                    width,
                    height,
                    rotationX,
                    rotationY,
                    false,
                    projectedCorners
            );
            projectedCard.setProjectedCorners(projectedCorners);
        }
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

    private static final class ProjectedCardLayout extends FrameLayout {
        private final Matrix projection = new Matrix();
        private final Path facePath = new Path();
        private final float[] sourceCorners = new float[8];

        ProjectedCardLayout(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
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
            int save = canvas.save();
            canvas.concat(projection);
            // Clip the whole face, including the blurred gradient and overlays.
            WalletCardGeometry.setFacePath(facePath, getWidth(), getHeight());
            canvas.clipPath(facePath);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
        }
    }

    private static final class GradientSurface extends View {
        private static final int BASE_BLUE = 0xFF088FEE;
        private static final int LIGHT_BLUE = 0xFF16ABF9;
        private static final float CSS_GRADIENT_ANGLE = 20.99f;

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix gradientMatrix = new Matrix();
        private final Path outlinePath = new Path();
        private SweepGradient gradient;
        private float gradientCenterX;
        private float gradientCenterY;

        GradientSurface(Context context) {
            super(context);
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    WalletCardGeometry.setFacePath(outlinePath, view.getWidth(), view.getHeight());
                    outline.setConvexPath(outlinePath);
                }
            });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(
                        dp(20),
                        dp(20),
                        Shader.TileMode.CLAMP
                ));
            }
        }

        void setTilt(float rotationX, float rotationY) {
            if (gradient == null) {
                return;
            }
            float reflectionRotation = rotationY * 2.0f - rotationX * 1.25f;
            gradientMatrix.setRotate(
                    CSS_GRADIENT_ANGLE - 90.0f + reflectionRotation,
                    gradientCenterX,
                    gradientCenterY
            );
            gradient.setLocalMatrix(gradientMatrix);
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            gradientCenterX = w * 0.5f;
            gradientCenterY = h * 0.5f;
            gradient = new SweepGradient(
                    gradientCenterX,
                    gradientCenterY,
                    new int[]{
                            BASE_BLUE,
                            LIGHT_BLUE,
                            BASE_BLUE,
                            LIGHT_BLUE,
                            BASE_BLUE
                    },
                    new float[]{0.0f, 0.25f, 0.5f, 0.75f, 1.0f}
            );
            gradientMatrix.setRotate(
                    CSS_GRADIENT_ANGLE - 90.0f,
                    gradientCenterX,
                    gradientCenterY
            );
            gradient.setLocalMatrix(gradientMatrix);
            backgroundPaint.setShader(gradient);
            invalidateOutline();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRect(0.0f, 0.0f, getWidth(), getHeight(), backgroundPaint);
        }
    }

    private static final class CardSurface extends View {
        private static final int FLECK_COUNT = 16;
        private static final float FLECK_MIN_RADIUS = 1.5f;
        private static final float FLECK_MAX_RADIUS = 5.0f;
        private static final float FLECK_MIN_TILT = 0.16f;
        private static final float FLECK_MAX_TILT = 0.64f;
        private static final float FLECK_GRADIENT_SPREAD = 0.08f;
        private static final float FLECK_SPECULAR_POWER = 6.0f;
        private static final float FLECK_BRIGHT_SIDE_ANGLE = 20.99f;
        private static final long FLECK_RANDOM_SEED = 0x3244464C45434B53L;
        private static final float QR_BUTTON_DESIGN_WIDTH = 336.0f;
        private static final float QR_BUTTON_WIDTH = 50.0f;
        private static final float QR_BUTTON_TOP = 73.0f;
        private static final float QR_BUTTON_BOTTOM = 111.0f;
        private static final float QR_ICON_INSET_X = 13.0f;
        private static final float QR_ICON_INSET_Y = 7.0f;
        private static final float QR_BUTTON_CORNER_RADIUS = 9.0f;

        private final Paint insetShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fleckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint engravingPaint = new Paint(
                Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
        );
        private final Paint engravingHighlightPaint = new Paint(
                Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
        );
        private final Paint qrLinearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint qrAngularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint qrBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint qrIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint qrIconHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final RectF qrBounds = new RectF();
        private final Matrix qrGradientMatrix = new Matrix();
        private Drawable cardIcon;
        private final Path fleckPath = new Path();
        private final Fleck[] flecks = createFlecks();
        private Bitmap engravingBitmap;
        private SweepGradient qrAngularGradient;
        private float rotationX;
        private float rotationY;

        CardSurface(Context context, int cardIconResId, int qrButtonRightPadding) {
            super(context);
            engravingPaint.setColorFilter(new PorterDuffColorFilter(
                    0xFF0071CC,
                    PorterDuff.Mode.SRC_IN
            ));
            engravingHighlightPaint.setColorFilter(new PorterDuffColorFilter(
                    Color.argb(15, 255, 255, 255),
                    PorterDuff.Mode.SRC_IN
            ));
            qrIconPaint.setColor(0xFF737B80);
            qrIconHighlightPaint.setColor(Color.WHITE);
            qrBorderPaint.setColor(0x29000000);
            qrBorderPaint.setStyle(Paint.Style.STROKE);
            qrBorderPaint.setStrokeWidth(1.0f);
            insetShadowPaint.setColor(0x24000000);
            insetShadowPaint.setStyle(Paint.Style.STROKE);
            insetShadowPaint.setStrokeWidth(dp(1));
            insetShadowPaint.setMaskFilter(new BlurMaskFilter(
                    dp(1),
                    BlurMaskFilter.Blur.NORMAL
            ));
            float buttonLeft = QR_BUTTON_DESIGN_WIDTH
                    - qrButtonRightPadding - QR_BUTTON_WIDTH;
            float buttonRight = QR_BUTTON_DESIGN_WIDTH - qrButtonRightPadding;
            qrBounds.set(
                    buttonLeft,
                    QR_BUTTON_TOP,
                    buttonRight,
                    QR_BUTTON_BOTTOM
            );
            setCardIcon(cardIconResId);
        }

        void setCardIcon(int drawableResId) {
            cardIcon = ContextCompat.getDrawable(getContext(), drawableResId);
            if (cardIcon != null) {
                cardIcon = cardIcon.mutate();
            }
            invalidate();
        }

        void setEngravingBitmap(Bitmap bitmap) {
            // The component and GL thread may still be using the previous bitmap.
            engravingBitmap = bitmap;
            invalidate();
        }

        void setTilt(float rotationX, float rotationY) {
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            qrLinearPaint.setShader(new LinearGradient(
                    qrBounds.left + 5.2f,
                    qrBounds.top + 3.4f,
                    qrBounds.right - 4.6f,
                    qrBounds.bottom - 4.0f,
                    new int[]{0xE0FDFEFF, 0xE0BBBDC0},
                    new float[]{0.0799f, 0.922f},
                    Shader.TileMode.CLAMP
            ));
            float qrCenterX = qrBounds.centerX();
            float qrCenterY = qrBounds.centerY();
            qrAngularGradient = new SweepGradient(
                    qrCenterX,
                    qrCenterY,
                    new int[]{
                            0x00FFFFFF,
                            0x00FFFFFF,
                            0x5CFFFFFF,
                            0x00FFFFFF,
                            0x00000000,
                            0x1F000000,
                            0x00000000,
                            0x00000000
                    },
                    new float[]{
                            0.0f,
                            0.06f,
                            0.25f,
                            0.44f,
                            0.56f,
                            0.75f,
                            0.94f,
                            1.0f
                    }
            );
            // CSS conic gradients start at twelve o'clock.
            qrGradientMatrix.setRotate(90.0f, qrCenterX, qrCenterY);
            qrAngularGradient.setLocalMatrix(qrGradientMatrix);
            qrAngularPaint.setShader(qrAngularGradient);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            bounds.set(0.0f, 0.0f, getWidth(), getHeight());
            float radiusX = WalletCardGeometry.radiusX(getWidth());
            float radiusY = WalletCardGeometry.radiusY(getHeight());
            Path cardPath = new Path();
            WalletCardGeometry.setFacePath(cardPath, getWidth(), getHeight());
            int backgroundSave = canvas.save();
            canvas.clipPath(cardPath);

            RectF insetBounds = new RectF(bounds);
            insetBounds.inset(dp(0.5f), dp(0.5f));
            int shadowSave = canvas.save();
            canvas.translate(0.0f, -dp(1));
            canvas.drawRoundRect(
                    insetBounds,
                    Math.max(0, radiusX - dp(0.5f)),
                    Math.max(0, radiusY - dp(0.5f)),
                    insetShadowPaint
            );
            canvas.restoreToCount(shadowSave);
            canvas.restoreToCount(backgroundSave);

            int flecksSave = canvas.save();
            canvas.clipPath(cardPath);
            drawFlecks(canvas);
            canvas.restoreToCount(flecksSave);

            if (engravingBitmap != null && !engravingBitmap.isRecycled()) {
                int save = canvas.save();
                canvas.clipRect(bounds);
                canvas.translate(0.0f, dp(1));
                canvas.drawBitmap(
                        engravingBitmap,
                        null,
                        bounds,
                        engravingHighlightPaint
                );
                canvas.restoreToCount(save);
                canvas.drawBitmap(engravingBitmap, null, bounds, engravingPaint);
            }

            drawQrButton(canvas);
        }

        private void drawFlecks(Canvas canvas) {
            float scaleX = getWidth() / 336.0f;
            float scaleY = getHeight() / 205.0f;
            float lightX = 0.08f
                    + (float) Math.sin(Math.toRadians(rotationY)) * 0.72f;
            float lightY = -(float) Math.sin(Math.toRadians(rotationX)) * 0.72f;
            float inverseLightLength = 1.0f / (float) Math.sqrt(
                    lightX * lightX + lightY * lightY + 1.0f
            );
            lightX *= inverseLightLength;
            lightY *= inverseLightLength;
            float lightZ = inverseLightLength;
            float brightSideAngle = (float) Math.toRadians(
                    FLECK_BRIGHT_SIDE_ANGLE
                            + rotationY * 2.0f
                            - rotationX * 1.25f
            );
            float brightSideX = (float) Math.cos(brightSideAngle);
            float brightSideY = (float) Math.sin(brightSideAngle);

            int save = canvas.save();
            canvas.scale(scaleX, scaleY);
            for (Fleck fleck : flecks) {
                float positionX = fleck.x - 168.0f;
                float positionY = fleck.y - 102.5f;
                float inversePositionLength = 1.0f / Math.max(
                        0.001f,
                        (float) Math.sqrt(
                                positionX * positionX
                                        + positionY * positionY
                        )
                );
                float brightAxisAlignment = Math.abs(
                        (positionX * brightSideX
                                + positionY * brightSideY)
                                * inversePositionLength
                );
                float brightSide = smoothstep(
                        0.38f,
                        0.72f,
                        brightAxisAlignment
                );
                float responseA = fleckResponse(
                        fleck.normalX + fleck.gradientX,
                        fleck.normalY + fleck.gradientY,
                        lightX,
                        lightY,
                        lightZ
                ) * brightSide;
                float responseB = fleckResponse(
                        fleck.normalX - fleck.gradientX,
                        fleck.normalY - fleck.gradientY,
                        lightX,
                        lightY,
                        lightZ
                ) * brightSide;
                if (Math.max(responseA, responseB) < 0.015f) {
                    continue;
                }
                int colorA = fleckColor(responseA);
                int colorB = fleckColor(responseB);
                float axisX = fleck.gradientDirectionX * fleck.radius;
                float axisY = fleck.gradientDirectionY * fleck.radius;
                fleckPaint.setShader(new LinearGradient(
                        fleck.x - axisX,
                        fleck.y - axisY,
                        fleck.x + axisX,
                        fleck.y + axisY,
                        colorA,
                        colorB,
                        Shader.TileMode.CLAMP
                ));
                setParticleStarPath(
                        fleckPath,
                        fleck.x,
                        fleck.y,
                        fleck.radius
                );
                canvas.drawPath(fleckPath, fleckPaint);
            }
            fleckPaint.setShader(null);
            canvas.restoreToCount(save);
        }

        private static float fleckResponse(
                float normalX,
                float normalY,
                float lightX,
                float lightY,
                float lightZ
        ) {
            float normalZ = (float) Math.sqrt(Math.max(
                    0.0f,
                    1.0f - normalX * normalX - normalY * normalY
            ));
            float normalLength = (float) Math.sqrt(
                    normalX * normalX + normalY * normalY
                            + normalZ * normalZ
            );
            float normalLight = Math.max(
                    0.0f,
                    (normalX * lightX + normalY * lightY
                            + normalZ * lightZ) / normalLength
            );
            return (float) Math.pow(normalLight, FLECK_SPECULAR_POWER);
        }

        private static int fleckColor(float response) {
            float white = smoothstep(0.78f, 0.96f, response);
            int red = Math.round(135.0f + 120.0f * white);
            int green = Math.round(239.0f + 16.0f * white);
            int blue = 255;
            int alpha = Math.round(145.0f * response);
            return Color.argb(alpha, red, green, blue);
        }

        private static Fleck[] createFlecks() {
            Random random = new Random(FLECK_RANDOM_SEED);
            Fleck[] result = new Fleck[FLECK_COUNT];
            for (int i = 0; i < result.length; i++) {
                Fleck fleck = new Fleck();
                fleck.x = 18.0f + random.nextFloat() * 300.0f;
                fleck.y = 16.0f + random.nextFloat() * 173.0f;
                fleck.radius = FLECK_MIN_RADIUS + random.nextFloat()
                        * (FLECK_MAX_RADIUS - FLECK_MIN_RADIUS);
                float angle = random.nextFloat() * (float) (Math.PI * 2.0);
                float tilt = FLECK_MIN_TILT + random.nextFloat()
                        * (FLECK_MAX_TILT - FLECK_MIN_TILT);
                float directionX = (float) Math.cos(angle);
                float directionY = (float) Math.sin(angle);
                fleck.normalX = directionX * tilt;
                fleck.normalY = directionY * tilt;
                fleck.gradientDirectionX = -directionY;
                fleck.gradientDirectionY = directionX;
                fleck.gradientX = fleck.gradientDirectionX
                        * FLECK_GRADIENT_SPREAD;
                fleck.gradientY = fleck.gradientDirectionY
                        * FLECK_GRADIENT_SPREAD;
                result[i] = fleck;
            }
            return result;
        }

        private static float smoothstep(float low, float high, float value) {
            float progress = clamp((value - low) / (high - low), 0.0f, 1.0f);
            return progress * progress * (3.0f - 2.0f * progress);
        }

        private static void setParticleStarPath(
                Path path,
                float centerX,
                float centerY,
                float radius
        ) {
            float inner = radius * 0.15f;
            path.rewind();
            path.moveTo(centerX - radius, centerY);
            path.lineTo(centerX - inner, centerY - inner);
            path.lineTo(centerX, centerY - radius);
            path.lineTo(centerX + inner, centerY - inner);
            path.lineTo(centerX + radius, centerY);
            path.lineTo(centerX + inner, centerY + inner);
            path.lineTo(centerX, centerY + radius);
            path.lineTo(centerX - inner, centerY + inner);
            path.close();
        }

        private static final class Fleck {
            float x;
            float y;
            float radius;
            float normalX;
            float normalY;
            float gradientX;
            float gradientY;
            float gradientDirectionX;
            float gradientDirectionY;
        }

        private void drawQrButton(Canvas canvas) {
            float scaleX = getWidth() / 336.0f;
            float scaleY = getHeight() / 205.0f;
            int save = canvas.save();
            canvas.scale(scaleX, scaleY);
            canvas.clipPath(roundedRectPath(qrBounds, QR_BUTTON_CORNER_RADIUS));
            canvas.drawRect(qrBounds, qrLinearPaint);
            canvas.drawRect(qrBounds, qrAngularPaint);
            canvas.restoreToCount(save);

            save = canvas.save();
            canvas.scale(scaleX, scaleY);
            RectF borderBounds = new RectF(qrBounds);
            borderBounds.inset(-0.5f, -0.5f);
            canvas.drawRoundRect(
                    borderBounds,
                    QR_BUTTON_CORNER_RADIUS + 0.5f,
                    QR_BUTTON_CORNER_RADIUS + 0.5f,
                    qrBorderPaint
            );
            canvas.restoreToCount(save);

            save = canvas.save();
            canvas.scale(scaleX, scaleY);
            canvas.translate(0.0f, 1.0f);
            drawCardIcon(canvas, qrIconHighlightPaint.getColor());
            canvas.restoreToCount(save);

            save = canvas.save();
            canvas.scale(scaleX, scaleY);
            drawCardIcon(canvas, qrIconPaint.getColor());
            canvas.restoreToCount(save);
        }

        private void drawCardIcon(Canvas canvas, int color) {
            if (cardIcon == null) return;
            cardIcon.setTint(color);
            cardIcon.setBounds(
                    (int) (qrBounds.left + QR_ICON_INSET_X),
                    (int) (qrBounds.top + QR_ICON_INSET_Y),
                    (int) (qrBounds.right - QR_ICON_INSET_X),
                    (int) (qrBounds.bottom - QR_ICON_INSET_Y)
            );
            cardIcon.draw(canvas);
        }

        private static Path roundedRectPath(RectF rect, float radius) {
            Path path = new Path();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            return path;
        }

        private static void buildQrIconPath(Path path) {
            path.setFillType(Path.FillType.EVEN_ODD);
            addQrFinder(path, 15.6951f, 9.6951f);
            addQrFinder(path, 25.9812f, 9.6951f);
            addQrFinder(path, 15.6951f, 19.9812f);
            addQrModule(path, 26.4998f, 20.4998f);
            addQrModule(path, 31.6423f, 20.4998f);
            addQrModule(path, 29.0710f, 23.0710f);
            addQrModule(path, 26.4998f, 25.6423f);
            addQrModule(path, 31.6423f, 25.6423f);
        }

        private static void addQrFinder(Path path, float left, float top) {
            final float offsetX = 241.0f;
            final float offsetY = 73.0f;
            path.addRoundRect(
                    new RectF(
                            offsetX + left,
                            offsetY + top,
                            offsetX + left + 8.3242f,
                            offsetY + top + 8.3242f
                    ),
                    2.44f,
                    2.44f,
                    Path.Direction.CW
            );
            path.addRoundRect(
                    new RectF(
                            offsetX + left + 1.4668f,
                            offsetY + top + 1.4668f,
                            offsetX + left + 6.8574f,
                            offsetY + top + 6.8574f
                    ),
                    0.98f,
                    0.98f,
                    Path.Direction.CCW
            );
        }

        private static void addQrModule(Path path, float left, float top) {
            final float offsetX = 241.0f;
            final float offsetY = 73.0f;
            path.addRoundRect(
                    new RectF(
                            offsetX + left,
                            offsetY + top,
                            offsetX + left + 2.1425f,
                            offsetY + top + 2.1425f
                    ),
                    0.43f,
                    0.43f,
                    Path.Direction.CW
            );
        }
    }
}
