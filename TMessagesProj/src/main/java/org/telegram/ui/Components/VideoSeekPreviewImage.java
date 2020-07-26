package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;

public class VideoSeekPreviewImage extends View {

    private AnimatedFileDrawable fileDrawable;
    private long duration;
    private Uri videoUri;
    private Runnable loadRunnable;
    private Runnable progressRunnable;
    private float pendingProgress;
    private int currentPixel = -1;
    private int pixelWidth;
    private boolean ready;
    private Bitmap bitmapToRecycle;
    private Bitmap bitmapToDraw;
    private Drawable frameDrawable;

    private String frameTime;
    private int timeWidth;
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private BitmapShader bitmapShader;
    private RectF dstR = new RectF();
    private Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private RectF bitmapRect = new RectF();
    private Matrix matrix = new Matrix();

    private VideoSeekPreviewImageDelegate delegate;

    public interface VideoSeekPreviewImageDelegate {
        void onReady();
    }

    public VideoSeekPreviewImage(Context context, VideoSeekPreviewImageDelegate videoSeekPreviewImageDelegate) {
        super(context);
        setVisibility(INVISIBLE);

        frameDrawable = context.getResources().getDrawable(R.drawable.videopreview);
        textPaint.setTextSize(AndroidUtilities.dp(13));
        textPaint.setColor(0xffffffff);

        delegate = videoSeekPreviewImageDelegate;
    }

    public void setProgress(float progress, int w) {
        if (w != 0) {
            pixelWidth = w;
            int pixel = (int) (w * progress) / 5;
            if (currentPixel == pixel) {
                return;
            }
            currentPixel = pixel;
        }
        long time = (long) (duration * progress);
        frameTime = AndroidUtilities.formatShortDuration((int) (time / 1000));
        timeWidth = (int) Math.ceil(textPaint.measureText(frameTime));
        invalidate();

        if (progressRunnable != null) {
            Utilities.globalQueue.cancelRunnable(progressRunnable);
        }
        AnimatedFileDrawable file = fileDrawable;
        if (file != null) {
            file.resetStream(false);
        }
        Utilities.globalQueue.postRunnable(progressRunnable = () -> {
            if (fileDrawable == null) {
                pendingProgress = progress;
                return;
            }
            int bitmapSize = Math.max(200, AndroidUtilities.dp(100));
            Bitmap bitmap = fileDrawable.getFrameAtTime(time);
            if (bitmap != null) {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();

                if (width > height) {
                    float scale = width / (float) bitmapSize;
                    width = bitmapSize;
                    height /= scale;
                } else {
                    float scale = height / (float) bitmapSize;
                    height = bitmapSize;
                    width /= scale;
                }
                try {
                    Bitmap backgroundBitmap = Bitmaps.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    dstR.set(0, 0, width, height);
                    Canvas canvas = new Canvas(backgroundBitmap);
                    canvas.drawBitmap(bitmap, null, dstR, paint);
                    canvas.setBitmap(null);
                    bitmap = backgroundBitmap;
                } catch (Throwable ignore) {
                    bitmap = null;
                }
            }
            Bitmap bitmapFinal = bitmap;
            AndroidUtilities.runOnUIThread(() -> {
                if (bitmapFinal != null) {
                    if (bitmapToDraw != null) {
                        if (bitmapToRecycle != null) {
                            bitmapToRecycle.recycle();
                        }
                        bitmapToRecycle = bitmapToDraw;
                    }
                    bitmapToDraw = bitmapFinal;
                    bitmapShader = new BitmapShader(bitmapToDraw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    bitmapShader.setLocalMatrix(matrix);
                    bitmapPaint.setShader(bitmapShader);
                    invalidate();
                    int viewSize = AndroidUtilities.dp(150);
                    float bitmapWidth = bitmapFinal.getWidth();
                    float bitmapHeight = bitmapFinal.getHeight();
                    float aspect = bitmapWidth / bitmapHeight;
                    int viewWidth;
                    int viewHeight;
                    if (aspect > 1.0f) {
                        viewWidth = viewSize;
                        viewHeight = (int) (viewSize / aspect);
                    } else {
                        viewHeight = viewSize;
                        viewWidth = (int) (viewSize * aspect);
                    }
                    ViewGroup.LayoutParams layoutParams = getLayoutParams();
                    if (getVisibility() != VISIBLE || layoutParams.width != viewWidth || layoutParams.height != viewHeight) {
                        layoutParams.width = viewWidth;
                        layoutParams.height = viewHeight;
                        setVisibility(VISIBLE);
                        requestLayout();
                    }
                }
                progressRunnable = null;
            });
        });
    }

    public void open(Uri uri) {
        if (uri == null || uri.equals(videoUri)) {
            return;
        }
        videoUri = uri;
        Utilities.globalQueue.postRunnable(loadRunnable = () -> {
            String scheme = uri.getScheme();
            String path;
            if ("tg".equals(scheme)) {
                int currentAccount = Utilities.parseInt(uri.getQueryParameter("account"));
                Object parentObject = FileLoader.getInstance(currentAccount).getParentObject(Utilities.parseInt(uri.getQueryParameter("rid")));
                TLRPC.TL_document document = new TLRPC.TL_document();
                document.access_hash = Utilities.parseLong(uri.getQueryParameter("hash"));
                document.id = Utilities.parseLong(uri.getQueryParameter("id"));
                document.size = Utilities.parseInt(uri.getQueryParameter("size"));
                document.dc_id = Utilities.parseInt(uri.getQueryParameter("dc"));
                document.mime_type = uri.getQueryParameter("mime");
                document.file_reference = Utilities.hexToBytes(uri.getQueryParameter("reference"));
                TLRPC.TL_documentAttributeFilename filename = new TLRPC.TL_documentAttributeFilename();
                filename.file_name = uri.getQueryParameter("name");
                document.attributes.add(filename);
                document.attributes.add(new TLRPC.TL_documentAttributeVideo());
                String name = FileLoader.getAttachFileName(document);
                if (FileLoader.getInstance(currentAccount).isLoadingFile(name)) {
                    path = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), document.dc_id + "_" + document.id + ".temp").getAbsolutePath();
                } else {
                    path = FileLoader.getPathToAttach(document, false).getAbsolutePath();
                }
                fileDrawable = new AnimatedFileDrawable(new File(path), true, document.size, document, null, parentObject, 0, currentAccount, true);
            } else {
                path = uri.getPath();
                fileDrawable = new AnimatedFileDrawable(new File(path), true, 0, null, null, null, 0, 0, true);
            }
            duration = fileDrawable.getDurationMs();
            if (pendingProgress != 0.0f) {
                setProgress(pendingProgress, pixelWidth);
                pendingProgress = 0.0f;
            }
            AndroidUtilities.runOnUIThread(() -> {
                loadRunnable = null;
                if (fileDrawable != null) {
                    ready = true;
                    delegate.onReady();
                }
            });
        });
    }

    public boolean isReady() {
        return ready;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmapToRecycle != null) {
            bitmapToRecycle.recycle();
            bitmapToRecycle = null;
        }
        if (bitmapToDraw != null && bitmapShader != null) {
            matrix.reset();
            float scale = getMeasuredWidth() / (float) bitmapToDraw.getWidth();
            matrix.preScale(scale, scale);
            bitmapRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRoundRect(bitmapRect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), bitmapPaint);
            frameDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            frameDrawable.draw(canvas);

            canvas.drawText(frameTime, (getMeasuredWidth() - timeWidth) / 2, getMeasuredHeight() - AndroidUtilities.dp(9), textPaint);
        }
    }

    public void close() {
        if (loadRunnable != null) {
            Utilities.globalQueue.cancelRunnable(loadRunnable);
            loadRunnable = null;
        }
        if (progressRunnable != null) {
            Utilities.globalQueue.cancelRunnable(progressRunnable);
            progressRunnable = null;
        }
        AnimatedFileDrawable drawable = fileDrawable;
        if (drawable != null) {
            drawable.resetStream(true);
        }
        Utilities.globalQueue.postRunnable(() -> {
            pendingProgress = 0.0f;
            if (fileDrawable != null) {
                fileDrawable.recycle();
                fileDrawable = null;
            }
        });
        setVisibility(INVISIBLE);
        /*if (bitmapToDraw != null) {
            if (bitmapToRecycle != null) {
                bitmapToRecycle.recycle();
            }
            bitmapToRecycle = bitmapToDraw;
        }*/
        bitmapToDraw = null;
        bitmapShader = null;
        invalidate();

        currentPixel = -1;
        videoUri = null;
        ready = false;
    }
}
