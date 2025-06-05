package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;

public class EntitiesContainerView extends FrameLayout {

    public boolean drawForThumb;

    public interface EntitiesContainerViewDelegate {
        boolean shouldReceiveTouches();
        void onEntityDeselect();
        EntityView onSelectedEntityRequest();
    }

    private EntitiesContainerViewDelegate delegate;
    private float previousScale = 1.0f;
    private float previousAngle;
    private boolean hasTransformed;

    public EntitiesContainerView(Context context, EntitiesContainerViewDelegate entitiesContainerViewDelegate) {
        super(context);
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

    private float px, py;
    private boolean cancelled;

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
                selectedEntity.hasPanned = false;
                selectedEntity.hasReleased = false;
                px = event.getX();
                py = event.getY();
                cancelled = false;
            } else if (!cancelled && action == MotionEvent.ACTION_MOVE) {
                final float x = event.getX();
                final float y = event.getY();
                if (hasTransformed || MathUtils.distance(x, y, px, py) > AndroidUtilities.touchSlop) {
                    hasTransformed = true;
                    selectedEntity.hasPanned = true;
                    selectedEntity.pan(x - px, y - py);
                    px = x;
                    py = y;
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                selectedEntity.hasPanned = false;
                selectedEntity.hasReleased = true;
                if (!hasTransformed && delegate != null) {
                    delegate.onEntityDeselect();
                }
                invalidate();
                return false;
            }
        } else {
            selectedEntity.hasPanned = false;
            selectedEntity.hasReleased = true;
            hasTransformed = false;
            cancelled = true;
            invalidate();
        }
        return true;
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

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (drawForThumb && child instanceof ReactionWidgetEntityView) {
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }
}
