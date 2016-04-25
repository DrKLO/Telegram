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
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
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
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class ChatMessageCell extends ChatBaseCell implements SeekBar.SeekBarDelegate, ImageReceiver.ImageReceiverDelegate {

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
        private StaticLayout caption;
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

    private Rect scrollRect = new Rect();

    private int lastVisibleBlockNum;
    private int firstVisibleBlockNum;
    private int totalVisibleBlocksCount;
    private boolean needNewVisiblePart;

    private RadialProgress radialProgress;
    private ImageReceiver photoImage;
    private AvatarDrawable avatarDrawable;

    private boolean disallowLongPress;

    private boolean isSmallImage;
    private boolean drawImageButton;
    private int documentAttachType;
    private TLRPC.Document documentAttach;
    private boolean drawPhotoImage;
    private boolean hasLinkPreview;
    private int linkPreviewHeight;
    private int mediaOffsetY;
    private int descriptionY;
    private int durationWidth;
    private int descriptionX;
    private int titleX;
    private int authorX;
    private StaticLayout siteCaptionLayout;
    private StaticLayout titleLayout;
    private StaticLayout descriptionLayout;
    private StaticLayout durationLayout;
    private StaticLayout authorLayout;

    private StaticLayout captionLayout;
    private int captionX;
    private int captionY;
    private int captionHeight;
    private int nameOffsetX;

    private StaticLayout infoLayout;
    private int infoWidth;

    private String currentUrl;

    private boolean allowedToSetPhoto = true;

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
    private static TextPaint durationPaint;

    private ClickableSpan pressedLink;
    private int pressedLinkType;
    private boolean linkPreviewPressed;
    private ArrayList<LinkPath> urlPathCache = new ArrayList<>();
    private ArrayList<LinkPath> urlPath = new ArrayList<>();
    //private LinkPath urlPath = new LinkPath();

    private boolean useSeekBarWaweform;
    private SeekBar seekBar;
    private SeekBarWaveform seekBarWaveform;
    private int seekBarX;
    private int seekBarY;

    private StaticLayout timeLayout;
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

    public ChatMessageCell(Context context) {
        super(context);
        avatarDrawable = new AvatarDrawable();
        photoImage = new ImageReceiver(this);
        photoImage.setDelegate(this);
        radialProgress = new RadialProgress(this);
        seekBar = new SeekBar(context);
        seekBar.setDelegate(this);
        seekBarWaveform = new SeekBarWaveform(context);
        seekBarWaveform.setDelegate(this);
        seekBarWaveform.setParentView(this);

        if (infoPaint == null) {
            infoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            infoPaint.setTextSize(AndroidUtilities.dp(12));

            docNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            docNamePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            docNamePaint.setTextSize(AndroidUtilities.dp(15));

            docBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            deleteProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            deleteProgressPaint.setColor(Theme.MSG_SECRET_TIME_TEXT_COLOR);

            botProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            botProgressPaint.setColor(Theme.MSG_BOT_PROGRESS_COLOR);
            botProgressPaint.setStrokeCap(Paint.Cap.ROUND);
            botProgressPaint.setStyle(Paint.Style.STROKE);
            botProgressPaint.setStrokeWidth(AndroidUtilities.dp(2));

            locationTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            locationTitlePaint.setTextSize(AndroidUtilities.dp(15));
            locationTitlePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            locationAddressPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            locationAddressPaint.setTextSize(AndroidUtilities.dp(13));

            urlPaint = new Paint();
            urlPaint.setColor(Theme.MSG_LINK_SELECT_BACKGROUND_COLOR);

            audioTimePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            audioTimePaint.setTextSize(AndroidUtilities.dp(12));

            audioTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            audioTitlePaint.setTextSize(AndroidUtilities.dp(16));
            audioTitlePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            audioPerformerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            audioPerformerPaint.setTextSize(AndroidUtilities.dp(15));

            botButtonPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            botButtonPaint.setTextSize(AndroidUtilities.dp(15));
            botButtonPaint.setColor(Theme.MSG_BOT_BUTTON_TEXT_COLOR);
            botButtonPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            contactNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            contactNamePaint.setTextSize(AndroidUtilities.dp(15));
            contactNamePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            contactPhonePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            contactPhonePaint.setTextSize(AndroidUtilities.dp(13));

            durationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            durationPaint.setTextSize(AndroidUtilities.dp(12));
            durationPaint.setColor(Theme.MSG_WEB_PREVIEW_DURATION_TEXT_COLOR);
        }

        radialProgress = new RadialProgress(this);
    }

    private void resetPressedLink(int type) {
        if (pressedLink == null || pressedLinkType != type && type != -1) {
            return;
        }
        resetUrlPaths();
        pressedLink = null;
        pressedLinkType = -1;
        invalidate();
    }

    private void resetUrlPaths() {
        if (urlPath.isEmpty()) {
            return;
        }
        urlPathCache.addAll(urlPath);
        urlPath.clear();
    }

    private LinkPath obtainNewUrlPath() {
        LinkPath linkPath;
        if (!urlPathCache.isEmpty()) {
            linkPath = urlPathCache.get(0);
            urlPathCache.remove(0);
        } else {
            linkPath = new LinkPath();
        }
        urlPath.add(linkPath);
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
                                resetUrlPaths();
                                try {
                                    LinkPath path = obtainNewUrlPath();
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
                                            path = obtainNewUrlPath();
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
                                            path = obtainNewUrlPath();
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
                                resetUrlPaths();
                                try {
                                    LinkPath path = obtainNewUrlPath();
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

    private boolean checkLinkPreviewMotionEvent(MotionEvent event) {
        if (currentMessageObject.type != 0 || !hasLinkPreview) {
            return false;
        }
        int x = (int) event.getX();
        int y = (int) event.getY();

        if (x >= textX && x <= textX + backgroundWidth && y >= textY + currentMessageObject.textHeight && y <= textY + currentMessageObject.textHeight + linkPreviewHeight + AndroidUtilities.dp(8)) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT && drawPhotoImage && photoImage.isInsideImage(x, y)) {
                    if (drawImageButton && buttonState != -1 && x >= buttonX && x <= buttonX + AndroidUtilities.dp(48) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48)) {
                        buttonPressed = 1;
                        return true;
                    } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && buttonState == -1 && MediaController.getInstance().canAutoplayGifs()) {
                        linkPreviewPressed = false;
                        return false;
                    } else {
                        linkPreviewPressed = true;
                        return true;
                    }
                } else if (descriptionLayout != null && y >= descriptionY) {
                    try {
                        x -= textX + AndroidUtilities.dp(10) + descriptionX;
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
                                resetUrlPaths();
                                try {
                                    LinkPath path = obtainNewUrlPath();
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
                        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                            buttonPressed = 0;
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
                        if (drawImageButton) {
                            if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                                if (buttonState == -1) {
                                    buttonState = 2;
                                    currentMessageObject.audioProgress = 1;
                                    photoImage.setAllowStartAnimation(false);
                                    photoImage.stopAnimation();
                                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                                    invalidate();
                                    playSoundEffect(SoundEffectConstants.CLICK);
                                } else if (buttonState == 2 || buttonState == 0) {
                                    didPressedButton(false);
                                    playSoundEffect(SoundEffectConstants.CLICK);
                                }
                            } else if (buttonState == -1) {
                                delegate.didPressedImage(this);
                                playSoundEffect(SoundEffectConstants.CLICK);
                            }
                        } else {
                            TLRPC.WebPage webPage = currentMessageObject.messageOwner.media.webpage;
                            if (webPage != null) {
                                if (Build.VERSION.SDK_INT >= 16 && webPage.embed_url != null && webPage.embed_url.length() != 0) {
                                    delegate.needOpenWebView(webPage.embed_url, webPage.site_name, webPage.description, webPage.url, webPage.embed_width, webPage.embed_height);
                                } else {
                                    Browser.openUrl(getContext(), webPage.url);
                                }
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
        if (documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT && currentMessageObject.type != 12 && documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC && documentAttachType != DOCUMENT_ATTACH_TYPE_VIDEO) {
            return false;
        }

        int x = (int) event.getX();
        int y = (int) event.getY();

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (x >= otherX - AndroidUtilities.dp(20) && x <= otherX + AndroidUtilities.dp(20) && y >= otherY - AndroidUtilities.dp(4) && y <= otherY + AndroidUtilities.dp(30)) {
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
            if (buttonState != -1 && x >= buttonX && x <= buttonX + AndroidUtilities.dp(48) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48)) {
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
                        TLRPC.User user = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.media.user_id);
                        if (user == null) {
                            imagePressed = false;
                            result = false;
                        }
                    }
                }
            }
            if (imagePressed && currentMessageObject.isSecretPhoto()) {
                imagePressed = false;
            } else if (imagePressed && currentMessageObject.isSendError()) {
                imagePressed = false;
                result = false;
            } else if (imagePressed && currentMessageObject.type == 8 && buttonState == -1 && MediaController.getInstance().canAutoplayGifs()) {
                imagePressed = false;
                result = false;
            }
        } else {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (buttonPressed == 1) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton(false);
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
            boolean area;
            if (buttonState == 0 || buttonState == 1 || buttonState == 2) {
                area = x >= buttonX - AndroidUtilities.dp(12) && x <= buttonX - AndroidUtilities.dp(12) + backgroundWidth && y >= namesOffset + mediaOffsetY && y <= layoutHeight;
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
                addX = getMeasuredWidth() - widthForButtons - AndroidUtilities.dp(10);
            } else {
                addX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(mediaBackground ? 1 : 7);
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
            result = checkLinkPreviewMotionEvent(event);
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

        return result || super.onTouchEvent(event);
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
                timeLayout = new StaticLayout(timeString, audioTimePaint, timeWidthAudio, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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
                timeLayout = new StaticLayout(timeString, audioTimePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
        }
        invalidate();
    }

    public void downloadAudioIfNeed() {
        if (documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO || documentAttach.size >= 1024 * 1024 * 5) {
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
            if (layout.getLineLeft(a) != 0 || Build.VERSION.SDK_INT >= 14 && (layout.isRtlCharAt(layout.getLineStart(a)) || layout.isRtlCharAt(layout.getLineEnd(a)))) {
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
            TLRPC.User user = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.media.user_id);
            delegate.didPressedUserAvatar(this, user);
        } else if (currentMessageObject.type == 8) {
            if (buttonState == -1) {
                buttonState = 2;
                currentMessageObject.audioProgress = 1;
                photoImage.setAllowStartAnimation(false);
                photoImage.stopAnimation();
                radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                invalidate();
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
                if (Build.VERSION.SDK_INT >= 16 && webPage.embed_url != null && webPage.embed_url.length() != 0) {
                    delegate.needOpenWebView(webPage.embed_url, webPage.site_name, webPage.description, webPage.url, webPage.embed_width, webPage.embed_height);
                } else {
                    Browser.openUrl(getContext(), webPage.url);
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
            String url = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=100x100&maptype=roadmap&scale=%d&markers=color:red|size:mid|%f,%f&sensor=false", lat, lon, Math.min(2, (int) Math.ceil(AndroidUtilities.density)), lat, lon);
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

    @Override
    protected boolean isUserDataChanged() {
        return currentMessageObject != null && (!hasLinkPreview && currentMessageObject.messageOwner.media != null && currentMessageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage || super.isUserDataChanged());
    }

    @Override
    public ImageReceiver getPhotoImage() {
        return photoImage;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        photoImage.onDetachedFromWindow();
        MediaController.getInstance().removeLoadingFileObserver(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
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
        super.onLongPress();
    }

    @Override
    public void setCheckPressed(boolean value, boolean pressed) {
        super.setCheckPressed(value, pressed);
        if (radialProgress.swapBackground(getDrawableForCurrentState())) {
            invalidate();
        }
        if (useSeekBarWaweform) {
            seekBarWaveform.setSelected(isDrawSelectedBackground());
        } else {
            seekBar.setSelected(isDrawSelectedBackground());
        }
    }

    @Override
    public void setHighlighted(boolean value) {
        super.setHighlighted(value);
        if (radialProgress.swapBackground(getDrawableForCurrentState())) {
            invalidate();
        }
        if (useSeekBarWaweform) {
            seekBarWaveform.setSelected(isDrawSelectedBackground());
        } else {
            seekBar.setSelected(isDrawSelectedBackground());
        }
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (radialProgress.swapBackground(getDrawableForCurrentState())) {
            invalidate();
        }
        if (useSeekBarWaweform) {
            seekBarWaveform.setSelected(isDrawSelectedBackground());
        } else {
            seekBar.setSelected(isDrawSelectedBackground());
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
            availableTimeWidth = maxWidth - AndroidUtilities.dp(76 + 18) - (int) Math.ceil(audioTimePaint.measureText("00:00"));
            measureTime(messageObject);
            int minSize = AndroidUtilities.dp(40 + 14 + 20 + 90 + 10) + timeWidth;
            if (!hasLinkPreview) {
                backgroundWidth = Math.min(maxWidth, minSize + duration * AndroidUtilities.dp(10));
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

            maxWidth = maxWidth - AndroidUtilities.dp(86);

            CharSequence stringFinal = TextUtils.ellipsize(messageObject.getMusicTitle().replace('\n', ' '), audioTitlePaint, maxWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
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
            availableTimeWidth = backgroundWidth - AndroidUtilities.dp(76 + 18) - durationWidth;
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
            String str = String.format("%d:%02d, %s", minutes, seconds, AndroidUtilities.formatFileSize(documentAttach.size));
            infoWidth = (int) Math.ceil(infoPaint.measureText(str));
            infoLayout = new StaticLayout(str, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

            return 0;
        } else {
            drawPhotoImage = documentAttach.mime_type != null && documentAttach.mime_type.toLowerCase().startsWith("image/") || documentAttach.thumb instanceof TLRPC.TL_photoSize && !(documentAttach.thumb.location instanceof TLRPC.TL_fileLocationUnavailable);
            if (!drawPhotoImage) {
                maxWidth += AndroidUtilities.dp(30);
            }
            documentAttachType = DOCUMENT_ATTACH_TYPE_DOCUMENT;
            String name = FileLoader.getDocumentFileName(documentAttach);
            if (name == null || name.length() == 0) {
                name = LocaleController.getString("AttachDocument", R.string.AttachDocument);
            }
            captionLayout = StaticLayoutEx.createStaticLayout(name, docNamePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.MIDDLE, maxWidth, drawPhotoImage ? 2 : 1);
            nameOffsetX = Integer.MIN_VALUE;
            int captionWidth;
            if (captionLayout != null && captionLayout.getLineCount() > 0) {
                int maxLineWidth = 0;
                for (int a = 0; a < captionLayout.getLineCount(); a++) {
                    maxLineWidth = Math.max(maxLineWidth, (int) Math.ceil(captionLayout.getLineWidth(a)));
                    nameOffsetX = Math.max(nameOffsetX, (int) Math.ceil(-captionLayout.getLineLeft(a)));
                }
                captionWidth = Math.min(maxWidth, maxLineWidth);
            } else {
                captionWidth = maxWidth;
                nameOffsetX = 0;
            }

            String str = AndroidUtilities.formatFileSize(documentAttach.size) + " " + FileLoader.getDocumentExtension(documentAttach);
            infoWidth = Math.min(maxWidth, (int) Math.ceil(infoPaint.measureText(str)));
            CharSequence str2 = TextUtils.ellipsize(str, infoPaint, infoWidth, TextUtils.TruncateAt.END);
            try {
                if (infoWidth < 0) {
                    infoWidth = AndroidUtilities.dp(10);
                }
                infoLayout = new StaticLayout(str2, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            if (drawPhotoImage) {
                currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
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
            return captionWidth;
        }
    }

    private void calcBackgroundWidth(int maxWidth, int timeMore, int maxChildWidth) {
        if (hasLinkPreview || maxWidth - currentMessageObject.lastLineWidth < timeMore) {
            totalHeight += AndroidUtilities.dp(14);
            backgroundWidth = Math.max(maxChildWidth, currentMessageObject.lastLineWidth) + AndroidUtilities.dp(31);
            backgroundWidth = Math.max(backgroundWidth, timeWidth + AndroidUtilities.dp(31));
        } else {
            int diff = maxChildWidth - currentMessageObject.lastLineWidth;
            if (diff >= 0 && diff <= timeMore) {
                backgroundWidth = maxChildWidth + timeMore - diff + AndroidUtilities.dp(31);
            } else {
                backgroundWidth = Math.max(maxChildWidth, currentMessageObject.lastLineWidth + timeMore) + AndroidUtilities.dp(31);
            }
        }
    }

    @Override
    public void setMessageObject(MessageObject messageObject) {
        boolean messageIdChanged = currentMessageObject == null || currentMessageObject.getId() != messageObject.getId();
        boolean messageChanged = currentMessageObject != messageObject || messageObject.forceUpdate;
        boolean dataChanged = currentMessageObject == messageObject && (isUserDataChanged() || photoNotSet);
        if (messageChanged || dataChanged || isPhotoDataChanged(messageObject)) {
            resetPressedLink(-1);
            messageObject.forceUpdate = false;
            drawPhotoImage = false;
            hasLinkPreview = false;
            linkPreviewPressed = false;
            buttonPressed = 0;
            pressedBotButton = -1;
            linkPreviewHeight = 0;
            mediaOffsetY = 0;
            durationLayout = null;
            documentAttachType = DOCUMENT_ATTACH_TYPE_NONE;
            documentAttach = null;
            descriptionLayout = null;
            titleLayout = null;
            siteCaptionLayout = null;
            authorLayout = null;
            captionLayout = null;
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
                    if (isChat && !messageObject.isOutOwner() && messageObject.isFromUser()) {
                        maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122);
                        drawName = true;
                    } else {
                        drawName = messageObject.messageOwner.to_id.channel_id != 0 && !messageObject.isOutOwner();
                        maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
                    }
                } else {
                    if (isChat && !messageObject.isOutOwner() && messageObject.isFromUser()) {
                        maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(122);
                        drawName = true;
                    } else {
                        maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(80);
                        drawName = messageObject.messageOwner.to_id.channel_id != 0 && !messageObject.isOutOwner();
                    }
                }
                measureTime(messageObject);
                int timeMore = timeWidth + AndroidUtilities.dp(6);
                if (messageObject.isOutOwner()) {
                    timeMore += AndroidUtilities.dp(20.5f);
                }

                hasLinkPreview = messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage;
                backgroundWidth = maxWidth;
                if (hasLinkPreview || maxWidth - messageObject.lastLineWidth < timeMore) {
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

                super.setMessageObject(messageObject);

                backgroundWidth = messageObject.textWidth;
                totalHeight = messageObject.textHeight + AndroidUtilities.dp(19.5f) + namesOffset;

                int maxChildWidth = Math.max(backgroundWidth, nameWidth);
                maxChildWidth = Math.max(maxChildWidth, forwardedNameWidth);
                maxChildWidth = Math.max(maxChildWidth, replyNameWidth);
                maxChildWidth = Math.max(maxChildWidth, replyTextWidth);
                int maxWebWidth = 0;

                if (hasLinkPreview) {
                    int linkPreviewMaxWidth;
                    if (AndroidUtilities.isTablet()) {
                        if (messageObject.isFromUser() && (currentMessageObject.messageOwner.to_id.channel_id != 0 || currentMessageObject.messageOwner.to_id.chat_id != 0) && !currentMessageObject.isOut()) {
                            linkPreviewMaxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122);
                        } else {
                            linkPreviewMaxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
                        }
                    } else {
                        if (messageObject.isFromUser() && (currentMessageObject.messageOwner.to_id.channel_id != 0 || currentMessageObject.messageOwner.to_id.chat_id != 0) && !currentMessageObject.isOutOwner()) {
                            linkPreviewMaxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(122);
                        } else {
                            linkPreviewMaxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(80);
                        }
                    }
                    if (drawShareButton) {
                        linkPreviewMaxWidth -= AndroidUtilities.dp(20);
                    }

                    TLRPC.TL_webPage webPage = (TLRPC.TL_webPage) messageObject.messageOwner.media.webpage;

                    if (webPage.site_name != null && webPage.photo != null && webPage.site_name.toLowerCase().equals("instagram")) {
                        linkPreviewMaxWidth = Math.max(AndroidUtilities.displaySize.y / 3, currentMessageObject.textWidth);
                    }

                    int additinalWidth = AndroidUtilities.dp(10);
                    int restLinesCount = 3;
                    int additionalHeight = 0;
                    linkPreviewMaxWidth -= additinalWidth;

                    if (currentMessageObject.photoThumbs == null && webPage.photo != null) {
                        currentMessageObject.generateThumbs(true);
                    }

                    isSmallImage = webPage.description != null && webPage.type != null && (webPage.type.equals("app") || webPage.type.equals("profile") || webPage.type.equals("article")) && currentMessageObject.photoThumbs != null;

                    if (webPage.site_name != null) {
                        try {
                            int width = (int) Math.ceil(replyNamePaint.measureText(webPage.site_name));
                            siteCaptionLayout = new StaticLayout(webPage.site_name, replyNamePaint, Math.min(width, linkPreviewMaxWidth), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            int height = siteCaptionLayout.getLineBottom(siteCaptionLayout.getLineCount() - 1);
                            linkPreviewHeight += height;
                            totalHeight += height;
                            additionalHeight += height;
                            width = siteCaptionLayout.getWidth();
                            maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
                            maxWebWidth = Math.max(maxWebWidth, width + additinalWidth);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }

                    boolean titleIsRTL = false;
                    if (webPage.title != null) {
                        try {
                            titleX = 0;
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += AndroidUtilities.dp(2);
                                totalHeight += AndroidUtilities.dp(2);
                            }
                            int restLines = 0;
                            if (!isSmallImage || webPage.description == null) {
                                titleLayout = StaticLayoutEx.createStaticLayout(webPage.title, replyNamePaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false, TextUtils.TruncateAt.END, linkPreviewMaxWidth, 4);
                            } else {
                                restLines = restLinesCount;
                                titleLayout = generateStaticLayout(webPage.title, replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - AndroidUtilities.dp(48 + 4), restLinesCount, 4);
                                restLinesCount -= titleLayout.getLineCount();
                            }
                            int height = titleLayout.getLineBottom(titleLayout.getLineCount() - 1);
                            linkPreviewHeight += height;
                            totalHeight += height;
                            for (int a = 0; a < titleLayout.getLineCount(); a++) {
                                int lineLeft = (int) titleLayout.getLineLeft(a);
                                if (lineLeft != 0) {
                                    titleIsRTL = true;
                                    if (titleX == 0) {
                                        titleX = -lineLeft;
                                    } else {
                                        titleX = Math.max(titleX, -lineLeft);
                                    }
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
                            FileLog.e("tmessages", e);
                        }
                    }

                    boolean authorIsRTL = false;
                    if (webPage.author != null) {
                        try {
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += AndroidUtilities.dp(2);
                                totalHeight += AndroidUtilities.dp(2);
                            }
                            //int width = Math.min((int) Math.ceil(replyNamePaint.measureText(webPage.author)), linkPreviewMaxWidth);
                            if (restLinesCount == 3 && (!isSmallImage || webPage.description == null)) {
                                authorLayout = new StaticLayout(webPage.author, replyNamePaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            } else {
                                authorLayout = generateStaticLayout(webPage.author, replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - AndroidUtilities.dp(48 + 4), restLinesCount, 1);
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

                    if (webPage.description != null) {
                        try {
                            descriptionX = 0;
                            currentMessageObject.generateLinkDescription();
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += AndroidUtilities.dp(2);
                                totalHeight += AndroidUtilities.dp(2);
                            }
                            int restLines = 0;
                            if (restLinesCount == 3 && !isSmallImage) {
                                descriptionLayout = StaticLayoutEx.createStaticLayout(messageObject.linkDescription, replyTextPaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false, TextUtils.TruncateAt.END, linkPreviewMaxWidth, 6);
                            } else {
                                restLines = restLinesCount;
                                descriptionLayout = generateStaticLayout(messageObject.linkDescription, replyTextPaint, linkPreviewMaxWidth, linkPreviewMaxWidth - AndroidUtilities.dp(48 + 4), restLinesCount, 6);
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
                                if (restLines == 0 || !isSmallImage) {
                                    if (titleIsRTL) {
                                        titleX = -AndroidUtilities.dp(4);
                                    }
                                    if (authorIsRTL) {
                                        authorX = -AndroidUtilities.dp(4);
                                    }
                                }
                                maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }

                    boolean smallImage = webPage.type != null && (webPage.type.equals("app") || webPage.type.equals("profile") || webPage.type.equals("article"));
                    if (smallImage && (descriptionLayout == null || descriptionLayout != null && descriptionLayout.getLineCount() == 1)) {
                        smallImage = false;
                        isSmallImage = false;
                    }
                    int maxPhotoWidth = smallImage ? AndroidUtilities.dp(48) : linkPreviewMaxWidth;

                    if (webPage.document != null) {
                        TLRPC.Document document = webPage.document;
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
                                    currentPhotoObject.w = currentPhotoObject.h = AndroidUtilities.dp(150);
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
                    } else if (webPage.photo != null) {
                        drawImageButton = webPage.type != null && webPage.type.equals("photo");
                        currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, drawImageButton ? AndroidUtilities.getPhotoSize() : maxPhotoWidth, !drawImageButton);
                        currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                        if (currentPhotoObjectThumb == currentPhotoObject) {
                            currentPhotoObjectThumb = null;
                        }
                    }

                    if (documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC && documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO && documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        if (currentPhotoObject != null) {
                            drawImageButton = webPage.type != null && (webPage.type.equals("photo") || webPage.type.equals("document") && documentAttachType != DOCUMENT_ATTACH_TYPE_STICKER || webPage.type.equals("gif") || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO);
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
                                width = currentPhotoObject.w;
                                height = currentPhotoObject.h;
                                float scale = width / (float) (maxPhotoWidth - AndroidUtilities.dp(2));
                                width /= scale;
                                height /= scale;
                                if (webPage.site_name == null || webPage.site_name != null && !webPage.site_name.toLowerCase().equals("instagram") && documentAttachType == 0) {
                                    if (height > AndroidUtilities.displaySize.y / 3) {
                                        height = AndroidUtilities.displaySize.y / 3;
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

                            if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER) {
                                photoImage.setImage(documentAttach, null, currentPhotoFilter, null, currentPhotoObject != null ? currentPhotoObject.location : null, "b1", documentAttach.size, "webp", true);
                            } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                                photoImage.setImage(null, null, currentPhotoObject.location, currentPhotoFilter, 0, null, false);
                            } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                                boolean photoExist = messageObject.mediaExists;
                                String fileName = FileLoader.getAttachFileName(webPage.document);
                                if (photoExist || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_GIF) || FileLoader.getInstance().isLoadingFile(fileName)) {
                                    photoNotSet = false;
                                    photoImage.setImage(webPage.document, null, currentPhotoObject.location, currentPhotoFilter, webPage.document.size, null, false);
                                } else {
                                    photoNotSet = true;
                                    photoImage.setImage(null, null, currentPhotoObject.location, currentPhotoFilter, 0, null, false);
                                }
                            } else {
                                boolean photoExist = messageObject.mediaExists;
                                String fileName = FileLoader.getAttachFileName(currentPhotoObject);
                                if (photoExist || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO) || FileLoader.getInstance().isLoadingFile(fileName)) {
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

                            if (webPage.type != null && webPage.type.equals("video") && webPage.duration != 0) {
                                int minutes = webPage.duration / 60;
                                int seconds = webPage.duration - minutes * 60;
                                String str = String.format("%d:%02d", minutes, seconds);
                                durationWidth = (int) Math.ceil(durationPaint.measureText(str));
                                durationLayout = new StaticLayout(str, durationPaint, durationWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            }
                        } else {
                            photoImage.setImageBitmap((Drawable) null);
                            linkPreviewHeight -= AndroidUtilities.dp(6);
                            totalHeight += AndroidUtilities.dp(4);
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
                photoImage.setRoundRadius(AndroidUtilities.dp(22));
                if (AndroidUtilities.isTablet()) {
                    backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                } else {
                    backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                }
                availableTimeWidth = backgroundWidth - AndroidUtilities.dp(31);

                int uid = messageObject.messageOwner.media.user_id;
                TLRPC.User user = MessagesController.getInstance().getUser(uid);

                int maxWidth = getMaxNameWidth() - AndroidUtilities.dp(110);
                if (maxWidth < 0) {
                    maxWidth = AndroidUtilities.dp(10);
                }

                TLRPC.FileLocation currentPhoto = null;
                if (user != null) {
                    if (user.photo != null) {
                        currentPhoto = user.photo.photo_small;
                    }
                    avatarDrawable.setInfo(user);
                }
                photoImage.setImage(currentPhoto, "50_50", user != null ? avatarDrawable : Theme.contactDrawable[messageObject.isOutOwner() ? 1 : 0], null, false);

                String phone = messageObject.messageOwner.media.phone_number;
                if (phone != null && phone.length() != 0) {
                    if (!phone.startsWith("+")) {
                        phone = "+" + phone;
                    }
                    phone = PhoneFormat.getInstance().format(phone);
                } else {
                    phone = LocaleController.getString("NumberUnknown", R.string.NumberUnknown);
                }

                CharSequence currentNameString = ContactsController.formatName(messageObject.messageOwner.media.first_name, messageObject.messageOwner.media.last_name).replace('\n', ' ');
                if (currentNameString.length() == 0) {
                    currentNameString = phone;
                }
                titleLayout = new StaticLayout(TextUtils.ellipsize(currentNameString, contactNamePaint, maxWidth, TextUtils.TruncateAt.END), contactNamePaint, maxWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                captionLayout = new StaticLayout(TextUtils.ellipsize(phone.replace('\n', ' '), contactPhonePaint, maxWidth, TextUtils.TruncateAt.END), contactPhonePaint, maxWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                super.setMessageObject(messageObject);

                if (drawForwardedName && messageObject.isForwarded()) {
                    namesOffset += AndroidUtilities.dp(5);
                } else if (drawNameLayout && messageObject.messageOwner.reply_to_msg_id == 0) {
                    namesOffset += AndroidUtilities.dp(7);
                }

                totalHeight = AndroidUtilities.dp(70) + namesOffset;
            } else if (messageObject.type == 2) {
                drawForwardedName = true;
                if (AndroidUtilities.isTablet()) {
                    backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                } else {
                    backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                }
                createDocumentLayout(backgroundWidth, messageObject);

                super.setMessageObject(messageObject);

                totalHeight = AndroidUtilities.dp(70) + namesOffset;
            } else if (messageObject.type == 14) {
                if (AndroidUtilities.isTablet()) {
                    backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                } else {
                    backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                }

                createDocumentLayout(backgroundWidth, messageObject);

                super.setMessageObject(messageObject);

                totalHeight = AndroidUtilities.dp(82) + namesOffset;
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
                    if (AndroidUtilities.isTablet()) {
                        backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                    } else {
                        backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                    }
                    if (checkNeedDrawShareButton(messageObject)) {
                        backgroundWidth -= AndroidUtilities.dp(20);
                    }
                    int maxWidth = backgroundWidth - AndroidUtilities.dp(86 + 52);

                    createDocumentLayout(maxWidth, messageObject);
                    if (drawPhotoImage) {
                        photoWidth = AndroidUtilities.dp(86);
                        photoHeight = AndroidUtilities.dp(86);
                    } else {
                        photoWidth = AndroidUtilities.dp(56);
                        photoHeight = AndroidUtilities.dp(56);
                    }
                    availableTimeWidth = maxWidth;
                } else if (messageObject.type == 4) { //geo
                    double lat = messageObject.messageOwner.media.geo.lat;
                    double lon = messageObject.messageOwner.media.geo._long;

                    if (messageObject.messageOwner.media.title != null && messageObject.messageOwner.media.title.length() > 0) {
                        if (AndroidUtilities.isTablet()) {
                            backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                        } else {
                            backgroundWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 102 : 50), AndroidUtilities.dp(270));
                        }
                        if (checkNeedDrawShareButton(messageObject)) {
                            backgroundWidth -= AndroidUtilities.dp(20);
                        }
                        int maxWidth = backgroundWidth - AndroidUtilities.dp(86 + 37);

                        captionLayout = StaticLayoutEx.createStaticLayout(messageObject.messageOwner.media.title, locationTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth, 2);
                        int lineCount = captionLayout.getLineCount();
                        if (messageObject.messageOwner.media.address != null && messageObject.messageOwner.media.address.length() > 0) {
                            infoLayout = StaticLayoutEx.createStaticLayout(messageObject.messageOwner.media.address, locationAddressPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth, Math.min(3, 3 - lineCount));
                        } else {
                            infoLayout = null;
                        }

                        mediaBackground = false;
                        availableTimeWidth = maxWidth;
                        photoWidth = AndroidUtilities.dp(86);
                        photoHeight = AndroidUtilities.dp(86);
                        currentUrl = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=72x72&maptype=roadmap&scale=%d&markers=color:red|size:mid|%f,%f&sensor=false", lat, lon, Math.min(2, (int) Math.ceil(AndroidUtilities.density)), lat, lon);
                    } else {
                        availableTimeWidth = AndroidUtilities.dp(200 - 14);
                        photoWidth = AndroidUtilities.dp(200);
                        photoHeight = AndroidUtilities.dp(100);
                        backgroundWidth = photoWidth + AndroidUtilities.dp(12);
                        currentUrl = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=200x100&maptype=roadmap&scale=%d&markers=color:red|size:mid|%f,%f&sensor=false", lat, lon, Math.min(2, (int) Math.ceil(AndroidUtilities.density)), lat, lon);
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
                    float maxHeight = AndroidUtilities.displaySize.y * 0.4f;
                    float maxWidth;
                    if (AndroidUtilities.isTablet()) {
                        maxWidth = AndroidUtilities.getMinTabletSide() * 0.5f;
                    } else {
                        maxWidth = AndroidUtilities.displaySize.x * 0.5f;
                    }
                    if (photoWidth == 0) {
                        photoHeight = (int) maxHeight;
                        photoWidth = photoHeight + AndroidUtilities.dp(100);
                    }
                    if (photoHeight > maxHeight) {
                        photoWidth *= maxHeight / photoHeight;
                        photoHeight = (int) maxHeight;
                    } else {
                        photoHeight *= maxWidth / photoWidth;
                        photoWidth = (int) maxWidth;
                    }
                    documentAttachType = DOCUMENT_ATTACH_TYPE_STICKER;
                    availableTimeWidth = photoWidth - AndroidUtilities.dp(14);
                    backgroundWidth = photoWidth + AndroidUtilities.dp(12);
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
                    if (AndroidUtilities.isTablet()) {
                        maxPhotoWidth = photoWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.7f);
                    } else {
                        maxPhotoWidth = photoWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f);
                    }
                    photoHeight = photoWidth + AndroidUtilities.dp(100);
                    if (checkNeedDrawShareButton(messageObject)) {
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
                        photoImage.setNeedsQualityThumb(true);
                        photoImage.setShouldGenerateQualityThumb(true);
                        photoImage.setParentMessageObject(messageObject);
                    } else if (messageObject.type == 8) { //gif
                        String str = AndroidUtilities.formatFileSize(messageObject.messageOwner.media.document.size);
                        infoWidth = (int) Math.ceil(infoPaint.measureText(str));
                        infoLayout = new StaticLayout(str, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                        photoImage.setNeedsQualityThumb(true);
                        photoImage.setShouldGenerateQualityThumb(true);
                        photoImage.setParentMessageObject(messageObject);
                    }

                    if (messageObject.caption != null) {
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

                    availableTimeWidth = maxPhotoWidth - AndroidUtilities.dp(14);
                    measureTime(messageObject);
                    int timeWidthTotal = timeWidth + AndroidUtilities.dp(14 + (messageObject.isOutOwner() ? 20 : 0));
                    if (w < timeWidthTotal) {
                        w = timeWidthTotal;
                    }

                    if (messageObject.isSecretPhoto()) {
                        if (AndroidUtilities.isTablet()) {
                            w = h = (int) (AndroidUtilities.getMinTabletSide() * 0.5f);
                        } else {
                            w = h = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f);
                        }
                    }

                    photoWidth = w;
                    photoHeight = h;
                    backgroundWidth = w + AndroidUtilities.dp(12);
                    if (!mediaBackground) {
                        backgroundWidth += AndroidUtilities.dp(9);
                    }
                    if (messageObject.caption != null) {
                        try {
                            captionLayout = new StaticLayout(messageObject.caption, MessageObject.getTextPaint(), photoWidth - AndroidUtilities.dp(10), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            if (captionLayout != null && captionLayout.getLineCount() > 0) {
                                captionHeight = captionLayout.getHeight();
                                additionHeight += captionHeight + AndroidUtilities.dp(9);
                                float lastLineWidth = captionLayout.getLineWidth(captionLayout.getLineCount() - 1) + captionLayout.getLineLeft(captionLayout.getLineCount() - 1);
                                if (photoWidth - AndroidUtilities.dp(8) - lastLineWidth < timeWidthTotal) {
                                    additionHeight += AndroidUtilities.dp(14);
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }

                    currentPhotoFilter = String.format(Locale.US, "%d_%d", (int) (w / AndroidUtilities.density), (int) (h / AndroidUtilities.density));
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
                                if (allowedToSetPhoto || ImageLoader.getInstance().getImageFromMemory(currentPhotoObject.location, null, currentPhotoFilter) != null) {
                                    allowedToSetPhoto = true;
                                    photoImage.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilter, noSize ? 0 : currentPhotoObject.size, null, false);
                                } else if (currentPhotoObjectThumb != null) {
                                    photoImage.setImage(null, null, currentPhotoObjectThumb.location, currentPhotoFilter, 0, null, false);
                                } else {
                                    photoImage.setImageBitmap((Drawable) null);
                                }
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
                super.setMessageObject(messageObject);

                if (drawForwardedName) {
                    namesOffset += AndroidUtilities.dp(5);
                } else if (drawNameLayout && messageObject.messageOwner.reply_to_msg_id == 0) {
                    namesOffset += AndroidUtilities.dp(7);
                }

                invalidate();

                photoImage.setImageCoords(0, AndroidUtilities.dp(7) + namesOffset, photoWidth, photoHeight);
                totalHeight = photoHeight + AndroidUtilities.dp(14) + namesOffset + additionHeight;
            }

            botButtons.clear();
            if (messageIdChanged) {
                botButtonsByData.clear();
            }
            if (messageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
                int rows = messageObject.messageOwner.reply_markup.rows.size();
                substractBackgroundHeight = keyboardHeight = AndroidUtilities.dp(44 + 4) * rows + AndroidUtilities.dp(1);

                widthForButtons = backgroundWidth;
                boolean fullWidth = false;
                if (messageObject.wantedBotKeyboardWidth > widthForButtons) {
                    int maxButtonWidth = -AndroidUtilities.dp(isChat && messageObject.isFromUser() && !messageObject.isOutOwner() ? 62 : 10);
                    if (AndroidUtilities.isTablet()) {
                        maxButtonWidth += AndroidUtilities.getMinTabletSide();
                    } else {
                        maxButtonWidth += Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
                    }
                    widthForButtons = Math.max(backgroundWidth, Math.min(messageObject.wantedBotKeyboardWidth, maxButtonWidth));
                    fullWidth = true;
                }

                int maxButtonsWidth = 0;
                for (int a = 0; a < rows; a++) {
                    TLRPC.TL_keyboardButtonRow row = messageObject.messageOwner.reply_markup.rows.get(a);
                    int buttonsCount = row.buttons.size();
                    int buttonWidth = (widthForButtons - (AndroidUtilities.dp(5) * (buttonsCount - 1)) - AndroidUtilities.dp(!fullWidth && mediaBackground ? 0 : 9) - AndroidUtilities.dp(2)) / buttonsCount;
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
                        botButton.x = b * (buttonWidth + AndroidUtilities.dp(5));
                        botButton.y = a * AndroidUtilities.dp(44 + 4) + AndroidUtilities.dp(5);
                        botButton.width = buttonWidth;
                        botButton.height = AndroidUtilities.dp(44);
                        CharSequence caption = Emoji.replaceEmoji(botButton.button.text, botButtonPaint.getFontMetricsInt(), AndroidUtilities.dp(15), false);
                        caption = TextUtils.ellipsize(caption, botButtonPaint, buttonWidth - AndroidUtilities.dp(10), TextUtils.TruncateAt.END);
                        botButton.caption = new StaticLayout(caption, botButtonPaint, buttonWidth - AndroidUtilities.dp(10), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
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
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight + keyboardHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (currentMessageObject.type == 0) {
            textY = AndroidUtilities.dp(10) + namesOffset;
        }
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
            if (currentMessageObject.isOutOwner()) {
                seekBarX = layoutWidth - backgroundWidth + AndroidUtilities.dp(57);
                buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(14);
                timeAudioX = layoutWidth - backgroundWidth + AndroidUtilities.dp(67);
            } else {
                if (isChat && currentMessageObject.isFromUser()) {
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

            updateAudioProgress();
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (currentMessageObject.isOutOwner()) {
                seekBarX = layoutWidth - backgroundWidth + AndroidUtilities.dp(56);
                buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(14);
                timeAudioX = layoutWidth - backgroundWidth + AndroidUtilities.dp(67);
            } else {
                if (isChat && currentMessageObject.isFromUser()) {
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

            updateAudioProgress();
        } else if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT && !drawPhotoImage) {
            if (currentMessageObject.isOutOwner()) {
                buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(14);
            } else {
                if (isChat && currentMessageObject.isFromUser()) {
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
                if (isChat && currentMessageObject.isFromUser()) {
                    x = AndroidUtilities.dp(72);
                } else {
                    x = AndroidUtilities.dp(23);
                }
            }
            photoImage.setImageCoords(x, AndroidUtilities.dp(13) + namesOffset, AndroidUtilities.dp(44), AndroidUtilities.dp(44));
        } else {
            int x;
            if (currentMessageObject.isOutOwner()) {
                if (mediaBackground) {
                    x = layoutWidth - backgroundWidth - AndroidUtilities.dp(3);
                } else {
                    x = layoutWidth - backgroundWidth + AndroidUtilities.dp(6);
                }
            } else {
                if (isChat && currentMessageObject.isFromUser()) {
                    x = AndroidUtilities.dp(63);
                } else {
                    x = AndroidUtilities.dp(15);
                }
            }
            photoImage.setImageCoords(x, photoImage.getImageY(), photoImage.getImageWidth(), photoImage.getImageHeight());
            buttonX = (int) (x + (photoImage.getImageWidth() - AndroidUtilities.dp(48)) / 2.0f);
            buttonY = (int) (AndroidUtilities.dp(7) + (photoImage.getImageHeight() - AndroidUtilities.dp(48)) / 2.0f) + namesOffset;
            radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(48), buttonY + AndroidUtilities.dp(48));
            deleteProgressRect.set(buttonX + AndroidUtilities.dp(3), buttonY + AndroidUtilities.dp(3), buttonX + AndroidUtilities.dp(45), buttonY + AndroidUtilities.dp(45));
        }
    }

    @Override
    protected void drawContent(Canvas canvas) {

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
        if (currentMessageObject.type == 0 && currentMessageObject.textLayoutBlocks != null && !currentMessageObject.textLayoutBlocks.isEmpty()) {
            if (currentMessageObject.isOutOwner()) {
                textX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(11);
            } else {
                textX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(17);
            }

            textY = AndroidUtilities.dp(10) + namesOffset;

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
                    try {
                        block.textLayout.draw(canvas);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    canvas.restore();
                }
            }

            if (hasLinkPreview) {
                int startY = textY + currentMessageObject.textHeight + AndroidUtilities.dp(8);
                int linkX = textX + AndroidUtilities.dp(1);
                int linkPreviewY = startY;
                int smallImageStartY = 0;
                replyLinePaint.setColor(currentMessageObject.isOutOwner() ? Theme.MSG_OUT_WEB_PREVIEW_LINE_COLOR : Theme.MSG_IN_WEB_PREVIEW_LINE_COLOR);

                canvas.drawRect(linkX, linkPreviewY - AndroidUtilities.dp(3), linkX + AndroidUtilities.dp(2), linkPreviewY + linkPreviewHeight + AndroidUtilities.dp(3), replyLinePaint);

                if (siteCaptionLayout != null) {
                    replyNamePaint.setColor(currentMessageObject.isOutOwner() ? Theme.MSG_OUT_SITE_NAME_TEXT_COLOR : Theme.MSG_IN_SITE_NAME_TEXT_COLOR);
                    canvas.save();
                    canvas.translate(linkX + AndroidUtilities.dp(10), linkPreviewY - AndroidUtilities.dp(3));
                    siteCaptionLayout.draw(canvas);
                    canvas.restore();
                    linkPreviewY += siteCaptionLayout.getLineBottom(siteCaptionLayout.getLineCount() - 1);
                }

                replyNamePaint.setColor(Theme.MSG_TEXT_COLOR);
                replyTextPaint.setColor(Theme.MSG_TEXT_COLOR);
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
                    canvas.translate(linkX + AndroidUtilities.dp(10) + descriptionX, descriptionY);
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
                        linkPreviewY += AndroidUtilities.dp(2);
                    }

                    if (isSmallImage) {
                        photoImage.setImageCoords(linkX + backgroundWidth - AndroidUtilities.dp(81), smallImageStartY, photoImage.getImageWidth(), photoImage.getImageHeight());
                    } else {
                        photoImage.setImageCoords(linkX + AndroidUtilities.dp(10), linkPreviewY, photoImage.getImageWidth(), photoImage.getImageHeight());
                        if (drawImageButton) {
                            int size = AndroidUtilities.dp(48);
                            buttonX = (int) (photoImage.getImageX() + (photoImage.getImageWidth() - size) / 2.0f);
                            buttonY = (int) (photoImage.getImageY() + (photoImage.getImageHeight() - size) / 2.0f);
                            radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(48), buttonY + AndroidUtilities.dp(48));
                        }
                    }
                    imageDrawn = photoImage.draw(canvas);

                    if (durationLayout != null) {
                        int x = photoImage.getImageX() + photoImage.getImageWidth() - AndroidUtilities.dp(8) - durationWidth;
                        int y = photoImage.getImageY() + photoImage.getImageHeight() - AndroidUtilities.dp(19);
                        Theme.timeBackgroundDrawable.setBounds(x - AndroidUtilities.dp(4), y - AndroidUtilities.dp(1.5f), x + durationWidth + AndroidUtilities.dp(4), y + AndroidUtilities.dp(14.5f));
                        Theme.timeBackgroundDrawable.draw(canvas);

                        canvas.save();
                        canvas.translate(x, y);
                        durationLayout.draw(canvas);
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
                    int offset = AndroidUtilities.dp(2);
                    invalidate((int) deleteProgressRect.left - offset, (int) deleteProgressRect.top - offset, (int) deleteProgressRect.right + offset * 2, (int) deleteProgressRect.bottom + offset * 2);
                }
                updateSecretTimeText(currentMessageObject);
            }
        }

        if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
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
            canvas.translate(timeAudioX + songX, AndroidUtilities.dp(13) + namesOffset + mediaOffsetY);
            songLayout.draw(canvas);
            canvas.restore();

            canvas.save();
            if (MediaController.getInstance().isPlayingAudio(currentMessageObject)) {
                canvas.translate(seekBarX, seekBarY);
                seekBar.draw(canvas);
            } else {
                canvas.translate(timeAudioX + performerX, AndroidUtilities.dp(35) + namesOffset + mediaOffsetY);
                performerLayout.draw(canvas);
            }
            canvas.restore();

            canvas.save();
            canvas.translate(timeAudioX, AndroidUtilities.dp(57) + namesOffset + mediaOffsetY);
            timeLayout.draw(canvas);
            canvas.restore();

            Drawable menuDrawable;
            if (currentMessageObject.isOutOwner()) {
                menuDrawable = Theme.docMenuDrawable[1];
            } else {
                menuDrawable = Theme.docMenuDrawable[isDrawSelectedBackground() ? 2 : 0];
            }
            setDrawableBounds(menuDrawable, otherX = buttonX + backgroundWidth - AndroidUtilities.dp(currentMessageObject.type == 0 ? 58 : 48), otherY = buttonY - AndroidUtilities.dp(5));
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
                canvas.translate(seekBarX + AndroidUtilities.dp(13), seekBarY);
                seekBarWaveform.draw(canvas);
            } else {
                canvas.translate(seekBarX, seekBarY);
                seekBar.draw(canvas);
            }
            canvas.restore();

            canvas.save();
            canvas.translate(timeAudioX, AndroidUtilities.dp(44) + namesOffset + mediaOffsetY);
            timeLayout.draw(canvas);
            canvas.restore();

            if (currentMessageObject.type != 0 && currentMessageObject.messageOwner.to_id.channel_id == 0 && currentMessageObject.isContentUnread()) {
                docBackPaint.setColor(currentMessageObject.isOutOwner() ? Theme.MSG_OUT_VOICE_SEEKBAR_FILL_COLOR : Theme.MSG_IN_VOICE_SEEKBAR_FILL_COLOR);
                canvas.drawCircle(timeAudioX + timeWidthAudio + AndroidUtilities.dp(6), AndroidUtilities.dp(51) + namesOffset + mediaOffsetY, AndroidUtilities.dp(3), docBackPaint);
            }
        }
        if (currentMessageObject.type == 1 || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
                setDrawableBounds(Theme.docMenuDrawable[3], otherX = photoImage.getImageX() + photoImage.getImageWidth() - AndroidUtilities.dp(14), otherY = photoImage.getImageY() + AndroidUtilities.dp(8.1f));
                Theme.docMenuDrawable[3].draw(canvas);
            }

            if (captionLayout != null) {
                canvas.save();
                canvas.translate(captionX = photoImage.getImageX() + AndroidUtilities.dp(5), captionY = photoImage.getImageY() + photoImage.getImageHeight() + AndroidUtilities.dp(6));
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
            if (infoLayout != null && (buttonState == 1 || buttonState == 0 || buttonState == 3 || currentMessageObject.isSecretPhoto())) {
                infoPaint.setColor(Theme.MSG_MEDIA_INFO_TEXT_COLOR);
                setDrawableBounds(Theme.timeBackgroundDrawable, photoImage.getImageX() + AndroidUtilities.dp(4), photoImage.getImageY() + AndroidUtilities.dp(4), infoWidth + AndroidUtilities.dp(8), AndroidUtilities.dp(16.5f));
                Theme.timeBackgroundDrawable.draw(canvas);

                canvas.save();
                canvas.translate(photoImage.getImageX() + AndroidUtilities.dp(8), photoImage.getImageY() + AndroidUtilities.dp(5.5f));
                infoLayout.draw(canvas);
                canvas.restore();
            }
        } else if (currentMessageObject.type == 4) {
            if (captionLayout != null) {
                if (currentMessageObject.isOutOwner()) {
                    locationTitlePaint.setColor(Theme.MSG_OUT_VENUE_NAME_TEXT_COLOR);
                    locationAddressPaint.setColor(isDrawSelectedBackground() ? Theme.MSG_OUT_VENUE_INFO_SELECTED_TEXT_COLOR : Theme.MSG_OUT_VENUE_INFO_TEXT_COLOR);
                } else {
                    locationTitlePaint.setColor(Theme.MSG_IN_VENUE_NAME_TEXT_COLOR);
                    locationAddressPaint.setColor(isDrawSelectedBackground() ? Theme.MSG_IN_VENUE_INFO_SELECTED_TEXT_COLOR : Theme.MSG_IN_VENUE_INFO_TEXT_COLOR);
                }

                canvas.save();
                canvas.translate(nameOffsetX + photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10), photoImage.getImageY() + AndroidUtilities.dp(8));
                captionLayout.draw(canvas);
                canvas.restore();

                if (infoLayout != null) {
                    canvas.save();
                    canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10), photoImage.getImageY() + captionLayout.getLineBottom(captionLayout.getLineCount() - 1) + AndroidUtilities.dp(13));
                    infoLayout.draw(canvas);
                    canvas.restore();
                }
            }
        } else if (currentMessageObject.type == 8) {
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(captionX = photoImage.getImageX() + AndroidUtilities.dp(5), captionY = photoImage.getImageY() + photoImage.getImageHeight() + AndroidUtilities.dp(6));
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
        } else if (currentMessageObject.type == 12) {
            contactNamePaint.setColor(currentMessageObject.isOutOwner() ? Theme.MSG_OUT_CONTACT_NAME_TEXT_COLOR : Theme.MSG_IN_CONTACT_NAME_TEXT_COLOR);
            contactPhonePaint.setColor(currentMessageObject.isOutOwner() ? Theme.MSG_OUT_CONTACT_PHONE_TEXT_COLOR : Theme.MSG_IN_CONTACT_PHONE_TEXT_COLOR);
            if (titleLayout != null) {
                canvas.save();
                canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(9), AndroidUtilities.dp(16) + namesOffset);
                titleLayout.draw(canvas);
                canvas.restore();
            }
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(9), AndroidUtilities.dp(39) + namesOffset);
                captionLayout.draw(canvas);
                canvas.restore();
            }

            Drawable menuDrawable;
            if (currentMessageObject.isOutOwner()) {
                menuDrawable = Theme.docMenuDrawable[1];
            } else {
                menuDrawable = Theme.docMenuDrawable[isDrawSelectedBackground() ? 2 : 0];
            }
            setDrawableBounds(menuDrawable, otherX = photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(48), otherY = photoImage.getImageY() - AndroidUtilities.dp(5));
            menuDrawable.draw(canvas);
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
                    setDrawableBounds(menuDrawable, otherX = photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(56), otherY = photoImage.getImageY() + AndroidUtilities.dp(1));
                } else {
                    setDrawableBounds(menuDrawable, otherX = photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(40), otherY = photoImage.getImageY() + AndroidUtilities.dp(1));
                }

                x = photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10);
                titleY = photoImage.getImageY() + AndroidUtilities.dp(8);
                subtitleY = photoImage.getImageY() + captionLayout.getLineBottom(captionLayout.getLineCount() - 1) + AndroidUtilities.dp(13);
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
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), docBackPaint);
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
                setDrawableBounds(menuDrawable, otherX = buttonX + backgroundWidth - AndroidUtilities.dp(currentMessageObject.type == 0 ? 58 : 48), otherY = buttonY - AndroidUtilities.dp(5));
                x = buttonX + AndroidUtilities.dp(53);
                titleY = buttonY + AndroidUtilities.dp(4);
                subtitleY = buttonY + AndroidUtilities.dp(27);
                if (currentMessageObject.isOutOwner()) {
                    radialProgress.setProgressColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.MSG_OUT_AUDIO_SELECTED_PROGRESS_COLOR : Theme.MSG_OUT_AUDIO_PROGRESS_COLOR);
                } else {
                    radialProgress.setProgressColor(isDrawSelectedBackground() || buttonPressed != 0 ? Theme.MSG_IN_AUDIO_SELECTED_PROGRESS_COLOR : Theme.MSG_IN_AUDIO_PROGRESS_COLOR);
                }
            }
            menuDrawable.draw(canvas);

            try {
                if (captionLayout != null) {
                    canvas.save();
                    canvas.translate(x + nameOffsetX, titleY);
                    captionLayout.draw(canvas);
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
        if (drawImageButton) {
            radialProgress.draw(canvas);
        }

        if (!botButtons.isEmpty()) {
            int addX;
            if (currentMessageObject.isOutOwner()) {
                addX = getMeasuredWidth() - widthForButtons - AndroidUtilities.dp(10);
            } else {
                addX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(mediaBackground ? 1 : 7);
            }
            for (int a = 0; a < botButtons.size(); a++) {
                BotButton button = botButtons.get(a);
                int y = button.y + layoutHeight - AndroidUtilities.dp(2);
                Theme.systemDrawable.setColorFilter(a == pressedBotButton ? Theme.colorPressedFilter : Theme.colorFilter);
                Theme.systemDrawable.setBounds(button.x + addX, y, button.x + addX + button.width, y + button.height);
                Theme.systemDrawable.draw(canvas);
                canvas.save();
                canvas.translate(button.x + addX + AndroidUtilities.dp(5), y + (AndroidUtilities.dp(44) - button.caption.getLineBottom(button.caption.getLineCount() - 1)) / 2);
                button.caption.draw(canvas);
                canvas.restore();
                if (button.button instanceof TLRPC.TL_keyboardButtonUrl) {
                    int x = button.x + button.width - AndroidUtilities.dp(3) - Theme.botLink.getIntrinsicWidth() + addX;
                    setDrawableBounds(Theme.botLink, x, y + AndroidUtilities.dp(3));
                    Theme.botLink.draw(canvas);
                } else if (button.button instanceof TLRPC.TL_keyboardButtonSwitchInline) {
                    int x = button.x + button.width - AndroidUtilities.dp(3) - Theme.botInline.getIntrinsicWidth() + addX;
                    setDrawableBounds(Theme.botInline, x, y + AndroidUtilities.dp(3));
                    Theme.botInline.draw(canvas);
                } else if (button.button instanceof TLRPC.TL_keyboardButtonCallback || button.button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation) {
                    boolean drawProgress = button.button instanceof TLRPC.TL_keyboardButtonCallback && SendMessagesHelper.getInstance().isSendingCallback(currentMessageObject, button.button) ||
                            button.button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation && SendMessagesHelper.getInstance().isSendingCurrentLocation(currentMessageObject, button.button);
                    if (drawProgress || !drawProgress && button.progressAlpha != 0) {
                        botProgressPaint.setAlpha(Math.min(255, (int) (button.progressAlpha * 255)));
                        int x = button.x + button.width - AndroidUtilities.dp(9 + 3) + addX;
                        rect.set(x, y + AndroidUtilities.dp(4), x + AndroidUtilities.dp(8), y + AndroidUtilities.dp(8 + 4));
                        canvas.drawArc(rect, button.angle, 220, false, botProgressPaint);
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

    @Override
    protected int getMaxNameWidth() {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER) {
            int maxWidth;
            if (AndroidUtilities.isTablet()) {
                if (isChat && !currentMessageObject.isOutOwner() && currentMessageObject.isFromUser()) {
                    maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(42);
                } else {
                    maxWidth = AndroidUtilities.getMinTabletSide();
                }
            } else {
                if (isChat && !currentMessageObject.isOutOwner() && currentMessageObject.isFromUser()) {
                    maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(42);
                } else {
                    maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
                }
            }
            return maxWidth - backgroundWidth - AndroidUtilities.dp(57);
        }
        return super.getMaxNameWidth();
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
        if (fileName == null || fileName.length() == 0) {
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

    public void setAllowedToSetPhoto(boolean value) {
        if (allowedToSetPhoto == value) {
            return;
        }
        if (currentMessageObject != null && currentMessageObject.type == 1) {
            allowedToSetPhoto = value;
            if (value) {
                MessageObject temp = currentMessageObject;
                currentMessageObject = null;
                setMessageObject(temp);
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
}
