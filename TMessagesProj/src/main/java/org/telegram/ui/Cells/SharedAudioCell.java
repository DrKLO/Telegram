package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.accessibility.AccessibilityNodeInfo;
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
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;

public class SharedAudioCell extends FrameLayout implements DownloadController.FileDownloadProgressListener {

    private CheckBox2 checkBox;

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
    private RadialProgress2 radialProgress;

    public SharedAudioCell(Context context) {
        super(context);
        setFocusable(true);

        radialProgress = new RadialProgress2(this);
        radialProgress.setColors(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);

        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
        setWillNotDraw(false);

        checkBox = new CheckBox2(context, 21);
        checkBox.setVisibility(INVISIBLE);
        checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(3);
        addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 38, 32, LocaleController.isRTL ? 39 : 0, 0));
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
        TLRPC.Document document = messageObject.getDocument();

        TLRPC.PhotoSize thumb = document != null ? FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90) : null;
        if (thumb instanceof TLRPC.TL_photoSize) {
            radialProgress.setImageOverlay(thumb, document, messageObject);
        } else {
            String artworkUrl = messageObject.getArtworkUrl(true);
            if (!TextUtils.isEmpty(artworkUrl)) {
                radialProgress.setImageOverlay(artworkUrl);
            } else {
                radialProgress.setImageOverlay(null, null, null);
            }
        }
        updateButtonState(false, false);
        requestLayout();
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox.getVisibility() != VISIBLE) {
            checkBox.setVisibility(VISIBLE);
        }
        checkBox.setChecked(checked, animated);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        radialProgress.onAttachedToWindow();
        updateButtonState(false, false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        radialProgress.onDetachedFromWindow();
    }

    public MessageObject getMessage() {
        return currentMessageObject;
    }

    public void initStreamingIcons() {
        radialProgress.initMiniIcons();
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
                radialProgress.setPressed(miniButtonPressed, true);
                invalidate();
                result = true;
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
            radialProgress.setPressed(miniButtonPressed, true);
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
            radialProgress.setPressed(buttonPressed, false);
            radialProgress.setPressed(miniButtonPressed, true);
        }

        return result;
    }

    private void didPressedMiniButton(boolean animated) {
        if (miniButtonState == 0) {
            miniButtonState = 1;
            radialProgress.setProgress(0, false);
            FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), currentMessageObject, 1, 0);
            radialProgress.setMiniIcon(getMiniIconForCurrentState(), false, true);
            invalidate();
        } else if (miniButtonState == 1) {
            if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
                MediaController.getInstance().cleanupPlayer(true, true);
            }
            miniButtonState = 0;
            FileLoader.getInstance(currentAccount).cancelLoadFile(currentMessageObject.getDocument());
            radialProgress.setMiniIcon(getMiniIconForCurrentState(), false, true);
            invalidate();
        }
    }

    public void didPressedButton() {
        if (buttonState == 0) {
            if (miniButtonState == 0) {
                FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), currentMessageObject, 1, 0);
            }
            if (needPlayMessage(currentMessageObject)) {
                if (hasMiniProgress == 2 && miniButtonState != 1) {
                    miniButtonState = 1;
                    radialProgress.setProgress(0, false);
                    radialProgress.setMiniIcon(getMiniIconForCurrentState(), false, true);
                }
                buttonState = 1;
                radialProgress.setIcon(getIconForCurrentState(), false, true);
                invalidate();
            }
        } else if (buttonState == 1) {
            boolean result = MediaController.getInstance().pauseMessage(currentMessageObject);
            if (result) {
                buttonState = 0;
                radialProgress.setIcon(getIconForCurrentState(), false, true);
                invalidate();
            }
        } else if (buttonState == 2) {
            radialProgress.setProgress(0, false);
            FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), currentMessageObject, 1, 0);
            buttonState = 4;
            radialProgress.setIcon(getIconForCurrentState(), false, true);
            invalidate();
        } else if (buttonState == 4) {
            FileLoader.getInstance(currentAccount).cancelLoadFile(currentMessageObject.getDocument());
            buttonState = 2;
            radialProgress.setIcon(getIconForCurrentState(), false, true);
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

    private int getMiniIconForCurrentState() {
        if (miniButtonState < 0) {
            return MediaActionDrawable.ICON_NONE;
        }
        if (miniButtonState == 0) {
            return MediaActionDrawable.ICON_DOWNLOAD;
        } else {
            return MediaActionDrawable.ICON_CANCEL;
        }
    }

    private int getIconForCurrentState() {
        if (buttonState == 1) {
            return MediaActionDrawable.ICON_PAUSE;
        } else if (buttonState == 2) {
            return MediaActionDrawable.ICON_DOWNLOAD;
        } else if (buttonState == 4) {
            return MediaActionDrawable.ICON_CANCEL;
        }
        return MediaActionDrawable.ICON_PLAY;
    }

    public void updateButtonState(boolean ifSame, boolean animated) {
        String fileName = currentMessageObject.getFileName();
        if (TextUtils.isEmpty(fileName)) {
            return;
        }
        boolean fileExists = currentMessageObject.attachPathExists || currentMessageObject.mediaExists;
        if (SharedConfig.streamMedia && currentMessageObject.isMusic() && (int) currentMessageObject.getDialogId() != 0) {
            hasMiniProgress = fileExists ? 1 : 2;
            fileExists = true;
        } else {
            hasMiniProgress = 0;
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
            radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
            if (hasMiniProgress == 1) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                miniButtonState = -1;
                radialProgress.setMiniIcon(getMiniIconForCurrentState(), ifSame, animated);
            } else {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this);
                if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                    miniButtonState = 0;
                    radialProgress.setMiniIcon(getMiniIconForCurrentState(), ifSame, animated);
                } else {
                    miniButtonState = 1;
                    radialProgress.setMiniIcon(getMiniIconForCurrentState(), ifSame, animated);
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    if (progress != null) {
                        radialProgress.setProgress(progress, animated);
                    } else {
                        radialProgress.setProgress(0, animated);
                    }
                }
            }
        } else if (fileExists) {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
            if (!playing || playing && MediaController.getInstance().isMessagePaused()) {
                buttonState = 0;
            } else {
                buttonState = 1;
            }
            radialProgress.setProgress(1, animated);
            radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
            invalidate();
        } else {
            DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this);
            boolean isLoading = FileLoader.getInstance(currentAccount).isLoadingFile(fileName);
            if (!isLoading) {
                buttonState = 2;
                radialProgress.setProgress(0, animated);
                radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
            } else {
                buttonState = 4;
                Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                if (progress != null) {
                    radialProgress.setProgress(progress, animated);
                } else {
                    radialProgress.setProgress(0, animated);
                }
                radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
            }
            invalidate();
        }
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {
        updateButtonState(true, canceled);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        radialProgress.setProgress(1, true);
        updateButtonState(false, true);
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        radialProgress.setProgress(progress, true);
        if (hasMiniProgress != 0) {
            if (miniButtonState != 1) {
                updateButtonState(false, true);
            }
        } else {
            if (buttonState != 4) {
                updateButtonState(false, true);
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

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (currentMessageObject.isMusic()) {
            info.setText(LocaleController.formatString("AccDescrMusicInfo", R.string.AccDescrMusicInfo, currentMessageObject.getMusicAuthor(), currentMessageObject.getMusicTitle()));
        } else { // voice message
            info.setText(titleLayout.getText() + ", " + descriptionLayout.getText());
        }
        if (checkBox.isChecked()) {
            info.setCheckable(true);
            info.setChecked(true);
        }
    }
}

