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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LetterDrawable;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.ResourceLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class ContextLinkCell extends View implements MediaController.FileDownloadProgressListener {

    public interface ContextLinkCellDelegate {
        void didPressedImage(ContextLinkCell cell);
    }

    private ImageReceiver linkImageView;
    private boolean drawLinkImageView;
    private LetterDrawable letterDrawable;

    private boolean needDivider;
    private boolean buttonPressed;

    private int linkY;
    private StaticLayout linkLayout;

    private int titleY = AndroidUtilities.dp(7);
    private StaticLayout titleLayout;

    private int descriptionY = AndroidUtilities.dp(27);
    private StaticLayout descriptionLayout;

    private TLRPC.BotInlineResult result;
    private TLRPC.Document gif;
    private boolean mediaWebpage;

    private static TextPaint titleTextPaint;
    private static TextPaint descriptionTextPaint;
    private static Paint paint;

    private int TAG;
    private int buttonState;
    private RadialProgress radialProgress;

    private ContextLinkCellDelegate delegate;

    public ContextLinkCell(Context context) {
        super(context);

        if (titleTextPaint == null) {
            titleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            titleTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextPaint.setColor(0xff212121);
            titleTextPaint.setTextSize(AndroidUtilities.dp(15));

            descriptionTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            descriptionTextPaint.setTextSize(AndroidUtilities.dp(13));

            paint = new Paint();
            paint.setColor(0xffd9d9d9);
            paint.setStrokeWidth(1);
        }

        setWillNotDraw(false);
        linkImageView = new ImageReceiver(this);
        letterDrawable = new LetterDrawable();
        radialProgress = new RadialProgress(this);
        TAG = MediaController.getInstance().generateObserverTag();
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        drawLinkImageView = false;
        descriptionLayout = null;
        titleLayout = null;
        linkLayout = null;
        TLRPC.PhotoSize currentPhotoObject = null;
        TLRPC.PhotoSize currentPhotoObjectThumb = null;
        ArrayList<TLRPC.PhotoSize> photoThumbs = null;
        String url = null;
        linkY = AndroidUtilities.dp(27);

        if (result == null && gif == null) {
            setMeasuredDimension(AndroidUtilities.dp(100), AndroidUtilities.dp(100));
            return;
        }

        int maxWidth = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - AndroidUtilities.dp(8);

        TLRPC.Document document = null;
        if (result != null && result.document != null) {
            document = result.document;
        } else if (gif != null) {
            document = gif;
        }
        if (document != null) {
            photoThumbs = new ArrayList<>();
            photoThumbs.add(document.thumb);
        } else if (result != null && result.photo != null) {
            photoThumbs = new ArrayList<>(result.photo.sizes);
        }

        if (!mediaWebpage && result != null) {
            if (result.title != null) {
                try {
                    int width = (int) Math.ceil(titleTextPaint.measureText(result.title));
                    CharSequence titleFinal = TextUtils.ellipsize(result.title.replace("\n", " "), titleTextPaint, Math.min(width, maxWidth), TextUtils.TruncateAt.END);
                    titleLayout = new StaticLayout(titleFinal, titleTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                letterDrawable.setTitle(result.title);
            }

            if (result.description != null) {
                try {
                    descriptionLayout = ChatMessageCell.generateStaticLayout(result.description, descriptionTextPaint, maxWidth, maxWidth, 0, 3);
                    if (descriptionLayout.getLineCount() > 0) {
                        linkY = descriptionY + descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1) + AndroidUtilities.dp(1);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }

            if (result.url != null) {
                try {
                    int width = (int) Math.ceil(descriptionTextPaint.measureText(result.url));
                    CharSequence linkFinal = TextUtils.ellipsize(result.url.replace("\n", " "), descriptionTextPaint, Math.min(width, maxWidth), TextUtils.TruncateAt.MIDDLE);
                    linkLayout = new StaticLayout(linkFinal, descriptionTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }

        boolean isGifDocument = false;
        if (document != null && MessageObject.isGifDocument(document)) {
            currentPhotoObject = document.thumb;
            isGifDocument = true;
        } else if (result != null && result.photo != null) {
            currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize(), true);
            currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, 80);
            if (currentPhotoObjectThumb == currentPhotoObject) {
                currentPhotoObjectThumb = null;
            }
        }
        if (result != null) {
            if (result.content_url != null) {
                if (result.type != null) {
                    if (result.type.startsWith("gif")) {
                        if (!isGifDocument) {
                            url = result.content_url;
                            isGifDocument = true;
                        }
                    } else if (result.type.equals("photo")) {
                        url = result.content_url;
                    }
                }
            }
            if (url == null && result.thumb_url != null) {
                url = result.thumb_url;
            }
        }

        int width = 1;
        int w = 0;
        int h = 0;

        if (document != null) {
            for (int b = 0; b < document.attributes.size(); b++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(b);
                if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    w = attribute.w;
                    h = attribute.h;
                    break;
                }
            }
        }
        if (w == 0 || h == 0) {
            if (currentPhotoObject != null) {
                currentPhotoObject.size = -1;
                if (currentPhotoObjectThumb != null) {
                    currentPhotoObjectThumb.size = -1;
                }
                w = currentPhotoObject.w;
                h = currentPhotoObject.h;
            } else if (result != null) {
                w = result.w;
                h = result.h;
            }
        }
        if (document != null || currentPhotoObject != null || url != null) {
            String currentPhotoFilter;
            String currentPhotoFilterThumb = "52_52_b";

            if (mediaWebpage) {
                width = (int) (w / (h / (float) AndroidUtilities.dp(80)));
                if (Build.VERSION.SDK_INT >= 11 && isGifDocument) {
                    currentPhotoFilterThumb = currentPhotoFilter = String.format(Locale.US, "%d_%d_b", (int) (width / AndroidUtilities.density), 80);
                } else {
                    currentPhotoFilter = String.format(Locale.US, "%d_%d", (int) (width / AndroidUtilities.density), 80);
                    currentPhotoFilterThumb = currentPhotoFilter + "_b";
                }
            } else {
                currentPhotoFilter = "52_52";
            }

            if (isGifDocument) {
                if (document != null && Build.VERSION.SDK_INT >= 11) {
                    linkImageView.setImage(document, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, document.size, null, false);
                } else {
                    linkImageView.setImage(null, url, null, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, -1, null, true);
                }
            } else {
                if (currentPhotoObject != null) {
                    linkImageView.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, 0, null, false);
                } else {
                    linkImageView.setImage(null, url, currentPhotoFilter, null, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, -1, null, true);
                }
            }
            drawLinkImageView = true;
        }

        if (mediaWebpage) {
            if (gif != null) {
                width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);
                setMeasuredDimension(width, height);
                if (needDivider) {
                    height -= AndroidUtilities.dp(2);
                }
                int x = (width - AndroidUtilities.dp(24)) / 2;
                int y = (height - AndroidUtilities.dp(24)) / 2;
                radialProgress.setProgressRect(x, y, x + AndroidUtilities.dp(24), y + AndroidUtilities.dp(24));
                linkImageView.setImageCoords(0, 0, width, height);
            } else {
                setMeasuredDimension(width + AndroidUtilities.dp(5), AndroidUtilities.dp(90));
                int x = AndroidUtilities.dp(5) + (width - AndroidUtilities.dp(24)) / 2;
                int y = (AndroidUtilities.dp(90) - AndroidUtilities.dp(24)) / 2;
                radialProgress.setProgressRect(x, y, x + AndroidUtilities.dp(24), y + AndroidUtilities.dp(24));
                linkImageView.setImageCoords(AndroidUtilities.dp(5), AndroidUtilities.dp(5), width, AndroidUtilities.dp(80));
            }
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
            letterDrawable.setBounds(x, AndroidUtilities.dp(8), x + maxPhotoWidth, AndroidUtilities.dp(62));
            linkImageView.setImageCoords(x, AndroidUtilities.dp(8), maxPhotoWidth, maxPhotoWidth);
        }
    }

    public void setLink(TLRPC.BotInlineResult contextResult, boolean media, boolean divider) {
        needDivider = divider;
        result = contextResult;
        mediaWebpage = media;
        requestLayout();
        updateButtonState(false);
    }

    public void setGif(TLRPC.Document document, boolean divider) {
        needDivider = divider;
        result = null;
        gif = document;
        mediaWebpage = true;
        requestLayout();
        updateButtonState(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (drawLinkImageView) {
            linkImageView.onDetachedFromWindow();
        }
        MediaController.getInstance().removeLoadingFileObserver(this);
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mediaWebpage || delegate == null || result == null) {
            return super.onTouchEvent(event);
        }
        int x = (int) event.getX();
        int y = (int) event.getY();

        boolean result = false;
        int side = AndroidUtilities.dp(48);
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
        if (!result) {
            result = super.onTouchEvent(event);
        }

        return result;
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
            descriptionTextPaint.setColor(0xff8a8a8a);
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), descriptionY);
            descriptionLayout.draw(canvas);
            canvas.restore();
        }

        if (linkLayout != null) {
            descriptionTextPaint.setColor(0xff316f9f);
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), linkY);
            linkLayout.draw(canvas);
            canvas.restore();
        }

        if (!mediaWebpage) {
            letterDrawable.draw(canvas);
        }
        if (drawLinkImageView) {
            linkImageView.draw(canvas);
        }
        if (mediaWebpage) {
            radialProgress.draw(canvas);
        }

        if (needDivider && !mediaWebpage) {
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, paint);
            } else {
                canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, paint);
            }
        }
    }

    private Drawable getDrawableForCurrentState() {
        return buttonState == 1 ? ResourceLoader.buttonStatesDrawables[6] : null;
    }

    public void updateButtonState(boolean animated) {
        if (!mediaWebpage || Build.VERSION.SDK_INT < 11) {
            return;
        }
        String fileName = null;
        File cacheFile = null;
        if (result != null) {
            if (result.photo instanceof TLRPC.TL_photo) {
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(result.photo.sizes, AndroidUtilities.getPhotoSize(), true);
                if (currentPhotoObject != null) {
                    currentPhotoObject.size = -1;
                }
                fileName = FileLoader.getAttachFileName(currentPhotoObject);
                cacheFile = FileLoader.getPathToAttach(currentPhotoObject);
            } else if (result.document instanceof TLRPC.TL_document) {
                fileName = FileLoader.getAttachFileName(result.document);
                cacheFile = FileLoader.getPathToAttach(result.document);
            } else if (result.content_url != null) {
                fileName = Utilities.MD5(result.content_url) + "." + ImageLoader.getHttpUrlExtension(result.content_url, "jpg");
                cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
            } else if (result.thumb_url != null) {
                fileName = Utilities.MD5(result.thumb_url) + "." + ImageLoader.getHttpUrlExtension(result.thumb_url, "jpg");
                cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
            }
        } else if (gif != null) {
            fileName = FileLoader.getAttachFileName(gif);
            cacheFile = FileLoader.getPathToAttach(gif);
        }
        if (fileName == null) {
            radialProgress.setBackground(null, false, false);
            return;
        }
        if (cacheFile.exists() && cacheFile.length() == 0) {
            cacheFile.delete();
        }
        if (!cacheFile.exists()) {
            MediaController.getInstance().addLoadingFileObserver(fileName, this);
            boolean progressVisible = true;
            buttonState = 1;
            Float progress = ImageLoader.getInstance().getFileProgress(fileName);
            float setProgress = progress != null ? progress : 0;
            radialProgress.setProgress(setProgress, false);
            radialProgress.setBackground(getDrawableForCurrentState(), progressVisible, animated);
            invalidate();
        } else {
            MediaController.getInstance().removeLoadingFileObserver(this);
            buttonState = -1;
            radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            invalidate();
        }
    }

    public void setDelegate(ContextLinkCellDelegate contextLinkCellDelegate) {
        delegate = contextLinkCellDelegate;
    }

    public TLRPC.BotInlineResult getResult() {
        return result;
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
        if (buttonState != 1) {
            updateButtonState(false);
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
