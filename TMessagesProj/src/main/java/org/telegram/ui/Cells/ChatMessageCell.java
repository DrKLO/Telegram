/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
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
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStructure;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLoader;
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
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.RoundVideoPlayingDrawable;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.SeekBarWaveform;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.SecretMediaViewer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class ChatMessageCell extends BaseCell implements SeekBar.SeekBarDelegate, ImageReceiver.ImageReceiverDelegate, DownloadController.FileDownloadProgressListener {

    public interface ChatMessageCellDelegate {
        void didPressedUserAvatar(ChatMessageCell cell, TLRPC.User user);
        void didPressedViaBot(ChatMessageCell cell, String username);
        void didPressedChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId);
        void didPressedCancelSendButton(ChatMessageCell cell);
        void didLongPressed(ChatMessageCell cell);
        void didPressedReplyMessage(ChatMessageCell cell, int id);
        void didPressedUrl(MessageObject messageObject, CharacterStyle url, boolean longPress);
        void needOpenWebView(String url, String title, String description, String originalUrl, int w, int h);
        void didPressedImage(ChatMessageCell cell);
        void didPressedShare(ChatMessageCell cell);
        void didPressedOther(ChatMessageCell cell);
        void didPressedBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button);
        void didPressedInstantButton(ChatMessageCell cell, int type);
        boolean isChatAdminCell(int uid);
        boolean needPlayMessage(MessageObject messageObject);
        boolean canPerformActions();
    }

    private final static int DOCUMENT_ATTACH_TYPE_NONE = 0;
    private final static int DOCUMENT_ATTACH_TYPE_DOCUMENT = 1;
    private final static int DOCUMENT_ATTACH_TYPE_GIF = 2;
    private final static int DOCUMENT_ATTACH_TYPE_AUDIO = 3;
    private final static int DOCUMENT_ATTACH_TYPE_VIDEO = 4;
    private final static int DOCUMENT_ATTACH_TYPE_MUSIC = 5;
    private final static int DOCUMENT_ATTACH_TYPE_STICKER = 6;
    private final static int DOCUMENT_ATTACH_TYPE_ROUND = 7;

    private class BotButton {
        private int x;
        private int y;
        private int width;
        private int height;
        private StaticLayout title;
        private TLRPC.KeyboardButton button;
        private int angle;
        private float progressAlpha;
        private long lastUpdateTime;
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

    private RadialProgress radialProgress;
    private boolean drawRadialCheckBackground;
    private ImageReceiver photoImage;
    private AvatarDrawable contactAvatarDrawable;

    private boolean disallowLongPress;

    private boolean isSmallImage;
    private boolean drawImageButton;
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
    private boolean drawJoinGroupView;
    private boolean drawJoinChannelView;
    private int instantTextX;
    private int instantTextLeftX;
    private int instantWidth;
    private boolean instantPressed;
    private boolean instantButtonPressed;
    private Drawable instantViewSelectorDrawable;
    private RectF instantButtonRect = new RectF();
    private int pressedState[] = new int[] {android.R.attr.state_enabled, android.R.attr.state_pressed};

    private RoundVideoPlayingDrawable roundVideoPlayingDrawable;

    private StaticLayout docTitleLayout;
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
    private int infoWidth;

    private String currentUrl;
    private WebFile currentWebFile;
    private boolean addedForTest;

    private int buttonX;
    private int buttonY;
    private int buttonState;
    private int buttonPressed;
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

    private ArrayList<BotButton> botButtons = new ArrayList<>();
    private HashMap<String, BotButton> botButtonsByData = new HashMap<>();
    private HashMap<String, BotButton> botButtonsByPosition = new HashMap<>();
    private String botButtonsLayout;
    private int widthForButtons;
    private int pressedBotButton;

    //
    private int TAG;
    private int currentAccount = UserConfig.selectedAccount;

    public boolean isChat;
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
    private MessageObject currentMessageObject;
    private int viaWidth;
    private int viaNameWidth;
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
    private TLRPC.FileLocation currentReplyPhoto;

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
    private float forwardNameOffsetX[] = new float[2];

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
        radialProgress = new RadialProgress(this);
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
        if (text) {
            urlPathSelection.add(linkPath);
        } else {
            urlPath.add(linkPath);
        }
        return linkPath;
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
                                    int start = buffer.getSpanStart(pressedLink);
                                    int end = buffer.getSpanEnd(pressedLink);
                                    path.setCurrentLayout(block.textLayout, start, 0);
                                    block.textLayout.getSelectionPath(start, end, path);
                                    if (end >= block.charactersEnd) {
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
                                            nextBlock.textLayout.getSelectionPath(0, end, path);
                                            if (end < nextBlock.charactersEnd - 1) {
                                                break;
                                            }
                                        }
                                    }
                                    if (start <= block.charactersOffset) {
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
                                            start = buffer.getSpanStart(pressedLink);
                                            offsetY -= nextBlock.height;
                                            path.setCurrentLayout(nextBlock.textLayout, start, offsetY);
                                            nextBlock.textLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink), path);
                                            if (start > nextBlock.charactersOffset) {
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
                                    delegate.didPressedUrl(currentMessageObject, pressedLink, false);
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
                                    int start = buffer.getSpanStart(pressedLink);
                                    path.setCurrentLayout(captionLayout, start, 0);
                                    captionLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink), path);
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
                    delegate.didPressedUrl(currentMessageObject, pressedLink, false);
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
            if (drawPhotoImage && photoImage.isInsideImage(x, y)) {
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
                                int start = buffer.getSpanStart(pressedLink);
                                path.setCurrentLayout(descriptionLayout, start, 0);
                                descriptionLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink), path);
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
            if (pressedLinkType == 2 || gamePreviewPressed) {
                if (pressedLink != null) {
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
                            delegate.didPressedBotButton(this, button.button);
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
                                        int start = buffer.getSpanStart(pressedLink);
                                        path.setCurrentLayout(descriptionLayout, start, 0);
                                        descriptionLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink), path);
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
                    } else if (drawPhotoImage && drawImageButton && buttonState != -1 && (photoImage.isInsideImage(x, y) || x >= buttonX && x <= buttonX + AndroidUtilities.dp(48) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48))) {
                        buttonPressed = 1;
                        return true;
                    } else if (drawInstantView) {
                        instantPressed = true;
                        if (Build.VERSION.SDK_INT >= 21 && instantViewSelectorDrawable != null) {
                            if (instantViewSelectorDrawable.getBounds().contains(x, y)) {
                                instantViewSelectorDrawable.setState(pressedState);
                                instantViewSelectorDrawable.setHotspot(x, y);
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
                        delegate.didPressedInstantButton(this, drawInstantViewType);
                    }
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (Build.VERSION.SDK_INT >= 21 && instantViewSelectorDrawable != null) {
                        instantViewSelectorDrawable.setState(StateSet.NOTHING);
                    }
                    instantPressed = instantButtonPressed = false;
                    invalidate();
                } else if (pressedLinkType == 2 || buttonPressed != 0 || miniButtonPressed != 0 || linkPreviewPressed) {
                    if (buttonPressed != 0) {
                        buttonPressed = 0;
                        playSoundEffect(SoundEffectConstants.CLICK);
                        didPressedButton(false);
                        invalidate();
                    } else if (miniButtonPressed != 0) {
                        miniButtonPressed = 0;
                        playSoundEffect(SoundEffectConstants.CLICK);
                        didPressedMiniButton(false);
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
                                    delegate.didPressedImage(this);
                                } else {
                                    buttonState = 2;
                                    currentMessageObject.gifState = 1;
                                    photoImage.setAllowStartAnimation(false);
                                    photoImage.stopAnimation();
                                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                                    invalidate();
                                    playSoundEffect(SoundEffectConstants.CLICK);
                                }
                            } else if (buttonState == 2 || buttonState == 0) {
                                didPressedButton(false);
                                playSoundEffect(SoundEffectConstants.CLICK);
                            }
                        } else {
                            TLRPC.WebPage webPage = currentMessageObject.messageOwner.media.webpage;
                            if (webPage != null && !TextUtils.isEmpty(webPage.embed_url)) {
                                delegate.needOpenWebView(webPage.embed_url, webPage.site_name, webPage.title, webPage.url, webPage.embed_width, webPage.embed_height);
                            } else if (buttonState == -1 || buttonState == 3) {
                                delegate.didPressedImage(this);
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
                if (instantButtonPressed && Build.VERSION.SDK_INT >= 21 && instantViewSelectorDrawable != null) {
                    instantViewSelectorDrawable.setHotspot(x, y);
                }
            }
        }
        return false;
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
                if (Build.VERSION.SDK_INT >= 21 && instantViewSelectorDrawable != null) {
                    if (instantViewSelectorDrawable.getBounds().contains(x, y)) {
                        instantViewSelectorDrawable.setState(pressedState);
                        instantViewSelectorDrawable.setHotspot(x, y);
                        instantButtonPressed = true;
                    }
                }
                invalidate();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (instantPressed) {
                if (delegate != null) {
                    delegate.didPressedInstantButton(this, drawInstantViewType);
                }
                playSoundEffect(SoundEffectConstants.CLICK);
                if (Build.VERSION.SDK_INT >= 21 && instantViewSelectorDrawable != null) {
                    instantViewSelectorDrawable.setState(StateSet.NOTHING);
                }
                instantPressed = instantButtonPressed = false;
                invalidate();
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (instantButtonPressed && Build.VERSION.SDK_INT >= 21 && instantViewSelectorDrawable != null) {
                instantViewSelectorDrawable.setHotspot(x, y);
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
                    delegate.didPressedOther(this);
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
            } else if (buttonState != -1 && x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side) {
                buttonPressed = 1;
                invalidate();
                result = true;
            } else {
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                    if (x >= photoImage.getImageX() && x <= photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(50) && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight()) {
                        imagePressed = true;
                        result = true;
                    }
                } else if (currentMessageObject.type != 13 || currentMessageObject.getInputStickerSet() != null) {
                    if (x >= photoImage.getImageX() && x <= photoImage.getImageX() + backgroundWidth && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight()) {
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
                } else if (currentMessageObject.type == 5 && buttonState != -1) {
                    imagePressed = false;
                    result = false;
                }
            }
        } else {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (buttonPressed == 1) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton(false);
                    updateRadialProgressBackground();
                    invalidate();
                } else if (miniButtonPressed == 1) {
                    miniButtonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedMiniButton(false);
                    invalidate();
                } else if (imagePressed) {
                    imagePressed = false;
                    if (buttonState == -1 || buttonState == 2 || buttonState == 3) {
                        playSoundEffect(SoundEffectConstants.CLICK);
                        didClickedImage();
                    } else if (buttonState == 0 && documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        playSoundEffect(SoundEffectConstants.CLICK);
                        didPressedButton(false);
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
                didPressedButton(true);
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
                    updateRadialProgressBackground();
                }
            } else if (buttonPressed != 0) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton(true);
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
                updateRadialProgressBackground();
            } else if (miniButtonPressed != 0) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    miniButtonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedMiniButton(true);
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
                updateRadialProgressBackground();
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
                    delegate.didPressedBotButton(this, botButtons.get(pressedBotButton).button);
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

        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            buttonPressed = 0;
            miniButtonPressed = 0;
            pressedBotButton = -1;
            linkPreviewPressed = false;
            otherPressed = false;
            imagePressed = false;
            gamePreviewPressed = false;
            instantPressed = instantButtonPressed = false;
            if (Build.VERSION.SDK_INT >= 21 && instantViewSelectorDrawable != null) {
                instantViewSelectorDrawable.setState(StateSet.NOTHING);
            }
            result = false;
            resetPressedLink(-1);
        }
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
                        if (currentMessageObject.type == 13 || currentMessageObject.type == 5) {
                            replyEnd = replyStartX + Math.max(replyNameWidth, replyTextWidth);
                        } else {
                            replyEnd = replyStartX + backgroundDrawableRight;
                        }
                        if (x >= replyStartX && x <= replyEnd && y >= replyStartY && y <= replyStartY + AndroidUtilities.dp(35)){
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
                                delegate.didPressedUserAvatar(this, currentUser);
                            } else if (currentChat != null) {
                                delegate.didPressedChannelAvatar(this, currentChat, 0);
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
                                delegate.didPressedChannelAvatar(this, currentForwardChannel, currentMessageObject.messageOwner.fwd_from.channel_post);
                            } else if (currentForwardUser != null) {
                                delegate.didPressedUserAvatar(this, currentForwardUser);
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
                            delegate.didPressedViaBot(this, currentViaBotUser != null ? currentViaBotUser.username : currentMessageObject.messageOwner.via_bot_name);
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
                            delegate.didPressedReplyMessage(this, currentMessageObject.messageOwner.reply_to_msg_id);
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                        replyPressed = false;
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        int replyEnd;
                        if (currentMessageObject.type == 13 || currentMessageObject.type == 5) {
                            replyEnd = replyStartX + Math.max(replyNameWidth, replyTextWidth);
                        } else {
                            replyEnd = replyStartX + backgroundDrawableRight;
                        }
                        if (!(x >= replyStartX && x <= replyEnd && y >= replyStartY && y <= replyStartY + AndroidUtilities.dp(35))){
                            replyPressed = false;
                        }
                    }
                } else if (sharePressed) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        sharePressed = false;
                        playSoundEffect(SoundEffectConstants.CLICK);
                        if (delegate != null) {
                            delegate.didPressedShare(this);
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

        if (currentMessageObject.isRoundVideo()) {
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
                if (!currentMessageObject.mediaExists && !currentMessageObject.attachPathExists) {
                    currentMessageObject.mediaExists = true;
                    updateButtonState(true, false);
                }
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

    public void downloadAudioIfNeed() {
        if (documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO) {
            return;
        }
        if (buttonState == 2) {
            FileLoader.getInstance(currentAccount).loadFile(documentAttach, true, 0);
            buttonState = 4;
            radialProgress.setBackground(getDrawableForCurrentState(), false, false);
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
        return StaticLayoutEx.createStaticLayout(stringBuilder, paint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false, TextUtils.TruncateAt.END, maxWidth, maxLines);
    }

    private void didClickedImage() {
        if (currentMessageObject.type == 1 || currentMessageObject.type == 13) {
            if (buttonState == -1) {
                delegate.didPressedImage(this);
            } else if (buttonState == 0) {
                didPressedButton(false);
            }
        } else if (currentMessageObject.type == 12) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(currentMessageObject.messageOwner.media.user_id);
            delegate.didPressedUserAvatar(this, user);
        } else if (currentMessageObject.type == 5) {
            if (!MediaController.getInstance().isPlayingMessage(currentMessageObject) || MediaController.getInstance().isMessagePaused()) {
                delegate.needPlayMessage(currentMessageObject);
            } else {
                MediaController.getInstance().pauseMessage(currentMessageObject);
            }
        } else if (currentMessageObject.type == 8) {
            if (buttonState == -1) {
                if (SharedConfig.autoplayGifs) {
                    delegate.didPressedImage(this);
                } else {
                    buttonState = 2;
                    currentMessageObject.gifState = 1;
                    photoImage.setAllowStartAnimation(false);
                    photoImage.stopAnimation();
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            } else if (buttonState == 2 || buttonState == 0) {
                didPressedButton(false);
            }
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
            if (buttonState == -1) {
                delegate.didPressedImage(this);
            } else if (buttonState == 0 || buttonState == 3) {
                didPressedButton(false);
            }
        } else if (currentMessageObject.type == 4) {
            delegate.didPressedImage(this);
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
            if (buttonState == -1) {
                delegate.didPressedImage(this);
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
                delegate.didPressedImage(this);
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
            return object.type == 1 || object.type == 5 || object.type == 3 || object.type == 8 || object.type == 13;
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

        updateCurrentUserAndChat();
        TLRPC.FileLocation newPhoto = null;

        if (isAvatarVisible) {
            if (currentUser != null && currentUser.photo != null){
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

        TLRPC.FileLocation newReplyPhoto = null;

        if (replyNameLayout != null) {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(currentMessageObject.replyMessageObject.photoThumbs, 80);
            if (photoSize != null && currentMessageObject.replyMessageObject.type != 13) {
                newReplyPhoto = photoSize.location;
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

        if (drawForwardedName) {
            newNameString = currentMessageObject.getForwardedName();
            return currentForwardNameString == null && newNameString != null || currentForwardNameString != null && newNameString == null || currentForwardNameString != null && newNameString != null && !currentForwardNameString.equals(newNameString);
        }
        return false;
    }

    public ImageReceiver getPhotoImage() {
        return photoImage;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
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
        setTranslationX(0);
        avatarImage.onAttachedToWindow();
        avatarImage.setParentView((View) getParent());
        replyImageReceiver.onAttachedToWindow();
        locationImageReceiver.onAttachedToWindow();
        if (photoImage.onAttachedToWindow()) {
            if (drawPhotoImage) {
                updateButtonState(false, false);
            }
        } else {
            updateButtonState(false, false);
        }
        if (currentMessageObject != null && currentMessageObject.isRoundVideo()) {
            checkRoundVideoPlayback(true);
        }
    }

    public void checkRoundVideoPlayback(boolean allowStart) {
        if (allowStart) {
            allowStart = MediaController.getInstance().getPlayingMessageObject() == null;
        }
        photoImage.setAllowStartAnimation(allowStart);
        if (allowStart) {
            photoImage.startAnimation();
        } else {
            photoImage.stopAnimation();
        }
    }

    @Override
    protected void onLongPress() {
        if (pressedLink instanceof URLSpanMono) {
            delegate.didPressedUrl(currentMessageObject, pressedLink, true);
            return;
        } else if (pressedLink instanceof URLSpanNoUnderline) {
            URLSpanNoUnderline url = (URLSpanNoUnderline) pressedLink;
            if (url.getURL().startsWith("/")) {
                delegate.didPressedUrl(currentMessageObject, pressedLink, true);
                return;
            }
        } else if (pressedLink instanceof URLSpan) {
            delegate.didPressedUrl(currentMessageObject, pressedLink, true);
            return;
        }
        resetPressedLink(-1);
        if (buttonPressed != 0 || miniButtonPressed != 0 || pressedBotButton != -1) {
            buttonPressed = 0;
            miniButtonState = 0;
            pressedBotButton = -1;
            invalidate();
        }
        if (instantPressed) {
            instantPressed = instantButtonPressed = false;
            if (Build.VERSION.SDK_INT >= 21 && instantViewSelectorDrawable != null) {
                instantViewSelectorDrawable.setState(StateSet.NOTHING);
            }
            invalidate();
        }
        if (delegate != null) {
            delegate.didLongPressed(this);
        }
    }

    public void setCheckPressed(boolean value, boolean pressed) {
        isCheckPressed = value;
        isPressed = pressed;
        updateRadialProgressBackground();
        if (useSeekBarWaweform) {
            seekBarWaveform.setSelected(isDrawSelectedBackground());
        } else {
            seekBar.setSelected(isDrawSelectedBackground());
        }
        invalidate();
    }

    public void setHighlightedAnimated() {
        isHighlightedAnimated = true;
        highlightProgress = 1000;
        lastHighlightProgressTime = System.currentTimeMillis();
        invalidate();
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
            seekBarWaveform.setSelected(isDrawSelectedBackground());
        } else {
            seekBar.setSelected(isDrawSelectedBackground());
        }
        invalidate();
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        updateRadialProgressBackground();
        if (useSeekBarWaweform) {
            seekBarWaveform.setSelected(isDrawSelectedBackground());
        } else {
            seekBar.setSelected(isDrawSelectedBackground());
        }
        invalidate();
    }

    private void updateRadialProgressBackground() {
        if (drawRadialCheckBackground) {
            return;
        }
        radialProgress.swapBackground(getDrawableForCurrentState());
        if (hasMiniProgress != 0) {
            radialProgress.swapMiniBackground(getMiniDrawableForCurrentState());
        }
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
            documentAttach = messageObject.messageOwner.media.document;
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
                int duration = 0;
                for (int a = 0; a < documentAttach.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = documentAttach.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                        duration = attribute.duration;
                        break;
                    }
                }
                int minutes = duration / 60;
                int seconds = duration - minutes * 60;
                String str = String.format("%d:%02d, %s", minutes, seconds, AndroidUtilities.formatFileSize(documentAttach.size));
                infoWidth = (int) Math.ceil(Theme.chat_infoPaint.measureText(str));
                infoLayout = new StaticLayout(str, Theme.chat_infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
            return 0;
        } else {
            drawPhotoImage = documentAttach.mime_type != null && documentAttach.mime_type.toLowerCase().startsWith("image/") || documentAttach.thumb != null && !(documentAttach.thumb instanceof TLRPC.TL_photoSizeEmpty) && !(documentAttach.thumb.location instanceof TLRPC.TL_fileLocationUnavailable);
            if (!drawPhotoImage) {
                maxWidth += AndroidUtilities.dp(30);
            }
            documentAttachType = DOCUMENT_ATTACH_TYPE_DOCUMENT;
            String name = FileLoader.getDocumentFileName(documentAttach);
            if (name == null || name.length() == 0) {
                name = LocaleController.getString("AttachDocument", R.string.AttachDocument);
            }
            docTitleLayout = StaticLayoutEx.createStaticLayout(name, Theme.chat_docNamePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.MIDDLE, maxWidth, drawPhotoImage ? 2 : 1);
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
                infoLayout = new StaticLayout(str2, Theme.chat_infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (drawPhotoImage) {
                currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
                photoImage.setNeedsQualityThumb(true);
                photoImage.setShouldGenerateQualityThumb(true);
                photoImage.setParentMessageObject(messageObject);
                if (currentPhotoObject != null) {
                    currentPhotoFilter = "86_86_b";
                    photoImage.setImage(null, null, null, null, currentPhotoObject.location, currentPhotoFilter, 0, null, 1);
                } else {
                    photoImage.setImageBitmap((BitmapDrawable) null);
                }
            }
            return width;
        }
    }

    private void calcBackgroundWidth(int maxWidth, int timeMore, int maxChildWidth) {
        if (hasLinkPreview || hasOldCaptionPreview || hasGamePreview || hasInvoicePreview || maxWidth - currentMessageObject.lastLineWidth < timeMore || currentMessageObject.hasRtl) {
            totalHeight += AndroidUtilities.dp(14);
            hasNewLineForTime = true;
            backgroundWidth = Math.max(maxChildWidth, currentMessageObject.lastLineWidth) + AndroidUtilities.dp(31);
            backgroundWidth = Math.max(backgroundWidth, (currentMessageObject.isOutOwner() ? timeWidth + AndroidUtilities.dp(17) : timeWidth)+ AndroidUtilities.dp(31));
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
        if (currentMessageObject.messageOwner.message == null || currentMessageObject == null || currentMessageObject.type != 0 || TextUtils.isEmpty(currentMessageObject.messageText) || text == null) {
            if (!urlPathSelection.isEmpty()) {
                linkSelectionBlockNum = -1;
                resetUrlPaths(true);
                invalidate();
            }
            return;
        }
        int start = TextUtils.indexOf(currentMessageObject.messageOwner.message.toLowerCase(), text.toLowerCase());
        if (start == -1) {
            if (!urlPathSelection.isEmpty()) {
                linkSelectionBlockNum = -1;
                resetUrlPaths(true);
                invalidate();
            }
            return;
        }
        int end = start + text.length();
        for (int c = 0; c < currentMessageObject.textLayoutBlocks.size(); c++) {
            MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(c);
            if (start >= block.charactersOffset && start < block.charactersOffset + block.textLayout.getText().length()) {
                linkSelectionBlockNum = c;
                resetUrlPaths(true);
                try {
                    LinkPath path = obtainNewUrlPath(true);
                    int length = block.textLayout.getText().length();
                    path.setCurrentLayout(block.textLayout, start, 0);
                    block.textLayout.getSelectionPath(start, end - block.charactersOffset, path);
                    if (end >= block.charactersOffset + length) {
                        for (int a = c + 1; a < currentMessageObject.textLayoutBlocks.size(); a++) {
                            MessageObject.TextLayoutBlock nextBlock = currentMessageObject.textLayoutBlocks.get(a);
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

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == instantViewSelectorDrawable;
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
                docTitleLayout = new StaticLayout(LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation), Theme.chat_locationTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            } else {
                MessageObject messageObject = currentMessageObject;
                currentMessageObject = null;
                setMessageObject(messageObject, currentMessagesGroup, pinnedBottom, pinnedTop);
            }
        }
    }

    public void setMessageObject(MessageObject messageObject, MessageObject.GroupedMessages groupedMessages, boolean bottomNear, boolean topNear) {
        if (messageObject.checkLayout() || currentPosition != null && lastHeight != AndroidUtilities.displaySize.y) {
            currentMessageObject = null;
        }
        boolean messageIdChanged = currentMessageObject == null || currentMessageObject.getId() != messageObject.getId();
        boolean messageChanged = currentMessageObject != messageObject || messageObject.forceUpdate;
        boolean dataChanged = currentMessageObject != null && currentMessageObject.getId() == messageObject.getId() && lastSendState == MessageObject.MESSAGE_SEND_STATE_EDITING && messageObject.isSent()
                || currentMessageObject == messageObject && (isUserDataChanged() || photoNotSet);
        boolean groupChanged = groupedMessages != currentMessagesGroup;
        if (!groupChanged && groupedMessages != null) {
            MessageObject.GroupedMessagePosition newPosition;
            if (groupedMessages.messages.size() > 1) {
                newPosition = currentMessagesGroup.positions.get(currentMessageObject);
            } else {
                newPosition = null;
            }
            groupChanged = newPosition != currentPosition;
        }
        if (messageChanged || dataChanged || groupChanged || isPhotoDataChanged(messageObject) || pinnedBottom != bottomNear || pinnedTop != topNear) {
            pinnedBottom = bottomNear;
            pinnedTop = topNear;
            lastTime = -2;
            isHighlightedAnimated = false;
            widthBeforeNewTimeLine = -1;
            currentMessageObject = messageObject;
            currentMessagesGroup = groupedMessages;
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
            isCheckPressed = true;
            hasNewLineForTime = false;
            isAvatarVisible = isChat && !messageObject.isOutOwner() && messageObject.needDrawAvatar() && (currentPosition == null || currentPosition.edge);
            wasLayout = false;
            drwaShareGoIcon = false;
            groupPhotoInvisible = false;
            drawShareButton = checkNeedDrawShareButton(messageObject);
            replyNameLayout = null;
            adminLayout = null;
            replyTextLayout = null;
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
            hasLinkPreview = false;
            hasOldCaptionPreview = false;
            hasGamePreview = false;
            hasInvoicePreview = false;
            instantPressed = instantButtonPressed = false;
            if (Build.VERSION.SDK_INT >= 21 && instantViewSelectorDrawable != null) {
                instantViewSelectorDrawable.setVisible(false, false);
                instantViewSelectorDrawable.setState(StateSet.NOTHING);
            }
            linkPreviewPressed = false;
            buttonPressed = 0;
            miniButtonPressed = 0;
            pressedBotButton = -1;
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
            currentPhotoObject = null;
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
            mediaBackground = false;
            int captionNewLine = 0;
            availableTimeWidth = 0;
            photoImage.setForceLoading(false);
            photoImage.setNeedsQualityThumb(false);
            photoImage.setShouldGenerateQualityThumb(false);
            photoImage.setAllowDecodeSingleFrame(false);
            photoImage.setParentMessageObject(null);
            photoImage.setRoundRadius(AndroidUtilities.dp(3));

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
                boolean slideshow = false;
                String siteName = hasLinkPreview ? messageObject.messageOwner.media.webpage.site_name : null;
                String webpageType = hasLinkPreview ? messageObject.messageOwner.media.webpage.type : null;
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
                    } /*else if ("telegram_proxy".equals(webpageType)) {
                        drawInstantView = true;
                        drawInstantViewType = 4;
                    }*/
                } else if (siteName != null) {
                    siteName = siteName.toLowerCase();
                    if ((siteName.equals("instagram") || siteName.equals("twitter") || "telegram_album".equals(webpageType)) && messageObject.messageOwner.media.webpage.cached_page instanceof TLRPC.TL_pageFull &&
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
                        if (isChat && messageObject.needDrawAvatar() && !currentMessageObject.isOut()) {
                            linkPreviewMaxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(132);
                        } else {
                            linkPreviewMaxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
                        }
                    } else {
                        if (isChat && messageObject.needDrawAvatar() && !currentMessageObject.isOutOwner()) {
                            linkPreviewMaxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(132);
                        } else {
                            linkPreviewMaxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(80);
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
                        title = webPage.title;
                        author = webPage.author;
                        description = webPage.description;
                        photo = webPage.photo;
                        webDocument = null;
                        document = webPage.document;
                        type = webPage.type;
                        duration = webPage.duration;
                        if (site_name != null && photo != null && site_name.toLowerCase().equals("instagram")) {
                            linkPreviewMaxWidth = Math.max(AndroidUtilities.displaySize.y / 3, currentMessageObject.textWidth);
                        }
                        smallImage = !slideshow && !drawInstantView && document == null && type != null && (type.equals("app") || type.equals("profile") || type.equals("article"));
                        isSmallImage = !slideshow && !drawInstantView && document == null && description != null && type != null && (type.equals("app") || type.equals("profile") || type.equals("article")) && currentMessageObject.photoThumbs != null;
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
                            boolean allowAllLines = site_name.toLowerCase().equals("twitter");
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
                            currentPhotoObject = document.thumb;
                            documentAttach = document;
                            documentAttachType = DOCUMENT_ATTACH_TYPE_ROUND;
                        } else if (MessageObject.isGifDocument(document)) {
                            if (!SharedConfig.autoplayGifs) {
                                messageObject.gifState = 1;
                            }
                            photoImage.setAllowStartAnimation(messageObject.gifState != 1);
                            currentPhotoObject = document.thumb;
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
                            currentPhotoObject = document.thumb;
                            if (currentPhotoObject != null && (currentPhotoObject.w == 0 || currentPhotoObject.h == 0)) {
                                for (int a = 0; a < document.attributes.size(); a++) {
                                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                                    if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                        currentPhotoObject.w = attribute.w;
                                        currentPhotoObject.h = attribute.h;
                                        break;
                                    }
                                }
                                if (currentPhotoObject.w == 0 || currentPhotoObject.h == 0) {
                                    currentPhotoObject.w = currentPhotoObject.h = AndroidUtilities.dp(150);
                                }
                            }
                            createDocumentLayout(0, messageObject);
                        } else if (MessageObject.isStickerDocument(document)) {
                            currentPhotoObject = document.thumb;
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
                        } else {
                            calcBackgroundWidth(maxWidth, timeMore, maxChildWidth);
                            if (!MessageObject.isStickerDocument(document)) {
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
                                    }
                                }
                            }
                        }
                    } else if (photo != null) {
                        drawImageButton = type != null && type.equals("photo");
                        currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, drawImageButton ? AndroidUtilities.getPhotoSize() : maxPhotoWidth, !drawImageButton);
                        currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
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
                            drawImageButton = type != null && (type.equals("photo") || type.equals("document") && documentAttachType != DOCUMENT_ATTACH_TYPE_STICKER || type.equals("gif") || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO);
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += AndroidUtilities.dp(2);
                                totalHeight += AndroidUtilities.dp(2);
                            }

                            if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER) {
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

                            photoImage.setImageCoords(0, 0, width, height);

                            currentPhotoFilter = String.format(Locale.US, "%d_%d", width, height);
                            currentPhotoFilterThumb = String.format(Locale.US, "%d_%d_b", width, height);

                            if (webDocument != null) {
                                photoImage.setImage(webDocument, null, currentPhotoFilter, null, null, "b1", webDocument.size, null, 1);
                            } else {
                                if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER) {
                                    photoImage.setImage(documentAttach, null, currentPhotoFilter, null, currentPhotoObject != null ? currentPhotoObject.location : null, "b1", documentAttach.size, "webp", 1);
                                } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                                    photoImage.setNeedsQualityThumb(true);
                                    photoImage.setShouldGenerateQualityThumb(true);
                                    photoImage.setParentMessageObject(messageObject);
                                    photoImage.setImage(null, null, currentPhotoObject.location, currentPhotoFilter, 0, null, 0);
                                } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
                                    String fileName = FileLoader.getAttachFileName(document);
                                    boolean autoDownload = false;
                                    if (MessageObject.isRoundVideoDocument(document)) {
                                        photoImage.setRoundRadius(AndroidUtilities.roundMessageSize / 2);
                                        autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject);
                                    } else if (MessageObject.isNewGifDocument(document)) {
                                        autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject);
                                    }
                                    if (!messageObject.isSending() && !messageObject.isEditing() && (messageObject.mediaExists || FileLoader.getInstance(currentAccount).isLoadingFile(fileName) || autoDownload)) {
                                        photoNotSet = false;
                                        photoImage.setImage(document, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilterThumb, document.size, null, 0);
                                    } else {
                                        photoNotSet = true;
                                        photoImage.setImage(null, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilterThumb, 0, null, 0);
                                    }
                                } else {
                                    boolean photoExist = messageObject.mediaExists;
                                    String fileName = FileLoader.getAttachFileName(currentPhotoObject);
                                    if (hasGamePreview || photoExist || DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject) || FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                                        photoNotSet = false;
                                        photoImage.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, 0, null, 0);
                                    } else {
                                        photoNotSet = true;
                                        if (currentPhotoObjectThumb != null) {
                                            photoImage.setImage(null, null, currentPhotoObjectThumb.location, String.format(Locale.US, "%d_%d_b", width, height), 0, null, 0);
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

                TLRPC.FileLocation currentPhoto = null;
                if (user != null) {
                    if (user.photo != null) {
                        currentPhoto = user.photo.photo_small;
                    }
                    contactAvatarDrawable.setInfo(user);
                }
                photoImage.setImage(currentPhoto, "50_50", user != null ? contactAvatarDrawable : Theme.chat_contactDrawable[messageObject.isOutOwner() ? 1 : 0], null, 0);

                CharSequence phone;
                if (!TextUtils.isEmpty(messageObject.vCardData)) {
                    phone = messageObject.vCardData;
                    drawInstantView = true;
                    drawInstantViewType = 5;
                } else {
                    phone = messageObject.messageOwner.media.phone_number;
                    if (!TextUtils.isEmpty(phone)) {
                        phone = PhoneFormat.getInstance().format((String) phone);
                    } else {
                        phone = LocaleController.getString("NumberUnknown", R.string.NumberUnknown);
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
            } else {
                drawForwardedName = messageObject.messageOwner.fwd_from != null && messageObject.type != 13;
                mediaBackground = messageObject.type != 9;
                drawImageButton = true;
                drawPhotoImage = true;

                int photoWidth = 0;
                int photoHeight = 0;
                int additionHeight = 0;

                if (messageObject.gifState != 2 && !SharedConfig.autoplayGifs && (messageObject.type == 8 || messageObject.type == 5)) {
                    messageObject.gifState = 1;
                }

                if (messageObject.isRoundVideo()) {
                    photoImage.setAllowDecodeSingleFrame(true);
                    photoImage.setAllowStartAnimation(MediaController.getInstance().getPlayingMessageObject() == null);
                } else {
                    photoImage.setAllowStartAnimation(messageObject.gifState == 0);
                }

                photoImage.setForcePreview(messageObject.needDrawBluredPreview());
                if (messageObject.type == 9) {
                    if (AndroidUtilities.isTablet()) {
                        backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                    } else {
                        backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                    }
                    if (checkNeedDrawShareButton(messageObject)) {
                        backgroundWidth -= AndroidUtilities.dp(20);
                    }
                    int maxWidth = backgroundWidth - AndroidUtilities.dp(86 + 52);
                    createDocumentLayout(maxWidth, messageObject);
                    if (!TextUtils.isEmpty(messageObject.caption)) {
                        maxWidth += AndroidUtilities.dp(86);
                    }
                    if (drawPhotoImage) {
                        photoWidth = AndroidUtilities.dp(86);
                        photoHeight = AndroidUtilities.dp(86);
                    } else {
                        photoWidth = AndroidUtilities.dp(56);
                        photoHeight = AndroidUtilities.dp(56);
                        maxWidth += AndroidUtilities.dp(TextUtils.isEmpty(messageObject.caption) ? 51 : 21);
                    }
                    availableTimeWidth = maxWidth;
                    if (!drawPhotoImage) {
                        if (TextUtils.isEmpty(messageObject.caption) && infoLayout.getLineCount() > 0) {
                            measureTime(messageObject);
                            int timeLeft = backgroundWidth - AndroidUtilities.dp(40 + 18 + 56 + 8) - (int) Math.ceil(infoLayout.getLineWidth(0));
                            if (timeLeft < timeWidth) {
                                photoHeight += AndroidUtilities.dp(8);
                            }
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
                        if (checkNeedDrawShareButton(messageObject)) {
                            backgroundWidth -= AndroidUtilities.dp(20);
                        }
                        int maxWidth = backgroundWidth - AndroidUtilities.dp(37);
                        availableTimeWidth = maxWidth;
                        maxWidth -= AndroidUtilities.dp(54);

                        photoWidth = backgroundWidth - AndroidUtilities.dp(21);
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
                        docTitleLayout = new StaticLayout(LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation), Theme.chat_locationTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                        TLRPC.FileLocation currentPhoto = null;
                        updateCurrentUserAndChat();
                        if (currentUser != null) {
                            if (currentUser.photo != null) {
                                currentPhoto = currentUser.photo.photo_small;
                            }
                            contactAvatarDrawable.setInfo(currentUser);
                        } else if (currentChat != null) {
                            if (currentChat.photo != null) {
                                currentPhoto = currentChat.photo.photo_small;
                            }
                            contactAvatarDrawable.setInfo(currentChat);
                        }
                        locationImageReceiver.setImage(currentPhoto, "50_50", contactAvatarDrawable, null, 0);

                        infoLayout = new StaticLayout(LocaleController.formatLocationUpdateDate(messageObject.messageOwner.edit_date != 0 ? messageObject.messageOwner.edit_date : messageObject.messageOwner.date), Theme.chat_locationAddressPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    } else if (!TextUtils.isEmpty(messageObject.messageOwner.media.title)) {
                        if (AndroidUtilities.isTablet()) {
                            backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(252 + 37));
                        } else {
                            backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.needDrawAvatar() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(252 + 37));
                        }
                        if (checkNeedDrawShareButton(messageObject)) {
                            backgroundWidth -= AndroidUtilities.dp(20);
                        }
                        int maxWidth = backgroundWidth - AndroidUtilities.dp(34);
                        availableTimeWidth = maxWidth;

                        photoWidth = backgroundWidth - AndroidUtilities.dp(21);
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
                            int timeLeft = backgroundWidth - (int) Math.ceil(infoLayout.getLineWidth(0));
                            if (timeLeft < timeWidth + AndroidUtilities.dp(14 + (messageObject.isOutOwner() ? 20 : 0))) {
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
                        if (checkNeedDrawShareButton(messageObject)) {
                            backgroundWidth -= AndroidUtilities.dp(20);
                        }
                        availableTimeWidth = backgroundWidth - AndroidUtilities.dp(34);

                        photoWidth = backgroundWidth - AndroidUtilities.dp(12);
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
                        photoImage.setImage((TLObject) null, null, Theme.chat_locationDrawable[messageObject.isOutOwner() ? 1 : 0], null, 0);
                    } else if (currentMapProvider == 2) {
                        if (currentWebFile != null) {
                            photoImage.setImage(currentWebFile, null, Theme.chat_locationDrawable[messageObject.isOutOwner() ? 1 : 0], null, 0);
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
                } else if (messageObject.type == 13) { //webp
                    drawBackground = false;
                    for (int a = 0; a < messageObject.messageOwner.media.document.attributes.size(); a++) {
                        TLRPC.DocumentAttribute attribute = messageObject.messageOwner.media.document.attributes.get(a);
                        if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                            photoWidth = attribute.w;
                            photoHeight = attribute.h;
                            break;
                        }
                    }
                    float maxHeight;
                    float maxWidth;
                    if (AndroidUtilities.isTablet()) {
                        maxHeight = maxWidth = AndroidUtilities.getMinTabletSide() * 0.4f;
                    } else {
                        maxHeight = maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
                    }
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
                    documentAttachType = DOCUMENT_ATTACH_TYPE_STICKER;
                    availableTimeWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(14);
                    backgroundWidth = photoWidth + AndroidUtilities.dp(12);

                    currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                    if (messageObject.attachPathExists) {
                        photoImage.setImage(null, messageObject.messageOwner.attachPath,
                                String.format(Locale.US, "%d_%d", photoWidth, photoHeight),
                                null,
                                currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null,
                                "b1",
                                messageObject.messageOwner.media.document.size, "webp", 1);
                    } else if (messageObject.messageOwner.media.document.id != 0) {
                        photoImage.setImage(messageObject.messageOwner.media.document, null,
                                String.format(Locale.US, "%d_%d", photoWidth, photoHeight),
                                null,
                                currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null,
                                "b1",
                                messageObject.messageOwner.media.document.size, "webp", 1);
                    }
                } else {
                    int maxPhotoWidth;
                    if (messageObject.type == 5) {
                        maxPhotoWidth = photoWidth = AndroidUtilities.roundMessageSize;
                    } else {
                        if (AndroidUtilities.isTablet()) {
                            maxPhotoWidth = photoWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.7f);
                        } else {
                            maxPhotoWidth = photoWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f);
                        }
                    }
                    photoHeight = photoWidth + AndroidUtilities.dp(100);
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

                    if (messageObject.type == 1) { //photo
                        updateSecretTimeText(messageObject);
                        currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                    } else if (messageObject.type == 3) { //video
                        createDocumentLayout(0, messageObject);
                        updateSecretTimeText(messageObject);
                        if (!messageObject.needDrawBluredPreview()) {
                            photoImage.setNeedsQualityThumb(true);
                            photoImage.setShouldGenerateQualityThumb(true);
                        }
                        photoImage.setParentMessageObject(messageObject);
                    } else if (messageObject.type == 5) { //round video
                        if (!messageObject.needDrawBluredPreview()) {
                            photoImage.setNeedsQualityThumb(true);
                            photoImage.setShouldGenerateQualityThumb(true);
                        }
                        photoImage.setParentMessageObject(messageObject);
                    } else if (messageObject.type == 8) { //gif
                        String str = AndroidUtilities.formatFileSize(messageObject.messageOwner.media.document.size);
                        infoWidth = (int) Math.ceil(Theme.chat_infoPaint.measureText(str));
                        infoLayout = new StaticLayout(str, Theme.chat_infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        if (!messageObject.needDrawBluredPreview()) {
                            photoImage.setNeedsQualityThumb(true);
                            photoImage.setShouldGenerateQualityThumb(true);
                        }
                        photoImage.setParentMessageObject(messageObject);
                    }

                    if (currentMessagesGroup == null && messageObject.caption != null) {
                        mediaBackground = false;
                    }

                    currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());

                    int w = 0;
                    int h = 0;

                    if (currentPhotoObject != null && currentPhotoObject == currentPhotoObjectThumb) {
                        currentPhotoObjectThumb = null;
                    }

                    if (currentPhotoObject != null) {
                        float scale = (float) currentPhotoObject.w / (float) photoWidth;
                        w = (int) (currentPhotoObject.w / scale);
                        h = (int) (currentPhotoObject.h / scale);
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
                            float hScale = (float) currentPhotoObject.h / h;
                            if (currentPhotoObject.w / hScale < photoWidth) {
                                w = (int) (currentPhotoObject.w / hScale);
                            }
                        }
                    }
                    if (messageObject.type == 5) {
                        w = h = AndroidUtilities.roundMessageSize;
                    }

                    if ((w == 0 || h == 0) && messageObject.type == 8) {
                        for (int a = 0; a < messageObject.messageOwner.media.document.attributes.size(); a++) {
                            TLRPC.DocumentAttribute attribute = messageObject.messageOwner.media.document.attributes.get(a);
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
                    if (messageObject.type == 5) {
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
                            h += (currentPosition.maxY - currentPosition.minY) * AndroidUtilities.dp(11); //TODO fix
                        } else {
                            h = (int) Math.ceil(maxHeight * currentPosition.ph);
                        }
                        backgroundWidth = w;
                        w -= AndroidUtilities.dp(12);
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
                                    w -= AndroidUtilities.dp(18);
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
                        photoWidth = w;
                        photoHeight = h;
                        backgroundWidth = w + AndroidUtilities.dp(12);
                        if (!mediaBackground) {
                            backgroundWidth += AndroidUtilities.dp(9);
                        }
                        currentCaption = messageObject.caption;
                        widthForCaption = photoWidth - AndroidUtilities.dp(10);
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
                            if (captionLayout.getLineCount() > 0) {
                                captionWidth = widthForCaption;
                                captionHeight = captionLayout.getHeight();
                                addedCaptionHeight = captionHeight + AndroidUtilities.dp(9);
                                if (currentPosition == null || (currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                                    additionHeight += addedCaptionHeight;
                                    float lastLineWidth = captionLayout.getLineWidth(captionLayout.getLineCount() - 1) + captionLayout.getLineLeft(captionLayout.getLineCount() - 1);
                                    if (widthForCaption + AndroidUtilities.dp(2) - lastLineWidth < timeWidthTotal) {
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

                    currentPhotoFilter = currentPhotoFilterThumb = String.format(Locale.US, "%d_%d", (int) (w / AndroidUtilities.density), (int) (h / AndroidUtilities.density));
                    if (messageObject.photoThumbs != null && messageObject.photoThumbs.size() > 1 || messageObject.type == 3 || messageObject.type == 8 || messageObject.type == 5) {
                        if (messageObject.needDrawBluredPreview()) {
                            currentPhotoFilter += "_b2";
                            currentPhotoFilterThumb += "_b2";
                        } else {
                            currentPhotoFilterThumb += "_b";
                        }
                    }

                    boolean noSize = false;
                    if (messageObject.type == 3 || messageObject.type == 8 || messageObject.type == 5) {
                        noSize = true;
                    }
                    if (currentPhotoObject != null && !noSize && currentPhotoObject.size == 0) {
                        currentPhotoObject.size = -1;
                    }

                    if (messageObject.type == 1) {
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
                                    photoImage.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, noSize ? 0 : currentPhotoObject.size, null, currentMessageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
                                } else {
                                    photoNotSet = true;
                                    if (currentPhotoObjectThumb != null) {
                                        photoImage.setImage(null, null, currentPhotoObjectThumb.location, currentPhotoFilterThumb, 0, null, currentMessageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
                                    } else {
                                        photoImage.setImageBitmap((Drawable) null);
                                    }
                                }
                            } else {
                                photoImage.setImageBitmap((Drawable) null);
                            }
                        }
                    } else if (messageObject.type == 8 || messageObject.type == 5) {
                        String fileName = FileLoader.getAttachFileName(messageObject.messageOwner.media.document);
                        int localFile = 0;
                        if (messageObject.attachPathExists) {
                            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                            localFile = 1;
                        } else if (messageObject.mediaExists) {
                            localFile = 2;
                        }
                        boolean autoDownload = false;
                        if (MessageObject.isNewGifDocument(messageObject.messageOwner.media.document)) {
                            autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject);
                        } else if (messageObject.type == 5) {
                            autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject);
                        }
                        if (!messageObject.isSending() && !messageObject.isEditing() && (localFile != 0 || FileLoader.getInstance(currentAccount).isLoadingFile(fileName) || autoDownload)) {
                            if (localFile == 1) {
                                photoImage.setImage(null, messageObject.isSendError() ? null : messageObject.messageOwner.attachPath, null, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilterThumb, 0, null, 0);
                            } else {
                                photoImage.setImage(messageObject.messageOwner.media.document, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilterThumb, messageObject.messageOwner.media.document.size, null, 0);
                            }
                        } else {
                            photoNotSet = true;
                            photoImage.setImage(null, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilterThumb, 0, null, 0);
                        }
                    } else {
                        photoImage.setImage(null, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilterThumb, 0, null, currentMessageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
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
                        photoHeight += AndroidUtilities.dp(4);
                    }
                }

                if (drawPinnedTop) {
                    namesOffset -= AndroidUtilities.dp(1);
                }

                photoImage.setImageCoords(0, AndroidUtilities.dp(7) + namesOffset + additionalTop, photoWidth, photoHeight);
                invalidate();
            }

            if (currentPosition == null && captionLayout == null && messageObject.caption != null && messageObject.type != 13) {
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
                    if (captionLayout.getLineCount() > 0) {
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
            } else if (widthBeforeNewTimeLine != -1 && availableTimeWidth - widthBeforeNewTimeLine < timeWidth) {
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
            if (currentPosition == null && messageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
                int rows = messageObject.messageOwner.reply_markup.rows.size();
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
            if (messageObject.type == 13 && totalHeight < AndroidUtilities.dp(70)) {
                totalHeight = AndroidUtilities.dp(70);
            }
            if (!drawPhotoImage) {
                photoImage.setImageBitmap((Drawable) null);
            }
        }
        updateWaveform();
        updateButtonState(dataChanged && !messageObject.cancelEditing, true);
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

    private void createInstantViewButton() {
        if (Build.VERSION.SDK_INT >= 21 && drawInstantView) {
            if (instantViewSelectorDrawable == null) {
                final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                maskPaint.setColor(0xffffffff);
                Drawable maskDrawable = new Drawable() {

                    RectF rect = new RectF();

                    @Override
                    public void draw(Canvas canvas) {
                        android.graphics.Rect bounds = getBounds();
                        rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), maskPaint);
                    }

                    @Override
                    public void setAlpha(int alpha) {

                    }

                    @Override
                    public void setColorFilter(ColorFilter colorFilter) {

                    }

                    @Override
                    public int getOpacity() {
                        return PixelFormat.OPAQUE;
                    }
                };
                ColorStateList colorStateList = new ColorStateList(
                        new int[][]{StateSet.WILD_CARD},
                        new int[]{Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText) & 0x5fffffff}
                );
                instantViewSelectorDrawable = new RippleDrawable(colorStateList, null, maskDrawable);
                instantViewSelectorDrawable.setCallback(this);
            } else {
                Theme.setSelectorDrawableColor(instantViewSelectorDrawable, Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText) & 0x5fffffff, true);
            }
            instantViewSelectorDrawable.setVisible(true, false);
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
            } else {
                str = LocaleController.getString("InstantView", R.string.InstantView);
            }
            int mWidth = backgroundWidth - AndroidUtilities.dp(10 + 24 + 10 + 31);
            instantViewLayout = new StaticLayout(TextUtils.ellipsize(str, Theme.chat_instantViewPaint, mWidth, TextUtils.TruncateAt.END), Theme.chat_instantViewPaint, mWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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
        if (currentMessageObject != null && (currentMessageObject.checkLayout() || currentPosition != null && lastHeight != AndroidUtilities.displaySize.y)) {
            inLayout = true;
            MessageObject messageObject = currentMessageObject;
            currentMessageObject = null;
            setMessageObject(messageObject, currentMessagesGroup, pinnedBottom, pinnedTop);
            inLayout = false;
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight + keyboardHeight);
        lastHeight = AndroidUtilities.displaySize.y;
    }

    public void forceResetMessageObject() {
        MessageObject messageObject = currentMessageObject;
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
                    x -= AndroidUtilities.dp(4);
                }
                if (currentPosition.leftSpanOffset != 0) {
                    x += (int) Math.ceil(currentPosition.leftSpanOffset / 1000.0f * getGroupPhotosWidth());
                }
            }
            photoImage.setImageCoords(x, photoImage.getImageY(), photoImage.getImageWidth(), photoImage.getImageHeight());
            buttonX = (int) (x + (photoImage.getImageWidth() - AndroidUtilities.dp(48)) / 2.0f);
            buttonY = photoImage.getImageY() + (photoImage.getImageHeight() - AndroidUtilities.dp(48)) / 2;
            radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(48), buttonY + AndroidUtilities.dp(48));
            deleteProgressRect.set(buttonX + AndroidUtilities.dp(3), buttonY + AndroidUtilities.dp(3), buttonX + AndroidUtilities.dp(45), buttonY + AndroidUtilities.dp(45));
        }
    }

    public boolean needDelayRoundProgressDraw() {
        return documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND && currentMessageObject.type != 5 && MediaController.getInstance().isPlayingMessage(currentMessageObject);
    }

    public void drawRoundProgress(Canvas canvas) {
        rect.set(photoImage.getImageX() + AndroidUtilities.dpf2(1.5f), photoImage.getImageY() + AndroidUtilities.dpf2(1.5f), photoImage.getImageX2() - AndroidUtilities.dpf2(1.5f), photoImage.getImageY2() - AndroidUtilities.dpf2(1.5f));
        canvas.drawArc(rect, -90, 360 * currentMessageObject.audioProgress, false, Theme.chat_radialProgressPaint);
    }

    private void drawContent(Canvas canvas) {
        if (needNewVisiblePart && currentMessageObject.type == 0) {
            getLocalVisibleRect(scrollRect);
            setVisiblePart(scrollRect.top, scrollRect.bottom - scrollRect.top);
            needNewVisiblePart = false;
        }
        forceNotDrawTime = currentMessagesGroup != null;
        photoImage.setPressed(isDrawSelectedBackground() ? (currentPosition != null ? 2 : 1) : 0);
        photoImage.setVisible(!PhotoViewer.isShowingImage(currentMessageObject) && !SecretMediaViewer.getInstance().isShowingImage(currentMessageObject), false);
        if (!photoImage.getVisible()) {
            mediaWasInvisible = true;
            timeWasInvisible = true;
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
        radialProgress.setHideCurrentDrawable(false);
        radialProgress.setProgressColor(Theme.getColor(Theme.key_chat_mediaProgress));

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

                if (drawPhotoImage && drawInstantView) {
                    if (linkPreviewY != startY) {
                        linkPreviewY += AndroidUtilities.dp(2);
                    }
                    photoImage.setImageCoords(linkX + AndroidUtilities.dp(10), linkPreviewY, photoImage.getImageWidth(), photoImage.getImageHeight());
                    if (drawImageButton) {
                        int size = AndroidUtilities.dp(48);
                        buttonX = (int) (photoImage.getImageX() + (photoImage.getImageWidth() - size) / 2.0f);
                        buttonY = (int) (photoImage.getImageY() + (photoImage.getImageHeight() - size) / 2.0f);
                        radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);
                    }
                    imageDrawn = photoImage.draw(canvas);
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
                    if (currentMessageObject.isRoundVideo() && MediaController.getInstance().isPlayingMessage(currentMessageObject) && MediaController.getInstance().isRoundVideoDrawingReady()) {
                        imageDrawn = true;
                        drawTime = true;
                    } else {
                        imageDrawn = photoImage.draw(canvas);
                    }
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
                if (videoInfoLayout != null && (!drawPhotoImage || photoImage.getVisible())) {
                    int x;
                    int y;
                    if (hasGamePreview || hasInvoicePreview) {
                        if (drawPhotoImage) {
                            x = photoImage.getImageX() + AndroidUtilities.dp(8.5f);
                            y = photoImage.getImageY() + AndroidUtilities.dp(6);
                            rect.set(x - AndroidUtilities.dp(4), y - AndroidUtilities.dp(1.5f), x + durationWidth + AndroidUtilities.dp(4), y + AndroidUtilities.dp(16.5f));
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
                        instantViewSelectorDrawable.setBounds(linkX, instantY, linkX + instantWidth, instantY + AndroidUtilities.dp(36));
                        instantViewSelectorDrawable.draw(canvas);
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
            if (currentMessageObject.isRoundVideo() && MediaController.getInstance().isPlayingMessage(currentMessageObject) && MediaController.getInstance().isRoundVideoDrawingReady()) {
                imageDrawn = true;
                drawTime = true;
            } else {
                if (currentMessageObject.type == 5 && Theme.chat_roundVideoShadow != null) {
                    int x = photoImage.getImageX() - AndroidUtilities.dp(3);
                    int y = photoImage.getImageY() - AndroidUtilities.dp(2);
                    Theme.chat_roundVideoShadow.setAlpha((int) (photoImage.getCurrentAlpha() * 255));
                    Theme.chat_roundVideoShadow.setBounds(x, y, x + AndroidUtilities.roundMessageSize + AndroidUtilities.dp(6), y + AndroidUtilities.roundMessageSize + AndroidUtilities.dp(6));
                    Theme.chat_roundVideoShadow.draw(canvas);
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

        if (buttonState == -1 && currentMessageObject.needDrawBluredPreview() && !MediaController.getInstance().isPlayingMessage(currentMessageObject) && photoImage.getVisible()) {
            int drawable = 4;
            if (currentMessageObject.messageOwner.destroyTime != 0) {
                if (currentMessageObject.isOutOwner()) {
                    drawable = 6;
                } else {
                    drawable = 5;
                }
            }
            setDrawableBounds(Theme.chat_photoStatesDrawables[drawable][buttonPressed], buttonX, buttonY);
            Theme.chat_photoStatesDrawables[drawable][buttonPressed].setAlpha((int) (255 * (1.0f - radialProgress.getAlpha()) * controlsAlpha));
            Theme.chat_photoStatesDrawables[drawable][buttonPressed].draw(canvas);
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

        if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || currentMessageObject.type == 8) {
            if (photoImage.getVisible() && !hasGamePreview && !currentMessageObject.needDrawBluredPreview()) {
                int oldAlpha = ((BitmapDrawable) Theme.chat_msgMediaMenuDrawable).getPaint().getAlpha();
                Theme.chat_msgMediaMenuDrawable.setAlpha((int) (oldAlpha * controlsAlpha));
                setDrawableBounds(Theme.chat_msgMediaMenuDrawable, otherX = photoImage.getImageX() + photoImage.getImageWidth() - AndroidUtilities.dp(14), otherY = photoImage.getImageY() + AndroidUtilities.dp(8.1f));
                Theme.chat_msgMediaMenuDrawable.draw(canvas);
                Theme.chat_msgMediaMenuDrawable.setAlpha(oldAlpha);
            }
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND || currentMessageObject.type == 5) {
            if (durationLayout != null) {
                int x1;
                int y1;

                boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
                if (playing && currentMessageObject.type == 5) {
                    drawRoundProgress(canvas);
                }
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
                    x1 = backgroundDrawableLeft + AndroidUtilities.dp(currentMessageObject.isOutOwner() || drawPinnedBottom ? 12 : 18);
                    y1 = layoutHeight - AndroidUtilities.dp(6.3f - (drawPinnedBottom ? 2 : 0)) - timeLayout.getHeight();
                } else {
                    x1 = backgroundDrawableLeft + AndroidUtilities.dp(8);
                    y1 = layoutHeight - AndroidUtilities.dp(28 - (drawPinnedBottom ? 2 : 0));
                    rect.set(x1, y1, x1 + timeWidthAudio + AndroidUtilities.dp(8 + 12 + 2), y1 + AndroidUtilities.dp(17));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_actionBackgroundPaint);

                    if (!playing && currentMessageObject.isContentUnread()) {
                        Theme.chat_docBackPaint.setColor(Theme.getColor(Theme.key_chat_mediaTimeText));
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
                }

                canvas.save();
                canvas.translate(x1, y1);
                durationLayout.draw(canvas);
                canvas.restore();
            }
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (currentMessageObject.isOutOwner()) {
                Theme.chat_audioTitlePaint.setColor(Theme.getColor(Theme.key_chat_outAudioTitleText));
                Theme.chat_audioPerformerPaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_outAudioPerfomerSelectedText : Theme.key_chat_outAudioPerfomerText));
                Theme.chat_audioTimePaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_outAudioDurationSelectedText : Theme.key_chat_outAudioDurationText));
                radialProgress.setProgressColor(Theme.getColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.key_chat_outAudioSelectedProgress : Theme.key_chat_outAudioProgress));
            } else {
                Theme.chat_audioTitlePaint.setColor(Theme.getColor(Theme.key_chat_inAudioTitleText));
                Theme.chat_audioPerformerPaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_inAudioPerfomerSelectedText : Theme.key_chat_inAudioPerfomerText));
                Theme.chat_audioTimePaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_inAudioDurationSelectedText : Theme.key_chat_inAudioDurationText));
                radialProgress.setProgressColor(Theme.getColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
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
                menuDrawable = isDrawSelectedBackground() ? Theme.chat_msgOutMenuSelectedDrawable : Theme.chat_msgOutMenuDrawable;
            } else {
                menuDrawable = isDrawSelectedBackground() ? Theme.chat_msgInMenuSelectedDrawable : Theme.chat_msgInMenuDrawable;
            }
            setDrawableBounds(menuDrawable, otherX = buttonX + backgroundWidth - AndroidUtilities.dp(currentMessageObject.type == 0 ? 58 : 48), otherY = buttonY - AndroidUtilities.dp(5));
            menuDrawable.draw(canvas);
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
            if (currentMessageObject.isOutOwner()) {
                Theme.chat_audioTimePaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_outAudioDurationSelectedText : Theme.key_chat_outAudioDurationText));
                radialProgress.setProgressColor(Theme.getColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.key_chat_outAudioSelectedProgress : Theme.key_chat_outAudioProgress));
            } else {
                Theme.chat_audioTimePaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_inAudioDurationSelectedText : Theme.key_chat_inAudioDurationText));
                radialProgress.setProgressColor(Theme.getColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
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

        if (currentMessageObject.type == 1 || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
            if (photoImage.getVisible()) {
                if (!currentMessageObject.needDrawBluredPreview()) {
                    if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                        int oldAlpha = ((BitmapDrawable) Theme.chat_msgMediaMenuDrawable).getPaint().getAlpha();
                        Theme.chat_msgMediaMenuDrawable.setAlpha((int) (oldAlpha * controlsAlpha));
                        setDrawableBounds(Theme.chat_msgMediaMenuDrawable, otherX = photoImage.getImageX() + photoImage.getImageWidth() - AndroidUtilities.dp(14), otherY = photoImage.getImageY() + AndroidUtilities.dp(8.1f));
                        Theme.chat_msgMediaMenuDrawable.draw(canvas);
                        Theme.chat_msgMediaMenuDrawable.setAlpha(oldAlpha);
                    }
                }

                if (!forceNotDrawTime && infoLayout != null && (buttonState == 1 || buttonState == 0 || buttonState == 3 || currentMessageObject.needDrawBluredPreview())) {
                    Theme.chat_infoPaint.setColor(Theme.getColor(Theme.key_chat_mediaInfoText));
                    int x1 = photoImage.getImageX() + AndroidUtilities.dp(4);
                    int y1 = photoImage.getImageY() + AndroidUtilities.dp(4);
                    rect.set(x1, y1, x1 + infoWidth + AndroidUtilities.dp(8), y1 + AndroidUtilities.dp(16.5f));
                    int oldAlpha = Theme.chat_timeBackgroundPaint.getAlpha();
                    Theme.chat_timeBackgroundPaint.setAlpha((int) (oldAlpha * controlsAlpha));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_timeBackgroundPaint);
                    Theme.chat_timeBackgroundPaint.setAlpha(oldAlpha);

                    canvas.save();
                    canvas.translate(photoImage.getImageX() + AndroidUtilities.dp(8), photoImage.getImageY() + AndroidUtilities.dp(5.5f));
                    Theme.chat_infoPaint.setAlpha((int) (255 * controlsAlpha));
                    infoLayout.draw(canvas);
                    canvas.restore();
                    Theme.chat_infoPaint.setAlpha(255);
                }
            }
        } else {
            if (currentMessageObject.type == 4) {
                if (docTitleLayout != null) {
                    if (currentMessageObject.isOutOwner()) {
                        Theme.chat_locationTitlePaint.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
                        Theme.chat_locationAddressPaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_outVenueInfoSelectedText : Theme.key_chat_outVenueInfoText));
                    } else {
                        Theme.chat_locationTitlePaint.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
                        Theme.chat_locationAddressPaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_inVenueInfoSelectedText : Theme.key_chat_inVenueInfoText));
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
                    Theme.chat_contactPhonePaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_outTimeSelectedText : Theme.key_chat_outTimeText));
                } else {
                    Theme.chat_audioTitlePaint.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
                    Theme.chat_contactPhonePaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_inTimeSelectedText : Theme.key_chat_inTimeText));
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
                    phone = isDrawSelectedBackground() || otherPressed ? Theme.chat_msgOutCallSelectedDrawable : Theme.chat_msgOutCallDrawable;
                } else {
                    TLRPC.PhoneCallDiscardReason reason = currentMessageObject.messageOwner.action.reason;
                    if (reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed || reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) {
                        icon = Theme.chat_msgCallDownRedDrawable;
                    } else {
                        icon = Theme.chat_msgCallDownGreenDrawable;
                    }
                    phone = isDrawSelectedBackground() || otherPressed ? Theme.chat_msgInCallSelectedDrawable : Theme.chat_msgInCallDrawable;
                }
                setDrawableBounds(icon, x - AndroidUtilities.dp(3), AndroidUtilities.dp(36) + namesOffset);
                icon.draw(canvas);

                setDrawableBounds(phone, x + AndroidUtilities.dp(205), otherY = AndroidUtilities.dp(22));
                phone.draw(canvas);
            } else if (currentMessageObject.type == 12) {
                if (currentMessageObject.isOutOwner()) {
                    Theme.chat_contactNamePaint.setColor(Theme.getColor(Theme.key_chat_outContactNameText));
                    Theme.chat_contactPhonePaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_outContactPhoneSelectedText : Theme.key_chat_outContactPhoneText));
                } else {
                    Theme.chat_contactNamePaint.setColor(Theme.getColor(Theme.key_chat_inContactNameText));
                    Theme.chat_contactPhonePaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_inContactPhoneSelectedText : Theme.key_chat_inContactPhoneText));
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
                    menuDrawable = isDrawSelectedBackground() ? Theme.chat_msgOutMenuSelectedDrawable : Theme.chat_msgOutMenuDrawable;
                } else {
                    menuDrawable = isDrawSelectedBackground() ? Theme.chat_msgInMenuSelectedDrawable : Theme.chat_msgInMenuDrawable;
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
                        instantViewSelectorDrawable.setBounds(textX, instantY, textX + instantWidth, instantY + AndroidUtilities.dp(36));
                        instantViewSelectorDrawable.draw(canvas);
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
        }

        if (captionLayout != null) {
            if (currentMessageObject.type == 1 || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || currentMessageObject.type == 8) {
                captionX = photoImage.getImageX() + AndroidUtilities.dp(5) + captionOffsetX;
                captionY = photoImage.getImageY() + photoImage.getImageHeight() + AndroidUtilities.dp(6);
            } else if (hasOldCaptionPreview) {
                captionX = backgroundDrawableLeft + AndroidUtilities.dp(currentMessageObject.isOutOwner() ? 11 : 17) + captionOffsetX;
                captionY = totalHeight - captionHeight - AndroidUtilities.dp(drawPinnedTop ? 9 : 10) - linkPreviewHeight - AndroidUtilities.dp(17);
            } else {
                captionX = backgroundDrawableLeft + AndroidUtilities.dp(currentMessageObject.isOutOwner() ? 11 : 17) + captionOffsetX;
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
                Theme.chat_infoPaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_outFileInfoSelectedText : Theme.key_chat_outFileInfoText));
                Theme.chat_docBackPaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_outFileBackgroundSelected : Theme.key_chat_outFileBackground));
                menuDrawable = isDrawSelectedBackground() ? Theme.chat_msgOutMenuSelectedDrawable : Theme.chat_msgOutMenuDrawable;
            } else {
                Theme.chat_docNamePaint.setColor(Theme.getColor(Theme.key_chat_inFileNameText));
                Theme.chat_infoPaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_inFileInfoSelectedText : Theme.key_chat_inFileInfoText));
                Theme.chat_docBackPaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_inFileBackgroundSelected : Theme.key_chat_inFileBackground));
                menuDrawable = isDrawSelectedBackground() ? Theme.chat_msgInMenuSelectedDrawable : Theme.chat_msgInMenuDrawable;
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
                subtitleY = photoImage.getImageY() + docTitleLayout.getLineBottom(docTitleLayout.getLineCount() - 1) + AndroidUtilities.dp(13);
                if (buttonState >= 0 && buttonState < 4) {
                    if (!imageDrawn) {
                        int image = buttonState;
                        if (buttonState == 0) {
                            image = currentMessageObject.isOutOwner() ? 7 : 10;
                        } else if (buttonState == 1) {
                            image = currentMessageObject.isOutOwner() ? 8 : 11;
                        }
                        radialProgress.swapBackground(Theme.chat_photoStatesDrawables[image][isDrawSelectedBackground() || buttonPressed != 0 ? 1 : 0]);
                    } else {
                        radialProgress.swapBackground(Theme.chat_photoStatesDrawables[buttonState][buttonPressed]);
                    }
                }

                if (!imageDrawn) {
                    rect.set(photoImage.getImageX(), photoImage.getImageY(), photoImage.getImageX() + photoImage.getImageWidth(), photoImage.getImageY() + photoImage.getImageHeight());
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), Theme.chat_docBackPaint);
                    if (currentMessageObject.isOutOwner()) {
                        radialProgress.setProgressColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_outFileProgressSelected : Theme.key_chat_outFileProgress));
                    } else {
                        radialProgress.setProgressColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_inFileProgressSelected : Theme.key_chat_inFileProgress));
                    }
                } else {
                    if (buttonState == -1) {
                        radialProgress.setHideCurrentDrawable(true);
                    }
                    radialProgress.setProgressColor(Theme.getColor(Theme.key_chat_mediaProgress));
                }
            } else {
                setDrawableBounds(menuDrawable, otherX = buttonX + backgroundWidth - AndroidUtilities.dp(currentMessageObject.type == 0 ? 58 : 48), otherY = buttonY - AndroidUtilities.dp(5));
                x = buttonX + AndroidUtilities.dp(53);
                titleY = buttonY + AndroidUtilities.dp(4);
                subtitleY = buttonY + AndroidUtilities.dp(27);
                if (currentMessageObject.isOutOwner()) {
                    radialProgress.setProgressColor(Theme.getColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.key_chat_outAudioSelectedProgress : Theme.key_chat_outAudioProgress));
                } else {
                    radialProgress.setProgressColor(Theme.getColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
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
        if (drawImageButton && photoImage.getVisible()) {
            if (controlsAlpha != 1.0f) {
                radialProgress.setOverrideAlpha(controlsAlpha);
            }
            radialProgress.draw(canvas);
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
                addX = backgroundDrawableLeft + AndroidUtilities.dp(mediaBackground ? 1 : 7);
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
                } else if (button.button instanceof TLRPC.TL_keyboardButtonCallback || button.button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation || button.button instanceof TLRPC.TL_keyboardButtonGame || button.button instanceof TLRPC.TL_keyboardButtonBuy) {
                    boolean drawProgress = (button.button instanceof TLRPC.TL_keyboardButtonCallback || button.button instanceof TLRPC.TL_keyboardButtonGame || button.button instanceof TLRPC.TL_keyboardButtonBuy) && SendMessagesHelper.getInstance(currentAccount).isSendingCallback(currentMessageObject, button.button) ||
                            button.button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation && SendMessagesHelper.getInstance(currentAccount).isSendingCurrentLocation(currentMessageObject, button.button);
                    if (drawProgress || !drawProgress && button.progressAlpha != 0) {
                        Theme.chat_botProgressPaint.setAlpha(Math.min(255, (int) (button.progressAlpha * 255)));
                        int x = button.x + button.width - AndroidUtilities.dp(9 + 3) + addX;
                        rect.set(x, y + AndroidUtilities.dp(4), x + AndroidUtilities.dp(8), y + AndroidUtilities.dp(8 + 4));
                        canvas.drawArc(rect, button.angle, 220, false, Theme.chat_botProgressPaint);
                        invalidate((int) rect.left - AndroidUtilities.dp(2), (int) rect.top - AndroidUtilities.dp(2), (int) rect.right + AndroidUtilities.dp(2), (int) rect.bottom + AndroidUtilities.dp(2));
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

    private Drawable getMiniDrawableForCurrentState() {
        if (miniButtonState < 0) {
            return null;
        }
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            radialProgress.setAlphaForPrevious(false);
            return Theme.chat_fileMiniStatesDrawable[currentMessageObject.isOutOwner() ? miniButtonState : (miniButtonState + 2)][isDrawSelectedBackground() || miniButtonPressed != 0 ? 1 : 0];
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
            return Theme.chat_fileMiniStatesDrawable[4 + miniButtonState][miniButtonPressed != 0 ? 1 : 0];
        }
        return null;
    }

    private Drawable getDrawableForCurrentState() {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (buttonState == -1) {
                return null;
            }
            radialProgress.setAlphaForPrevious(false);
            radialProgress.setAlphaForMiniPrevious(true);
            return Theme.chat_fileStatesDrawable[currentMessageObject.isOutOwner() ? buttonState : buttonState + 5][isDrawSelectedBackground() || buttonPressed != 0 ? 1 : 0];
        } else {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT && !drawPhotoImage) {
                radialProgress.setAlphaForPrevious(false);
                if (buttonState == -1) {
                    return Theme.chat_fileStatesDrawable[currentMessageObject.isOutOwner() ? 3 : 8][isDrawSelectedBackground() ? 1 : 0];
                } else if (buttonState == 0) {
                    return Theme.chat_fileStatesDrawable[currentMessageObject.isOutOwner() ? 2 : 7][isDrawSelectedBackground() ? 1 : 0];
                } else if (buttonState == 1) {
                    return Theme.chat_fileStatesDrawable[currentMessageObject.isOutOwner() ? 4 : 9][isDrawSelectedBackground() ? 1 : 0];
                }
            } else {
                radialProgress.setAlphaForPrevious(true);
                if (buttonState >= 0 && buttonState < 4) {
                    if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        int image = buttonState;
                        if (buttonState == 0) {
                            image = currentMessageObject.isOutOwner() ? 7 : 10;
                        } else if (buttonState == 1) {
                            image = currentMessageObject.isOutOwner() ? 8 : 11;
                        }
                        return Theme.chat_photoStatesDrawables[image][isDrawSelectedBackground() || buttonPressed != 0 ? 1 : 0];
                    } else {
                        return Theme.chat_photoStatesDrawables[buttonState][buttonPressed];
                    }
                } else if (buttonState == -1 && documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                    return Theme.chat_photoStatesDrawables[currentMessageObject.isOutOwner() ? 9 : 12][isDrawSelectedBackground() ? 1 : 0];
                }
            }
        }
        return null;
    }

    private int getMaxNameWidth() {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER || currentMessageObject.type == 5) {
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

    public void updateButtonState(boolean animated, boolean fromSet) {
        drawRadialCheckBackground = false;
        String fileName = null;
        boolean fileExists = false;
        if (currentMessageObject.type == 1) {
            if (currentPhotoObject == null) {
                return;
            }
            fileName = FileLoader.getAttachFileName(currentPhotoObject);
            fileExists = currentMessageObject.mediaExists;
        } else if (currentMessageObject.type == 8 || currentMessageObject.type == 5 || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || currentMessageObject.type == 9 || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (currentMessageObject.useCustomPhoto) {
                buttonState = 1;
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                return;
            }
            if (currentMessageObject.attachPathExists) {
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
        if (SharedConfig.streamMedia && (int) currentMessageObject.getDialogId() != 0 && !currentMessageObject.isSecretMedia() && (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO && currentMessageObject.canStreamVideo())) {
            hasMiniProgress = fileExists ? 1 : 2;
            fileExists = true;
        }
        if (TextUtils.isEmpty(fileName)) {
            radialProgress.setBackground(null, false, false);
            radialProgress.setMiniBackground(null, false, false);
            return;
        }
        boolean fromBot = currentMessageObject.messageOwner.params != null && currentMessageObject.messageOwner.params.containsKey("query_id");

        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (currentMessageObject.isOut() && (currentMessageObject.isSending() || currentMessageObject.isEditing()) || currentMessageObject.isSendError() && fromBot) {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(currentMessageObject.messageOwner.attachPath, currentMessageObject, this);
                buttonState = 4;
                radialProgress.setBackground(getDrawableForCurrentState(), !fromBot, animated);
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
                } else {
                    DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this);
                    if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
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
                }
            }
            updatePlayingMessageProgress();
        } else if (currentMessageObject.type == 0 && documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT && documentAttachType != DOCUMENT_ATTACH_TYPE_VIDEO) {
            if (currentPhotoObject == null || !drawImageButton) {
                return;
            }
            if (!fileExists) {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this);
                float setProgress = 0;
                boolean progressVisible = false;
                if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                    if (!cancelLoading &&
                            (documentAttachType == 0 && DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject) ||
                                    documentAttachType == DOCUMENT_ATTACH_TYPE_GIF  && MessageObject.isNewGifDocument(documentAttach) && DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject))) {
                        progressVisible = true;
                        buttonState = 1;
                    } else {
                        buttonState = 0;
                    }
                } else {
                    progressVisible = true;
                    buttonState = 1;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    setProgress = progress != null ? progress : 0;
                }
                radialProgress.setProgress(setProgress, false);
                radialProgress.setBackground(getDrawableForCurrentState(), progressVisible, animated);
                invalidate();
            } else {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && !photoImage.isAllowStartAnimation()) {
                    buttonState = 2;
                } else {
                    buttonState = -1;
                }
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                invalidate();
            }
        } else {
            if (currentMessageObject.isOut() && (currentMessageObject.isSending() || currentMessageObject.isEditing())) {
                if (currentMessageObject.messageOwner.attachPath != null && currentMessageObject.messageOwner.attachPath.length() > 0) {
                    DownloadController.getInstance(currentAccount).addLoadingFileObserver(currentMessageObject.messageOwner.attachPath, currentMessageObject, this);
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
                        radialProgress.setCheckBackground(false, animated);
                    } else {
                        radialProgress.setBackground(getDrawableForCurrentState(), needProgress, animated);
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
                }
            } else {
                if (currentMessageObject.messageOwner.attachPath != null && currentMessageObject.messageOwner.attachPath.length() != 0) {
                    DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                }
                if (hasMiniProgress != 0) {
                    radialProgress.setMiniProgressBackgroundColor(Theme.getColor(Theme.key_chat_inLoaderPhoto));
                    buttonState = 3;
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
                    if (currentMessageObject.needDrawBluredPreview()) {
                        buttonState = -1;
                    } else {
                        if (currentMessageObject.type == 8 && !photoImage.isAllowStartAnimation()) {
                            buttonState = 2;
                        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                            buttonState = 3;
                        } else {
                            buttonState = -1;
                        }
                    }
                    radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                    if (!fromSet && photoNotSet) {
                        setMessageObject(currentMessageObject, currentMessagesGroup, pinnedBottom, pinnedTop);
                    }
                    invalidate();
                } else {
                    DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this);
                    float setProgress = 0;
                    boolean progressVisible = false;
                    if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                        boolean autoDownload = false;
                        if (currentMessageObject.type == 1) {
                            autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject);
                        } else if (currentMessageObject.type == 8 && MessageObject.isNewGifDocument(currentMessageObject.messageOwner.media.document)) {
                            autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject);
                        } else if (currentMessageObject.type == 5) {
                            autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject);
                        }
                        if (!cancelLoading && autoDownload) {
                            progressVisible = true;
                            buttonState = 1;
                        } else {
                            buttonState = 0;
                        }
                    } else {
                        progressVisible = true;
                        buttonState = 1;
                        Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                        setProgress = progress != null ? progress : 0;
                    }
                    radialProgress.setBackground(getDrawableForCurrentState(), progressVisible, animated);
                    radialProgress.setProgress(setProgress, false);
                    invalidate();
                }
            }
        }
        if (hasMiniProgress == 0) {
            radialProgress.setMiniBackground(null, false, animated);
        }
    }

    private void didPressedMiniButton(boolean animated) {
        if (miniButtonState == 0) {
            miniButtonState = 1;
            radialProgress.setProgress(0, false);
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                FileLoader.getInstance(currentAccount).loadFile(documentAttach, true, 0);
            } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                FileLoader.getInstance(currentAccount).loadFile(documentAttach, true, currentMessageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
            }
            radialProgress.setMiniBackground(getMiniDrawableForCurrentState(), true, false);
            invalidate();
        } else if (miniButtonState == 1) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
                    MediaController.getInstance().cleanupPlayer(true, true);
                }
            }
            miniButtonState = 0;
            FileLoader.getInstance(currentAccount).cancelLoadFile(documentAttach);
            radialProgress.setMiniBackground(getMiniDrawableForCurrentState(), true, false);
            invalidate();
        }
    }

    private void didPressedButton(boolean animated) {
        if (buttonState == 0) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                if (miniButtonState == 0) {
                    FileLoader.getInstance(currentAccount).loadFile(documentAttach, true, 0);
                }
                if (delegate.needPlayMessage(currentMessageObject)) {
                    if (hasMiniProgress == 2 && miniButtonState != 1) {
                        miniButtonState = 1;
                        radialProgress.setProgress(0, false);
                        radialProgress.setMiniBackground(getMiniDrawableForCurrentState(), true, false);
                    }
                    updatePlayingMessageProgress();
                    buttonState = 1;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            } else {
                cancelLoading = false;
                radialProgress.setProgress(0, false);
                if (currentMessageObject.type == 1) {
                    photoImage.setForceLoading(true);
                    photoImage.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, currentPhotoObject.size, null, currentMessageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
                } else if (currentMessageObject.type == 8) {
                    currentMessageObject.gifState = 2;
                    photoImage.setForceLoading(true);
                    photoImage.setImage(currentMessageObject.messageOwner.media.document, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilterThumb, currentMessageObject.messageOwner.media.document.size, null, 0);
                } else if (currentMessageObject.isRoundVideo()) {
                    if (currentMessageObject.isSecretMedia()) {
                        FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.getDocument(), true, 1);
                    } else {
                        currentMessageObject.gifState = 2;
                        TLRPC.Document document = currentMessageObject.getDocument();
                        photoImage.setForceLoading(true);
                        photoImage.setImage(document, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilterThumb, document.size, null, 0);
                    }
                } else if (currentMessageObject.type == 9) {
                    FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.messageOwner.media.document, false, 0);
                } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                    FileLoader.getInstance(currentAccount).loadFile(documentAttach, true, currentMessageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
                } else if (currentMessageObject.type == 0 && documentAttachType != DOCUMENT_ATTACH_TYPE_NONE) {
                    if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                        photoImage.setForceLoading(true);
                        photoImage.setImage(currentMessageObject.messageOwner.media.webpage.document, null, currentPhotoObject.location, currentPhotoFilterThumb, currentMessageObject.messageOwner.media.webpage.document.size, null, 0);
                        currentMessageObject.gifState = 2;
                    } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        FileLoader.getInstance(currentAccount).loadFile(currentMessageObject.messageOwner.media.webpage.document, false, 0);
                    }
                } else {
                    photoImage.setForceLoading(true);
                    photoImage.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, 0, null, 0);
                }
                buttonState = 1;
                radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
                invalidate();
            }
        } else if (buttonState == 1) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                boolean result = MediaController.getInstance().pauseMessage(currentMessageObject);
                if (result) {
                    buttonState = 0;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            } else {
                if (currentMessageObject.isOut() && (currentMessageObject.isSending() || currentMessageObject.isEditing())) {
                    if (!radialProgress.isDrawCheckDrawable()) {
                        delegate.didPressedCancelSendButton(this);
                    }
                } else {
                    cancelLoading = true;
                    if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        FileLoader.getInstance(currentAccount).cancelLoadFile(documentAttach);
                    } else if (currentMessageObject.type == 0 || currentMessageObject.type == 1 || currentMessageObject.type == 8 || currentMessageObject.type == 5) {
                        ImageLoader.getInstance().cancelForceLoadingForImageReceiver(photoImage);
                        photoImage.cancelLoadImage();
                    } else if (currentMessageObject.type == 9) {
                        FileLoader.getInstance(currentAccount).cancelLoadFile(currentMessageObject.messageOwner.media.document);
                    }
                    buttonState = 0;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                    invalidate();
                }
            }
        } else if (buttonState == 2) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                radialProgress.setProgress(0, false);
                FileLoader.getInstance(currentAccount).loadFile(documentAttach, true, 0);
                buttonState = 4;
                radialProgress.setBackground(getDrawableForCurrentState(), true, false);
                invalidate();
            } else {
                photoImage.setAllowStartAnimation(true);
                photoImage.startAnimation();
                currentMessageObject.gifState = 0;
                buttonState = -1;
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            }
        } else if (buttonState == 3) {
            if (hasMiniProgress == 2 && miniButtonState != 1) {
                miniButtonState = 1;
                radialProgress.setProgress(0, false);
                radialProgress.setMiniBackground(getMiniDrawableForCurrentState(), true, false);
            }
            delegate.didPressedImage(this);
        } else if (buttonState == 4) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                if (currentMessageObject.isOut() && (currentMessageObject.isSending() || currentMessageObject.isEditing()) || currentMessageObject.isSendError()) {
                    if (delegate != null) {
                        delegate.didPressedCancelSendButton(this);
                    }
                } else {
                    FileLoader.getInstance(currentAccount).cancelLoadFile(documentAttach);
                    buttonState = 2;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            }
        }
    }

    @Override
    public void onFailedDownload(String fileName) {
        updateButtonState(documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC, false);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            updateButtonState(true, false);
            updateWaveform();
        } else {
            radialProgress.setProgress(1, true);
            if (currentMessageObject.type == 0) {
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && currentMessageObject.gifState != 1) {
                    buttonState = 2;
                    didPressedButton(true);
                } else if (!photoNotSet) {
                    updateButtonState(true, false);
                } else {
                    setMessageObject(currentMessageObject, currentMessagesGroup, pinnedBottom, pinnedTop);
                }
            } else {
                if (!photoNotSet || (currentMessageObject.type == 8 || currentMessageObject.type == 5) && currentMessageObject.gifState != 1) {
                    if ((currentMessageObject.type == 8 || currentMessageObject.type == 5) && currentMessageObject.gifState != 1) {
                        photoNotSet = false;
                        buttonState = 2;
                        didPressedButton(true);
                    } else {
                        updateButtonState(true, false);
                    }
                }
                if (photoNotSet) {
                    setMessageObject(currentMessageObject, currentMessagesGroup, pinnedBottom, pinnedTop);
                }
            }
        }
    }

    @Override
    public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb) {
        if (currentMessageObject != null && (currentMessageObject.type == 0 || currentMessageObject.type == 1 || currentMessageObject.type == 5 || currentMessageObject.type == 8) && set && !thumb && !currentMessageObject.mediaExists && !currentMessageObject.attachPathExists) {
            currentMessageObject.mediaExists = true;
            updateButtonState(true, false);
        }
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        radialProgress.setProgress(progress, true);
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (hasMiniProgress != 0) {
                if (miniButtonState != 1) {
                    updateButtonState(false, false);
                }
            } else {
                if (buttonState != 4) {
                    updateButtonState(false, false);
                }
            }
        } else {
            if (hasMiniProgress != 0) {
                if (miniButtonState != 1) {
                    updateButtonState(false, false);
                }
            } else {
                if (buttonState != 1) {
                    updateButtonState(false, false);
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
                radialProgress.setCheckBackground(false, true);
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
        if (messageObject.messageOwner.post_author != null) {
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
        if (messageObject.isLiveLocation() || messageObject.getDialogId() == 777000 || messageObject.messageOwner.via_bot_id != 0 || messageObject.messageOwner.via_bot_name != null || author != null && author.bot) {
            edited = false;
        } else if (currentPosition == null || currentMessagesGroup == null) {
            edited = (messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_EDITED) != 0 || messageObject.isEditing();
        } else {
            edited = false;
            for (int a = 0, size = currentMessagesGroup.messages.size(); a < size; a++){
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
        if (signString != null) {
            if (availableTimeWidth == 0) {
                availableTimeWidth = AndroidUtilities.dp(1000);
            }
            int widthForSign = availableTimeWidth - timeWidth;
            if (messageObject.isOutOwner()) {
                if (messageObject.type == 5) {
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

    private boolean isDrawSelectedBackground() {
        return isPressed() && isCheckPressed || !isCheckPressed && isPressed || isHighlighted;
    }

    private boolean isOpenChatByShare(MessageObject messageObject) {
        return messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.saved_from_peer != null;
    }

    private boolean checkNeedDrawShareButton(MessageObject messageObject) {
        if (currentPosition != null && !currentPosition.last) {
            return false;
        } else if (messageObject.eventId != 0) {
            return false;
        } else if (messageObject.messageOwner.fwd_from != null && !messageObject.isOutOwner() && messageObject.messageOwner.fwd_from.saved_from_peer != null && messageObject.getDialogId() == UserConfig.getInstance(currentAccount).getClientUserId()) {
            drwaShareGoIcon = true;
            return true;
        } else if (messageObject.type == 13) {
            return false;
        } else if (messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.channel_id != 0 && !messageObject.isOutOwner()) {
            return true;
        } else if (messageObject.isFromUser()) {
            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty || messageObject.messageOwner.media == null || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && !(messageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage)) {
                return false;
            }
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.from_id);
            if (user != null && user.bot) {
                return true;
            }
            if (!messageObject.isOut()) {
                if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                    return true;
                }
                if (messageObject.isMegagroup()) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(messageObject.messageOwner.to_id.channel_id);
                    return chat != null && chat.username != null && chat.username.length() > 0 && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo);
                }
            }
        } else if (messageObject.messageOwner.from_id < 0 || messageObject.messageOwner.post) {
            if (messageObject.messageOwner.to_id.channel_id != 0 && (messageObject.messageOwner.via_bot_id == 0 && messageObject.messageOwner.reply_to_msg_id == 0 || messageObject.type != 13)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInsideBackground(float x, float y) {
        return currentBackgroundDrawable != null && x >= getLeft() + backgroundDrawableLeft && x <= getLeft() + backgroundDrawableLeft + backgroundDrawableRight;
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
        } else if (currentMessageObject.isFromUser()) {
            currentUser = messagesController.getUser(currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.from_id < 0) {
            currentChat = messagesController.getChat(-currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.post) {
            currentChat = messagesController.getChat(currentMessageObject.messageOwner.to_id.channel_id);
        }
    }

    private void setMessageObjectInternal(MessageObject messageObject) {
        if ((messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
            if (!currentMessageObject.viewsReloaded) {
                MessagesController.getInstance(currentAccount).addToViewsQueue(currentMessageObject.messageOwner);
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
            } else if (currentChat != null) {
                if (currentChat.photo != null) {
                    currentPhoto = currentChat.photo.photo_small;
                } else {
                    currentPhoto = null;
                }
                avatarDrawable.setInfo(currentChat);
            } else {
                currentPhoto = null;
                avatarDrawable.setInfo(messageObject.messageOwner.from_id, null, null, false);
            }
            avatarImage.setImage(currentPhoto, "50_50", avatarDrawable, null, 0);
        }


        measureTime(messageObject);

        namesOffset = 0;

        String viaUsername = null;
        CharSequence viaString = null;
        if (messageObject.messageOwner.via_bot_id != 0) {
            TLRPC.User botUser = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.via_bot_id);
            if (botUser != null && botUser.username != null && botUser.username.length() > 0) {
                viaUsername = "@" + botUser.username;
                viaString = AndroidUtilities.replaceTags(String.format(" via <b>%s</b>", viaUsername));
                viaWidth = (int) Math.ceil(Theme.chat_replyNamePaint.measureText(viaString, 0, viaString.length()));
                currentViaBotUser = botUser;
            }
        } else if (messageObject.messageOwner.via_bot_name != null && messageObject.messageOwner.via_bot_name.length() > 0) {
            viaUsername = "@" + messageObject.messageOwner.via_bot_name;
            viaString = AndroidUtilities.replaceTags(String.format(" via <b>%s</b>", viaUsername));
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
            if (currentUser != null && !currentMessageObject.isOutOwner() && currentMessageObject.type != 13 && currentMessageObject.type != 5 && delegate.isChatAdminCell(currentUser.id)) {
                adminString = LocaleController.getString("ChatAdmin", R.string.ChatAdmin);
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
                if (currentMessageObject.type == 13 || currentMessageObject.type == 5) {
                    color = Theme.getColor(Theme.key_chat_stickerViaBotNameText);
                } else {
                    color = Theme.getColor(currentMessageObject.isOutOwner() ? Theme.key_chat_outViaBotNameText : Theme.key_chat_inViaBotNameText);
                }
                if (currentNameString.length() > 0) {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format("%s via %s", nameStringFinal, viaUsername));
                    stringBuilder.setSpan(new TypefaceSpan(Typeface.DEFAULT, 0, color), nameStringFinal.length() + 1, nameStringFinal.length() + 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, color), nameStringFinal.length() + 5, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    nameStringFinal = stringBuilder;
                } else {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format("via %s", viaUsername));
                    stringBuilder.setSpan(new TypefaceSpan(Typeface.DEFAULT, 0, color), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, color), 4, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    nameStringFinal = stringBuilder;
                }
                nameStringFinal = TextUtils.ellipsize(nameStringFinal, Theme.chat_namePaint, nameWidth, TextUtils.TruncateAt.END);
            }
            try {
                nameLayout = new StaticLayout(nameStringFinal, Theme.chat_namePaint, nameWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (nameLayout != null && nameLayout.getLineCount() > 0) {
                    nameWidth = (int) Math.ceil(nameLayout.getLineWidth(0));
                    if (messageObject.type != 13) {
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

            if (currentForwardUser != null || currentForwardChannel != null) {
                if (currentForwardChannel != null) {
                    if (currentForwardUser != null) {
                        currentForwardNameString = String.format("%s (%s)", currentForwardChannel.title, UserObject.getUserName(currentForwardUser));
                    } else {
                        currentForwardNameString = currentForwardChannel.title;
                    }
                } else if (currentForwardUser != null) {
                    currentForwardNameString = UserObject.getUserName(currentForwardUser);
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
                    stringBuilder = new SpannableStringBuilder(String.format("%s via %s", fromString, viaUsername));
                    viaNameWidth = (int) Math.ceil(Theme.chat_forwardNamePaint.measureText(fromString));
                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), stringBuilder.length() - viaUsername.length() - 1, stringBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    stringBuilder = new SpannableStringBuilder(String.format(fromFormattedString, name));
                }
                if (idx >= 0) {
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
                if (messageObject.type != 13 && messageObject.type != 5) {
                    namesOffset += AndroidUtilities.dp(42);
                    if (messageObject.type != 0) {
                        namesOffset += AndroidUtilities.dp(5);
                    }
                }

                int maxWidth = getMaxNameWidth();
                if (messageObject.type != 13 && messageObject.type != 5) {
                    maxWidth -= AndroidUtilities.dp(10);
                } else if (messageObject.type == 5) {
                    maxWidth += AndroidUtilities.dp(13);
                }

                CharSequence stringFinalText = null;

                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject.photoThumbs2, 80);
                if (photoSize == null) {
                    photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject.photoThumbs, 80);
                }
                if (photoSize == null || messageObject.replyMessageObject.type == 13 || messageObject.type == 13 && !AndroidUtilities.isTablet() || messageObject.replyMessageObject.isSecretMedia()) {
                    replyImageReceiver.setImageBitmap((Drawable) null);
                    needReplyImage = false;
                } else {
                    if (messageObject.replyMessageObject.isRoundVideo()) {
                        replyImageReceiver.setRoundRadius(AndroidUtilities.dp(22));
                    } else {
                        replyImageReceiver.setRoundRadius(0);
                    }
                    currentReplyPhoto = photoSize.location;
                    replyImageReceiver.setImage(photoSize.location, "50_50", null, null, 1);
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
                        replyTextLayout = new StaticLayout(stringFinalText, Theme.chat_replyTextPaint, maxWidth + AndroidUtilities.dp(6), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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
        if (currentMessageObject.type == 5) {
            Theme.chat_timePaint.setColor(Theme.getColor(Theme.key_chat_mediaTimeText));
        } else {
            if (mediaBackground) {
                if (currentMessageObject.type == 13 || currentMessageObject.type == 5) {
                    Theme.chat_timePaint.setColor(Theme.getColor(Theme.key_chat_serviceText));
                } else {
                    Theme.chat_timePaint.setColor(Theme.getColor(Theme.key_chat_mediaTimeText));
                }
            } else {
                if (currentMessageObject.isOutOwner()) {
                    Theme.chat_timePaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_outTimeSelectedText : Theme.key_chat_outTimeText));
                } else {
                    Theme.chat_timePaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_inTimeSelectedText : Theme.key_chat_inTimeText));
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
        if (drawBackground && currentBackgroundDrawable != null) {
            if (isHighlightedAnimated) {
                currentBackgroundDrawable.draw(canvas);
                float alpha = highlightProgress >= 300 ? 1.0f : highlightProgress / 300.0f;
                if (currentPosition == null) {
                    currentBackgroundSelectedDrawable.setAlpha((int) (alpha * 255));
                    currentBackgroundSelectedDrawable.draw(canvas);
                }
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
            } else {
                if (isDrawSelectedBackground() && (currentPosition == null || getBackground() != null)) {
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

        drawContent(canvas);

        if (drawShareButton) {
            Theme.chat_shareDrawable.setColorFilter(sharePressed ? Theme.colorPressedFilter : Theme.colorFilter);
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
                setDrawableBounds(Theme.chat_shareIconDrawable, shareStartX + AndroidUtilities.dp(9), shareStartY + AndroidUtilities.dp(9));
                Theme.chat_shareIconDrawable.draw(canvas);
            }
        }

        if (currentPosition == null) {
            drawNamesLayout(canvas);
        }

        if ((drawTime || !mediaBackground) && !forceNotDrawTime) {
            drawTimeLayout(canvas);
        }

        if (controlsAlpha != 1.0f || timeAlpha != 1.0f) {
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

    public void drawNamesLayout(Canvas canvas) {
        if (drawNameLayout && nameLayout != null) {
            canvas.save();

            if (currentMessageObject.type == 13 || currentMessageObject.type == 5) {
                Theme.chat_namePaint.setColor(Theme.getColor(Theme.key_chat_stickerNameText));
                int backWidth;
                if (currentMessageObject.isOutOwner()) {
                    nameX = AndroidUtilities.dp(28);
                } else {
                    nameX = backgroundDrawableLeft + backgroundDrawableRight + AndroidUtilities.dp(22);
                }
                nameY = layoutHeight - AndroidUtilities.dp(38);
                Theme.chat_systemDrawable.setColorFilter(Theme.colorFilter);
                Theme.chat_systemDrawable.setBounds((int) nameX - AndroidUtilities.dp(12), (int) nameY - AndroidUtilities.dp(5), (int) nameX + AndroidUtilities.dp(12) + nameWidth, (int) nameY + AndroidUtilities.dp(22));
                Theme.chat_systemDrawable.draw(canvas);
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
                        Theme.chat_namePaint.setColor(AvatarDrawable.getNameColorForId(5));
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
                Theme.chat_adminPaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_adminSelectedText : Theme.key_chat_adminText));
                canvas.save();
                canvas.translate(backgroundDrawableLeft + backgroundDrawableRight - AndroidUtilities.dp(11) - adminLayout.getLineWidth(0), nameY + AndroidUtilities.dp(0.5f));
                adminLayout.draw(canvas);
                canvas.restore();
            }
        }

        if (drawForwardedName && forwardedNameLayout[0] != null && forwardedNameLayout[1] != null && (currentPosition == null || currentPosition.minY == 0 && currentPosition.minX == 0)) {
            if (currentMessageObject.type == 5) {
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
            if (currentMessageObject.type == 13 || currentMessageObject.type == 5) {
                Theme.chat_replyLinePaint.setColor(Theme.getColor(Theme.key_chat_stickerReplyLine));
                Theme.chat_replyNamePaint.setColor(Theme.getColor(Theme.key_chat_stickerReplyNameText));
                Theme.chat_replyTextPaint.setColor(Theme.getColor(Theme.key_chat_stickerReplyMessageText));
                if (currentMessageObject.isOutOwner()) {
                    replyStartX = AndroidUtilities.dp(23);
                } else if (currentMessageObject.type == 5) {
                    replyStartX = backgroundDrawableLeft + backgroundDrawableRight + AndroidUtilities.dp(4);
                } else {
                    replyStartX = backgroundDrawableLeft + backgroundDrawableRight + AndroidUtilities.dp(17);
                }
                replyStartY = AndroidUtilities.dp(12);
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
                        Theme.chat_replyTextPaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_outReplyMediaMessageSelectedText : Theme.key_chat_outReplyMediaMessageText));
                    }
                    replyStartX = backgroundDrawableLeft + AndroidUtilities.dp(12);
                } else {
                    Theme.chat_replyLinePaint.setColor(Theme.getColor(Theme.key_chat_inReplyLine));
                    Theme.chat_replyNamePaint.setColor(Theme.getColor(Theme.key_chat_inReplyNameText));
                    if (currentMessageObject.hasValidReplyMessageObject() && currentMessageObject.replyMessageObject.type == 0 && !(currentMessageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame || currentMessageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice)) {
                        Theme.chat_replyTextPaint.setColor(Theme.getColor(Theme.key_chat_inReplyMessageText));
                    } else {
                        Theme.chat_replyTextPaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_inReplyMediaMessageSelectedText : Theme.key_chat_inReplyMediaMessageText));
                    }
                    if (mediaBackground) {
                        replyStartX = backgroundDrawableLeft + AndroidUtilities.dp(12);
                    } else {
                        replyStartX = backgroundDrawableLeft + AndroidUtilities.dp(!mediaBackground && drawPinnedBottom ? 12 : 18);
                    }
                }
                replyStartY = AndroidUtilities.dp(12 + (drawForwardedName && forwardedNameLayout[0] != null ? 36 : 0) + (drawNameLayout && nameLayout != null ? 20 : 0));
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

    public void drawCaptionLayout(Canvas canvas, boolean selectionOnly) {
        if (captionLayout == null || selectionOnly && pressedLink == null) {
            return;
        }
        canvas.save();
        canvas.translate(captionX, captionY);
        if (pressedLink != null) {
            for (int b = 0; b < urlPath.size(); b++) {
                canvas.drawPath(urlPath.get(b), Theme.chat_urlPaint);
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

    public void drawTimeLayout(Canvas canvas) {
        if ((!drawTime || groupPhotoInvisible) && mediaBackground && captionLayout == null || timeLayout == null) {
            return;
        }
        if (currentMessageObject.type == 5) {
            Theme.chat_timePaint.setColor(Theme.getColor(Theme.key_chat_mediaTimeText));
        } else {
            if (mediaBackground && captionLayout == null) {
                if (currentMessageObject.type == 13 || currentMessageObject.type == 5) {
                    Theme.chat_timePaint.setColor(Theme.getColor(Theme.key_chat_serviceText));
                } else {
                    Theme.chat_timePaint.setColor(Theme.getColor(Theme.key_chat_mediaTimeText));
                }
            } else {
                if (currentMessageObject.isOutOwner()) {
                    Theme.chat_timePaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_outTimeSelectedText : Theme.key_chat_outTimeText));
                } else {
                    Theme.chat_timePaint.setColor(Theme.getColor(isDrawSelectedBackground() ? Theme.key_chat_inTimeSelectedText : Theme.key_chat_inTimeText));
                }
            }
        }
        if (drawPinnedBottom) {
            canvas.translate(0, AndroidUtilities.dp(2));
        }
        if (mediaBackground && captionLayout == null) {
            Paint paint;
            if (currentMessageObject.type == 13 || currentMessageObject.type == 5) {
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
            if ((currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                additionalX += (int) (timeWidth - timeLayout.getLineWidth(0));

                if (currentMessageObject.isSending() || currentMessageObject.isEditing()) {
                    if (!currentMessageObject.isOutOwner()) {
                        setDrawableBounds(Theme.chat_msgMediaClockDrawable, timeX + AndroidUtilities.dp(11), layoutHeight - AndroidUtilities.dp(14.0f) - Theme.chat_msgMediaClockDrawable.getIntrinsicHeight());
                        Theme.chat_msgMediaClockDrawable.draw(canvas);
                    }
                } else if (currentMessageObject.isSendError()) {
                    if (!currentMessageObject.isOutOwner()) {
                        int x = timeX + AndroidUtilities.dp(11);
                        int y = layoutHeight - AndroidUtilities.dp(27.5f);
                        rect.set(x, y, x + AndroidUtilities.dp(14), y + AndroidUtilities.dp(14));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), Theme.chat_msgErrorPaint);
                        setDrawableBounds(Theme.chat_msgErrorDrawable, x + AndroidUtilities.dp(6), y + AndroidUtilities.dp(2));
                        Theme.chat_msgErrorDrawable.draw(canvas);
                    }
                } else {
                    Drawable viewsDrawable;
                    if (currentMessageObject.type == 13 || currentMessageObject.type == 5) {
                        viewsDrawable = Theme.chat_msgStickerViewsDrawable;
                    } else {
                        viewsDrawable = Theme.chat_msgMediaViewsDrawable;
                    }
                    oldAlpha = ((BitmapDrawable) viewsDrawable).getPaint().getAlpha();
                    viewsDrawable.setAlpha((int) (timeAlpha * oldAlpha));
                    setDrawableBounds(viewsDrawable, timeX, layoutHeight - AndroidUtilities.dp(10.5f) - timeLayout.getHeight());
                    viewsDrawable.draw(canvas);
                    viewsDrawable.setAlpha(oldAlpha);

                    if (viewsLayout != null) {
                        canvas.save();
                        canvas.translate(timeX + viewsDrawable.getIntrinsicWidth() + AndroidUtilities.dp(3), layoutHeight - AndroidUtilities.dp(12.3f) - timeLayout.getHeight());
                        viewsLayout.draw(canvas);
                        canvas.restore();
                    }
                }
            }

            canvas.save();
            canvas.translate(timeX + additionalX, layoutHeight - AndroidUtilities.dp(12.3f) - timeLayout.getHeight());
            timeLayout.draw(canvas);
            canvas.restore();
            Theme.chat_timePaint.setAlpha(255);
        } else {
            int additionalX = (int) (-timeLayout.getLineLeft(0));
            if ((currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                additionalX += (int) (timeWidth - timeLayout.getLineWidth(0));

                if (currentMessageObject.isSending() || currentMessageObject.isEditing()) {
                    if (!currentMessageObject.isOutOwner()) {
                        Drawable clockDrawable = isDrawSelectedBackground() ? Theme.chat_msgInSelectedClockDrawable : Theme.chat_msgInClockDrawable;
                        setDrawableBounds(clockDrawable, timeX + AndroidUtilities.dp(11), layoutHeight - AndroidUtilities.dp(8.5f) - clockDrawable.getIntrinsicHeight());
                        clockDrawable.draw(canvas);
                    }
                } else if (currentMessageObject.isSendError()) {
                    if (!currentMessageObject.isOutOwner()) {
                        int x = timeX + AndroidUtilities.dp(11);
                        int y = layoutHeight - AndroidUtilities.dp(20.5f);
                        rect.set(x, y, x + AndroidUtilities.dp(14), y + AndroidUtilities.dp(14));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), Theme.chat_msgErrorPaint);
                        setDrawableBounds(Theme.chat_msgErrorDrawable, x + AndroidUtilities.dp(6), y + AndroidUtilities.dp(2));
                        Theme.chat_msgErrorDrawable.draw(canvas);
                    }
                } else {
                    if (!currentMessageObject.isOutOwner()) {
                        Drawable viewsDrawable = isDrawSelectedBackground() ? Theme.chat_msgInViewsSelectedDrawable : Theme.chat_msgInViewsDrawable;
                        setDrawableBounds(viewsDrawable, timeX, layoutHeight - AndroidUtilities.dp(4.5f) - timeLayout.getHeight());
                        viewsDrawable.draw(canvas);
                    } else {
                        Drawable viewsDrawable = isDrawSelectedBackground() ? Theme.chat_msgOutViewsSelectedDrawable : Theme.chat_msgOutViewsDrawable;
                        setDrawableBounds(viewsDrawable, timeX, layoutHeight - AndroidUtilities.dp(4.5f) - timeLayout.getHeight());
                        viewsDrawable.draw(canvas);
                    }

                    if (viewsLayout != null) {
                        canvas.save();
                        canvas.translate(timeX + Theme.chat_msgInViewsDrawable.getIntrinsicWidth() + AndroidUtilities.dp(3), layoutHeight - AndroidUtilities.dp(6.5f) - timeLayout.getHeight());
                        viewsLayout.draw(canvas);
                        canvas.restore();
                    }
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
                if (!currentMessageObject.isUnread()) {
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
                    if (currentMessageObject.type == 13 || currentMessageObject.type == 5) {
                        setDrawableBounds(Theme.chat_msgStickerClockDrawable, layoutWidth - AndroidUtilities.dp(22.0f) - Theme.chat_msgStickerClockDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.5f) - Theme.chat_msgStickerClockDrawable.getIntrinsicHeight());
                        Theme.chat_msgStickerClockDrawable.draw(canvas);
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
                        if (currentMessageObject.type == 13 || currentMessageObject.type == 5) {
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
                        Drawable drawable = isDrawSelectedBackground() ? Theme.chat_msgOutCheckSelectedDrawable : Theme.chat_msgOutCheckDrawable;
                        if (drawCheck1) {
                            setDrawableBounds(drawable, layoutWidth - AndroidUtilities.dp(22.5f) - drawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - drawable.getIntrinsicHeight());
                        } else {
                            setDrawableBounds(drawable, layoutWidth - AndroidUtilities.dp(18.5f) - drawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - drawable.getIntrinsicHeight());
                        }
                        drawable.draw(canvas);
                    }
                }
                if (drawCheck1) {
                    if (mediaBackground && captionLayout == null) {
                        if (currentMessageObject.type == 13 || currentMessageObject.type == 5) {
                            setDrawableBounds(Theme.chat_msgStickerHalfCheckDrawable, layoutWidth - AndroidUtilities.dp(21.5f) - Theme.chat_msgStickerHalfCheckDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.5f) - Theme.chat_msgStickerHalfCheckDrawable.getIntrinsicHeight());
                            Theme.chat_msgStickerHalfCheckDrawable.draw(canvas);
                        } else {
                            setDrawableBounds(Theme.chat_msgMediaHalfCheckDrawable, layoutWidth - AndroidUtilities.dp(21.5f) - Theme.chat_msgMediaHalfCheckDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.5f) - Theme.chat_msgMediaHalfCheckDrawable.getIntrinsicHeight());
                            Theme.chat_msgMediaHalfCheckDrawable.setAlpha((int) (255 * timeAlpha));
                            Theme.chat_msgMediaHalfCheckDrawable.draw(canvas);
                            Theme.chat_msgMediaHalfCheckDrawable.setAlpha(255);
                        }
                    } else {
                        Drawable drawable = isDrawSelectedBackground() ? Theme.chat_msgOutHalfCheckSelectedDrawable : Theme.chat_msgOutHalfCheckDrawable;
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

    @Override
    public int getObserverTag() {
        return TAG;
    }

    public MessageObject getMessageObject() {
        return currentMessageObject;
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
}
