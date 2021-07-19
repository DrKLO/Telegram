package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.Rect;

import java.util.UUID;

public class EntityView extends FrameLayout {

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

    protected Point position;
    protected SelectionView selectionView;

    private GestureDetector gestureDetector;

    private UUID uuid;

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

    public void pan(float tx, float ty) {
        position.x += tx;
        position.y += ty;
        updatePosition();
    }

    protected void updatePosition() {
        float halfWidth = getMeasuredWidth() / 2.0f;
        float halfHeight = getMeasuredHeight() / 2.0f;
        setX(position.x - halfWidth);
        setY(position.y - halfHeight);
        updateSelectionView();
    }

    public void scale(float scale) {
        float newScale = Math.max(getScale() * scale, 0.1f);
        setScale(newScale);
        updateSelectionView();
    }

    public void rotate(float angle) {
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
        this.selectionView = selectionView;
        selectionContainer.addView(selectionView);
        selectionView.updatePosition();
    }

    public void deselect() {
        if (selectionView == null) {
            return;
        }
        if (selectionView.getParent() != null) {
            ((ViewGroup) selectionView.getParent()).removeView(selectionView);
        }
        selectionView = null;
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

            dotPaint.setColor(0xff3ccaef);
            dotStrokePaint.setColor(0xffffffff);
            dotStrokePaint.setStyle(Paint.Style.STROKE);
            dotStrokePaint.setStrokeWidth(AndroidUtilities.dp(1));
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

                            float scaleDelta = 1 + (delta * 2) / getMeasuredWidth();
                            scale(scaleDelta);

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
