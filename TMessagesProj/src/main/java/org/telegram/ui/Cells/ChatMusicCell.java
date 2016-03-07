/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.FileLoader;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.ResourceLoader;
import org.telegram.ui.Components.SeekBar;

import java.io.File;

public class ChatMusicCell extends ChatBaseCell implements SeekBar.SeekBarDelegate {

    public interface ChatMusicCellDelegate {
        boolean needPlayMusic(MessageObject messageObject);
    }

    private static TextPaint timePaint;
    private static TextPaint titlePaint;
    private static TextPaint authorPaint;

    private SeekBar seekBar;
    private int seekBarX;
    private int seekBarY;

    private RadialProgress radialProgress;
    private int buttonState = 0;
    private int buttonX;
    private int buttonY;
    private boolean buttonPressed = false;

    private StaticLayout timeLayout;
    private int timeX;
    private String lastTimeString = null;

    private StaticLayout titleLayout;
    private int titleX;

    private StaticLayout authorLayout;
    private int authorX;

    private ChatMusicCellDelegate musicDelegate;

    public ChatMusicCell(Context context) {
        super(context);

        seekBar = new SeekBar(context);
        seekBar.setDelegate(this);
        radialProgress = new RadialProgress(this);
        drawForwardedName = false;

        if (timePaint == null) {
            timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            timePaint.setTextSize(AndroidUtilities.dp(13));

            titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setTextSize(AndroidUtilities.dp(16));
            titlePaint.setColor(0xff212121);
            titlePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            authorPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            authorPaint.setTextSize(AndroidUtilities.dp(15));
            authorPaint.setColor(0xff212121);
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
        updateButtonState(false);
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
                    radialProgress.swapBackground(getDrawableForCurrentState());
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
                radialProgress.swapBackground(getDrawableForCurrentState());
            }
            if (!result) {
                result = super.onTouchEvent(event);
            }
        }

        return result;
    }

    private void didPressedButton() {
        if (buttonState == 0) {
            if (musicDelegate != null) {
                if (musicDelegate.needPlayMusic(currentMessageObject)) {
                    buttonState = 1;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            }
        } else if (buttonState == 1) {
            boolean result = MediaController.getInstance().pauseAudio(currentMessageObject);
            if (result) {
                buttonState = 0;
                radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                invalidate();
            }
        } else if (buttonState == 2) {
            radialProgress.setProgress(0, false);
            FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.document, true, false);
            buttonState = 3;
            radialProgress.setBackground(getDrawableForCurrentState(), true, false);
            invalidate();
        } else if (buttonState == 3) {
            FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.document);
            buttonState = 2;
            radialProgress.setBackground(getDrawableForCurrentState(), false, false);
            invalidate();
        } else if (buttonState == 4) {
            if (currentMessageObject.isOut() && currentMessageObject.isSending()) {
                if (delegate != null) {
                    delegate.didPressedCancelSendButton(this);
                }
            }
        }
    }

    public void setMusicDelegate(ChatMusicCellDelegate delegate) {
        musicDelegate = delegate;
    }

    public void updateProgress() {
        if (currentMessageObject == null) {
            return;
        }

        if (!seekBar.isDragging()) {
            seekBar.setProgress(currentMessageObject.audioProgress);
        }

        int duration = 0;
        int currentProgress = 0;
        for (int a = 0; a < currentMessageObject.messageOwner.media.document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = currentMessageObject.messageOwner.media.document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                duration = attribute.duration;
                break;
            }
        }
        if (MediaController.getInstance().isPlayingAudio(currentMessageObject)) {
            currentProgress = currentMessageObject.audioProgressSec;
        }
        String timeString = String.format("%d:%02d / %d:%02d", currentProgress / 60, currentProgress % 60, duration / 60, duration % 60);
        if (lastTimeString == null || lastTimeString != null && !lastTimeString.equals(timeString)) {
            lastTimeString = timeString;
            int timeWidth = (int) Math.ceil(timePaint.measureText(timeString));
            timeLayout = new StaticLayout(timeString, timePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
        invalidate();
    }

    public void downloadAudioIfNeed() {
        //if (buttonState == 2) {
            //FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.document, true, false);
        //    buttonState = 3;
        //    invalidate();
        //}
    }

    public void updateButtonState(boolean animated) {
        if (currentMessageObject == null) {
            return;
        }
        if (currentMessageObject.isOut() && currentMessageObject.isSending()) {
            MediaController.getInstance().addLoadingFileObserver(currentMessageObject.messageOwner.attachPath, this);
            buttonState = 4;
            radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
            Float progress = ImageLoader.getInstance().getFileProgress(currentMessageObject.messageOwner.attachPath);
            if (progress == null && SendMessagesHelper.getInstance().isSendingMessage(currentMessageObject.getId())) {
                progress = 1.0f;
            }
            radialProgress.setProgress(progress != null ? progress : 0, false);
        } else {
            File cacheFile = null;
            if (currentMessageObject.messageOwner.attachPath != null && currentMessageObject.messageOwner.attachPath.length() > 0) {
                cacheFile = new File(currentMessageObject.messageOwner.attachPath);
                if (!cacheFile.exists()) {
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
                radialProgress.setProgress(0, animated);
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            } else {
                String fileName = currentMessageObject.getFileName();
                MediaController.getInstance().addLoadingFileObserver(fileName, this);
                if (!FileLoader.getInstance().isLoadingFile(fileName)) {
                    buttonState = 2;
                    radialProgress.setProgress(0, animated);
                    radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                } else {
                    buttonState = 3;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    if (progress != null) {
                        radialProgress.setProgress(progress, animated);
                    } else {
                        radialProgress.setProgress(0, animated);
                    }
                    radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
                }
            }
        }
        updateProgress();
    }

    @Override
    public void onFailedDownload(String fileName) {
        updateButtonState(true);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        updateButtonState(true);
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        radialProgress.setProgress(progress, true);
        if (buttonState != 3) {
            updateButtonState(false);
        }
    }

    @Override
    public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {
        radialProgress.setProgress(progress, true);
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
        setMeasuredDimension(width, AndroidUtilities.dp(78) + namesOffset);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (currentMessageObject.isOutOwner()) {
            seekBarX = layoutWidth - backgroundWidth + AndroidUtilities.dp(52);
            buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(13);
            timeX = layoutWidth - backgroundWidth + AndroidUtilities.dp(63);
        } else {
            if (isChat && currentMessageObject.isFromUser()) {
                seekBarX = AndroidUtilities.dp(113);
                buttonX = AndroidUtilities.dp(74);
                timeX = AndroidUtilities.dp(124);
            } else {
                seekBarX = AndroidUtilities.dp(61);
                buttonX = AndroidUtilities.dp(22);
                timeX = AndroidUtilities.dp(72);
            }
        }

        seekBar.width = backgroundWidth - AndroidUtilities.dp(67);
        seekBar.height = AndroidUtilities.dp(30);
        seekBarY = AndroidUtilities.dp(26) + namesOffset;
        buttonY = AndroidUtilities.dp(13) + namesOffset;
        radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(40), buttonY + AndroidUtilities.dp(40));

        updateProgress();
    }

    @Override
    public void setMessageObject(MessageObject messageObject) {
        boolean dataChanged = currentMessageObject == messageObject && isUserDataChanged();
        if (currentMessageObject != messageObject || dataChanged) {
            if (AndroidUtilities.isTablet()) {
                backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(300));
            } else {
                backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(300));
            }

            if (messageObject.isOutOwner()) {
                seekBar.type = 0;
                radialProgress.setProgressColor(0xff87bf78);
            } else {
                seekBar.type = 1;
                radialProgress.setProgressColor(0xffa2b5c7);
            }

            int maxWidth = backgroundWidth - AndroidUtilities.dp(86);

            CharSequence stringFinal = TextUtils.ellipsize(messageObject.getMusicTitle().replace("\n", " "), titlePaint, maxWidth, TextUtils.TruncateAt.END);
            titleLayout = new StaticLayout(stringFinal, titlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (titleLayout.getLineCount() > 0) {
                titleX = -(int) Math.ceil(titleLayout.getLineLeft(0));
            }

            stringFinal = TextUtils.ellipsize(messageObject.getMusicAuthor().replace("\n", " "), authorPaint, maxWidth, TextUtils.TruncateAt.END);
            authorLayout = new StaticLayout(stringFinal, authorPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (authorLayout.getLineCount() > 0) {
                authorX = -(int) Math.ceil(authorLayout.getLineLeft(0));
            }

            int duration = 0;
            for (int a = 0; a < messageObject.messageOwner.media.document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = messageObject.messageOwner.media.document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    duration = attribute.duration;
                    break;
                }
            }
            availableTimeWidth = backgroundWidth - AndroidUtilities.dp(72 + 14) - (int) Math.ceil(timePaint.measureText(String.format("%d:%02d / %d:%02d", duration / 60, duration % 60, duration / 60, duration % 60)));

            super.setMessageObject(messageObject);
        }
        updateButtonState(dataChanged);
    }

    @Override
    public void setCheckPressed(boolean value, boolean pressed) {
        super.setCheckPressed(value, pressed);
        if (radialProgress.swapBackground(getDrawableForCurrentState())) {
            invalidate();
        }
    }

    @Override
    public void setHighlighted(boolean value) {
        super.setHighlighted(value);
        if (radialProgress.swapBackground(getDrawableForCurrentState())) {
            invalidate();
        }
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (radialProgress.swapBackground(getDrawableForCurrentState())) {
            invalidate();
        }
    }

    private Drawable getDrawableForCurrentState() {
        return ResourceLoader.audioStatesDrawable[currentMessageObject.isOutOwner() ? buttonState : buttonState + 5][isDrawSelectedBackground() ? 2 : (buttonPressed ? 1 : 0)];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (currentMessageObject == null) {
            return;
        }

        if (currentMessageObject.isOutOwner()) {
            timePaint.setColor(0xff70b15c);
        } else {
            timePaint.setColor(isDrawSelectedBackground() ? 0xff89b4c1 : 0xffa1aab3);
        }
        radialProgress.draw(canvas);

        canvas.save();
        canvas.translate(timeX + titleX, AndroidUtilities.dp(12) + namesOffset);
        titleLayout.draw(canvas);
        canvas.restore();

        canvas.save();
        if (MediaController.getInstance().isPlayingAudio(currentMessageObject)) {
            canvas.translate(seekBarX, seekBarY);
            seekBar.draw(canvas);
        } else {
            canvas.translate(timeX + authorX, AndroidUtilities.dp(32) + namesOffset);
            authorLayout.draw(canvas);
        }
        canvas.restore();

        canvas.save();
        canvas.translate(timeX, AndroidUtilities.dp(52) + namesOffset);
        timeLayout.draw(canvas);
        canvas.restore();
    }
}
