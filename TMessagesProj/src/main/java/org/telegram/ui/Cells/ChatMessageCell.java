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
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
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
import android.util.SparseArray;
import android.util.StateSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStructure;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.animation.Interpolator;
import android.widget.Toast;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.WebFile;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.MessageBackgroundDrawable;
import org.telegram.ui.Components.CheckBoxBase;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RoundVideoPlayingDrawable;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.SeekBarWaveform;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanBrowser;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.SecretMediaViewer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

public class ChatMessageCell extends BaseCell implements SeekBar.SeekBarDelegate, ImageReceiver.ImageReceiverDelegate, DownloadController.FileDownloadProgressListener {

    public interface ChatMessageCellDelegate {
        default void didPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY) {
        }

        default void didPressHiddenForward(ChatMessageCell cell) {
        }

        default void didPressViaBot(ChatMessageCell cell, String username) {
        }

        default void didPressChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId, float touchX, float touchY) {
        }

        default void didPressCancelSendButton(ChatMessageCell cell) {
        }

        default void didLongPress(ChatMessageCell cell, float x, float y) {
        }

        default void didPressReplyMessage(ChatMessageCell cell, int id) {
        }

        default void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
        }

        default void needOpenWebView(String url, String title, String description, String originalUrl, int w, int h) {
        }

        default void didPressImage(ChatMessageCell cell, float x, float y) {
        }

        default void didPressShare(ChatMessageCell cell) {
        }

        default void didPressOther(ChatMessageCell cell, float otherX, float otherY) {
        }

        default void didPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {
        }

        default void didPressReaction(ChatMessageCell cell, TLRPC.TL_reactionCount reaction) {
        }

        default void didPressVoteButton(ChatMessageCell cell, TLRPC.TL_pollAnswer button) {
        }

        default void didPressInstantButton(ChatMessageCell cell, int type) {
        }

        default String getAdminRank(int uid) {
            return null;
        }

        default boolean needPlayMessage(MessageObject messageObject) {
            return false;
        }

        default boolean canPerformActions() {
            return false;
        }

        default void videoTimerReached() {
        }

        default void didStartVideoStream(MessageObject message) {
        }

        default boolean shouldRepeatSticker(MessageObject message) {
            return true;
        }

        default void setShouldNotRepeatSticker(MessageObject message) {
        }
    }

    private final static int DOCUMENT_ATTACH_TYPE_NONE = 0;
    private final static int DOCUMENT_ATTACH_TYPE_DOCUMENT = 1;
    private final static int DOCUMENT_ATTACH_TYPE_GIF = 2;
    private final static int DOCUMENT_ATTACH_TYPE_AUDIO = 3;
    private final static int DOCUMENT_ATTACH_TYPE_VIDEO = 4;
    private final static int DOCUMENT_ATTACH_TYPE_MUSIC = 5;
    private final static int DOCUMENT_ATTACH_TYPE_STICKER = 6;
    private final static int DOCUMENT_ATTACH_TYPE_ROUND = 7;
    private final static int DOCUMENT_ATTACH_TYPE_WALLPAPER = 8;
    private final static int DOCUMENT_ATTACH_TYPE_THEME = 9;

    private class BotButton {
        private int x;
        private int y;
        private int width;
        private int height;
        private StaticLayout title;
        private TLRPC.KeyboardButton button;
        private TLRPC.TL_reactionCount reaction;
        private int angle;
        private float progressAlpha;
        private long lastUpdateTime;
    }

    private class PollButton {
        private int x;
        private int y;
        private int height;
        private int percent;
        private float decimal;
        private int prevPercent;
        private float percentProgress;
        private float prevPercentProgress;
        private StaticLayout title;
        private TLRPC.TL_pollAnswer answer;
    }

    private boolean pinnedTop;
    private boolean pinnedBottom;
    private boolean drawPinnedTop;
    private boolean drawPinnedBottom;
    private MessageObject.GroupedMessages currentMessagesGroup;
    private MessageObject.GroupedMessagePosition currentPosition;
    private boolean groupPhotoInvisible;

    private int textX;
    private int unmovedTextX;
    private int textY;
    private int totalHeight;
    private int keyboardHeight;
    private int linkBlockNum;
    private int linkSelectionBlockNum;

    private boolean inLayout;

    private int currentMapProvider;

    private Rect scrollRect = new Rect();

    private int lastVisibleBlockNum;
    private int firstVisibleBlockNum;
    private int totalVisibleBlocksCount;
    private boolean needNewVisiblePart;
    private boolean fullyDraw;

    private boolean attachedToWindow;

    private RadialProgress2 radialProgress;
    private RadialProgress2 videoRadialProgress;
    private boolean drawRadialCheckBackground;
    private ImageReceiver photoImage;
    private AvatarDrawable contactAvatarDrawable;

    private boolean disallowLongPress;
    private float lastTouchX;
    private float lastTouchY;

    private boolean drawPhotoCheckBox;
    private boolean drawSelectionBackground;
    private CheckBoxBase photoCheckBox;
    private CheckBoxBase checkBox;
    private boolean checkBoxVisible;
    private boolean checkBoxAnimationInProgress;
    private float checkBoxAnimationProgress;
    private long lastCheckBoxAnimationTime;
    private int checkBoxTranslation;

    private boolean isSmallImage;
    private boolean drawImageButton;
    private boolean drawVideoImageButton;
    private boolean drawVideoSize;
    private boolean canStreamVideo;
    private int animatingDrawVideoImageButton;
    private float animatingDrawVideoImageButtonProgress;
    private boolean animatingNoSoundPlaying;
    private int animatingNoSound;
    private float animatingNoSoundProgress;
    private int noSoundCenterX;
    private int forwardNameCenterX;
    private long lastAnimationTime;
    private int documentAttachType;
    private TLRPC.Document documentAttach;
    private boolean drawPhotoImage;
    private boolean hasLinkPreview;
    private boolean hasOldCaptionPreview;
    private boolean hasGamePreview;
    private boolean hasInvoicePreview;
    private int linkPreviewHeight;
    private int mediaOffsetY;
    private int descriptionY;
    private int durationWidth;
    private int photosCountWidth;
    private int descriptionX;
    private int titleX;
    private int authorX;
    private boolean siteNameRtl;
    private int siteNameWidth;
    private StaticLayout siteNameLayout;
    private StaticLayout titleLayout;
    private StaticLayout descriptionLayout;
    private StaticLayout videoInfoLayout;
    private StaticLayout photosCountLayout;
    private StaticLayout authorLayout;
    private StaticLayout instantViewLayout;
    private boolean drawInstantView;
    private int drawInstantViewType;
    private int imageBackgroundColor;
    private int imageBackgroundSideColor;
    private int imageBackgroundSideWidth;
    private boolean drawJoinGroupView;
    private boolean drawJoinChannelView;
    private int instantTextX;
    private int instantTextLeftX;
    private int instantWidth;
    private boolean instantPressed;
    private boolean instantButtonPressed;
    private Drawable selectorDrawable;
    private int selectorDrawableMaskType;
    private RectF instantButtonRect = new RectF();
    private int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};

    private RoundVideoPlayingDrawable roundVideoPlayingDrawable;

    private StaticLayout docTitleLayout;
    private int docTitleWidth;
    private int docTitleOffsetX;
    private boolean locationExpired;

    private StaticLayout captionLayout;
    private CharSequence currentCaption;
    private int captionOffsetX;
    private int captionX;
    private int captionY;
    private int captionHeight;
    private int captionWidth;
    private int addedCaptionHeight;

    private StaticLayout infoLayout;
    private int infoX;
    private int infoWidth;

    private String currentUrl;
    private WebFile currentWebFile;
    private boolean addedForTest;

    private boolean hasEmbed;

    private boolean wasSending;
    private boolean checkOnlyButtonPressed;
    private int buttonX;
    private int buttonY;
    private int videoButtonX;
    private int videoButtonY;
    private int buttonState;
    private int buttonPressed;
    private int videoButtonPressed;
    private int miniButtonPressed;
    private int otherX;
    private int otherY;
    private int lastHeight;
    private int hasMiniProgress;
    private int miniButtonState;
    private boolean imagePressed;
    private boolean otherPressed;
    private boolean photoNotSet;
    private RectF deleteProgressRect = new RectF();
    private RectF rect = new RectF();
    private TLObject photoParentObject;
    private TLRPC.PhotoSize currentPhotoObject;
    private TLRPC.PhotoSize currentPhotoObjectThumb;
    private String currentPhotoFilter;
    private String currentPhotoFilterThumb;
    private boolean cancelLoading;

    private float timeAlpha = 1.0f;
    private float controlsAlpha = 1.0f;
    private long lastControlsAlphaChangeTime;
    private long totalChangeTime;
    private boolean mediaWasInvisible;
    private boolean timeWasInvisible;

    private CharacterStyle pressedLink;
    private int pressedLinkType;
    private boolean linkPreviewPressed;
    private boolean gamePreviewPressed;
    private ArrayList<LinkPath> urlPathCache = new ArrayList<>();
    private ArrayList<LinkPath> urlPath = new ArrayList<>();
    private ArrayList<LinkPath> urlPathSelection = new ArrayList<>();

    private boolean useSeekBarWaweform;
    private SeekBar seekBar;
    private SeekBarWaveform seekBarWaveform;
    private int seekBarX;
    private int seekBarY;

    private StaticLayout durationLayout;
    private int lastTime;
    private int timeWidthAudio;
    private int timeAudioX;

    private StaticLayout songLayout;
    private int songX;

    private StaticLayout performerLayout;
    private int performerX;

    private ArrayList<PollButton> pollButtons = new ArrayList<>();
    private float pollAnimationProgress;
    private float pollAnimationProgressTime;
    private boolean pollVoted;
    private boolean pollClosed;
    private boolean pollVoteInProgress;
    private boolean pollUnvoteInProgress;
    private boolean animatePollAnswer;
    private boolean animatePollAnswerAlpha;
    private int pollVoteInProgressNum;
    private long voteLastUpdateTime;
    private float voteRadOffset;
    private float voteCurrentCircleLength;
    private boolean firstCircleLength;
    private boolean voteRisingCircleLength;
    private float voteCurrentProgressTime;
    private int pressedVoteButton;
    private TLRPC.TL_poll lastPoll;
    private ArrayList<TLRPC.TL_pollAnswerVoters> lastPollResults;
    private int lastPollResultsVoters;

    private TLRPC.TL_messageReactions lastReactions;

    private boolean autoPlayingMedia;

    private ArrayList<BotButton> botButtons = new ArrayList<>();
    private HashMap<String, BotButton> botButtonsByData = new HashMap<>();
    private HashMap<String, BotButton> botButtonsByPosition = new HashMap<>();
    private String botButtonsLayout;
    private int widthForButtons;
    private int pressedBotButton;

    private MessageObject currentMessageObject;
    private MessageObject messageObjectToSet;
    private MessageObject.GroupedMessages groupedMessagesToSet;
    private boolean topNearToSet;
    private boolean bottomNearToSet;

    //
    private int TAG;
    private int currentAccount = UserConfig.selectedAccount;

    private boolean invalidatesParent;

    public boolean isChat;
    public boolean isMegagroup;
    private boolean isPressed;
    private boolean forwardName;
    private boolean isHighlighted;
    private boolean isHighlightedAnimated;
    private int highlightProgress;
    private long lastHighlightProgressTime;
    private boolean mediaBackground;
    private boolean isCheckPressed = true;
    private boolean wasLayout;
    private boolean isAvatarVisible;
    private boolean drawBackground = true;
    private int substractBackgroundHeight;
    private boolean allowAssistant;
    private Drawable currentBackgroundDrawable;
    private int backgroundDrawableLeft;
    private int backgroundDrawableRight;
    private int viaWidth;
    private int viaNameWidth;
    private TypefaceSpan viaSpan1;
    private TypefaceSpan viaSpan2;
    private int availableTimeWidth;
    private int widthBeforeNewTimeLine;

    private int backgroundWidth = 100;
    private boolean hasNewLineForTime;

    private int layoutWidth;
    private int layoutHeight;

    private ImageReceiver avatarImage;
    private AvatarDrawable avatarDrawable;
    private boolean avatarPressed;
    private boolean forwardNamePressed;
    private boolean forwardBotPressed;

    private ImageReceiver locationImageReceiver;

    private StaticLayout replyNameLayout;
    private StaticLayout replyTextLayout;
    private ImageReceiver replyImageReceiver;
    private int replyStartX;
    private int replyStartY;
    private int replyNameWidth;
    private float replyNameOffset;
    private int replyTextWidth;
    private float replyTextOffset;
    private boolean needReplyImage;
    private boolean replyPressed;
    private TLRPC.PhotoSize currentReplyPhoto;

    private boolean drwaShareGoIcon;
    private boolean drawShareButton;
    private boolean sharePressed;
    private int shareStartX;
    private int shareStartY;

    private StaticLayout nameLayout;
    private StaticLayout adminLayout;
    private int nameWidth;
    private float nameOffsetX;
    private float nameX;
    private float nameY;
    private boolean drawName;
    private boolean drawNameLayout;

    private StaticLayout[] forwardedNameLayout = new StaticLayout[2];
    private int forwardedNameWidth;
    private boolean drawForwardedName;
    private int forwardNameX;
    private int forwardNameY;
    private float[] forwardNameOffsetX = new float[2];

    private StaticLayout timeLayout;
    private int timeWidth;
    private int timeTextWidth;
    private int timeX;
    private String currentTimeString;
    private boolean drawTime = true;
    private boolean forceNotDrawTime;

    private StaticLayout viewsLayout;
    private int viewsTextWidth;
    private String currentViewsString;

    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private TLRPC.FileLocation currentPhoto;
    private String currentNameString;

    private TLRPC.User currentForwardUser;
    private TLRPC.User currentViaBotUser;
    private TLRPC.Chat currentForwardChannel;
    private String currentForwardName;
    private String currentForwardNameString;

    private ChatMessageCellDelegate delegate;

    private int namesOffset;

    private int lastSendState;
    private int lastDeleteDate;
    private int lastViewsCount;

    private boolean scheduledInvalidate;
    private Runnable invalidateRunnable = new Runnable() {
        @Override
        public void run() {
            checkLocationExpired();
            if (locationExpired) {
                invalidate();
                scheduledInvalidate = false;
            } else {
                invalidate((int) rect.left - 5, (int) rect.top - 5, (int) rect.right + 5, (int) rect.bottom + 5);
                if (scheduledInvalidate) {
                    AndroidUtilities.runOnUIThread(invalidateRunnable, 1000);
                }
            }
        }
    };
    private SparseArray<Rect> accessibilityVirtualViewBounds = new SparseArray<>();
    private int currentFocusedVirtualView = -1;

    public ChatMessageCell(Context context) {
        super(context);

        avatarImage = new ImageReceiver();
        avatarImage.setRoundRadius(AndroidUtilities.dp(21));
        avatarDrawable = new AvatarDrawable();
        replyImageReceiver = new ImageReceiver(this);
        locationImageReceiver = new ImageReceiver(this);
        locationImageReceiver.setRoundRadius(AndroidUtilities.dp(26.1f));
        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();

        contactAvatarDrawable = new AvatarDrawable();
        photoImage = new ImageReceiver(this);
        photoImage.setDelegate(this);
        radialProgress = new RadialProgress2(this);
        videoRadialProgress = new RadialProgress2(this);
        videoRadialProgress.setDrawBackground(false);
        videoRadialProgress.setCircleRadius(AndroidUtilities.dp(15));
        seekBar = new SeekBar(context);
        seekBar.setDelegate(this);
        seekBarWaveform = new SeekBarWaveform(context);
        seekBarWaveform.setDelegate(this);
        seekBarWaveform.setParentView(this);
        roundVideoPlayingDrawable = new RoundVideoPlayingDrawable(this);
    }

    private void resetPressedLink(int type) {
        if (pressedLink == null || pressedLinkType != type && type != -1) {
            return;
        }
        resetUrlPaths(false);
        pressedLink = null;
        pressedLinkType = -1;
        invalidate();
    }

    private void resetUrlPaths(boolean text) {
        if (text) {
            if (urlPathSelection.isEmpty()) {
                return;
            }
            urlPathCache.addAll(urlPathSelection);
            urlPathSelection.clear();
        } else {
            if (urlPath.isEmpty()) {
                return;
            }
            urlPathCache.addAll(urlPath);
            urlPath.clear();
        }
    }

    private LinkPath obtainNewUrlPath(boolean text) {
        LinkPath linkPath;
        if (!urlPathCache.isEmpty()) {
            linkPath = urlPathCache.get(0);
            urlPathCache.remove(0);
        } else {
            linkPath = new LinkPath();
        }
        linkPath.reset();
        if (text) {
            urlPathSelection.add(linkPath);
        } else {
            urlPath.add(linkPath);
        }
        return linkPath;
    }

    private int[] getRealSpanStartAndEnd(Spannable buffer, CharacterStyle link) {
        int start = 0;
        int end = 0;
        boolean ok = false;
        if (link instanceof URLSpanBrowser) {
            URLSpanBrowser span = (URLSpanBrowser) link;
            TextStyleSpan.TextStyleRun style = span.getStyle();
            if (style != null && style.urlEntity != null) {
                start = style.urlEntity.offset;
                end = style.urlEntity.offset + style.urlEntity.length;
                ok = true;
            }
        }
        if (!ok) {
            start = buffer.getSpanStart(link);
            end = buffer.getSpanEnd(link);
        }
        return new int[]{start, end};
    }

    private boolean checkTextBlockMotionEvent(MotionEvent event) {
        if (currentMessageObject.type != 0 || currentMessageObject.textLayoutBlocks == null || currentMessageObject.textLayoutBlocks.isEmpty() || !(currentMessageObject.messageText instanceof Spannable)) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP && pressedLinkType == 1) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            if (x >= textX && y >= textY && x <= textX + currentMessageObject.textWidth && y <= textY + currentMessageObject.textHeight) {
                y -= textY;
                int blockNum = 0;
                for (int a = 0; a < currentMessageObject.textLayoutBlocks.size(); a++) {
                    if (currentMessageObject.textLayoutBlocks.get(a).textYOffset > y) {
                        break;
                    }
                    blockNum = a;
                }
                try {
                    MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(blockNum);
                    x -= textX - (block.isRtl() ? currentMessageObject.textXOffset : 0);
                    y -= block.textYOffset;
                    final int line = block.textLayout.getLineForVertical(y);
                    final int off = block.textLayout.getOffsetForHorizontal(line, x);

                    final float left = block.textLayout.getLineLeft(line);
                    if (left <= x && left + block.textLayout.getLineWidth(line) >= x) {
                        Spannable buffer = (Spannable) currentMessageObject.messageText;
                        CharacterStyle[] link = buffer.getSpans(off, off, ClickableSpan.class);
                        boolean isMono = false;
                        if (link == null || link.length == 0) {
                            link = buffer.getSpans(off, off, URLSpanMono.class);
                            isMono = true;
                        }
                        boolean ignore = false;
                        if (link.length == 0 || link.length != 0 && link[0] instanceof URLSpanBotCommand && !URLSpanBotCommand.enabled) {
                            ignore = true;
                        }
                        if (!ignore) {
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                pressedLink = link[0];
                                linkBlockNum = blockNum;
                                pressedLinkType = 1;
                                resetUrlPaths(false);
                                try {
                                    LinkPath path = obtainNewUrlPath(false);
                                    int[] pos = getRealSpanStartAndEnd(buffer, pressedLink);
                                    path.setCurrentLayout(block.textLayout, pos[0], 0);
                                    block.textLayout.getSelectionPath(pos[0], pos[1], path);
                                    if (pos[1] >= block.charactersEnd) {
                                        for (int a = blockNum + 1; a < currentMessageObject.textLayoutBlocks.size(); a++) {
                                            MessageObject.TextLayoutBlock nextBlock = currentMessageObject.textLayoutBlocks.get(a);
                                            CharacterStyle[] nextLink;
                                            if (isMono) {
                                                nextLink = buffer.getSpans(nextBlock.charactersOffset, nextBlock.charactersOffset, URLSpanMono.class);
                                            } else {
                                                nextLink = buffer.getSpans(nextBlock.charactersOffset, nextBlock.charactersOffset, ClickableSpan.class);
                                            }
                                            if (nextLink == null || nextLink.length == 0 || nextLink[0] != pressedLink) {
                                                break;
                                            }
                                            path = obtainNewUrlPath(false);
                                            path.setCurrentLayout(nextBlock.textLayout, 0, nextBlock.textYOffset - block.textYOffset);
                                            nextBlock.textLayout.getSelectionPath(0, pos[1], path);
                                            if (pos[1] < nextBlock.charactersEnd - 1) {
                                                break;
                                            }
                                        }
                                    }
                                    if (pos[0] <= block.charactersOffset) {
                                        int offsetY = 0;
                                        for (int a = blockNum - 1; a >= 0; a--) {
                                            MessageObject.TextLayoutBlock nextBlock = currentMessageObject.textLayoutBlocks.get(a);
                                            CharacterStyle[] nextLink;
                                            if (isMono) {
                                                nextLink = buffer.getSpans(nextBlock.charactersEnd - 1, nextBlock.charactersEnd - 1, URLSpanMono.class);
                                            } else {
                                                nextLink = buffer.getSpans(nextBlock.charactersEnd - 1, nextBlock.charactersEnd - 1, ClickableSpan.class);
                                            }
                                            if (nextLink == null || nextLink.length == 0 || nextLink[0] != pressedLink) {
                                                break;
                                            }
                                            path = obtainNewUrlPath(false);
                                            offsetY -= nextBlock.height;
                                            path.setCurrentLayout(nextBlock.textLayout, pos[0], offsetY);
                                            nextBlock.textLayout.getSelectionPath(pos[0], pos[1], path);
                                            if (pos[0] > nextBlock.charactersOffset) {
                                                break;
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                invalidate();
                                return true;
                            } else {
                                if (link[0] == pressedLink) {
                                    delegate.didPressUrl(this, pressedLink, false);
                                    resetPressedLink(1);
                                    return true;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else {
                resetPressedLink(1);
            }
        }
        return false;
    }

    private boolean checkCaptionMotionEvent(MotionEvent event) {
        if (!(currentCaption instanceof Spannable) || captionLayout == null) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN || (linkPreviewPressed || pressedLink != null) && event.getAction() == MotionEvent.ACTION_UP) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            if (x >= captionX && x <= captionX + captionWidth && y >= captionY && y <= captionY + captionHeight) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    try {
                        x -= captionX;
                        y -= captionY;
                        final int line = captionLayout.getLineForVertical(y);
                        final int off = captionLayout.getOffsetForHorizontal(line, x);

                        final float left = captionLayout.getLineLeft(line);
                        if (left <= x && left + captionLayout.getLineWidth(line) >= x) {
                            Spannable buffer = (Spannable) currentCaption;
                            CharacterStyle[] link = buffer.getSpans(off, off, ClickableSpan.class);
                            if (link == null || link.length == 0) {
                                link = buffer.getSpans(off, off, URLSpanMono.class);
                            }
                            boolean ignore = false;
                            if (link.length == 0 || link.length != 0 && link[0] instanceof URLSpanBotCommand && !URLSpanBotCommand.enabled) {
                                ignore = true;
                            }
                            if (!ignore) {
                                pressedLink = link[0];
                                pressedLinkType = 3;
                                resetUrlPaths(false);
                                try {
                                    LinkPath path = obtainNewUrlPath(false);
                                    int[] pos = getRealSpanStartAndEnd(buffer, pressedLink);
                                    path.setCurrentLayout(captionLayout, pos[0], 0);
                                    captionLayout.getSelectionPath(pos[0], pos[1], path);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                if (currentMessagesGroup != null && getParent() != null) {
                                    ((ViewGroup) getParent()).invalidate();
                                }
                                invalidate();
                                return true;
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (pressedLinkType == 3) {
                    delegate.didPressUrl(this, pressedLink, false);
                    resetPressedLink(3);
                    return true;
                }
            } else {
                resetPressedLink(3);
            }
        }
        return false;
    }

    private boolean checkGameMotionEvent(MotionEvent event) {
        if (!hasGamePreview) {
            return false;
        }
        int x = (int) event.getX();
        int y = (int) event.getY();

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (drawPhotoImage && drawImageButton && buttonState != -1 && x >= buttonX && x <= buttonX + AndroidUtilities.dp(48) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48) && radialProgress.getIcon() != MediaActionDrawable.ICON_NONE) {
                buttonPressed = 1;
                invalidate();
                return true;
            } else if (drawPhotoImage && photoImage.isInsideImage(x, y)) {
                gamePreviewPressed = true;
                return true;
            } else if (descriptionLayout != null && y >= descriptionY) {
                try {
                    x -= unmovedTextX + AndroidUtilities.dp(10) + descriptionX;
                    y -= descriptionY;
                    final int line = descriptionLayout.getLineForVertical(y);
                    final int off = descriptionLayout.getOffsetForHorizontal(line, x);

                    final float left = descriptionLayout.getLineLeft(line);
                    if (left <= x && left + descriptionLayout.getLineWidth(line) >= x) {
                        Spannable buffer = (Spannable) currentMessageObject.linkDescription;
                        ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                        boolean ignore = false;
                        if (link.length == 0 || link.length != 0 && link[0] instanceof URLSpanBotCommand && !URLSpanBotCommand.enabled) {
                            ignore = true;
                        }
                        if (!ignore) {
                            pressedLink = link[0];
                            linkBlockNum = -10;
                            pressedLinkType = 2;
                            resetUrlPaths(false);
                            try {
                                LinkPath path = obtainNewUrlPath(false);
                                int[] pos = getRealSpanStartAndEnd(buffer, pressedLink);
                                path.setCurrentLayout(descriptionLayout, pos[0], 0);
                                descriptionLayout.getSelectionPath(pos[0], pos[1], path);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            invalidate();
                            return true;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressedLinkType == 2 || gamePreviewPressed || buttonPressed != 0) {
                if (buttonPressed != 0) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressButton(true, false);
                    invalidate();
                } else if (pressedLink != null) {
                    if (pressedLink instanceof URLSpan) {
                        Browser.openUrl(getContext(), ((URLSpan) pressedLink).getURL());
                    } else if (pressedLink instanceof ClickableSpan) {
                        ((ClickableSpan) pressedLink).onClick(this);
                    }
                    resetPressedLink(2);
                } else {
                    gamePreviewPressed = false;
                    for (int a = 0; a < botButtons.size(); a++) {
                        BotButton button = botButtons.get(a);
                        if (button.button instanceof TLRPC.TL_keyboardButtonGame) {
                            playSoundEffect(SoundEffectConstants.CLICK);
                            delegate.didPressBotButton(this, button.button);
                            invalidate();
                            break;
                        }
                    }
                    resetPressedLink(2);
                    return true;
                }
            } else {
                resetPressedLink(2);
            }
        }
        return false;
    }

    private boolean checkLinkPreviewMotionEvent(MotionEvent event) {
        if (currentMessageObject.type != 0 || !hasLinkPreview) {
            return false;
        }
        int x = (int) event.getX();
        int y = (int) event.getY();

        if (x >= unmovedTextX && x <= unmovedTextX + backgroundWidth && y >= textY + currentMessageObject.textHeight && y <= textY + currentMessageObject.textHeight + linkPreviewHeight + AndroidUtilities.dp(8 + (drawInstantView ? 46 : 0))) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (descriptionLayout != null && y >= descriptionY) {
                    try {
                        int checkX = x - (unmovedTextX + AndroidUtilities.dp(10) + descriptionX);
                        int checkY = y - descriptionY;
                        if (checkY <= descriptionLayout.getHeight()) {
                            final int line = descriptionLayout.getLineForVertical(checkY);
                            final int off = descriptionLayout.getOffsetForHorizontal(line, checkX);

                            final float left = descriptionLayout.getLineLeft(line);
                            if (left <= checkX && left + descriptionLayout.getLineWidth(line) >= checkX) {
                                Spannable buffer = (Spannable) currentMessageObject.linkDescription;
                                ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                                boolean ignore = false;
                                if (link.length == 0 || link.length != 0 && link[0] instanceof URLSpanBotCommand && !URLSpanBotCommand.enabled) {
                                    ignore = true;
                                }
                                if (!ignore) {
                                    pressedLink = link[0];
                                    linkBlockNum = -10;
                                    pressedLinkType = 2;
                                    resetUrlPaths(false);
                                    try {
                                        LinkPath path = obtainNewUrlPath(false);
                                        int[] pos = getRealSpanStartAndEnd(buffer, pressedLink);
                                        path.setCurrentLayout(descriptionLayout, pos[0], 0);
                                        descriptionLayout.getSelectionPath(pos[0], pos[1], path);
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                    invalidate();
                                    return true;
                                }
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (pressedLink == null) {
                    int side = AndroidUtilities.dp(48);
                    boolean area2 = false;
                    if (miniButtonState >= 0) {
                        int offset = AndroidUtilities.dp(27);
                        area2 = x >= buttonX + offset && x <= buttonX + offset + side && y >= buttonY + offset && y <= buttonY + offset + side;
                    }
                    if (area2) {
                        miniButtonPressed = 1;
                        invalidate();
                        return true;
                    } else if (drawVideoImageButton && buttonState != -1 && x >= videoButtonX && x <= videoButtonX + AndroidUtilities.dp(26 + 8) + Math.max(infoWidth, docTitleWidth) && y >= videoButtonY && y <= videoButtonY + AndroidUtilities.dp(30)) {
                        videoButtonPressed = 1;
                        invalidate();
                        return true;
                    } else if (drawPhotoImage && drawImageButton && buttonState != -1 && (!checkOnlyButtonPressed && photoImage.isInsideImage(x, y) || x >= buttonX && x <= buttonX + AndroidUtilities.dp(48) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48) && radialProgress.getIcon() != MediaActionDrawable.ICON_NONE)) {
                        buttonPressed = 1;
                        invalidate();
                        return true;
                    } else if (drawInstantView) {
                        instantPressed = true;
                        if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                            if (selectorDrawable.getBounds().contains(x, y)) {
                                selectorDrawable.setState(pressedState);
                                selectorDrawable.setHotspot(x, y);
                                instantButtonPressed = true;
                            }
                        }
                        invalidate();
                        return true;
                    } else if (documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT && drawPhotoImage && photoImage.isInsideImage(x, y)) {
                        linkPreviewPressed = true;
                        TLRPC.WebPage webPage = currentMessageObject.messageOwner.media.webpage;
                        if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && buttonState == -1 && SharedConfig.autoplayGifs && (photoImage.getAnimation() == null || !TextUtils.isEmpty(webPage.embed_url))) {
                            linkPreviewPressed = false;
                            return false;
                        }
                        return true;
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (instantPressed) {
                    if (delegate != null) {
                        delegate.didPressInstantButton(this, drawInstantViewType);
                    }
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                        selectorDrawable.setState(StateSet.NOTHING);
                    }
                    instantPressed = instantButtonPressed = false;
                    invalidate();
                } else if (pressedLinkType == 2 || buttonPressed != 0 || miniButtonPressed != 0 || videoButtonPressed != 0 || linkPreviewPressed) {
                    if (videoButtonPressed == 1) {
                        videoButtonPressed = 0;
                        playSoundEffect(SoundEffectConstants.CLICK);
                        didPressButton(true, true);
                        invalidate();
                    } else if (buttonPressed != 0) {
                        buttonPressed = 0;
                        playSoundEffect(SoundEffectConstants.CLICK);
                        if (drawVideoImageButton) {
                            didClickedImage();
                        } else {
                            didPressButton(true, false);
                        }
                        invalidate();
                    } else if (miniButtonPressed != 0) {
                        miniButtonPressed = 0;
                        playSoundEffect(SoundEffectConstants.CLICK);
                        didPressMiniButton(true);
                        invalidate();
                    } else if (pressedLink != null) {
                        if (pressedLink instanceof URLSpan) {
                            Browser.openUrl(getContext(), ((URLSpan) pressedLink).getURL());
                        } else if (pressedLink instanceof ClickableSpan) {
                            ((ClickableSpan) pressedLink).onClick(this);
                        }
                        resetPressedLink(2);
                    } else {
                        if (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
                            if (!MediaController.getInstance().isPlayingMessage(currentMessageObject) || MediaController.getInstance().isMessagePaused()) {
                                delegate.needPlayMessage(currentMessageObject);
                            } else {
                                MediaController.getInstance().pauseMessage(currentMessageObject);
                            }
                        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && drawImageButton) {
                            if (buttonState == -1) {
                                if (SharedConfig.autoplayGifs) {
                                    delegate.didPressImage(this, lastTouchX, lastTouchY);
                                } else {
                                    buttonState = 2;
                                    currentMessageObject.gifState = 1;
                                    photoImage.setAllowStartAnimation(false);
                                    photoImage.stopAnimation();
                                    radialProgress.setIcon(getIconForCurrentState(), false, true);
                                    invalidate();
                                    playSoundEffect(SoundEffectConstants.CLICK);
                                }
                            } else if (buttonState == 2 || buttonState == 0) {
                                didPressButton(true, false);
                                playSoundEffect(SoundEffectConstants.CLICK);
                            }
                        } else {
                            TLRPC.WebPage webPage = currentMessageObject.messageOwner.media.webpage;
                            if (webPage != null && !TextUtils.isEmpty(webPage.embed_url)) {
                                delegate.needOpenWebView(webPage.embed_url, webPage.site_name, webPage.title, webPage.url, webPage.embed_width, webPage.embed_height);
                            } else if (buttonState == -1 || buttonState == 3) {
                                delegate.didPressImage(this, lastTouchX, lastTouchY);
                                playSoundEffect(SoundEffectConstants.CLICK);
                            } else if (webPage != null) {
                                Browser.openUrl(getContext(), webPage.url);
                            }
                        }
                        resetPressedLink(2);
                        return true;
                    }
                } else {
                    resetPressedLink(2);
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (instantButtonPressed && Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                    selectorDrawable.setHotspot(x, y);
                }
            }
        }
        return false;
    }

    private boolean checkPollButtonMotionEvent(MotionEvent event) {
        if (currentMessageObject.eventId != 0 || pollVoted || pollClosed || pollVoteInProgress || pollUnvoteInProgress || pollButtons.isEmpty() || currentMessageObject.type != 17 || !currentMessageObject.isSent()) {
            return false;
        }
        int x = (int) event.getX();
        int y = (int) event.getY();

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pressedVoteButton = -1;
            for (int a = 0; a < pollButtons.size(); a++) {
                PollButton button = pollButtons.get(a);
                int y2 = button.y + namesOffset - AndroidUtilities.dp(13);
                if (x >= button.x && x <= button.x + backgroundWidth - AndroidUtilities.dp(31) && y >= y2 && y <= y2 + button.height + AndroidUtilities.dp(26)) {
                    pressedVoteButton = a;
                    if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                        selectorDrawable.setBounds(button.x - AndroidUtilities.dp(9), y2, button.x + backgroundWidth - AndroidUtilities.dp(22), y2 + button.height + AndroidUtilities.dp(26));
                        selectorDrawable.setState(pressedState);
                        selectorDrawable.setHotspot(x, y);
                    }
                    invalidate();
                    result = true;
                    break;
                }
            }
        } else {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pressedVoteButton != -1) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                        selectorDrawable.setState(StateSet.NOTHING);
                    }
                    if (currentMessageObject.scheduled) {
                        Toast.makeText(getContext(), LocaleController.getString("MessageScheduledVote", R.string.MessageScheduledVote), Toast.LENGTH_LONG).show();
                    } else {
                        pollVoteInProgressNum = pressedVoteButton;
                        pollVoteInProgress = true;
                        voteCurrentProgressTime = 0.0f;
                        firstCircleLength = true;
                        voteCurrentCircleLength = 360;
                        voteRisingCircleLength = false;
                        delegate.didPressVoteButton(this, pollButtons.get(pressedVoteButton).answer);
                    }
                    pressedVoteButton = -1;
                    invalidate();
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (pressedVoteButton != -1 && Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                    selectorDrawable.setHotspot(x, y);
                }
            }
        }
        return result;
    }

    private boolean checkInstantButtonMotionEvent(MotionEvent event) {
        if (!drawInstantView || currentMessageObject.type == 0) {
            return false;
        }
        int x = (int) event.getX();
        int y = (int) event.getY();

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (drawInstantView && instantButtonRect.contains(x, y)) {
                instantPressed = true;
                if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                    if (selectorDrawable.getBounds().contains(x, y)) {
                        selectorDrawable.setState(pressedState);
                        selectorDrawable.setHotspot(x, y);
                        instantButtonPressed = true;
                    }
                }
                invalidate();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (instantPressed) {
                if (delegate != null) {
                    delegate.didPressInstantButton(this, drawInstantViewType);
                }
                playSoundEffect(SoundEffectConstants.CLICK);
                if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                    selectorDrawable.setState(StateSet.NOTHING);
                }
                instantPressed = instantButtonPressed = false;
                invalidate();
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (instantButtonPressed && Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                selectorDrawable.setHotspot(x, y);
            }
        }
        return false;
    }

    private boolean checkOtherButtonMotionEvent(MotionEvent event) {
        boolean allow = currentMessageObject.type == 16;
        if (!allow) {
            allow = !(documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT && currentMessageObject.type != 12 && documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC && documentAttachType != DOCUMENT_ATTACH_TYPE_VIDEO && documentAttachType != DOCUMENT_ATTACH_TYPE_GIF && currentMessageObject.type != 8 || hasGamePreview || hasInvoicePreview);
        }
        if (!allow) {
            return false;
        }

        int x = (int) event.getX();
        int y = (int) event.getY();

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (currentMessageObject.type == 16) {
                if (x >= otherX && x <= otherX + AndroidUtilities.dp(30 + 205) && y >= otherY - AndroidUtilities.dp(14) && y <= otherY + AndroidUtilities.dp(50)) {
                    otherPressed = true;
                    result = true;
                    invalidate();
                }
            } else {
                if (x >= otherX - AndroidUtilities.dp(20) && x <= otherX + AndroidUtilities.dp(20) && y >= otherY - AndroidUtilities.dp(4) && y <= otherY + AndroidUtilities.dp(30)) {
                    otherPressed = true;
                    result = true;
                    invalidate();
                }
            }
        } else {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (otherPressed) {
                    otherPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    delegate.didPressOther(this, otherX, otherY);
                    invalidate();
                    result = true;
                }
            }
        }
        return result;
    }

    private boolean checkPhotoImageMotionEvent(MotionEvent event) {
        if (!drawPhotoImage && documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT) {
            return false;
        }

        int x = (int) event.getX();
        int y = (int) event.getY();

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            boolean area2 = false;
            int side = AndroidUtilities.dp(48);

            if (miniButtonState >= 0) {
                int offset = AndroidUtilities.dp(27);
                area2 = x >= buttonX + offset && x <= buttonX + offset + side && y >= buttonY + offset && y <= buttonY + offset + side;
            }
            if (area2) {
                miniButtonPressed = 1;
                invalidate();
                result = true;
            } else if (buttonState != -1 && radialProgress.getIcon() != MediaActionDrawable.ICON_NONE && x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side) {
                buttonPressed = 1;
                invalidate();
                result = true;
            } else if (drawVideoImageButton && buttonState != -1 && x >= videoButtonX && x <= videoButtonX + AndroidUtilities.dp(26 + 8) + Math.max(infoWidth, docTitleWidth) && y >= videoButtonY && y <= videoButtonY + AndroidUtilities.dp(30)) {
                videoButtonPressed = 1;
                invalidate();
                result = true;
            } else {
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                    if (x >= photoImage.getImageX() && x <= photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(50) && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight()) {
                        imagePressed = true;
                        result = true;
                    }
                } else if (!currentMessageObject.isAnyKindOfSticker() || currentMessageObject.getInputStickerSet() != null || currentMessageObject.isAnimatedEmoji()) {
                    if (x >= photoImage.getImageX() && x <= photoImage.getImageX() + photoImage.getImageWidth() && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight()) {
                        imagePressed = true;
                        result = true;
                    }
                    if (currentMessageObject.type == 12) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(currentMessageObject.messageOwner.media.user_id);
                        if (user == null) {
                            imagePressed = false;
                            result = false;
                        }
                    }
                }
            }
            if (imagePressed) {
                if (currentMessageObject.isSendError()) {
                    imagePressed = false;
                    result = false;
                } else if (currentMessageObject.type == 8 && buttonState == -1 && SharedConfig.autoplayGifs && photoImage.getAnimation() == null) {
                    imagePressed = false;
                    result = false;
                }
            }
        } else {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (videoButtonPressed == 1) {
                    videoButtonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressButton(true, true);
                    invalidate();
                } else if (buttonPressed == 1) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (drawVideoImageButton) {
                        didClickedImage();
                    } else {
                        didPressButton(true, false);
                    }
                    invalidate();
                } else if (miniButtonPressed == 1) {
                    miniButtonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressMiniButton(true);
                    invalidate();
                } else if (imagePressed) {
                    imagePressed = false;
                    if (buttonState == -1 || buttonState == 2 || buttonState == 3 || drawVideoImageButton) {
                        playSoundEffect(SoundEffectConstants.CLICK);
                        didClickedImage();
                    } else if (buttonState == 0) {
                        playSoundEffect(SoundEffectConstants.CLICK);
                        didPressButton(true, false);
                    }
                    invalidate();
                }
            }
        }
        return result;
    }

    private boolean checkAudioMotionEvent(MotionEvent event) {
        if (documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO && documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC) {
            return false;
        }

        int x = (int) event.getX();
        int y = (int) event.getY();
        boolean result;
        if (useSeekBarWaweform) {
            result = seekBarWaveform.onTouch(event.getAction(), event.getX() - seekBarX - AndroidUtilities.dp(13), event.getY() - seekBarY);
        } else {
            result = seekBar.onTouch(event.getAction(), event.getX() - seekBarX, event.getY() - seekBarY);
        }
        if (result) {
            if (!useSeekBarWaweform && event.getAction() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if (useSeekBarWaweform && !seekBarWaveform.isStartDraging() && event.getAction() == MotionEvent.ACTION_UP) {
                didPressButton(true, false);
            }
            disallowLongPress = true;
            invalidate();
        } else {
            int side = AndroidUtilities.dp(36);
            boolean area = false;
            boolean area2 = false;
            if (miniButtonState >= 0) {
                int offset = AndroidUtilities.dp(27);
                area2 = x >= buttonX + offset && x <= buttonX + offset + side && y >= buttonY + offset && y <= buttonY + offset + side;
            }
            if (!area2) {
                if (buttonState == 0 || buttonState == 1 || buttonState == 2) {
                    area = x >= buttonX - AndroidUtilities.dp(12) && x <= buttonX - AndroidUtilities.dp(12) + backgroundWidth && y >= (drawInstantView ? buttonY : namesOffset + mediaOffsetY) && y <= (drawInstantView ? buttonY + side : namesOffset + mediaOffsetY + AndroidUtilities.dp(82));
                } else {
                    area = x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side;
                }
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (area || area2) {
                    if (area) {
                        buttonPressed = 1;
                    } else {
                        miniButtonPressed = 1;
                    }
                    invalidate();
                    result = true;
                }
            } else if (buttonPressed != 0) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressButton(true, false);
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    buttonPressed = 0;
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!area) {
                        buttonPressed = 0;
                        invalidate();
                    }
                }
            } else if (miniButtonPressed != 0) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    miniButtonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressMiniButton(true);
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    miniButtonPressed = 0;
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!area2) {
                        miniButtonPressed = 0;
                        invalidate();
                    }
                }
            }
        }
        return result;
    }

    private boolean checkBotButtonMotionEvent(MotionEvent event) {
        if (botButtons.isEmpty() || currentMessageObject.eventId != 0) {
            return false;
        }

        int x = (int) event.getX();
        int y = (int) event.getY();

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int addX;
            if (currentMessageObject.isOutOwner()) {
                addX = getMeasuredWidth() - widthForButtons - AndroidUtilities.dp(10);
            } else {
                addX = backgroundDrawableLeft + AndroidUtilities.dp(mediaBackground ? 1 : 7);
            }
            for (int a = 0; a < botButtons.size(); a++) {
                BotButton button = botButtons.get(a);
                int y2 = button.y + layoutHeight - AndroidUtilities.dp(2);
                if (x >= button.x + addX && x <= button.x + addX + button.width && y >= y2 && y <= y2 + button.height) {
                    pressedBotButton = a;
                    invalidate();
                    result = true;
                    break;
                }
            }
        } else {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pressedBotButton != -1) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (currentMessageObject.scheduled) {
                        Toast.makeText(getContext(), LocaleController.getString("MessageScheduledBotAction", R.string.MessageScheduledBotAction), Toast.LENGTH_LONG).show();
                    } else {
                        BotButton button = botButtons.get(pressedBotButton);
                        if (button.button != null) {
                            delegate.didPressBotButton(this, button.button);
                        } else if (button.reaction != null) {
                            delegate.didPressReaction(this, button.reaction);
                        }
                    }
                    pressedBotButton = -1;
                    invalidate();
                }
            }
        }
        return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentMessageObject == null || !delegate.canPerformActions()) {
            return super.onTouchEvent(event);
        }

        disallowLongPress = false;
        lastTouchX = event.getX();
        lastTouchX = event.getY();

        boolean result = checkTextBlockMotionEvent(event);
        if (!result) {
            result = checkOtherButtonMotionEvent(event);
        }
        if (!result) {
            result = checkCaptionMotionEvent(event);
        }
        if (!result) {
            result = checkAudioMotionEvent(event);
        }
        if (!result) {
            result = checkLinkPreviewMotionEvent(event);
        }
        if (!result) {
            result = checkInstantButtonMotionEvent(event);
        }
        if (!result) {
            result = checkGameMotionEvent(event);
        }
        if (!result) {
            result = checkPhotoImageMotionEvent(event);
        }
        if (!result) {
            result = checkBotButtonMotionEvent(event);
        }
        if (!result) {
            result = checkPollButtonMotionEvent(event);
        }

        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            buttonPressed = 0;
            miniButtonPressed = 0;
            pressedBotButton = -1;
            pressedVoteButton = -1;
            linkPreviewPressed = false;
            otherPressed = false;
            sharePressed = false;
            imagePressed = false;
            gamePreviewPressed = false;
            instantPressed = instantButtonPressed = false;
            if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                selectorDrawable.setState(StateSet.NOTHING);
            }
            result = false;
            resetPressedLink(-1);
        }
        updateRadialProgressBackground();
        if (!disallowLongPress && result && event.getAction() == MotionEvent.ACTION_DOWN) {
            startCheckLongPress();
        }

        if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
            cancelCheckLongPress();
        }

        if (!result) {
            float x = event.getX();
            float y = event.getY();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (delegate == null || delegate.canPerformActions()) {
                    if (isAvatarVisible && avatarImage.isInsideImage(x, y + getTop())) {
                        avatarPressed = true;
                        result = true;
                    } else if (drawForwardedName && forwardedNameLayout[0] != null && x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + AndroidUtilities.dp(32)) {
                        if (viaWidth != 0 && x >= forwardNameX + viaNameWidth + AndroidUtilities.dp(4)) {
                            forwardBotPressed = true;
                        } else {
                            forwardNamePressed = true;
                        }
                        result = true;
                    } else if (drawNameLayout && nameLayout != null && viaWidth != 0 && x >= nameX + viaNameWidth && x <= nameX + viaNameWidth + viaWidth && y >= nameY - AndroidUtilities.dp(4) && y <= nameY + AndroidUtilities.dp(20)) {
                        forwardBotPressed = true;
                        result = true;
                    } else if (drawShareButton && x >= shareStartX && x <= shareStartX + AndroidUtilities.dp(40) && y >= shareStartY && y <= shareStartY + AndroidUtilities.dp(32)) {
                        sharePressed = true;
                        result = true;
                        invalidate();
                    } else if (replyNameLayout != null) {
                        int replyEnd;
                        if (currentMessageObject.shouldDrawWithoutBackground()) {
                            replyEnd = replyStartX + Math.max(replyNameWidth, replyTextWidth);
                        } else {
                            replyEnd = replyStartX + backgroundDrawableRight;
                        }
                        if (x >= replyStartX && x <= replyEnd && y >= replyStartY && y <= replyStartY + AndroidUtilities.dp(35)) {
                            replyPressed = true;
                            result = true;
                        }
                    }
                    if (result) {
                        startCheckLongPress();
                    }
                }
            } else {
                if (event.getAction() != MotionEvent.ACTION_MOVE) {
                    cancelCheckLongPress();
                }
                if (avatarPressed) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        avatarPressed = false;
                        playSoundEffect(SoundEffectConstants.CLICK);
                        if (delegate != null) {
                            if (currentUser != null) {
                                if (currentUser.id == 0) {
                                    delegate.didPressHiddenForward(this);
                                } else {
                                    delegate.didPressUserAvatar(this, currentUser, lastTouchX, lastTouchY);
                                }
                            } else if (currentChat != null) {
                                delegate.didPressChannelAvatar(this, currentChat, currentMessageObject.messageOwner.fwd_from.channel_post, lastTouchX, lastTouchY);
                            }
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                        avatarPressed = false;
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        if (isAvatarVisible && !avatarImage.isInsideImage(x, y + getTop())) {
                            avatarPressed = false;
                        }
                    }
                } else if (forwardNamePressed) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        forwardNamePressed = false;
                        playSoundEffect(SoundEffectConstants.CLICK);
                        if (delegate != null) {
                            if (currentForwardChannel != null) {
                                delegate.didPressChannelAvatar(this, currentForwardChannel, currentMessageObject.messageOwner.fwd_from.channel_post, lastTouchX, lastTouchY);
                            } else if (currentForwardUser != null) {
                                delegate.didPressUserAvatar(this, currentForwardUser, lastTouchX, lastTouchY);
                            } else if (currentForwardName != null) {
                                delegate.didPressHiddenForward(this);
                            }
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                        forwardNamePressed = false;
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        if (!(x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + AndroidUtilities.dp(32))) {
                            forwardNamePressed = false;
                        }
                    }
                } else if (forwardBotPressed) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        forwardBotPressed = false;
                        playSoundEffect(SoundEffectConstants.CLICK);
                        if (delegate != null) {
                            delegate.didPressViaBot(this, currentViaBotUser != null ? currentViaBotUser.username : currentMessageObject.messageOwner.via_bot_name);
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                        forwardBotPressed = false;
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        if (drawForwardedName && forwardedNameLayout[0] != null) {
                            if (!(x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + AndroidUtilities.dp(32))) {
                                forwardBotPressed = false;
                            }
                        } else {
                            if (!(x >= nameX + viaNameWidth && x <= nameX + viaNameWidth + viaWidth && y >= nameY - AndroidUtilities.dp(4) && y <= nameY + AndroidUtilities.dp(20))) {
                                forwardBotPressed = false;
                            }
                        }
                    }
                } else if (replyPressed) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        replyPressed = false;
                        playSoundEffect(SoundEffectConstants.CLICK);
                        if (delegate != null) {
                            delegate.didPressReplyMessage(this, currentMessageObject.messageOwner.reply_to_msg_id);
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                        replyPressed = false;
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        int replyEnd;
                        if (currentMessageObject.shouldDrawWithoutBackground()) {
                            replyEnd = replyStartX + Math.max(replyNameWidth, replyTextWidth);
                        } else {
                            replyEnd = replyStartX + backgroundDrawableRight;
                        }
                        if (!(x >= replyStartX && x <= replyEnd && y >= replyStartY && y <= replyStartY + AndroidUtilities.dp(35))) {
                            replyPressed = false;
                        }
                    }
                } else if (sharePressed) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        sharePressed = false;
                        playSoundEffect(SoundEffectConstants.CLICK);
                        if (delegate != null) {
                            delegate.didPressShare(this);
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                        sharePressed = false;
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        if (!(x >= shareStartX && x <= shareStartX + AndroidUtilities.dp(40) && y >= shareStartY && y <= shareStartY + AndroidUtilities.dp(32))) {
                            sharePressed = false;
                        }
                    }
                    invalidate();
                }
            }
        }
        return result;
    }

    public void updatePlayingMessageProgress() {
        if (currentMessageObject == null) {
            return;
        }

        if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
            if (infoLayout != null && (PhotoViewer.isPlayingMessage(currentMessageObject) || MediaController.getInstance().isGoingToShowMessageObject(currentMessageObject))) {
                return;
            }
            int duration = 0;
            AnimatedFileDrawable animation = photoImage.getAnimation();
            if (animation != null) {
                duration = currentMessageObject.audioPlayerDuration = animation.getDurationMs() / 1000;
                if (currentMessageObject.messageOwner.ttl > 0 && currentMessageObject.messageOwner.destroyTime == 0 && !currentMessageObject.needDrawBluredPreview() && currentMessageObject.isVideo() && animation.hasBitmap()) {
                    delegate.didStartVideoStream(currentMessageObject);
                }
            }
            if (duration == 0) {
                duration = currentMessageObject.getDuration();
            }
            if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
                duration -= duration * currentMessageObject.audioProgress;
            } else if (animation != null) {
                if (duration != 0) {
                    duration -= animation.getCurrentProgressMs() / 1000;
                }
                if (delegate != null && animation.getCurrentProgressMs() >= 3000) {
                    delegate.videoTimerReached();
                }
            }
            int minutes = duration / 60;
            int seconds = duration - minutes * 60;
            if (lastTime != duration) {
                String str = String.format("%d:%02d", minutes, seconds);
                infoWidth = (int) Math.ceil(Theme.chat_infoPaint.measureText(str));
                infoLayout = new StaticLayout(str, Theme.chat_infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                lastTime = duration;
            }
        } else if (currentMessageObject.isRoundVideo()) {
            int duration = 0;
            TLRPC.Document document = currentMessageObject.getDocument();
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    duration = attribute.duration;
                    break;
                }
            }
            if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
                duration = Math.max(0, duration - currentMessageObject.audioProgressSec);
            }
            if (lastTime != duration) {
                lastTime = duration;
                String timeString = String.format("%02d:%02d", duration / 60, duration % 60);
                timeWidthAudio = (int) Math.ceil(Theme.chat_timePaint.measureText(timeString));
                durationLayout = new StaticLayout(timeString, Theme.chat_timePaint, timeWidthAudio, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                invalidate();
            }
        } else if (documentAttach != null) {
            if (useSeekBarWaweform) {
                if (!seekBarWaveform.isDragging()) {
                    seekBarWaveform.setProgress(currentMessageObject.audioProgress);
                }
            } else {
                if (!seekBar.isDragging()) {
                    seekBar.setProgress(currentMessageObject.audioProgress);
                    seekBar.setBufferedProgress(currentMessageObject.bufferedProgress);
                }
            }

            int duration = 0;
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
                if (!MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
                    for (int a = 0; a < documentAttach.attributes.size(); a++) {
                        TLRPC.DocumentAttribute attribute = documentAttach.attributes.get(a);
                        if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                            duration = attribute.duration;
                            break;
                        }
                    }
                } else {
                    duration = currentMessageObject.audioProgressSec;
                }

                if (lastTime != duration) {
                    lastTime = duration;
                    String timeString = String.format("%02d:%02d", duration / 60, duration % 60);
                    timeWidthAudio = (int) Math.ceil(Theme.chat_audioTimePaint.measureText(timeString));
                    durationLayout = new StaticLayout(timeString, Theme.chat_audioTimePaint, timeWidthAudio, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                }
            } else {
                int currentProgress = 0;
                duration = currentMessageObject.getDuration();
                if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
                    currentProgress = currentMessageObject.audioProgressSec;
                }
                if (lastTime != currentProgress) {
                    lastTime = currentProgress;
                    String timeString;
                    if (duration == 0) {
                        timeString = String.format("%d:%02d / -:--", currentProgress / 60, currentProgress % 60);
                    } else {
                        timeString = String.format("%d:%02d / %d:%02d", currentProgress / 60, currentProgress % 60, duration / 60, duration % 60);
                    }
                    int timeWidth = (int) Math.ceil(Theme.chat_audioTimePaint.measureText(timeString));
                    durationLayout = new StaticLayout(timeString, Theme.chat_audioTimePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                }
            }
            invalidate();
        }
    }

    public void setFullyDraw(boolean draw) {
        fullyDraw = draw;
    }

    public void setVisiblePart(int position, int height) {
        if (currentMessageObject == null || currentMessageObject.textLayoutBlocks == null) {
            return;
        }
        position -= textY;

        int newFirst = -1, newLast = -1, newCount = 0;

        int startBlock = 0;
        for (int a = 0; a < currentMessageObject.textLayoutBlocks.size(); a++) {
            if (currentMessageObject.textLayoutBlocks.get(a).textYOffset > position) {
                break;
            }
            startBlock = a;
        }

        for (int a = startBlock; a < currentMessageObject.textLayoutBlocks.size(); a++) {
            MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(a);
            float y = block.textYOffset;
            if (intersect(y, y + block.height, position, position + height)) {
                if (newFirst == -1) {
                    newFirst = a;
                }
                newLast = a;
                newCount++;
            } else if (y > position) {
                break;
            }
        }

        if (lastVisibleBlockNum != newLast || firstVisibleBlockNum != newFirst || totalVisibleBlocksCount != newCount) {
            lastVisibleBlockNum = newLast;
            firstVisibleBlockNum = newFirst;
            totalVisibleBlocksCount = newCount;
            invalidate();
        }
    }

    private boolean intersect(float left1, float right1, float left2, float right2) {
        if (left1 <= left2) {
            return right1 >= left2;
        }
        return left1 <= right2;
    }

    public static StaticLayout generateStaticLayout(CharSequence text, TextPaint paint, int maxWidth, int smallWidth, int linesCount, int maxLines) {
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
        int addedChars = 0;
        StaticLayout layout = new StaticLayout(text, paint, smallWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        for (int a = 0; a < linesCount; a++) {
            Layout.Directions directions = layout.getLineDirections(a);
            if (layout.getLineLeft(a) != 0 || layout.isRtlCharAt(layout.getLineStart(a)) || layout.isRtlCharAt(layout.getLineEnd(a))) {
                maxWidth = smallWidth;
            }
            int pos = layout.getLineEnd(a);
            if (pos == text.length()) {
                break;
            }
            pos--;
            if (stringBuilder.charAt(pos + addedChars) == ' ') {
                stringBuilder.replace(pos + addedChars, pos + addedChars + 1, "\n");
            } else if (stringBuilder.charAt(pos + addedChars) != '\n') {
                stringBuilder.insert(pos + addedChars, "\n");
                addedChars++;
            }
            if (a == layout.getLineCount() - 1 || a == maxLines - 1) {
                break;
            }
        }
        return StaticLayoutEx.createStaticLayout(stringBuilder, paint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false, TextUtils.TruncateAt.END, maxWidth, maxLines, true);
    }

    private void didClickedImage() {
        if (currentMessageObject.type == 1 || currentMessageObject.isAnyKindOfSticker()) {
            if (buttonState == -1) {
                delegate.didPressImage(this, lastTouchX, lastTouchY);
            } else if (buttonState == 0) {
                didPressButton(true, false);
            }
        } else if (currentMessageObject.type == 12) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(currentMessageObject.messageOwner.media.user_id);
            delegate.didPressUserAvatar(this, user, lastTouchX, lastTouchY);
        } else if (currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
            if (buttonState != -1) {
                didPressButton(true, false);
            } else {
                if (!MediaController.getInstance().isPlayingMessage(currentMessageObject) || MediaController.getInstance().isMessagePaused()) {
                    delegate.needPlayMessage(currentMessageObject);
                } else {
                    MediaController.getInstance().pauseMessage(currentMessageObject);
                }
            }
        } else if (currentMessageObject.type == 8) {
            if (buttonState == -1 || buttonState == 1 && canStreamVideo && autoPlayingMedia) {
                //if (SharedConfig.autoplayGifs) {
                    delegate.didPressImage(this, lastTouchX, lastTouchY);
                /*} else {
                    buttonState = 2;
                    currentMessageObject.gifState = 1;
                    photoImage.setAllowStartAnimation(false);
                    photoImage.stopAnimation();
                    radialProgress.setIcon(getIconForCurrentState(), false, true);
                    invalidate();
                }*/
            } else if (buttonState == 2 || buttonState == 0) {
                didPressButton(true, false);
            }
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
            if (buttonState == -1 || drawVideoImageButton && (autoPlayingMedia || SharedConfig.streamMedia && canStreamVideo)) {
                delegate.didPressImage(this, lastTouchX, lastTouchY);
            } else if (drawVideoImageButton) {
                didPressButton(true, true);
            } else if (buttonState == 0 || buttonState == 3) {
                didPressButton(true, false);
            }
        } else if (currentMessageObject.type == 4) {
            delegate.didPressImage(this, lastTouchX, lastTouchY);
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
            if (buttonState == -1) {
                delegate.didPressImage(this, lastTouchX, lastTouchY);
            }
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
            if (buttonState == -1) {
                TLRPC.WebPage webPage = currentMessageObject.messageOwner.media.webpage;
                if (webPage != null) {
                    if (webPage.embed_url != null && webPage.embed_url.length() != 0) {
                        delegate.needOpenWebView(webPage.embed_url, webPage.site_name, webPage.description, webPage.url, webPage.embed_width, webPage.embed_height);
                    } else {
                        Browser.openUrl(getContext(), webPage.url);
                    }
                }
            }
        } else if (hasInvoicePreview) {
            if (buttonState == -1) {
                delegate.didPressImage(this, lastTouchX, lastTouchY);
            }
        }
    }

    private void updateSecretTimeText(MessageObject messageObject) {
        if (messageObject == null || !messageObject.needDrawBluredPreview()) {
            return;
        }
        String str = messageObject.getSecretTimeString();
        if (str == null) {
            return;
        }
        infoWidth = (int) Math.ceil(Theme.chat_infoPaint.measureText(str));
        CharSequence str2 = TextUtils.ellipsize(str, Theme.chat_infoPaint, infoWidth, TextUtils.TruncateAt.END);
        infoLayout = new StaticLayout(str2, Theme.chat_infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        invalidate();
    }

    private boolean isPhotoDataChanged(MessageObject object) {
        if (object.type == 0 || object.type == 14) {
            return false;
        }
        if (object.type == 4) {
            if (currentUrl == null) {
                return true;
            }
            double lat = object.messageOwner.media.geo.lat;
            double lon = object.messageOwner.media.geo._long;
            String url;
            if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                int photoWidth = backgroundWidth - AndroidUtilities.dp(21);
                int photoHeight = AndroidUtilities.dp(195);

                int offset = 268435456;
                double rad = offset / Math.PI;
                double y = Math.round(offset - rad * Math.log((1 + Math.sin(lat * Math.PI / 180.0)) / (1 - Math.sin(lat * Math.PI / 180.0))) / 2) - (AndroidUtilities.dp(10.3f) << (21 - 15));
                lat = (Math.PI / 2.0 - 2 * Math.atan(Math.exp((y - offset) / rad))) * 180.0 / Math.PI;
                url = AndroidUtilities.formapMapUrl(currentAccount, lat, lon, (int) (photoWidth / AndroidUtilities.density), (int) (photoHeight / AndroidUtilities.density), false, 15);
            } else if (!TextUtils.isEmpty(object.messageOwner.media.title)) {
                int photoWidth = backgroundWidth - AndroidUtilities.dp(21);
                int photoHeight = AndroidUtilities.dp(195);
                url = AndroidUtilities.formapMapUrl(currentAccount, lat, lon, (int) (photoWidth / AndroidUtilities.density), (int) (photoHeight / AndroidUtilities.density), true, 15);
            } else {
                int photoWidth = backgroundWidth - AndroidUtilities.dp(12);
                int photoHeight = AndroidUtilities.dp(195);
                url = AndroidUtilities.formapMapUrl(currentAccount, lat, lon, (int) (photoWidth / AndroidUtilities.density), (int) (photoHeight / AndroidUtilities.density), true, 15);
            }
            return !url.equals(currentUrl);
        } else if (currentPhotoObject == null || currentPhotoObject.location instanceof TLRPC.TL_fileLocationUnavailable) {
            return object.type == 1 || object.type == MessageObject.TYPE_ROUND_VIDEO || object.type == 3 || object.type == 8 || object.isAnyKindOfSticker();
        } else if (currentMessageObject != null && photoNotSet) {
            File cacheFile = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
            return cacheFile.exists();
        }
        return false;
    }

    private boolean isUserDataChanged() {
        if (currentMessageObject != null && (!hasLinkPreview && currentMessageObject.messageOwner.media != null && currentMessageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage)) {
            return true;
        }
        if (currentMessageObject == null || currentUser == null && currentChat == null) {
            return false;
        }
        if (lastSendState != currentMessageObject.messageOwner.send_state) {
            return true;
        }
        if (lastDeleteDate != currentMessageObject.messageOwner.destroyTime) {
            return true;
        }
        if (lastViewsCount != currentMessageObject.messageOwner.views) {
            return true;
        }
        if (lastReactions != currentMessageObject.messageOwner.reactions) {
            return true;
        }

        updateCurrentUserAndChat();
        TLRPC.FileLocation newPhoto = null;

        if (isAvatarVisible) {
            if (currentUser != null && currentUser.photo != null) {
                newPhoto = currentUser.photo.photo_small;
            } else if (currentChat != null && currentChat.photo != null) {
                newPhoto = currentChat.photo.photo_small;
            }
        }

        if (replyTextLayout == null && currentMessageObject.replyMessageObject != null) {
            return true;
        }

        if (currentPhoto == null && newPhoto != null || currentPhoto != null && newPhoto == null || currentPhoto != null && newPhoto != null && (currentPhoto.local_id != newPhoto.local_id || currentPhoto.volume_id != newPhoto.volume_id)) {
            return true;
        }

        TLRPC.PhotoSize newReplyPhoto = null;

        if (replyNameLayout != null) {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(currentMessageObject.replyMessageObject.photoThumbs, 40);
            if (photoSize != null && !currentMessageObject.replyMessageObject.isAnyKindOfSticker()) {
                newReplyPhoto = photoSize;
            }
        }

        if (currentReplyPhoto == null && newReplyPhoto != null) {
            return true;
        }

        String newNameString = null;
        if (drawName && isChat && !currentMessageObject.isOutOwner()) {
            if (currentUser != null) {
                newNameString = UserObject.getUserName(currentUser);
            } else if (currentChat != null) {
                newNameString = currentChat.title;
            }
        }

        if (currentNameString == null && newNameString != null || currentNameString != null && newNameString == null || currentNameString != null && newNameString != null && !currentNameString.equals(newNameString)) {
            return true;
        }

        if (drawForwardedName && currentMessageObject.needDrawForwarded()) {
            newNameString = currentMessageObject.getForwardedName();
            return currentForwardNameString == null && newNameString != null || currentForwardNameString != null && newNameString == null || currentForwardNameString != null && newNameString != null && !currentForwardNameString.equals(newNameString);
        }
        return false;
    }

    public ImageReceiver getPhotoImage() {
        return photoImage;
    }

    public int getNoSoundIconCenterX() {
        return noSoundCenterX;
    }

    public int getForwardNameCenterX() {
        if (currentUser != null && currentUser.id == 0) {
            return (int) avatarImage.getCenterX();
        }
        return forwardNameX + forwardNameCenterX;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (checkBox != null) {
            checkBox.onDetachedFromWindow();
        }
        if (photoCheckBox != null) {
            photoCheckBox.onDetachedFromWindow();
        }
        attachedToWindow = false;
        radialProgress.onDetachedFromWindow();
        videoRadialProgress.onDetachedFromWindow();
        avatarImage.onDetachedFromWindow();
        replyImageReceiver.onDetachedFromWindow();
        locationImageReceiver.onDetachedFromWindow();
        photoImage.onDetachedFromWindow();
        if (addedForTest && currentUrl != null && currentWebFile != null) {
            ImageLoader.getInstance().removeTestWebFile(currentUrl);
            addedForTest = false;
        }
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (messageObjectToSet != null) {
            setMessageContent(messageObjectToSet, groupedMessagesToSet, bottomNearToSet, topNearToSet);
            messageObjectToSet = null;
            groupedMessagesToSet = null;
        }
        if (checkBox != null) {
            checkBox.onAttachedToWindow();
        }
        if (photoCheckBox != null) {
            photoCheckBox.onAttachedToWindow();
        }

        attachedToWindow = true;
        setTranslationX(0);
        radialProgress.onAttachedToWindow();
        videoRadialProgress.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
        avatarImage.setParentView((View) getParent());
        replyImageReceiver.onAttachedToWindow();
        locationImageReceiver.onAttachedToWindow();
        if (photoImage.onAttachedToWindow()) {
            if (drawPhotoImage) {
                updateButtonState(false, false, false);
            }
        } else {
            updateButtonState(false, false, false);
        }
        if (currentMessageObject != null && (currentMessageObject.isRoundVideo() || currentMessageObject.isVideo())) {
            checkVideoPlayback(true);
        }
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO && autoPlayingMedia) {
            animatingNoSoundPlaying = MediaController.getInstance().isPlayingMessage(currentMessageObject);
            animatingNoSoundProgress = animatingNoSoundPlaying ? 0.0f : 1.0f;
            animatingNoSound = 0;
        } else {
            animatingNoSoundPlaying = false;
            animatingNoSoundProgress = 0;
            animatingDrawVideoImageButtonProgress = (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) && drawVideoSize ? 1.0f : 0.0f;
        }
    }

    private void setMessageContent(MessageObject messageObject, MessageObject.GroupedMessages groupedMessages, boolean bottomNear, boolean topNear) {
        if (messageObject.checkLayout() || currentPosition != null && lastHeight != AndroidUtilities.displaySize.y) {
            currentMessageObject = null;
        }
        lastHeight = AndroidUtilities.displaySize.y;
        boolean messageIdChanged = currentMessageObject == null || currentMessageObject.getId() != messageObject.getId();
        boolean messageChanged = currentMessageObject != messageObject || messageObject.forceUpdate;
        boolean dataChanged = currentMessageObject != null && currentMessageObject.getId() == messageObject.getId() && lastSendState == MessageObject.MESSAGE_SEND_STATE_EDITING && messageObject.isSent()
                || currentMessageObject == messageObject && (isUserDataChanged() || photoNotSet);
        boolean groupChanged = groupedMessages != currentMessagesGroup;
        boolean pollChanged = false;
        if (!messageChanged && messageObject.type == MessageObject.TYPE_POLL) {
            ArrayList<TLRPC.TL_pollAnswerVoters> newResults = null;
            TLRPC.TL_poll newPoll = null;
            int newVoters = 0;
            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media;
                newResults = mediaPoll.results.results;
                newPoll = mediaPoll.poll;
                newVoters = mediaPoll.results.total_voters;
            }
            if (newResults != null && lastPollResults != null && newVoters != lastPollResultsVoters) {
                pollChanged = true;
            }
            if (!pollChanged && newResults != lastPollResults) {
                pollChanged = true;
            }
            if (!pollChanged && lastPoll != newPoll && lastPoll.closed != newPoll.closed) {
                pollChanged = true;
            }
            if (pollChanged && attachedToWindow) {
                pollAnimationProgressTime = 0.0f;
                if (pollVoted && !messageObject.isVoted()) {
                    pollUnvoteInProgress = true;
                }
            }
        }
        if (!groupChanged && groupedMessages != null) {
            MessageObject.GroupedMessagePosition newPosition;
            if (groupedMessages.messages.size() > 1) {
                newPosition = currentMessagesGroup.positions.get(currentMessageObject);
            } else {
                newPosition = null;
            }
            groupChanged = newPosition != currentPosition;
        }
        if (messageChanged || dataChanged || groupChanged || pollChanged || isPhotoDataChanged(messageObject) || pinnedBottom != bottomNear || pinnedTop != topNear) {
            pinnedBottom = bottomNear;
            pinnedTop = topNear;
            currentMessageObject = messageObject;
            currentMessagesGroup = groupedMessages;
            lastTime = -2;
            isHighlightedAnimated = false;
            widthBeforeNewTimeLine = -1;
            if (currentMessagesGroup != null && currentMessagesGroup.posArray.size() > 1) {
                currentPosition = currentMessagesGroup.positions.get(currentMessageObject);
                if (currentPosition == null) {
                    currentMessagesGroup = null;
                }
            } else {
                currentMessagesGroup = null;
                currentPosition = null;
            }
            drawPinnedTop = pinnedTop && (currentPosition == null || (currentPosition.flags & MessageObject.POSITION_FLAG_TOP) != 0);
            drawPinnedBottom = pinnedBottom && (currentPosition == null || (currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0);
            photoImage.setCrossfadeWithOldImage(false);
            lastSendState = messageObject.messageOwner.send_state;
            lastDeleteDate = messageObject.messageOwner.destroyTime;
            lastViewsCount = messageObject.messageOwner.views;
            isPressed = false;
            gamePreviewPressed = false;
            sharePressed = false;
            isCheckPressed = true;
            hasNewLineForTime = false;
            isAvatarVisible = isChat && !messageObject.isOutOwner() && messageObject.needDrawAvatar() && (currentPosition == null || currentPosition.edge);
            wasLayout = false;
            drwaShareGoIcon = false;
            groupPhotoInvisible = false;
            animatingDrawVideoImageButton = 0;
            drawVideoSize = false;
            canStreamVideo = false;
            animatingNoSound = 0;
            drawShareButton = checkNeedDrawShareButton(messageObject);
            replyNameLayout = null;
            adminLayout = null;
            checkOnlyButtonPressed = false;
            replyTextLayout = null;
            hasEmbed = false;
            autoPlayingMedia = false;
            replyNameWidth = 0;
            replyTextWidth = 0;
            viaWidth = 0;
            viaNameWidth = 0;
            addedCaptionHeight = 0;
            currentReplyPhoto = null;
            currentUser = null;
            currentChat = null;
            currentViaBotUser = null;
            instantViewLayout = null;
            drawNameLayout = false;
            if (scheduledInvalidate) {
                AndroidUtilities.cancelRunOnUIThread(invalidateRunnable);
                scheduledInvalidate = false;
            }

            resetPressedLink(-1);
            messageObject.forceUpdate = false;
            drawPhotoImage = false;
            drawPhotoCheckBox = false;
            hasLinkPreview = false;
            hasOldCaptionPreview = false;
            hasGamePreview = false;
            hasInvoicePreview = false;
            instantPressed = instantButtonPressed = false;
            if (!pollChanged && Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                selectorDrawable.setVisible(false, false);
                selectorDrawable.setState(StateSet.NOTHING);
            }
            linkPreviewPressed = false;
            buttonPressed = 0;
            miniButtonPressed = 0;
            pressedBotButton = -1;
            pressedVoteButton = -1;
            linkPreviewHeight = 0;
            mediaOffsetY = 0;
            documentAttachType = DOCUMENT_ATTACH_TYPE_NONE;
            documentAttach = null;
            descriptionLayout = null;
            titleLayout = null;
            videoInfoLayout = null;
            photosCountLayout = null;
            siteNameLayout = null;
            authorLayout = null;
            captionLayout = null;
            captionOffsetX = 0;
            currentCaption = null;
            docTitleLayout = null;
            drawImageButton = false;
            drawVideoImageButton = false;
            currentPhotoObject = null;
            photoParentObject = null;
            currentPhotoObjectThumb = null;
            currentPhotoFilter = null;
            infoLayout = null;
            cancelLoading = false;
            buttonState = -1;
            miniButtonState = -1;
            hasMiniProgress = 0;
            if (addedForTest && currentUrl != null && currentWebFile != null) {
                ImageLoader.getInstance().removeTestWebFile(currentUrl);
            }
            addedForTest = false;
            currentUrl = null;
            currentWebFile = null;
            photoNotSet = false;
            drawBackground = true;
            drawName = false;
            useSeekBarWaweform = false;
            drawInstantView = false;
            drawInstantViewType = 0;
            drawForwardedName = false;
            photoImage.setSideClip(0);
            imageBackgroundColor = 0;
            imageBackgroundSideColor = 0;
            mediaBackground = false;
            photoImage.setAlpha(1.0f);
            if (messageChanged || dataChanged) {
                pollButtons.clear();
            }
            int captionNewLine = 0;
            availableTimeWidth = 0;
            lastReactions = messageObject.messageOwner.reactions;
            photoImage.setForceLoading(false);
            photoImage.setNeedsQualityThumb(false);
            photoImage.setShouldGenerateQualityThumb(false);
            photoImage.setAllowDecodeSingleFrame(false);
            photoImage.setRoundRadius(AndroidUtilities.dp(4));
            photoImage.setColorFilter(null);

            if (messageChanged) {
                firstVisibleBlockNum = 0;
                lastVisibleBlockNum = 0;
                needNewVisiblePart = true;
            }

            if (messageObject.type == 0) {
                drawForwardedName = true;

                int maxWidth;
                if (AndroidUtilities.isTablet()) {
                    if (isChat && !messageObject.isOutOwner() && messageObject.needDrawAvatar()) {
                        maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122);
                        drawName = true;
                    } else {
                        drawName = messageObject.messageOwner.to_id.channel_id != 0 && !messageObject.isOutOwner();
                        maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
                    }
                } else {
                    if (isChat && !messageObject.isOutOwner() && messageObject.needDrawAvatar()) {
                        maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(122);
                        drawName = true;
                    } else {
                        maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(80);
                        drawName = messageObject.messageOwner.to_id.channel_id != 0 && !messageObject.isOutOwner();
                    }
                }
                availableTimeWidth = maxWidth;
                if (messageObject.isRoundVideo()) {
                    availableTimeWidth -= Math.ceil(Theme.chat_audioTimePaint.measureText("00:00")) + (messageObject.isOutOwner() ? 0 : AndroidUtilities.dp(64));
                }
                measureTime(messageObject);
                int timeMore = timeWidth + AndroidUtilities.dp(6);
                if (messageObject.isOutOwner()) {
                    timeMore += AndroidUtilities.dp(20.5f);
                }

                hasGamePreview = messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame && messageObject.messageOwner.media.game instanceof TLRPC.TL_game;
                hasInvoicePreview = messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice;
                hasLinkPreview = messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage;
                drawInstantView = hasLinkPreview && messageObject.messageOwner.media.webpage.cached_page != null;
                hasEmbed = hasLinkPreview && !TextUtils.isEmpty(messageObject.messageOwner.media.webpage.embed_url) && !messageObject.isGif();
                boolean slideshow = false;
                String siteName = hasLinkPreview ? messageObject.messageOwner.media.webpage.site_name : null;
                String webpageType = hasLinkPreview ? messageObject.messageOwner.media.webpage.type : null;
                TLRPC.Document androidThemeDocument = null;
                if (!drawInstantView) {
                    if ("telegram_channel".equals(webpageType)) {
                        drawInstantView = true;
                        drawInstantViewType = 1;
                    } else if ("telegram_megagroup".equals(webpageType)) {
                        drawInstantView = true;
                        drawInstantViewType = 2;
                    } else if ("telegram_message".equals(webpageType)) {
                        drawInstantView = true;
                        drawInstantViewType = 3;
                    } else if ("telegram_theme".equals(webpageType)) {
                        ArrayList<TLRPC.Document> documents = messageObject.messageOwner.media.webpage.documents;
                        for (int a = 0, N = documents.size(); a < N; a++) {
                            TLRPC.Document document = documents.get(a);
                            if ("application/x-tgtheme-android".equals(document.mime_type)) {
                                drawInstantView = true;
                                drawInstantViewType = 7;
                                androidThemeDocument = document;
                                break;
                            }
                        }
                    } else if ("telegram_background".equals(webpageType)) {
                        drawInstantView = true;
                        drawInstantViewType = 6;
                        try {
                            Uri url = Uri.parse(messageObject.messageOwner.media.webpage.url);
                            int intensity = Utilities.parseInt(url.getQueryParameter("intensity"));
                            String bgColor = url.getQueryParameter("bg_color");
                            if (TextUtils.isEmpty(bgColor)) {
                                TLRPC.Document document = messageObject.getDocument();
                                if (document != null && "image/png".equals(document.mime_type)) {
                                    bgColor = "ffffff";
                                }
                                if (intensity == 0) {
                                    intensity = 50;
                                }
                            }
                            if (bgColor != null) {
                                imageBackgroundColor = Integer.parseInt(bgColor, 16) | 0xff000000;
                                imageBackgroundSideColor = AndroidUtilities.getPatternSideColor(imageBackgroundColor);
                                photoImage.setColorFilter(new PorterDuffColorFilter(AndroidUtilities.getPatternColor(imageBackgroundColor), PorterDuff.Mode.SRC_IN));
                                photoImage.setAlpha(intensity / 100.0f);
                            } else {
                                String color = url.getLastPathSegment();
                                if (color != null && color.length() == 6) {
                                    imageBackgroundColor = Integer.parseInt(color, 16) | 0xff000000;
                                    currentPhotoObject = new TLRPC.TL_photoSizeEmpty();
                                    currentPhotoObject.type = "s";
                                    currentPhotoObject.w = AndroidUtilities.dp(180);
                                    currentPhotoObject.h = AndroidUtilities.dp(150);
                                    currentPhotoObject.location = new TLRPC.TL_fileLocationUnavailable();
                                }
                            }
                        } catch (Exception ignore) {

                        }
                    } /*else if ("telegram_proxy".equals(webpageType)) {
                        drawInstantView = true;
                        drawInstantViewType = 4;
                    }*/
                } else if (siteName != null) {
                    siteName = siteName.toLowerCase();
                    if ((siteName.equals("instagram") || siteName.equals("twitter") || "telegram_album".equals(webpageType)) && messageObject.messageOwner.media.webpage.cached_page instanceof TLRPC.TL_page &&
                            (messageObject.messageOwner.media.webpage.photo instanceof TLRPC.TL_photo || MessageObject.isVideoDocument(messageObject.messageOwner.media.webpage.document))) {
                        drawInstantView = false;
                        slideshow = true;
                        ArrayList<TLRPC.PageBlock> blocks = messageObject.messageOwner.media.webpage.cached_page.blocks;
                        int count = 1;
                        for (int a = 0; a < blocks.size(); a++) {
                            TLRPC.PageBlock block = blocks.get(a);
                            if (block instanceof TLRPC.TL_pageBlockSlideshow) {
                                TLRPC.TL_pageBlockSlideshow b = (TLRPC.TL_pageBlockSlideshow) block;
                                count = b.items.size();
                            } else if (block instanceof TLRPC.TL_pageBlockCollage) {
                                TLRPC.TL_pageBlockCollage b = (TLRPC.TL_pageBlockCollage) block;
                                count = b.items.size();
                            }
                        }
                        String str = LocaleController.formatString("Of", R.string.Of, 1, count);
                        photosCountWidth = (int) Math.ceil(Theme.chat_durationPaint.measureText(str));
                        photosCountLayout = new StaticLayout(str, Theme.chat_durationPaint, photosCountWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    }
                }
                backgroundWidth = maxWidth;
                if (hasLinkPreview || hasGamePreview || hasInvoicePreview || maxWidth - messageObject.lastLineWidth < timeMore) {
                    backgroundWidth = Math.max(backgroundWidth, messageObject.lastLineWidth) + AndroidUtilities.dp(31);
                    backgroundWidth = Math.max(backgroundWidth, timeWidth + AndroidUtilities.dp(31));
                } else {
                    int diff = backgroundWidth - messageObject.lastLineWidth;
                    if (diff >= 0 && diff <= timeMore) {
                        backgroundWidth = backgroundWidth + timeMore - diff + AndroidUtilities.dp(31);
                    } else {
                        backgroundWidth = Math.max(backgroundWidth, messageObject.lastLineWidth + timeMore) + AndroidUtilities.dp(31);
                    }
                }
                availableTimeWidth = backgroundWidth - AndroidUtilities.dp(31);
                if (messageObject.isRoundVideo()) {
                    availableTimeWidth -= Math.ceil(Theme.chat_audioTimePaint.measureText("00:00")) + (messageObject.isOutOwner() ? 0 : AndroidUtilities.dp(64));
                }

                setMessageObjectInternal(messageObject);

                backgroundWidth = messageObject.textWidth + ((hasGamePreview || hasInvoicePreview) ? AndroidUtilities.dp(10) : 0);
                totalHeight = messageObject.textHeight + AndroidUtilities.dp(19.5f) + namesOffset;
                if (drawPinnedTop) {
                    namesOffset -= AndroidUtilities.dp(1);
                }

                int maxChildWidth = Math.max(backgroundWidth, nameWidth);
                maxChildWidth = Math.max(maxChildWidth, forwardedNameWidth);
                maxChildWidth = Math.max(maxChildWidth, replyNameWidth);
                maxChildWidth = Math.max(maxChildWidth, replyTextWidth);
                int maxWebWidth = 0;

                if (hasLinkPreview || hasGamePreview || hasInvoicePreview) {
                    int linkPreviewMaxWidth;
                    if (AndroidUtilities.isTablet()) {
                        if (isChat && messageObject.needDrawAvatar() && !currentMessageObject.isOutOwner()) {
                            linkPreviewMaxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(132);
                        } else {
                            linkPreviewMaxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
                        }
                    } else {
                        if (isChat && messageObject.needDrawAvatar() && !currentMessageObject.isOutOwner()) {
                            linkPreviewMaxWidth = AndroidUtilities.displaySize.x - AndroidUtilities.dp(132);
                        } else {
                            linkPreviewMaxWidth = AndroidUtilities.displaySize.x - AndroidUtilities.dp(80);
                        }
                    }
                    if (drawShareButton) {
                        linkPreviewMaxWidth -= AndroidUtilities.dp(20);
                    }
                    String site_name;
                    String title;
                    String author;
                    String description;
                    TLRPC.Photo photo;
                    TLRPC.Document document;
                    WebFile webDocument;
                    int duration;
                    boolean smallImage;
                    String type;
                    if (hasLinkPreview) {
                        TLRPC.TL_webPage webPage = (TLRPC.TL_webPage) messageObject.messageOwner.media.webpage;
                        site_name = webPage.site_name;
                        title = drawInstantViewType != 6 && drawInstantViewType != 7 ? webPage.title : null;
                        author = drawInstantViewType != 6 && drawInstantViewType != 7 ? webPage.author : null;
                        description = drawInstantViewType != 6 && drawInstantViewType != 7 ? webPage.description : null;
                        photo = webPage.photo;
                        webDocument = null;
                        if (drawInstantViewType == 7) {
                            document = androidThemeDocument;
                        } else {
                            document = webPage.document;
                        }
                        type = webPage.type;
                        duration = webPage.duration;
                        if (site_name != null && photo != null && site_name.toLowerCase().equals("instagram")) {
                            linkPreviewMaxWidth = Math.max(AndroidUtilities.displaySize.y / 3, currentMessageObject.textWidth);
                        }
                        boolean isSmallImageType = "app".equals(type) || "profile".equals(type) || "article".equals(type);
                        smallImage = !slideshow && !drawInstantView && document == null && isSmallImageType;
                        isSmallImage = !slideshow && !drawInstantView && document == null && description != null && type != null && isSmallImageType && currentMessageObject.photoThumbs != null;
                    } else if (hasInvoicePreview) {
                        TLRPC.TL_messageMediaInvoice invoice = (TLRPC.TL_messageMediaInvoice) messageObject.messageOwner.media;
                        site_name = messageObject.messageOwner.media.title;
                        title = null;
                        description = null;
                        photo = null;
                        author = null;
                        document = null;
                        if (invoice.photo instanceof TLRPC.TL_webDocument) {
                            webDocument = WebFile.createWithWebDocument(invoice.photo);
                        } else {
                            webDocument = null;
                        }
                        duration = 0;
                        type = "invoice";
                        isSmallImage = false;
                        smallImage = false;
                    } else {
                        TLRPC.TL_game game = messageObject.messageOwner.media.game;
                        site_name = game.title;
                        title = null;
                        webDocument = null;
                        description = TextUtils.isEmpty(messageObject.messageText) ? game.description : null;
                        photo = game.photo;
                        author = null;
                        document = game.document;
                        duration = 0;
                        type = "game";
                        isSmallImage = false;
                        smallImage = false;
                    }
                    if (drawInstantViewType == 6) {
                        site_name = LocaleController.getString("ChatBackground", R.string.ChatBackground);
                    } else if ("telegram_theme".equals(webpageType)) {
                        site_name = LocaleController.getString("ColorTheme", R.string.ColorTheme);
                    }

                    int additinalWidth = hasInvoicePreview ? 0 : AndroidUtilities.dp(10);
                    int restLinesCount = 3;
                    int additionalHeight = 0;
                    linkPreviewMaxWidth -= additinalWidth;

                    if (currentMessageObject.photoThumbs == null && photo != null) {
                        currentMessageObject.generateThumbs(true);
                    }

                    if (site_name != null) {
                        try {
                            int width = (int) Math.ceil(Theme.chat_replyNamePaint.measureText(site_name) + 1);
                            siteNameLayout = new StaticLayout(site_name, Theme.chat_replyNamePaint, Math.min(width, linkPreviewMaxWidth), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            siteNameRtl = siteNameLayout.getLineLeft(0) != 0;
                            int height = siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
                            linkPreviewHeight += height;
                            totalHeight += height;
                            additionalHeight += height;
                            siteNameWidth = width = siteNameLayout.getWidth();
                            maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
                            maxWebWidth = Math.max(maxWebWidth, width + additinalWidth);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }

                    boolean titleIsRTL = false;
                    if (title != null) {
                        try {
                            titleX = Integer.MAX_VALUE;
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += AndroidUtilities.dp(2);
                                totalHeight += AndroidUtilities.dp(2);
                            }
                            int restLines = 0;
                            if (!isSmallImage || description == null) {
                                titleLayout = StaticLayoutEx.createStaticLayout(title, Theme.chat_replyNamePaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false, TextUtils.TruncateAt.END, linkPreviewMaxWidth, 4);
                            } else {
                                restLines = restLinesCount;
                                titleLayout = generateStaticLayout(title, Theme.chat_replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - AndroidUtilities.dp(48 + 4), restLinesCount, 4);
                                restLinesCount -= titleLayout.getLineCount();
                            }
                            int height = titleLayout.getLineBottom(titleLayout.getLineCount() - 1);
                            linkPreviewHeight += height;
                            totalHeight += height;
                            boolean checkForRtl = true;
                            for (int a = 0; a < titleLayout.getLineCount(); a++) {
                                int lineLeft = (int) titleLayout.getLineLeft(a);
                                if (lineLeft != 0) {
                                    titleIsRTL = true;
                                }
                                if (titleX == Integer.MAX_VALUE) {
                                    titleX = -lineLeft;
                                } else {
                                    titleX = Math.max(titleX, -lineLeft);
                                }
                                int width;
                                if (lineLeft != 0) {
                                    width = titleLayout.getWidth() - lineLeft;
                                } else {
                                    width = (int) Math.ceil(titleLayout.getLineWidth(a));
                                }
                                if (a < restLines || lineLeft != 0 && isSmallImage) {
                                    width += AndroidUtilities.dp(48 + 4);
                                }
                                maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
                                maxWebWidth = Math.max(maxWebWidth, width + additinalWidth);
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (titleIsRTL && isSmallImage) {
                            linkPreviewMaxWidth -= AndroidUtilities.dp(48);
                        }
                    }

                    boolean authorIsRTL = false;
                    if (author != null && title == null) {
                        try {
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += AndroidUtilities.dp(2);
                                totalHeight += AndroidUtilities.dp(2);
                            }
                            if (restLinesCount == 3 && (!isSmallImage || description == null)) {
                                authorLayout = new StaticLayout(author, Theme.chat_replyNamePaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            } else {
                                authorLayout = generateStaticLayout(author, Theme.chat_replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - AndroidUtilities.dp(48 + 4), restLinesCount, 1);
                                restLinesCount -= authorLayout.getLineCount();
                            }
                            int height = authorLayout.getLineBottom(authorLayout.getLineCount() - 1);
                            linkPreviewHeight += height;
                            totalHeight += height;
                            int lineLeft = (int) authorLayout.getLineLeft(0);
                            authorX = -lineLeft;
                            int width;
                            if (lineLeft != 0) {
                                width = authorLayout.getWidth() - lineLeft;
                                authorIsRTL = true;
                            } else {
                                width = (int) Math.ceil(authorLayout.getLineWidth(0));
                            }
                            maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
                            maxWebWidth = Math.max(maxWebWidth, width + additinalWidth);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }

                    if (description != null) {
                        try {
                            descriptionX = 0;
                            currentMessageObject.generateLinkDescription();
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += AndroidUtilities.dp(2);
                                totalHeight += AndroidUtilities.dp(2);
                            }
                            int restLines = 0;
                            boolean allowAllLines = site_name != null && site_name.toLowerCase().equals("twitter");
                            if (restLinesCount == 3 && !isSmallImage) {
                                descriptionLayout = StaticLayoutEx.createStaticLayout(messageObject.linkDescription, Theme.chat_replyTextPaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false, TextUtils.TruncateAt.END, linkPreviewMaxWidth, allowAllLines ? 100 : 6);
                            } else {
                                restLines = restLinesCount;
                                descriptionLayout = generateStaticLayout(messageObject.linkDescription, Theme.chat_replyTextPaint, linkPreviewMaxWidth, linkPreviewMaxWidth - AndroidUtilities.dp(48 + 4), restLinesCount, allowAllLines ? 100 : 6);
                            }
                            int height = descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
                            linkPreviewHeight += height;
                            totalHeight += height;

                            boolean hasRTL = false;
                            for (int a = 0; a < descriptionLayout.getLineCount(); a++) {
                                int lineLeft = (int) Math.ceil(descriptionLayout.getLineLeft(a));
                                if (lineLeft != 0) {
                                    hasRTL = true;
                                    if (descriptionX == 0) {
                                        descriptionX = -lineLeft;
                                    } else {
                                        descriptionX = Math.max(descriptionX, -lineLeft);
                                    }
                                }
                            }

                            int textWidth = descriptionLayout.getWidth();
                            for (int a = 0; a < descriptionLayout.getLineCount(); a++) {
                                int lineLeft = (int) Math.ceil(descriptionLayout.getLineLeft(a));
                                if (lineLeft == 0 && descriptionX != 0) {
                                    descriptionX = 0;
                                }

                                int width;
                                if (lineLeft != 0) {
                                    width = textWidth - lineLeft;
                                } else {
                                    if (hasRTL) {
                                        width = textWidth;
                                    } else {
                                        width = Math.min((int) Math.ceil(descriptionLayout.getLineWidth(a)), textWidth);
                                    }
                                }
                                if (a < restLines || restLines != 0 && lineLeft != 0 && isSmallImage) {
                                    width += AndroidUtilities.dp(48 + 4);
                                }
                                if (maxWebWidth < width + additinalWidth) {
                                    if (titleIsRTL) {
                                        titleX += (width + additinalWidth - maxWebWidth);
                                    }
                                    if (authorIsRTL) {
                                        authorX += (width + additinalWidth - maxWebWidth);
                                    }
                                    maxWebWidth = width + additinalWidth;
                                }
                                maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }

                    if (smallImage && (descriptionLayout == null || descriptionLayout != null && descriptionLayout.getLineCount() == 1)) {
                        smallImage = false;
                        isSmallImage = false;
                    }
                    int maxPhotoWidth = smallImage ? AndroidUtilities.dp(48) : linkPreviewMaxWidth;

                    if (document != null) {
                        if (MessageObject.isRoundVideoDocument(document)) {
                            currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                            photoParentObject = document;
                            documentAttach = document;
                            documentAttachType = DOCUMENT_ATTACH_TYPE_ROUND;
                        } else if (MessageObject.isGifDocument(document)) {
                            if (!messageObject.isGame() && !SharedConfig.autoplayGifs) {
                                messageObject.gifState = 1;
                            }
                            photoImage.setAllowStartAnimation(messageObject.gifState != 1);
                            currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                            photoParentObject = document;
                            if (currentPhotoObject != null && (currentPhotoObject.w == 0 || currentPhotoObject.h == 0)) {
                                for (int a = 0; a < document.attributes.size(); a++) {
                                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                                    if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                        currentPhotoObject.w = attribute.w;
                                        currentPhotoObject.h = attribute.h;
                                        break;
                                    }
                                }
                                if (currentPhotoObject.w == 0 || currentPhotoObject.h == 0) {
                                    currentPhotoObject.w = currentPhotoObject.h = AndroidUtilities.dp(150);
                                }
                            }
                            documentAttach = document;
                            documentAttachType = DOCUMENT_ATTACH_TYPE_GIF;
                        } else if (MessageObject.isVideoDocument(document)) {
                            if (photo != null) {
                                currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize(), true);
                                currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 40);
                                photoParentObject = photo;
                            }
                            if (currentPhotoObject == null) {
                                currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                                currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 40);
                                photoParentObject = document;
                            }
                            if (currentPhotoObject == currentPhotoObjectThumb) {
                                currentPhotoObjectThumb = null;
                            }
                            if (currentPhotoObject == null) {
                                currentPhotoObject = new TLRPC.TL_photoSize();
                                currentPhotoObject.type = "s";
                                currentPhotoObject.location = new TLRPC.TL_fileLocationUnavailable();
                            }
                            if (currentPhotoObject != null && (currentPhotoObject.w == 0 || currentPhotoObject.h == 0 || currentPhotoObject instanceof TLRPC.TL_photoStrippedSize)) {
                                for (int a = 0; a < document.attributes.size(); a++) {
                                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                                    if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                        if (currentPhotoObject instanceof TLRPC.TL_photoStrippedSize) {
                                            float scale = Math.max(attribute.w, attribute.w) / 50.0f;
                                            currentPhotoObject.w = (int) (attribute.w / scale);
                                            currentPhotoObject.h = (int) (attribute.h / scale);
                                        } else {
                                            currentPhotoObject.w = attribute.w;
                                            currentPhotoObject.h = attribute.h;
                                        }
                                        break;
                                    }
                                }
                                if (currentPhotoObject.w == 0 || currentPhotoObject.h == 0) {
                                    currentPhotoObject.w = currentPhotoObject.h = AndroidUtilities.dp(150);
                                }
                            }
                            createDocumentLayout(0, messageObject);
                        } else if (MessageObject.isStickerDocument(document) || MessageObject.isAnimatedStickerDocument(document)) {
                            currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                            photoParentObject = document;
                            if (currentPhotoObject != null && (currentPhotoObject.w == 0 || currentPhotoObject.h == 0)) {
                                for (int a = 0; a < document.attributes.size(); a++) {
                                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                                    if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                                        currentPhotoObject.w = attribute.w;
                                        currentPhotoObject.h = attribute.h;
                                        break;
                                    }
                                }
                                if (currentPhotoObject.w == 0 || currentPhotoObject.h == 0) {
                                    currentPhotoObject.w = currentPhotoObject.h = AndroidUtilities.dp(150);
                                }
                            }
                            documentAttach = document;
                            documentAttachType = DOCUMENT_ATTACH_TYPE_STICKER;
                        } else if (drawInstantViewType == 6) {
                            currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                            photoParentObject = document;
                            if (currentPhotoObject != null && (currentPhotoObject.w == 0 || currentPhotoObject.h == 0)) {
                                for (int a = 0; a < document.attributes.size(); a++) {
                                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                                    if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                                        currentPhotoObject.w = attribute.w;
                                        currentPhotoObject.h = attribute.h;
                                        break;
                                    }
                                }
                                if (currentPhotoObject.w == 0 || currentPhotoObject.h == 0) {
                                    currentPhotoObject.w = currentPhotoObject.h = AndroidUtilities.dp(150);
                                }
                            }
                            documentAttach = document;
                            documentAttachType = DOCUMENT_ATTACH_TYPE_WALLPAPER;
                            String str = AndroidUtilities.formatFileSize(documentAttach.size);
                            durationWidth = (int) Math.ceil(Theme.chat_durationPaint.measureText(str));
                            videoInfoLayout = new StaticLayout(str, Theme.chat_durationPaint, durationWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        } else if (drawInstantViewType == 7) {
                            currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 700);
                            currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 40);
                            photoParentObject = document;
                            if (currentPhotoObject != null && (currentPhotoObject.w == 0 || currentPhotoObject.h == 0)) {
                                for (int a = 0; a < document.attributes.size(); a++) {
                                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                                    if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                                        currentPhotoObject.w = attribute.w;
                                        currentPhotoObject.h = attribute.h;
                                        break;
                                    }
                                }
                                if (currentPhotoObject.w == 0 || currentPhotoObject.h == 0) {
                                    currentPhotoObject.w = currentPhotoObject.h = AndroidUtilities.dp(150);
                                }
                            }
                            documentAttach = document;
                            documentAttachType = DOCUMENT_ATTACH_TYPE_THEME;
                        } else {
                            calcBackgroundWidth(maxWidth, timeMore, maxChildWidth);
                            if (backgroundWidth < maxWidth + AndroidUtilities.dp(20)) {
                                backgroundWidth = maxWidth + AndroidUtilities.dp(20);
                            }
                            if (MessageObject.isVoiceDocument(document)) {
                                createDocumentLayout(backgroundWidth - AndroidUtilities.dp(10), messageObject);
                                mediaOffsetY = currentMessageObject.textHeight + AndroidUtilities.dp(8) + linkPreviewHeight;
                                totalHeight += AndroidUtilities.dp(30 + 14);
                                linkPreviewHeight += AndroidUtilities.dp(44);

                                maxWidth = maxWidth - AndroidUtilities.dp(86);
                                if (AndroidUtilities.isTablet()) {
                                    maxChildWidth = Math.max(maxChildWidth, Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 52 : 0), AndroidUtilities.dp(220)) - AndroidUtilities.dp(30) + additinalWidth);
                                } else {
                                    maxChildWidth = Math.max(maxChildWidth, Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 52 : 0), AndroidUtilities.dp(220)) - AndroidUtilities.dp(30) + additinalWidth);
                                }
                                calcBackgroundWidth(maxWidth, timeMore, maxChildWidth);
                            } else if (MessageObject.isMusicDocument(document)) {
                                int durationWidth = createDocumentLayout(backgroundWidth - AndroidUtilities.dp(10), messageObject);
                                mediaOffsetY = currentMessageObject.textHeight + AndroidUtilities.dp(8) + linkPreviewHeight;
                                totalHeight += AndroidUtilities.dp(42 + 14);
                                linkPreviewHeight += AndroidUtilities.dp(56);

                                maxWidth = maxWidth - AndroidUtilities.dp(86);
                                maxChildWidth = Math.max(maxChildWidth, durationWidth + additinalWidth + AndroidUtilities.dp(86 + 8));
                                if (songLayout != null && songLayout.getLineCount() > 0) {
                                    maxChildWidth = (int) Math.max(maxChildWidth, songLayout.getLineWidth(0) + additinalWidth + AndroidUtilities.dp(86));
                                }
                                if (performerLayout != null && performerLayout.getLineCount() > 0) {
                                    maxChildWidth = (int) Math.max(maxChildWidth, performerLayout.getLineWidth(0) + additinalWidth + AndroidUtilities.dp(86));
                                }

                                calcBackgroundWidth(maxWidth, timeMore, maxChildWidth);
                            } else {
                                createDocumentLayout(backgroundWidth - AndroidUtilities.dp(86 + 24 + 58), messageObject);
                                drawImageButton = true;
                                if (drawPhotoImage) {
                                    totalHeight += AndroidUtilities.dp(86 + 14);
                                    linkPreviewHeight += AndroidUtilities.dp(86);
                                    photoImage.setImageCoords(0, totalHeight + namesOffset, AndroidUtilities.dp(86), AndroidUtilities.dp(86));
                                } else {
                                    mediaOffsetY = currentMessageObject.textHeight + AndroidUtilities.dp(8) + linkPreviewHeight;
                                    photoImage.setImageCoords(0, totalHeight + namesOffset - AndroidUtilities.dp(14), AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                                    totalHeight += AndroidUtilities.dp(50 + 14);
                                    linkPreviewHeight += AndroidUtilities.dp(50);
                                    if (docTitleLayout != null && docTitleLayout.getLineCount() > 1) {
                                        int h = (docTitleLayout.getLineCount() - 1) * AndroidUtilities.dp(16);
                                        totalHeight += h;
                                        linkPreviewHeight += h;
                                    }
                                }
                            }
                        }
                    } else if (photo != null) {
                        boolean isPhoto = type != null && type.equals("photo");
                        currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, isPhoto || !smallImage ? AndroidUtilities.getPhotoSize() : maxPhotoWidth, !isPhoto);
                        photoParentObject = messageObject.photoThumbsObject;
                        checkOnlyButtonPressed = !isPhoto;
                        currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40);
                        if (currentPhotoObjectThumb == currentPhotoObject) {
                            currentPhotoObjectThumb = null;
                        }
                    } else if (webDocument != null) {
                        if (!webDocument.mime_type.startsWith("image/")) {
                            webDocument = null;
                        }
                        drawImageButton = false;
                    }

                    if (documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC && documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO && documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        if (currentPhotoObject != null || webDocument != null) {
                            drawImageButton = photo != null && !smallImage || type != null && (type.equals("photo") || type.equals("document") && documentAttachType != DOCUMENT_ATTACH_TYPE_STICKER || type.equals("gif") || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER);
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += AndroidUtilities.dp(2);
                                totalHeight += AndroidUtilities.dp(2);
                            }

                            if (imageBackgroundSideColor != 0) {
                                maxPhotoWidth = AndroidUtilities.dp(208);
                            } else if (currentPhotoObject instanceof TLRPC.TL_photoSizeEmpty && currentPhotoObject.w != 0) {
                                maxPhotoWidth = currentPhotoObject.w;
                            } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER || documentAttachType == DOCUMENT_ATTACH_TYPE_THEME) {
                                if (AndroidUtilities.isTablet()) {
                                    maxPhotoWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.5f);
                                } else {
                                    maxPhotoWidth = (int) (AndroidUtilities.displaySize.x * 0.5f);
                                }
                            } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
                                maxPhotoWidth = AndroidUtilities.roundMessageSize;
                                photoImage.setAllowDecodeSingleFrame(true);
                            }

                            maxChildWidth = Math.max(maxChildWidth, maxPhotoWidth - (hasInvoicePreview ? AndroidUtilities.dp(12) : 0) + additinalWidth);
                            if (currentPhotoObject != null) {
                                currentPhotoObject.size = -1;
                                if (currentPhotoObjectThumb != null) {
                                    currentPhotoObjectThumb.size = -1;
                                }
                            } else {
                                webDocument.size = -1;
                            }
                            if (imageBackgroundSideColor != 0) {
                                imageBackgroundSideWidth = maxChildWidth - AndroidUtilities.dp(13);
                            }

                            int width;
                            int height;
                            if (smallImage || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
                                width = height = maxPhotoWidth;
                            } else {
                                if (hasGamePreview || hasInvoicePreview) {
                                    width = 640;
                                    height = 360;
                                    float scale = width / (float) (maxPhotoWidth - AndroidUtilities.dp(2));
                                    width /= scale;
                                    height /= scale;
                                } else {
                                    width = currentPhotoObject.w;
                                    height = currentPhotoObject.h;
                                    float scale = width / (float) (maxPhotoWidth - AndroidUtilities.dp(2));
                                    width /= scale;
                                    height /= scale;
                                    if (site_name == null || site_name != null && !site_name.toLowerCase().equals("instagram") && documentAttachType == 0) {
                                        if (height > AndroidUtilities.displaySize.y / 3) {
                                            height = AndroidUtilities.displaySize.y / 3;
                                        }
                                    } else {
                                        if (height > AndroidUtilities.displaySize.y / 2) {
                                            height = AndroidUtilities.displaySize.y / 2;
                                        }
                                    }
                                    if (imageBackgroundSideColor != 0) {
                                        scale = height / (float) AndroidUtilities.dp(160);
                                        width /= scale;
                                        height /= scale;
                                    }
                                    if (height < AndroidUtilities.dp(60)) {
                                        height = AndroidUtilities.dp(60);
                                    }
                                }
                            }
                            if (isSmallImage) {
                                if (AndroidUtilities.dp(50) + additionalHeight > linkPreviewHeight) {
                                    totalHeight += AndroidUtilities.dp(50) + additionalHeight - linkPreviewHeight + AndroidUtilities.dp(8);
                                    linkPreviewHeight = AndroidUtilities.dp(50) + additionalHeight;
                                }
                                linkPreviewHeight -= AndroidUtilities.dp(8);
                            } else {
                                totalHeight += height + AndroidUtilities.dp(12);
                                linkPreviewHeight += height;
                            }

                            if (documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER && imageBackgroundSideColor == 0) {
                                photoImage.setImageCoords(0, 0, Math.max(maxChildWidth - AndroidUtilities.dp(13), width), height);
                            } else {
                                photoImage.setImageCoords(0, 0, width, height);
                            }

                            currentPhotoFilter = String.format(Locale.US, "%d_%d", width, height);
                            currentPhotoFilterThumb = String.format(Locale.US, "%d_%d_b", width, height);

                            if (webDocument != null) {
                                photoImage.setImage(ImageLocation.getForWebFile(webDocument), currentPhotoFilter, null, null, webDocument.size, null, messageObject, 1);
                            } else {
                                if (documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER) {
                                    if (messageObject.mediaExists) {
                                        photoImage.setImage(ImageLocation.getForDocument(documentAttach), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObject, document), "b1", 0, "jpg", messageObject, 1);
                                    } else {
                                        photoImage.setImage(null, null, ImageLocation.getForDocument(currentPhotoObject, document), "b1", 0, "jpg", messageObject, 1);
                                    }
                                } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_THEME) {
                                    photoImage.setImage(ImageLocation.getForDocument(currentPhotoObject, document), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObjectThumb, document), "b1", 0, "jpg", messageObject, 1);
                                } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER) {
                                    boolean isWebpSticker = messageObject.isSticker();
                                    if (SharedConfig.loopStickers || isWebpSticker) {
                                        photoImage.setAutoRepeat(1);
                                    } else {
                                        currentPhotoFilter = String.format(Locale.US, "%d_%d_nr_%s", width, height, messageObject.toString());
                                        photoImage.setAutoRepeat(delegate.shouldRepeatSticker(messageObject) ? 2 : 3);
                                    }
                                    photoImage.setImage(ImageLocation.getForDocument(documentAttach), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObject, documentAttach), "b1", documentAttach.size, "webp", messageObject, 1);
                                } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                                    photoImage.setNeedsQualityThumb(true);
                                    photoImage.setShouldGenerateQualityThumb(true);
                                    if (SharedConfig.autoplayVideo && (
                                            currentMessageObject.mediaExists ||
                                                    messageObject.canStreamVideo() && DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject)
                                    )) {
                                        photoImage.setAllowDecodeSingleFrame(true);
                                        photoImage.setAllowStartAnimation(true);
                                        photoImage.startAnimation();
                                        photoImage.setImage(ImageLocation.getForDocument(documentAttach), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForDocument(currentPhotoObject, documentAttach), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObjectThumb, documentAttach), currentPhotoFilterThumb, null, documentAttach.size, null, messageObject, 0);
                                        autoPlayingMedia = true;
                                    } else {
                                        if (currentPhotoObjectThumb != null) {
                                            photoImage.setImage(ImageLocation.getForDocument(currentPhotoObject, documentAttach), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObjectThumb, documentAttach), currentPhotoFilterThumb, 0, null, messageObject, 0);
                                        } else {
                                            photoImage.setImage(null, null, ImageLocation.getForDocument(currentPhotoObject, documentAttach), currentPhotoObject instanceof TLRPC.TL_photoStrippedSize || "s".equals(currentPhotoObject.type) ? currentPhotoFilterThumb : currentPhotoFilter, 0, null, messageObject, 0);
                                        }
                                    }
                                } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
                                    photoImage.setAllowDecodeSingleFrame(true);
                                    String fileName = FileLoader.getAttachFileName(document);
                                    boolean autoDownload = false;
                                    if (MessageObject.isRoundVideoDocument(document)) {
                                        photoImage.setRoundRadius(AndroidUtilities.roundMessageSize / 2);
                                        autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject);
                                    } else if (MessageObject.isGifDocument(document)) {
                                        autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject);
                                    }
                                    String filter = currentPhotoObject instanceof TLRPC.TL_photoStrippedSize || "s".equals(currentPhotoObject.type) ? currentPhotoFilterThumb : currentPhotoFilter;
                                    if (messageObject.mediaExists || autoDownload) {
                                        autoPlayingMedia = true;
                                        photoImage.setImage(ImageLocation.getForDocument(document), document.size < 1024 * 32 ? null : ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForDocument(currentPhotoObject, documentAttach), filter, ImageLocation.getForDocument(currentPhotoObjectThumb, documentAttach), currentPhotoFilterThumb, null, document.size, null, messageObject, 0);
                                    } else {
                                        photoImage.setImage(null, null, ImageLocation.getForDocument(currentPhotoObject, documentAttach), filter, 0, null, currentMessageObject, 0);
                                    }
                                } else {
                                    boolean photoExist = messageObject.mediaExists;
                                    String fileName = FileLoader.getAttachFileName(currentPhotoObject);
                                    if (hasGamePreview || photoExist || DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject) || FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                                        photoNotSet = false;
                                        photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, 0, null, messageObject, 0);
                                    } else {
                                        photoNotSet = true;
                                        if (currentPhotoObjectThumb != null) {
                                            photoImage.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), String.format(Locale.US, "%d_%d_b", width, height), 0, null, messageObject, 0);
                                        } else {
                                            photoImage.setImageBitmap((Drawable) null);
                                        }
                                    }
                                }
                            }
                            drawPhotoImage = true;

                            if (type != null && type.equals("video") && duration != 0) {
                                int minutes = duration / 60;
                                int seconds = duration - minutes * 60;
                                String str = String.format("%d:%02d", minutes, seconds);
                                durationWidth = (int) Math.ceil(Theme.chat_durationPaint.measureText(str));
                                videoInfoLayout = new StaticLayout(str, Theme.chat_durationPaint, durationWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            } else if (hasGamePreview) {
                                String str = LocaleController.getString("AttachGame", R.string.AttachGame).toUpperCase();
                                durationWidth = (int) Math.ceil(Theme.chat_gamePaint.measureText(str));
                                videoInfoLayout = new StaticLayout(str, Theme.chat_gamePaint, durationWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            }
                        } else {
                            photoImage.setImageBitmap((Drawable) null);
                            linkPreviewHeight -= AndroidUtilities.dp(6);
                            totalHeight += AndroidUtilities.dp(4);
                        }
                        if (hasInvoicePreview) {
                            CharSequence str;
                            if ((messageObject.messageOwner.media.flags & 4) != 0) {
                                str = LocaleController.getString("PaymentReceipt", R.string.PaymentReceipt).toUpperCase();
                            } else {
                                if (messageObject.messageOwner.media.test) {
                                    str = LocaleController.getString("PaymentTestInvoice", R.string.PaymentTestInvoice).toUpperCase();
                                } else {
                                    str = LocaleController.getString("PaymentInvoice", R.string.PaymentInvoice).toUpperCase();
                                }
                            }
                            String price = LocaleController.getInstance().formatCurrencyString(messageObject.messageOwner.media.total_amount, messageObject.messageOwner.media.currency);
                            SpannableStringBuilder stringBuilder = new SpannableStringBuilder(price + " " + str);
                            stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), 0, price.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            durationWidth = (int) Math.ceil(Theme.chat_shipmentPaint.measureText(stringBuilder, 0, stringBuilder.length()));
                            videoInfoLayout = new StaticLayout(stringBuilder, Theme.chat_shipmentPaint, durationWidth + AndroidUtilities.dp(10), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            if (!drawPhotoImage) {
                                totalHeight += AndroidUtilities.dp(6);
                                int timeWidthTotal = timeWidth + AndroidUtilities.dp(14 + (messageObject.isOutOwner() ? 20 : 0));
                                if (durationWidth + timeWidthTotal > maxWidth) {
                                    maxChildWidth = Math.max(durationWidth, maxChildWidth);
                                    totalHeight += AndroidUtilities.dp(12);
                                } else {
                                    maxChildWidth = Math.max(durationWidth + timeWidthTotal, maxChildWidth);
                                }
                            }
                        }
                        if (hasGamePreview && messageObject.textHeight != 0) {
                            linkPreviewHeight += messageObject.textHeight + AndroidUtilities.dp(6);
                            totalHeight += AndroidUtilities.dp(4);
                        }
                        calcBackgroundWidth(maxWidth, timeMore, maxChildWidth);
                    }
                    createInstantViewButton();
                } else {
                    photoImage.setImageBitmap((Drawable) null);
                    calcBackgroundWidth(maxWidth, timeMore, maxChildWidth);
                }
            } else if (messageObject.type == 16) {
                drawName = false;
                drawForwardedName = false;
                drawPhotoImage = false;
                if (AndroidUtilities.isTablet()) {
                    backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                } else {
                    backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                }
                availableTimeWidth = backgroundWidth - AndroidUtilities.dp(31);

                int maxWidth = getMaxNameWidth() - AndroidUtilities.dp(50);
                if (maxWidth < 0) {
                    maxWidth = AndroidUtilities.dp(10);
                }

                String text;
                String time = LocaleController.getInstance().formatterDay.format((long) (messageObject.messageOwner.date) * 1000);
                TLRPC.TL_messageActionPhoneCall call = (TLRPC.TL_messageActionPhoneCall) messageObject.messageOwner.action;
                boolean isMissed = call.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed;
                if (messageObject.isOutOwner()) {
                    if (isMissed) {
                        text = LocaleController.getString("CallMessageOutgoingMissed", R.string.CallMessageOutgoingMissed);
                    } else {
                        text = LocaleController.getString("CallMessageOutgoing", R.string.CallMessageOutgoing);
                    }
                } else {
                    if (isMissed) {
                        text = LocaleController.getString("CallMessageIncomingMissed", R.string.CallMessageIncomingMissed);
                    } else if (call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) {
                        text = LocaleController.getString("CallMessageIncomingDeclined", R.string.CallMessageIncomingDeclined);
                    } else {
                        text = LocaleController.getString("CallMessageIncoming", R.string.CallMessageIncoming);
                    }
                }
                if (call.duration > 0) {
                    time += ", " + LocaleController.formatCallDuration(call.duration);
                }

                titleLayout = new StaticLayout(TextUtils.ellipsize(text, Theme.chat_audioTitlePaint, maxWidth, TextUtils.TruncateAt.END), Theme.chat_audioTitlePaint, maxWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                docTitleLayout = new StaticLayout(TextUtils.ellipsize(time, Theme.chat_contactPhonePaint, maxWidth, TextUtils.TruncateAt.END), Theme.chat_contactPhonePaint, maxWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                setMessageObjectInternal(messageObject);

                totalHeight = AndroidUtilities.dp(65) + namesOffset;
                if (drawPinnedTop) {
                    namesOffset -= AndroidUtilities.dp(1);
                }
            } else if (messageObject.type == 12) {
                drawName = false;
                drawForwardedName = true;
                drawPhotoImage = true;
                photoImage.setRoundRadius(AndroidUtilities.dp(22));
                if (AndroidUtilities.isTablet()) {
                    backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                } else {
                    backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                }
                availableTimeWidth = backgroundWidth - AndroidUtilities.dp(31);

                int uid = messageObject.messageOwner.media.user_id;
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(uid);

                int maxWidth = getMaxNameWidth() - AndroidUtilities.dp(80);
                if (maxWidth < 0) {
                    maxWidth = AndroidUtilities.dp(10);
                }
                if (user != null) {
                    contactAvatarDrawable.setInfo(user);
                }
                photoImage.setImage(ImageLocation.getForUser(user, false), "50_50", user != null ? contactAvatarDrawable : Theme.chat_contactDrawable[messageObject.isOutOwner() ? 1 : 0], null, messageObject, 0);

                CharSequence phone;
                if (!TextUtils.isEmpty(messageObject.vCardData)) {
                    phone = messageObject.vCardData;
                    drawInstantView = true;
                    drawInstantViewType = 5;
                } else {
                    if (user != null && !TextUtils.isEmpty(user.phone)) {
                        phone = PhoneFormat.getInstance().format("+" + user.phone);
                    } else {
                        phone = messageObject.messageOwner.media.phone_number;
                        if (!TextUtils.isEmpty(phone)) {
                            phone = PhoneFormat.getInstance().format((String) phone);
                        } else {
                            phone = LocaleController.getString("NumberUnknown", R.string.NumberUnknown);
                        }
                    }
                }

                CharSequence currentNameString = ContactsController.formatName(messageObject.messageOwner.media.first_name, messageObject.messageOwner.media.last_name).replace('\n', ' ');
                if (currentNameString.length() == 0) {
                    currentNameString = messageObject.messageOwner.media.phone_number;
                    if (currentNameString == null) {
                        currentNameString = "";
                    }
                }
                titleLayout = new StaticLayout(TextUtils.ellipsize(currentNameString, Theme.chat_contactNamePaint, maxWidth, TextUtils.TruncateAt.END), Theme.chat_contactNamePaint, maxWidth + AndroidUtilities.dp(4), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                docTitleLayout = new StaticLayout(phone, Theme.chat_contactPhonePaint, maxWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false);

                setMessageObjectInternal(messageObject);

                if (drawForwardedName && messageObject.needDrawForwarded() && (currentPosition == null || currentPosition.minY == 0)) {
                    namesOffset += AndroidUtilities.dp(5);
                } else if (drawNameLayout && messageObject.messageOwner.reply_to_msg_id == 0) {
                    namesOffset += AndroidUtilities.dp(7);
                }

                totalHeight = AndroidUtilities.dp(70 - 15) + namesOffset + docTitleLayout.getHeight();
                if (drawPinnedTop) {
                    namesOffset -= AndroidUtilities.dp(1);
                }
                if (drawInstantView) {
                    createInstantViewButton();
                } else {
                    if (docTitleLayout.getLineCount() > 0) {
                        int timeLeft = backgroundWidth - AndroidUtilities.dp(40 + 18 + 44 + 8) - (int) Math.ceil(docTitleLayout.getLineWidth(docTitleLayout.getLineCount() - 1));
                        if (timeLeft < timeWidth) {
                            totalHeight += AndroidUtilities.dp(8);
                        }
                    }
                }
            } else if (messageObject.type == 2) {
                drawForwardedName = true;
                if (AndroidUtilities.isTablet()) {
                    backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                } else {
                    backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                }
                createDocumentLayout(backgroundWidth, messageObject);

                setMessageObjectInternal(messageObject);

                totalHeight = AndroidUtilities.dp(70) + namesOffset;
                if (drawPinnedTop) {
                    namesOffset -= AndroidUtilities.dp(1);
                }
            } else if (messageObject.type == 14) {
                if (AndroidUtilities.isTablet()) {
                    backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                } else {
                    backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                }

                createDocumentLayout(backgroundWidth, messageObject);

                setMessageObjectInternal(messageObject);

                totalHeight = AndroidUtilities.dp(82) + namesOffset;
                if (drawPinnedTop) {
                    namesOffset -= AndroidUtilities.dp(1);
                }
            } else if (messageObject.type == MessageObject.TYPE_POLL) {
                createSelectorDrawable();
                drawName = true;
                drawForwardedName = true;
                drawPhotoImage = false;
                int maxWidth = availableTimeWidth = Math.min(AndroidUtilities.dp(500), messageObject.getMaxMessageTextWidth());
                backgroundWidth = maxWidth + AndroidUtilities.dp(31);
                availableTimeWidth = AndroidUtilities.dp(120);
                measureTime(messageObject);

                TLRPC.TL_messageMediaPoll media = (TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media;

                pollClosed = media.poll.closed;
                pollVoted = messageObject.isVoted();
                titleLayout = new StaticLayout(Emoji.replaceEmoji(media.poll.question, Theme.chat_audioTitlePaint.getFontMetricsInt(), AndroidUtilities.dp(16), false), Theme.chat_audioTitlePaint, maxWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                boolean titleRtl = false;
                if (titleLayout != null) {
                    for (int a = 0, N = titleLayout.getLineCount(); a < N; a++) {
                        if (titleLayout.getLineLeft(a) != 0) {
                            titleRtl = true;
                            break;
                        }
                    }
                }
                docTitleLayout = new StaticLayout(TextUtils.ellipsize(media.poll.closed ? LocaleController.getString("FinalResults", R.string.FinalResults) : LocaleController.getString("AnonymousPoll", R.string.AnonymousPoll), Theme.chat_timePaint, maxWidth, TextUtils.TruncateAt.END), Theme.chat_timePaint, maxWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (docTitleLayout != null && docTitleLayout.getLineCount() > 0) {
                    if (titleRtl && !LocaleController.isRTL) {
                        docTitleOffsetX = (int) Math.ceil(maxWidth - docTitleLayout.getLineWidth(0));
                    } else if (!titleRtl && LocaleController.isRTL) {
                        docTitleOffsetX = -(int) Math.ceil(docTitleLayout.getLineLeft(0));
                    }
                }
                int w = maxWidth - timeWidth - AndroidUtilities.dp(messageObject.isOutOwner() ? 28 : 8);
                infoLayout = new StaticLayout(TextUtils.ellipsize(media.results.total_voters == 0 ? LocaleController.getString("NoVotes", R.string.NoVotes) : LocaleController.formatPluralString("Vote", media.results.total_voters), Theme.chat_livePaint, w, TextUtils.TruncateAt.END), Theme.chat_livePaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                infoX = (int) Math.ceil(infoLayout != null && infoLayout.getLineCount() > 0 ? -infoLayout.getLineLeft(0) : 0);

                lastPoll = media.poll;
                lastPollResults = media.results.results;
                lastPollResultsVoters = media.results.total_voters;

                int maxVote = 0;
                if (!animatePollAnswer && pollVoteInProgress) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                }
                animatePollAnswerAlpha = animatePollAnswer = attachedToWindow && (pollVoteInProgress || pollUnvoteInProgress);
                ArrayList<PollButton> previousPollButtons = null;
                ArrayList<PollButton> sortedPollButtons = new ArrayList<>();
                if (!pollButtons.isEmpty()) {
                    previousPollButtons = new ArrayList<>(pollButtons);
                    pollButtons.clear();
                    if (!animatePollAnswer) {
                        animatePollAnswer = attachedToWindow && (pollVoted || pollClosed);
                    }
                    if (pollAnimationProgress > 0 && pollAnimationProgress < 1.0f) {
                        for (int b = 0, N2 = previousPollButtons.size(); b < N2; b++) {
                            PollButton button = previousPollButtons.get(b);
                            button.percent = (int) Math.ceil(button.prevPercent + (button.percent - button.prevPercent) * pollAnimationProgress);
                            button.percentProgress = button.prevPercentProgress + (button.percentProgress - button.prevPercentProgress) * pollAnimationProgress;
                        }
                    }
                }

                pollAnimationProgress = animatePollAnswer ? 0.0f : 1.0f;
                byte[] votingFor;
                if (!animatePollAnswerAlpha) {
                    pollVoteInProgress = false;
                    pollVoteInProgressNum = -1;
                    votingFor = SendMessagesHelper.getInstance(currentAccount).isSendingVote(currentMessageObject);
                } else {
                    votingFor = null;
                }

                int height = titleLayout != null ? titleLayout.getHeight() : 0;
                int restPercent = 100;
                boolean hasDifferent = false;
                int previousPercent = 0;
                for (int a = 0, N = media.poll.answers.size(); a < N; a++) {
                    PollButton button = new PollButton();
                    button.answer = media.poll.answers.get(a);
                    button.title = new StaticLayout(Emoji.replaceEmoji(button.answer.text, Theme.chat_audioPerformerPaint.getFontMetricsInt(), AndroidUtilities.dp(15), false), Theme.chat_audioPerformerPaint, maxWidth - AndroidUtilities.dp(33), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    button.y = height + AndroidUtilities.dp(52);
                    button.height = button.title.getHeight();
                    pollButtons.add(button);
                    sortedPollButtons.add(button);
                    height += button.height + AndroidUtilities.dp(26);
                    if (!media.results.results.isEmpty()) {
                        for (int b = 0, N2 = media.results.results.size(); b < N2; b++) {
                            TLRPC.TL_pollAnswerVoters answer = media.results.results.get(b);
                            if (Arrays.equals(button.answer.option, answer.option)) {
                                if ((pollVoted || pollClosed) && media.results.total_voters > 0) {
                                    button.decimal = 100 * (answer.voters / (float) media.results.total_voters);
                                    button.percent = (int) button.decimal;
                                    button.decimal -= button.percent;
                                } else {
                                    button.percent = 0;
                                    button.decimal = 0;
                                }
                                if (previousPercent == 0) {
                                    previousPercent = button.percent;
                                } else if (button.percent != 0 && previousPercent != button.percent) {
                                    hasDifferent = true;
                                }
                                restPercent -= button.percent;
                                maxVote = Math.max(button.percent, maxVote);
                                break;
                            }
                        }
                    }
                    if (previousPollButtons != null) {
                        for (int b = 0, N2 = previousPollButtons.size(); b < N2; b++) {
                            PollButton prevButton = previousPollButtons.get(b);
                            if (Arrays.equals(button.answer.option, prevButton.answer.option)) {
                                button.prevPercent = prevButton.percent;
                                button.prevPercentProgress = prevButton.percentProgress;
                                break;
                            }
                        }
                    }
                    if (votingFor != null && Arrays.equals(button.answer.option, votingFor)) {
                        pollVoteInProgressNum = a;
                        pollVoteInProgress = true;
                        votingFor = null;
                    }
                }
                if (hasDifferent && restPercent != 0) {
                    Collections.sort(sortedPollButtons, (o1, o2) -> {
                        if (o1.decimal > o2.decimal) {
                            return -1;
                        } else if (o1.decimal < o2.decimal) {
                            return 1;
                        }
                        return 0;
                    });
                    for (int a = 0, N = Math.min(restPercent, sortedPollButtons.size()); a < N; a++) {
                        sortedPollButtons.get(a).percent += 1;
                    }
                }
                int width = backgroundWidth - AndroidUtilities.dp(76);
                for (int b = 0, N2 = pollButtons.size(); b < N2; b++) {
                    PollButton button = pollButtons.get(b);
                    button.percentProgress = Math.max(AndroidUtilities.dp(5) / (float) width, maxVote != 0 ? button.percent / (float) maxVote : 0);
                }

                setMessageObjectInternal(messageObject);

                totalHeight = AndroidUtilities.dp(46 + 27) + namesOffset + height;
                if (drawPinnedTop) {
                    namesOffset -= AndroidUtilities.dp(1);
                }
            } else {
                drawForwardedName = messageObject.messageOwner.fwd_from != null && !messageObject.isAnyKindOfSticker();
                mediaBackground = messageObject.type != 9;
                drawImageButton = true;
                drawPhotoImage = true;

                int photoWidth = 0;
                int photoHeight = 0;
                int additionHeight = 0;

                if (messageObject.gifState != 2 && !SharedConfig.autoplayGifs && (messageObject.type == 8 || messageObject.type == MessageObject.TYPE_ROUND_VIDEO)) {
                    messageObject.gifState = 1;
                }

                photoImage.setAllowDecodeSingleFrame(true);
                if (messageObject.isVideo()) {
                    photoImage.setAllowStartAnimation(true);
                } else if (messageObject.isRoundVideo()) {
                    MessageObject playingMessage = MediaController.getInstance().getPlayingMessageObject();
                    photoImage.setAllowStartAnimation(playingMessage == null || !playingMessage.isRoundVideo());
                } else {
                    photoImage.setAllowStartAnimation(messageObject.gifState == 0);
                }

                photoImage.setForcePreview(messageObject.needDrawBluredPreview());
                if (messageObject.type == 9) {
                    if (AndroidUtilities.isTablet()) {
                        backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(300));
                    } else {
                        backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(300));
                    }
                    if (checkNeedDrawShareButton(messageObject)) {
                        backgroundWidth -= AndroidUtilities.dp(20);
                    }
                    int maxTextWidth = 0;
                    int maxWidth = backgroundWidth - AndroidUtilities.dp(86 + 52);
                    int widthForCaption = 0;
                    createDocumentLayout(maxWidth, messageObject);
                    if (!TextUtils.isEmpty(messageObject.caption)) {
                        try {
                            currentCaption = messageObject.caption;
                            int width = backgroundWidth - AndroidUtilities.dp(31);
                            widthForCaption = width - AndroidUtilities.dp(10);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                captionLayout = StaticLayout.Builder.obtain(messageObject.caption, 0, messageObject.caption.length(), Theme.chat_msgTextPaint, widthForCaption)
                                        .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                                        .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                        .build();
                            } else {
                                captionLayout = new StaticLayout(messageObject.caption, Theme.chat_msgTextPaint, widthForCaption, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }

                    if (docTitleLayout != null) {
                        for (int a = 0, N = docTitleLayout.getLineCount(); a < N; a++) {
                            maxTextWidth = Math.max(maxTextWidth, (int) Math.ceil(docTitleLayout.getLineWidth(a) + docTitleLayout.getLineLeft(a)) + AndroidUtilities.dp(86 + (drawPhotoImage ? 52 : 22)));
                        }
                    }
                    if (infoLayout != null) {
                        for (int a = 0, N = infoLayout.getLineCount(); a < N; a++) {
                            maxTextWidth = Math.max(maxTextWidth, (int) Math.ceil(infoLayout.getLineWidth(a)) + AndroidUtilities.dp(86 + (drawPhotoImage ? 52 : 22)));
                        }
                    }
                    if (captionLayout != null) {
                        for (int a = 0, N = captionLayout.getLineCount(); a < N; a++) {
                            int w = (int) Math.ceil(Math.min(widthForCaption, captionLayout.getLineWidth(a) + captionLayout.getLineLeft(a))) + AndroidUtilities.dp(31);
                            if (w > maxTextWidth) {
                                maxTextWidth = w;
                            }
                        }
                    }

                    if (maxTextWidth > 0) {
                        backgroundWidth = maxTextWidth;
                        maxWidth = maxTextWidth - AndroidUtilities.dp(31);
                    }
                    if (drawPhotoImage) {
                        photoWidth = AndroidUtilities.dp(86);
                        photoHeight = AndroidUtilities.dp(86);
                    } else {
                        photoWidth = AndroidUtilities.dp(56);
                        photoHeight = AndroidUtilities.dp(56);
                        if (docTitleLayout != null && docTitleLayout.getLineCount() > 1) {
                            photoHeight += (docTitleLayout.getLineCount() - 1) * AndroidUtilities.dp(16);
                        }
                    }
                    availableTimeWidth = maxWidth;
                    if (!drawPhotoImage && TextUtils.isEmpty(messageObject.caption) && infoLayout != null) {
                        int lineCount = infoLayout.getLineCount();
                        measureTime(messageObject);
                        int timeLeft = backgroundWidth - AndroidUtilities.dp(40 + 18 + 56 + 8) - (int) Math.ceil(infoLayout.getLineWidth(0));
                        if (timeLeft < timeWidth) {
                            photoHeight += AndroidUtilities.dp(12);
                        } else if (lineCount == 1) {
                            photoHeight += AndroidUtilities.dp(4);
                        }
                    }
                } else if (messageObject.type == 4) { //geo
                    TLRPC.GeoPoint point = messageObject.messageOwner.media.geo;
                    double lat = point.lat;
                    double lon = point._long;

                    if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                        if (AndroidUtilities.isTablet()) {
                            backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(252 + 37));
                        } else {
                            backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(252 + 37));
                        }
                        backgroundWidth -= AndroidUtilities.dp(4);
                        if (checkNeedDrawShareButton(messageObject)) {
                            backgroundWidth -= AndroidUtilities.dp(20);
                        }
                        int maxWidth = backgroundWidth - AndroidUtilities.dp(37);
                        availableTimeWidth = maxWidth;
                        maxWidth -= AndroidUtilities.dp(54);

                        photoWidth = backgroundWidth - AndroidUtilities.dp(17);
                        photoHeight = AndroidUtilities.dp(195);

                        int offset = 268435456;
                        double rad = offset / Math.PI;
                        double y = Math.round(offset - rad * Math.log((1 + Math.sin(lat * Math.PI / 180.0)) / (1 - Math.sin(lat * Math.PI / 180.0))) / 2) - (AndroidUtilities.dp(10.3f) << (21 - 15));
                        lat = (Math.PI / 2.0 - 2 * Math.atan(Math.exp((y - offset) / rad))) * 180.0 / Math.PI;
                        currentUrl = AndroidUtilities.formapMapUrl(currentAccount, lat, lon, (int) (photoWidth / AndroidUtilities.density), (int) (photoHeight / AndroidUtilities.density), false, 15);
                        currentWebFile = WebFile.createWithGeoPoint(lat, lon, point.access_hash, (int) (photoWidth / AndroidUtilities.density), (int) (photoHeight / AndroidUtilities.density), 15, Math.min(2, (int) Math.ceil(AndroidUtilities.density)));

                        if (!(locationExpired = isCurrentLocationTimeExpired(messageObject))) {
                            photoImage.setCrossfadeWithOldImage(true);
                            mediaBackground = false;
                            additionHeight = AndroidUtilities.dp(56);
                            AndroidUtilities.runOnUIThread(invalidateRunnable, 1000);
                            scheduledInvalidate = true;
                        } else {
                            backgroundWidth -= AndroidUtilities.dp(9);
                        }
                        docTitleLayout = new StaticLayout(TextUtils.ellipsize(LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation), Theme.chat_locationTitlePaint, maxWidth, TextUtils.TruncateAt.END), Theme.chat_locationTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                        updateCurrentUserAndChat();
                        if (currentUser != null) {
                            contactAvatarDrawable.setInfo(currentUser);
                            locationImageReceiver.setImage(ImageLocation.getForUser(currentUser, false), "50_50", contactAvatarDrawable, null, currentUser, 0);
                        } else if (currentChat != null) {
                            if (currentChat.photo != null) {
                                currentPhoto = currentChat.photo.photo_small;
                            }
                            contactAvatarDrawable.setInfo(currentChat);
                            locationImageReceiver.setImage(ImageLocation.getForChat(currentChat, false), "50_50", contactAvatarDrawable, null, currentChat, 0);
                        } else {
                            locationImageReceiver.setImage(null, null, contactAvatarDrawable, null, null, 0);
                        }
                        infoLayout = new StaticLayout(LocaleController.formatLocationUpdateDate(messageObject.messageOwner.edit_date != 0 && !messageObject.messageOwner.edit_hide ? messageObject.messageOwner.edit_date : messageObject.messageOwner.date), Theme.chat_locationAddressPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    } else if (!TextUtils.isEmpty(messageObject.messageOwner.media.title)) {
                        if (AndroidUtilities.isTablet()) {
                            backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(252 + 37));
                        } else {
                            backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(252 + 37));
                        }
                        backgroundWidth -= AndroidUtilities.dp(4);
                        if (checkNeedDrawShareButton(messageObject)) {
                            backgroundWidth -= AndroidUtilities.dp(20);
                        }
                        int maxWidth = backgroundWidth - AndroidUtilities.dp(34);
                        availableTimeWidth = maxWidth;

                        photoWidth = backgroundWidth - AndroidUtilities.dp(17);
                        photoHeight = AndroidUtilities.dp(195);

                        mediaBackground = false;
                        currentUrl = AndroidUtilities.formapMapUrl(currentAccount, lat, lon, (int) (photoWidth / AndroidUtilities.density), (int) (photoHeight / AndroidUtilities.density), true, 15);
                        currentWebFile = WebFile.createWithGeoPoint(point, (int) (photoWidth / AndroidUtilities.density), (int) (photoHeight / AndroidUtilities.density), 15, Math.min(2, (int) Math.ceil(AndroidUtilities.density)));

                        docTitleLayout = StaticLayoutEx.createStaticLayout(messageObject.messageOwner.media.title, Theme.chat_locationTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth, 1);
                        additionHeight += AndroidUtilities.dp(50);
                        int lineCount = docTitleLayout.getLineCount();
                        if (!TextUtils.isEmpty(messageObject.messageOwner.media.address)) {
                            infoLayout = StaticLayoutEx.createStaticLayout(messageObject.messageOwner.media.address, Theme.chat_locationAddressPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth, 1);
                            measureTime(messageObject);
                            int timeLeft = backgroundWidth - (int) Math.ceil(infoLayout.getLineWidth(0)) - AndroidUtilities.dp(24);
                            if (timeLeft < timeWidth + AndroidUtilities.dp(20 + (messageObject.isOutOwner() ? 20 : 0))) {
                                additionHeight += AndroidUtilities.dp(8);
                            }
                        } else {
                            infoLayout = null;
                        }
                    } else {
                        if (AndroidUtilities.isTablet()) {
                            backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(252 + 37));
                        } else {
                            backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(252 + 37));
                        }
                        backgroundWidth -= AndroidUtilities.dp(4);
                        if (checkNeedDrawShareButton(messageObject)) {
                            backgroundWidth -= AndroidUtilities.dp(20);
                        }
                        availableTimeWidth = backgroundWidth - AndroidUtilities.dp(34);

                        photoWidth = backgroundWidth - AndroidUtilities.dp(8);
                        photoHeight = AndroidUtilities.dp(195);

                        currentUrl = AndroidUtilities.formapMapUrl(currentAccount, lat, lon, (int) (photoWidth / AndroidUtilities.density), (int) (photoHeight / AndroidUtilities.density), true, 15);
                        currentWebFile = WebFile.createWithGeoPoint(point, (int) (photoWidth / AndroidUtilities.density), (int) (photoHeight / AndroidUtilities.density), 15, Math.min(2, (int) Math.ceil(AndroidUtilities.density)));
                    }
                    if ((int) messageObject.getDialogId() == 0) {
                        if (SharedConfig.mapPreviewType == 0) {
                            currentMapProvider = 2;
                        } else if (SharedConfig.mapPreviewType == 1) {
                            currentMapProvider = 1;
                        } else {
                            currentMapProvider = -1;
                        }
                    } else {
                        currentMapProvider = MessagesController.getInstance(messageObject.currentAccount).mapProvider;
                    }
                    if (currentMapProvider == -1) {
                        photoImage.setImage(null, null, Theme.chat_locationDrawable[messageObject.isOutOwner() ? 1 : 0], null, messageObject, 0);
                    } else if (currentMapProvider == 2) {
                        if (currentWebFile != null) {
                            photoImage.setImage(ImageLocation.getForWebFile(currentWebFile), null, Theme.chat_locationDrawable[messageObject.isOutOwner() ? 1 : 0], null, messageObject, 0);
                        }
                    } else {
                        if (currentMapProvider == 3 || currentMapProvider == 4) {
                            ImageLoader.getInstance().addTestWebFile(currentUrl, currentWebFile);
                            addedForTest = true;
                        }
                        if (currentUrl != null) {
                            photoImage.setImage(currentUrl, null, Theme.chat_locationDrawable[messageObject.isOutOwner() ? 1 : 0], null, 0);
                        }
                    }
                } else if (messageObject.isAnyKindOfSticker()) { //sticker
                    drawBackground = false;
                    boolean isWebpSticker = messageObject.type == MessageObject.TYPE_STICKER;
                    for (int a = 0; a < messageObject.getDocument().attributes.size(); a++) {
                        TLRPC.DocumentAttribute attribute = messageObject.getDocument().attributes.get(a);
                        if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                            photoWidth = attribute.w;
                            photoHeight = attribute.h;
                            break;
                        }
                    }
                    if (messageObject.isAnimatedSticker() && photoWidth == 0 && photoHeight == 0) {
                        photoWidth = photoHeight = 512;
                    }
                    float maxHeight;
                    float maxWidth;
                    if (AndroidUtilities.isTablet()) {
                        maxHeight = maxWidth = AndroidUtilities.getMinTabletSide() * 0.4f;
                    } else {
                        maxHeight = maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
                    }
                    String filter;
                    if (messageObject.isAnimatedEmoji()) {
                        float zoom = MessagesController.getInstance(currentAccount).animatedEmojisZoom;
                        photoWidth = (int) ((photoWidth / 512.0f) * maxWidth * zoom);
                        photoHeight = (int) ((photoHeight / 512.0f) * maxHeight * zoom);
                    } else {
                        if (photoWidth == 0) {
                            photoHeight = (int) maxHeight;
                            photoWidth = photoHeight + AndroidUtilities.dp(100);
                        }
                        photoHeight *= maxWidth / photoWidth;
                        photoWidth = (int) maxWidth;
                        if (photoHeight > maxHeight) {
                            photoWidth *= maxHeight / photoHeight;
                            photoHeight = (int) maxHeight;
                        }
                    }
                    Object parentObject = messageObject;
                    if (messageObject.isAnimatedEmoji()) {
                        filter = String.format(Locale.US, "%d_%d_nr_%s" + messageObject.emojiAnimatedStickerColor, photoWidth, photoHeight, messageObject.toString());
                        photoImage.setAutoRepeat(delegate.shouldRepeatSticker(messageObject) ? 2 : 3);
                        parentObject = MessageObject.getInputStickerSet(messageObject.emojiAnimatedSticker);
                    } else if (SharedConfig.loopStickers || isWebpSticker) {
                        filter = String.format(Locale.US, "%d_%d", photoWidth, photoHeight);
                        photoImage.setAutoRepeat(1);
                    } else {
                        filter = String.format(Locale.US, "%d_%d_nr_%s", photoWidth, photoHeight, messageObject.toString());
                        photoImage.setAutoRepeat(delegate.shouldRepeatSticker(messageObject) ? 2 : 3);
                    }
                    documentAttachType = DOCUMENT_ATTACH_TYPE_STICKER;
                    availableTimeWidth = photoWidth - AndroidUtilities.dp(14);
                    backgroundWidth = photoWidth + AndroidUtilities.dp(12);

                    currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40);
                    photoParentObject = messageObject.photoThumbsObject;
                    if (messageObject.attachPathExists) {
                        photoImage.setImage(ImageLocation.getForPath(messageObject.messageOwner.attachPath), filter,
                                ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), "b1",
                                messageObject.getDocument().size, isWebpSticker ? "webp" : null, parentObject, 1);
                    } else if (messageObject.getDocument().id != 0) {
                        photoImage.setImage(ImageLocation.getForDocument(messageObject.getDocument()), filter,
                                ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), "b1",
                                messageObject.getDocument().size, isWebpSticker ? "webp" : null, parentObject, 1);
                    }
                } else {
                    currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
                    photoParentObject = messageObject.photoThumbsObject;
                    int maxPhotoWidth;
                    boolean useFullWidth = false;
                    if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                        maxPhotoWidth = photoWidth = AndroidUtilities.roundMessageSize;
                        documentAttach = messageObject.getDocument();
                        documentAttachType = DOCUMENT_ATTACH_TYPE_ROUND;
                    } else {
                        if (AndroidUtilities.isTablet()) {
                            maxPhotoWidth = photoWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.7f);
                        } else {
                            if (currentPhotoObject != null && (messageObject.type == 1 || messageObject.type == 3 || messageObject.type == 8) && currentPhotoObject.w >= currentPhotoObject.h) {
                                maxPhotoWidth = photoWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(64);
                                useFullWidth = true;
                            } else {
                                maxPhotoWidth = photoWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f);
                            }
                        }
                    }
                    photoHeight = photoWidth + AndroidUtilities.dp(100);
                    if (!useFullWidth) {
                        if (messageObject.type != 5 && checkNeedDrawShareButton(messageObject)) {
                            maxPhotoWidth -= AndroidUtilities.dp(20);
                            photoWidth -= AndroidUtilities.dp(20);
                        }
                        if (photoWidth > AndroidUtilities.getPhotoSize()) {
                            photoWidth = AndroidUtilities.getPhotoSize();
                        }
                        if (photoHeight > AndroidUtilities.getPhotoSize()) {
                            photoHeight = AndroidUtilities.getPhotoSize();
                        }
                    } else if (isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner()) {
                        photoWidth -= AndroidUtilities.dp(52);
                    }

                    boolean needQualityPreview = false;

                    if (messageObject.type == 1) { //photo
                        updateSecretTimeText(messageObject);
                        currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40);
                    } else if (messageObject.type == 3 || messageObject.type == 8) { //video, gif
                        createDocumentLayout(0, messageObject);
                        currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40);
                        updateSecretTimeText(messageObject);
                        needQualityPreview = true;
                    } else if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                        currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40);
                        needQualityPreview = true;
                    }
                    int w;
                    int h;
                    if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                        w = h = AndroidUtilities.roundMessageSize;
                    } else {
                        TLRPC.PhotoSize size = currentPhotoObject != null ? currentPhotoObject : currentPhotoObjectThumb;
                        int imageW = 0;
                        int imageH = 0;
                        if (size != null) {
                            imageW = size.w;
                            imageH = size.h;
                        } else if (documentAttach != null) {
                            for (int a = 0, N = documentAttach.attributes.size(); a < N; a++) {
                                TLRPC.DocumentAttribute attribute = documentAttach.attributes.get(a);
                                if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                    imageW = attribute.w;
                                    imageH = attribute.h;
                                }
                            }
                        }
                        float scale = (float) imageW / (float) photoWidth;
                        w = (int) (imageW / scale);
                        h = (int) (imageH / scale);
                        if (w == 0) {
                            w = AndroidUtilities.dp(150);
                        }
                        if (h == 0) {
                            h = AndroidUtilities.dp(150);
                        }
                        if (h > photoHeight) {
                            float scale2 = h;
                            h = photoHeight;
                            scale2 /= h;
                            w = (int) (w / scale2);
                        } else if (h < AndroidUtilities.dp(120)) {
                            h = AndroidUtilities.dp(120);
                            float hScale = (float) imageH / h;
                            if (imageW / hScale < photoWidth) {
                                w = (int) (imageW / hScale);
                            }
                        }
                    }
                    if (currentPhotoObject != null && "s".equals(currentPhotoObject.type)) {
                        currentPhotoObject = null;
                    }

                    if (currentPhotoObject != null && currentPhotoObject == currentPhotoObjectThumb) {
                        if (messageObject.type == 1) {
                            currentPhotoObjectThumb = null;
                        } else {
                            currentPhotoObject = null;
                        }
                    }

                    if (needQualityPreview) {
                        /*if ((DownloadController.getInstance(currentAccount).getAutodownloadMask() & DownloadController.AUTODOWNLOAD_TYPE_PHOTO) == 0) {
                            currentPhotoObject = null;
                        }*/
                        if (!messageObject.needDrawBluredPreview() && (currentPhotoObject == null || currentPhotoObject == currentPhotoObjectThumb) && (currentPhotoObjectThumb == null || !"m".equals(currentPhotoObjectThumb.type))) {
                            photoImage.setNeedsQualityThumb(true);
                            photoImage.setShouldGenerateQualityThumb(true);
                        }
                    }

                    if (currentMessagesGroup == null && messageObject.caption != null) {
                        mediaBackground = false;
                    }

                    if ((w == 0 || h == 0) && messageObject.type == 8) {
                        for (int a = 0; a < messageObject.getDocument().attributes.size(); a++) {
                            TLRPC.DocumentAttribute attribute = messageObject.getDocument().attributes.get(a);
                            if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                float scale = (float) attribute.w / (float) photoWidth;
                                w = (int) (attribute.w / scale);
                                h = (int) (attribute.h / scale);
                                if (h > photoHeight) {
                                    float scale2 = h;
                                    h = photoHeight;
                                    scale2 /= h;
                                    w = (int) (w / scale2);
                                } else if (h < AndroidUtilities.dp(120)) {
                                    h = AndroidUtilities.dp(120);
                                    float hScale = (float) attribute.h / h;
                                    if (attribute.w / hScale < photoWidth) {
                                        w = (int) (attribute.w / hScale);
                                    }
                                }
                                break;
                            }
                        }
                    }

                    if (w == 0 || h == 0) {
                        w = h = AndroidUtilities.dp(150);
                    }
                    if (messageObject.type == 3) {
                        if (w < infoWidth + AndroidUtilities.dp(16 + 24)) {
                            w = infoWidth + AndroidUtilities.dp(16 + 24);
                        }
                    }

                    if (currentMessagesGroup != null) {
                        int firstLineWidth = 0;
                        int dWidth = getGroupPhotosWidth();
                        for (int a = 0; a < currentMessagesGroup.posArray.size(); a++) {
                            MessageObject.GroupedMessagePosition position = currentMessagesGroup.posArray.get(a);
                            if (position.minY == 0) {
                                firstLineWidth += Math.ceil((position.pw + position.leftSpanOffset) / 1000.0f * dWidth);
                            } else {
                                break;
                            }
                        }
                        availableTimeWidth = firstLineWidth - AndroidUtilities.dp(35);
                    } else {
                        availableTimeWidth = maxPhotoWidth - AndroidUtilities.dp(14);
                    }
                    if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                        availableTimeWidth -= Math.ceil(Theme.chat_audioTimePaint.measureText("00:00")) + AndroidUtilities.dp(26);
                    }
                    measureTime(messageObject);
                    int timeWidthTotal = timeWidth + AndroidUtilities.dp(14 + (messageObject.isOutOwner() ? 20 : 0));
                    if (w < timeWidthTotal) {
                        w = timeWidthTotal;
                    }

                    if (messageObject.isRoundVideo()) {
                        w = h = Math.min(w, h);
                        drawBackground = false;
                        photoImage.setRoundRadius(w / 2);
                    } else if (messageObject.needDrawBluredPreview()) {
                        if (AndroidUtilities.isTablet()) {
                            w = h = (int) (AndroidUtilities.getMinTabletSide() * 0.5f);
                        } else {
                            w = h = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f);
                        }
                    }

                    int widthForCaption = 0;
                    boolean fixPhotoWidth = false;
                    if (currentMessagesGroup != null) {
                        float maxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
                        int dWidth = getGroupPhotosWidth();
                        w = (int) Math.ceil(currentPosition.pw / 1000.0f * dWidth);
                        if (currentPosition.minY != 0 && (messageObject.isOutOwner() && (currentPosition.flags & MessageObject.POSITION_FLAG_LEFT) != 0 || !messageObject.isOutOwner() && (currentPosition.flags & MessageObject.POSITION_FLAG_RIGHT) != 0)) {
                            int firstLineWidth = 0;
                            int currentLineWidth = 0;
                            for (int a = 0; a < currentMessagesGroup.posArray.size(); a++) {
                                MessageObject.GroupedMessagePosition position = currentMessagesGroup.posArray.get(a);
                                if (position.minY == 0) {
                                    firstLineWidth += Math.ceil(position.pw / 1000.0f * dWidth) + (position.leftSpanOffset != 0 ? Math.ceil(position.leftSpanOffset / 1000.0f * dWidth) : 0);
                                } else if (position.minY == currentPosition.minY) {
                                    currentLineWidth += Math.ceil((position.pw) / 1000.0f * dWidth) + (position.leftSpanOffset != 0 ? Math.ceil(position.leftSpanOffset / 1000.0f * dWidth) : 0);
                                } else if (position.minY > currentPosition.minY) {
                                    break;
                                }
                            }
                            w += firstLineWidth - currentLineWidth;
                        }
                        w -= AndroidUtilities.dp(9);
                        if (isAvatarVisible) {
                            w -= AndroidUtilities.dp(48);
                        }
                        if (currentPosition.siblingHeights != null) {
                            h = 0;
                            for (int a = 0; a < currentPosition.siblingHeights.length; a++) {
                                h += (int) Math.ceil(maxHeight * currentPosition.siblingHeights[a]);
                            }
                            h += (currentPosition.maxY - currentPosition.minY) * Math.round(7 * AndroidUtilities.density); //TODO fix
                        } else {
                            h = (int) Math.ceil(maxHeight * currentPosition.ph);
                        }
                        backgroundWidth = w;
                        if ((currentPosition.flags & MessageObject.POSITION_FLAG_RIGHT) != 0 && (currentPosition.flags & MessageObject.POSITION_FLAG_LEFT) != 0) {
                            w -= AndroidUtilities.dp(8);
                        } else if ((currentPosition.flags & MessageObject.POSITION_FLAG_RIGHT) == 0 && (currentPosition.flags & MessageObject.POSITION_FLAG_LEFT) == 0) {
                            w -= AndroidUtilities.dp(11);
                        } else if ((currentPosition.flags & MessageObject.POSITION_FLAG_RIGHT) != 0) {
                            w -= AndroidUtilities.dp(10);
                        } else {
                            w -= AndroidUtilities.dp(9);
                        }
                        photoWidth = w;
                        if (!currentPosition.edge) {
                            photoWidth += AndroidUtilities.dp(10);
                        }
                        photoHeight = h;
                        widthForCaption += photoWidth - AndroidUtilities.dp(10);
                        if ((currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0 || currentMessagesGroup.hasSibling && (currentPosition.flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                            widthForCaption += getAdditionalWidthForPosition(currentPosition);
                            int count = currentMessagesGroup.messages.size();
                            for (int i = 0; i < count; i++) {
                                MessageObject m = currentMessagesGroup.messages.get(i);
                                MessageObject.GroupedMessagePosition rowPosition = currentMessagesGroup.posArray.get(i);
                                if (rowPosition != currentPosition && (rowPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                                    w = (int) Math.ceil(rowPosition.pw / 1000.0f * dWidth);
                                    if (rowPosition.minY != 0 && (messageObject.isOutOwner() && (rowPosition.flags & MessageObject.POSITION_FLAG_LEFT) != 0 || !messageObject.isOutOwner() && (rowPosition.flags & MessageObject.POSITION_FLAG_RIGHT) != 0)) {
                                        int firstLineWidth = 0;
                                        int currentLineWidth = 0;
                                        for (int a = 0; a < currentMessagesGroup.posArray.size(); a++) {
                                            MessageObject.GroupedMessagePosition position = currentMessagesGroup.posArray.get(a);
                                            if (position.minY == 0) {
                                                firstLineWidth += Math.ceil(position.pw / 1000.0f * dWidth) + (position.leftSpanOffset != 0 ? Math.ceil(position.leftSpanOffset / 1000.0f * dWidth) : 0);
                                            } else if (position.minY == rowPosition.minY) {
                                                currentLineWidth += Math.ceil((position.pw) / 1000.0f * dWidth) + (position.leftSpanOffset != 0 ? Math.ceil(position.leftSpanOffset / 1000.0f * dWidth) : 0);
                                            } else if (position.minY > rowPosition.minY) {
                                                break;
                                            }
                                        }
                                        w += firstLineWidth - currentLineWidth;
                                    }
                                    w -= AndroidUtilities.dp(9);
                                    if ((rowPosition.flags & MessageObject.POSITION_FLAG_RIGHT) != 0 && (rowPosition.flags & MessageObject.POSITION_FLAG_LEFT) != 0) {
                                        w -= AndroidUtilities.dp(8);
                                    } else if ((rowPosition.flags & MessageObject.POSITION_FLAG_RIGHT) == 0 && (rowPosition.flags & MessageObject.POSITION_FLAG_LEFT) == 0) {
                                        w -= AndroidUtilities.dp(11);
                                    } else if ((rowPosition.flags & MessageObject.POSITION_FLAG_RIGHT) != 0) {
                                        w -= AndroidUtilities.dp(10);
                                    } else {
                                        w -= AndroidUtilities.dp(9);
                                    }
                                    if (isChat && !m.isOutOwner() && m.needDrawAvatar() && (rowPosition == null || rowPosition.edge)) {
                                        w -= AndroidUtilities.dp(48);
                                    }
                                    w += getAdditionalWidthForPosition(rowPosition);
                                    if (!rowPosition.edge) {
                                        w += AndroidUtilities.dp(10);
                                    }
                                    widthForCaption += w;
                                    if (rowPosition.minX < currentPosition.minX || currentMessagesGroup.hasSibling && rowPosition.minY != rowPosition.maxY) {
                                        captionOffsetX -= w;
                                    }
                                }
                                if (m.caption != null) {
                                    if (currentCaption != null) {
                                        currentCaption = null;
                                        break;
                                    } else {
                                        currentCaption = m.caption;
                                    }
                                }
                            }
                        }
                    } else {
                        photoHeight = h;
                        photoWidth = w;
                        currentCaption = messageObject.caption;

                        int minCaptionWidth;
                        if (AndroidUtilities.isTablet()) {
                            minCaptionWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.65f);
                        } else {
                            minCaptionWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.65f);
                        }
                        if (!messageObject.needDrawBluredPreview() && currentCaption != null && photoWidth < minCaptionWidth) {
                            widthForCaption = minCaptionWidth;
                            fixPhotoWidth = true;
                        } else {
                            widthForCaption = photoWidth - AndroidUtilities.dp(10);
                        }

                        backgroundWidth = photoWidth + AndroidUtilities.dp(8);
                        if (!mediaBackground) {
                            backgroundWidth += AndroidUtilities.dp(9);
                        }
                    }

                    if (currentCaption != null) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                captionLayout = StaticLayout.Builder.obtain(currentCaption, 0, currentCaption.length(), Theme.chat_msgTextPaint, widthForCaption)
                                        .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                                        .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                        .build();
                            } else {
                                captionLayout = new StaticLayout(currentCaption, Theme.chat_msgTextPaint, widthForCaption, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            }
                            int lineCount = captionLayout.getLineCount();
                            if (lineCount > 0) {
                                if (fixPhotoWidth) {
                                    captionWidth = 0;
                                    for (int a = 0; a < lineCount; a++) {
                                        captionWidth = (int) Math.max(captionWidth, Math.ceil(captionLayout.getLineWidth(a)));
                                        if (captionLayout.getLineLeft(a) != 0) {
                                            captionWidth = widthForCaption;
                                            break;
                                        }
                                    }
                                    if (captionWidth > widthForCaption) {
                                        captionWidth = widthForCaption;
                                    }
                                } else {
                                    captionWidth = widthForCaption;
                                }
                                captionHeight = captionLayout.getHeight();
                                addedCaptionHeight = captionHeight + AndroidUtilities.dp(9);
                                if (currentPosition == null || (currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                                    additionHeight += addedCaptionHeight;
                                    int widthToCheck = Math.max(captionWidth, photoWidth - AndroidUtilities.dp(10));
                                    float lastLineWidth = captionLayout.getLineWidth(captionLayout.getLineCount() - 1) + captionLayout.getLineLeft(captionLayout.getLineCount() - 1);
                                    if (widthToCheck + AndroidUtilities.dp(2) - lastLineWidth < timeWidthTotal) {
                                        additionHeight += AndroidUtilities.dp(14);
                                        addedCaptionHeight += AndroidUtilities.dp(14);
                                        captionNewLine = 1;
                                    }
                                } else {
                                    captionLayout = null;
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }

                    if (fixPhotoWidth && photoWidth < captionWidth + AndroidUtilities.dp(10)) {
                        photoWidth = captionWidth + AndroidUtilities.dp(10);
                        backgroundWidth = photoWidth + AndroidUtilities.dp(8);
                        if (!mediaBackground) {
                            backgroundWidth += AndroidUtilities.dp(9);
                        }
                    }

                    currentPhotoFilter = currentPhotoFilterThumb = String.format(Locale.US, "%d_%d", (int) (w / AndroidUtilities.density), (int) (h / AndroidUtilities.density));
                    if (messageObject.photoThumbs != null && messageObject.photoThumbs.size() > 1 || messageObject.type == 3 || messageObject.type == 8 || messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                        if (messageObject.needDrawBluredPreview()) {
                            currentPhotoFilter += "_b2";
                            currentPhotoFilterThumb += "_b2";
                        } else {
                            currentPhotoFilterThumb += "_b";
                        }
                    }

                    boolean noSize = false;
                    if (messageObject.type == 3 || messageObject.type == 8 || messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                        noSize = true;
                    }
                    if (currentPhotoObject != null && !noSize && currentPhotoObject.size == 0) {
                        currentPhotoObject.size = -1;
                    }
                    if (currentPhotoObjectThumb != null && !noSize && currentPhotoObjectThumb.size == 0) {
                        currentPhotoObjectThumb.size = -1;
                    }

                    if (SharedConfig.autoplayVideo && messageObject.type == 3 && !messageObject.needDrawBluredPreview() && (
                            currentMessageObject.mediaExists ||
                                    messageObject.canStreamVideo() && DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject)
                    )) {
                        if (currentPosition != null) {
                            autoPlayingMedia = (currentPosition.flags & MessageObject.POSITION_FLAG_LEFT) != 0 && (currentPosition.flags & MessageObject.POSITION_FLAG_RIGHT) != 0;
                        } else {
                            autoPlayingMedia = true;
                        }
                    }

                    if (autoPlayingMedia) {
                        photoImage.setAllowStartAnimation(true);
                        photoImage.startAnimation();
                        TLRPC.Document document = messageObject.getDocument();
                        photoImage.setImage(ImageLocation.getForDocument(document), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObjectThumb, document), currentPhotoFilterThumb, null, messageObject.getDocument().size, null, messageObject, 0);
                    } else if (messageObject.type == 1) {
                        if (messageObject.useCustomPhoto) {
                            photoImage.setImageBitmap(getResources().getDrawable(R.drawable.theme_preview_image));
                        } else {
                            if (currentPhotoObject != null) {
                                boolean photoExist = true;
                                String fileName = FileLoader.getAttachFileName(currentPhotoObject);
                                if (messageObject.mediaExists) {
                                    DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                                } else {
                                    photoExist = false;
                                }
                                if (photoExist || DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject) || FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                                    photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, noSize ? 0 : currentPhotoObject.size, null, currentMessageObject, currentMessageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
                                } else {
                                    photoNotSet = true;
                                    if (currentPhotoObjectThumb != null) {
                                        photoImage.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, 0, null, currentMessageObject, currentMessageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
                                    } else {
                                        photoImage.setImageBitmap((Drawable) null);
                                    }
                                }
                            } else {
                                photoImage.setImageBitmap((Drawable) null);
                            }
                        }
                    } else if (messageObject.type == 8 || messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                        String fileName = FileLoader.getAttachFileName(messageObject.getDocument());
                        int localFile = 0;
                        if (messageObject.attachPathExists) {
                            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                            localFile = 1;
                        } else if (messageObject.mediaExists) {
                            localFile = 2;
                        }
                        boolean autoDownload = false;
                        if (MessageObject.isGifDocument(messageObject.getDocument()) || messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                            autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject);
                        }
                        if (!messageObject.isSending() && !messageObject.isEditing() && (localFile != 0 || FileLoader.getInstance(currentAccount).isLoadingFile(fileName) || autoDownload)) {
                            if (localFile != 1 && !messageObject.needDrawBluredPreview() && (localFile != 0 || messageObject.canStreamVideo() && autoDownload)) {
                                autoPlayingMedia = true;
                                photoImage.setImage(ImageLocation.getForDocument(messageObject.getDocument()), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, null, messageObject.getDocument().size, null, messageObject, 0);
                            } else if (localFile == 1) {
                                photoImage.setImage(ImageLocation.getForPath(messageObject.isSendError() ? null : messageObject.messageOwner.attachPath), null, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, 0, null, messageObject, 0);
                            } else {
                                photoImage.setImage(ImageLocation.getForDocument(messageObject.getDocument()), null, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, null, messageObject.getDocument().size, null, messageObject, 0);
                            }
                        } else {
                            photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, 0, null, messageObject, 0);
                        }
                    } else {
                        photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, 0, null, messageObject, currentMessageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
                    }
                }
                setMessageObjectInternal(messageObject);

                if (drawForwardedName && messageObject.needDrawForwarded() && (currentPosition == null || currentPosition.minY == 0)) {
                    if (messageObject.type != 5) {
                        namesOffset += AndroidUtilities.dp(5);
                    }
                } else if (drawNameLayout && messageObject.messageOwner.reply_to_msg_id == 0) {
                    namesOffset += AndroidUtilities.dp(7);
                }
                totalHeight = photoHeight + AndroidUtilities.dp(14) + namesOffset + additionHeight;
                if (currentPosition != null && (currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                    totalHeight -= AndroidUtilities.dp(3);
                }

                int additionalTop = 0;
                if (currentPosition != null) {
                    photoWidth += getAdditionalWidthForPosition(currentPosition);
                    if ((currentPosition.flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                        photoHeight += AndroidUtilities.dp(4);
                        additionalTop -= AndroidUtilities.dp(4);
                    }
                    if ((currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                        photoHeight += AndroidUtilities.dp(1);
                    }
                }

                if (drawPinnedTop) {
                    namesOffset -= AndroidUtilities.dp(1);
                }

                int y;
                if (currentPosition != null) {
                    if (namesOffset > 0) {
                        y = AndroidUtilities.dp(7);
                        totalHeight -= AndroidUtilities.dp(2);
                    } else {
                        y = AndroidUtilities.dp(5);
                        totalHeight -= AndroidUtilities.dp(4);
                    }
                } else {
                    if (namesOffset > 0) {
                        y = AndroidUtilities.dp(7);
                        totalHeight -= AndroidUtilities.dp(2);
                    } else {
                        y = AndroidUtilities.dp(5);
                        totalHeight -= AndroidUtilities.dp(4);
                    }
                }
                photoImage.setImageCoords(0, y + namesOffset + additionalTop, photoWidth, photoHeight);
                invalidate();
            }

            if (currentPosition == null && !messageObject.isAnyKindOfSticker() && addedCaptionHeight == 0) {
                if (captionLayout == null && messageObject.caption != null) {
                    try {
                        currentCaption = messageObject.caption;
                        int width = backgroundWidth - AndroidUtilities.dp(31);
                        int widthForCaption = width - AndroidUtilities.dp(10);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            captionLayout = StaticLayout.Builder.obtain(messageObject.caption, 0, messageObject.caption.length(), Theme.chat_msgTextPaint, widthForCaption)
                                    .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                                    .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                    .build();
                        } else {
                            captionLayout = new StaticLayout(messageObject.caption, Theme.chat_msgTextPaint, widthForCaption, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (captionLayout != null) {
                    try {
                        int width = backgroundWidth - AndroidUtilities.dp(31);
                        int widthForCaption = width - AndroidUtilities.dp(10);
                        if (captionLayout != null && captionLayout.getLineCount() > 0) {
                            captionWidth = widthForCaption;
                            int timeWidthTotal = timeWidth + (messageObject.isOutOwner() ? AndroidUtilities.dp(20) : 0);
                            captionHeight = captionLayout.getHeight();
                            totalHeight += captionHeight + AndroidUtilities.dp(9);
                            float lastLineWidth = captionLayout.getLineWidth(captionLayout.getLineCount() - 1) + captionLayout.getLineLeft(captionLayout.getLineCount() - 1);
                            if (width - AndroidUtilities.dp(8) - lastLineWidth < timeWidthTotal) {
                                totalHeight += AndroidUtilities.dp(14);
                                captionHeight += AndroidUtilities.dp(14);
                                captionNewLine = 2;
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
            if (captionLayout == null && widthBeforeNewTimeLine != -1 && availableTimeWidth - widthBeforeNewTimeLine < timeWidth) {
                totalHeight += AndroidUtilities.dp(14);
            }

            if (currentMessageObject.eventId != 0 && !currentMessageObject.isMediaEmpty() && currentMessageObject.messageOwner.media.webpage != null) {
                int linkPreviewMaxWidth = backgroundWidth - AndroidUtilities.dp(41);
                hasOldCaptionPreview = true;
                linkPreviewHeight = 0;
                TLRPC.WebPage webPage = currentMessageObject.messageOwner.media.webpage;
                try {
                    int width = siteNameWidth = (int) Math.ceil(Theme.chat_replyNamePaint.measureText(webPage.site_name) + 1);
                    siteNameLayout = new StaticLayout(webPage.site_name, Theme.chat_replyNamePaint, Math.min(width, linkPreviewMaxWidth), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    siteNameRtl = siteNameLayout.getLineLeft(0) != 0;
                    int height = siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
                    linkPreviewHeight += height;
                    totalHeight += height;
                } catch (Exception e) {
                    FileLog.e(e);
                }

                try {
                    descriptionX = 0;
                    if (linkPreviewHeight != 0) {
                        totalHeight += AndroidUtilities.dp(2);
                    }

                    descriptionLayout = StaticLayoutEx.createStaticLayout(webPage.description, Theme.chat_replyTextPaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false, TextUtils.TruncateAt.END, linkPreviewMaxWidth, 6);

                    int height = descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
                    linkPreviewHeight += height;
                    totalHeight += height;

                    for (int a = 0; a < descriptionLayout.getLineCount(); a++) {
                        int lineLeft = (int) Math.ceil(descriptionLayout.getLineLeft(a));
                        if (lineLeft != 0) {
                            if (descriptionX == 0) {
                                descriptionX = -lineLeft;
                            } else {
                                descriptionX = Math.max(descriptionX, -lineLeft);
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }

                totalHeight += AndroidUtilities.dp(17);
                if (captionNewLine != 0) {
                    totalHeight -= AndroidUtilities.dp(14);
                    if (captionNewLine == 2) {
                        captionHeight -= AndroidUtilities.dp(14);
                    }
                }
            }

            botButtons.clear();
            if (messageIdChanged) {
                botButtonsByData.clear();
                botButtonsByPosition.clear();
                botButtonsLayout = null;
            }
            if (currentPosition == null && (messageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup || messageObject.messageOwner.reactions != null && !messageObject.messageOwner.reactions.results.isEmpty())) {
                int rows;

                if (messageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
                    rows = messageObject.messageOwner.reply_markup.rows.size();
                } else {
                    rows = 1;
                }
                substractBackgroundHeight = keyboardHeight = AndroidUtilities.dp(44 + 4) * rows + AndroidUtilities.dp(1);
                widthForButtons = backgroundWidth - AndroidUtilities.dp(mediaBackground ? 0 : 9);
                boolean fullWidth = false;
                if (messageObject.wantedBotKeyboardWidth > widthForButtons) {
                    int maxButtonWidth = -AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 62 : 10);
                    if (AndroidUtilities.isTablet()) {
                        maxButtonWidth += AndroidUtilities.getMinTabletSide();
                    } else {
                        maxButtonWidth += Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(5);
                    }
                    widthForButtons = Math.max(backgroundWidth, Math.min(messageObject.wantedBotKeyboardWidth, maxButtonWidth));
                }

                int maxButtonsWidth = 0;
                HashMap<String, BotButton> oldByData = new HashMap<>(botButtonsByData);
                HashMap<String, BotButton> oldByPosition;
                if (messageObject.botButtonsLayout != null && botButtonsLayout != null && botButtonsLayout.equals(messageObject.botButtonsLayout.toString())) {
                    oldByPosition = new HashMap<>(botButtonsByPosition);
                } else {
                    if (messageObject.botButtonsLayout != null) {
                        botButtonsLayout = messageObject.botButtonsLayout.toString();
                    }
                    oldByPosition = null;
                }
                botButtonsByData.clear();
                if (messageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
                    for (int a = 0; a < rows; a++) {
                        TLRPC.TL_keyboardButtonRow row = messageObject.messageOwner.reply_markup.rows.get(a);
                        int buttonsCount = row.buttons.size();
                        if (buttonsCount == 0) {
                            continue;
                        }
                        int buttonWidth = (widthForButtons - AndroidUtilities.dp(5) * (buttonsCount - 1) - AndroidUtilities.dp(2)) / buttonsCount;
                        for (int b = 0; b < row.buttons.size(); b++) {
                            BotButton botButton = new BotButton();
                            botButton.button = row.buttons.get(b);
                            String key = Utilities.bytesToHex(botButton.button.data);
                            String position = a + "" + b;
                            BotButton oldButton;
                            if (oldByPosition != null) {
                                oldButton = oldByPosition.get(position);
                            } else {
                                oldButton = oldByData.get(key);
                            }
                            if (oldButton != null) {
                                botButton.progressAlpha = oldButton.progressAlpha;
                                botButton.angle = oldButton.angle;
                                botButton.lastUpdateTime = oldButton.lastUpdateTime;
                            } else {
                                botButton.lastUpdateTime = System.currentTimeMillis();
                            }
                            botButtonsByData.put(key, botButton);
                            botButtonsByPosition.put(position, botButton);
                            botButton.x = b * (buttonWidth + AndroidUtilities.dp(5));
                            botButton.y = a * AndroidUtilities.dp(44 + 4) + AndroidUtilities.dp(5);
                            botButton.width = buttonWidth;
                            botButton.height = AndroidUtilities.dp(44);
                            CharSequence buttonText;
                            if (botButton.button instanceof TLRPC.TL_keyboardButtonBuy && (messageObject.messageOwner.media.flags & 4) != 0) {
                                buttonText = LocaleController.getString("PaymentReceipt", R.string.PaymentReceipt);
                            } else {
                                buttonText = Emoji.replaceEmoji(botButton.button.text, Theme.chat_botButtonPaint.getFontMetricsInt(), AndroidUtilities.dp(15), false);
                                buttonText = TextUtils.ellipsize(buttonText, Theme.chat_botButtonPaint, buttonWidth - AndroidUtilities.dp(10), TextUtils.TruncateAt.END);
                            }
                            botButton.title = new StaticLayout(buttonText, Theme.chat_botButtonPaint, buttonWidth - AndroidUtilities.dp(10), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                            botButtons.add(botButton);
                            if (b == row.buttons.size() - 1) {
                                maxButtonsWidth = Math.max(maxButtonsWidth, botButton.x + botButton.width);
                            }
                        }
                    }
                } else {
                    int buttonsCount = messageObject.messageOwner.reactions.results.size();
                    int buttonWidth = (widthForButtons - AndroidUtilities.dp(5) * (buttonsCount - 1) - AndroidUtilities.dp(2)) / buttonsCount;
                    for (int b = 0; b < buttonsCount; b++) {
                        TLRPC.TL_reactionCount reaction = messageObject.messageOwner.reactions.results.get(b);
                        BotButton botButton = new BotButton();
                        botButton.reaction = reaction;
                        String key = reaction.reaction;
                        String position = 0 + "" + b;
                        BotButton oldButton;
                        if (oldByPosition != null) {
                            oldButton = oldByPosition.get(position);
                        } else {
                            oldButton = oldByData.get(key);
                        }
                        if (oldButton != null) {
                            botButton.progressAlpha = oldButton.progressAlpha;
                            botButton.angle = oldButton.angle;
                            botButton.lastUpdateTime = oldButton.lastUpdateTime;
                        } else {
                            botButton.lastUpdateTime = System.currentTimeMillis();
                        }
                        botButtonsByData.put(key, botButton);
                        botButtonsByPosition.put(position, botButton);
                        botButton.x = b * (buttonWidth + AndroidUtilities.dp(5));
                        botButton.y = AndroidUtilities.dp(5);
                        botButton.width = buttonWidth;
                        botButton.height = AndroidUtilities.dp(44);

                        CharSequence buttonText = Emoji.replaceEmoji(String.format("%d %s", reaction.count, reaction.reaction), Theme.chat_botButtonPaint.getFontMetricsInt(), AndroidUtilities.dp(15), false);
                        buttonText = TextUtils.ellipsize(buttonText, Theme.chat_botButtonPaint, buttonWidth - AndroidUtilities.dp(10), TextUtils.TruncateAt.END);

                        botButton.title = new StaticLayout(buttonText, Theme.chat_botButtonPaint, buttonWidth - AndroidUtilities.dp(10), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                        botButtons.add(botButton);
                        if (b == buttonsCount - 1) {
                            maxButtonsWidth = Math.max(maxButtonsWidth, botButton.x + botButton.width);
                        }
                    }
                }
                widthForButtons = maxButtonsWidth;
            } else {
                substractBackgroundHeight = 0;
                keyboardHeight = 0;
            }
            if (drawPinnedBottom && drawPinnedTop) {
                totalHeight -= AndroidUtilities.dp(2);
            } else if (drawPinnedBottom) {
                totalHeight -= AndroidUtilities.dp(1);
            } else if (drawPinnedTop && pinnedBottom && currentPosition != null && currentPosition.siblingHeights == null) {
                totalHeight -= AndroidUtilities.dp(1);
            }
            if (messageObject.isAnyKindOfSticker() && totalHeight < AndroidUtilities.dp(70)) {
                totalHeight = AndroidUtilities.dp(70);
            } else if (messageObject.isAnimatedEmoji()) {
                totalHeight += AndroidUtilities.dp(16);
            }
            if (!drawPhotoImage) {
                photoImage.setImageBitmap((Drawable) null);
            }
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                if (MessageObject.isDocumentHasThumb(documentAttach)) {
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(documentAttach.thumbs, 90);
                    radialProgress.setImageOverlay(thumb, documentAttach, messageObject);
                } else {
                    String artworkUrl = messageObject.getArtworkUrl(true);
                    if (!TextUtils.isEmpty(artworkUrl)) {
                        radialProgress.setImageOverlay(artworkUrl);
                    } else {
                        radialProgress.setImageOverlay(null, null, null);
                    }
                }
            } else {
                radialProgress.setImageOverlay(null, null, null);
            }
        }
        updateWaveform();
        updateButtonState(false, dataChanged && !messageObject.cancelEditing, true);

        if (buttonState == 2 && documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO && DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
            FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, 1, 0);
            buttonState = 4;
            radialProgress.setIcon(getIconForCurrentState(), false, false);
        }

        accessibilityVirtualViewBounds.clear();
    }

    public void checkVideoPlayback(boolean allowStart) {
        if (currentMessageObject.isVideo()) {
            if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
                photoImage.setAllowStartAnimation(false);
                photoImage.stopAnimation();
            } else {
                photoImage.setAllowStartAnimation(true);
                photoImage.startAnimation();
            }
        } else {
            if (allowStart) {
                MessageObject playingMessage = MediaController.getInstance().getPlayingMessageObject();
                allowStart = playingMessage == null || !playingMessage.isRoundVideo();
            }
            photoImage.setAllowStartAnimation(allowStart);
            if (allowStart) {
                photoImage.startAnimation();
            } else {
                photoImage.stopAnimation();
            }
        }
    }

    @Override
    protected void onLongPress() {
        if (pressedLink instanceof URLSpanMono) {
            delegate.didPressUrl(this, pressedLink, true);
            return;
        } else if (pressedLink instanceof URLSpanNoUnderline) {
            URLSpanNoUnderline url = (URLSpanNoUnderline) pressedLink;
            if (url.getURL().startsWith("/")) {
                delegate.didPressUrl(this, pressedLink, true);
                return;
            }
        } else if (pressedLink instanceof URLSpan) {
            delegate.didPressUrl(this, pressedLink, true);
            return;
        }
        resetPressedLink(-1);
        if (buttonPressed != 0 || miniButtonPressed != 0 || videoButtonPressed != 0 || pressedBotButton != -1) {
            buttonPressed = 0;
            miniButtonPressed = 0;
            videoButtonPressed = 0;
            pressedBotButton = -1;
            invalidate();
        }

        linkPreviewPressed = false;
        otherPressed = false;
        sharePressed = false;
        imagePressed = false;
        gamePreviewPressed = false;

        if (instantPressed) {
            instantPressed = instantButtonPressed = false;
            if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                selectorDrawable.setState(StateSet.NOTHING);
            }
            invalidate();
        }
        if (pressedVoteButton != -1) {
            pressedVoteButton = -1;
            if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                selectorDrawable.setState(StateSet.NOTHING);
            }
            invalidate();
        }
        if (delegate != null) {
            delegate.didLongPress(this, lastTouchX, lastTouchY);
        }
    }

    public void setCheckPressed(boolean value, boolean pressed) {
        isCheckPressed = value;
        isPressed = pressed;
        updateRadialProgressBackground();
        if (useSeekBarWaweform) {
            seekBarWaveform.setSelected(isDrawSelectionBackground());
        } else {
            seekBar.setSelected(isDrawSelectionBackground());
        }
        invalidate();
    }

    public void setInvalidatesParent(boolean value) {
        invalidatesParent = value;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (invalidatesParent && getParent() != null) {
            View parent = (View) getParent();
            if (parent.getParent() != null) {
                parent = (View) parent.getParent();
                parent.invalidate();
            }
        }
    }

    public void setHighlightedAnimated() {
        isHighlightedAnimated = true;
        highlightProgress = 1000;
        lastHighlightProgressTime = System.currentTimeMillis();
        invalidate();
        if (getParent() != null) {
            ((View) getParent()).invalidate();
        }
    }

    public boolean isHighlighted() {
        return isHighlighted;
    }

    public void setHighlighted(boolean value) {
        if (isHighlighted == value) {
            return;
        }
        isHighlighted = value;
        if (!isHighlighted) {
            lastHighlightProgressTime = System.currentTimeMillis();
            isHighlightedAnimated = true;
            highlightProgress = 300;
        } else {
            isHighlightedAnimated = false;
            highlightProgress = 0;
        }

        updateRadialProgressBackground();
        if (useSeekBarWaweform) {
            seekBarWaveform.setSelected(isDrawSelectionBackground());
        } else {
            seekBar.setSelected(isDrawSelectionBackground());
        }
        invalidate();
        if (getParent() != null) {
            ((View) getParent()).invalidate();
        }
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        updateRadialProgressBackground();
        if (useSeekBarWaweform) {
            seekBarWaveform.setSelected(isDrawSelectionBackground());
        } else {
            seekBar.setSelected(isDrawSelectionBackground());
        }
        invalidate();
    }

    private void updateRadialProgressBackground() {
        if (drawRadialCheckBackground) {
            return;
        }
        boolean forcePressed = (isHighlighted || isPressed || isPressed()) && (!drawPhotoImage || !photoImage.hasBitmapImage());
        radialProgress.setPressed(forcePressed || buttonPressed != 0, false);
        if (hasMiniProgress != 0) {
            radialProgress.setPressed(forcePressed || miniButtonPressed != 0, true);
        }
        videoRadialProgress.setPressed(forcePressed || videoButtonPressed != 0, false);
    }

    @Override
    public void onSeekBarDrag(float progress) {
        if (currentMessageObject == null) {
            return;
        }
        currentMessageObject.audioProgress = progress;
        MediaController.getInstance().seekToProgress(currentMessageObject, progress);
    }

    private void updateWaveform() {
        if (currentMessageObject == null || documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO) {
            return;
        }
        for (int a = 0; a < documentAttach.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = documentAttach.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                if (attribute.waveform == null || attribute.waveform.length == 0) {
                    MediaController.getInstance().generateWaveform(currentMessageObject);
                }
                useSeekBarWaweform = attribute.waveform != null;
                seekBarWaveform.setWaveform(attribute.waveform);
                break;
            }
        }
    }

    private int createDocumentLayout(int maxWidth, MessageObject messageObject) {
        if (messageObject.type == 0) {
            documentAttach = messageObject.messageOwner.media.webpage.document;
        } else {
            documentAttach = messageObject.getDocument();
        }
        if (documentAttach == null) {
            return 0;
        }
        if (MessageObject.isVoiceDocument(documentAttach)) {
            documentAttachType = DOCUMENT_ATTACH_TYPE_AUDIO;
            int duration = 0;
            for (int a = 0; a < documentAttach.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = documentAttach.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    duration = attribute.duration;
                    break;
                }
            }
            widthBeforeNewTimeLine = maxWidth - AndroidUtilities.dp(76 + 18) - (int) Math.ceil(Theme.chat_audioTimePaint.measureText("00:00"));
            availableTimeWidth = maxWidth - AndroidUtilities.dp(18);
            measureTime(messageObject);
            int minSize = AndroidUtilities.dp(40 + 14 + 20 + 90 + 10) + timeWidth;
            if (!hasLinkPreview) {
                backgroundWidth = Math.min(maxWidth, minSize + duration * AndroidUtilities.dp(10));
            }
            seekBarWaveform.setMessageObject(messageObject);
            return 0;
        } else if (MessageObject.isMusicDocument(documentAttach)) {
            documentAttachType = DOCUMENT_ATTACH_TYPE_MUSIC;

            maxWidth = maxWidth - AndroidUtilities.dp(86);
            if (maxWidth < 0) {
                maxWidth = AndroidUtilities.dp(100);
            }

            CharSequence stringFinal = TextUtils.ellipsize(messageObject.getMusicTitle().replace('\n', ' '), Theme.chat_audioTitlePaint, maxWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
            songLayout = new StaticLayout(stringFinal, Theme.chat_audioTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (songLayout.getLineCount() > 0) {
                songX = -(int) Math.ceil(songLayout.getLineLeft(0));
            }

            stringFinal = TextUtils.ellipsize(messageObject.getMusicAuthor().replace('\n', ' '), Theme.chat_audioPerformerPaint, maxWidth, TextUtils.TruncateAt.END);
            performerLayout = new StaticLayout(stringFinal, Theme.chat_audioPerformerPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (performerLayout.getLineCount() > 0) {
                performerX = -(int) Math.ceil(performerLayout.getLineLeft(0));
            }

            int duration = 0;
            for (int a = 0; a < documentAttach.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = documentAttach.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    duration = attribute.duration;
                    break;
                }
            }
            int durationWidth = (int) Math.ceil(Theme.chat_audioTimePaint.measureText(String.format("%d:%02d / %d:%02d", duration / 60, duration % 60, duration / 60, duration % 60)));
            widthBeforeNewTimeLine = backgroundWidth - AndroidUtilities.dp(10 + 76) - durationWidth;
            availableTimeWidth = backgroundWidth - AndroidUtilities.dp(28);
            return durationWidth;
        } else if (MessageObject.isVideoDocument(documentAttach)) {
            documentAttachType = DOCUMENT_ATTACH_TYPE_VIDEO;
            if (!messageObject.needDrawBluredPreview()) {
                updatePlayingMessageProgress();
                String str = String.format("%s", AndroidUtilities.formatFileSize(documentAttach.size));
                docTitleWidth = (int) Math.ceil(Theme.chat_infoPaint.measureText(str));
                docTitleLayout = new StaticLayout(str, Theme.chat_infoPaint, docTitleWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
            return 0;
        } else if (MessageObject.isGifDocument(documentAttach)) {
            documentAttachType = DOCUMENT_ATTACH_TYPE_GIF;
            if (!messageObject.needDrawBluredPreview()) {

                String str = LocaleController.getString("AttachGif", R.string.AttachGif);
                infoWidth = (int) Math.ceil(Theme.chat_infoPaint.measureText(str));
                infoLayout = new StaticLayout(str, Theme.chat_infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                str = String.format("%s", AndroidUtilities.formatFileSize(documentAttach.size));
                docTitleWidth = (int) Math.ceil(Theme.chat_infoPaint.measureText(str));
                docTitleLayout = new StaticLayout(str, Theme.chat_infoPaint, docTitleWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
            return 0;
        } else {
            drawPhotoImage = documentAttach.mime_type != null && documentAttach.mime_type.toLowerCase().startsWith("image/") || MessageObject.isDocumentHasThumb(documentAttach);
            if (!drawPhotoImage) {
                maxWidth += AndroidUtilities.dp(30);
            }
            documentAttachType = DOCUMENT_ATTACH_TYPE_DOCUMENT;
            String name = FileLoader.getDocumentFileName(documentAttach);
            if (name == null || name.length() == 0) {
                name = LocaleController.getString("AttachDocument", R.string.AttachDocument);
            }
            docTitleLayout = StaticLayoutEx.createStaticLayout(name, Theme.chat_docNamePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.MIDDLE, maxWidth, 2, false);
            docTitleOffsetX = Integer.MIN_VALUE;
            int width;
            if (docTitleLayout != null && docTitleLayout.getLineCount() > 0) {
                int maxLineWidth = 0;
                for (int a = 0; a < docTitleLayout.getLineCount(); a++) {
                    maxLineWidth = Math.max(maxLineWidth, (int) Math.ceil(docTitleLayout.getLineWidth(a)));
                    docTitleOffsetX = Math.max(docTitleOffsetX, (int) Math.ceil(-docTitleLayout.getLineLeft(a)));
                }
                width = Math.min(maxWidth, maxLineWidth);
            } else {
                width = maxWidth;
                docTitleOffsetX = 0;
            }

            String str = AndroidUtilities.formatFileSize(documentAttach.size) + " " + FileLoader.getDocumentExtension(documentAttach);
            infoWidth = Math.min(maxWidth - AndroidUtilities.dp(30), (int) Math.ceil(Theme.chat_infoPaint.measureText(str)));
            CharSequence str2 = TextUtils.ellipsize(str, Theme.chat_infoPaint, infoWidth, TextUtils.TruncateAt.END);
            try {
                if (infoWidth < 0) {
                    infoWidth = AndroidUtilities.dp(10);
                }
                infoLayout = new StaticLayout(str2, Theme.chat_infoPaint, infoWidth + AndroidUtilities.dp(6), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (drawPhotoImage) {
                currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320);
                currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40);

                if ((DownloadController.getInstance(currentAccount).getAutodownloadMask() & DownloadController.AUTODOWNLOAD_TYPE_PHOTO) == 0) {
                    currentPhotoObject = null;
                }
                if (currentPhotoObject == null || currentPhotoObject == currentPhotoObjectThumb) {
                    currentPhotoObject = null;
                    photoImage.setNeedsQualityThumb(true);
                    photoImage.setShouldGenerateQualityThumb(true);
                }
                currentPhotoFilter = "86_86_b";
                photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "86_86", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), currentPhotoFilter, 0, null, messageObject, 1);
            }
            return width;
        }
    }

    private void calcBackgroundWidth(int maxWidth, int timeMore, int maxChildWidth) {
        if (hasLinkPreview || hasOldCaptionPreview || hasGamePreview || hasInvoicePreview || maxWidth - currentMessageObject.lastLineWidth < timeMore || currentMessageObject.hasRtl) {
            totalHeight += AndroidUtilities.dp(14);
            hasNewLineForTime = true;
            backgroundWidth = Math.max(maxChildWidth, currentMessageObject.lastLineWidth) + AndroidUtilities.dp(31);
            backgroundWidth = Math.max(backgroundWidth, (currentMessageObject.isOutOwner() ? timeWidth + AndroidUtilities.dp(17) : timeWidth) + AndroidUtilities.dp(31));
        } else {
            int diff = maxChildWidth - currentMessageObject.lastLineWidth;
            if (diff >= 0 && diff <= timeMore) {
                backgroundWidth = maxChildWidth + timeMore - diff + AndroidUtilities.dp(31);
            } else {
                backgroundWidth = Math.max(maxChildWidth, currentMessageObject.lastLineWidth + timeMore) + AndroidUtilities.dp(31);
            }
        }
    }

    public void setHighlightedText(String text) {
        MessageObject messageObject = messageObjectToSet != null ? messageObjectToSet : currentMessageObject;
        if (messageObject == null || messageObject.messageOwner.message == null || TextUtils.isEmpty(text)) {
            if (!urlPathSelection.isEmpty()) {
                linkSelectionBlockNum = -1;
                resetUrlPaths(true);
                invalidate();
            }
            return;
        }
        text = text.toLowerCase();
        String message = messageObject.messageOwner.message.toLowerCase();
        int start = -1;
        int length = -1;
        String punctuationsChars = " !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~\n";
        for (int a = 0, N1 = message.length(); a < N1; a++) {
            int currentLen = 0;
            for (int b = 0, N2 = Math.min(text.length(), N1 - a); b < N2; b++) {
                boolean match = message.charAt(a + b) == text.charAt(b);
                if (match) {
                    if (currentLen != 0 || a == 0 || punctuationsChars.indexOf(message.charAt(a - 1)) >= 0) {
                        currentLen++;
                    } else {
                        match = false;
                    }
                }
                if (!match || b == N2 - 1) {
                    if (currentLen > 0 && currentLen > length) {
                        length = currentLen;
                        start = a;
                    }
                    break;
                }
            }
        }
        if (start == -1) {
            if (!urlPathSelection.isEmpty()) {
                linkSelectionBlockNum = -1;
                resetUrlPaths(true);
                invalidate();
            }
            return;
        }
        for (int a = start + length, N = message.length(); a < N; a++) {
            if (punctuationsChars.indexOf(message.charAt(a)) < 0) {
                length++;
            } else {
                break;
            }
        }
        int end = start + length;
        if (captionLayout != null && !TextUtils.isEmpty(messageObject.caption)) {
            resetUrlPaths(true);
            try {
                LinkPath path = obtainNewUrlPath(true);
                path.setCurrentLayout(captionLayout, start, 0);
                captionLayout.getSelectionPath(start, end, path);
            } catch (Exception e) {
                FileLog.e(e);
            }
            invalidate();
        } else if (messageObject.textLayoutBlocks != null) {
            for (int c = 0; c < messageObject.textLayoutBlocks.size(); c++) {
                MessageObject.TextLayoutBlock block = messageObject.textLayoutBlocks.get(c);
                if (start >= block.charactersOffset && start < block.charactersOffset + block.textLayout.getText().length()) {
                    linkSelectionBlockNum = c;
                    resetUrlPaths(true);
                    try {
                        LinkPath path = obtainNewUrlPath(true);
                        path.setCurrentLayout(block.textLayout, start, 0);
                        block.textLayout.getSelectionPath(start, end - block.charactersOffset, path);
                        if (end >= block.charactersOffset + length) {
                            for (int a = c + 1; a < messageObject.textLayoutBlocks.size(); a++) {
                                MessageObject.TextLayoutBlock nextBlock = messageObject.textLayoutBlocks.get(a);
                                length = nextBlock.textLayout.getText().length();
                                path = obtainNewUrlPath(true);
                                path.setCurrentLayout(nextBlock.textLayout, 0, nextBlock.height);
                                nextBlock.textLayout.getSelectionPath(0, end - nextBlock.charactersOffset, path);
                                if (end < block.charactersOffset + length - 1) {
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    invalidate();
                    break;
                }
            }
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == selectorDrawable;
    }

    private boolean isCurrentLocationTimeExpired(MessageObject messageObject) {
        if (currentMessageObject.messageOwner.media.period % 60 == 0) {
            return Math.abs(ConnectionsManager.getInstance(currentAccount).getCurrentTime() - messageObject.messageOwner.date) > messageObject.messageOwner.media.period;
        } else {
            return Math.abs(ConnectionsManager.getInstance(currentAccount).getCurrentTime() - messageObject.messageOwner.date) > messageObject.messageOwner.media.period - 5;
        }
    }

    private void checkLocationExpired() {
        if (currentMessageObject == null) {
            return;
        }
        boolean newExpired = isCurrentLocationTimeExpired(currentMessageObject);
        if (newExpired != locationExpired) {
            locationExpired = newExpired;
            if (!locationExpired) {
                AndroidUtilities.runOnUIThread(invalidateRunnable, 1000);
                scheduledInvalidate = true;
                int maxWidth = backgroundWidth - AndroidUtilities.dp(37 + 54);
                docTitleLayout = new StaticLayout(TextUtils.ellipsize(LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation), Theme.chat_locationTitlePaint, maxWidth, TextUtils.TruncateAt.END), Theme.chat_locationTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            } else {
                MessageObject messageObject = currentMessageObject;
                currentMessageObject = null;
                setMessageObject(messageObject, currentMessagesGroup, pinnedBottom, pinnedTop);
            }
        }
    }

    public void setMessageObject(MessageObject messageObject, MessageObject.GroupedMessages groupedMessages, boolean bottomNear, boolean topNear) {
        if (attachedToWindow) {
            setMessageContent(messageObject, groupedMessages, bottomNear, topNear);
        } else {
            messageObjectToSet = messageObject;
            groupedMessagesToSet = groupedMessages;
            bottomNearToSet = bottomNear;
            topNearToSet = topNear;
        }
    }

    private int getAdditionalWidthForPosition(MessageObject.GroupedMessagePosition position) {
        int w = 0;
        if (position != null) {
            if ((position.flags & MessageObject.POSITION_FLAG_RIGHT) == 0) {
                w += AndroidUtilities.dp(4);
            }
            if ((position.flags & MessageObject.POSITION_FLAG_LEFT) == 0) {
                w += AndroidUtilities.dp(4);
            }
        }
        return w;
    }

    private void createSelectorDrawable() {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        if (selectorDrawable == null) {
            final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            maskPaint.setColor(0xffffffff);
            Drawable maskDrawable = new Drawable() {

                RectF rect = new RectF();

                @Override
                public void draw(Canvas canvas) {
                    android.graphics.Rect bounds = getBounds();
                    rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
                    canvas.drawRoundRect(rect, selectorDrawableMaskType == 0 ? AndroidUtilities.dp(6) : 0, selectorDrawableMaskType == 0 ? AndroidUtilities.dp(6) : 0, maskPaint);
                }

                @Override
                public void setAlpha(int alpha) {

                }

                @Override
                public void setColorFilter(ColorFilter colorFilter) {

                }

                @Override
                public int getOpacity() {
                    return PixelFormat.TRANSPARENT;
                }
            };
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText) & 0x5fffffff}
            );
            selectorDrawable = new RippleDrawable(colorStateList, null, maskDrawable);
            selectorDrawable.setCallback(this);
        } else {
            Theme.setSelectorDrawableColor(selectorDrawable, Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText) & 0x5fffffff, true);
        }
        selectorDrawable.setVisible(true, false);
    }

    private void createInstantViewButton() {
        if (Build.VERSION.SDK_INT >= 21 && drawInstantView) {
            createSelectorDrawable();
        }
        if (drawInstantView && instantViewLayout == null) {
            String str;
            instantWidth = AndroidUtilities.dp(12 + 9 + 12);
            if (drawInstantViewType == 1) {
                str = LocaleController.getString("OpenChannel", R.string.OpenChannel);
            } else if (drawInstantViewType == 2) {
                str = LocaleController.getString("OpenGroup", R.string.OpenGroup);
            } else if (drawInstantViewType == 3) {
                str = LocaleController.getString("OpenMessage", R.string.OpenMessage);
            } else if (drawInstantViewType == 5) {
                str = LocaleController.getString("ViewContact", R.string.ViewContact);
            } else if (drawInstantViewType == 6) {
                str = LocaleController.getString("OpenBackground", R.string.OpenBackground);
            } else if (drawInstantViewType == 7) {
                str = LocaleController.getString("OpenTheme", R.string.OpenTheme);
            } else {
                str = LocaleController.getString("InstantView", R.string.InstantView);
            }
            int mWidth = backgroundWidth - AndroidUtilities.dp(10 + 24 + 10 + 31);
            instantViewLayout = new StaticLayout(TextUtils.ellipsize(str, Theme.chat_instantViewPaint, mWidth, TextUtils.TruncateAt.END), Theme.chat_instantViewPaint, mWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            instantWidth = backgroundWidth - AndroidUtilities.dp(34);
            totalHeight += AndroidUtilities.dp(46);
            if (currentMessageObject.type == 12) {
                totalHeight += AndroidUtilities.dp(14);
            }
            if (instantViewLayout != null && instantViewLayout.getLineCount() > 0) {
                instantTextX = (int) (instantWidth - Math.ceil(instantViewLayout.getLineWidth(0))) / 2 + (drawInstantViewType == 0 ? AndroidUtilities.dp(8) : 0);
                instantTextLeftX = (int) instantViewLayout.getLineLeft(0);
                instantTextX += -instantTextLeftX;
            }
        }
    }

    @Override
    public void requestLayout() {
        if (inLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (currentMessageObject != null && (currentMessageObject.checkLayout() || lastHeight != AndroidUtilities.displaySize.y)) {
            inLayout = true;
            MessageObject messageObject = currentMessageObject;
            currentMessageObject = null;
            setMessageObject(messageObject, currentMessagesGroup, pinnedBottom, pinnedTop);
            inLayout = false;
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight + keyboardHeight);
    }

    public void forceResetMessageObject() {
        MessageObject messageObject = messageObjectToSet != null ? messageObjectToSet : currentMessageObject;
        currentMessageObject = null;
        setMessageObject(messageObject, currentMessagesGroup, pinnedBottom, pinnedTop);
    }

    private int getGroupPhotosWidth() {
        if (!AndroidUtilities.isInMultiwindow && AndroidUtilities.isTablet() && (!AndroidUtilities.isSmallTablet() || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)) {
            int leftWidth = AndroidUtilities.displaySize.x / 100 * 35;
            if (leftWidth < AndroidUtilities.dp(320)) {
                leftWidth = AndroidUtilities.dp(320);
            }
            return AndroidUtilities.displaySize.x - leftWidth;
        } else {
            return AndroidUtilities.displaySize.x;
        }
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (currentMessageObject == null) {
            return;
        }
        if (changed || !wasLayout) {
            layoutWidth = getMeasuredWidth();
            layoutHeight = getMeasuredHeight() - substractBackgroundHeight;
            if (timeTextWidth < 0) {
                timeTextWidth = AndroidUtilities.dp(10);
            }
            timeLayout = new StaticLayout(currentTimeString, Theme.chat_timePaint, timeTextWidth + AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (!mediaBackground) {
                if (!currentMessageObject.isOutOwner()) {
                    timeX = backgroundWidth - AndroidUtilities.dp(9) - timeWidth + (isAvatarVisible ? AndroidUtilities.dp(48) : 0);
                } else {
                    timeX = layoutWidth - timeWidth - AndroidUtilities.dp(38.5f);
                }
            } else {
                if (!currentMessageObject.isOutOwner()) {
                    timeX = backgroundWidth - AndroidUtilities.dp(4) - timeWidth + (isAvatarVisible ? AndroidUtilities.dp(48) : 0);

                    if (currentPosition != null && currentPosition.leftSpanOffset != 0) {
                        timeX += (int) Math.ceil(currentPosition.leftSpanOffset / 1000.0f * getGroupPhotosWidth());
                    }
                } else {
                    timeX = layoutWidth - timeWidth - AndroidUtilities.dp(42.0f);
                }
            }

            if ((currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                viewsLayout = new StaticLayout(currentViewsString, Theme.chat_timePaint, viewsTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            } else {
                viewsLayout = null;
            }

            if (isAvatarVisible) {
                avatarImage.setImageCoords(AndroidUtilities.dp(6), avatarImage.getImageY(), AndroidUtilities.dp(42), AndroidUtilities.dp(42));
            }

            wasLayout = true;
        }

        if (currentMessageObject.type == 0) {
            textY = AndroidUtilities.dp(10) + namesOffset;
        }
        if (currentMessageObject.isRoundVideo()) {
            updatePlayingMessageProgress();
        }
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
            if (currentMessageObject.isOutOwner()) {
                seekBarX = layoutWidth - backgroundWidth + AndroidUtilities.dp(57);
                buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(14);
                timeAudioX = layoutWidth - backgroundWidth + AndroidUtilities.dp(67);
            } else {
                if (isChat && currentMessageObject.needDrawAvatar()) {
                    seekBarX = AndroidUtilities.dp(114);
                    buttonX = AndroidUtilities.dp(71);
                    timeAudioX = AndroidUtilities.dp(124);
                } else {
                    seekBarX = AndroidUtilities.dp(66);
                    buttonX = AndroidUtilities.dp(23);
                    timeAudioX = AndroidUtilities.dp(76);
                }
            }
            if (hasLinkPreview) {
                seekBarX += AndroidUtilities.dp(10);
                buttonX += AndroidUtilities.dp(10);
                timeAudioX += AndroidUtilities.dp(10);
            }
            seekBarWaveform.setSize(backgroundWidth - AndroidUtilities.dp(92 + (hasLinkPreview ? 10 : 0)), AndroidUtilities.dp(30));
            seekBar.setSize(backgroundWidth - AndroidUtilities.dp(72 + (hasLinkPreview ? 10 : 0)), AndroidUtilities.dp(30));
            seekBarY = AndroidUtilities.dp(13) + namesOffset + mediaOffsetY;
            buttonY = AndroidUtilities.dp(13) + namesOffset + mediaOffsetY;
            radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(44), buttonY + AndroidUtilities.dp(44));

            updatePlayingMessageProgress();
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (currentMessageObject.isOutOwner()) {
                seekBarX = layoutWidth - backgroundWidth + AndroidUtilities.dp(56);
                buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(14);
                timeAudioX = layoutWidth - backgroundWidth + AndroidUtilities.dp(67);
            } else {
                if (isChat && currentMessageObject.needDrawAvatar()) {
                    seekBarX = AndroidUtilities.dp(113);
                    buttonX = AndroidUtilities.dp(71);
                    timeAudioX = AndroidUtilities.dp(124);
                } else {
                    seekBarX = AndroidUtilities.dp(65);
                    buttonX = AndroidUtilities.dp(23);
                    timeAudioX = AndroidUtilities.dp(76);
                }
            }
            if (hasLinkPreview) {
                seekBarX += AndroidUtilities.dp(10);
                buttonX += AndroidUtilities.dp(10);
                timeAudioX += AndroidUtilities.dp(10);
            }
            seekBar.setSize(backgroundWidth - AndroidUtilities.dp(65 + (hasLinkPreview ? 10 : 0)), AndroidUtilities.dp(30));
            seekBarY = AndroidUtilities.dp(29) + namesOffset + mediaOffsetY;
            buttonY = AndroidUtilities.dp(13) + namesOffset + mediaOffsetY;
            radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(44), buttonY + AndroidUtilities.dp(44));

            updatePlayingMessageProgress();
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT && !drawPhotoImage) {
            if (currentMessageObject.isOutOwner()) {
                buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(14);
            } else {
                if (isChat && currentMessageObject.needDrawAvatar()) {
                    buttonX = AndroidUtilities.dp(71);
                } else {
                    buttonX = AndroidUtilities.dp(23);
                }
            }
            if (hasLinkPreview) {
                buttonX += AndroidUtilities.dp(10);
            }
            buttonY = AndroidUtilities.dp(13) + namesOffset + mediaOffsetY;
            radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(44), buttonY + AndroidUtilities.dp(44));
            photoImage.setImageCoords(buttonX - AndroidUtilities.dp(10), buttonY - AndroidUtilities.dp(10), photoImage.getImageWidth(), photoImage.getImageHeight());
        } else if (currentMessageObject.type == 12) {
            int x;

            if (currentMessageObject.isOutOwner()) {
                x = layoutWidth - backgroundWidth + AndroidUtilities.dp(14);
            } else {
                if (isChat && currentMessageObject.needDrawAvatar()) {
                    x = AndroidUtilities.dp(72);
                } else {
                    x = AndroidUtilities.dp(23);
                }
            }
            photoImage.setImageCoords(x, AndroidUtilities.dp(13) + namesOffset, AndroidUtilities.dp(44), AndroidUtilities.dp(44));
        } else {
            int x;
            if (currentMessageObject.type == 0 && (hasLinkPreview || hasGamePreview || hasInvoicePreview)) {
                int linkX;
                if (hasGamePreview) {
                    linkX = unmovedTextX - AndroidUtilities.dp(10);
                } else if (hasInvoicePreview) {
                    linkX = unmovedTextX + AndroidUtilities.dp(1);
                } else {
                    linkX = unmovedTextX + AndroidUtilities.dp(1);
                }
                if (isSmallImage) {
                    x = linkX + backgroundWidth - AndroidUtilities.dp(81);
                } else {
                    x = linkX + (hasInvoicePreview ? -AndroidUtilities.dp(6.3f) : AndroidUtilities.dp(10));
                }
            } else {
                if (currentMessageObject.isOutOwner()) {
                    if (mediaBackground) {
                        x = layoutWidth - backgroundWidth - AndroidUtilities.dp(3);
                    } else {
                        x = layoutWidth - backgroundWidth + AndroidUtilities.dp(6);
                    }
                } else {
                    if (isChat && isAvatarVisible) {
                        x = AndroidUtilities.dp(63);
                    } else {
                        x = AndroidUtilities.dp(15);
                    }
                    if (currentPosition != null && !currentPosition.edge) {
                        x -= AndroidUtilities.dp(10);
                    }
                }
            }
            if (currentPosition != null) {
                if ((currentPosition.flags & MessageObject.POSITION_FLAG_LEFT) == 0) {
                    x -= AndroidUtilities.dp(2);
                }
                if (currentPosition.leftSpanOffset != 0) {
                    x += (int) Math.ceil(currentPosition.leftSpanOffset / 1000.0f * getGroupPhotosWidth());
                }
            }
            if (currentMessageObject.type != 0) {
                x -= AndroidUtilities.dp(2);
            }
            photoImage.setImageCoords(x, photoImage.getImageY(), photoImage.getImageWidth(), photoImage.getImageHeight());
            buttonX = (int) (x + (photoImage.getImageWidth() - AndroidUtilities.dp(48)) / 2.0f);
            buttonY = photoImage.getImageY() + (photoImage.getImageHeight() - AndroidUtilities.dp(48)) / 2;
            radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(48), buttonY + AndroidUtilities.dp(48));
            deleteProgressRect.set(buttonX + AndroidUtilities.dp(5), buttonY + AndroidUtilities.dp(5), buttonX + AndroidUtilities.dp(43), buttonY + AndroidUtilities.dp(43));
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                videoButtonX = photoImage.getImageX() + AndroidUtilities.dp(8);
                videoButtonY = photoImage.getImageY() + AndroidUtilities.dp(8);
                videoRadialProgress.setProgressRect(videoButtonX, videoButtonY, videoButtonX + AndroidUtilities.dp(24), videoButtonY + AndroidUtilities.dp(24));
            }
        }
    }

    public boolean needDelayRoundProgressDraw() {
        return (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) && currentMessageObject.type != 5 && MediaController.getInstance().isPlayingMessage(currentMessageObject);
    }

    public void drawRoundProgress(Canvas canvas) {
        rect.set(photoImage.getImageX() + AndroidUtilities.dpf2(1.5f), photoImage.getImageY() + AndroidUtilities.dpf2(1.5f), photoImage.getImageX2() - AndroidUtilities.dpf2(1.5f), photoImage.getImageY2() - AndroidUtilities.dpf2(1.5f));
        canvas.drawArc(rect, -90, 360 * currentMessageObject.audioProgress, false, Theme.chat_radialProgressPaint);
    }

    private void updatePollAnimations() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - voteLastUpdateTime;
        if (dt > 17) {
            dt = 17;
        }
        voteLastUpdateTime = newTime;
        if (pollVoteInProgress) {
            voteRadOffset += 360 * dt / 2000.0f;
            int count = (int) (voteRadOffset / 360);
            voteRadOffset -= count * 360;

            voteCurrentProgressTime += dt;
            if (voteCurrentProgressTime >= 500.0f) {
                voteCurrentProgressTime = 500.0f;
            }
            if (voteRisingCircleLength) {
                voteCurrentCircleLength = 4 + 266 * AndroidUtilities.accelerateInterpolator.getInterpolation(voteCurrentProgressTime / 500.0f);
            } else {
                voteCurrentCircleLength = 4 - (firstCircleLength ? 360 : 270) * (1.0f - AndroidUtilities.decelerateInterpolator.getInterpolation(voteCurrentProgressTime / 500.0f));
            }
            if (voteCurrentProgressTime == 500.0f) {
                if (voteRisingCircleLength) {
                    voteRadOffset += 270;
                    voteCurrentCircleLength = -266;
                }
                voteRisingCircleLength = !voteRisingCircleLength;
                if (firstCircleLength) {
                    firstCircleLength = false;
                }
                voteCurrentProgressTime = 0;
            }
            invalidate();
        }
        if (animatePollAnswer) {
            pollAnimationProgressTime += dt;
            if (pollAnimationProgressTime >= 300.0f) {
                pollAnimationProgressTime = 300.0f;
            }
            pollAnimationProgress = AndroidUtilities.decelerateInterpolator.getInterpolation(pollAnimationProgressTime / 300.0f);
            if (pollAnimationProgress >= 1.0f) {
                pollAnimationProgress = 1.0f;
                animatePollAnswer = false;
                animatePollAnswerAlpha = false;
                pollVoteInProgress = false;
                pollUnvoteInProgress = false;
            }
            invalidate();
        }
    }

    private void drawContent(Canvas canvas) {
        if (needNewVisiblePart && currentMessageObject.type == 0) {
            getLocalVisibleRect(scrollRect);
            setVisiblePart(scrollRect.top, scrollRect.bottom - scrollRect.top);
            needNewVisiblePart = false;
        }
        forceNotDrawTime = currentMessagesGroup != null;
        photoImage.setVisible(!PhotoViewer.isShowingImage(currentMessageObject) && !SecretMediaViewer.getInstance().isShowingImage(currentMessageObject), false);
        if (!photoImage.getVisible()) {
            mediaWasInvisible = true;
            timeWasInvisible = true;
            if (animatingNoSound == 1) {
                animatingNoSoundProgress = 0.0f;
                animatingNoSound = 0;
            } else if (animatingNoSound == 2) {
                animatingNoSoundProgress = 1.0f;
                animatingNoSound = 0;
            }
        } else if (groupPhotoInvisible) {
            timeWasInvisible = true;
        } else if (mediaWasInvisible || timeWasInvisible) {
            if (mediaWasInvisible) {
                controlsAlpha = 0.0f;
                mediaWasInvisible = false;
            }
            if (timeWasInvisible) {
                timeAlpha = 0.0f;
                timeWasInvisible = false;
            }
            lastControlsAlphaChangeTime = System.currentTimeMillis();
            totalChangeTime = 0;
        }
        radialProgress.setProgressColor(Theme.getColor(Theme.key_chat_mediaProgress));
        videoRadialProgress.setProgressColor(Theme.getColor(Theme.key_chat_mediaProgress));

        boolean imageDrawn = false;
        if (currentMessageObject.type == 0) {
            if (currentMessageObject.isOutOwner()) {
                textX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(11);
            } else {
                textX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(!mediaBackground && drawPinnedBottom ? 11 : 17);
            }
            if (hasGamePreview) {
                textX += AndroidUtilities.dp(11);
                textY = AndroidUtilities.dp(14) + namesOffset;
                if (siteNameLayout != null) {
                    textY += siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
                }
            } else if (hasInvoicePreview) {
                textY = AndroidUtilities.dp(14) + namesOffset;
                if (siteNameLayout != null) {
                    textY += siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
                }
            } else {
                textY = AndroidUtilities.dp(10) + namesOffset;
            }
            unmovedTextX = textX;
            if (currentMessageObject.textXOffset != 0 && replyNameLayout != null) {
                int diff = backgroundWidth - AndroidUtilities.dp(31) - currentMessageObject.textWidth;
                if (!hasNewLineForTime) {
                    diff -= timeWidth + AndroidUtilities.dp(4 + (currentMessageObject.isOutOwner() ? 20 : 0));
                }
                if (diff > 0) {
                    textX += diff;
                }
            }
            if (currentMessageObject.textLayoutBlocks != null && !currentMessageObject.textLayoutBlocks.isEmpty()) {
                if (fullyDraw) {
                    firstVisibleBlockNum = 0;
                    lastVisibleBlockNum = currentMessageObject.textLayoutBlocks.size();
                }
                if (firstVisibleBlockNum >= 0) {
                    for (int a = firstVisibleBlockNum; a <= lastVisibleBlockNum; a++) {
                        if (a >= currentMessageObject.textLayoutBlocks.size()) {
                            break;
                        }
                        MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(a);
                        canvas.save();
                        canvas.translate(textX - (block.isRtl() ? (int) Math.ceil(currentMessageObject.textXOffset) : 0), textY + block.textYOffset);
                        if (pressedLink != null && a == linkBlockNum) {
                            for (int b = 0; b < urlPath.size(); b++) {
                                canvas.drawPath(urlPath.get(b), Theme.chat_urlPaint);
                            }
                        }
                        if (a == linkSelectionBlockNum && !urlPathSelection.isEmpty()) {
                            for (int b = 0; b < urlPathSelection.size(); b++) {
                                canvas.drawPath(urlPathSelection.get(b), Theme.chat_textSearchSelectionPaint);
                            }
                        }
                        try {
                            block.textLayout.draw(canvas);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        canvas.restore();
                    }
                }
            }

            if (hasLinkPreview || hasGamePreview || hasInvoicePreview) {
                int startY;
                int linkX;
                if (hasGamePreview) {
                    startY = AndroidUtilities.dp(14) + namesOffset;
                    linkX = unmovedTextX - AndroidUtilities.dp(10);
                } else if (hasInvoicePreview) {
                    startY = AndroidUtilities.dp(14) + namesOffset;
                    linkX = unmovedTextX + AndroidUtilities.dp(1);
                } else {
                    startY = textY + currentMessageObject.textHeight + AndroidUtilities.dp(8);
                    linkX = unmovedTextX + AndroidUtilities.dp(1);
                }
                int linkPreviewY = startY;
                int smallImageStartY = 0;

                if (!hasInvoicePreview) {
                    Theme.chat_replyLinePaint.setColor(Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outPreviewLine : Theme.key_chat_inPreviewLine));
                    canvas.drawRect(linkX, linkPreviewY - AndroidUtilities.dp(3), linkX + AndroidUtilities.dp(2), linkPreviewY + linkPreviewHeight + AndroidUtilities.dp(3), Theme.chat_replyLinePaint);
                }

                if (siteNameLayout != null) {
                    Theme.chat_replyNamePaint.setColor(Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outSiteNameText : Theme.key_chat_inSiteNameText));
                    canvas.save();
                    int x;
                    if (siteNameRtl) {
                        x = backgroundWidth - siteNameWidth - AndroidUtilities.dp(32);
                    } else {
                        x = (hasInvoicePreview ? 0 : AndroidUtilities.dp(10));
                    }
                    canvas.translate(linkX + x, linkPreviewY - AndroidUtilities.dp(3));
                    siteNameLayout.draw(canvas);
                    canvas.restore();
                    linkPreviewY += siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
                }
                if ((hasGamePreview || hasInvoicePreview) && currentMessageObject.textHeight != 0) {
                    startY += currentMessageObject.textHeight + AndroidUtilities.dp(4);
                    linkPreviewY += currentMessageObject.textHeight + AndroidUtilities.dp(4);
                }

                if (drawPhotoImage && drawInstantView || drawInstantViewType == 6 && imageBackgroundColor != 0) {
                    if (linkPreviewY != startY) {
                        linkPreviewY += AndroidUtilities.dp(2);
                    }
                    if (imageBackgroundSideColor != 0) {
                        int x = linkX + AndroidUtilities.dp(10);
                        photoImage.setImageCoords(x + (imageBackgroundSideWidth - photoImage.getImageWidth()) / 2, linkPreviewY, photoImage.getImageWidth(), photoImage.getImageHeight());
                        rect.set(x, photoImage.getImageY(), x + imageBackgroundSideWidth, photoImage.getImageY2());
                        Theme.chat_instantViewPaint.setColor(imageBackgroundSideColor);
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_instantViewPaint);
                    } else {
                        photoImage.setImageCoords(linkX + AndroidUtilities.dp(10), linkPreviewY, photoImage.getImageWidth(), photoImage.getImageHeight());
                    }
                    if (imageBackgroundColor != 0) {
                        Theme.chat_instantViewPaint.setColor(imageBackgroundColor);
                        rect.set(photoImage.getImageX(), photoImage.getImageY(), photoImage.getImageX2(), photoImage.getImageY2());
                        if (imageBackgroundSideColor != 0) {
                            canvas.drawRect(photoImage.getImageX(), photoImage.getImageY(), photoImage.getImageX2(), photoImage.getImageY2(), Theme.chat_instantViewPaint);
                        } else {
                            canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_instantViewPaint);
                        }
                    }
                    if (drawPhotoImage && drawInstantView) {
                        if (drawImageButton) {
                            int size = AndroidUtilities.dp(48);
                            buttonX = (int) (photoImage.getImageX() + (photoImage.getImageWidth() - size) / 2.0f);
                            buttonY = (int) (photoImage.getImageY() + (photoImage.getImageHeight() - size) / 2.0f);
                            radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);
                        }
                        imageDrawn = photoImage.draw(canvas);
                    }
                    linkPreviewY += photoImage.getImageHeight() + AndroidUtilities.dp(6);
                }

                if (currentMessageObject.isOutOwner()) {
                    Theme.chat_replyNamePaint.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
                    Theme.chat_replyTextPaint.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
                } else {
                    Theme.chat_replyNamePaint.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
                    Theme.chat_replyTextPaint.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
                }
                if (titleLayout != null) {
                    if (linkPreviewY != startY) {
                        linkPreviewY += AndroidUtilities.dp(2);
                    }
                    smallImageStartY = linkPreviewY - AndroidUtilities.dp(1);
                    canvas.save();
                    canvas.translate(linkX + AndroidUtilities.dp(10) + titleX, linkPreviewY - AndroidUtilities.dp(3));
                    titleLayout.draw(canvas);
                    canvas.restore();
                    linkPreviewY += titleLayout.getLineBottom(titleLayout.getLineCount() - 1);
                }

                if (authorLayout != null) {
                    if (linkPreviewY != startY) {
                        linkPreviewY += AndroidUtilities.dp(2);
                    }
                    if (smallImageStartY == 0) {
                        smallImageStartY = linkPreviewY - AndroidUtilities.dp(1);
                    }
                    canvas.save();
                    canvas.translate(linkX + AndroidUtilities.dp(10) + authorX, linkPreviewY - AndroidUtilities.dp(3));
                    authorLayout.draw(canvas);
                    canvas.restore();
                    linkPreviewY += authorLayout.getLineBottom(authorLayout.getLineCount() - 1);
                }

                if (descriptionLayout != null) {
                    if (linkPreviewY != startY) {
                        linkPreviewY += AndroidUtilities.dp(2);
                    }
                    if (smallImageStartY == 0) {
                        smallImageStartY = linkPreviewY - AndroidUtilities.dp(1);
                    }
                    descriptionY = linkPreviewY - AndroidUtilities.dp(3);
                    canvas.save();
                    canvas.translate(linkX + (hasInvoicePreview ? 0 : AndroidUtilities.dp(10)) + descriptionX, descriptionY);
                    if (pressedLink != null && linkBlockNum == -10) {
                        for (int b = 0; b < urlPath.size(); b++) {
                            canvas.drawPath(urlPath.get(b), Theme.chat_urlPaint);
                        }
                    }
                    descriptionLayout.draw(canvas);
                    canvas.restore();
                    linkPreviewY += descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
                }

                if (drawPhotoImage && !drawInstantView) {
                    if (linkPreviewY != startY) {
                        linkPreviewY += AndroidUtilities.dp(2);
                    }

                    if (isSmallImage) {
                        photoImage.setImageCoords(linkX + backgroundWidth - AndroidUtilities.dp(81), smallImageStartY, photoImage.getImageWidth(), photoImage.getImageHeight());
                    } else {
                        photoImage.setImageCoords(linkX + (hasInvoicePreview ? -AndroidUtilities.dp(6.3f) : AndroidUtilities.dp(10)), linkPreviewY, photoImage.getImageWidth(), photoImage.getImageHeight());
                        if (drawImageButton) {
                            int size = AndroidUtilities.dp(48);
                            buttonX = (int) (photoImage.getImageX() + (photoImage.getImageWidth() - size) / 2.0f);
                            buttonY = (int) (photoImage.getImageY() + (photoImage.getImageHeight() - size) / 2.0f);
                            radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);
                        }
                    }
                    if (currentMessageObject.isRoundVideo() && MediaController.getInstance().isPlayingMessage(currentMessageObject) && MediaController.getInstance().isVideoDrawingReady()) {
                        imageDrawn = true;
                        drawTime = true;
                    } else {
                        imageDrawn = photoImage.draw(canvas);
                    }
                }
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                    videoButtonX = photoImage.getImageX() + AndroidUtilities.dp(8);
                    videoButtonY = photoImage.getImageY() + AndroidUtilities.dp(8);
                    videoRadialProgress.setProgressRect(videoButtonX, videoButtonY, videoButtonX + AndroidUtilities.dp(24), videoButtonY + AndroidUtilities.dp(24));
                }
                if (photosCountLayout != null && photoImage.getVisible()) {
                    int x = photoImage.getImageX() + photoImage.getImageWidth() - AndroidUtilities.dp(8) - photosCountWidth;
                    int y = photoImage.getImageY() + photoImage.getImageHeight() - AndroidUtilities.dp(19);
                    rect.set(x - AndroidUtilities.dp(4), y - AndroidUtilities.dp(1.5f), x + photosCountWidth + AndroidUtilities.dp(4), y + AndroidUtilities.dp(14.5f));
                    int oldAlpha = Theme.chat_timeBackgroundPaint.getAlpha();
                    Theme.chat_timeBackgroundPaint.setAlpha((int) (oldAlpha * controlsAlpha));
                    Theme.chat_durationPaint.setAlpha((int) (255 * controlsAlpha));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_timeBackgroundPaint);
                    Theme.chat_timeBackgroundPaint.setAlpha(oldAlpha);
                    canvas.save();
                    canvas.translate(x, y);
                    photosCountLayout.draw(canvas);
                    canvas.restore();
                    Theme.chat_durationPaint.setAlpha(255);
                }
                if (videoInfoLayout != null && (!drawPhotoImage || photoImage.getVisible()) && imageBackgroundSideColor == 0) {
                    int x;
                    int y;
                    if (hasGamePreview || hasInvoicePreview || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER) {
                        if (drawPhotoImage) {
                            x = photoImage.getImageX() + AndroidUtilities.dp(8.5f);
                            y = photoImage.getImageY() + AndroidUtilities.dp(6);
                            int height = AndroidUtilities.dp(documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER ? 14.5f : 16.5f);
                            rect.set(x - AndroidUtilities.dp(4), y - AndroidUtilities.dp(1.5f), x + durationWidth + AndroidUtilities.dp(4), y + height);
                            canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_timeBackgroundPaint);
                        } else {
                            x = linkX;
                            y = linkPreviewY;
                        }
                    } else {
                        x = photoImage.getImageX() + photoImage.getImageWidth() - AndroidUtilities.dp(8) - durationWidth;
                        y = photoImage.getImageY() + photoImage.getImageHeight() - AndroidUtilities.dp(19);
                        rect.set(x - AndroidUtilities.dp(4), y - AndroidUtilities.dp(1.5f), x + durationWidth + AndroidUtilities.dp(4), y + AndroidUtilities.dp(14.5f));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_timeBackgroundPaint);
                    }

                    canvas.save();
                    canvas.translate(x, y);
                    if (hasInvoicePreview) {
                        if (drawPhotoImage) {
                            Theme.chat_shipmentPaint.setColor(Theme.getColor(Theme.key_chat_previewGameText));
                        } else {
                            if (currentMessageObject.isOutOwner()) {
                                Theme.chat_shipmentPaint.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
                            } else {
                                Theme.chat_shipmentPaint.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
                            }
                        }
                    }
                    videoInfoLayout.draw(canvas);
                    canvas.restore();
                }

                if (drawInstantView) {
                    Drawable instantDrawable;
                    int instantY = startY + linkPreviewHeight + AndroidUtilities.dp(10);
                    Paint backPaint = Theme.chat_instantViewRectPaint;
                    if (currentMessageObject.isOutOwner()) {
                        instantDrawable = Theme.chat_msgOutInstantDrawable;
                        Theme.chat_instantViewPaint.setColor(Theme.getColor(Theme.key_chat_outPreviewInstantText));
                        backPaint.setColor(Theme.getColor(Theme.key_chat_outPreviewInstantText));
                    } else {
                        instantDrawable = Theme.chat_msgInInstantDrawable;
                        Theme.chat_instantViewPaint.setColor(Theme.getColor(Theme.key_chat_inPreviewInstantText));
                        backPaint.setColor(Theme.getColor(Theme.key_chat_inPreviewInstantText));
                    }

                    if (Build.VERSION.SDK_INT >= 21) {
                        selectorDrawableMaskType = 0;
                        selectorDrawable.setBounds(linkX, instantY, linkX + instantWidth, instantY + AndroidUtilities.dp(36));
                        selectorDrawable.draw(canvas);
                    }
                    rect.set(linkX, instantY, linkX + instantWidth, instantY + AndroidUtilities.dp(36));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), backPaint);
                    if (drawInstantViewType == 0) {
                        setDrawableBounds(instantDrawable, instantTextLeftX + instantTextX + linkX - AndroidUtilities.dp(15), instantY + AndroidUtilities.dp(11.5f), AndroidUtilities.dp(9), AndroidUtilities.dp(13));
                        instantDrawable.draw(canvas);
                    }
                    if (instantViewLayout != null) {
                        canvas.save();
                        canvas.translate(linkX + instantTextX, instantY + AndroidUtilities.dp(10.5f));
                        instantViewLayout.draw(canvas);
                        canvas.restore();
                    }
                }
            }
            drawTime = true;
        } else if (drawPhotoImage) {
            if (currentMessageObject.isRoundVideo() && MediaController.getInstance().isPlayingMessage(currentMessageObject) && MediaController.getInstance().isVideoDrawingReady()) {
                imageDrawn = true;
                drawTime = true;
            } else {
                if (currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO && Theme.chat_roundVideoShadow != null) {
                    int x = photoImage.getImageX() - AndroidUtilities.dp(3);
                    int y = photoImage.getImageY() - AndroidUtilities.dp(2);
                    Theme.chat_roundVideoShadow.setAlpha(255/*(int) (photoImage.getCurrentAlpha() * 255)*/);
                    Theme.chat_roundVideoShadow.setBounds(x, y, x + AndroidUtilities.roundMessageSize + AndroidUtilities.dp(6), y + AndroidUtilities.roundMessageSize + AndroidUtilities.dp(6));
                    Theme.chat_roundVideoShadow.draw(canvas);

                    if (!photoImage.hasBitmapImage() || photoImage.getCurrentAlpha() != 1) {
                        Theme.chat_docBackPaint.setColor(Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outBubble : Theme.key_chat_inBubble));
                        canvas.drawCircle(photoImage.getCenterX(), photoImage.getCenterY(), photoImage.getImageWidth() / 2, Theme.chat_docBackPaint);
                    }
                }
                drawPhotoCheckBox = photoCheckBox != null && (checkBoxVisible || photoCheckBox.getProgress() != 0 || checkBoxAnimationInProgress) && currentMessagesGroup != null && currentMessagesGroup.messages.size() > 1;
                if (drawPhotoCheckBox && (photoCheckBox.isChecked() || photoCheckBox.getProgress() != 0 || checkBoxAnimationInProgress)) {
                    Theme.chat_replyLinePaint.setColor(Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outBubbleSelected : Theme.key_chat_inBubbleSelected));
                    rect.set(photoImage.getImageX(), photoImage.getImageY(), photoImage.getImageX2(), photoImage.getImageY2());
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_replyLinePaint);
                    photoImage.setSideClip(AndroidUtilities.dp(14) * photoCheckBox.getProgress());
                    if (checkBoxAnimationInProgress) {
                        photoCheckBox.setBackgroundAlpha(checkBoxAnimationProgress);
                    } else {
                        photoCheckBox.setBackgroundAlpha(checkBoxVisible ? 1.0f : photoCheckBox.getProgress());
                    }
                } else {
                    photoImage.setSideClip(0);
                }
                imageDrawn = photoImage.draw(canvas);
                boolean drawTimeOld = drawTime;
                drawTime = photoImage.getVisible();
                if (currentPosition != null && drawTimeOld != drawTime) {
                    ViewGroup viewGroup = (ViewGroup) getParent();
                    if (viewGroup != null) {
                        if (!currentPosition.last) {
                            int count = viewGroup.getChildCount();
                            for (int a = 0; a < count; a++) {
                                View child = viewGroup.getChildAt(a);
                                if (child == this || !(child instanceof ChatMessageCell)) {
                                    continue;
                                }
                                ChatMessageCell cell = (ChatMessageCell) child;

                                if (cell.getCurrentMessagesGroup() == currentMessagesGroup) {
                                    MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
                                    if (position.last && position.maxY == currentPosition.maxY && cell.timeX - AndroidUtilities.dp(4) + cell.getLeft() < getRight()) {
                                        cell.groupPhotoInvisible = !drawTime;
                                        cell.invalidate();
                                        viewGroup.invalidate();
                                    }
                                }
                            }
                        } else {
                            viewGroup.invalidate();
                        }
                    }
                }
            }
        }

        if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
            if (photoImage.getVisible() && !hasGamePreview && !currentMessageObject.needDrawBluredPreview()) {
                int oldAlpha = ((BitmapDrawable) Theme.chat_msgMediaMenuDrawable).getPaint().getAlpha();
                Theme.chat_msgMediaMenuDrawable.setAlpha((int) (oldAlpha * controlsAlpha));
                setDrawableBounds(Theme.chat_msgMediaMenuDrawable, otherX = photoImage.getImageX() + photoImage.getImageWidth() - AndroidUtilities.dp(14), otherY = photoImage.getImageY() + AndroidUtilities.dp(8.1f));
                Theme.chat_msgMediaMenuDrawable.draw(canvas);
                Theme.chat_msgMediaMenuDrawable.setAlpha(oldAlpha);
            }
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
            if (durationLayout != null) {
                int x1;
                int y1;

                boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
                if (playing && currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                    drawRoundProgress(canvas);
                    drawOverlays(canvas);
                }
                if (currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                    x1 = backgroundDrawableLeft + AndroidUtilities.dp(8);
                    y1 = layoutHeight - AndroidUtilities.dp(28 - (drawPinnedBottom ? 2 : 0));
                    rect.set(x1, y1, x1 + timeWidthAudio + AndroidUtilities.dp(8 + 12 + 2), y1 + AndroidUtilities.dp(17));

                    int oldAlpha = Theme.chat_actionBackgroundPaint.getAlpha();
                    Theme.chat_actionBackgroundPaint.setAlpha((int) (oldAlpha * timeAlpha));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_actionBackgroundPaint);
                    Theme.chat_actionBackgroundPaint.setAlpha(oldAlpha);

                    if (!playing && currentMessageObject.isContentUnread()) {
                        Theme.chat_docBackPaint.setColor(Theme.getColor(Theme.key_chat_mediaTimeText));
                        Theme.chat_docBackPaint.setAlpha((int) (255 * timeAlpha));
                        canvas.drawCircle(x1 + timeWidthAudio + AndroidUtilities.dp(12), y1 + AndroidUtilities.dp(8.3f), AndroidUtilities.dp(3), Theme.chat_docBackPaint);
                    } else {
                        if (playing && !MediaController.getInstance().isMessagePaused()) {
                            roundVideoPlayingDrawable.start();
                        } else {
                            roundVideoPlayingDrawable.stop();
                        }
                        setDrawableBounds(roundVideoPlayingDrawable, x1 + timeWidthAudio + AndroidUtilities.dp(6), y1 + AndroidUtilities.dp(2.3f));
                        roundVideoPlayingDrawable.draw(canvas);
                    }
                    x1 += AndroidUtilities.dp(4);
                    y1 += AndroidUtilities.dp(1.7f);
                } else {
                    x1 = backgroundDrawableLeft + AndroidUtilities.dp(currentMessageObject.isOutOwner() || drawPinnedBottom ? 12 : 18);
                    y1 = layoutHeight - AndroidUtilities.dp(6.3f - (drawPinnedBottom ? 2 : 0)) - timeLayout.getHeight();
                }

                Theme.chat_timePaint.setAlpha((int) (255 * timeAlpha));
                canvas.save();
                canvas.translate(x1, y1);
                durationLayout.draw(canvas);
                canvas.restore();
                Theme.chat_timePaint.setAlpha(255);
            }
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (currentMessageObject.isOutOwner()) {
                Theme.chat_audioTitlePaint.setColor(Theme.getColor(Theme.key_chat_outAudioTitleText));
                Theme.chat_audioPerformerPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outAudioPerformerSelectedText : Theme.key_chat_outAudioPerformerText));
                Theme.chat_audioTimePaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outAudioDurationSelectedText : Theme.key_chat_outAudioDurationText));
                radialProgress.setProgressColor(Theme.getColor(isDrawSelectionBackground() || buttonPressed != 0 ? Theme.key_chat_outAudioSelectedProgress : Theme.key_chat_outAudioProgress));
            } else {
                Theme.chat_audioTitlePaint.setColor(Theme.getColor(Theme.key_chat_inAudioTitleText));
                Theme.chat_audioPerformerPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inAudioPerformerSelectedText : Theme.key_chat_inAudioPerformerText));
                Theme.chat_audioTimePaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inAudioDurationSelectedText : Theme.key_chat_inAudioDurationText));
                radialProgress.setProgressColor(Theme.getColor(isDrawSelectionBackground() || buttonPressed != 0 ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
            }

            radialProgress.draw(canvas);

            canvas.save();
            canvas.translate(timeAudioX + songX, AndroidUtilities.dp(13) + namesOffset + mediaOffsetY);
            songLayout.draw(canvas);
            canvas.restore();

            canvas.save();
            if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
                canvas.translate(seekBarX, seekBarY);
                seekBar.draw(canvas);
            } else {
                canvas.translate(timeAudioX + performerX, AndroidUtilities.dp(35) + namesOffset + mediaOffsetY);
                performerLayout.draw(canvas);
            }
            canvas.restore();

            canvas.save();
            canvas.translate(timeAudioX, AndroidUtilities.dp(57) + namesOffset + mediaOffsetY);
            durationLayout.draw(canvas);
            canvas.restore();

            Drawable menuDrawable;
            if (currentMessageObject.isOutOwner()) {
                menuDrawable = isDrawSelectionBackground() ? Theme.chat_msgOutMenuSelectedDrawable : Theme.chat_msgOutMenuDrawable;
            } else {
                menuDrawable = isDrawSelectionBackground() ? Theme.chat_msgInMenuSelectedDrawable : Theme.chat_msgInMenuDrawable;
            }
            setDrawableBounds(menuDrawable, otherX = buttonX + backgroundWidth - AndroidUtilities.dp(currentMessageObject.type == 0 ? 58 : 48), otherY = buttonY - AndroidUtilities.dp(5));
            menuDrawable.draw(canvas);
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
            if (currentMessageObject.isOutOwner()) {
                Theme.chat_audioTimePaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outAudioDurationSelectedText : Theme.key_chat_outAudioDurationText));
                radialProgress.setProgressColor(Theme.getColor(isDrawSelectionBackground() || buttonPressed != 0 ? Theme.key_chat_outAudioSelectedProgress : Theme.key_chat_outAudioProgress));
            } else {
                Theme.chat_audioTimePaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inAudioDurationSelectedText : Theme.key_chat_inAudioDurationText));
                radialProgress.setProgressColor(Theme.getColor(isDrawSelectionBackground() || buttonPressed != 0 ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
            }
            radialProgress.draw(canvas);

            canvas.save();
            if (useSeekBarWaweform) {
                canvas.translate(seekBarX + AndroidUtilities.dp(13), seekBarY);
                seekBarWaveform.draw(canvas);
            } else {
                canvas.translate(seekBarX, seekBarY);
                seekBar.draw(canvas);
            }
            canvas.restore();

            canvas.save();
            canvas.translate(timeAudioX, AndroidUtilities.dp(44) + namesOffset + mediaOffsetY);
            durationLayout.draw(canvas);
            canvas.restore();

            if (currentMessageObject.type != 0 && currentMessageObject.isContentUnread()) {
                Theme.chat_docBackPaint.setColor(Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outVoiceSeekbarFill : Theme.key_chat_inVoiceSeekbarFill));
                canvas.drawCircle(timeAudioX + timeWidthAudio + AndroidUtilities.dp(6), AndroidUtilities.dp(51) + namesOffset + mediaOffsetY, AndroidUtilities.dp(3), Theme.chat_docBackPaint);
            }
        }

        if (captionLayout != null) {
            if (currentMessageObject.type == 1 || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || currentMessageObject.type == 8) {
                captionX = photoImage.getImageX() + AndroidUtilities.dp(5) + captionOffsetX;
                captionY = photoImage.getImageY() + photoImage.getImageHeight() + AndroidUtilities.dp(6);
            } else if (hasOldCaptionPreview) {
                captionX = backgroundDrawableLeft + AndroidUtilities.dp(currentMessageObject.isOutOwner() ? 11 : 17) + captionOffsetX;
                captionY = totalHeight - captionHeight - AndroidUtilities.dp(drawPinnedTop ? 9 : 10) - linkPreviewHeight - AndroidUtilities.dp(17);
            } else {
                captionX = backgroundDrawableLeft + AndroidUtilities.dp(currentMessageObject.isOutOwner() || mediaBackground || !mediaBackground && drawPinnedBottom ? 11 : 17) + captionOffsetX;
                captionY = totalHeight - captionHeight - AndroidUtilities.dp(drawPinnedTop ? 9 : 10);
            }
        }
        if (currentPosition == null) {
            drawCaptionLayout(canvas, false);
        }

        if (hasOldCaptionPreview) {
            int linkX;
            if (currentMessageObject.type == 1 || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || currentMessageObject.type == 8) {
                linkX = photoImage.getImageX() + AndroidUtilities.dp(5);
            } else {
                linkX = backgroundDrawableLeft + AndroidUtilities.dp(currentMessageObject.isOutOwner() ? 11 : 17);
            }
            int startY = totalHeight - AndroidUtilities.dp(drawPinnedTop ? 9 : 10) - linkPreviewHeight - AndroidUtilities.dp(8);
            int linkPreviewY = startY;

            Theme.chat_replyLinePaint.setColor(Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outPreviewLine : Theme.key_chat_inPreviewLine));
            canvas.drawRect(linkX, linkPreviewY - AndroidUtilities.dp(3), linkX + AndroidUtilities.dp(2), linkPreviewY + linkPreviewHeight, Theme.chat_replyLinePaint);

            if (siteNameLayout != null) {
                Theme.chat_replyNamePaint.setColor(Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outSiteNameText : Theme.key_chat_inSiteNameText));
                canvas.save();
                int x;
                if (siteNameRtl) {
                    x = backgroundWidth - siteNameWidth - AndroidUtilities.dp(32);
                } else {
                    x = (hasInvoicePreview ? 0 : AndroidUtilities.dp(10));
                }
                canvas.translate(linkX + x, linkPreviewY - AndroidUtilities.dp(3));
                siteNameLayout.draw(canvas);
                canvas.restore();
                linkPreviewY += siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
            }

            if (currentMessageObject.isOutOwner()) {
                Theme.chat_replyTextPaint.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
            } else {
                Theme.chat_replyTextPaint.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
            }

            if (descriptionLayout != null) {
                if (linkPreviewY != startY) {
                    linkPreviewY += AndroidUtilities.dp(2);
                }
                descriptionY = linkPreviewY - AndroidUtilities.dp(3);
                canvas.save();
                canvas.translate(linkX + AndroidUtilities.dp(10) + descriptionX, descriptionY);
                descriptionLayout.draw(canvas);
                canvas.restore();
            }
            drawTime = true;
        }

        if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
            Drawable menuDrawable;
            if (currentMessageObject.isOutOwner()) {
                Theme.chat_docNamePaint.setColor(Theme.getColor(Theme.key_chat_outFileNameText));
                Theme.chat_infoPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outFileInfoSelectedText : Theme.key_chat_outFileInfoText));
                Theme.chat_docBackPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outFileBackgroundSelected : Theme.key_chat_outFileBackground));
                menuDrawable = isDrawSelectionBackground() ? Theme.chat_msgOutMenuSelectedDrawable : Theme.chat_msgOutMenuDrawable;
            } else {
                Theme.chat_docNamePaint.setColor(Theme.getColor(Theme.key_chat_inFileNameText));
                Theme.chat_infoPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inFileInfoSelectedText : Theme.key_chat_inFileInfoText));
                Theme.chat_docBackPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inFileBackgroundSelected : Theme.key_chat_inFileBackground));
                menuDrawable = isDrawSelectionBackground() ? Theme.chat_msgInMenuSelectedDrawable : Theme.chat_msgInMenuDrawable;
            }

            int x;
            int titleY;
            int subtitleY;
            if (drawPhotoImage) {
                if (currentMessageObject.type == 0) {
                    setDrawableBounds(menuDrawable, otherX = photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(56), otherY = photoImage.getImageY() + AndroidUtilities.dp(1));
                } else {
                    setDrawableBounds(menuDrawable, otherX = photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(40), otherY = photoImage.getImageY() + AndroidUtilities.dp(1));
                }

                x = photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10);
                titleY = photoImage.getImageY() + AndroidUtilities.dp(8);
                subtitleY = photoImage.getImageY() + (docTitleLayout != null ? docTitleLayout.getLineBottom(docTitleLayout.getLineCount() - 1) + AndroidUtilities.dp(13) : AndroidUtilities.dp(8));
                if (!imageDrawn) {
                    if (currentMessageObject.isOutOwner()) {
                        radialProgress.setColors(Theme.key_chat_outLoader, Theme.key_chat_outLoaderSelected, Theme.key_chat_outMediaIcon, Theme.key_chat_outMediaIconSelected);
                        radialProgress.setProgressColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outFileProgressSelected : Theme.key_chat_outFileProgress));
                        videoRadialProgress.setColors(Theme.key_chat_outLoader, Theme.key_chat_outLoaderSelected, Theme.key_chat_outMediaIcon, Theme.key_chat_outMediaIconSelected);
                        videoRadialProgress.setProgressColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outFileProgressSelected : Theme.key_chat_outFileProgress));
                    } else {
                        radialProgress.setColors(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);
                        radialProgress.setProgressColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inFileProgressSelected : Theme.key_chat_inFileProgress));
                        videoRadialProgress.setColors(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);
                        videoRadialProgress.setProgressColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inFileProgressSelected : Theme.key_chat_inFileProgress));
                    }

                    rect.set(photoImage.getImageX(), photoImage.getImageY(), photoImage.getImageX() + photoImage.getImageWidth(), photoImage.getImageY() + photoImage.getImageHeight());
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), Theme.chat_docBackPaint);
                } else {
                    radialProgress.setColors(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
                    radialProgress.setProgressColor(Theme.getColor(Theme.key_chat_mediaProgress));
                    videoRadialProgress.setColors(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
                    videoRadialProgress.setProgressColor(Theme.getColor(Theme.key_chat_mediaProgress));

                    if (buttonState == -1 && radialProgress.getIcon() != MediaActionDrawable.ICON_NONE) {
                        radialProgress.setIcon(MediaActionDrawable.ICON_NONE, true, true);
                    }
                }
            } else {
                setDrawableBounds(menuDrawable, otherX = buttonX + backgroundWidth - AndroidUtilities.dp(currentMessageObject.type == 0 ? 58 : 48), otherY = buttonY - AndroidUtilities.dp(5));
                x = buttonX + AndroidUtilities.dp(53);
                titleY = buttonY + AndroidUtilities.dp(4);
                subtitleY = buttonY + AndroidUtilities.dp(27);
                if (docTitleLayout != null && docTitleLayout.getLineCount() > 1) {
                    subtitleY += (docTitleLayout.getLineCount() - 1) * AndroidUtilities.dp(16) + AndroidUtilities.dp(2);
                }
                if (currentMessageObject.isOutOwner()) {
                    radialProgress.setProgressColor(Theme.getColor(isDrawSelectionBackground() || buttonPressed != 0 ? Theme.key_chat_outAudioSelectedProgress : Theme.key_chat_outAudioProgress));
                    videoRadialProgress.setProgressColor(Theme.getColor(isDrawSelectionBackground() || videoButtonPressed != 0 ? Theme.key_chat_outAudioSelectedProgress : Theme.key_chat_outAudioProgress));
                } else {
                    radialProgress.setProgressColor(Theme.getColor(isDrawSelectionBackground() || buttonPressed != 0 ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
                    videoRadialProgress.setProgressColor(Theme.getColor(isDrawSelectionBackground() || videoButtonPressed != 0 ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
                }
            }
            menuDrawable.draw(canvas);

            try {
                if (docTitleLayout != null) {
                    canvas.save();
                    canvas.translate(x + docTitleOffsetX, titleY);
                    docTitleLayout.draw(canvas);
                    canvas.restore();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            try {
                if (infoLayout != null) {
                    canvas.save();
                    canvas.translate(x, subtitleY);
                    infoLayout.draw(canvas);
                    canvas.restore();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (buttonState == -1 && currentMessageObject.needDrawBluredPreview() && !MediaController.getInstance().isPlayingMessage(currentMessageObject) && photoImage.getVisible()) {
            if (currentMessageObject.messageOwner.destroyTime != 0) {
                if (!currentMessageObject.isOutOwner()) {
                    long msTime = System.currentTimeMillis() + ConnectionsManager.getInstance(currentAccount).getTimeDifference() * 1000;
                    float progress = Math.max(0, (long) currentMessageObject.messageOwner.destroyTime * 1000 - msTime) / (currentMessageObject.messageOwner.ttl * 1000.0f);
                    Theme.chat_deleteProgressPaint.setAlpha((int) (255 * controlsAlpha));
                    canvas.drawArc(deleteProgressRect, -90, -360 * progress, true, Theme.chat_deleteProgressPaint);
                    if (progress != 0) {
                        int offset = AndroidUtilities.dp(2);
                        invalidate((int) deleteProgressRect.left - offset, (int) deleteProgressRect.top - offset, (int) deleteProgressRect.right + offset * 2, (int) deleteProgressRect.bottom + offset * 2);
                    }
                }
                updateSecretTimeText(currentMessageObject);
            }
        }
        if (currentMessageObject.type == 4 && !(currentMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) && currentMapProvider == 2 && photoImage.hasNotThumb()) {
            int w = (int) (Theme.chat_redLocationIcon.getIntrinsicWidth() * 0.8f);
            int h = (int) (Theme.chat_redLocationIcon.getIntrinsicHeight() * 0.8f);
            int x = photoImage.getImageX() + (photoImage.getImageWidth() - w) / 2;
            int y = photoImage.getImageY() + (photoImage.getImageHeight() / 2 - h);
            Theme.chat_redLocationIcon.setAlpha((int) (255 * photoImage.getCurrentAlpha()));
            Theme.chat_redLocationIcon.setBounds(x, y, x + w, y + h);
            Theme.chat_redLocationIcon.draw(canvas);
        }

        if (!botButtons.isEmpty()) {
            int addX;
            if (currentMessageObject.isOutOwner()) {
                addX = getMeasuredWidth() - widthForButtons - AndroidUtilities.dp(10);
            } else {
                addX = backgroundDrawableLeft + AndroidUtilities.dp(mediaBackground || drawPinnedBottom ? 1 : 7);
            }
            for (int a = 0; a < botButtons.size(); a++) {
                BotButton button = botButtons.get(a);
                int y = button.y + layoutHeight - AndroidUtilities.dp(2);
                Theme.chat_systemDrawable.setColorFilter(a == pressedBotButton ? Theme.colorPressedFilter : Theme.colorFilter);
                Theme.chat_systemDrawable.setBounds(button.x + addX, y, button.x + addX + button.width, y + button.height);
                Theme.chat_systemDrawable.draw(canvas);
                canvas.save();
                canvas.translate(button.x + addX + AndroidUtilities.dp(5), y + (AndroidUtilities.dp(44) - button.title.getLineBottom(button.title.getLineCount() - 1)) / 2);
                button.title.draw(canvas);
                canvas.restore();
                if (button.button instanceof TLRPC.TL_keyboardButtonUrl) {
                    int x = button.x + button.width - AndroidUtilities.dp(3) - Theme.chat_botLinkDrawalbe.getIntrinsicWidth() + addX;
                    setDrawableBounds(Theme.chat_botLinkDrawalbe, x, y + AndroidUtilities.dp(3));
                    Theme.chat_botLinkDrawalbe.draw(canvas);
                } else if (button.button instanceof TLRPC.TL_keyboardButtonSwitchInline) {
                    int x = button.x + button.width - AndroidUtilities.dp(3) - Theme.chat_botInlineDrawable.getIntrinsicWidth() + addX;
                    setDrawableBounds(Theme.chat_botInlineDrawable, x, y + AndroidUtilities.dp(3));
                    Theme.chat_botInlineDrawable.draw(canvas);
                } else if (button.button instanceof TLRPC.TL_keyboardButtonCallback || button.button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation || button.button instanceof TLRPC.TL_keyboardButtonGame || button.button instanceof TLRPC.TL_keyboardButtonBuy || button.button instanceof TLRPC.TL_keyboardButtonUrlAuth) {
                    boolean drawProgress = (button.button instanceof TLRPC.TL_keyboardButtonCallback || button.button instanceof TLRPC.TL_keyboardButtonGame || button.button instanceof TLRPC.TL_keyboardButtonBuy || button.button instanceof TLRPC.TL_keyboardButtonUrlAuth) && SendMessagesHelper.getInstance(currentAccount).isSendingCallback(currentMessageObject, button.button) ||
                            button.button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation && SendMessagesHelper.getInstance(currentAccount).isSendingCurrentLocation(currentMessageObject, button.button);
                    if (drawProgress || !drawProgress && button.progressAlpha != 0) {
                        Theme.chat_botProgressPaint.setAlpha(Math.min(255, (int) (button.progressAlpha * 255)));
                        int x = button.x + button.width - AndroidUtilities.dp(9 + 3) + addX;
                        rect.set(x, y + AndroidUtilities.dp(4), x + AndroidUtilities.dp(8), y + AndroidUtilities.dp(8 + 4));
                        canvas.drawArc(rect, button.angle, 220, false, Theme.chat_botProgressPaint);
                        invalidate();
                        long newTime = System.currentTimeMillis();
                        if (Math.abs(button.lastUpdateTime - System.currentTimeMillis()) < 1000) {
                            long delta = (newTime - button.lastUpdateTime);
                            float dt = 360 * delta / 2000.0f;
                            button.angle += dt;
                            button.angle -= 360 * (button.angle / 360);
                            if (drawProgress) {
                                if (button.progressAlpha < 1.0f) {
                                    button.progressAlpha += delta / 200.0f;
                                    if (button.progressAlpha > 1.0f) {
                                        button.progressAlpha = 1.0f;
                                    }
                                }
                            } else {
                                if (button.progressAlpha > 0.0f) {
                                    button.progressAlpha -= delta / 200.0f;
                                    if (button.progressAlpha < 0.0f) {
                                        button.progressAlpha = 0.0f;
                                    }
                                }
                            }
                        }
                        button.lastUpdateTime = newTime;
                    }
                }
            }
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
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (currentMessageObject.isOutOwner()) {
                radialProgress.setColors(Theme.key_chat_outLoader, Theme.key_chat_outLoaderSelected, Theme.key_chat_outMediaIcon, Theme.key_chat_outMediaIconSelected);
            } else {
                radialProgress.setColors(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);
            }
            if (buttonState == 1) {
                return MediaActionDrawable.ICON_PAUSE;
            } else if (buttonState == 2) {
                return MediaActionDrawable.ICON_DOWNLOAD;
            } else if (buttonState == 4) {
                return MediaActionDrawable.ICON_CANCEL;
            }
            return MediaActionDrawable.ICON_PLAY;
        } else {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT && !drawPhotoImage) {
                if (currentMessageObject.isOutOwner()) {
                    radialProgress.setColors(Theme.key_chat_outLoader, Theme.key_chat_outLoaderSelected, Theme.key_chat_outMediaIcon, Theme.key_chat_outMediaIconSelected);
                } else {
                    radialProgress.setColors(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);
                }
                if (buttonState == -1) {
                    return MediaActionDrawable.ICON_FILE;
                } else if (buttonState == 0) {
                    return MediaActionDrawable.ICON_DOWNLOAD;
                } else if (buttonState == 1) {
                    return MediaActionDrawable.ICON_CANCEL;
                }
            } else {
                radialProgress.setColors(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
                videoRadialProgress.setColors(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
                if (buttonState >= 0 && buttonState < 4) {
                    if (buttonState == 0) {
                        return MediaActionDrawable.ICON_DOWNLOAD;
                    } else if (buttonState == 1) {
                        return MediaActionDrawable.ICON_CANCEL;
                    } else if (buttonState == 2) {
                        return MediaActionDrawable.ICON_PLAY;
                    } else if (buttonState == 3) {
                        return autoPlayingMedia ? MediaActionDrawable.ICON_NONE : MediaActionDrawable.ICON_PLAY;
                    }
                } else if (buttonState == -1) {
                    if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        return (drawPhotoImage && (currentPhotoObject != null || currentPhotoObjectThumb != null) && (photoImage.hasBitmapImage() || currentMessageObject.mediaExists || currentMessageObject.attachPathExists)) ? MediaActionDrawable.ICON_NONE : MediaActionDrawable.ICON_FILE;
                    } else if (currentMessageObject.needDrawBluredPreview()) {
                        if (currentMessageObject.messageOwner.destroyTime != 0) {
                            if (currentMessageObject.isOutOwner()) {
                                return MediaActionDrawable.ICON_SECRETCHECK;
                            } else {
                                return MediaActionDrawable.ICON_EMPTY_NOPROGRESS;
                            }
                        } else {
                            return MediaActionDrawable.ICON_FIRE;
                        }
                    } else if (hasEmbed) {
                        return MediaActionDrawable.ICON_PLAY;
                    }
                }
            }
        }
        return MediaActionDrawable.ICON_NONE;
    }

    private int getMaxNameWidth() {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER || currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
            int maxWidth;
            if (AndroidUtilities.isTablet()) {
                if (isChat && !currentMessageObject.isOutOwner() && currentMessageObject.needDrawAvatar()) {
                    maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(42);
                } else {
                    maxWidth = AndroidUtilities.getMinTabletSide();
                }
            } else {
                if (isChat && !currentMessageObject.isOutOwner() && currentMessageObject.needDrawAvatar()) {
                    maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(42);
                } else {
                    maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
                }
            }
            return maxWidth - backgroundWidth - AndroidUtilities.dp(57);
        }
        if (currentMessagesGroup != null) {
            int dWidth;
            if (AndroidUtilities.isTablet()) {
                dWidth = AndroidUtilities.getMinTabletSide();
            } else {
                dWidth = AndroidUtilities.displaySize.x;
            }
            int firstLineWidth = 0;
            for (int a = 0; a < currentMessagesGroup.posArray.size(); a++) {
                MessageObject.GroupedMessagePosition position = currentMessagesGroup.posArray.get(a);
                if (position.minY == 0) {
                    firstLineWidth += Math.ceil((position.pw + position.leftSpanOffset) / 1000.0f * dWidth);
                } else {
                    break;
                }
            }
            return firstLineWidth - AndroidUtilities.dp(31 + (isAvatarVisible ? 48 : 0));
        } else {
            return backgroundWidth - AndroidUtilities.dp(mediaBackground ? 22 : 31);
        }
    }

    public void updateButtonState(boolean ifSame, boolean animated, boolean fromSet) {
        if (animated && (PhotoViewer.isShowingImage(currentMessageObject) || !attachedToWindow)) {
            animated = false;
        }
        drawRadialCheckBackground = false;
        String fileName = null;
        boolean fileExists = false;
        if (currentMessageObject.type == 1) {
            if (currentPhotoObject == null) {
                radialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, animated);
                return;
            }
            fileName = FileLoader.getAttachFileName(currentPhotoObject);
            fileExists = currentMessageObject.mediaExists;
        } else if (currentMessageObject.type == 8 || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER || currentMessageObject.type == 9 || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (currentMessageObject.useCustomPhoto) {
                buttonState = 1;
                radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
                return;
            }
            if (currentMessageObject.attachPathExists && !TextUtils.isEmpty(currentMessageObject.messageOwner.attachPath)) {
                fileName = currentMessageObject.messageOwner.attachPath;
                fileExists = true;
            } else if (!currentMessageObject.isSendError() || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                fileName = currentMessageObject.getFileName();
                fileExists = currentMessageObject.mediaExists;
            }
        } else if (documentAttachType != DOCUMENT_ATTACH_TYPE_NONE) {
            fileName = FileLoader.getAttachFileName(documentAttach);
            fileExists = currentMessageObject.mediaExists;
        } else if (currentPhotoObject != null) {
            fileName = FileLoader.getAttachFileName(currentPhotoObject);
            fileExists = currentMessageObject.mediaExists;
        }
        boolean autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject);
        canStreamVideo = currentMessageObject.isSent() && (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && autoDownload) && currentMessageObject.canStreamVideo() && !currentMessageObject.needDrawBluredPreview();
        if (SharedConfig.streamMedia && (int) currentMessageObject.getDialogId() != 0 && !currentMessageObject.isSecretMedia() &&
                (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC ||
                        canStreamVideo && currentPosition != null && ((currentPosition.flags & MessageObject.POSITION_FLAG_LEFT) == 0 || (currentPosition.flags & MessageObject.POSITION_FLAG_RIGHT) == 0))) {
            hasMiniProgress = fileExists ? 1 : 2;
            fileExists = true;
        }
        if (currentMessageObject.isSendError() || TextUtils.isEmpty(fileName) && !currentMessageObject.isSending() && !currentMessageObject.isEditing()) {
            radialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false);
            radialProgress.setMiniIcon(MediaActionDrawable.ICON_NONE, ifSame, false);
            videoRadialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false);
            videoRadialProgress.setMiniIcon(MediaActionDrawable.ICON_NONE, ifSame, false);
            return;
        }
        boolean fromBot = currentMessageObject.messageOwner.params != null && currentMessageObject.messageOwner.params.containsKey("query_id");

        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (currentMessageObject.isOut() && (currentMessageObject.isSending() || currentMessageObject.isEditing()) || currentMessageObject.isSendError() && fromBot) {
                if (!TextUtils.isEmpty(currentMessageObject.messageOwner.attachPath)) {
                    DownloadController.getInstance(currentAccount).addLoadingFileObserver(currentMessageObject.messageOwner.attachPath, currentMessageObject, this);
                    wasSending = true;
                    buttonState = 4;
                    radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
                    if (!fromBot) {
                        Float progress = ImageLoader.getInstance().getFileProgress(currentMessageObject.messageOwner.attachPath);
                        if (progress == null && SendMessagesHelper.getInstance(currentAccount).isSendingMessage(currentMessageObject.getId())) {
                            progress = 1.0f;
                        }
                        radialProgress.setProgress(progress != null ? progress : 0, false);
                    } else {
                        radialProgress.setProgress(0, false);
                    }
                } else {
                    buttonState = -1;
                    getIconForCurrentState();
                    radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL_NOPROFRESS, ifSame, false);
                    radialProgress.setProgress(0, false);
                }
            } else {
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
                    } else {
                        DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this);
                        if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
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
                    radialProgress.setMiniIcon(getMiniIconForCurrentState(), ifSame, animated);
                } else if (fileExists) {
                    DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                    boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
                    if (!playing || playing && MediaController.getInstance().isMessagePaused()) {
                        buttonState = 0;
                    } else {
                        buttonState = 1;
                    }
                    radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
                } else {
                    DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this);
                    if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                        buttonState = 2;
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
                }
            }
            updatePlayingMessageProgress();
        } else if (currentMessageObject.type == 0 &&
                documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT &&
                documentAttachType != DOCUMENT_ATTACH_TYPE_GIF &&
                documentAttachType != DOCUMENT_ATTACH_TYPE_ROUND &&
                documentAttachType != DOCUMENT_ATTACH_TYPE_VIDEO &&
                documentAttachType != DOCUMENT_ATTACH_TYPE_WALLPAPER &&
                documentAttachType != DOCUMENT_ATTACH_TYPE_THEME) {
            if (currentPhotoObject == null || !drawImageButton) {
                return;
            }
            if (!fileExists) {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this);
                float setProgress = 0;
                if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                    if (!cancelLoading && (documentAttachType == 0 && autoDownload || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && MessageObject.isGifDocument(documentAttach) && autoDownload)) {
                        buttonState = 1;
                    } else {
                        buttonState = 0;
                    }
                } else {
                    buttonState = 1;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    setProgress = progress != null ? progress : 0;
                }
                radialProgress.setProgress(setProgress, false);
                radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
                invalidate();
            } else {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && !photoImage.isAllowStartAnimation()) {
                    buttonState = 2;
                } else {
                    buttonState = -1;
                }
                radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
                invalidate();
            }
        } else {
            if (currentMessageObject.isOut() && (currentMessageObject.isSending() || currentMessageObject.isEditing())) {
                if (!TextUtils.isEmpty(currentMessageObject.messageOwner.attachPath)) {
                    DownloadController.getInstance(currentAccount).addLoadingFileObserver(currentMessageObject.messageOwner.attachPath, currentMessageObject, this);
                    wasSending = true;
                    boolean needProgress = currentMessageObject.messageOwner.attachPath == null || !currentMessageObject.messageOwner.attachPath.startsWith("http");
                    HashMap<String, String> params = currentMessageObject.messageOwner.params;
                    if (currentMessageObject.messageOwner.message != null && params != null && (params.containsKey("url") || params.containsKey("bot"))) {
                        needProgress = false;
                        buttonState = -1;
                    } else {
                        buttonState = 1;
                    }
                    boolean sending = SendMessagesHelper.getInstance(currentAccount).isSendingMessage(currentMessageObject.getId());
                    if (currentPosition != null && sending && buttonState == 1) {
                        drawRadialCheckBackground = true;
                        getIconForCurrentState();
                        radialProgress.setIcon(MediaActionDrawable.ICON_CHECK, ifSame, animated);
                    } else {
                        radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
                    }
                    if (needProgress) {
                        Float progress = ImageLoader.getInstance().getFileProgress(currentMessageObject.messageOwner.attachPath);
                        if (progress == null && sending) {
                            progress = 1.0f;
                        }
                        radialProgress.setProgress(progress != null ? progress : 0, false);
                    } else {
                        radialProgress.setProgress(0, false);
                    }
                    invalidate();
                } else {
                    buttonState = -1;
                    getIconForCurrentState();
                    radialProgress.setIcon(currentMessageObject.isSticker() || currentMessageObject.isAnimatedSticker() || currentMessageObject.isLocation() ? MediaActionDrawable.ICON_NONE : MediaActionDrawable.ICON_CANCEL_NOPROFRESS, ifSame, false);
                    radialProgress.setProgress(0, false);
                }
                videoRadialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false);
            } else {
                if (wasSending && !TextUtils.isEmpty(currentMessageObject.messageOwner.attachPath)) {
                    DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                }
                boolean isLoadingVideo = false;
                if ((documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) && autoPlayingMedia) {
                    isLoadingVideo = FileLoader.getInstance(currentAccount).isLoadingVideo(documentAttach, MediaController.getInstance().isPlayingMessage(currentMessageObject));
                    AnimatedFileDrawable animation = photoImage.getAnimation();
                    if (animation != null) {
                        if (currentMessageObject.hadAnimationNotReadyLoading) {
                            if (animation.hasBitmap()) {
                                currentMessageObject.hadAnimationNotReadyLoading = false;
                            }
                        } else {
                            currentMessageObject.hadAnimationNotReadyLoading = isLoadingVideo && !animation.hasBitmap();
                        }
                    } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && !fileExists) {
                        currentMessageObject.hadAnimationNotReadyLoading = true;
                    }
                }
                if (hasMiniProgress != 0) {
                    radialProgress.setMiniProgressBackgroundColor(Theme.getColor(Theme.key_chat_inLoaderPhoto));
                    buttonState = 3;
                    radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
                    if (hasMiniProgress == 1) {
                        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                        miniButtonState = -1;
                    } else {
                        DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this);
                        if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
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
                    radialProgress.setMiniIcon(getMiniIconForCurrentState(), ifSame, animated);
                } else if (fileExists || (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) && autoPlayingMedia && !currentMessageObject.hadAnimationNotReadyLoading && !isLoadingVideo) {
                    DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                    if (drawVideoImageButton && animated) {
                        if (animatingDrawVideoImageButton != 1 && animatingDrawVideoImageButtonProgress > 0) {
                            if (animatingDrawVideoImageButton == 0) {
                                animatingDrawVideoImageButtonProgress = 1.0f;
                            }
                            animatingDrawVideoImageButton = 1;
                        }
                    } else if (animatingDrawVideoImageButton == 0) {
                        animatingDrawVideoImageButtonProgress = 0.0f;
                    }
                    drawVideoImageButton = false;
                    drawVideoSize = false;
                    if (currentMessageObject.needDrawBluredPreview()) {
                        buttonState = -1;
                    } else {
                        if (currentMessageObject.type == 8 && currentMessageObject.gifState == 1) {
                            buttonState = 2;
                        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                            buttonState = 3;
                        } else {
                            buttonState = -1;
                        }
                    }
                    videoRadialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, animatingDrawVideoImageButton != 0);
                    radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
                    if (!fromSet && photoNotSet) {
                        setMessageObject(currentMessageObject, currentMessagesGroup, pinnedBottom, pinnedTop);
                    }
                    invalidate();
                } else {
                    drawVideoSize = documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF;
                    if ((documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) && canStreamVideo && !drawVideoImageButton && animated) {
                        if (animatingDrawVideoImageButton != 2 && animatingDrawVideoImageButtonProgress < 1.0f) {
                            if (animatingDrawVideoImageButton == 0) {
                                animatingDrawVideoImageButtonProgress = 0.0f;
                            }
                            animatingDrawVideoImageButton = 2;
                        }
                    } else if (animatingDrawVideoImageButton == 0) {
                        animatingDrawVideoImageButtonProgress = 1.0f;
                    }
                    DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this);
                    boolean progressVisible = false;
                    if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                        if (!cancelLoading && autoDownload) {
                            buttonState = 1;
                        } else {
                            buttonState = 0;
                        }
                        if ((documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && autoDownload) && canStreamVideo) {
                            drawVideoImageButton = true;
                            getIconForCurrentState();
                            radialProgress.setIcon(autoPlayingMedia ? MediaActionDrawable.ICON_NONE : MediaActionDrawable.ICON_PLAY, ifSame, animated);
                            videoRadialProgress.setIcon(MediaActionDrawable.ICON_DOWNLOAD, ifSame, animated);
                        } else {
                            drawVideoImageButton = false;
                            radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
                            videoRadialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false);
                            if (!drawVideoSize && animatingDrawVideoImageButton == 0) {
                                animatingDrawVideoImageButtonProgress = 0.0f;
                            }
                        }
                    } else {
                        buttonState = 1;
                        Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                        if ((documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && autoDownload) && canStreamVideo) {
                            drawVideoImageButton = true;
                            getIconForCurrentState();
                            radialProgress.setIcon(autoPlayingMedia || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF ? MediaActionDrawable.ICON_NONE : MediaActionDrawable.ICON_PLAY, ifSame, animated);
                            videoRadialProgress.setProgress(progress != null ? progress : 0, animated);
                            videoRadialProgress.setIcon(MediaActionDrawable.ICON_CANCEL_FILL, ifSame, animated);
                        } else {
                            drawVideoImageButton = false;
                            radialProgress.setProgress(progress != null ? progress : 0, animated);
                            radialProgress.setIcon(getIconForCurrentState(), ifSame, animated);
                            videoRadialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false);
                            if (!drawVideoSize && animatingDrawVideoImageButton == 0) {
                                animatingDrawVideoImageButtonProgress = 0.0f;
                            }
                        }
                    }
                    invalidate();
                }
            }
        }
        if (hasMiniProgress == 0) {
            radialProgress.setMiniIcon(MediaActionDrawable.ICON_NONE, false, animated);
        }
    }

    private void didPressMiniButton(boolean animated) {
        if (miniButtonState == 0) {
            miniButtonState = 1;
            radialProgress.setProgress(0, false);
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, 1, 0);
            } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, 1, currentMessageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
            }
            radialProgress.setMiniIcon(getMiniIconForCurrentState(), false, true);
            invalidate();
        } else if (miniButtonState == 1) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
                    MediaController.getInstance().cleanupPlayer(true, true);
                }
            }
            miniButtonState = 0;
            FileLoader.getInstance(currentAccount).cancelLoadFile(documentAttach);
            radialProgress.setMiniIcon(getMiniIconForCurrentState(), false, true);
            invalidate();
        }
    }

    private void didPressButton(boolean animated, boolean video) {
        if (buttonState == 0 && (!drawVideoImageButton || video)) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                if (miniButtonState == 0) {
                    FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, 1, 0);
                }
                if (delegate.needPlayMessage(currentMessageObject)) {
                    if (hasMiniProgress == 2 && miniButtonState != 1) {
                        miniButtonState = 1;
                        radialProgress.setProgress(0, false);
                        radialProgress.setMiniIcon(getMiniIconForCurrentState(), false, true);
                    }
                    updatePlayingMessageProgress();
                    buttonState = 1;
                    radialProgress.setIcon(getIconForCurrentState(), false, true);
                    invalidate();
                }
            } else {
                cancelLoading = false;
                if (video) {
                    videoRadialProgress.setProgress(0, false);
                } else {
                    radialProgress.setProgress(0, false);
                }
                TLRPC.PhotoSize thumb;
                String thumbFilter;
                if (currentPhotoObject != null && (photoImage.hasNotThumb() || currentPhotoObjectThumb == null)) {
                    thumb = currentPhotoObject;
                    thumbFilter = thumb instanceof TLRPC.TL_photoStrippedSize || "s".equals(thumb.type) ? currentPhotoFilterThumb : currentPhotoFilter;
                } else {
                    thumb = currentPhotoObjectThumb;
                    thumbFilter = currentPhotoFilterThumb;
                }
                if (currentMessageObject.type == 1) {
                    photoImage.setForceLoading(true);
                    photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObject.size, null, currentMessageObject, currentMessageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
                } else if (currentMessageObject.type == 8) {
                    FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, 1, 0);
                } else if (currentMessageObject.isRoundVideo()) {
                    if (currentMessageObject.isSecretMedia()) {
                        FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), currentMessageObject, 1, 1);
                    } else {
                        currentMessageObject.gifState = 2;
                        TLRPC.Document document = currentMessageObject.getDocument();
                        photoImage.setForceLoading(true);

                        photoImage.setImage(ImageLocation.getForDocument(document), null, ImageLocation.getForObject(thumb, document), thumbFilter, document.size, null, currentMessageObject, 0);
                    }
                } else if (currentMessageObject.type == 9) {
                    FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, 1, 0);
                } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                    FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, 1, currentMessageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
                } else if (currentMessageObject.type == 0 && documentAttachType != DOCUMENT_ATTACH_TYPE_NONE) {
                    if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                        photoImage.setForceLoading(true);
                        photoImage.setImage(ImageLocation.getForDocument(documentAttach), null, ImageLocation.getForDocument(currentPhotoObject, documentAttach), currentPhotoFilterThumb, documentAttach.size, null, currentMessageObject, 0);
                        currentMessageObject.gifState = 2;
                    } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, 0, 0);
                    } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER) {
                        photoImage.setImage(ImageLocation.getForDocument(documentAttach), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObject, documentAttach), "b1", 0, "jpg", currentMessageObject, 1);
                    }
                } else {
                    photoImage.setForceLoading(true);
                    photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, 0, null, currentMessageObject, 0);
                }
                buttonState = 1;
                if (video) {
                    videoRadialProgress.setIcon(MediaActionDrawable.ICON_CANCEL_FILL, false, animated);
                } else {
                    radialProgress.setIcon(getIconForCurrentState(), false, animated);
                }
                invalidate();
            }
        } else if (buttonState == 1 && (!drawVideoImageButton || video)) {
            photoImage.setForceLoading(false);
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                boolean result = MediaController.getInstance().pauseMessage(currentMessageObject);
                if (result) {
                    buttonState = 0;
                    radialProgress.setIcon(getIconForCurrentState(), false, animated);
                    invalidate();
                }
            } else {
                if (currentMessageObject.isOut() && (currentMessageObject.isSending() || currentMessageObject.isEditing())) {
                    if (radialProgress.getIcon() != MediaActionDrawable.ICON_CHECK) {
                        delegate.didPressCancelSendButton(this);
                    }
                } else {
                    cancelLoading = true;
                    if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER) {
                        FileLoader.getInstance(currentAccount).cancelLoadFile(documentAttach);
                    } else if (currentMessageObject.type == 0 || currentMessageObject.type == 1 || currentMessageObject.type == 8 || currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                        ImageLoader.getInstance().cancelForceLoadingForImageReceiver(photoImage);
                        photoImage.cancelLoadImage();
                    } else if (currentMessageObject.type == 9) {
                        FileLoader.getInstance(currentAccount).cancelLoadFile(currentMessageObject.getDocument());
                    }
                    buttonState = 0;
                    if (video) {
                        videoRadialProgress.setIcon(MediaActionDrawable.ICON_DOWNLOAD, false, animated);
                    } else {
                        radialProgress.setIcon(getIconForCurrentState(), false, animated);
                    }
                    invalidate();
                }
            }
        } else if (buttonState == 2) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                radialProgress.setProgress(0, false);
                FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, 1, 0);
                buttonState = 4;
                radialProgress.setIcon(getIconForCurrentState(), true, animated);
                invalidate();
            } else {
                if (currentMessageObject.isRoundVideo()) {
                    MessageObject playingMessage = MediaController.getInstance().getPlayingMessageObject();
                    if (playingMessage == null || !playingMessage.isRoundVideo()) {
                        photoImage.setAllowStartAnimation(true);
                        photoImage.startAnimation();
                    }
                } else {
                    photoImage.setAllowStartAnimation(true);
                    photoImage.startAnimation();
                }
                currentMessageObject.gifState = 0;
                buttonState = -1;
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
            }
        } else if (buttonState == 3 || buttonState == 0 && drawVideoImageButton) {
            if (hasMiniProgress == 2 && miniButtonState != 1) {
                miniButtonState = 1;
                radialProgress.setProgress(0, false);
                radialProgress.setMiniIcon(getMiniIconForCurrentState(), false, animated);
            }
            delegate.didPressImage(this, 0, 0);
        } else if (buttonState == 4) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                if (currentMessageObject.isOut() && (currentMessageObject.isSending() || currentMessageObject.isEditing()) || currentMessageObject.isSendError()) {
                    if (delegate != null) {
                        delegate.didPressCancelSendButton(this);
                    }
                } else {
                    FileLoader.getInstance(currentAccount).cancelLoadFile(documentAttach);
                    buttonState = 2;
                    radialProgress.setIcon(getIconForCurrentState(), false, animated);
                    invalidate();
                }
            }
        }
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {
        updateButtonState(true, documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC, false);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            updateButtonState(false, true, false);
            updateWaveform();
        } else {
            if (drawVideoImageButton) {
                videoRadialProgress.setProgress(1, true);
            } else {
                radialProgress.setProgress(1, true);
            }
            boolean startedAutoplay = false;
            if (!currentMessageObject.needDrawBluredPreview() && !autoPlayingMedia && documentAttach != null) {
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
                    photoImage.setImage(ImageLocation.getForDocument(documentAttach), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoObject instanceof TLRPC.TL_photoStrippedSize || currentPhotoObject != null && "s".equals(currentPhotoObject.type) ? currentPhotoFilterThumb : currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, null, documentAttach.size, null, currentMessageObject, 0);
                    photoImage.setAllowStartAnimation(true);
                    photoImage.startAnimation();
                    autoPlayingMedia = true;
                } else if (SharedConfig.autoplayVideo && documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO && (currentPosition == null || (currentPosition.flags & MessageObject.POSITION_FLAG_LEFT) != 0 && (currentPosition.flags & MessageObject.POSITION_FLAG_RIGHT) != 0)) {
                    animatingNoSound = 2;
                    photoImage.setImage(ImageLocation.getForDocument(documentAttach), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoObject instanceof TLRPC.TL_photoStrippedSize || currentPhotoObject != null && "s".equals(currentPhotoObject.type) ? currentPhotoFilterThumb : currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, null, documentAttach.size, null, currentMessageObject, 0);
                    if (!PhotoViewer.isPlayingMessage(currentMessageObject)) {
                        photoImage.setAllowStartAnimation(true);
                        photoImage.startAnimation();
                    } else {
                        photoImage.setAllowStartAnimation(false);
                    }
                    autoPlayingMedia = true;
                } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                    photoImage.setImage(ImageLocation.getForDocument(documentAttach), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoObject instanceof TLRPC.TL_photoStrippedSize || currentPhotoObject != null && "s".equals(currentPhotoObject.type) ? currentPhotoFilterThumb : currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, null, documentAttach.size, null, currentMessageObject, 0);
                    if (SharedConfig.autoplayGifs) {
                        photoImage.setAllowStartAnimation(true);
                        photoImage.startAnimation();
                    } else {
                        photoImage.setAllowStartAnimation(false);
                        photoImage.stopAnimation();
                    }
                    autoPlayingMedia = true;
                }
            }
            if (currentMessageObject.type == 0) {
                if (!autoPlayingMedia && documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && currentMessageObject.gifState != 1) {
                    buttonState = 2;
                    didPressButton(true, false);
                } else if (!photoNotSet) {
                    updateButtonState(false, true, false);
                } else {
                    setMessageObject(currentMessageObject, currentMessagesGroup, pinnedBottom, pinnedTop);
                }
            } else {
                if (!photoNotSet) {
                    updateButtonState(false, true, false);
                }
                if (photoNotSet) {
                    setMessageObject(currentMessageObject, currentMessagesGroup, pinnedBottom, pinnedTop);
                }
            }
        }
    }

    @Override
    public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb) {
        if (currentMessageObject != null && set && !thumb && !currentMessageObject.mediaExists && !currentMessageObject.attachPathExists &&
                (currentMessageObject.type == 0 && (documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER || documentAttachType == DOCUMENT_ATTACH_TYPE_NONE || documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER) || currentMessageObject.type == 1)) {
            currentMessageObject.mediaExists = true;
            updateButtonState(false, true, false);
        }
    }

    @Override
    public void onAnimationReady(ImageReceiver imageReceiver) {
        if (currentMessageObject != null && imageReceiver == photoImage && currentMessageObject.isAnimatedSticker()) {
            delegate.setShouldNotRepeatSticker(currentMessageObject);
        }
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        if (drawVideoImageButton) {
            videoRadialProgress.setProgress(progress, true);
        } else {
            radialProgress.setProgress(progress, true);
        }
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (hasMiniProgress != 0) {
                if (miniButtonState != 1) {
                    updateButtonState(false, false, false);
                }
            } else {
                if (buttonState != 4) {
                    updateButtonState(false, false, false);
                }
            }
        } else {
            if (hasMiniProgress != 0) {
                if (miniButtonState != 1) {
                    updateButtonState(false, false, false);
                }
            } else {
                if (buttonState != 1) {
                    updateButtonState(false, false, false);
                }
            }
        }
    }

    @Override
    public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {
        radialProgress.setProgress(progress, true);
        if (progress == 1.0f && currentPosition != null) {
            boolean sending = SendMessagesHelper.getInstance(currentAccount).isSendingMessage(currentMessageObject.getId());
            if (sending && buttonState == 1) {
                drawRadialCheckBackground = true;
                getIconForCurrentState();
                radialProgress.setIcon(MediaActionDrawable.ICON_CHECK, false, true);
            }
        }
    }

    @Override
    public void onProvideStructure(ViewStructure structure) {
        super.onProvideStructure(structure);
        if (allowAssistant && Build.VERSION.SDK_INT >= 23) {
            if (currentMessageObject.messageText != null && currentMessageObject.messageText.length() > 0) {
                structure.setText(currentMessageObject.messageText);
            } else if (currentMessageObject.caption != null && currentMessageObject.caption.length() > 0) {
                structure.setText(currentMessageObject.caption);
            }
        }
    }

    public void setDelegate(ChatMessageCellDelegate chatMessageCellDelegate) {
        delegate = chatMessageCellDelegate;
    }

    public void setAllowAssistant(boolean value) {
        allowAssistant = value;
    }

    private void measureTime(MessageObject messageObject) {
        CharSequence signString;
        if (messageObject.scheduled) {
            signString = null;
        } else if (messageObject.messageOwner.post_author != null) {
            signString = messageObject.messageOwner.post_author.replace("\n", "");
        } else if (messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.post_author != null) {
            signString = messageObject.messageOwner.fwd_from.post_author.replace("\n", "");
        } else if (!messageObject.isOutOwner() && messageObject.messageOwner.from_id > 0 && messageObject.messageOwner.post) {
            TLRPC.User signUser = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.from_id);
            if (signUser != null) {
                signString = ContactsController.formatName(signUser.first_name, signUser.last_name).replace('\n', ' ');
            } else {
                signString = null;
            }
        } else {
            signString = null;
        }
        String timeString;
        TLRPC.User author = null;
        if (currentMessageObject.isFromUser()) {
            author = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.from_id);
        }
        boolean edited;
        if (messageObject.scheduled || messageObject.isLiveLocation() || messageObject.messageOwner.edit_hide || messageObject.getDialogId() == 777000 || messageObject.messageOwner.via_bot_id != 0 || messageObject.messageOwner.via_bot_name != null || author != null && author.bot) {
            edited = false;
        } else if (currentPosition == null || currentMessagesGroup == null) {
            edited = (messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_EDITED) != 0 || messageObject.isEditing();
        } else {
            edited = false;
            for (int a = 0, size = currentMessagesGroup.messages.size(); a < size; a++) {
                MessageObject object = currentMessagesGroup.messages.get(a);
                if ((object.messageOwner.flags & TLRPC.MESSAGE_FLAG_EDITED) != 0 || object.isEditing()) {
                    edited = true;
                    break;
                }
            }
        }
        if (edited) {
            timeString = LocaleController.getString("EditedMessage", R.string.EditedMessage) + " " + LocaleController.getInstance().formatterDay.format((long) (messageObject.messageOwner.date) * 1000);
        } else {
            timeString = LocaleController.getInstance().formatterDay.format((long) (messageObject.messageOwner.date) * 1000);
        }
        if (signString != null) {
            currentTimeString = ", " + timeString;
        } else {
            currentTimeString = timeString;
        }
        timeTextWidth = timeWidth = (int) Math.ceil(Theme.chat_timePaint.measureText(currentTimeString));
        if ((messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
            currentViewsString = String.format("%s", LocaleController.formatShortNumber(Math.max(1, messageObject.messageOwner.views), null));
            viewsTextWidth = (int) Math.ceil(Theme.chat_timePaint.measureText(currentViewsString));
            timeWidth += viewsTextWidth + Theme.chat_msgInViewsDrawable.getIntrinsicWidth() + AndroidUtilities.dp(10);
        }
        if (messageObject.scheduled && messageObject.isSendError()) {
            timeWidth += AndroidUtilities.dp(18);
        }
        if (signString != null) {
            if (availableTimeWidth == 0) {
                availableTimeWidth = AndroidUtilities.dp(1000);
            }
            int widthForSign = availableTimeWidth - timeWidth;
            if (messageObject.isOutOwner()) {
                if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                    widthForSign -= AndroidUtilities.dp(20);
                } else {
                    widthForSign -= AndroidUtilities.dp(96);
                }
            }
            int width = (int) Math.ceil(Theme.chat_timePaint.measureText(signString, 0, signString.length()));
            if (width > widthForSign) {
                if (widthForSign <= 0) {
                    signString = "";
                    width = 0;
                } else {
                    signString = TextUtils.ellipsize(signString, Theme.chat_timePaint, widthForSign, TextUtils.TruncateAt.END);
                    width = widthForSign;
                }
            }
            currentTimeString = signString + currentTimeString;
            timeTextWidth += width;
            timeWidth += width;
        }
    }

    private boolean isDrawSelectionBackground() {
        return isPressed() && isCheckPressed || !isCheckPressed && isPressed || isHighlighted;
    }

    private boolean isOpenChatByShare(MessageObject messageObject) {
        return messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.saved_from_peer != null;
    }

    private boolean checkNeedDrawShareButton(MessageObject messageObject) {
        if (currentPosition != null && !currentPosition.last) {
            return false;
        } else if (messageObject.messageOwner.fwd_from != null && !messageObject.isOutOwner() && messageObject.messageOwner.fwd_from.saved_from_peer != null && messageObject.getDialogId() == UserConfig.getInstance(currentAccount).getClientUserId()) {
            drwaShareGoIcon = true;
        }
        return messageObject.needDrawShareButton();
    }

    public boolean isInsideBackground(float x, float y) {
        return currentBackgroundDrawable != null && x >= backgroundDrawableLeft && x <= backgroundDrawableLeft + backgroundDrawableRight;
    }

    private void updateCurrentUserAndChat() {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        TLRPC.MessageFwdHeader fwd_from = currentMessageObject.messageOwner.fwd_from;
        int currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
        if (fwd_from != null && fwd_from.channel_id != 0 && currentMessageObject.getDialogId() == currentUserId) {
            currentChat = MessagesController.getInstance(currentAccount).getChat(fwd_from.channel_id);
        } else if (fwd_from != null && fwd_from.saved_from_peer != null) {
            if (fwd_from.saved_from_peer.user_id != 0) {
                if (fwd_from.from_id != 0) {
                    currentUser = messagesController.getUser(fwd_from.from_id);
                } else {
                    currentUser = messagesController.getUser(fwd_from.saved_from_peer.user_id);
                }
            } else if (fwd_from.saved_from_peer.channel_id != 0) {
                if (currentMessageObject.isSavedFromMegagroup() && fwd_from.from_id != 0) {
                    currentUser = messagesController.getUser(fwd_from.from_id);
                } else {
                    currentChat = messagesController.getChat(fwd_from.saved_from_peer.channel_id);
                }
            } else if (fwd_from.saved_from_peer.chat_id != 0) {
                if (fwd_from.from_id != 0) {
                    currentUser = messagesController.getUser(fwd_from.from_id);
                } else {
                    currentChat = messagesController.getChat(fwd_from.saved_from_peer.chat_id);
                }
            }
        } else if (fwd_from != null && fwd_from.from_id != 0 && fwd_from.channel_id == 0 && currentMessageObject.getDialogId() == currentUserId) {
            currentUser = messagesController.getUser(fwd_from.from_id);
        } else if (fwd_from != null && !TextUtils.isEmpty(fwd_from.from_name) && currentMessageObject.getDialogId() == currentUserId) {
            currentUser = new TLRPC.TL_user();
            currentUser.first_name = fwd_from.from_name;
        } else if (currentMessageObject.isFromUser()) {
            currentUser = messagesController.getUser(currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.from_id < 0) {
            currentChat = messagesController.getChat(-currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.post) {
            currentChat = messagesController.getChat(currentMessageObject.messageOwner.to_id.channel_id);
        }
    }

    private void setMessageObjectInternal(MessageObject messageObject) {
        if ((messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0 && !currentMessageObject.scheduled) {
            if (!currentMessageObject.viewsReloaded) {
                MessagesController.getInstance(currentAccount).addToViewsQueue(currentMessageObject);
                currentMessageObject.viewsReloaded = true;
            }
        }

        updateCurrentUserAndChat();

        if (isAvatarVisible) {
            if (currentUser != null) {
                if (currentUser.photo != null) {
                    currentPhoto = currentUser.photo.photo_small;
                } else {
                    currentPhoto = null;
                }
                avatarDrawable.setInfo(currentUser);
                avatarImage.setImage(ImageLocation.getForUser(currentUser, false), "50_50", avatarDrawable, null, currentUser, 0);
            } else if (currentChat != null) {
                if (currentChat.photo != null) {
                    currentPhoto = currentChat.photo.photo_small;
                } else {
                    currentPhoto = null;
                }
                avatarDrawable.setInfo(currentChat);
                avatarImage.setImage(ImageLocation.getForChat(currentChat, false), "50_50", avatarDrawable, null, currentChat, 0);
            } else {
                currentPhoto = null;
                avatarDrawable.setInfo(messageObject.messageOwner.from_id, null, null);
                avatarImage.setImage(null, null, avatarDrawable, null, null, 0);
            }
        } else {
            currentPhoto = null;
        }


        measureTime(messageObject);

        namesOffset = 0;

        String viaUsername = null;
        CharSequence viaString = null;
        if (messageObject.messageOwner.via_bot_id != 0) {
            TLRPC.User botUser = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.via_bot_id);
            if (botUser != null && botUser.username != null && botUser.username.length() > 0) {
                viaUsername = "@" + botUser.username;
                viaString = AndroidUtilities.replaceTags(String.format(" %s <b>%s</b>", LocaleController.getString("ViaBot", R.string.ViaBot), viaUsername));
                viaWidth = (int) Math.ceil(Theme.chat_replyNamePaint.measureText(viaString, 0, viaString.length()));
                currentViaBotUser = botUser;
            }
        } else if (messageObject.messageOwner.via_bot_name != null && messageObject.messageOwner.via_bot_name.length() > 0) {
            viaUsername = "@" + messageObject.messageOwner.via_bot_name;
            viaString = AndroidUtilities.replaceTags(String.format(" %s <b>%s</b>", LocaleController.getString("ViaBot", R.string.ViaBot), viaUsername));
            viaWidth = (int) Math.ceil(Theme.chat_replyNamePaint.measureText(viaString, 0, viaString.length()));
        }

        boolean authorName = drawName && isChat && !currentMessageObject.isOutOwner();
        boolean viaBot = (messageObject.messageOwner.fwd_from == null || messageObject.type == 14) && viaUsername != null;
        if (authorName || viaBot) {
            drawNameLayout = true;
            nameWidth = getMaxNameWidth();
            if (nameWidth < 0) {
                nameWidth = AndroidUtilities.dp(100);
            }
            int adminWidth;
            String adminString;
            String adminLabel;
            if (isMegagroup && currentChat != null && currentMessageObject.isForwardedChannelPost()) {
                adminString = LocaleController.getString("DiscussChannel", R.string.DiscussChannel);
                adminWidth = (int) Math.ceil(Theme.chat_adminPaint.measureText(adminString));
                nameWidth -= adminWidth;
            } else if (currentUser != null && !currentMessageObject.isOutOwner() && !currentMessageObject.isAnyKindOfSticker() && currentMessageObject.type != 5 && (adminLabel = delegate.getAdminRank(currentUser.id)) != null) {
                if (adminLabel.length() == 0) {
                    adminLabel = LocaleController.getString("ChatAdmin", R.string.ChatAdmin);
                }
                adminString = adminLabel;
                adminWidth = (int) Math.ceil(Theme.chat_adminPaint.measureText(adminString));
                nameWidth -= adminWidth;
            } else {
                adminString = null;
                adminWidth = 0;
            }

            if (authorName) {
                if (currentUser != null) {
                    currentNameString = UserObject.getUserName(currentUser);
                } else if (currentChat != null) {
                    currentNameString = currentChat.title;
                } else {
                    currentNameString = "DELETED";
                }
            } else {
                currentNameString = "";
            }
            CharSequence nameStringFinal = TextUtils.ellipsize(currentNameString.replace('\n', ' '), Theme.chat_namePaint, nameWidth - (viaBot ? viaWidth : 0), TextUtils.TruncateAt.END);
            if (viaBot) {
                viaNameWidth = (int) Math.ceil(Theme.chat_namePaint.measureText(nameStringFinal, 0, nameStringFinal.length()));
                if (viaNameWidth != 0) {
                    viaNameWidth += AndroidUtilities.dp(4);
                }
                int color;
                if (currentMessageObject.shouldDrawWithoutBackground()) {
                    color = Theme.getColor(Theme.key_chat_stickerViaBotNameText);
                } else {
                    color = Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outViaBotNameText : Theme.key_chat_inViaBotNameText);
                }
                String viaBotString = LocaleController.getString("ViaBot", R.string.ViaBot);
                if (currentNameString.length() > 0) {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format("%s %s %s", nameStringFinal, viaBotString, viaUsername));
                    stringBuilder.setSpan(viaSpan1 = new TypefaceSpan(Typeface.DEFAULT, 0, color), nameStringFinal.length() + 1, nameStringFinal.length() + 1 + viaBotString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    stringBuilder.setSpan(viaSpan2 = new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, color), nameStringFinal.length() + 2 + viaBotString.length(), stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    nameStringFinal = stringBuilder;
                } else {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format("%s %s", viaBotString, viaUsername));
                    stringBuilder.setSpan(viaSpan1 = new TypefaceSpan(Typeface.DEFAULT, 0, color), 0, viaBotString.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    stringBuilder.setSpan(viaSpan2 = new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, color), 1 + viaBotString.length(), stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    nameStringFinal = stringBuilder;
                }
                nameStringFinal = TextUtils.ellipsize(nameStringFinal, Theme.chat_namePaint, nameWidth, TextUtils.TruncateAt.END);
            }
            try {
                nameLayout = new StaticLayout(nameStringFinal, Theme.chat_namePaint, nameWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (nameLayout != null && nameLayout.getLineCount() > 0) {
                    nameWidth = (int) Math.ceil(nameLayout.getLineWidth(0));
                    if (!messageObject.isAnyKindOfSticker()) {
                        namesOffset += AndroidUtilities.dp(19);
                    }
                    nameOffsetX = nameLayout.getLineLeft(0);
                } else {
                    nameWidth = 0;
                }
                if (adminString != null) {
                    adminLayout = new StaticLayout(adminString, Theme.chat_adminPaint, adminWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    nameWidth += adminLayout.getLineWidth(0) + AndroidUtilities.dp(8);
                } else {
                    adminLayout = null;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (currentNameString.length() == 0) {
                currentNameString = null;
            }
        } else {
            currentNameString = null;
            nameLayout = null;
            nameWidth = 0;
        }

        currentForwardUser = null;
        currentForwardNameString = null;
        currentForwardChannel = null;
        currentForwardName = null;
        forwardedNameLayout[0] = null;
        forwardedNameLayout[1] = null;
        forwardedNameWidth = 0;
        if (drawForwardedName && messageObject.needDrawForwarded() && (currentPosition == null || currentPosition.minY == 0)) {
            if (messageObject.messageOwner.fwd_from.channel_id != 0) {
                currentForwardChannel = MessagesController.getInstance(currentAccount).getChat(messageObject.messageOwner.fwd_from.channel_id);
            }
            if (messageObject.messageOwner.fwd_from.from_id != 0) {
                currentForwardUser = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.fwd_from.from_id);
            }
            if (messageObject.messageOwner.fwd_from.from_name != null) {
                currentForwardName = messageObject.messageOwner.fwd_from.from_name;
            }

            if (currentForwardUser != null || currentForwardChannel != null || currentForwardName != null) {
                if (currentForwardChannel != null) {
                    if (currentForwardUser != null) {
                        currentForwardNameString = String.format("%s (%s)", currentForwardChannel.title, UserObject.getUserName(currentForwardUser));
                    } else {
                        currentForwardNameString = currentForwardChannel.title;
                    }
                } else if (currentForwardUser != null) {
                    currentForwardNameString = UserObject.getUserName(currentForwardUser);
                } else if (currentForwardName != null) {
                    currentForwardNameString = currentForwardName;
                }

                forwardedNameWidth = getMaxNameWidth();
                String from = LocaleController.getString("From", R.string.From);
                String fromFormattedString = LocaleController.getString("FromFormatted", R.string.FromFormatted);
                int idx = fromFormattedString.indexOf("%1$s");
                int fromWidth = (int) Math.ceil(Theme.chat_forwardNamePaint.measureText(from + " "));
                CharSequence name = TextUtils.ellipsize(currentForwardNameString.replace('\n', ' '), Theme.chat_replyNamePaint, forwardedNameWidth - fromWidth - viaWidth, TextUtils.TruncateAt.END);
                String fromString;
                try {
                    fromString = String.format(fromFormattedString, name);
                } catch (Exception e) {
                    fromString = name.toString();
                }
                CharSequence lastLine;
                SpannableStringBuilder stringBuilder;
                if (viaString != null) {
                    stringBuilder = new SpannableStringBuilder(String.format("%s %s %s", fromString, LocaleController.getString("ViaBot", R.string.ViaBot), viaUsername));
                    viaNameWidth = (int) Math.ceil(Theme.chat_forwardNamePaint.measureText(fromString));
                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), stringBuilder.length() - viaUsername.length() - 1, stringBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    stringBuilder = new SpannableStringBuilder(String.format(fromFormattedString, name));
                }
                forwardNameCenterX = fromWidth + (int) Math.ceil(Theme.chat_forwardNamePaint.measureText(name, 0, name.length())) / 2;
                if (idx >= 0 && (currentForwardName == null || messageObject.messageOwner.fwd_from.from_id != 0)) {
                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), idx, idx + name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                lastLine = stringBuilder;
                lastLine = TextUtils.ellipsize(lastLine, Theme.chat_forwardNamePaint, forwardedNameWidth, TextUtils.TruncateAt.END);
                try {
                    forwardedNameLayout[1] = new StaticLayout(lastLine, Theme.chat_forwardNamePaint, forwardedNameWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    lastLine = TextUtils.ellipsize(AndroidUtilities.replaceTags(LocaleController.getString("ForwardedMessage", R.string.ForwardedMessage)), Theme.chat_forwardNamePaint, forwardedNameWidth, TextUtils.TruncateAt.END);
                    forwardedNameLayout[0] = new StaticLayout(lastLine, Theme.chat_forwardNamePaint, forwardedNameWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    forwardedNameWidth = Math.max((int) Math.ceil(forwardedNameLayout[0].getLineWidth(0)), (int) Math.ceil(forwardedNameLayout[1].getLineWidth(0)));
                    forwardNameOffsetX[0] = forwardedNameLayout[0].getLineLeft(0);
                    forwardNameOffsetX[1] = forwardedNameLayout[1].getLineLeft(0);
                    if (messageObject.type != 5) {
                        namesOffset += AndroidUtilities.dp(36);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        if (messageObject.hasValidReplyMessageObject()) {
            if (currentPosition == null || currentPosition.minY == 0) {
                if (!messageObject.isAnyKindOfSticker() && messageObject.type != 5) {
                    namesOffset += AndroidUtilities.dp(42);
                    if (messageObject.type != 0) {
                        namesOffset += AndroidUtilities.dp(5);
                    }
                }

                int maxWidth = getMaxNameWidth();
                if (!messageObject.shouldDrawWithoutBackground()) {
                    maxWidth -= AndroidUtilities.dp(10);
                } else if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                    maxWidth += AndroidUtilities.dp(13);
                }

                CharSequence stringFinalText = null;

                int cacheType = 1;
                int size = 0;
                TLObject photoObject;
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject.photoThumbs2, 320);
                TLRPC.PhotoSize thumbPhotoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject.photoThumbs2, 40);
                photoObject = messageObject.replyMessageObject.photoThumbsObject2;
                if (photoSize == null) {
                    if (messageObject.replyMessageObject.mediaExists) {
                        photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject.photoThumbs, AndroidUtilities.getPhotoSize());
                        if (photoSize != null) {
                            size = photoSize.size;
                        }
                        cacheType = 0;
                    } else {
                        photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject.photoThumbs, 320);
                    }
                    thumbPhotoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject.photoThumbs, 40);
                    photoObject = messageObject.replyMessageObject.photoThumbsObject;
                }
                if (thumbPhotoSize == photoSize) {
                    thumbPhotoSize = null;
                }
                if (photoSize == null || messageObject.replyMessageObject.isAnyKindOfSticker() || messageObject.isAnyKindOfSticker() && !AndroidUtilities.isTablet() || messageObject.replyMessageObject.isSecretMedia()) {
                    replyImageReceiver.setImageBitmap((Drawable) null);
                    needReplyImage = false;
                } else {
                    if (messageObject.replyMessageObject.isRoundVideo()) {
                        replyImageReceiver.setRoundRadius(AndroidUtilities.dp(22));
                    } else {
                        replyImageReceiver.setRoundRadius(0);
                    }
                    currentReplyPhoto = photoSize;
                    replyImageReceiver.setImage(ImageLocation.getForObject(photoSize, photoObject), "50_50", ImageLocation.getForObject(thumbPhotoSize, photoObject), "50_50_b", size, null, messageObject.replyMessageObject, cacheType);
                    needReplyImage = true;
                    maxWidth -= AndroidUtilities.dp(44);
                }

                String name = null;
                if (messageObject.customReplyName != null) {
                    name = messageObject.customReplyName;
                } else {
                    if (messageObject.replyMessageObject.isFromUser()) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageObject.replyMessageObject.messageOwner.from_id);
                        if (user != null) {
                            name = UserObject.getUserName(user);
                        }
                    } else if (messageObject.replyMessageObject.messageOwner.from_id < 0) {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-messageObject.replyMessageObject.messageOwner.from_id);
                        if (chat != null) {
                            name = chat.title;
                        }
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(messageObject.replyMessageObject.messageOwner.to_id.channel_id);
                        if (chat != null) {
                            name = chat.title;
                        }
                    }
                }
                if (name == null) {
                    name = LocaleController.getString("Loading", R.string.Loading);
                }
                CharSequence stringFinalName = TextUtils.ellipsize(name.replace('\n', ' '), Theme.chat_replyNamePaint, maxWidth, TextUtils.TruncateAt.END);
                if (messageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                    stringFinalText = Emoji.replaceEmoji(messageObject.replyMessageObject.messageOwner.media.game.title, Theme.chat_replyTextPaint.getFontMetricsInt(), AndroidUtilities.dp(14), false);
                    stringFinalText = TextUtils.ellipsize(stringFinalText, Theme.chat_replyTextPaint, maxWidth, TextUtils.TruncateAt.END);
                } else if (messageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                    stringFinalText = Emoji.replaceEmoji(messageObject.replyMessageObject.messageOwner.media.title, Theme.chat_replyTextPaint.getFontMetricsInt(), AndroidUtilities.dp(14), false);
                    stringFinalText = TextUtils.ellipsize(stringFinalText, Theme.chat_replyTextPaint, maxWidth, TextUtils.TruncateAt.END);
                } else if (messageObject.replyMessageObject.messageText != null && messageObject.replyMessageObject.messageText.length() > 0) {
                    String mess = messageObject.replyMessageObject.messageText.toString();
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    mess = mess.replace('\n', ' ');
                    stringFinalText = Emoji.replaceEmoji(mess, Theme.chat_replyTextPaint.getFontMetricsInt(), AndroidUtilities.dp(14), false);
                    stringFinalText = TextUtils.ellipsize(stringFinalText, Theme.chat_replyTextPaint, maxWidth, TextUtils.TruncateAt.END);
                }

                try {
                    replyNameWidth = AndroidUtilities.dp(4 + (needReplyImage ? 44 : 0));
                    if (stringFinalName != null) {
                        replyNameLayout = new StaticLayout(stringFinalName, Theme.chat_replyNamePaint, maxWidth + AndroidUtilities.dp(6), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        if (replyNameLayout.getLineCount() > 0) {
                            replyNameWidth += (int) Math.ceil(replyNameLayout.getLineWidth(0)) + AndroidUtilities.dp(8);
                            replyNameOffset = replyNameLayout.getLineLeft(0);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    replyTextWidth = AndroidUtilities.dp(4 + (needReplyImage ? 44 : 0));
                    if (stringFinalText != null) {
                        replyTextLayout = new StaticLayout(stringFinalText, Theme.chat_replyTextPaint, maxWidth + AndroidUtilities.dp(10), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        if (replyTextLayout.getLineCount() > 0) {
                            replyTextWidth += (int) Math.ceil(replyTextLayout.getLineWidth(0)) + AndroidUtilities.dp(8);
                            replyTextOffset = replyTextLayout.getLineLeft(0);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        requestLayout();
    }

    public int getCaptionHeight() {
        return addedCaptionHeight;
    }

    public ImageReceiver getAvatarImage() {
        return isAvatarVisible ? avatarImage : null;
    }

    public float getCheckBoxTranslation() {
        return checkBoxTranslation;
    }

    public void drawCheckBox(Canvas canvas) {
        if (currentMessageObject != null && !currentMessageObject.isSending() && !currentMessageObject.isSendError() && checkBox != null && (checkBoxVisible || checkBoxAnimationInProgress) && (currentPosition == null || (currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0 && (currentPosition.flags & MessageObject.POSITION_FLAG_LEFT) != 0)) {
            canvas.save();
            canvas.translate(0, getTop());
            checkBox.draw(canvas);
            canvas.restore();
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

        if (currentMessageObject.isOutOwner()) {
            Theme.chat_msgTextPaint.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
            Theme.chat_msgTextPaint.linkColor = Theme.getColor(Theme.key_chat_messageLinkOut);
            Theme.chat_msgGameTextPaint.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
            Theme.chat_msgGameTextPaint.linkColor = Theme.getColor(Theme.key_chat_messageLinkOut);
            Theme.chat_replyTextPaint.linkColor = Theme.getColor(Theme.key_chat_messageLinkOut);
        } else {
            Theme.chat_msgTextPaint.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
            Theme.chat_msgTextPaint.linkColor = Theme.getColor(Theme.key_chat_messageLinkIn);
            Theme.chat_msgGameTextPaint.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
            Theme.chat_msgGameTextPaint.linkColor = Theme.getColor(Theme.key_chat_messageLinkIn);
            Theme.chat_replyTextPaint.linkColor = Theme.getColor(Theme.key_chat_messageLinkIn);
        }

        if (documentAttach != null) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
                if (currentMessageObject.isOutOwner()) {
                    seekBarWaveform.setColors(Theme.getColor(Theme.key_chat_outVoiceSeekbar), Theme.getColor(Theme.key_chat_outVoiceSeekbarFill), Theme.getColor(Theme.key_chat_outVoiceSeekbarSelected));
                    seekBar.setColors(Theme.getColor(Theme.key_chat_outAudioSeekbar), Theme.getColor(Theme.key_chat_outAudioCacheSeekbar), Theme.getColor(Theme.key_chat_outAudioSeekbarFill), Theme.getColor(Theme.key_chat_outAudioSeekbarFill), Theme.getColor(Theme.key_chat_outAudioSeekbarSelected));
                } else {
                    seekBarWaveform.setColors(Theme.getColor(Theme.key_chat_inVoiceSeekbar), Theme.getColor(Theme.key_chat_inVoiceSeekbarFill), Theme.getColor(Theme.key_chat_inVoiceSeekbarSelected));
                    seekBar.setColors(Theme.getColor(Theme.key_chat_inAudioSeekbar), Theme.getColor(Theme.key_chat_inAudioCacheSeekbar), Theme.getColor(Theme.key_chat_inAudioSeekbarFill), Theme.getColor(Theme.key_chat_inAudioSeekbarFill), Theme.getColor(Theme.key_chat_inAudioSeekbarSelected));
                }
            } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_MUSIC;
                if (currentMessageObject.isOutOwner()) {
                    seekBar.setColors(Theme.getColor(Theme.key_chat_outAudioSeekbar), Theme.getColor(Theme.key_chat_outAudioCacheSeekbar), Theme.getColor(Theme.key_chat_outAudioSeekbarFill), Theme.getColor(Theme.key_chat_outAudioSeekbarFill), Theme.getColor(Theme.key_chat_outAudioSeekbarSelected));
                } else {
                    seekBar.setColors(Theme.getColor(Theme.key_chat_inAudioSeekbar), Theme.getColor(Theme.key_chat_inAudioCacheSeekbar), Theme.getColor(Theme.key_chat_inAudioSeekbarFill), Theme.getColor(Theme.key_chat_inAudioSeekbarFill), Theme.getColor(Theme.key_chat_inAudioSeekbarSelected));
                }
            }
        }
        if (currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
            Theme.chat_timePaint.setColor(Theme.getColor(Theme.key_chat_mediaTimeText));
        } else {
            if (mediaBackground) {
                if (currentMessageObject.shouldDrawWithoutBackground()) {
                    Theme.chat_timePaint.setColor(Theme.getColor(Theme.key_chat_serviceText));
                } else {
                    Theme.chat_timePaint.setColor(Theme.getColor(Theme.key_chat_mediaTimeText));
                }
            } else {
                if (currentMessageObject.isOutOwner()) {
                    Theme.chat_timePaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outTimeSelectedText : Theme.key_chat_outTimeText));
                } else {
                    Theme.chat_timePaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inTimeSelectedText : Theme.key_chat_inTimeText));
                }
            }
        }

        Drawable currentBackgroundShadowDrawable;
        Drawable currentBackgroundSelectedDrawable;
        int additionalTop = 0;
        int additionalBottom = 0;
        if (currentMessageObject.isOutOwner()) {
            if (!mediaBackground && !drawPinnedBottom) {
                currentBackgroundDrawable = Theme.chat_msgOutDrawable;
                currentBackgroundSelectedDrawable = Theme.chat_msgOutSelectedDrawable;
                currentBackgroundShadowDrawable = Theme.chat_msgOutShadowDrawable;
            } else {
                currentBackgroundDrawable = Theme.chat_msgOutMediaDrawable;
                currentBackgroundSelectedDrawable = Theme.chat_msgOutMediaSelectedDrawable;
                currentBackgroundShadowDrawable = Theme.chat_msgOutMediaShadowDrawable;
            }
            backgroundDrawableLeft = layoutWidth - backgroundWidth - (!mediaBackground ? 0 : AndroidUtilities.dp(9));
            backgroundDrawableRight = backgroundWidth - (mediaBackground ? 0 : AndroidUtilities.dp(3));
            if (currentMessagesGroup != null) {
                if (!currentPosition.edge) {
                    backgroundDrawableRight += AndroidUtilities.dp(10);
                }
            }
            int backgroundLeft = backgroundDrawableLeft;
            if (!mediaBackground && drawPinnedBottom) {
                backgroundDrawableRight -= AndroidUtilities.dp(6);
            }

            if (currentPosition != null) {
                if ((currentPosition.flags & MessageObject.POSITION_FLAG_RIGHT) == 0) {
                    backgroundDrawableRight += AndroidUtilities.dp(8);
                }
                if ((currentPosition.flags & MessageObject.POSITION_FLAG_LEFT) == 0) {
                    backgroundLeft -= AndroidUtilities.dp(8);
                    backgroundDrawableRight += AndroidUtilities.dp(8);
                }
                if ((currentPosition.flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                    additionalTop -= AndroidUtilities.dp(9);
                    additionalBottom += AndroidUtilities.dp(9);
                }
                if ((currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                    additionalBottom += AndroidUtilities.dp(9);
                }
            }
            int offsetBottom;
            if (drawPinnedBottom && drawPinnedTop) {
                offsetBottom = 0;
            } else if (drawPinnedBottom) {
                offsetBottom = AndroidUtilities.dp(1);
            } else {
                offsetBottom = AndroidUtilities.dp(2);
            }
            int backgroundTop = additionalTop + (drawPinnedTop || drawPinnedTop && drawPinnedBottom ? 0 : AndroidUtilities.dp(1));
            setDrawableBounds(currentBackgroundDrawable, backgroundLeft, backgroundTop, backgroundDrawableRight, layoutHeight - offsetBottom + additionalBottom);
            setDrawableBounds(currentBackgroundSelectedDrawable, backgroundLeft, backgroundTop, backgroundDrawableRight, layoutHeight - offsetBottom + additionalBottom);
            setDrawableBounds(currentBackgroundShadowDrawable, backgroundLeft, backgroundTop, backgroundDrawableRight, layoutHeight - offsetBottom + additionalBottom);
        } else {
            if (!mediaBackground && !drawPinnedBottom) {
                currentBackgroundDrawable = Theme.chat_msgInDrawable;
                currentBackgroundSelectedDrawable = Theme.chat_msgInSelectedDrawable;
                currentBackgroundShadowDrawable = Theme.chat_msgInShadowDrawable;
            } else {
                currentBackgroundDrawable = Theme.chat_msgInMediaDrawable;
                currentBackgroundSelectedDrawable = Theme.chat_msgInMediaSelectedDrawable;
                currentBackgroundShadowDrawable = Theme.chat_msgInMediaShadowDrawable;
            }
            backgroundDrawableLeft = AndroidUtilities.dp((isChat && isAvatarVisible ? 48 : 0) + (!mediaBackground ? 3 : 9));
            backgroundDrawableRight = backgroundWidth - (mediaBackground ? 0 : AndroidUtilities.dp(3));
            if (currentMessagesGroup != null) {
                if (!currentPosition.edge) {
                    backgroundDrawableLeft -= AndroidUtilities.dp(10);
                    backgroundDrawableRight += AndroidUtilities.dp(10);
                }
                if (currentPosition.leftSpanOffset != 0) {
                    backgroundDrawableLeft += (int) Math.ceil(currentPosition.leftSpanOffset / 1000.0f * getGroupPhotosWidth());
                }
            }
            if (!mediaBackground && drawPinnedBottom) {
                backgroundDrawableRight -= AndroidUtilities.dp(6);
                backgroundDrawableLeft += AndroidUtilities.dp(6);
            }
            if (currentPosition != null) {
                if ((currentPosition.flags & MessageObject.POSITION_FLAG_RIGHT) == 0) {
                    backgroundDrawableRight += AndroidUtilities.dp(8);
                }
                if ((currentPosition.flags & MessageObject.POSITION_FLAG_LEFT) == 0) {
                    backgroundDrawableLeft -= AndroidUtilities.dp(8);
                    backgroundDrawableRight += AndroidUtilities.dp(8);
                }
                if ((currentPosition.flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                    additionalTop -= AndroidUtilities.dp(9);
                    additionalBottom += AndroidUtilities.dp(9);
                }
                if ((currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                    additionalBottom += AndroidUtilities.dp(10);
                }
            }
            int offsetBottom;
            if (drawPinnedBottom && drawPinnedTop) {
                offsetBottom = 0;
            } else if (drawPinnedBottom) {
                offsetBottom = AndroidUtilities.dp(1);
            } else {
                offsetBottom = AndroidUtilities.dp(2);
            }
            int backgroundTop = additionalTop + (drawPinnedTop || drawPinnedTop && drawPinnedBottom ? 0 : AndroidUtilities.dp(1));
            setDrawableBounds(currentBackgroundDrawable, backgroundDrawableLeft, backgroundTop, backgroundDrawableRight, layoutHeight - offsetBottom + additionalBottom);
            setDrawableBounds(currentBackgroundSelectedDrawable, backgroundDrawableLeft, backgroundTop, backgroundDrawableRight, layoutHeight - offsetBottom + additionalBottom);
            setDrawableBounds(currentBackgroundShadowDrawable, backgroundDrawableLeft, backgroundTop, backgroundDrawableRight, layoutHeight - offsetBottom + additionalBottom);
        }

        if (checkBoxVisible || checkBoxAnimationInProgress) {
            if (checkBoxVisible && checkBoxAnimationProgress == 1.0f || !checkBoxVisible && checkBoxAnimationProgress == 0.0f) {
                checkBoxAnimationInProgress = false;
            }
            Interpolator interpolator = checkBoxVisible ? CubicBezierInterpolator.EASE_OUT : CubicBezierInterpolator.EASE_IN;
            checkBoxTranslation = (int) Math.ceil(interpolator.getInterpolation(checkBoxAnimationProgress) * AndroidUtilities.dp(35));
            if (!currentMessageObject.isOutOwner()) {
                setTranslationX(checkBoxTranslation);
            }

            int size = AndroidUtilities.dp(21);
            checkBox.setBounds(AndroidUtilities.dp(8 - 35) + checkBoxTranslation, currentBackgroundDrawable.getBounds().bottom - AndroidUtilities.dp(8) - size, size, size);

            if (checkBoxAnimationInProgress) {
                long newTime = SystemClock.uptimeMillis();
                long dt = newTime - lastCheckBoxAnimationTime;
                lastCheckBoxAnimationTime = newTime;

                if (checkBoxVisible) {
                    checkBoxAnimationProgress += dt / MessageBackgroundDrawable.ANIMATION_DURATION;
                    if (checkBoxAnimationProgress > 1.0f) {
                        checkBoxAnimationProgress = 1.0f;
                    }
                    invalidate();
                    ((View) getParent()).invalidate();
                } else {
                    checkBoxAnimationProgress -= dt / MessageBackgroundDrawable.ANIMATION_DURATION;
                    if (checkBoxAnimationProgress <= 0.0f) {
                        checkBoxAnimationProgress = 0.0f;
                    }
                    invalidate();
                    ((View) getParent()).invalidate();
                }
            }
        }

        if (drawBackground && currentBackgroundDrawable != null) {
            if (isHighlightedAnimated) {
                currentBackgroundDrawable.draw(canvas);
                float alpha = highlightProgress >= 300 ? 1.0f : highlightProgress / 300.0f;
                if (currentPosition == null) {
                    currentBackgroundSelectedDrawable.setAlpha((int) (alpha * 255));
                    currentBackgroundSelectedDrawable.draw(canvas);
                }
            } else {
                if (isDrawSelectionBackground() && (currentPosition == null || getBackground() != null)) {
                    currentBackgroundSelectedDrawable.setAlpha(255);
                    currentBackgroundSelectedDrawable.draw(canvas);
                } else {
                    currentBackgroundDrawable.draw(canvas);
                }
            }
            if (currentPosition == null || currentPosition.flags != 0) {
                currentBackgroundShadowDrawable.draw(canvas);
            }
        }
        if (isHighlightedAnimated) {
            long newTime = System.currentTimeMillis();
            long dt = Math.abs(newTime - lastHighlightProgressTime);
            if (dt > 17) {
                dt = 17;
            }
            highlightProgress -= dt;
            lastHighlightProgressTime = newTime;
            if (highlightProgress <= 0) {
                highlightProgress = 0;
                isHighlightedAnimated = false;
            }
            invalidate();
            if (getParent() != null) {
                ((View) getParent()).invalidate();
            }
        }

        drawContent(canvas);

        if (drawShareButton) {
            if (sharePressed) {
                if (!Theme.isCustomTheme() || Theme.hasThemeKey(Theme.key_chat_shareBackgroundSelected)) {
                    Theme.chat_shareDrawable.setColorFilter(Theme.getShareColorFilter(Theme.getColor(Theme.key_chat_shareBackgroundSelected), true));
                } else {
                    Theme.chat_shareDrawable.setColorFilter(Theme.colorPressedFilter2);
                }
            } else {
                if (!Theme.isCustomTheme() || Theme.hasThemeKey(Theme.key_chat_shareBackground)) {
                    Theme.chat_shareDrawable.setColorFilter(Theme.getShareColorFilter(Theme.getColor(Theme.key_chat_shareBackground), false));
                } else {
                    Theme.chat_shareDrawable.setColorFilter(Theme.colorFilter2);
                }
            }
            if (currentMessageObject.isOutOwner()) {
                shareStartX = currentBackgroundDrawable.getBounds().left - AndroidUtilities.dp(8) - Theme.chat_shareDrawable.getIntrinsicWidth();
            } else {
                shareStartX = currentBackgroundDrawable.getBounds().right + AndroidUtilities.dp(8);
            }
            setDrawableBounds(Theme.chat_shareDrawable, shareStartX, shareStartY = layoutHeight - AndroidUtilities.dp(41));
            Theme.chat_shareDrawable.draw(canvas);
            if (drwaShareGoIcon) {
                setDrawableBounds(Theme.chat_goIconDrawable, shareStartX + AndroidUtilities.dp(12), shareStartY + AndroidUtilities.dp(9));
                Theme.chat_goIconDrawable.draw(canvas);
            } else {
                setDrawableBounds(Theme.chat_shareIconDrawable, shareStartX + AndroidUtilities.dp(8), shareStartY + AndroidUtilities.dp(9));
                Theme.chat_shareIconDrawable.draw(canvas);
            }
        }

        if (replyNameLayout != null) {
            if (currentMessageObject.shouldDrawWithoutBackground()) {
                if (currentMessageObject.isOutOwner()) {
                    replyStartX = AndroidUtilities.dp(23);
                } else if (currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                    replyStartX = backgroundDrawableLeft + backgroundDrawableRight + AndroidUtilities.dp(4);
                } else {
                    replyStartX = backgroundDrawableLeft + backgroundDrawableRight + AndroidUtilities.dp(17);
                }
                replyStartY = AndroidUtilities.dp(12);
            } else {
                if (currentMessageObject.isOutOwner()) {
                    replyStartX = backgroundDrawableLeft + AndroidUtilities.dp(12);
                } else {
                    if (mediaBackground) {
                        replyStartX = backgroundDrawableLeft + AndroidUtilities.dp(12);
                    } else {
                        replyStartX = backgroundDrawableLeft + AndroidUtilities.dp(!mediaBackground && drawPinnedBottom ? 12 : 18);
                    }
                }
                replyStartY = AndroidUtilities.dp(12 + (drawForwardedName && forwardedNameLayout[0] != null ? 36 : 0) + (drawNameLayout && nameLayout != null ? 20 : 0));
            }
        }
        if (currentPosition == null) {
            drawNamesLayout(canvas);
        }

        if (!autoPlayingMedia || !MediaController.getInstance().isPlayingMessageAndReadyToDraw(currentMessageObject)) {
            drawOverlays(canvas);
        }
        if ((drawTime || !mediaBackground) && !forceNotDrawTime) {
            drawTime(canvas);
        }

        if ((controlsAlpha != 1.0f || timeAlpha != 1.0f) && currentMessageObject.type != 5) {
            long newTime = System.currentTimeMillis();
            long dt = Math.abs(lastControlsAlphaChangeTime - newTime);
            if (dt > 17) {
                dt = 17;
            }
            totalChangeTime += dt;
            if (totalChangeTime > 100) {
                totalChangeTime = 100;
            }
            lastControlsAlphaChangeTime = newTime;
            if (controlsAlpha != 1.0f) {
                controlsAlpha = AndroidUtilities.decelerateInterpolator.getInterpolation(totalChangeTime / 100.0f);
            }
            if (timeAlpha != 1.0f) {
                timeAlpha = AndroidUtilities.decelerateInterpolator.getInterpolation(totalChangeTime / 100.0f);
            }
            invalidate();
            if (forceNotDrawTime && currentPosition != null && currentPosition.last && getParent() != null) {
                View parent = (View) getParent();
                parent.invalidate();
            }
        }
    }

    public void setTimeAlpha(float value) {
        timeAlpha = value;
    }

    public float getTimeAlpha() {
        return timeAlpha;
    }

    public int getBackgroundDrawableLeft() {
        if (currentMessageObject.isOutOwner()) {
            return layoutWidth - backgroundWidth - (!mediaBackground ? 0 : AndroidUtilities.dp(9));
        } else {
            return AndroidUtilities.dp((isChat && isAvatarVisible ? 48 : 0) + (!mediaBackground ? 3 : 9));
        }
    }

    public boolean hasNameLayout() {
        return drawNameLayout && nameLayout != null ||
                drawForwardedName && forwardedNameLayout[0] != null && forwardedNameLayout[1] != null && (currentPosition == null || currentPosition.minY == 0 && currentPosition.minX == 0) ||
                replyNameLayout != null;
    }

    public boolean isDrawNameLayout() {
        return drawNameLayout && nameLayout != null;
    }

    public void drawNamesLayout(Canvas canvas) {
        if (drawNameLayout && nameLayout != null) {
            canvas.save();

            int oldAlpha = 255;

            if (currentMessageObject.shouldDrawWithoutBackground()) {
                Theme.chat_namePaint.setColor(Theme.getColor(Theme.key_chat_stickerNameText));
                int backWidth;
                if (currentMessageObject.isOutOwner()) {
                    nameX = AndroidUtilities.dp(28);
                } else {
                    nameX = backgroundDrawableLeft + backgroundDrawableRight + AndroidUtilities.dp(22);
                }
                nameY = layoutHeight - AndroidUtilities.dp(38);
                float alphaProgress = currentMessageObject.isOut() && (checkBoxVisible || checkBoxAnimationInProgress) ? (1.0f - checkBoxAnimationProgress) : 1.0f;
                Theme.chat_systemDrawable.setAlpha((int) (alphaProgress * 255));
                Theme.chat_systemDrawable.setColorFilter(Theme.colorFilter);
                Theme.chat_systemDrawable.setBounds((int) nameX - AndroidUtilities.dp(12), (int) nameY - AndroidUtilities.dp(5), (int) nameX + AndroidUtilities.dp(12) + nameWidth, (int) nameY + AndroidUtilities.dp(22));
                Theme.chat_systemDrawable.draw(canvas);
                if (checkBoxVisible || checkBoxAnimationInProgress) {
                    Theme.chat_systemDrawable.setAlpha(oldAlpha);
                }
                nameX -= nameOffsetX;
                int color = Theme.getColor(Theme.key_chat_stickerViaBotNameText);
                color = (Theme.getColor(Theme.key_chat_stickerViaBotNameText) & 0x00ffffff) | ((int) (Color.alpha(color) * alphaProgress) << 24);
                if (viaSpan1 != null) {
                    viaSpan1.setColor(color);
                }
                if (viaSpan2 != null) {
                    viaSpan2.setColor(color);
                }
                Theme.chat_systemDrawable.setAlpha(255);
            } else {
                if (mediaBackground || currentMessageObject.isOutOwner()) {
                    nameX = backgroundDrawableLeft + AndroidUtilities.dp(11) - nameOffsetX;
                } else {
                    nameX = backgroundDrawableLeft + AndroidUtilities.dp(!mediaBackground && drawPinnedBottom ? 11 : 17) - nameOffsetX;
                }
                if (currentUser != null) {
                    Theme.chat_namePaint.setColor(AvatarDrawable.getNameColorForId(currentUser.id));
                } else if (currentChat != null) {
                    if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                        Theme.chat_namePaint.setColor(Theme.changeColorAccent(AvatarDrawable.getNameColorForId(5)));
                    } else {
                        Theme.chat_namePaint.setColor(AvatarDrawable.getNameColorForId(currentChat.id));
                    }
                } else {
                    Theme.chat_namePaint.setColor(AvatarDrawable.getNameColorForId(0));
                }
                nameY = AndroidUtilities.dp(drawPinnedTop ? 9 : 10);
            }
            canvas.translate(nameX, nameY);
            nameLayout.draw(canvas);
            canvas.restore();
            if (adminLayout != null) {
                Theme.chat_adminPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_adminSelectedText : Theme.key_chat_adminText));
                canvas.save();
                canvas.translate(backgroundDrawableLeft + backgroundDrawableRight - AndroidUtilities.dp(11) - adminLayout.getLineWidth(0), nameY + AndroidUtilities.dp(0.5f));
                adminLayout.draw(canvas);
                canvas.restore();
            }
        }

        if (drawForwardedName && forwardedNameLayout[0] != null && forwardedNameLayout[1] != null && (currentPosition == null || currentPosition.minY == 0 && currentPosition.minX == 0)) {
            if (currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                Theme.chat_forwardNamePaint.setColor(Theme.getColor(Theme.key_chat_stickerReplyNameText));
                if (currentMessageObject.isOutOwner()) {
                    forwardNameX = AndroidUtilities.dp(23);
                } else {
                    forwardNameX = backgroundDrawableLeft + backgroundDrawableRight + AndroidUtilities.dp(17);
                }
                forwardNameY = AndroidUtilities.dp(12);

                int backWidth = forwardedNameWidth + AndroidUtilities.dp(14);
                Theme.chat_systemDrawable.setColorFilter(Theme.colorFilter);
                Theme.chat_systemDrawable.setBounds(forwardNameX - AndroidUtilities.dp(7), forwardNameY - AndroidUtilities.dp(6), forwardNameX - AndroidUtilities.dp(7) + backWidth, forwardNameY + AndroidUtilities.dp(38));
                Theme.chat_systemDrawable.draw(canvas);
            } else {
                forwardNameY = AndroidUtilities.dp(10 + (drawNameLayout ? 19 : 0));
                if (currentMessageObject.isOutOwner()) {
                    Theme.chat_forwardNamePaint.setColor(Theme.getColor(Theme.key_chat_outForwardedNameText));
                    forwardNameX = backgroundDrawableLeft + AndroidUtilities.dp(11);
                } else {
                    Theme.chat_forwardNamePaint.setColor(Theme.getColor(Theme.key_chat_inForwardedNameText));
                    if (mediaBackground) {
                        forwardNameX = backgroundDrawableLeft + AndroidUtilities.dp(11);
                    } else {
                        forwardNameX = backgroundDrawableLeft + AndroidUtilities.dp(!mediaBackground && drawPinnedBottom ? 11 : 17);
                    }
                }
            }
            for (int a = 0; a < 2; a++) {
                canvas.save();
                canvas.translate(forwardNameX - forwardNameOffsetX[a], forwardNameY + AndroidUtilities.dp(16) * a);
                forwardedNameLayout[a].draw(canvas);
                canvas.restore();
            }
        }

        if (replyNameLayout != null) {
            if (currentMessageObject.shouldDrawWithoutBackground()) {
                Theme.chat_replyLinePaint.setColor(Theme.getColor(Theme.key_chat_stickerReplyLine));
                Theme.chat_replyNamePaint.setColor(Theme.getColor(Theme.key_chat_stickerReplyNameText));
                Theme.chat_replyTextPaint.setColor(Theme.getColor(Theme.key_chat_stickerReplyMessageText));
                int backWidth = Math.max(replyNameWidth, replyTextWidth) + AndroidUtilities.dp(14);
                Theme.chat_systemDrawable.setColorFilter(Theme.colorFilter);
                Theme.chat_systemDrawable.setBounds(replyStartX - AndroidUtilities.dp(7), replyStartY - AndroidUtilities.dp(6), replyStartX - AndroidUtilities.dp(7) + backWidth, replyStartY + AndroidUtilities.dp(41));
                Theme.chat_systemDrawable.draw(canvas);
            } else {
                if (currentMessageObject.isOutOwner()) {
                    Theme.chat_replyLinePaint.setColor(Theme.getColor(Theme.key_chat_outReplyLine));
                    Theme.chat_replyNamePaint.setColor(Theme.getColor(Theme.key_chat_outReplyNameText));
                    if (currentMessageObject.hasValidReplyMessageObject() && currentMessageObject.replyMessageObject.type == 0 && !(currentMessageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame || currentMessageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice)) {
                        Theme.chat_replyTextPaint.setColor(Theme.getColor(Theme.key_chat_outReplyMessageText));
                    } else {
                        Theme.chat_replyTextPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outReplyMediaMessageSelectedText : Theme.key_chat_outReplyMediaMessageText));
                    }
                } else {
                    Theme.chat_replyLinePaint.setColor(Theme.getColor(Theme.key_chat_inReplyLine));
                    Theme.chat_replyNamePaint.setColor(Theme.getColor(Theme.key_chat_inReplyNameText));
                    if (currentMessageObject.hasValidReplyMessageObject() && currentMessageObject.replyMessageObject.type == 0 && !(currentMessageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame || currentMessageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice)) {
                        Theme.chat_replyTextPaint.setColor(Theme.getColor(Theme.key_chat_inReplyMessageText));
                    } else {
                        Theme.chat_replyTextPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inReplyMediaMessageSelectedText : Theme.key_chat_inReplyMediaMessageText));
                    }
                }
            }
            if (currentPosition == null || currentPosition.minY == 0 && currentPosition.minX == 0) {
                canvas.drawRect(replyStartX, replyStartY, replyStartX + AndroidUtilities.dp(2), replyStartY + AndroidUtilities.dp(35), Theme.chat_replyLinePaint);
                if (needReplyImage) {
                    replyImageReceiver.setImageCoords(replyStartX + AndroidUtilities.dp(10), replyStartY, AndroidUtilities.dp(35), AndroidUtilities.dp(35));
                    replyImageReceiver.draw(canvas);
                }

                if (replyNameLayout != null) {
                    canvas.save();
                    canvas.translate(replyStartX - replyNameOffset + AndroidUtilities.dp(10 + (needReplyImage ? 44 : 0)), replyStartY);
                    replyNameLayout.draw(canvas);
                    canvas.restore();
                }
                if (replyTextLayout != null) {
                    canvas.save();
                    canvas.translate(replyStartX - replyTextOffset + AndroidUtilities.dp(10 + (needReplyImage ? 44 : 0)), replyStartY + AndroidUtilities.dp(19));
                    replyTextLayout.draw(canvas);
                    canvas.restore();
                }
            }
        }
    }

    public boolean hasCaptionLayout() {
        return captionLayout != null;
    }

    public void setDrawSelectionBackground(boolean value) {
        drawSelectionBackground = value;
        invalidate();
    }

    public boolean isDrawingSelectionBackground() {
        return drawSelectionBackground || isHighlightedAnimated || isHighlighted;
    }

    public float getHightlightAlpha() {
        if (!drawSelectionBackground && isHighlightedAnimated) {
            return highlightProgress >= 300 ? 1.0f : highlightProgress / 300.0f;
        }
        return 1.0f;
    }

    public void setCheckBoxVisible(boolean visible, boolean animated) {
        if (visible && checkBox == null) {
            checkBox = new CheckBoxBase(this, 21);
            if (attachedToWindow) {
                checkBox.onAttachedToWindow();
            }
        }
        if (visible && photoCheckBox == null && currentMessagesGroup != null && currentMessagesGroup.messages.size() > 1) {
            photoCheckBox = new CheckBoxBase(this, 21);
            photoCheckBox.setUseDefaultCheck(true);
            if (attachedToWindow) {
                photoCheckBox.onAttachedToWindow();
            }
        }
        if (checkBoxVisible == visible) {
            if (animated != checkBoxAnimationInProgress && !animated) {
                checkBoxAnimationProgress = visible ? 1.0f : 0.0f;
                invalidate();
            }
            return;
        }
        checkBoxAnimationInProgress = animated;
        checkBoxVisible = visible;
        if (animated) {
            lastCheckBoxAnimationTime = SystemClock.uptimeMillis();
        } else {
            checkBoxAnimationProgress = visible ? 1.0f : 0.0f;
        }
        invalidate();
    }

    public void setChecked(boolean checked, boolean allChecked, boolean animated) {
        if (checkBox != null) {
            checkBox.setChecked(allChecked, animated);
        }
        if (photoCheckBox != null) {
            photoCheckBox.setChecked(checked, animated);
        }
    }

    public void drawCaptionLayout(Canvas canvas, boolean selectionOnly) {
        if (captionLayout == null || selectionOnly && pressedLink == null) {
            return;
        }
        if (currentMessageObject.isOutOwner()) {
            Theme.chat_msgTextPaint.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
            Theme.chat_msgTextPaint.linkColor = Theme.getColor(Theme.key_chat_messageLinkOut);
        } else {
            Theme.chat_msgTextPaint.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
            Theme.chat_msgTextPaint.linkColor = Theme.getColor(Theme.key_chat_messageLinkIn);
        }
        canvas.save();
        canvas.translate(captionX, captionY);
        if (pressedLink != null) {
            for (int b = 0; b < urlPath.size(); b++) {
                canvas.drawPath(urlPath.get(b), Theme.chat_urlPaint);
            }
        }
        if (!urlPathSelection.isEmpty()) {
            for (int b = 0; b < urlPathSelection.size(); b++) {
                canvas.drawPath(urlPathSelection.get(b), Theme.chat_textSearchSelectionPaint);
            }
        }
        if (!selectionOnly) {
            try {
                captionLayout.draw(canvas);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        canvas.restore();
    }

    public boolean needDrawTime() {
        return !forceNotDrawTime;
    }

    public void drawTime(Canvas canvas) {
        if ((!drawTime || groupPhotoInvisible) && mediaBackground && captionLayout == null || timeLayout == null) {
            return;
        }
        if (currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
            Theme.chat_timePaint.setColor(Theme.getColor(Theme.key_chat_mediaTimeText));
        } else {
            if (mediaBackground && captionLayout == null) {
                if (currentMessageObject.shouldDrawWithoutBackground()) {
                    Theme.chat_timePaint.setColor(Theme.getColor(Theme.key_chat_serviceText));
                } else {
                    Theme.chat_timePaint.setColor(Theme.getColor(Theme.key_chat_mediaTimeText));
                }
            } else {
                if (currentMessageObject.isOutOwner()) {
                    Theme.chat_timePaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outTimeSelectedText : Theme.key_chat_outTimeText));
                } else {
                    Theme.chat_timePaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inTimeSelectedText : Theme.key_chat_inTimeText));
                }
            }
        }
        if (drawPinnedBottom) {
            canvas.translate(0, AndroidUtilities.dp(2));
        }
        if (mediaBackground && captionLayout == null) {
            Paint paint;
            if (currentMessageObject.shouldDrawWithoutBackground()) {
                paint = Theme.chat_actionBackgroundPaint;
            } else {
                paint = Theme.chat_timeBackgroundPaint;
            }
            int oldAlpha = paint.getAlpha();
            paint.setAlpha((int) (oldAlpha * timeAlpha));
            Theme.chat_timePaint.setAlpha((int) (255 * timeAlpha));
            int x1 = timeX - AndroidUtilities.dp(4);
            int y1 = layoutHeight - AndroidUtilities.dp(28);
            rect.set(x1, y1, x1 + timeWidth + AndroidUtilities.dp(8 + (currentMessageObject.isOutOwner() ? 20 : 0)), y1 + AndroidUtilities.dp(17));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
            paint.setAlpha(oldAlpha);

            int additionalX = (int) (-timeLayout.getLineLeft(0));
            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup || (currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                additionalX += (int) (timeWidth - timeLayout.getLineWidth(0));

                if (currentMessageObject.isSending() || currentMessageObject.isEditing()) {
                    if (!currentMessageObject.isOutOwner()) {
                        setDrawableBounds(Theme.chat_msgMediaClockDrawable, timeX + (currentMessageObject.scheduled ? 0 : AndroidUtilities.dp(11)), layoutHeight - AndroidUtilities.dp(14.0f) - Theme.chat_msgMediaClockDrawable.getIntrinsicHeight());
                        Theme.chat_msgMediaClockDrawable.draw(canvas);
                    }
                } else if (currentMessageObject.isSendError()) {
                    if (!currentMessageObject.isOutOwner()) {
                        int x = timeX + (currentMessageObject.scheduled ? 0 : AndroidUtilities.dp(11));
                        int y = layoutHeight - AndroidUtilities.dp(26.5f);
                        rect.set(x, y, x + AndroidUtilities.dp(14), y + AndroidUtilities.dp(14));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), Theme.chat_msgErrorPaint);
                        setDrawableBounds(Theme.chat_msgErrorDrawable, x + AndroidUtilities.dp(6), y + AndroidUtilities.dp(2));
                        Theme.chat_msgErrorDrawable.draw(canvas);
                    }
                } else if (viewsLayout != null) {
                    Drawable viewsDrawable;
                    if (currentMessageObject.shouldDrawWithoutBackground()) {
                        viewsDrawable = Theme.chat_msgStickerViewsDrawable;
                    } else {
                        viewsDrawable = Theme.chat_msgMediaViewsDrawable;
                    }
                    oldAlpha = ((BitmapDrawable) viewsDrawable).getPaint().getAlpha();
                    viewsDrawable.setAlpha((int) (timeAlpha * oldAlpha));
                    setDrawableBounds(viewsDrawable, timeX, layoutHeight - AndroidUtilities.dp(10.5f) - timeLayout.getHeight());
                    viewsDrawable.draw(canvas);
                    viewsDrawable.setAlpha(oldAlpha);

                    canvas.save();
                    canvas.translate(timeX + viewsDrawable.getIntrinsicWidth() + AndroidUtilities.dp(3), layoutHeight - AndroidUtilities.dp(12.3f) - timeLayout.getHeight());
                    viewsLayout.draw(canvas);
                    canvas.restore();
                }
            }

            canvas.save();
            canvas.translate(timeX + additionalX, layoutHeight - AndroidUtilities.dp(12.3f) - timeLayout.getHeight());
            timeLayout.draw(canvas);
            canvas.restore();
            Theme.chat_timePaint.setAlpha(255);
        } else {
            int additionalX = (int) (-timeLayout.getLineLeft(0));
            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup || (currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                additionalX += (int) (timeWidth - timeLayout.getLineWidth(0));

                if (currentMessageObject.isSending() || currentMessageObject.isEditing()) {
                    if (!currentMessageObject.isOutOwner()) {
                        Drawable clockDrawable = isDrawSelectionBackground() ? Theme.chat_msgInSelectedClockDrawable : Theme.chat_msgInClockDrawable;
                        setDrawableBounds(clockDrawable, timeX + (currentMessageObject.scheduled ? 0 : AndroidUtilities.dp(11)), layoutHeight - AndroidUtilities.dp(8.5f) - clockDrawable.getIntrinsicHeight());
                        clockDrawable.draw(canvas);
                    }
                } else if (currentMessageObject.isSendError()) {
                    if (!currentMessageObject.isOutOwner()) {
                        int x = timeX + (currentMessageObject.scheduled ? 0 : AndroidUtilities.dp(11));
                        int y = layoutHeight - AndroidUtilities.dp(20.5f);
                        rect.set(x, y, x + AndroidUtilities.dp(14), y + AndroidUtilities.dp(14));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), Theme.chat_msgErrorPaint);
                        setDrawableBounds(Theme.chat_msgErrorDrawable, x + AndroidUtilities.dp(6), y + AndroidUtilities.dp(2));
                        Theme.chat_msgErrorDrawable.draw(canvas);
                    }
                } else if (viewsLayout != null) {
                    if (!currentMessageObject.isOutOwner()) {
                        Drawable viewsDrawable = isDrawSelectionBackground() ? Theme.chat_msgInViewsSelectedDrawable : Theme.chat_msgInViewsDrawable;
                        setDrawableBounds(viewsDrawable, timeX, layoutHeight - AndroidUtilities.dp(4.5f) - timeLayout.getHeight());
                        viewsDrawable.draw(canvas);
                    } else {
                        Drawable viewsDrawable = isDrawSelectionBackground() ? Theme.chat_msgOutViewsSelectedDrawable : Theme.chat_msgOutViewsDrawable;
                        setDrawableBounds(viewsDrawable, timeX, layoutHeight - AndroidUtilities.dp(4.5f) - timeLayout.getHeight());
                        viewsDrawable.draw(canvas);
                    }

                    canvas.save();
                    canvas.translate(timeX + Theme.chat_msgInViewsDrawable.getIntrinsicWidth() + AndroidUtilities.dp(3), layoutHeight - AndroidUtilities.dp(6.5f) - timeLayout.getHeight());
                    viewsLayout.draw(canvas);
                    canvas.restore();
                }
            }

            canvas.save();
            canvas.translate(timeX + additionalX, layoutHeight - AndroidUtilities.dp(6.5f) - timeLayout.getHeight());
            timeLayout.draw(canvas);
            canvas.restore();
        }

        if (currentMessageObject.isOutOwner()) {
            boolean drawCheck1 = false;
            boolean drawCheck2 = false;
            boolean drawClock = false;
            boolean drawError = false;
            boolean isBroadcast = (int) (currentMessageObject.getDialogId() >> 32) == 1;

            if (currentMessageObject.isSending() || currentMessageObject.isEditing()) {
                drawCheck1 = false;
                drawCheck2 = false;
                drawClock = true;
                drawError = false;
            } else if (currentMessageObject.isSendError()) {
                drawCheck1 = false;
                drawCheck2 = false;
                drawClock = false;
                drawError = true;
            } else if (currentMessageObject.isSent()) {
                if (!currentMessageObject.scheduled && !currentMessageObject.isUnread()) {
                    drawCheck1 = true;
                    drawCheck2 = true;
                } else {
                    drawCheck1 = false;
                    drawCheck2 = true;
                }
                drawClock = false;
                drawError = false;
            }

            if (drawClock) {
                if (mediaBackground && captionLayout == null) {
                    if (currentMessageObject.shouldDrawWithoutBackground()) {
                        Theme.chat_msgStickerClockDrawable.setAlpha((int) (255 * timeAlpha));
                        setDrawableBounds(Theme.chat_msgStickerClockDrawable, layoutWidth - AndroidUtilities.dp(22.0f) - Theme.chat_msgStickerClockDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.5f) - Theme.chat_msgStickerClockDrawable.getIntrinsicHeight());
                        Theme.chat_msgStickerClockDrawable.draw(canvas);
                        Theme.chat_msgStickerClockDrawable.setAlpha(255);
                    } else {
                        setDrawableBounds(Theme.chat_msgMediaClockDrawable, layoutWidth - AndroidUtilities.dp(22.0f) - Theme.chat_msgMediaClockDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.5f) - Theme.chat_msgMediaClockDrawable.getIntrinsicHeight());
                        Theme.chat_msgMediaClockDrawable.draw(canvas);
                    }
                } else {
                    setDrawableBounds(Theme.chat_msgOutClockDrawable, layoutWidth - AndroidUtilities.dp(18.5f) - Theme.chat_msgOutClockDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.5f) - Theme.chat_msgOutClockDrawable.getIntrinsicHeight());
                    Theme.chat_msgOutClockDrawable.draw(canvas);
                }
            }

            if (isBroadcast) {
                if (drawCheck1 || drawCheck2) {
                    if (mediaBackground && captionLayout == null) {
                        setDrawableBounds(Theme.chat_msgBroadcastMediaDrawable, layoutWidth - AndroidUtilities.dp(24.0f) - Theme.chat_msgBroadcastMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(14.0f) - Theme.chat_msgBroadcastMediaDrawable.getIntrinsicHeight());
                        Theme.chat_msgBroadcastMediaDrawable.draw(canvas);
                    } else {
                        setDrawableBounds(Theme.chat_msgBroadcastDrawable, layoutWidth - AndroidUtilities.dp(20.5f) - Theme.chat_msgBroadcastDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - Theme.chat_msgBroadcastDrawable.getIntrinsicHeight());
                        Theme.chat_msgBroadcastDrawable.draw(canvas);
                    }
                }
            } else {
                if (drawCheck2) {
                    if (mediaBackground && captionLayout == null) {
                        if (currentMessageObject.shouldDrawWithoutBackground()) {
                            if (drawCheck1) {
                                setDrawableBounds(Theme.chat_msgStickerCheckDrawable, layoutWidth - AndroidUtilities.dp(26.3f) - Theme.chat_msgStickerCheckDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.5f) - Theme.chat_msgStickerCheckDrawable.getIntrinsicHeight());
                            } else {
                                setDrawableBounds(Theme.chat_msgStickerCheckDrawable, layoutWidth - AndroidUtilities.dp(21.5f) - Theme.chat_msgStickerCheckDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.5f) - Theme.chat_msgStickerCheckDrawable.getIntrinsicHeight());
                            }
                            Theme.chat_msgStickerCheckDrawable.draw(canvas);
                        } else {
                            if (drawCheck1) {
                                setDrawableBounds(Theme.chat_msgMediaCheckDrawable, layoutWidth - AndroidUtilities.dp(26.3f) - Theme.chat_msgMediaCheckDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.5f) - Theme.chat_msgMediaCheckDrawable.getIntrinsicHeight());
                            } else {
                                setDrawableBounds(Theme.chat_msgMediaCheckDrawable, layoutWidth - AndroidUtilities.dp(21.5f) - Theme.chat_msgMediaCheckDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.5f) - Theme.chat_msgMediaCheckDrawable.getIntrinsicHeight());
                            }
                            Theme.chat_msgMediaCheckDrawable.setAlpha((int) (255 * timeAlpha));
                            Theme.chat_msgMediaCheckDrawable.draw(canvas);
                            Theme.chat_msgMediaCheckDrawable.setAlpha(255);
                        }
                    } else {
                        Drawable drawable;
                        if (drawCheck1) {
                            drawable = isDrawSelectionBackground() ? Theme.chat_msgOutCheckReadSelectedDrawable : Theme.chat_msgOutCheckReadDrawable;
                            setDrawableBounds(drawable, layoutWidth - AndroidUtilities.dp(22.5f) - drawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - drawable.getIntrinsicHeight());
                        } else {
                            drawable = isDrawSelectionBackground() ? Theme.chat_msgOutCheckSelectedDrawable : Theme.chat_msgOutCheckDrawable;
                            setDrawableBounds(drawable, layoutWidth - AndroidUtilities.dp(18.5f) - drawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - drawable.getIntrinsicHeight());
                        }
                        drawable.draw(canvas);
                    }
                }
                if (drawCheck1) {
                    if (mediaBackground && captionLayout == null) {
                        if (currentMessageObject.shouldDrawWithoutBackground()) {
                            setDrawableBounds(Theme.chat_msgStickerHalfCheckDrawable, layoutWidth - AndroidUtilities.dp(21.5f) - Theme.chat_msgStickerHalfCheckDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.5f) - Theme.chat_msgStickerHalfCheckDrawable.getIntrinsicHeight());
                            Theme.chat_msgStickerHalfCheckDrawable.draw(canvas);
                        } else {
                            setDrawableBounds(Theme.chat_msgMediaHalfCheckDrawable, layoutWidth - AndroidUtilities.dp(21.5f) - Theme.chat_msgMediaHalfCheckDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.5f) - Theme.chat_msgMediaHalfCheckDrawable.getIntrinsicHeight());
                            Theme.chat_msgMediaHalfCheckDrawable.setAlpha((int) (255 * timeAlpha));
                            Theme.chat_msgMediaHalfCheckDrawable.draw(canvas);
                            Theme.chat_msgMediaHalfCheckDrawable.setAlpha(255);
                        }
                    } else {
                        Drawable drawable = isDrawSelectionBackground() ? Theme.chat_msgOutHalfCheckSelectedDrawable : Theme.chat_msgOutHalfCheckDrawable;
                        setDrawableBounds(drawable, layoutWidth - AndroidUtilities.dp(18) - drawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - drawable.getIntrinsicHeight());
                        drawable.draw(canvas);
                    }
                }
            }
            if (drawError) {
                int x;
                int y;
                if (mediaBackground && captionLayout == null) {
                    x = layoutWidth - AndroidUtilities.dp(34.5f);
                    y = layoutHeight - AndroidUtilities.dp(26.5f);
                } else {
                    x = layoutWidth - AndroidUtilities.dp(32);
                    y = layoutHeight - AndroidUtilities.dp(21);
                }
                rect.set(x, y, x + AndroidUtilities.dp(14), y + AndroidUtilities.dp(14));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), Theme.chat_msgErrorPaint);
                setDrawableBounds(Theme.chat_msgErrorDrawable, x + AndroidUtilities.dp(6), y + AndroidUtilities.dp(2));
                Theme.chat_msgErrorDrawable.draw(canvas);
            }
        }
    }

    public void drawOverlays(Canvas canvas) {
        long newAnimationTime = SystemClock.uptimeMillis();
        long animationDt = newAnimationTime - lastAnimationTime;
        if (animationDt > 17) {
            animationDt = 17;
        }
        lastAnimationTime = newAnimationTime;

        if (currentMessageObject.hadAnimationNotReadyLoading && photoImage.getVisible() && !currentMessageObject.needDrawBluredPreview() && (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF)) {
            AnimatedFileDrawable animation = photoImage.getAnimation();
            if (animation != null && animation.hasBitmap()) {
                currentMessageObject.hadAnimationNotReadyLoading = false;
                updateButtonState(false, true, false);
            }
        }

        if (currentMessageObject.type == 1 || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
            if (photoImage.getVisible()) {
                if (!currentMessageObject.needDrawBluredPreview()) {
                    if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                        int oldAlpha = ((BitmapDrawable) Theme.chat_msgMediaMenuDrawable).getPaint().getAlpha();
                        if (drawPhotoCheckBox) {
                            Theme.chat_msgMediaMenuDrawable.setAlpha((int) (oldAlpha * controlsAlpha * (1.0f - checkBoxAnimationProgress)));
                        } else {
                            Theme.chat_msgMediaMenuDrawable.setAlpha((int) (oldAlpha * controlsAlpha));
                        }
                        setDrawableBounds(Theme.chat_msgMediaMenuDrawable, otherX = photoImage.getImageX() + photoImage.getImageWidth() - AndroidUtilities.dp(14), otherY = photoImage.getImageY() + AndroidUtilities.dp(8.1f));
                        Theme.chat_msgMediaMenuDrawable.draw(canvas);
                        Theme.chat_msgMediaMenuDrawable.setAlpha(oldAlpha);
                    }
                }

                boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
                if (animatingNoSoundPlaying != playing) {
                    animatingNoSoundPlaying = playing;
                    animatingNoSound = playing ? 1 : 2;
                    animatingNoSoundProgress = playing ? 1.0f : 0.0f;
                }
                if (buttonState == 1 || buttonState == 2 || buttonState == 0 || buttonState == 3 || buttonState == -1 || currentMessageObject.needDrawBluredPreview()) {
                    if (autoPlayingMedia) {
                        updatePlayingMessageProgress();
                    }
                    if (infoLayout != null && (!forceNotDrawTime || autoPlayingMedia || drawVideoImageButton)) {
                        float alpha = currentMessageObject.needDrawBluredPreview() && docTitleLayout == null ? 0 : animatingDrawVideoImageButtonProgress;

                        Theme.chat_infoPaint.setColor(Theme.getColor(Theme.key_chat_mediaInfoText));
                        int x1 = photoImage.getImageX() + AndroidUtilities.dp(4);
                        int y1 = photoImage.getImageY() + AndroidUtilities.dp(4);

                        int imageW;
                        if (autoPlayingMedia && (!playing || animatingNoSound != 0)) {
                            imageW = (int) ((Theme.chat_msgNoSoundDrawable.getIntrinsicWidth() + AndroidUtilities.dp(4)) * animatingNoSoundProgress);
                        } else {
                            imageW = 0;
                        }
                        int w = (int) Math.ceil(infoWidth + AndroidUtilities.dp(8) + imageW + (Math.max(infoWidth + imageW, docTitleWidth) + (canStreamVideo ? AndroidUtilities.dp(32) : 0) - infoWidth - imageW) * alpha);
                        if (alpha != 0 && docTitleLayout == null) {
                            alpha = 0;
                        }
                        rect.set(x1, y1, x1 + w, y1 + AndroidUtilities.dp(16.5f + 15.5f * alpha));

                        int oldAlpha = Theme.chat_timeBackgroundPaint.getAlpha();
                        Theme.chat_timeBackgroundPaint.setAlpha((int) (oldAlpha * controlsAlpha));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_timeBackgroundPaint);
                        Theme.chat_timeBackgroundPaint.setAlpha(oldAlpha);

                        Theme.chat_infoPaint.setAlpha((int) (255 * controlsAlpha));

                        canvas.save();
                        canvas.translate(noSoundCenterX = photoImage.getImageX() + AndroidUtilities.dp(8 + (canStreamVideo ? 30 * alpha : 0)), photoImage.getImageY() + AndroidUtilities.dp(5.5f + 0.2f * alpha));
                        if (infoLayout != null) {
                            infoLayout.draw(canvas);
                        }
                        if (alpha > 0 && docTitleLayout != null) {
                            canvas.save();
                            Theme.chat_infoPaint.setAlpha((int) (255 * controlsAlpha * alpha));
                            canvas.translate(0, AndroidUtilities.dp(14.3f * alpha));
                            docTitleLayout.draw(canvas);
                            canvas.restore();
                        }
                        if (imageW != 0) {
                            Theme.chat_msgNoSoundDrawable.setAlpha((int) (255 * animatingNoSoundProgress * animatingNoSoundProgress * controlsAlpha));
                            canvas.translate(infoWidth + AndroidUtilities.dp(4), 0);
                            int size = AndroidUtilities.dp(14 * animatingNoSoundProgress);
                            int y = (AndroidUtilities.dp(14) - size) / 2;
                            Theme.chat_msgNoSoundDrawable.setBounds(0, y, size, y + size);
                            Theme.chat_msgNoSoundDrawable.draw(canvas);
                            noSoundCenterX += infoWidth + AndroidUtilities.dp(4) + size / 2;
                        }
                        canvas.restore();
                        Theme.chat_infoPaint.setAlpha(255);
                    }
                }
                if (animatingDrawVideoImageButton == 1) {
                    animatingDrawVideoImageButtonProgress -= animationDt / 160.0f;
                    if (animatingDrawVideoImageButtonProgress <= 0) {
                        animatingDrawVideoImageButtonProgress = 0;
                        animatingDrawVideoImageButton = 0;
                    }
                    invalidate();
                } else if (animatingDrawVideoImageButton == 2) {
                    animatingDrawVideoImageButtonProgress += animationDt / 160.0f;
                    if (animatingDrawVideoImageButtonProgress >= 1) {
                        animatingDrawVideoImageButtonProgress = 1;
                        animatingDrawVideoImageButton = 0;
                    }
                    invalidate();
                }
                if (animatingNoSound == 1) {
                    animatingNoSoundProgress -= animationDt / 180.0f;
                    if (animatingNoSoundProgress <= 0.0f) {
                        animatingNoSoundProgress = 0.0f;
                        animatingNoSound = 0;
                    }
                    invalidate();
                } else if (animatingNoSound == 2) {
                    animatingNoSoundProgress += animationDt / 180.0f;
                    if (animatingNoSoundProgress >= 1.0f) {
                        animatingNoSoundProgress = 1.0f;
                        animatingNoSound = 0;
                    }
                    invalidate();
                }
            }
        } else if (currentMessageObject.type == 4) {
            if (docTitleLayout != null) {
                if (currentMessageObject.isOutOwner()) {
                    Theme.chat_locationTitlePaint.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
                    Theme.chat_locationAddressPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outVenueInfoSelectedText : Theme.key_chat_outVenueInfoText));
                } else {
                    Theme.chat_locationTitlePaint.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
                    Theme.chat_locationAddressPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inVenueInfoSelectedText : Theme.key_chat_inVenueInfoText));
                }

                if (currentMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                    int cy = photoImage.getImageY2() + AndroidUtilities.dp(30);
                    if (!locationExpired) {
                        forceNotDrawTime = true;
                        float progress = 1.0f - Math.abs(ConnectionsManager.getInstance(currentAccount).getCurrentTime() - currentMessageObject.messageOwner.date) / (float) currentMessageObject.messageOwner.media.period;
                        rect.set(photoImage.getImageX2() - AndroidUtilities.dp(43), cy - AndroidUtilities.dp(15), photoImage.getImageX2() - AndroidUtilities.dp(13), cy + AndroidUtilities.dp(15));
                        if (currentMessageObject.isOutOwner()) {
                            Theme.chat_radialProgress2Paint.setColor(Theme.getColor(Theme.key_chat_outInstant));
                            Theme.chat_livePaint.setColor(Theme.getColor(Theme.key_chat_outInstant));
                        } else {
                            Theme.chat_radialProgress2Paint.setColor(Theme.getColor(Theme.key_chat_inInstant));
                            Theme.chat_livePaint.setColor(Theme.getColor(Theme.key_chat_inInstant));
                        }

                        Theme.chat_radialProgress2Paint.setAlpha(50);
                        canvas.drawCircle(rect.centerX(), rect.centerY(), AndroidUtilities.dp(15), Theme.chat_radialProgress2Paint);
                        Theme.chat_radialProgress2Paint.setAlpha(255);
                        canvas.drawArc(rect, -90, -360 * progress, false, Theme.chat_radialProgress2Paint);

                        String text = LocaleController.formatLocationLeftTime(Math.abs(currentMessageObject.messageOwner.media.period - (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - currentMessageObject.messageOwner.date)));
                        float w = Theme.chat_livePaint.measureText(text);

                        canvas.drawText(text, rect.centerX() - w / 2, cy + AndroidUtilities.dp(4), Theme.chat_livePaint);

                        canvas.save();
                        canvas.translate(photoImage.getImageX() + AndroidUtilities.dp(10), photoImage.getImageY2() + AndroidUtilities.dp(10));
                        docTitleLayout.draw(canvas);
                        canvas.translate(0, AndroidUtilities.dp(23));
                        infoLayout.draw(canvas);
                        canvas.restore();
                    }

                    int cx = photoImage.getImageX() + photoImage.getImageWidth() / 2 - AndroidUtilities.dp(31);
                    cy = photoImage.getImageY() + photoImage.getImageHeight() / 2 - AndroidUtilities.dp(38);
                    setDrawableBounds(Theme.chat_msgAvatarLiveLocationDrawable, cx, cy);
                    Theme.chat_msgAvatarLiveLocationDrawable.draw(canvas);

                    locationImageReceiver.setImageCoords(cx + AndroidUtilities.dp(5.0f), cy + AndroidUtilities.dp(5.0f), AndroidUtilities.dp(52), AndroidUtilities.dp(52));
                    locationImageReceiver.draw(canvas);
                } else {
                    canvas.save();
                    canvas.translate(photoImage.getImageX() + AndroidUtilities.dp(6), photoImage.getImageY2() + AndroidUtilities.dp(8));
                    docTitleLayout.draw(canvas);
                    if (infoLayout != null) {
                        canvas.translate(0, AndroidUtilities.dp(21));
                        infoLayout.draw(canvas);
                    }
                    canvas.restore();
                }
            }
        } else if (currentMessageObject.type == 16) {
            if (currentMessageObject.isOutOwner()) {
                Theme.chat_audioTitlePaint.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
                Theme.chat_contactPhonePaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outTimeSelectedText : Theme.key_chat_outTimeText));
            } else {
                Theme.chat_audioTitlePaint.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
                Theme.chat_contactPhonePaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inTimeSelectedText : Theme.key_chat_inTimeText));
            }
            forceNotDrawTime = true;
            int x;
            if (currentMessageObject.isOutOwner()) {
                x = layoutWidth - backgroundWidth + AndroidUtilities.dp(16);
            } else {
                if (isChat && currentMessageObject.needDrawAvatar()) {
                    x = AndroidUtilities.dp(74);
                } else {
                    x = AndroidUtilities.dp(25);
                }
            }
            otherX = x;
            if (titleLayout != null) {
                canvas.save();
                canvas.translate(x, AndroidUtilities.dp(12) + namesOffset);
                titleLayout.draw(canvas);
                canvas.restore();
            }
            if (docTitleLayout != null) {
                canvas.save();
                canvas.translate(x + AndroidUtilities.dp(19), AndroidUtilities.dp(37) + namesOffset);
                docTitleLayout.draw(canvas);
                canvas.restore();
            }
            Drawable icon;
            Drawable phone;
            if (currentMessageObject.isOutOwner()) {
                icon = Theme.chat_msgCallUpGreenDrawable;
                phone = isDrawSelectionBackground() || otherPressed ? Theme.chat_msgOutCallSelectedDrawable : Theme.chat_msgOutCallDrawable;
            } else {
                TLRPC.PhoneCallDiscardReason reason = currentMessageObject.messageOwner.action.reason;
                if (reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed || reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) {
                    icon = Theme.chat_msgCallDownRedDrawable;
                } else {
                    icon = Theme.chat_msgCallDownGreenDrawable;
                }
                phone = isDrawSelectionBackground() || otherPressed ? Theme.chat_msgInCallSelectedDrawable : Theme.chat_msgInCallDrawable;
            }
            setDrawableBounds(icon, x - AndroidUtilities.dp(3), AndroidUtilities.dp(36) + namesOffset);
            icon.draw(canvas);

            setDrawableBounds(phone, x + AndroidUtilities.dp(205), otherY = AndroidUtilities.dp(22));
            phone.draw(canvas);
        } else if (currentMessageObject.type == MessageObject.TYPE_POLL) {
            if (currentMessageObject.isOutOwner()) {
                int color = Theme.getColor(Theme.key_chat_messageTextOut);
                Theme.chat_audioTitlePaint.setColor(color);
                Theme.chat_audioPerformerPaint.setColor(color);
                Theme.chat_instantViewPaint.setColor(color);
                color = Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outTimeSelectedText : Theme.key_chat_outTimeText);
                Theme.chat_timePaint.setColor(color);
                Theme.chat_livePaint.setColor(color);
            } else {
                int color = Theme.getColor(Theme.key_chat_messageTextIn);
                Theme.chat_audioTitlePaint.setColor(color);
                Theme.chat_audioPerformerPaint.setColor(color);
                Theme.chat_instantViewPaint.setColor(color);
                color = Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inTimeSelectedText : Theme.key_chat_inTimeText);
                Theme.chat_timePaint.setColor(color);
                Theme.chat_livePaint.setColor(color);
            }

            int x;
            if (currentMessageObject.isOutOwner()) {
                x = layoutWidth - backgroundWidth + AndroidUtilities.dp(11);
            } else {
                if (isChat && currentMessageObject.needDrawAvatar()) {
                    x = AndroidUtilities.dp(68);
                } else {
                    x = AndroidUtilities.dp(20);
                }
            }
            if (titleLayout != null) {
                canvas.save();
                canvas.translate(x, AndroidUtilities.dp(15) + namesOffset);
                titleLayout.draw(canvas);
                canvas.restore();
            }
            if (docTitleLayout != null) {
                canvas.save();
                canvas.translate(x + docTitleOffsetX, (titleLayout != null ? titleLayout.getHeight() : 0) + AndroidUtilities.dp(20) + namesOffset);
                docTitleLayout.draw(canvas);
                canvas.restore();
            }
            if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                selectorDrawableMaskType = 1;
                selectorDrawable.draw(canvas);
            }
            int lastVoteY = 0;
            for (int a = 0, N = pollButtons.size(); a < N; a++) {
                PollButton button = pollButtons.get(a);
                button.x = x;
                canvas.save();
                canvas.translate(x + AndroidUtilities.dp(34), button.y + namesOffset);
                button.title.draw(canvas);
                int alpha = (int) (animatePollAnswerAlpha ? 255 * Math.min((pollUnvoteInProgress ? 1.0f - pollAnimationProgress : pollAnimationProgress) / 0.3f, 1.0f) : 255);
                if (pollVoted || pollClosed || animatePollAnswerAlpha) {
                    Theme.chat_docBackPaint.setColor(Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill));
                    if (animatePollAnswerAlpha) {
                        float oldAlpha = Theme.chat_instantViewPaint.getAlpha() / 255.0f;
                        Theme.chat_instantViewPaint.setAlpha((int) (alpha * oldAlpha));
                        oldAlpha = Theme.chat_docBackPaint.getAlpha() / 255.0f;
                        Theme.chat_docBackPaint.setAlpha((int) (alpha * oldAlpha));
                    }

                    int currentPercent = (int) Math.ceil(button.prevPercent + (button.percent - button.prevPercent) * pollAnimationProgress);
                    String text = String.format("%d%%", currentPercent);
                    int width = (int) Math.ceil(Theme.chat_instantViewPaint.measureText(text));
                    canvas.drawText(text, -AndroidUtilities.dp(7) - width, AndroidUtilities.dp(14), Theme.chat_instantViewPaint);

                    width = backgroundWidth - AndroidUtilities.dp(76);
                    float currentPercentProgress = button.prevPercentProgress + (button.percentProgress - button.prevPercentProgress) * pollAnimationProgress;
                    instantButtonRect.set(0, button.height + AndroidUtilities.dp(6), width * currentPercentProgress, button.height + AndroidUtilities.dp(11));
                    canvas.drawRoundRect(instantButtonRect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.chat_docBackPaint);
                }

                if (!pollVoted && !pollClosed || animatePollAnswerAlpha) {
                    if (isDrawSelectionBackground()) {
                        Theme.chat_replyLinePaint.setColor(Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outVoiceSeekbarSelected : Theme.key_chat_inVoiceSeekbarSelected));
                    } else {
                        Theme.chat_replyLinePaint.setColor(Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outVoiceSeekbar : Theme.key_chat_inVoiceSeekbar));
                    }
                    if (animatePollAnswerAlpha) {
                        float oldAlpha = Theme.chat_replyLinePaint.getAlpha() / 255.0f;
                        Theme.chat_replyLinePaint.setAlpha((int) ((255 - alpha) * oldAlpha));
                    }
                    canvas.drawLine(-AndroidUtilities.dp(2), button.height + AndroidUtilities.dp(13), backgroundWidth - AndroidUtilities.dp(56), button.height + AndroidUtilities.dp(13), Theme.chat_replyLinePaint);
                    if (pollVoteInProgress && a == pollVoteInProgressNum) {
                        Theme.chat_instantViewRectPaint.setColor(Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill));
                        if (animatePollAnswerAlpha) {
                            float oldAlpha = Theme.chat_instantViewRectPaint.getAlpha() / 255.0f;
                            Theme.chat_instantViewRectPaint.setAlpha((int) ((255 - alpha) * oldAlpha));
                        }
                        instantButtonRect.set(-AndroidUtilities.dp(23) - AndroidUtilities.dp(8.5f), AndroidUtilities.dp(9) - AndroidUtilities.dp(8.5f), -AndroidUtilities.dp(23) + AndroidUtilities.dp(8.5f), AndroidUtilities.dp(9) + AndroidUtilities.dp(8.5f));
                        canvas.drawArc(instantButtonRect, voteRadOffset, voteCurrentCircleLength, false, Theme.chat_instantViewRectPaint);
                    } else {
                        if (currentMessageObject.isOutOwner()) {
                            Theme.chat_instantViewRectPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outMenuSelected : Theme.key_chat_outMenu));
                        } else {
                            Theme.chat_instantViewRectPaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inMenuSelected : Theme.key_chat_inMenu));
                        }
                        if (animatePollAnswerAlpha) {
                            float oldAlpha = Theme.chat_instantViewRectPaint.getAlpha() / 255.0f;
                            Theme.chat_instantViewRectPaint.setAlpha((int) ((255 - alpha) * oldAlpha));
                        }
                        canvas.drawCircle(-AndroidUtilities.dp(23), AndroidUtilities.dp(9), AndroidUtilities.dp(8.5f), Theme.chat_instantViewRectPaint);
                    }
                }
                canvas.restore();
                if (a == N - 1) {
                    lastVoteY = button.y + namesOffset + button.height;
                }
            }
            if (infoLayout != null) {
                canvas.save();
                canvas.translate(x + infoX, lastVoteY + AndroidUtilities.dp(22));
                infoLayout.draw(canvas);
                canvas.restore();
            }
            updatePollAnimations();
        } else if (currentMessageObject.type == 12) {
            if (currentMessageObject.isOutOwner()) {
                Theme.chat_contactNamePaint.setColor(Theme.getColor(Theme.key_chat_outContactNameText));
                Theme.chat_contactPhonePaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_outContactPhoneSelectedText : Theme.key_chat_outContactPhoneText));
            } else {
                Theme.chat_contactNamePaint.setColor(Theme.getColor(Theme.key_chat_inContactNameText));
                Theme.chat_contactPhonePaint.setColor(Theme.getColor(isDrawSelectionBackground() ? Theme.key_chat_inContactPhoneSelectedText : Theme.key_chat_inContactPhoneText));
            }
            if (titleLayout != null) {
                canvas.save();
                canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(9), AndroidUtilities.dp(16) + namesOffset);
                titleLayout.draw(canvas);
                canvas.restore();
            }
            if (docTitleLayout != null) {
                canvas.save();
                canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(9), AndroidUtilities.dp(39) + namesOffset);
                docTitleLayout.draw(canvas);
                canvas.restore();
            }

            Drawable menuDrawable;
            if (currentMessageObject.isOutOwner()) {
                menuDrawable = isDrawSelectionBackground() ? Theme.chat_msgOutMenuSelectedDrawable : Theme.chat_msgOutMenuDrawable;
            } else {
                menuDrawable = isDrawSelectionBackground() ? Theme.chat_msgInMenuSelectedDrawable : Theme.chat_msgInMenuDrawable;
            }
            setDrawableBounds(menuDrawable, otherX = photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(48), otherY = photoImage.getImageY() - AndroidUtilities.dp(5));
            menuDrawable.draw(canvas);

            if (drawInstantView) {
                int textX = photoImage.getImageX() - AndroidUtilities.dp(2);
                Drawable instantDrawable;
                int instantY = getMeasuredHeight() - AndroidUtilities.dp(36 + 28);
                Paint backPaint = Theme.chat_instantViewRectPaint;
                if (currentMessageObject.isOutOwner()) {
                    Theme.chat_instantViewPaint.setColor(Theme.getColor(Theme.key_chat_outPreviewInstantText));
                    backPaint.setColor(Theme.getColor(Theme.key_chat_outPreviewInstantText));
                } else {
                    Theme.chat_instantViewPaint.setColor(Theme.getColor(Theme.key_chat_inPreviewInstantText));
                    backPaint.setColor(Theme.getColor(Theme.key_chat_inPreviewInstantText));
                }

                if (Build.VERSION.SDK_INT >= 21) {
                    selectorDrawableMaskType = 0;
                    selectorDrawable.setBounds(textX, instantY, textX + instantWidth, instantY + AndroidUtilities.dp(36));
                    selectorDrawable.draw(canvas);
                }
                instantButtonRect.set(textX, instantY, textX + instantWidth, instantY + AndroidUtilities.dp(36));
                canvas.drawRoundRect(instantButtonRect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), backPaint);
                if (instantViewLayout != null) {
                    canvas.save();
                    canvas.translate(textX + instantTextX, instantY + AndroidUtilities.dp(10.5f));
                    instantViewLayout.draw(canvas);
                    canvas.restore();
                }
            }
        }

        if (drawImageButton && photoImage.getVisible()) {
            if (controlsAlpha != 1.0f) {
                radialProgress.setOverrideAlpha(controlsAlpha);
            }
            radialProgress.draw(canvas);
        }
        if ((drawVideoImageButton || animatingDrawVideoImageButton != 0) && photoImage.getVisible()) {
            if (controlsAlpha != 1.0f) {
                videoRadialProgress.setOverrideAlpha(controlsAlpha);
            }
            videoRadialProgress.draw(canvas);
        }
        if (drawPhotoCheckBox) {
            int size = AndroidUtilities.dp(21);
            photoCheckBox.setColor(null, null, currentMessageObject.isOutOwner() ? Theme.key_chat_outBubbleSelected : Theme.key_chat_inBubbleSelected);
            photoCheckBox.setBounds(photoImage.getImageX2() - AndroidUtilities.dp(21 + 4), photoImage.getImageY() + AndroidUtilities.dp(4), size, size);
            photoCheckBox.draw(canvas);
        }
    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    public MessageObject getMessageObject() {
        return messageObjectToSet != null ? messageObjectToSet : currentMessageObject;
    }

    public TLRPC.Document getStreamingMedia() {
        return documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF ? documentAttach : null;
    }

    public boolean isPinnedBottom() {
        return pinnedBottom;
    }

    public boolean isPinnedTop() {
        return pinnedTop;
    }

    public MessageObject.GroupedMessages getCurrentMessagesGroup() {
        return currentMessagesGroup;
    }

    public MessageObject.GroupedMessagePosition getCurrentPosition() {
        return currentPosition;
    }

    public int getLayoutHeight() {
        return layoutHeight;
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_CLICK) {
            int icon = getIconForCurrentState();
            if (icon != MediaActionDrawable.ICON_NONE) {
                didPressButton(true, false);
            } else if (currentMessageObject.type == 16) {
                delegate.didPressOther(this, otherX, otherY);
            } else {
                didClickedImage();
            }
            return true;
        } else if (action == R.id.acc_action_small_button) {
            didPressMiniButton(true);
        } else if (action == R.id.acc_action_msg_options) {
            if (delegate != null) {
                if (currentMessageObject.type == 16)
					delegate.didLongPress(this, 0, 0);
                else
                    delegate.didPressOther(this, otherX, otherY);
            }
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER || event.getAction() == MotionEvent.ACTION_HOVER_MOVE) {
            for (int i = 0; i < accessibilityVirtualViewBounds.size(); i++) {
                Rect rect = accessibilityVirtualViewBounds.valueAt(i);
                if (rect.contains(x, y)) {
                    int id = accessibilityVirtualViewBounds.keyAt(i);
                    if (id != currentFocusedVirtualView) {
                        currentFocusedVirtualView = id;
                        sendAccessibilityEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                    }
                    return true;
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
            currentFocusedVirtualView = 0;
        }
        return super.onHoverEvent(event);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return new MessageAccessibilityNodeProvider();
    }

    private void sendAccessibilityEventForVirtualView(int viewId, int eventType) {
        AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am.isTouchExplorationEnabled()) {
            AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
            event.setPackageName(getContext().getPackageName());
            event.setSource(ChatMessageCell.this, viewId);
            getParent().requestSendAccessibilityEvent(ChatMessageCell.this, event);
        }
    }

    private class MessageAccessibilityNodeProvider extends AccessibilityNodeProvider {

        private final int LINK_IDS_START = 2000;
        private final int BOT_BUTTONS_START = 1000;
        private final int POLL_BUTTONS_START = 500;
        private final int INSTANT_VIEW = 499;
        private final int SHARE = 498;
        private final int REPLY = 497;
        private Path linkPath = new Path();
        private RectF rectF = new RectF();
        private Rect rect = new Rect();

        @Override
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
            //FileLog.e("create node info "+virtualViewId);
            int[] pos = {0, 0};
            getLocationOnScreen(pos);
            if (virtualViewId == HOST_VIEW_ID) {
                AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(ChatMessageCell.this);
                onInitializeAccessibilityNodeInfo(info);
                StringBuilder sb = new StringBuilder();
                if (isChat && currentUser!=null && !currentMessageObject.isOut()) {
                    sb.append(UserObject.getUserName(currentUser));
                    sb.append('\n');
                }
                if (!TextUtils.isEmpty(currentMessageObject.messageText))
                    sb.append(currentMessageObject.messageText);
                if (currentMessageObject.isMusic()) {
                    sb.append("\n");
                    sb.append(LocaleController.formatString("AccDescrMusicInfo", R.string.AccDescrMusicInfo, currentMessageObject.getMusicAuthor(), currentMessageObject.getMusicTitle()));
                }else if(currentMessageObject.isVoice() || currentMessageObject.isRoundVideo()){
                    sb.append(", ");
                    sb.append(LocaleController.formatCallDuration(currentMessageObject.getDuration()));
                    if(currentMessageObject.isContentUnread()){
                        sb.append(", ");
                        sb.append(LocaleController.getString("AccDescrMsgNotPlayed", R.string.AccDescrMsgNotPlayed));
                    }
                }
                if (lastPoll != null) {
                    sb.append(", ");
                    sb.append(lastPoll.question);
                    sb.append(", ");
                    sb.append(LocaleController.getString("AnonymousPoll", R.string.AnonymousPoll));
                }
                if (currentMessageObject.messageOwner.media != null && !TextUtils.isEmpty(currentMessageObject.caption)) {
                    sb.append("\n");
                    sb.append(currentMessageObject.caption);
                }
                sb.append("\n");
                CharSequence time = LocaleController.getString("TodayAt", R.string.TodayAt) + " " + currentTimeString;
                if (currentMessageObject.isOut()) {
                    sb.append(LocaleController.formatString("AccDescrSentDate", R.string.AccDescrSentDate, time));
                    sb.append(", ");
                    sb.append(currentMessageObject.isUnread() ? LocaleController.getString("AccDescrMsgUnread", R.string.AccDescrMsgUnread) : LocaleController.getString("AccDescrMsgRead", R.string.AccDescrMsgRead));
                } else {
                    sb.append(LocaleController.formatString("AccDescrReceivedDate", R.string.AccDescrReceivedDate, time));
                }
                info.setContentDescription(sb.toString());
                info.setEnabled(true);
                if (Build.VERSION.SDK_INT >= 19) {
                    AccessibilityNodeInfo.CollectionItemInfo itemInfo = info.getCollectionItemInfo();
                    if (itemInfo != null) {
                        info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(itemInfo.getRowIndex(), 1, 0, 1, false));
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.acc_action_msg_options, LocaleController.getString("AccActionMessageOptions", R.string.AccActionMessageOptions)));
                    int icon = getIconForCurrentState();
                    CharSequence actionLabel = null;
                    switch (icon) {
                        case MediaActionDrawable.ICON_PLAY:
                            actionLabel = LocaleController.getString("AccActionPlay", R.string.AccActionPlay);
                            break;
                        case MediaActionDrawable.ICON_PAUSE:
                            actionLabel = LocaleController.getString("AccActionPause", R.string.AccActionPause);
                            break;
                        case MediaActionDrawable.ICON_FILE:
                            actionLabel = LocaleController.getString("AccActionOpenFile", R.string.AccActionOpenFile);
                            break;
                        case MediaActionDrawable.ICON_DOWNLOAD:
                            actionLabel = LocaleController.getString("AccActionDownload", R.string.AccActionDownload);
                            break;
                        case MediaActionDrawable.ICON_CANCEL:
                            actionLabel = LocaleController.getString("AccActionCancelDownload", R.string.AccActionCancelDownload);
                            break;
                        default:
                            if (currentMessageObject.type == 16) {
                                actionLabel = LocaleController.getString("CallAgain", R.string.CallAgain);
                            }
                    }
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, actionLabel));
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, LocaleController.getString("AccActionEnterSelectionMode", R.string.AccActionEnterSelectionMode)));
                    int smallIcon = getMiniIconForCurrentState();
                    if (smallIcon == MediaActionDrawable.ICON_DOWNLOAD) {
                        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.acc_action_small_button, LocaleController.getString("AccActionDownload", R.string.AccActionDownload)));
                    }
                } else {
                    info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                    info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
                }

                int i;
                if (currentMessageObject.messageText instanceof Spannable) {
                    Spannable buffer = (Spannable) currentMessageObject.messageText;
                    CharacterStyle[] links = buffer.getSpans(0, buffer.length(), ClickableSpan.class);
                    i = 0;
                    for (CharacterStyle link : links) {
                        info.addChild(ChatMessageCell.this, LINK_IDS_START + i);
                        i++;
                    }
                }
                i = 0;
                for (BotButton button : botButtons) {
                    info.addChild(ChatMessageCell.this, BOT_BUTTONS_START + i);
                    i++;
                }
                i = 0;
                for (PollButton button : pollButtons) {
                    info.addChild(ChatMessageCell.this, POLL_BUTTONS_START + i);
                    i++;
                }
                if (drawInstantView) {
                    info.addChild(ChatMessageCell.this, INSTANT_VIEW);
                }
                if (drawShareButton) {
                    info.addChild(ChatMessageCell.this, SHARE);
                }
                if (replyNameLayout != null) {
                    info.addChild(ChatMessageCell.this, REPLY);
                }
                if (drawSelectionBackground || getBackground() != null) {
                    info.setSelected(true);
                }
                return info;
            } else {
                AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
                info.setSource(ChatMessageCell.this, virtualViewId);
                info.setParent(ChatMessageCell.this);
                info.setPackageName(getContext().getPackageName());
                if (virtualViewId >= LINK_IDS_START) {
                    Spannable buffer = (Spannable) currentMessageObject.messageText;
                    ClickableSpan link = getLinkById(virtualViewId);
                    if (link == null) {
                        return null;
                    }
                    int[] linkPos = getRealSpanStartAndEnd(buffer, link);
                    String content = buffer.subSequence(linkPos[0], linkPos[1]).toString();
                    info.setText(content);
                    for (MessageObject.TextLayoutBlock block : currentMessageObject.textLayoutBlocks) {
                        int length = block.textLayout.getText().length();
                        if (block.charactersOffset <= linkPos[0] && block.charactersOffset + length >= linkPos[1]) {
                            block.textLayout.getSelectionPath(linkPos[0] - block.charactersOffset, linkPos[1] - block.charactersOffset, linkPath);
                            linkPath.computeBounds(rectF, true);
                            rect.set((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
                            rect.offset(0, (int) block.textYOffset);
                            rect.offset(textX, textY);
                            info.setBoundsInParent(rect);
                            if (accessibilityVirtualViewBounds.get(virtualViewId) == null) {
                                accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                            }
                            rect.offset(pos[0], pos[1]);
                            info.setBoundsInScreen(rect);
                            break;
                        }
                    }

                    info.setClassName("android.widget.TextView");
                    info.setEnabled(true);
                    info.setClickable(true);
                    info.setLongClickable(true);
                    info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                    info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
                } else if (virtualViewId >= BOT_BUTTONS_START) {
                    int buttonIndex = virtualViewId - BOT_BUTTONS_START;
                    if (buttonIndex >= botButtons.size()) {
                        return null;
                    }
                    BotButton button = botButtons.get(buttonIndex);
                    info.setText(button.title.getText());
                    info.setClassName("android.widget.Button");
                    info.setEnabled(true);
                    info.setClickable(true);
                    info.addAction(AccessibilityNodeInfo.ACTION_CLICK);

                    rect.set(button.x, button.y, button.x + button.width, button.y + button.height);
                    int addX;
                    if (currentMessageObject.isOutOwner()) {
                        addX = getMeasuredWidth() - widthForButtons - AndroidUtilities.dp(10);
                    } else {
                        addX = backgroundDrawableLeft + AndroidUtilities.dp(mediaBackground ? 1 : 7);
                    }
                    rect.offset(addX, layoutHeight);
                    info.setBoundsInParent(rect);
                    if (accessibilityVirtualViewBounds.get(virtualViewId) == null) {
                        accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                    }
                    rect.offset(pos[0], pos[1]);
                    info.setBoundsInScreen(rect);
                } else if (virtualViewId >= POLL_BUTTONS_START) {
                    int buttonIndex = virtualViewId - POLL_BUTTONS_START;
                    if (buttonIndex >= pollButtons.size()) {
                        return null;
                    }
                    PollButton button = pollButtons.get(buttonIndex);
                    info.setText(button.title.getText());
                    if (!pollVoted) {
                        info.setClassName("android.widget.Button");
                    } else {
                        info.setText(info.getText() + ", " + button.percent + "%");
                    }
                    info.setEnabled(true);
                    info.addAction(AccessibilityNodeInfo.ACTION_CLICK);

                    int width = backgroundWidth - AndroidUtilities.dp(76);
                    rect.set(button.x, button.y, button.x + width, button.y + button.height);
                    info.setBoundsInParent(rect);
                    if (accessibilityVirtualViewBounds.get(virtualViewId) == null) {
                        accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                    }
                    rect.offset(pos[0], pos[1]);
                    info.setBoundsInScreen(rect);

                    info.setClickable(true);
                } else if (virtualViewId == INSTANT_VIEW) {
                    info.setClassName("android.widget.Button");
                    info.setEnabled(true);
                    if (instantViewLayout != null) {
                        info.setText(instantViewLayout.getText());
                    }
                    info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                    int textX = photoImage.getImageX();
                    int instantY = getMeasuredHeight() - AndroidUtilities.dp(36 + 28);
                    int addX;
                    if (currentMessageObject.isOutOwner()) {
                        addX = getMeasuredWidth() - widthForButtons - AndroidUtilities.dp(10);
                    } else {
                        addX = backgroundDrawableLeft + AndroidUtilities.dp(mediaBackground ? 1 : 7);
                    }
                    rect.set(textX + addX, instantY, textX + instantWidth + addX, instantY + AndroidUtilities.dp(38));
                    info.setBoundsInParent(rect);
                    if (accessibilityVirtualViewBounds.get(virtualViewId) == null || !accessibilityVirtualViewBounds.get(virtualViewId).equals(rect)) {
                        accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                    }
                    rect.offset(pos[0], pos[1]);
                    info.setBoundsInScreen(rect);
                    info.setClickable(true);
                } else if (virtualViewId == SHARE) {
                    info.setClassName("android.widget.ImageButton");
                    info.setEnabled(true);
                    if (isOpenChatByShare(currentMessageObject)) {
                        info.setContentDescription(LocaleController.getString("AccDescrOpenChat", R.string.AccDescrOpenChat));
                    } else {
                        info.setContentDescription(LocaleController.getString("ShareFile", R.string.ShareFile));
                    }
                    info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                    // } else if (drawShareButton && x >= shareStartX && x <= shareStartX + AndroidUtilities.dp(40) && y >= shareStartY && y <= shareStartY + AndroidUtilities.dp(32)) {
                    rect.set(shareStartX, shareStartY, shareStartX + AndroidUtilities.dp(40), shareStartY + AndroidUtilities.dp(32));
                    info.setBoundsInParent(rect);
                    if (accessibilityVirtualViewBounds.get(virtualViewId) == null || !accessibilityVirtualViewBounds.get(virtualViewId).equals(rect)) {
                        accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                    }
                    rect.offset(pos[0], pos[1]);
                    info.setBoundsInScreen(rect);
                    info.setClickable(true);
                } else if (virtualViewId == REPLY) {
                    info.setEnabled(true);
                    StringBuilder sb = new StringBuilder();
                    sb.append(LocaleController.getString("Reply", R.string.Reply));
                    sb.append(", ");
                    if (replyNameLayout != null) {
                        sb.append(replyNameLayout.getText());
                        sb.append(", ");
                    }
                    if (replyTextLayout != null) {
                        sb.append(replyTextLayout.getText());
                    }
                    info.setContentDescription(sb.toString());
                    info.addAction(AccessibilityNodeInfo.ACTION_CLICK);

                    rect.set(replyStartX, replyStartY, replyStartX + Math.max(replyNameWidth, replyTextWidth), replyStartY + AndroidUtilities.dp(35));
                    info.setBoundsInParent(rect);
                    if (accessibilityVirtualViewBounds.get(virtualViewId) == null || !accessibilityVirtualViewBounds.get(virtualViewId).equals(rect)) {
                        accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                    }
                    rect.offset(pos[0], pos[1]);
                    info.setBoundsInScreen(rect);
                    info.setClickable(true);
                }
                info.setFocusable(true);
                info.setVisibleToUser(true);
                return info;
            }
        }

        @Override
        public boolean performAction(int virtualViewId, int action, Bundle arguments) {
            if (virtualViewId == HOST_VIEW_ID) {
                performAccessibilityAction(action, arguments);
            } else {
                if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                    sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                } else if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                    if (virtualViewId >= LINK_IDS_START) {
                        ClickableSpan link = getLinkById(virtualViewId);
                        if (link != null) {
                            delegate.didPressUrl(ChatMessageCell.this, link, false);
                            sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
                        }
                    } else if (virtualViewId >= BOT_BUTTONS_START) {
                        int buttonIndex = virtualViewId - BOT_BUTTONS_START;
                        if (buttonIndex >= botButtons.size()) {
                            return false;
                        }
                        BotButton button = botButtons.get(buttonIndex);
                        if (delegate != null) {
                            if (button.button != null) {
                                delegate.didPressBotButton(ChatMessageCell.this, button.button);
                            } else if (button.reaction != null) {
                                delegate.didPressReaction(ChatMessageCell.this, button.reaction);
                            }
                        }
                        sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
                    } else if (virtualViewId >= POLL_BUTTONS_START) {
                        int buttonIndex = virtualViewId - POLL_BUTTONS_START;
                        if (buttonIndex >= pollButtons.size()) {
                            return false;
                        }
                        PollButton button = pollButtons.get(buttonIndex);
                        if (delegate != null) {
                            delegate.didPressVoteButton(ChatMessageCell.this, button.answer);
                        }
                        sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
                    } else if (virtualViewId == INSTANT_VIEW) {
                        if (delegate != null) {
                            delegate.didPressInstantButton(ChatMessageCell.this, drawInstantViewType);
                        }
                    } else if (virtualViewId == SHARE) {
                        if (delegate != null) {
                            delegate.didPressShare(ChatMessageCell.this);
                        }
                    } else if (virtualViewId == REPLY) {
                        if (delegate != null) {
                            delegate.didPressReplyMessage(ChatMessageCell.this, currentMessageObject.messageOwner.reply_to_msg_id);
                        }
                    }
                } else if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
                    ClickableSpan link = getLinkById(virtualViewId);
                    if (link != null) {
                        delegate.didPressUrl(ChatMessageCell.this, link, true);
                        sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                    }
                }
            }
            return true;
        }

        private ClickableSpan getLinkById(int id) {
            id -= LINK_IDS_START;
            if (!(currentMessageObject.messageText instanceof Spannable)) {
                return null;
            }
            Spannable buffer = (Spannable) currentMessageObject.messageText;
            ClickableSpan[] links = buffer.getSpans(0, buffer.length(), ClickableSpan.class);
            if (links.length <= id) {
                return null;
            }
            return links[id];
        }
    }
}
