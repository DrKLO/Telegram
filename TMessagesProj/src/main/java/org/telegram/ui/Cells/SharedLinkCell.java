/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ImageReceiver;
import org.telegram.android.MediaController;
import org.telegram.android.MessageObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.TLRPC;

import java.io.File;
import java.util.Locale;

public class SharedLinkCell extends View {

    private ImageReceiver linkImageView;
    private boolean drawLinkImageView;

    private boolean needDivider;

    private int linkX;
    private int linkY;
    private StaticLayout linkLayout;

    private int titleX;
    private int titleY;
    private StaticLayout titleLayout;

    private int descriptionX;
    private int descriptionY;
    private StaticLayout descriptionLayout;

    private MessageObject message;

    private static TextPaint titleTextPaint;
    private static TextPaint descriptionTextPaint;
    private static Paint paint;

    public SharedLinkCell(Context context) {
        super(context);

        if (titleTextPaint == null) {
            titleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            titleTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextPaint.setColor(0xff212121);
            titleTextPaint.setTextSize(AndroidUtilities.dp(16));

            descriptionTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            descriptionTextPaint.setTextSize(AndroidUtilities.dp(16));

            paint = new Paint();
            paint.setColor(0xffd9d9d9);
            paint.setStrokeWidth(1);
        }
        linkImageView = new ImageReceiver(this);
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        drawLinkImageView = false;
        descriptionLayout = null;
        titleLayout = null;
        linkLayout = null;
        descriptionX = 0;
        titleX = 0;
        linkX = 0;

        int maxWidth = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(32);

        String title = "";
        String description = "";
        String link = "";
        boolean hasPhoto = false;

        if (message.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && message.messageOwner.media.webpage instanceof TLRPC.TL_webPage) {
            TLRPC.TL_webPage webPage = (TLRPC.TL_webPage) message.messageOwner.media.webpage;
            if (message.photoThumbs == null && webPage.photo != null) {
                message.generateThumbs(true);
            }
            hasPhoto = webPage.photo != null && message.photoThumbs != null;
            title = webPage.title;
            if (title == null) {
                title = webPage.site_name;
            }
            description = webPage.description;
            link = webPage.url;
        }

        if (title != null) {
            try {
                int width = (int) Math.ceil(titleTextPaint.measureText(title));
                CharSequence titleFinal = TextUtils.ellipsize(title.replace("\n", " "), titleTextPaint, Math.min(width, maxWidth - (hasPhoto ? AndroidUtilities.dp(56) : 0)), TextUtils.TruncateAt.END);
                titleLayout = new StaticLayout(titleFinal, titleTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                titleX = (int) Math.ceil(-titleLayout.getLineLeft(0)) + AndroidUtilities.dp(16);
                titleY = AndroidUtilities.dp(8);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        if (description != null) {
            try {
                descriptionLayout = ChatMessageCell.generateStaticLayout(description, descriptionTextPaint, maxWidth, maxWidth - (hasPhoto ? AndroidUtilities.dp(56) : 0), 2, 6);
                int height = descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
                for (int a = 0; a < descriptionLayout.getLineCount(); a++) {
                    int lineLeft = (int) Math.ceil(descriptionLayout.getLineLeft(a));
                    if (descriptionX == 0) {
                        descriptionX = -lineLeft;
                    } else {
                        descriptionX = Math.max(descriptionX, -lineLeft);
                    }
                }
                descriptionX += AndroidUtilities.dp(16);
                descriptionY = AndroidUtilities.dp(28);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        if (link != null) {
            try {
                int width = (int) Math.ceil(descriptionTextPaint.measureText(link));
                CharSequence linkFinal = TextUtils.ellipsize(link.replace("\n", " "), descriptionTextPaint, Math.min(width, maxWidth), TextUtils.TruncateAt.END);
                linkLayout = new StaticLayout(linkFinal, descriptionTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                linkX = (int) Math.ceil(-linkLayout.getLineLeft(0)) + AndroidUtilities.dp(16);
                linkY = descriptionY;
                if (descriptionLayout != null && descriptionLayout.getLineCount() != 0) {
                    linkY += descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1) + AndroidUtilities.dp(1);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        if (hasPhoto) {
            int maxPhotoWidth = AndroidUtilities.dp(48);
            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, maxPhotoWidth, true);
            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 80);
            if (currentPhotoObjectThumb == currentPhotoObject) {
                currentPhotoObjectThumb = null;
            }
            currentPhotoObject.size = -1;
            if (currentPhotoObjectThumb != null) {
                currentPhotoObjectThumb.size = -1;
            }
            linkImageView.setImageCoords(maxWidth - AndroidUtilities.dp(32), AndroidUtilities.dp(8), maxPhotoWidth, maxPhotoWidth);
            String fileName = FileLoader.getAttachFileName(currentPhotoObject);
            boolean photoExist = true;
            File cacheFile = FileLoader.getPathToAttach(currentPhotoObject, true);
            if (!cacheFile.exists()) {
                photoExist = false;
            }
            String filter = String.format(Locale.US, "%d_%d", maxPhotoWidth, maxPhotoWidth);
            if (photoExist || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO) || FileLoader.getInstance().isLoadingFile(fileName)) {
                linkImageView.setImage(currentPhotoObject.location, filter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, String.format(Locale.US, "%d_%d_b", maxPhotoWidth, maxPhotoWidth), 0, null, false);
            } else {
                if (currentPhotoObjectThumb != null) {
                    linkImageView.setImage(null, null, currentPhotoObjectThumb.location, String.format(Locale.US, "%d_%d_b", maxPhotoWidth, maxPhotoWidth), 0, null, false);
                } else {
                    linkImageView.setImageBitmap((Drawable) null);
                }
            }
            drawLinkImageView = true;
        }

        int height = 0;
        if (titleLayout != null && titleLayout.getLineCount() != 0) {
            height += titleLayout.getLineBottom(titleLayout.getLineCount() - 1);
        }
        if (descriptionLayout != null && descriptionLayout.getLineCount() != 0) {
            height += descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
        }
        if (linkLayout != null && linkLayout.getLineCount() != 0) {
            height += linkLayout.getLineBottom(linkLayout.getLineCount() - 1);
        }
        if (hasPhoto) {
            height = Math.max(AndroidUtilities.dp(48), height);
        }

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.makeMeasureSpec(height + AndroidUtilities.dp(16) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setLink(MessageObject messageObject, boolean divider) {
        needDivider = divider;
        message = messageObject;

        requestLayout();
    }

    public MessageObject getMessage() {
        return message;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (titleLayout != null) {
            canvas.save();
            canvas.translate(titleX, titleY);
            titleLayout.draw(canvas);
            canvas.restore();
        }

        if (descriptionLayout != null) {
            descriptionTextPaint.setColor(0xff212121);
            canvas.save();
            canvas.translate(descriptionX, descriptionY);
            descriptionLayout.draw(canvas);
            canvas.restore();
        }

        if (linkLayout != null) {
            descriptionTextPaint.setColor(0xff316f9f);
            canvas.save();
            canvas.translate(linkX, linkY);
            linkLayout.draw(canvas);
            canvas.restore();
        }

        if (drawLinkImageView) {
            linkImageView.draw(canvas);
        }

        if (needDivider) {
            canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, paint);
        }
    }
}
