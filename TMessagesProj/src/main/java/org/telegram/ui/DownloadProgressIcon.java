package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.ArrayList;
import java.util.HashMap;

public class DownloadProgressIcon extends View implements NotificationCenter.NotificationCenterDelegate {

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int currentAccount;
    ArrayList<ProgressObserver> currentListeners = new ArrayList<>();
    float progress;
    float currentProgress;
    float progressDt;

    ImageReceiver downloadImageReceiver = new ImageReceiver(this);
    ImageReceiver downloadCompleteImageReceiver = new ImageReceiver(this);
    RLottieDrawable downloadDrawable;
    RLottieDrawable downloadCompleteDrawable;
    boolean showCompletedIcon;
    boolean hasUnviewedDownloads;
    int currentColor;

    public DownloadProgressIcon(int currentAccount, Context context) {
        super(context);
        this.currentAccount = currentAccount;

        downloadDrawable = new RLottieDrawable(R.raw.download_progress, "download_progress", AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
        downloadDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
        downloadCompleteDrawable = new RLottieDrawable(R.raw.download_finish, "download_finish", AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
        downloadCompleteDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));

        downloadImageReceiver.setImageBitmap(downloadDrawable);
        downloadCompleteImageReceiver.setImageBitmap(downloadCompleteDrawable);

        downloadImageReceiver.setAutoRepeat(1);
        downloadDrawable.setAutoRepeat(1);
        downloadDrawable.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
        int padding = AndroidUtilities.dp(15);
        downloadImageReceiver.setImageCoords(padding, padding, getMeasuredWidth() - padding * 2, getMeasuredHeight() - padding * 2);
        downloadCompleteImageReceiver.setImageCoords(padding, padding, getMeasuredWidth() - padding * 2, getMeasuredHeight() - padding * 2);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getAlpha() == 0) {
            return;
        }

        if (currentColor != Theme.getColor(Theme.key_actionBarDefaultIcon)) {
            currentColor = Theme.getColor(Theme.key_actionBarDefaultIcon);
            paint.setColor(Theme.getColor(Theme.key_actionBarDefaultIcon));
            paint2.setColor(Theme.getColor(Theme.key_actionBarDefaultIcon));
            downloadImageReceiver.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
            downloadCompleteImageReceiver.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
            paint2.setAlpha(100);
        }

        if (currentProgress != progress) {
            currentProgress += progressDt;
            if (progressDt > 0 && currentProgress > progress) {
                currentProgress = progress;
            } else if (progressDt < 0 && currentProgress < progress) {
                currentProgress = progress;
            } else {
                invalidate();
            }
        }

        int cy = getMeasuredHeight() / 2 + AndroidUtilities.dp(8);
        float r = AndroidUtilities.dp(1f);

        float startPadding = AndroidUtilities.dp(16);
        float width = getMeasuredWidth() - startPadding * 2;
        AndroidUtilities.rectTmp.set(startPadding, cy - r, getMeasuredWidth() - startPadding, cy + r);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint2);

        AndroidUtilities.rectTmp.set(startPadding, cy - r, startPadding + width * currentProgress, cy + r);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint);

        canvas.save();
        canvas.clipRect(0, 0, getMeasuredWidth(), cy - r);
        if (progress != 1f) {
            showCompletedIcon = false;
        }
        if (showCompletedIcon) {
            downloadCompleteImageReceiver.draw(canvas);
        } else {
            downloadImageReceiver.draw(canvas);
        }

        if (progress == 1f && !showCompletedIcon) {
            if (downloadDrawable.getCurrentFrame() == 0) {
                downloadCompleteDrawable.setCurrentFrame(0, false);
                downloadCompleteDrawable.start();
                showCompletedIcon = true;
            }
        }
        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateDownloadingListeners();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.onDownloadingFilesChanged);
        downloadImageReceiver.onAttachedToWindow();
        downloadCompleteImageReceiver.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        detachCurrentListeners();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.onDownloadingFilesChanged);
        downloadImageReceiver.onDetachedFromWindow();
        downloadCompleteImageReceiver.onDetachedFromWindow();
    }


    private void updateDownloadingListeners() {
        DownloadController downloadController = DownloadController.getInstance(currentAccount);
        HashMap<String, ProgressObserver> observerHashMap = new HashMap<>();
        for (int i = 0; i < currentListeners.size(); i++) {
            observerHashMap.put(currentListeners.get(i).fileName, currentListeners.get(i));
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(currentListeners.get(i));
        }
        currentListeners.clear();
        for (int i = 0; i < downloadController.downloadingFiles.size(); i++) {
            String filename = downloadController.downloadingFiles.get(i).getFileName();
            if (FileLoader.getInstance(currentAccount).isLoadingFile(filename)) {
                ProgressObserver progressObserver = observerHashMap.get(filename);
                if (progressObserver == null) {
                    progressObserver = new ProgressObserver(filename);
                }
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(filename, progressObserver);
                currentListeners.add(progressObserver);
            }
        }
        if (currentListeners.size() == 0 && (getVisibility() != View.VISIBLE || getAlpha() != 1f)) {
            if (DownloadController.getInstance(currentAccount).hasUnviewedDownloads()) {
                progress = 1f;
                currentProgress = 1f;
            } else {
                progress = 0;
                currentProgress = 0;
            }
        }
    }

    public void updateProgress() {
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        long total = 0;
        long downloaded = 0;
        for (int i = 0; i < currentListeners.size(); i++) {
            total += currentListeners.get(i).total;
            downloaded += currentListeners.get(i).downloaded;
        }
        if (total == 0) {
            progress = 1f;
        } else {
            progress = downloaded / (float) total;
        }
        if (progress > 1f) {
            progress = 1f;
        } else if (progress < 0) {
            progress = 0;
        }

        progressDt = (progress - currentProgress) * 16f / 150f;
        invalidate();
    }

    private void detachCurrentListeners() {
        for (int i = 0; i < currentListeners.size(); i++) {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(currentListeners.get(i));
        }
        currentListeners.clear();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.onDownloadingFilesChanged) {
            updateDownloadingListeners();
            updateProgress();
        }
    }

    private class ProgressObserver implements DownloadController.FileDownloadProgressListener {

        long total;
        long downloaded;
        private final String fileName;

        private ProgressObserver(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void onFailedDownload(String fileName, boolean canceled) {

        }

        @Override
        public void onSuccessDownload(String fileName) {

        }

        @Override
        public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
            downloaded = downloadSize;
            total = totalSize;
            updateProgress();
        }

        @Override
        public void onProgressUpload(String fileName, long downloadSize, long totalSize, boolean isEncrypted) {

        }

        @Override
        public int getObserverTag() {
            return 0;
        }
    }

}
