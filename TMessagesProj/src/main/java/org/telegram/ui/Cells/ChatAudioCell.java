/*
 * This is the source code of Telegram for Android v. 1.3.x.
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
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.ResourceLoader;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.SeekBarWaveform;

import java.io.File;

public class ChatAudioCell extends ChatBaseCell implements SeekBar.SeekBarDelegate {

    public interface ChatAudioCellDelegate {
        boolean needPlayAudio(MessageObject messageObject);
    }

    private static TextPaint timePaint;
    private static Paint circlePaint;

    private boolean hasWaveform;
    private SeekBar seekBar;
    private SeekBarWaveform seekBarWaveform;
    private int seekBarX;
    private int seekBarY;

    private RadialProgress radialProgress;
    private int buttonState = 0;
    private int buttonX;
    private int buttonY;
    private boolean buttonPressed = false;

    private StaticLayout timeLayout;
    private int timeX;
    private int timeWidth2;
    private String lastTimeString = null;

    private ChatAudioCellDelegate audioDelegate;

    public ChatAudioCell(Context context) {
        super(context);

        seekBar = new SeekBar(context);
        seekBar.setDelegate(this);

        seekBarWaveform = new SeekBarWaveform(context);
        seekBarWaveform.setDelegate(this);
        seekBarWaveform.setParentView(this);

        radialProgress = new RadialProgress(this);
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
        updateButtonState(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        boolean result = false;
        if (delegate.canPerformActions()) {
            if (hasWaveform) {
                result = seekBarWaveform.onTouch(event.getAction(), event.getX() - seekBarX - AndroidUtilities.dp(13), event.getY() - seekBarY);
            } else {
                result = seekBar.onTouch(event.getAction(), event.getX() - seekBarX, event.getY() - seekBarY);
            }
            if (result) {
                if (!hasWaveform && event.getAction() == MotionEvent.ACTION_DOWN) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                } else if (hasWaveform && !seekBarWaveform.isStartDraging() && event.getAction() == MotionEvent.ACTION_UP) {
                    didPressedButton();
                }
                invalidate();
            } else {
                int side = AndroidUtilities.dp(36);
                boolean area;
                if (buttonState == 0 || buttonState == 1) {
                    area = x >= buttonX - AndroidUtilities.dp(12) && x <= buttonX - AndroidUtilities.dp(12) + backgroundWidth && y >= namesOffset && y <= getMeasuredHeight();
                } else {
                    area = x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (area) {
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
                        if (!area) {
                            buttonPressed = false;
                            invalidate();
                        }
                    }
                    radialProgress.swapBackground(getDrawableForCurrentState());
                }
                if (result && event.getAction() == MotionEvent.ACTION_DOWN) {
                    startCheckLongPress();
                }
                if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
                    cancelCheckLongPress();
                }
                if (!result) {
                    result = super.onTouchEvent(event);
                }
            }
        }

        return result;
    }

    @Override
    protected void onLongPress() {
        super.onLongPress();
        if (buttonPressed) {
            buttonPressed = false;
            invalidate();
        }
    }

    public void setAudioDelegate(ChatAudioCellDelegate delegate) {
        audioDelegate = delegate;
    }

    private void didPressedButton() {
        if (buttonState == 0) {
            if (audioDelegate.needPlayAudio(currentMessageObject)) {
                buttonState = 1;
                radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                invalidate();
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

    public void updateProgress() {
        if (currentMessageObject == null) {
            return;
        }

        if (hasWaveform) {
            if (!seekBarWaveform.isDragging()) {
                seekBarWaveform.setProgress(currentMessageObject.audioProgress);
            }
        } else {
            if (!seekBar.isDragging()) {
                seekBar.setProgress(currentMessageObject.audioProgress);
            }
        }

        int duration = 0;
        if (!MediaController.getInstance().isPlayingAudio(currentMessageObject)) {
            for (int a = 0; a < currentMessageObject.messageOwner.media.document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = currentMessageObject.messageOwner.media.document.attributes.get(a);
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
            lastTimeString = timeString;
            timeWidth2 = (int)Math.ceil(timePaint.measureText(timeString));
            timeLayout = new StaticLayout(timeString, timePaint, timeWidth2, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
        invalidate();
    }

    public void downloadAudioIfNeed() {
        if (buttonState == 2) {
            FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.document, true, false);
            buttonState = 3;
            radialProgress.setBackground(getDrawableForCurrentState(), false, false);
        }
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
        updateWaveform();
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
        setMeasuredDimension(width, AndroidUtilities.dp(66) + namesOffset);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (currentMessageObject.isOutOwner()) {
            seekBarX = layoutWidth - backgroundWidth + AndroidUtilities.dp(55);
            buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(13);
            timeX = layoutWidth - backgroundWidth + AndroidUtilities.dp(66);
        } else {
            if (isChat && currentMessageObject.isFromUser()) {
                seekBarX = AndroidUtilities.dp(116);
                buttonX = AndroidUtilities.dp(74);
                timeX = AndroidUtilities.dp(127);
            } else {
                seekBarX = AndroidUtilities.dp(64);
                buttonX = AndroidUtilities.dp(22);
                timeX = AndroidUtilities.dp(75);
            }
        }
        seekBarWaveform.width = seekBar.width = backgroundWidth - AndroidUtilities.dp(70);
        seekBarWaveform.height = seekBar.height = AndroidUtilities.dp(30);
        seekBarWaveform.width -= AndroidUtilities.dp(20);
        seekBarY = AndroidUtilities.dp(11) + namesOffset;
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

            int duration = 0;
            for (int a = 0; a < messageObject.messageOwner.media.document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = messageObject.messageOwner.media.document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    duration = attribute.duration;
                    break;
                }
            }

            availableTimeWidth = backgroundWidth - AndroidUtilities.dp(75 + 14) - (int) Math.ceil(timePaint.measureText("00:00"));
            measureTime(messageObject);
            int minSize = AndroidUtilities.dp(40 + 14 + 20 + 90 + 10) + timeWidth;
            backgroundWidth = Math.min(backgroundWidth, minSize + duration * AndroidUtilities.dp(10));

            hasWaveform = false;
            if (messageObject.isOutOwner()) {
                seekBarWaveform.setColors(0xffc3e3ab, 0xff87bf78, 0xffa9d389);
            } else {
                seekBarWaveform.setColors(0xffdee5eb, 0xff4195e5, 0xffaed5e2);
            }
            seekBar.type = messageObject.isOutOwner() ? 0 : 1;

            super.setMessageObject(messageObject);
        }
        updateWaveform();
        updateButtonState(dataChanged);
    }

    @Override
    protected int getMaxNameWidth() {
        return backgroundWidth - AndroidUtilities.dp(24);
    }

    @Override
    public void setCheckPressed(boolean value, boolean pressed) {
        super.setCheckPressed(value, pressed);
        if (radialProgress.swapBackground(getDrawableForCurrentState())) {
            invalidate();
        }
        seekBarWaveform.setSelected(isDrawSelectedBackground());
    }

    @Override
    public void setHighlighted(boolean value) {
        super.setHighlighted(value);
        if (radialProgress.swapBackground(getDrawableForCurrentState())) {
            invalidate();
        }
        seekBarWaveform.setSelected(isDrawSelectedBackground());
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (radialProgress.swapBackground(getDrawableForCurrentState())) {
            invalidate();
        }
        seekBarWaveform.setSelected(isDrawSelectedBackground());
    }

    private Drawable getDrawableForCurrentState() {
        return ResourceLoader.audioStatesDrawable[currentMessageObject.isOutOwner() ? buttonState : buttonState + 5][isDrawSelectedBackground() ? 2 : (buttonPressed ? 1 : 0)];
    }

    private void updateWaveform() {
        File path = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
        for (int a = 0; a < currentMessageObject.messageOwner.media.document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = currentMessageObject.messageOwner.media.document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                if (attribute.waveform == null || attribute.waveform.length == 0) {
                    MediaController.getInstance().generateWaveform(currentMessageObject);
                }
                hasWaveform = attribute.waveform != null;
                seekBarWaveform.setWaveform(attribute.waveform);
                seekBarWaveform.setMessageObject(currentMessageObject);
                break;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentMessageObject == null) {
            return;
        }

        super.onDraw(canvas);

        canvas.save();
        if (hasWaveform) {
            canvas.translate(seekBarX + AndroidUtilities.dp(13), seekBarY);
            seekBarWaveform.draw(canvas);
        } else {
            canvas.translate(seekBarX, seekBarY);
            seekBar.draw(canvas);
        }
        canvas.restore();

        radialProgress.setProgressColor(currentMessageObject.isOutOwner() ? 0xff87bf78 : (isDrawSelectedBackground() ? 0xff83b2c2 : 0xffa2b5c7));
        timePaint.setColor(currentMessageObject.isOutOwner() ? 0xff70b15c : (isDrawSelectedBackground() ? 0xff89b4c1 : 0xffa1aab3));
        circlePaint.setColor(currentMessageObject.isOutOwner() ? 0xff87bf78 : 0xff4195e5);
        radialProgress.draw(canvas);

        canvas.save();
        canvas.translate(timeX, AndroidUtilities.dp(42) + namesOffset);
        timeLayout.draw(canvas);
        canvas.restore();

        if (currentMessageObject.messageOwner.to_id.channel_id == 0 && currentMessageObject.isContentUnread()) {
            canvas.drawCircle(timeX + timeWidth2 + AndroidUtilities.dp(8), AndroidUtilities.dp(49.5f) + namesOffset, AndroidUtilities.dp(3), circlePaint);
        }
    }
}
