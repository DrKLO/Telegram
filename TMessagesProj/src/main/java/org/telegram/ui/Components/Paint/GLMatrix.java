package org.telegram.ui.Components.Paint;

import android.graphics.Matrix;

public class GLMatrix {

    public static float[] LoadOrtho(float left, float right, float bottom, float top, float near, float far) {
        float r_l = right - left;
        float t_b = top - bottom;
        float f_n = far - near;
        float tx = -(right + left) / (right - left);
        float ty = -(top + bottom) / (top - bottom);
        float tz = -(far + near) / (far - near);

        float out[] = new float[16];

        out[0] = 2.0f / r_l;
        out[1] = 0.0f;
        out[2] = 0.0f;
        out[3] = 0.0f;

        out[4] = 0.0f;
        out[5] = 2.0f / t_b;
        out[6] = 0.0f;
        out[7] = 0.0f;

        out[8] = 0.0f;
        out[9] = 0.0f;
        out[10] = -2.0f / f_n;
        out[11] = 0.0f;

        out[12] = tx;
        out[13] = ty;
        out[14] = tz;
        out[15] = 1.0f;

        return out;
    }

    public static float[] LoadGraphicsMatrix(Matrix matrix) {
        float m[] = new float[16];
        float v[] = new float[9];
        matrix.getValues(v);

        m[0] = v[Matrix.MSCALE_X]; //m.a;
        m[1] = v[Matrix.MSKEW_X]; //m.b;
        m[2] = 0.0f;
        m[3] = 0.0f;

        m[4] = v[Matrix.MSKEW_Y]; //m.c;
        m[5] = v[Matrix.MSCALE_Y]; //m.d;
        m[6] = 0.0f;
        m[7] = 0.0f;

        m[8] = 0.0f;
        m[9] = 0.0f;
        m[10] = 1.0f;
        m[11] = 0.0f;

        m[12] = v[Matrix.MTRANS_X]; //m.tx;
        m[13] = v[Matrix.MTRANS_Y]; //m.ty;
        m[14] = 0.0f;
        m[15] = 1.0f;

        return m;
    }

    public static float[] MultiplyMat4f(float[] a, float[] b) {
        float out[] = new float[16];

        out[0] = a[0] * b[0] + a[4] * b[1] + a[8] * b[2] + a[12] * b[3];
        out[1] = a[1] * b[0] + a[5] * b[1] + a[9] * b[2] + a[13] * b[3];
        out[2] = a[2] * b[0] + a[6] * b[1] + a[10] * b[2] + a[14] * b[3];
        out[3] = a[3] * b[0] + a[7] * b[1] + a[11] * b[2] + a[15] * b[3];

        out[4] = a[0] * b[4] + a[4] * b[5] + a[8] * b[6] + a[12] * b[7];
        out[5] = a[1] * b[4] + a[5] * b[5] + a[9] * b[6] + a[13] * b[7];
        out[6] = a[2] * b[4] + a[6] * b[5] + a[10] * b[6] + a[14] * b[7];
        out[7] = a[3] * b[4] + a[7] * b[5] + a[11] * b[6] + a[15] * b[7];

        out[8] = a[0] * b[8] + a[4] * b[9] + a[8] * b[10] + a[12] * b[11];
        out[9] = a[1] * b[8] + a[5] * b[9] + a[9] * b[10] + a[13] * b[11];
        out[10] = a[2] * b[8] + a[6] * b[9] + a[10] * b[10] + a[14] * b[11];
        out[11] = a[3] * b[8] + a[7] * b[9] + a[11] * b[10] + a[15] * b[11];

        out[12] = a[0] * b[12] + a[4] * b[13] + a[8] * b[14] + a[12] * b[15];
        out[13] = a[1] * b[12] + a[5] * b[13] + a[9] * b[14] + a[13] * b[15];
        out[14] = a[2] * b[12] + a[6] * b[13] + a[10] * b[14] + a[14] * b[15];
        out[15] = a[3] * b[12] + a[7] * b[13] + a[11] * b[14] + a[15] * b[15];

        return out;
    }
}
