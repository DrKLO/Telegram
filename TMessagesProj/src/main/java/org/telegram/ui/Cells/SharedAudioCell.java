package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.DotDividerSpan;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.FilteredSearchView;

public class SharedAudioCell extends FrameLayout implements DownloadController.FileDownloadProgressListener, NotificationCenter.NotificationCenterDelegate {

    private SpannableStringBuilder dotSpan;
    private CheckBox2 checkBox;

    private boolean needDivider;

    private boolean buttonPressed;
    private boolean miniButtonPressed;
    private int hasMiniProgress;
    private int buttonX;
    private int buttonY;

    private int titleY = AndroidUtilities.dp(9);
    private StaticLayout titleLayout;
    AnimatedEmojiSpan.EmojiGroupedSpans titleLayoutEmojis;

    private int descriptionY = AndroidUtilities.dp(29);
    AnimatedEmojiSpan.EmojiGroupedSpans descriptionLayoutEmojis;
    private StaticLayout descriptionLayout;

    private int captionY = AndroidUtilities.dp(29);
    AnimatedEmojiSpan.EmojiGroupedSpans captionLayoutEmojis;
    private StaticLayout captionLayout;

    private MessageObject currentMessageObject;

    private boolean checkForButtonPress;

    private int currentAccount = UserConfig.selectedAccount;
    private int TAG;
    private int buttonState;
    private int miniButtonState;
    private RadialProgress2 radialProgress;

    private int viewType;

    public final static int VIEW_TYPE_DEFAULT = 0;
    public final static int VIEW_TYPE_GLOBAL_SEARCH = 1;
    private StaticLayout dateLayout;
    private int dateLayoutX;

    private TextPaint description2TextPaint;
    private TextPaint captionTextPaint;
    private final Theme.ResourcesProvider resourcesProvider;

    boolean showReorderIcon;
    float showReorderIconProgress;
    boolean showName = true;
    float showNameProgress = 0;

    public SharedAudioCell(Context context) {
        this(context, VIEW_TYPE_DEFAULT, null);
    }

    public SharedAudioCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, VIEW_TYPE_DEFAULT, resourcesProvider);
    }

    public SharedAudioCell(Context context, int viewType, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.viewType = viewType;
        setFocusable(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        radialProgress = new RadialProgress2(this, resourcesProvider);
        radialProgress.setColors(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);

        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
        setWillNotDraw(false);

        checkBox = new CheckBox2(context, 22, resourcesProvider);
        checkBox.setVisibility(INVISIBLE);
        checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(3);
        addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 38.1f, 32.1f, LocaleController.isRTL ? 6 : 0, 0));

        if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
            description2TextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            description2TextPaint.setTextSize(AndroidUtilities.dp(13));

            dotSpan = new SpannableStringBuilder(".");
            dotSpan.setSpan(new DotDividerSpan(), 0, 1, 0);
        }

        captionTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        captionTextPaint.setTextSize(AndroidUtilities.dp(13));
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        descriptionLayout = null;
        titleLayout = null;
        captionLayout = null;

        int viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        int maxWidth = viewWidth - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - AndroidUtilities.dp(8 + 20);


        int dateWidth = 0;
        if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
            String str = LocaleController.stringForMessageListDate(currentMessageObject.messageOwner.date);
            int width = (int) Math.ceil(description2TextPaint.measureText(str));
            dateLayout = ChatMessageCell.generateStaticLayout(str, description2TextPaint, width, width, 0, 1);
            dateLayoutX = maxWidth - width - AndroidUtilities.dp(8) + AndroidUtilities.dp(20);
            dateWidth = width + AndroidUtilities.dp(12);
        }

        try {
            CharSequence title;
            if (viewType == VIEW_TYPE_GLOBAL_SEARCH && (currentMessageObject.isVoice() || currentMessageObject.isRoundVideo())) {
                title = FilteredSearchView.createFromInfoString(currentMessageObject);
            } else {
                title = currentMessageObject.getMusicTitle().replace('\n', ' ');
            }
            CharSequence titleH = AndroidUtilities.highlightText(title, currentMessageObject.highlightedWords, resourcesProvider);
            if (titleH != null) {
                title = titleH;
            }
            CharSequence titleFinal = TextUtils.ellipsize(title, Theme.chat_contextResult_titleTextPaint, maxWidth - dateWidth, TextUtils.TruncateAt.END);
            titleLayout = new StaticLayout(titleFinal, Theme.chat_contextResult_titleTextPaint, maxWidth + AndroidUtilities.dp(4) - dateWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            titleLayoutEmojis = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, titleLayoutEmojis, titleLayout);
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (currentMessageObject.hasHighlightedWords()) {
            CharSequence caption = Emoji.replaceEmoji(currentMessageObject.messageOwner.message.replace("\n", " ").replaceAll(" +", " ").trim(), Theme.chat_msgTextPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
            CharSequence sequence = AndroidUtilities.highlightText(caption, currentMessageObject.highlightedWords, resourcesProvider);
            if (sequence != null) {
                sequence = TextUtils.ellipsize(AndroidUtilities.ellipsizeCenterEnd(sequence, currentMessageObject.highlightedWords.get(0), maxWidth, captionTextPaint, 130), captionTextPaint, maxWidth, TextUtils.TruncateAt.END);
                captionLayout = new StaticLayout(sequence, captionTextPaint, maxWidth + AndroidUtilities.dp(4), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
            captionLayoutEmojis = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, captionLayoutEmojis, captionLayout);
        }
        try {
            if (viewType == VIEW_TYPE_GLOBAL_SEARCH && (currentMessageObject.isVoice() || currentMessageObject.isRoundVideo())) {
                CharSequence duration = AndroidUtilities.formatDuration(currentMessageObject.getDuration(), false);
                TextPaint paint = viewType == VIEW_TYPE_GLOBAL_SEARCH ? description2TextPaint : Theme.chat_contextResult_descriptionTextPaint;
                duration = TextUtils.ellipsize(duration, paint, maxWidth, TextUtils.TruncateAt.END);
                descriptionLayout = new StaticLayout(duration, paint, maxWidth + AndroidUtilities.dp(4), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            } else {
                CharSequence author = currentMessageObject.getMusicAuthor().replace('\n', ' ');
                CharSequence authorH = AndroidUtilities.highlightText(author, currentMessageObject.highlightedWords, resourcesProvider);
                if (authorH != null) {
                    author = authorH;
                }
                if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
                    author = new SpannableStringBuilder(author).append(' ').append(dotSpan).append(' ').append(FilteredSearchView.createFromInfoString(currentMessageObject));
                }
                TextPaint paint = viewType == VIEW_TYPE_GLOBAL_SEARCH ? description2TextPaint : Theme.chat_contextResult_descriptionTextPaint;
                author = TextUtils.ellipsize(author, paint, maxWidth, TextUtils.TruncateAt.END);
                descriptionLayout = new StaticLayout(author, paint, maxWidth + AndroidUtilities.dp(4), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
            descriptionLayoutEmojis = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, descriptionLayoutEmojis, descriptionLayout);
        } catch (Exception e) {
            FileLog.e(e);
        }

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(56) + (captionLayout == null ? 0 : AndroidUtilities.dp(18)) + (needDivider ? 1 : 0));

        int maxPhotoWidth = AndroidUtilities.dp(52);
        int x = LocaleController.isRTL ? MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(8) - maxPhotoWidth : AndroidUtilities.dp(8);
        radialProgress.setProgressRect(buttonX = x + AndroidUtilities.dp(4), buttonY = AndroidUtilities.dp(6), x + AndroidUtilities.dp(48), AndroidUtilities.dp(50));

        measureChildWithMargins(checkBox, widthMeasureSpec, 0, heightMeasureSpec, 0);

        if (captionLayout != null) {
            captionY = AndroidUtilities.dp(29);
            descriptionY = AndroidUtilities.dp(29) + AndroidUtilities.dp(18);
        } else {
            descriptionY = AndroidUtilities.dp(29);
        }
    }

    public void setMessageObject(MessageObject messageObject, boolean divider) {
        needDivider = divider;
        currentMessageObject = messageObject;
        TLRPC.Document document = messageObject.getDocument();

        TLRPC.PhotoSize thumb = document != null ? FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 360) : null;
        if (thumb instanceof TLRPC.TL_photoSize || thumb instanceof TLRPC.TL_photoSizeProgressive) {
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

    public void setCheckForButtonPress(boolean value) {
        checkForButtonPress = value;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        radialProgress.onAttachedToWindow();
        updateButtonState(false, false);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);

        titleLayoutEmojis = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, titleLayoutEmojis, titleLayout);
        descriptionLayoutEmojis = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, descriptionLayoutEmojis, descriptionLayout);
        captionLayoutEmojis = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, captionLayoutEmojis, captionLayout);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        radialProgress.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);

        AnimatedEmojiSpan.release(this, titleLayoutEmojis);
        AnimatedEmojiSpan.release(this, descriptionLayoutEmojis);
        AnimatedEmojiSpan.release(this, captionLayoutEmojis);
    }

    public MessageObject getMessage() {
        return currentMessageObject;
    }

    public void initStreamingIcons() {
        radialProgress.initMiniIcons();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return onTouchEvent(ev);
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
            } else if (checkForButtonPress && radialProgress.getProgressRect().contains(x, y)) {
                requestDisallowInterceptTouchEvent(true);
                buttonPressed = true;
                radialProgress.setPressed(buttonPressed, false);
                invalidate();
                result = true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (miniButtonPressed) {
                miniButtonPressed = false;
                playSoundEffect(SoundEffectConstants.CLICK);
                didPressedMiniButton(true);
                invalidate();
            } else if (buttonPressed) {
                buttonPressed = false;
                playSoundEffect(SoundEffectConstants.CLICK);
                didPressedButton();
                invalidate();
            }
            requestDisallowInterceptTouchEvent(false);
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            requestDisallowInterceptTouchEvent(false);
            miniButtonPressed = false;
            buttonPressed = false;
            invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (!area && miniButtonPressed) {
                miniButtonPressed = false;
                invalidate();
            }
        }
        radialProgress.setPressed(miniButtonPressed, true);
        return result || buttonPressed;
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
            FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), currentMessageObject, FileLoader.PRIORITY_NORMAL, 0);
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
                currentMessageObject.putInDownloadsStore = true;
                FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), currentMessageObject, FileLoader.PRIORITY_NORMAL, 0);
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
            currentMessageObject.putInDownloadsStore = true;
            FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), currentMessageObject, FileLoader.PRIORITY_NORMAL, 0);
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
            radialProgress.setMiniProgressBackgroundColor(getThemedColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outLoader : Theme.key_chat_inLoader));
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
            } else {
                buttonState = 4;
                Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                if (progress != null) {
                    radialProgress.setProgress(progress, animated);
                } else {
                    radialProgress.setProgress(0, animated);
                }
            }
            radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
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
    public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
        float progress = Math.min(1f, downloadSize / (float) totalSize);
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
    public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {

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
        info.setEnabled(true);
        if (currentMessageObject.isMusic()) {
            info.setText(LocaleController.formatString("AccDescrMusicInfo", R.string.AccDescrMusicInfo, currentMessageObject.getMusicAuthor(), currentMessageObject.getMusicTitle()));
        } else if (titleLayout != null && descriptionLayout != null) {
            info.setText(titleLayout.getText() + ", " + descriptionLayout.getText());
        }
        if (checkBox.isChecked()) {
            info.setCheckable(true);
            info.setChecked(true);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        updateButtonState(false, true);
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    float enterAlpha = 1f;
    FlickerLoadingView globalGradientView;
    public void setGlobalGradientView(FlickerLoadingView globalGradientView) {
        this.globalGradientView = globalGradientView;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (showName && showNameProgress != 1f) {
            showNameProgress += 16 / 150f;
            invalidate();
        } else if (!showName && showNameProgress != 0) {
            showNameProgress -= 16 / 150f;
            invalidate();
        }
        showNameProgress = Utilities.clamp(showNameProgress, 1f, 0);
        if (enterAlpha != 1f && globalGradientView != null) {
            canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), (int) ((1f - enterAlpha) * 255), Canvas.ALL_SAVE_FLAG);
            globalGradientView.setViewType(FlickerLoadingView.AUDIO_TYPE);
            globalGradientView.updateColors();
            globalGradientView.updateGradient();
            globalGradientView.draw(canvas);
            canvas.restore();
            canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), (int) (enterAlpha * 255), Canvas.ALL_SAVE_FLAG);
            drawInternal(canvas);
            super.dispatchDraw(canvas);
            drawReorder(canvas);
            canvas.restore();
        } else {
            drawInternal(canvas);
            drawReorder(canvas);
            super.dispatchDraw(canvas);
        }


    }

    private void drawReorder(Canvas canvas) {
        if (showReorderIcon || showReorderIconProgress != 0) {
            if (showReorderIcon && showReorderIconProgress != 1f) {
                showReorderIconProgress += 16 /150f;
                invalidate();
            } else if (!showReorderIcon && showReorderIconProgress != 0) {
                showReorderIconProgress -= 16 /150f;
                invalidate();
            }
            showReorderIconProgress = Utilities.clamp(showReorderIconProgress, 1f, 0);

            int x = getMeasuredWidth() - AndroidUtilities.dp(12) - Theme.dialogs_reorderDrawable.getIntrinsicWidth();
            int y = (getMeasuredHeight() - Theme.dialogs_reorderDrawable.getIntrinsicHeight()) >> 1;

            canvas.save();
            canvas.scale(showReorderIconProgress, showReorderIconProgress, x + Theme.dialogs_reorderDrawable.getIntrinsicWidth() / 2f, y + Theme.dialogs_reorderDrawable.getIntrinsicHeight() / 2f);
            Theme.dialogs_reorderDrawable.setBounds(x, y, x + Theme.dialogs_reorderDrawable.getIntrinsicWidth(), y + Theme.dialogs_reorderDrawable.getIntrinsicHeight());
            Theme.dialogs_reorderDrawable.draw(canvas);
            canvas.restore();
        }
    }

    private void drawInternal(Canvas canvas) {
        if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
            description2TextPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3));
        }
        if (dateLayout != null) {
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline) + (LocaleController.isRTL ? 0 : dateLayoutX), titleY);
            dateLayout.draw(canvas);
            canvas.restore();
        }

        if (titleLayout != null) {
            int oldAlpha = Theme.chat_contextResult_titleTextPaint.getAlpha();
            if (showNameProgress != 1f) {
                Theme.chat_contextResult_titleTextPaint.setAlpha((int) (oldAlpha * showNameProgress));
            }
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline) + (LocaleController.isRTL && dateLayout != null ? dateLayout.getWidth() + AndroidUtilities.dp(4) : 0), titleY);
            titleLayout.draw(canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, titleLayout, titleLayoutEmojis, 0, null, 0, 0, 0, 1f);
            canvas.restore();
            if (showNameProgress != 1f) {
                Theme.chat_contextResult_titleTextPaint.setAlpha(oldAlpha);
            }
        }

        if (captionLayout != null) {
            captionTextPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), captionY);
            captionLayout.draw(canvas);
            canvas.restore();
        }

        if (descriptionLayout != null) {
            Theme.chat_contextResult_descriptionTextPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            int oldAlpha = Theme.chat_contextResult_descriptionTextPaint.getAlpha();
            if (showNameProgress != 1f) {
                Theme.chat_contextResult_descriptionTextPaint.setAlpha((int) (oldAlpha * showNameProgress));
            }
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), descriptionY);
            descriptionLayout.draw(canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, descriptionLayout, descriptionLayoutEmojis, 0, null, 0, 0, 0, 1f);
            canvas.restore();
            if (showNameProgress != 1f) {
                Theme.chat_contextResult_descriptionTextPaint.setAlpha(oldAlpha);
            }
        }

        radialProgress.setProgressColor(getThemedColor(buttonPressed ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
        radialProgress.setOverlayImageAlpha(showNameProgress);
        radialProgress.draw(canvas);

        if (needDivider) {
            canvas.drawLine(AndroidUtilities.dp(72), getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, Theme.dividerPaint);
        }
    }

    public void setEnterAnimationAlpha(float alpha) {
        if (enterAlpha != alpha) {
            this.enterAlpha = alpha;
            invalidate();
        }
    }


    public void showReorderIcon(boolean show, boolean animated) {
        if (showReorderIcon == show) {
            return;
        }
        showReorderIcon = show;
        if (!animated) {
            showReorderIconProgress = show ? 1f : 0;
        }
        invalidate();
    }

    public void showName(boolean show, boolean animated) {
        if (!animated) {
            showNameProgress = show ? 1f : 0f;
        }
        if (showName == show) {
            return;
        }
        showName = show;
        invalidate();
    }
}

