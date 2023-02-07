package org.telegram.ui.Components.Paint;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.Size;

import java.util.ArrayList;

public class ShapeInput {

    private RenderView renderView;
    private Runnable invalidate;

    private Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint centerPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint centerPointStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint controlPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint controlPointStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ShapeInput(RenderView renderView, Runnable invalidate) {
        this.renderView = renderView;
        this.invalidate = invalidate;

        centerPointPaint.setColor(0xFF2CD058);
        centerPointStrokePaint.setStyle(Paint.Style.STROKE);
        centerPointStrokePaint.setColor(0xFFFFFFFF);
        centerPointStrokePaint.setStrokeWidth(AndroidUtilities.dp(1));

        controlPointPaint.setColor(0xFF007AFF);
        controlPointStrokePaint.setStyle(Paint.Style.STROKE);
        controlPointStrokePaint.setColor(0xFFFFFFFF);
        controlPointStrokePaint.setStrokeWidth(AndroidUtilities.dp(1));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(0xFFFFFFFF);
        linePaint.setStrokeWidth(AndroidUtilities.dp(.8f));
        linePaint.setPathEffect(new DashPathEffect(new float[] { AndroidUtilities.dp(8), AndroidUtilities.dp(8) }, 0));
        linePaint.setShadowLayer(4f, 0, 1.5f, 0x40000000);
    }

    private Shape shape;

    private float touchOffsetX, touchOffsetY;
    private Point movingPoint;

    private Point center;
    private ArrayList<Point> allPoints = new ArrayList<>();
    private ArrayList<Point> movingPoints = new ArrayList<>(); // these points would move with others when shape is moved

    float dx, dy;

    private Matrix invertMatrix;
    public void setMatrix(Matrix m) {
        invertMatrix = new Matrix();
        m.invert(invertMatrix);
    }

    private void rotate(float x, float y, boolean back) {
        tempPoint[0] = x;
        tempPoint[1] = y;
        rotate(back);
    }

    private void rotate(boolean back) {
        if (shape != null && shape.rotation != 0) {
            tempPoint[0] -= shape.centerX;
            tempPoint[1] -= shape.centerY;
            float a = shape.rotation * (back ? -1 : 1);
            float nx = (float) (tempPoint[0] * Math.cos(a) - tempPoint[1] * Math.sin(a));
            float ny = (float) (tempPoint[0] * Math.sin(a) + tempPoint[1] * Math.cos(a));
            tempPoint[0] = nx + shape.centerX;
            tempPoint[1] = ny + shape.centerY;
        }
    }


    private float distToLine(float x, float y, float l1x, float l1y, float l2x, float l2y) {
        float px = l2x-l1x, py = l2y-l1y;
        float norm = px * px + py * py;
        float u = Math.max(Math.min(((x - l1x) * px + (y - l1y) * py) / norm, 1), 0);
        float dx = (l1x + u * px) - x, dy = (l1y + u * py) - y;
        return (float) Math.sqrt(dx*dx + dy*dy);
    }

    private float distToLine(float x, float y, float x2, float y2, double a2) {
        return (float) (Math.cos(a2) * (y2 - y) - Math.sin(a2) * (x2 - x));
    }

    private boolean isInsideShape(float x, float y) {
        if (shape == null) {
            return false;
        }

        if (shape.getType() == Brush.Shape.SHAPE_TYPE_CIRCLE || shape.getType() == Brush.Shape.SHAPE_TYPE_STAR) {
            return Math.sqrt(Math.pow(x - shape.centerX, 2) + Math.pow(y - shape.centerY, 2)) - Math.min(shape.radiusX, shape.radiusY) - shape.thickness / 2 < AndroidUtilities.dp(30);
        } else if (shape.getType() == Brush.Shape.SHAPE_TYPE_RECTANGLE || shape.getType() == Brush.Shape.SHAPE_TYPE_BUBBLE) {
            float left = (shape.centerX - shape.radiusX - shape.thickness / 2),
                  top = (shape.centerY - shape.radiusY - shape.thickness / 2),
                  right = (shape.centerX + shape.radiusX + shape.thickness / 2),
                  bottom = (shape.centerY + shape.radiusY + shape.thickness / 2);
            float dist;
            if (y > top && y < bottom) {
                if (x < left) {
                    dist = left - x;
                } else if (x > right) {
                    dist = x - right;
                } else {
                    dist = 0;
                }
            } else if (x < left && x > right) {
                if (y < top) {
                    dist = top - y;
                } else if (y > bottom) {
                    dist = y - bottom;
                } else {
                    dist = 0;
                }
            } else {
                dist = (float) Math.sqrt(Math.min(
                    Math.min(
                        Math.pow(x - left, 2) + Math.pow(y - top, 2),
                        Math.pow(x - right, 2) + Math.pow(y - top, 2)
                    ),
                    Math.min(
                        Math.pow(x - left, 2) + Math.pow(y - bottom, 2),
                        Math.pow(x - right, 2) + Math.pow(y - bottom, 2)
                    )
                ));
            }
            if (shape.getType() == Brush.Shape.SHAPE_TYPE_BUBBLE) {
                dist = Math.min(dist, distToLine(x, y, shape.centerX, shape.centerY, shape.middleX, shape.middleY));
            }
            return dist < AndroidUtilities.dp(30);
        } else if (shape.getType() == Brush.Shape.SHAPE_TYPE_ARROW) {
            Size size = renderView.getPainting().getSize();
            return Math.min(
                distToLine(x, y, shape.centerX, shape.centerY, shape.middleX, shape.middleY),
                distToLine(x, y, shape.radiusX, shape.radiusY, shape.middleX, shape.middleY)
            ) - shape.thickness / 2f < Math.min(size.width, size.height) * 0.10f;
        }

        return false;
    }

    private float[] tempPoint = new float[2];
    public void process(MotionEvent event, float scale) {
        if (renderView == null || renderView.getPainting() == null || shape == null) {
            return;
        }

        int action = event.getActionMasked();
        float x = event.getX();
        float y = renderView.getHeight() - event.getY();

        tempPoint[0] = x;
        tempPoint[1] = y;
        invertMatrix.mapPoints(tempPoint);
        float dx = tempPoint[0];
        float dy = tempPoint[1];

        invalidate.run();
        if (action == MotionEvent.ACTION_DOWN) {
            Point hitPoint = null;
            double hitPointDist = Double.MAX_VALUE;
            for (int i = 0; i < allPoints.size(); ++i) {
                Point p = allPoints.get(i);
                if (!p.draw) {
                    continue;
                }
                tempPoint[0] = dx;
                tempPoint[1] = dy;
                if (p.rotate) {
                    rotate(dx, dy, false);
                }
                double dist = MathUtils.distance(p.x, p.y, tempPoint[0], tempPoint[1]);
                if (dist < AndroidUtilities.dp(40) && (hitPoint == null || dist < hitPointDist)) {
                    hitPoint = p;
                    hitPointDist = dist;
                }
            }
            tempPoint[0] = dx;
            tempPoint[1] = dy;
            rotate(dx, dy, false);
            boolean inside = hitPoint != null || isInsideShape(dx, dy);
            if (!inside) {
                stop();
                return;
            }
            tempPoint[0] = dx;
            tempPoint[1] = dy;
            movingPoint = hitPoint;
            if (movingPoint != null) {
                if (movingPoint.rotate) {
                    rotate(dx, dy, false);
                }
                touchOffsetX = movingPoint.x - tempPoint[0];
                touchOffsetY = movingPoint.y - tempPoint[1];
            } else if (center != null) {
                if (center.rotate) {
                    rotate(dx, dy, false);
                }
                touchOffsetX = center.x - tempPoint[0];
                touchOffsetY = center.y - tempPoint[1];
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (movingPoint == null) {
                if (center != null) {
                    if (center.rotate) {
                        rotate(dx, dy, false);
                    }
                    float ddx = tempPoint[0] + touchOffsetX - center.x, ddy = tempPoint[1] + touchOffsetY - center.y;
                    for (int i = 0; i < movingPoints.size(); ++i) {
                        Point point = movingPoints.get(i);
                        point.update(point.x + ddx, point.y + ddy);
                    }
                }
            } else {
                if (movingPoint.rotate) {
                    rotate(false);
                }
                movingPoint.update(tempPoint[0] + touchOffsetX, tempPoint[1] + touchOffsetY);
            }
            renderView.getPainting().paintShape(shape, null);
            invalidate.run();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            movingPoint = null;
        }
    }

    public void onWeightChange() {
        if (shape != null && shape.thickness != renderView.getCurrentWeight()) {
            shape.thickness = renderView.getCurrentWeight();
            renderView.getPainting().paintShape(shape, null);
        }
    }

    public void onColorChange() {
        if (shape != null) {
            renderView.getPainting().paintShape(shape, null);
        }
    }

    public void start(int shapeType) {
        if (renderView == null || renderView.getPainting() == null) {
            return;
        }
        allPoints.clear();
        movingPoints.clear();
        shape = new Shape(Brush.Shape.make(shapeType));
        Size size = renderView.getPainting().getSize();
        shape.centerX = size.width / 2f;
        shape.centerY = size.height / 2f;
        shape.radiusX = shape.radiusY = Math.min(size.width, size.height) / 5f;
        shape.thickness = renderView.getCurrentWeight();
        shape.rounding = AndroidUtilities.dp(32);
        shape.fill = PersistColorPalette.getInstance(UserConfig.selectedAccount).getFillShapes();

        if (shape.getType() == Brush.Shape.SHAPE_TYPE_ARROW) {
            // radius actually stands here for second (end) point
            shape.centerX = shape.radiusX = shape.middleX = size.width / 2f;
            shape.middleX++;
            shape.centerY = size.height / 3f * 1f;
            shape.middleY = size.height / 2f;
            shape.radiusY = size.height / 3f * 2f;
            shape.arrowTriangleLength = Math.abs(shape.centerY - shape.middleY);
            final Point triangleLengthControl, middleControl, endControl;
            allPoints.add(triangleLengthControl = new Point() {
                @Override
                void set() {
                    double a = Math.atan2(shape.centerY - shape.middleY, shape.centerX - shape.middleX) + Math.PI, r = shape.arrowTriangleLength / 5.5f;
                    set(shape.centerX + (float) (Math.cos(a) * r), shape.centerY + (float) (Math.sin(a) * r));
                }

                @Override
                protected void update(float x, float y) {
                    double a = Math.atan2(shape.centerY - shape.middleY, shape.centerX - shape.middleX) + Math.PI / 2.;
                    float maxdist = MathUtils.distance(shape.centerX, shape.centerY, shape.middleX, shape.middleY) * 5.5f / 2f;
                    shape.arrowTriangleLength = Math.min(maxdist, Math.max(100, -distToLine(x, y, shape.centerX, shape.centerY, a) * 5.5f));
                    set();
                }
            });
            allPoints.add(middleControl = new Point() {
                @Override
                void set() {
                    set(shape.middleX, shape.middleY);
                }

                @Override
                protected void update(float x, float y) {
                    super.update(shape.middleX = x, shape.middleY = y);
                    triangleLengthControl.set();
                }
            });
            movingPoints.add(middleControl);
            allPoints.add(endControl = new Point() {
                @Override
                void set() {
                    set(shape.radiusX, shape.radiusY);
                }

                @Override
                protected void update(float x, float y) {
                    super.update(shape.radiusX = x, shape.radiusY = y);
                    triangleLengthControl.set();
                }
            });
            movingPoints.add(endControl);
        }

        if (shape.getType() == Brush.Shape.SHAPE_TYPE_CIRCLE) {
            allPoints.add(new Point() {
                @Override
                void set() {
                    set(shape.centerX + shape.radiusX, shape.centerY);
                }

                @Override
                protected void update(float x, float y) {
                    super.update(x, y);
                    shape.radiusX = shape.radiusY = MathUtils.distance(shape.centerX, shape.centerY, x, y);
                }
            });
        }

        if (shape.getType() == Brush.Shape.SHAPE_TYPE_STAR) {
            allPoints.add(new Point() {
                final int n = 5;

                @Override
                void set() {
                    float r = Math.min(shape.radiusX, shape.radiusY);
                    double a = -Math.PI / 2f + 2 * Math.PI / n;
                    set(shape.centerX + (float) Math.cos(a) * r, shape.centerY + (float) Math.sin(a) * r);
                }

                @Override
                protected void update(float x, float y) {
                    shape.radiusX = shape.radiusY = MathUtils.distance(shape.centerX, shape.centerY, x, y);
                    shape.rotation += ((float) Math.atan2(shape.centerY - y, x - shape.centerX) + (-Math.PI / 2f + 2 * Math.PI / n));
                    this.set();
                }
            });
        }

        if (shape.getType() == Brush.Shape.SHAPE_TYPE_RECTANGLE || shape.getType() == Brush.Shape.SHAPE_TYPE_BUBBLE) {
            allPoints.add(new CornerPoint(shape, false, false));
            allPoints.add(new CornerPoint(shape, true, false));
            allPoints.add(new CornerPoint(shape, false, true));
            allPoints.add(new CornerPoint(shape, true, true));
            allPoints.add(new Point(true) {
                @Override
                void set() {
                    set(shape.centerX, shape.centerY - Math.abs(shape.radiusY));
                }

                @Override
                protected void update(float x, float y) {
                    shape.rotation += (float) Math.atan2(shape.centerY - y, x - shape.centerX) - Math.PI / 2f;
                    for (int i = 0; i < allPoints.size(); ++i) {
                        Point p = allPoints.get(i);
                        if (p instanceof CornerPoint) {
                            p.set();
                        }
                    }
                }
            });
        }

        if (shape.getType() == Brush.Shape.SHAPE_TYPE_BUBBLE) {
            shape.middleX = shape.centerX + shape.radiusX * .8f;
            shape.middleY = shape.centerY + shape.radiusY * 1.2f + shape.thickness;

            Point bubbleCornerPoint;
            allPoints.add(bubbleCornerPoint = new Point() {
                private void limit() {
                    // do not allow to put this corner inside a bubble
                    if (y > shape.centerY - shape.radiusY && y < shape.centerY + shape.radiusY) {
                        if (x <= shape.centerX && x > shape.centerX - shape.radiusX) {
                            x = shape.centerX - shape.radiusX;
                        } else if (x > shape.centerY && x < shape.centerX + shape.radiusX) {
                            x = shape.centerX + shape.radiusX;
                        }
                    }
                    if (x > shape.centerX - shape.radiusX && x < shape.centerX + shape.radiusX) {
                        if (y <= shape.centerY && y > shape.centerY - shape.radiusY) {
                            y = shape.centerY - shape.radiusY;
                        } else if (y > shape.centerY && y < shape.centerY + shape.radiusY) {
                            y = shape.centerY + shape.radiusY;
                        }
                    }
                }

                @Override
                void set() {
                    set(shape.middleX, shape.middleY);
                }

                @Override
                protected void update(float x, float y) {
                    set(x, y);
                    limit();
                    shape.middleX = this.x;
                    shape.middleY = this.y;
                }

                @Override
                void set(float x, float y) {
                    super.set(shape.middleX = x, shape.middleY = y);
                }
            });
            bubbleCornerPoint.rotate = false;
            movingPoints.add(bubbleCornerPoint);
        }

        center = new Point(true) {
            @Override
            void set() {
                this.x = shape.centerX;
                this.y = shape.centerY;
            }

            @Override
            protected void update(float x, float y) {
                for (int i = 0; i < allPoints.size(); ++i) {
                    Point p = allPoints.get(i);
                    if (p != this) {
                        p.set();
                    }
                }
                super.update(shape.centerX = x, shape.centerY = y);
            }
        };
        if (shape.getType() != Brush.Shape.SHAPE_TYPE_ARROW) {
            center.draw = false;
        }
        center.rotate = false;
        movingPoints.add(center);
        allPoints.add(center);

        renderView.getPainting().paintShape(shape, null);
    }

    public void clear() {
        if (renderView == null || renderView.getPainting() == null || shape == null) {
            return;
        }
        renderView.getPainting().clearShape();
        allPoints.clear();
        movingPoints.clear();
        shape = null;
    }

    public void stop() {
        if (renderView == null || renderView.getPainting() == null || shape == null) {
            return;
        }
        shape.thickness = renderView.getCurrentWeight();
        renderView.getPainting().commitShape(shape, renderView.getCurrentColor());
        allPoints.clear();
        movingPoints.clear();
        shape = null;
        renderView.resetBrush();
    }

    public void dispatchDraw(Canvas canvas) {
        if (renderView == null || renderView.getPainting() == null) {
            return;
        }

        Size size = renderView.getPainting().getSize();

        for (int i = 0; i < allPoints.size(); ++i) {
            Point p = allPoints.get(i);
            if (p.draw && !p.rotate) {
                drawPoint(canvas, size, p);
            }
        }

        if (shape != null && shape.rotation != 0) {
            canvas.save();
            canvas.rotate((float) (-shape.rotation / Math.PI * 180f), shape.centerX / size.width * canvas.getWidth(), shape.centerY / size.height * canvas.getHeight());
        }

        if (shape != null && shape.getType() == Brush.Shape.SHAPE_TYPE_ARROW) {
            canvas.drawLine(
                shape.centerX / size.width * canvas.getWidth(),
                shape.centerY / size.height * canvas.getHeight(),
                shape.middleX / size.width * canvas.getWidth(),
                shape.middleY / size.height * canvas.getHeight(),
                linePaint
            );
            canvas.drawLine(
                shape.radiusX / size.width * canvas.getWidth(),
                shape.radiusY / size.height * canvas.getHeight(),
                shape.middleX / size.width * canvas.getWidth(),
                shape.middleY / size.height * canvas.getHeight(),
                linePaint
            );
        }

//        if (shape != null && shape.type == Brush.Shape.SHAPE_TYPE_STAR) {
//            float r = Math.min(
//                shape.radiusX / size.width * canvas.getWidth(),
//                shape.radiusY / size.height * canvas.getHeight()
//            );
//            canvas.drawCircle(
//                shape.centerX / size.width * canvas.getWidth(),
//                shape.centerY / size.height * canvas.getHeight(),
//                r,
//                linePaint
//            );
//        }

        for (int i = 0; i < allPoints.size(); ++i) {
            Point p = allPoints.get(i);
            if (p.draw && p.rotate) {
                drawPoint(canvas, size, p);
            }
        }

        if (shape != null && shape.rotation != 0) {
            canvas.restore();
        }
    }

    private void drawPoint(Canvas canvas, Size size, Point point) {
        canvas.drawCircle(
                point.x / size.width * canvas.getWidth(),
                point.y / size.height * canvas.getHeight(),
                AndroidUtilities.dp(5),
                point.green ? centerPointPaint : controlPointPaint
        );
        canvas.drawCircle(
                point.x / size.width * canvas.getWidth(),
                point.y / size.height * canvas.getHeight(),
                AndroidUtilities.dp(5),
                point.green ? centerPointStrokePaint :controlPointStrokePaint
        );
    }

    private class CornerPoint extends Point {
        public Shape shape;
        public float rx, ry;
        public CornerPoint(Shape shape, boolean left, boolean top) {
            super();
            this.rotate = false;
            this.shape = shape;
            this.rx = left ? -1 : 1;
            this.ry = top  ? -1 : 1;
            set();
        }

        @Override
        void set() {
            if (shape != null) {
                float x = shape.centerX + rx * shape.radiusX,
                      y = shape.centerY + ry * shape.radiusY;
                rotate(x, y, true);
                set(tempPoint[0], tempPoint[1]);
            }
        }

        @Override
        protected void update(float x, float y) {
            super.update(x, y);

            float ox = shape.centerX + -rx * shape.radiusX,
                  oy = shape.centerY + -ry * shape.radiusY;
            rotate(x, y, false);
            rotate(ox, oy, true);
            ox = tempPoint[0];
            oy = tempPoint[1];

            double g = Math.PI - Math.atan2(y - oy, x - ox);
            double B = g - shape.rotation;
            double rx = Math.cos(B) * MathUtils.distance(x, y, ox, oy);
            double ry = Math.sin(B) * MathUtils.distance(x, y, ox, oy);

            shape.radiusX = (float) Math.abs(rx) / 2f;
            shape.radiusY = (float) Math.abs(ry) / 2f;
            shape.centerX = (x + ox) / 2f;
            shape.centerY = (y + oy) / 2f;

            for (int i = 0; i < allPoints.size(); ++i) {
                allPoints.get(i).set();
            }
        }
    }

    private class Point {
        boolean green;
        boolean rotate = true;
        boolean draw = true;
        float x, y;

        public Point() {
            set();
        }

        public Point(boolean green) {
            this.green = green;
            set();
        }

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public Point(float x, float y, boolean green) {
            this.x = x;
            this.y = y;
            this.green = green;
        }

        void set() {
            // get from shape
        }

        void set(float x, float y) {
            this.x = x;
            this.y = y;
        }

        protected void update(float x, float y) {
            this.x = x;
            this.y = y;
            // implement some logic here
        }
    }
}
