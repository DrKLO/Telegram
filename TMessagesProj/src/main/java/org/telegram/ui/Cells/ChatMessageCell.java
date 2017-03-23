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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.ResourceLoader;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;

public class ChatMessageCell extends ChatBaseCell {

    private int textX;
    private int textY;
    private int totalHeight;
    private int linkBlockNum;

    private int lastVisibleBlockNum;
    private int firstVisibleBlockNum;
    private int totalVisibleBlocksCount;

    private RadialProgress radialProgress;
    private ImageReceiver photoImage;

    private boolean isSmallImage;
    private boolean drawImageButton;
    private int isDocument;
    private boolean drawPhotoImage;
    private boolean hasLinkPreview;
    private int linkPreviewHeight;
    private boolean isInstagram;
    private int descriptionY;
    private int durationWidth;
    private int descriptionX;
    private int titleX;
    private int authorX;
    private StaticLayout sitecaptionLayout;
    private StaticLayout titleLayout;
    private StaticLayout descriptionLayout;
    private StaticLayout durationLayout;
    private StaticLayout authorLayout;
    private static TextPaint durationPaint;

    private StaticLayout captionLayout;
    private int captionX;
    private int captionY;
    private int captionHeight;
    private int nameOffsetX;

    private StaticLayout infoLayout;
    private int infoWidth;
    private int infoOffset;

    private String currentUrl;

    private boolean allowedToSetPhoto = true;

    private int buttonX;
    private int buttonY;
    private int buttonState;
    private int buttonPressed;
    private int otherX;
    private boolean imagePressed;
    private boolean otherPressed;
    private boolean photoNotSet;
    private RectF deleteProgressRect = new RectF();
    private TLRPC.PhotoSize currentPhotoObject;
    private TLRPC.PhotoSize currentPhotoObjectThumb;
    private String currentPhotoFilter;
    private String currentPhotoFilterThumb;
    private boolean cancelLoading;

    private static Drawable igvideoDrawable;
    private static TextPaint infoPaint;
    private static TextPaint namePaint;
    private static Paint docBackPaint;
    private static Paint deleteProgressPaint;
    private static TextPaint locationTitlePaint;
    private static TextPaint locationAddressPaint;
    private static Paint urlPaint;

    private ClickableSpan pressedLink;
    private int pressedLinkType;
    private boolean linkPreviewPressed;
    private LinkPath urlPath = new LinkPath();

    public ChatMessageCell(Context context) {
        super(context);
        photoImage = new ImageReceiver(this);
        radialProgress = new RadialProgress(this);

        if (infoPaint == null) {
            infoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            infoPaint.setTextSize(AndroidUtilities.dp(12));

            namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            namePaint.setColor(0xff212121);
            namePaint.setTextSize(AndroidUtilities.dp(16));

            docBackPaint = new Paint();

            deleteProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            deleteProgressPaint.setColor(0xffe4e2e0);

            locationTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            locationTitlePaint.setTextSize(AndroidUtilities.dp(14));
            locationTitlePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            locationAddressPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            locationAddressPaint.setTextSize(AndroidUtilities.dp(14));

            igvideoDrawable = getResources().getDrawable(R.drawable.igvideo);

            urlPaint = new Paint();
            urlPaint.setColor(0x33316f9f);
        }
    }

    private void resetPressedLink(int type) {
        if (pressedLink == null || pressedLinkType != type && type != -1) {
            return;
        }
        pressedLink = null;
        pressedLinkType = -1;
        invalidate();
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
                                try {
                                    int start = buffer.getSpanStart(pressedLink) - block.charactersOffset;
                                    urlPath.setCurrentLayout(block.textLayout, start);
                                    block.textLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink) - block.charactersOffset, urlPath);
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

    private boolean chechCaptionMotionEvent(MotionEvent event) {
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
                                try {
                                    int start = buffer.getSpanStart(pressedLink);
                                    urlPath.setCurrentLayout(captionLayout, start);
                                    captionLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink), urlPath);
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
                if (isDocument != 1 && drawPhotoImage && photoImage.isInsideImage(x, y)) {
                    if (drawImageButton && buttonState != -1 && x >= buttonX && x <= buttonX + AndroidUtilities.dp(48) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48)) {
                        buttonPressed = 1;
                        return true;
                    } else if (isDocument == 2 && buttonState == -1 && MediaController.getInstance().canAutoplayGifs()) {
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
                                try {
                                    int start = buffer.getSpanStart(pressedLink);
                                    urlPath.setCurrentLayout(descriptionLayout, start);
                                    descriptionLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink), urlPath);
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
            } else if (event.getAction() == MotionEvent.ACTION_UP && (pressedLinkType == 2 || buttonPressed != 0 || linkPreviewPressed)) {
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
                        AndroidUtilities.openUrl(getContext(), ((URLSpan) pressedLink).getURL());
                    } else {
                        pressedLink.onClick(this);
                    }
                } else {
                    if (drawImageButton) {
                        if (isDocument == 2) {
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
                        if (Build.VERSION.SDK_INT >= 16 && webPage.embed_url != null && webPage.embed_url.length() != 0) {
                            delegate.needOpenWebView(webPage.embed_url, webPage.site_name, webPage.url, webPage.embed_width, webPage.embed_height);
                        } else {
                            AndroidUtilities.openUrl(getContext(), webPage.url);
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
    
    private boolean checkPhotoImageMotionEvent(MotionEvent event) {
        if (!drawPhotoImage) {
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
                if (isDocument == 1) {
                    if (x >= photoImage.getImageX() && x <= photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(50) && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight()) {
                        imagePressed = true;
                        result = true;
                    } else if (x >= otherX - AndroidUtilities.dp(20) && x <= photoImage.getImageX() + backgroundWidth && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight()) {
                        otherPressed = true;
                        result = true;
                    }
                } else if (currentMessageObject.type != 13) {
                    if (x >= photoImage.getImageX() && x <= photoImage.getImageX() + backgroundWidth && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight()) {
                        imagePressed = true;
                        result = true;
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
                    }
                    invalidate();
                } else if (otherPressed) {
                    otherPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    delegate.didPressedOther(this);
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

        boolean result = checkTextBlockMotionEvent(event);
        if (!result) {
            result = checkLinkPreviewMotionEvent(event);
        }
        if (!result) {
            result = chechCaptionMotionEvent(event);
        }
        if (!result) {
            result = checkPhotoImageMotionEvent(event);
        }

        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            buttonPressed = 0;
            linkPreviewPressed = false;
            otherPressed = false;
            imagePressed = false;
            result = false;
            resetPressedLink(-1);
        }
        if (result && event.getAction() == MotionEvent.ACTION_DOWN) {
            startCheckLongPress();
        }
        if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
            cancelCheckLongPress();
        }
        return result || super.onTouchEvent(event);
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
        if (currentMessageObject.type == 1) {
            if (buttonState == -1) {
                delegate.didPressedImage(this);
            } else if (buttonState == 0) {
                didPressedButton(false);
            }
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
        } else if (currentMessageObject.type == 3) {
            if (buttonState == 0 || buttonState == 3) {
                didPressedButton(false);
            }
        } else if (currentMessageObject.type == 4) {
            delegate.didPressedImage(this);
        } else if (isDocument == 1) {
            if (buttonState == -1) {
                delegate.didPressedImage(this);
            }
        } else if (isDocument == 2) {
            if (buttonState == -1) {
                TLRPC.WebPage webPage = currentMessageObject.messageOwner.media.webpage;
                if (Build.VERSION.SDK_INT >= 16 && webPage.embed_url != null && webPage.embed_url.length() != 0) {
                    delegate.needOpenWebView(webPage.embed_url, webPage.site_name, webPage.url, webPage.embed_width, webPage.embed_height);
                } else {
                    AndroidUtilities.openUrl(getContext(), webPage.url);
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
        infoOffset = 0;
        infoWidth = (int) Math.ceil(infoPaint.measureText(str));
        CharSequence str2 = TextUtils.ellipsize(str, infoPaint, infoWidth, TextUtils.TruncateAt.END);
        infoLayout = new StaticLayout(str2, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        invalidate();
    }

    private boolean isPhotoDataChanged(MessageObject object) {
        if (object.type == 0) {
            return false;
        }
        if (object.type == 4) {
            if (currentUrl == null) {
                return true;
            }
            double lat = object.messageOwner.media.geo.lat;
            double lon = object.messageOwner.media.geo._long;
            String url = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=100x100&maptype=roadmap&scale=%d&markers=color:red|size:big|%f,%f&sensor=false", lat, lon, Math.min(2, (int) Math.ceil(AndroidUtilities.density)), lat, lon);
            if (!url.equals(currentUrl)) {
                return true;
            }
        } else if (currentPhotoObject == null || currentPhotoObject.location instanceof TLRPC.TL_fileLocationUnavailable) {
            return true;
        } else if (currentMessageObject != null && photoNotSet) {
            File cacheFile = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
            if (cacheFile.exists()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isUserDataChanged() {
        if (!hasLinkPreview && currentMessageObject.messageOwner.media != null && currentMessageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage) {
            return true;
        }
        //suppress warning
        return super.isUserDataChanged();
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
        if (photoImage.onAttachedToWindow()) {
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
        super.onLongPress();
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

    private int createDocumentLayout(int maxWidth, MessageObject messageObject) {
        TLRPC.Document document = null;
        if (messageObject.type == 9) {
            document = messageObject.messageOwner.media.document;
        } else if (messageObject.type == 0) {
            document = messageObject.messageOwner.media.webpage.document;
        }
        if (document == null) {
            return 0;
        }
        isDocument = 1;
        String name = FileLoader.getDocumentFileName(document);
        if (name == null || name.length() == 0) {
            name = LocaleController.getString("AttachDocument", R.string.AttachDocument);
        }
        captionLayout = StaticLayoutEx.createStaticLayout(name, namePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.MIDDLE, maxWidth, 3);
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

        String str = AndroidUtilities.formatFileSize(document.size) + " " + FileLoader.getDocumentExtension(document);
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
        return captionWidth;
    }

    private void calcBackgroundWidth(int maxWidth, int timeMore, int maxChildWidth) {
        if (hasLinkPreview || maxWidth - currentMessageObject.lastLineWidth < timeMore) {
            totalHeight += AndroidUtilities.dp(14);
            backgroundWidth = Math.max(maxChildWidth, currentMessageObject.lastLineWidth) + AndroidUtilities.dp(29);
            backgroundWidth = Math.max(backgroundWidth, timeWidth + AndroidUtilities.dp(29));
        } else {
            int diff = maxChildWidth - currentMessageObject.lastLineWidth;
            if (diff >= 0 && diff <= timeMore) {
                backgroundWidth = maxChildWidth + timeMore - diff + AndroidUtilities.dp(29);
            } else {
                backgroundWidth = Math.max(maxChildWidth, currentMessageObject.lastLineWidth + timeMore) + AndroidUtilities.dp(29);
            }
        }
    }

    @Override
    public void setMessageObject(MessageObject messageObject) {
        boolean messageChanged = currentMessageObject != messageObject;
        boolean dataChanged = currentMessageObject == messageObject && (isUserDataChanged() || photoNotSet);
        if (messageChanged || dataChanged || isPhotoDataChanged(messageObject)) {
            resetPressedLink(-1);
            drawPhotoImage = false;
            hasLinkPreview = false;
            linkPreviewPressed = false;
            buttonPressed = 0;
            linkPreviewHeight = 0;
            infoOffset = 0;
            isInstagram = false;
            durationLayout = null;
            isDocument = 0;
            descriptionLayout = null;
            titleLayout = null;
            sitecaptionLayout = null;
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

            if (messageChanged) {
                firstVisibleBlockNum = 0;
                lastVisibleBlockNum = 0;
            }

            if (messageObject.type == 0) {
                drawForwardedName = true;
                mediaBackground = false;

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

                backgroundWidth = maxWidth;
                availableTimeWidth = backgroundWidth - AndroidUtilities.dp(29);

                super.setMessageObject(messageObject);

                backgroundWidth = messageObject.textWidth;
                totalHeight = messageObject.textHeight + AndroidUtilities.dp(19.5f) + namesOffset;

                int maxChildWidth = Math.max(backgroundWidth, nameWidth);
                maxChildWidth = Math.max(maxChildWidth, forwardedNameWidth);
                maxChildWidth = Math.max(maxChildWidth, replyNameWidth);
                maxChildWidth = Math.max(maxChildWidth, replyTextWidth);
                int maxWebWidth = 0;

                int timeMore = timeWidth + AndroidUtilities.dp(6);
                if (messageObject.isOutOwner()) {
                    timeMore += AndroidUtilities.dp(20.5f);
                }

                if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage) {
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

                    hasLinkPreview = true;

                    if (currentMessageObject.photoThumbs == null && webPage.photo != null) {
                        currentMessageObject.generateThumbs(true);
                    }

                    isSmallImage = webPage.description != null && webPage.type != null && (webPage.type.equals("app") || webPage.type.equals("profile") || webPage.type.equals("article")) && currentMessageObject.photoThumbs != null;

                    if (webPage.site_name != null) {
                        try {
                            int width = (int) Math.ceil(replyNamePaint.measureText(webPage.site_name));
                            sitecaptionLayout = new StaticLayout(webPage.site_name, replyNamePaint, Math.min(width, linkPreviewMaxWidth), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            int height = sitecaptionLayout.getLineBottom(sitecaptionLayout.getLineCount() - 1);
                            linkPreviewHeight += height;
                            totalHeight += height;
                            additionalHeight += height;
                            width = sitecaptionLayout.getWidth();
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
                                titleLayout = generateStaticLayout(webPage.title, replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - AndroidUtilities.dp(48 + 2), restLinesCount, 4);
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
                                    width += AndroidUtilities.dp(48 + 2);
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
                                authorLayout = generateStaticLayout(webPage.author, replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - AndroidUtilities.dp(48 + 2), restLinesCount, 1);
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
                                descriptionLayout = generateStaticLayout(messageObject.linkDescription, replyTextPaint, linkPreviewMaxWidth, linkPreviewMaxWidth - AndroidUtilities.dp(48 + 2), restLinesCount, 6);
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
                                    width += AndroidUtilities.dp(48 + 2);
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
                        if (MessageObject.isGifDocument(webPage.document)){
                            if (!MediaController.getInstance().canAutoplayGifs()) {
                                messageObject.audioProgress = 1;
                            }
                            photoImage.setAllowStartAnimation(messageObject.audioProgress != 1);
                            currentPhotoObject = webPage.document.thumb;
                            if (currentPhotoObject != null && (currentPhotoObject.w == 0 || currentPhotoObject.h == 0)) {
                                for (int a = 0; a < webPage.document.attributes.size(); a++) {
                                    TLRPC.DocumentAttribute attribute = webPage.document.attributes.get(a);
                                    if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                        currentPhotoObject.w = attribute.w;
                                        currentPhotoObject.h = attribute.h;
                                        break;
                                    }
                                }
                                if (currentPhotoObject.w == 0 || currentPhotoObject.h == 0) {
                                    currentPhotoObject.w = currentPhotoObject.h = AndroidUtilities.dp(100);
                                }
                            }
                            isDocument = 2;
                        } else {
                            TLRPC.Document document = messageObject.messageOwner.media.webpage.document;
                            if (!MessageObject.isStickerDocument(document) && !MessageObject.isVoiceDocument(document)) {
                                calcBackgroundWidth(maxWidth, timeMore, maxChildWidth);
                                if (backgroundWidth < maxWidth + AndroidUtilities.dp(20)) {
                                    backgroundWidth = maxWidth + AndroidUtilities.dp(20);
                                }
                                createDocumentLayout(backgroundWidth - AndroidUtilities.dp(86 + 24 + 58), messageObject);
                                drawPhotoImage = true;
                                drawImageButton = true;
                                photoImage.setImageCoords(0, totalHeight + namesOffset, AndroidUtilities.dp(86), AndroidUtilities.dp(86));
                                totalHeight += AndroidUtilities.dp(86);
                                linkPreviewHeight += AndroidUtilities.dp(86);
                            }
                        }
                    } else if (webPage.photo != null) {
                        currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, drawImageButton ? AndroidUtilities.getPhotoSize() : maxPhotoWidth, !drawImageButton);
                        currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                        if (currentPhotoObjectThumb == currentPhotoObject) {
                            currentPhotoObjectThumb = null;
                        }
                    }

                    if (isDocument != 1) {
                        if (currentPhotoObject != null) {
                            drawImageButton = webPage.type != null && (webPage.type.equals("photo") || webPage.type.equals("document") || webPage.type.equals("gif"));
                            if (linkPreviewHeight != 0) {
                                linkPreviewHeight += AndroidUtilities.dp(2);
                                totalHeight += AndroidUtilities.dp(2);
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
                                float scale = width / (float) maxPhotoWidth;
                                width /= scale;
                                height /= scale;
                                if (webPage.site_name == null || webPage.site_name != null && !webPage.site_name.toLowerCase().equals("instagram") && isDocument == 0) {
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

                            if (isDocument == 2) {
                                boolean photoExist = true;
                                File cacheFile = FileLoader.getPathToAttach(webPage.document);
                                if (!cacheFile.exists()) {
                                    photoExist = false;
                                }
                                String fileName = FileLoader.getAttachFileName(webPage.document);
                                if (photoExist || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_GIF) || FileLoader.getInstance().isLoadingFile(fileName)) {
                                    photoNotSet = false;
                                    photoImage.setImage(webPage.document, null, currentPhotoObject.location, currentPhotoFilter, webPage.document.size, null, false);
                                } else {
                                    photoNotSet = true;
                                    photoImage.setImage(null, null, currentPhotoObject.location, currentPhotoFilter, 0, null, false);
                                }
                            } else {
                                boolean photoExist = true;
                                File cacheFile = FileLoader.getPathToAttach(currentPhotoObject, true);
                                if (!cacheFile.exists()) {
                                    photoExist = false;
                                }
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

                            if (webPage.site_name != null) {
                                if (webPage.site_name.toLowerCase().equals("instagram") && webPage.type != null && webPage.type.equals("video")) {
                                    isInstagram = true;
                                }
                            }

                            if (webPage.type != null && webPage.type.equals("video") && webPage.duration != 0) {
                                if (durationPaint == null) {
                                    durationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                                    durationPaint.setTextSize(AndroidUtilities.dp(12));
                                    durationPaint.setColor(0xffffffff);
                                }
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
            } else {
                drawForwardedName = messageObject.messageOwner.fwd_from != null && messageObject.type != 13;
                mediaBackground = messageObject.type != 9;
                drawImageButton = true;

                int photoWidth = 0;
                int photoHeight = 0;
                int additionHeight = 0;

                if (messageObject.audioProgress != 2 && !MediaController.getInstance().canAutoplayGifs() && messageObject.type == 8) {
                    messageObject.audioProgress = 1;
                }

                photoImage.setAllowStartAnimation(messageObject.audioProgress == 0);

                photoImage.setForcePreview(messageObject.isSecretPhoto());
                if (messageObject.type == 9) {
                    int maxWidth;
                    if (AndroidUtilities.isTablet()) {
                        maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122 + 86 + 24);
                    } else {
                        maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(122 + 86 + 24);
                    }
                    if (checkNeedDrawShareButton(messageObject)) {
                        maxWidth -= AndroidUtilities.dp(20);
                    }
                    int captionWidth = createDocumentLayout(maxWidth, messageObject);
                    photoWidth = AndroidUtilities.dp(86);
                    photoHeight = AndroidUtilities.dp(86);
                    availableTimeWidth = Math.max(captionWidth, infoWidth) + AndroidUtilities.dp(37);
                    backgroundWidth = photoWidth + availableTimeWidth + AndroidUtilities.dp(31);
                } else if (messageObject.type == 4) { //geo
                    double lat = messageObject.messageOwner.media.geo.lat;
                    double lon = messageObject.messageOwner.media.geo._long;

                    if (messageObject.messageOwner.media.title != null && messageObject.messageOwner.media.title.length() > 0) {
                        int maxWidth = (AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)) - AndroidUtilities.dp((isChat && !messageObject.isOutOwner() ? 102 : 40) + 86 + 24);
                        captionLayout = StaticLayoutEx.createStaticLayout(messageObject.messageOwner.media.title, locationTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth - AndroidUtilities.dp(4), 3);
                        int lineCount = captionLayout.getLineCount();
                        if (messageObject.messageOwner.media.address != null && messageObject.messageOwner.media.address.length() > 0) {
                            infoLayout = StaticLayoutEx.createStaticLayout(messageObject.messageOwner.media.address, locationAddressPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth - AndroidUtilities.dp(4), Math.min(3, 4 - lineCount));
                        } else {
                            infoLayout = null;
                        }

                        mediaBackground = false;
                        availableTimeWidth = maxWidth - AndroidUtilities.dp(7);
                        measureTime(messageObject);
                        photoWidth = AndroidUtilities.dp(86);
                        photoHeight = AndroidUtilities.dp(86);
                        maxWidth = timeWidth + AndroidUtilities.dp(messageObject.isOutOwner() ? 29 : 9);
                        for (int a = 0; a < lineCount; a++) {
                            maxWidth = (int) Math.max(maxWidth, captionLayout.getLineWidth(a) + AndroidUtilities.dp(16));
                        }
                        if (infoLayout != null) {
                            for (int a = 0; a < infoLayout.getLineCount(); a++) {
                                maxWidth = (int) Math.max(maxWidth, infoLayout.getLineWidth(a) + AndroidUtilities.dp(16));
                            }
                        }
                        backgroundWidth = photoWidth + AndroidUtilities.dp(21) + maxWidth;
                        currentUrl = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=72x72&maptype=roadmap&scale=%d&markers=color:red|size:big|%f,%f&sensor=false", lat, lon, Math.min(2, (int) Math.ceil(AndroidUtilities.density)), lat, lon);
                    } else {
                        availableTimeWidth = AndroidUtilities.dp(200 - 14);
                        photoWidth = AndroidUtilities.dp(200);
                        photoHeight = AndroidUtilities.dp(100);
                        backgroundWidth = photoWidth + AndroidUtilities.dp(12);
                        currentUrl = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=200x100&maptype=roadmap&scale=%d&markers=color:red|size:big|%f,%f&sensor=false", lat, lon, Math.min(2, (int) Math.ceil(AndroidUtilities.density)), lat, lon);
                    }

                    photoImage.setNeedsQualityThumb(false);
                    photoImage.setShouldGenerateQualityThumb(false);
                    photoImage.setParentMessageObject(null);
                    photoImage.setImage(currentUrl, null, messageObject.isOutOwner() ? ResourceLoader.geoOutDrawable : ResourceLoader.geoInDrawable, null, 0);
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
                    }
                    if (photoWidth > maxWidth) {
                        photoHeight *= maxWidth / photoWidth;
                        photoWidth = (int) maxWidth;
                    }
                    availableTimeWidth = photoWidth - AndroidUtilities.dp(14);
                    backgroundWidth = photoWidth + AndroidUtilities.dp(12);
                    currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                    photoImage.setNeedsQualityThumb(false);
                    photoImage.setShouldGenerateQualityThumb(false);
                    photoImage.setParentMessageObject(null);
                    if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() > 0) {
                        File f = new File(messageObject.messageOwner.attachPath);
                        if (f.exists()) {
                            photoImage.setImage(null, messageObject.messageOwner.attachPath,
                                    String.format(Locale.US, "%d_%d", photoWidth, photoHeight),
                                    null,
                                    currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null,
                                    "b1",
                                    messageObject.messageOwner.media.document.size, "webp", true);
                        }
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
                        photoImage.setNeedsQualityThumb(false);
                        photoImage.setShouldGenerateQualityThumb(false);
                        photoImage.setParentMessageObject(null);
                        currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                    } else if (messageObject.type == 3) { //video
                        int duration = 0;
                        for (int a = 0; a < messageObject.messageOwner.media.document.attributes.size(); a++) {
                            TLRPC.DocumentAttribute attribute = messageObject.messageOwner.media.document.attributes.get(a);
                            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                duration = attribute.duration;
                                break;
                            }
                        }
                        int minutes = duration / 60;
                        int seconds = duration - minutes * 60;
                        String str = String.format("%d:%02d, %s", minutes, seconds, AndroidUtilities.formatFileSize(messageObject.messageOwner.media.document.size));
                        infoOffset = ResourceLoader.videoIconDrawable.getIntrinsicWidth() + AndroidUtilities.dp(4);
                        infoWidth = (int) Math.ceil(infoPaint.measureText(str));
                        infoLayout = new StaticLayout(str, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

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
                            if (messageObject.type == 3) {
                                w = infoWidth + infoOffset + AndroidUtilities.dp(16);
                            } else {
                                w = AndroidUtilities.dp(100);
                            }
                        }
                        if (h == 0) {
                            h = AndroidUtilities.dp(100);
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
                        w = h = AndroidUtilities.dp(100);
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
                            String fileName = FileLoader.getAttachFileName(currentPhotoObject);
                            boolean photoExist = true;
                            File cacheFile = FileLoader.getPathToMessage(messageObject.messageOwner);
                            if (!cacheFile.exists()) {
                                photoExist = false;
                            } else {
                                MediaController.getInstance().removeLoadingFileObserver(this);
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
                        File cacheFile = null;
                        boolean localFile = false;
                        if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() != 0) {
                            cacheFile = new File(messageObject.messageOwner.attachPath);
                            if (!cacheFile.exists()) {
                                cacheFile = null;
                            } else {
                                MediaController.getInstance().removeLoadingFileObserver(this);
                                localFile = true;
                            }
                        }
                        if (cacheFile == null) {
                            cacheFile = FileLoader.getPathToMessage(messageObject.messageOwner);
                            if (!cacheFile.exists()) {
                                cacheFile = null;
                            }
                        }
                        if (!messageObject.isSending() && (cacheFile != null || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_GIF) && MessageObject.isNewGifDocument(messageObject.messageOwner.media.document) || FileLoader.getInstance().isLoadingFile(fileName))) {
                            if (localFile) {
                                photoImage.setImage(null, messageObject.isSendError() ? null : cacheFile.getAbsolutePath(), null, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, 0, null, false);
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
                } else if (drawName && messageObject.messageOwner.reply_to_msg_id == 0) {
                    namesOffset += AndroidUtilities.dp(7);
                }

                invalidate();

                drawPhotoImage = true;
                photoImage.setImageCoords(0, AndroidUtilities.dp(7) + namesOffset, photoWidth, photoHeight);
                totalHeight = photoHeight + AndroidUtilities.dp(14) + namesOffset + additionHeight;
            }
        }
        updateButtonState(dataChanged);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (currentMessageObject.type == 0) {
            if (currentMessageObject.isOutOwner()) {
                textX = layoutWidth - backgroundWidth + AndroidUtilities.dp(10);
                textY = AndroidUtilities.dp(10) + namesOffset;
            } else {
                textX = AndroidUtilities.dp(19) + (isChat && currentMessageObject.isFromUser() ? AndroidUtilities.dp(52) : 0);
                textY = AndroidUtilities.dp(10) + namesOffset;
            }
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
                    x = AndroidUtilities.dp(67);
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
    protected void onAfterBackgroundDraw(Canvas canvas) {

        photoImage.setPressed(isDrawSelectedBackground());
        photoImage.setVisible(!PhotoViewer.getInstance().isShowingImage(currentMessageObject), false);
        radialProgress.setHideCurrentDrawable(false);

        boolean imageDrawn = false;
        if (currentMessageObject.type == 0 && currentMessageObject.textLayoutBlocks != null && !currentMessageObject.textLayoutBlocks.isEmpty()) {
            if (currentMessageObject.isOutOwner()) {
                textX = layoutWidth - backgroundWidth + AndroidUtilities.dp(10);
                textY = AndroidUtilities.dp(10) + namesOffset;
            } else {
                textX = AndroidUtilities.dp(19) + (isChat && currentMessageObject.isFromUser() ? AndroidUtilities.dp(52) : 0);
                textY = AndroidUtilities.dp(10) + namesOffset;
            }

            if (firstVisibleBlockNum >= 0) {
                for (int a = firstVisibleBlockNum; a <= lastVisibleBlockNum; a++) {
                    if (a >= currentMessageObject.textLayoutBlocks.size()) {
                        break;
                    }
                    MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(a);
                    canvas.save();
                    canvas.translate(textX - (int) Math.ceil(block.textXOffset), textY + block.textYOffset);
                    if (pressedLink != null && a == linkBlockNum) {
                        canvas.drawPath(urlPath, urlPaint);
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
                int linkPreviewY = startY;
                int smallImageStartY = 0;
                replyLinePaint.setColor(currentMessageObject.isOutOwner() ? 0xff8dc97a : 0xff6c9fd2);

                canvas.drawRect(textX, linkPreviewY - AndroidUtilities.dp(3), textX + AndroidUtilities.dp(2), linkPreviewY + linkPreviewHeight + AndroidUtilities.dp(3), replyLinePaint);

                if (sitecaptionLayout != null) {
                    replyNamePaint.setColor(currentMessageObject.isOutOwner() ? 0xff70b15c : 0xff4b91cf);
                    canvas.save();
                    canvas.translate(textX + AndroidUtilities.dp(10), linkPreviewY - AndroidUtilities.dp(3));
                    sitecaptionLayout.draw(canvas);
                    canvas.restore();
                    linkPreviewY += sitecaptionLayout.getLineBottom(sitecaptionLayout.getLineCount() - 1);
                }

                if (titleLayout != null) {
                    if (linkPreviewY != startY) {
                        linkPreviewY += AndroidUtilities.dp(2);
                    }
                    replyNamePaint.setColor(0xff000000);
                    smallImageStartY = linkPreviewY - AndroidUtilities.dp(1);
                    canvas.save();
                    canvas.translate(textX + AndroidUtilities.dp(10) + titleX, linkPreviewY - AndroidUtilities.dp(3));
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
                    replyNamePaint.setColor(0xff000000);
                    canvas.save();
                    canvas.translate(textX + AndroidUtilities.dp(10) + authorX, linkPreviewY - AndroidUtilities.dp(3));
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
                    replyTextPaint.setColor(0xff000000);
                    descriptionY = linkPreviewY - AndroidUtilities.dp(3);
                    canvas.save();
                    canvas.translate(textX + AndroidUtilities.dp(10) + descriptionX, descriptionY);
                    if (pressedLink != null && linkBlockNum == -10) {
                        canvas.drawPath(urlPath, urlPaint);
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
                        photoImage.setImageCoords(textX + backgroundWidth - AndroidUtilities.dp(77), smallImageStartY, photoImage.getImageWidth(), photoImage.getImageHeight());
                    } else {
                        photoImage.setImageCoords(textX + AndroidUtilities.dp(10), linkPreviewY, photoImage.getImageWidth(), photoImage.getImageHeight());
                        if (drawImageButton) {
                            int size = AndroidUtilities.dp(48);
                            buttonX = (int) (photoImage.getImageX() + (photoImage.getImageWidth() - size) / 2.0f);
                            buttonY = (int) (photoImage.getImageY() + (photoImage.getImageHeight() - size) / 2.0f);
                            radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(48), buttonY + AndroidUtilities.dp(48));
                        }
                    }
                    imageDrawn = photoImage.draw(canvas);

                    if (isInstagram && igvideoDrawable != null) {
                        int x = photoImage.getImageX() + photoImage.getImageWidth() - igvideoDrawable.getIntrinsicWidth() - AndroidUtilities.dp(4);
                        int y = photoImage.getImageY() + AndroidUtilities.dp(4);
                        igvideoDrawable.setBounds(x, y, x + igvideoDrawable.getIntrinsicWidth(), y + igvideoDrawable.getIntrinsicHeight());
                        igvideoDrawable.draw(canvas);
                    }

                    if (durationLayout != null) {
                        int x = photoImage.getImageX() + photoImage.getImageWidth() - AndroidUtilities.dp(8) - durationWidth;
                        int y = photoImage.getImageY() + photoImage.getImageHeight() - AndroidUtilities.dp(19);
                        ResourceLoader.mediaBackgroundDrawable.setBounds(x - AndroidUtilities.dp(4), y - AndroidUtilities.dp(1.5f), x + durationWidth + AndroidUtilities.dp(4), y + AndroidUtilities.dp(14.5f));
                        ResourceLoader.mediaBackgroundDrawable.draw(canvas);

                        canvas.save();
                        canvas.translate(x, y);
                        durationLayout.draw(canvas);
                        canvas.restore();
                    }
                }
            }
            drawTime = true;
        } else {
            imageDrawn = photoImage.draw(canvas);
            drawTime = photoImage.getVisible();
            radialProgress.setProgressColor(0xffffffff);
        }

        if (buttonState == -1 && currentMessageObject.isSecretPhoto()) {
            int drawable = 5;
            if (currentMessageObject.messageOwner.destroyTime != 0) {
                if (currentMessageObject.isOutOwner()) {
                    drawable = 7;
                } else {
                    drawable = 6;
                }
            }
            setDrawableBounds(ResourceLoader.buttonStatesDrawables[drawable], buttonX, buttonY);
            ResourceLoader.buttonStatesDrawables[drawable].setAlpha((int) (255 * (1.0f - radialProgress.getAlpha())));
            ResourceLoader.buttonStatesDrawables[drawable].draw(canvas);
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

        if (currentMessageObject.type == 1 || currentMessageObject.type == 3) {
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(captionX = photoImage.getImageX() + AndroidUtilities.dp(5), captionY = photoImage.getImageY() + photoImage.getImageHeight() + AndroidUtilities.dp(6));
                if (pressedLink != null) {
                    canvas.drawPath(urlPath, urlPaint);
                }
                try {
                    captionLayout.draw(canvas);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                canvas.restore();
            }
            if (infoLayout != null && (buttonState == 1 || buttonState == 0 || buttonState == 3 || currentMessageObject.isSecretPhoto())) {
                infoPaint.setColor(0xffffffff);
                setDrawableBounds(ResourceLoader.mediaBackgroundDrawable, photoImage.getImageX() + AndroidUtilities.dp(4), photoImage.getImageY() + AndroidUtilities.dp(4), infoWidth + AndroidUtilities.dp(8) + infoOffset, AndroidUtilities.dp(16.5f));
                ResourceLoader.mediaBackgroundDrawable.draw(canvas);

                if (currentMessageObject.type == 3) {
                    setDrawableBounds(ResourceLoader.videoIconDrawable, photoImage.getImageX() + AndroidUtilities.dp(8), photoImage.getImageY() + AndroidUtilities.dp(7.5f));
                    ResourceLoader.videoIconDrawable.draw(canvas);
                }

                canvas.save();
                canvas.translate(photoImage.getImageX() + AndroidUtilities.dp(8) + infoOffset, photoImage.getImageY() + AndroidUtilities.dp(5.5f));
                infoLayout.draw(canvas);
                canvas.restore();
            }
        } else if (currentMessageObject.type == 4) {
            if (captionLayout != null) {
                locationAddressPaint.setColor(currentMessageObject.isOutOwner() ? 0xff70b15c : (isDrawSelectedBackground() ? 0xff89b4c1 : 0xff999999));

                canvas.save();
                canvas.translate(nameOffsetX + photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10), photoImage.getImageY() + AndroidUtilities.dp(3));
                captionLayout.draw(canvas);
                canvas.restore();

                if (infoLayout != null) {
                    canvas.save();
                    canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10), photoImage.getImageY() + AndroidUtilities.dp(captionLayout.getLineCount() * 16 + 5));
                    infoLayout.draw(canvas);
                    canvas.restore();
                }
            }
        } else if (currentMessageObject.type == 8) {
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(captionX = photoImage.getImageX() + AndroidUtilities.dp(5), captionY = photoImage.getImageY() + photoImage.getImageHeight() + AndroidUtilities.dp(6));
                if (pressedLink != null) {
                    canvas.drawPath(urlPath, urlPaint);
                }
                try {
                    captionLayout.draw(canvas);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                canvas.restore();
            }
        } else if (captionLayout != null) {
            canvas.save();
            canvas.translate(nameOffsetX + photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10), photoImage.getImageY() + AndroidUtilities.dp(8));
            captionLayout.draw(canvas);
            canvas.restore();

            try {
                if (infoLayout != null) {
                    canvas.save();
                    canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10), photoImage.getImageY() + captionLayout.getLineBottom(captionLayout.getLineCount() - 1) + AndroidUtilities.dp(10));
                    infoLayout.draw(canvas);
                    canvas.restore();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        if (isDocument == 1) {
            Drawable menuDrawable;
            if (currentMessageObject.isOutOwner()) {
                infoPaint.setColor(0xff70b15c);
                docBackPaint.setColor(isDrawSelectedBackground() ? 0xffc5eca7 : 0xffdaf5c3);
                menuDrawable = ResourceLoader.docMenuDrawable[1];
            } else {
                infoPaint.setColor(isDrawSelectedBackground() ? 0xff89b4c1 : 0xffa1aab3);
                docBackPaint.setColor(isDrawSelectedBackground() ? 0xffcbeaf6 : 0xffebf0f5);
                menuDrawable = ResourceLoader.docMenuDrawable[isDrawSelectedBackground() ? 2 : 0];
            }

            if (currentMessageObject.type == 0) {
                setDrawableBounds(menuDrawable, otherX = photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(58), photoImage.getImageY() + AndroidUtilities.dp(4));
            } else {
                setDrawableBounds(menuDrawable, otherX = photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(44), photoImage.getImageY() + AndroidUtilities.dp(4));
            }
            menuDrawable.draw(canvas);

            if (buttonState >= 0 && buttonState < 4) {
                if (!imageDrawn) {
                    if (buttonState == 1 && !currentMessageObject.isSending()) {
                        radialProgress.swapBackground(ResourceLoader.buttonStatesDrawablesDoc[2][currentMessageObject.isOutOwner() ? 1 : (isDrawSelectedBackground() ? 2 : 0)]);
                    } else {
                        radialProgress.swapBackground(ResourceLoader.buttonStatesDrawablesDoc[buttonState][currentMessageObject.isOutOwner() ? 1 : (isDrawSelectedBackground() ? 2 : 0)]);
                    }
                } else {
                    if (buttonState == 1 && !currentMessageObject.isSending()) {
                        radialProgress.swapBackground(ResourceLoader.buttonStatesDrawables[4]);
                    } else {
                        radialProgress.swapBackground(ResourceLoader.buttonStatesDrawables[buttonState]);
                    }
                }
            }

            if (!imageDrawn) {
                canvas.drawRect(photoImage.getImageX(), photoImage.getImageY(), photoImage.getImageX() + photoImage.getImageWidth(), photoImage.getImageY() + photoImage.getImageHeight(), docBackPaint);
                if (currentMessageObject.isOutOwner()) {
                    radialProgress.setProgressColor(0xff81bd72);
                } else {
                    radialProgress.setProgressColor(isDrawSelectedBackground() ? 0xff83b2c2 : 0xffadbdcc);
                }
            } else {
                if (buttonState == -1) {
                    radialProgress.setHideCurrentDrawable(true);
                }
                radialProgress.setProgressColor(0xffffffff);
            }

            try {
                if (captionLayout != null) {
                    canvas.save();
                    canvas.translate(nameOffsetX + photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10), photoImage.getImageY() + AndroidUtilities.dp(8));
                    captionLayout.draw(canvas);
                    canvas.restore();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            try {
                if (infoLayout != null) {
                    canvas.save();
                    canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10), photoImage.getImageY() + captionLayout.getLineBottom(captionLayout.getLineCount() - 1) + AndroidUtilities.dp(10));
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
    }

    private Drawable getDrawableForCurrentState() {
        if (buttonState >= 0 && buttonState < 4) {
            if (isDocument == 1) {
                if (buttonState == 1 && !currentMessageObject.isSending()) {
                    return ResourceLoader.buttonStatesDrawablesDoc[2][currentMessageObject.isOutOwner() ? 1 : (isDrawSelectedBackground() ? 2 : 0)];
                } else {
                    return ResourceLoader.buttonStatesDrawablesDoc[buttonState][currentMessageObject.isOutOwner() ? 1 : (isDrawSelectedBackground() ? 2 : 0)];
                }
            } else {
                if (buttonState == 1 && (currentMessageObject.type == 0 || !currentMessageObject.isSending())) {
                    return ResourceLoader.buttonStatesDrawables[4];
                } else {
                    return ResourceLoader.buttonStatesDrawables[buttonState];
                }
            }
        } else if (buttonState == -1 && isDocument == 1) {
            return ResourceLoader.placeholderDocDrawable[currentMessageObject.isOutOwner() ? 1 : (isDrawSelectedBackground() ? 2 : 0)];
        }
        return null;
    }

    @Override
    protected int getMaxNameWidth() {
        if (currentMessageObject.type == 0) {
            return super.getMaxNameWidth();
        }
        return backgroundWidth - AndroidUtilities.dp(mediaBackground ? 14 : 26);
    }

    public void updateButtonState(boolean animated) {
        String fileName = null;
        File cacheFile = null;
        if (currentMessageObject.type == 1) {
            if (currentPhotoObject == null) {
                return;
            }
            fileName = FileLoader.getAttachFileName(currentPhotoObject);
            cacheFile = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
        } else if (currentMessageObject.type == 8 || currentMessageObject.type == 3 || currentMessageObject.type == 9) {
            if (currentMessageObject.messageOwner.attachPath != null && currentMessageObject.messageOwner.attachPath.length() != 0) {
                File f = new File(currentMessageObject.messageOwner.attachPath);
                if (f.exists()) {
                    fileName = currentMessageObject.messageOwner.attachPath;
                    cacheFile = f;
                }
            }
            if (fileName == null) {
                if (!currentMessageObject.isSendError()) {
                    fileName = currentMessageObject.getFileName();
                    cacheFile = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
                }
            }
        } else if (isDocument != 0) {
            fileName = FileLoader.getAttachFileName(currentMessageObject.messageOwner.media.webpage.document);
            cacheFile = FileLoader.getPathToAttach(currentMessageObject.messageOwner.media.webpage.document);
        } else {
            fileName = FileLoader.getAttachFileName(currentPhotoObject);
            cacheFile = FileLoader.getPathToAttach(currentPhotoObject, true);
        }
        if (fileName == null || fileName.length() == 0) {
            radialProgress.setBackground(null, false, false);
            return;
        }

        if (currentMessageObject.type == 0 && isDocument != 1) {
            if (currentPhotoObject == null || !drawImageButton) {
                return;
            }
            if (!cacheFile.exists()) {
                MediaController.getInstance().addLoadingFileObserver(fileName, this);
                float setProgress = 0;
                boolean progressVisible = false;
                if (!FileLoader.getInstance().isLoadingFile(fileName)) {
                    if (!cancelLoading &&
                            (isDocument == 0 && MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO) ||
                                    isDocument == 2 && MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_GIF))) {
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
                if (isDocument == 2 && !photoImage.isAllowStartAnimation()) {
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
                    MediaController.getInstance().addLoadingFileObserver(currentMessageObject.messageOwner.attachPath, this);
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
                if (cacheFile.exists() && cacheFile.length() == 0) {
                    cacheFile.delete();
                }
                if (!cacheFile.exists()) {
                    MediaController.getInstance().addLoadingFileObserver(fileName, this);
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
                    radialProgress.setProgress(setProgress, false);
                    radialProgress.setBackground(getDrawableForCurrentState(), progressVisible, animated);
                    invalidate();
                } else {
                    MediaController.getInstance().removeLoadingFileObserver(this);
                    if (currentMessageObject.type == 8 && !photoImage.isAllowStartAnimation()) {
                        buttonState = 2;
                    } else if (currentMessageObject.type == 3) {
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
            cancelLoading = false;
            radialProgress.setProgress(0, false);
            if (currentMessageObject.type == 1) {
                photoImage.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilter, currentPhotoObject.size, null, false);
            } else if (currentMessageObject.type == 8) {
                currentMessageObject.audioProgress = 2;
                photoImage.setImage(currentMessageObject.messageOwner.media.document, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, currentMessageObject.messageOwner.media.document.size, null, false);
            } else if (currentMessageObject.type == 9) {
                FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.document, false, false);
            } else if (currentMessageObject.type == 3) {
                FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.document, true, false);
            } else if (currentMessageObject.type == 0 && isDocument != 0) {
                if (isDocument == 2) {
                    photoImage.setImage(currentMessageObject.messageOwner.media.webpage.document, null, currentPhotoObject.location, currentPhotoFilter, currentMessageObject.messageOwner.media.webpage.document.size, null, false);
                    currentMessageObject.audioProgress = 2;
                } else if (isDocument == 1) {
                    FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.webpage.document, false, false);
                }
            } else {
                photoImage.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, 0, null, false);
            }
            buttonState = 1;
            radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
            invalidate();
        } else if (buttonState == 1) {
            if (currentMessageObject.isOut() && currentMessageObject.isSending()) {
                delegate.didPressedCancelSendButton(this);
            } else {
                cancelLoading = true;
                if (currentMessageObject.type == 0 && isDocument == 1) {
                    FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.webpage.document);
                } else if (currentMessageObject.type == 0 || currentMessageObject.type == 1 || currentMessageObject.type == 8) {
                    photoImage.cancelLoadImage();
                } else if (currentMessageObject.type == 9 || currentMessageObject.type == 3) {
                    FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.document);
                }
                buttonState = 0;
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                invalidate();
            }
        } else if (buttonState == 2) {
            photoImage.setAllowStartAnimation(true);
            photoImage.startAnimation();
            currentMessageObject.audioProgress = 0;
            buttonState = -1;
            radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
        } else if (buttonState == 3) {
            delegate.didPressedImage(this);
        }
    }

    @Override
    public void onFailedDownload(String fileName) {
        updateButtonState(false);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        radialProgress.setProgress(1, true);
        if (currentMessageObject.type == 0) {
            if (isDocument == 2 && currentMessageObject.audioProgress != 1) {
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

    @Override
    public void onProgressDownload(String fileName, float progress) {
        radialProgress.setProgress(progress, true);
        if (buttonState != 1) {
            updateButtonState(false);
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
