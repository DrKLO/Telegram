package org.telegram.ui.Components.Paint;

import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

public class Utils {

    public static void HasGLError() {
        int error = GLES20.glGetError();
        if (error != 0) {
            Log.d("Paint", GLUtils.getEGLErrorString(error));
        }
    }

    public static void RectFIntegral(RectF rect) {
        rect.left = (int) Math.floor(rect.left);
        rect.top = (int) Math.floor(rect.top);
        rect.right = (int) Math.ceil(rect.right);
        rect.bottom = (int) Math.ceil(rect.bottom);
    }
}
