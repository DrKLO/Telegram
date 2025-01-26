package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.camera.CameraView;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class QRScanner {

    private final AtomicReference<BarcodeDetector> detector = new AtomicReference<>();
    private final AtomicBoolean paused = new AtomicBoolean(false);

    private final Utilities.Callback<Detected> listener;
    private Detected lastDetected;
    private final String prefix;

    public QRScanner(Context context, Utilities.Callback<Detected> whenScanned) {
        this.listener = whenScanned;
        this.prefix = MessagesController.getInstance(UserConfig.selectedAccount).linkPrefix;
        Utilities.globalQueue.postRunnable(() -> {
            detector.set(new BarcodeDetector.Builder(context).setBarcodeFormats(Barcode.QR_CODE).build());
            attach(cameraView);
        });
    }

    public Detected getDetected() {
        return lastDetected;
    }

    private CameraView cameraView;

    public void destroy() {
        this.cameraView = null;
        Utilities.globalQueue.cancelRunnable(this.process);
    }

    public void attach(CameraView cameraView) {
        this.cameraView = cameraView;
        if (detector.get() == null) return;

        if (!paused.get()) {
            Utilities.globalQueue.cancelRunnable(this.process);
            Utilities.globalQueue.postRunnable(this.process, getTimeout());
        }
    }

    public void setPaused(boolean pause) {
        if (this.paused.getAndSet(pause) == pause) return;

        if (pause) {
            Utilities.globalQueue.cancelRunnable(this.process);
            if (lastDetected != null) {
                lastDetected = null;
                AndroidUtilities.runOnUIThread(() -> QRScanner.this.listener.run(null));
            }
        } else {
            Utilities.globalQueue.cancelRunnable(this.process);
            Utilities.globalQueue.postRunnable(this.process, getTimeout());
        }
    }

    public boolean isPaused() {
        return this.paused.get();
    }

    private Bitmap cacheBitmap;
    private final Runnable process = () -> {
        if (detector.get() == null || cameraView == null || paused.get()) {
            return;
        }

        TextureView textureView = cameraView.getTextureView();
        if (textureView != null) {
            final int maxSide = 720;
            int w = textureView.getWidth();
            int h = textureView.getHeight();
            if (w > maxSide || h > maxSide) {
                final float scale = Math.min((float) maxSide / w, (float) maxSide / h);
                w = (int) (w * scale);
                h = (int) (h * scale);
            }
            w = Math.max(1, w);
            h = Math.max(1, h);
            if (cacheBitmap == null || w != cacheBitmap.getWidth() || h != cacheBitmap.getHeight()) {
                cacheBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            textureView.getBitmap(cacheBitmap);
            final Detected detected = detect(cacheBitmap);
            if ((lastDetected != null) != (detected != null) || detected != null && lastDetected != null && !detected.equals(lastDetected)) {
                lastDetected = detected;
                AndroidUtilities.runOnUIThread(() -> QRScanner.this.listener.run(detected));
            }
        }

        if (!paused.get()) {
            Utilities.globalQueue.cancelRunnable(this.process);
            Utilities.globalQueue.postRunnable(this.process, getTimeout());
        }
    };

    private Detected detect(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        final BarcodeDetector detector = this.detector.get();
        if (detector == null || !detector.isOperational()) {
            return null;
        }

        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final Frame frame = new Frame.Builder().setBitmap(bitmap).build();
        final SparseArray<Barcode> codes = detector.detect(frame);

        for (int i = 0; i < codes.size(); ++i) {
            final Barcode code = codes.valueAt(i);
            String link = code.rawValue;
            if (link == null) continue;
            link = link.trim();
            if (!link.startsWith(prefix) && !link.startsWith("https://" + prefix) && !link.startsWith("http://" + prefix)) continue;

            final PointF[] cornerPoints = new PointF[code.cornerPoints.length];
            for (int j = 0; j < code.cornerPoints.length; ++j) {
                cornerPoints[j] = new PointF((float) code.cornerPoints[j].x / w, (float) code.cornerPoints[j].y / h);
            }

            return new Detected(link, cornerPoints);
        }

        return null;
    }

    public int getMaxSide() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH: return 720;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE: return 540;
            default: return 540;
        }
    }

    public long getTimeout() {
        if (lastDetected == null) {
            return 750;
        }
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH: return 80;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE: return 400;
            default: return 800;
        }
    }

    public void detach() {
        BarcodeDetector detector = this.detector.getAndSet(null);
        if (detector != null) {
            detector.release();
        }
    }

    public static final class Detected {

        public final String link;
        public final PointF[] points;

        public final float cx, cy;

        private Detected(String link, PointF[] points) {
            this.link = link;
            this.points = points;

            float cx = 0, cy = 0;
            if (points != null) {
                for (int i = 0; i < points.length; ++i) {
                    cx += points[i].x;
                    cy += points[i].y;
                }
                cx /= points.length;
                cy /= points.length;
            }
            this.cx = cx;
            this.cy = cy;
        }

        public boolean equals(Detected d) {
            if (d == null) return false;
            if (!TextUtils.equals(link, d.link)) return false;
            if (points == d.points) return true;
            if ((points != null) != (d.points != null)) return false;
            if (points == null || d.points == null) return false;
            if (points.length != d.points.length) return false;
            for (int i = 0; i < points.length; ++i) {
                if (Math.abs(points[i].x - d.points[i].x) > 0.001f || Math.abs(points[i].y - d.points[i].y) > 0.001f) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class QrRegionView extends View {

        public final QrRegionDrawer drawer = new QrRegionDrawer(this::invalidate);
        private final RectF rect = new RectF();

        public QrRegionView(Context context) {
            super(context);
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            if (drawer.hasNoDraw()) {
                return;
            }
            rect.set(0, 0, getWidth(), getHeight());
            drawer.draw(canvas, rect);
        }
    }

    public static class QrRegionDrawer {

        private final Runnable invalidate;

        private boolean hasQrResult;
        private QRScanner.Detected qrResult;
        private final AnimatedFloat animatedQr, animatedQrCX, animatedQrCY;
        private final AnimatedFloat[] animatedQPX;
        private final AnimatedFloat[] animatedQPY;

        public QrRegionDrawer(Runnable invalidate) {
            this.invalidate = invalidate;

            animatedQr = new AnimatedFloat(0, this.invalidate, 0, 320, CubicBezierInterpolator.EASE_OUT);
            animatedQrCX = new AnimatedFloat(0, this.invalidate, 0, 160, CubicBezierInterpolator.EASE_OUT);
            animatedQrCY = new AnimatedFloat(0, this.invalidate, 0, 160, CubicBezierInterpolator.EASE_OUT);
            animatedQPX = new AnimatedFloat[] {
                    new AnimatedFloat(0, this.invalidate, 0, 160, CubicBezierInterpolator.EASE_OUT),
                    new AnimatedFloat(0, this.invalidate, 0, 160, CubicBezierInterpolator.EASE_OUT),
                    new AnimatedFloat(0, this.invalidate, 0, 160, CubicBezierInterpolator.EASE_OUT),
                    new AnimatedFloat(0, this.invalidate, 0, 160, CubicBezierInterpolator.EASE_OUT)
            };
            animatedQPY = new AnimatedFloat[] {
                    new AnimatedFloat(0, this.invalidate, 0, 160, CubicBezierInterpolator.EASE_OUT),
                    new AnimatedFloat(0, this.invalidate, 0, 160, CubicBezierInterpolator.EASE_OUT),
                    new AnimatedFloat(0, this.invalidate, 0, 160, CubicBezierInterpolator.EASE_OUT),
                    new AnimatedFloat(0, this.invalidate, 0, 160, CubicBezierInterpolator.EASE_OUT)
            };
        }

        public void draw(Canvas canvas, RectF rect) {
            if (qrResult == null || qrResult.points.length <= 0) {
                return;
            }

            final float qrAlpha = animatedQr.set(hasQrResult);
            final float cx = animatedQrCX.set(qrResult.cx), cxPx = rect.left + cx * rect.width();
            final float cy = animatedQrCY.set(qrResult.cy), cyPx = rect.top + cy * rect.height();
            final float qrScale = lerp(0.5f, 1.1f, qrAlpha);

            canvas.save();
            canvas.scale(qrScale, qrScale, cxPx, cyPx);
            if (qrAlpha > 0) {
                qrPath.rewind();
                final int len = Math.min(4, qrResult.points.length);
                for (int i = 0; i < len; ++i) {
                    final int li = i - 1 < 0 ? len - 1 : i - 1;
                    final int ri = i + 1 >= len ? 0 : i + 1;

                    final PointF l = qrResult.points[li];
                    final PointF p = qrResult.points[i];
                    final PointF r = qrResult.points[ri];

                    float lx = rect.left + (animatedQPX[li].set(l.x - qrResult.cx) + cx) * rect.width(), ly = rect.top + (animatedQPY[li].set(l.y - qrResult.cy) + cy) * rect.height();
                    float px = rect.left + (animatedQPX[i].set(p.x - qrResult.cx)  + cx) * rect.width(), py = rect.top + (animatedQPY[i].set(p.y - qrResult.cy)  + cy) * rect.height();
                    float rx = rect.left + (animatedQPX[ri].set(r.x - qrResult.cx) + cx) * rect.width(), ry = rect.top + (animatedQPY[ri].set(r.y - qrResult.cy) + cy) * rect.height();

                    final float lvx = lx - px, lvy = ly - py;
                    final float rvx = rx - px, rvy = ry - py;

                    qrPath.moveTo(
                            px + lvx * .18f,
                            py + lvy * .18f
                    );
                    qrPath.lineTo(px, py);
                    qrPath.lineTo(
                            px + rvx * .18f,
                            py + rvy * .18f
                    );
                }
                qrPaint.setAlpha((int) (0xFF * qrAlpha));
                canvas.drawPath(qrPath, qrPaint);
            }
            canvas.restore();
        }

        private final Paint qrPaint = new Paint(Paint.ANTI_ALIAS_FLAG); {
            qrPaint.setStyle(Paint.Style.STROKE);
            qrPaint.setColor(0xFFFFDE07);
            qrPaint.setStrokeWidth(dp(6));
            qrPaint.setStrokeJoin(Paint.Join.ROUND);
            qrPaint.setStrokeCap(Paint.Cap.ROUND);
            qrPaint.setShadowLayer(0x40666666, 0, dp(3), dp(6));
        }
        private final Path qrPath = new Path();

        public void setQrDetected(QRScanner.Detected qrResult) {
            if (qrResult != null) {
                this.qrResult = qrResult;
            }
            if (qrResult != null && !hasQrResult) {
                animatedQrCX.set(qrResult.cx, true);
                animatedQrCY.set(qrResult.cy, true);
                for (int i = 0; i < Math.min(4, qrResult.points.length); ++i) {
                    animatedQPX[i].set(qrResult.points[i].x - qrResult.cx, true);
                    animatedQPY[i].set(qrResult.points[i].y - qrResult.cy, true);
                }
            }
            hasQrResult = qrResult != null;
            this.invalidate.run();
        }

        public boolean hasNoDraw() {
            return !hasQrResult && animatedQr.get() <= 0;
        }
    }
}
