package org.telegram.ui.Components.Premium.GLIcon;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.Theme;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLIconRenderer implements GLSurfaceView.Renderer {

    private int mWidth;
    private int mHeight;
    public Icon3D model;
    public float angleX = 0;
    public float angleX2 = 0;
    public float angleY = 0;

    private static final float Z_NEAR = 1f;
    private static final float Z_FAR = 200f;

    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mRotationMatrix = new float[16];
    Context context;

    Bitmap backgroundBitmap;

    public float gradientStartX;
    public float gradientStartY;
    public float gradientScaleX;
    public float gradientScaleY;

    public boolean forceNight;
    boolean night;
    int color1;
    int color2;

    public int colorKey1 = Theme.key_premiumStartGradient1;
    public int colorKey2 = Theme.key_premiumStartGradient2;

    private final int style;
    private final int type;
    public final static int FRAGMENT_STYLE = 0;
    public final static int DIALOG_STYLE = 1;
    public final static int BUSINESS_STYLE = 2;
    public boolean isDarkBackground;

    public GLIconRenderer(Context context, int style, int type) {
        this.context = context;
        this.style = style;
        this.type = type;
        updateColors();
    }

    public static int loadShader(int type, String shaderSrc) {
        int shader;
        int[] compiled = new int[1];

        shader = GLES20.glCreateShader(type);

        if (shader == 0) {
            return 0;
        }

        GLES20.glShaderSource(shader, shaderSrc);
        GLES20.glCompileShader(shader);
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);

        if (compiled[0] == 0) {
            throw new RuntimeException("Could not compile program: "
                    + GLES20.glGetShaderInfoLog(shader) + " " + shaderSrc);
        }

        return shader;
    }

    public static void checkGlError(String glOperation, int program) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(glOperation + ": glError " + error + GLES20.glGetShaderInfoLog(program));
        }
    }

    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        if (model != null) {
            model.destroy();
        }
        model = new Icon3D(context, type);
        if (backgroundBitmap != null) {
            model.setBackground(backgroundBitmap);
        }
        if (isDarkBackground) {
            model.spec1 = 1f;
            model.spec2 = 0.2f;
        }
    }

    private float dt;
    public void setDeltaTime(float dt) {
        this.dt = dt;
    }

    public void onDrawFrame(GL10 glUnused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 100, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
        Matrix.setIdentityM(mRotationMatrix, 0);

        Matrix.translateM(mRotationMatrix, 0, 0, angleX2, 0);

        Matrix.rotateM(mRotationMatrix, 0, -angleY, 1f, 0, 0f);
        Matrix.rotateM(mRotationMatrix, 0, -angleX, 0, 1.0f, 0);

        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mRotationMatrix, 0);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);

        if (model != null) {
            model.night = night;
            model.gradientColor1 = color1;
            model.gradientColor2 = color2;
            model.draw(mMVPMatrix, mRotationMatrix, mWidth, mHeight, gradientStartX, gradientScaleX, gradientStartY, gradientScaleY, dt);
        }
    }

    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        mWidth = width;
        mHeight = height;

        GLES20.glViewport(0, 0, mWidth, mHeight);
        float aspect = (float) width / height;

        Matrix.perspectiveM(mProjectionMatrix, 0, 53.13f, aspect, Z_NEAR, Z_FAR);
    }

    public void setBackground(Bitmap gradientTextureBitmap) {
        if (model != null) {
            model.setBackground(gradientTextureBitmap);
        }
        backgroundBitmap = gradientTextureBitmap;
    }

    public void updateColors() {
        night = forceNight || ColorUtils.calculateLuminance(Theme.getColor(Theme.key_dialogBackground)) < 0.5f;
        color1 = Theme.getColor(colorKey1);
        color2 = Theme.getColor(colorKey2);
        isDarkBackground = style == DIALOG_STYLE && ColorUtils.calculateLuminance(Theme.getColor(Theme.key_dialogBackground)) < 0.5f;
    }
}