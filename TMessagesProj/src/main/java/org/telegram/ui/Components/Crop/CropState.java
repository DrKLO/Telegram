package org.telegram.ui.Components.Crop;

import android.graphics.Bitmap;
import android.graphics.Matrix;

public class CropState {
    private float width;
    private float height;

    private float x;
    private float y;
    private float scale;
    private float minimumScale;
    private float rotation;
    private Matrix matrix;

    private float[] values;

    public CropState(Bitmap bitmap) {
        width = bitmap.getWidth();
        height = bitmap.getHeight();

        x = 0.0f;
        y = 0.0f;
        scale = 1.0f;
        rotation = 0.0f;
        matrix = new Matrix();

        values = new float[9];
    }

    private void updateValues() {
        matrix.getValues(values);
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public void translate(float x, float y) {
        this.x += x;
        this.y += y;
        matrix.postTranslate(x, y);
    }

    public float getX() {
        updateValues();
        return values[Matrix.MTRANS_X];
    }

    public float getY() {
        updateValues();
        return values[Matrix.MTRANS_Y];
    }

    public void scale(float s, float pivotX, float pivotY) {
        scale *= s;
        matrix.postScale(s, s, pivotX, pivotY);
    }

    public float getScale() {
        return scale;
    }

    public void rotate(float angle, float pivotX, float pivotY) {
        rotation += angle;
        matrix.postRotate(angle, pivotX, pivotY);
    }

    public float getRotation() {
        return rotation;
    }

    public void getConcatMatrix(Matrix toMatrix) {
        toMatrix.postConcat(matrix);
    }

    public Matrix getMatrix() {
        Matrix m = new Matrix();
        m.set(matrix);
        return m;
    }
}
