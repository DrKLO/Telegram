package org.telegram.ui.Components.Premium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.OverScroller;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class CarouselView extends View implements PagerHeaderView {

    private final ArrayList<? extends DrawingObject> drawingObjects;
    private final ArrayList<? extends DrawingObject> drawingObjectsSorted;

    float lastFlingX;
    float lastFlingY;
    int cX, cY;

    float offsetAngle = 0f;
    boolean firstScroll = true;
    boolean firstScroll1 = true;
    boolean firstScrollEnabled = true;
    GestureDetector gestureDetector;
    boolean autoPlayEnabled = true;
    static final Interpolator sQuinticInterpolator = t -> {
        t -= 1.0f;
        return t * t * t * t * t + 1.0f;
    };

    Comparator<DrawingObject> comparator = Comparator.comparingInt(value -> (int) (value.yRelative * 100));

    OverScroller overScroller;
    ValueAnimator autoScrollAnimation;
    boolean scrolled;
    private Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoPlayEnabled) {
                return;
            }
            scrollToInternal(offsetAngle + 360f / drawingObjects.size());
        }
    };

    public CarouselView(Context context, ArrayList<? extends DrawingObject> drawingObjects) {
        super(context);
        overScroller = new OverScroller(getContext(), sQuinticInterpolator);
        gestureDetector = new GestureDetector(context, new GestureDetector.OnGestureListener() {

            double lastAngle;

            @Override
            public boolean onDown(MotionEvent motionEvent) {
                if (motionEvent.getY() > 0.2 * getMeasuredHeight() && motionEvent.getY() < 0.9 * getMeasuredHeight()) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (autoScrollAnimation != null) {
                    autoScrollAnimation.removeAllListeners();
                    autoScrollAnimation.cancel();
                    autoScrollAnimation = null;
                }
                AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
                overScroller.abortAnimation();
                lastAngle = Math.atan2((motionEvent.getX() - cX), (motionEvent.getY() - cY));
                float aStep = 360f / drawingObjects.size();
                lastSelected = ((int) (offsetAngle / aStep));
                for (int i = 0; i < drawingObjects.size(); i++) {
                    drawingObjects.get(i).hideAnimation();
                }
                return true;
            }

            @Override
            public void onShowPress(MotionEvent motionEvent) {

            }

            @Override
            public boolean onSingleTapUp(MotionEvent motionEvent) {
                float x = motionEvent.getX();
                float y = motionEvent.getY();
                for (int i = drawingObjectsSorted.size() - 1; i >= 0; i--) {
                    if (drawingObjectsSorted.get(i).checkTap(x, y)) {

                        if (drawingObjectsSorted.get(i).angle % 360 != 270) {
                            double toAngle = (270 - drawingObjectsSorted.get(i).angle % 360 + 180) % 360;
                            if (toAngle > 180.0) {
                                toAngle = -(360 - toAngle);
                            }
                            scrollToInternal(offsetAngle + (float) toAngle);
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float dx, float dy) {
                double angle = Math.atan2((motionEvent1.getX() - cX), (motionEvent1.getY() - cY));
                double dAngle = lastAngle - angle;
                lastAngle = angle;
                offsetAngle += Math.toDegrees(dAngle);
                checkSelectedHaptic();
                invalidate();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent motionEvent) {

            }

            @Override
            public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float velocityX, float velocityY) {
                lastFlingX = lastFlingY = 0;
                double angle = Math.atan2((motionEvent1.getX() - cX), (motionEvent1.getY() - cY));
                float xVelocity = (float) (Math.cos(angle) * velocityX - Math.sin(angle) * velocityY);
                overScroller.fling(0, 0, (int) xVelocity, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
                if (overScroller.isFinished()) {
                    scheduleAutoscroll();
                }
                invalidate();
                return true;
            }

        });
        this.drawingObjects = drawingObjects;
        this.drawingObjectsSorted = new ArrayList<>(drawingObjects);
        for (int i = 0; i < drawingObjects.size() / 2; i++) {
            drawingObjects.get(i).y = drawingObjects.size() / (float) i;
            drawingObjects.get(drawingObjects.size() - 1 - i).y = drawingObjects.size() / (float) i;
        }
        Collections.sort(drawingObjects, comparator);
        for (int i = 0; i < drawingObjects.size(); i++) {
            drawingObjects.get(i).carouselView = this;
        }
    }

    private void checkSelectedHaptic() {
        float aStep = 360f / drawingObjects.size();
        int selected = ((int) (offsetAngle / aStep));
        if (lastSelected != selected) {
            lastSelected = selected;
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void scrollToInternal(float scrollTo) {
        if (Math.abs(scrollTo - offsetAngle) < 1 && autoScrollAnimation == null) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
        if (autoScrollAnimation != null) {
            autoScrollAnimation.removeAllListeners();
            autoScrollAnimation.cancel();
            autoScrollAnimation = null;
        }
        float from = offsetAngle;
        autoScrollAnimation = ValueAnimator.ofFloat(0f, 1f);
        autoScrollAnimation.addUpdateListener(animation -> {
            float f = (float) animation.getAnimatedValue();
            offsetAngle = from * (1f - f) + scrollTo * f;
            invalidate();
        });
        autoScrollAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                offsetAngle = scrollTo;
                autoScrollAnimation = null;
                invalidate();
                AndroidUtilities.runOnUIThread(() -> {
                    if (!drawingObjectsSorted.isEmpty()) {
                        drawingObjectsSorted.get(drawingObjectsSorted.size() - 1).select();
                    }
                    scheduleAutoscroll();
                });
            }
        });
        autoScrollAnimation.setInterpolator(new OvershootInterpolator());
        autoScrollAnimation.setDuration(600);
        autoScrollAnimation.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            scrolled = true;
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            scrolled = false;
            getParent().requestDisallowInterceptTouchEvent(false);
            invalidate();
        }
        return gestureDetector.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        cX = getMeasuredWidth() >> 1;
        cY = getMeasuredHeight() >> 1;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < drawingObjectsSorted.size(); i++) {
                drawingObjectsSorted.get(i).onAttachToWindow(this, k);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int i = 0; i < drawingObjects.size(); i++) {
            drawingObjects.get(i).onDetachFromWindow();
        }
    }

    int lastSelected;

    @Override
    protected void onDraw(Canvas canvas) {
        double aStep = 360.0 / drawingObjects.size();

        if (overScroller.computeScrollOffset()) {
            //fling
            final int x = overScroller.getCurrX();
            float dx = lastFlingX - x;
            if (lastFlingX != 0 && Math.abs(dx * 0.08f) < 0.3f) {
                overScroller.abortAnimation();
            }
            lastFlingX = x;
            offsetAngle += dx * 0.08f;
            checkSelectedHaptic();
            invalidate();
            scheduleAutoscroll();
        } else if (firstScroll1 || firstScroll || (!scrolled && autoScrollAnimation == null && Math.abs((offsetAngle - 90) % aStep) > 2)) {
            if (firstScroll1) {
                offsetAngle += 90 + aStep;
            }
            float dif = (float) ((offsetAngle - 90) % aStep);
            if (Math.abs(dif) > aStep / 2) {
                if (dif < 0) {
                    dif += aStep;
                } else {
                    dif -= aStep;
                }
            }
            firstScroll1 = false;
            if (firstScroll && firstScrollEnabled) {
                firstScroll = false;
                offsetAngle -= 180;
                scrollToInternal(offsetAngle - dif + 180);
            } else {
                scrollToInternal(offsetAngle - dif);
            }
        }

        float r = (Math.min(getMeasuredWidth(), getMeasuredHeight()  * 1.3f) - AndroidUtilities.dp(140)) * 0.5f;
        float rY = r * 0.6f;
        for (int i = 0; i < drawingObjects.size(); i++) {
            DrawingObject object = drawingObjects.get(i);
            object.angle = offsetAngle + aStep * i;
            double s = Math.cos(Math.toRadians(object.angle));
            double p = s;
            double totalAngle = object.angle - 30.0 * p;
            object.x = (float) Math.cos(Math.toRadians(totalAngle)) * r + cX;
            object.yRelative = (float) Math.sin(Math.toRadians(totalAngle));
            object.y = object.yRelative * rY + cY;
        }

        Collections.sort(drawingObjectsSorted, comparator);

        for (int i = 0; i < drawingObjectsSorted.size(); i++) {
            DrawingObject object = drawingObjectsSorted.get(i);
            float s = 0.2f + 0.7f * (object.yRelative + 1f) / 2f;
            object.draw(canvas, object.x, object.y, s);
        }
        invalidate();
    }

    void scheduleAutoscroll() {
        AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
        if (!autoPlayEnabled) {
            return;
        }
        AndroidUtilities.runOnUIThread(autoScrollRunnable, 3000);
    }

    @Override
    public void setOffset(float translationX) {
        if (translationX >= getMeasuredWidth() || translationX <= -getMeasuredWidth()) {
            overScroller.abortAnimation();
            if (autoScrollAnimation != null) {
                autoScrollAnimation.removeAllListeners();
                autoScrollAnimation.cancel();
                autoScrollAnimation = null;
            }
            firstScroll = true;
            firstScroll1 = true;
            offsetAngle = 0;
        }
        setAutoPlayEnabled(translationX == 0);
        setFirstScrollEnabled(Math.abs(translationX) < getMeasuredWidth() * 0.2f);
        float s = Utilities.clamp(Math.abs(translationX) / getMeasuredWidth(), 1f, 0f);
        setScaleX(1f - s);
        setScaleY(1f - s);
    }

    public void autoplayToNext() {
        AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
        if (!autoPlayEnabled) {
            return;
        }
        DrawingObject drawingObject = drawingObjectsSorted.get(drawingObjectsSorted.size() - 1);
        int i = drawingObjects.indexOf(drawingObject);
        i--;
        if (i < 0) {
            i = drawingObjects.size() - 1;
        }
        drawingObjects.get(i).select();
        AndroidUtilities.runOnUIThread(autoScrollRunnable, 16);
    }

    public static class DrawingObject {

        public float x, y;
        public double angle;
        float yRelative;
        CarouselView carouselView;

        public DrawingObject() {
        }

        public void onAttachToWindow(View parentView, int i) {

        }

        public void onDetachFromWindow() {

        }

        public void draw(Canvas canvas, float cX, float cY, float scale) {

        }

        public boolean checkTap(float x, float y) {
            return false;
        }

        public void select() {

        }

        public void hideAnimation() {

        }
    }


    void setAutoPlayEnabled(boolean autoPlayEnabled) {
        if (this.autoPlayEnabled != autoPlayEnabled) {
            this.autoPlayEnabled = autoPlayEnabled;
            if (autoPlayEnabled) {
                scheduleAutoscroll();
            } else {
                AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
            }
            invalidate();
        }
    }

    void setFirstScrollEnabled(boolean b) {
        if (firstScrollEnabled != b) {
            this.firstScrollEnabled = b;
            invalidate();
        }
    }
}

