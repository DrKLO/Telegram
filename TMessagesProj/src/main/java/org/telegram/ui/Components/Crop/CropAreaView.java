package org.telegram.ui.Components.Crop;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import org.telegram.messenger.AndroidUtilities;

public class CropAreaView extends View {

    private enum Control {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, LEFT, BOTTOM, RIGHT
    }

    interface AreaViewListener {
        void onAreaChangeBegan();
        void onAreaChange();
        void onAreaChangeEnded();
    }

    private RectF topLeftCorner = new RectF();
    private RectF topRightCorner = new RectF();
    private RectF bottomLeftCorner = new RectF();
    private RectF bottomRightCorner = new RectF();
    private RectF topEdge = new RectF();
    private RectF leftEdge = new RectF();
    private RectF bottomEdge = new RectF();
    private RectF rightEdge = new RectF();

    private float lockAspectRatio;

    private Control activeControl;
    private RectF actualRect = new RectF();
    private RectF tempRect = new RectF();
    private int previousX;
    private int previousY;

    private float bottomPadding;
    private boolean dimVisibile;
    private boolean frameVisible;

    Paint dimPaint;
    Paint shadowPaint;
    Paint linePaint;
    Paint handlePaint;
    Paint framePaint;

    AccelerateDecelerateInterpolator interpolator = new AccelerateDecelerateInterpolator();

    private float sidePadding;
    private float minWidth;

    enum GridType {
        NONE, MINOR, MAJOR
    }
    private GridType previousGridType;
    private GridType gridType;
    private float gridProgress;
    private Animator gridAnimator;

    private AreaViewListener listener;

    private boolean isDragging;

    private Animator animator;

    public CropAreaView(Context context) {
        super(context);

        frameVisible = true;
        dimVisibile = true;

        sidePadding = AndroidUtilities.dp(16);
        minWidth = AndroidUtilities.dp(32);

        gridType = GridType.NONE;

        dimPaint = new Paint();
        dimPaint.setColor(0xcc000000);

        shadowPaint = new Paint();
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x1a000000);
        shadowPaint.setStrokeWidth(AndroidUtilities.dp(2));

        linePaint = new Paint();
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setColor(0xffffffff);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));

        handlePaint = new Paint();
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(Color.WHITE);

        framePaint = new Paint();
        framePaint.setStyle(Paint.Style.FILL);
        framePaint.setColor(0xb2ffffff);
    }

    public boolean isDragging() {
        return isDragging;
    }

    public void setDimVisibility(boolean visible) {
        dimVisibile = visible;
    }

    public void setFrameVisibility(boolean visible) {
        frameVisible = visible;
    }

    public void setBottomPadding(float value) {
        bottomPadding = value;
    }

    public Interpolator getInterpolator() {
        return interpolator;
    }

    public void setListener(AreaViewListener l) {
        listener = l;
    }

    public void setBitmap(Bitmap bitmap, boolean sideward, boolean freeform) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        float aspectRatio;
        if (sideward) {
            aspectRatio = ((float)bitmap.getHeight()) / ((float)bitmap.getWidth());
        } else {
            aspectRatio = ((float)bitmap.getWidth()) / ((float)bitmap.getHeight());
        }

        if (!freeform) {
            aspectRatio = 1.0f;
            lockAspectRatio = 1.0f;
        }

        setActualRect(aspectRatio);
    }

    public void setActualRect(float aspectRatio) {
        calculateRect(actualRect, aspectRatio);
        updateTouchAreas();
        invalidate();
    }

    public void setActualRect(RectF rect) {
        actualRect.set(rect);
        updateTouchAreas();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int lineThickness = AndroidUtilities.dp(2);
        int handleSize = AndroidUtilities.dp(16);
        int handleThickness = AndroidUtilities.dp(3);

        int originX = (int)actualRect.left - lineThickness;
        int originY = (int)actualRect.top - lineThickness;
        int width = (int)(actualRect.right - actualRect.left) + lineThickness * 2;
        int height = (int)(actualRect.bottom - actualRect.top) + lineThickness * 2;

        if (dimVisibile) {
            canvas.drawRect(0, 0, getWidth(), originY + lineThickness, dimPaint);
            canvas.drawRect(0, originY + lineThickness, originX + lineThickness, originY + height - lineThickness, dimPaint);
            canvas.drawRect(originX + width - lineThickness, originY + lineThickness, getWidth(), originY + height - lineThickness, dimPaint);
            canvas.drawRect(0, originY + height - lineThickness, getWidth(), getHeight(), dimPaint);
        }

        if (!frameVisible) {
            return;
        }

        int inset = handleThickness - lineThickness;
        int gridWidth = width - handleThickness * 2;
        int gridHeight = height - handleThickness * 2;

        GridType type = gridType;
        if (type == GridType.NONE && gridProgress > 0)
            type = previousGridType;

        shadowPaint.setAlpha((int)(gridProgress * 26));
        linePaint.setAlpha((int)(gridProgress * 178));

        for (int i = 0; i < 3; i++) {
            if (type == GridType.MINOR) {
                for (int j = 1; j < 4; j++) {
                    if (i == 2 && j == 3)
                        continue;

                    canvas.drawLine(originX + handleThickness + gridWidth / 3 / 3 * j + gridWidth / 3 * i, originY + handleThickness, originX + handleThickness + gridWidth / 3 / 3 * j + gridWidth / 3 * i, originY + handleThickness + gridHeight, shadowPaint);
                    canvas.drawLine(originX + handleThickness + gridWidth / 3 / 3 * j + gridWidth / 3 * i, originY + handleThickness, originX + handleThickness + gridWidth / 3 / 3 * j + gridWidth / 3 * i, originY + handleThickness + gridHeight, linePaint);

                    canvas.drawLine(originX + handleThickness, originY + handleThickness + gridHeight / 3 / 3 * j + gridHeight / 3 * i, originX + handleThickness + gridWidth, originY + handleThickness + gridHeight / 3 / 3 * j + gridHeight / 3 * i, shadowPaint);
                    canvas.drawLine(originX + handleThickness, originY + handleThickness + gridHeight / 3 / 3 * j + gridHeight / 3 * i, originX + handleThickness + gridWidth, originY + handleThickness + gridHeight / 3 / 3 * j + gridHeight / 3 * i, linePaint);
                }
            }
            else if (type == GridType.MAJOR) {
                if (i > 0) {
                    canvas.drawLine(originX + handleThickness + gridWidth / 3 * i, originY + handleThickness, originX + handleThickness + gridWidth / 3 * i, originY + handleThickness + gridHeight, shadowPaint);
                    canvas.drawLine(originX + handleThickness + gridWidth / 3 * i, originY + handleThickness, originX + handleThickness + gridWidth / 3 * i, originY + handleThickness + gridHeight, linePaint);

                    canvas.drawLine(originX + handleThickness, originY + handleThickness + gridHeight / 3 * i, originX + handleThickness + gridWidth, originY + handleThickness + gridHeight / 3 * i, shadowPaint);
                    canvas.drawLine(originX + handleThickness, originY + handleThickness + gridHeight / 3 * i, originX + handleThickness + gridWidth, originY + handleThickness + gridHeight / 3 * i, linePaint);
                }
            }
        }

        canvas.drawRect(originX + inset, originY + inset, originX + width - inset, originY + inset + lineThickness, framePaint);
        canvas.drawRect(originX + inset, originY + inset, originX + inset + lineThickness, originY + height - inset, framePaint);
        canvas.drawRect(originX + inset, originY + height - inset - lineThickness, originX + width - inset, originY + height - inset, framePaint);
        canvas.drawRect(originX + width - inset - lineThickness, originY + inset, originX + width - inset, originY + height - inset, framePaint);

        canvas.drawRect(originX, originY, originX + handleSize, originY + handleThickness, handlePaint);
        canvas.drawRect(originX, originY, originX + handleThickness, originY + handleSize, handlePaint);

        canvas.drawRect(originX + width - handleSize, originY, originX + width, originY + handleThickness, handlePaint);
        canvas.drawRect(originX + width - handleThickness, originY, originX + width, originY + handleSize, handlePaint);

        canvas.drawRect(originX, originY + height - handleThickness, originX + handleSize, originY + height, handlePaint);
        canvas.drawRect(originX, originY + height - handleSize, originX + handleThickness, originY + height, handlePaint);

        canvas.drawRect(originX + width - handleSize, originY + height - handleThickness, originX + width, originY + height, handlePaint);
        canvas.drawRect(originX + width - handleThickness, originY + height - handleSize, originX + width, originY + height, handlePaint);
    }

    private void updateTouchAreas() {
        int touchPadding = AndroidUtilities.dp(16);

        topLeftCorner.set(actualRect.left - touchPadding, actualRect.top - touchPadding, actualRect.left + touchPadding, actualRect.top + touchPadding);
        topRightCorner.set(actualRect.right - touchPadding, actualRect.top - touchPadding, actualRect.right + touchPadding, actualRect.top + touchPadding);
        bottomLeftCorner.set(actualRect.left - touchPadding, actualRect.bottom - touchPadding, actualRect.left + touchPadding, actualRect.bottom + touchPadding);
        bottomRightCorner.set(actualRect.right - touchPadding, actualRect.bottom - touchPadding, actualRect.right + touchPadding, actualRect.bottom + touchPadding);

        topEdge.set(actualRect.left + touchPadding, actualRect.top - touchPadding, actualRect.right - touchPadding, actualRect.top + touchPadding);
        leftEdge.set(actualRect.left - touchPadding, actualRect.top + touchPadding, actualRect.left + touchPadding, actualRect.bottom - touchPadding);
        rightEdge.set(actualRect.right - touchPadding, actualRect.top + touchPadding, actualRect.right + touchPadding, actualRect.bottom - touchPadding);
        bottomEdge.set(actualRect.left + touchPadding, actualRect.bottom - touchPadding, actualRect.right - touchPadding, actualRect.bottom + touchPadding);
    }

    public float getLockAspectRatio() {
        return lockAspectRatio;
    }

    public void setLockedAspectRatio(float aspectRatio) {
        lockAspectRatio = aspectRatio;
    }

    public void setGridType(GridType type, boolean animated) {
        if (gridAnimator != null) {
            if (!animated || gridType != type) {
                gridAnimator.cancel();
                gridAnimator = null;
            }
        }

        if (gridType == type)
            return;

        previousGridType = gridType;
        gridType = type;

        final float targetProgress = type == GridType.NONE ? 0.0f : 1.0f;
        if (!animated) {
            gridProgress = targetProgress;
            invalidate();
        } else {
            gridAnimator = ObjectAnimator.ofFloat(this, "gridProgress", gridProgress, targetProgress);
            gridAnimator.setDuration(200);
            gridAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    gridAnimator = null;
                }
            });
            if (type == GridType.NONE)
                gridAnimator.setStartDelay(200);
            gridAnimator.start();
        }
    }

    @SuppressWarnings("unused")
    private void setGridProgress(float value) {
        gridProgress = value;
        invalidate();
    }

    @SuppressWarnings("unused")
    private float getGridProgress() {
        return gridProgress;
    }

    public float getAspectRatio() {
        return (actualRect.right - actualRect.left) / (actualRect.bottom - actualRect.top);
    }

    public void fill(final RectF targetRect, Animator scaleAnimator, boolean animated) {
        if (animated) {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }

            AnimatorSet set = new AnimatorSet();
            animator = set;
            set.setDuration(300);

            Animator animators[] = new Animator[5];
            animators[0] = ObjectAnimator.ofFloat(this, "cropLeft", targetRect.left);
            animators[0].setInterpolator(interpolator);
            animators[1] = ObjectAnimator.ofFloat(this, "cropTop", targetRect.top);
            animators[1].setInterpolator(interpolator);
            animators[2] = ObjectAnimator.ofFloat(this, "cropRight", targetRect.right);
            animators[2].setInterpolator(interpolator);
            animators[3] = ObjectAnimator.ofFloat(this, "cropBottom", targetRect.bottom);
            animators[3].setInterpolator(interpolator);
            animators[4] = scaleAnimator;
            animators[4].setInterpolator(interpolator);

            set.playTogether(animators);
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setActualRect(targetRect);
                    animator = null;
                }
            });
            set.start();
        } else {
            setActualRect(targetRect);
        }
    }

    public void resetAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @SuppressWarnings("unused")
    private void setCropLeft(float value) {
        actualRect.left = value;
        invalidate();
    }

    @SuppressWarnings("unused")
    public float getCropLeft() {
        return actualRect.left;
    }

    @SuppressWarnings("unused")
    private void setCropTop(float value) {
        actualRect.top = value;
        invalidate();
    }

    @SuppressWarnings("unused")
    public float getCropTop() {
        return actualRect.top;
    }

    @SuppressWarnings("unused")
    private void setCropRight(float value) {
        actualRect.right = value;
        invalidate();
    }

    public float getCropRight() {
        return actualRect.right;
    }

    @SuppressWarnings("unused")
    private void setCropBottom(float value) {
        actualRect.bottom = value;
        invalidate();
    }

    public float getCropBottom() {
        return actualRect.bottom;
    }

    public float getCropCenterX() {
        return actualRect.left + ((actualRect.right - actualRect.left) / 2.0f);
    }

    public float getCropCenterY() {
        return actualRect.top + ((actualRect.bottom - actualRect.top) / 2.0f);
    }

    public float getCropWidth() {
        return actualRect.right - actualRect.left;
    }

    public float getCropHeight() {
        return actualRect.bottom - actualRect.top;
    }

    public RectF getTargetRectToFill() {
        RectF rect = new RectF();
        calculateRect(rect, getAspectRatio());
        return rect;
    }

    public void calculateRect(RectF rect, float cropAspectRatio) {
        float statusBarHeight = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
        float left, top, right, bottom;
        float measuredHeight = (float)getMeasuredHeight() - bottomPadding - statusBarHeight;
        float aspectRatio = (float)getMeasuredWidth() / measuredHeight;
        float minSide = Math.min(getMeasuredWidth(), measuredHeight) - 2 * sidePadding;
        float width = getMeasuredWidth() - 2 * sidePadding;
        float height = measuredHeight - 2 * sidePadding;
        float centerX = getMeasuredWidth() / 2.0f;
        float centerY = statusBarHeight + measuredHeight / 2.0f;

        if (Math.abs(1.0f - cropAspectRatio) < 0.0001) {
            left = centerX - (minSide / 2.0f);
            top = centerY - (minSide / 2.0f);
            right = centerX + (minSide / 2.0f);
            bottom = centerY + (minSide / 2.0f);
        } else if (cropAspectRatio > aspectRatio) {
            left = centerX - (width / 2.0f);
            top = centerY - ((width / cropAspectRatio) / 2.0f);
            right = centerX + (width / 2.0f);
            bottom = centerY + ((width / cropAspectRatio) / 2.0f);
        } else {
            left = centerX - ((height * cropAspectRatio) / 2.0f);
            top = centerY - (height / 2.0f);
            right = centerX + ((height * cropAspectRatio) / 2.0f);
            bottom = centerY + (height / 2.0f);
        }
        rect.set(left, top, right, bottom);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int x = (int)(event.getX() - ((ViewGroup)getParent()).getX());
        int y = (int)(event.getY() - ((ViewGroup)getParent()).getY());

        float statusBarHeight = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);

        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            if (this.topLeftCorner.contains(x, y)) {
                activeControl = Control.TOP_LEFT;
            } else if (this.topRightCorner.contains(x, y)) {
                activeControl = Control.TOP_RIGHT;
            } else if (this.bottomLeftCorner.contains(x, y)) {
                activeControl = Control.BOTTOM_LEFT;
            } else if (this.bottomRightCorner.contains(x, y)) {
                activeControl = Control.BOTTOM_RIGHT;
            } else if (this.leftEdge.contains(x, y)) {
                activeControl = Control.LEFT;
            } else if (this.topEdge.contains(x, y)) {
                activeControl = Control.TOP;
            } else if (this.rightEdge.contains(x, y)) {
                activeControl = Control.RIGHT;
            } else if (this.bottomEdge.contains(x, y)) {
                activeControl = Control.BOTTOM;
            } else {
                activeControl = Control.NONE;
                return false;
            }
            previousX = x;
            previousY = y;
            setGridType(GridType.MAJOR, false);

            isDragging = true;

            if (listener != null)
                listener.onAreaChangeBegan();

            return true;
        }
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            isDragging = false;

            if (activeControl == Control.NONE)
                return false;

            activeControl = Control.NONE;

            if (listener != null)
                listener.onAreaChangeEnded();

            return true;
        }
        else if (action == MotionEvent.ACTION_MOVE) {
            if (activeControl == Control.NONE)
                return false;

            tempRect.set(actualRect);

            float translationX = x - previousX;
            float translationY = y - previousY;
            previousX = x;
            previousY = y;

            switch (activeControl) {
                case TOP_LEFT:
                    tempRect.left += translationX;
                    tempRect.top += translationY;

                    if (lockAspectRatio > 0) {
                        float w = tempRect.width();
                        float h = tempRect.height();

                        if (Math.abs(translationX) > Math.abs(translationY)) {
                            constrainRectByWidth(tempRect, lockAspectRatio);
                        } else {
                            constrainRectByHeight(tempRect, lockAspectRatio);
                        }

                        tempRect.left -= tempRect.width() - w;
                        tempRect.top -= tempRect.width() - h;
                    }
                    break;

                case TOP_RIGHT:
                    tempRect.right += translationX;
                    tempRect.top += translationY;

                    if (lockAspectRatio > 0) {
                        float h = tempRect.height();

                        if (Math.abs(translationX) > Math.abs(translationY)) {
                            constrainRectByWidth(tempRect, lockAspectRatio);
                        } else {
                            constrainRectByHeight(tempRect, lockAspectRatio);
                        }

                        tempRect.top -= tempRect.width() - h;
                    }
                    break;

                case BOTTOM_LEFT:
                    tempRect.left += translationX;
                    tempRect.bottom += translationY;

                    if (lockAspectRatio > 0) {
                        float w = tempRect.width();

                        if (Math.abs(translationX) > Math.abs(translationY)) {
                            constrainRectByWidth(tempRect, lockAspectRatio);
                        } else {
                            constrainRectByHeight(tempRect, lockAspectRatio);
                        }

                        tempRect.left -= tempRect.width() - w;
                    }
                    break;

                case BOTTOM_RIGHT:
                    tempRect.right += translationX;
                    tempRect.bottom += translationY;

                    if (lockAspectRatio > 0) {
                        if (Math.abs(translationX) > Math.abs(translationY)) {
                            constrainRectByWidth(tempRect, lockAspectRatio);
                        } else {
                            constrainRectByHeight(tempRect, lockAspectRatio);
                        }
                    }
                    break;

                case TOP:
                    tempRect.top += translationY;

                    if (lockAspectRatio > 0) {
                        constrainRectByHeight(tempRect, lockAspectRatio);
                    }
                    break;

                case LEFT:
                    tempRect.left += translationX;

                    if (lockAspectRatio > 0) {
                        constrainRectByWidth(tempRect, lockAspectRatio);
                    }
                    break;

                case RIGHT:
                    tempRect.right += translationX;

                    if (lockAspectRatio > 0) {
                        constrainRectByWidth(tempRect, lockAspectRatio);
                    }
                    break;

                case BOTTOM:
                    tempRect.bottom += translationY;

                    if (lockAspectRatio > 0) {
                        constrainRectByHeight(tempRect, lockAspectRatio);
                    }
                    break;

                default:
                    break;
            }

            if (tempRect.left < sidePadding) {
                if (lockAspectRatio > 0) {
                    tempRect.bottom = tempRect.top + (tempRect.right - sidePadding) / lockAspectRatio;
                }
                tempRect.left = sidePadding;
            } else if (tempRect.right > getWidth() - sidePadding) {
                tempRect.right = getWidth() - sidePadding;
                if (lockAspectRatio > 0) {
                    tempRect.bottom = tempRect.top + tempRect.width() / lockAspectRatio;
                }
            }

            float topPadding = statusBarHeight + sidePadding;
            float finalBottomPadidng = bottomPadding + sidePadding;
            if (tempRect.top < topPadding) {
                if (lockAspectRatio > 0) {
                    tempRect.right = tempRect.left + (tempRect.bottom - topPadding) * lockAspectRatio;
                }
                tempRect.top = topPadding;
            } else if (tempRect.bottom > getHeight() - finalBottomPadidng) {
                tempRect.bottom = getHeight() - finalBottomPadidng;
                if (lockAspectRatio > 0) {
                    tempRect.right = tempRect.left + tempRect.height() * lockAspectRatio;
                }
            }

            if (tempRect.width() < minWidth) {
                tempRect.right = tempRect.left + minWidth;
            }
            if (tempRect.height() < minWidth) {
                tempRect.bottom = tempRect.top + minWidth;
            }

            if (lockAspectRatio > 0) {
                if (lockAspectRatio < 1) {
                    if (tempRect.width() <= minWidth) {
                        tempRect.right = tempRect.left + minWidth;
                        tempRect.bottom = tempRect.top + tempRect.width() / lockAspectRatio;
                    }
                } else {
                    if (tempRect.height() <= minWidth) {
                        tempRect.bottom = tempRect.top + minWidth;
                        tempRect.right = tempRect.left + tempRect.height() * lockAspectRatio;
                    }
                }
            }

            setActualRect(tempRect);

            if (listener != null) {
                listener.onAreaChange();
            }

            return true;
        }

        return false;
    }

    private void constrainRectByWidth(RectF rect, float aspectRatio) {
        float w = rect.width();
        float h = w / aspectRatio;

        rect.right = rect.left + w;
        rect.bottom = rect.top + h;
    }

    private void constrainRectByHeight(RectF rect, float aspectRatio) {
        float h = rect.height();
        float w = h * aspectRatio;

        rect.right = rect.left + w;
        rect.bottom = rect.top + h;
    }

    public void getCropRect(RectF rect) {
        rect.set(actualRect);
    }
}