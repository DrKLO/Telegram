/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

import java.util.Locale;

public class PhotoFilterCurvesControl extends View {

    public interface PhotoFilterCurvesControlDelegate {
        void valueChanged();
    }

    private final static int CurvesSegmentNone = 0;
    private final static int CurvesSegmentBlacks = 1;
    private final static int CurvesSegmentShadows = 2;
    private final static int CurvesSegmentMidtones = 3;
    private final static int CurvesSegmentHighlights = 4;
    private final static int CurvesSegmentWhites = 5;

    private final static int GestureStateBegan = 1;
    private final static int GestureStateChanged = 2;
    private final static int GestureStateEnded = 3;
    private final static int GestureStateCancelled = 4;
    private final static int GestureStateFailed = 5;

    private int activeSegment = CurvesSegmentNone;

    private boolean isMoving;
    private boolean checkForMoving = true;

    private float lastX;
    private float lastY;

    private Rect actualArea = new Rect();

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paintDash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paintCurve = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Path path = new Path();

    private PhotoFilterCurvesControlDelegate delegate;

    private PhotoFilterView.CurvesToolValue curveValue;

    public PhotoFilterCurvesControl(Context context, PhotoFilterView.CurvesToolValue value) {
        super(context);
        setWillNotDraw(false);

        curveValue = value;

        paint.setColor(0x99ffffff);
        paint.setStrokeWidth(AndroidUtilities.dp(1));
        paint.setStyle(Paint.Style.STROKE);

        paintDash.setColor(0x99ffffff);
        paintDash.setStrokeWidth(AndroidUtilities.dp(2));
        paintDash.setStyle(Paint.Style.STROKE);

        paintCurve.setColor(0xffffffff);
        paintCurve.setStrokeWidth(AndroidUtilities.dp(2));
        paintCurve.setStyle(Paint.Style.STROKE);

        textPaint.setColor(0xffbfbfbf);
        textPaint.setTextSize(AndroidUtilities.dp(13));
    }

    public void setDelegate(PhotoFilterCurvesControlDelegate photoFilterCurvesControlDelegate) {
        delegate = photoFilterCurvesControlDelegate;
    }

    public void setActualArea(float x, float y, float width, float height) {
        actualArea.x = x;
        actualArea.y = y;
        actualArea.width = width;
        actualArea.height = height;
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
                        lastX = locationX;
                        lastY = locationY;
                        if (locationX >= actualArea.x && locationX <= actualArea.x + actualArea.width && locationY >= actualArea.y && locationY <= actualArea.y + actualArea.height) {
                            isMoving = true;
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
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if (isMoving) {
                    handlePan(GestureStateEnded, event);
                    isMoving = false;
                }
                checkForMoving = true;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (isMoving) {
                    handlePan(GestureStateChanged, event);
                }
            }
        }
        return true;
    }

    private void handlePan(int state, MotionEvent event) {
        float locationX = event.getX();
        float locationY = event.getY();

        switch (state) {
            case GestureStateBegan: {
                selectSegmentWithPoint(locationX);
                break;
            }

            case GestureStateChanged: {
                float delta = Math.min(2, (lastY - locationY) / 8.0f);

                PhotoFilterView.CurvesValue curveValue = null;
                switch (this.curveValue.activeType) {
                    case PhotoFilterView.CurvesToolValue.CurvesTypeLuminance:
                        curveValue = this.curveValue.luminanceCurve;
                        break;

                    case PhotoFilterView.CurvesToolValue.CurvesTypeRed:
                        curveValue = this.curveValue.redCurve;
                        break;

                    case PhotoFilterView.CurvesToolValue.CurvesTypeGreen:
                        curveValue = this.curveValue.greenCurve;
                        break;

                    case PhotoFilterView.CurvesToolValue.CurvesTypeBlue:
                        curveValue = this.curveValue.blueCurve;
                        break;

                    default:
                        break;
                }

                switch (activeSegment) {
                    case CurvesSegmentBlacks:
                        curveValue.blacksLevel = Math.max(0, Math.min(100, curveValue.blacksLevel + delta));
                        break;

                    case CurvesSegmentShadows:
                        curveValue.shadowsLevel = Math.max(0, Math.min(100, curveValue.shadowsLevel + delta));
                        break;

                    case CurvesSegmentMidtones:
                        curveValue.midtonesLevel = Math.max(0, Math.min(100, curveValue.midtonesLevel + delta));
                        break;

                    case CurvesSegmentHighlights:
                        curveValue.highlightsLevel = Math.max(0, Math.min(100, curveValue.highlightsLevel + delta));
                        break;

                    case CurvesSegmentWhites:
                        curveValue.whitesLevel = Math.max(0, Math.min(100, curveValue.whitesLevel + delta));
                        break;

                    default:
                        break;
                }

                invalidate();

                if (delegate != null) {
                    delegate.valueChanged();
                }

                lastX = locationX;
                lastY = locationY;
            }
            break;

            case GestureStateEnded:
            case GestureStateCancelled:
            case GestureStateFailed: {
                unselectSegments();
            }
            break;

            default:
                break;
        }
    }

    private void selectSegmentWithPoint(float pointx) {
        if (activeSegment != CurvesSegmentNone) {
            return;
        }
        float segmentWidth = actualArea.width / 5.0f;
        pointx -= actualArea.x;
        activeSegment = (int) Math.floor((pointx / segmentWidth) + 1);
    }

    private void unselectSegments() {
        if (activeSegment == CurvesSegmentNone) {
            return;
        }
        activeSegment = CurvesSegmentNone;
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        float segmentWidth = actualArea.width / 5.0f;

        for (int i = 0; i < 4; i++) {
            canvas.drawLine(actualArea.x + segmentWidth + i * segmentWidth, actualArea.y, actualArea.x + segmentWidth + i * segmentWidth, actualArea.y + actualArea.height, paint);
        }

        canvas.drawLine(actualArea.x, actualArea.y + actualArea.height, actualArea.x + actualArea.width, actualArea.y, paintDash);

        PhotoFilterView.CurvesValue curvesValue = null;
        switch (curveValue.activeType) {
            case PhotoFilterView.CurvesToolValue.CurvesTypeLuminance:
                paintCurve.setColor(0xffffffff);
                curvesValue = curveValue.luminanceCurve;
                break;

            case PhotoFilterView.CurvesToolValue.CurvesTypeRed:
                paintCurve.setColor(0xffed3d4c);
                curvesValue = curveValue.redCurve;
                break;

            case PhotoFilterView.CurvesToolValue.CurvesTypeGreen:
                paintCurve.setColor(0xff10ee9d);
                curvesValue = curveValue.greenCurve;
                break;

            case PhotoFilterView.CurvesToolValue.CurvesTypeBlue:
                paintCurve.setColor(0xff3377fb);
                curvesValue = curveValue.blueCurve;
                break;

            default:
                break;
        }

        for (int a = 0; a < 5; a++) {
            String str;
            switch (a) {
                case 0:
                    str = String.format(Locale.US, "%.2f", curvesValue.blacksLevel / 100.0f);
                    break;
                case 1:
                    str = String.format(Locale.US, "%.2f", curvesValue.shadowsLevel / 100.0f);
                    break;
                case 2:
                    str = String.format(Locale.US, "%.2f", curvesValue.midtonesLevel / 100.0f);
                    break;
                case 3:
                    str = String.format(Locale.US, "%.2f", curvesValue.highlightsLevel / 100.0f);
                    break;
                case 4:
                    str = String.format(Locale.US, "%.2f", curvesValue.whitesLevel / 100.0f);
                    break;
                default:
                    str = "";
                    break;
            }
            float width = textPaint.measureText(str);
            canvas.drawText(str, actualArea.x + (segmentWidth - width) / 2 + segmentWidth * a, actualArea.y + actualArea.height - AndroidUtilities.dp(4), textPaint);
        }

        float[] points = curvesValue.interpolateCurve();
        invalidate();
        path.reset();
        for (int a = 0; a < points.length / 2; a++) {
            if (a == 0) {
                path.moveTo(actualArea.x + points[a * 2] * actualArea.width, actualArea.y + (1.0f - points[a * 2 + 1]) * actualArea.height);
            } else {
                path.lineTo(actualArea.x + points[a * 2] * actualArea.width, actualArea.y + (1.0f - points[a * 2 + 1]) * actualArea.height);
            }
        }

        canvas.drawPath(path, paintCurve);
    }
}
