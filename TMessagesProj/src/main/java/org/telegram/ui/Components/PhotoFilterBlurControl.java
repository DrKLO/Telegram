/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import org.telegram.android.AndroidUtilities;

public class PhotoFilterBlurControl extends FrameLayout {

    public interface PhotoFilterLinearBlurControlDelegate {
        void valueChanged(Point centerPoint, float falloff, float size, float angle);
    }

    private final static float BlurInsetProximity = AndroidUtilities.dp(20);
    private final static float BlurMinimumFalloff = 0.1f;
    private final static float BlurMinimumDifference = 0.02f;
    private final static float BlurViewCenterInset = AndroidUtilities.dp(30.0f);
    private final static float BlurViewRadiusInset = AndroidUtilities.dp(30.0f);

    private enum BlurViewActiveControl {
        BlurViewActiveControlNone,
        BlurViewActiveControlCenter,
        BlurViewActiveControlInnerRadius,
        BlurViewActiveControlOuterRadius,
        BlurViewActiveControlWholeArea,
        BlurViewActiveControlRotation
    }

    private final int GestureStateBegan = 1;
    private final int GestureStateChanged = 2;
    private final int GestureStateEnded = 3;
    private final int GestureStateCancelled = 4;
    private final int GestureStateFailed = 5;

    private BlurViewActiveControl activeControl;
    private Point startCenterPoint = new Point();
    private float startDistance;
    private float startRadius;
    private Size actualAreaSize = new Size();
    private Point centerPoint = new Point(0.5f, 0.5f);
    private float falloff = 0.15f;
    private float size = 0.35f;
    private float angle;
    private RectF arcRect = new RectF();

    private float pointerStartX;
    private float pointerStartY;
    private float startPointerDistance;
    private float pointerScale = 1;
    private boolean isMoving;
    private boolean isZooming;
    private boolean checkForMoving;
    private boolean checkForZooming;

    private int type;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private PhotoFilterLinearBlurControlDelegate delegate;

    public PhotoFilterBlurControl(Context context) {
        super(context);
        setWillNotDraw(false);
        paint.setColor(0xffffffff);
        arcPaint.setColor(0xffffffff);
        arcPaint.setStrokeWidth(AndroidUtilities.dp(2));
        arcPaint.setStyle(Paint.Style.STROKE);
    }

    public void setType(int blurType) {
        type = blurType;
    }

    public void setDelegate(PhotoFilterLinearBlurControlDelegate delegate) {
        this.delegate = delegate;
    }

    private float getDistance(MotionEvent event) {
        if (event.getPointerCount() != 2) {
            return 0;
        }
        float x1 = event.getX(0);
        float y1 = event.getY(0);
        float x2 = event.getX(1);
        float y2 = event.getY(1);
        return (float) Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    private float degreesToRadians(float degrees) {
        return degrees * (float) Math.PI / 180.0f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_DOWN: {
                if (event.getPointerCount() == 1) {
                    if (checkForMoving && !isMoving) {
                        float locationX = event.getX();
                        float locationY = event.getY();
                        Point centerPoint = getActualCenterPoint();
                        Point delta = new Point(locationX - centerPoint.x, locationY - centerPoint.y);
                        float radialDistance = (float) Math.sqrt(delta.x * delta.x + delta.y * delta.y);
                        float innerRadius = getActualInnerRadius();
                        float outerRadius = getActualOuterRadius();
                        boolean close = Math.abs(outerRadius - innerRadius) < BlurInsetProximity;
                        float innerRadiusOuterInset = close ? 0 : BlurViewRadiusInset;
                        float outerRadiusInnerInset = close ? 0 : BlurViewRadiusInset;

                        if (type == 0) {
                            float distance = (float) Math.abs(delta.x * Math.cos(degreesToRadians(angle) + Math.PI / 2) + delta.y * Math.sin(degreesToRadians(angle) + Math.PI / 2));
                            if (radialDistance < BlurViewCenterInset) {
                                isMoving = true;
                            } else if (distance > innerRadius - BlurViewRadiusInset && distance < innerRadius + innerRadiusOuterInset) {
                                isMoving = true;
                            } else if (distance > outerRadius - outerRadiusInnerInset && distance < outerRadius + BlurViewRadiusInset) {
                                isMoving = true;
                            } else if ((distance <= innerRadius - BlurViewRadiusInset) || distance >= outerRadius + BlurViewRadiusInset) {
                                isMoving = true;
                            }
                        } else if (type == 1) {
                            if (radialDistance < BlurViewCenterInset) {
                                isMoving = true;
                            } else if (radialDistance > innerRadius - BlurViewRadiusInset && radialDistance < innerRadius + innerRadiusOuterInset) {
                                isMoving = true;
                            } else if (radialDistance > outerRadius - outerRadiusInnerInset && radialDistance < outerRadius + BlurViewRadiusInset) {
                                isMoving = true;
                            }
                        }
                        checkForMoving = false;
                        if (isMoving) {
                            handlePan(GestureStateBegan, event);
                        }
                    }
                } else {
                    if (isMoving) {
                        handlePan(GestureStateEnded, event);
                        checkForMoving = true;
                        isMoving = false;
                    }
                    if (event.getPointerCount() == 2) {
                        if (checkForZooming && !isZooming) {
                            handlePinch(GestureStateBegan, event);
                            isZooming = true;
                        }
                    } else {
                        handlePinch(GestureStateEnded, event);
                        checkForZooming = true;
                        isZooming = false;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if (isMoving) {
                    handlePan(GestureStateEnded, event);
                    isMoving = false;
                } else if (isZooming) {
                    handlePinch(GestureStateEnded, event);
                    isZooming = false;
                }
                checkForMoving = true;
                checkForZooming = true;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (isMoving) {
                    handlePan(GestureStateChanged, event);
                } else if (isZooming) {
                    handlePinch(GestureStateChanged, event);
                }
            }
        }
        return true;
    }

    private void handlePan(int state, MotionEvent event) {
        float locationX = event.getX();
        float locationY = event.getY();
        Point actualCenterPoint = getActualCenterPoint();
        Point delta = new Point(locationX - actualCenterPoint.x, locationY - actualCenterPoint.y);
        float radialDistance = (float) Math.sqrt(delta.x * delta.x + delta.y * delta.y);
        float shorterSide = (actualAreaSize.width > actualAreaSize.height) ? actualAreaSize.height : actualAreaSize.width;
        float innerRadius = shorterSide * falloff;
        float outerRadius = shorterSide * size;
        float distance = (float) Math.abs(delta.x * Math.cos(degreesToRadians(angle) + Math.PI / 2.0f) + delta.y * Math.sin(degreesToRadians(angle) + Math.PI / 2.0f));

        switch (state) {
            case GestureStateBegan: {
                pointerStartX = event.getX();
                pointerStartY = event.getY();

                boolean close = Math.abs(outerRadius - innerRadius) < BlurInsetProximity;
                float innerRadiusOuterInset = close ? 0 : BlurViewRadiusInset;
                float outerRadiusInnerInset = close ? 0 : BlurViewRadiusInset;

                if (type == 0) {
                    if (radialDistance < BlurViewCenterInset) {
                        activeControl = BlurViewActiveControl.BlurViewActiveControlCenter;
                        startCenterPoint = actualCenterPoint;
                    } else if (distance > innerRadius - BlurViewRadiusInset && distance < innerRadius + innerRadiusOuterInset) {
                        activeControl = BlurViewActiveControl.BlurViewActiveControlInnerRadius;
                        startDistance = distance;
                        startRadius = innerRadius;
                    } else if (distance > outerRadius - outerRadiusInnerInset && distance < outerRadius + BlurViewRadiusInset) {
                        activeControl = BlurViewActiveControl.BlurViewActiveControlOuterRadius;
                        startDistance = distance;
                        startRadius = outerRadius;
                    } else if (distance <= innerRadius - BlurViewRadiusInset || distance >= outerRadius + BlurViewRadiusInset) {
                        activeControl = BlurViewActiveControl.BlurViewActiveControlRotation;
                    }
                } else if (type == 1) {
                    if (radialDistance < BlurViewCenterInset) {
                        activeControl = BlurViewActiveControl.BlurViewActiveControlCenter;
                        startCenterPoint = actualCenterPoint;
                    } else if (radialDistance > innerRadius - BlurViewRadiusInset && radialDistance < innerRadius + innerRadiusOuterInset) {
                        activeControl = BlurViewActiveControl.BlurViewActiveControlInnerRadius;
                        startDistance = radialDistance;
                        startRadius = innerRadius;
                    } else if (radialDistance > outerRadius - outerRadiusInnerInset && radialDistance < outerRadius + BlurViewRadiusInset) {
                        activeControl = BlurViewActiveControl.BlurViewActiveControlOuterRadius;
                        startDistance = radialDistance;
                        startRadius = outerRadius;
                    }
                }
                setSelected(true, true);
            }
            break;

            case GestureStateChanged: {
                if (type == 0) {
                    switch (activeControl) {
                        case BlurViewActiveControlCenter: {
                            float translationX = locationX - pointerStartX;
                            float translationY = locationY - pointerStartY;
                            Rect actualArea = new Rect((getWidth() - actualAreaSize.width) / 2, (getHeight() - actualAreaSize.height) / 2, actualAreaSize.width, actualAreaSize.height);
                            Point newPoint = new Point(Math.max(actualArea.x, Math.min(actualArea.x + actualArea.width, startCenterPoint.x + translationX)), Math.max(actualArea.y, Math.min(actualArea.y + actualArea.height, startCenterPoint.y + translationY)));
                            centerPoint = new Point((newPoint.x - actualArea.x) / actualAreaSize.width, ((newPoint.y - actualArea.y) + (actualAreaSize.width - actualAreaSize.height) / 2) / actualAreaSize.width);
                        }
                        break;

                        case BlurViewActiveControlInnerRadius: {
                            float d = distance - startDistance;
                            falloff = Math.min(Math.max(BlurMinimumFalloff, (startRadius + d) / shorterSide), size - BlurMinimumDifference);
                        }
                        break;

                        case BlurViewActiveControlOuterRadius: {
                            float d = distance - startDistance;
                            size = Math.max(falloff + BlurMinimumDifference, (startRadius + d) / shorterSide);
                        }
                        break;

                        case BlurViewActiveControlRotation: {
                            float translationX = locationX - pointerStartX;
                            float translationY = locationY - pointerStartY;

                            boolean clockwise = false;

                            boolean right = locationX > actualCenterPoint.x;
                            boolean bottom = locationY > actualCenterPoint.y;

                            if (!right && !bottom) {
                                if (Math.abs(translationY) > Math.abs(translationX)) {
                                    if (translationY < 0) {
                                        clockwise = true;
                                    }
                                } else {
                                    if (translationX > 0) {
                                        clockwise = true;
                                    }
                                }
                            } else if (right && !bottom) {
                                if (Math.abs(translationY) > Math.abs(translationX)) {
                                    if (translationY > 0) {
                                        clockwise = true;
                                    }
                                } else {
                                    if (translationX > 0) {
                                        clockwise = true;
                                    }
                                }
                            } else if (right && bottom) {
                                if (Math.abs(translationY) > Math.abs(translationX)) {
                                    if (translationY > 0) {
                                        clockwise = true;
                                    }
                                } else {
                                    if (translationX < 0) {
                                        clockwise = true;
                                    }
                                }
                            } else {
                                if (Math.abs(translationY) > Math.abs(translationX)) {
                                    if (translationY < 0) {
                                        clockwise = true;
                                    }
                                } else {
                                    if (translationX < 0) {
                                        clockwise = true;
                                    }
                                }
                            }

                            float d = (float) Math.sqrt(translationX * translationX + translationY * translationY);
                            angle += d * ((clockwise ? 1 : 0) * 2 - 1) / (float) Math.PI / 1.15f;

                            pointerStartX = locationX;
                            pointerStartY = locationY;
                        }
                        break;

                        default:
                            break;
                    }
                } else if (type == 1) {
                    switch (activeControl) {
                        case BlurViewActiveControlCenter: {
                            float translationX = locationX - pointerStartX;
                            float translationY = locationY - pointerStartY;
                            Rect actualArea = new Rect((getWidth() - actualAreaSize.width) / 2, (getHeight() - actualAreaSize.height) / 2, actualAreaSize.width, actualAreaSize.height);
                            Point newPoint = new Point(Math.max(actualArea.x, Math.min(actualArea.x + actualArea.width, startCenterPoint.x + translationX)), Math.max(actualArea.y, Math.min(actualArea.y + actualArea.height, startCenterPoint.y + translationY)));
                            centerPoint = new Point((newPoint.x - actualArea.x) / actualAreaSize.width, ((newPoint.y - actualArea.y) + (actualAreaSize.width - actualAreaSize.height) / 2) / actualAreaSize.width);
                        }
                        break;

                        case BlurViewActiveControlInnerRadius: {
                            float d = radialDistance - startDistance;
                            falloff = Math.min(Math.max(BlurMinimumFalloff, (startRadius + d) / shorterSide), size - BlurMinimumDifference);
                        }
                        break;

                        case BlurViewActiveControlOuterRadius: {
                            float d = radialDistance - startDistance;
                            size = Math.max(falloff + BlurMinimumDifference, (startRadius + d) / shorterSide);
                        }
                        break;

                        default:
                            break;
                    }
                }
                invalidate();

                if (delegate != null) {
                    delegate.valueChanged(centerPoint, falloff, size, degreesToRadians(angle) + (float) Math.PI / 2.0f);
                }
            }
            break;

            case GestureStateEnded:
            case GestureStateCancelled:
            case GestureStateFailed: {
                activeControl = BlurViewActiveControl.BlurViewActiveControlNone;
                setSelected(false, true);
            }
            break;

            default:
                break;
        }
    }

    private void handlePinch(int state, MotionEvent event) {

        switch (state) {
            case GestureStateBegan: {
                startPointerDistance = getDistance(event);
                pointerScale = 1;
                activeControl = BlurViewActiveControl.BlurViewActiveControlWholeArea;
                setSelected(true, true);
            }
            case GestureStateChanged: {
                float newDistance = getDistance(event);
                pointerScale += (newDistance - startPointerDistance) / AndroidUtilities.density * 0.01f;

                falloff = Math.max(BlurMinimumFalloff, falloff * pointerScale);
                size = Math.max(falloff + BlurMinimumDifference, size * pointerScale);

                pointerScale = 1;
                startPointerDistance = newDistance;

                invalidate();

                if (delegate != null) {
                    delegate.valueChanged(centerPoint, falloff, size, degreesToRadians(angle) + (float) Math.PI / 2.0f);
                }
            }
            break;

            case GestureStateEnded:
            case GestureStateCancelled:
            case GestureStateFailed: {
                activeControl = BlurViewActiveControl.BlurViewActiveControlNone;
                setSelected(false, true);
            }
            break;

            default:
                break;
        }
    }

    private void setSelected(boolean selected, boolean animated) {
        /*if (animated) {
            [UIView animateWithDuration:0.16f delay:0.0f options:UIViewAnimationOptionBeginFromCurrentState animations:^
            {
                self.alpha = selected ? 0.6f : 1.0f;
            } completion:nil];
        } else {
            self.alpha = selected ? 0.6f : 1.0f;
        }*/
    }

    public void setActualAreaSize(float width, float height) {
        actualAreaSize.width = width;
        actualAreaSize.height = height;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Point centerPoint = getActualCenterPoint();
        float innerRadius = getActualInnerRadius();
        float outerRadius = getActualOuterRadius();
        canvas.translate(centerPoint.x, centerPoint.y);

        if (type == 0) {
            canvas.rotate(angle);

            float space = AndroidUtilities.dp(6.0f);
            float length = AndroidUtilities.dp(12.0f);
            float thickness = AndroidUtilities.dp(1.5f);
            for (int i = 0; i < 30; i++) {
                canvas.drawRect(i * (length + space), -innerRadius, i * (length + space) + length, thickness - innerRadius, paint);
                canvas.drawRect(-i * (length + space) - space - length, -innerRadius, -i * (length + space) - space, thickness - innerRadius, paint);

                canvas.drawRect(i * (length + space), innerRadius, length + i * (length + space), thickness + innerRadius, paint);
                canvas.drawRect(-i * (length + space) - space - length, innerRadius, -i * (length + space) - space, thickness + innerRadius, paint);
            }

            length = AndroidUtilities.dp(6.0f);
            for (int i = 0; i < 64; i++) {
                canvas.drawRect(i * (length + space), -outerRadius, length + i * (length + space), thickness - outerRadius, paint);
                canvas.drawRect(-i * (length + space) - space - length, -outerRadius, -i * (length + space) - space, thickness - outerRadius, paint);

                canvas.drawRect(i * (length + space), outerRadius, length + i * (length + space), thickness + outerRadius, paint);
                canvas.drawRect(-i * (length + space) - space - length, outerRadius, -i * (length + space) - space, thickness + outerRadius, paint);
            }
        } else if (type == 1) {
            float radSpace = 6.15f;
            float radLen = 10.2f;
            arcRect.set(-innerRadius, -innerRadius, innerRadius, innerRadius);
            for (int i = 0; i < 22; i++) {
                canvas.drawArc(arcRect, i * (radSpace + radLen), radLen, false, arcPaint);
            }

            radSpace = 2.02f;
            radLen = 3.6f;
            arcRect.set(-outerRadius, -outerRadius, outerRadius, outerRadius);
            for (int i = 0; i < 64; i++) {
                canvas.drawArc(arcRect, i * (radSpace + radLen), radLen, false, arcPaint);
            }
        }
        canvas.drawCircle(0, 0, AndroidUtilities.dp(8), paint);
    }

    private Point getActualCenterPoint() {
        return new Point((getWidth() - actualAreaSize.width) / 2 + centerPoint.x * actualAreaSize.width, (getHeight() - actualAreaSize.height) / 2 - (actualAreaSize.width - actualAreaSize.height) / 2 + centerPoint.y * actualAreaSize.width);
    }

    private float getActualInnerRadius() {
        return (actualAreaSize.width > actualAreaSize.height ? actualAreaSize.height : actualAreaSize.width) * falloff;
    }

    private float getActualOuterRadius() {
        return (actualAreaSize.width > actualAreaSize.height ? actualAreaSize.height : actualAreaSize.width) * size;
    }
}
