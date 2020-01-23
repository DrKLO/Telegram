package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;

import java.io.File;

public class PatternCell extends BackupImageView implements DownloadController.FileDownloadProgressListener {

    private RectF rect = new RectF();
    private RadialProgress2 radialProgress;
    private boolean wasSelected;
    private TLRPC.TL_wallPaper currentPattern;
    private int currentAccount = UserConfig.selectedAccount;
    private LinearGradient gradientShader;
    private int currentGradientColor1;
    private int currentGradientColor2;
    private int currentGradientAngle;

    private Paint backgroundPaint;

    private int TAG;

    private PatternCellDelegate delegate;
    private int maxWallpaperSize;

    public interface PatternCellDelegate {
        TLRPC.TL_wallPaper getSelectedPattern();
        int getPatternColor();
        int getBackgroundGradientColor();
        int getBackgroundGradientAngle();
        int getBackgroundColor();
    }

    public PatternCell(Context context, int maxSize, PatternCellDelegate patternCellDelegate) {
        super(context);
        setRoundRadius(AndroidUtilities.dp(6));
        maxWallpaperSize = maxSize;
        delegate = patternCellDelegate;

        radialProgress = new RadialProgress2(this);
        radialProgress.setProgressRect(AndroidUtilities.dp(30), AndroidUtilities.dp(30), AndroidUtilities.dp(70), AndroidUtilities.dp(70));

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
    }

    public void setPattern(TLRPC.TL_wallPaper wallPaper) {
        currentPattern = wallPaper;
        if (wallPaper != null) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(wallPaper.document.thumbs, 100);
            setImage(ImageLocation.getForDocument(thumb, wallPaper.document), "100_100", null, null, "jpg", 0, 1, wallPaper);
        } else {
            setImageDrawable(null);
        }
        updateSelected(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSelected(false);
    }

    public void updateSelected(boolean animated) {
        TLRPC.TL_wallPaper selectedPattern = delegate.getSelectedPattern();
        boolean isSelected = currentPattern == null && selectedPattern == null || selectedPattern != null && currentPattern != null && currentPattern.id == selectedPattern.id;
        if (isSelected) {
            updateButtonState(selectedPattern, false, animated);
        } else {
            radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, animated);
        }
        invalidate();
    }

    private void updateButtonState(Object image, boolean ifSame, boolean animated) {
        if (image instanceof TLRPC.TL_wallPaper || image instanceof MediaController.SearchImage) {
            File path;
            int size;
            String fileName;
            if (image instanceof TLRPC.TL_wallPaper) {
                TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) image;
                fileName = FileLoader.getAttachFileName(wallPaper.document);
                if (TextUtils.isEmpty(fileName)) {
                    return;
                }
                path = FileLoader.getPathToAttach(wallPaper.document, true);
            } else {
                MediaController.SearchImage wallPaper = (MediaController.SearchImage) image;
                if (wallPaper.photo != null) {
                    TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, maxWallpaperSize, true);
                    path = FileLoader.getPathToAttach(photoSize, true);
                    fileName = FileLoader.getAttachFileName(photoSize);
                } else {
                    path = ImageLoader.getHttpFilePath(wallPaper.imageUrl, "jpg");
                    fileName = path.getName();
                }
                if (TextUtils.isEmpty(fileName)) {
                    return;
                }
            }
            if (path.exists()) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                radialProgress.setProgress(1, animated);
                radialProgress.setIcon(MediaActionDrawable.ICON_CHECK, ifSame, animated);
            } else {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, this);
                boolean isLoading = FileLoader.getInstance(currentAccount).isLoadingFile(fileName);
                Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                if (progress != null) {
                    radialProgress.setProgress(progress, animated);
                } else {
                    radialProgress.setProgress(0, animated);
                }
                radialProgress.setIcon(MediaActionDrawable.ICON_EMPTY, ifSame, animated);
            }
        } else {
            radialProgress.setIcon(MediaActionDrawable.ICON_CHECK, ifSame, animated);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        getImageReceiver().setAlpha(0.8f);

        int backgroundColor = delegate.getBackgroundColor();
        int backgroundGradientColor = delegate.getBackgroundGradientColor();
        int backgroundGradientAngle = delegate.getBackgroundGradientAngle();
        int patternColor = delegate.getPatternColor();

        if (backgroundGradientColor != 0) {
            if (gradientShader == null || backgroundColor != currentGradientColor1 || backgroundGradientColor != currentGradientColor2 || backgroundGradientAngle != currentGradientAngle) {
                currentGradientColor1 = backgroundColor;
                currentGradientColor2 = backgroundGradientColor;
                currentGradientAngle = backgroundGradientAngle;

                final Rect r = BackgroundGradientDrawable.getGradientPoints(currentGradientAngle, getMeasuredWidth(), getMeasuredHeight());
                gradientShader = new LinearGradient(r.left, r.top, r.right, r.bottom, new int[]{backgroundColor, backgroundGradientColor}, null, Shader.TileMode.CLAMP);
            }
        } else {
            gradientShader = null;
        }
        backgroundPaint.setShader(gradientShader);
        if (gradientShader == null) {
            backgroundPaint.setColor(backgroundColor);
        }
        rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), backgroundPaint);

        super.onDraw(canvas);

        radialProgress.setColors(patternColor, patternColor, 0xffffffff, 0xffffffff);
        radialProgress.draw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(100), AndroidUtilities.dp(100));
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {
        if (canceled) {
            radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, true);
        } else {
            updateButtonState(currentPattern, true, canceled);
        }
    }

    @Override
    public void onSuccessDownload(String fileName) {
        radialProgress.setProgress(1, true);
        updateButtonState(currentPattern, false, true);
    }

    @Override
    public void onProgressDownload(String fileName, long downloadedSize, long totalSize) {
        radialProgress.setProgress(Math.min(1f, downloadedSize / (float) totalSize), true);
        if (radialProgress.getIcon() != MediaActionDrawable.ICON_EMPTY) {
            updateButtonState(currentPattern, false, true);
        }
    }

    @Override
    public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }
}
