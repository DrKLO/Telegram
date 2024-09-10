package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.io.File;

public class OtherDocumentPlaceholderDrawable extends RecyclableDrawable implements DownloadController.FileDownloadProgressListener {

    private static Paint paint;
    private static Paint progressPaint;
    private static TextPaint docPaint;
    private static TextPaint namePaint;
    private static TextPaint sizePaint;
    private static TextPaint buttonPaint;
    private static TextPaint percentPaint;
    private static TextPaint openPaint;
    private static DecelerateInterpolator decelerateInterpolator;

    private long lastUpdateTime = 0;
    private float currentProgress = 0;
    private float animationProgressStart = 0;
    private long currentProgressTime = 0;
    private float animatedProgressValue = 0;
    private float animatedAlphaValue = 1.0f;
    private boolean progressVisible;

    private View parentView;
    private MessageObject parentMessageObject;
    private int TAG;
    private boolean loading;
    private boolean loaded;

    private Drawable thumbDrawable;
    private String ext;
    private String fileName;
    private String fileSize;
    private String progress;

    static {
        paint = new Paint();
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        docPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        sizePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        buttonPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        percentPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        openPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        decelerateInterpolator = new DecelerateInterpolator();

        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        paint.setColor(0xff272c32);
        docPaint.setColor(0xffffffff);
        namePaint.setColor(0xffffffff);
        sizePaint.setColor(0xff626b75);
        buttonPaint.setColor(0xff626b75);
        percentPaint.setColor(0xffffffff);
        openPaint.setColor(0xffffffff);

        docPaint.setTypeface(AndroidUtilities.bold());
        namePaint.setTypeface(AndroidUtilities.bold());
        buttonPaint.setTypeface(AndroidUtilities.bold());
        percentPaint.setTypeface(AndroidUtilities.bold());
        openPaint.setTypeface(AndroidUtilities.bold());
    }

    public OtherDocumentPlaceholderDrawable(Context context, View view, MessageObject messageObject) {
        docPaint.setTextSize(AndroidUtilities.dp(14));
        namePaint.setTextSize(AndroidUtilities.dp(19));
        sizePaint.setTextSize(AndroidUtilities.dp(15));
        buttonPaint.setTextSize(AndroidUtilities.dp(15));
        percentPaint.setTextSize(AndroidUtilities.dp(15));
        openPaint.setTextSize(AndroidUtilities.dp(15));
        progressPaint.setStrokeWidth(AndroidUtilities.dp(2));

        parentView = view;
        parentMessageObject = messageObject;
        TAG = DownloadController.getInstance(messageObject.currentAccount).generateObserverTag();

        TLRPC.Document document = messageObject.getDocument();
        if (document != null) {
            fileName = FileLoader.getDocumentFileName(messageObject.getDocument());
            if (TextUtils.isEmpty(fileName)) {
                fileName = "name";
            }
            int idx;
            ext = (idx = fileName.lastIndexOf('.')) == -1 ? "" : fileName.substring(idx + 1).toUpperCase();
            int w = (int) Math.ceil(docPaint.measureText(ext));
            if (w > AndroidUtilities.dp(40)) {
                ext = TextUtils.ellipsize(ext, docPaint, AndroidUtilities.dp(40), TextUtils.TruncateAt.END).toString();
            }
            thumbDrawable = context.getResources().getDrawable(AndroidUtilities.getThumbForNameOrMime(fileName, messageObject.getDocument().mime_type, true)).mutate();
            fileSize = AndroidUtilities.formatFileSize(document.size);
            w = (int) Math.ceil(namePaint.measureText(fileName));
            if (w > AndroidUtilities.dp(320)) {
                fileName = TextUtils.ellipsize(fileName, namePaint, AndroidUtilities.dp(320), TextUtils.TruncateAt.END).toString();
            }
        }
        checkFileExist();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }

    @Override
    public void setAlpha(int alpha) {
        if (thumbDrawable != null) {
            thumbDrawable.setAlpha(alpha);
        }
        paint.setAlpha(alpha);
        docPaint.setAlpha(alpha);
        namePaint.setAlpha(alpha);
        sizePaint.setAlpha(alpha);
        buttonPaint.setAlpha(alpha);
        percentPaint.setAlpha(alpha);
        openPaint.setAlpha(alpha);
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect bounds = getBounds();
        int width = bounds.width();
        int height = bounds.height();
        canvas.save();
        canvas.translate(bounds.left, bounds.top);

        canvas.drawRect(0, 0, width, height, paint);
        int y = (height - AndroidUtilities.dp(240)) / 2;
        int x = (width - AndroidUtilities.dp(48)) / 2;
        thumbDrawable.setBounds(x, y, x + AndroidUtilities.dp(48), y + AndroidUtilities.dp(48));
        thumbDrawable.draw(canvas);
        int w = (int) Math.ceil(docPaint.measureText(ext));
        canvas.drawText(ext, (width - w) / 2, y + AndroidUtilities.dp(31), docPaint);

        w = (int) Math.ceil(namePaint.measureText(fileName));
        canvas.drawText(fileName,(width - w) / 2, y + AndroidUtilities.dp(96), namePaint);

        w = (int) Math.ceil(sizePaint.measureText(fileSize));
        canvas.drawText(fileSize,(width - w) / 2, y + AndroidUtilities.dp(125), sizePaint);

        String button;
        TextPaint paint;
        int offsetY;
        if (loaded) {
            button = LocaleController.getString(R.string.OpenFile);
            paint = openPaint;
            offsetY = 0;
        } else {
            if (loading) {
                button = LocaleController.getString(R.string.Cancel).toUpperCase();
            } else {
                button = LocaleController.getString(R.string.TapToDownload);
            }
            offsetY = AndroidUtilities.dp(28);
            paint = buttonPaint;
        }
        w = (int) Math.ceil(paint.measureText(button));
        canvas.drawText(button,(width - w) / 2, y + AndroidUtilities.dp(235) + offsetY, paint);

        if (progressVisible) {
            if (progress != null) {
                w = (int) Math.ceil(percentPaint.measureText(progress));
                canvas.drawText(progress,(width - w) / 2, y + AndroidUtilities.dp(210), percentPaint);
            }

            x = (width - AndroidUtilities.dp(240)) / 2;
            y += AndroidUtilities.dp(232);
            progressPaint.setColor(0xff626b75);
            progressPaint.setAlpha((int) (255 * animatedAlphaValue));
            int start = (int) (AndroidUtilities.dp(240) * animatedProgressValue);
            canvas.drawRect(x + start, y, x + AndroidUtilities.dp(240), y + AndroidUtilities.dp(2), progressPaint);

            progressPaint.setColor(0xffffffff);
            progressPaint.setAlpha((int) (255 * animatedAlphaValue));
            canvas.drawRect(x, y, x + AndroidUtilities.dp(240) * animatedProgressValue, y + AndroidUtilities.dp(2), progressPaint);
            updateAnimation();
        }

        canvas.restore();
    }

    @Override
    public int getIntrinsicWidth() {
        return parentView.getMeasuredWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return parentView.getMeasuredHeight();
    }

    @Override
    public int getMinimumWidth() {
        return parentView.getMeasuredWidth();
    }

    @Override
    public int getMinimumHeight() {
        return parentView.getMeasuredHeight();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    @Override
    public void onFailedDownload(String name, boolean canceled) {
        checkFileExist();
    }

    @Override
    public void onSuccessDownload(String name) {
        setProgress(1, true);
        checkFileExist();
    }

    @Override
    public void onProgressDownload(String fileName, long downloadedSize, long totalSize) {
        if (!progressVisible) {
            checkFileExist();
        }
        setProgress(Math.min(1f, downloadedSize / (float) totalSize), true);
    }

    @Override
    public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    @Override
    public void recycle() {
        DownloadController.getInstance(parentMessageObject.currentAccount).removeLoadingFileObserver(this);
        parentView = null;
        parentMessageObject = null;
    }

    public void checkFileExist() {
        if (parentMessageObject != null && parentMessageObject.messageOwner.media != null) {
            String fileName = null;
            File cacheFile;
            if (TextUtils.isEmpty(parentMessageObject.messageOwner.attachPath) || !(new File(parentMessageObject.messageOwner.attachPath).exists())) {
                cacheFile = FileLoader.getInstance(UserConfig.selectedAccount).getPathToMessage(parentMessageObject.messageOwner);
                if (!cacheFile.exists()) {
                    fileName = FileLoader.getAttachFileName(parentMessageObject.getDocument());
                }
            }
            loaded = false;
            if (fileName == null) {
                progressVisible = false;
                loading = false;
                loaded = true;
                DownloadController.getInstance(parentMessageObject.currentAccount).removeLoadingFileObserver(this);
            } else {
                DownloadController.getInstance(parentMessageObject.currentAccount).addLoadingFileObserver(fileName, this);
                loading = FileLoader.getInstance(parentMessageObject.currentAccount).isLoadingFile(fileName);
                if (loading) {
                    progressVisible = true;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    if (progress == null) {
                        progress = 0.0f;
                    }
                    setProgress(progress, false);
                } else {
                    progressVisible = false;
                }
            }
        } else {
            loading = false;
            loaded = true;
            progressVisible = false;
            setProgress(0, false);
            DownloadController.getInstance(parentMessageObject.currentAccount).removeLoadingFileObserver(this);
        }
        parentView.invalidate();
    }

    private void updateAnimation() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;

        if (animatedProgressValue != 1 && animatedProgressValue != currentProgress) {
            float progressDiff = currentProgress - animationProgressStart;
            if (progressDiff > 0) {
                currentProgressTime += dt;
                if (currentProgressTime >= 300) {
                    animatedProgressValue = currentProgress;
                    animationProgressStart = currentProgress;
                    currentProgressTime = 0;
                } else {
                    animatedProgressValue = animationProgressStart + progressDiff * decelerateInterpolator.getInterpolation(currentProgressTime / 300.0f);
                }
            }
            parentView.invalidate();
        }
        if (animatedProgressValue >= 1 && animatedProgressValue == 1 && animatedAlphaValue != 0) {
            animatedAlphaValue -= dt / 200.0f;
            if (animatedAlphaValue <= 0) {
                animatedAlphaValue = 0.0f;
            }
            parentView.invalidate();
        }
    }

    public void setProgress(float value, boolean animated) {
        if (!animated) {
            animatedProgressValue = value;
            animationProgressStart = value;
        } else {
            animationProgressStart = animatedProgressValue;
        }
        progress = String.format("%d%%", (int) (100 * value));
        if (value != 1) {
            animatedAlphaValue = 1;
        }
        currentProgress = value;
        currentProgressTime = 0;

        lastUpdateTime = System.currentTimeMillis();
        parentView.invalidate();
    }

    public float getCurrentProgress() {
        return currentProgress;
    }
}
