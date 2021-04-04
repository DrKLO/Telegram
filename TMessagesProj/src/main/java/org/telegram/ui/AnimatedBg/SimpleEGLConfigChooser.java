package org.telegram.ui.AnimatedBg;

import android.opengl.GLSurfaceView;
import android.util.Log;

import org.telegram.messenger.BuildConfig;

import java.util.Locale;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

public class SimpleEGLConfigChooser implements GLSurfaceView.EGLConfigChooser {

    private static final int[] RGB888 = {
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_RENDERABLE_TYPE, 4,
            EGL10.EGL_SAMPLE_BUFFERS, 1,
            EGL10.EGL_SAMPLES, 2,
            EGL10.EGL_NONE
    };

    private static final int[] RGB565 = {
            EGL10.EGL_RED_SIZE, 5,
            EGL10.EGL_GREEN_SIZE, 6,
            EGL10.EGL_BLUE_SIZE, 5,
            EGL10.EGL_RENDERABLE_TYPE, 4,
            EGL10.EGL_NONE
    };

    private static final int[][] configs = {RGB888, RGB565};

    @Override
    public EGLConfig chooseConfig(
            EGL10 egl, EGLDisplay display
    ) {
        int[] resultValue = new int[1];
        int configsCount = 0;
        int[] configSpec = null;
        for (int[] config : configs) {
            configSpec = config;
            if (!egl.eglChooseConfig(display, configSpec, null, 0, resultValue)) {
                continue;
            }
            configsCount = resultValue[0];
            if (configsCount > 0) {
                break;
            }
        }
        if (configsCount <= 0 || configSpec == null) {
            throw new IllegalArgumentException(
                    "Your device doesn't support any of standard configurations");
        }
        EGLConfig[] configs = new EGLConfig[configsCount];
        egl.eglChooseConfig(display, configSpec, configs, configsCount, resultValue);
        EGLConfig resultConfig = null;
        int maxSamples = 0;
        for (EGLConfig config : configs) {
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_RED_SIZE, resultValue);
            int red = resultValue[0];
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_ALPHA_SIZE, resultValue);
            int alpha = resultValue[0];
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_SAMPLES, resultValue);
            int samples = resultValue[0];
            if(red == 8 && alpha == 0) {
                if(samples > maxSamples) {
                    resultConfig = config;
                    maxSamples = samples;
                }
            }
        }
        if(resultConfig == null) {
            resultConfig = configs[0];
        }
        printConfig(egl, display, resultConfig);
        return resultConfig;
    }

    private void printConfig(EGL10 egl, EGLDisplay display, EGLConfig config) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        int[] resultValue = new int[1];
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_RED_SIZE, resultValue);
        int r = resultValue[0];
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_GREEN_SIZE, resultValue);
        int g = resultValue[0];
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_BLUE_SIZE, resultValue);
        int b = resultValue[0];
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_ALPHA_SIZE, resultValue);
        int a = resultValue[0];
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_SAMPLES, resultValue);
        int samples = resultValue[0];
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_SAMPLE_BUFFERS, resultValue);
        int antiAlias = resultValue[0];
        Log.d(
                "Config",
                String.format(Locale.ROOT,
                        "RGBA: %d, %d, %d, %d Samples: %d Anti Alias: %d",
                        r, g, b, a, samples, antiAlias
                )
        );
    }
}
