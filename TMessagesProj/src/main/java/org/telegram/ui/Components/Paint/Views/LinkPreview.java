package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLParseException;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Text;

public class LinkPreview extends View {

    private int currentAccount;
    public int maxWidth;
    private boolean relayout = true;
    private boolean animated;

    public final float density;
    public final int padx, pady;

    private float textScale = 1;
    private final TextPaint layoutPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout layout;
    private float layoutWidth, layoutLeft;

    private final RectF padding = new RectF(4, 4.33f, 7.66f, 3);
    private final float iconPadding = 3.25f;
    private final float flagIconPadding = 2.25f;
    private final float iconSize = 30f;

    private final Drawable icon;
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean messageAbove;
    private Text messageText;

    private Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean hasTitle;
    private Text titleText;
    private boolean hasSiteName;
    private Text siteNameText;

    private boolean hasDescription;
    private final TextPaint descriptionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout descriptionLayout;
    private float descriptionLayoutWidth, descriptionLayoutLeft;

    public boolean hasPhoto;
    private boolean smallPhoto;
    private final ImageReceiver photoImage = new ImageReceiver(this);

    public LinkPreview(Context context, float density) {
        super(context);
        this.density = density;
        photoImage.setInvalidateAll(true);

        padx = (int) (3 * density);
        pady = (int) (1 * density);

        icon = context.getResources().getDrawable(R.drawable.story_link).mutate();
        layoutPaint.setTextSize(24 * density);
        layoutPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf"));
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        this.relayout = true;
    }

    private boolean video;
    public void setVideoTexture() {
        this.video = true;
    }

    public int previewType;
    public int type, color;
    private WebPagePreview webpage;

    public float w, h;
    private float previewHeight;
    private float photoHeight;

    public void setupLayout() {
        if (!relayout || webpage == null) {
            return;
        }

        if (withPreview()) {
            final String text = TextUtils.isEmpty(webpage.name) ? fromUrl(webpage.url) : webpage.name;
            final TLRPC.WebPage preview = this.webpage.webpage;
            float maxWidth = this.maxWidth - padx - padx;

            h = 0;
            w = 0;
            previewHeight = 0;

            final TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
            final int colorId = UserObject.getColorId(user);
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
            MessagesController.PeerColor color = peerColors == null || colorId < 7 ? null : peerColors.getColor(colorId);
            previewPaint.setColor(color == null ? Theme.getColor(Theme.keys_avatar_nameInMessage[colorId % Theme.keys_avatar_nameInMessage.length]) : color.getColor1());

            h += 7.33f * density;
            messageAbove = webpage.captionAbove;
            messageText = new Text(text, 16).setTextSizePx(16 * density).setMaxWidth(maxWidth - 20 * density);
            w = Math.max(w, Math.min(messageText.getCurrentWidth() + 20 * density, maxWidth));
            h += messageText.getHeight();
            h += 7 * density;

            hasPhoto = preview.photo != null || MessageObject.isVideoDocument(preview.document);
            smallPhoto = !webpage.largePhoto;
            final int photoSide = video && (webpage.flags & 4) != 0 ? webpage.photoSize : (int) (smallPhoto ? 48 : maxWidth / density - 40) * 2;
            photoImage.setRoundRadius((int) (4 * density));
            int pw = 0, ph = 0;
            if (preview.photo != null) {
                TLRPC.PhotoSize thumbPhotoSize = FileLoader.getClosestPhotoSizeWithSize(preview.photo.sizes, 1, false, null, false);
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(preview.photo.sizes, (int) (photoSide * density), false, thumbPhotoSize, false);
                if (photoSize != null) {
                    pw = photoSize.w;
                    ph = photoSize.h;
                }
                photoImage.setImage(ImageLocation.getForPhoto(photoSize, preview.photo), photoSide + "_" + photoSide, video ? null : ImageLocation.getForPhoto(thumbPhotoSize, preview.photo), video ? null : photoSide + "_" + photoSide,0, null, null, 0);
            } else if (preview.document != null) {
                TLRPC.PhotoSize thumbPhotoSize = FileLoader.getClosestPhotoSizeWithSize(preview.document.thumbs, 1, false, null, false);
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(preview.document.thumbs, (int) (photoSide * density), false, thumbPhotoSize, false);
                if (photoSize != null) {
                    pw = photoSize.w;
                    ph = photoSize.h;
                }
                photoImage.setImage(ImageLocation.getForDocument(photoSize, preview.document), photoSide + "_" + photoSide, video ? null : ImageLocation.getForDocument(thumbPhotoSize, preview.document), video ? null : photoSide + "_" + photoSide,0, null, null, 0);
            }

            int lines = 0;
            previewHeight += 5.66f * density;
            hasSiteName = !TextUtils.isEmpty(preview.site_name);
            if (hasSiteName) {
                siteNameText = new Text(preview.site_name, 14, AndroidUtilities.bold()).setTextSizePx(14 * density).setMaxWidth((int) Math.ceil(maxWidth - 40 * density - (hasPhoto && smallPhoto ? (48 + 12) * density : 0)));
                w = Math.max(w, Math.min(siteNameText.getCurrentWidth() + 40 * density + (hasPhoto && smallPhoto ? (48 + 12) * density : 0), maxWidth));
                previewHeight += siteNameText.getHeight();
                previewHeight += 2.66f * density;
                lines += siteNameText.getLineCount();
            }

            hasTitle = !TextUtils.isEmpty(preview.title);
            if (hasTitle) {
                titleText = new Text(preview.title, 14, AndroidUtilities.bold()).setTextSizePx(14 * density).setMaxWidth((int) Math.ceil(maxWidth - 40 * density - (hasPhoto && smallPhoto ? (48 + 12) * density : 0)));
                w = Math.max(w, Math.min(titleText.getCurrentWidth() + 40 * density + (hasPhoto && smallPhoto ? (48 + 12) * density : 0), maxWidth));
                previewHeight += titleText.getHeight();
                previewHeight += 2.66f * density;
                lines += titleText.getLineCount();
            }

            hasDescription = !TextUtils.isEmpty(preview.description);
            if (hasDescription) {
                descriptionPaint.setTextSize(14 * density);
                descriptionLayout = ChatMessageCell.generateStaticLayout(preview.description, descriptionPaint, (int) Math.ceil(Math.max(1, maxWidth - 40 * density)), (int) Math.ceil(Math.max(1, maxWidth - (40 + (hasPhoto && smallPhoto ? 48 + 12 : 0)) * density)), 3 - lines, 4);
                descriptionLayoutWidth = 0;
                descriptionLayoutLeft = Float.MAX_VALUE;
                for (int i = 0; i < descriptionLayout.getLineCount(); ++i) {
                    boolean photoLine = hasPhoto && smallPhoto && i < (3 - lines);
                    descriptionLayoutWidth = Math.max(descriptionLayoutWidth, descriptionLayout.getLineWidth(i) + (photoLine ? 48 * density : 0));
                    descriptionLayoutLeft = Math.min(descriptionLayoutLeft, descriptionLayout.getLineLeft(i));
                }
                w = Math.max(w, Math.min(descriptionLayoutWidth + 40 * density, maxWidth));

                previewHeight += descriptionLayout.getHeight();
                previewHeight += 2.66f * density;
            }

            if (hasPhoto && !smallPhoto) {
                if (pw <= 0 || ph <= 0) {
                    photoHeight = 120 * density;
                } else {
                    final float photoWidth = Math.max(0, w - 40 * density);
                    photoHeight = Math.min(photoWidth / pw * ph, 200 * density);
                }
                previewHeight += photoHeight;
                previewHeight += 2.66f * density;
            }

            previewHeight += 7 * density;
            h += previewHeight;

            h += 11 * density;

        } else {
            final String text = TextUtils.isEmpty(webpage.name) ? fromUrlWithoutSchema(webpage.url).toUpperCase() : webpage.name;
            float maxWidth = this.maxWidth - padx - padx - (padding.left + iconSize + iconPadding + padding.right) * density;
            textScale = 1f;
            layout = new StaticLayout(TextUtils.ellipsize(text, layoutPaint, (int) Math.ceil(maxWidth), TextUtils.TruncateAt.END), layoutPaint, (int) Math.ceil(maxWidth), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);

            layoutWidth = 0;
            layoutLeft = Float.MAX_VALUE;
            for (int i = 0; i < layout.getLineCount(); ++i) {
                layoutWidth = Math.max(layoutWidth, layout.getLineWidth(i));
                layoutLeft = Math.min(layoutLeft, layout.getLineLeft(i));
            }
            if (layout.getLineCount() > 2) {
                textScale = .3f;
            } else {
                textScale = Math.min(1, maxWidth / layoutWidth);
            }

            w = (padding.left + iconSize + iconPadding + padding.right) * density + layoutWidth * textScale;
            h = (padding.top + padding.bottom) * density + Math.max(iconSize * density, layout.getHeight() * textScale);
        }

        if (!animated) {
            captionAbove.set(this.messageAbove, true);
            photoSmallProgress.set(this.smallPhoto, true);
            photoAlphaProgress.set(this.hasPhoto, true);
            previewHeightProgress.set(this.previewHeight, true);
        } else {
            invalidate();
        }

        relayout = false;
    }

    public void pushPhotoToCache() {
        if (!hasPhoto) return;
        if (!photoImage.hasImageLoaded() || photoImage.getBitmap() == null) return;
        ImageLoader.getInstance().putImageToCache(new BitmapDrawable(photoImage.getBitmap()), photoImage.getImageKey(), false);
    }

    public int getPhotoSide() {
        return (int) (smallPhoto ? 48 : (maxWidth - padx - padx) / density - 40) * 2;
    }

    public boolean withPreview() {
        return webpage != null && webpage.webpage != null;
    }

    public int backgroundColor;

    public void setType(int type, int color) {
        if (this.type == 1) return;
        if (type == 0) {
            backgroundColor = color;
            final int textColor = AndroidUtilities.computePerceivedBrightness(color) >= .721f ? Color.BLACK : Color.WHITE;
            layoutPaint.setColor(textColor);
            icon.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        } else if (type == 1) {
            backgroundColor = 0xFF000000;
            layoutPaint.setColor(0xFFFFFFFF);
            icon.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        } else if (type == 2) {
            backgroundColor = 0x4C000000;
            layoutPaint.setColor(0xFFFFFFFF);
            icon.setColorFilter(null);
        } else {
            backgroundColor = 0xFFFFFFFF;
            layoutPaint.setColor(0xFF3391D4);
            icon.setColorFilter(new PorterDuffColorFilter(0xFF3391D4, PorterDuff.Mode.SRC_IN));
        }
        invalidate();
    }

    public void setPreviewType(int type) {
        previewType = type;
        invalidate();
    }

    public int getPreviewType() {
        return previewType;
    }

    public void set(int currentAccount, WebPagePreview webpage) {
        set(currentAccount, webpage, false);
    }

    public void set(int currentAccount, WebPagePreview webpage, boolean animated) {
        this.currentAccount = currentAccount;
        if (this.webpage != webpage || animated) {
            this.webpage = webpage;
            this.relayout = true;
            this.animated = animated;
            this.requestLayout();
        }
    }

    public static String fromUrl(String url) {
        return url;
    }

    public static String fromUrlWithoutSchema(String url) {
        if (url.startsWith("https://")) {
            return url.substring(8);
        }
        return url;
    }


    private final RectF bounds = new RectF();
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Path path2 = new Path();

    private final RectF rect1 = new RectF();
    private final RectF rect2 = new RectF();

    private final AnimatedFloat captionAbove = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat photoAlphaProgress = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat photoSmallProgress = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat previewProgress = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat previewTheme = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat previewHeightProgress = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat width = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat height = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawInternal(canvas);
    }

    public float getRadius() {
        return withPreview() ? 16.66f * density : .2f * h;
    }

    public void drawInternal(Canvas canvas) {
        setupLayout();

        final float w = width.set(this.w);
        final float h = height.set(this.h);
        final float previewDark = previewTheme.set(previewType == 0);
        final float preview = previewProgress.set(withPreview());
        final float r = AndroidUtilities.lerp(.2f * h, 16.66f * density, preview);

        bounds.set(padx, pady, padx + w, pady + h);
        outlinePaint.setColor(ColorUtils.blendARGB(backgroundColor, ColorUtils.blendARGB(0xFFFFFFFF, 0xFF202429, previewDark), preview));

        path2.rewind();
        path2.addRoundRect(bounds, r, r, Path.Direction.CW);
        canvas.drawPath(path2, outlinePaint);

        if (preview > 0) {
            canvas.save();
            canvas.clipPath(path2);
            canvas.translate(padx, pady);

            final float above = captionAbove.set(this.messageAbove);

            float y = 0;
            y += 7.33f * density;
            if (messageText != null && above > 0) {
                messageText
                    .draw(canvas, 10 * density, y + messageText.getHeight() / 2f - (messageText.getHeight() + 15 * density) * (1f - above), 0xFF1A9CFF, preview);
                y += (messageText.getHeight() + 7 * density) * above;
            }

            final float previewHeight = previewHeightProgress.set(this.previewHeight);
            float linkY = y;
            previewPaint.setAlpha((int) (0xFF * .1f));
            rect.set(10 * density, linkY, w - 10 * density, linkY + previewHeight);
            path.rewind();
            path.addRoundRect(rect, 5 * density, 5 * density, Path.Direction.CW);
            canvas.drawPath(path, previewPaint);
            canvas.save();
            canvas.clipPath(path);
            previewPaint.setAlpha(0xFF);
            canvas.drawRect(10 * density, linkY, 13 * density, linkY + previewHeight, previewPaint);
            canvas.restore();

            y += 5.66f * density;
            if (hasSiteName && siteNameText != null) {
                siteNameText
                    .draw(canvas, 20 * density, y + siteNameText.getHeight() / 2f, previewPaint.getColor(), preview);
                y += siteNameText.getHeight() + 2.66f * density;
            }

            if (hasTitle && titleText != null) {
                titleText
                    .draw(canvas, 20 * density, y + titleText.getHeight() / 2f, ColorUtils.blendARGB(0xFF333333, 0xFFFFFFFF, previewDark), preview);
                y += titleText.getHeight() + 2.66f * density;
            }

            if (hasDescription && descriptionLayout != null) {
                canvas.save();
                canvas.translate(20 * density - descriptionLayoutLeft, y);
                descriptionPaint.setColor(ColorUtils.blendARGB(0xFF333333, 0xFFFFFFFF, previewDark));
                descriptionPaint.setAlpha((int) (0xFF * preview));
                descriptionLayout.draw(canvas);
                canvas.restore();
                y += descriptionLayout.getHeight() + 2.66f * density;
            }

            float photoAlpha = photoAlphaProgress.set(this.hasPhoto);
            if (photoAlpha > 0) {
                final float smallPhoto = photoSmallProgress.set(this.smallPhoto);
                rect1.set(20 * density, y + 2.66f * density, w - 20 * density, y + 2.66f * density + photoHeight);
                rect2.set(w - 10 * density - 6 * density - 48 * density, linkY + 6 * density, w - 10 * density - 6 * density, linkY + 6 * density + 48 * density);
                AndroidUtilities.lerp(rect1, rect2, smallPhoto, rect);
                photoImage.setImageCoords(rect.left, rect.top, rect.width(), rect.height());
                photoImage.setAlpha(photoAlpha * preview);
                photoImage.draw(canvas);
                y += (1f - smallPhoto) * (2.66f * density + photoHeight);
            }

            y += 7f * density;
            y += 5f * density;

            if (messageText != null && (1f - above) > 0) {
                messageText
                    .draw(canvas, 10 * density, y + messageText.getHeight() / 2f + (messageText.getHeight() + 15 * density) * above, 0xFF1A9CFF, preview);
                y += (messageText.getHeight() + 7 * density) * (1f - above);
            }

            canvas.restore();
        }

        if (preview < 1) {
            icon.setBounds(
                padx + (int) (padding.left * density),
                pady + (int) ((h - iconSize * density) / 2),
                padx + (int) ((padding.left + iconSize) * density),
                pady + (int) ((h + iconSize * density) / 2)
            );
            icon.setAlpha((int) (0xFF * (1f - preview)));
            icon.draw(canvas);

            if (layout != null) {
                canvas.save();
                canvas.translate(padx + (padding.left + iconSize + iconPadding) * density, pady + h / 2f);
                canvas.scale(textScale, textScale);
                canvas.translate(-layoutLeft, -layout.getHeight() / 2f);
                layoutPaint.setAlpha((int) (0xFF * (1f - preview)));
                layout.draw(canvas);
                canvas.restore();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setupLayout();
        setMeasuredDimension(padx + (int) Math.ceil(w) + padx, pady + (int) Math.ceil(h) + pady);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        photoImage.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        photoImage.onDetachedFromWindow();
    }

    public static class WebPagePreview extends TLObject {
        public static final int constructor = 0xDAB228AB;

        public int flags;

        public String name;
        public String url;
        public TLRPC.WebPage webpage;

        public boolean largePhoto;
        public boolean captionAbove = true;

        public int photoSize;

        public static WebPagePreview TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final WebPagePreview result = WebPagePreview.constructor != constructor ? null : new WebPagePreview();
            return TLdeserialize(WebPagePreview.class, result, stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = (webpage != null) ? flags | 1 : flags &~ 1;
            flags = !TextUtils.isEmpty(name) ? flags | 2 : flags &~ 2;
            flags = largePhoto ? flags | 8 : flags &~ 8;
            flags = captionAbove ? flags | 16 : flags &~ 16;
            stream.writeInt32(flags);
            stream.writeString(url);
            if ((flags & 1) != 0) {
                webpage.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeString(name);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(photoSize);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            largePhoto = (flags & 8) != 0;
            captionAbove = (flags & 16) != 0;
            url = stream.readString(exception);
            if ((flags & 1) != 0) {
                webpage = TLRPC.WebPage.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                photoSize = stream.readInt32(exception);
            }
        }

        public boolean isPreviewEmpty() {
            return webpage == null;
        }
    }
}
