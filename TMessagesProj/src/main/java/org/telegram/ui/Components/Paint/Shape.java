package org.telegram.ui.Components.Paint;

import android.graphics.RectF;

import androidx.annotation.NonNull;

public class Shape {

    public Shape(@NonNull Brush.Shape brush) {
        this.brush = brush;
    }

    public final Brush.Shape brush;

    public float centerX, centerY;
    public float radiusX, radiusY;
    public float thickness;
    public float rounding;
    public float rotation;

    // for arrows it is middle, for bubbles it is corner
    public float middleX, middleY;

    public float arrowTriangleLength;

    public boolean fill;

    public int getType() {
        return brush.getShapeShaderType();
    }

    public void getBounds(RectF rect) {
        if (getType() == Brush.Shape.SHAPE_TYPE_ARROW) {
            rect.set(centerX - arrowTriangleLength, centerY - arrowTriangleLength, centerX + arrowTriangleLength, centerY + arrowTriangleLength);
            rect.union(radiusX, radiusY);
            rect.union(middleX, middleY);
        } else {
            float r = Math.max(Math.abs(radiusX), Math.abs(radiusY));
            rect.set(
                centerX - r * 1.42f,
                centerY - r * 1.42f,
                centerX + r * 1.42f,
                centerY + r * 1.42f
            );
            if (getType() == Brush.Shape.SHAPE_TYPE_BUBBLE) {
                rect.union(middleX, middleY);
            }
        }
        rect.inset(-thickness - 3, -thickness - 3);
    }
}
