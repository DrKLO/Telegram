package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

import static android.graphics.Canvas.ALL_SAVE_FLAG;

public class BlurBehindDrawable {

    DispatchQueue queue;

    private final int type;
    public static final int TAG_DRAWING_AS_BACKGROUND = (1 << 26) + 3;

    public static final int STATIC_CONTENT = 0;
    public static final int ADJUST_PAN_TRANSLATION_CONTENT = 1;

    private View behindView;
    private View parentView;

    private Bitmap[] blurredBitmapTmp;
    private Bitmap[] backgroundBitmap;
    private Bitmap[] renderingBitmap;
    private Canvas[] renderingBitmapCanvas;
    private Canvas[] backgroundBitmapCanvas;
    private Canvas[] blurCanvas;

    private boolean processingNextFrame;
    private boolean invalidate = true;

    private float blurAlpha;
    private boolean show;
    private boolean error;
    private boolean animateAlpha = true;

    private final float DOWN_SCALE = 6f;
    private int lastH;
    private int lastW;
    private int toolbarH;

    private boolean wasDraw;
    private boolean skipDraw;

    private float panTranslationY;

    BlurBackgroundTask blurBackgroundTask = new BlurBackgroundTask();

    Paint emptyPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    Paint errorBlackoutPaint = new Paint();
    private final Theme.ResourcesProvider resourcesProvider;

    public BlurBehindDrawable(View behindView, View parentView, int type, Theme.ResourcesProvider resourcesProvider) {
        this.type = type;
        this.behindView = behindView;
        this.parentView = parentView;
        this.resourcesProvider = resourcesProvider;
        errorBlackoutPaint.setColor(Color.BLACK);
    }

    public void draw(Canvas canvas) {
        if (type == 1 && !wasDraw && !animateAlpha) {
            generateBlurredBitmaps();
            invalidate = false;
        }
        final Bitmap[] bitmap = renderingBitmap;
        if ((bitmap != null || error) && animateAlpha) {
            if (show && blurAlpha != 1f) {
                blurAlpha += 0.09f;
                if (blurAlpha > 1f) {
                    blurAlpha = 1f;
                }
                parentView.invalidate();
            } else if (!show && blurAlpha != 0) {
                blurAlpha -= 0.09f;
                if (blurAlpha < 0) {
                    blurAlpha = 0f;
                }
                parentView.invalidate();
            }
        }

        float alpha = animateAlpha ? blurAlpha : 1f;
        if (bitmap == null && error) {
            errorBlackoutPaint.setAlpha((int) (50 * alpha));
            canvas.drawPaint(errorBlackoutPaint);
            return;
        }

        if (alpha == 1f) {
            canvas.save();
        } else {
            canvas.saveLayerAlpha(0, 0, parentView.getMeasuredWidth(), parentView.getMeasuredHeight(), (int) (alpha * 255), ALL_SAVE_FLAG);
        }
        if (bitmap != null) {
            emptyPaint.setAlpha((int) (255 * alpha));
            if (type == 1) {
                canvas.translate(0, panTranslationY);
            }
            canvas.save();
            canvas.scale( parentView.getMeasuredWidth() / (float) bitmap[1].getWidth(),  parentView.getMeasuredHeight() / (float) bitmap[1].getHeight());
            canvas.drawBitmap(bitmap[1], 0, 0, emptyPaint);
            canvas.restore();
            canvas.save();
            if (type == 0) {
                canvas.translate(0, panTranslationY);
            }
            canvas.scale(parentView.getMeasuredWidth() / (float) bitmap[0].getWidth(),toolbarH / (float) bitmap[0].getHeight());
            canvas.drawBitmap(bitmap[0], 0, 0, emptyPaint);
            canvas.restore();
            wasDraw = true;
            canvas.drawColor(0x1a000000);
        }
        canvas.restore();

        if (show && !processingNextFrame && (renderingBitmap == null || invalidate)) {
            processingNextFrame = true;
            invalidate = false;
            if (blurredBitmapTmp == null) {
                blurredBitmapTmp = new Bitmap[2];
                blurCanvas = new Canvas[2];
            }
            for (int i = 0; i < 2; i++) {
                if (blurredBitmapTmp[i] == null || parentView.getMeasuredWidth() != lastW || parentView.getMeasuredHeight() != lastH) {
                    int lastH = parentView.getMeasuredHeight();
                    int lastW = parentView.getMeasuredWidth();
                    toolbarH = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(200);
                    try {
                        int h = i == 0 ? toolbarH : lastH;
                        blurredBitmapTmp[i] = Bitmap.createBitmap((int) (lastW / DOWN_SCALE), (int) (h / DOWN_SCALE), Bitmap.Config.ARGB_8888);
                        blurCanvas[i] = new Canvas(blurredBitmapTmp[i]);
                    } catch (Exception e) {
                        FileLog.e(e);
                        AndroidUtilities.runOnUIThread(() -> {
                            error = true;
                            parentView.invalidate();
                        });
                        return;
                    }
                } else {
                    blurredBitmapTmp[i].eraseColor(Color.TRANSPARENT);
                }
                if (i == 1) {
                    blurredBitmapTmp[i].eraseColor(getThemedColor(Theme.key_windowBackgroundWhite));
                }
                blurCanvas[i].save();
                blurCanvas[i].scale(1f / DOWN_SCALE, 1f / DOWN_SCALE, 0, 0);
                Drawable backDrawable = behindView.getBackground();
                if (backDrawable == null) {
                    backDrawable = getBackgroundDrawable();
                }
                behindView.setTag(TAG_DRAWING_AS_BACKGROUND, i);
                if (i == STATIC_CONTENT) {
                    blurCanvas[i].translate(0, -panTranslationY);
                    behindView.draw(blurCanvas[i]);
                }

                if (backDrawable != null && i == ADJUST_PAN_TRANSLATION_CONTENT) {
                    Rect oldBounds = backDrawable.getBounds();
                    backDrawable.setBounds(0, 0, behindView.getMeasuredWidth(), behindView.getMeasuredHeight());
                    backDrawable.draw(blurCanvas[i]);
                    backDrawable.setBounds(oldBounds);
                    behindView.draw(blurCanvas[i]);
                }

                behindView.setTag(TAG_DRAWING_AS_BACKGROUND, null);
                blurCanvas[i].restore();
            }

            lastH = parentView.getMeasuredHeight();
            lastW = parentView.getMeasuredWidth();

            blurBackgroundTask.width = parentView.getMeasuredWidth();
            blurBackgroundTask.height = parentView.getMeasuredHeight();
            if (blurBackgroundTask.width == 0 || blurBackgroundTask.height == 0) {
                processingNextFrame = false;
                return;
            }
            if (queue == null) {
                queue = new DispatchQueue("blur_thread_" + this);
            }
            queue.postRunnable(blurBackgroundTask);
        }
    }

    private int getBlurRadius() {
        return Math.max(7, Math.max(lastH, lastW) / 180);
    }

    public void clear() {
        invalidate = true;
        wasDraw = false;
        error = false;
        blurAlpha = 0;
        lastW = 0;
        lastH = 0;
        if (queue != null) {
            queue.cleanupQueue();
            queue.postRunnable(() -> {
                if (renderingBitmap != null) {
                    if (renderingBitmap[0] != null) {
                        renderingBitmap[0].recycle();
                    }
                    if (renderingBitmap[1] != null) {
                        renderingBitmap[1].recycle();
                    }
                    renderingBitmap = null;
                }
                if (backgroundBitmap != null) {
                    if (backgroundBitmap[0] != null) {
                        backgroundBitmap[0].recycle();
                    }
                    if (backgroundBitmap[1] != null) {
                        backgroundBitmap[1].recycle();
                    }
                    backgroundBitmap = null;
                }
                renderingBitmapCanvas = null;
                skipDraw = false;
                AndroidUtilities.runOnUIThread(() -> {
                    if (queue != null) {
                        queue.recycle();
                        queue = null;
                    }
                });
            });
        }
    }

    public void invalidate() {
        invalidate = true;
        if (parentView != null) {
            parentView.invalidate();
        }
    }

    public boolean isFullyDrawing() {
        return !skipDraw && wasDraw && (blurAlpha == 1f || !animateAlpha) && show && parentView.getAlpha() == 1f;
    }

    public void checkSizes() {
        final Bitmap[] bitmap = renderingBitmap;
        if (bitmap == null || parentView.getMeasuredHeight() == 0 || parentView.getMeasuredWidth() == 0) {
            return;
        }
        generateBlurredBitmaps();

        lastH = parentView.getMeasuredHeight();
        lastW = parentView.getMeasuredWidth();
    }

    private void generateBlurredBitmaps() {
        Bitmap[] bitmap = renderingBitmap;
        if (bitmap == null) {
            bitmap = renderingBitmap = new Bitmap[2];
            renderingBitmapCanvas = new Canvas[2];
        }
        if (blurredBitmapTmp == null) {
            blurredBitmapTmp = new Bitmap[2];
            blurCanvas = new Canvas[2];
        }
        blurBackgroundTask.canceled = true;
        blurBackgroundTask = new BlurBackgroundTask();

        for (int i = 0; i < 2; i++) {
            int lastH = parentView.getMeasuredHeight();
            int lastW = parentView.getMeasuredWidth();
            toolbarH = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(200);
            int h = i == 0 ? toolbarH : lastH;

            if (bitmap[i] == null || bitmap[i].getHeight() != h || bitmap[i].getWidth() != parentView.getMeasuredWidth()) {
                if (queue != null) {
                    queue.cleanupQueue();
                }

                blurredBitmapTmp[i] = Bitmap.createBitmap((int) (lastW / DOWN_SCALE), (int) (h / DOWN_SCALE), Bitmap.Config.ARGB_8888);
                if (i == 1) {
                    blurredBitmapTmp[i].eraseColor(getThemedColor(Theme.key_windowBackgroundWhite));
                }
                blurCanvas[i] = new Canvas(blurredBitmapTmp[i]);

                int bitmapH = (int) ((i == 0 ? toolbarH : lastH) / DOWN_SCALE);
                int bitmapW = (int) (lastW / DOWN_SCALE);
                renderingBitmap[i] = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
                renderingBitmapCanvas[i] = new Canvas(renderingBitmap[i]);
                renderingBitmapCanvas[i].scale((float) renderingBitmap[i].getWidth() / (float) blurredBitmapTmp[i].getWidth(), (float) renderingBitmap[i].getHeight() / (float) blurredBitmapTmp[i].getHeight());

                blurCanvas[i].save();
                blurCanvas[i].scale(1f / DOWN_SCALE, 1f / DOWN_SCALE, 0, 0);
                Drawable backDrawable = behindView.getBackground();
                if (backDrawable == null) {
                    backDrawable = getBackgroundDrawable();
                }
                behindView.setTag(TAG_DRAWING_AS_BACKGROUND, i);
                if (i == STATIC_CONTENT) {
                    blurCanvas[i].translate(0, -panTranslationY);
                    behindView.draw(blurCanvas[i]);
                }

                if (i == ADJUST_PAN_TRANSLATION_CONTENT) {
                    Rect oldBounds = backDrawable.getBounds();
                    backDrawable.setBounds(0, 0, behindView.getMeasuredWidth(), behindView.getMeasuredHeight());
                    backDrawable.draw(blurCanvas[i]);
                    backDrawable.setBounds(oldBounds);
                    behindView.draw(blurCanvas[i]);
                }

                behindView.setTag(TAG_DRAWING_AS_BACKGROUND, null);
                blurCanvas[i].restore();

                Utilities.stackBlurBitmap(blurredBitmapTmp[i], getBlurRadius());
                emptyPaint.setAlpha(255);
                if (i == 1) {
                    renderingBitmap[i].eraseColor(getThemedColor(Theme.key_windowBackgroundWhite));
                }
                renderingBitmapCanvas[i].drawBitmap(blurredBitmapTmp[i], 0, 0, emptyPaint);
            }
        }
    }

    public void show(boolean show) {
        this.show = show;
    }

    public void setAnimateAlpha(boolean animateAlpha) {
        this.animateAlpha = animateAlpha;
    }

    public void onPanTranslationUpdate(float y) {
        panTranslationY = y;
        parentView.invalidate();
    }

    public class BlurBackgroundTask implements Runnable {

        boolean canceled;
        int width;
        int height;

        @Override
        public void run() {
            if (backgroundBitmap == null) {
                backgroundBitmap = new Bitmap[2];
                backgroundBitmapCanvas = new Canvas[2];
            }
            int bitmapWidth = (int) (width / DOWN_SCALE);
            for (int i = 0; i < 2; i++) {
                int h = (int) ((i == 0 ? toolbarH : height) / DOWN_SCALE);

                if (backgroundBitmap[i] != null && (backgroundBitmap[i].getHeight() != h || backgroundBitmap[i].getWidth() != bitmapWidth)) {
                    if (backgroundBitmap[i] != null) {
                        backgroundBitmap[i].recycle();
                        backgroundBitmap[i] = null;
                    }
                }
                long t = System.currentTimeMillis();
                if (backgroundBitmap[i] == null) {
                    int w = bitmapWidth;
                    try {
                        backgroundBitmap[i] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        backgroundBitmapCanvas[i] = new Canvas(backgroundBitmap[i]);
                        backgroundBitmapCanvas[i].scale(bitmapWidth / (float) blurredBitmapTmp[i].getWidth(), h / (float) blurredBitmapTmp[i].getHeight());
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                if (i == 1) {
                    backgroundBitmap[i].eraseColor(getThemedColor(Theme.key_windowBackgroundWhite));
                } else {
                    backgroundBitmap[i].eraseColor(Color.TRANSPARENT);
                }
                emptyPaint.setAlpha(255);
                Utilities.stackBlurBitmap(blurredBitmapTmp[i], getBlurRadius());

                if (backgroundBitmapCanvas[i] != null) {
                    backgroundBitmapCanvas[i].drawBitmap(blurredBitmapTmp[i], 0, 0, emptyPaint);
                }

                if (canceled) {
                    return;
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (canceled) {
                    return;
                }
                Bitmap[] bitmap = renderingBitmap;
                Canvas[] canvas = renderingBitmapCanvas;

                renderingBitmap = backgroundBitmap;
                renderingBitmapCanvas = backgroundBitmapCanvas;

                backgroundBitmap = bitmap;
                backgroundBitmapCanvas = canvas;

                processingNextFrame = false;
                if (parentView != null) {
                    parentView.invalidate();
                }
            });
        }
    }

    private Drawable getBackgroundDrawable() {
        return (resourcesProvider instanceof ChatActivity.ThemeDelegate)
                ? ((ChatActivity.ThemeDelegate) resourcesProvider).getWallpaperDrawable()
                : Theme.getCachedWallpaperNonBlocking();
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
