/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.ViewStructure;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
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
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.SeekBarWaveform;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import static org.telegram.messenger.AndroidUtilities.*;

public class ChatMessageCell extends BaseCell implements SeekBar.SeekBarDelegate, ImageReceiver.ImageReceiverDelegate, MediaController.FileDownloadProgressListener {

    public interface ChatMessageCellDelegate {
        void didPressedUserAvatar(ChatMessageCell cell, TLRPC.User user);
        void didPressedViaBot(ChatMessageCell cell, String username);
        void didPressedChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId);
        void didPressedCancelSendButton(ChatMessageCell cell);
        void didLongPressed(ChatMessageCell cell);
        void didPressedReplyMessage(ChatMessageCell cell, int id);
        void didPressedUrl(MessageObject messageObject, ClickableSpan url, boolean longPress);
        void needOpenWebView(String url, String title, String description, String originalUrl, int w, int h);
        void didPressedImage(ChatMessageCell cell);
        void didPressedShare(ChatMessageCell cell);
        void didPressedOther(ChatMessageCell cell);
        void didPressedBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button);
        boolean needPlayAudio(MessageObject messageObject);
        boolean canPerformActions();
    }

    private final static int DOCUMENT_ATTACH_TYPE_NONE = 0;
    private final static int DOCUMENT_ATTACH_TYPE_DOCUMENT = 1;
    private final static int DOCUMENT_ATTACH_TYPE_GIF = 2;
    private final static int DOCUMENT_ATTACH_TYPE_AUDIO = 3;
    private final static int DOCUMENT_ATTACH_TYPE_VIDEO = 4;
    private final static int DOCUMENT_ATTACH_TYPE_MUSIC = 5;
    private final static int DOCUMENT_ATTACH_TYPE_STICKER = 6;

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

    private int textX;
    private int textY;
    private int totalHeight;
    private int keyboardHeight;
    private int linkBlockNum;
    private int linkSelectionBlockNum;

    private boolean inLayout;

    private Rect scrollRect = new Rect();

    private int lastVisibleBlockNum;
    private int firstVisibleBlockNum;
    private int totalVisibleBlocksCount;
    private boolean needNewVisiblePart;

    private RadialProgress radialProgress;
    private ImageReceiver photoImage;
    private AvatarDrawable contactAvatarDrawable;

    private boolean disallowLongPress;

    private boolean isSmallImage;
    private boolean drawImageButton;
    private int documentAttachType;
    private TLRPC.Document documentAttach;
    private boolean drawPhotoImage;
    private boolean hasLinkPreview;
    private boolean hasGamePreview;
    private int linkPreviewHeight;
    private int mediaOffsetY;
    private int descriptionY;
    private int durationWidth;
    private int descriptionX;
    private int titleX;
    private int authorX;
    private StaticLayout siteNameLayout;
    private StaticLayout titleLayout;
    private StaticLayout descriptionLayout;
    private StaticLayout videoInfoLayout;
    private StaticLayout authorLayout;

    private StaticLayout docTitleLayout;
    private int docTitleOffsetX;

    private StaticLayout captionLayout;
    private int captionX;
    private int captionY;
    private int captionHeight;

    private StaticLayout infoLayout;
    private int infoWidth;

    private String currentUrl;

    private int buttonX;
    private int buttonY;
    private int buttonState;
    private int buttonPressed;
    private int otherX;
    private int otherY;
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

    private static TextPaint infoPaint;
    private static TextPaint docNamePaint;
    private static Paint docBackPaint;
    private static Paint deleteProgressPaint;
    private static Paint botProgressPaint;
    private static TextPaint locationTitlePaint;
    private static TextPaint locationAddressPaint;
    private static Paint urlPaint;
    private static Paint urlSelectionPaint;
    private static TextPaint durationPaint;
    private static TextPaint gamePaint;

    private ClickableSpan pressedLink;
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
    private String lastTimeString;
    private int timeWidthAudio;
    private int timeAudioX;

    private static TextPaint audioTimePaint;
    private static TextPaint audioTitlePaint;
    private static TextPaint audioPerformerPaint;
    private static TextPaint botButtonPaint;
    private static TextPaint contactNamePaint;
    private static TextPaint contactPhonePaint;

    private StaticLayout songLayout;
    private int songX;

    private StaticLayout performerLayout;
    private int performerX;

    private ArrayList<BotButton> botButtons = new ArrayList<>();
    private HashMap<String, BotButton> botButtonsByData = new HashMap<>();
    private int widthForButtons;
    private int pressedBotButton;

    //
    private int TAG;

    public boolean isChat;
    private boolean isPressed;
    private boolean forwardName;
    private boolean isHighlighted;
    private boolean mediaBackground;
    private boolean isCheckPressed = true;
    private boolean wasLayout;
    private boolean isAvatarVisible;
    private boolean drawBackground = true;
    private int substractBackgroundHeight;
    private boolean allowAssistant;
    private Drawable currentBackgroundDrawable;
    private int backgroundDrawableLeft;
    private MessageObject currentMessageObject;
    private int viaWidth;
    private int viaNameWidth;
    private int availableTimeWidth;

    private static TextPaint timePaint;
    private static TextPaint namePaint;
    private static TextPaint forwardNamePaint;
    private static TextPaint replyNamePaint;
    private static TextPaint replyTextPaint;
    private static Paint replyLinePaint;

    private int backgroundWidth = 100;

    private int layoutWidth;
    private int layoutHeight;

    private ImageReceiver avatarImage;
    private AvatarDrawable avatarDrawable;
    private boolean avatarPressed;
    private boolean forwardNamePressed;
    private boolean forwardBotPressed;

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

    private boolean drawShareButton;
    private boolean sharePressed;
    private int shareStartX;
    private int shareStartY;

    private StaticLayout nameLayout;
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

    public ChatMessageCell(Context context) {
        super(context);
        if (infoPaint == null) {
            infoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

            docNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            docNamePaint.setTypeface(getTypeface("fonts/rmedium.ttf"));

            docBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            deleteProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            deleteProgressPaint.setColor(Theme.MSG_SECRET_TIME_TEXT_COLOR);

            botProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            botProgressPaint.setColor(Theme.MSG_BOT_PROGRESS_COLOR);
            botProgressPaint.setStrokeCap(Paint.Cap.ROUND);
            botProgressPaint.setStyle(Paint.Style.STROKE);

            locationTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            locationTitlePaint.setTypeface(getTypeface("fonts/rmedium.ttf"));

            locationAddressPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

            urlPaint = new Paint();
            urlPaint.setColor(Theme.MSG_LINK_SELECT_BACKGROUND_COLOR);

            urlSelectionPaint = new Paint();
            urlSelectionPaint.setColor(Theme.MSG_TEXT_SELECT_BACKGROUND_COLOR);

            audioTimePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);

            audioTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            audioTitlePaint.setTypeface(getTypeface("fonts/rmedium.ttf"));

            audioPerformerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

            botButtonPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            botButtonPaint.setColor(Theme.MSG_BOT_BUTTON_TEXT_COLOR);
            botButtonPaint.setTypeface(getTypeface("fonts/rmedium.ttf"));

            contactNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            contactNamePaint.setTypeface(getTypeface("fonts/rmedium.ttf"));

            contactPhonePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

            durationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            durationPaint.setColor(Theme.MSG_WEB_PREVIEW_DURATION_TEXT_COLOR);

            gamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            gamePaint.setColor(Theme.MSG_WEB_PREVIEW_GAME_TEXT_COLOR);
            gamePaint.setTypeface(getTypeface("fonts/rmedium.ttf"));

            timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);

            namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            namePaint.setTypeface(getTypeface("fonts/rmedium.ttf"));

            forwardNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);

            replyNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            replyNamePaint.setTypeface(getTypeface("fonts/rmedium.ttf"));

            replyTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            replyTextPaint.linkColor = Theme.MSG_LINK_TEXT_COLOR;

            replyLinePaint = new Paint();
        }

        botProgressPaint.setStrokeWidth(dp(2));
        infoPaint.setTextSize(dp(12));
        docNamePaint.setTextSize(dp(15));
        locationTitlePaint.setTextSize(dp(15));
        locationAddressPaint.setTextSize(dp(13));
        audioTimePaint.setTextSize(dp(12));
        audioTitlePaint.setTextSize(dp(16));
        audioPerformerPaint.setTextSize(dp(15));
        botButtonPaint.setTextSize(dp(15));
        contactNamePaint.setTextSize(dp(15));
        contactPhonePaint.setTextSize(dp(13));
        durationPaint.setTextSize(dp(12));
        timePaint.setTextSize(dp(12));
        namePaint.setTextSize(dp(14));
        forwardNamePaint.setTextSize(dp(14));
        replyNamePaint.setTextSize(dp(14));
        replyTextPaint.setTextSize(dp(14));
        gamePaint.setTextSize(dp(13));

        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(dp(21));
        avatarDrawable = new AvatarDrawable();
        replyImageReceiver = new ImageReceiver(this);
        TAG = MediaController.getInstance().generateObserverTag();

        contactAvatarDrawable = new AvatarDrawable();
        photoImage = new ImageReceiver(this);
        photoImage.setDelegate(this);
        radialProgress = new RadialProgress(this);
        seekBar = new SeekBar(context);
        seekBar.setDelegate(this);
        seekBarWaveform = new SeekBarWaveform(context);
        seekBarWaveform.setDelegate(this);
        seekBarWaveform.setParentView(this);

        radialProgress = new RadialProgress(this);
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
                    x -= textX - (int) Math.ceil(block.textXOffset);
                    y -= block.textYOffset;
                    final int line = block.textLayout.getLineForVertical(y);
                    final int off = block.textLayout.getOffsetForHorizontal(line, x) + block.charactersOffset;

                    final float left = block.textLayout.getLineLeft(line);
                    if (left <= x && left + block.textLayout.getLineWidth(line) >= x) {
                        Spannable buffer = (Spannable) currentMessageObject.messageText;
                        ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
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
                                    int start = buffer.getSpanStart(pressedLink) - block.charactersOffset;
                                    int end = buffer.getSpanEnd(pressedLink);
                                    int length = block.textLayout.getText().length();
                                    path.setCurrentLayout(block.textLayout, start, 0);
                                    block.textLayout.getSelectionPath(start, end - block.charactersOffset, path);
                                    if (end >= block.charactersOffset + length) {
                                        for (int a = blockNum + 1; a < currentMessageObject.textLayoutBlocks.size(); a++) {
                                            MessageObject.TextLayoutBlock nextBlock = currentMessageObject.textLayoutBlocks.get(a);
                                            length = nextBlock.textLayout.getText().length();
                                            ClickableSpan[] nextLink = buffer.getSpans(nextBlock.charactersOffset, nextBlock.charactersOffset, ClickableSpan.class);
                                            if (nextLink == null || nextLink.length == 0 || nextLink[0] != pressedLink) {
                                                break;
                                            }
                                            path = obtainNewUrlPath(false);
                                            path.setCurrentLayout(nextBlock.textLayout, 0, nextBlock.height);
                                            nextBlock.textLayout.getSelectionPath(0, end - nextBlock.charactersOffset, path);
                                            if (end < block.charactersOffset + length - 1) {
                                                break;
                                            }
                                        }
                                    }
                                    if (start < 0) {
                                        for (int a = blockNum - 1; a >= 0; a--) {
                                            MessageObject.TextLayoutBlock nextBlock = currentMessageObject.textLayoutBlocks.get(a);
                                            length = nextBlock.textLayout.getText().length();
                                            ClickableSpan[] nextLink = buffer.getSpans(nextBlock.charactersOffset + length - 1, nextBlock.charactersOffset + length - 1, ClickableSpan.class);
                                            if (nextLink == null || nextLink.length == 0 || nextLink[0] != pressedLink) {
                                                break;
                                            }
                                            path = obtainNewUrlPath(false);
                                            start = buffer.getSpanStart(pressedLink) - nextBlock.charactersOffset;
                                            path.setCurrentLayout(nextBlock.textLayout, start, -nextBlock.height);
                                            nextBlock.textLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink) - nextBlock.charactersOffset, path);
                                            if (start >= 0) {
                                                break;
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
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
                    FileLog.e("tmessages", e);
                }
            } else {
                resetPressedLink(1);
            }
        }
        return false;
    }

    private boolean checkCaptionMotionEvent(MotionEvent event) {
        if (!(currentMessageObject.caption instanceof Spannable) || captionLayout == null) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN || (linkPreviewPressed || pressedLink != null) && event.getAction() == MotionEvent.ACTION_UP) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            if (x >= captionX && x <= captionX + backgroundWidth && y >= captionY && y <= captionY + captionHeight) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    try {
                        x -= captionX;
                        y -= captionY;
                        final int line = captionLayout.getLineForVertical(y);
                        final int off = captionLayout.getOffsetForHorizontal(line, x);

                        final float left = captionLayout.getLineLeft(line);
                        if (left <= x && left + captionLayout.getLineWidth(line) >= x) {
                            Spannable buffer = (Spannable) currentMessageObject.caption;
                            ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
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
                                    FileLog.e("tmessages", e);
                                }
                                invalidate();
                                return true;
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
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
                    x -= textX + dp(10) + descriptionX;
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
                                FileLog.e("tmessages", e);
                            }
                            invalidate();
                            return true;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressedLinkType == 2 || gamePreviewPressed) {
                if (pressedLink != null) {
                    if (pressedLink instanceof URLSpan) {
                        Browser.openUrl(getContext(), ((URLSpan) pressedLink).getURL());
                    } else {
                        pressedLink.onClick(this);
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

        if (x >= textX && x <= textX + backgroundWidth && y >= textY + currentMessageObject.textHeight && y <= textY + currentMessageObject.textHeight + linkPreviewHeight + dp(8)) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT && drawPhotoImage && photoImage.isInsideImage(x, y)) {
                    if (drawImageButton && buttonState != -1 && x >= buttonX && x <= buttonX + dp(48) && y >= buttonY && y <= buttonY + dp(48)) {
                        buttonPressed = 1;
                        return true;
                    } else {
                        linkPreviewPressed = true;
                        TLRPC.WebPage webPage = currentMessageObject.messageOwner.media.webpage;
                        if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && buttonState == -1 && MediaController.getInstance().canAutoplayGifs() && (photoImage.getAnimation() == null || !TextUtils.isEmpty(webPage.embed_url))) {
                            linkPreviewPressed = false;
                            return false;
                        }
                        return true;
                    }
                } else if (descriptionLayout != null && y >= descriptionY) {
                    try {
                        x -= textX + dp(10) + descriptionX;
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
                                    FileLog.e("tmessages", e);
                                }
                                invalidate();
                                return true;
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pressedLinkType == 2 || buttonPressed != 0 || linkPreviewPressed) {
                    if (buttonPressed != 0) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            buttonPressed = 0;
                            playSoundEffect(SoundEffectConstants.CLICK);
                            didPressedButton(false);
                            invalidate();
                        }
                    } else if (pressedLink != null) {
                        if (pressedLink instanceof URLSpan) {
                            Browser.openUrl(getContext(), ((URLSpan) pressedLink).getURL());
                        } else {
                            pressedLink.onClick(this);
                        }
                        resetPressedLink(2);
                    } else {
                        if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && drawImageButton) {
                            if (buttonState == -1) {
                                if (MediaController.getInstance().canAutoplayGifs()) {
                                    delegate.didPressedImage(this);
                                } else {
                                    buttonState = 2;
                                    currentMessageObject.audioProgress = 1;
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
                            if (webPage != null && Build.VERSION.SDK_INT >= 16 && !TextUtils.isEmpty(webPage.embed_url)) {
                                delegate.needOpenWebView(webPage.embed_url, webPage.site_name, webPage.description, webPage.url, webPage.embed_width, webPage.embed_height);
                            } else if (buttonState == -1) {
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
            }
        }
        return false;
    }

    private boolean checkOtherButtonMotionEvent(MotionEvent event) {
        if (documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT && currentMessageObject.type != 12 && documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC && documentAttachType != DOCUMENT_ATTACH_TYPE_VIDEO && documentAttachType != DOCUMENT_ATTACH_TYPE_GIF && currentMessageObject.type != 8 || hasGamePreview) {
            return false;
        }

        int x = (int) event.getX();
        int y = (int) event.getY();

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (x >= otherX - dp(20) && x <= otherX + dp(20) && y >= otherY - dp(4) && y <= otherY + dp(30)) {
                otherPressed = true;
                result = true;
            }
        } else {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (otherPressed) {
                    otherPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    delegate.didPressedOther(this);
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
            if (buttonState != -1 && x >= buttonX && x <= buttonX + dp(48) && y >= buttonY && y <= buttonY + dp(48)) {
                buttonPressed = 1;
                invalidate();
                result = true;
            } else {
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                    if (x >= photoImage.getImageX() && x <= photoImage.getImageX() + backgroundWidth - dp(50) && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight()) {
                        imagePressed = true;
                        result = true;
                    }
                } else if (currentMessageObject.type != 13 || currentMessageObject.getInputStickerSet() != null) {
                    if (x >= photoImage.getImageX() && x <= photoImage.getImageX() + backgroundWidth && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight()) {
                        imagePressed = true;
                        result = true;
                    }
                    if (currentMessageObject.type == 12) {
                        TLRPC.User user = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.media.user_id);
                        if (user == null) {
                            imagePressed = false;
                            result = false;
                        }
                    }
                }
            }
            if (imagePressed) {
                if (currentMessageObject.isSecretPhoto()) {
                    imagePressed = false;
                } else if (currentMessageObject.isSendError()) {
                    imagePressed = false;
                    result = false;
                } else if (currentMessageObject.type == 8 && buttonState == -1 && MediaController.getInstance().canAutoplayGifs() && photoImage.getAnimation() == null) {
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
                    radialProgress.swapBackground(getDrawableForCurrentState());
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
            result = seekBarWaveform.onTouch(event.getAction(), event.getX() - seekBarX - dp(13), event.getY() - seekBarY);
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
            int side = dp(36);
            boolean area;
            if (buttonState == 0 || buttonState == 1 || buttonState == 2) {
                area = x >= buttonX - dp(12) && x <= buttonX - dp(12) + backgroundWidth && y >= namesOffset + mediaOffsetY && y <= layoutHeight;
            } else {
                area = x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (area) {
                    buttonPressed = 1;
                    invalidate();
                    result = true;
                    radialProgress.swapBackground(getDrawableForCurrentState());
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
                radialProgress.swapBackground(getDrawableForCurrentState());
            }
        }
        return result;
    }

    private boolean checkBotButtonMotionEvent(MotionEvent event) {
        if (botButtons.isEmpty()) {
            return false;
        }

        int x = (int) event.getX();
        int y = (int) event.getY();

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int addX;
            if (currentMessageObject.isOutOwner()) {
                addX = getMeasuredWidth() - widthForButtons - dp(10);
            } else {
                addX = backgroundDrawableLeft + dp(mediaBackground ? 1 : 7);
            }
            for (int a = 0; a < botButtons.size(); a++) {
                BotButton button = botButtons.get(a);
                int y2 = button.y + layoutHeight - dp(2);
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
            result = checkLinkPreviewMotionEvent(event);
        }
        if (!result) {
            result = checkGameMotionEvent(event);
        }
        if (!result) {
            result = checkCaptionMotionEvent(event);
        }
        if (!result) {
            result = checkAudioMotionEvent(event);
        }
        if (!result) {
            result = checkPhotoImageMotionEvent(event);
        }
        if (!result) {
            result = checkBotButtonMotionEvent(event);
        }

        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            buttonPressed = 0;
            pressedBotButton = -1;
            linkPreviewPressed = false;
            otherPressed = false;
            imagePressed = false;
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
                    if (isAvatarVisible && avatarImage.isInsideImage(x, y)) {
                        avatarPressed = true;
                        result = true;
                    } else if (drawForwardedName && forwardedNameLayout[0] != null && x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + dp(32)) {
                        if (viaWidth != 0 && x >= forwardNameX + viaNameWidth + dp(4)) {
                            forwardBotPressed = true;
                        } else {
                            forwardNamePressed = true;
                        }
                        result = true;
                    } else if (drawNameLayout && nameLayout != null && viaWidth != 0 && x >= nameX + viaNameWidth && x <= nameX + viaNameWidth + viaWidth && y >= nameY - dp(4) && y <= nameY + dp(20)) {
                        forwardBotPressed = true;
                        result = true;
                    } else if (currentMessageObject.isReply() && x >= replyStartX && x <= replyStartX + Math.max(replyNameWidth, replyTextWidth) && y >= replyStartY && y <= replyStartY + dp(35)) {
                        replyPressed = true;
                        result = true;
                    } else if (drawShareButton && x >= shareStartX && x <= shareStartX + dp(40) && y >= shareStartY && y <= shareStartY + dp(32)) {
                        sharePressed = true;
                        result = true;
                        invalidate();
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
                        if (isAvatarVisible && !avatarImage.isInsideImage(x, y)) {
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
                        if (!(x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + dp(32))) {
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
                            if (!(x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + dp(32))) {
                                forwardBotPressed = false;
                            }
                        } else {
                            if (!(x >= nameX + viaNameWidth && x <= nameX + viaNameWidth + viaWidth && y >= nameY - dp(4) && y <= nameY + dp(20))) {
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
                        if (!(x >= replyStartX && x <= replyStartX + Math.max(replyNameWidth, replyTextWidth) && y >= replyStartY && y <= replyStartY + dp(35))) {
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
                        if (!(x >= shareStartX && x <= shareStartX + dp(40) && y >= shareStartY && y <= shareStartY + dp(32))) {
                            sharePressed = false;
                        }
                    }
                    invalidate();
                }
            }
        }
        return result;
    }

    public void updateAudioProgress() {
        if (currentMessageObject == null || documentAttach == null) {
            return;
        }

        if (useSeekBarWaweform) {
            if (!seekBarWaveform.isDragging()) {
                seekBarWaveform.setProgress(currentMessageObject.audioProgress);
            }
        } else {
            if (!seekBar.isDragging()) {
                seekBar.setProgress(currentMessageObject.audioProgress);
            }
        }

        int duration = 0;
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
            if (!MediaController.getInstance().isPlayingAudio(currentMessageObject)) {
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
            String timeString = String.format("%02d:%02d", duration / 60, duration % 60);
            if (lastTimeString == null || lastTimeString != null && !lastTimeString.equals(timeString)) {
                lastTimeString = timeString;
                timeWidthAudio = (int) Math.ceil(audioTimePaint.measureText(timeString));
                durationLayout = new StaticLayout(timeString, audioTimePaint, timeWidthAudio, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
        } else {
            int currentProgress = 0;
            for (int a = 0; a < documentAttach.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = documentAttach.attributes.get(a);
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
                int timeWidth = (int) Math.ceil(audioTimePaint.measureText(timeString));
                durationLayout = new StaticLayout(timeString, audioTimePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
        }
        invalidate();
    }

    public void downloadAudioIfNeed() {
        if (documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO || documentAttach.size >= 1024 * 1024) {
            return;
        }
        if (buttonState == 2) {
            FileLoader.getInstance().loadFile(documentAttach, true, false);
            buttonState = 4;
            radialProgress.setBackground(getDrawableForCurrentState(), false, false);
        }
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
        return StaticLayoutEx.createStaticLayout(stringBuilder, paint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, dp(1), false, TextUtils.TruncateAt.END, maxWidth, maxLines);
    }

    private void didClickedImage() {
        if (currentMessageObject.type == 1 || currentMessageObject.type == 13) {
            if (buttonState == -1) {
                delegate.didPressedImage(this);
            } else if (buttonState == 0) {
                didPressedButton(false);
            }
        } else if (currentMessageObject.type == 12) {
            TLRPC.User user = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.media.user_id);
            delegate.didPressedUserAvatar(this, user);
        } else if (currentMessageObject.type == 8) {
            if (buttonState == -1) {
                if (MediaController.getInstance().canAutoplayGifs()) {
                    delegate.didPressedImage(this);
                } else {
                    buttonState = 2;
                    currentMessageObject.audioProgress = 1;
                    photoImage.setAllowStartAnimation(false);
                    photoImage.stopAnimation();
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            } else if (buttonState == 2 || buttonState == 0) {
                didPressedButton(false);
            }
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
            if (buttonState == 0 || buttonState == 3) {
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
                    if (Build.VERSION.SDK_INT >= 16 && webPage.embed_url != null && webPage.embed_url.length() != 0) {
                        delegate.needOpenWebView(webPage.embed_url, webPage.site_name, webPage.description, webPage.url, webPage.embed_width, webPage.embed_height);
                    } else {
                        Browser.openUrl(getContext(), webPage.url);
                    }
                }
            }
        }
    }

    private void updateSecretTimeText(MessageObject messageObject) {
        if (messageObject == null || messageObject.isOut()) {
            return;
        }
        String str = messageObject.getSecretTimeString();
        if (str == null) {
            return;
        }
        infoWidth = (int) Math.ceil(infoPaint.measureText(str));
        CharSequence str2 = TextUtils.ellipsize(str, infoPaint, infoWidth, TextUtils.TruncateAt.END);
        infoLayout = new StaticLayout(str2, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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
            String url = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=100x100&maptype=roadmap&scale=%d&markers=color:red|size:mid|%f,%f&sensor=false", lat, lon, Math.min(2, (int) Math.ceil(density)), lat, lon);
            if (!url.equals(currentUrl)) {
                return true;
            }
        } else if (currentPhotoObject == null || currentPhotoObject.location instanceof TLRPC.TL_fileLocationUnavailable) {
            return true;
        } else if (currentMessageObject != null && photoNotSet) {
            File cacheFile = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
            if (cacheFile.exists()) { //TODO
                return true;
            }
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

        TLRPC.User newUser = null;
        TLRPC.Chat newChat = null;
        if (currentMessageObject.isFromUser()) {
            newUser = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.from_id < 0) {
            newChat = MessagesController.getInstance().getChat(-currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.post) {
            newChat = MessagesController.getInstance().getChat(currentMessageObject.messageOwner.to_id.channel_id);
        }
        TLRPC.FileLocation newPhoto = null;

        if (isAvatarVisible) {
            if (newUser != null && newUser.photo != null){
                newPhoto = newUser.photo.photo_small;
            } else if (newChat != null && newChat.photo != null) {
                newPhoto = newChat.photo.photo_small;
            }
        }

        if (replyTextLayout == null && currentMessageObject.replyMessageObject != null) {
            return true;
        }

        if (currentPhoto == null && newPhoto != null || currentPhoto != null && newPhoto == null || currentPhoto != null && newPhoto != null && (currentPhoto.local_id != newPhoto.local_id || currentPhoto.volume_id != newPhoto.volume_id)) {
            return true;
        }

        TLRPC.FileLocation newReplyPhoto = null;

        if (currentMessageObject.replyMessageObject != null) {
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
            if (newUser != null) {
                newNameString = UserObject.getUserName(newUser);
            } else if (newChat != null) {
                newNameString = newChat.title;
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
        photoImage.onDetachedFromWindow();
        MediaController.getInstance().removeLoadingFileObserver(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
        replyImageReceiver.onAttachedToWindow();
        if (drawPhotoImage) {
            if (photoImage.onAttachedToWindow()) {
                updateButtonState(false);
            }
        } else {
            updateButtonState(false);
        }
    }

    @Override
    protected void onLongPress() {
        if (pressedLink instanceof URLSpanNoUnderline) {
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
        if (buttonPressed != 0 || pressedBotButton != -1) {
            buttonPressed = 0;
            pressedBotButton = -1;
            invalidate();
        }
        if (delegate != null) {
            delegate.didLongPressed(this);
        }
    }

    public void setCheckPressed(boolean value, boolean pressed) {
        isCheckPressed = value;
        isPressed = pressed;
        radialProgress.swapBackground(getDrawableForCurrentState());
        if (useSeekBarWaweform) {
            seekBarWaveform.setSelected(isDrawSelectedBackground());
        } else {
            seekBar.setSelected(isDrawSelectedBackground());
        }
        invalidate();
    }

    public void setHighlighted(boolean value) {
        if (isHighlighted == value) {
            return;
        }
        isHighlighted = value;
        radialProgress.swapBackground(getDrawableForCurrentState());
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
        radialProgress.swapBackground(getDrawableForCurrentState());
        if (useSeekBarWaweform) {
            seekBarWaveform.setSelected(isDrawSelectedBackground());
        } else {
            seekBar.setSelected(isDrawSelectedBackground());
        }
        invalidate();
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
            availableTimeWidth = maxWidth - dp(76 + 18) - (int) Math.ceil(audioTimePaint.measureText("00:00"));
            measureTime(messageObject);
            int minSize = dp(40 + 14 + 20 + 90 + 10) + timeWidth;
            if (!hasLinkPreview) {
                backgroundWidth = Math.min(maxWidth, minSize + duration * dp(10));
            }

            if (messageObject.isOutOwner()) {
                seekBarWaveform.setColors(Theme.MSG_OUT_VOICE_SEEKBAR_COLOR, Theme.MSG_OUT_VOICE_SEEKBAR_FILL_COLOR, Theme.MSG_OUT_VOICE_SEEKBAR_SELECTED_COLOR);
                seekBar.setColors(Theme.MSG_OUT_AUDIO_SEEKBAR_COLOR, Theme.MSG_OUT_AUDIO_SEEKBAR_FILL_COLOR, Theme.MSG_OUT_AUDIO_SEEKBAR_SELECTED_COLOR);
            } else {
                seekBarWaveform.setColors(Theme.MSG_IN_VOICE_SEEKBAR_COLOR, Theme.MSG_IN_VOICE_SEEKBAR_FILL_COLOR, Theme.MSG_IN_VOICE_SEEKBAR_SELECTED_COLOR);
                seekBar.setColors(Theme.MSG_IN_AUDIO_SEEKBAR_COLOR, Theme.MSG_IN_AUDIO_SEEKBAR_FILL_COLOR, Theme.MSG_IN_AUDIO_SEEKBAR_SELECTED_COLOR);
            }
            seekBarWaveform.setMessageObject(messageObject);
            return 0;
        } else if (MessageObject.isMusicDocument(documentAttach)) {
            documentAttachType = DOCUMENT_ATTACH_TYPE_MUSIC;
            if (messageObject.isOutOwner()) {
                seekBar.setColors(Theme.MSG_OUT_AUDIO_SEEKBAR_COLOR, Theme.MSG_OUT_AUDIO_SEEKBAR_FILL_COLOR, Theme.MSG_OUT_AUDIO_SEEKBAR_SELECTED_COLOR);
            } else {
                seekBar.setColors(Theme.MSG_IN_AUDIO_SEEKBAR_COLOR, Theme.MSG_IN_AUDIO_SEEKBAR_FILL_COLOR, Theme.MSG_IN_AUDIO_SEEKBAR_SELECTED_COLOR);
            }

            maxWidth = maxWidth - dp(86);

            CharSequence stringFinal = TextUtils.ellipsize(messageObject.getMusicTitle().replace('\n', ' '), audioTitlePaint, maxWidth - dp(12), TextUtils.TruncateAt.END);
            songLayout = new StaticLayout(stringFinal, audioTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (songLayout.getLineCount() > 0) {
                songX = -(int) Math.ceil(songLayout.getLineLeft(0));
            }

            stringFinal = TextUtils.ellipsize(messageObject.getMusicAuthor().replace('\n', ' '), audioPerformerPaint, maxWidth, TextUtils.TruncateAt.END);
            performerLayout = new StaticLayout(stringFinal, audioPerformerPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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
            int durationWidth = (int) Math.ceil(audioTimePaint.measureText(String.format("%d:%02d / %d:%02d", duration / 60, duration % 60, duration / 60, duration % 60)));
            availableTimeWidth = backgroundWidth - dp(76 + 18) - durationWidth;
            return durationWidth;
        } else if (MessageObject.isVideoDocument(documentAttach)) {
            documentAttachType = DOCUMENT_ATTACH_TYPE_VIDEO;
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
            String str = String.format("%d:%02d, %s", minutes, seconds, formatFileSize(documentAttach.size));
            infoWidth = (int) Math.ceil(infoPaint.measureText(str));
            infoLayout = new StaticLayout(str, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

            return 0;
        } else {
            drawPhotoImage = documentAttach.mime_type != null && documentAttach.mime_type.toLowerCase().startsWith("image/") || documentAttach.thumb instanceof TLRPC.TL_photoSize && !(documentAttach.thumb.location instanceof TLRPC.TL_fileLocationUnavailable);
            if (!drawPhotoImage) {
                maxWidth += dp(30);
            }
            documentAttachType = DOCUMENT_ATTACH_TYPE_DOCUMENT;
            String name = FileLoader.getDocumentFileName(documentAttach);
            if (name == null || name.length() == 0) {
                name = LocaleController.getString("AttachDocument", R.string.AttachDocument);
            }
            docTitleLayout = StaticLayoutEx.createStaticLayout(name, docNamePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.MIDDLE, maxWidth, drawPhotoImage ? 2 : 1);
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

            String str = formatFileSize(documentAttach.size) + " " + FileLoader.getDocumentExtension(documentAttach);
            infoWidth = Math.min(maxWidth - AndroidUtilities.dp(30), (int) Math.ceil(infoPaint.measureText(str)));
            CharSequence str2 = TextUtils.ellipsize(str, infoPaint, infoWidth, TextUtils.TruncateAt.END);
            try {
                if (infoWidth < 0) {
                    infoWidth = dp(10);
                }
                infoLayout = new StaticLayout(str2, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            if (drawPhotoImage) {
                currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, getPhotoSize());
                photoImage.setNeedsQualityThumb(true);
                photoImage.setShouldGenerateQualityThumb(true);
                photoImage.setParentMessageObject(messageObject);
                if (currentPhotoObject != null) {
                    currentPhotoFilter = "86_86_b";
                    photoImage.setImage(null, null, null, null, currentPhotoObject.location, currentPhotoFilter, 0, null, true);
                } else {
                    photoImage.setImageBitmap((BitmapDrawable) null);
                }
            }
            return width;
        }
    }

    private void calcBackgroundWidth(int maxWidth, int timeMore, int maxChildWidth) {
        if (hasLinkPreview || hasGamePreview || maxWidth - currentMessageObject.lastLineWidth < timeMore) {
            totalHeight += dp(14);
            backgroundWidth = Math.max(maxChildWidth, currentMessageObject.lastLineWidth) + dp(31);
            backgroundWidth = Math.max(backgroundWidth, timeWidth + dp(31));
        } else {
            int diff = maxChildWidth - currentMessageObject.lastLineWidth;
            if (diff >= 0 && diff <= timeMore) {
                backgroundWidth = maxChildWidth + timeMore - diff + dp(31);
            } else {
                backgroundWidth = Math.max(maxChildWidth, currentMessageObject.lastLineWidth + timeMore) + dp(31);
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
                    FileLog.e("tmessages", e);
                }
                invalidate();
                break;
            }
        }
    }

    public void setMessageObject(MessageObject messageObject) {
        if (messageObject.checkLayout()) {
            currentMessageObject = null;
        }
        boolean messageIdChanged = currentMessageObject == null || currentMessageObject.getId() != messageObject.getId();
        boolean messageChanged = currentMessageObject != messageObject || messageObject.forceUpdate;
        boolean dataChanged = currentMessageObject == messageObject && (isUserDataChanged() || photoNotSet);
        if (messageChanged || dataChanged || isPhotoDataChanged(messageObject)) {
            currentMessageObject = messageObject;
            lastSendState = messageObject.messageOwner.send_state;
            lastDeleteDate = messageObject.messageOwner.destroyTime;
            lastViewsCount = messageObject.messageOwner.views;
            isPressed = false;
            isCheckPressed = true;
            isAvatarVisible = false;
            wasLayout = false;
            drawShareButton = checkNeedDrawShareButton(messageObject);
            replyNameLayout = null;
            replyTextLayout = null;
            replyNameWidth = 0;
            replyTextWidth = 0;
            viaWidth = 0;
            viaNameWidth = 0;
            currentReplyPhoto = null;
            currentUser = null;
            currentChat = null;
            currentViaBotUser = null;
            drawNameLayout = false;

            resetPressedLink(-1);
            messageObject.forceUpdate = false;
            drawPhotoImage = false;
            hasLinkPreview = false;
            hasGamePreview = false;
            linkPreviewPressed = false;
            buttonPressed = 0;
            pressedBotButton = -1;
            linkPreviewHeight = 0;
            mediaOffsetY = 0;
            documentAttachType = DOCUMENT_ATTACH_TYPE_NONE;
            documentAttach = null;
            descriptionLayout = null;
            titleLayout = null;
            videoInfoLayout = null;
            siteNameLayout = null;
            authorLayout = null;
            captionLayout = null;
            docTitleLayout = null;
            drawImageButton = false;
            currentPhotoObject = null;
            currentPhotoObjectThumb = null;
            currentPhotoFilter = null;
            infoLayout = null;
            cancelLoading = false;
            buttonState = -1;
            currentUrl = null;
            photoNotSet = false;
            drawBackground = true;
            drawName = false;
            useSeekBarWaweform = false;
            drawForwardedName = false;
            mediaBackground = false;
            availableTimeWidth = 0;
            photoImage.setNeedsQualityThumb(false);
            photoImage.setShouldGenerateQualityThumb(false);
            photoImage.setParentMessageObject(null);
            photoImage.setRoundRadius(dp(3));

            if (messageChanged) {
                firstVisibleBlockNum = 0;
                lastVisibleBlockNum = 0;
                needNewVisiblePart = true;
            }

            if (messageObject.type == 0) {
                drawForwardedName = true;

                int maxWidth;
                if (isTablet()) {
                    if (isChat && !messageObject.isOutOwner() && messageObject.isFromUser()) {
                        maxWidth = getMinTabletSide() - dp(122);
                        drawName = true;
                    } else {
                        drawName = messageObject.messageOwner.to_id.channel_id != 0 && !messageObject.isOutOwner();
                        maxWidth = getMinTabletSide() - dp(80);
                    }
                } else {
                    if (isChat && !messageObject.isOutOwner() && messageObject.isFromUser()) {
                        maxWidth = Math.min(displaySize.x, displaySize.y) - dp(122);
                        drawName = true;
                    } else {
                        maxWidth = Math.min(displaySize.x, displaySize.y) - dp(80);
                        drawName = messageObject.messageOwner.to_id.channel_id != 0 && !messageObject.isOutOwner();
                    }
                }
                availableTimeWidth = maxWidth;
                measureTime(messageObject);
                int timeMore = timeWidth + dp(6);
                if (messageObject.isOutOwner()) {
                    timeMore += dp(20.5f);
                }

                hasGamePreview = messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame && messageObject.messageOwner.media.game instanceof TLRPC.TL_game;
                hasLinkPreview = messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage;
                backgroundWidth = maxWidth;
                if (hasLinkPreview || hasGamePreview || maxWidth - messageObject.lastLineWidth < timeMore) {
                    backgroundWidth = Math.max(backgroundWidth, messageObject.lastLineWidth) + dp(31);
                    backgroundWidth = Math.max(backgroundWidth, timeWidth + dp(31));
                } else {
                    int diff = backgroundWidth - messageObject.lastLineWidth;
                    if (diff >= 0 && diff <= timeMore) {
                        backgroundWidth = backgroundWidth + timeMore - diff + dp(31);
                    } else {
                        backgroundWidth = Math.max(backgroundWidth, messageObject.lastLineWidth + timeMore) + dp(31);
                    }
                }
                availableTimeWidth = backgroundWidth - dp(31);

                setMessageObjectInternal(messageObject);

                backgroundWidth = messageObject.textWidth + (hasGamePreview ? AndroidUtilities.dp(10) : 0);
                totalHeight = messageObject.textHeight + dp(19.5f) + namesOffset;

                int maxChildWidth = Math.max(backgroundWidth, nameWidth);
                maxChildWidth = Math.max(maxChildWidth, forwardedNameWidth);
                maxChildWidth = Math.max(maxChildWidth, replyNameWidth);
                maxChildWidth = Math.max(maxChildWidth, replyTextWidth);
                int maxWebWidth = 0;

                if (hasLinkPreview || hasGamePreview) {
                    int linkPreviewMaxWidth;
                    if (isTablet()) {
                        if (messageObject.isFromUser() && (currentMessageObject.messageOwner.to_id.channel_id != 0 || currentMessageObject.messageOwner.to_id.chat_id != 0) && !currentMessageObject.isOut()) {
                            linkPreviewMaxWidth = getMinTabletSide() - dp(122);
                        } else {
                            linkPreviewMaxWidth = getMinTabletSide() - dp(80);
                        }
                    } else {
                        if (messageObject.isFromUser() && (currentMessageObject.messageOwner.to_id.channel_id != 0 || currentMessageObject.messageOwner.to_id.chat_id != 0) && !currentMessageObject.isOutOwner()) {
                            linkPreviewMaxWidth = Math.min(displaySize.x, displaySize.y) - dp(122);
                        } else {
                            linkPreviewMaxWidth = Math.min(displaySize.x, displaySize.y) - dp(80);
                        }
                    }
                    if (drawShareButton) {
                        linkPreviewMaxWidth -= dp(20);
                    }
                    String site_name;
                    String title;
                    String author;
                    String description;
                    TLRPC.Photo photo;
                    TLRPC.Document document;
                    int duration;
                    boolean smallImage;
                    String type;
                    if (messageObject.messageOwner.media.webpage != null) {
                        TLRPC.TL_webPage webPage = (TLRPC.TL_webPage) messageObject.messageOwner.media.webpage;
                        site_name = webPage.site_name;
                        title = webPage.title;
                        author = webPage.author;
                        description = webPage.description;
                        photo = webPage.photo;
                        document = webPage.document;
                        type = webPage.type;
                        duration = webPage.duration;
                        if (site_name != null && photo != null && site_name.toLowerCase().equals("instagram")) {
                            linkPreviewMaxWidth = Math.max(displaySize.y / 3, currentMessageObject.textWidth);
                        }
                        smallImage = type != null && (type.equals("app") || type.equals("profile") || type.equals("article"));
                        isSmallImage = description != null && type != null && (type.equals("app") || type.equals("profile") || type.equals("article")) && currentMessageObject.photoThumbs != null;
                    } else {
                        TLRPC.TL_game game = messageObject.messageOwner.media.game;
                        site_name = game.title;
                        title = null;
                        description = TextUtils.isEmpty(messageObject.messageText) ? game.description : null;
                        photo = game.photo;
                        author = null;
                        document = game.document;
                        duration = 0;
                        type = "game";
                        isSmallImage = false;
                        smallImage = false;
                    }

                    int additinalWidth = dp(10);
                    int restLinesCount = 3;
                    int additionalHeight = 0;
                    linkPreviewMaxWidth -= additinalWidth;

                    if (currentMessageObject.photoThumbs == null && photo != null) {
                        currentMessageObject.generateThumbs(true);
                    }

                    if (site_name != null) {
                        try {
                            int width = (int) Math.ceil(replyNamePaint.measureText(site_name));
                            siteNameLayout = new StaticLayout(site_name, replyNamePaint, Math.min(width, linkPreviewMaxWidth), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            int height = siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
                            linkPreviewHeight += height;
                            totalHeight += height;
                            additionalHeight += height;
                            width = siteNameLayout.getWidth();
                            maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
                            maxWebWidth = Math.max(maxWebWidth, width + additinalWidth);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }

                    boolean titleIsRTL = false;
                    if (title != null) {
                        try {
                            titleX = Integer.MAX_VALUE;
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += dp(2);
                                totalHeight += dp(2);
                            }
                            int restLines = 0;
                            if (!isSmallImage || description == null) {
                                titleLayout = StaticLayoutEx.createStaticLayout(title, replyNamePaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, dp(1), false, TextUtils.TruncateAt.END, linkPreviewMaxWidth, 4);
                            } else {
                                restLines = restLinesCount;
                                titleLayout = generateStaticLayout(title, replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - dp(48 + 4), restLinesCount, 4);
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
                                    width += dp(48 + 4);
                                }
                                maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
                                maxWebWidth = Math.max(maxWebWidth, width + additinalWidth);
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }

                    boolean authorIsRTL = false;
                    if (author != null) {
                        try {
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += dp(2);
                                totalHeight += dp(2);
                            }
                            if (restLinesCount == 3 && (!isSmallImage || description == null)) {
                                authorLayout = new StaticLayout(author, replyNamePaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            } else {
                                authorLayout = generateStaticLayout(author, replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - dp(48 + 4), restLinesCount, 1);
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
                            FileLog.e("tmessages", e);
                        }
                    }

                    if (description != null) {
                        try {
                            descriptionX = 0;
                            currentMessageObject.generateLinkDescription();
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += dp(2);
                                totalHeight += dp(2);
                            }
                            int restLines = 0;
                            if (restLinesCount == 3 && !isSmallImage) {
                                descriptionLayout = StaticLayoutEx.createStaticLayout(messageObject.linkDescription, replyTextPaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, dp(1), false, TextUtils.TruncateAt.END, linkPreviewMaxWidth, 6);
                            } else {
                                restLines = restLinesCount;
                                descriptionLayout = generateStaticLayout(messageObject.linkDescription, replyTextPaint, linkPreviewMaxWidth, linkPreviewMaxWidth - dp(48 + 4), restLinesCount, 6);
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

                            for (int a = 0; a < descriptionLayout.getLineCount(); a++) {
                                int lineLeft = (int) Math.ceil(descriptionLayout.getLineLeft(a));
                                if (lineLeft == 0 && descriptionX != 0) {
                                    descriptionX = 0;
                                }

                                int width;
                                if (lineLeft != 0) {
                                    width = descriptionLayout.getWidth() - lineLeft;
                                } else {
                                    width = hasRTL ? descriptionLayout.getWidth() : (int) Math.ceil(descriptionLayout.getLineWidth(a));
                                }
                                if (a < restLines || restLines != 0 && lineLeft != 0 && isSmallImage) {
                                    width += dp(48 + 4);
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
                            FileLog.e("tmessages", e);
                        }
                    }

                    if (smallImage && (descriptionLayout == null || descriptionLayout != null && descriptionLayout.getLineCount() == 1)) {
                        smallImage = false;
                        isSmallImage = false;
                    }
                    int maxPhotoWidth = smallImage ? dp(48) : linkPreviewMaxWidth;

                    if (document != null) {
                        if (MessageObject.isGifDocument(document)){
                            if (!MediaController.getInstance().canAutoplayGifs()) {
                                messageObject.audioProgress = 1;
                            }
                            photoImage.setAllowStartAnimation(messageObject.audioProgress != 1);
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
                                    currentPhotoObject.w = currentPhotoObject.h = dp(150);
                                }
                            }
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
                                    currentPhotoObject.w = currentPhotoObject.h = dp(150);
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
                                    currentPhotoObject.w = currentPhotoObject.h = dp(150);
                                }
                            }
                            documentAttach = document;
                            documentAttachType = DOCUMENT_ATTACH_TYPE_STICKER;
                        } else {
                            calcBackgroundWidth(maxWidth, timeMore, maxChildWidth);
                            if (!MessageObject.isStickerDocument(document)) {
                                if (backgroundWidth < maxWidth + dp(20)) {
                                    backgroundWidth = maxWidth + dp(20);
                                }
                                if (MessageObject.isVoiceDocument(document)) {
                                    createDocumentLayout(backgroundWidth - dp(10), messageObject);
                                    mediaOffsetY = currentMessageObject.textHeight + dp(8) + linkPreviewHeight;
                                    totalHeight += dp(30 + 14);
                                    linkPreviewHeight += dp(44);
                                    calcBackgroundWidth(maxWidth, timeMore, maxChildWidth);
                                } else if (MessageObject.isMusicDocument(document)) {
                                    int durationWidth = createDocumentLayout(backgroundWidth - dp(10), messageObject);
                                    mediaOffsetY = currentMessageObject.textHeight + dp(8) + linkPreviewHeight;
                                    totalHeight += dp(42 + 14);
                                    linkPreviewHeight += dp(56);

                                    maxWidth = maxWidth - dp(86);
                                    maxChildWidth = Math.max(maxChildWidth, durationWidth + additinalWidth + dp(86 + 8));
                                    if (songLayout != null && songLayout.getLineCount() > 0) {
                                        maxChildWidth = (int) Math.max(maxChildWidth, songLayout.getLineWidth(0) + additinalWidth + dp(86));
                                    }
                                    if (performerLayout != null && performerLayout.getLineCount() > 0) {
                                        maxChildWidth = (int) Math.max(maxChildWidth, performerLayout.getLineWidth(0) + additinalWidth + dp(86));
                                    }

                                    calcBackgroundWidth(maxWidth, timeMore, maxChildWidth);
                                } else {
                                    createDocumentLayout(backgroundWidth - dp(86 + 24 + 58), messageObject);
                                    drawImageButton = true;
                                    if (drawPhotoImage) {
                                        totalHeight += dp(86 + 14);
                                        linkPreviewHeight += dp(86);
                                        photoImage.setImageCoords(0, totalHeight + namesOffset, dp(86), dp(86));
                                    } else {
                                        mediaOffsetY = currentMessageObject.textHeight + dp(8) + linkPreviewHeight;
                                        photoImage.setImageCoords(0, totalHeight + namesOffset - dp(14), dp(56), dp(56));
                                        totalHeight += dp(50 + 14);
                                        linkPreviewHeight += dp(50);
                                    }
                                }
                            }
                        }
                    } else if (photo != null) {
                        drawImageButton = type != null && type.equals("photo");
                        currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, drawImageButton ? getPhotoSize() : maxPhotoWidth, !drawImageButton);
                        currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                        if (currentPhotoObjectThumb == currentPhotoObject) {
                            currentPhotoObjectThumb = null;
                        }
                    }

                    if (documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC && documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO && documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        if (currentPhotoObject != null) {
                            drawImageButton = type != null && (type.equals("photo") || type.equals("document") && documentAttachType != DOCUMENT_ATTACH_TYPE_STICKER || type.equals("gif") || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO);
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += dp(2);
                                totalHeight += dp(2);
                            }

                            if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER) {
                                if (isTablet()) {
                                    maxPhotoWidth = (int) (getMinTabletSide() * 0.5f);
                                } else {
                                    maxPhotoWidth = (int) (displaySize.x * 0.5f);
                                }
                            }

                            maxChildWidth = Math.max(maxChildWidth, maxPhotoWidth + additinalWidth);
                            currentPhotoObject.size = -1;
                            if (currentPhotoObjectThumb != null) {
                                currentPhotoObjectThumb.size = -1;
                            }

                            int width;
                            int height;
                            if (smallImage) {
                                width = height = maxPhotoWidth;
                            } else {
                                if (hasGamePreview) {
                                    width = 640;
                                    height = 360;
                                    float scale = width / (float) (maxPhotoWidth - dp(2));
                                    width /= scale;
                                    height /= scale;
                                } else {
                                    width = currentPhotoObject.w;
                                    height = currentPhotoObject.h;
                                    float scale = width / (float) (maxPhotoWidth - dp(2));
                                    width /= scale;
                                    height /= scale;
                                    if (site_name == null || site_name != null && !site_name.toLowerCase().equals("instagram") && documentAttachType == 0) {
                                        if (height > displaySize.y / 3) {
                                            height = displaySize.y / 3;
                                        }
                                    }
                                }
                            }
                            if (isSmallImage) {
                                if (dp(50) + additionalHeight > linkPreviewHeight) {
                                    totalHeight += dp(50) + additionalHeight - linkPreviewHeight + dp(8);
                                    linkPreviewHeight = dp(50) + additionalHeight;
                                }
                                linkPreviewHeight -= dp(8);
                            } else {
                                totalHeight += height + dp(12);
                                linkPreviewHeight += height;
                            }

                            photoImage.setImageCoords(0, 0, width, height);

                            currentPhotoFilter = String.format(Locale.US, "%d_%d", width, height);
                            currentPhotoFilterThumb = String.format(Locale.US, "%d_%d_b", width, height);

                            if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER) {
                                photoImage.setImage(documentAttach, null, currentPhotoFilter, null, currentPhotoObject != null ? currentPhotoObject.location : null, "b1", documentAttach.size, "webp", true);
                            } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                                photoImage.setImage(null, null, currentPhotoObject.location, currentPhotoFilter, 0, null, false);
                            } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                                boolean photoExist = messageObject.mediaExists;
                                String fileName = FileLoader.getAttachFileName(document);
                                if (hasGamePreview || photoExist || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_GIF) || FileLoader.getInstance().isLoadingFile(fileName)) {
                                    photoNotSet = false;
                                    photoImage.setImage(document, null, currentPhotoObject.location, currentPhotoFilter, document.size, null, false);
                                } else {
                                    photoNotSet = true;
                                    photoImage.setImage(null, null, currentPhotoObject.location, currentPhotoFilter, 0, null, false);
                                }
                            } else {
                                boolean photoExist = messageObject.mediaExists;
                                String fileName = FileLoader.getAttachFileName(currentPhotoObject);
                                if (hasGamePreview || photoExist || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO) || FileLoader.getInstance().isLoadingFile(fileName)) {
                                    photoNotSet = false;
                                    photoImage.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, 0, null, false);
                                } else {
                                    photoNotSet = true;
                                    if (currentPhotoObjectThumb != null) {
                                        photoImage.setImage(null, null, currentPhotoObjectThumb.location, String.format(Locale.US, "%d_%d_b", width, height), 0, null, false);
                                    } else {
                                        photoImage.setImageBitmap((Drawable) null);
                                    }
                                }
                            }
                            drawPhotoImage = true;

                            if (type != null && type.equals("video") && duration != 0) {
                                int minutes = duration / 60;
                                int seconds = duration - minutes * 60;
                                String str = String.format("%d:%02d", minutes, seconds);
                                durationWidth = (int) Math.ceil(durationPaint.measureText(str));
                                videoInfoLayout = new StaticLayout(str, durationPaint, durationWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            } else if (hasGamePreview) {
                                String str = LocaleController.getString("AttachGame", R.string.AttachGame).toUpperCase();
                                durationWidth = (int) Math.ceil(gamePaint.measureText(str));
                                videoInfoLayout = new StaticLayout(str, gamePaint, durationWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            }
                        } else {
                            photoImage.setImageBitmap((Drawable) null);
                            linkPreviewHeight -= dp(6);
                            totalHeight += dp(4);
                        }
                        if (hasGamePreview && messageObject.textHeight != 0) {
                            linkPreviewHeight += messageObject.textHeight + AndroidUtilities.dp(6);
                            totalHeight += dp(4);
                        }
                        calcBackgroundWidth(maxWidth, timeMore, maxChildWidth);
                    }
                } else {
                    photoImage.setImageBitmap((Drawable) null);
                    calcBackgroundWidth(maxWidth, timeMore, maxChildWidth);
                }
            } else if (messageObject.type == 12) {
                drawName = false;
                drawForwardedName = true;
                drawPhotoImage = true;
                photoImage.setRoundRadius(dp(22));
                if (isTablet()) {
                    backgroundWidth = Math.min(getMinTabletSide() - dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), dp(270));
                } else {
                    backgroundWidth = Math.min(displaySize.x - dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), dp(270));
                }
                availableTimeWidth = backgroundWidth - dp(31);

                int uid = messageObject.messageOwner.media.user_id;
                TLRPC.User user = MessagesController.getInstance().getUser(uid);

                int maxWidth = getMaxNameWidth() - dp(110);
                if (maxWidth < 0) {
                    maxWidth = dp(10);
                }

                TLRPC.FileLocation currentPhoto = null;
                if (user != null) {
                    if (user.photo != null) {
                        currentPhoto = user.photo.photo_small;
                    }
                    contactAvatarDrawable.setInfo(user);
                }
                photoImage.setImage(currentPhoto, "50_50", user != null ? contactAvatarDrawable : Theme.contactDrawable[messageObject.isOutOwner() ? 1 : 0], null, false);

                String phone = messageObject.messageOwner.media.phone_number;
                if (phone != null && phone.length() != 0) {
                    phone = PhoneFormat.getInstance().format(phone);
                } else {
                    phone = LocaleController.getString("NumberUnknown", R.string.NumberUnknown);
                }

                CharSequence currentNameString = ContactsController.formatName(messageObject.messageOwner.media.first_name, messageObject.messageOwner.media.last_name).replace('\n', ' ');
                if (currentNameString.length() == 0) {
                    currentNameString = phone;
                }
                titleLayout = new StaticLayout(TextUtils.ellipsize(currentNameString, contactNamePaint, maxWidth, TextUtils.TruncateAt.END), contactNamePaint, maxWidth + dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                docTitleLayout = new StaticLayout(TextUtils.ellipsize(phone.replace('\n', ' '), contactPhonePaint, maxWidth, TextUtils.TruncateAt.END), contactPhonePaint, maxWidth + dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                setMessageObjectInternal(messageObject);

                if (drawForwardedName && messageObject.isForwarded()) {
                    namesOffset += dp(5);
                } else if (drawNameLayout && messageObject.messageOwner.reply_to_msg_id == 0) {
                    namesOffset += dp(7);
                }

                totalHeight = dp(70) + namesOffset;
                if (docTitleLayout.getLineCount() > 0) {
                    int timeLeft = backgroundWidth - AndroidUtilities.dp(40 + 18 + 44 + 8) - (int) Math.ceil(docTitleLayout.getLineWidth(0));
                    if (timeLeft < timeWidth) {
                        totalHeight += AndroidUtilities.dp(8);
                    }
                }
            } else if (messageObject.type == 2) {
                drawForwardedName = true;
                if (isTablet()) {
                    backgroundWidth = Math.min(getMinTabletSide() - dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), dp(270));
                } else {
                    backgroundWidth = Math.min(displaySize.x - dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), dp(270));
                }
                createDocumentLayout(backgroundWidth, messageObject);

                setMessageObjectInternal(messageObject);

                totalHeight = dp(70) + namesOffset;
            } else if (messageObject.type == 14) {
                if (isTablet()) {
                    backgroundWidth = Math.min(getMinTabletSide() - dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), dp(270));
                } else {
                    backgroundWidth = Math.min(displaySize.x - dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), dp(270));
                }

                createDocumentLayout(backgroundWidth, messageObject);

                setMessageObjectInternal(messageObject);

                totalHeight = dp(82) + namesOffset;
            } else {
                drawForwardedName = messageObject.messageOwner.fwd_from != null && messageObject.type != 13;
                mediaBackground = messageObject.type != 9;
                drawImageButton = true;
                drawPhotoImage = true;

                int photoWidth = 0;
                int photoHeight = 0;
                int additionHeight = 0;

                if (messageObject.audioProgress != 2 && !MediaController.getInstance().canAutoplayGifs() && messageObject.type == 8) {
                    messageObject.audioProgress = 1;
                }

                photoImage.setAllowStartAnimation(messageObject.audioProgress == 0);

                photoImage.setForcePreview(messageObject.isSecretPhoto());
                if (messageObject.type == 9) {
                    if (isTablet()) {
                        backgroundWidth = Math.min(getMinTabletSide() - dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), dp(270));
                    } else {
                        backgroundWidth = Math.min(displaySize.x - dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), dp(270));
                    }
                    if (checkNeedDrawShareButton(messageObject)) {
                        backgroundWidth -= dp(20);
                    }
                    int maxWidth = backgroundWidth - dp(86 + 52);
                    createDocumentLayout(maxWidth, messageObject);
                    if (!TextUtils.isEmpty(messageObject.caption)) {
                        maxWidth += AndroidUtilities.dp(86);
                    }
                    if (drawPhotoImage) {
                        photoWidth = dp(86);
                        photoHeight = dp(86);
                    } else {
                        photoWidth = dp(56);
                        photoHeight = dp(56);
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
                    double lat = messageObject.messageOwner.media.geo.lat;
                    double lon = messageObject.messageOwner.media.geo._long;

                    if (messageObject.messageOwner.media.title != null && messageObject.messageOwner.media.title.length() > 0) {
                        if (isTablet()) {
                            backgroundWidth = Math.min(getMinTabletSide() - dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), dp(270));
                        } else {
                            backgroundWidth = Math.min(displaySize.x - dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), dp(270));
                        }
                        if (checkNeedDrawShareButton(messageObject)) {
                            backgroundWidth -= dp(20);
                        }
                        int maxWidth = backgroundWidth - dp(86 + 37);

                        docTitleLayout = StaticLayoutEx.createStaticLayout(messageObject.messageOwner.media.title, locationTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth, 2);
                        int lineCount = docTitleLayout.getLineCount();
                        if (messageObject.messageOwner.media.address != null && messageObject.messageOwner.media.address.length() > 0) {
                            infoLayout = StaticLayoutEx.createStaticLayout(messageObject.messageOwner.media.address, locationAddressPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth, Math.min(3, 3 - lineCount));
                        } else {
                            infoLayout = null;
                        }

                        mediaBackground = false;
                        availableTimeWidth = maxWidth;
                        photoWidth = dp(86);
                        photoHeight = dp(86);
                        currentUrl = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=72x72&maptype=roadmap&scale=%d&markers=color:red|size:mid|%f,%f&sensor=false", lat, lon, Math.min(2, (int) Math.ceil(density)), lat, lon);
                    } else {
                        availableTimeWidth = dp(200 - 14);
                        photoWidth = dp(200);
                        photoHeight = dp(100);
                        backgroundWidth = photoWidth + dp(12);
                        currentUrl = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=200x100&maptype=roadmap&scale=%d&markers=color:red|size:mid|%f,%f&sensor=false", lat, lon, Math.min(2, (int) Math.ceil(density)), lat, lon);
                    }
                    photoImage.setImage(currentUrl, null, messageObject.isOutOwner() ? Theme.geoOutDrawable : Theme.geoInDrawable, null, 0);
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
                    if (isTablet()) {
                        maxHeight = maxWidth = getMinTabletSide() * 0.4f;
                    } else {
                        maxHeight = maxWidth = Math.min(displaySize.x, displaySize.y) * 0.5f;
                    }
                    if (photoWidth == 0) {
                        photoHeight = (int) maxHeight;
                        photoWidth = photoHeight + dp(100);
                    }
                    photoHeight *= maxWidth / photoWidth;
                    photoWidth = (int) maxWidth;
                    if (photoHeight > maxHeight) {
                        photoWidth *= maxHeight / photoHeight;
                        photoHeight = (int) maxHeight;
                    }
                    documentAttachType = DOCUMENT_ATTACH_TYPE_STICKER;
                    availableTimeWidth = photoWidth - dp(14);
                    backgroundWidth = photoWidth + dp(12);
                    currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                    if (messageObject.attachPathExists) {
                        photoImage.setImage(null, messageObject.messageOwner.attachPath,
                                String.format(Locale.US, "%d_%d", photoWidth, photoHeight),
                                null,
                                currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null,
                                "b1",
                                messageObject.messageOwner.media.document.size, "webp", true);
                    } else if (messageObject.messageOwner.media.document.id != 0) {
                        photoImage.setImage(messageObject.messageOwner.media.document, null,
                                String.format(Locale.US, "%d_%d", photoWidth, photoHeight),
                                null,
                                currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null,
                                "b1",
                                messageObject.messageOwner.media.document.size, "webp", true);
                    }
                } else {
                    int maxPhotoWidth;
                    if (isTablet()) {
                        maxPhotoWidth = photoWidth = (int) (getMinTabletSide() * 0.7f);
                    } else {
                        maxPhotoWidth = photoWidth = (int) (Math.min(displaySize.x, displaySize.y) * 0.7f);
                    }
                    photoHeight = photoWidth + dp(100);
                    if (checkNeedDrawShareButton(messageObject)) {
                        maxPhotoWidth -= dp(20);
                        photoWidth -= dp(20);
                    }

                    if (photoWidth > getPhotoSize()) {
                        photoWidth = getPhotoSize();
                    }
                    if (photoHeight > getPhotoSize()) {
                        photoHeight = getPhotoSize();
                    }

                    if (messageObject.type == 1) { //photo
                        updateSecretTimeText(messageObject);
                        currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                    } else if (messageObject.type == 3) { //video
                        createDocumentLayout(0, messageObject);
                        photoImage.setNeedsQualityThumb(true);
                        photoImage.setShouldGenerateQualityThumb(true);
                        photoImage.setParentMessageObject(messageObject);
                    } else if (messageObject.type == 8) { //gif
                        String str = formatFileSize(messageObject.messageOwner.media.document.size);
                        infoWidth = (int) Math.ceil(infoPaint.measureText(str));
                        infoLayout = new StaticLayout(str, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                        photoImage.setNeedsQualityThumb(true);
                        photoImage.setShouldGenerateQualityThumb(true);
                        photoImage.setParentMessageObject(messageObject);
                    }

                    if (messageObject.caption != null) {
                        mediaBackground = false;
                    }

                    currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, getPhotoSize());

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
                            w = dp(150);
                        }
                        if (h == 0) {
                            h = dp(150);
                        }
                        if (h > photoHeight) {
                            float scale2 = h;
                            h = photoHeight;
                            scale2 /= h;
                            w = (int) (w / scale2);
                        } else if (h < dp(120)) {
                            h = dp(120);
                            float hScale = (float) currentPhotoObject.h / h;
                            if (currentPhotoObject.w / hScale < photoWidth) {
                                w = (int) (currentPhotoObject.w / hScale);
                            }
                        }
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
                                } else if (h < dp(120)) {
                                    h = dp(120);
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
                        w = h = dp(150);
                    }
                    if (messageObject.type == 3) {
                        if (w < infoWidth + dp(16 + 24)) {
                            w = infoWidth + dp(16 + 24);
                        }
                    }

                    availableTimeWidth = maxPhotoWidth - dp(14);
                    measureTime(messageObject);
                    int timeWidthTotal = timeWidth + dp(14 + (messageObject.isOutOwner() ? 20 : 0));
                    if (w < timeWidthTotal) {
                        w = timeWidthTotal;
                    }

                    if (messageObject.isSecretPhoto()) {
                        if (isTablet()) {
                            w = h = (int) (getMinTabletSide() * 0.5f);
                        } else {
                            w = h = (int) (Math.min(displaySize.x, displaySize.y) * 0.5f);
                        }
                    }

                    photoWidth = w;
                    photoHeight = h;
                    backgroundWidth = w + dp(12);
                    if (!mediaBackground) {
                        backgroundWidth += dp(9);
                    }
                    if (messageObject.caption != null) {
                        try {
                            captionLayout = new StaticLayout(messageObject.caption, MessageObject.getTextPaint(), photoWidth - dp(10), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            if (captionLayout.getLineCount() > 0) {
                                captionHeight = captionLayout.getHeight();
                                additionHeight += captionHeight + dp(9);
                                float lastLineWidth = captionLayout.getLineWidth(captionLayout.getLineCount() - 1) + captionLayout.getLineLeft(captionLayout.getLineCount() - 1);
                                if (photoWidth - dp(8) - lastLineWidth < timeWidthTotal) {
                                    additionHeight += dp(14);
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }

                    currentPhotoFilter = String.format(Locale.US, "%d_%d", (int) (w / density), (int) (h / density));
                    if (messageObject.photoThumbs != null && messageObject.photoThumbs.size() > 1 || messageObject.type == 3 || messageObject.type == 8) {
                        if (messageObject.isSecretPhoto()) {
                            currentPhotoFilter += "_b2";
                        } else {
                            currentPhotoFilter += "_b";
                        }
                    }

                    boolean noSize = false;
                    if (messageObject.type == 3 || messageObject.type == 8) {
                        noSize = true;
                    }
                    if (currentPhotoObject != null && !noSize && currentPhotoObject.size == 0) {
                        currentPhotoObject.size = -1;
                    }

                    if (messageObject.type == 1) {
                        if (currentPhotoObject != null) {
                            boolean photoExist = true;
                            String fileName = FileLoader.getAttachFileName(currentPhotoObject);
                            if (messageObject.mediaExists) {
                                MediaController.getInstance().removeLoadingFileObserver(this);
                            } else {
                                photoExist = false;
                            }
                            if (photoExist || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO) || FileLoader.getInstance().isLoadingFile(fileName)) {
                                photoImage.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilter, noSize ? 0 : currentPhotoObject.size, null, false);
                            } else {
                                photoNotSet = true;
                                if (currentPhotoObjectThumb != null) {
                                    photoImage.setImage(null, null, currentPhotoObjectThumb.location, currentPhotoFilter, 0, null, false);
                                } else {
                                    photoImage.setImageBitmap((Drawable) null);
                                }
                            }
                        } else {
                            photoImage.setImageBitmap((BitmapDrawable) null);
                        }
                    } else if (messageObject.type == 8) {
                        String fileName = FileLoader.getAttachFileName(messageObject.messageOwner.media.document);
                        int localFile = 0;
                        if (messageObject.attachPathExists) {
                            MediaController.getInstance().removeLoadingFileObserver(this);
                            localFile = 1;
                        } else if (messageObject.mediaExists) {
                            localFile = 2;
                        }
                        if (!messageObject.isSending() && (localFile != 0 || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_GIF) && MessageObject.isNewGifDocument(messageObject.messageOwner.media.document) || FileLoader.getInstance().isLoadingFile(fileName))) {
                            if (localFile == 1) {
                                photoImage.setImage(null, messageObject.isSendError() ? null : messageObject.messageOwner.attachPath, null, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, 0, null, false);
                            } else {
                                photoImage.setImage(messageObject.messageOwner.media.document, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, messageObject.messageOwner.media.document.size, null, false);
                            }
                        } else {
                            photoNotSet = true;
                            photoImage.setImage(null, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, 0, null, false);
                        }
                    } else {
                        photoImage.setImage(null, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, 0, null, false);
                    }
                }
                setMessageObjectInternal(messageObject);

                if (drawForwardedName) {
                    namesOffset += dp(5);
                } else if (drawNameLayout && messageObject.messageOwner.reply_to_msg_id == 0) {
                    namesOffset += dp(7);
                }

                invalidate();

                photoImage.setImageCoords(0, dp(7) + namesOffset, photoWidth, photoHeight);
                totalHeight = photoHeight + dp(14) + namesOffset + additionHeight;
            }
            if (captionLayout == null && messageObject.caption != null && messageObject.type != 13) {
                try {
                    int width = backgroundWidth - AndroidUtilities.dp(31);
                    captionLayout = new StaticLayout(messageObject.caption, MessageObject.getTextPaint(), width - dp(10), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    if (captionLayout.getLineCount() > 0) {
                        int timeWidthTotal = timeWidth + (messageObject.isOutOwner() ? dp(20) : 0);
                        captionHeight = captionLayout.getHeight();
                        totalHeight += captionHeight + dp(9);
                        float lastLineWidth = captionLayout.getLineWidth(captionLayout.getLineCount() - 1) + captionLayout.getLineLeft(captionLayout.getLineCount() - 1);
                        if (width - dp(8) - lastLineWidth < timeWidthTotal) {
                            totalHeight += dp(14);
                            captionHeight += dp(14);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }

            botButtons.clear();
            if (messageIdChanged) {
                botButtonsByData.clear();
            }
            if (messageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
                int rows = messageObject.messageOwner.reply_markup.rows.size();
                substractBackgroundHeight = keyboardHeight = dp(44 + 4) * rows + dp(1);

                widthForButtons = backgroundWidth;
                boolean fullWidth = false;
                if (messageObject.wantedBotKeyboardWidth > widthForButtons) {
                    int maxButtonWidth = -dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 62 : 10);
                    if (isTablet()) {
                        maxButtonWidth += getMinTabletSide();
                    } else {
                        maxButtonWidth += Math.min(displaySize.x, displaySize.y);
                    }
                    widthForButtons = Math.max(backgroundWidth, Math.min(messageObject.wantedBotKeyboardWidth, maxButtonWidth));
                    fullWidth = true;
                }

                int maxButtonsWidth = 0;
                for (int a = 0; a < rows; a++) {
                    TLRPC.TL_keyboardButtonRow row = messageObject.messageOwner.reply_markup.rows.get(a);
                    int buttonsCount = row.buttons.size();
                    if (buttonsCount == 0) {
                        continue;
                    }
                    int buttonWidth = (widthForButtons - (dp(5) * (buttonsCount - 1)) - dp(!fullWidth && mediaBackground ? 0 : 9) - dp(2)) / buttonsCount;
                    for (int b = 0; b < row.buttons.size(); b++) {
                        BotButton botButton = new BotButton();
                        botButton.button = row.buttons.get(b);
                        String key = Utilities.bytesToHex(botButton.button.data);
                        BotButton oldButton = botButtonsByData.get(key);
                        if (oldButton != null) {
                            botButton.progressAlpha = oldButton.progressAlpha;
                            botButton.angle = oldButton.angle;
                            botButton.lastUpdateTime = oldButton.lastUpdateTime;
                        } else {
                            botButton.lastUpdateTime = System.currentTimeMillis();
                        }
                        botButtonsByData.put(key, botButton);
                        botButton.x = b * (buttonWidth + dp(5));
                        botButton.y = a * dp(44 + 4) + dp(5);
                        botButton.width = buttonWidth;
                        botButton.height = dp(44);
                        CharSequence buttonText = Emoji.replaceEmoji(botButton.button.text, botButtonPaint.getFontMetricsInt(), dp(15), false);
                        buttonText = TextUtils.ellipsize(buttonText, botButtonPaint, buttonWidth - dp(10), TextUtils.TruncateAt.END);
                        botButton.title = new StaticLayout(buttonText, botButtonPaint, buttonWidth - dp(10), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
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
        }
        updateWaveform();
        updateButtonState(dataChanged);
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
        if (currentMessageObject != null && currentMessageObject.checkLayout()) {
            inLayout = true;
            MessageObject messageObject = currentMessageObject;
            currentMessageObject = null;
            setMessageObject(messageObject);
            inLayout = false;
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight + keyboardHeight);
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (currentMessageObject == null) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }

        if (changed || !wasLayout) {
            layoutWidth = getMeasuredWidth();
            layoutHeight = getMeasuredHeight() - substractBackgroundHeight;
            if (timeTextWidth < 0) {
                timeTextWidth = dp(10);
            }
            timeLayout = new StaticLayout(currentTimeString, timePaint, timeTextWidth + dp(6), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (!mediaBackground) {
                if (!currentMessageObject.isOutOwner()) {
                    timeX = backgroundWidth - dp(9) - timeWidth + (isChat && currentMessageObject.isFromUser() ? dp(48) : 0);
                } else {
                    timeX = layoutWidth - timeWidth - dp(38.5f);
                }
            } else {
                if (!currentMessageObject.isOutOwner()) {
                    timeX = backgroundWidth - dp(4) - timeWidth + (isChat && currentMessageObject.isFromUser() ? dp(48) : 0);
                } else {
                    timeX = layoutWidth - timeWidth - dp(42.0f);
                }
            }

            if ((currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                viewsLayout = new StaticLayout(currentViewsString, timePaint, viewsTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            } else {
                viewsLayout = null;
            }

            if (isAvatarVisible) {
                avatarImage.setImageCoords(dp(6), layoutHeight - dp(44), dp(42), dp(42));
            }

            wasLayout = true;
        }

        if (currentMessageObject.type == 0) {
            textY = dp(10) + namesOffset;
        }
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
            if (currentMessageObject.isOutOwner()) {
                seekBarX = layoutWidth - backgroundWidth + dp(57);
                buttonX = layoutWidth - backgroundWidth + dp(14);
                timeAudioX = layoutWidth - backgroundWidth + dp(67);
            } else {
                if (isChat && currentMessageObject.isFromUser()) {
                    seekBarX = dp(114);
                    buttonX = dp(71);
                    timeAudioX = dp(124);
                } else {
                    seekBarX = dp(66);
                    buttonX = dp(23);
                    timeAudioX = dp(76);
                }
            }
            if (hasLinkPreview) {
                seekBarX += dp(10);
                buttonX += dp(10);
                timeAudioX += dp(10);
            }
            seekBarWaveform.setSize(backgroundWidth - dp(92 + (hasLinkPreview ? 10 : 0)), dp(30));
            seekBar.setSize(backgroundWidth - dp(72 + (hasLinkPreview ? 10 : 0)), dp(30));
            seekBarY = dp(13) + namesOffset + mediaOffsetY;
            buttonY = dp(13) + namesOffset + mediaOffsetY;
            radialProgress.setProgressRect(buttonX, buttonY, buttonX + dp(44), buttonY + dp(44));

            updateAudioProgress();
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (currentMessageObject.isOutOwner()) {
                seekBarX = layoutWidth - backgroundWidth + dp(56);
                buttonX = layoutWidth - backgroundWidth + dp(14);
                timeAudioX = layoutWidth - backgroundWidth + dp(67);
            } else {
                if (isChat && currentMessageObject.isFromUser()) {
                    seekBarX = dp(113);
                    buttonX = dp(71);
                    timeAudioX = dp(124);
                } else {
                    seekBarX = dp(65);
                    buttonX = dp(23);
                    timeAudioX = dp(76);
                }
            }
            if (hasLinkPreview) {
                seekBarX += dp(10);
                buttonX += dp(10);
                timeAudioX += dp(10);
            }
            seekBar.setSize(backgroundWidth - dp(65 + (hasLinkPreview ? 10 : 0)), dp(30));
            seekBarY = dp(29) + namesOffset + mediaOffsetY;
            buttonY = dp(13) + namesOffset + mediaOffsetY;
            radialProgress.setProgressRect(buttonX, buttonY, buttonX + dp(44), buttonY + dp(44));

            updateAudioProgress();
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT && !drawPhotoImage) {
            if (currentMessageObject.isOutOwner()) {
                buttonX = layoutWidth - backgroundWidth + dp(14);
            } else {
                if (isChat && currentMessageObject.isFromUser()) {
                    buttonX = dp(71);
                } else {
                    buttonX = dp(23);
                }
            }
            if (hasLinkPreview) {
                buttonX += dp(10);
            }
            buttonY = dp(13) + namesOffset + mediaOffsetY;
            radialProgress.setProgressRect(buttonX, buttonY, buttonX + dp(44), buttonY + dp(44));
            photoImage.setImageCoords(buttonX - dp(10), buttonY - dp(10), photoImage.getImageWidth(), photoImage.getImageHeight());
        } else if (currentMessageObject.type == 12) {
            int x;

            if (currentMessageObject.isOutOwner()) {
                x = layoutWidth - backgroundWidth + dp(14);
            } else {
                if (isChat && currentMessageObject.isFromUser()) {
                    x = dp(72);
                } else {
                    x = dp(23);
                }
            }
            photoImage.setImageCoords(x, dp(13) + namesOffset, dp(44), dp(44));
        } else {
            int x;
            if (currentMessageObject.isOutOwner()) {
                if (mediaBackground) {
                    x = layoutWidth - backgroundWidth - dp(3);
                } else {
                    x = layoutWidth - backgroundWidth + dp(6);
                }
            } else {
                if (isChat && currentMessageObject.isFromUser()) {
                    x = dp(63);
                } else {
                    x = dp(15);
                }
            }
            photoImage.setImageCoords(x, photoImage.getImageY(), photoImage.getImageWidth(), photoImage.getImageHeight());
            buttonX = (int) (x + (photoImage.getImageWidth() - dp(48)) / 2.0f);
            buttonY = (int) (dp(7) + (photoImage.getImageHeight() - dp(48)) / 2.0f) + namesOffset;
            radialProgress.setProgressRect(buttonX, buttonY, buttonX + dp(48), buttonY + dp(48));
            deleteProgressRect.set(buttonX + dp(3), buttonY + dp(3), buttonX + dp(45), buttonY + dp(45));
        }
    }

    private void drawContent(Canvas canvas) {

        if (needNewVisiblePart && currentMessageObject.type == 0) {
            getLocalVisibleRect(scrollRect);
            setVisiblePart(scrollRect.top, scrollRect.bottom - scrollRect.top);
            needNewVisiblePart = false;
        }

        photoImage.setPressed(isDrawSelectedBackground());
        photoImage.setVisible(!PhotoViewer.getInstance().isShowingImage(currentMessageObject), false);
        radialProgress.setHideCurrentDrawable(false);
        radialProgress.setProgressColor(Theme.MSG_MEDIA_PROGRESS_COLOR);

        boolean imageDrawn = false;
        if (currentMessageObject.type == 0) {
            if (currentMessageObject.isOutOwner()) {
                textX = currentBackgroundDrawable.getBounds().left + dp(11);
            } else {
                textX = currentBackgroundDrawable.getBounds().left + dp(17);
            }
            if (hasGamePreview) {
                textX += dp(11);
                textY = dp(14) + namesOffset;
                if (siteNameLayout != null) {
                    textY += siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
                }
            } else {
                textY = dp(10) + namesOffset;
            }

            if (currentMessageObject.textLayoutBlocks != null && !currentMessageObject.textLayoutBlocks.isEmpty()) {
                if (firstVisibleBlockNum >= 0) {
                    for (int a = firstVisibleBlockNum; a <= lastVisibleBlockNum; a++) {
                        if (a >= currentMessageObject.textLayoutBlocks.size()) {
                            break;
                        }
                        MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(a);
                        canvas.save();
                        canvas.translate(textX - (int) Math.ceil(block.textXOffset), textY + block.textYOffset);
                        if (pressedLink != null && a == linkBlockNum) {
                            for (int b = 0; b < urlPath.size(); b++) {
                                canvas.drawPath(urlPath.get(b), urlPaint);
                            }
                        }
                        if (a == linkSelectionBlockNum && !urlPathSelection.isEmpty()) {
                            for (int b = 0; b < urlPathSelection.size(); b++) {
                                canvas.drawPath(urlPathSelection.get(b), urlSelectionPaint);
                            }
                        }
                        try {
                            block.textLayout.draw(canvas);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                        canvas.restore();
                    }
                }
            }

            if (hasLinkPreview || hasGamePreview) {
                int startY;
                int linkX;
                if (hasGamePreview) {
                    startY = dp(14) + namesOffset;
                    linkX = textX - dp(10);
                } else {
                    startY = textY + currentMessageObject.textHeight + dp(8);
                    linkX = textX + dp(1);
                }
                int linkPreviewY = startY;
                int smallImageStartY = 0;
                replyLinePaint.setColor(currentMessageObject.isOutOwner() ? Theme.MSG_OUT_WEB_PREVIEW_LINE_COLOR : Theme.MSG_IN_WEB_PREVIEW_LINE_COLOR);

                canvas.drawRect(linkX, linkPreviewY - dp(3), linkX + dp(2), linkPreviewY + linkPreviewHeight + dp(3), replyLinePaint);

                if (siteNameLayout != null) {
                    replyNamePaint.setColor(currentMessageObject.isOutOwner() ? Theme.MSG_OUT_SITE_NAME_TEXT_COLOR : Theme.MSG_IN_SITE_NAME_TEXT_COLOR);
                    canvas.save();
                    canvas.translate(linkX + dp(10), linkPreviewY - dp(3));
                    siteNameLayout.draw(canvas);
                    canvas.restore();
                    linkPreviewY += siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
                }
                if (hasGamePreview && currentMessageObject.textHeight != 0) {
                    startY += currentMessageObject.textHeight + dp(4);
                    linkPreviewY += currentMessageObject.textHeight + dp(4);
                }

                replyNamePaint.setColor(Theme.MSG_TEXT_COLOR);
                replyTextPaint.setColor(Theme.MSG_TEXT_COLOR);
                if (titleLayout != null) {
                    if (linkPreviewY != startY) {
                        linkPreviewY += dp(2);
                    }
                    smallImageStartY = linkPreviewY - dp(1);
                    canvas.save();
                    canvas.translate(linkX + dp(10) + titleX, linkPreviewY - dp(3));
                    titleLayout.draw(canvas);
                    canvas.restore();
                    linkPreviewY += titleLayout.getLineBottom(titleLayout.getLineCount() - 1);
                }

                if (authorLayout != null) {
                    if (linkPreviewY != startY) {
                        linkPreviewY += dp(2);
                    }
                    if (smallImageStartY == 0) {
                        smallImageStartY = linkPreviewY - dp(1);
                    }
                    canvas.save();
                    canvas.translate(linkX + dp(10) + authorX, linkPreviewY - dp(3));
                    authorLayout.draw(canvas);
                    canvas.restore();
                    linkPreviewY += authorLayout.getLineBottom(authorLayout.getLineCount() - 1);
                }

                if (descriptionLayout != null) {
                    if (linkPreviewY != startY) {
                        linkPreviewY += dp(2);
                    }
                    if (smallImageStartY == 0) {
                        smallImageStartY = linkPreviewY - dp(1);
                    }
                    descriptionY = linkPreviewY - dp(3);
                    canvas.save();
                    canvas.translate(linkX + dp(10) + descriptionX, descriptionY);
                    if (pressedLink != null && linkBlockNum == -10) {
                        for (int b = 0; b < urlPath.size(); b++) {
                            canvas.drawPath(urlPath.get(b), urlPaint);
                        }
                    }
                    descriptionLayout.draw(canvas);
                    canvas.restore();
                    linkPreviewY += descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
                }

                if (drawPhotoImage) {
                    if (linkPreviewY != startY) {
                        linkPreviewY += dp(2);
                    }

                    if (isSmallImage) {
                        photoImage.setImageCoords(linkX + backgroundWidth - dp(81), smallImageStartY, photoImage.getImageWidth(), photoImage.getImageHeight());
                    } else {
                        photoImage.setImageCoords(linkX + dp(10), linkPreviewY, photoImage.getImageWidth(), photoImage.getImageHeight());
                        if (drawImageButton) {
                            int size = dp(48);
                            buttonX = (int) (photoImage.getImageX() + (photoImage.getImageWidth() - size) / 2.0f);
                            buttonY = (int) (photoImage.getImageY() + (photoImage.getImageHeight() - size) / 2.0f);
                            radialProgress.setProgressRect(buttonX, buttonY, buttonX + dp(48), buttonY + dp(48));
                        }
                    }
                    imageDrawn = photoImage.draw(canvas);

                    if (videoInfoLayout != null) {
                        int x;
                        int y;
                        if (hasGamePreview) {
                            x = photoImage.getImageX() + dp(8.5f);
                            y = photoImage.getImageY() + dp(6);
                            Theme.timeBackgroundDrawable.setBounds(x - dp(4), y - dp(1.5f), x + durationWidth + dp(4), y + dp(16.5f));
                            Theme.timeBackgroundDrawable.draw(canvas);
                        } else {
                            x = photoImage.getImageX() + photoImage.getImageWidth() - dp(8) - durationWidth;
                            y = photoImage.getImageY() + photoImage.getImageHeight() - dp(19);
                            Theme.timeBackgroundDrawable.setBounds(x - dp(4), y - dp(1.5f), x + durationWidth + dp(4), y + dp(14.5f));
                            Theme.timeBackgroundDrawable.draw(canvas);
                        }

                        canvas.save();
                        canvas.translate(x, y);
                        videoInfoLayout.draw(canvas);
                        canvas.restore();
                    }
                }
            }
            drawTime = true;
        } else if (drawPhotoImage) {
            imageDrawn = photoImage.draw(canvas);
            drawTime = photoImage.getVisible();
        }

        if (buttonState == -1 && currentMessageObject.isSecretPhoto()) {
            int drawable = 4;
            if (currentMessageObject.messageOwner.destroyTime != 0) {
                if (currentMessageObject.isOutOwner()) {
                    drawable = 6;
                } else {
                    drawable = 5;
                }
            }
            setDrawableBounds(Theme.photoStatesDrawables[drawable][buttonPressed], buttonX, buttonY);
            Theme.photoStatesDrawables[drawable][buttonPressed].setAlpha((int) (255 * (1.0f - radialProgress.getAlpha())));
            Theme.photoStatesDrawables[drawable][buttonPressed].draw(canvas);
            if (!currentMessageObject.isOutOwner() && currentMessageObject.messageOwner.destroyTime != 0) {
                long msTime = System.currentTimeMillis() + ConnectionsManager.getInstance().getTimeDifference() * 1000;
                float progress = Math.max(0, (long) currentMessageObject.messageOwner.destroyTime * 1000 - msTime) / (currentMessageObject.messageOwner.ttl * 1000.0f);
                canvas.drawArc(deleteProgressRect, -90, -360 * progress, true, deleteProgressPaint);
                if (progress != 0) {
                    int offset = dp(2);
                    invalidate((int) deleteProgressRect.left - offset, (int) deleteProgressRect.top - offset, (int) deleteProgressRect.right + offset * 2, (int) deleteProgressRect.bottom + offset * 2);
                }
                updateSecretTimeText(currentMessageObject);
            }
        }

        if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || currentMessageObject.type == 8) {
            if (photoImage.getVisible() && !hasGamePreview) {
                setDrawableBounds(Theme.docMenuDrawable[3], otherX = photoImage.getImageX() + photoImage.getImageWidth() - dp(14), otherY = photoImage.getImageY() + dp(8.1f));
                Theme.docMenuDrawable[3].draw(canvas);
            }
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (currentMessageObject.isOutOwner()) {
                audioTitlePaint.setColor(Theme.MSG_OUT_AUDIO_TITLE_TEXT_COLOR);
                audioPerformerPaint.setColor(Theme.MSG_OUT_AUDIO_PERFORMER_TEXT_COLOR);
                audioTimePaint.setColor(Theme.MSG_OUT_AUDIO_DURATION_TEXT_COLOR);
                radialProgress.setProgressColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.MSG_OUT_AUDIO_SELECTED_PROGRESS_COLOR : Theme.MSG_OUT_AUDIO_PROGRESS_COLOR);
            } else {
                audioTitlePaint.setColor(Theme.MSG_IN_AUDIO_TITLE_TEXT_COLOR);
                audioPerformerPaint.setColor(Theme.MSG_IN_AUDIO_PERFORMER_TEXT_COLOR);
                audioTimePaint.setColor(Theme.MSG_IN_AUDIO_DURATION_TEXT_COLOR);
                radialProgress.setProgressColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.MSG_IN_AUDIO_SELECTED_PROGRESS_COLOR : Theme.MSG_IN_AUDIO_PROGRESS_COLOR);
            }
            radialProgress.draw(canvas);

            canvas.save();
            canvas.translate(timeAudioX + songX, dp(13) + namesOffset + mediaOffsetY);
            songLayout.draw(canvas);
            canvas.restore();

            canvas.save();
            if (MediaController.getInstance().isPlayingAudio(currentMessageObject)) {
                canvas.translate(seekBarX, seekBarY);
                seekBar.draw(canvas);
            } else {
                canvas.translate(timeAudioX + performerX, dp(35) + namesOffset + mediaOffsetY);
                performerLayout.draw(canvas);
            }
            canvas.restore();

            canvas.save();
            canvas.translate(timeAudioX, dp(57) + namesOffset + mediaOffsetY);
            durationLayout.draw(canvas);
            canvas.restore();

            Drawable menuDrawable;
            if (currentMessageObject.isOutOwner()) {
                menuDrawable = Theme.docMenuDrawable[1];
            } else {
                menuDrawable = Theme.docMenuDrawable[isDrawSelectedBackground() ? 2 : 0];
            }
            setDrawableBounds(menuDrawable, otherX = buttonX + backgroundWidth - dp(currentMessageObject.type == 0 ? 58 : 48), otherY = buttonY - dp(5));
            menuDrawable.draw(canvas);
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
            if (currentMessageObject.isOutOwner()) {
                audioTimePaint.setColor(isDrawSelectedBackground() ? Theme.MSG_OUT_AUDIO_DURATION_SELECTED_TEXT_COLOR : Theme.MSG_OUT_AUDIO_DURATION_TEXT_COLOR);
                radialProgress.setProgressColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.MSG_OUT_AUDIO_SELECTED_PROGRESS_COLOR : Theme.MSG_OUT_AUDIO_PROGRESS_COLOR);
            } else {
                audioTimePaint.setColor(isDrawSelectedBackground() ? Theme.MSG_IN_AUDIO_DURATION_SELECTED_TEXT_COLOR : Theme.MSG_IN_AUDIO_DURATION_TEXT_COLOR);
                radialProgress.setProgressColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.MSG_IN_AUDIO_SELECTED_PROGRESS_COLOR : Theme.MSG_IN_AUDIO_PROGRESS_COLOR);
            }
            radialProgress.draw(canvas);

            canvas.save();
            if (useSeekBarWaweform) {
                canvas.translate(seekBarX + dp(13), seekBarY);
                seekBarWaveform.draw(canvas);
            } else {
                canvas.translate(seekBarX, seekBarY);
                seekBar.draw(canvas);
            }
            canvas.restore();

            canvas.save();
            canvas.translate(timeAudioX, dp(44) + namesOffset + mediaOffsetY);
            durationLayout.draw(canvas);
            canvas.restore();

            if (currentMessageObject.type != 0 && currentMessageObject.messageOwner.to_id.channel_id == 0 && currentMessageObject.isContentUnread()) {
                docBackPaint.setColor(currentMessageObject.isOutOwner() ? Theme.MSG_OUT_VOICE_SEEKBAR_FILL_COLOR : Theme.MSG_IN_VOICE_SEEKBAR_FILL_COLOR);
                canvas.drawCircle(timeAudioX + timeWidthAudio + dp(6), dp(51) + namesOffset + mediaOffsetY, dp(3), docBackPaint);
            }
        }

        if (currentMessageObject.type == 1 || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
            if (photoImage.getVisible()) {
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                    setDrawableBounds(Theme.docMenuDrawable[3], otherX = photoImage.getImageX() + photoImage.getImageWidth() - dp(14), otherY = photoImage.getImageY() + dp(8.1f));
                    Theme.docMenuDrawable[3].draw(canvas);
                }

                if (infoLayout != null && (buttonState == 1 || buttonState == 0 || buttonState == 3 || currentMessageObject.isSecretPhoto())) {
                    infoPaint.setColor(Theme.MSG_MEDIA_INFO_TEXT_COLOR);
                    setDrawableBounds(Theme.timeBackgroundDrawable, photoImage.getImageX() + dp(4), photoImage.getImageY() + dp(4), infoWidth + dp(8), dp(16.5f));
                    Theme.timeBackgroundDrawable.draw(canvas);

                    canvas.save();
                    canvas.translate(photoImage.getImageX() + dp(8), photoImage.getImageY() + dp(5.5f));
                    infoLayout.draw(canvas);
                    canvas.restore();
                }
            }
        } else {
            if (currentMessageObject.type == 4) {
                if (docTitleLayout != null) {
                    if (currentMessageObject.isOutOwner()) {
                        locationTitlePaint.setColor(Theme.MSG_OUT_VENUE_NAME_TEXT_COLOR);
                        locationAddressPaint.setColor(isDrawSelectedBackground() ? Theme.MSG_OUT_VENUE_INFO_SELECTED_TEXT_COLOR : Theme.MSG_OUT_VENUE_INFO_TEXT_COLOR);
                    } else {
                        locationTitlePaint.setColor(Theme.MSG_IN_VENUE_NAME_TEXT_COLOR);
                        locationAddressPaint.setColor(isDrawSelectedBackground() ? Theme.MSG_IN_VENUE_INFO_SELECTED_TEXT_COLOR : Theme.MSG_IN_VENUE_INFO_TEXT_COLOR);
                    }

                    canvas.save();
                    canvas.translate(docTitleOffsetX + photoImage.getImageX() + photoImage.getImageWidth() + dp(10), photoImage.getImageY() + dp(8));
                    docTitleLayout.draw(canvas);
                    canvas.restore();

                    if (infoLayout != null) {
                        canvas.save();
                        canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + dp(10), photoImage.getImageY() + docTitleLayout.getLineBottom(docTitleLayout.getLineCount() - 1) + dp(13));
                        infoLayout.draw(canvas);
                        canvas.restore();
                    }
                }
            } else if (currentMessageObject.type == 12) {
                contactNamePaint.setColor(currentMessageObject.isOutOwner() ? Theme.MSG_OUT_CONTACT_NAME_TEXT_COLOR : Theme.MSG_IN_CONTACT_NAME_TEXT_COLOR);
                contactPhonePaint.setColor(currentMessageObject.isOutOwner() ? Theme.MSG_OUT_CONTACT_PHONE_TEXT_COLOR : Theme.MSG_IN_CONTACT_PHONE_TEXT_COLOR);
                if (titleLayout != null) {
                    canvas.save();
                    canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + dp(9), dp(16) + namesOffset);
                    titleLayout.draw(canvas);
                    canvas.restore();
                }
                if (docTitleLayout != null) {
                    canvas.save();
                    canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + dp(9), dp(39) + namesOffset);
                    docTitleLayout.draw(canvas);
                    canvas.restore();
                }

                Drawable menuDrawable;
                if (currentMessageObject.isOutOwner()) {
                    menuDrawable = Theme.docMenuDrawable[1];
                } else {
                    menuDrawable = Theme.docMenuDrawable[isDrawSelectedBackground() ? 2 : 0];
                }
                setDrawableBounds(menuDrawable, otherX = photoImage.getImageX() + backgroundWidth - dp(48), otherY = photoImage.getImageY() - dp(5));
                menuDrawable.draw(canvas);
            }
        }

        if (captionLayout != null) {
            canvas.save();
            if (currentMessageObject.type == 1 || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || currentMessageObject.type == 8) {
                canvas.translate(captionX = photoImage.getImageX() + dp(5), captionY = photoImage.getImageY() + photoImage.getImageHeight() + dp(6));
            } else {
                canvas.translate(captionX = backgroundDrawableLeft + dp(currentMessageObject.isOutOwner() ? 11 : 17), captionY = totalHeight - captionHeight - dp(10));
            }
            if (pressedLink != null) {
                for (int b = 0; b < urlPath.size(); b++) {
                    canvas.drawPath(urlPath.get(b), urlPaint);
                }
            }
            try {
                captionLayout.draw(canvas);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            canvas.restore();
        }

        if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
            Drawable menuDrawable;
            if (currentMessageObject.isOutOwner()) {
                docNamePaint.setColor(Theme.MSG_OUT_FILE_NAME_TEXT_COLOR);
                infoPaint.setColor(isDrawSelectedBackground() ? Theme.MSG_OUT_FILE_INFO_SELECTED_TEXT_COLOR : Theme.MSG_OUT_FILE_INFO_TEXT_COLOR);
                docBackPaint.setColor(isDrawSelectedBackground() ? Theme.MSG_OUT_FILE_BACKGROUND_SELECTED_COLOR : Theme.MSG_OUT_FILE_BACKGROUND_COLOR);
                menuDrawable = Theme.docMenuDrawable[1];
            } else {
                docNamePaint.setColor(Theme.MSG_IN_FILE_NAME_TEXT_COLOR);
                infoPaint.setColor(isDrawSelectedBackground() ? Theme.MSG_IN_FILE_INFO_SELECTED_TEXT_COLOR : Theme.MSG_IN_FILE_INFO_TEXT_COLOR);
                docBackPaint.setColor(isDrawSelectedBackground() ? Theme.MSG_IN_FILE_BACKGROUND_SELECTED_COLOR : Theme.MSG_IN_FILE_BACKGROUND_COLOR);
                menuDrawable = Theme.docMenuDrawable[isDrawSelectedBackground() ? 2 : 0];
            }

            int x;
            int titleY;
            int subtitleY;
            if (drawPhotoImage) {
                if (currentMessageObject.type == 0) {
                    setDrawableBounds(menuDrawable, otherX = photoImage.getImageX() + backgroundWidth - dp(56), otherY = photoImage.getImageY() + dp(1));
                } else {
                    setDrawableBounds(menuDrawable, otherX = photoImage.getImageX() + backgroundWidth - dp(40), otherY = photoImage.getImageY() + dp(1));
                }

                x = photoImage.getImageX() + photoImage.getImageWidth() + dp(10);
                titleY = photoImage.getImageY() + dp(8);
                subtitleY = photoImage.getImageY() + docTitleLayout.getLineBottom(docTitleLayout.getLineCount() - 1) + dp(13);
                if (buttonState >= 0 && buttonState < 4) {
                    if (!imageDrawn) {
                        int image = buttonState;
                        if (buttonState == 0) {
                            image = currentMessageObject.isOutOwner() ? 7 : 10;
                        } else if (buttonState == 1) {
                            image = currentMessageObject.isOutOwner() ? 8 : 11;
                        }
                        radialProgress.swapBackground(Theme.photoStatesDrawables[image][isDrawSelectedBackground() || buttonPressed != 0 ? 1 : 0]);
                    } else {
                        radialProgress.swapBackground(Theme.photoStatesDrawables[buttonState][buttonPressed]);
                    }
                }

                if (!imageDrawn) {
                    rect.set(photoImage.getImageX(), photoImage.getImageY(), photoImage.getImageX() + photoImage.getImageWidth(), photoImage.getImageY() + photoImage.getImageHeight());
                    canvas.drawRoundRect(rect, dp(3), dp(3), docBackPaint);
                    if (currentMessageObject.isOutOwner()) {
                        radialProgress.setProgressColor(isDrawSelectedBackground() ? Theme.MSG_OUT_FILE_PROGRESS_SELECTED_COLOR : Theme.MSG_OUT_FILE_PROGRESS_COLOR);
                    } else {
                        radialProgress.setProgressColor(isDrawSelectedBackground() ? Theme.MSG_IN_FILE_PROGRESS_SELECTED_COLOR : Theme.MSG_IN_FILE_PROGRESS_COLOR);
                    }
                } else {
                    if (buttonState == -1) {
                        radialProgress.setHideCurrentDrawable(true);
                    }
                    radialProgress.setProgressColor(Theme.MSG_MEDIA_PROGRESS_COLOR);
                }
            } else {
                setDrawableBounds(menuDrawable, otherX = buttonX + backgroundWidth - dp(currentMessageObject.type == 0 ? 58 : 48), otherY = buttonY - dp(5));
                x = buttonX + dp(53);
                titleY = buttonY + dp(4);
                subtitleY = buttonY + dp(27);
                if (currentMessageObject.isOutOwner()) {
                    radialProgress.setProgressColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.MSG_OUT_AUDIO_SELECTED_PROGRESS_COLOR : Theme.MSG_OUT_AUDIO_PROGRESS_COLOR);
                } else {
                    radialProgress.setProgressColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.MSG_IN_AUDIO_SELECTED_PROGRESS_COLOR : Theme.MSG_IN_AUDIO_PROGRESS_COLOR);
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
                FileLog.e("tmessages", e);
            }

            try {
                if (infoLayout != null) {
                    canvas.save();
                    canvas.translate(x, subtitleY);
                    infoLayout.draw(canvas);
                    canvas.restore();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        if (drawImageButton && photoImage.getVisible()) {
            radialProgress.draw(canvas);
        }

        if (!botButtons.isEmpty()) {
            int addX;
            if (currentMessageObject.isOutOwner()) {
                addX = getMeasuredWidth() - widthForButtons - dp(10);
            } else {
                addX = backgroundDrawableLeft + dp(mediaBackground ? 1 : 7);
            }
            for (int a = 0; a < botButtons.size(); a++) {
                BotButton button = botButtons.get(a);
                int y = button.y + layoutHeight - dp(2);
                Theme.systemDrawable.setColorFilter(a == pressedBotButton ? Theme.colorPressedFilter : Theme.colorFilter);
                Theme.systemDrawable.setBounds(button.x + addX, y, button.x + addX + button.width, y + button.height);
                Theme.systemDrawable.draw(canvas);
                canvas.save();
                canvas.translate(button.x + addX + dp(5), y + (dp(44) - button.title.getLineBottom(button.title.getLineCount() - 1)) / 2);
                button.title.draw(canvas);
                canvas.restore();
                if (button.button instanceof TLRPC.TL_keyboardButtonUrl) {
                    int x = button.x + button.width - dp(3) - Theme.botLink.getIntrinsicWidth() + addX;
                    setDrawableBounds(Theme.botLink, x, y + dp(3));
                    Theme.botLink.draw(canvas);
                } else if (button.button instanceof TLRPC.TL_keyboardButtonSwitchInline) {
                    int x = button.x + button.width - dp(3) - Theme.botInline.getIntrinsicWidth() + addX;
                    setDrawableBounds(Theme.botInline, x, y + dp(3));
                    Theme.botInline.draw(canvas);
                } else if (button.button instanceof TLRPC.TL_keyboardButtonCallback || button.button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation || button.button instanceof TLRPC.TL_keyboardButtonGame) {
                    boolean drawProgress = (button.button instanceof TLRPC.TL_keyboardButtonCallback || button.button instanceof TLRPC.TL_keyboardButtonGame) && SendMessagesHelper.getInstance().isSendingCallback(currentMessageObject, button.button) ||
                            button.button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation && SendMessagesHelper.getInstance().isSendingCurrentLocation(currentMessageObject, button.button);
                    if (drawProgress || !drawProgress && button.progressAlpha != 0) {
                        botProgressPaint.setAlpha(Math.min(255, (int) (button.progressAlpha * 255)));
                        int x = button.x + button.width - dp(9 + 3) + addX;
                        rect.set(x, y + dp(4), x + dp(8), y + dp(8 + 4));
                        canvas.drawArc(rect, button.angle, 220, false, botProgressPaint);
                        invalidate((int) rect.left - dp(2), (int) rect.top - dp(2), (int) rect.right + dp(2), (int) rect.bottom + dp(2));
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

    private Drawable getDrawableForCurrentState() {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (buttonState == -1) {
                return null;
            }
            radialProgress.setAlphaForPrevious(false);
            return Theme.fileStatesDrawable[currentMessageObject.isOutOwner() ? buttonState : buttonState + 5][isDrawSelectedBackground() || buttonPressed != 0 ? 1 : 0];
        } else {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT && !drawPhotoImage) {
                radialProgress.setAlphaForPrevious(false);
                if (buttonState == -1) {
                    return Theme.fileStatesDrawable[currentMessageObject.isOutOwner() ? 3 : 8][isDrawSelectedBackground() ? 1 : 0];
                } else if (buttonState == 0) {
                    return Theme.fileStatesDrawable[currentMessageObject.isOutOwner() ? 2 : 7][isDrawSelectedBackground() ? 1 : 0];
                } else if (buttonState == 1) {
                    return Theme.fileStatesDrawable[currentMessageObject.isOutOwner() ? 4 : 9][isDrawSelectedBackground() ? 1 : 0];
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
                        return Theme.photoStatesDrawables[image][isDrawSelectedBackground() || buttonPressed != 0 ? 1 : 0];
                    } else {
                        return Theme.photoStatesDrawables[buttonState][buttonPressed];
                    }
                } else if (buttonState == -1 && documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                    return Theme.photoStatesDrawables[currentMessageObject.isOutOwner() ? 9 : 12][isDrawSelectedBackground() ? 1 : 0];
                }
            }
        }
        return null;
    }

    private int getMaxNameWidth() {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER) {
            int maxWidth;
            if (isTablet()) {
                if (isChat && !currentMessageObject.isOutOwner() && currentMessageObject.isFromUser()) {
                    maxWidth = getMinTabletSide() - dp(42);
                } else {
                    maxWidth = getMinTabletSide();
                }
            } else {
                if (isChat && !currentMessageObject.isOutOwner() && currentMessageObject.isFromUser()) {
                    maxWidth = Math.min(displaySize.x, displaySize.y) - dp(42);
                } else {
                    maxWidth = Math.min(displaySize.x, displaySize.y);
                }
            }
            return maxWidth - backgroundWidth - dp(57);
        }
        return backgroundWidth - dp(mediaBackground ? 22 : 31);
    }

    public void updateButtonState(boolean animated) {
        String fileName = null;
        boolean fileExists = false;
        if (currentMessageObject.type == 1) {
            if (currentPhotoObject == null) {
                return;
            }
            fileName = FileLoader.getAttachFileName(currentPhotoObject);
            fileExists = currentMessageObject.mediaExists;
        } else if (currentMessageObject.type == 8 || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || currentMessageObject.type == 9 || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
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
        if (TextUtils.isEmpty(fileName)) {
            radialProgress.setBackground(null, false, false);
            return;
        }
        boolean fromBot = currentMessageObject.messageOwner.params != null && currentMessageObject.messageOwner.params.containsKey("query_id");

        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (currentMessageObject.isOut() && currentMessageObject.isSending() || currentMessageObject.isSendError() && fromBot) {
                MediaController.getInstance().addLoadingFileObserver(currentMessageObject.messageOwner.attachPath, currentMessageObject, this);
                buttonState = 4;
                radialProgress.setBackground(getDrawableForCurrentState(), !fromBot, animated);
                if (!fromBot) {
                    Float progress = ImageLoader.getInstance().getFileProgress(currentMessageObject.messageOwner.attachPath);
                    if (progress == null && SendMessagesHelper.getInstance().isSendingMessage(currentMessageObject.getId())) {
                        progress = 1.0f;
                    }
                    radialProgress.setProgress(progress != null ? progress : 0, false);
                } else {
                    radialProgress.setProgress(0, false);
                }
            } else {
                if (fileExists) {
                    MediaController.getInstance().removeLoadingFileObserver(this);
                    boolean playing = MediaController.getInstance().isPlayingAudio(currentMessageObject);
                    if (!playing || playing && MediaController.getInstance().isAudioPaused()) {
                        buttonState = 0;
                    } else {
                        buttonState = 1;
                    }
                    radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                } else {
                    MediaController.getInstance().addLoadingFileObserver(fileName, currentMessageObject, this);
                    if (!FileLoader.getInstance().isLoadingFile(fileName)) {
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
            updateAudioProgress();
        } else if (currentMessageObject.type == 0 && documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT && documentAttachType != DOCUMENT_ATTACH_TYPE_VIDEO) {
            if (currentPhotoObject == null || !drawImageButton) {
                return;
            }
            if (!fileExists) {
                MediaController.getInstance().addLoadingFileObserver(fileName, currentMessageObject, this);
                float setProgress = 0;
                boolean progressVisible = false;
                if (!FileLoader.getInstance().isLoadingFile(fileName)) {
                    if (!cancelLoading &&
                            (documentAttachType == 0 && MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO) ||
                                    documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_GIF))) {
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
                MediaController.getInstance().removeLoadingFileObserver(this);
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && !photoImage.isAllowStartAnimation()) {
                    buttonState = 2;
                } else {
                    buttonState = -1;
                }
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                invalidate();
            }
        } else {
            if (currentMessageObject.isOut() && currentMessageObject.isSending()) {
                if (currentMessageObject.messageOwner.attachPath != null && currentMessageObject.messageOwner.attachPath.length() > 0) {
                    MediaController.getInstance().addLoadingFileObserver(currentMessageObject.messageOwner.attachPath, currentMessageObject, this);
                    boolean needProgress = currentMessageObject.messageOwner.attachPath == null || !currentMessageObject.messageOwner.attachPath.startsWith("http");
                    HashMap<String, String> params = currentMessageObject.messageOwner.params;
                    if (currentMessageObject.messageOwner.message != null && params != null && (params.containsKey("url") || params.containsKey("bot"))) {
                        needProgress = false;
                        buttonState = -1;
                    } else {
                        buttonState = 1;
                    }
                    radialProgress.setBackground(getDrawableForCurrentState(), needProgress, animated);
                    if (needProgress) {
                        Float progress = ImageLoader.getInstance().getFileProgress(currentMessageObject.messageOwner.attachPath);
                        if (progress == null && SendMessagesHelper.getInstance().isSendingMessage(currentMessageObject.getId())) {
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
                    MediaController.getInstance().removeLoadingFileObserver(this);
                }
                if (!fileExists) {
                    MediaController.getInstance().addLoadingFileObserver(fileName, currentMessageObject, this);
                    float setProgress = 0;
                    boolean progressVisible = false;
                    if (!FileLoader.getInstance().isLoadingFile(fileName)) {
                        if (!cancelLoading &&
                                (currentMessageObject.type == 1 && MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO) ||
                                        currentMessageObject.type == 8 && MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_GIF) && MessageObject.isNewGifDocument(currentMessageObject.messageOwner.media.document)) ) {
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
                } else {
                    MediaController.getInstance().removeLoadingFileObserver(this);
                    if (currentMessageObject.type == 8 && !photoImage.isAllowStartAnimation()) {
                        buttonState = 2;
                    } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                        buttonState = 3;
                    } else {
                        buttonState = -1;
                    }
                    radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                    if (photoNotSet) {
                        setMessageObject(currentMessageObject);
                    }
                    invalidate();
                }
            }
        }
    }

    private void didPressedButton(boolean animated) {
        if (buttonState == 0) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                if (delegate.needPlayAudio(currentMessageObject)) {
                    buttonState = 1;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            } else {
                cancelLoading = false;
                radialProgress.setProgress(0, false);
                if (currentMessageObject.type == 1) {
                    photoImage.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilter, currentPhotoObject.size, null, false);
                } else if (currentMessageObject.type == 8) {
                    currentMessageObject.audioProgress = 2;
                    photoImage.setImage(currentMessageObject.messageOwner.media.document, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, currentMessageObject.messageOwner.media.document.size, null, false);
                } else if (currentMessageObject.type == 9) {
                    FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.document, false, false);
                } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                    FileLoader.getInstance().loadFile(documentAttach, true, false);
                } else if (currentMessageObject.type == 0 && documentAttachType != DOCUMENT_ATTACH_TYPE_NONE) {
                    if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                        photoImage.setImage(currentMessageObject.messageOwner.media.webpage.document, null, currentPhotoObject.location, currentPhotoFilter, currentMessageObject.messageOwner.media.webpage.document.size, null, false);
                        currentMessageObject.audioProgress = 2;
                    } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.webpage.document, false, false);
                    }
                } else {
                    photoImage.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, 0, null, false);
                }
                buttonState = 1;
                radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
                invalidate();
            }
        } else if (buttonState == 1) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                boolean result = MediaController.getInstance().pauseAudio(currentMessageObject);
                if (result) {
                    buttonState = 0;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            } else {
                if (currentMessageObject.isOut() && currentMessageObject.isSending()) {
                    delegate.didPressedCancelSendButton(this);
                } else {
                    cancelLoading = true;
                    if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        FileLoader.getInstance().cancelLoadFile(documentAttach);
                    } else if (currentMessageObject.type == 0 || currentMessageObject.type == 1 || currentMessageObject.type == 8) {
                        photoImage.cancelLoadImage();
                    } else if (currentMessageObject.type == 9) {
                        FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.document);
                    }
                    buttonState = 0;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                    invalidate();
                }
            }
        } else if (buttonState == 2) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                radialProgress.setProgress(0, false);
                FileLoader.getInstance().loadFile(documentAttach, true, false);
                buttonState = 4;
                radialProgress.setBackground(getDrawableForCurrentState(), true, false);
                invalidate();
            } else {
                photoImage.setAllowStartAnimation(true);
                photoImage.startAnimation();
                currentMessageObject.audioProgress = 0;
                buttonState = -1;
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            }
        } else if (buttonState == 3) {
            delegate.didPressedImage(this);
        } else if (buttonState == 4) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                if (currentMessageObject.isOut() && currentMessageObject.isSending() || currentMessageObject.isSendError()) {
                    if (delegate != null) {
                        delegate.didPressedCancelSendButton(this);
                    }
                } else {
                    FileLoader.getInstance().cancelLoadFile(documentAttach);
                    buttonState = 2;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            }
        }
    }

    @Override
    public void onFailedDownload(String fileName) {
        updateButtonState(documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            updateButtonState(true);
            updateWaveform();
        } else {
            radialProgress.setProgress(1, true);
            if (currentMessageObject.type == 0) {
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && currentMessageObject.audioProgress != 1) {
                    buttonState = 2;
                    didPressedButton(true);
                } else if (!photoNotSet) {
                    updateButtonState(true);
                } else {
                    setMessageObject(currentMessageObject);
                }
            } else {
                if (!photoNotSet || currentMessageObject.type == 8 && currentMessageObject.audioProgress != 1) {
                    if (currentMessageObject.type == 8 && currentMessageObject.audioProgress != 1) {
                        photoNotSet = false;
                        buttonState = 2;
                        didPressedButton(true);
                    } else {
                        updateButtonState(true);
                    }
                }
                if (photoNotSet) {
                    setMessageObject(currentMessageObject);
                }
            }
        }
    }

    @Override
    public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb) {
        if (currentMessageObject != null && set && !thumb && !currentMessageObject.mediaExists && !currentMessageObject.attachPathExists) {
            currentMessageObject.mediaExists = true;
            updateButtonState(true);
        }
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        radialProgress.setProgress(progress, true);
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (buttonState != 4) {
                updateButtonState(false);
            }
        } else {
            if (buttonState != 1) {
                updateButtonState(false);
            }
        }
    }

    @Override
    public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {
        radialProgress.setProgress(progress, true);
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
        boolean hasSign = !messageObject.isOutOwner() && messageObject.messageOwner.from_id > 0 && messageObject.messageOwner.post;
        TLRPC.User signUser = MessagesController.getInstance().getUser(messageObject.messageOwner.from_id);
        if (hasSign && signUser == null) {
            hasSign = false;
        }
        String timeString;
        TLRPC.User author = null;
        if (currentMessageObject.isFromUser()) {
            author = MessagesController.getInstance().getUser(messageObject.messageOwner.from_id);
        }
        if (messageObject.messageOwner.via_bot_id == 0 && messageObject.messageOwner.via_bot_name == null && (author == null || !author.bot) && (messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_EDITED) != 0) {
            timeString = LocaleController.getString("EditedMessage", R.string.EditedMessage) + " " + LocaleController.getInstance().formatterDay.format((long) (messageObject.messageOwner.date) * 1000);
        } else {
            timeString = LocaleController.getInstance().formatterDay.format((long) (messageObject.messageOwner.date) * 1000);
        }
        if (hasSign) {
            currentTimeString = ", " + timeString;
        } else {
            currentTimeString = timeString;
        }
        timeTextWidth = timeWidth = (int) Math.ceil(timePaint.measureText(currentTimeString));
        if ((messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
            currentViewsString = String.format("%s", LocaleController.formatShortNumber(Math.max(1, messageObject.messageOwner.views), null));
            viewsTextWidth = (int) Math.ceil(timePaint.measureText(currentViewsString));
            timeWidth += viewsTextWidth + Theme.viewsCountDrawable[0].getIntrinsicWidth() + dp(10);
        }
        if (hasSign) {
            if (availableTimeWidth == 0) {
                availableTimeWidth = dp(1000);
            }
            CharSequence name = ContactsController.formatName(signUser.first_name, signUser.last_name).replace('\n', ' ');
            int widthForSign = availableTimeWidth - timeWidth;
            int width = (int) Math.ceil(timePaint.measureText(name, 0, name.length()));
            if (width > widthForSign) {
                name = TextUtils.ellipsize(name, timePaint, widthForSign, TextUtils.TruncateAt.END);
                width = widthForSign;
            }
            currentTimeString = name + currentTimeString;
            timeTextWidth += width;
            timeWidth += width;
        }
    }

    private boolean isDrawSelectedBackground() {
        return isPressed() && isCheckPressed || !isCheckPressed && isPressed || isHighlighted;
    }

    private boolean checkNeedDrawShareButton(MessageObject messageObject) {
        if (messageObject.type == 13) {
            return false;
        } else if (messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.channel_id != 0 && !messageObject.isOut()) {
            return true;
        } else if (messageObject.isFromUser()) {
            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty || messageObject.messageOwner.media == null || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && !(messageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage)) {
                return false;
            }
            TLRPC.User user = MessagesController.getInstance().getUser(messageObject.messageOwner.from_id);
            if (user != null && user.bot) {
                return true;
            }
            if (!messageObject.isOut()) {
                if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                    return true;
                }
                if (messageObject.isMegagroup()) {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(messageObject.messageOwner.to_id.channel_id);
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

    private void setMessageObjectInternal(MessageObject messageObject) {
        if ((messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
            if (currentMessageObject.isContentUnread() && !currentMessageObject.isOut()) {
                MessagesController.getInstance().addToViewsQueue(currentMessageObject.messageOwner, false);
                currentMessageObject.setContentIsRead();
            } else if (!currentMessageObject.viewsReloaded) {
                MessagesController.getInstance().addToViewsQueue(currentMessageObject.messageOwner, true);
                currentMessageObject.viewsReloaded = true;
            }
        }

        if (currentMessageObject.isFromUser()) {
            currentUser = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.from_id < 0) {
            currentChat = MessagesController.getInstance().getChat(-currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.post) {
            currentChat = MessagesController.getInstance().getChat(currentMessageObject.messageOwner.to_id.channel_id);
        }

        if (isChat && !messageObject.isOutOwner() && messageObject.isFromUser()) {
            isAvatarVisible = true;
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
            avatarImage.setImage(currentPhoto, "50_50", avatarDrawable, null, false);
        }


        measureTime(messageObject);

        namesOffset = 0;

        String viaUsername = null;
        CharSequence viaString = null;
        if (messageObject.messageOwner.via_bot_id != 0) {
            TLRPC.User botUser = MessagesController.getInstance().getUser(messageObject.messageOwner.via_bot_id);
            if (botUser != null && botUser.username != null && botUser.username.length() > 0) {
                viaUsername = "@" + botUser.username;
                viaString = replaceTags(String.format(" via <b>%s</b>", viaUsername));
                viaWidth = (int) Math.ceil(replyNamePaint.measureText(viaString, 0, viaString.length()));
                currentViaBotUser = botUser;
            }
        } else if (messageObject.messageOwner.via_bot_name != null && messageObject.messageOwner.via_bot_name.length() > 0) {
            viaUsername = "@" + messageObject.messageOwner.via_bot_name;
            viaString = replaceTags(String.format(" via <b>%s</b>", viaUsername));
            viaWidth = (int) Math.ceil(replyNamePaint.measureText(viaString, 0, viaString.length()));
        }

        boolean authorName = drawName && isChat && !currentMessageObject.isOutOwner();
        boolean viaBot = (messageObject.messageOwner.fwd_from == null || messageObject.type == 14) && viaUsername != null;
        if (authorName || viaBot) {
            drawNameLayout = true;
            nameWidth = getMaxNameWidth();
            if (nameWidth < 0) {
                nameWidth = dp(100);
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
            CharSequence nameStringFinal = TextUtils.ellipsize(currentNameString.replace('\n', ' '), namePaint, nameWidth - (viaBot ? viaWidth : 0), TextUtils.TruncateAt.END);
            if (viaBot) {
                viaNameWidth = (int) Math.ceil(namePaint.measureText(nameStringFinal, 0, nameStringFinal.length()));
                if (viaNameWidth != 0) {
                    viaNameWidth += dp(4);
                }
                int color;
                if (currentMessageObject.type == 13) {
                    color = Theme.MSG_STICKER_VIA_BOT_NAME_TEXT_COLOR;
                } else {
                    color = currentMessageObject.isOutOwner() ? Theme.MSG_OUT_VIA_BOT_NAME_TEXT_COLOR : Theme.MSG_IN_VIA_BOT_NAME_TEXT_COLOR;
                }
                if (currentNameString.length() > 0) {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format("%s via %s", nameStringFinal, viaUsername));
                    stringBuilder.setSpan(new TypefaceSpan(Typeface.DEFAULT, 0, color), nameStringFinal.length() + 1, nameStringFinal.length() + 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    stringBuilder.setSpan(new TypefaceSpan(getTypeface("fonts/rmedium.ttf"), 0, color), nameStringFinal.length() + 5, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    nameStringFinal = stringBuilder;
                } else {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format("via %s", viaUsername));
                    stringBuilder.setSpan(new TypefaceSpan(Typeface.DEFAULT, 0, color), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    stringBuilder.setSpan(new TypefaceSpan(getTypeface("fonts/rmedium.ttf"), 0, color), 4, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    nameStringFinal = stringBuilder;
                }
                nameStringFinal = TextUtils.ellipsize(nameStringFinal, namePaint, nameWidth, TextUtils.TruncateAt.END);
            }
            try {
                nameLayout = new StaticLayout(nameStringFinal, namePaint, nameWidth + dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (nameLayout != null && nameLayout.getLineCount() > 0) {
                    nameWidth = (int) Math.ceil(nameLayout.getLineWidth(0));
                    if (messageObject.type != 13) {
                        namesOffset += dp(19);
                    }
                    nameOffsetX = nameLayout.getLineLeft(0);
                } else {
                    nameWidth = 0;
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
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
        if (drawForwardedName && messageObject.isForwarded()) {
            if (messageObject.messageOwner.fwd_from.channel_id != 0) {
                currentForwardChannel = MessagesController.getInstance().getChat(messageObject.messageOwner.fwd_from.channel_id);
            }
            if (messageObject.messageOwner.fwd_from.from_id != 0) {
                currentForwardUser = MessagesController.getInstance().getUser(messageObject.messageOwner.fwd_from.from_id);
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
                int fromWidth = (int) Math.ceil(forwardNamePaint.measureText(LocaleController.getString("From", R.string.From) + " "));
                CharSequence name = TextUtils.ellipsize(currentForwardNameString.replace('\n', ' '), replyNamePaint, forwardedNameWidth - fromWidth - viaWidth, TextUtils.TruncateAt.END);
                CharSequence lastLine;
                if (viaString != null) {
                    viaNameWidth = (int) Math.ceil(forwardNamePaint.measureText(LocaleController.getString("From", R.string.From) + " " + name));
                    lastLine = replaceTags(String.format("%s <b>%s</b> via <b>%s</b>", LocaleController.getString("From", R.string.From), name, viaUsername));
                } else {
                    lastLine = replaceTags(String.format("%s <b>%s</b>", LocaleController.getString("From", R.string.From), name));
                }
                lastLine = TextUtils.ellipsize(lastLine, forwardNamePaint, forwardedNameWidth, TextUtils.TruncateAt.END);
                try {
                    forwardedNameLayout[1] = new StaticLayout(lastLine, forwardNamePaint, forwardedNameWidth + dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    lastLine = TextUtils.ellipsize(replaceTags(LocaleController.getString("ForwardedMessage", R.string.ForwardedMessage)), forwardNamePaint, forwardedNameWidth, TextUtils.TruncateAt.END);
                    forwardedNameLayout[0] = new StaticLayout(lastLine, forwardNamePaint, forwardedNameWidth + dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    forwardedNameWidth = Math.max((int) Math.ceil(forwardedNameLayout[0].getLineWidth(0)), (int) Math.ceil(forwardedNameLayout[1].getLineWidth(0)));
                    forwardNameOffsetX[0] = forwardedNameLayout[0].getLineLeft(0);
                    forwardNameOffsetX[1] = forwardedNameLayout[1].getLineLeft(0);
                    namesOffset += dp(36);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }

        if (messageObject.isReply()) {
            namesOffset += dp(42);
            if (messageObject.type != 0) {
                if (messageObject.type == 13) {
                    namesOffset -= dp(42);
                } else {
                    namesOffset += dp(5);
                }
            }

            int maxWidth = getMaxNameWidth();
            if (messageObject.type != 13) {
                maxWidth -= dp(10);
            }

            CharSequence stringFinalName = null;
            CharSequence stringFinalText = null;
            if (messageObject.replyMessageObject != null) {
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject.photoThumbs2, 80);
                if (photoSize == null) {
                    photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject.photoThumbs, 80);
                }
                if (photoSize == null || messageObject.replyMessageObject.type == 13 || messageObject.type == 13 && !isTablet() || messageObject.replyMessageObject.isSecretMedia()) {
                    replyImageReceiver.setImageBitmap((Drawable) null);
                    needReplyImage = false;
                } else {
                    currentReplyPhoto = photoSize.location;
                    replyImageReceiver.setImage(photoSize.location, "50_50", null, null, true);
                    needReplyImage = true;
                    maxWidth -= dp(44);
                }

                String name = null;
                if (messageObject.replyMessageObject.isFromUser()) {
                    TLRPC.User user = MessagesController.getInstance().getUser(messageObject.replyMessageObject.messageOwner.from_id);
                    if (user != null) {
                        name = UserObject.getUserName(user);
                    }
                } else if (messageObject.replyMessageObject.messageOwner.from_id < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(-messageObject.replyMessageObject.messageOwner.from_id);
                    if (chat != null) {
                        name = chat.title;
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(messageObject.replyMessageObject.messageOwner.to_id.channel_id);
                    if (chat != null) {
                        name = chat.title;
                    }
                }

                if (name != null) {
                    stringFinalName = TextUtils.ellipsize(name.replace('\n', ' '), replyNamePaint, maxWidth, TextUtils.TruncateAt.END);
                }
                if (messageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                    stringFinalText = Emoji.replaceEmoji(messageObject.replyMessageObject.messageOwner.media.game.title, replyTextPaint.getFontMetricsInt(), dp(14), false);
                    stringFinalText = TextUtils.ellipsize(stringFinalText, replyTextPaint, maxWidth, TextUtils.TruncateAt.END);
                } else if (messageObject.replyMessageObject.messageText != null && messageObject.replyMessageObject.messageText.length() > 0) {
                    String mess = messageObject.replyMessageObject.messageText.toString();
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    mess = mess.replace('\n', ' ');
                    stringFinalText = Emoji.replaceEmoji(mess, replyTextPaint.getFontMetricsInt(), dp(14), false);
                    stringFinalText = TextUtils.ellipsize(stringFinalText, replyTextPaint, maxWidth, TextUtils.TruncateAt.END);
                }
            }
            if (stringFinalName == null) {
                stringFinalName = LocaleController.getString("Loading", R.string.Loading);
            }
            try {
                replyNameLayout = new StaticLayout(stringFinalName, replyNamePaint, maxWidth + dp(6), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (replyNameLayout.getLineCount() > 0) {
                    replyNameWidth = (int)Math.ceil(replyNameLayout.getLineWidth(0)) + dp(12 + (needReplyImage ? 44 : 0));
                    replyNameOffset = replyNameLayout.getLineLeft(0);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            try {
                if (stringFinalText != null) {
                    replyTextLayout = new StaticLayout(stringFinalText, replyTextPaint, maxWidth + dp(6), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    if (replyTextLayout.getLineCount() > 0) {
                        replyTextWidth = (int) Math.ceil(replyTextLayout.getLineWidth(0)) + dp(12 + (needReplyImage ? 44 : 0));
                        replyTextOffset = replyTextLayout.getLineLeft(0);
                    }
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        requestLayout();
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

        if (isAvatarVisible) {
            avatarImage.draw(canvas);
        }

        if (mediaBackground) {
            timePaint.setColor(Theme.MSG_MEDIA_TIME_TEXT_COLOR);
        } else {
            if (currentMessageObject.isOutOwner()) {
                timePaint.setColor(isDrawSelectedBackground() ? Theme.MSG_OUT_TIME_SELECTED_TEXT_COLOR : Theme.MSG_OUT_TIME_TEXT_COLOR);
            } else {
                timePaint.setColor(isDrawSelectedBackground() ? Theme.MSG_IN_TIME_SELECTED_TEXT_COLOR : Theme.MSG_IN_TIME_TEXT_COLOR);
            }
        }

        if (currentMessageObject.isOutOwner()) {
            if (isDrawSelectedBackground()) {
                if (!mediaBackground) {
                    currentBackgroundDrawable = Theme.backgroundDrawableOutSelected;
                } else {
                    currentBackgroundDrawable = Theme.backgroundMediaDrawableOutSelected;
                }
            } else {
                if (!mediaBackground) {
                    currentBackgroundDrawable = Theme.backgroundDrawableOut;
                } else {
                    currentBackgroundDrawable = Theme.backgroundMediaDrawableOut;
                }
            }
            setDrawableBounds(currentBackgroundDrawable, backgroundDrawableLeft = layoutWidth - backgroundWidth - (!mediaBackground ? 0 : dp(9)), dp(1), backgroundWidth - (mediaBackground ? 0 : dp(3)), layoutHeight - dp(2));
        } else {
            if (isDrawSelectedBackground()) {
                if (!mediaBackground) {
                    currentBackgroundDrawable = Theme.backgroundDrawableInSelected;
                } else {
                    currentBackgroundDrawable = Theme.backgroundMediaDrawableInSelected;
                }
            } else {
                if (!mediaBackground) {
                    currentBackgroundDrawable = Theme.backgroundDrawableIn;
                } else {
                    currentBackgroundDrawable = Theme.backgroundMediaDrawableIn;
                }
            }
            if (isChat && currentMessageObject.isFromUser()) {
                setDrawableBounds(currentBackgroundDrawable, backgroundDrawableLeft = dp(48 + (!mediaBackground ? 3 : 9)), dp(1), backgroundWidth - (mediaBackground ? 0 : dp(3)), layoutHeight - dp(2));
            } else {
                setDrawableBounds(currentBackgroundDrawable, backgroundDrawableLeft = (!mediaBackground ? dp(3) : dp(9)), dp(1), backgroundWidth - (mediaBackground ? 0 : dp(3)), layoutHeight - dp(2));
            }
        }
        if (drawBackground && currentBackgroundDrawable != null) {
            currentBackgroundDrawable.draw(canvas);
        }

        drawContent(canvas);

        if (drawShareButton) {
            Theme.shareDrawable.setColorFilter(sharePressed ? Theme.colorPressedFilter : Theme.colorFilter);
            if (currentMessageObject.isOutOwner()) {
                shareStartX = currentBackgroundDrawable.getBounds().left - dp(8) - Theme.shareDrawable.getIntrinsicWidth();
            } else {
                shareStartX = currentBackgroundDrawable.getBounds().right + dp(8);
            }
            setDrawableBounds(Theme.shareDrawable, shareStartX, shareStartY = layoutHeight - dp(41));
            Theme.shareDrawable.draw(canvas);
            setDrawableBounds(Theme.shareIconDrawable, shareStartX + dp(9), shareStartY + dp(9));
            Theme.shareIconDrawable.draw(canvas);
        }

        if (drawNameLayout && nameLayout != null) {
            canvas.save();

            if (currentMessageObject.type == 13) {
                namePaint.setColor(Theme.MSG_STICKER_NAME_TEXT_COLOR);
                int backWidth;
                if (currentMessageObject.isOutOwner()) {
                    nameX = dp(28);
                } else {
                    nameX = currentBackgroundDrawable.getBounds().right + dp(22);
                }
                nameY = layoutHeight - dp(38);
                Theme.systemDrawable.setColorFilter(Theme.colorFilter);
                Theme.systemDrawable.setBounds((int) nameX - dp(12), (int) nameY - dp(5), (int) nameX + dp(12) + nameWidth, (int) nameY + dp(22));
                Theme.systemDrawable.draw(canvas);
            } else {
                if (mediaBackground || currentMessageObject.isOutOwner()) {
                    nameX = currentBackgroundDrawable.getBounds().left + dp(11) - nameOffsetX;
                } else {
                    nameX = currentBackgroundDrawable.getBounds().left + dp(17) - nameOffsetX;
                }
                if (currentUser != null) {
                    namePaint.setColor(AvatarDrawable.getNameColorForId(currentUser.id));
                } else if (currentChat != null) {
                    namePaint.setColor(AvatarDrawable.getNameColorForId(currentChat.id));
                } else {
                    namePaint.setColor(AvatarDrawable.getNameColorForId(0));
                }
                nameY = dp(10);
            }
            canvas.translate(nameX, nameY);
            nameLayout.draw(canvas);
            canvas.restore();
        }

        if (drawForwardedName && forwardedNameLayout[0] != null && forwardedNameLayout[1] != null) {
            forwardNameY = dp(10 + (drawNameLayout ? 19 : 0));
            if (currentMessageObject.isOutOwner()) {
                forwardNamePaint.setColor(Theme.MSG_OUT_FORDWARDED_NAME_TEXT_COLOR);
                forwardNameX = currentBackgroundDrawable.getBounds().left + dp(11);
            } else {
                forwardNamePaint.setColor(Theme.MSG_IN_FORDWARDED_NAME_TEXT_COLOR);
                if (mediaBackground) {
                    forwardNameX = currentBackgroundDrawable.getBounds().left + dp(11);
                } else {
                    forwardNameX = currentBackgroundDrawable.getBounds().left + dp(17);
                }
            }
            for (int a = 0; a < 2; a++) {
                canvas.save();
                canvas.translate(forwardNameX - forwardNameOffsetX[a], forwardNameY + dp(16) * a);
                forwardedNameLayout[a].draw(canvas);
                canvas.restore();
            }
        }

        if (currentMessageObject.isReply()) {
            if (currentMessageObject.type == 13) {
                replyLinePaint.setColor(Theme.MSG_STICKER_REPLY_LINE_COLOR);
                replyNamePaint.setColor(Theme.MSG_STICKER_REPLY_NAME_TEXT_COLOR);
                replyTextPaint.setColor(Theme.MSG_STICKER_REPLY_MESSAGE_TEXT_COLOR);
                if (currentMessageObject.isOutOwner()) {
                    replyStartX = dp(23);
                } else {
                    replyStartX = currentBackgroundDrawable.getBounds().right + dp(17);
                }
                replyStartY = layoutHeight - dp(58);
                if (nameLayout != null) {
                    replyStartY -= dp(25 + 6);
                }
                int backWidth = Math.max(replyNameWidth, replyTextWidth) + dp(14 + (needReplyImage ? 44 : 0));
                Theme.systemDrawable.setColorFilter(Theme.colorFilter);
                Theme.systemDrawable.setBounds(replyStartX - dp(7), replyStartY - dp(6), replyStartX - dp(7) + backWidth, replyStartY + dp(41));
                Theme.systemDrawable.draw(canvas);
            } else {
                if (currentMessageObject.isOutOwner()) {
                    replyLinePaint.setColor(Theme.MSG_OUT_REPLY_LINE_COLOR);
                    replyNamePaint.setColor(Theme.MSG_OUT_REPLY_NAME_TEXT_COLOR);
                    if (currentMessageObject.replyMessageObject != null && currentMessageObject.replyMessageObject.type == 0 && !(currentMessageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame)) {
                        replyTextPaint.setColor(Theme.MSG_OUT_REPLY_MESSAGE_TEXT_COLOR);
                    } else {
                        replyTextPaint.setColor(isDrawSelectedBackground() ? Theme.MSG_OUT_REPLY_MEDIA_MESSAGE_SELETED_TEXT_COLOR : Theme.MSG_OUT_REPLY_MEDIA_MESSAGE_TEXT_COLOR);
                    }
                    replyStartX = currentBackgroundDrawable.getBounds().left + dp(12);
                } else {
                    replyLinePaint.setColor(Theme.MSG_IN_REPLY_LINE_COLOR);
                    replyNamePaint.setColor(Theme.MSG_IN_REPLY_NAME_TEXT_COLOR);
                    if (currentMessageObject.replyMessageObject != null && currentMessageObject.replyMessageObject.type == 0 && !(currentMessageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame)) {
                        replyTextPaint.setColor(Theme.MSG_IN_REPLY_MESSAGE_TEXT_COLOR);
                    } else {
                        replyTextPaint.setColor(isDrawSelectedBackground() ? Theme.MSG_IN_REPLY_MEDIA_MESSAGE_SELETED_TEXT_COLOR : Theme.MSG_IN_REPLY_MEDIA_MESSAGE_TEXT_COLOR);
                    }
                    if (mediaBackground) {
                        replyStartX = currentBackgroundDrawable.getBounds().left + dp(12);
                    } else {
                        replyStartX = currentBackgroundDrawable.getBounds().left + dp(18);
                    }
                }
                replyStartY = dp(12 + (drawForwardedName && forwardedNameLayout[0] != null ? 36 : 0) + (drawNameLayout && nameLayout != null ? 20 : 0));
            }
            canvas.drawRect(replyStartX, replyStartY, replyStartX + dp(2), replyStartY + dp(35), replyLinePaint);
            if (needReplyImage) {
                replyImageReceiver.setImageCoords(replyStartX + dp(10), replyStartY, dp(35), dp(35));
                replyImageReceiver.draw(canvas);
            }

            if (replyNameLayout != null) {
                canvas.save();
                canvas.translate(replyStartX - replyNameOffset + dp(10 + (needReplyImage ? 44 : 0)), replyStartY);
                replyNameLayout.draw(canvas);
                canvas.restore();
            }
            if (replyTextLayout != null) {
                canvas.save();
                canvas.translate(replyStartX - replyTextOffset + dp(10 + (needReplyImage ? 44 : 0)), replyStartY + dp(19));
                replyTextLayout.draw(canvas);
                canvas.restore();
            }
        }

        if (drawTime || !mediaBackground) {
            if (mediaBackground) {
                Drawable drawable;
                if (currentMessageObject.type == 13) {
                    drawable = Theme.timeStickerBackgroundDrawable;
                } else {
                    drawable = Theme.timeBackgroundDrawable;
                }
                setDrawableBounds(drawable, timeX - dp(4), layoutHeight - dp(27), timeWidth + dp(8 + (currentMessageObject.isOutOwner() ? 20 : 0)), dp(17));
                drawable.draw(canvas);

                int additionalX = 0;
                if ((currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                    additionalX = (int) (timeWidth - timeLayout.getLineWidth(0));

                    if (currentMessageObject.isSending()) {
                        if (!currentMessageObject.isOutOwner()) {
                            setDrawableBounds(Theme.clockMediaDrawable, timeX + dp(11), layoutHeight - dp(13.0f) - Theme.clockMediaDrawable.getIntrinsicHeight());
                            Theme.clockMediaDrawable.draw(canvas);
                        }
                    } else if (currentMessageObject.isSendError()) {
                        if (!currentMessageObject.isOutOwner()) {
                            setDrawableBounds(Theme.errorDrawable, timeX + dp(11), layoutHeight - dp(12.5f) - Theme.errorDrawable.getIntrinsicHeight());
                            Theme.errorDrawable.draw(canvas);
                        }
                    } else {
                        Drawable countDrawable = Theme.viewsMediaCountDrawable;
                        setDrawableBounds(countDrawable, timeX, layoutHeight - dp(9.5f) - timeLayout.getHeight());
                        countDrawable.draw(canvas);

                        if (viewsLayout != null) {
                            canvas.save();
                            canvas.translate(timeX + countDrawable.getIntrinsicWidth() + dp(3), layoutHeight - dp(11.3f) - timeLayout.getHeight());
                            viewsLayout.draw(canvas);
                            canvas.restore();
                        }
                    }
                }

                canvas.save();
                canvas.translate(timeX + additionalX, layoutHeight - dp(11.3f) - timeLayout.getHeight());
                timeLayout.draw(canvas);
                canvas.restore();
            } else {
                int additionalX = 0;
                if ((currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                    additionalX = (int) (timeWidth - timeLayout.getLineWidth(0));

                    if (currentMessageObject.isSending()) {
                        if (!currentMessageObject.isOutOwner()) {
                            Drawable clockDrawable = Theme.clockChannelDrawable[isDrawSelectedBackground() ? 1 : 0];
                            setDrawableBounds(clockDrawable, timeX + dp(11), layoutHeight - dp(8.5f) - clockDrawable.getIntrinsicHeight());
                            clockDrawable.draw(canvas);
                        }
                    } else if (currentMessageObject.isSendError()) {
                        if (!currentMessageObject.isOutOwner()) {
                            setDrawableBounds(Theme.errorDrawable, timeX + dp(11), layoutHeight - dp(6.5f) - Theme.errorDrawable.getIntrinsicHeight());
                            Theme.errorDrawable.draw(canvas);
                        }
                    } else {
                        if (!currentMessageObject.isOutOwner()) {
                            setDrawableBounds(Theme.viewsCountDrawable[isDrawSelectedBackground() ? 1 : 0], timeX, layoutHeight - dp(4.5f) - timeLayout.getHeight());
                            Theme.viewsCountDrawable[isDrawSelectedBackground() ? 1 : 0].draw(canvas);
                        } else {
                            setDrawableBounds(Theme.viewsOutCountDrawable, timeX, layoutHeight - dp(4.5f) - timeLayout.getHeight());
                            Theme.viewsOutCountDrawable.draw(canvas);
                        }

                        if (viewsLayout != null) {
                            canvas.save();
                            canvas.translate(timeX + Theme.viewsOutCountDrawable.getIntrinsicWidth() + dp(3), layoutHeight - dp(6.5f) - timeLayout.getHeight());
                            viewsLayout.draw(canvas);
                            canvas.restore();
                        }
                    }
                }

                canvas.save();
                canvas.translate(timeX + additionalX, layoutHeight - dp(6.5f) - timeLayout.getHeight());
                timeLayout.draw(canvas);
                canvas.restore();
                //canvas.drawRect(timeX, layoutHeight - AndroidUtilities.dp(6.5f) - timeLayout.getHeight(), timeX + availableTimeWidth, layoutHeight - AndroidUtilities.dp(4.5f) - timeLayout.getHeight(), timePaint);
            }

            if (currentMessageObject.isOutOwner()) {
                boolean drawCheck1 = false;
                boolean drawCheck2 = false;
                boolean drawClock = false;
                boolean drawError = false;
                boolean isBroadcast = (int)(currentMessageObject.getDialogId() >> 32) == 1;

                if (currentMessageObject.isSending()) {
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
                    if (!mediaBackground) {
                        setDrawableBounds(Theme.clockDrawable, layoutWidth - dp(18.5f) - Theme.clockDrawable.getIntrinsicWidth(), layoutHeight - dp(8.5f) - Theme.clockDrawable.getIntrinsicHeight());
                        Theme.clockDrawable.draw(canvas);
                    } else {
                        setDrawableBounds(Theme.clockMediaDrawable, layoutWidth - dp(22.0f) - Theme.clockMediaDrawable.getIntrinsicWidth(), layoutHeight - dp(12.5f) - Theme.clockMediaDrawable.getIntrinsicHeight());
                        Theme.clockMediaDrawable.draw(canvas);
                    }
                }
                if (isBroadcast) {
                    if (drawCheck1 || drawCheck2) {
                        if (!mediaBackground) {
                            setDrawableBounds(Theme.broadcastDrawable, layoutWidth - dp(20.5f) - Theme.broadcastDrawable.getIntrinsicWidth(), layoutHeight - dp(8.0f) - Theme.broadcastDrawable.getIntrinsicHeight());
                            Theme.broadcastDrawable.draw(canvas);
                        } else {
                            setDrawableBounds(Theme.broadcastMediaDrawable, layoutWidth - dp(24.0f) - Theme.broadcastMediaDrawable.getIntrinsicWidth(), layoutHeight - dp(13.0f) - Theme.broadcastMediaDrawable.getIntrinsicHeight());
                            Theme.broadcastMediaDrawable.draw(canvas);
                        }
                    }
                } else {
                    if (drawCheck2) {
                        if (!mediaBackground) {
                            if (drawCheck1) {
                                setDrawableBounds(Theme.checkDrawable, layoutWidth - dp(22.5f) - Theme.checkDrawable.getIntrinsicWidth(), layoutHeight - dp(8.0f) - Theme.checkDrawable.getIntrinsicHeight());
                            } else {
                                setDrawableBounds(Theme.checkDrawable, layoutWidth - dp(18.5f) - Theme.checkDrawable.getIntrinsicWidth(), layoutHeight - dp(8.0f) - Theme.checkDrawable.getIntrinsicHeight());
                            }
                            Theme.checkDrawable.draw(canvas);
                        } else {
                            if (drawCheck1) {
                                setDrawableBounds(Theme.checkMediaDrawable, layoutWidth - dp(26.3f) - Theme.checkMediaDrawable.getIntrinsicWidth(), layoutHeight - dp(12.5f) - Theme.checkMediaDrawable.getIntrinsicHeight());
                            } else {
                                setDrawableBounds(Theme.checkMediaDrawable, layoutWidth - dp(21.5f) - Theme.checkMediaDrawable.getIntrinsicWidth(), layoutHeight - dp(12.5f) - Theme.checkMediaDrawable.getIntrinsicHeight());
                            }
                            Theme.checkMediaDrawable.draw(canvas);
                        }
                    }
                    if (drawCheck1) {
                        if (!mediaBackground) {
                            setDrawableBounds(Theme.halfCheckDrawable, layoutWidth - dp(18) - Theme.halfCheckDrawable.getIntrinsicWidth(), layoutHeight - dp(8.0f) - Theme.halfCheckDrawable.getIntrinsicHeight());
                            Theme.halfCheckDrawable.draw(canvas);
                        } else {
                            setDrawableBounds(Theme.halfCheckMediaDrawable, layoutWidth - dp(21.5f) - Theme.halfCheckMediaDrawable.getIntrinsicWidth(), layoutHeight - dp(12.5f) - Theme.halfCheckMediaDrawable.getIntrinsicHeight());
                            Theme.halfCheckMediaDrawable.draw(canvas);
                        }
                    }
                }
                if (drawError) {
                    if (!mediaBackground) {
                        setDrawableBounds(Theme.errorDrawable, layoutWidth - dp(18) - Theme.errorDrawable.getIntrinsicWidth(), layoutHeight - dp(7) - Theme.errorDrawable.getIntrinsicHeight());
                        Theme.errorDrawable.draw(canvas);
                    } else {
                        setDrawableBounds(Theme.errorDrawable, layoutWidth - dp(20.5f) - Theme.errorDrawable.getIntrinsicWidth(), layoutHeight - dp(11.5f) - Theme.errorDrawable.getIntrinsicHeight());
                        Theme.errorDrawable.draw(canvas);
                    }
                }
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
}
