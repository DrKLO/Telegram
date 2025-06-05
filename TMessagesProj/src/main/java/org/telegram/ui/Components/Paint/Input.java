package org.telegram.ui.Components.Paint;

import static com.google.zxing.common.detector.MathUtils.distance;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Size;

import java.util.Vector;

public class Input {
    private final static CubicBezierInterpolator PRESSURE_INTERPOLATOR = new CubicBezierInterpolator(0, 0.5, 0, 1);

    private RenderView renderView;

    private boolean beganDrawing;
    private boolean isFirst;
    private long drawingStart;
    private boolean hasMoved;
    private boolean clearBuffer;

    private Point lastLocation, lastThickLocation;
    private double lastRemainder;
    private boolean lastAngleSet;
    private float lastAngle;
    private float lastScale;
    private boolean canFill;
    private Point[] points = new Point[3];
    private int pointsCount, realPointsCount;
    private double thicknessSum, thicknessCount;

    private ValueAnimator arrowAnimator;

    private final ShapeDetector detector;
    
    private void setShapeHelper(Shape shape) {
        if (shape != null) {
            shape.thickness = renderView.getCurrentWeight();
            if (thicknessSum > 0) {
                shape.thickness *= (thicknessSum / thicknessCount);
            }
            if (shape.getType() == Brush.Shape.SHAPE_TYPE_ARROW) {
                shape.arrowTriangleLength *= shape.thickness;
            }
        }
        renderView.getPainting().setHelperShape(shape);
    }

    private Matrix invertMatrix;
    private float[] tempPoint = new float[2];

    private long lastVelocityUpdate;
    private float velocity;

    public Input(RenderView renderView) {
        this.renderView = renderView;
        this.detector = new ShapeDetector(renderView.getContext(), this::setShapeHelper);
    }

    public void setMatrix(Matrix m) {
        invertMatrix = new Matrix();
        m.invert(invertMatrix);
    }

    private ValueAnimator fillAnimator;
    private void fill(Brush brush, boolean registerUndo, Runnable onDone) {
        if (!canFill || renderView.getPainting().masking || lastLocation == null) {
            return;
        }

        if (brush == null) {
            brush = renderView.getCurrentBrush();
        }

        if (brush instanceof Brush.Elliptical ||
            brush instanceof Brush.Neon) {
            brush = new Brush.Radial();
        }

        canFill = false;
        if (brush instanceof Brush.Eraser) {
            renderView.getPainting().hasBlur = false;
        }
        renderView.getPainting().clearStroke();
        pointsCount = 0;
        realPointsCount = 0;
        lastAngleSet = false;
        beganDrawing = false;
        if (registerUndo) {
            renderView.onBeganDrawing();
        }

        Size size = renderView.getPainting().getSize();
        float R = Math.max(
            Math.max(
                distance((float) lastLocation.x, (float) lastLocation.y, 0, 0),
                distance((float) lastLocation.x, (float) lastLocation.y, size.width, 0)
            ),
            Math.max(
                distance((float) lastLocation.x, (float) lastLocation.y, 0, size.height),
                distance((float) lastLocation.x, (float) lastLocation.y, size.width, size.height)
            )
        ) / 0.84f;

        if (arrowAnimator != null) {
            arrowAnimator.cancel();
            arrowAnimator = null;
        }
        if (fillAnimator != null) {
            fillAnimator.cancel();
            fillAnimator = null;
        }
        final Point point = new Point(lastLocation.x, lastLocation.y, 1);
        final Brush finalBrush = brush;
        fillAnimator = ValueAnimator.ofFloat(0, 1);
        fillAnimator.addUpdateListener(anm -> {
            float t = (float) anm.getAnimatedValue();
            Path path = new Path(new Point[] { point });
            int color = finalBrush.isEraser() ? 0xffffffff : renderView.getCurrentColor();
            path.setup(color, t * R, finalBrush);
            renderView.getPainting().paintStroke(path, true, true, null);
        });
        fillAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                fillAnimator = null;
                Path path = new Path(new Point[] { point });
                path.setup(renderView.getCurrentColor(), 1f * R, finalBrush);
                int color = finalBrush.isEraser() ? 0xffffffff : renderView.getCurrentColor();
                renderView.getPainting().commitPath(path, color, registerUndo, null);
                if (registerUndo) {
                    renderView.onFinishedDrawing(true);
                }

                if (onDone != null) {
                    onDone.run();
                }
            }
        });
        fillAnimator.setDuration(450);
        fillAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        fillAnimator.start();

        if (registerUndo) {
            BotWebViewVibrationEffect.IMPACT_HEAVY.vibrate();
        }
    };
    private final Runnable fillWithCurrentBrush = () -> fill(null, true, null);

    public void clear(Runnable onDone) {
        Size size = renderView.getPainting().getSize();
        lastLocation = new Point(size.width, 0, 1);
        canFill = true;
        fill(new Brush.Eraser(), false, onDone);
    }

    private boolean ignore;
    public void ignoreOnce() {
        this.ignore = true;
    }

    private Brush switchedBrushByStylusFrom;

    public void process(MotionEvent event, float scale) {
        if (fillAnimator != null || arrowAnimator != null) {
            return;
        }
        int action = event.getActionMasked();
        float x = event.getX();
        float y = renderView.getHeight() - event.getY();

        tempPoint[0] = x;
        tempPoint[1] = y;
        invertMatrix.mapPoints(tempPoint);

        long dt = System.currentTimeMillis() - lastVelocityUpdate;
        velocity = MathUtils.clamp(velocity - dt / 125f, 0.6f, 1f);
        if (renderView.getCurrentBrush() != null && renderView.getCurrentBrush() instanceof Brush.Arrow) {
            velocity = 1 - velocity;
        }
        lastScale = scale;
        lastVelocityUpdate = System.currentTimeMillis();

        boolean stylusToolPressed = false;
        float weight = velocity;
        if (event.getToolType(event.getActionIndex()) == MotionEvent.TOOL_TYPE_STYLUS) {
            weight = Math.max(.1f, PRESSURE_INTERPOLATOR.getInterpolation(event.getPressure()));
            stylusToolPressed = (event.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) == MotionEvent.BUTTON_STYLUS_PRIMARY;
        }
        if (renderView.getCurrentBrush() != null) {
            weight = 1 + (weight - 1) * AndroidUtilities.lerp(renderView.getCurrentBrush().getSmoothThicknessRate(), 1, MathUtils.clamp(realPointsCount / 16f, 0, 1));
        }
        Point location = new Point(tempPoint[0], tempPoint[1], weight);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                if (ignore) {
                    return;
                }
                if (!beganDrawing) {
                    beganDrawing = true;
                    hasMoved = false;
                    isFirst = true;

                    lastLocation = location;
                    drawingStart = System.currentTimeMillis();

                    points[0] = location;
                    pointsCount = 1;
                    realPointsCount = 1;
                    lastAngleSet = false;

                    clearBuffer = true;
                    canFill = true;
                    AndroidUtilities.runOnUIThread(fillWithCurrentBrush, ViewConfiguration.getLongPressTimeout());
                } else {
                    float distance = location.getDistanceTo(lastLocation);
                    if (distance < AndroidUtilities.dp(5.0f) / scale) {
                        return;
                    }
                    if (canFill && (distance > AndroidUtilities.dp(6) / scale || pointsCount > 4)) {
                        canFill = false;
                        AndroidUtilities.cancelRunOnUIThread(fillWithCurrentBrush);
                    }

                    if (!hasMoved) {
                        renderView.onBeganDrawing();
                        hasMoved = true;

                        if (stylusToolPressed && renderView.getCurrentBrush() instanceof Brush.Radial) {
                            switchedBrushByStylusFrom = renderView.getCurrentBrush();
                            renderView.selectBrush(Brush.BRUSHES_LIST.get(Brush.BRUSHES_LIST.size() - 1));
                        }
                    }

                    points[pointsCount] = location;
                    if (renderView.getPainting() == null || !renderView.getPainting().masking) {
                        if ((System.currentTimeMillis() - drawingStart) > 3000) {
                            detector.clear();
                            renderView.getPainting().setHelperShape(null);
                        } else if (renderView.getCurrentBrush() instanceof Brush.Radial || renderView.getCurrentBrush() instanceof Brush.Elliptical) {
                            detector.append(location.x, location.y, distance > AndroidUtilities.dp(6) / scale);
                        }
                    }
                    pointsCount++;
                    realPointsCount++;

                    if (pointsCount == 3) {
                        float angle = (float) Math.atan2(points[2].y - points[1].y, points[2].x - points[1].x);
                        if (!lastAngleSet) {
                            lastAngle = angle;
                            lastAngleSet = true;
                        } else {
                            float f = MathUtils.clamp(distance / (AndroidUtilities.dp(16) / scale), 0, 1);
                            if (f > .4f) {
                                lastAngle = lerpAngle(lastAngle, angle, f);
                            }
                        }
                        smoothenAndPaintPoints(false, renderView.getCurrentBrush().getSmoothThicknessRate());
                    }

                    lastLocation = location;
                    if (distance > AndroidUtilities.dp(8) / scale) {
                        lastThickLocation = location;
                    }

                    velocity = MathUtils.clamp(velocity + dt / 75f, 0.6f, 1);
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (ignore) {
                    ignore = false;
                    return;
                }
                canFill = false;
                detector.clear();
                AndroidUtilities.cancelRunOnUIThread(fillWithCurrentBrush);
                if (!renderView.getPainting().applyHelperShape()) {

                    boolean commit = true;
                    if (!hasMoved) {
                        if (renderView.shouldDraw()) {
                            location.edge = true;
                            paintPath(new Path(location));
                        }
                        reset();
                    } else if (pointsCount > 0) {
                        smoothenAndPaintPoints(true, renderView.getCurrentBrush().getSmoothThicknessRate());

                        Brush brush = renderView.getCurrentBrush();
                        if (brush instanceof Brush.Arrow) {
                            float angle = lastAngle;
                            final Point loc = points[pointsCount - 1];
                            double z = lastThickLocation == null ? location.z : lastThickLocation.z;
                            float arrowLength = renderView.getCurrentWeight() * (float) z * 12f;

                            commit = false;
                            if (arrowAnimator != null) {
                                arrowAnimator.cancel();
                            }
                            final float[] lastT = new float[1];
                            final boolean[] vibrated = new boolean[1];
                            arrowAnimator = ValueAnimator.ofFloat(0, 1);
                            arrowAnimator.addUpdateListener(anm -> {
                                float t = (float) anm.getAnimatedValue();

                                double leftCos = Math.cos(angle - Math.PI / 4 * 3.3);
                                double leftSin = Math.sin(angle - Math.PI / 4 * 3.5);
                                paintPath(new Path(new Point[]{
                                    new Point(loc.x + leftCos * arrowLength * lastT[0], loc.y + leftSin * arrowLength * lastT[0], z),
                                    new Point(loc.x + leftCos * arrowLength * t, loc.y + leftSin * arrowLength * t, z, true)
                                }));
                                double rightCos = Math.cos(angle + Math.PI / 4 * 3.3);
                                double rightSin = Math.sin(angle + Math.PI / 4 * 3.5);
                                paintPath(new Path(new Point[]{
                                    new Point(loc.x + rightCos * arrowLength * lastT[0], loc.y + rightSin * arrowLength * lastT[0], z),
                                    new Point(loc.x + rightCos * arrowLength * t, loc.y + rightSin * arrowLength * t, z, true)
                                }));

                                if (!vibrated[0] && t > .4f) {
                                    vibrated[0] = true;
                                    BotWebViewVibrationEffect.SELECTION_CHANGE.vibrate();
                                }

                                lastT[0] = t;
                            });
                            arrowAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    renderView.getPainting().commitPath(null, renderView.getCurrentColor());
                                    arrowAnimator = null;
                                }
                            });
                            arrowAnimator.setDuration(240);
                            arrowAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                            arrowAnimator.start();
                        }
                    }

                    if (commit) {
                        renderView.getPainting().commitPath(null, renderView.getCurrentColor(), true, () -> {
                            if (switchedBrushByStylusFrom != null) {
                                renderView.selectBrush(switchedBrushByStylusFrom);
                                switchedBrushByStylusFrom = null;
                            }
                        });
                    }
                }

                pointsCount = 0;
                realPointsCount = 0;
                lastAngleSet = false;
                beganDrawing = false;
                thicknessCount = thicknessSum = 0;

                renderView.onFinishedDrawing(hasMoved);
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (ignore) {
                    ignore = false;
                    return;
                }
                canFill = false;
                detector.clear();
                renderView.getPainting().setHelperShape(null);
                AndroidUtilities.cancelRunOnUIThread(fillWithCurrentBrush);
                renderView.getPainting().clearStroke();
                pointsCount = 0;
                realPointsCount = 0;
                lastAngleSet = false;
                beganDrawing = false;
                thicknessCount = thicknessSum = 0;

                if (switchedBrushByStylusFrom != null) {
                    renderView.selectBrush(switchedBrushByStylusFrom);
                    switchedBrushByStylusFrom = null;
                }
                break;
            }
        }
    }

    private float lerpAngle(float angleA, float angleB, float t) {
//        double da = (angleB - angleA) % (Math.PI * 2);
//        return (float) (angleA + (2 * da % (Math.PI * 2) - da) * t);
        return (float) Math.atan2((1-t)*Math.sin(angleA) + t*Math.sin(angleB), (1-t)*Math.cos(angleA) + t*Math.cos(angleB));
    }

    private void reset() {
        pointsCount = 0;
    }

    private void smoothenAndPaintPoints(boolean ended, float smoothThickness) {
        if (pointsCount > 2) {
            Vector<Point> points = new Vector<>();

            Point prev2 = this.points[0];
            Point prev1 = this.points[1];
            Point cur = this.points[2];

            if (cur == null || prev1 == null || prev2 == null) {
                return;
            }

            Point midPoint1 = prev1.multiplySum(prev2, 0.5f);
            Point midPoint2 = cur.multiplySum(prev1, 0.5f);

            int segmentDistance = 1;
            float distance = midPoint1.getDistanceTo(midPoint2);
            int numberOfSegments = (int) Math.min(48, Math.max(Math.floor(distance / segmentDistance), 24));

            float t = 0.0f;
            float step = 1.0f / (float) numberOfSegments;

            for (int j = 0; j < numberOfSegments; j++) {
                Point point = smoothPoint(midPoint1, midPoint2, prev1, t, smoothThickness);
                if (isFirst) {
                    point.edge = true;
                    isFirst = false;
                }
                points.add(point);
                thicknessSum += point.z;
                thicknessCount++;
                t += step;
            }

            if (ended) {
                midPoint2.edge = true;
            }
            points.add(midPoint2);

            Point[] result = new Point[points.size()];
            points.toArray(result);

            Path path = new Path(result);
            paintPath(path);

            System.arraycopy(this.points, 1, this.points, 0, 2);

            if (ended) {
                pointsCount = 0;
            } else {
                pointsCount = 2;
            }
        } else {
            Point[] result = new Point[pointsCount];
            System.arraycopy(this.points, 0, result, 0, pointsCount);
            Path path = new Path(result);
            paintPath(path);
        }
    }

    private Point smoothPoint(Point midPoint1, Point midPoint2, Point prev1, float t, float smoothThickness) {
        double a1 = Math.pow(1.0f - t, 2);
        double a2 = (2.0f * (1.0f - t) * t);
        double a3 = t * t;

        float t_squared = t * t;
        float minus_t_squard = (1 - t) * (1 - t);

        double x = midPoint1.x * minus_t_squard + 2 * prev1.x * t * (1 - t) + midPoint2.x * t_squared;
        double y = midPoint1.y * minus_t_squard + 2 * prev1.y * t * (1 - t) + midPoint2.y * t_squared;
        double z = midPoint1.z * a1 + prev1.z * a2 + midPoint2.z * a3;
        z = 1 + (z - 1) * AndroidUtilities.lerp(smoothThickness, 1, MathUtils.clamp(realPointsCount / 16f, 0, 1));

        return new Point(x, y, z);
    }

    private void paintPath(final Path path) {
        path.setup(renderView.getCurrentColor(), renderView.getCurrentWeight(), renderView.getCurrentBrush());

        if (clearBuffer) {
            lastRemainder = 0.0f;
        }

        path.remainder = lastRemainder;

        renderView.getPainting().paintStroke(path, clearBuffer, false, () -> AndroidUtilities.runOnUIThread(() -> lastRemainder = path.remainder));
        clearBuffer = false;
    }
}
