package org.telegram.ui.Components.Paint;

import android.graphics.PointF;

public class Point {

    public double x;
    public double y;
    public double z;

    public boolean edge;

    public Point(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Point(Point point) {
        x = point.x;
        y = point.y;
        z = point.z;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)  {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Point)) {
            return false;
        }
        Point other = (Point) obj;
        return this.x == other.x && this.y == other.y && this.z == other.z;
    }

    Point multiplySum(Point point, double scalar) {
        return new Point((x + point.x) * scalar, (y + point.y) * scalar, (z + point.z) * scalar);
    }

    Point multiplyAndAdd(double scalar, Point point) {
        return new Point((x * scalar) + point.x, (y * scalar) + point.y, (z * scalar) + point.z);
    }

    void alteringAddMultiplication(Point point, double scalar) {
        x = x + (point.x * scalar);
        y = y + (point.y * scalar);
        z = z + (point.z * scalar);
    }

    Point add(Point point) {
        return new Point(x + point.x, y + point.y, z + point.z);
    }

    Point substract(Point point) {
        return new Point(x - point.x, y - point.y, z - point.z);
    }

    Point multiplyByScalar(double scalar) {
        return new Point(x * scalar, y * scalar, z * scalar);
    }

    Point getNormalized() {
        return multiplyByScalar(1.0 / getMagnitude());
    }

    private double getMagnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    float getDistanceTo(Point point) {
        return (float) Math.sqrt(Math.pow(x - point.x, 2) + Math.pow(y - point.y, 2) + Math.pow(z - point.z, 2));
    }

    PointF toPointF() {
        return new PointF((float) x, (float) y);
    }
}
