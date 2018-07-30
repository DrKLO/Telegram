package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgress;

import java.io.File;

public class SharedAudioCell extends FrameLayout implements DownloadController.FileDownloadProgressListener {

    private CheckBox checkBox;

    private boolean needDivider;

    private boolean buttonPressed;
    private boolean miniButtonPressed;
    private int hasMiniProgress;
    private int buttonX;
    private int buttonY;

    private int titleY = AndroidUtilities.dp(9);
    private StaticLayout titleLayout;

    private int descriptionY = AndroidUtilities.dp(29);
    private StaticLayout descriptionLayout;

    private MessageObject currentMessageObject;

    private int currentAccount = UserConfig.selectedAccount;
    private int TAG;
    private int buttonState;
    private int miniButtonState;
    private RadialProgress radialProgress;

    public SharedAudioCell(Context context) {
        super(context);

        radialProgress = new RadialProgress(this);
        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
        setWillNotDraw(false);

        checkBox = new CheckBox(context, R.drawable.round_check2);
        checkBox.setVisibility(INVISIBLE);
        checkBox.setColor(Theme.getColor(Theme.key_checkbox), Theme.getColor(Theme.key_checkboxCheck));
        addView(checkBox, LayoutHelper.createFrame(20, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 40, 34, LocaleController.isRTL ? 40 : 0, 0));
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

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(56) + (needDivider ? 1 : 0));

        int maxPhotoWidth = AndroidUtilities.dp(52);
        int x = LocaleController.isRTL ? MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(8) - maxPhotoWidth : AndroidUtilities.dp(8);
        radialProgress.setProgressRect(buttonX = x + AndroidUtilities.dp(4), buttonY = AndroidUtilities.dp(6), x + AndroidUtilities.dp(48), AndroidUtilities.dp(50));

        measureChildWithMargins(checkBox, widthMeasureSpec, 0, heightMeasureSpec, 0);
    }

    public void setMessageObject(MessageObject messageObject, boolean divider) {
        needDivider = divider;
        currentMessageObject = messageObject;
        requestLayout();
        updateButtonState(false);
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox.getVisibility() != VISIBLE) {
            checkBox.setVisibility(VISIBLE);
        }
        checkBox.setChecked(checked, animated);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
    }

    public MessageObject getMessage() {
        return currentMessageObject;
    }

    private boolean checkAudioMotionEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        boolean result = false;
        int side = AndroidUtilities.dp(36);
        boolean area = false;
        if (miniButtonState >= 0) {
            int offset = AndroidUtilities.dp(27);
            area = x >= buttonX + offset && x <= buttonX + offset + side && y >= buttonY + offset && y <= buttonY + offset + side;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (area) {
                miniButtonPressed = true;
                invalidate();
                result = true;
                updateRadialProgressBackground();
            }
        } else if (miniButtonPressed) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                miniButtonPressed = false;
                playSoundEffect(SoundEffectConstants.CLICK);
                didPressedMiniButton(true);
                invalidate();
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                miniButtonPressed = false;
                invalidate();
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (!area) {
                    miniButtonPressed = false;
                    invalidate();
                }
            }
            updateRadialProgressBackground();
        }
        return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentMessageObject == null) {
            return super.onTouchEvent(event);
        }
        boolean result = checkAudioMotionEvent(event);
        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            miniButtonPressed = false;
            buttonPressed = false;
            result = false;
        }
        return result;
    }

    private void updateRadialProgressBackground() {
        radialProgress.swapBackground(getDrawableForCurrentState());
        if (hasMiniProgress != 0) {
            radialProgress.swapMiniBackground(getMiniDrawableForCurrentState());
        }
    }

    private void didPressedMiniButton(boolean animated) {
        if (miniButtonState == 0) {
            miniButtonState = 1;
            radialProgress.setProgress(0, false);
            FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), true, 0);
            radialProgress.setMiniBackground(getMiniDrawableForCurrentState(), true, false);
            invalidate();
        } else if (miniButtonState == 1) {
            if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
                MediaController.getInstance().cleanupPlayer(true, true);
            }
            miniButtonState = 0;
            FileLoader.getInstance(currentAccount).cancelLoadFile(currentMessageObject.getDocument());
            radialProgress.setMiniBackground(getMiniDrawableForCurrentState(), true, false);
            invalidate();
        }
    }

    public void didPressedButton() {
        if (buttonState == 0) {
            if (miniButtonState == 0) {
                FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), true, 0);
            }
            if (needPlayMessage(currentMessageObject)) {
                if (hasMiniProgress == 2 && miniButtonState != 1) {
                    miniButtonState = 1;
                    radialProgress.setProgress(0, false);
                    radialProgress.setMiniBackground(getMiniDrawableForCurrentState(), true, false);
                }
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
            FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), true, 0);
            buttonState = 4;
            radialProgress.setBackground(getDrawableForCurrentState(), true, false);
            invalidate();
        } else if (buttonState == 4) {
            FileLoader.getInstance(currentAccount).cancelLoadFile(currentMessageObject.getDocument());
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

        if (needDivider) {
            canvas.drawLine(AndroidUtilities.dp(72), getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, Theme.dividerPaint);
        }
    }

    private Drawable getMiniDrawableForCurrentState() {
        if (miniButtonState < 0) {
            return null;
        }
        radialProgress.setAlphaForPrevious(false);
        return Theme.chat_fileMiniStatesDrawable[miniButtonState + 2][miniButtonPressed ? 1 : 0];
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
        boolean fileExists;
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
        fileExists = cacheFile.exists();
        if (SharedConfig.streamMedia && currentMessageObject.isMusic() && (int) currentMessageObject.getDialogId() != 0) {
            hasMiniProgress = fileExists ? 1 : 2;
            fileExists = true;
        } else {
            miniButtonState = -1;
        }
        if (hasMiniProgress != 0) {
            radialProgress.setMiniProgressBackgroundColor(Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outLoader : Theme.key_chat_inLoader));
            boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
            if (!playing || playing && MediaController.getInstance().isMessagePaused()) {
                buttonState = 0;
            } else {
                buttonState = 1;
            }
            radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            if (hasMiniProgress == 1) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                miniButtonState = -1;
            } else {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this);
                if (!(FileLoader.getInstance(currentAccount).isLoadingFile(fileName))) {
                    radialProgress.setProgress(0, animated);
                    miniButtonState = 0;
                } else {
                    miniButtonState = 1;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    if (progress != null) {
                        radialProgress.setProgress(progress, animated);
                    } else {
                        radialProgress.setProgress(0, animated);
                    }
                }
            }
            radialProgress.setMiniBackground(getMiniDrawableForCurrentState(), miniButtonState == 1, animated);
        } else if (fileExists) {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
            if (!playing || playing && MediaController.getInstance().isMessagePaused()) {
                buttonState = 0;
            } else {
                buttonState = 1;
            }
            radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            invalidate();
        } else {
            DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, this);
            boolean isLoading = FileLoader.getInstance(currentAccount).isLoadingFile(fileName);
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
        if (hasMiniProgress != 0) {
            if (miniButtonState != 1) {
                updateButtonState(false);
            }
        } else {
            if (buttonState != 4) {
                updateButtonState(false);
            }
        }
    }

    @Override
    public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    protected boolean needPlayMessage(MessageObject messageObject) {
        return false;
    }
}

