package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.google.common.primitives.Floats;
import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.messenger.camera.CameraView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.Arrays;
import java.util.Locale;

public class DualCameraView extends CameraView {

    private boolean dualAvailable;

    public DualCameraView(Context context, boolean frontface, boolean lazy) {
        super(context, frontface, lazy);
        dualAvailable = dualAvailableStatic(context);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean r = touchEvent(event);
        return super.onTouchEvent(event) || r;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && isAtDual(ev.getX(), ev.getY())) {
            return touchEvent(ev);
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public void destroy(boolean async, Runnable beforeDestroyRunnable) {
        saveDual();
        super.destroy(async, beforeDestroyRunnable);
    }

    private final PointF lastTouch = new PointF();
    private final PointF touch = new PointF();
    private float lastTouchDistance;
    private double lastTouchRotation;
    private boolean multitouch;
    private boolean allowRotation;
    private final Matrix touchMatrix = new Matrix(), finalMatrix = new Matrix();
    private boolean down;
    private float rotationDiff;
    private boolean snappedRotation;
    private boolean doNotSpanRotation;
    private float[] tempPoint = new float[4];

    private final Matrix toScreen = new Matrix();
    private final Matrix toGL = new Matrix();

    private boolean firstMeasure = true;
    private boolean atTop, atBottom;

    private boolean enabledSavedDual;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setupToScreenMatrix();
    }

//    @Override
//    protected void updatedDualRotation() {
//        setupToScreenMatrix();
//    }

    private void setupToScreenMatrix() {
        toScreen.reset();
//        if (applyCameraRotation()) {
//            toScreen.postRotate(getDualRotation());
//        }
        toScreen.postTranslate(1f, -1f);
        toScreen.postScale(getMeasuredWidth() / 2f, -getMeasuredHeight() / 2f);
        toScreen.invert(toGL);
    }

    protected boolean applyCameraRotation() {
        return false;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (firstMeasure) {
            if (isSavedDual()) {
                enabledSavedDual = true;
                setupDualMatrix();
                super.dual = true;
            }
            firstMeasure = false;
        }
        super.onSurfaceTextureAvailable(surface, width, height);
    }

    @Override
    protected void onDualCameraSuccess() {
        saveDual();
        if (enabledSavedDual) {
            onSavedDualCameraSuccess();
        }
        log(true);
    }

    private void log(boolean success) {
        final boolean vendor = DualCameraView.dualAvailableDefault(ApplicationLoader.applicationContext, false);
        if (MessagesController.getInstance(UserConfig.selectedAccount).collectDeviceStats) {
            try {
                TLRPC.TL_help_saveAppLog req = new TLRPC.TL_help_saveAppLog();
                TLRPC.TL_inputAppEvent event = new TLRPC.TL_inputAppEvent();
                event.time = ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime();
                event.type = "android_dual_camera";
                TLRPC.TL_jsonObject obj = new TLRPC.TL_jsonObject();
                TLRPC.TL_jsonObjectValue kv = new TLRPC.TL_jsonObjectValue();
                kv.key = "device";
                TLRPC.TL_jsonString str = new TLRPC.TL_jsonString();
                str.value = "" + Build.MANUFACTURER + Build.MODEL;
                kv.value = str;
                obj.value.add(kv);
                event.data = obj;
                event.peer = (success ? 1 : 0) | (vendor ? 2 : 0);
                req.events.add(event);
                ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {
                });
            } catch (Exception ignore) {
            }
        }
        ApplicationLoader.logDualCamera(success, vendor);
    }

    protected void onSavedDualCameraSuccess() {

    }

    public void resetSaved() {
        resetSavedDual();
    }

    @Override
    public void toggleDual() {
        if (!isDual() && !dualAvailable()) {
            return;
        }
        if (!isDual()) {
            setupDualMatrix();
        } else {
            resetSaved();
        }
        super.toggleDual();
    }

    private void setupDualMatrix() {
        Matrix matrix = getDualPosition();
        matrix.reset();
        boolean setDefault = true;
        Matrix savedMatrix = getSavedDualMatrix();
        if (savedMatrix != null) {
            matrix.set(savedMatrix);
            setDefault = false;
        }

        if (setDefault) {
            matrix.postConcat(toScreen);

            float w = getMeasuredWidth() * .43f;
            float h = getMeasuredHeight() * .43f;
            float px = Math.min(getMeasuredWidth(), getMeasuredWidth()) * .025f;
            float py = px * 2;

            matrix.postScale(w / getMeasuredWidth(), h / getMeasuredHeight());
            matrix.postTranslate(getMeasuredWidth() - px - w, px);
            matrix.postConcat(toGL);
        }
        updateDualPosition();
    }

    public boolean isAtDual(float x, float y) {
        if (!isDual()) {
            return false;
        }
        vertex[0] = x;
        vertex[1] = y;
        toGL.mapPoints(vertex);
        getDualPosition().invert(invMatrix);
        invMatrix.mapPoints(vertex);
        int shape = getDualShape() % 3;
        boolean square = shape == 0 || shape == 1 || shape == 3;
        float H = square ? 9 / 16f : 1f;
        return vertex[0] >= -1 && vertex[0] <= 1 && vertex[1] >= -H && vertex[1] <= H;
    }

    private float tapX, tapY;
    private long tapTime;
    private Matrix invMatrix = new Matrix();
    private Runnable longpressRunnable;
    private boolean checkTap(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            tapTime = System.currentTimeMillis();
            tapX = ev.getX();
            tapY = ev.getY();
            lastFocusToPoint = null;
            if (longpressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(longpressRunnable);
                longpressRunnable = null;
            }
            if (isAtDual(tapX, tapY)) {
                AndroidUtilities.runOnUIThread(longpressRunnable = () -> {
                    if (tapTime > 0) {
                        this.dualToggleShape();
                        try {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                        } catch (Exception ignored) {}
                    }
                }, ViewConfiguration.getLongPressTimeout());
                return true;
            }
        } else if (ev.getAction() == MotionEvent.ACTION_UP) {
            if (System.currentTimeMillis() - tapTime <= ViewConfiguration.getTapTimeout() && MathUtils.distance(tapX, tapY, ev.getX(), ev.getY()) < AndroidUtilities.dp(10)) {
                if (isAtDual(tapX, tapY)) {
                    switchCamera();
                    lastFocusToPoint = null;
                } else {
                    lastFocusToPoint = () -> focusToPoint((int) tapX, (int) tapY);
                }
            }
            tapTime = -1;
            if (longpressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(longpressRunnable);
                longpressRunnable = null;
            }
        } else if (ev.getAction() == MotionEvent.ACTION_CANCEL) {
            tapTime = -1;
            lastFocusToPoint = null;
            if (longpressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(longpressRunnable);
                longpressRunnable = null;
            }
        }
        return false;
    }

    private Runnable lastFocusToPoint;
    public void allowToTapFocus() {
        if (lastFocusToPoint != null) {
            lastFocusToPoint.run();
            lastFocusToPoint = null;
        }
    }

    public void clearTapFocus() {
        lastFocusToPoint = null;
        tapTime = -1;
    }

    private boolean touchEvent(MotionEvent ev) {
        boolean r = false;
        r = checkTap(ev) || r;
        if (isDual()) {
            Matrix matrix = getDualPosition();

            final boolean currentMultitouch = ev.getPointerCount() > 1;
            float distance = 0;
            double rotation = 0;
            if (currentMultitouch) {
                touch.x = (ev.getX(0) + ev.getX(1)) / 2f;
                touch.y = (ev.getY(0) + ev.getY(1)) / 2f;
                distance = MathUtils.distance(ev.getX(0), ev.getY(0), ev.getX(1), ev.getY(1));
                rotation = Math.atan2(ev.getY(1) - ev.getY(0), ev.getX(1) - ev.getX(0));
            } else {
                touch.x = ev.getX(0);
                touch.y = ev.getY(0);
            }
            if (multitouch != currentMultitouch) {
                lastTouch.x = touch.x;
                lastTouch.y = touch.y;
                lastTouchDistance = distance;
                lastTouchRotation = rotation;
                multitouch = currentMultitouch;
            }

            float tx = touch.x, ty = touch.y;
            float ltx = lastTouch.x, lty = lastTouch.y;
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                touchMatrix.set(matrix);
                touchMatrix.postConcat(toScreen);
                rotationDiff = 0;
                snappedRotation = false;
                doNotSpanRotation = false;
                down = isPointInsideDual(touchMatrix, touch.x, touch.y);
            }
            if (ev.getAction() == MotionEvent.ACTION_MOVE && down) {
                if (MathUtils.distance(tx, ty, ltx, lty) > AndroidUtilities.dp(2)) {
                    if (longpressRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(longpressRunnable);
                        longpressRunnable = null;
                    }
                }
                if (ev.getPointerCount() > 1) {
                    if (lastTouchDistance != 0) {
                        extractPointsData(touchMatrix);
                        float scaleFactor = distance / lastTouchDistance;
                        if (w * scaleFactor > getWidth() * .7f) {
                            scaleFactor = getWidth() * .7f / w;
                        } else if (w * scaleFactor < getWidth() * .2f) {
                            scaleFactor = getWidth() * .2f / w;
                        }
                        touchMatrix.postScale(scaleFactor, scaleFactor, tx, ty);
                    }
                    float rotate = (float) Math.toDegrees(rotation - lastTouchRotation);
                    rotationDiff += rotate;
                    if (!allowRotation) {
                        allowRotation = Math.abs(rotationDiff) > 20f;
                        if (!allowRotation) {
                            extractPointsData(touchMatrix);
                            allowRotation = Math.round(angle / 90f) * 90f - angle > 20f;
                        }
                        if (!snappedRotation) {
                            AndroidUtilities.vibrateCursor(this);
                            snappedRotation = true;
                        }
                    }
                    if (allowRotation) {
                        touchMatrix.postRotate(rotate, tx, ty);
                    }
                }
                touchMatrix.postTranslate(tx - ltx, ty - lty);
                finalMatrix.set(touchMatrix);
                extractPointsData(finalMatrix);
                float rotDiff = Math.round(angle / 90f) * 90f - angle;
                if (allowRotation && !doNotSpanRotation) {
                    if (Math.abs(rotDiff) < 5f) {
                        finalMatrix.postRotate(rotDiff, cx, cy);
                        if (!snappedRotation) {
                            AndroidUtilities.vibrateCursor(this);
                            snappedRotation = true;
                        }
                    } else {
                        snappedRotation = false;
                    }
                }
                if (cx < 0) {
                    finalMatrix.postTranslate(-cx, 0);
                } else if (cx > getWidth()) {
                    finalMatrix.postTranslate(getWidth() - cx, 0);
                }
                if (cy < 0) {
                    finalMatrix.postTranslate(0, -cy);
                } else if (cy > getHeight() - AndroidUtilities.dp(150)) {
                    finalMatrix.postTranslate(0, getHeight() - AndroidUtilities.dp(150) - cy);
                }
                finalMatrix.postConcat(toGL);
                matrix.set(finalMatrix);
                updateDualPosition();

                boolean atTop = Math.min(cy, cy - h / 2f) < AndroidUtilities.dp(66);
                boolean atBottom = Math.max(cy, cy + h / 2f) > getHeight() - AndroidUtilities.dp(66);
                if (this.atTop != atTop) {
                    onEntityDraggedTop(this.atTop = atTop);
                }
                if (this.atBottom != atBottom) {
                    onEntityDraggedBottom(this.atBottom = atBottom);
                }
            }
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                allowRotation = false;
                rotationDiff = 0;
                snappedRotation = false;
                invalidate();
                down = false;

                if (this.atTop) {
                    onEntityDraggedTop(this.atTop = false);
                }
                if (this.atBottom) {
                    onEntityDraggedBottom(this.atBottom = false);
                }
            } else if (ev.getAction() == MotionEvent.ACTION_CANCEL) {
                down = false;

                if (this.atTop) {
                    onEntityDraggedTop(this.atTop = false);
                }
                if (this.atBottom) {
                    onEntityDraggedBottom(this.atBottom = false);
                }
            }
            lastTouch.x = touch.x;
            lastTouch.y = touch.y;
            lastTouchDistance = distance;
            lastTouchRotation = rotation;
            r = down || r;
        }
        return r;
    }

    protected void onEntityDraggedTop(boolean value) {}
    protected void onEntityDraggedBottom(boolean value) {}

    public boolean isDualTouch() {
        return down;
    }

    private final float[] vertices = new float[2];
    private float cx, cy, angle, w, h;

    private void extractPointsData(Matrix matrix) {
        vertices[0] = 0;
        vertices[1] = 0;
        matrix.mapPoints(vertices);
        cx = vertices[0];
        cy = vertices[1];

        vertices[0] = 1;
        vertices[1] = 0;
        matrix.mapPoints(vertices);
        angle = (float) Math.toDegrees(Math.atan2(vertices[1] - cy, vertices[0] - cx));
        w = 2 * MathUtils.distance(cx, cy, vertices[0], vertices[1]);

        vertices[0] = 0;
        vertices[1] = 1;
        matrix.mapPoints(vertices);
        h = 2 * MathUtils.distance(cx, cy, vertices[0], vertices[1]);
    }

    private Matrix tempMatrix = new Matrix();
    private float[] vertex = new float[2];
    private float[] verticesSrc, verticesDst;
    public boolean isPointInsideDual(Matrix matrix, float x, float y) {
//        vertex[0] = x;
//        vertex[1] = y;
//        toGL.mapPoints(vertex);
//        matrix.invert(tempMatrix);
//        tempMatrix.mapPoints(vertex);
//        return vertex[0] >= -1f && vertex[0] <= 1f && vertex[1] >= -1f && vertex[1] <= 1f;

        if (verticesSrc == null) {
            verticesSrc = new float[8];
        }
        if (verticesDst == null) {
            verticesDst = new float[8];
        }
        int shape = getDualShape() % 3;
        boolean square = shape == 0 || shape == 1 || shape == 3;
        float H = square ? 9 / 16f : 1f;
        verticesSrc[0] = -1;
        verticesSrc[1] = -H;
        verticesSrc[2] = 1;
        verticesSrc[3] = -H;
        verticesSrc[4] = 1;
        verticesSrc[5] = H;
        verticesSrc[6] = -1;
        verticesSrc[7] = H;
        matrix.mapPoints(verticesDst, verticesSrc);

        double a1 = Math.sqrt((verticesDst[0] - verticesDst[2]) * (verticesDst[0] - verticesDst[2]) + (verticesDst[1] - verticesDst[3]) * (verticesDst[1] - verticesDst[3]));
        double a2 = Math.sqrt((verticesDst[2] - verticesDst[4]) * (verticesDst[2] - verticesDst[4]) + (verticesDst[3] - verticesDst[5]) * (verticesDst[3] - verticesDst[5]));
        double a3 = Math.sqrt((verticesDst[4] - verticesDst[6]) * (verticesDst[4] - verticesDst[6]) + (verticesDst[5] - verticesDst[7]) * (verticesDst[5] - verticesDst[7]));
        double a4 = Math.sqrt((verticesDst[6] - verticesDst[0]) * (verticesDst[6] - verticesDst[0]) + (verticesDst[7] - verticesDst[1]) * (verticesDst[7] - verticesDst[1]));

        double b1 = Math.sqrt((verticesDst[0] - x) * (verticesDst[0] - x) + (verticesDst[1] - y) * (verticesDst[1] - y));
        double b2 = Math.sqrt((verticesDst[2] - x) * (verticesDst[2] - x) + (verticesDst[3] - y) * (verticesDst[3] - y));
        double b3 = Math.sqrt((verticesDst[4] - x) * (verticesDst[4] - x) + (verticesDst[5] - y) * (verticesDst[5] - y));
        double b4 = Math.sqrt((verticesDst[6] - x) * (verticesDst[6] - x) + (verticesDst[7] - y) * (verticesDst[7] - y));

        double u1 = (a1 + b1 + b2) / 2;
        double u2 = (a2 + b2 + b3) / 2;
        double u3 = (a3 + b3 + b4) / 2;
        double u4 = (a4 + b4 + b1) / 2;

        return (Math.sqrt(u1 * (u1 - a1) * (u1 - b1) * (u1 - b2)) + Math.sqrt(u2 * (u2 - a2) * (u2 - b2) * (u2 - b3)) + Math.sqrt(u3 * (u3 - a3) * (u3 - b3) * (u3 - b4)) + Math.sqrt(u4 * (u4 - a4) * (u4 - b4) * (u4 - b1)) - a1 * a2) < 1;
    }

    @Override
    public void onError(int error, Camera camera, CameraSessionWrapper session) {
        if (isDual()) {
            if (!dualAvailableDefault(getContext(), false)) {
                MessagesController.getGlobalMainSettings().edit().putBoolean("dual_available", dualAvailable = false).apply();
                new AlertDialog.Builder(getContext())
                    .setTitle(LocaleController.getString(R.string.DualErrorTitle))
                    .setMessage(LocaleController.getString(R.string.DualErrorMessage))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null)
                    .show();
            }
            log(false);
            toggleDual();
        }
        if (getCameraSession(0) != null && getCameraSession(0).equals(session)) {
            resetCamera();
        }
        onCameraError();
    }

    protected void onCameraError() {
        resetSaved();
    }

    public boolean dualAvailable() {
        return dualAvailable;
    }

    private static final int[] dualWhitelistByDevice = new int[] {
        1893745684,  // XIAOMI CUPID
        -215458996,  // XIAOMI VAYU
        -862041025,  // XIAOMI WILLOW
        -1258375037, // XIAOMI INGRES
        -1320049076, // XIAOMI GINKGO
        -215749424,  // XIAOMI LISA
        1901578030,  // XIAOMI LEMON
        -215451421,  // XIAOMI VIVA
        1908491424,  // XIAOMI STONE
        -1321491332, // XIAOMI RAPHAEL
        -1155551678, // XIAOMI MARBLE
        1908524435,  // XIAOMI SURYA
        976847578,   // XIAOMI LAUREL_SPROUT
        -1489198134, // XIAOMI ALIOTH
        1910814392,  // XIAOMI VENUS
        -713271737,  // OPPO OP4F2F
        -2010722764, // SAMSUNG A52SXQ (A52s 5G)
        1407170066,  // SAMSUNG D2Q (Note10+)
        -821405251,  // SAMSUNG BEYOND2
        -1394190955, // SAMSUNG A71
        -1394190055, // SAMSUNG B4Q
        1407170066,  // HUAWEI HWNAM
        1407159934,  // HUAWEI HWCOR
        1407172057,  // HUAWEI HWPCT
        1231389747,  // FAIRPHONE FP3
        -2076538925, // MOTOROLA RSTAR
        41497626,    // MOTOROLA RHODEC
        846150482,   // MOTOROLA CHANNEL
        -1198092731, // MOTOROLA CYPRUS64
        -251277614,  // MOTOROLA HANOIP
//        -2078385967, // MOTOROLA PSTAR
        -2073158771, // MOTOROLA VICKY
        1273004781   // MOTOROLA BLACKJACK
//        -1426053134  // REALME REE2ADL1
    };

    private static final int[] dualWhitelistByModel = new int[] {

    };

    public static boolean dualAvailableDefault(Context context, boolean withWhitelist) {
        boolean def = (
            SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE &&
            Camera.getNumberOfCameras() > 1 &&
            SharedConfig.allowPreparingHevcPlayers()
        );
        if (def) {
            def = context != null && context.getPackageManager().hasSystemFeature("android.hardware.camera.concurrent");
            if (!def && withWhitelist) {
                int hash = (Build.MANUFACTURER + " " + Build.DEVICE).toUpperCase().hashCode();
                for (int i = 0; i < dualWhitelistByDevice.length; ++i) {
                    if (dualWhitelistByDevice[i] == hash) {
                        def = true;
                        break;
                    }
                }
                if (!def) {
                    hash = (Build.MANUFACTURER + Build.MODEL).toUpperCase().hashCode();
                    for (int i = 0; i < dualWhitelistByModel.length; ++i) {
                        if (dualWhitelistByModel[i] == hash) {
                            def = true;
                            break;
                        }
                    }
                }
            }
        }
        return def;
    }

    public static boolean dualAvailableStatic(Context context) {
        return MessagesController.getGlobalMainSettings().getBoolean("dual_available", dualAvailableDefault(context, true));
    }

    public static boolean roundDualAvailableStatic(Context context) {
        return MessagesController.getGlobalMainSettings().getBoolean("rounddual_available", roundDualAvailableDefault(context));
    }

    public static boolean roundDualAvailableDefault(Context context) {
        return (
            SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH &&
            Camera.getNumberOfCameras() > 1 &&
            SharedConfig.allowPreparingHevcPlayers() &&
            context != null && context.getPackageManager().hasSystemFeature("android.hardware.camera.concurrent")
        );
    }


    private Matrix getSavedDualMatrix() {
        String str = MessagesController.getGlobalMainSettings().getString("dualmatrix", null);
        if (str == null) {
            return null;
        }
        String[] parts = str.split(";");
        if (parts.length != 9) {
            return null;
        }
        float[] values = new float[9];
        for (int i = 0; i < parts.length; ++i) {
            try {
                values[i] = Float.parseFloat(parts[i]);
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
        }
        Matrix matrix = new Matrix();
        matrix.setValues(values);
        return matrix;
    }

    public boolean isSavedDual() {
        return dualAvailableStatic(getContext()) && MessagesController.getGlobalMainSettings().getBoolean("dualcam", dualAvailableDefault(ApplicationLoader.applicationContext, false));
    }

    private void resetSavedDual() {
        MessagesController.getGlobalMainSettings().edit().putBoolean("dualcam", false).remove("dualmatrix").apply();
    }

    private void saveDual() {
        SharedPreferences.Editor edit = MessagesController.getGlobalMainSettings().edit();
        edit.putBoolean("dualcam", isDual());
        if (isDual()) {
            float[] values = new float[9];
            getDualPosition().getValues(values);
            edit.putString("dualmatrix", Floats.join(";", values));
        } else {
            edit.remove("dualmatrix");
        }
        edit.apply();
    }
}
