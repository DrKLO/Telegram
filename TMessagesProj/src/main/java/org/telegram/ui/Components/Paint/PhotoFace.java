package org.telegram.ui.Components.Paint;

import android.graphics.Bitmap;
import android.graphics.PointF;

import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.Landmark;

import org.telegram.ui.Components.Size;

import java.util.List;

public class PhotoFace {

    private float width;
    private float angle;

    private PointF foreheadPoint;

    private PointF eyesCenterPoint;
    private float eyesDistance;

    private PointF mouthPoint;
    private PointF chinPoint;

    public PhotoFace(Face face, Bitmap sourceBitmap, Size targetSize, boolean sideward) {
        List<Landmark> landmarks = face.getLandmarks();

        PointF leftEyePoint = null;
        PointF rightEyePoint = null;

        PointF leftMouthPoint = null;
        PointF rightMouthPoint = null;

        for (Landmark landmark : landmarks) {
            PointF point = landmark.getPosition();

            switch (landmark.getType()) {
                case Landmark.LEFT_EYE: {
                    leftEyePoint = transposePoint(point, sourceBitmap, targetSize, sideward);
                }
                break;

                case Landmark.RIGHT_EYE: {
                    rightEyePoint = transposePoint(point, sourceBitmap, targetSize, sideward);
                }
                break;

                case Landmark.LEFT_MOUTH: {
                    leftMouthPoint = transposePoint(point, sourceBitmap, targetSize, sideward);
                }
                break;

                case Landmark.RIGHT_MOUTH: {
                    rightMouthPoint = transposePoint(point, sourceBitmap, targetSize, sideward);
                }
                break;
            }
        }

        if (leftEyePoint != null && rightEyePoint != null) {
            if (leftEyePoint.x < rightEyePoint.x) {
                PointF temp = leftEyePoint;
                leftEyePoint = rightEyePoint;
                rightEyePoint = temp;
            }
            float x = 0.5f * leftEyePoint.x + 0.5f * rightEyePoint.x;
            float y = 0.5f * leftEyePoint.y + 0.5f * rightEyePoint.y;
            eyesCenterPoint = new PointF(x, y);
            eyesDistance = (float)Math.hypot(rightEyePoint.x - leftEyePoint.x, rightEyePoint.y - leftEyePoint.y);
            angle = (float)Math.toDegrees(Math.PI + Math.atan2(rightEyePoint.y - leftEyePoint.y, rightEyePoint.x - leftEyePoint.x));

            width = eyesDistance * 2.35f;

            float foreheadHeight = 0.8f * eyesDistance;
            float upAngle = (float)Math.toRadians(angle - 90);
            float x1 = eyesCenterPoint.x + foreheadHeight * (float) Math.cos(upAngle);
            float y1 = eyesCenterPoint.y + foreheadHeight * (float) Math.sin(upAngle);
            foreheadPoint = new PointF(x1, y1);
        }

        if (leftMouthPoint != null && rightMouthPoint != null) {
            if (leftMouthPoint.x < rightMouthPoint.x) {
                PointF temp = leftMouthPoint;
                leftMouthPoint = rightMouthPoint;
                rightMouthPoint = temp;
            }
            float x = 0.5f * leftMouthPoint.x + 0.5f * rightMouthPoint.x;
            float y = 0.5f * leftMouthPoint.y + 0.5f * rightMouthPoint.y;
            mouthPoint = new PointF(x, y);

            float chinDepth = 0.7f * eyesDistance;
            float downAngle = (float)Math.toRadians(angle + 90);
            float x1 = mouthPoint.x + chinDepth * (float) Math.cos(downAngle);
            float y1 = mouthPoint.y + chinDepth * (float) Math.sin(downAngle);
            chinPoint = new PointF(x1, y1);
        }
    }

    public boolean isSufficient() {
        return eyesCenterPoint != null;
    }

    private PointF transposePoint(PointF point, Bitmap sourceBitmap, Size targetSize, boolean sideward) {
        float bitmapW = sideward ? sourceBitmap.getHeight() : sourceBitmap.getWidth();
        float bitmapH = sideward ? sourceBitmap.getWidth() : sourceBitmap.getHeight();
        float x = targetSize.width * point.x / bitmapW;
        float y = targetSize.height * point.y / bitmapH;
        return new PointF(x, y);
    }

    public PointF getPointForAnchor(int anchor) {
        switch (anchor) {
            case 0: {
                return foreheadPoint;
            }

            case 1: {
                return eyesCenterPoint;
            }

            case 2: {
                return mouthPoint;
            }

            case 3: {
                return chinPoint;
            }

            default: {
                return null;
            }
        }
    }

    public float getWidthForAnchor(int anchor) {
        if (anchor == 1) {
            return eyesDistance;
        }
        return width;
    }

    public float getAngle() {
        return angle;
    }
 }
