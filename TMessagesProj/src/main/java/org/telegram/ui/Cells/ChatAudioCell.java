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
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.android.MediaController;
import org.telegram.messenger.TLRPC;
import org.telegram.android.MessagesController;
import org.telegram.messenger.R;
import org.telegram.android.MessageObject;
import org.telegram.android.ImageReceiver;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ProgressView;
import org.telegram.ui.Components.SeekBar;

import java.io.File;

public class ChatAudioCell extends ChatBaseCell implements SeekBar.SeekBarDelegate, MediaController.FileDownloadProgressListener {

    private static Drawable[][] statesDrawable = new Drawable[8][2];
    private static TextPaint timePaint;

    private ImageReceiver avatarImage;
    private AvatarDrawable avatarDrawable;
    private boolean needAvatarImage = false;
    private SeekBar seekBar;
    private ProgressView progressView;
    private int seekBarX;
    private int seekBarY;

    private int buttonState = 0;
    private int buttonX;
    private int buttonY;
    private boolean buttonPressed = false;

    private boolean avatarPressed = false;

    private StaticLayout timeLayout;
    private int timeX;
    private String lastTimeString = null;

    private int TAG;

    public TLRPC.User audioUser;
    private TLRPC.FileLocation currentPhoto;

    public ChatAudioCell(Context context) {
        super(context);
        TAG = MediaController.getInstance().generateObserverTag();

        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(AndroidUtilities.dp(25));
        seekBar = new SeekBar(context);
        seekBar.delegate = this;
        progressView = new ProgressView();
        avatarDrawable = new AvatarDrawable();

        if (timePaint == null) {
            statesDrawable[0][0] = getResources().getDrawable(R.drawable.play1);
            statesDrawable[0][1] = getResources().getDrawable(R.drawable.play1_pressed);
            statesDrawable[1][0] = getResources().getDrawable(R.drawable.pause1);
            statesDrawable[1][1] = getResources().getDrawable(R.drawable.pause1_pressed);
            statesDrawable[2][0] = getResources().getDrawable(R.drawable.audioload1);
            statesDrawable[2][1] = getResources().getDrawable(R.drawable.audioload1_pressed);
            statesDrawable[3][0] = getResources().getDrawable(R.drawable.audiocancel1);
            statesDrawable[3][1] = getResources().getDrawable(R.drawable.audiocancel1_pressed);

            statesDrawable[4][0] = getResources().getDrawable(R.drawable.play2);
            statesDrawable[4][1] = getResources().getDrawable(R.drawable.play2_pressed);
            statesDrawable[5][0] = getResources().getDrawable(R.drawable.pause2);
            statesDrawable[5][1] = getResources().getDrawable(R.drawable.pause2_pressed);
            statesDrawable[6][0] = getResources().getDrawable(R.drawable.audioload2);
            statesDrawable[6][1] = getResources().getDrawable(R.drawable.audioload2_pressed);
            statesDrawable[7][0] = getResources().getDrawable(R.drawable.audiocancel2);
            statesDrawable[7][1] = getResources().getDrawable(R.drawable.audiocancel2_pressed);

            timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            timePaint.setTextSize(AndroidUtilities.dp(12));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (avatarImage != null) {
            avatarImage.clearImage();
            currentPhoto = null;
        }
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
                    buttonPressed = true;
                    invalidate();
                    result = true;
                } else if (needAvatarImage && avatarImage.isInsideImage(x, y)) {
                    avatarPressed = true;
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
            } else if (avatarPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    avatarPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (delegate != null) {
                        delegate.didPressedUserAvatar(this, audioUser);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    avatarPressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!avatarImage.isInsideImage(x, y)) {
                        avatarPressed = false;
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
        if (!MediaController.getInstance().isPlayingAudio(currentMessageObject)) {
            duration = currentMessageObject.messageOwner.media.audio.duration;
        } else {
            duration = currentMessageObject.audioProgressSec;
        }
        String timeString = String.format("%02d:%02d", duration / 60, duration % 60);
        if (lastTimeString == null || lastTimeString != null && !lastTimeString.equals(timeString)) {
            int timeWidth = (int)Math.ceil(timePaint.measureText(timeString));
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
        String fileName = currentMessageObject.getFileName();
        File cacheFile = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
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
            MediaController.getInstance().addLoadingFileObserver(fileName, this);
            if (!FileLoader.getInstance().isLoadingFile(fileName)) {
                buttonState = 2;
                progressView.setProgress(0);
            } else {
                buttonState = 3;
                Float progress = FileLoader.getInstance().getFileProgress(fileName);
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, AndroidUtilities.dp(68));
        if (isChat) {
            backgroundWidth = Math.min(width - AndroidUtilities.dp(102), AndroidUtilities.dp(300));
        } else {
            backgroundWidth = Math.min(width - AndroidUtilities.dp(50), AndroidUtilities.dp(300));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int x;

        if (currentMessageObject.isOut()) {
            x = layoutWidth - backgroundWidth + AndroidUtilities.dp(8);
            seekBarX = layoutWidth - backgroundWidth + AndroidUtilities.dp(97);
            buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(67);
            timeX = layoutWidth - backgroundWidth + AndroidUtilities.dp(71);
        } else {
            if (isChat) {
                x = AndroidUtilities.dp(69);
                seekBarX = AndroidUtilities.dp(158);
                buttonX = AndroidUtilities.dp(128);
                timeX = AndroidUtilities.dp(132);
            } else {
                x = AndroidUtilities.dp(16);
                seekBarX = AndroidUtilities.dp(106);
                buttonX = AndroidUtilities.dp(76);
                timeX = AndroidUtilities.dp(80);
            }
        }
        int diff = 0;
        if (needAvatarImage) {
            avatarImage.setImageCoords(x, AndroidUtilities.dp(9), AndroidUtilities.dp(50), AndroidUtilities.dp(50));
        } else {
            diff = AndroidUtilities.dp(56);
            seekBarX -= diff;
            buttonX -= diff;
            timeX -= diff;
        }

        seekBar.width = backgroundWidth - AndroidUtilities.dp(112) + diff;
        seekBar.height = AndroidUtilities.dp(30);
        progressView.width = backgroundWidth - AndroidUtilities.dp(136) + diff;
        progressView.height = AndroidUtilities.dp(30);
        seekBarY = AndroidUtilities.dp(13);
        buttonY = AndroidUtilities.dp(10);

        updateProgress();
    }

    @Override
    protected boolean isUserDataChanged() {
        TLRPC.User newUser = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.media.audio.user_id);
        TLRPC.FileLocation newPhoto = null;

        if (avatarImage != null && newUser != null && newUser.photo != null) {
            newPhoto = newUser.photo.photo_small;
        }

        return currentPhoto == null && newPhoto != null || currentPhoto != null && newPhoto == null || currentPhoto != null && newPhoto != null && (currentPhoto.local_id != newPhoto.local_id || currentPhoto.volume_id != newPhoto.volume_id) || super.isUserDataChanged();
    }

    @Override
    public void setMessageObject(MessageObject messageObject) {
        if (currentMessageObject != messageObject || isUserDataChanged()) {
            int uid = messageObject.messageOwner.media.audio.user_id;
            if (uid == 0) {
                uid = messageObject.messageOwner.from_id;
            }
            needAvatarImage = !(messageObject.messageOwner.to_id != null && messageObject.messageOwner.to_id.chat_id != 0 && !messageObject.isOut() && messageObject.messageOwner.media.audio.user_id == messageObject.messageOwner.from_id);
            audioUser = MessagesController.getInstance().getUser(uid);

            if (needAvatarImage) {
                if (audioUser != null) {
                    if (audioUser.photo != null) {
                        currentPhoto = audioUser.photo.photo_small;
                    } else {
                        currentPhoto = null;
                    }
                    avatarDrawable.setInfo(audioUser);
                } else {
                    avatarDrawable.setInfo(uid, null, null, false);
                    currentPhoto = null;
                }
                avatarImage.setImage(currentPhoto, "50_50", avatarDrawable, false);
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

        if (needAvatarImage) {
            avatarImage.draw(canvas);
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
        if (!currentMessageObject.isOut()) {
            state += 4;
            timePaint.setColor(0xffa1aab3);
        } else {
            timePaint.setColor(0xff70b15c);
        }
        Drawable buttonDrawable = statesDrawable[state][buttonPressed ? 1 : 0];
        int side = AndroidUtilities.dp(36);
        int x = (side - buttonDrawable.getIntrinsicWidth()) / 2;
        int y = (side - buttonDrawable.getIntrinsicHeight()) / 2;
        setDrawableBounds(buttonDrawable, x + buttonX, y + buttonY);
        buttonDrawable.draw(canvas);

        canvas.save();
        canvas.translate(timeX, AndroidUtilities.dp(45));
        timeLayout.draw(canvas);
        canvas.restore();
    }
}
