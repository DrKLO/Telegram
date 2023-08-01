package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Display;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Intro;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.CameraView;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;

public class RecordControlRenderer extends DispatchQueue {

    private static final String VERTEX_SHADER =
        "attribute vec4 aPosition;\n" +
        "void main() {\n" +
        "   gl_Position = aPosition;\n" +
        "}\n";
    private final static String FRAGMENT_SHADER =
        "precision lowp float;\n" +
        "void main() {\n" +
        "   gl_FragColor = vec4(1., 0., 0., 1.);\n" +
        "}\n";

    private final Object layoutLock = new Object();
    private FloatBuffer vertexBuffer;

    private final static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private final static int EGL_OPENGL_ES2_BIT = 4;
    private SurfaceTexture surfaceTexture;
    private EGL10 egl10;
    private EGLDisplay eglDisplay;
    private EGLContext eglContext;
    private EGLSurface eglSurface;
    private EGLConfig eglConfig;
    private boolean initied;

    private final int DO_RENDER_MESSAGE = 0;
    private final int DO_SHUTDOWN_MESSAGE = 1;

    private int drawProgram;
    private int positionHandle;
    private int resolutionUniform;

    private int width, height;

    private Integer cameraId = 0;

    public RecordControlRenderer(SurfaceTexture surface) {
        super("RecordControlRenderer");
        surfaceTexture = surface;
    }

    private boolean initGL() {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("RecordControlRenderer " + "start init gl");
        }
        egl10 = (EGL10) EGLContext.getEGL();

        eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("eglGetDisplay failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
            }
            eglDisplay = null;
            finish();
            return false;
        }

        int[] version = new int[2];
        if (!egl10.eglInitialize(eglDisplay, version)) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("eglInitialize failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
            }
            finish();
            return false;
        }

        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = new int[]{
            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 0,
            EGL10.EGL_DEPTH_SIZE, 0,
            EGL10.EGL_STENCIL_SIZE, 0,
            EGL10.EGL_NONE
        };
        if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("eglChooseConfig failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
            }
            finish();
            return false;
        } else if (configsCount[0] > 0) {
            eglConfig = configs[0];
        } else {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("eglConfig not initialized");
            }
            finish();
            return false;
        }
        int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
        eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
        if (eglContext == null || eglContext == EGL10.EGL_NO_CONTEXT) {
            eglContext = null;
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
            }
            finish();
            return false;
        }

        if (surfaceTexture != null) {
            eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
        } else {
            finish();
            return false;
        }

        if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("createWindowSurface failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
            }
            finish();
            return false;
        }
        if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
            }
            finish();
            return false;
        }
        GL gl = eglContext.getGL();

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        if (vertexShader != 0 && fragmentShader != 0) {
            drawProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(drawProgram, vertexShader);
            GLES20.glAttachShader(drawProgram, fragmentShader);
            GLES20.glLinkProgram(drawProgram);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(drawProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("failed link shader");
                }
                GLES20.glDeleteProgram(drawProgram);
                drawProgram = 0;
            } else {
                positionHandle = GLES20.glGetAttribLocation(drawProgram, "aPosition");
            }
        } else {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("failed creating shader");
            }
            finish();
            return false;
        }

        if (BuildVars.LOGS_ENABLED) {
            FileLog.e("gl initied");
        }

        float[] verticesData = {
            -1.0f, -1.0f, 0,
            1.0f, -1.0f, 0,
            -1.0f, 1.0f, 0,
            1.0f, 1.0f, 0
        };

        vertexBuffer = ByteBuffer.allocateDirect(verticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(verticesData).position(0);

        return true;
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e(GLES20.glGetShaderInfoLog(shader));
            }
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    public void finish() {
        if (eglSurface != null) {
            egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            egl10.eglDestroySurface(eglDisplay, eglSurface);
            eglSurface = null;
        }
        if (eglContext != null) {
            egl10.eglDestroyContext(eglDisplay, eglContext);
            eglContext = null;
        }
        if (eglDisplay != null) {
            egl10.eglTerminate(eglDisplay);
            eglDisplay = null;
        }
    }

    final int array[] = new int[1];

    private void draw() {
        if (!initied) {
            return;
        }

        if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
            if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                return;
            }
        }

        egl10.eglQuerySurface(eglDisplay, eglSurface, EGL10.EGL_WIDTH, array);
        int drawnWidth = array[0];
        egl10.eglQuerySurface(eglDisplay, eglSurface, EGL10.EGL_HEIGHT, array);
        int drawnHeight = array[0];

        GLES20.glViewport(0, 0, drawnWidth, drawnHeight);

        GLES20.glUseProgram(drawProgram);

        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);
        GLES20.glEnableVertexAttribArray(positionHandle);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glUseProgram(0);

        egl10.eglSwapBuffers(eglDisplay, eglSurface);
    }


    @Override
    public void run() {
        initied = initGL();
        super.run();
    }

    @Override
    public void handleMessage(Message inputMessage) {
        int what = inputMessage.what;

        switch (what) {
            case DO_RENDER_MESSAGE:
                draw(

                );
                break;
        }
    }

    public void shutdown() {
        postRunnable(() -> {
            finish();
            Looper looper = Looper.myLooper();
            if (looper != null) {
                looper.quit();
            }
        });
    }

    public void requestRender() {
        Handler handler = getHandler();
        if (handler != null) {
            sendMessage(handler.obtainMessage(DO_RENDER_MESSAGE, cameraId), 0);
        }
    }
}