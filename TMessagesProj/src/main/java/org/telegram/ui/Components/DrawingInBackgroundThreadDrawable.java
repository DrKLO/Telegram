package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;

import java.util.ArrayList;

public class DrawingInBackgroundThreadDrawable implements NotificationCenter.NotificationCenterDelegate {

    boolean attachedToWindow;

    Bitmap backgroundBitmap;
    Canvas backgroundCanvas;

    Bitmap bitmap;
    Canvas bitmapCanvas;

    Bitmap nextRenderingBitmap;
    Canvas nextRenderingCanvas;

    private boolean bitmapUpdating;

    public int currentLayerNum = 1;
    private int currentOpenedLayerFlags;
    protected boolean paused;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    int frameGuid;

    int height;
    int width;
    int padding;

    private static DispatchQueue backgroundQueue;
    boolean error;

    Runnable bitmapCreateTask = new Runnable() {
        @Override
        public void run() {
            try {
                int heightInternal = height + padding;
                if (backgroundBitmap == null || backgroundBitmap.getWidth() != width || backgroundBitmap.getHeight() != heightInternal) {
                    if (backgroundBitmap != null) {
                        backgroundBitmap.recycle();
                    }
                    backgroundBitmap = Bitmap.createBitmap(width, heightInternal, Bitmap.Config.ARGB_8888);

                    backgroundCanvas = new Canvas(backgroundBitmap);
                }

                backgroundBitmap.eraseColor(Color.TRANSPARENT);
                backgroundCanvas.save();
                backgroundCanvas.translate(0, padding);
                drawInBackground(backgroundCanvas);
                backgroundCanvas.restore();

            } catch (Exception e) {
                FileLog.e(e);
                error = true;
            }

            AndroidUtilities.runOnUIThread(uiFrameRunnable);
        }
    };

    Runnable uiFrameRunnable = new Runnable() {
        @Override
        public void run() {
            bitmapUpdating = false;
            onFrameReady();
            if (!attachedToWindow) {
                if (backgroundBitmap != null) {
                    backgroundBitmap.recycle();
                    backgroundBitmap = null;
                }
                return;
            }
            if (frameGuid != lastFrameId) {
                return;
            }
            Bitmap bitmapTmp = bitmap;
            Canvas bitmapCanvasTmp = bitmapCanvas;

            bitmap = nextRenderingBitmap;
            bitmapCanvas = nextRenderingCanvas;

            nextRenderingBitmap = backgroundBitmap;
            nextRenderingCanvas = backgroundCanvas;

            backgroundBitmap = bitmapTmp;
            backgroundCanvas = bitmapCanvasTmp;
        }
    };
    private boolean reset;
    private int lastFrameId;

    DrawingInBackgroundThreadDrawable() {
        if (backgroundQueue == null) {
            backgroundQueue = new DispatchQueue("draw_background_queue");
        }
    }

    public void draw(Canvas canvas, long time, int w, int h, float alpha) {
        if (error) {
            return;
        }
        height = h;
        width = w;

        if ((bitmap == null && nextRenderingBitmap == null) || reset) {
            reset = false;

            if (bitmap != null) {
                ArrayList<Bitmap> bitmaps = new ArrayList<>();
                bitmaps.add(bitmap);
                AndroidUtilities.recycleBitmaps(bitmaps);
                bitmap = null;
            }
            int heightInternal = height + padding;
            if (nextRenderingBitmap == null || nextRenderingBitmap.getHeight() != heightInternal || nextRenderingBitmap.getWidth() != width) {
                nextRenderingBitmap = Bitmap.createBitmap(width, heightInternal, Bitmap.Config.ARGB_8888);
                nextRenderingCanvas = new Canvas(nextRenderingBitmap);
            } else {
                nextRenderingBitmap.eraseColor(Color.TRANSPARENT);
            }
            nextRenderingCanvas.save();
            nextRenderingCanvas.translate(0, padding);
            drawInUiThread(nextRenderingCanvas);
            nextRenderingCanvas.restore();
        }

        if (!bitmapUpdating && !paused) {
            bitmapUpdating = true;
            prepareDraw(time);
            lastFrameId = frameGuid;
            backgroundQueue.postRunnable(bitmapCreateTask);
        }

        if (bitmap != null || nextRenderingBitmap != null) {
            Bitmap drawingBitmap = bitmap;
            if (drawingBitmap == null) {
                drawingBitmap = nextRenderingBitmap;
            }
            paint.setAlpha((int) (255 * alpha));
            canvas.save();
            canvas.translate(0, -padding);
            canvas.drawBitmap(drawingBitmap, 0, 0, paint);
            canvas.restore();
        }
    }

    protected void drawInUiThread(Canvas nextRenderingCanvas) {

    }

    public void drawInBackground(Canvas canvas) {

    }

    public void prepareDraw(long time) {

    }

    public void onFrameReady() {

    }

    public void onAttachToWindow() {
        attachedToWindow = true;
        currentOpenedLayerFlags = NotificationCenter.getGlobalInstance().getCurrentHeavyOperationFlags();
        currentOpenedLayerFlags &= ~currentLayerNum;
        if (currentOpenedLayerFlags == 0) {
            if (paused) {
                paused = false;
                onResume();
            }
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.stopAllHeavyOperations);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.startAllHeavyOperations);
    }

    public void onDetachFromWindow() {
        ArrayList<Bitmap> bitmaps = new ArrayList<>();
        if (bitmap != null) {
            bitmaps.add(bitmap);
        }
        if (nextRenderingBitmap != null) {
            bitmaps.add(nextRenderingBitmap);
        }
        bitmap = null;
        nextRenderingBitmap = null;
        AndroidUtilities.recycleBitmaps(bitmaps);
        attachedToWindow = false;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.stopAllHeavyOperations);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.startAllHeavyOperations);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stopAllHeavyOperations) {
            Integer layer = (Integer) args[0];
            if (currentLayerNum >= layer || (layer == 512 && (SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH))) {
                return;
            }
            currentOpenedLayerFlags |= layer;
            if (currentOpenedLayerFlags != 0) {
                if (!paused) {
                    paused = true;
                    onPaused();
                }
            }
        } else if (id == NotificationCenter.startAllHeavyOperations) {
            Integer layer = (Integer) args[0];
            if (currentLayerNum >= layer || currentOpenedLayerFlags == 0) {
                return;
            }
            currentOpenedLayerFlags &= ~layer;
            if (currentOpenedLayerFlags == 0) {
                if (paused) {
                    paused = false;
                    onResume();
                }
            }
        }
    }

    public void onResume() {

    }

    public void onPaused() {

    }

    public void reset() {
        reset = true;
        frameGuid++;

        if (bitmap != null) {
            ArrayList<Bitmap> bitmaps = new ArrayList<>();
            bitmaps.add(bitmap);
            bitmap = null;
            AndroidUtilities.recycleBitmaps(bitmaps);
        }
    }
}
