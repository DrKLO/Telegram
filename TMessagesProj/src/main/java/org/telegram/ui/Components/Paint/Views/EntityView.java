package org.telegram.ui.Components.Paint.Views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.Rect;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class EntityView extends FrameLayout {
    private final static List<Integer> STICKY_ANGLES = Arrays.asList(
            -90, 0, 90, 180
    );
    private final static float STICKY_THRESHOLD_ANGLE = 15;
    private final static float STICKY_TRIGGER_ANGLE = 5;

    private final static float STICKY_THRESHOLD_DP = 48;
    private final static float STICKY_TRIGGER_DP = 16;

    public interface EntityViewDelegate {
        boolean onEntitySelected(EntityView entityView);
        boolean onEntityLongClicked(EntityView entityView);
        boolean allowInteraction(EntityView entityView);
        int[] getCenterLocation(EntityView entityView);
        float[] getTransformedTouch(float x, float y);
        float getCropRotation();
    }

    private float previousLocationX;
    private float previousLocationY;
    private boolean hasPanned = false;
    private boolean hasReleased = false;
    private boolean hasTransformed = false;
    private boolean announcedSelection = false;
    private boolean recognizedLongPress = false;

    private EntityViewDelegate delegate;

    private Point position;
    protected SelectionView selectionView;

    private GestureDetector gestureDetector;

    private UUID uuid;

    private boolean hasStickyAngle = true;
    private int currentStickyAngle = 0;

    private float stickyAnimatedAngle;
    private ValueAnimator angleAnimator;

    private float fromStickyAnimatedAngle;
    private float fromStickyToAngle;
    private ValueAnimator fromStickyAngleAnimator;

    private boolean hasStickyX, hasStickyY;
    private float fromStickyX, fromStickyY;
    private ValueAnimator stickyXAnimator, stickyYAnimator;
    private boolean hasFromStickyXAnimation, hasFromStickyYAnimation;

    public EntityView(Context context, Point pos) {
        super(context);

        uuid = UUID.randomUUID();
        position = pos;

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            public void onLongPress(MotionEvent e) {
                if (hasPanned || hasTransformed || hasReleased) {
                    return;
                }

                recognizedLongPress = true;
                if (delegate != null) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    delegate.onEntityLongClicked(EntityView.this);
                }
            }
        });
    }

    public UUID getUUID() {
        return uuid;
    }

    public Point getPosition() {
        return position;
    }

    public void setPosition(Point value) {
        position = value;
        updatePosition();
    }

    public float getScale() {
        return getScaleX();
    }

    public void setScale(float scale) {
        setScaleX(scale);
        setScaleY(scale);
    }

    public void setDelegate(EntityViewDelegate entityViewDelegate) {
        delegate = entityViewDelegate;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return delegate.allowInteraction(this);
    }

    private boolean onTouchMove(float x, float y) {
        if (getParent() == null) {
            return false;
        }
        float scale = ((View) getParent()).getScaleX();
        float tx = (x - previousLocationX) / scale;
        float ty = (y - previousLocationY) / scale;
        float distance = (float) Math.hypot(tx, ty);
        float minDistance = hasPanned ? 6 : 16;
        if (distance > minDistance) {
            pan(tx, ty);
            previousLocationX = x;
            previousLocationY = y;
            hasPanned = true;

            if (getParent() instanceof EntitiesContainerView && (hasStickyX || hasStickyY)) {
                ((EntitiesContainerView) getParent()).invalidate();
            }

            return true;
        }
        return false;
    }

    private void onTouchUp() {
        if (!recognizedLongPress && !hasPanned && !hasTransformed && !announcedSelection && delegate != null) {
            delegate.onEntitySelected(this);
        }
        recognizedLongPress = false;
        hasPanned = false;
        hasTransformed = false;
        hasReleased = true;
        announcedSelection = false;

        if (getParent() instanceof EntitiesContainerView) {
            ((EntitiesContainerView) getParent()).invalidate();
        }
    }

    public final boolean hasTouchDown() {
        return !hasReleased;
    }

    public void setHasStickyX(boolean hasStickyX) {
        this.hasStickyX = hasStickyX;
    }

    public final boolean hasStickyX() {
        return hasStickyX;
    }

    public void setHasStickyY(boolean hasStickyY) {
        this.hasStickyY = hasStickyY;
    }

    public final boolean hasStickyY() {
        return hasStickyY;
    }

    public boolean hasPanned() {
        return hasPanned;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() > 1 || !delegate.allowInteraction(this)) {
            return false;
        }

        float[] xy = delegate.getTransformedTouch(event.getRawX(), event.getRawY());
        int action = event.getActionMasked();
        boolean handled = false;

        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_DOWN: {
                if (!isSelected() && delegate != null) {
                    delegate.onEntitySelected(this);
                    announcedSelection = true;
                }
                previousLocationX = xy[0];
                previousLocationY = xy[1];
                handled = true;
                hasReleased = false;

                if (getParent() instanceof EntitiesContainerView && (hasStickyX || hasStickyY)) {
                    ((EntitiesContainerView) getParent()).invalidate();
                }
            }
            break;

            case MotionEvent.ACTION_MOVE: {
                handled = onTouchMove(xy[0], xy[1]);
            }
            break;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                onTouchUp();
                handled = true;
            }
            break;
        }

        gestureDetector.onTouchEvent(event);

        return handled;
    }

    private void runStickyXAnimator(float... values) {
        stickyXAnimator = ValueAnimator.ofFloat(values).setDuration(150);
        stickyXAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        stickyXAnimator.addUpdateListener(animation -> updatePosition());
        stickyXAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation == stickyXAnimator) {
                    stickyXAnimator = null;

                    hasFromStickyXAnimation = false;
                }
            }
        });
        stickyXAnimator.start();
    }

    private void runStickyYAnimator(float... values) {
        stickyYAnimator = ValueAnimator.ofFloat(values).setDuration(150);
        stickyYAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        stickyYAnimator.addUpdateListener(animation -> updatePosition());
        stickyYAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation == stickyYAnimator) {
                    stickyYAnimator = null;

                    hasFromStickyYAnimation = false;
                }
            }
        });
        stickyYAnimator.start();
    }

    public void pan(float tx, float ty) {
        position.x += tx;
        position.y += ty;

        if (hasFromStickyXAnimation) {
            fromStickyX = position.x;
        }
        if (hasFromStickyYAnimation) {
            fromStickyY = position.y;
        }

        View parent = (View) getParent();
        if (parent != null) {
            if (!hasStickyX) {
                if (Math.abs(position.x - parent.getMeasuredWidth() / 2f) <= AndroidUtilities.dp(STICKY_TRIGGER_DP)) {
                    hasStickyX = true;
                    try {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignored) {}
                    if (getParent() instanceof EntitiesContainerView) {
                        ((EntitiesContainerView) getParent()).invalidate();
                    }

                    if (stickyXAnimator != null) {
                        stickyXAnimator.cancel();
                    }

                    fromStickyX = position.x;
                    hasFromStickyXAnimation = false;
                    runStickyXAnimator(0, 1);
                }
            } else {
                if (Math.abs(position.x - parent.getMeasuredWidth() / 2f) > AndroidUtilities.dp(STICKY_THRESHOLD_DP)) {
                    hasStickyX = false;
                    if (getParent() instanceof EntitiesContainerView) {
                        ((EntitiesContainerView) getParent()).invalidate();
                    }

                    if (stickyXAnimator != null) {
                        stickyXAnimator.cancel();
                    }
                    hasFromStickyXAnimation = true;
                    runStickyXAnimator(1, 0);
                }
            }

            if (!hasStickyY) {
                if (Math.abs(position.y - parent.getMeasuredHeight() / 2f) <= AndroidUtilities.dp(STICKY_TRIGGER_DP)) {
                    hasStickyY = true;
                    try {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignored) {}
                    if (getParent() instanceof EntitiesContainerView) {
                        ((EntitiesContainerView) getParent()).invalidate();
                    }

                    if (stickyYAnimator != null) {
                        stickyYAnimator.cancel();
                    }
                    fromStickyY = position.y;
                    hasFromStickyYAnimation = false;
                    runStickyYAnimator(0, 1);
                }
            } else {
                if (Math.abs(position.y - parent.getMeasuredHeight() / 2f) > AndroidUtilities.dp(STICKY_THRESHOLD_DP)) {
                    hasStickyY = false;
                    if (getParent() instanceof EntitiesContainerView) {
                        ((EntitiesContainerView) getParent()).invalidate();
                    }

                    if (stickyYAnimator != null) {
                        stickyYAnimator.cancel();
                    }
                    hasFromStickyYAnimation = true;
                    runStickyYAnimator(1, 0);
                }
            }
        }

        updatePosition();
    }

    protected float getPositionX() {
        float x = position.x;
        if (getParent() != null) {
            View parent = (View) getParent();
            if (stickyXAnimator != null) {
                x = AndroidUtilities.lerp(fromStickyX, parent.getMeasuredWidth() / 2f, (Float) stickyXAnimator.getAnimatedValue());
            } else if (hasStickyX) {
                x = parent.getMeasuredWidth() / 2f;
            }
        }
        return x;
    }

    protected float getPositionY() {
        float y = position.y;
        if (getParent() != null) {
            View parent = (View) getParent();
            if (stickyYAnimator != null) {
                y = AndroidUtilities.lerp(fromStickyY, parent.getMeasuredHeight() / 2f, (Float) stickyYAnimator.getAnimatedValue());
            } else if (hasStickyY) {
                y = parent.getMeasuredHeight() / 2f;
            }
        }
        return y;
    }

    protected void updatePosition() {
        float halfWidth = getMeasuredWidth() / 2.0f;
        float halfHeight = getMeasuredHeight() / 2.0f;
        setX(getPositionX() - halfWidth);
        setY(getPositionY() - halfHeight);
        updateSelectionView();
    }

    public void scale(float scale) {
        float newScale = Math.max(getScale() * scale, 0.1f);
        setScale(newScale);
        updateSelectionView();
    }

    public void rotate(float angle) {
        if (!hasStickyAngle) {
            for (int stickyAngle : STICKY_ANGLES) {
                if (Math.abs(stickyAngle - angle) < STICKY_TRIGGER_ANGLE) {
                    currentStickyAngle = stickyAngle;
                    hasStickyAngle = true;
                    try {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignored) {}

                    if (angleAnimator != null) {
                        angleAnimator.cancel();
                    }
                    if (fromStickyAngleAnimator != null) {
                        fromStickyAngleAnimator.cancel();
                    }
                    angleAnimator = ValueAnimator.ofFloat(0, 1).setDuration(150);
                    angleAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    float from = angle;
                    angleAnimator.addUpdateListener(animation -> {
                        stickyAnimatedAngle = AndroidUtilities.lerpAngle(from, currentStickyAngle, animation.getAnimatedFraction());
                        rotateInternal(stickyAnimatedAngle);
                    });
                    angleAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animation == angleAnimator) {
                                angleAnimator = null;
                                stickyAnimatedAngle = 0;
                            }
                        }
                    });
                    angleAnimator.start();
                    break;
                }
            }
        } else {
            if (Math.abs(currentStickyAngle - angle) >= STICKY_THRESHOLD_ANGLE) {
                if (angleAnimator != null) {
                    angleAnimator.cancel();
                }

                if (fromStickyAngleAnimator != null) {
                    fromStickyAngleAnimator.cancel();
                }

                fromStickyAnimatedAngle = currentStickyAngle;
                fromStickyToAngle = angle;

                fromStickyAngleAnimator = ValueAnimator.ofFloat(0, 1).setDuration(150);
                fromStickyAngleAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                fromStickyAngleAnimator.addUpdateListener(animation -> rotateInternal(AndroidUtilities.lerpAngle(fromStickyAnimatedAngle, fromStickyToAngle, fromStickyAngleAnimator.getAnimatedFraction())));
                fromStickyAngleAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation == fromStickyAngleAnimator) {
                            fromStickyAngleAnimator = null;
                        }
                    }
                });
                fromStickyAngleAnimator.start();

                hasStickyAngle = false;
            } else {
                if (angleAnimator != null) {
                    angle = stickyAnimatedAngle;
                } else {
                    angle = currentStickyAngle;
                }
            }
        }
        if (fromStickyAngleAnimator != null) {
            fromStickyToAngle = angle;
            angle = AndroidUtilities.lerpAngle(fromStickyAnimatedAngle, fromStickyToAngle, fromStickyAngleAnimator.getAnimatedFraction());
        }
        rotateInternal(angle);
    }

    private void rotateInternal(float angle) {
        setRotation(angle);
        updateSelectionView();
    }

    protected Rect getSelectionBounds() {
        return new Rect(0, 0, 0, 0);
    }

    public boolean isSelected() {
        return selectionView != null;
    }

    protected SelectionView createSelectionView() {
        return null;
    }

    public void updateSelectionView() {
        if (selectionView != null) {
            selectionView.updatePosition();
        }
    }

    public void select(ViewGroup selectionContainer) {
        SelectionView selectionView = createSelectionView();
        selectionView.setAlpha(0f);
        selectionView.setScaleX(0.9f);
        selectionView.setScaleY(0.9f);
        selectionView.animate().cancel();
        selectionView.animate().alpha(1f).scaleX(1).scaleY(1).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(null).start();
        this.selectionView = selectionView;
        selectionContainer.addView(selectionView);
        selectionView.updatePosition();
    }

    public void deselect() {
        if (selectionView == null) {
            return;
        }
        if (selectionView.getParent() != null) {
            selectionView.animate().cancel();
            selectionView.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    AndroidUtilities.removeFromParent(selectionView);
                    selectionView = null;
                }
            }).start();
        }
    }

    public void setSelectionVisibility(boolean visible) {
        if (selectionView == null) {
            return;
        }
        selectionView.setVisibility(visible ? VISIBLE : GONE);
    }

    public class SelectionView extends FrameLayout {

        public static final int SELECTION_LEFT_HANDLE = 1;
        public static final int SELECTION_RIGHT_HANDLE = 2;
        public static final int SELECTION_WHOLE_HANDLE = 3;

        protected Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        protected Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        protected Paint dotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private int currentHandle;

        public SelectionView(Context context) {
            super(context);
            setWillNotDraw(false);

            paint.setColor(0xffffffff);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setPathEffect(new DashPathEffect(new float[]{AndroidUtilities.dp(10), AndroidUtilities.dp(10)}, .5f));
            paint.setShadowLayer(AndroidUtilities.dp(0.75f), 0, AndroidUtilities.dp(1), 0x70000000);

            dotPaint.setColor(0xff3ccaef);
            dotStrokePaint.setColor(0xffffffff);
            dotStrokePaint.setStyle(Paint.Style.STROKE);
            dotStrokePaint.setStrokeWidth(AndroidUtilities.dp(2));
            dotStrokePaint.setShadowLayer(AndroidUtilities.dp(0.75f), 0, AndroidUtilities.dp(1), 0x70000000);
        }

        protected void updatePosition() {
            Rect bounds = getSelectionBounds();
            LayoutParams layoutParams = (LayoutParams) getLayoutParams();
            layoutParams.leftMargin = (int) bounds.x;
            layoutParams.topMargin = (int) bounds.y;
            layoutParams.width = (int) bounds.width;
            layoutParams.height = (int) bounds.height;
            setLayoutParams(layoutParams);
            setRotation(EntityView.this.getRotation());
        }

        protected int pointInsideHandle(float x, float y) {
            return 0;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            boolean handled = false;

            float rawX = event.getRawX();
            float rawY = event.getRawY();
            float[] xy = delegate.getTransformedTouch(rawX, rawY);
            float x = xy[0];
            float y = xy[1];
            switch (action) {
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_DOWN: {
                    int handle = pointInsideHandle(event.getX(), event.getY());
                    if (handle != 0) {
                        currentHandle = handle;
                        previousLocationX = x;
                        previousLocationY = y;
                        hasReleased = false;
                        handled = true;

                        if (getParent() instanceof EntitiesContainerView) {
                            ((EntitiesContainerView) getParent()).invalidate();
                        }
                    }
                }
                break;

                case MotionEvent.ACTION_MOVE: {
                    if (currentHandle == SELECTION_WHOLE_HANDLE) {
                        handled = onTouchMove(x, y);
                    } else if (currentHandle != 0) {

                        float tx = x - previousLocationX;
                        float ty = y - previousLocationY;

                        if (hasTransformed || Math.abs(tx) > AndroidUtilities.dp(2) || Math.abs(ty) > AndroidUtilities.dp(2)) {
                            hasTransformed = true;
                            float radAngle = (float) Math.toRadians(getRotation());
                            float delta = (float) (tx * Math.cos(radAngle) + ty * Math.sin(radAngle));
                            if (currentHandle == SELECTION_LEFT_HANDLE) {
                                delta *= -1;
                            }

                            if (getMeasuredWidth() != 0) {
                                float scaleDelta = 1 + (delta * 2) / getMeasuredWidth();
                                scale(scaleDelta);
                            }

                            int[] pos = delegate.getCenterLocation(EntityView.this);
                            float angle = 0;
                            if (currentHandle == SELECTION_LEFT_HANDLE) {
                                angle = (float) Math.atan2(pos[1] - rawY, pos[0] - rawX);
                            } else if (currentHandle == SELECTION_RIGHT_HANDLE) {
                                angle = (float) Math.atan2(rawY - pos[1], rawX - pos[0]);
                            }

                            rotate((float) Math.toDegrees(angle) - delegate.getCropRotation());

                            previousLocationX = x;
                            previousLocationY = y;
                        }

                        handled = true;
                    }
                }
                break;

                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    onTouchUp();
                    currentHandle = 0;
                    handled = true;
                }
                break;
            }

            if (currentHandle == SELECTION_WHOLE_HANDLE) {
                gestureDetector.onTouchEvent(event);
            }

            return handled;
        }
    }
}
