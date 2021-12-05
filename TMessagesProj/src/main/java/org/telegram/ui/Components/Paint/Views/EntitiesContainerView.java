package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

public class EntitiesContainerView extends FrameLayout implements ScaleGestureDetector.OnScaleGestureListener, RotationGestureDetector.OnRotationGestureListener {

    public interface EntitiesContainerViewDelegate {
        boolean shouldReceiveTouches();
        void onEntityDeselect();
        EntityView onSelectedEntityRequest();
    }

    private EntitiesContainerViewDelegate delegate;
    private ScaleGestureDetector gestureDetector;
    private RotationGestureDetector rotationGestureDetector;
    private float previousScale = 1.0f;
    private float previousAngle;
    private boolean hasTransformed;

    public EntitiesContainerView(Context context, EntitiesContainerViewDelegate entitiesContainerViewDelegate) {
        super(context);

        gestureDetector = new ScaleGestureDetector(context, this);
        rotationGestureDetector = new RotationGestureDetector(this);
        delegate = entitiesContainerViewDelegate;
    }

    public int entitiesCount() {
        int count = 0;
        for (int index = 0; index < getChildCount(); index++) {
            View view = getChildAt(index);
            if (!(view instanceof EntityView)) {
                continue;
            }
            count++;
        }
        return count;
    }

    public void bringViewToFront(EntityView view) {
        if (indexOfChild(view) != getChildCount() - 1) {
            removeView(view);
            addView(view, getChildCount());
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return ev.getPointerCount() == 2 && delegate.shouldReceiveTouches();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        EntityView selectedEntity = delegate.onSelectedEntityRequest();
        if (selectedEntity == null) {
            return false;
        }

        if (event.getPointerCount() == 1) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                hasTransformed = false;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_MOVE) {
                if (!hasTransformed && delegate != null) {
                    delegate.onEntityDeselect();
                }
                return false;
            }
        }

        gestureDetector.onTouchEvent(event);
        rotationGestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float sf = detector.getScaleFactor();
        float newScale = sf / previousScale;

        EntityView view = delegate.onSelectedEntityRequest();
        view.scale(newScale);

        previousScale = sf;

        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        previousScale = 1.0f;
        hasTransformed = true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    @Override
    public void onRotationBegin(RotationGestureDetector rotationDetector) {
        previousAngle = rotationDetector.getStartAngle();
        hasTransformed = true;
    }

    @Override
    public void onRotation(RotationGestureDetector rotationDetector) {
        EntityView view = delegate.onSelectedEntityRequest();
        float angle = rotationDetector.getAngle();
        float delta = previousAngle - angle;
        view.rotate(view.getRotation() + delta);
        previousAngle = angle;
    }

    @Override
    public void onRotationEnd(RotationGestureDetector rotationDetector) {

    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
        if (child instanceof TextPaintView) {
            final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin + widthUsed, lp.width);
            child.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        } else {
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed);
        }
    }
}
