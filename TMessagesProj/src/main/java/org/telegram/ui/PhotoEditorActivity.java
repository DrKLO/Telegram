/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ImageLoader;
import org.telegram.android.LocaleController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.ui.AnimationCompat.AnimatorSetProxy;
import org.telegram.ui.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.ui.AnimationCompat.ViewProxy;
import org.telegram.ui.Cells.PhotoEditToolCell;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

public class PhotoEditorActivity extends BaseFragment {

    private GLSurfaceView glView;
    private PhotoCropView cropView;

    private SeekBar valueSeekBar;
    private LinearLayout toolsView;
    private LinearLayout cropButtonsView;
    private ImageView imageView;
    private ImageView filtersButton;
    private ImageView toolButton;
    private AnimatorSetProxy rotationAnimation;

    private ActionBarMenuItem doneButton;
    private ActionBarMenuItem sizeButton;
    private ActionBarMenuItem rotateButton;

    private boolean sameBitmap = false;
    private int currentMode = 0;
    private boolean freeformCrop;
    private boolean onlyCrop;

    private PhotoCropActivity.PhotoEditActivityDelegate delegate;

    private int selectedTool = 0;
    private int rotateDegree = 0;

    private Bitmap bitmapToEdit;
    private String bitmapKey;

    private float highlightsValue = 0; //0 100
    private float contrastValue = 0; //-100 100
    private float shadowsValue = 0; //0 100
    private float exposureValue = 0; //-100 100
    private float saturationValue = 0; //-100 100
    private float warmthValue = 0; //-100 100
    private float vignetteValue = 0; //0 100
    private float grainValue = 0; //0 100
    private float width = 0;
    private float height = 0;

    private boolean donePressed = false;

    private final static int done_button = 1;
    private final static int rotate_button = 2;
    private final static int size_button = 3;

    private class PhotoCropView extends FrameLayout {

        private Paint rectPaint;
        private Paint circlePaint;
        private Paint halfPaint;
        private Paint shadowPaint;
        private float rectSizeX = 600;
        private float rectSizeY = 600;
        private int draggingState = 0;
        private float oldX = 0, oldY = 0;
        private int bitmapWidth = 1, bitmapHeight = 1, bitmapX, bitmapY;
        private float rectX = -1, rectY = -1;

        public PhotoCropView(Context context) {
            super(context);

            rectPaint = new Paint();
            rectPaint.setColor(0xb2ffffff);
            rectPaint.setStrokeWidth(AndroidUtilities.dp(2));
            rectPaint.setStyle(Paint.Style.STROKE);
            circlePaint = new Paint();
            circlePaint.setColor(0xffffffff);
            halfPaint = new Paint();
            halfPaint.setColor(0x7f000000);
            shadowPaint = new Paint();
            shadowPaint.setColor(0x1a000000);
            setWillNotDraw(false);

            setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    float x = motionEvent.getX();
                    float y = motionEvent.getY();
                    int cornerSide = AndroidUtilities.dp(20);
                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        if (rectX - cornerSide < x && rectX + cornerSide > x && rectY - cornerSide < y && rectY + cornerSide > y) {
                            draggingState = 1;
                        } else if (rectX - cornerSide + rectSizeX < x && rectX + cornerSide + rectSizeX > x && rectY - cornerSide < y && rectY + cornerSide > y) {
                            draggingState = 2;
                        } else if (rectX - cornerSide < x && rectX + cornerSide > x && rectY - cornerSide + rectSizeY < y && rectY + cornerSide + rectSizeY > y) {
                            draggingState = 3;
                        } else if (rectX - cornerSide + rectSizeX < x && rectX + cornerSide + rectSizeX > x && rectY - cornerSide + rectSizeY < y && rectY + cornerSide + rectSizeY > y) {
                            draggingState = 4;
                        } else if (rectX < x && rectX + rectSizeX > x && rectY < y && rectY + rectSizeY > y) {
                            draggingState = 5;
                        } else {
                            draggingState = 0;
                        }
                        if (draggingState != 0) {
                            PhotoCropView.this.requestDisallowInterceptTouchEvent(true);
                        }
                        oldX = x;
                        oldY = y;
                    } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        draggingState = 0;
                    } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && draggingState != 0) {
                        float diffX = x - oldX;
                        float diffY = y - oldY;
                        if (draggingState == 5) {
                            rectX += diffX;
                            rectY += diffY;

                            if (rectX < bitmapX) {
                                rectX = bitmapX;
                            } else if (rectX + rectSizeX > bitmapX + bitmapWidth) {
                                rectX = bitmapX + bitmapWidth - rectSizeX;
                            }
                            if (rectY < bitmapY) {
                                rectY = bitmapY;
                            } else if (rectY + rectSizeY > bitmapY + bitmapHeight) {
                                rectY = bitmapY + bitmapHeight - rectSizeY;
                            }
                        } else {
                            if (draggingState == 1) {
                                if (rectSizeX - diffX < 160) {
                                    diffX = rectSizeX - 160;
                                }
                                if (rectX + diffX < bitmapX) {
                                    diffX = bitmapX - rectX;
                                }
                                if (!freeformCrop) {
                                    if (rectY + diffX < bitmapY) {
                                        diffX = bitmapY - rectY;
                                    }
                                    rectX += diffX;
                                    rectY += diffX;
                                    rectSizeX -= diffX;
                                    rectSizeY -= diffX;
                                } else {
                                    if (rectSizeY - diffY < 160) {
                                        diffY = rectSizeY - 160;
                                    }
                                    if (rectY + diffY < bitmapY) {
                                        diffY = bitmapY - rectY;
                                    }
                                    rectX += diffX;
                                    rectY += diffY;
                                    rectSizeX -= diffX;
                                    rectSizeY -= diffY;
                                }
                            } else if (draggingState == 2) {
                                if (rectSizeX + diffX < 160) {
                                    diffX = -(rectSizeX - 160);
                                }
                                if (rectX + rectSizeX + diffX > bitmapX + bitmapWidth) {
                                    diffX = bitmapX + bitmapWidth - rectX - rectSizeX;
                                }
                                if (!freeformCrop) {
                                    if (rectY - diffX < bitmapY) {
                                        diffX = rectY - bitmapY;
                                    }
                                    rectY -= diffX;
                                    rectSizeX += diffX;
                                    rectSizeY += diffX;
                                } else {
                                    if (rectSizeY - diffY < 160) {
                                        diffY = rectSizeY - 160;
                                    }
                                    if (rectY + diffY < bitmapY) {
                                        diffY = bitmapY - rectY;
                                    }
                                    rectY += diffY;
                                    rectSizeX += diffX;
                                    rectSizeY -= diffY;
                                }
                            } else if (draggingState == 3) {
                                if (rectSizeX - diffX < 160) {
                                    diffX = rectSizeX - 160;
                                }
                                if (rectX + diffX < bitmapX) {
                                    diffX = bitmapX - rectX;
                                }
                                if (!freeformCrop) {
                                    if (rectY + rectSizeX - diffX > bitmapY + bitmapHeight) {
                                        diffX = rectY + rectSizeX - bitmapY - bitmapHeight;
                                    }
                                    rectX += diffX;
                                    rectSizeX -= diffX;
                                    rectSizeY -= diffX;
                                } else {
                                    if (rectY + rectSizeY + diffY > bitmapY + bitmapHeight) {
                                        diffY = bitmapY + bitmapHeight - rectY - rectSizeY;
                                    }
                                    rectX += diffX;
                                    rectSizeX -= diffX;
                                    rectSizeY += diffY;
                                    if (rectSizeY < 160) {
                                        rectSizeY = 160;
                                    }
                                }
                            } else if (draggingState == 4) {
                                if (rectX + rectSizeX + diffX > bitmapX + bitmapWidth) {
                                    diffX = bitmapX + bitmapWidth - rectX - rectSizeX;
                                }
                                if (!freeformCrop) {
                                    if (rectY + rectSizeX + diffX > bitmapY + bitmapHeight) {
                                        diffX = bitmapY + bitmapHeight - rectY - rectSizeX;
                                    }
                                    rectSizeX += diffX;
                                    rectSizeY += diffX;
                                } else {
                                    if (rectY + rectSizeY + diffY > bitmapY + bitmapHeight) {
                                        diffY = bitmapY + bitmapHeight - rectY - rectSizeY;
                                    }
                                    rectSizeX += diffX;
                                    rectSizeY += diffY;
                                }
                                if (rectSizeX < 160) {
                                    rectSizeX = 160;
                                }
                                if (rectSizeY < 160) {
                                    rectSizeY = 160;
                                }
                            }
                        }

                        oldX = x;
                        oldY = y;
                        invalidate();
                    }
                    return true;
                }
            });
        }

        public Bitmap getBitmap() {
            float percX = (rectX - bitmapX) / bitmapWidth;
            float percY = (rectY - bitmapY) / bitmapHeight;
            float percSizeX = rectSizeX / bitmapWidth;
            float percSizeY = rectSizeY / bitmapWidth;
            int x = (int)(percX * bitmapToEdit.getWidth());
            int y = (int)(percY * bitmapToEdit.getHeight());
            int sizeX = (int)(percSizeX * bitmapToEdit.getWidth());
            int sizeY = (int)(percSizeY * bitmapToEdit.getWidth());
            if (x + sizeX > bitmapToEdit.getWidth()) {
                sizeX = bitmapToEdit.getWidth() - x;
            }
            if (y + sizeY > bitmapToEdit.getHeight()) {
                sizeY = bitmapToEdit.getHeight() - y;
            }
            try {
                return Bitmap.createBitmap(bitmapToEdit, x, y, sizeX, sizeY);
            } catch (Throwable e) {
                FileLog.e("tmessags", e);
                System.gc();
                try {
                    return Bitmap.createBitmap(bitmapToEdit, x, y, sizeX, sizeY);
                } catch (Throwable e2) {
                    FileLog.e("tmessages", e2);
                }
            }
            return null;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(bitmapX, bitmapY, bitmapX + bitmapWidth, rectY, halfPaint);
            canvas.drawRect(bitmapX, rectY, rectX, rectY + rectSizeY, halfPaint);
            canvas.drawRect(rectX + rectSizeX, rectY, bitmapX + bitmapWidth, rectY + rectSizeY, halfPaint);
            canvas.drawRect(bitmapX, rectY + rectSizeY, bitmapX + bitmapWidth, bitmapY + bitmapHeight, halfPaint);

            int side = AndroidUtilities.dp(1);
            canvas.drawRect(rectX - side * 2, rectY - side * 2, rectX - side * 2 + AndroidUtilities.dp(20), rectY, circlePaint);
            canvas.drawRect(rectX - side * 2, rectY - side * 2, rectX, rectY - side * 2 + AndroidUtilities.dp(20), circlePaint);

            canvas.drawRect(rectX + rectSizeX + side * 2 - AndroidUtilities.dp(20), rectY - side * 2, rectX + rectSizeX + side * 2, rectY, circlePaint);
            canvas.drawRect(rectX + rectSizeX, rectY - side * 2, rectX + rectSizeX + side * 2, rectY - side * 2 + AndroidUtilities.dp(20), circlePaint);

            canvas.drawRect(rectX - side * 2, rectY + rectSizeY + side * 2 - AndroidUtilities.dp(20), rectX, rectY + rectSizeY + side * 2, circlePaint);
            canvas.drawRect(rectX - side * 2, rectY + rectSizeY, rectX - side * 2 + AndroidUtilities.dp(20), rectY + rectSizeY + side * 2, circlePaint);

            canvas.drawRect(rectX + rectSizeX + side * 2 - AndroidUtilities.dp(20), rectY + rectSizeY, rectX + rectSizeX + side * 2, rectY + rectSizeY + side * 2, circlePaint);
            canvas.drawRect(rectX + rectSizeX, rectY + rectSizeY + side * 2 - AndroidUtilities.dp(20), rectX + rectSizeX + side * 2, rectY + rectSizeY + side * 2, circlePaint);

            for (int a = 1; a < 3; a++) {
                canvas.drawRect(rectX + rectSizeX / 3 * a - side, rectY, rectX + side * 2 + rectSizeX / 3 * a, rectY + rectSizeY, shadowPaint);
                canvas.drawRect(rectX, rectY + rectSizeY / 3 * a - side, rectX + rectSizeX, rectY + rectSizeY / 3 * a + side * 2, shadowPaint);
            }

            for (int a = 1; a < 3; a++) {
                canvas.drawRect(rectX + rectSizeX / 3 * a, rectY, rectX + side + rectSizeX / 3 * a, rectY + rectSizeY, circlePaint);
                canvas.drawRect(rectX, rectY + rectSizeY / 3 * a, rectX + rectSizeX, rectY + rectSizeY / 3 * a + side, circlePaint);
            }

            canvas.drawRect(rectX, rectY, rectX + rectSizeX, rectY + rectSizeY, rectPaint);
        }
    }

    class MyGLSurfaceView extends GLSurfaceView {

        public MyGLSurfaceView(Context context){
            super(context);
            setEGLContextClientVersion(2);
            setRenderer(new MyGLRenderer());
            setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
    }

    public class MyGLRenderer implements GLSurfaceView.Renderer {

        private int trivialShaderProgram;

        private int positionHandle;
        private int inputTexCoordHandle;
        private int photoImageHandle;
        private int shadowsHandle;
        private int highlightsHandle;
        private int exposureHandle;
        private int contrastHandle;
        private int saturationHandle;
        private int warmthHandle;
        private int vignetteHandle;
        private int grainHandle;
        private int grainWidthHandle;
        private int grainHeightHandle;

        private int[] textures = new int[1];

        private FloatBuffer vertexBuffer;
        private FloatBuffer textureBuffer;
        private FloatBuffer vertexSaveBuffer;

        private static final String trivialVertexShaderCode =
                "attribute vec4 position;" +
                        "attribute vec4 inputTexCoord;" +
                        "varying vec2 texCoord;" +
                        "void main() {" +
                        "gl_Position = position;" +
                        "texCoord = inputTexCoord.xy;" +
                        "}";

        private static final String trivialFragmentShaderCode =
                "varying highp vec2 texCoord;" +
                        "uniform sampler2D photoImage;" +
                        "uniform lowp float shadows;" +
                        "uniform highp float width;" +
                        "uniform highp float height;" +
                        "const mediump vec3 hsLuminanceWeighting = vec3(0.3, 0.3, 0.3);" +
                        "uniform lowp float highlights;" +
                        "uniform highp float exposure;" +
                        "uniform lowp float contrast;" +
                        "const mediump vec3 satLuminanceWeighting = vec3(0.2126, 0.7152, 0.0722);" +
                        "uniform lowp float saturation;" +
                        "uniform lowp float warmth;" +
                        "uniform lowp float grain;" +
                        "const lowp float permTexUnit = 1.0 / 256.0;" +
                        "const lowp float permTexUnitHalf = 0.5 / 256.0;" +
                        "const lowp float grainsize = 2.3;" +
                        "uniform lowp float vignette;" +
                        "highp float getLuma(highp vec3 rgbP) { return (0.299 * rgbP.r) + (0.587 * rgbP.g) + (0.114 * rgbP.b); }" +
                        "highp vec3 rgbToYuv(highp vec3 inP) { highp vec3 outP; outP.r = getLuma(inP); outP.g = (1.0 / 1.772) * (inP.b - outP.r); outP.b = (1.0 / 1.402) * (inP.r - outP.r); return outP; }" +
                        "lowp vec3 yuvToRgb(highp vec3 inP) { highp float y = inP.r; highp float u = inP.g; highp float v = inP.b; lowp vec3 outP; outP.r = 1.402 * v + y; outP.g = (y - (0.299 * 1.402 / 0.587) * v - (0.114 * 1.772 / 0.587) * u); outP.b = 1.772 * u + y; return outP; } " +
                        "lowp float easeInOutSigmoid(lowp float value, lowp float strength) { lowp float t = 1.0 / (1.0 - strength); if (value > 0.5) { return 1.0 - pow(2.0 - 2.0 * value, t) * 0.5; } else { return pow(2.0 * value, t) * 0.5; } }" +
                        "highp vec4 rnm(in highp vec2 tc) { highp float noise = sin(dot(tc,vec2(12.9898,78.233))) * 43758.5453; highp float noiseR = fract(noise)*2.0-1.0; highp float noiseG = fract(noise*1.2154)*2.0-1.0; highp float noiseB = fract(noise*1.3453)*2.0-1.0; " +
                        "highp float noiseA = fract(noise*1.3647)*2.0-1.0; return vec4(noiseR,noiseG,noiseB,noiseA); } highp float fade(in highp float t) { return t*t*t*(t*(t*6.0-15.0)+10.0); } highp float pnoise3D(in highp vec3 p) { highp vec3 pi = permTexUnit*floor(p)+permTexUnitHalf; " +
                        "highp vec3 pf = fract(p); highp float perm00 = rnm(pi.xy).a ; highp vec3 grad000 = rnm(vec2(perm00, pi.z)).rgb * 4.0 - 1.0; highp float n000 = dot(grad000, pf); highp vec3 grad001 = rnm(vec2(perm00, pi.z + permTexUnit)).rgb * 4.0 - 1.0; " +
                        "highp float n001 = dot(grad001, pf - vec3(0.0, 0.0, 1.0)); highp float perm01 = rnm(pi.xy + vec2(0.0, permTexUnit)).a ; highp vec3 grad010 = rnm(vec2(perm01, pi.z)).rgb * 4.0 - 1.0; highp float n010 = dot(grad010, pf - vec3(0.0, 1.0, 0.0));" +
                        "highp vec3 grad011 = rnm(vec2(perm01, pi.z + permTexUnit)).rgb * 4.0 - 1.0; highp float n011 = dot(grad011, pf - vec3(0.0, 1.0, 1.0)); highp float perm10 = rnm(pi.xy + vec2(permTexUnit, 0.0)).a ;" +
                        "highp vec3 grad100 = rnm(vec2(perm10, pi.z)).rgb * 4.0 - 1.0; highp float n100 = dot(grad100, pf - vec3(1.0, 0.0, 0.0)); highp vec3 grad101 = rnm(vec2(perm10, pi.z + permTexUnit)).rgb * 4.0 - 1.0;" +
                        "highp float n101 = dot(grad101, pf - vec3(1.0, 0.0, 1.0)); highp float perm11 = rnm(pi.xy + vec2(permTexUnit, permTexUnit)).a ; highp vec3 grad110 = rnm(vec2(perm11, pi.z)).rgb * 4.0 - 1.0; highp float n110 = dot(grad110, pf - vec3(1.0, 1.0, 0.0));" +
                        "highp vec3 grad111 = rnm(vec2(perm11, pi.z + permTexUnit)).rgb * 4.0 - 1.0; highp float n111 = dot(grad111, pf - vec3(1.0, 1.0, 1.0)); highp vec4 n_x = mix(vec4(n000, n001, n010, n011), vec4(n100, n101, n110, n111), fade(pf.x));" +
                        "highp vec2 n_xy = mix(n_x.xy, n_x.zw, fade(pf.y)); highp float n_xyz = mix(n_xy.x, n_xy.y, fade(pf.z)); return n_xyz; } lowp vec2 coordRot(in lowp vec2 tc, in lowp float angle) { lowp float rotX = ((tc.x * 2.0 - 1.0) * cos(angle)) - ((tc.y * 2.0 - 1.0) * sin(angle));" +
                        "lowp float rotY = ((tc.y * 2.0 - 1.0) * cos(angle)) + ((tc.x * 2.0 - 1.0) * sin(angle)); rotX = rotX * 0.5 + 0.5; rotY = rotY * 0.5 + 0.5; return vec2(rotX,rotY); }void main() {lowp vec4 source = texture2D(photoImage, texCoord);lowp vec4 result = source;" +
                        "const lowp float toolEpsilon = 0.005;mediump float hsLuminance = dot(result.rgb, hsLuminanceWeighting); mediump float shadow = clamp((pow(hsLuminance, 1.0 / (shadows + 1.0)) + (-0.76) * pow(hsLuminance, 2.0 / (shadows + 1.0))) - hsLuminance, 0.0, 1.0);" +
                        "mediump float highlight = clamp((1.0 - (pow(1.0 - hsLuminance, 1.0 / (2.0 - highlights)) + (-0.8) * pow(1.0 - hsLuminance, 2.0 / (2.0 - highlights)))) - hsLuminance, -1.0, 0.0);" +
                        "lowp vec3 shresult = vec3(0.0, 0.0, 0.0) + ((hsLuminance + shadow + highlight) - 0.0) * ((result.rgb - vec3(0.0, 0.0, 0.0)) / (hsLuminance - 0.0)); result = vec4(shresult.rgb, result.a);" +
                        "if (abs(exposure) > toolEpsilon) { mediump float mag = exposure * 1.045; mediump float exppower = 1.0 + abs(mag); if (mag < 0.0) { exppower = 1.0 / exppower; } result.r = 1.0 - pow((1.0 - result.r), exppower);" +
                        "result.g = 1.0 - pow((1.0 - result.g), exppower); result.b = 1.0 - pow((1.0 - result.b), exppower); }result = vec4(((result.rgb - vec3(0.5)) * contrast + vec3(0.5)), result.a);" +
                        "lowp float satLuminance = dot(result.rgb, satLuminanceWeighting); lowp vec3 greyScaleColor = vec3(satLuminance); result = vec4(mix(greyScaleColor, result.rgb, saturation), result.a);" +
                        "if (abs(warmth) > toolEpsilon) { highp vec3 yuvVec; if (warmth > 0.0 ) { yuvVec = vec3(0.1765, -0.1255, 0.0902); } else { yuvVec = -vec3(0.0588, 0.1569, -0.1255); } highp vec3 yuvColor = rgbToYuv(result.rgb); highp float luma = yuvColor.r;" +
                        "highp float curveScale = sin(luma * 3.14159); yuvColor += 0.375 * warmth * curveScale * yuvVec; result.rgb = yuvToRgb(yuvColor); }if (abs(grain) > toolEpsilon) { highp vec3 rotOffset = vec3(1.425, 3.892, 5.835);" +
                        "highp vec2 rotCoordsR = coordRot(texCoord, rotOffset.x); highp vec3 noise = vec3(pnoise3D(vec3(rotCoordsR * vec2(width / grainsize, height / grainsize),0.0))); lowp vec3 lumcoeff = vec3(0.299,0.587,0.114);" +
                        "lowp float luminance = dot(result.rgb, lumcoeff); lowp float lum = smoothstep(0.2, 0.0, luminance); lum += luminance; noise = mix(noise,vec3(0.0),pow(lum,4.0)); result.rgb = result.rgb + noise * grain; }" +
                        "if (abs(vignette) > toolEpsilon) { const lowp float midpoint = 0.7; const lowp float fuzziness = 0.62; lowp float radDist = length(texCoord - 0.5) / sqrt(0.5);" +
                        "lowp float mag = easeInOutSigmoid(radDist * midpoint, fuzziness) * vignette * 0.645; result.rgb = mix(pow(result.rgb, vec3(1.0 / (1.0 - mag))), vec3(0.0), mag * mag); }gl_FragColor = result;}";

        private int loadShader(int type, String shaderCode) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, shaderCode);
            GLES20.glCompileShader(shader);
            int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
            if (compileStatus[0] == 0) {
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
            return shader;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, javax.microedition.khronos.egl.EGLConfig config) {

            float squareCoordinates[] = {
                    -1.0f, 1.0f,
                    1.0f, 1.0f,
                    -1.0f, -1.0f,
                    1.0f, -1.0f};

            ByteBuffer bb = ByteBuffer.allocateDirect(squareCoordinates.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(squareCoordinates);
            vertexBuffer.position(0);

            float squareCoordinates2[] = {
                    -1.0f, -1.0f,
                    1.0f, -1.0f,
                    -1.0f, 1.0f,
                    1.0f, 1.0f};

            bb = ByteBuffer.allocateDirect(squareCoordinates2.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexSaveBuffer = bb.asFloatBuffer();
            vertexSaveBuffer.put(squareCoordinates2);
            vertexSaveBuffer.position(0);

            float textureCoordinates[] = {
                    0.0f, 0.0f,
                    1.0f, 0.0f,
                    0.0f, 1.0f,
                    1.0f, 1.0f,
            };

            bb = ByteBuffer.allocateDirect(textureCoordinates.length * 4);
            bb.order(ByteOrder.nativeOrder());
            textureBuffer = bb.asFloatBuffer();
            textureBuffer.put(textureCoordinates);
            textureBuffer.position(0);

            GLES20.glGenTextures(1, textures, 0);
            gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmapToEdit, 0);

            int trivialVertexShader = loadShader(GLES20.GL_VERTEX_SHADER, trivialVertexShaderCode);
            int trivialFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, trivialFragmentShaderCode);

            if (trivialVertexShader != 0 && trivialFragmentShader != 0) {
                trivialShaderProgram = GLES20.glCreateProgram();
                GLES20.glAttachShader(trivialShaderProgram, trivialVertexShader);
                GLES20.glAttachShader(trivialShaderProgram, trivialFragmentShader);
                GLES20.glBindAttribLocation(trivialShaderProgram, 0, "position");
                GLES20.glBindAttribLocation(trivialShaderProgram, 1, "inputTexCoord");

                GLES20.glLinkProgram(trivialShaderProgram);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(trivialShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    GLES20.glDeleteProgram(trivialShaderProgram);
                    trivialShaderProgram = 0;
                }
            }

            if (trivialShaderProgram != 0) {
                positionHandle = GLES20.glGetAttribLocation(trivialShaderProgram, "position");
                inputTexCoordHandle = GLES20.glGetAttribLocation(trivialShaderProgram, "inputTexCoord");
                photoImageHandle = GLES20.glGetUniformLocation(trivialShaderProgram, "photoImage");
                shadowsHandle = GLES20.glGetUniformLocation(trivialShaderProgram, "shadows");
                highlightsHandle = GLES20.glGetUniformLocation(trivialShaderProgram, "highlights");
                exposureHandle = GLES20.glGetUniformLocation(trivialShaderProgram, "exposure");
                contrastHandle = GLES20.glGetUniformLocation(trivialShaderProgram, "contrast");
                saturationHandle = GLES20.glGetUniformLocation(trivialShaderProgram, "saturation");
                warmthHandle = GLES20.glGetUniformLocation(trivialShaderProgram, "warmth");
                vignetteHandle = GLES20.glGetUniformLocation(trivialShaderProgram, "vignette");
                grainHandle = GLES20.glGetUniformLocation(trivialShaderProgram, "grain");
                grainWidthHandle = GLES20.glGetUniformLocation(trivialShaderProgram, "width");
                grainHeightHandle = GLES20.glGetUniformLocation(trivialShaderProgram, "height");
                GLES20.glUseProgram(trivialShaderProgram);
            }
        }

        public void onDrawFrame(GL10 unused) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glUniform1i(photoImageHandle, 0);
            GLES20.glUniform1f(shadowsHandle, getShadowsValue());
            GLES20.glUniform1f(highlightsHandle, getHighlightsValue());
            GLES20.glUniform1f(exposureHandle, getExposureValue());
            GLES20.glUniform1f(contrastHandle, getContrastValue());
            GLES20.glUniform1f(saturationHandle, getSaturationValue());
            GLES20.glUniform1f(warmthHandle, getWarmthValue());
            GLES20.glUniform1f(vignetteHandle, getVignetteValue());
            GLES20.glUniform1f(grainHandle, getGrainValue());
            GLES20.glUniform1f(grainWidthHandle, width);
            GLES20.glUniform1f(grainHeightHandle, height);
            GLES20.glEnableVertexAttribArray(inputTexCoordHandle);
            GLES20.glVertexAttribPointer(inputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(positionHandle);
            if (donePressed) {
                GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexSaveBuffer);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                final Bitmap bitmap = saveTexture((int)width, (int)height);
                donePressed = false;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        delegate.didFinishEdit(bitmap, getArguments());
                        finishFragment();
                    }
                });
            }
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        public void onSurfaceChanged(GL10 unused, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        public Bitmap saveTexture(int width, int height) {
            //int[] frame = new int[1];
            //GLES20.glGenFramebuffers(1, frame, 0);
            //GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frame[0]);
            //GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);
            ByteBuffer buffer = ByteBuffer.allocate(width * height * 4);
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            //GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            //GLES20.glDeleteFramebuffers(1, frame, 0);
            return bitmap;
        }
    }

    public PhotoEditorActivity(Bundle args, Bitmap bitmap, String key) {
        super(args);
        bitmapToEdit = bitmap;
        bitmapKey = key;
        if (bitmapToEdit != null && key != null) {
            ImageLoader.getInstance().incrementUseCount(key);
        }
    }

    private float getShadowsValue() {
        return (shadowsValue / 100.0f) * 0.65f;
    }

    private float getHighlightsValue() {
        return 1 - (highlightsValue / 100.0f);
    }

    private float getExposureValue() {
        return (exposureValue / 100.0f);
    }

    private float getContrastValue() {
        return (contrastValue / 100.0f) * 0.3f + 1;
    }

    private float getWarmthValue() {
        return warmthValue / 100.0f;
    }

    private float getVignetteValue() {
        return vignetteValue / 100.0f;
    }

    private float getGrainValue() {
        return grainValue / 100.0f * 0.04f;
    }

    private float getSaturationValue() {
        float value = (saturationValue / 100.0f);
        if (value < 0) {
            value *= 0.55f;
        } else {
            value *= 1.05f;
        }
        return value + 1;
    }

    @Override
    public boolean onFragmentCreate() {
        swipeBackEnabled = false;
        freeformCrop = getArguments().getBoolean("freeformCrop", false);
        onlyCrop = getArguments().getBoolean("onlyCrop", false);
        if (bitmapToEdit == null) {
            String photoPath = getArguments().getString("photoPath");
            Uri photoUri = getArguments().getParcelable("photoUri");
            if (photoPath == null && photoUri == null) {
                return false;
            }
            if (photoPath != null) {
                File f = new File(photoPath);
                if (!f.exists()) {
                    return false;
                }
            }
            int size = 0;
            if (AndroidUtilities.isTablet()) {
                size = AndroidUtilities.dp(520);
            } else {
                size = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            }
            bitmapToEdit = ImageLoader.loadBitmap(photoPath, photoUri, size, size, true);
            if (bitmapToEdit == null) {
                return false;
            }
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (bitmapKey != null) {
            if (ImageLoader.getInstance().decrementUseCount(bitmapKey) && !ImageLoader.getInstance().isInCache(bitmapKey)) {
                bitmapKey = null;
            }
        }
        if (bitmapKey == null && bitmapToEdit != null && !sameBitmap) {
            bitmapToEdit.recycle();
            bitmapToEdit = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (glView != null) {
            glView.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (glView != null) {
            glView.onResume();
        }
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBar.setBackgroundColor(0xff262626);
            actionBar.setItemsBackground(R.drawable.bar_selector_picker);
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString("EditImage", R.string.EditImage));
            actionBar.setCastShadows(false);
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == done_button) {
                        donePressed = true;
                        glView.requestRender();
                    } else if (id == rotate_button) {
                        int newRotation = rotateDegree;
                        newRotation += 90;
                        fixLayoutInternal(newRotation, true);
                    }
                }
            });

            ActionBarMenu menu = actionBar.createMenu();
            rotateButton = menu.addItemWithWidth(rotate_button, R.drawable.photo_rotate, AndroidUtilities.dp(56));
            sizeButton = menu.addItemWithWidth(size_button, R.drawable.photo_sizes, AndroidUtilities.dp(56));
            doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

            rotateButton.setVisibility(View.GONE);
            sizeButton.setVisibility(View.GONE);

            FrameLayout frameLayout = null;
            fragmentView = frameLayout = new FrameLayout(getParentActivity());
            fragmentView.setBackgroundColor(0xff262626);

            imageView = new ImageView(getParentActivity());
            imageView.setScaleType(ImageView.ScaleType.MATRIX);
            imageView.setImageBitmap(bitmapToEdit);
            frameLayout.addView(imageView);

            cropView = new PhotoCropView(getParentActivity());
            cropView.setVisibility(View.GONE);
            frameLayout.addView(cropView);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) cropView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            cropView.setLayoutParams(layoutParams);

            cropButtonsView = new LinearLayout(getParentActivity());
            cropButtonsView.setVisibility(View.GONE);
            frameLayout.addView(cropButtonsView);
            layoutParams = (FrameLayout.LayoutParams) cropButtonsView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = AndroidUtilities.dp(48);
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            cropButtonsView.setLayoutParams(layoutParams);

            ImageView button = new ImageView(getParentActivity());
            button.setScaleType(ImageView.ScaleType.CENTER);
            button.setImageResource(R.drawable.ic_close_white);
            cropButtonsView.addView(button);
            LinearLayout.LayoutParams layoutParams1 = (LinearLayout.LayoutParams) button.getLayoutParams();
            layoutParams1.width = AndroidUtilities.dp(48);
            layoutParams1.height = AndroidUtilities.dp(48);
            button.setLayoutParams(layoutParams1);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onlyCrop) {
                        finishFragment();
                    } else {
                        switchToMode(0, true);
                    }
                }
            });

            button = new ImageView(getParentActivity());
            button.setScaleType(ImageView.ScaleType.CENTER);
            button.setImageResource(R.drawable.ic_done);
            cropButtonsView.addView(button);
            layoutParams1 = (LinearLayout.LayoutParams) button.getLayoutParams();
            layoutParams1.width = AndroidUtilities.dp(48);
            layoutParams1.height = AndroidUtilities.dp(48);
            layoutParams1.leftMargin = AndroidUtilities.dp(146);
            button.setLayoutParams(layoutParams1);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onlyCrop) {
                        if (delegate != null && currentMode == 1) {
                            Bitmap bitmap = cropView.getBitmap();
                            if (bitmap == bitmapToEdit) {
                                sameBitmap = true;
                            }
                            delegate.didFinishEdit(bitmap, getArguments());
                            currentMode = 0;
                            finishFragment();
                        }
                    } else {
                        switchToMode(0, false);
                    }
                }
            });

            if (!onlyCrop) {
                toolsView = new LinearLayout(getParentActivity());
                frameLayout.addView(toolsView);
                layoutParams = (FrameLayout.LayoutParams) toolsView.getLayoutParams();
                layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
                layoutParams.height = AndroidUtilities.dp(48);
                layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                toolsView.setLayoutParams(layoutParams);

                button = new ImageView(getParentActivity());
                button.setScaleType(ImageView.ScaleType.CENTER);
                button.setImageResource(R.drawable.photo_crop);
                toolsView.addView(button);
                layoutParams1 = (LinearLayout.LayoutParams) button.getLayoutParams();
                layoutParams1.width = AndroidUtilities.dp(48);
                layoutParams1.height = AndroidUtilities.dp(48);
                button.setLayoutParams(layoutParams1);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchToMode(1, true);
                    }
                });

                filtersButton = new ImageView(getParentActivity());
                filtersButton.setScaleType(ImageView.ScaleType.CENTER);
                filtersButton.setImageResource(R.drawable.photo_filters);
                toolsView.addView(filtersButton);
                layoutParams1 = (LinearLayout.LayoutParams) filtersButton.getLayoutParams();
                layoutParams1.width = AndroidUtilities.dp(48);
                layoutParams1.height = AndroidUtilities.dp(48);
                layoutParams1.leftMargin = AndroidUtilities.dp(54);
                filtersButton.setLayoutParams(layoutParams1);

                toolButton = new ImageView(getParentActivity());
                toolButton.setScaleType(ImageView.ScaleType.CENTER);
                toolButton.setImageResource(R.drawable.photo_tune);
                toolsView.addView(toolButton);
                layoutParams1 = (LinearLayout.LayoutParams) toolButton.getLayoutParams();
                layoutParams1.width = AndroidUtilities.dp(48);
                layoutParams1.height = AndroidUtilities.dp(48);
                layoutParams1.leftMargin = AndroidUtilities.dp(54);
                toolButton.setLayoutParams(layoutParams1);

                glView = new MyGLSurfaceView(getParentActivity());
                glView.setVisibility(View.GONE);
                frameLayout.addView(glView);
                layoutParams = (FrameLayout.LayoutParams) glView.getLayoutParams();
                layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
                glView.setLayoutParams(layoutParams);

                RecyclerListView toolsView = new RecyclerListView(getParentActivity());
                LinearLayoutManager layoutManager = new LinearLayoutManager(getParentActivity());
                layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                toolsView.setLayoutManager(layoutManager);
                toolsView.setClipToPadding(false);
                if (Build.VERSION.SDK_INT >= 9) {
                    toolsView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
                }
                toolsView.setAdapter(new ToolsAdapter(getParentActivity()));
                toolsView.setVisibility(View.GONE);
                frameLayout.addView(toolsView);
                layoutParams = (FrameLayout.LayoutParams) toolsView.getLayoutParams();
                layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                layoutParams.height = AndroidUtilities.dp(60);
                layoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
                layoutParams.bottomMargin = AndroidUtilities.dp(40);
                toolsView.setLayoutParams(layoutParams);
                toolsView.addOnItemTouchListener(new RecyclerListView.RecyclerListViewItemClickListener(getParentActivity(), new RecyclerListView.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, int i) {
                        selectedTool = i;
                        if (i == 0) {
                            valueSeekBar.setMax(100);
                            valueSeekBar.setProgress((int) highlightsValue);
                        } else if (i == 1) {
                            valueSeekBar.setMax(200);
                            valueSeekBar.setProgress((int) contrastValue + 100);
                        } else if (i == 2) {
                            valueSeekBar.setMax(200);
                            valueSeekBar.setProgress((int) exposureValue + 100);
                        } else if (i == 3) {
                            valueSeekBar.setMax(200);
                            valueSeekBar.setProgress((int) warmthValue + 100);
                        } else if (i == 4) {
                            valueSeekBar.setMax(200);
                            valueSeekBar.setProgress((int) saturationValue + 100);
                        } else if (i == 5) {
                            valueSeekBar.setMax(100);
                            valueSeekBar.setProgress((int) vignetteValue);
                        } else if (i == 6) {
                            valueSeekBar.setMax(100);
                            valueSeekBar.setProgress((int) shadowsValue);
                        } else if (i == 7) {
                            valueSeekBar.setMax(100);
                            valueSeekBar.setProgress((int) grainValue);
                        }
                    }
                }));

                valueSeekBar = new SeekBar(getParentActivity());
                valueSeekBar.setVisibility(View.GONE);
                valueSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (!fromUser) {
                            return;
                        }
                        if (selectedTool == 0) {
                            highlightsValue = progress;
                        } else if (selectedTool == 1) {
                            contrastValue = progress - 100;
                        } else if (selectedTool == 2) {
                            exposureValue = progress - 100;
                        } else if (selectedTool == 3) {
                            warmthValue = progress - 100;
                        } else if (selectedTool == 4) {
                            saturationValue = progress - 100;
                        } else if (selectedTool == 5) {
                            vignetteValue = progress;
                        } else if (selectedTool == 6) {
                            shadowsValue = progress;
                        } else if (selectedTool == 7) {
                            grainValue = progress;
                        }
                        glView.requestRender();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {

                    }
                });
                try {
                    Field field = ProgressBar.class.getDeclaredField("mMinHeight");
                    field.setAccessible(true);
                    field.setInt(valueSeekBar, AndroidUtilities.dp(40));
                    field = ProgressBar.class.getDeclaredField("mMaxHeight");
                    field.setAccessible(true);
                    field.setInt(valueSeekBar, AndroidUtilities.dp(40));
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                frameLayout.addView(valueSeekBar);
                layoutParams = (FrameLayout.LayoutParams) valueSeekBar.getLayoutParams();
                layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                layoutParams.height = AndroidUtilities.dp(40);
                layoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
                layoutParams.leftMargin = AndroidUtilities.dp(10);
                layoutParams.rightMargin = AndroidUtilities.dp(10);
                valueSeekBar.setLayoutParams(layoutParams);
            } else {
                switchToMode(1, false);
            }

            fixLayout();
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void switchToMode(final int mode, final boolean animated) {
        if (animated) {
            if (currentMode == 0) {
                AnimatorSetProxy animatorSet = new AnimatorSetProxy();
                animatorSet.playTogether(
                        ObjectAnimatorProxy.ofFloat(doneButton, "alpha", 1.0f, 0.0f),
                        ObjectAnimatorProxy.ofFloat(toolsView, "translationY", 0, AndroidUtilities.dp(48)));
                animatorSet.setDuration(150);
                animatorSet.setInterpolator(new DecelerateInterpolator());
                animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        processFromMode(currentMode, mode, animated);
                    }
                });
                animatorSet.start();
            } else if (currentMode == 1) {
                AnimatorSetProxy animatorSet = new AnimatorSetProxy();
                animatorSet.playTogether(
                        ObjectAnimatorProxy.ofFloat(cropView, "alpha", 1.0f, 0.0f),
                        ObjectAnimatorProxy.ofFloat(cropButtonsView, "translationY", 0, AndroidUtilities.dp(48)),
                        ObjectAnimatorProxy.ofFloat(rotateButton, "alpha", 1.0f, 0.0f),
                        ObjectAnimatorProxy.ofFloat(sizeButton, "alpha", 1.0f, 0.0f));
                animatorSet.setDuration(150);
                animatorSet.setInterpolator(new DecelerateInterpolator());
                animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        processFromMode(currentMode, mode, animated);
                    }
                });
                animatorSet.start();
            }
        } else {
            processFromMode(currentMode, mode, animated);
        }
    }

    private void processFromMode(int from, int to, boolean animated) {
        if (from == 0) {
            doneButton.setVisibility(View.GONE);
            if (toolsView != null) {
                toolsView.setVisibility(View.GONE);
            }
            processToMode(to, animated);
        } else if (from == 1) {
            cropView.setVisibility(View.GONE);
            rotateButton.setVisibility(View.GONE);
            if (freeformCrop) {
                sizeButton.setVisibility(View.GONE);
            }
            cropButtonsView.setVisibility(View.GONE);
            processToMode(to, animated);
        }
    }

    private void processToMode(int to, boolean animated) {
        currentMode = to;
        if (currentMode == 0) {
            doneButton.setVisibility(View.VISIBLE);
            toolsView.setVisibility(View.VISIBLE);
            actionBar.setTitle(LocaleController.getString("EditImage", R.string.EditImage));
            if (animated) {
                AnimatorSetProxy animatorSet = new AnimatorSetProxy();
                animatorSet.playTogether(
                        ObjectAnimatorProxy.ofFloat(doneButton, "alpha", 0.0f, 1.0f),
                        ObjectAnimatorProxy.ofFloat(toolsView, "translationY", AndroidUtilities.dp(48), 0));
                animatorSet.setDuration(150);
                animatorSet.setInterpolator(new AccelerateInterpolator());
                animatorSet.start();
            }
        } else if (currentMode == 1) {
            cropView.setVisibility(View.VISIBLE);
            rotateButton.setVisibility(View.VISIBLE);
            if (freeformCrop) {
                sizeButton.setVisibility(View.VISIBLE);
            }
            cropButtonsView.setVisibility(View.VISIBLE);
            actionBar.setTitle(LocaleController.getString("CropImage", R.string.CropImage));
            if (animated) {
                AnimatorSetProxy animatorSet = new AnimatorSetProxy();
                animatorSet.playTogether(
                        ObjectAnimatorProxy.ofFloat(cropView, "alpha", 0.0f, 1.0f),
                        ObjectAnimatorProxy.ofFloat(cropButtonsView, "translationY", AndroidUtilities.dp(48), 0),
                        ObjectAnimatorProxy.ofFloat(rotateButton, "alpha", 0.0f, 1.0f),
                        ObjectAnimatorProxy.ofFloat(sizeButton, "alpha", 0.0f, 1.0f));
                animatorSet.setDuration(150);
                animatorSet.setInterpolator(new AccelerateInterpolator());
                animatorSet.start();
            }
        }
    }

    private void fixLayoutInternal(int rotation, final boolean animated) {
        if (bitmapToEdit == null || fragmentView == null) {
            return;
        }

        int viewWidth = fragmentView.getWidth() - AndroidUtilities.dp(28);
        int viewHeight = fragmentView.getHeight() - AndroidUtilities.dp(28 + 48);

        rotateDegree = rotation;

        if (cropView != null) {
            float bitmapWidth = rotation % 180 == 0 ? bitmapToEdit.getWidth() : bitmapToEdit.getHeight();
            float bitmapHeight = rotation % 180 == 0 ? bitmapToEdit.getHeight() : bitmapToEdit.getWidth();
            float scaleX = viewWidth / bitmapWidth;
            float scaleY = viewHeight / bitmapHeight;
            if (scaleX > scaleY) {
                bitmapHeight = viewHeight;
                bitmapWidth = (int)Math.ceil(bitmapWidth * scaleY);
            } else {
                bitmapWidth = viewWidth;
                bitmapHeight = (int)Math.ceil(bitmapHeight * scaleX);
            }

            float percX = (cropView.rectX - cropView.bitmapX) / cropView.bitmapWidth;
            float percY = (cropView.rectY - cropView.bitmapY) / cropView.bitmapHeight;
            float percSizeX = cropView.rectSizeX / cropView.bitmapWidth;
            float percSizeY = cropView.rectSizeY / cropView.bitmapHeight;
            cropView.bitmapWidth = (int) bitmapWidth;
            cropView.bitmapHeight = (int) bitmapHeight;

            cropView.bitmapX = (int) Math.ceil((viewWidth - bitmapWidth) / 2 + AndroidUtilities.dp(14));
            cropView.bitmapY = (int) Math.ceil((viewHeight - bitmapHeight) / 2 + AndroidUtilities.dp(14));

            if (cropView.rectX == -1 && cropView.rectY == -1) {
                if (freeformCrop) {
                    cropView.rectY = cropView.bitmapY;
                    cropView.rectX = cropView.bitmapX;
                    cropView.rectSizeX = bitmapWidth;
                    cropView.rectSizeY = bitmapHeight;
                } else {
                    if (bitmapWidth > bitmapHeight) {
                        cropView.rectY = cropView.bitmapY;
                        cropView.rectX = (viewWidth - bitmapHeight) / 2 + AndroidUtilities.dp(14);
                        cropView.rectSizeX = bitmapHeight;
                        cropView.rectSizeY = bitmapHeight;
                    } else {
                        cropView.rectX = cropView.bitmapX;
                        cropView.rectY = (viewHeight - bitmapWidth) / 2 + AndroidUtilities.dp(14);
                        cropView.rectSizeX = bitmapWidth;
                        cropView.rectSizeY = bitmapWidth;
                    }
                }
            } else {
                if (rotation % 180 == 0) {
                    cropView.rectX = percX * bitmapWidth + cropView.bitmapX;
                    cropView.rectY = percY * bitmapHeight + cropView.bitmapY;
                } else {
                    cropView.rectX = percY * bitmapWidth + cropView.bitmapX;
                    cropView.rectY = percX * bitmapHeight + cropView.bitmapY;
                }
                cropView.rectSizeX = percSizeX * bitmapWidth;
                cropView.rectSizeY = percSizeY * bitmapHeight;
            }
            cropView.invalidate();
        }

        float bitmapWidth = bitmapToEdit.getWidth();
        float bitmapHeight = bitmapToEdit.getHeight();
        float scaleX = viewWidth / bitmapWidth;
        float scaleY = viewHeight / bitmapHeight;
        float scale;
        if (scaleX > scaleY) {
            bitmapHeight = viewHeight;
            bitmapWidth = (int)Math.ceil(bitmapWidth * scaleY);
            scale = cropView.bitmapHeight / bitmapWidth;
        } else {
            bitmapWidth = viewWidth;
            bitmapHeight = (int)Math.ceil(bitmapHeight * scaleX);
            scale = cropView.bitmapWidth / bitmapHeight;
        }

        FrameLayout.LayoutParams layoutParams;
        if (imageView != null) {
            layoutParams = (FrameLayout.LayoutParams) imageView.getLayoutParams();
            layoutParams.leftMargin = (int) ((viewWidth - bitmapWidth) / 2 + AndroidUtilities.dp(14));
            layoutParams.topMargin = (int) ((viewHeight - bitmapHeight) / 2 + AndroidUtilities.dp(14));
            layoutParams.width = (int) bitmapWidth;
            layoutParams.height = (int) bitmapHeight;
            imageView.setLayoutParams(layoutParams);

            if (animated) {
                ViewProxy.setAlpha(cropView, 0.0f);
                rotationAnimation = new AnimatorSetProxy();
                rotationAnimation.playTogether(
                        ObjectAnimatorProxy.ofFloat(imageView, "scaleX", rotateDegree % 180 != 0 ? scale : 1),
                        ObjectAnimatorProxy.ofFloat(imageView, "scaleY", rotateDegree % 180 != 0 ? scale : 1),
                        ObjectAnimatorProxy.ofFloat(imageView, "rotation", rotateDegree));
                rotationAnimation.setDuration(150);
                rotationAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
                rotationAnimation.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        if (rotationAnimation.equals(animation)) {
                            AnimatorSetProxy animatorSet = new AnimatorSetProxy();
                            animatorSet.playTogether(ObjectAnimatorProxy.ofFloat(cropView, "alpha", 1.0f));
                            animatorSet.setDuration(150);
                            animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
                            animatorSet.start();
                            rotationAnimation = null;
                        }
                    }
                });
                rotationAnimation.start();
            } else {
                imageView.setScaleX(rotateDegree % 180 != 0 ? scale : 1);
                imageView.setScaleY(rotateDegree % 180 != 0 ? scale : 1);
                imageView.setRotation(rotateDegree);
            }
        }

        if (glView != null) {
            width = bitmapWidth;
            height = bitmapHeight;
            layoutParams = (FrameLayout.LayoutParams) glView.getLayoutParams();
            layoutParams.leftMargin = (int) ((viewWidth - bitmapWidth) / 2 + AndroidUtilities.dp(14));
            layoutParams.topMargin = (int) ((viewHeight - bitmapHeight) / 2 + AndroidUtilities.dp(14));
            layoutParams.width = (int) bitmapWidth;
            layoutParams.height = (int) bitmapHeight;
            glView.setLayoutParams(layoutParams);
            glView.requestRender();
        }
    }

    private void fixLayout() {
        if (fragmentView == null) {
            return;
        }
        fragmentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (fragmentView != null) {
                    fixLayoutInternal(rotateDegree, false);
                    fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                return false;
            }
        });
    }

    public void setDelegate(PhotoCropActivity.PhotoEditActivityDelegate delegate) {
        this.delegate = delegate;
    }

    public class ToolsAdapter extends RecyclerView.Adapter {

        private Context mContext;

        private class Holder extends RecyclerView.ViewHolder {

            public Holder(View itemView) {
                super(itemView);
            }
        }

        public ToolsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return 8;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            PhotoEditToolCell view = new PhotoEditToolCell(mContext);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            Holder holder = (Holder) viewHolder;
            if (i == 0) {
                ((PhotoEditToolCell) holder.itemView).setIconAndText(R.drawable.photo_editor_highlights, "Highlights");
            } else if (i == 1) {
                ((PhotoEditToolCell) holder.itemView).setIconAndText(R.drawable.photo_editor_contrast, "Contrast");
            } else if (i == 2) {
                ((PhotoEditToolCell) holder.itemView).setIconAndText(R.drawable.photo_editor_exposure, "Exposure");
            } else if (i == 3) {
                ((PhotoEditToolCell) holder.itemView).setIconAndText(R.drawable.photo_editor_warmth, "Warmth");
            } else if (i == 4) {
                ((PhotoEditToolCell) holder.itemView).setIconAndText(R.drawable.photo_editor_saturation, "Saturation");
            } else if (i == 5) {
                ((PhotoEditToolCell) holder.itemView).setIconAndText(R.drawable.photo_editor_vignette, "Vignette");
            } else if (i == 6) {
                ((PhotoEditToolCell) holder.itemView).setIconAndText(R.drawable.photo_editor_shadows, "Shadows");
            }  else if (i == 7) {
                ((PhotoEditToolCell) holder.itemView).setIconAndText(R.drawable.photo_editor_grain, "Grain");
            }
        }
    }
}
