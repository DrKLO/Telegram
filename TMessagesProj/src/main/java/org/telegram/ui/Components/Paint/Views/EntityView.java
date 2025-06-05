package org.telegram.ui.Components.Paint.Views;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Build;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
    private final static float STICKY_THRESHOLD_ANGLE = 12;
    private final static float STICKY_TRIGGER_ANGLE = 4;

    private final static float STICKY_TRIGGER_DP = 12;

    public final static float STICKY_PADDING_X_DP = 8;
    public final static float STICKY_PADDING_Y_DP = 64;

    public final static long STICKY_DURATION = 250;

    private ButtonBounce bounce = new ButtonBounce(this);

    public interface EntityViewDelegate {
        boolean onEntitySelected(EntityView entityView);
        boolean onEntityLongClicked(EntityView entityView);
        boolean allowInteraction(EntityView entityView);
        int[] getCenterLocation(EntityView entityView);
        void getTransformedTouch(float x, float y, float[] output);
        float getCropRotation();

        default void onEntityDraggedTop(boolean value) {}
        default void onEntityDraggedBottom(boolean value) {}
        default void onEntityDragStart() {}
        default void onEntityDragMultitouchStart() {}
        default void onEntityDragMultitouchEnd() {}
        default void onEntityDragEnd(boolean delete) {}
        default void onEntityDragTrash(boolean enter) {}
        default void onEntityHandleTouched() {}
        default boolean isEntityDeletable() {
            return true;
        }
    }

    private float previousLocationX,  previousLocationY;
    private float previousLocationX2, previousLocationY2;
    private float previousLocationCX,  previousLocationCY;
    public boolean hasPanned = false;
    public boolean hasReleased = false;
    private boolean hasTransformed = false;
    private boolean announcedDrag = false;
    private boolean announcedMultitouchDrag = false;
    private boolean announcedSelection = false;
    private boolean announcedTrash = false;
    private boolean recognizedLongPress = false;

    private EntityViewDelegate delegate;

    private Point position;
    public SelectionView selectionView;

    private final Runnable longPressRunnable = () -> {
        recognizedLongPress = true;
        if (delegate != null) {
            try {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } catch (Exception ignored) {}
            delegate.onEntityLongClicked(EntityView.this);
        }
    };

    private UUID uuid;

    private boolean hasStickyAngle = true;
    private int currentStickyAngle = 0;
    private int stickyAngleRunnableValue = -1;
    private Runnable setStickyAngleRunnable;

    private float stickyAnimatedAngle;
    private ValueAnimator angleAnimator;

    private float fromStickyAnimatedAngle;
    private float fromStickyToAngle;
    private ValueAnimator fromStickyAngleAnimator;

    public static final int STICKY_NONE = 0;
    public static final int STICKY_START = 1;
    public static final int STICKY_CENTER = 2;
    public static final int STICKY_END = 3;

    private int stickyX = STICKY_NONE, stickyY = STICKY_NONE;
    private Runnable setStickyXRunnable = this::updateStickyX, setStickyYRunnable = this::updateStickyY;
    private int stickyXRunnableValue, stickyYRunnableValue;
    private ValueAnimator stickyXAnimator, stickyYAnimator;

    public EntityView(Context context, Point pos) {
        super(context);

        uuid = UUID.randomUUID();
        position = pos;
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

    public void setIsVideo(boolean isVideo) {}

    protected float getMaxScale() {
        return 100f;
    }

    protected float getMinScale() {
        return 0f;
    }

    public float getScale() {
        return getScaleX();
    }

    public void setScale(float scale) {
        this.scale = scale;
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

    private boolean onTouchMove(float x1, float y1, boolean multitouch, float x2, float y2) {
        if (getParent() == null) {
            return false;
        }
        float scale = ((View) getParent()).getScaleX();
        float x = multitouch ? (x1 + x2) / 2f : x1;
        float y = multitouch ? (y1 + y2) / 2f : y1;
        float tx = (x - previousLocationCX) / scale;
        float ty = (y - previousLocationCY) / scale;
        float distance = (float) Math.hypot(tx, ty);
        float minDistance = hasPanned ? 6 : 16;
        if (distance > minDistance || multitouch) {
            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            pan(tx, ty);

            if (multitouch) {
                float d = MathUtils.distance(x1, y1, x2, y2);
                float pd = MathUtils.distance(previousLocationX, previousLocationY, previousLocationX2, previousLocationY2);
                if (pd > 0) {
                    scale(d / pd);
                }
                double angleDiff = Math.atan2(y1 - y2, x1 - x2) - Math.atan2(previousLocationY - previousLocationY2, previousLocationX - previousLocationX2);
                rotate(this.angle + (float) Math.toDegrees(angleDiff));
            }

            previousLocationX = x1;
            previousLocationY = y1;
            previousLocationCX = x;
            previousLocationCY = y;
            if (multitouch) {
                previousLocationX2 = x2;
                previousLocationY2 = y2;
            }
            hasPanned = true;

            if (getParent() instanceof EntitiesContainerView && (stickyX != STICKY_NONE || stickyY != STICKY_NONE)) {
                ((EntitiesContainerView) getParent()).invalidate();
            }

            if (!announcedDrag && delegate != null) {
                announcedDrag = true;
                delegate.onEntityDragStart();
            }
            if (!announcedMultitouchDrag && multitouch && delegate != null) {
                announcedMultitouchDrag = true;
                delegate.onEntityDragMultitouchStart();
            }
            if (announcedMultitouchDrag && !multitouch && delegate != null) {
                announcedMultitouchDrag = false;
                delegate.onEntityDragMultitouchEnd();
            }
            if (!isSelected() && !announcedSelection && delegate != null) {
                delegate.onEntitySelected(this);
                announcedSelection = true;
            }

            if (delegate != null) {
                delegate.onEntityDraggedTop(position.y - getHeight() / 2f * scale < dp(66));
                delegate.onEntityDraggedBottom(position.y + getHeight() / 2f * scale > ((View) getParent()).getHeight() - dp(64 + 50));
            }

            updateTrash(
                (delegate == null || delegate.isEntityDeletable()) &&
                    !multitouch &&
                    MathUtils.distance(x, y,  ((View) getParent()).getWidth() / 2f, ((View) getParent()).getHeight() - dp(76)) < dp(32)
            );

            bounce.setPressed(false);

            return true;
        }
        return false;
    }

    private void onTouchUp(boolean canceled) {
        if (announcedDrag) {
            delegate.onEntityDragEnd(announcedTrash);
            announcedDrag = false;
        }
        announcedMultitouchDrag = false;
        if (!canceled && !recognizedLongPress && !hasPanned && !hasTransformed && !announcedSelection && delegate != null) {
            delegate.onEntitySelected(this);
        }
        if (hasPanned && delegate != null) {
            delegate.onEntityDraggedTop(false);
            delegate.onEntityDraggedBottom(false);
        }
        AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
        recognizedLongPress = false;
        hasPanned = false;
        hasTransformed = false;
        hasReleased = true;
        announcedSelection = false;

        stickyAngleRunnableValue = currentStickyAngle;
        if (setStickyAngleRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(setStickyAngleRunnable);
            setStickyAngleRunnable = null;
        }
        stickyXRunnableValue = stickyX;
        AndroidUtilities.cancelRunOnUIThread(setStickyXRunnable);
        stickyYRunnableValue = stickyY;
        AndroidUtilities.cancelRunOnUIThread(setStickyYRunnable);
        if (getParent() instanceof EntitiesContainerView) {
            ((EntitiesContainerView) getParent()).invalidate();
        }
    }

    public final boolean hasTouchDown() {
        return !hasReleased;
    }

    public void setStickyX(int stickyX) {
        this.stickyX = this.stickyXRunnableValue = stickyX;
    }

    public final int getStickyX() {
        return stickyX;
    }

    public void setStickyY(int stickyY) {
        this.stickyY = this.stickyYRunnableValue = stickyY;
    }

    public final int getStickyY() {
        return stickyY;
    }

    public boolean hasPanned() {
        return hasPanned;
    }

    protected float getStickyPaddingLeft() {
        return 0;
    }

    protected float getStickyPaddingTop() {
        return 0;
    }

    protected float getStickyPaddingRight() {
        return 0;
    }

    protected float getStickyPaddingBottom() {
        return 0;
    }

    private boolean lastIsMultitouch;
    private boolean hadMultitouch;
    private final float[] xy = new float[2];
    private final float[] xy2 = new float[2];
    private final float[] cxy = new float[2];

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!delegate.allowInteraction(this)) {
            return false;
        }

        delegate.getTransformedTouch(event.getRawX(), event.getRawY(), xy);
        boolean isMultitouch = event.getPointerCount() > 1;
        if (isMultitouch) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                delegate.getTransformedTouch(event.getRawX(1), event.getRawY(1), xy2);
            } else {
                isMultitouch = false;
                // TODO
            }
        }
        if (isMultitouch) {
            cxy[0] = (xy[0] + xy2[0]) / 2f;
            cxy[1] = (xy[1] + xy2[1]) / 2f;
        } else {
            cxy[0] = xy[0];
            cxy[1] = xy[1];
        }
        if (lastIsMultitouch != isMultitouch) {
            previousLocationX = xy[0];
            previousLocationY = xy[1];
            previousLocationX2 = xy2[0];
            previousLocationY2 = xy2[1];
            previousLocationCX = cxy[0];
            previousLocationCY = cxy[1];
            if (selectionView != null) {
                selectionView.hide(isMultitouch);
            }
        }
        lastIsMultitouch = isMultitouch;
        float x = cxy[0];
        float y = cxy[1];
        int action = event.getActionMasked();
        boolean handled = false;

        switch (action) {
//            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_DOWN: {
                hadMultitouch = false;
                previousLocationX = xy[0];
                previousLocationY = xy[1];
                previousLocationCX = x;
                previousLocationCY = y;
                handled = true;
                hasReleased = false;

                if (getParent() instanceof EntitiesContainerView && (stickyX != STICKY_NONE || stickyY != STICKY_NONE)) {
                    ((EntitiesContainerView) getParent()).invalidate();
                }
                bounce.setPressed(true);

                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                if (!isMultitouch) {
                    AndroidUtilities.runOnUIThread(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
            }
            break;

            case MotionEvent.ACTION_MOVE: {
                handled = onTouchMove(xy[0], xy[1], isMultitouch, xy2[0], xy2[1]);
            }
            break;

//            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                onTouchUp(action == MotionEvent.ACTION_CANCEL);
                bounce.setPressed(false);
                handled = true;
                if (selectionView != null) {
                    selectionView.hide(false);
                }
            }
            break;
        }

        hadMultitouch = isMultitouch;

        return super.onTouchEvent(event) || handled;
    }

    private void runStickyXAnimator(float... values) {
        if (stickyXAnimator != null) {
            stickyXAnimator.cancel();
        }
        stickyXAnimator = ValueAnimator.ofFloat(values).setDuration(150);
        stickyXAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        stickyXAnimator.addUpdateListener(animation -> updatePosition());
        stickyXAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation == stickyXAnimator) {
                    stickyXAnimator = null;
                }
            }
        });
        stickyXAnimator.start();
    }

    private void runStickyYAnimator(float... values) {
        if (stickyYAnimator != null) {
            stickyYAnimator.cancel();
        }
        stickyYAnimator = ValueAnimator.ofFloat(values).setDuration(150);
        stickyYAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        stickyYAnimator.addUpdateListener(animation -> updatePosition());
        stickyYAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation == stickyYAnimator) {
                    stickyYAnimator = null;
                }
            }
        });
        stickyYAnimator.start();
    }

    private void updateStickyX() {
        AndroidUtilities.cancelRunOnUIThread(setStickyXRunnable);
        if (stickyX == stickyXRunnableValue) {
            return;
        }
        stickyX = stickyXRunnableValue;
        if (getParent() instanceof EntitiesContainerView) {
            ((EntitiesContainerView) getParent()).invalidate();
        }
        if (stickyXAnimator != null) {
            stickyXAnimator.cancel();
        }
        if (stickyXRunnableValue == STICKY_NONE) {
            runStickyXAnimator(1, 0);
        } else {
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignored) {}
            runStickyXAnimator(0, 1);
        }
    }

    private void updateStickyY() {
        AndroidUtilities.cancelRunOnUIThread(setStickyYRunnable);
        if (stickyY == stickyYRunnableValue) {
            return;
        }
        stickyY = stickyYRunnableValue;
        if (getParent() instanceof EntitiesContainerView) {
            ((EntitiesContainerView) getParent()).invalidate();
        }
        if (stickyYAnimator != null) {
            stickyYAnimator.cancel();
        }
        if (stickyYRunnableValue == STICKY_NONE) {
            runStickyYAnimator(1, 0);
        } else {
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignored) {}
            runStickyYAnimator(0, 1);
        }
    }

    public void pan(float tx, float ty) {
        position.x += tx;
        position.y += ty;

        View parent = (View) getParent();
        if (parent != null) {
            int newStickyX = STICKY_NONE;
            if (!lastIsMultitouch) {
                if (Math.abs(position.x - parent.getMeasuredWidth() / 2f) <= dp(STICKY_TRIGGER_DP) && position.y < parent.getMeasuredHeight() - dp(112)) {
                    newStickyX = STICKY_CENTER;
                } else if (Math.abs(position.x - (width() / 2f + getStickyPaddingLeft()) * getScaleX() - dp(STICKY_PADDING_X_DP)) <= dp(STICKY_TRIGGER_DP)) {
                    newStickyX = STICKY_START;
                } else if (Math.abs(position.x + (width() / 2f - getStickyPaddingRight()) * getScaleX() - (parent.getMeasuredWidth() - dp(STICKY_PADDING_X_DP))) <= dp(STICKY_TRIGGER_DP)) {
                    newStickyX = STICKY_END;
                }
            }
            if (stickyXRunnableValue != newStickyX) {
                if ((stickyXRunnableValue = newStickyX) == STICKY_NONE) {
                    updateStickyX();
                } else {
                    AndroidUtilities.runOnUIThread(setStickyXRunnable, STICKY_DURATION);
                }
            }

            int newStickyY = STICKY_NONE;
            if (!lastIsMultitouch) {
                if (Math.abs(position.y - parent.getMeasuredHeight() / 2f) <= dp(STICKY_TRIGGER_DP)) {
                    newStickyY = STICKY_CENTER;
                } else if (Math.abs(position.y - (height() / 2f + getStickyPaddingTop()) * getScaleY() - dp(STICKY_PADDING_Y_DP)) <= dp(STICKY_TRIGGER_DP)) {
                    newStickyY = STICKY_START;
                } else if (Math.abs(position.y + (height() / 2f - getStickyPaddingBottom()) * getScaleY() - (parent.getMeasuredHeight() - dp(STICKY_PADDING_Y_DP))) <= dp(STICKY_TRIGGER_DP)) {
                    newStickyY = STICKY_END;
                }
            }
            if (stickyYRunnableValue != newStickyY) {
                if ((stickyYRunnableValue = newStickyY) == STICKY_NONE) {
                    updateStickyY();
                } else {
                    AndroidUtilities.runOnUIThread(setStickyYRunnable, STICKY_DURATION);
                }
            }
        }

        updatePosition();
    }

    private float width() {
        return (float) (Math.abs(Math.cos(getRotation() / 180 * Math.PI)) * getMeasuredWidth() + Math.abs(Math.sin(getRotation() / 180 * Math.PI)) * getMeasuredHeight());
    }

    private float height() {
        return (float) (Math.abs(Math.cos(getRotation() / 180 * Math.PI)) * getMeasuredHeight() + Math.abs(Math.sin(getRotation() / 180 * Math.PI)) * getMeasuredWidth());
    }

    protected float getPositionX() {
        float x = position.x;
        if (getParent() != null) {
            View parent = (View) getParent();
            float stickyX = x;
            if (this.stickyX == STICKY_START) {
                stickyX = dp(STICKY_PADDING_X_DP) + (width() / 2f - getStickyPaddingLeft()) * getScaleX();
            } else if (this.stickyX == STICKY_CENTER) {
                stickyX = parent.getMeasuredWidth() / 2f;
            } else if (this.stickyX == STICKY_END) {
                stickyX = parent.getMeasuredWidth() - dp(STICKY_PADDING_X_DP) - (width() / 2f + getStickyPaddingRight()) * getScaleX();
            }
            if (stickyXAnimator != null) {
                x = AndroidUtilities.lerp(x, stickyX, (Float) stickyXAnimator.getAnimatedValue());
            } else if (stickyX != STICKY_NONE) {
                x = stickyX;
            }
        }
        return x;
    }

    protected float getPositionY() {
        float y = position.y;
        if (getParent() != null) {
            View parent = (View) getParent();
            float stickyY = y;
            if (this.stickyY == STICKY_START) {
                stickyY = dp(STICKY_PADDING_Y_DP) + (height() / 2f - getStickyPaddingTop()) * getScaleY();
            } else if (this.stickyY == STICKY_CENTER) {
                stickyY = parent.getMeasuredHeight() / 2f;
            } else if (this.stickyY == STICKY_END) {
                stickyY = parent.getMeasuredHeight() - dp(STICKY_PADDING_Y_DP) - (height() / 2f + getStickyPaddingBottom()) * getScaleY();
            }
            if (stickyYAnimator != null) {
                y = AndroidUtilities.lerp(y, stickyY, (Float) stickyYAnimator.getAnimatedValue());
            } else if (stickyY != STICKY_NONE) {
                y = stickyY;
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

    private float scale = 1f;

    public void scale(float scale) {
        float oldScale = this.scale;
        this.scale *= scale;
        float newScale = Math.max(this.scale, 0.1f);
        newScale = Utilities.clamp(newScale, getMaxScale(), getMinScale());
        if (allowHaptic() && (newScale >= getMaxScale() || newScale <= getMinScale()) != (oldScale >= getMaxScale() || oldScale <= getMinScale())) {
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            } catch (Exception ignore) {}
        }
        setScaleX(newScale);
        setScaleY(newScale);
//        updateSelectionView();
    }

    protected boolean allowHaptic() {
        return true;
    }

    private float angle;
    public void rotate(float angle) {
        if (stickyX != STICKY_NONE) {
            stickyXRunnableValue = STICKY_NONE;
            updateStickyX();
        }
        if (stickyY != STICKY_NONE) {
            stickyYRunnableValue = STICKY_NONE;
            updateStickyY();
        }

        this.angle = angle;
        if (!hasStickyAngle && !lastIsMultitouch) {
            for (int stickyAngle : STICKY_ANGLES) {
                if (Math.abs(stickyAngle - angle) < STICKY_TRIGGER_ANGLE) {
                    if (stickyAngleRunnableValue != stickyAngle) {
                        stickyAngleRunnableValue = stickyAngle;
                        if (setStickyAngleRunnable != null) {
                            AndroidUtilities.cancelRunOnUIThread(setStickyAngleRunnable);
                        }
                        AndroidUtilities.runOnUIThread(setStickyAngleRunnable = () -> {
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
                            angleAnimator.addUpdateListener(animation -> {
                                stickyAnimatedAngle = AndroidUtilities.lerpAngle(this.angle, currentStickyAngle, animation.getAnimatedFraction());
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
                        }, STICKY_DURATION);
                        break;
                    }
                    break;
                }
            }
        } else if (hasStickyAngle) {
            if (Math.abs(currentStickyAngle - angle) >= STICKY_THRESHOLD_ANGLE || lastIsMultitouch) {
                stickyAngleRunnableValue = -1;
                if (setStickyAngleRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(setStickyAngleRunnable);
                    setStickyAngleRunnable = null;
                }
                if (angleAnimator != null) {
                    angleAnimator.cancel();
                }
                if (fromStickyAngleAnimator != null) {
                    fromStickyAngleAnimator.cancel();
                }

                fromStickyAngleAnimator = ValueAnimator.ofFloat(0, 1).setDuration(150);
                fromStickyAngleAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                fromStickyAngleAnimator.addUpdateListener(animation -> rotateInternal(AndroidUtilities.lerpAngle(currentStickyAngle, this.angle, fromStickyAngleAnimator.getAnimatedFraction())));
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
        if (stickyX != STICKY_NONE || stickyY != STICKY_NONE) {
            updatePosition();
        }
        updateSelectionView();
    }

    public Rect getSelectionBounds() {
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
                selectionView.hide(lastIsMultitouch);
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

    public boolean isSelectedProgress() {
        return isSelected() || selectT > 0;
    }

    private ViewGroup lastSelectionContainer;
    public void select(ViewGroup selectionContainer) {
        updateSelect(lastSelectionContainer = selectionContainer, true);
    }

    public void deselect() {
        updateSelect(lastSelectionContainer, false);
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
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setPathEffect(new DashPathEffect(new float[]{dp(10), dp(10)}, .5f));
            paint.setShadowLayer(AndroidUtilities.dpf2(0.75f), 0, 0, 0x50000000);

            dotPaint.setColor(0xff1A9CFF);
            dotStrokePaint.setColor(0xffffffff);
            dotStrokePaint.setStyle(Paint.Style.STROKE);
            dotStrokePaint.setStrokeWidth(AndroidUtilities.dpf2(2.66f));
            dotStrokePaint.setShadowLayer(AndroidUtilities.dpf2(0.75f), 0, 0, 0x50000000);
        }

        public void updatePosition() {
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
            delegate.getTransformedTouch(rawX, rawY, xy);
            boolean isMultitouch = event.getPointerCount() > 1 && currentHandle == SELECTION_WHOLE_HANDLE;
            if (isMultitouch) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    delegate.getTransformedTouch(event.getRawX(1), event.getRawY(1), xy2);
                } else {
                    isMultitouch = false;
                    // TODO
                }
            }
            if (isMultitouch) {
                cxy[0] = (xy[0] + xy2[0]) / 2f;
                cxy[1] = (xy[1] + xy2[1]) / 2f;
            } else {
                cxy[0] = xy[0];
                cxy[1] = xy[1];
            }
            if (lastIsMultitouch != isMultitouch) {
                previousLocationX = xy[0];
                previousLocationY = xy[1];
                previousLocationX2 = xy2[0];
                previousLocationY2 = xy2[1];
                previousLocationCX = cxy[0];
                previousLocationCY = cxy[1];
                hide(isMultitouch);
            }
            lastIsMultitouch = isMultitouch;
            float x = cxy[0];
            float y = cxy[1];
            switch (action) {
//                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_DOWN: {
                    hadMultitouch = false;
                    int handle = pointInsideHandle(event.getX(), event.getY());
                    if (handle != 0) {
                        currentHandle = handle;
                        previousLocationX = xy[0];
                        previousLocationY = xy[1];
                        previousLocationCX = x;
                        previousLocationCY = y;
                        hasReleased = false;
                        handled = true;

                        if (getParent() instanceof EntitiesContainerView) {
                            ((EntitiesContainerView) getParent()).invalidate();
                        }
                        if (handle == SELECTION_WHOLE_HANDLE && allowLongPressOnSelected()) {
                            AndroidUtilities.runOnUIThread(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                        }
                    }
                }
                break;

                case MotionEvent.ACTION_MOVE: {
                    if (currentHandle == SELECTION_WHOLE_HANDLE) {
                        handled = onTouchMove(xy[0], xy[1], isMultitouch, xy2[0], xy2[1]);
                    } else if (currentHandle != 0) {

                        float tx = x - previousLocationX;
                        float ty = y - previousLocationY;

                        if (hasTransformed || Math.abs(tx) > dp(2) || Math.abs(ty) > dp(2)) {
                            if (!hasTransformed && delegate != null) {
                                delegate.onEntityHandleTouched();
                            }
                            hasTransformed = true;
                            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);

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

                            rotate((float) Math.toDegrees(angle));

                            previousLocationX = x;
                            previousLocationY = y;
                        }

                        handled = true;
                    }
                }
                break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    onTouchUp(action == MotionEvent.ACTION_CANCEL);
                    currentHandle = 0;
                    handled = true;
                    hide(false);
                }
                break;
            }

            hadMultitouch = isMultitouch;

            return super.onTouchEvent(event) || handled;
        }

        private final AnimatedFloat showAlpha = new AnimatedFloat(this, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        private boolean shown = true;

        public void hide(boolean hide) {
            shown = !hide;
            invalidate();
        }

        protected float getShowAlpha() {
            return showAlpha.set(shown);
        }
    }

    public boolean allowLongPressOnSelected() {
        return false;
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

    public boolean trashCenter() {
        return false;
    }

    protected float getBounceScale() {
        return .04f;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final float scale = bounce.getScale(getBounceScale());
        canvas.save();
        canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
        if (getParent() instanceof View) {
            View p = (View) getParent();
            if (trashCenter()) {
                canvas.scale(trashScale, trashScale, getWidth() / 2f, getHeight() / 2f);
            } else {
                float px = p.getWidth() / 2f - getX();
                float py = p.getHeight() - dp(76) - getY();
                canvas.scale(trashScale, trashScale, px, py);
            }
        }
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    public boolean hadMultitouch() {
        return hadMultitouch;
    }
}
