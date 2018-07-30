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
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LetterDrawable;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class ContextLinkCell extends View implements DownloadController.FileDownloadProgressListener {

    private final static int DOCUMENT_ATTACH_TYPE_NONE = 0;
    private final static int DOCUMENT_ATTACH_TYPE_DOCUMENT = 1;
    private final static int DOCUMENT_ATTACH_TYPE_GIF = 2;
    private final static int DOCUMENT_ATTACH_TYPE_AUDIO = 3;
    private final static int DOCUMENT_ATTACH_TYPE_VIDEO = 4;
    private final static int DOCUMENT_ATTACH_TYPE_MUSIC = 5;
    private final static int DOCUMENT_ATTACH_TYPE_STICKER = 6;
    private final static int DOCUMENT_ATTACH_TYPE_PHOTO = 7;
    private final static int DOCUMENT_ATTACH_TYPE_GEO = 8;

    public interface ContextLinkCellDelegate {
        void didPressedImage(ContextLinkCell cell);
    }

    private ImageReceiver linkImageView;
    private boolean drawLinkImageView;
    private LetterDrawable letterDrawable;
    private int currentAccount = UserConfig.selectedAccount;

    private boolean needDivider;
    private boolean buttonPressed;
    private boolean needShadow;

    private int linkY;
    private StaticLayout linkLayout;

    private int titleY = AndroidUtilities.dp(7);
    private StaticLayout titleLayout;

    private int descriptionY = AndroidUtilities.dp(27);
    private StaticLayout descriptionLayout;

    private TLRPC.BotInlineResult inlineResult;
    private TLRPC.Document documentAttach;
    private TLRPC.PhotoSize currentPhotoObject;
    private int documentAttachType;
    private boolean mediaWebpage;
    private MessageObject currentMessageObject;

    private int TAG;
    private int buttonState;
    private RadialProgress radialProgress;

    private long lastUpdateTime;
    private boolean scaled;
    private float scale;
    private static AccelerateInterpolator interpolator = new AccelerateInterpolator(0.5f);

    private ContextLinkCellDelegate delegate;

    public ContextLinkCell(Context context) {
        super(context);

        linkImageView = new ImageReceiver(this);
        letterDrawable = new LetterDrawable();
        radialProgress = new RadialProgress(this);
        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        drawLinkImageView = false;
        descriptionLayout = null;
        titleLayout = null;
        linkLayout = null;
        currentPhotoObject = null;
        linkY = AndroidUtilities.dp(27);

        if (inlineResult == null && documentAttach == null) {
            setMeasuredDimension(AndroidUtilities.dp(100), AndroidUtilities.dp(100));
            return;
        }

        int viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        int maxWidth = viewWidth - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - AndroidUtilities.dp(8);

        TLRPC.PhotoSize currentPhotoObjectThumb = null;
        ArrayList<TLRPC.PhotoSize> photoThumbs = null;
        WebFile webFile = null;
        TLRPC.TL_webDocument webDocument = null;
        String urlLocation = null;

        if (documentAttach != null) {
            photoThumbs = new ArrayList<>();
            photoThumbs.add(documentAttach.thumb);
        } else if (inlineResult != null && inlineResult.photo != null) {
            photoThumbs = new ArrayList<>(inlineResult.photo.sizes);
        }

        if (!mediaWebpage && inlineResult != null) {
            if (inlineResult.title != null) {
                try {
                    int width = (int) Math.ceil(Theme.chat_contextResult_titleTextPaint.measureText(inlineResult.title));
                    CharSequence titleFinal = TextUtils.ellipsize(Emoji.replaceEmoji(inlineResult.title.replace('\n', ' '), Theme.chat_contextResult_titleTextPaint.getFontMetricsInt(), AndroidUtilities.dp(15), false), Theme.chat_contextResult_titleTextPaint, Math.min(width, maxWidth), TextUtils.TruncateAt.END);
                    titleLayout = new StaticLayout(titleFinal, Theme.chat_contextResult_titleTextPaint, maxWidth + AndroidUtilities.dp(4), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                letterDrawable.setTitle(inlineResult.title);
            }

            if (inlineResult.description != null) {
                try {
                    descriptionLayout = ChatMessageCell.generateStaticLayout(Emoji.replaceEmoji(inlineResult.description, Theme.chat_contextResult_descriptionTextPaint.getFontMetricsInt(), AndroidUtilities.dp(13), false), Theme.chat_contextResult_descriptionTextPaint, maxWidth, maxWidth, 0, 3);
                    if (descriptionLayout.getLineCount() > 0) {
                        linkY = descriptionY + descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1) + AndroidUtilities.dp(1);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            if (inlineResult.url != null) {
                try {
                    int width = (int) Math.ceil(Theme.chat_contextResult_descriptionTextPaint.measureText(inlineResult.url));
                    CharSequence linkFinal = TextUtils.ellipsize(inlineResult.url.replace('\n', ' '), Theme.chat_contextResult_descriptionTextPaint, Math.min(width, maxWidth), TextUtils.TruncateAt.MIDDLE);
                    linkLayout = new StaticLayout(linkFinal, Theme.chat_contextResult_descriptionTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        String ext = null;
        if (documentAttach != null) {
            if (MessageObject.isGifDocument(documentAttach)) {
                currentPhotoObject = documentAttach.thumb;
            } else if (MessageObject.isStickerDocument(documentAttach)) {
                currentPhotoObject = documentAttach.thumb;
                ext = "webp";
            } else {
                if (documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC && documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO) {
                    currentPhotoObject = documentAttach.thumb;
                }
            }
        } else if (inlineResult != null && inlineResult.photo != null) {
            currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize(), true);
            currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, 80);
            if (currentPhotoObjectThumb == currentPhotoObject) {
                currentPhotoObjectThumb = null;
            }
        }
        if (inlineResult != null) {
            if (inlineResult.content instanceof TLRPC.TL_webDocument) {
                if (inlineResult.type != null) {
                    if (inlineResult.type.startsWith("gif")) {
                        if (documentAttachType != DOCUMENT_ATTACH_TYPE_GIF) {
                            webDocument = (TLRPC.TL_webDocument) inlineResult.content;
                            documentAttachType = DOCUMENT_ATTACH_TYPE_GIF;
                        }
                    } else if (inlineResult.type.equals("photo")) {
                        if (inlineResult.thumb instanceof TLRPC.TL_webDocument) {
                            webDocument = (TLRPC.TL_webDocument) inlineResult.thumb;
                        } else {
                            webDocument = (TLRPC.TL_webDocument) inlineResult.content;
                        }
                    }
                }
            }
            if (webDocument == null && (inlineResult.thumb instanceof TLRPC.TL_webDocument)) {
                webDocument = (TLRPC.TL_webDocument) inlineResult.thumb;
            }
            if (webDocument == null && currentPhotoObject == null && currentPhotoObjectThumb == null) {
                if (inlineResult.send_message instanceof TLRPC.TL_botInlineMessageMediaVenue || inlineResult.send_message instanceof TLRPC.TL_botInlineMessageMediaGeo) {
                    double lat = inlineResult.send_message.geo.lat;
                    double lon = inlineResult.send_message.geo._long;
                    if (MessagesController.getInstance(currentAccount).mapProvider == 2) {
                        webFile = WebFile.createWithGeoPoint(inlineResult.send_message.geo, 72, 72, 15, Math.min(2, (int) Math.ceil(AndroidUtilities.density)));
                    } else {
                        urlLocation = AndroidUtilities.formapMapUrl(currentAccount, lat, lon, 72, 72, true, 15);
                    }
                }
            }
            if (webDocument != null) {
                webFile = WebFile.createWithWebDocument(webDocument);
            }
        }

        int width;
        int w = 0;
        int h = 0;

        if (documentAttach != null) {
            for (int b = 0; b < documentAttach.attributes.size(); b++) {
                TLRPC.DocumentAttribute attribute = documentAttach.attributes.get(b);
                if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    w = attribute.w;
                    h = attribute.h;
                    break;
                }
            }
        }
        if (w == 0 || h == 0) {
            if (currentPhotoObject != null) {
                if (currentPhotoObjectThumb != null) {
                    currentPhotoObjectThumb.size = -1;
                }
                w = currentPhotoObject.w;
                h = currentPhotoObject.h;
            } else if (inlineResult != null) {
                int result[] = MessageObject.getInlineResultWidthAndHeight(inlineResult);
                w = result[0];
                h = result[1];
            }
        }
        if (w == 0 || h == 0) {
            w = h = AndroidUtilities.dp(80);
        }
        if (documentAttach != null || currentPhotoObject != null || webFile != null || urlLocation != null) {
            String currentPhotoFilter;
            String currentPhotoFilterThumb = "52_52_b";

            if (mediaWebpage) {
                width = (int) (w / (h / (float) AndroidUtilities.dp(80)));
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                    currentPhotoFilterThumb = currentPhotoFilter = String.format(Locale.US, "%d_%d_b", (int) (width / AndroidUtilities.density), 80);
                } else {
                    currentPhotoFilter = String.format(Locale.US, "%d_%d", (int) (width / AndroidUtilities.density), 80);
                    currentPhotoFilterThumb = currentPhotoFilter + "_b";
                }
            } else {
                currentPhotoFilter = "52_52";
            }
            linkImageView.setAspectFit(documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER);

            if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                if (documentAttach != null) {
                    linkImageView.setImage(documentAttach, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, documentAttach.size, ext, 0);
                } else {
                    linkImageView.setImage(webFile, urlLocation, null, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, -1, ext, 1);
                }
            } else {
                if (currentPhotoObject != null) {
                    linkImageView.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, currentPhotoObject.size, ext, 0);
                } else {
                    linkImageView.setImage(webFile, urlLocation, currentPhotoFilter, null, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, -1, ext, 1);
                }
            }
            drawLinkImageView = true;
        }

        if (mediaWebpage) {
            width = viewWidth;
            int height = MeasureSpec.getSize(heightMeasureSpec);
            if (height == 0) {
                height = AndroidUtilities.dp(100);
            }
            setMeasuredDimension(width, height);
            int x = (width - AndroidUtilities.dp(24)) / 2;
            int y = (height - AndroidUtilities.dp(24)) / 2;
            radialProgress.setProgressRect(x, y, x + AndroidUtilities.dp(24), y + AndroidUtilities.dp(24));
            linkImageView.setImageCoords(0, 0, width, height);
        } else {
            int height = 0;
            if (titleLayout != null && titleLayout.getLineCount() != 0) {
                height += titleLayout.getLineBottom(titleLayout.getLineCount() - 1);
            }
            if (descriptionLayout != null && descriptionLayout.getLineCount() != 0) {
                height += descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
            }
            if (linkLayout != null && linkLayout.getLineCount() > 0) {
                height += linkLayout.getLineBottom(linkLayout.getLineCount() - 1);
            }
            height = Math.max(AndroidUtilities.dp(52), height);
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.max(AndroidUtilities.dp(68), height + AndroidUtilities.dp(16)) + (needDivider ? 1 : 0));

            int maxPhotoWidth = AndroidUtilities.dp(52);
            int x = LocaleController.isRTL ? MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(8) - maxPhotoWidth : AndroidUtilities.dp(8);
            letterDrawable.setBounds(x, AndroidUtilities.dp(8), x + maxPhotoWidth, AndroidUtilities.dp(60));
            linkImageView.setImageCoords(x, AndroidUtilities.dp(8), maxPhotoWidth, maxPhotoWidth);
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                radialProgress.setProgressRect(x + AndroidUtilities.dp(4), AndroidUtilities.dp(12), x + AndroidUtilities.dp(48), AndroidUtilities.dp(56));
            }
        }
    }

    private void setAttachType() {
        currentMessageObject = null;
        documentAttachType = DOCUMENT_ATTACH_TYPE_NONE;
        if (documentAttach != null) {
            if (MessageObject.isGifDocument(documentAttach)) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_GIF;
            } else if (MessageObject.isStickerDocument(documentAttach)) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_STICKER;
            } else if (MessageObject.isMusicDocument(documentAttach)) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_MUSIC;
            } else if (MessageObject.isVoiceDocument(documentAttach)) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_AUDIO;
            }
        } else if (inlineResult != null) {
            if (inlineResult.photo != null) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_PHOTO;
            } else if (inlineResult.type.equals("audio")) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_MUSIC;
            } else if (inlineResult.type.equals("voice")) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_AUDIO;
            }
        }
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            TLRPC.TL_message message = new TLRPC.TL_message();
            message.out = true;
            message.id = -Utilities.random.nextInt();
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = message.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
            message.date = (int) (System.currentTimeMillis() / 1000);
            message.message = "";
            message.media = new TLRPC.TL_messageMediaDocument();
            message.media.flags |= 3;
            message.media.document = new TLRPC.TL_document();
            message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;

            if (documentAttach != null) {
                message.media.document = documentAttach;
                message.attachPath = "";
            } else {
                String ext = ImageLoader.getHttpUrlExtension(inlineResult.content.url, documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC ? "mp3" : "ogg");
                message.media.document.id = 0;
                message.media.document.access_hash = 0;
                message.media.document.date = message.date;
                message.media.document.mime_type = "audio/" + ext;
                message.media.document.size = 0;
                message.media.document.thumb = new TLRPC.TL_photoSizeEmpty();
                message.media.document.thumb.type = "s";
                message.media.document.dc_id = 0;

                TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
                attributeAudio.duration = MessageObject.getInlineResultDuration(inlineResult);
                attributeAudio.title = inlineResult.title != null ? inlineResult.title : "";
                attributeAudio.performer = inlineResult.description != null ? inlineResult.description : "";
                attributeAudio.flags |= 3;
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
                    attributeAudio.voice = true;
                }
                message.media.document.attributes.add(attributeAudio);

                TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                fileName.file_name = Utilities.MD5(inlineResult.content.url) + "." + ImageLoader.getHttpUrlExtension(inlineResult.content.url, documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC ? "mp3" : "ogg");
                message.media.document.attributes.add(fileName);

                message.attachPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(inlineResult.content.url) + "." + ImageLoader.getHttpUrlExtension(inlineResult.content.url, documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC ? "mp3" : "ogg")).getAbsolutePath();
            }

            currentMessageObject = new MessageObject(currentAccount, message, false);
        }
    }

    public void setLink(TLRPC.BotInlineResult contextResult, boolean media, boolean divider, boolean shadow) {
        needDivider = divider;
        needShadow = shadow;
        inlineResult = contextResult;
        if (inlineResult != null && inlineResult.document != null) {
            documentAttach = inlineResult.document;
        } else {
            documentAttach = null;
        }
        mediaWebpage = media;
        setAttachType();
        requestLayout();
        updateButtonState(false);
    }

    public void setGif(TLRPC.Document document, boolean divider) {
        needDivider = divider;
        needShadow = false;
        inlineResult = null;
        documentAttach = document;
        mediaWebpage = true;
        setAttachType();
        requestLayout();
        updateButtonState(false);
    }

    public boolean isSticker() {
        return documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER;
    }

    public boolean showingBitmap() {
        return linkImageView.getBitmap() != null;
    }

    public TLRPC.Document getDocument() {
        return documentAttach;
    }

    public ImageReceiver getPhotoImage() {
        return linkImageView;
    }

    public void setScaled(boolean value) {
        scaled = value;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (drawLinkImageView) {
            linkImageView.onDetachedFromWindow();
        }
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (drawLinkImageView) {
            if (linkImageView.onAttachedToWindow()) {
                updateButtonState(false);
            }
        }
    }

    public MessageObject getMessageObject() {
        return currentMessageObject;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mediaWebpage || delegate == null || inlineResult == null) {
            return super.onTouchEvent(event);
        }
        int x = (int) event.getX();
        int y = (int) event.getY();

        boolean result = false;
        int side = AndroidUtilities.dp(48);
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            boolean area = letterDrawable.getBounds().contains(x, y);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (area) {
                    buttonPressed = true;
                    invalidate();
                    result = true;
                    radialProgress.swapBackground(getDrawableForCurrentState());
                }
            } else if (buttonPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    buttonPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton();
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    buttonPressed = false;
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!area) {
                        buttonPressed = false;
                        invalidate();
                    }
                }
                radialProgress.swapBackground(getDrawableForCurrentState());
            }
        } else {
            if (inlineResult != null && inlineResult.content != null && !TextUtils.isEmpty(inlineResult.content.url)) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (letterDrawable.getBounds().contains(x, y)) {
                        buttonPressed = true;
                        result = true;
                    }
                } else {
                    if (buttonPressed) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            buttonPressed = false;
                            playSoundEffect(SoundEffectConstants.CLICK);
                            delegate.didPressedImage(this);
                        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                            buttonPressed = false;
                        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                            if (!letterDrawable.getBounds().contains(x, y)) {
                                buttonPressed = false;
                            }
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

    private void didPressedButton() {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (buttonState == 0) {
                if (MediaController.getInstance().playMessage(currentMessageObject)) {
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
                if (documentAttach != null) {
                    FileLoader.getInstance(currentAccount).loadFile(documentAttach, true, 0);
                } else if (inlineResult.content instanceof TLRPC.TL_webDocument) {
                    FileLoader.getInstance(currentAccount).loadFile(WebFile.createWithWebDocument(inlineResult.content), true, 1);
                }
                buttonState = 4;
                radialProgress.setBackground(getDrawableForCurrentState(), true, false);
                invalidate();
            } else if (buttonState == 4) {
                if (documentAttach != null) {
                    FileLoader.getInstance(currentAccount).cancelLoadFile(documentAttach);
                } else if (inlineResult.content instanceof TLRPC.TL_webDocument) {
                    FileLoader.getInstance(currentAccount).cancelLoadFile(WebFile.createWithWebDocument(inlineResult.content));
                }
                buttonState = 2;
                radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                invalidate();
            }
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

        if (linkLayout != null) {
            Theme.chat_contextResult_descriptionTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), linkY);
            linkLayout.draw(canvas);
            canvas.restore();
        }

        if (!mediaWebpage) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                radialProgress.setProgressColor(Theme.getColor(buttonPressed ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
                radialProgress.draw(canvas);
            } else if (inlineResult != null && inlineResult.type.equals("file")) {
                int w = Theme.chat_inlineResultFile.getIntrinsicWidth();
                int h = Theme.chat_inlineResultFile.getIntrinsicHeight();
                int x = linkImageView.getImageX() + (AndroidUtilities.dp(52) - w) / 2;
                int y = linkImageView.getImageY() + (AndroidUtilities.dp(52) - h) / 2;
                canvas.drawRect(linkImageView.getImageX(), linkImageView.getImageY(), linkImageView.getImageX() + AndroidUtilities.dp(52), linkImageView.getImageY() + AndroidUtilities.dp(52), LetterDrawable.paint);
                Theme.chat_inlineResultFile.setBounds(x, y, x + w, y + h);
                Theme.chat_inlineResultFile.draw(canvas);
            } else if (inlineResult != null && (inlineResult.type.equals("audio") || inlineResult.type.equals("voice"))) {
                int w = Theme.chat_inlineResultAudio.getIntrinsicWidth();
                int h = Theme.chat_inlineResultAudio.getIntrinsicHeight();
                int x = linkImageView.getImageX() + (AndroidUtilities.dp(52) - w) / 2;
                int y = linkImageView.getImageY() + (AndroidUtilities.dp(52) - h) / 2;
                canvas.drawRect(linkImageView.getImageX(), linkImageView.getImageY(), linkImageView.getImageX() + AndroidUtilities.dp(52), linkImageView.getImageY() + AndroidUtilities.dp(52), LetterDrawable.paint);
                Theme.chat_inlineResultAudio.setBounds(x, y, x + w, y + h);
                Theme.chat_inlineResultAudio.draw(canvas);
            } else if (inlineResult != null && (inlineResult.type.equals("venue") || inlineResult.type.equals("geo"))) {
                int w = Theme.chat_inlineResultLocation.getIntrinsicWidth();
                int h = Theme.chat_inlineResultLocation.getIntrinsicHeight();
                int x = linkImageView.getImageX() + (AndroidUtilities.dp(52) - w) / 2;
                int y = linkImageView.getImageY() + (AndroidUtilities.dp(52) - h) / 2;
                canvas.drawRect(linkImageView.getImageX(), linkImageView.getImageY(), linkImageView.getImageX() + AndroidUtilities.dp(52), linkImageView.getImageY() + AndroidUtilities.dp(52), LetterDrawable.paint);
                Theme.chat_inlineResultLocation.setBounds(x, y, x + w, y + h);
                Theme.chat_inlineResultLocation.draw(canvas);
            } else {
                letterDrawable.draw(canvas);
            }
        } else {
            if (inlineResult != null && (inlineResult.send_message instanceof TLRPC.TL_botInlineMessageMediaGeo || inlineResult.send_message instanceof TLRPC.TL_botInlineMessageMediaVenue)) {
                int w = Theme.chat_inlineResultLocation.getIntrinsicWidth();
                int h = Theme.chat_inlineResultLocation.getIntrinsicHeight();
                int x = linkImageView.getImageX() + (linkImageView.getImageWidth() - w) / 2;
                int y = linkImageView.getImageY() + (linkImageView.getImageHeight() - h) / 2;
                canvas.drawRect(linkImageView.getImageX(), linkImageView.getImageY(), linkImageView.getImageX() + linkImageView.getImageWidth(), linkImageView.getImageY() + linkImageView.getImageHeight(), LetterDrawable.paint);
                Theme.chat_inlineResultLocation.setBounds(x, y, x + w, y + h);
                Theme.chat_inlineResultLocation.draw(canvas);
            }
        }
        if (drawLinkImageView) {
            if (inlineResult != null) {
                linkImageView.setVisible(!PhotoViewer.isShowingImage(inlineResult), false);
            }
            canvas.save();
            if (scaled && scale != 0.8f || !scaled && scale != 1.0f) {
                long newTime = System.currentTimeMillis();
                long dt = (newTime - lastUpdateTime);
                lastUpdateTime = newTime;
                if (scaled && scale != 0.8f) {
                    scale -= dt / 400.0f;
                    if (scale < 0.8f) {
                        scale = 0.8f;
                    }
                } else {
                    scale += dt / 400.0f;
                    if (scale > 1.0f) {
                        scale = 1.0f;
                    }
                }
                invalidate();
            }
            canvas.scale(scale, scale, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
            linkImageView.draw(canvas);
            canvas.restore();
        }
        if (mediaWebpage && (documentAttachType == DOCUMENT_ATTACH_TYPE_PHOTO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF)) {
            radialProgress.draw(canvas);
        }

        if (needDivider && !mediaWebpage) {
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, Theme.dividerPaint);
            } else {
                canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
        if (needShadow) {
            Theme.chat_contextResult_shadowUnderSwitchDrawable.setBounds(0, 0, getMeasuredWidth(), AndroidUtilities.dp(3));
            Theme.chat_contextResult_shadowUnderSwitchDrawable.draw(canvas);
        }
    }

    private Drawable getDrawableForCurrentState() {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (buttonState == -1) {
                return null;
            }
            radialProgress.setAlphaForPrevious(false);
            return Theme.chat_fileStatesDrawable[buttonState + 5][buttonPressed ? 1 : 0];
        }
        return buttonState == 1 ? Theme.chat_photoStatesDrawables[5][0] : null;
    }

    public void updateButtonState(boolean animated) {
        String fileName = null;
        File cacheFile = null;
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
            if (documentAttach != null) {
                fileName = FileLoader.getAttachFileName(documentAttach);
                cacheFile = FileLoader.getPathToAttach(documentAttach);
            } else if (inlineResult.content instanceof TLRPC.TL_webDocument) {
                fileName = Utilities.MD5(inlineResult.content.url) + "." + ImageLoader.getHttpUrlExtension(inlineResult.content.url, documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC ? "mp3" : "ogg");
                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
            }
        } else if (mediaWebpage) {
            if (inlineResult != null) {
                if (inlineResult.document instanceof TLRPC.TL_document) {
                    fileName = FileLoader.getAttachFileName(inlineResult.document);
                    cacheFile = FileLoader.getPathToAttach(inlineResult.document);
                } else if (inlineResult.photo instanceof TLRPC.TL_photo) {
                    currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(inlineResult.photo.sizes, AndroidUtilities.getPhotoSize(), true);
                    fileName = FileLoader.getAttachFileName(currentPhotoObject);
                    cacheFile = FileLoader.getPathToAttach(currentPhotoObject);
                } else if (inlineResult.content instanceof TLRPC.TL_webDocument) {
                    fileName = Utilities.MD5(inlineResult.content.url) + "." + ImageLoader.getHttpUrlExtension(inlineResult.content.url, "jpg");
                    cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                } else if (inlineResult.thumb instanceof TLRPC.TL_webDocument) {
                    fileName = Utilities.MD5(inlineResult.thumb.url) + "." + ImageLoader.getHttpUrlExtension(inlineResult.thumb.url, "jpg");
                    cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                }
            } else if (documentAttach != null) {
                fileName = FileLoader.getAttachFileName(documentAttach);
                cacheFile = FileLoader.getPathToAttach(documentAttach);
            }
        }

        if (TextUtils.isEmpty(fileName)) {
            radialProgress.setBackground(null, false, false);
            return;
        }
        if (!cacheFile.exists()) {
            DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, this);
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
                boolean isLoading;
                if (documentAttach != null) {
                    isLoading = FileLoader.getInstance(currentAccount).isLoadingFile(fileName);
                } else {
                    isLoading = ImageLoader.getInstance().isLoadingHttpFile(fileName);
                }
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
            } else {
                buttonState = 1;
                Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                float setProgress = progress != null ? progress : 0;
                radialProgress.setProgress(setProgress, false);
                radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
            }
            invalidate();
        } else {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
                boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
                if (!playing || playing && MediaController.getInstance().isMessagePaused()) {
                    buttonState = 0;
                } else {
                    buttonState = 1;
                }
            } else {
                buttonState = -1;
            }
            radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            invalidate();
        }
    }

    public void setDelegate(ContextLinkCellDelegate contextLinkCellDelegate) {
        delegate = contextLinkCellDelegate;
    }

    public TLRPC.BotInlineResult getResult() {
        return inlineResult;
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

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }
}
