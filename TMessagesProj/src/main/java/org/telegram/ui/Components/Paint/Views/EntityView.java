package org.telegram.ui.Components.Paint.Views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.Log;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
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

    private final static float STICKY_THRESHOLD_DP = 16;
    private final static float STICKY_TRIGGER_DP = 6;

    private ButtonBounce bounce = new ButtonBounce(this);

    public interface EntityViewDelegate {
        boolean onEntitySelected(EntityView entityView);
        boolean onEntityLongClicked(EntityView entityView);
        boolean allowInteraction(EntityView entityView);
        int[] getCenterLocation(EntityView entityView);
        float[] getTransformedTouch(MotionEvent e, float x, float y);
        float getCropRotation();

        default void onEntityDraggedTop(boolean value) {}
        default void onEntityDraggedBottom(boolean value) {}
        default void onEntityDragStart() {}
        default void onEntityDragEnd(boolean delete) {}
        default void onEntityDragTrash(boolean enter) {}
    }

    private float previousLocationX;
    private float previousLocationY;
    private boolean hasPanned = false;
    private boolean hasReleased = false;
    private boolean hasTransformed = false;
    private boolean announcedDrag = false;
    private boolean announcedSelection = false;
    private boolean announcedTrash = false;
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

            if (!announcedDrag && delegate != null) {
                announcedDrag = true;
                delegate.onEntityDragStart();
            }
            if (!isSelected() && !announcedSelection && delegate != null) {
                delegate.onEntitySelected(this);
                announcedSelection = true;
            }

            if (delegate != null) {
                delegate.onEntityDraggedTop(position.y - getHeight() / 2f * scale < AndroidUtilities.dp(66));
                delegate.onEntityDraggedBottom(position.y + getHeight() / 2f * scale > ((View) getParent()).getHeight() - AndroidUtilities.dp(64 + 50));
            }

            updateTrash(MathUtils.distance(x, y,  ((View) getParent()).getWidth() / 2f, ((View) getParent()).getHeight() - AndroidUtilities.dp(76)) < AndroidUtilities.dp(32));

            bounce.setPressed(false);

            return true;
        }
        return false;
    }

    private void onTouchUp() {
        if (announcedDrag) {
            delegate.onEntityDragEnd(announcedTrash);
            announcedDrag = false;
        }
        if (!recognizedLongPress && !hasPanned && !hasTransformed && !announcedSelection && delegate != null) {
            delegate.onEntitySelected(this);
        }
        if (hasPanned && delegate != null) {
            delegate.onEntityDraggedTop(false);
            delegate.onEntityDraggedBottom(false);
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

        float[] xy = delegate.getTransformedTouch(event, event.getRawX(), event.getRawY());
        int action = event.getActionMasked();
        boolean handled = false;

        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_DOWN: {
                previousLocationX = xy[0];
                previousLocationY = xy[1];
                handled = true;
                hasReleased = false;

                if (getParent() instanceof EntitiesContainerView && (hasStickyX || hasStickyY)) {
                    ((EntitiesContainerView) getParent()).invalidate();
                }
                bounce.setPressed(true);
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
                bounce.setPressed(false);
                handled = true;
            }
            break;
        }

        gestureDetector.onTouchEvent(event);

        return super.onTouchEvent(event) || handled;
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
                if (Math.abs(position.x - parent.getMeasuredWidth() / 2f) <= AndroidUtilities.dp(STICKY_TRIGGER_DP) && position.y < parent.getMeasuredHeight() - AndroidUtilities.dp(112 + 64)) {
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
                if (Math.abs(position.x - parent.getMeasuredWidth() / 2f) > AndroidUtilities.dp(STICKY_THRESHOLD_DP) || position.y >= parent.getMeasuredHeight() - AndroidUtilities.dp(112 + 64)) {
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
        return selecting;
    }

    protected SelectionView createSelectionView() {
        return null;
    }

    public void updateSelectionView() {
        if (selectionView != null) {
            selectionView.updatePosition();
        }
    }

    private float selectT;
    private ValueAnimator selectAnimator;
    private boolean selecting = false;
    private void updateSelect(ViewGroup selectionContainer, boolean select) {
        if (selecting != select) {
            selecting = select;

            if (selectAnimator != null) {
                selectAnimator.cancel();
                selectAnimator = null;
            }

            if (selectionView == null) {
                if (!select && selectionContainer == null) {
                    return;
                }
                selectionView = createSelectionView();
                selectionContainer.addView(selectionView);
                selectT = 0;
            }
            selectionView.updatePosition();

            selectAnimator = ValueAnimator.ofFloat(selectT, select ? 1f : 0f);
            selectAnimator.addUpdateListener(anm -> {
                selectT = (float) anm.getAnimatedValue();
                if (selectionView != null) {
                    selectionView.setScaleX(AndroidUtilities.lerp(0.9f, 1f, selectT) * Utilities.clamp(trashScale * 1.25f, 1, 0));
                    selectionView.setScaleY(AndroidUtilities.lerp(0.9f, 1f, selectT) * Utilities.clamp(trashScale * 1.25f, 1, 0));
                    selectionView.setAlpha(selectT * Math.max(0, trashScale - .8f) * 5);
                }
            });
            selectAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!selecting) {
                        AndroidUtilities.removeFromParent(selectionView);
                        selectionView = null;
                    }
                }
            });
            selectAnimator.setDuration(280);
            selectAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            selectAnimator.start();
        }
    }

    public void select(ViewGroup selectionContainer) {
        updateSelect(selectionContainer, true);
    }

    public void deselect() {
        updateSelect(null, false);
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
            paint.setShadowLayer(AndroidUtilities.dpf2(0.75f), 0, 0, 0x50000000);

            dotPaint.setColor(0xff1A9CFF);
            dotStrokePaint.setColor(0xffffffff);
            dotStrokePaint.setStyle(Paint.Style.STROKE);
            dotStrokePaint.setStrokeWidth(AndroidUtilities.dpf2(2.66f));
            dotStrokePaint.setShadowLayer(AndroidUtilities.dpf2(0.75f), 0, 0, 0x50000000);
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
            float[] xy = delegate.getTransformedTouch(event, rawX, rawY);
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
//                            float radAngle = (float) Math.toRadians(getRotation());
//                            float delta = (float) (tx * Math.cos(radAngle) + ty * Math.sin(radAngle));
//                            if (currentHandle == SELECTION_LEFT_HANDLE) {
//                                delta *= -1;
//                            }
//
//                            if (getMeasuredWidth() != 0) {
//                                float scaleDelta = 1 + (delta * 2) / getMeasuredWidth();
//                                scale(scaleDelta);
//                            }

                            int[] pos = delegate.getCenterLocation(EntityView.this);
                            float pd = MathUtils.distance(pos[0], pos[1], previousLocationX, previousLocationY);
                            float d = MathUtils.distance(pos[0], pos[1], x, y);
                            if (pd > 0) {
                                float scaleFactor = d / pd;
                                scale(scaleFactor);
                            }

                            float angle = 0;
                            if (currentHandle == SELECTION_LEFT_HANDLE) {
                                angle = (float) Math.atan2(pos[1] - y, pos[0] - x);
                            } else if (currentHandle == SELECTION_RIGHT_HANDLE) {
                                angle = (float) Math.atan2(y - pos[1], x - pos[0]);
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

            return super.onTouchEvent(event) || handled;
        }
    }

    private float trashScale = 1f;
    private ValueAnimator trashAnimator;
    private void updateTrash(boolean enter) {
        if (announcedTrash != enter) {
            if (trashAnimator != null) {
                trashAnimator.cancel();
                trashAnimator = null;
            }
            trashAnimator = ValueAnimator.ofFloat(trashScale, enter ? .5f : 1f);
            trashAnimator.addUpdateListener(anm -> {
                trashScale = (float) anm.getAnimatedValue();
                setAlpha(trashScale);
                if (selectionView != null) {
                    selectionView.setScaleX(AndroidUtilities.lerp(0.9f, 1f, selectT) * Utilities.clamp(trashScale * 1.25f, 1, 0));
                    selectionView.setScaleY(AndroidUtilities.lerp(0.9f, 1f, selectT) * Utilities.clamp(trashScale * 1.25f, 1, 0));
                    selectionView.setAlpha(selectT * Math.max(0, trashScale - .8f) * 5);
                }
                invalidate();
            });
            trashAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            trashAnimator.setDuration(280);
            trashAnimator.start();

            announcedTrash = enter;
            if (delegate != null) {
                delegate.onEntityDragTrash(enter);
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final float scale = bounce.getScale(.05f);
        canvas.save();
        canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
        if (getParent() instanceof View) {
            View p = (View) getParent();
            canvas.scale(trashScale, trashScale, p.getWidth() / 2f - getX(), p.getHeight() - AndroidUtilities.dp(76) - getY());
        }
        super.dispatchDraw(canvas);
        canvas.restore();
    }
}
