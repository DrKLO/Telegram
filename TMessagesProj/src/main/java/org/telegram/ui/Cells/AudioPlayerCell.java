/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RadialProgress;

import java.io.File;

public class AudioPlayerCell extends View implements MediaController.FileDownloadProgressListener {

    private boolean buttonPressed;

    private int titleY = AndroidUtilities.dp(9);
    private StaticLayout titleLayout;

    private int descriptionY = AndroidUtilities.dp(29);
    private StaticLayout descriptionLayout;

    private MessageObject currentMessageObject;

    private int TAG;
    private int buttonState;
    private RadialProgress radialProgress;

    public AudioPlayerCell(Context context) {
        super(context);

        radialProgress = new RadialProgress(this);
        TAG = MediaController.getInstance().generateObserverTag();
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        descriptionLayout = null;
        titleLayout = null;

        int viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        int maxWidth = viewWidth - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - AndroidUtilities.dp(8 + 20);

        try {
            String title = currentMessageObject.getMusicTitle();
            int width = (int) Math.ceil(Theme.chat_contextResult_titleTextPaint.measureText(title));
            CharSequence titleFinal = TextUtils.ellipsize(title.replace('\n', ' '), Theme.chat_contextResult_titleTextPaint, Math.min(width, maxWidth), TextUtils.TruncateAt.END);
            titleLayout = new StaticLayout(titleFinal, Theme.chat_contextResult_titleTextPaint, maxWidth + AndroidUtilities.dp(4), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            String author = currentMessageObject.getMusicAuthor();
            int width = (int) Math.ceil(Theme.chat_contextResult_descriptionTextPaint.measureText(author));
            CharSequence authorFinal = TextUtils.ellipsize(author.replace('\n', ' '), Theme.chat_contextResult_descriptionTextPaint, Math.min(width, maxWidth), TextUtils.TruncateAt.END);
            descriptionLayout = new StaticLayout(authorFinal, Theme.chat_contextResult_descriptionTextPaint, maxWidth + AndroidUtilities.dp(4), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e(e);
        }

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(56));

        int maxPhotoWidth = AndroidUtilities.dp(52);
        int x = LocaleController.isRTL ? MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(8) - maxPhotoWidth : AndroidUtilities.dp(8);
        radialProgress.setProgressRect(x + AndroidUtilities.dp(4), AndroidUtilities.dp(6), x + AndroidUtilities.dp(48), AndroidUtilities.dp(50));
    }

    public void setMessageObject(MessageObject messageObject) {
        currentMessageObject = messageObject;
        requestLayout();
        updateButtonState(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        MediaController.getInstance().removeLoadingFileObserver(this);
    }

    public MessageObject getMessageObject() {
        return currentMessageObject;
    }

    public void didPressedButton() {
        if (buttonState == 0) {
            if (MediaController.getInstance().findMessageInPlaylistAndPlay(currentMessageObject)) {
                buttonState = 1;
                radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                invalidate();
            }
        } else if (buttonState == 1) {
            boolean result = MediaController.getInstance().pauseMessage(currentMessageObject);
            if (result) {
                buttonState = 0;
                radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                invalidate();
            }
        } else if (buttonState == 2) {
            radialProgress.setProgress(0, false);
            FileLoader.getInstance().loadFile(currentMessageObject.getDocument(), true, 0);
            buttonState = 4;
            radialProgress.setBackground(getDrawableForCurrentState(), true, false);
            invalidate();
        } else if (buttonState == 4) {
            FileLoader.getInstance().cancelLoadFile(currentMessageObject.getDocument());
            buttonState = 2;
            radialProgress.setBackground(getDrawableForCurrentState(), false, false);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (titleLayout != null) {
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), titleY);
            titleLayout.draw(canvas);
            canvas.restore();
        }

        if (descriptionLayout != null) {
            Theme.chat_contextResult_descriptionTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), descriptionY);
            descriptionLayout.draw(canvas);
            canvas.restore();
        }

        radialProgress.setProgressColor(Theme.getColor(buttonPressed ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
        radialProgress.draw(canvas);
    }

    private Drawable getDrawableForCurrentState() {
        if (buttonState == -1) {
            return null;
        }
        radialProgress.setAlphaForPrevious(false);
        return Theme.chat_fileStatesDrawable[buttonState + 5][buttonPressed ? 1 : 0];
    }

    public void updateButtonState(boolean animated) {
        String fileName = currentMessageObject.getFileName();
        File cacheFile = null;
        if (!TextUtils.isEmpty(currentMessageObject.messageOwner.attachPath)) {
            cacheFile = new File(currentMessageObject.messageOwner.attachPath);
            if (!cacheFile.exists()) {
                cacheFile = null;
            }
        }
        if (cacheFile == null) {
            cacheFile = FileLoader.getPathToAttach(currentMessageObject.getDocument());
        }
        if (TextUtils.isEmpty(fileName)) {
            radialProgress.setBackground(null, false, false);
            return;
        }
        if (cacheFile.exists() && cacheFile.length() == 0) {
            cacheFile.delete();
        }
        if (!cacheFile.exists()) {
            MediaController.getInstance().addLoadingFileObserver(fileName, this);
            boolean isLoading = FileLoader.getInstance().isLoadingFile(fileName);
            if (!isLoading) {
                buttonState = 2;
                radialProgress.setProgress(0, animated);
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            } else {
                buttonState = 4;
                Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                if (progress != null) {
                    radialProgress.setProgress(progress, animated);
                } else {
                    radialProgress.setProgress(0, animated);
                }
                radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
            }
            invalidate();
        } else {
            MediaController.getInstance().removeLoadingFileObserver(this);
            boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
            if (!playing || playing && MediaController.getInstance().isMessagePaused()) {
                buttonState = 0;
            } else {
                buttonState = 1;
            }
            radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            invalidate();
        }
    }

    @Override
    public void onFailedDownload(String fileName) {
        updateButtonState(false);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        radialProgress.setProgress(1, true);
        updateButtonState(true);
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        radialProgress.setProgress(progress, true);
        if (buttonState != 4) {
            updateButtonState(false);
        }
    }

    @Override
    public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }
}
