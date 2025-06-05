package org.telegram.ui.Charts;

import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.view.MotionEvent;

import org.telegram.messenger.AndroidUtilities;

public class ChartPickerDelegate {

    public boolean disabled;
    Listener view;

    public ChartPickerDelegate(Listener view) {
        this.view = view;
    }


    private final static int CAPTURE_NONE = 0;
    private final static int CAPTURE_LEFT = 1;
    private final static int CAPTURE_RIGHT = 1 << 1;
    private final static int CAPTURE_MIDDLE = 1 << 2;
    public float pickerWidth;
    public boolean tryMoveTo;
    public float moveToX;
    public float moveToY;
    public long startTapTime;
    ValueAnimator moveToAnimator;

    public Rect leftPickerArea = new Rect();
    public Rect rightPickerArea = new Rect();
    public Rect middlePickerArea = new Rect();


    public float pickerStart = 0.7f;
    public float pickerEnd = 1f;

    public float minDistance = 0.1f;


    public CapturesData getMiddleCaptured() {
        if (capturedStates[0] != null && capturedStates[0].state == CAPTURE_MIDDLE)
            return capturedStates[0];
        if (capturedStates[1] != null && capturedStates[1].state == CAPTURE_MIDDLE)
            return capturedStates[1];
        return null;
    }

    public CapturesData getLeftCaptured() {
        if (capturedStates[0] != null && capturedStates[0].state == CAPTURE_LEFT)
            return capturedStates[0];
        if (capturedStates[1] != null && capturedStates[1].state == CAPTURE_LEFT)
            return capturedStates[1];
        return null;
    }

    public CapturesData getRightCaptured() {
        if (capturedStates[0] != null && capturedStates[0].state == CAPTURE_RIGHT)
            return capturedStates[0];
        if (capturedStates[1] != null && capturedStates[1].state == CAPTURE_RIGHT)
            return capturedStates[1];
        return null;
    }


    class CapturesData {
        public final int state;
        public int capturedX;
        public int lastMovingX;
        public float start;
        public float end;

        ValueAnimator a;
        ValueAnimator jumpToAnimator;
        public float aValue = 0f;

        public CapturesData(int state) {
            this.state = state;
        }

        public void captured() {
            a = ValueAnimator.ofFloat(0, 1f);
            a.setDuration(600);
            a.setInterpolator(BaseChartView.INTERPOLATOR);
            a.addUpdateListener(animation -> {
                aValue = (float) animation.getAnimatedValue();
                view.invalidate();
            });
            a.start();
        }

        public void uncapture() {
            if (a != null) a.cancel();
            if (jumpToAnimator != null) jumpToAnimator.cancel();
        }
    }

    CapturesData[] capturedStates = {null, null};

    public boolean capture(int x, int y, int pointerIndex) {
        if (disabled) {
            return false;
        }
        if (pointerIndex == 0) {
            if (leftPickerArea.contains(x, y)) {
                if (capturedStates[0] != null) capturedStates[1] = capturedStates[0];
                capturedStates[0] = new CapturesData(CAPTURE_LEFT);
                capturedStates[0].start = pickerStart;
                capturedStates[0].capturedX = x;
                capturedStates[0].lastMovingX = x;
                capturedStates[0].captured();

                if (moveToAnimator != null) {
                    moveToAnimator.cancel();
                }
                return true;
            }

            if (rightPickerArea.contains(x, y)) {
                if (capturedStates[0] != null) capturedStates[1] = capturedStates[0];
                capturedStates[0] = new CapturesData(CAPTURE_RIGHT);
                capturedStates[0].end = pickerEnd;
                capturedStates[0].capturedX = x;
                capturedStates[0].lastMovingX = x;
                capturedStates[0].captured();

                if (moveToAnimator != null) moveToAnimator.cancel();
                return true;
            }


            if (middlePickerArea.contains(x, y)) {
                capturedStates[0] = new CapturesData(CAPTURE_MIDDLE);
                capturedStates[0].end = pickerEnd;
                capturedStates[0].start = pickerStart;
                capturedStates[0].capturedX = x;
                capturedStates[0].lastMovingX = x;
                capturedStates[0].captured();
                if (moveToAnimator != null) moveToAnimator.cancel();
                return true;
            }


            if (y < leftPickerArea.bottom && y > leftPickerArea.top) {
                tryMoveTo = true;
                moveToX = x;
                moveToY = y;
                startTapTime = System.currentTimeMillis();
                if (moveToAnimator != null) {
                    if (moveToAnimator.isRunning()) {
                        view.onPickerJumpTo(pickerStart, pickerEnd, true);
                    }
                    moveToAnimator.cancel();
                }
                return true;
            }
        } else if (pointerIndex == 1) {
            if (capturedStates[0] == null) return false;
            if (capturedStates[0].state == CAPTURE_MIDDLE) return false;


            if (leftPickerArea.contains(x, y) && capturedStates[0].state != CAPTURE_LEFT) {
                capturedStates[1] = new CapturesData(CAPTURE_LEFT);
                capturedStates[1].start = pickerStart;
                capturedStates[1].capturedX = x;
                capturedStates[1].lastMovingX = x;
                capturedStates[1].captured();
                if (moveToAnimator != null) moveToAnimator.cancel();
                return true;
            }

            if (rightPickerArea.contains(x, y)) {
                if (capturedStates[0].state == CAPTURE_RIGHT) return false;
                capturedStates[1] = new CapturesData(CAPTURE_RIGHT);
                capturedStates[1].end = pickerEnd;
                capturedStates[1].capturedX = x;
                capturedStates[1].lastMovingX = x;
                capturedStates[1].captured();
                if (moveToAnimator != null) moveToAnimator.cancel();
                return true;
            }
        }
        return false;
    }

    public boolean captured() {
        return capturedStates[0] != null || tryMoveTo;
    }

    public boolean move(int x, int y, int pointer) {
        if (tryMoveTo) {
            return false;
        }
        CapturesData d = capturedStates[pointer];
        if (d == null) return false;
        int capturedState = d.state;
        float capturedStart = d.start;
        float capturedEnd = d.end;
        int capturedX = d.capturedX;
        d.lastMovingX = x;

        boolean notifyPicker = false;
        if (capturedState == CAPTURE_LEFT) {
            pickerStart = capturedStart - (capturedX - x) / (float) pickerWidth;
            if (pickerStart < 0f) pickerStart = 0f;
            if (pickerEnd - pickerStart < minDistance) pickerStart = pickerEnd - minDistance;
            notifyPicker = true;
        }

        if (capturedState == CAPTURE_RIGHT) {
            pickerEnd = capturedEnd - (capturedX - x) / (float) pickerWidth;
            if (pickerEnd > 1f) pickerEnd = 1f;
            if (pickerEnd - pickerStart < minDistance) pickerEnd = pickerStart + minDistance;
            notifyPicker = true;
        }

        if (capturedState == CAPTURE_MIDDLE) {
            pickerStart = capturedStart - (capturedX - x) / (float) pickerWidth;
            pickerEnd = capturedEnd - (capturedX - x) / (float) pickerWidth;
            if (pickerStart < 0f) {
                pickerStart = 0f;
                pickerEnd = capturedEnd - capturedStart;
            }

            if (pickerEnd > 1f) {
                pickerEnd = 1f;
                pickerStart = 1f - (capturedEnd - capturedStart);
            }

            notifyPicker = true;
        }
        if (notifyPicker) view.onPickerDataChanged();
        return true;
    }

    public void set(float start, float end) {
        pickerStart = start;
        pickerEnd = end;
        view.onPickerDataChanged();
    }

    public boolean uncapture(MotionEvent event, int pointerIndex) {
        if (pointerIndex == 0) {
            if (tryMoveTo) {
                tryMoveTo = false;
                float dx = moveToX - event.getX();
                float dy = moveToY - event.getY();
                if (event.getAction() == MotionEvent.ACTION_UP && System.currentTimeMillis() - startTapTime < 300 && Math.sqrt(dx * dx + dy * dy) < AndroidUtilities.dp(10)) {

                    float moveToX = (this.moveToX - BaseChartView.HORIZONTAL_PADDING) / pickerWidth;
                    float w = pickerEnd - pickerStart;
                    float moveToLeft = moveToX - w / 2f;
                    float moveToRight = moveToX + w / 2f;
                    if (moveToLeft < 0f) {
                        moveToLeft = 0;
                        moveToRight = w;
                    } else if (moveToRight > 1f) {
                        moveToLeft = 1f - w;
                        moveToRight = 1f;

                    }
                    float moveFromLeft = pickerStart;
                    float moveFromRight = pickerEnd;

                    moveToAnimator = ValueAnimator.ofFloat(0f, 1f);
                    float finalMoveToLeft = moveToLeft;
                    float finalMoveToRight = moveToRight;
                    view.onPickerJumpTo(finalMoveToLeft, finalMoveToRight,true);
                    moveToAnimator.addUpdateListener(animation -> {
                        float v = (float) animation.getAnimatedValue();
                        pickerStart = moveFromLeft + (finalMoveToLeft - moveFromLeft) * v;
                        pickerEnd = moveFromRight + (finalMoveToRight - moveFromRight) * v;
                        view.onPickerJumpTo(finalMoveToLeft, finalMoveToRight,false);
                    });
                    moveToAnimator.setInterpolator(BaseChartView.INTERPOLATOR);
                    moveToAnimator.start();
                }
                return true;
            }

            if (capturedStates[0] != null) capturedStates[0].uncapture();
            capturedStates[0] = null;
            if (capturedStates[1] != null) {
                capturedStates[0] = capturedStates[1];
                capturedStates[1] = null;
            }
        } else {
            if (capturedStates[1] != null) capturedStates[1].uncapture();
            capturedStates[1] = null;
        }
        return false;
    }

    public void uncapture() {
        if (capturedStates[0] != null) capturedStates[0].uncapture();
        if (capturedStates[1] != null) capturedStates[1].uncapture();
        capturedStates[0] = null;
        capturedStates[1] = null;
    }

    interface Listener {
        void onPickerDataChanged();
        void onPickerJumpTo(float start, float end, boolean force);
        void invalidate();
    }

}
