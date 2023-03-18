/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

public class ChatActionCell extends BaseCell implements DownloadController.FileDownloadProgressListener, NotificationCenter.NotificationCenterDelegate {
    private final static boolean USE_PREMIUM_GIFT_LOCAL_STICKER = false;
    private final static boolean USE_PREMIUM_GIFT_MONTHS_AS_EMOJI_NUMBERS = false;

    private static Map<Integer, String> monthsToEmoticon = new HashMap<>();

    static {
        monthsToEmoticon.put(1, 1 + "\u20E3");
        monthsToEmoticon.put(3, 2 + "\u20E3");
        monthsToEmoticon.put(6, 3 + "\u20E3");
        monthsToEmoticon.put(12, 4 + "\u20E3");
        monthsToEmoticon.put(24, 5 + "\u20E3");
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.startSpoilers) {
            setSpoilersSuppressed(false);
        } else if (id == NotificationCenter.stopSpoilers) {
            setSpoilersSuppressed(true);
        } else if (id == NotificationCenter.didUpdatePremiumGiftStickers) {
            MessageObject messageObject = currentMessageObject;
            if (messageObject != null) {
                setMessageObject(messageObject, true);
            }
        } else if (id == NotificationCenter.diceStickersDidLoad) {
            if (Objects.equals(args[0], UserConfig.getInstance(currentAccount).premiumGiftsStickerPack)) {
                MessageObject messageObject = currentMessageObject;
                if (messageObject != null) {
                    setMessageObject(messageObject, true);
                }
            }
        }
    }

    public void setSpoilersSuppressed(boolean s) {
        for (SpoilerEffect eff : spoilers) {
            eff.setSuppressUpdates(s);
        }
    }

    private boolean canDrawInParent;
    private View invalidateWithParent;

    public void setInvalidateWithParent(View viewToInvalidate) {
        invalidateWithParent = viewToInvalidate;
    }

    public interface ChatActionCellDelegate {
        default void didClickImage(ChatActionCell cell) {
        }

        default void didOpenPremiumGift(ChatActionCell cell, TLRPC.TL_premiumGiftOption giftOption, boolean animateConfetti) {
        }

        default boolean didLongPress(ChatActionCell cell, float x, float y) {
            return false;
        }

        default void needOpenUserProfile(long uid) {
        }

        default void didPressBotButton(MessageObject messageObject, TLRPC.KeyboardButton button) {
        }

        default void didPressReplyMessage(ChatActionCell cell, int id) {
        }

        default void needOpenInviteLink(TLRPC.TL_chatInviteExported invite) {
        }

        default void needShowEffectOverlay(ChatActionCell cell, TLRPC.Document document, TLRPC.VideoSize videoSize) {
        }

        default BaseFragment getBaseFragment() {
            return null;
        }

        default long getDialogId() {
            return 0;
        }

        default int getTopicId() {
            return 0;
        }

        default boolean canDrawOutboundsContent() {
            return true;
        }
    }

    public interface ThemeDelegate extends Theme.ResourcesProvider {

        int getCurrentColor();
    }

    private int TAG;

    private URLSpan pressedLink;
    private int currentAccount = UserConfig.selectedAccount;
    private ImageReceiver imageReceiver;
    private AvatarDrawable avatarDrawable;
    private StaticLayout textLayout;
    private int textWidth;
    private int textHeight;
    private int textX;
    private int textY;
    private int textXLeft;
    private int previousWidth;
    private boolean imagePressed;
    private boolean giftButtonPressed;
    RadialProgressView progressView;
    float progressToProgress;

    private RectF giftButtonRect = new RectF();

    public List<SpoilerEffect> spoilers = new ArrayList<>();
    private Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private AnimatedEmojiSpan.EmojiGroupedSpans animatedEmojiStack;

    TextPaint textPaint;

    private float viewTop;
    private int backgroundHeight;
    private boolean visiblePartSet;

    private ImageLocation currentVideoLocation;

    private float lastTouchX;
    private float lastTouchY;

    private boolean wasLayout;

    private boolean hasReplyMessage;

    private MessageObject currentMessageObject;
    private int customDate;
    private CharSequence customText;

    private String overrideBackground;
    private String overrideText;
    private Paint overrideBackgroundPaint;
    private TextPaint overrideTextPaint;
    private int overrideColor;
    private ArrayList<Integer> lineWidths = new ArrayList<>();
    private ArrayList<Integer> lineHeights = new ArrayList<>();
    private Path backgroundPath = new Path();
    private RectF rect = new RectF();
    private boolean invalidatePath = true;
    private boolean invalidateColors = false;

    private ChatActionCellDelegate delegate;
    private ThemeDelegate themeDelegate;

    private int stickerSize;
    private int giftRectSize;
    private StaticLayout giftPremiumTitleLayout;
    private StaticLayout giftPremiumSubtitleLayout;
    private StaticLayout giftPremiumButtonLayout;
    private float giftPremiumButtonWidth;

    private TextPaint giftTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint giftSubtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private TLRPC.Document giftSticker;
    private TLRPC.VideoSize giftEffectAnimation;
    private RadialProgress2 radialProgress = new RadialProgress2(this);
    private int giftPremiumAdditionalHeight;
    private boolean forceWasUnread;
    private RectF backroundRect;
    private ImageReceiver.ImageReceiverDelegate giftStickerDelegate = (imageReceiver1, set, thumb, memCache) -> {
        if (set) {
            RLottieDrawable drawable = imageReceiver.getLottieAnimation();
            if (drawable != null) {
                MessageObject messageObject = currentMessageObject;
                if (messageObject != null && !messageObject.playedGiftAnimation) {
                    messageObject.playedGiftAnimation = true;
                    drawable.setCurrentFrame(0, false);
                    AndroidUtilities.runOnUIThread(drawable::start);

                    if (messageObject != null && messageObject.wasUnread || forceWasUnread) {
                        forceWasUnread = messageObject.wasUnread = false;

                        try {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignored) {
                        }

                        if (getContext() instanceof LaunchActivity) {
                            ((LaunchActivity) getContext()).getFireworksOverlay().start();
                        }

                        if (giftEffectAnimation != null && delegate != null) {
                            delegate.needShowEffectOverlay(ChatActionCell.this, giftSticker, giftEffectAnimation);
                        }
                    }
                } else {
                    drawable.stop();
                    drawable.setCurrentFrame(drawable.getFramesCount() - 1, false);
                }
            }
        }
    };

    private View rippleView;

    private Path starsPath = new Path();
    private StarParticlesView.Drawable starParticlesDrawable;
    private int starsSize;

    public ChatActionCell(Context context) {
        this(context, false, null);
    }

    public ChatActionCell(Context context, boolean canDrawInParent, ThemeDelegate themeDelegate) {
        super(context);
        this.canDrawInParent = canDrawInParent;
        this.themeDelegate = themeDelegate;
        imageReceiver = new ImageReceiver(this);
        imageReceiver.setRoundRadius(AndroidUtilities.roundMessageSize / 2);
        avatarDrawable = new AvatarDrawable();
        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();

        giftTitlePaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()));
        giftSubtitlePaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 15, getResources().getDisplayMetrics()));

        rippleView = new View(context);
        rippleView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP, AndroidUtilities.dp(16)));
        rippleView.setVisibility(GONE);
        addView(rippleView);

        starParticlesDrawable = new StarParticlesView.Drawable(10);
        starParticlesDrawable.type = 100;
        starParticlesDrawable.isCircle = false;
        starParticlesDrawable.roundEffect = true;
        starParticlesDrawable.useRotate = false;
        starParticlesDrawable.useBlur = true;
        starParticlesDrawable.checkBounds = true;
        starParticlesDrawable.size1 = 1;
        starParticlesDrawable.k1 = starParticlesDrawable.k2 = starParticlesDrawable.k3 = 0.98f;
        starParticlesDrawable.paused = false;
        starParticlesDrawable.speedScale = 0f;
        starParticlesDrawable.minLifeTime = 750;
        starParticlesDrawable.randLifeTime = 750;
        starParticlesDrawable.init();
    }

    public void setDelegate(ChatActionCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setCustomDate(int date, boolean scheduled, boolean inLayout) {
        if (customDate == date || customDate / 3600 == date / 3600) {
            return;
        }
        CharSequence newText;
        if (scheduled) {
            if (date == 0x7ffffffe) {
                newText = LocaleController.getString("MessageScheduledUntilOnline", R.string.MessageScheduledUntilOnline);
            } else {
                newText = LocaleController.formatString("MessageScheduledOn", R.string.MessageScheduledOn, LocaleController.formatDateChat(date));
            }
        } else {
            newText = LocaleController.formatDateChat(date);
        }
        customDate = date;
        if (customText != null && TextUtils.equals(newText, customText)) {
            return;
        }
        customText = newText;
        accessibilityText = null;
        updateTextInternal(inLayout);
    }

    private void updateTextInternal(boolean inLayout) {
        if (getMeasuredWidth() != 0) {
            createLayout(customText, getMeasuredWidth());
            invalidate();
        }
        if (!wasLayout) {
            if (inLayout) {
                AndroidUtilities.runOnUIThread(this::requestLayout);
            } else {
                requestLayout();
            }
        } else {
            buildLayout();
        }
    }

    public void setCustomText(CharSequence text) {
        customText = text;
        if (customText != null) {
            updateTextInternal(false);
        }
    }

    public void setOverrideColor(String background, String text) {
        overrideBackground = background;
        overrideText = text;
    }

    public void setMessageObject(MessageObject messageObject) {
        setMessageObject(messageObject, false);
    }

    public void setMessageObject(MessageObject messageObject, boolean force) {
        if (currentMessageObject == messageObject && (textLayout == null || TextUtils.equals(textLayout.getText(), messageObject.messageText)) && (hasReplyMessage || messageObject.replyMessageObject == null) && !force && messageObject.type != MessageObject.TYPE_SUGGEST_PHOTO) {
            return;
        }
        if (BuildVars.DEBUG_PRIVATE_VERSION && Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
            FileLog.e(new IllegalStateException("Wrong thread!!!"));
        }
        accessibilityText = null;
        boolean messageIdChanged = currentMessageObject == null || currentMessageObject.stableId != messageObject.stableId;
        currentMessageObject = messageObject;
        hasReplyMessage = messageObject.replyMessageObject != null;
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        previousWidth = 0;
        imageReceiver.setAutoRepeatCount(0);
        if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
            imageReceiver.setRoundRadius((int) (stickerSize / 2f));
            imageReceiver.setAllowStartLottieAnimation(true);
            imageReceiver.setDelegate(null);
            TLRPC.TL_messageActionSuggestProfilePhoto action = (TLRPC.TL_messageActionSuggestProfilePhoto) messageObject.messageOwner.action;

            TLRPC.VideoSize videoSize = FileLoader.getClosestVideoSizeWithSize(action.photo.video_sizes, 1000);
            ImageLocation videoLocation;
            if (action.photo.video_sizes != null && !action.photo.video_sizes.isEmpty()) {
                videoLocation = ImageLocation.getForPhoto(videoSize, action.photo);
            } else {
                videoLocation = null;
            }
            TLRPC.Photo photo = messageObject.messageOwner.action.photo;
            TLRPC.PhotoSize strippedPhotoSize = null;
            if (messageObject.strippedThumb == null) {
                for (int a = 0, N = messageObject.photoThumbs.size(); a < N; a++) {
                    TLRPC.PhotoSize photoSize = messageObject.photoThumbs.get(a);
                    if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                        strippedPhotoSize = photoSize;
                        break;
                    }
                }
            }
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 1000);
            if (photoSize != null) {
                if (videoSize != null) {
                    imageReceiver.setImage(videoLocation, ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForPhoto(photoSize, photo), "150_150", ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", messageObject.strippedThumb, 0, null, messageObject, 0);
                } else {
                    imageReceiver.setImage(ImageLocation.getForPhoto(photoSize, photo), "150_150", ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", messageObject.strippedThumb, 0, null, messageObject, 0);
                }
            }

            imageReceiver.setAllowStartLottieAnimation(false);
            ImageUpdater imageUpdater = MessagesController.getInstance(currentAccount).photoSuggestion.get(messageObject.messageOwner.local_id);
            if (imageUpdater == null || imageUpdater.getCurrentImageProgress() == 1f) {
                radialProgress.setProgress(1f, !messageIdChanged);
                radialProgress.setIcon(MediaActionDrawable.ICON_NONE, !messageIdChanged, !messageIdChanged);
            } else {
                radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, !messageIdChanged, !messageIdChanged);
            }
        } else if (messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
            imageReceiver.setRoundRadius(0);

            if (USE_PREMIUM_GIFT_LOCAL_STICKER) {
                forceWasUnread = messageObject.wasUnread;
                imageReceiver.setAllowStartLottieAnimation(false);
                imageReceiver.setDelegate(giftStickerDelegate);
                imageReceiver.setImageBitmap(new RLottieDrawable(R.raw.premium_gift, messageObject.getId() + "_" + R.raw.premium_gift, AndroidUtilities.dp(160), AndroidUtilities.dp(160)));
            } else {
                TLRPC.TL_messages_stickerSet set;
                TLRPC.Document document = null;

                String packName = UserConfig.getInstance(currentAccount).premiumGiftsStickerPack;
                if (packName == null) {
                    MediaDataController.getInstance(currentAccount).checkPremiumGiftStickers();
                    return;
                }
                set = MediaDataController.getInstance(currentAccount).getStickerSetByName(packName);
                if (set == null) {
                    set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(packName);
                }
                if (set != null) {
                    int months = messageObject.messageOwner.action.months;

                    String monthsEmoticon;
                    if (USE_PREMIUM_GIFT_MONTHS_AS_EMOJI_NUMBERS) {
                        StringBuilder monthsEmoticonBuilder = new StringBuilder();
                        while (months > 0) {
                            monthsEmoticonBuilder.insert(0, (months % 10) + "\u20E3");
                            months /= 10;
                        }
                        monthsEmoticon = monthsEmoticonBuilder.toString();
                    } else {
                        monthsEmoticon = monthsToEmoticon.get(months);
                    }
                    for (TLRPC.TL_stickerPack pack : set.packs) {
                        if (Objects.equals(pack.emoticon, monthsEmoticon)) {
                            for (long id : pack.documents) {
                                for (TLRPC.Document doc : set.documents) {
                                    if (doc.id == id) {
                                        document = doc;
                                        break;
                                    }
                                }
                                if (document != null) {
                                    break;
                                }
                            }
                        }
                        if (document != null) {
                            break;
                        }
                    }

                    if (document == null && !set.documents.isEmpty()) {
                        document = set.documents.get(0);
                    }
                }

                forceWasUnread = messageObject.wasUnread;
                giftSticker = document;
                if (document != null) {
                    imageReceiver.setAllowStartLottieAnimation(false);
                    imageReceiver.setDelegate(giftStickerDelegate);

                    giftEffectAnimation = null;
                    for (int i = 0; i < document.video_thumbs.size(); i++) {
                        if ("f".equals(document.video_thumbs.get(i).type)) {
                            giftEffectAnimation = document.video_thumbs.get(i);
                            break;
                        }
                    }

                    SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document.thumbs, Theme.key_emptyListPlaceholder, 0.2f);
                    if (svgThumb != null) {
                        svgThumb.overrideWidthAndHeight(512, 512);
                    }

                    imageReceiver.setAutoRepeat(0);
                    imageReceiver.setImage(ImageLocation.getForDocument(document), messageObject.getId() + "_130_130", svgThumb, "tgs", set, 1);
                } else {
                    MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(packName, false, set == null);
                }
            }
        } else if (messageObject.type == MessageObject.TYPE_ACTION_PHOTO) {
            imageReceiver.setAllowStartLottieAnimation(true);
            imageReceiver.setDelegate(null);
            imageReceiver.setRoundRadius(AndroidUtilities.roundMessageSize / 2);
            imageReceiver.setAutoRepeatCount(1);
            long id = messageObject.getDialogId();
            avatarDrawable.setInfo(id, null, null);
            if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                imageReceiver.setImage(null, null, avatarDrawable, null, messageObject, 0);
            } else {
                TLRPC.PhotoSize strippedPhotoSize = null;
                if (messageObject.strippedThumb == null) {
                    for (int a = 0, N = messageObject.photoThumbs.size(); a < N; a++) {
                        TLRPC.PhotoSize photoSize = messageObject.photoThumbs.get(a);
                        if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                            strippedPhotoSize = photoSize;
                            break;
                        }
                    }
                }
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 640);
                if (photoSize != null) {
                    TLRPC.Photo photo = messageObject.messageOwner.action.photo;
                    TLRPC.VideoSize videoSize = null;
                    if (!photo.video_sizes.isEmpty() && SharedConfig.isAutoplayGifs()) {
                        videoSize = FileLoader.getClosestVideoSizeWithSize(photo.video_sizes, 1000);
                        if (!messageObject.mediaExists && !DownloadController.getInstance(currentAccount).canDownloadMedia(DownloadController.AUTODOWNLOAD_TYPE_VIDEO, videoSize.size)) {
                            currentVideoLocation = ImageLocation.getForPhoto(videoSize, photo);
                            String fileName = FileLoader.getAttachFileName(videoSize);
                            DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, messageObject, this);
                            videoSize = null;
                        }
                    }
                    if (videoSize != null) {
                        imageReceiver.setImage(ImageLocation.getForPhoto(videoSize, photo), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", messageObject.strippedThumb, 0, null, messageObject, 1);
                    } else {
                        imageReceiver.setImage(ImageLocation.getForObject(photoSize, messageObject.photoThumbsObject), "150_150", ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", messageObject.strippedThumb, 0, null, messageObject, 1);
                    }
                } else {
                    imageReceiver.setImageBitmap(avatarDrawable);
                }
            }
            imageReceiver.setVisible(!PhotoViewer.isShowingImage(messageObject), false);
        } else {
            imageReceiver.setAllowStartLottieAnimation(true);
            imageReceiver.setDelegate(null);
            imageReceiver.setImageBitmap((Bitmap) null);
        }
        rippleView.setVisibility(isButtonLayout(messageObject) ? VISIBLE : GONE);
        ForumUtilities.applyTopicToMessage(messageObject);
        requestLayout();
    }

    public MessageObject getMessageObject() {
        return currentMessageObject;
    }

    public ImageReceiver getPhotoImage() {
        return imageReceiver;
    }

    public void setVisiblePart(float visibleTop, int parentH) {
        visiblePartSet = true;
        backgroundHeight = parentH;
        viewTop = visibleTop;
    }

    @Override
    protected boolean onLongPress() {
        if (delegate != null) {
            return delegate.didLongPress(this, lastTouchX, lastTouchY);
        }
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        rippleView.layout((int) giftButtonRect.left, (int) giftButtonRect.top, (int) giftButtonRect.right, (int) giftButtonRect.bottom);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        imageReceiver.onDetachedFromWindow();
        setStarsPaused(true);
        wasLayout = false;
        AnimatedEmojiSpan.release(this, animatedEmojiStack);

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdatePremiumGiftStickers);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
        setStarsPaused(false);

        animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, canDrawInParent && (delegate != null && !delegate.canDrawOutboundsContent()), animatedEmojiStack, textLayout);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdatePremiumGiftStickers);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad);

        if (currentMessageObject != null && currentMessageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
            setMessageObject(currentMessageObject, true);
        }
    }

    private void setStarsPaused(boolean paused) {
        if (paused == starParticlesDrawable.paused) {
            return;
        }
        starParticlesDrawable.paused = paused;
        if (paused) {
            starParticlesDrawable.pausedTime = System.currentTimeMillis();
        } else {
            for (int i = 0; i < starParticlesDrawable.particles.size(); i++) {
                starParticlesDrawable.particles.get(i).lifeTime += System.currentTimeMillis() - starParticlesDrawable.pausedTime;
            }
            invalidate();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        MessageObject messageObject = currentMessageObject;
        if (messageObject == null) {
            return super.onTouchEvent(event);
        }
        float x = lastTouchX = event.getX();
        float y = lastTouchY = event.getY();

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (delegate != null) {
                if ((messageObject.type == MessageObject.TYPE_ACTION_PHOTO || isButtonLayout(messageObject)) && imageReceiver.isInsideImage(x, y)) {
                    imagePressed = true;
                    result = true;
                }
                if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO && backroundRect.contains(x, y)) {
                    imagePressed = true;
                    result = true;
                }
                if (isButtonLayout(messageObject) && giftButtonRect.contains(x, y)) {
                    rippleView.setPressed(giftButtonPressed = true);
                    result = true;
                }
                if (result) {
                    startCheckLongPress();
                }
            }
        } else {
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }
            if (imagePressed) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        imagePressed = false;
                        if (messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
                            openPremiumGiftPreview();
                        } else if (delegate != null) {
                            ImageUpdater imageUpdater = MessagesController.getInstance(currentAccount).photoSuggestion.get(messageObject.messageOwner.local_id);
                            if (imageUpdater != null) {
                                imageUpdater.cancel();
                            } else {
                                delegate.didClickImage(this);
                                playSoundEffect(SoundEffectConstants.CLICK);
                            }
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        imagePressed = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
                            if (!backroundRect.contains(x, y)) {
                                imagePressed = false;
                            }
                        } else {
                            if (!imageReceiver.isInsideImage(x, y)) {
                                imagePressed = false;
                            }
                        }
                        break;
                }
            } else if (giftButtonPressed) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        rippleView.setPressed(giftButtonPressed = false);
                        if (delegate != null) {
                            if (messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
                                playSoundEffect(SoundEffectConstants.CLICK);
                                openPremiumGiftPreview();
                            } else {
                                ImageUpdater imageUpdater = MessagesController.getInstance(currentAccount).photoSuggestion.get(messageObject.messageOwner.local_id);
                                if (imageUpdater == null) {
                                    delegate.didClickImage(this);
                                }
                            }
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        rippleView.setPressed(giftButtonPressed = false);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (!giftButtonRect.contains(x, y)) {
                            rippleView.setPressed(giftButtonPressed = false);
                        }
                        break;
                }
            }
        }
        if (!result) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || pressedLink != null && event.getAction() == MotionEvent.ACTION_UP) {
                if (textLayout != null && x >= textX && y >= textY && x <= textX + textWidth && y <= textY + textHeight) {
                    y -= textY;
                    x -= textXLeft;

                    final int line = textLayout.getLineForVertical((int) y);
                    final int off = textLayout.getOffsetForHorizontal(line, x);
                    final float left = textLayout.getLineLeft(line);
                    if (left <= x && left + textLayout.getLineWidth(line) >= x && messageObject.messageText instanceof Spannable) {
                        Spannable buffer = (Spannable) messageObject.messageText;
                        URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);

                        if (link.length != 0) {
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                pressedLink = link[0];
                                result = true;
                            } else {
                                if (link[0] == pressedLink) {
                                    openLink(pressedLink);
                                    result = true;
                                }
                            }
                        } else {
                            pressedLink = null;
                        }
                    } else {
                        pressedLink = null;
                    }
                } else {
                    pressedLink = null;
                }
            }
        }

        if (!result) {
            result = super.onTouchEvent(event);
        }

        return result;
    }

    private void openPremiumGiftPreview() {
        TLRPC.TL_premiumGiftOption giftOption = new TLRPC.TL_premiumGiftOption();
        TLRPC.MessageAction action = currentMessageObject.messageOwner.action;
        giftOption.amount = action.amount;
        giftOption.months = action.months;
        giftOption.currency = action.currency;

        if (delegate != null) {
            AndroidUtilities.runOnUIThread(() -> delegate.didOpenPremiumGift(ChatActionCell.this, giftOption, false));
        }
    }

    private void openLink(CharacterStyle link) {
        if (delegate != null && link instanceof URLSpan) {
            String url = ((URLSpan) link).getURL();
            if (url.startsWith("topic") && pressedLink instanceof URLSpanNoUnderline) {
                URLSpanNoUnderline spanNoUnderline = (URLSpanNoUnderline) pressedLink;
                TLObject object = spanNoUnderline.getObject();
                if (object instanceof TLRPC.TL_forumTopic) {
                    TLRPC.TL_forumTopic forumTopic = (TLRPC.TL_forumTopic) object;
                    ForumUtilities.openTopic(delegate.getBaseFragment(), -delegate.getDialogId(), forumTopic, 0);
                }
            } else if (url.startsWith("invite") && pressedLink instanceof URLSpanNoUnderline) {
                URLSpanNoUnderline spanNoUnderline = (URLSpanNoUnderline) pressedLink;
                TLObject object = spanNoUnderline.getObject();
                if (object instanceof TLRPC.TL_chatInviteExported) {
                    TLRPC.TL_chatInviteExported invite = (TLRPC.TL_chatInviteExported) object;
                    delegate.needOpenInviteLink(invite);
                }
            } else if (url.startsWith("game")) {
                delegate.didPressReplyMessage(this, currentMessageObject.getReplyMsgId());
                /*TLRPC.KeyboardButton gameButton = null;
                MessageObject messageObject = currentMessageObject.replyMessageObject;
                if (messageObject != null && messageObject.messageOwner.reply_markup != null) {
                    for (int a = 0; a < messageObject.messageOwner.reply_markup.rows.size(); a++) {
                        TLRPC.TL_keyboardButtonRow row = messageObject.messageOwner.reply_markup.rows.get(a);
                        for (int b = 0; b < row.buttons.size(); b++) {
                            TLRPC.KeyboardButton button = row.buttons.get(b);
                            if (button instanceof TLRPC.TL_keyboardButtonGame && button.game_id == currentMessageObject.messageOwner.action.game_id) {
                                gameButton = button;
                                break;
                            }
                        }
                        if (gameButton != null) {
                            break;
                        }
                    }
                }
                if (gameButton != null) {
                    delegate.didPressBotButton(messageObject, gameButton);
                }*/
            } else if (url.startsWith("http")) {
                Browser.openUrl(getContext(), url);
            } else {
                delegate.needOpenUserProfile(Long.parseLong(url));
            }
        }
    }

    private void createLayout(CharSequence text, int width) {
        int maxWidth = width - AndroidUtilities.dp(30);
        if (maxWidth < 0) {
            return;
        }
        invalidatePath = true;
        TextPaint paint;
        if (currentMessageObject != null && currentMessageObject.drawServiceWithDefaultTypeface) {
            paint = (TextPaint) getThemedPaint(Theme.key_paint_chatActionText2);
        } else {
            paint = (TextPaint) getThemedPaint(Theme.key_paint_chatActionText);
        }
        paint.linkColor = paint.getColor();
        textLayout = new StaticLayout(text, paint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

        spoilersPool.addAll(spoilers);
        spoilers.clear();
        if (text instanceof Spannable) {
            SpoilerEffect.addSpoilers(this, textLayout, (Spannable) text, spoilersPool, spoilers);
        }
        animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, canDrawInParent && (delegate != null && !delegate.canDrawOutboundsContent()), animatedEmojiStack, textLayout);

        textHeight = 0;
        textWidth = 0;
        try {
            int linesCount = textLayout.getLineCount();
            for (int a = 0; a < linesCount; a++) {
                float lineWidth;
                try {
                    lineWidth = textLayout.getLineWidth(a);
                    if (lineWidth > maxWidth) {
                        lineWidth = maxWidth;
                    }
                    textHeight = (int) Math.max(textHeight, Math.ceil(textLayout.getLineBottom(a)));
                } catch (Exception e) {
                    FileLog.e(e);
                    return;
                }
                textWidth = (int) Math.max(textWidth, Math.ceil(lineWidth));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        textX = (width - textWidth) / 2;
        textY = AndroidUtilities.dp(7);
        textXLeft = (width - textLayout.getWidth()) / 2;
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        MessageObject messageObject = currentMessageObject;
        if (messageObject == null && customText == null) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), textHeight + AndroidUtilities.dp(14));
            return;
        }
        if (isButtonLayout(messageObject)) {
            giftRectSize = Math.min((int) (AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() * 0.6f : AndroidUtilities.displaySize.x * 0.6f), AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(64));
            stickerSize = giftRectSize - AndroidUtilities.dp(106);
            if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
                imageReceiver.setRoundRadius(stickerSize / 2);
            } else {
                imageReceiver.setRoundRadius(0);
            }
        }
        int width = Math.max(AndroidUtilities.dp(30), MeasureSpec.getSize(widthMeasureSpec));
        if (previousWidth != width) {
            wasLayout = true;
            previousWidth = width;
            buildLayout();
        }
        int additionalHeight = 0;
        if (messageObject != null) {
            if (messageObject.type == MessageObject.TYPE_ACTION_PHOTO) {
                additionalHeight = AndroidUtilities.roundMessageSize + AndroidUtilities.dp(10);
            } else if (isButtonLayout(messageObject)) {
                additionalHeight = giftRectSize + AndroidUtilities.dp(12);
            }
        }

        if (isButtonLayout(messageObject)) {
            int imageSize = stickerSize;
            if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
                imageSize = (int) (stickerSize * 0.7f);
            }
            float y = textY + textHeight + giftRectSize * 0.075f + imageSize + AndroidUtilities.dp(4) + AndroidUtilities.dp(4) + giftPremiumSubtitleLayout.getHeight();
            giftPremiumAdditionalHeight = 0;
            if (giftPremiumTitleLayout != null) {
                y += giftPremiumTitleLayout.getHeight();
            } else {
                y -= AndroidUtilities.dp(12);
                giftPremiumAdditionalHeight -= AndroidUtilities.dp(30);
            }
            if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
                y += AndroidUtilities.dp(16);
            }

            if (giftPremiumSubtitleLayout.getLineCount() > 2) {
                giftPremiumAdditionalHeight += (giftPremiumSubtitleLayout.getLineBottom(0) - giftPremiumSubtitleLayout.getLineTop(0)) * giftPremiumSubtitleLayout.getLineCount() - 2;
            }
            additionalHeight += giftPremiumAdditionalHeight;

            int h = textHeight + additionalHeight + AndroidUtilities.dp(14);

            y += (h - y - giftPremiumButtonLayout.getHeight() - AndroidUtilities.dp(8)) / 2f;
            float rectX = (previousWidth - giftPremiumButtonWidth) / 2f;
            giftButtonRect.set(rectX - AndroidUtilities.dp(18), y - AndroidUtilities.dp(8), rectX + giftPremiumButtonWidth + AndroidUtilities.dp(18), y + giftPremiumButtonLayout.getHeight() + AndroidUtilities.dp(8));

            int sizeInternal = getMeasuredWidth() << 16 + getMeasuredHeight();
            starParticlesDrawable.rect.set(giftButtonRect);
            starParticlesDrawable.rect2.set(giftButtonRect);
            if (starsSize != sizeInternal) {
                starsSize = sizeInternal;
                starParticlesDrawable.resetPositions();
            }


        }

        setMeasuredDimension(width, textHeight + additionalHeight + AndroidUtilities.dp(14));

    }

    private void buildLayout() {
        CharSequence text = null;
        MessageObject messageObject = currentMessageObject;
        if (messageObject != null) {
            if (delegate.getTopicId() == 0 && MessageObject.isTopicActionMessage(messageObject)) {
                TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(-messageObject.getDialogId(), MessageObject.getTopicId(messageObject.messageOwner, true));
                text = ForumUtilities.createActionTextWithTopic(topic, messageObject);
            }
            if (text == null) {
                if (messageObject.messageOwner != null && messageObject.messageOwner.media != null && messageObject.messageOwner.media.ttl_seconds != 0) {
                    if (messageObject.messageOwner.media.photo instanceof TLRPC.TL_photoEmpty) {
                        text = LocaleController.getString("AttachPhotoExpired", R.string.AttachPhotoExpired);
                    } else if (messageObject.messageOwner.media.document instanceof TLRPC.TL_documentEmpty) {
                        text = LocaleController.getString("AttachVideoExpired", R.string.AttachVideoExpired);
                    } else {
                        text = AnimatedEmojiSpan.cloneSpans(messageObject.messageText);
                    }
                } else {
                    text = AnimatedEmojiSpan.cloneSpans(messageObject.messageText);
                }
            }
        } else {
            text = customText;
        }
        createLayout(text, previousWidth);
        if (messageObject != null) {
            if (messageObject.type == MessageObject.TYPE_ACTION_PHOTO) {
                imageReceiver.setImageCoords((previousWidth - AndroidUtilities.roundMessageSize) / 2f, textHeight + AndroidUtilities.dp(19), AndroidUtilities.roundMessageSize, AndroidUtilities.roundMessageSize);
            } else if (messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
                createGiftPremiumLayouts(LocaleController.getString(R.string.ActionGiftPremiumTitle), LocaleController.formatString(R.string.ActionGiftPremiumSubtitle, LocaleController.formatPluralString("Months", messageObject.messageOwner.action.months)), LocaleController.getString(R.string.ActionGiftPremiumView), giftRectSize);
            } else if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
                TLRPC.TL_messageActionSuggestProfilePhoto actionSuggestProfilePhoto = (TLRPC.TL_messageActionSuggestProfilePhoto) messageObject.messageOwner.action;
                String description;
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageObject.isOutOwner() ? 0 : messageObject.getDialogId());
                boolean isVideo = actionSuggestProfilePhoto.video || (actionSuggestProfilePhoto.photo != null && actionSuggestProfilePhoto.photo.video_sizes != null && !actionSuggestProfilePhoto.photo.video_sizes.isEmpty());
                if (user.id == UserConfig.getInstance(currentAccount).clientUserId) {
                    TLRPC.User user2 = MessagesController.getInstance(currentAccount).getUser(messageObject.getDialogId());
                    if (isVideo) {
                        description = LocaleController.formatString("ActionSuggestVideoFromYouDescription", R.string.ActionSuggestVideoFromYouDescription, user2.first_name);
                    } else {
                        description = LocaleController.formatString("ActionSuggestPhotoFromYouDescription", R.string.ActionSuggestPhotoFromYouDescription, user2.first_name);
                    }
                } else {
                    if (isVideo) {
                        description = LocaleController.formatString("ActionSuggestVideoToYouDescription", R.string.ActionSuggestVideoToYouDescription, user.first_name);
                    } else {
                        description = LocaleController.formatString("ActionSuggestPhotoToYouDescription", R.string.ActionSuggestPhotoToYouDescription, user.first_name);
                    }
                }
                String action;
                if (actionSuggestProfilePhoto.video || (actionSuggestProfilePhoto.photo.video_sizes != null && !actionSuggestProfilePhoto.photo.video_sizes.isEmpty())) {
                    action = LocaleController.getString("ViewVideoAction", R.string.ViewVideoAction);
                } else {
                    action = LocaleController.getString("ViewPhotoAction", R.string.ViewPhotoAction);
                }
                createGiftPremiumLayouts(null, description, action, giftRectSize);
                textLayout = null;
                textHeight = 0;
                textY = 0;
            }
        }
    }

    private void createGiftPremiumLayouts(CharSequence title, CharSequence subtitle, CharSequence button, int width) {
        width -= AndroidUtilities.dp(16);
        if (title != null) {
            SpannableStringBuilder titleBuilder = SpannableStringBuilder.valueOf(title);
            titleBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)), 0, titleBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            giftPremiumTitleLayout = new StaticLayout(titleBuilder, giftTitlePaint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
        } else {
            giftPremiumTitleLayout = null;
        }
        if (currentMessageObject != null && currentMessageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
            giftSubtitlePaint.setTextSize(AndroidUtilities.dp(13));
        } else {
            giftSubtitlePaint.setTextSize(AndroidUtilities.dp(15));
        }
        giftPremiumSubtitleLayout = new StaticLayout(subtitle, giftSubtitlePaint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
        SpannableStringBuilder buttonBuilder = SpannableStringBuilder.valueOf(button);
        buttonBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)), 0, buttonBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        giftPremiumButtonLayout = new StaticLayout(buttonBuilder, (TextPaint) getThemedPaint(Theme.key_paint_chatActionText), width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
        giftPremiumButtonWidth = measureLayoutWidth(giftPremiumButtonLayout);
    }

    private float measureLayoutWidth(Layout layout) {
        float maxWidth = 0;
        for (int i = 0; i < layout.getLineCount(); i++) {
            int lineWidth = (int) Math.ceil(layout.getLineWidth(i));
            if (lineWidth > maxWidth) {
                maxWidth = lineWidth;
            }
        }
        return maxWidth;
    }

    public int getCustomDate() {
        return customDate;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        MessageObject messageObject = currentMessageObject;
        int imageSize = stickerSize;
        if (isButtonLayout(messageObject)) {
            stickerSize = giftRectSize - AndroidUtilities.dp(106);
            if (messageObject.type == MessageObject.TYPE_ACTION_PHOTO) {
                imageReceiver.setImageCoords((previousWidth - stickerSize) / 2f, textY + textHeight + giftRectSize * 0.075f, stickerSize, stickerSize);
            } else {
                imageSize = (int) (stickerSize * 0.7f);
                imageReceiver.setImageCoords((previousWidth - imageSize) / 2f, textY + textHeight + giftRectSize * 0.075f + AndroidUtilities.dp(8), imageSize, imageSize);
            }
            if (textPaint != null && giftTitlePaint != null && giftSubtitlePaint != null) {
                if (giftTitlePaint.getColor() != textPaint.getColor()) {
                    giftTitlePaint.setColor(textPaint.getColor());
                }
                if (giftSubtitlePaint.getColor() != textPaint.getColor()) {
                    giftSubtitlePaint.setColor(textPaint.getColor());
                }
            }
        }

        drawBackground(canvas, false);

        if (isButtonLayout(messageObject) || (messageObject != null && messageObject.type == MessageObject.TYPE_ACTION_PHOTO)) {
            imageReceiver.draw(canvas);
            radialProgress.setProgressRect(
                    imageReceiver.getImageX(),
                    imageReceiver.getImageY(),
                    imageReceiver.getImageX() + imageReceiver.getImageWidth(),
                    imageReceiver.getImageY() + imageReceiver.getImageHeight()
            );
            if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
                ImageUpdater imageUpdater = MessagesController.getInstance(currentAccount).photoSuggestion.get(messageObject.messageOwner.local_id);
                if (imageUpdater != null) {
                    radialProgress.setProgress(imageUpdater.getCurrentImageProgress(), true);
                    radialProgress.setCircleRadius((int) (imageReceiver.getImageWidth() * 0.5f) + 1);
                    radialProgress.setMaxIconSize(AndroidUtilities.dp(24));
                    radialProgress.setColors(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
                    if (imageUpdater.getCurrentImageProgress() == 1f) {
                        radialProgress.setIcon(MediaActionDrawable.ICON_NONE, true, true);
                    } else {
                        radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, true, true);
                    }
                }
                radialProgress.draw(canvas);
            }
        }

        if (textPaint != null && textLayout != null) {
            canvas.save();
            canvas.translate(textXLeft, textY);
            if (textLayout.getPaint() != textPaint) {
                buildLayout();
            }
            canvas.save();
            SpoilerEffect.clipOutCanvas(canvas, spoilers);
            textLayout.draw(canvas);
            if (delegate == null || delegate.canDrawOutboundsContent()) {
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, textLayout, animatedEmojiStack, 0, spoilers, 0, 0, 0, 1f);
            }
            canvas.restore();

            for (SpoilerEffect eff : spoilers) {
                eff.setColor(textLayout.getPaint().getColor());
                eff.draw(canvas);
            }

            canvas.restore();
        }

        if (isButtonLayout(messageObject)) {
            canvas.save();
            float x = (previousWidth - giftRectSize) / 2f + AndroidUtilities.dp(8), y = textY + textHeight + giftRectSize * 0.075f + (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO ? imageSize : stickerSize) + AndroidUtilities.dp(4);
            if (messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO) {
                y += +AndroidUtilities.dp(16);
            }
            canvas.translate(x, y);
            if (giftPremiumTitleLayout != null) {
                giftPremiumTitleLayout.draw(canvas);
                y += giftPremiumTitleLayout.getHeight();
            } else {
                y -= AndroidUtilities.dp(4);
            }
            canvas.restore();

            y += AndroidUtilities.dp(4);
            canvas.save();
            canvas.translate(x, y);
            giftPremiumSubtitleLayout.draw(canvas);
            canvas.restore();

            if (giftPremiumTitleLayout == null) {
                y -= AndroidUtilities.dp(8);
            }

            y += giftPremiumSubtitleLayout.getHeight();
            y += (getHeight() - y - giftPremiumButtonLayout.getHeight() - AndroidUtilities.dp(8)) / 2f;

            if (themeDelegate != null) {
                themeDelegate.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, 0, viewTop + AndroidUtilities.dp(4));
            } else {
                Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, 0, viewTop + AndroidUtilities.dp(4));
            }
            Paint backgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackground);
            canvas.drawRoundRect(giftButtonRect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), backgroundPaint);

            if (hasGradientService()) {
                canvas.drawRoundRect(giftButtonRect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), Theme.chat_actionBackgroundGradientDarkenPaint);
            }

            starsPath.rewind();
            starsPath.addRoundRect(giftButtonRect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), Path.Direction.CW);
            canvas.save();
            canvas.clipPath(starsPath);
            if (getMessageObject().type != MessageObject.TYPE_SUGGEST_PHOTO) {
                starParticlesDrawable.onDraw(canvas);
                if (!starParticlesDrawable.paused) {
                    invalidate();
                }
            } else {
                //TODO optimize
                invalidate();
            }
            canvas.restore();

            if (messageObject.settingAvatar && progressToProgress != 1f) {
                progressToProgress += 16 / 150f;
            } else if (!messageObject.settingAvatar && progressToProgress != 0) {
                progressToProgress -= 16 / 150f;
            }
            progressToProgress = Utilities.clamp(progressToProgress, 1f, 0f);
            if (progressToProgress != 0) {
                if (progressView == null) {
                    progressView = new RadialProgressView(getContext());
                }
                int rad = AndroidUtilities.dp(16);
                canvas.save();
                canvas.scale(progressToProgress, progressToProgress, giftButtonRect.centerX(), giftButtonRect.centerY());
                progressView.setSize(rad);
            progressView.setProgressColor(Theme.getColor(Theme.key_chat_serviceText));
                progressView.draw(canvas, giftButtonRect.centerX(), giftButtonRect.centerY());
                canvas.restore();
            }
            if (progressToProgress != 1f){
                canvas.save();
                float s = 1f - progressToProgress;
                canvas.scale(s, s, giftButtonRect.centerX(), giftButtonRect.centerY());
                canvas.translate(x, y);
                giftPremiumButtonLayout.draw(canvas);
                canvas.restore();
            }
        }
    }

    public void drawBackground(Canvas canvas, boolean fromParent) {
        if (canDrawInParent) {
            if (hasGradientService() && !fromParent) {
                return;
            }
            if (!hasGradientService() && fromParent) {
                return;
            }
        }
        Paint backgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackground);
        textPaint = (TextPaint) getThemedPaint(Theme.key_paint_chatActionText);
        if (overrideBackground != null) {
            int color = getThemedColor(overrideBackground);
            if (overrideBackgroundPaint == null) {
                overrideBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                overrideBackgroundPaint.setColor(color);
                overrideTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                overrideTextPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                overrideTextPaint.setTextSize(AndroidUtilities.dp(Math.max(16, SharedConfig.fontSize) - 2));
                overrideTextPaint.setColor(getThemedColor(overrideText));
            }
            backgroundPaint = overrideBackgroundPaint;
            textPaint = overrideTextPaint;
        }
        if (invalidatePath) {
            invalidatePath = false;
            lineWidths.clear();
            final int count = textLayout == null ? 0 : textLayout.getLineCount();
            final int corner = AndroidUtilities.dp(11);
            final int cornerIn = AndroidUtilities.dp(8);

            int prevLineWidth = 0;
            for (int a = 0; a < count; a++) {
                int lineWidth = (int) Math.ceil(textLayout.getLineWidth(a));
                if (a != 0) {
                    int diff = prevLineWidth - lineWidth;
                    if (diff > 0 && diff <= corner + cornerIn) {
                        lineWidth = prevLineWidth;
                    }
                }
                lineWidths.add(lineWidth);
                prevLineWidth = lineWidth;
            }
            for (int a = count - 2; a >= 0; a--) {
                int lineWidth = lineWidths.get(a);
                int diff = prevLineWidth - lineWidth;
                if (diff > 0 && diff <= corner + cornerIn) {
                    lineWidth = prevLineWidth;
                }
                lineWidths.set(a, lineWidth);
                prevLineWidth = lineWidth;
            }

            int y = AndroidUtilities.dp(4);
            int x = getMeasuredWidth() / 2;
            int previousLineBottom = 0;

            final int cornerOffset = AndroidUtilities.dp(3);
            final int cornerInSmall = AndroidUtilities.dp(6);
            final int cornerRest = corner - cornerOffset;

            lineHeights.clear();
            backgroundPath.reset();
            backgroundPath.moveTo(x, y);

            for (int a = 0; a < count; a++) {
                int lineWidth = lineWidths.get(a);
                int lineBottom = textLayout.getLineBottom(a);
                int nextLineWidth = a < count - 1 ? lineWidths.get(a + 1) : 0;

                int height = lineBottom - previousLineBottom;
                if (a == 0 || lineWidth > prevLineWidth) {
                    height += AndroidUtilities.dp(3);
                }
                if (a == count - 1 || lineWidth > nextLineWidth) {
                    height += AndroidUtilities.dp(3);
                }

                previousLineBottom = lineBottom;

                float startX = x + lineWidth / 2.0f;

                int innerCornerRad;
                if (a != count - 1 && lineWidth < nextLineWidth && a != 0 && lineWidth < prevLineWidth) {
                    innerCornerRad = cornerInSmall;
                } else {
                    innerCornerRad = cornerIn;
                }

                if (a == 0 || lineWidth > prevLineWidth) {
                    rect.set(startX - cornerOffset - corner, y, startX + cornerRest, y + corner * 2);
                    backgroundPath.arcTo(rect, -90, 90);
                } else if (lineWidth < prevLineWidth) {
                    rect.set(startX + cornerRest, y, startX + cornerRest + innerCornerRad * 2, y + innerCornerRad * 2);
                    backgroundPath.arcTo(rect, -90, -90);
                }
                y += height;
                int yOffset = y;
                if (a != count - 1 && lineWidth < nextLineWidth) {
                    y -= AndroidUtilities.dp(3);
                    height -= AndroidUtilities.dp(3);
                }
                if (a != 0 && lineWidth < prevLineWidth) {
                    y -= AndroidUtilities.dp(3);
                    height -= AndroidUtilities.dp(3);
                }
                lineHeights.add(height);

                if (a == count - 1 || lineWidth > nextLineWidth) {
                    rect.set(startX - cornerOffset - corner, y - corner * 2, startX + cornerRest, y);
                    backgroundPath.arcTo(rect, 0, 90);
                } else if (lineWidth < nextLineWidth) {
                    rect.set(startX + cornerRest, y - innerCornerRad * 2, startX + cornerRest + innerCornerRad * 2, y);
                    backgroundPath.arcTo(rect, 180, -90);
                }

                prevLineWidth = lineWidth;
            }
            for (int a = count - 1; a >= 0; a--) {
                prevLineWidth = a != 0 ? lineWidths.get(a - 1) : 0;
                int lineWidth = lineWidths.get(a);
                int nextLineWidth = a != count - 1 ? lineWidths.get(a + 1) : 0;
                int lineBottom = textLayout.getLineBottom(a);
                float startX = x - lineWidth / 2;

                int innerCornerRad;
                if (a != count - 1 && lineWidth < nextLineWidth && a != 0 && lineWidth < prevLineWidth) {
                    innerCornerRad = cornerInSmall;
                } else {
                    innerCornerRad = cornerIn;
                }

                if (a == count - 1 || lineWidth > nextLineWidth) {
                    rect.set(startX - cornerRest, y - corner * 2, startX + cornerOffset + corner, y);
                    backgroundPath.arcTo(rect, 90, 90);
                } else if (lineWidth < nextLineWidth) {
                    rect.set(startX - cornerRest - innerCornerRad * 2, y - innerCornerRad * 2, startX - cornerRest, y);
                    backgroundPath.arcTo(rect, 90, -90);
                }

                y -= lineHeights.get(a);

                if (a == 0 || lineWidth > prevLineWidth) {
                    rect.set(startX - cornerRest, y, startX + cornerOffset + corner, y + corner * 2);
                    backgroundPath.arcTo(rect, 180, 90);
                } else if (lineWidth < prevLineWidth) {
                    rect.set(startX - cornerRest - innerCornerRad * 2, y, startX - cornerRest, y + innerCornerRad * 2);
                    backgroundPath.arcTo(rect, 0, -90);
                }
            }
            backgroundPath.close();
        }
        if (!visiblePartSet) {
            ViewGroup parent = (ViewGroup) getParent();
            backgroundHeight = parent.getMeasuredHeight();
        }
        if (themeDelegate != null) {
            themeDelegate.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, 0, viewTop + AndroidUtilities.dp(4));
        } else {
            Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, 0, viewTop + AndroidUtilities.dp(4));
        }

        int oldAlpha = -1;
        int oldAlpha2 = -1;
        if (fromParent && getAlpha() != 1f) {
            oldAlpha = backgroundPaint.getAlpha();
            oldAlpha2 = Theme.chat_actionBackgroundGradientDarkenPaint.getAlpha();
            backgroundPaint.setAlpha((int) (oldAlpha * getAlpha()));
            Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha((int) (oldAlpha2 * getAlpha()));
        }
        canvas.drawPath(backgroundPath, backgroundPaint);
        if (hasGradientService()) {
            canvas.drawPath(backgroundPath, Theme.chat_actionBackgroundGradientDarkenPaint);
        }

        MessageObject messageObject = currentMessageObject;
        if (isButtonLayout(messageObject)) {
            float x = (getWidth() - giftRectSize) / 2f, y = textY + textHeight + AndroidUtilities.dp(12);
            AndroidUtilities.rectTmp.set(x, y, x + giftRectSize, y + giftRectSize + giftPremiumAdditionalHeight);
            if (backroundRect == null) {
                backroundRect = new RectF();
            }
            backroundRect.set(AndroidUtilities.rectTmp);
            canvas.drawRoundRect(backroundRect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), backgroundPaint);

            if (hasGradientService()) {
                canvas.drawRoundRect(backroundRect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), Theme.chat_actionBackgroundGradientDarkenPaint);
            }
        }

        if (oldAlpha >= 0) {
            backgroundPaint.setAlpha(oldAlpha);
            Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha(oldAlpha2);
        }
    }

    public boolean hasGradientService() {
        return overrideBackgroundPaint == null && (themeDelegate != null ? themeDelegate.hasGradientService() : Theme.hasGradientService());
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {

    }

    @Override
    public void onSuccessDownload(String fileName) {
        MessageObject messageObject = currentMessageObject;
        if (messageObject != null && messageObject.type == MessageObject.TYPE_ACTION_PHOTO) {
            TLRPC.PhotoSize strippedPhotoSize = null;
            for (int a = 0, N = messageObject.photoThumbs.size(); a < N; a++) {
                TLRPC.PhotoSize photoSize = messageObject.photoThumbs.get(a);
                if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                    strippedPhotoSize = photoSize;
                    break;
                }
            }
            imageReceiver.setImage(currentVideoLocation, ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", avatarDrawable, 0, null, messageObject, 1);
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        }
    }

    @Override
    public void onProgressDownload(String fileName, long downloadSize, long totalSize) {

    }

    @Override
    public void onProgressUpload(String fileName, long downloadSize, long totalSize, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    private SpannableStringBuilder accessibilityText;

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        MessageObject messageObject = currentMessageObject;
        if (TextUtils.isEmpty(customText) && messageObject == null) {
            return;
        }
        if (accessibilityText == null) {
            CharSequence text = !TextUtils.isEmpty(customText) ? customText : messageObject.messageText;
            SpannableStringBuilder sb = new SpannableStringBuilder(text);
            CharacterStyle[] links = sb.getSpans(0, sb.length(), ClickableSpan.class);
            for (CharacterStyle link : links) {
                int start = sb.getSpanStart(link);
                int end = sb.getSpanEnd(link);
                sb.removeSpan(link);

                ClickableSpan underlineSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View view) {
                        if (delegate != null) {
                            openLink(link);
                        }
                    }
                };
                sb.setSpan(underlineSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            accessibilityText = sb;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            info.setContentDescription(accessibilityText.toString());
        } else {
            info.setText(accessibilityText);
        }
        info.setEnabled(true);
    }

    public void setInvalidateColors(boolean invalidate) {
        if (invalidateColors == invalidate) {
            return;
        }
        invalidateColors = invalidate;
        invalidate();
    }

    private int getThemedColor(String key) {
        Integer color = themeDelegate != null ? themeDelegate.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    private Paint getThemedPaint(String paintKey) {
        Paint paint = themeDelegate != null ? themeDelegate.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    public void drawOutboundsContent(Canvas canvas) {
        canvas.save();
        canvas.translate(textXLeft, textY);
        AnimatedEmojiSpan.drawAnimatedEmojis(canvas, textLayout, animatedEmojiStack, 0, spoilers, 0, 0, 0, 1f);
        canvas.restore();
    }

    private boolean isButtonLayout(MessageObject messageObject) {
        return messageObject != null && (messageObject.type == MessageObject.TYPE_GIFT_PREMIUM || messageObject.type == MessageObject.TYPE_SUGGEST_PHOTO);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (invalidateWithParent != null) {
            invalidateWithParent.invalidate();
        }
    }

    @Override
    public void invalidate(Rect dirty) {
        super.invalidate(dirty);
        if (invalidateWithParent != null) {
            invalidateWithParent.invalidate();
        }
    }

    @Override
    public void invalidate(int l, int t, int r, int b) {
        super.invalidate(l, t, r, b);
        if (invalidateWithParent != null) {
            invalidateWithParent.invalidate();
        }
    }
}
