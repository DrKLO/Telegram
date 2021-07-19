package org.telegram.ui.Components.Paint.Views;

import android.view.MotionEvent;

public class RotationGestureDetector {

    public interface OnRotationGestureListener {
        void onRotationBegin(RotationGestureDetector rotationDetector);
        void onRotation(RotationGestureDetector rotationDetector);
        void onRotationEnd(RotationGestureDetector rotationDetector);
    }

    private float fX, fY, sX, sY;
    private float angle;
    private float startAngle;

    private OnRotationGestureListener mListener;

    public float getAngle() {
        return angle;
    }

    public float getStartAngle() {
        return startAngle;
    }

    public RotationGestureDetector(OnRotationGestureListener listener) {
        mListener = listener;
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() != 2)
            return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                sX = event.getX(0);
                sY = event.getY(0);
                fX = event.getX(1);
                fY = event.getY(1);
            }
            break;

            case MotionEvent.ACTION_MOVE: {
                float nfX, nfY, nsX, nsY;
                nsX = event.getX(0);
                nsY = event.getY(0);
                nfX = event.getX(1);
                nfY = event.getY(1);

                angle = angleBetweenLines(fX, fY, sX, sY, nfX, nfY, nsX, nsY);

                if (mListener != null) {
                    if (Float.isNaN(startAngle)) {
                        startAngle = angle;
                        mListener.onRotationBegin(this);
                    } else {
                        mListener.onRotation(this);
                    }
                }
            }
            break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                startAngle = Float.NaN;
            }
            break;

            case MotionEvent.ACTION_POINTER_UP: {
                startAngle = Float.NaN;

                if (mListener != null) {
                    mListener.onRotationEnd(this);
                }
            }
            break;
        }
        return true;
    }

    private float angleBetweenLines(float fX, float fY, float sX, float sY, float nfX, float nfY, float nsX, float nsY) {
        float angle1 = (float) Math.atan2((fY - sY), (fX - sX));
        float angle2 = (float) Math.atan2((nfY - nsY), (nfX - nsX));
        float angle = ((float) Math.toDegrees(angle1 - angle2)) % 360;
        if (angle < -180.f) {
            angle += 360.0f;
        }
        if (angle > 180.f) {
            angle -= 360.0f;
        }
        return angle;
    }
}