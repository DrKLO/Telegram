package org.telegram.ui.Components.Paint;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShapeDetector {

    private static DispatchQueue queue = new DispatchQueue("ShapeDetector");

    private static class Point {
        public double x;
        public double y;
        public Point(double x, double y) {
            set(x, y);
        }
        public void set(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double distance(double x, double y) {
            return Math.sqrt(Math.pow(x - this.x, 2) + Math.pow(y - this.y, 2));
        }
        public double distance(Point p) {
            return distance(p.x, p.y);
        }

        public static double distance(double x1, double y1, double x2, double y2) {
            return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
        }
    }

    private static class RectD {
        public double left, top, right, bottom;
        public RectD(double left, double top, double right, double bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
        public void union(double x, double y) {
            if (left >= x) {
                left = x;
            }
            if (top >= y) {
                top = y;
            }
            if (right <= x) {
                right = x;
            }
            if (bottom <= y) {
                bottom = y;
            }
        }

        @Override
        public String toString() {
            return "RectD{" +
                    "left=" + left +
                    ", top=" + top +
                    ", right=" + right +
                    ", bottom=" + bottom +
                    '}';
        }
    }

    private int templatesUsageScore;
    private static class Template {
        public int shapeType;
        public ArrayList<Point> points = new ArrayList<>();

        public int score;
    }

    private final int MIN_POINTS = 8;
    private final long TIMEOUT = 150;

    private ArrayList<Point> points = new ArrayList<>();
    private ArrayList<Template> templates = new ArrayList<>();

    private boolean shapeDetected;
    private Utilities.Callback<Shape> onShapeDetected;

    Context context;
    SharedPreferences preferences;
    private boolean isLearning;
    public ShapeDetector(Context context, Utilities.Callback<Shape> onShapeDetected) {
        this.context = context;
        this.onShapeDetected = onShapeDetected;
        preferences = context.getSharedPreferences("shapedetector_conf", Context.MODE_PRIVATE);
        isLearning = preferences.getBoolean("learning", false);
        templatesUsageScore = preferences.getInt("scoreall", 0);
        parseTemplates();
    }


    public static boolean isLearning(Context context) {
        return context.getSharedPreferences("shapedetector_conf", Context.MODE_PRIVATE).getBoolean("learning", false);
    }

    public static void setLearning(Context context, boolean enabled) {
        SharedPreferences preferences = context.getSharedPreferences("shapedetector_conf", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        if (!enabled) {
            editor.clear();
        } else {
            editor.putBoolean("learning", true);
        }
        editor.apply();
    }

    public void scheduleDetect(boolean cancel) {
        if (!busy.get()) {
            if (scheduled.get() && !shapeDetected && cancel) {
                queue.cancelRunnable(detect);
                queue.postRunnable(detect, TIMEOUT);
            }
            if (!scheduled.get()) {
                scheduled.set(true);
                queue.postRunnable(detect, TIMEOUT);
            }
        }
    }

    public void append(double x, double y, boolean cancel) {
        boolean enough;
        synchronized (this) {
            points.add(new Point(x, y));
            enough = points.size() >= MIN_POINTS;
        }
        if (enough) {
            scheduleDetect(cancel);
        }
    }

    public void clear() {
        synchronized (this) {
            points.clear();
        }
        queue.cancelRunnable(detect);
        scheduled.set(false);
        shapeDetected = false;

        if (isLearning && toSave != null) {
            showSaveLearnDialog();
        }
    }

    private void parseTemplates() {
        queue.postRunnable(() -> {
            try {
                Context ctx = ApplicationLoader.applicationContext;
                InputStream in = ctx.getAssets().open("shapes.dat");
                while (in.available() > 5) {
                    Template template = new Template();
                    template.shapeType = in.read();
                    int pointsCount = in.read();
                    int ox = in.read() - 64, oy = in.read() - 64;
                    if (in.available() >= pointsCount * 2) {
                        for (int i = 0; i < pointsCount; ++i) {
                            template.points.add(new Point(in.read() - ox - 127, in.read() - oy - 127));
                        }
                        template.score = preferences.getInt("score" + templates.size(), 0);
                        templates.add(template);
                    } else {
                        break;
                    }
                }
                if (isLearning) {
                    String add = preferences.getString("moretemplates", null);
                    if (add != null) {
                        String[] adds = add.split("\\|");
                        int i = templates.size();
                        for (int j = 0; j < adds.length; ++j) {
                            Template template = new Template();
                            String[] addss = adds[j].split(",");
                            if (addss.length <= 1) {
                                continue;
                            }
                            template.shapeType = Integer.parseInt(addss[0]);
                            for (int k = 1; k < addss.length; k += 2) {
                                template.points.add(new Point(Double.parseDouble(addss[k]), Double.parseDouble(addss[k + 1])));
                            }
                            template.score = preferences.getInt("score" + (i + j), 0);
                            templates.add(template);
                        }
                    }
                }
                in.close();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private ArrayList<Point> toSave = null;

    // == detect logic ==
    private static final int pointsCount = 48;
    private static final double squareSize = 250.0;
    private static final double diagonal = Math.sqrt(squareSize * squareSize + squareSize * squareSize);
    private static final double halfDiagonal = diagonal / 2.;
    private static final double angleRange = Math.PI / 2.0;
    private static final double anglePrecision = Math.PI / 45.;
    private static final double minThreshold = 0.8;

    private AtomicBoolean busy = new AtomicBoolean(false);
    private AtomicBoolean scheduled = new AtomicBoolean(false);
    private Runnable detect = () -> {
        if (busy.get()) {
            return;
        }
        scheduled.set(false);
        busy.set(true);

        long start = System.currentTimeMillis();

        ArrayList<Point> initialPoints, points;
        synchronized (this) {
            if (this.points.size() < MIN_POINTS) {
                busy.set(false);
                return;
            }
            initialPoints = fullClone(this.points);
        }
        initialPoints = resample(initialPoints, pointsCount);
        points = fullClone(initialPoints);
        rotate(points, indicativeAngle(points));
        Point centroid = centroid(points);
        translate(points, -centroid.x, -centroid.y);
        scale(points, squareSize);

        double bestDistance = Double.MAX_VALUE;
        int bestShape = -1, a = -1;

        Point pointsCentroid = centroid(points);
        for (int i = 0; i < templates.size(); ++i) {
            double templateDistance = distanceAtBestAngle(points, pointsCentroid, templates.get(i).points, -angleRange, angleRange, anglePrecision);
            if (templateDistance < bestDistance) {
                a = i;
                bestDistance = templateDistance;
                bestShape = templates.get(i).shapeType;
            }
        }

        if ((1.0 - bestDistance / halfDiagonal) < minThreshold) {
            bestShape = -1;
        }
        final int A = a;
        final Shape shape = constructShape(bestShape, initialPoints);
        if (BuildVars.LOGS_ENABLED) {
            Log.i("shapedetector", "took " + (System.currentTimeMillis() - start) + "ms to " + (shape != null ? "" : "not ") + "detect a shape" + (shape != null ? " (template#" + A + " shape#" + bestShape + ")" : ""));
        }
        AndroidUtilities.runOnUIThread(() -> {
            shapeDetected = shape != null;

            if (shapeDetected && A >= 0 && A < templates.size()) {
                templatesUsageScore++;
                templates.get(A).score++;
                preferences.edit().putInt("score" + A, templates.get(A).score).putInt("scoreall", templatesUsageScore).apply();
                toSave = null;
            } else {
                toSave = points;
            }

            onShapeDetected.run(shape);
        });

        busy.set(false);
    };

    private ArrayList<Point> resample(ArrayList<Point> initialPoints, int totalPoints) {
        ArrayList<Point> newPoints = new ArrayList<>();
        newPoints.add(initialPoints.get(0));
        double interval = pathLength(initialPoints) / (totalPoints - 1);
        double length = 0.0;
        for (int i = 1; i < initialPoints.size(); ++i) {
            double currentLength = initialPoints.get(i - 1).distance(initialPoints.get(i));
            if (length + currentLength >= interval) {
                Point newPoint = new Point(
                    initialPoints.get(i - 1).x + ((interval - length) / currentLength) * (initialPoints.get(i).x - initialPoints.get(i - 1).x),
                    initialPoints.get(i - 1).y + ((interval - length) / currentLength) * (initialPoints.get(i).y - initialPoints.get(i - 1).y)
                );
                newPoints.add(newPoint);
                initialPoints.add(i, newPoint);
                length = 0;
            } else {
                length += currentLength;
            }
        }
        if (newPoints.size() == totalPoints - 1) {
            newPoints.add(initialPoints.get(initialPoints.size() - 1));
        }
        return newPoints;
    }

    private double distanceAtBestAngle(
        ArrayList<Point> points,
        Point pointsCentroid,
        ArrayList<Point> template,
        double fromAngle,
        double toAngle,
        double threshold
    ) {
        double phi = (0.5 * (-1.0 + Math.sqrt(5.0)));

        double x1 = phi * fromAngle + (1.0 - phi) * toAngle;
        double f1 = distanceAtAngle(points, pointsCentroid, template, x1);

        double x2 = (1.0 - phi) * fromAngle + phi * toAngle;
        double f2 = distanceAtAngle(points, pointsCentroid, template, x2);

        while (Math.abs(toAngle - fromAngle) > threshold) {
            if (f1 < f2) {
                toAngle = x2;
                x2 = x1;
                f2 = f1;
                x1 = phi * fromAngle + (1.0 - phi) * toAngle;
                f1 = distanceAtAngle(points, pointsCentroid, template, x1);
            } else {
                fromAngle = x1;
                x1 = x2;
                f1 = f2;
                x2 = (1.0 - phi) * fromAngle + phi * toAngle;
                f2 = distanceAtAngle(points, pointsCentroid, template, x2);
            }
        }

        return Math.min(f1, f2);
    }

    private double distanceAtAngle(ArrayList<Point> points, Point pointsCentroid, ArrayList<Point> template, double radians) {
        double cos = Math.cos(radians), sin = Math.sin(radians);
        int len = Math.min(points.size(), template.size());
        double d = 0.0;
        for (int i = 0; i < len; ++i) {
            Point point = points.get(i);
            d += template.get(i).distance(
                (point.x - pointsCentroid.x) * cos - (point.y - pointsCentroid.y) * sin + pointsCentroid.x,
                (point.x - pointsCentroid.x) * sin + (point.y - pointsCentroid.y) * cos + pointsCentroid.y
            );
        }
        return d / points.size();
    }

    // at best this function must not exist
    private ArrayList<Point> fullClone(ArrayList<Point> points) {
        ArrayList<Point> newPoints = new ArrayList<>();
        for (int i = 0; i < points.size(); ++i) {
            Point point = points.get(i);
            newPoints.add(new Point(point.x, point.y));
        }
        return newPoints;
    }

    private void translate(ArrayList<Point> points, double ox, double oy) {
        for (int i = 0; i < points.size(); ++i) {
            Point point = points.get(i);
            point.x += ox;
            point.y += oy;
        }
    }

    private void scale(ArrayList<Point> points, double size) {
        RectD boundingBox = boundingBox(points);
        double width = boundingBox.right - boundingBox.left, height = boundingBox.bottom - boundingBox.top;
        for (int i = 0; i < points.size(); ++i) {
            Point point = points.get(i);
            point.x *= size / width;
            point.y *= size / height;
        }
    }

    private void rotate(ArrayList<Point> points, double radians) {
        rotate(points, radians, centroid(points));
    }
    private void rotate(ArrayList<Point> points, double radians, Point centroid) {
        double cos = Math.cos(radians), sin = Math.sin(radians), newX;
        for (int i = 0; i < points.size(); ++i) {
            Point point = points.get(i);
            newX = (point.x - centroid.x) * cos - (point.y - centroid.y) * sin + centroid.x;
            point.y = (point.x - centroid.x) * sin + (point.y - centroid.y) * cos + centroid.y;
            point.x = newX;
        }
    }

    private RectD boundingBox(ArrayList<Point> points) {
        if (points.size() <= 0) {
            return null;
        }
        double firstPointX = points.get(0).x, firstPointY = points.get(0).y;
        RectD rect = new RectD(firstPointX, firstPointY, firstPointX, firstPointY);
        for (int i = 1; i < points.size(); ++i) {
            Point point = points.get(i);
            rect.union(point.x, point.y);
        }
        return rect;
    }
    private Point centroid(ArrayList<Point> points) {
        Point centroidPoint = new Point(0., 0.);
        for (int i = 0; i < points.size(); ++i) {
            Point point = points.get(i);
            centroidPoint.x += point.x;
            centroidPoint.y += point.y;
        }
        centroidPoint.x /= points.size();
        centroidPoint.y /= points.size();
        return centroidPoint;
    }
    private double indicativeAngle(ArrayList<Point> points) {
        Point centroid = centroid(points);
        return Math.atan2(centroid.y - points.get(0).y, centroid.x - points.get(0).x);
    }
    private double pathLength(ArrayList<Point> points) {
        double total = 0;
        for (int i = 1; i < points.size(); ++i) {
            total += points.get(i - 1).distance(points.get(i));
        }
        return total;
    }


    // === position finding logic ===
    private int findAnglePoint(ArrayList<Point> points) {
        return findAnglePoint(points, 0);
    }
    private int findAnglePoint(ArrayList<Point> points, int next) {
        for (int i = Math.max(1, points.size() / 4); i < points.size() - 1; ++i) {
            Point p1 = points.get(i - 1);
            Point p2 = points.get(i);
            Point p3 = points.get(i + 1);
            double p12 = p1.distance(p2), p13 = p1.distance(p3), p23 = p2.distance(p3);
            double angle = Math.acos((p12*p12 + p13*p13 - p23*p23)/(2 * p12 * p13)) / Math.PI * 180;
            if (angle > 18) {
                if (next > 0) {
                    next--;
                    continue;
                }
                return i;
            }
        }
        return -1;
    }

    private Shape constructShape(int type, ArrayList<Point> points) {
        if (type < 0 || type >= Brush.Shape.SHAPES_LIST.size() || points.size() < 1) {
            return null;
        }
        Shape shape = new Shape(Brush.Shape.make(type));
        if (type == Brush.Shape.SHAPE_TYPE_ARROW) {
            Point pointer, middle, first;
            int pointerIndex = findAnglePoint(points);
            if (pointerIndex > 0) {
                if (pointerIndex > 10) {
                    pointerIndex -= 2;
                }
                pointer = points.get(pointerIndex);
                middle = points.get(pointerIndex / 2);
                first = points.get(0);
            } else {
//                pointer = points.get(points.size() - 1);
//                middle = points.get(points.size() / 2);
//                first = points.get(0);
                return null;
            }
            shape.centerX = (float) pointer.x;
            shape.centerY = (float) pointer.y;
            shape.middleX = (float) middle.x;
            shape.middleY = (float) middle.y;
            shape.radiusX = (float) first.x;
            shape.radiusY = (float) first.y;
            shape.arrowTriangleLength = 16;
        } else {
            Point centroid = centroid(points);
            shape.centerX = (float) centroid.x;
            shape.centerY = (float) centroid.y;
            RectD bounds = boundingBox(points);
            shape.radiusX = (float) (bounds.right - bounds.left) / 2f;
            shape.radiusY = (float) (bounds.bottom - bounds.top) / 2f;
            if (type == Brush.Shape.SHAPE_TYPE_STAR) {
                int pointerIndex = findAnglePoint(points, 1);
                if (pointerIndex > 0) {
                    Point angle = points.get(pointerIndex);
                    shape.rotation = (float) Math.atan2(angle.y - shape.centerY, angle.x - shape.centerX);
                }
            }
        }
        return shape;
    }

    private void showSaveLearnDialog() {
        ArrayList<Point> points = toSave;
        new AlertDialog.Builder(context)
            .setTitle("Shape?")
            .setItems(new String[] { "Log all", "Circle", "Rectangle", "Star", "Bubble", "Arrow", "None" }, (di, which) -> {
                if (which == 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[");
                    for (int i = 0; i < templates.size(); ++i) {
                        Template template = templates.get(i);
                        if (i > 0) {
                            sb.append(",\n");
                        }
                        sb.append("\t{\n\t\t\"shape\": ").append(template.shapeType).append(",\n\t\t\"points\": [");
                        for (int j = 0; j < template.points.size(); ++j) {
                            if (j > 0) {
                                sb.append(",");
                            }
                            Point point = template.points.get(j);
                            sb.append("[").append(Math.round(point.x)).append(",").append(Math.round(point.y)).append("]");
                        }
                        sb.append("],\n\t\t\"freq\": ").append(Math.round(template.score / (float) templatesUsageScore * 100 * 100f) / 100f).append("\n\t}");
                    }
                    sb.append("\n]");
                    Log.i("shapedetector", sb.toString());
                } else {
                    Template template = new Template();
                    template.shapeType = which - 1;
                    template.points = points;
                    templates.add(template);

                    String s = preferences.getString("moretemplates", null);
                    if (s == null) {
                        s = "" + template.shapeType;
                    } else {
                        s += "|" + template.shapeType;
                    }
                    for (int i = 0; i < points.size(); ++i) {
                        s += "," + Math.round(points.get(i).x) + "," + Math.round(points.get(i).y);
                    }
                    preferences.edit().putString("moretemplates", s).apply();
                }
            }).show();
        toSave = null;
    }
}
