/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;

import java.io.File;

public class PopupAudioView extends BaseCell implements SeekBar.SeekBarDelegate, MediaController.FileDownloadProgressListener {

    private boolean wasLayout = false;
    protected MessageObject currentMessageObject;

    private TextPaint timePaint;

    private SeekBar seekBar;
    private ProgressView progressView;
    private int seekBarX;
    private int seekBarY;

    private int buttonState = 0;
    private int buttonX;
    private int buttonY;
    private int buttonPressed = 0;

    private StaticLayout timeLayout;
    private int timeX;
    int timeWidth = 0;
    private String lastTimeString = null;

    private int TAG;

    public PopupAudioView(Context context) {
        super(context);
        timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        timePaint.setTextSize(AndroidUtilities.dp(16));

        TAG = MediaController.getInstance().generateObserverTag();

        seekBar = new SeekBar(getContext());
        seekBar.setDelegate(this);
        progressView = new ProgressView();
    }

    public void setMessageObject(MessageObject messageObject) {
        if (currentMessageObject != messageObject) {
            seekBar.setColors(Theme.getColor(Theme.key_chat_inAudioSeekbar), Theme.getColor(Theme.key_chat_inAudioSeekbarFill), Theme.getColor(Theme.key_chat_inAudioSeekbarSelected));
            progressView.setProgressColors(0xffd9e2eb, 0xff86c5f8); //TODO

            currentMessageObject = messageObject;
            wasLayout = false;

            requestLayout();
        }
        updateButtonState();
    }

    public final MessageObject getMessageObject() {
        return currentMessageObject;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, AndroidUtilities.dp(56));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (currentMessageObject == null) {
            return;
        }

        seekBarX = AndroidUtilities.dp(54);
        buttonX = AndroidUtilities.dp(10);
        timeX = getMeasuredWidth() - timeWidth - AndroidUtilities.dp(16);

        seekBar.setSize(getMeasuredWidth() - AndroidUtilities.dp(70) - timeWidth, AndroidUtilities.dp(30));
        progressView.width = getMeasuredWidth() - AndroidUtilities.dp(94) - timeWidth;
        progressView.height = AndroidUtilities.dp(30);
        seekBarY = AndroidUtilities.dp(13);
        buttonY = AndroidUtilities.dp(10);

        updateProgress();

        if (changed || !wasLayout) {
            wasLayout = true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentMessageObject == null) {
            return;
        }

        if (!wasLayout) {
            requestLayout();
            return;
        }

        setDrawableBounds(Theme.chat_msgInMediaDrawable, 0, 0, getMeasuredWidth(), getMeasuredHeight());
        Theme.chat_msgInMediaDrawable.draw(canvas);

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

        int state = buttonState + 5;
        timePaint.setColor(0xffa1aab3);
        Drawable buttonDrawable = Theme.chat_fileStatesDrawable[state][buttonPressed];
        int side = AndroidUtilities.dp(36);
        int x = (side - buttonDrawable.getIntrinsicWidth()) / 2;
        int y = (side - buttonDrawable.getIntrinsicHeight()) / 2;
        setDrawableBounds(buttonDrawable, x + buttonX, y + buttonY);
        buttonDrawable.draw(canvas);

        canvas.save();
        canvas.translate(timeX, AndroidUtilities.dp(18));
        timeLayout.draw(canvas);
        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        MediaController.getInstance().removeLoadingFileObserver(this);
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
                    buttonPressed = 1;
                    invalidate();
                    result = true;
                }
            } else if (buttonPressed == 1) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton();
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    buttonPressed = 0;
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!(x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side)) {
                        buttonPressed = 0;
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
            boolean result = MediaController.getInstance().playMessage(currentMessageObject);
            if (!currentMessageObject.isOut() && currentMessageObject.isContentUnread()) {
                if (currentMessageObject.messageOwner.to_id.channel_id == 0) {
                    MessagesController.getInstance().markMessageContentAsRead(currentMessageObject);
                    currentMessageObject.setContentIsRead();
                }
            }
            if (result) {
                buttonState = 1;
                invalidate();
            }
        } else if (buttonState == 1) {
            boolean result = MediaController.getInstance().pauseMessage(currentMessageObject);
            if (result) {
                buttonState = 0;
                invalidate();
            }
        } else if (buttonState == 2) {
            FileLoader.getInstance().loadFile(currentMessageObject.getDocument(), true, 0);
            buttonState = 4;
            invalidate();
        } else if (buttonState == 3) {
            FileLoader.getInstance().cancelLoadFile(currentMessageObject.getDocument());
            buttonState = 2;
            invalidate();
        }
    }

    public void updateProgress() {
        if (currentMessageObject == null) {
            return;
        }

        if (!seekBar.isDragging()) {
            seekBar.setProgress(currentMessageObject.audioProgress);
        }

        int duration = 0;
        if (!MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
            for (int a = 0; a < currentMessageObject.getDocument().attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = currentMessageObject.getDocument().attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    duration = attribute.duration;
                    break;
                }
            }
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
            FileLoader.getInstance().loadFile(currentMessageObject.getDocument(), true, 0);
            buttonState = 3;
            invalidate();
        }
    }

    public void updateButtonState() {
        String fileName = currentMessageObject.getFileName();
        File cacheFile = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
        if (cacheFile.exists()) {
            MediaController.getInstance().removeLoadingFileObserver(this);
            boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
            if (!playing || playing && MediaController.getInstance().isMessagePaused()) {
                buttonState = 0;
            } else {
                buttonState = 1;
            }
            progressView.setProgress(0);
        } else {
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
}
