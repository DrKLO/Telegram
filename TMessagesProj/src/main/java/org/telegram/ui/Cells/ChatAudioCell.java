/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ImageLoader;
import org.telegram.android.MessagesController;
import org.telegram.messenger.FileLoader;
import org.telegram.android.MediaController;
import org.telegram.android.MessageObject;
import org.telegram.ui.Components.ProgressView;
import org.telegram.ui.Components.ResourceLoader;
import org.telegram.ui.Components.SeekBar;

import java.io.File;

public class ChatAudioCell extends ChatBaseCell implements SeekBar.SeekBarDelegate, MediaController.FileDownloadProgressListener {

    private static TextPaint timePaint;
    private static Paint circlePaint;

    private SeekBar seekBar;
    private ProgressView progressView;
    private int seekBarX;
    private int seekBarY;

    private int buttonState = 0;
    private int buttonX;
    private int buttonY;
    private boolean buttonPressed = false;

    private StaticLayout timeLayout;
    private int timeX;
    private int timeWidth;
    private String lastTimeString = null;

    private int TAG;

    public ChatAudioCell(Context context) {
        super(context);
        TAG = MediaController.getInstance().generateObserverTag();

        seekBar = new SeekBar(context);
        seekBar.delegate = this;
        progressView = new ProgressView();
        drawForwardedName = true;

        if (timePaint == null) {
            timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            timePaint.setTextSize(AndroidUtilities.dp(12));

            circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        MediaController.getInstance().removeLoadingFileObserver(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateButtonState();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        boolean result = seekBar.onTouch(event.getAction(), event.getX() - seekBarX, event.getY() - seekBarY);
        if (result) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            invalidate();
        } else {
            int side = AndroidUtilities.dp(36);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side) {
                    buttonPressed = true;
                    invalidate();
                    result = true;
                }
            } else if (buttonPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    buttonPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton();
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    buttonPressed = false;
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!(x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side)) {
                        buttonPressed = false;
                        invalidate();
                    }
                }
            }
            if (!result) {
                result = super.onTouchEvent(event);
            }
        }

        return result;
    }

    private void didPressedButton() {
        if (buttonState == 0) {
            boolean result = MediaController.getInstance().playAudio(currentMessageObject);
            if (!currentMessageObject.isOut() && currentMessageObject.isContentUnread()) {
                MessagesController.getInstance().markMessageContentAsRead(currentMessageObject.getId());
            }
            if (result) {
                buttonState = 1;
                invalidate();
            }
        } else if (buttonState == 1) {
            boolean result = MediaController.getInstance().pauseAudio(currentMessageObject);
            if (result) {
                buttonState = 0;
                invalidate();
            }
        } else if (buttonState == 2) {
            FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.audio, true);
            buttonState = 3;
            invalidate();
        } else if (buttonState == 3) {
            FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.audio);
            buttonState = 2;
            invalidate();
        } else if (buttonState == 4) {
            if (currentMessageObject.isOut() && currentMessageObject.isSending()) {
                if (delegate != null) {
                    delegate.didPressedCancelSendButton(this);
                }
            }
        }
    }

    public void updateProgress() {
        if (currentMessageObject == null) {
            return;
        }

        if (!seekBar.isDragging()) {
            seekBar.setProgress(currentMessageObject.audioProgress);
        }

        int duration;
        if (!MediaController.getInstance().isPlayingAudio(currentMessageObject)) {
            duration = currentMessageObject.messageOwner.media.audio.duration;
        } else {
            duration = currentMessageObject.audioProgressSec;
        }
        String timeString = String.format("%02d:%02d", duration / 60, duration % 60);
        if (lastTimeString == null || lastTimeString != null && !lastTimeString.equals(timeString)) {
            timeWidth = (int)Math.ceil(timePaint.measureText(timeString));
            timeLayout = new StaticLayout(timeString, timePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
        invalidate();
    }

    public void downloadAudioIfNeed() {
        if (buttonState == 2) {
            FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.audio, true);
            buttonState = 3;
            invalidate();
        }
    }

    public void updateButtonState() {
        if (currentMessageObject == null) {
            return;
        }
        if (currentMessageObject.isOut() && currentMessageObject.isSending()) {
            buttonState = 4;
        } else {
            File cacheFile = null;
            if (currentMessageObject.messageOwner.attachPath != null && currentMessageObject.messageOwner.attachPath.length() > 0) {
                cacheFile = new File(currentMessageObject.messageOwner.attachPath);
                if(!cacheFile.exists()) {
                    cacheFile = null;
                }
            }
            if (cacheFile == null) {
                cacheFile = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
            }
            if (cacheFile.exists()) {
                MediaController.getInstance().removeLoadingFileObserver(this);
                boolean playing = MediaController.getInstance().isPlayingAudio(currentMessageObject);
                if (!playing || playing && MediaController.getInstance().isAudioPaused()) {
                    buttonState = 0;
                } else {
                    buttonState = 1;
                }
                progressView.setProgress(0);
            } else {
                String fileName = currentMessageObject.getFileName();
                MediaController.getInstance().addLoadingFileObserver(fileName, this);
                if (!FileLoader.getInstance().isLoadingFile(fileName)) {
                    buttonState = 2;
                    progressView.setProgress(0);
                } else {
                    buttonState = 3;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    if (progress != null) {
                        progressView.setProgress(progress);
                    } else {
                        progressView.setProgress(0);
                    }
                }
            }
        }
        updateProgress();
    }

    @Override
    public void onFailedDownload(String fileName) {
        updateButtonState();
    }

    @Override
    public void onSuccessDownload(String fileName) {
        updateButtonState();
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        progressView.setProgress(progress);
        if (buttonState != 3) {
            updateButtonState();
        }
        invalidate();
    }

    @Override
    public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    @Override
    public void onSeekBarDrag(float progress) {
        if (currentMessageObject == null) {
            return;
        }
        currentMessageObject.audioProgress = progress;
        MediaController.getInstance().seekToProgress(currentMessageObject, progress);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, AndroidUtilities.dp(66) + namesOffset);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (currentMessageObject.isOut()) {
            seekBarX = layoutWidth - backgroundWidth + AndroidUtilities.dp(55);
            buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(13);
            timeX = layoutWidth - backgroundWidth + AndroidUtilities.dp(66);
        } else {
            if (isChat) {
                seekBarX = AndroidUtilities.dp(116);
                buttonX = AndroidUtilities.dp(74);
                timeX = AndroidUtilities.dp(127);
            } else {
                seekBarX = AndroidUtilities.dp(64);
                buttonX = AndroidUtilities.dp(22);
                timeX = AndroidUtilities.dp(75);
            }
        }

        seekBar.width = backgroundWidth - AndroidUtilities.dp(70);
        seekBar.height = AndroidUtilities.dp(30);
        progressView.width = backgroundWidth - AndroidUtilities.dp(94);
        progressView.height = AndroidUtilities.dp(30);
        seekBarY = AndroidUtilities.dp(11) + namesOffset;
        buttonY = AndroidUtilities.dp(13) + namesOffset;

        updateProgress();
    }

    @Override
    public void setMessageObject(MessageObject messageObject) {
        if (currentMessageObject != messageObject || isUserDataChanged()) {
            if (AndroidUtilities.isTablet()) {
                backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat ? 102 : 50), AndroidUtilities.dp(300));
            } else {
                backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat ? 102 : 50), AndroidUtilities.dp(300));
            }

            if (messageObject.isOut()) {
                seekBar.type = 0;
                progressView.setProgressColors(0xffb4e396, 0xff6ac453);
            } else {
                seekBar.type = 1;
                progressView.setProgressColors(0xffd9e2eb, 0xff86c5f8);
            }

            super.setMessageObject(messageObject);
        }
        updateButtonState();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (currentMessageObject == null) {
            return;
        }

        canvas.save();
        if (buttonState == 0 || buttonState == 1) {
            canvas.translate(seekBarX, seekBarY);
            seekBar.draw(canvas);
        } else {
            canvas.translate(seekBarX + AndroidUtilities.dp(12), seekBarY);
            progressView.draw(canvas);
        }
        canvas.restore();

        int state = buttonState;
        if (currentMessageObject.isOut()) {
            timePaint.setColor(0xff70b15c);
            circlePaint.setColor(0xff87bf78);
        } else {
            state += 5;
            timePaint.setColor(0xffa1aab3);
            circlePaint.setColor(0xff4195e5);
        }
        Drawable buttonDrawable = ResourceLoader.audioStatesDrawable[state][buttonPressed ? 1 : 0];
        setDrawableBounds(buttonDrawable, buttonX, buttonY);
        buttonDrawable.draw(canvas);

        canvas.save();
        canvas.translate(timeX, AndroidUtilities.dp(42) + namesOffset);
        timeLayout.draw(canvas);
        canvas.restore();

        if (currentMessageObject.isContentUnread()) {
            canvas.drawCircle(timeX + timeWidth + AndroidUtilities.dp(8), AndroidUtilities.dp(49.5f) + namesOffset, AndroidUtilities.dp(3), circlePaint);
        }
    }
}
