/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.ResourceLoader;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.PhotoViewer;
import org.telegram.messenger.ImageReceiver;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;

public class ChatMediaCell extends ChatBaseCell {

    public interface ChatMediaCellDelegate {
        void didPressedOther(ChatMediaCell cell);
    }

    private static TextPaint infoPaint;
    private static TextPaint namePaint;
    private static Paint docBackPaint;
    private static Paint deleteProgressPaint;
    private static TextPaint locationTitlePaint;
    private static TextPaint locationAddressPaint;

    private RadialProgress radialProgress;

    private int photoWidth;
    private int photoHeight;
    private TLRPC.PhotoSize currentPhotoObject;
    private TLRPC.PhotoSize currentPhotoObjectThumb;
    private String currentUrl;
    private String currentPhotoFilter;
    private ImageReceiver photoImage;
    private boolean photoNotSet = false;
    private boolean cancelLoading = false;
    private int additionHeight;

    private boolean allowedToSetPhoto = true;

    private int buttonState = 0;
    private int buttonPressed = 0;
    private boolean imagePressed = false;
    private boolean otherPressed = false;
    private int buttonX;
    private int buttonY;

    private StaticLayout infoLayout;
    private int infoWidth;
    private int infoOffset = 0;
    private String currentInfoString;

    private StaticLayout nameLayout;
    private int nameWidth = 0;
    private int nameOffsetX = 0;
    private String currentNameString;

    private ChatMediaCellDelegate mediaDelegate = null;
    private RectF deleteProgressRect = new RectF();

    private int captionX;
    private int captionY;
    private int captionHeight;

    public ChatMediaCell(Context context) {
        super(context);

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
        }

        photoImage = new ImageReceiver(this);
        radialProgress = new RadialProgress(this);
    }

    public void setMediaDelegate(ChatMediaCellDelegate delegate) {
        this.mediaDelegate = delegate;
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
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        boolean result = false;
        int side = AndroidUtilities.dp(48);
        if (currentMessageObject.caption instanceof Spannable && delegate.canPerformActions()) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || (linkPreviewPressed || pressedLink != null) && event.getAction() == MotionEvent.ACTION_UP) {
                if (nameLayout != null && x >= captionX && x <= captionX + backgroundWidth && y >= captionY && y <= captionY + captionHeight) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        resetPressedLink();
                        try {
                            int x2 = (int) (x - captionX);
                            int y2 = (int) (y - captionY);
                            final int line = nameLayout.getLineForVertical(y2);
                            final int off = nameLayout.getOffsetForHorizontal(line, x2);

                            final float left = nameLayout.getLineLeft(line);
                            if (left <= x2 && left + nameLayout.getLineWidth(line) >= x2) {
                                Spannable buffer = (Spannable) currentMessageObject.caption;
                                ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                                boolean ignore = false;
                                if (link.length == 0 || link.length != 0 && link[0] instanceof URLSpanBotCommand && !URLSpanBotCommand.enabled) {
                                    ignore = true;
                                }
                                if (!ignore) {
                                    resetPressedLink();
                                    pressedLink = link[0];
                                    linkPreviewPressed = true;
                                    result = true;
                                    try {
                                        int start = buffer.getSpanStart(pressedLink);
                                        urlPath.setCurrentLayout(nameLayout, start);
                                        nameLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink), urlPath);
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                } else {
                                    resetPressedLink();
                                }
                            } else {
                                resetPressedLink();
                            }
                        } catch (Exception e) {
                            resetPressedLink();
                            FileLog.e("tmessages", e);
                        }
                    } else if (linkPreviewPressed) {
                        try {
                            delegate.didPressUrl(currentMessageObject, pressedLink, false);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                        resetPressedLink();
                        result = true;
                    }
                } else {
                    resetPressedLink();
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                resetPressedLink();
            }

            if (result && event.getAction() == MotionEvent.ACTION_DOWN) {
                startCheckLongPress();
            }
            if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }
            if (result) {
                return true;
            }
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (delegate == null || delegate.canPerformActions()) {
                if (buttonState != -1 && x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side) {
                    buttonPressed = 1;
                    invalidate();
                    result = true;
                } else {
                    if (currentMessageObject.type == 9) {
                        if (x >= photoImage.getImageX() && x <= photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(50) && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight()) {
                            imagePressed = true;
                            result = true;
                        } else if (x >= photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(50) && x <= photoImage.getImageX() + backgroundWidth && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight()) {
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
                } else if (result) {
                    startCheckLongPress();
                }
            }
        } else {
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }
            if (buttonPressed == 1) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton(false);
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    buttonPressed = 0;
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!(x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side)) {
                        buttonPressed = 0;
                        invalidate();
                    }
                }
            } else if (imagePressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    imagePressed = false;
                    if (buttonState == -1 || buttonState == 2 || buttonState == 3) {
                        playSoundEffect(SoundEffectConstants.CLICK);
                        didClickedImage();
                    }
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    imagePressed = false;
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (currentMessageObject.type == 9) {
                        if (!(x >= photoImage.getImageX() && x <= photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(50) && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight())) {
                            imagePressed = false;
                            invalidate();
                        }
                    } else {
                        if (!photoImage.isInsideImage(x, y)) {
                            imagePressed = false;
                            invalidate();
                        }
                    }
                }
            } else if (otherPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    otherPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (mediaDelegate != null) {
                        mediaDelegate.didPressedOther(this);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    otherPressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (currentMessageObject.type == 9) {
                        if (!(x >= photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(50) && x <= photoImage.getImageX() + backgroundWidth && y >= photoImage.getImageY() && y <= photoImage.getImageY() + photoImage.getImageHeight())) {
                            otherPressed = false;
                        }
                    }
                }
            }
        }
        if (!result) {
            result = super.onTouchEvent(event);
        }

        return result;
    }

    private void didClickedImage() {
        if (currentMessageObject.type == 1) {
            if (buttonState == -1) {
                if (delegate != null) {
                    delegate.didClickedImage(this);
                }
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
            if (delegate != null) {
                delegate.didClickedImage(this);
            }
        } else if (currentMessageObject.type == 9) {
            if (buttonState == -1) {
                if (delegate != null) {
                    delegate.didClickedImage(this);
                }
            }
        }
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

    private Drawable getDrawableForCurrentState() {
        if (buttonState >= 0 && buttonState < 4) {
            if (currentMessageObject.type == 9) {
                if (buttonState == 1 && !currentMessageObject.isSending()) {
                    return ResourceLoader.buttonStatesDrawablesDoc[2][currentMessageObject.isOutOwner() ? 1 : (isDrawSelectedBackground() ? 2 : 0)];
                } else {
                    return ResourceLoader.buttonStatesDrawablesDoc[buttonState][currentMessageObject.isOutOwner() ? 1 : (isDrawSelectedBackground() ? 2 : 0)];
                }
            } else {
                if (buttonState == 1 && !currentMessageObject.isSending()) {
                    return ResourceLoader.buttonStatesDrawables[4];
                } else {
                    return ResourceLoader.buttonStatesDrawables[buttonState];
                }
            }
        } else if (buttonState == -1) {
            if (currentMessageObject.type == 9) {
                return ResourceLoader.placeholderDocDrawable[currentMessageObject.isOutOwner() ? 1 : (isDrawSelectedBackground() ? 2 : 0)];
            }
        }
        return null;
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
                FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.video, true);
            }
            buttonState = 1;
            radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
            invalidate();
        } else if (buttonState == 1) {
            if (currentMessageObject.isOut() && currentMessageObject.isSending()) {
                if (delegate != null) {
                    delegate.didPressedCancelSendButton(this);
                }
            } else {
                cancelLoading = true;
                if (currentMessageObject.type == 1 || currentMessageObject.type == 8) {
                    photoImage.cancelLoadImage();
                } else if (currentMessageObject.type == 9) {
                    FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.document);
                } else if (currentMessageObject.type == 3) {
                    FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.video);
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
            if (delegate != null) {
                delegate.didClickedImage(this);
            }
        }
    }

    private boolean isPhotoDataChanged(MessageObject object) {
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
    public void setMessageObject(MessageObject messageObject) {
        boolean messageChanged = currentMessageObject != messageObject;
        boolean dataChanged = currentMessageObject == messageObject && (isUserDataChanged() || photoNotSet);
        if (currentMessageObject != messageObject || isPhotoDataChanged(messageObject) || dataChanged) {
            drawForwardedName = messageObject.messageOwner.fwd_from_id != null && messageObject.type != 13;
            media = messageObject.type != 9;
            cancelLoading = false;
            additionHeight = 0;
            resetPressedLink();
            if (messageObject.audioProgress != 2 && !MediaController.getInstance().canAutoplayGifs() && messageObject.type == 8) {
                messageObject.audioProgress = 1;
            }

            buttonState = -1;
            currentPhotoObject = null;
            currentPhotoObjectThumb = null;
            currentUrl = null;
            photoNotSet = false;
            drawBackground = true;
            drawName = false;
            photoImage.setAllowStartAnimation(messageObject.audioProgress == 0);

            photoImage.setForcePreview(messageObject.isSecretPhoto());
            if (messageObject.type == 9) {
                String name = messageObject.getDocumentName();
                if (name == null || name.length() == 0) {
                    name = LocaleController.getString("AttachDocument", R.string.AttachDocument);
                }
                int maxWidth;
                if (AndroidUtilities.isTablet()) {
                    maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122 + 86 + 24);
                } else {
                    maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(122 + 86 + 24);
                }
                if (currentNameString == null || !currentNameString.equals(name)) {
                    currentNameString = name;
                    nameLayout = StaticLayoutEx.createStaticLayout(currentNameString, namePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth, 1);
                    if (nameLayout != null && nameLayout.getLineCount() > 0) {
                        nameWidth = Math.min(maxWidth, (int) Math.ceil(nameLayout.getLineWidth(0)));
                        nameOffsetX = (int) Math.ceil(-nameLayout.getLineLeft(0));
                    } else {
                        nameWidth = maxWidth;
                    }
                }

                String str = AndroidUtilities.formatFileSize(messageObject.messageOwner.media.document.size) + " " + messageObject.getExtension();

                if (currentInfoString == null || !currentInfoString.equals(str)) {
                    currentInfoString = str;
                    infoOffset = 0;
                    infoWidth = Math.min(maxWidth, (int) Math.ceil(infoPaint.measureText(currentInfoString)));
                    CharSequence str2 = TextUtils.ellipsize(currentInfoString, infoPaint, infoWidth, TextUtils.TruncateAt.END);
                    infoLayout = new StaticLayout(str2, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                }
            } else if (messageObject.type == 8) {
                String str = AndroidUtilities.formatFileSize(messageObject.messageOwner.media.document.size);
                if (currentInfoString == null || !currentInfoString.equals(str)) {
                    currentInfoString = str;
                    infoOffset = 0;
                    infoWidth = (int) Math.ceil(infoPaint.measureText(currentInfoString));
                    infoLayout = new StaticLayout(currentInfoString, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                }
                nameLayout = null;
                currentNameString = null;
            } else if (messageObject.type == 3) {
                int duration = messageObject.messageOwner.media.video.duration;
                int minutes = duration / 60;
                int seconds = duration - minutes * 60;
                String str = String.format("%d:%02d, %s", minutes, seconds, AndroidUtilities.formatFileSize(messageObject.messageOwner.media.video.size));
                if (currentInfoString == null || !currentInfoString.equals(str)) {
                    currentInfoString = str;
                    infoOffset = ResourceLoader.videoIconDrawable.getIntrinsicWidth() + AndroidUtilities.dp(4);
                    infoWidth = (int) Math.ceil(infoPaint.measureText(currentInfoString));
                    infoLayout = new StaticLayout(currentInfoString, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                }
                nameLayout = null;
                currentNameString = null;
            } else {
                currentInfoString = null;
                currentNameString = null;
                infoLayout = null;
                nameLayout = null;
                updateSecretTimeText(messageObject);
            }
            if (messageObject.type == 9) { //doc
                photoWidth = AndroidUtilities.dp(86);
                photoHeight = AndroidUtilities.dp(86);
                backgroundWidth = photoWidth + Math.max(nameWidth, infoWidth) + AndroidUtilities.dp(68);
                currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
                photoImage.setNeedsQualityThumb(true);
                photoImage.setShouldGenerateQualityThumb(true);
                photoImage.setParentMessageObject(messageObject);
                if (currentPhotoObject != null) {
                    currentPhotoFilter = String.format(Locale.US, "%d_%d_b", photoWidth, photoHeight);
                    photoImage.setImage(null, null, null, null, currentPhotoObject.location, currentPhotoFilter, 0, null, true);
                } else {
                    photoImage.setImageBitmap((BitmapDrawable) null);
                }
            } else if (messageObject.type == 4) { //geo
                double lat = messageObject.messageOwner.media.geo.lat;
                double lon = messageObject.messageOwner.media.geo._long;

                if (messageObject.messageOwner.media.title != null && messageObject.messageOwner.media.title.length() > 0) {
                    int maxWidth = (AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)) - AndroidUtilities.dp((isChat && !messageObject.isOutOwner() ? 102 : 40) + 86 + 24);
                    nameLayout = StaticLayoutEx.createStaticLayout(messageObject.messageOwner.media.title, locationTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth - AndroidUtilities.dp(4), 3);
                    int lineCount = nameLayout.getLineCount();
                    if (messageObject.messageOwner.media.address != null && messageObject.messageOwner.media.address.length() > 0) {
                        infoLayout = StaticLayoutEx.createStaticLayout(messageObject.messageOwner.media.address, locationAddressPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth - AndroidUtilities.dp(4), Math.min(3, 4 - lineCount));
                    } else {
                        infoLayout = null;
                    }

                    media = false;
                    measureTime(messageObject);
                    photoWidth = AndroidUtilities.dp(86);
                    photoHeight = AndroidUtilities.dp(86);
                    maxWidth = timeWidth + AndroidUtilities.dp(messageObject.isOutOwner() ? 29 : 9);
                    for (int a = 0; a < lineCount; a++) {
                        maxWidth = (int) Math.max(maxWidth, nameLayout.getLineWidth(a) + AndroidUtilities.dp(16));
                    }
                    if (infoLayout != null) {
                        for (int a = 0; a < infoLayout.getLineCount(); a++) {
                            maxWidth = (int) Math.max(maxWidth, infoLayout.getLineWidth(a) + AndroidUtilities.dp(16));
                        }
                    }
                    backgroundWidth = photoWidth + AndroidUtilities.dp(21) + maxWidth;
                    currentUrl = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=72x72&maptype=roadmap&scale=%d&markers=color:red|size:big|%f,%f&sensor=false", lat, lon, Math.min(2, (int) Math.ceil(AndroidUtilities.density)), lat, lon);
                } else {
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
                if (AndroidUtilities.isTablet()) {
                    photoWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.7f);
                } else {
                    photoWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f);
                }
                photoHeight = photoWidth + AndroidUtilities.dp(100);

                if (photoWidth > AndroidUtilities.getPhotoSize()) {
                    photoWidth = AndroidUtilities.getPhotoSize();
                }
                if (photoHeight > AndroidUtilities.getPhotoSize()) {
                    photoHeight = AndroidUtilities.getPhotoSize();
                }

                if (messageObject.type == 1) {
                    photoImage.setNeedsQualityThumb(false);
                    photoImage.setShouldGenerateQualityThumb(false);
                    photoImage.setParentMessageObject(null);
                    currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                } else if (messageObject.type == 3) {
                    photoImage.setNeedsQualityThumb(true);
                    photoImage.setShouldGenerateQualityThumb(true);
                    photoImage.setParentMessageObject(messageObject);
                } else if (messageObject.type == 8) {
                    photoImage.setNeedsQualityThumb(true);
                    photoImage.setShouldGenerateQualityThumb(true);
                    photoImage.setParentMessageObject(messageObject);
                }
                //8 - gif, 1 - photo, 3 - video

                if (messageObject.caption != null) {
                    media = false;
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
                if (!media) {
                    backgroundWidth += AndroidUtilities.dp(9);
                }
                if (messageObject.caption != null) {
                    try {
                        nameLayout = new StaticLayout(messageObject.caption, MessageObject.textPaint, photoWidth - AndroidUtilities.dp(10), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        if (nameLayout != null && nameLayout.getLineCount() > 0) {
                            captionHeight = nameLayout.getHeight();
                            additionHeight += captionHeight + AndroidUtilities.dp(9);
                            float lastLineWidth = nameLayout.getLineWidth(nameLayout.getLineCount() - 1) + nameLayout.getLineLeft(nameLayout.getLineCount() - 1);
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
                        photoImage.setImageBitmap((Bitmap) null);
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
        }
        updateButtonState(dataChanged);
    }

    @Override
    protected int getMaxNameWidth() {
        return backgroundWidth - AndroidUtilities.dp(14);
    }

    @Override
    public ImageReceiver getPhotoImage() {
        return photoImage;
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
        }
        if (fileName == null || fileName.length() == 0) {
            radialProgress.setBackground(null, false, false);
            return;
        }
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), photoHeight + AndroidUtilities.dp(14) + namesOffset + additionHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int x;
        if (currentMessageObject.isOutOwner()) {
            if (media) {
                x = layoutWidth - backgroundWidth - AndroidUtilities.dp(3);
            } else {
                x = layoutWidth - backgroundWidth + AndroidUtilities.dp(6);
            }
        } else {
            if (isChat && currentMessageObject.messageOwner.from_id > 0) {
                x = AndroidUtilities.dp(67);
            } else {
                x = AndroidUtilities.dp(15);
            }
        }
        photoImage.setImageCoords(x, AndroidUtilities.dp(7) + namesOffset, photoWidth, photoHeight);
        int size = AndroidUtilities.dp(48);
        buttonX = (int) (x + (photoWidth - size) / 2.0f);
        buttonY = (int) (AndroidUtilities.dp(7) + (photoHeight - size) / 2.0f) + namesOffset;

        radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(48), buttonY + AndroidUtilities.dp(48));
        deleteProgressRect.set(buttonX + AndroidUtilities.dp(3), buttonY + AndroidUtilities.dp(3), buttonX + AndroidUtilities.dp(45), buttonY + AndroidUtilities.dp(45));
    }

    private void updateSecretTimeText(MessageObject messageObject) {
        if (messageObject == null || messageObject.isOut()) {
            return;
        }
        String str = messageObject.getSecretTimeString();
        if (str == null) {
            infoLayout = null;
            return;
        }
        if (currentInfoString == null || !currentInfoString.equals(str)) {
            currentInfoString = str;
            infoOffset = 0;
            infoWidth = (int) Math.ceil(infoPaint.measureText(currentInfoString));
            CharSequence str2 = TextUtils.ellipsize(currentInfoString, infoPaint, infoWidth, TextUtils.TruncateAt.END);
            infoLayout = new StaticLayout(str2, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            invalidate();
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

    @Override
    protected void onAfterBackgroundDraw(Canvas canvas) {
        photoImage.setPressed(isDrawSelectedBackground());
        photoImage.setVisible(!PhotoViewer.getInstance().isShowingImage(currentMessageObject), false);
        boolean imageDrawn = photoImage.draw(canvas);
        drawTime = photoImage.getVisible();

        radialProgress.setHideCurrentDrawable(false);

        if (currentMessageObject.type == 9) {
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

            setDrawableBounds(menuDrawable, photoImage.getImageX() + backgroundWidth - AndroidUtilities.dp(44), AndroidUtilities.dp(10) + namesOffset);
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
        } else {
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

        radialProgress.draw(canvas);

        if (currentMessageObject.type == 1 || currentMessageObject.type == 3) {
            if (nameLayout != null) {
                canvas.save();
                canvas.translate(captionX = photoImage.getImageX() + AndroidUtilities.dp(5), captionY = photoImage.getImageY() + photoHeight + AndroidUtilities.dp(6));
                if (pressedLink != null) {
                    canvas.drawPath(urlPath, urlPaint);
                }
                try {
                    nameLayout.draw(canvas);
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
            if (nameLayout != null) {
                locationAddressPaint.setColor(currentMessageObject.isOutOwner() ? 0xff70b15c : (isDrawSelectedBackground() ? 0xff89b4c1 : 0xff999999));

                canvas.save();
                canvas.translate(nameOffsetX + photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10), photoImage.getImageY() + AndroidUtilities.dp(3));
                nameLayout.draw(canvas);
                canvas.restore();

                if (infoLayout != null) {
                    canvas.save();
                    canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10), photoImage.getImageY() + AndroidUtilities.dp(nameLayout.getLineCount() * 16 + 5));
                    infoLayout.draw(canvas);
                    canvas.restore();
                }
            }
        } else if (currentMessageObject.type == 8) {
            if (nameLayout != null) {
                canvas.save();
                canvas.translate(captionX = photoImage.getImageX() + AndroidUtilities.dp(5), captionY = photoImage.getImageY() + photoHeight + AndroidUtilities.dp(6));
                if (pressedLink != null) {
                    canvas.drawPath(urlPath, urlPaint);
                }
                try {
                    nameLayout.draw(canvas);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                canvas.restore();
            }
        } else if (nameLayout != null) {
            canvas.save();
            canvas.translate(nameOffsetX + photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10), photoImage.getImageY() + AndroidUtilities.dp(8));
            nameLayout.draw(canvas);
            canvas.restore();

            if (infoLayout != null) {
                canvas.save();
                canvas.translate(photoImage.getImageX() + photoImage.getImageWidth() + AndroidUtilities.dp(10), photoImage.getImageY() + AndroidUtilities.dp(30));
                infoLayout.draw(canvas);
                canvas.restore();
            }
        }
    }

    @Override
    public void onFailedDownload(String fileName) {
        updateButtonState(false);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        radialProgress.setProgress(1, true);
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
}
