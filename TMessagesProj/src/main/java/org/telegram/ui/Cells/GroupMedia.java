package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_BOTTOM;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_LEFT;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_RIGHT;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_TOP;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;

import com.google.android.exoplayer2.offline.Download;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessageObject.GroupedMessagePosition;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BlurBehindDrawable;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.spoilers.SpoilerEffect2;
import org.telegram.ui.Stars.StarsIntroActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class GroupMedia {

    @NonNull
    public final ChatMessageCell cell;

    private GroupedMessages layout;
    public final ArrayList<MediaHolder> holders = new ArrayList<>();

    public int x, y;
    public int maxWidth;
    public int width;
    public int height;

    public boolean hidden;
    private final AnimatedFloat animatedHidden;
    private LoadingDrawable loadingDrawable;

    SpoilerEffect2 spoilerEffect;

    public GroupMedia(@NonNull ChatMessageCell cell) {
        this.cell = cell;
        this.spoilerEffect = SpoilerEffect2.getInstance(cell);
        this.animatedHidden = new AnimatedFloat(cell, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        this.bounce = new ButtonBounce(cell);
    }

    private int overrideWidth;
    public void setOverrideWidth(int overrideWidth) {
        this.overrideWidth = overrideWidth;
    }

    public void setMessageObject(MessageObject messageObject, boolean pinnedBottom, boolean pinnedTop) {
        if (messageObject == null) return;
        if (messageObject.messageOwner == null) return;
        if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPaidMedia)) return;

        final TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) messageObject.messageOwner.media;

        if (layout == null) {
            layout = new GroupedMessages();
        }
        layout.medias.clear();
        layout.medias.addAll(paidMedia.extended_media);
        layout.calculate();

        if (overrideWidth > 0) {
            maxWidth = overrideWidth;
        } else {
            if (AndroidUtilities.isTablet()) {
                maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122);
            } else {
                maxWidth = Math.min(cell.getParentWidth(), AndroidUtilities.displaySize.y) - AndroidUtilities.dp(64 + (cell.checkNeedDrawShareButton(messageObject) ? 10 : 0));
            }
            if (cell.needDrawAvatar()) {
                maxWidth -= dp(52);
            }
        }

        for (int i = 0; i < paidMedia.extended_media.size(); ++i) {
            final TLRPC.MessageExtendedMedia media = paidMedia.extended_media.get(i);
            MediaHolder holder = i >= holders.size() ? null : holders.get(i);
            if (holder == null) {
                final GroupedMessagePosition pos = layout.getPosition(media);
                final int w = (int) (pos.pw / 1000f * maxWidth);
                final int h = (int) (pos.ph * layout.maxSizeHeight);
                holder = new MediaHolder(cell, messageObject, media, paidMedia.extended_media.size() != 1, w, h);
                if (media.attachPath != null) {
                    holder.attachPath = media.attachPath;
                } else if (paidMedia.extended_media.size() == 1) {
                    holder.attachPath = messageObject.messageOwner != null ? messageObject.messageOwner.attachPath : null;
                }
                if (!TextUtils.isEmpty(holder.attachPath)) {
                    DownloadController.getInstance(cell.currentAccount).addLoadingFileObserver(holder.attachPath, messageObject, holder);
                    if (messageObject.isSending()) {
                        holder.radialProgress.setProgress(media.uploadProgress, false);
                    }
                }
                if (cell.isCellAttachedToWindow()) {
                    holder.attach();
                }
                holders.add(holder);
            } else {
                holder.updateMedia(media, messageObject);
            }
        }

        for (int i = paidMedia.extended_media.size(); i < holders.size(); ++i) {
            MediaHolder holder = i >= holders.size() ? null : holders.get(i);
            if (holder != null) {
                holder.detach();
                holders.remove(i);
                i--;
            }
        }

//        for (int i = 0; i < holders.size(); ++i) {
//            final MediaHolder holder = holders.get(i);
//            if (!paidMedia.extended_media.contains(holder.media)) {
//                holder.detach();
//                holders.remove(i);
//                i--;
//            }
//        }
//        for (int i = 0; i < paidMedia.extended_media.size(); ++i) {
//            final TLRPC.MessageExtendedMedia media = paidMedia.extended_media.get(i);
//            final GroupedMessagePosition pos = layout.getPosition(media);
//            boolean found = false;
//            for (int j = 0; j < holders.size(); ++j) {
//                if (holders.get(j).media == media) {
//                    found = true;
//                    break;
//                }
//            }
//            if (!found) {
//                final int w = (int) (pos.pw / 1000f * maxWidth);
//                final int h = (int) (pos.ph * layout.maxSizeHeight);
//                final MediaHolder holder = new MediaHolder(cell, messageObject, media, paidMedia.extended_media.size() != 1, w, h);
//                if (media.attachPath != null) {
//                    holder.attachPath = media.attachPath;
//                } else if (paidMedia.extended_media.size() == 1) {
//                    holder.attachPath = messageObject.messageOwner != null ? messageObject.messageOwner.attachPath : null;
//                }
//                if (!TextUtils.isEmpty(holder.attachPath)) {
//                    DownloadController.getInstance(cell.currentAccount).addLoadingFileObserver(holder.attachPath, messageObject, holder);
//                    if (messageObject.isSending()) {
//                        holder.radialProgress.setProgress(media.uploadProgress, false);
//                    }
//                }
//                if (cell.isCellAttachedToWindow()) {
//                    holder.attach();
//                }
//                holders.add(holder);
//            }
//        }

        updateHolders(messageObject);

        width = (int) (layout.width / 1000f * maxWidth);
        height = (int) (layout.height * layout.maxSizeHeight);

        if (hidden) {
            buttonText = new Text(StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatPluralStringComma("UnlockPaidContent", (int) (buttonTextPrice = paidMedia.stars_amount)), .7f), 14, AndroidUtilities.bold());
            if (buttonText.getCurrentWidth() > width - dp(30)) {
                buttonText = new Text(StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatPluralStringComma("UnlockPaidContentShort", (int) (buttonTextPrice = paidMedia.stars_amount)), .7f), 14, AndroidUtilities.bold());
            }
        }
        if (priceText == null || priceTextPrice != paidMedia.stars_amount) {
            priceText = new Text(StarsIntroActivity.replaceStars(LocaleController.formatPluralStringComma("PaidMediaPrice", (int) (priceTextPrice = paidMedia.stars_amount)), .9f), 12, AndroidUtilities.bold());
        }
    }

    public void updateHolders(MessageObject messageObject) {
        final boolean top = cell.namesOffset > 0 || cell.captionAbove && !TextUtils.isEmpty(messageObject.caption);
        final boolean bottom = !cell.captionAbove && !TextUtils.isEmpty(messageObject.caption) || !cell.reactionsLayoutInBubble.isEmpty || cell.hasCommentLayout();

        float f = 1f;
        if (overrideWidth > 0) {
            f = 1000f / layout.width;
            maxWidth = overrideWidth;
        } else {
            if (AndroidUtilities.isTablet()) {
                maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122);
            } else {
                maxWidth = Math.min(cell.getParentWidth(), AndroidUtilities.displaySize.y) - AndroidUtilities.dp(64 + (cell.checkNeedDrawShareButton(messageObject) ? 10 : 0));
            }
            if (cell.needDrawAvatar()) {
                maxWidth -= dp(52);
            }
        }

        width = (int) (layout.width / 1000f * f * maxWidth);
        height = (int) (layout.height * layout.maxSizeHeight);

        hidden = false;
        final int p = dp(1);
        final int minRad = dp(4);
        final int rad = dp(SharedConfig.bubbleRadius - (SharedConfig.bubbleRadius > 2 ? 2 : 0));
        final int nearRad = Math.min(dp(3), rad);
        for (int i = 0; i < holders.size(); ++i) {
            final MediaHolder holder = holders.get(i);
            final GroupedMessagePosition pos = layout.getPosition(holder.media);

            if (pos == null) continue;

            int l = (int) (pos.left / 1000f * f * maxWidth);
            int t = (int) (pos.top * layout.maxSizeHeight);
            int w = (int) (pos.pw / 1000f * f * maxWidth);
            int h = (int) (pos.ph * layout.maxSizeHeight);

            if ((pos.flags & POSITION_FLAG_LEFT)   == 0) { l += p; w -= p; }
            if ((pos.flags & POSITION_FLAG_TOP)    == 0) { t += p; h -= p; }
            if ((pos.flags & POSITION_FLAG_RIGHT)  == 0) { w -= p; }
            if ((pos.flags & POSITION_FLAG_BOTTOM) == 0) { h -= p; }

            holder.l = l;
            holder.t = t;
            holder.r = l + w;
            holder.b = t + h;
            holder.imageReceiver.setImageCoords(l, t, w, h);

            int tl, tr, bl, br;

            tl = (pos.flags & POSITION_FLAG_TOP) != 0 && (pos.flags & POSITION_FLAG_LEFT) != 0 && !top ? rad : minRad;
            tr = (pos.flags & POSITION_FLAG_TOP) != 0 && (pos.flags & POSITION_FLAG_RIGHT) != 0 && !top ? rad : minRad;
            bl = (pos.flags & POSITION_FLAG_BOTTOM) != 0 && (pos.flags & POSITION_FLAG_LEFT) != 0 && !bottom ? rad : minRad;
            br = (pos.flags & POSITION_FLAG_BOTTOM) != 0 && (pos.flags & POSITION_FLAG_RIGHT) != 0 && !bottom ? rad : minRad;

            if (!bottom) {
                if (messageObject.isOutOwner()) {
                    br = minRad;
                } else {
                    bl = minRad;
                }
            }
            if (!top && cell.pinnedTop) {
                if (messageObject.isOutOwner()) {
                    tr = nearRad;
                } else {
                    tl = nearRad;
                }
            }

            holder.imageReceiver.setRoundRadius(tl, tr, br, bl);

            holder.radii[0] = holder.radii[1] = tl;
            holder.radii[2] = holder.radii[3] = tr;
            holder.radii[4] = holder.radii[5] = br;
            holder.radii[6] = holder.radii[7] = bl;

            if (messageObject != null) {
                if (messageObject.isSending()) {
                    holder.setIcon(MediaActionDrawable.ICON_CANCEL);
                }
            }

            hidden = hidden || holder.hidden;
        }

        if (hidden) {
            final TLRPC.TL_messageMediaPaidMedia paidMedia = messageObject == null ? null : (TLRPC.TL_messageMediaPaidMedia) messageObject.messageOwner.media;
            if (paidMedia != null) {
                buttonText = new Text(StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatPluralStringComma("UnlockPaidContent", (int) (buttonTextPrice = paidMedia.stars_amount)), .7f), 14, AndroidUtilities.bold());
                if (buttonText.getCurrentWidth() > width - dp(30)) {
                    buttonText = new Text(StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatPluralStringComma("UnlockPaidContentShort", (int) (buttonTextPrice = paidMedia.stars_amount)), .7f), 14, AndroidUtilities.bold());
                }
            }
        }
    }

    private final ButtonBounce bounce;
    private MediaHolder pressHolder;
    private boolean pressButton;
    public boolean onTouchEvent(MotionEvent e) {
        final float ex = e.getX();
        final float ey = e.getY();
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            pressHolder = getHolderAt(ex, ey);
            pressButton = pressHolder != null && pressHolder.radialProgress.getIcon() != MediaActionDrawable.ICON_NONE && pressHolder.radialProgress.getProgressRect().contains(ex, ey);
        } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
            MediaHolder holder = getHolderAt(ex, ey);
            boolean pressedButton = holder != null && holder.radialProgress.getIcon() != MediaActionDrawable.ICON_NONE && holder.radialProgress.getProgressRect().contains(ex, ey);
            if (pressHolder != null && pressHolder == holder && cell.getDelegate() != null && e.getAction() == MotionEvent.ACTION_UP) {
                MessageObject messageObject = cell.getMessageObject();
                if (pressButton && pressedButton && holder.radialProgress.getIcon() == MediaActionDrawable.ICON_CANCEL && messageObject != null) {
                    if (messageObject.isSending()) {
                        SendMessagesHelper.getInstance(messageObject.currentAccount).cancelSendingMessage(messageObject);
                    } else {

                    }
                } else {
                    cell.getDelegate().didPressGroupImage(cell, pressHolder.imageReceiver, pressHolder.media, e.getX(), e.getY());
                }
            }
            pressButton = false;
            pressHolder = null;
        }
        bounce.setPressed(pressHolder != null);
        return pressHolder != null;
    }

    public MediaHolder getHolderAt(float x, float y) {
        for (int i = 0; i < holders.size(); ++i) {
            if (holders.get(i).imageReceiver.isInsideImage(x, y)) {
                return holders.get(i);
            }
        }
        return null;
    }

    public ImageReceiver getPhotoImage(int index) {
        if (layout == null) return null;
        if (index < 0 || index >= layout.medias.size()) return null;
        TLRPC.MessageExtendedMedia media = layout.medias.get(index);
        for (int i = 0; i < holders.size(); ++i) {
            if (holders.get(i).media == media)
                return holders.get(i).imageReceiver;
        }
        return null;
    }

    public ImageReceiver getPhotoImage(TLRPC.FileLocation location) {
        if (layout == null) return null;
        for (int i = 0; i < holders.size(); ++i) {
            final MediaHolder holder = holders.get(i);
            final TLRPC.MessageExtendedMedia extendedMedia = holder.media;
            if (extendedMedia instanceof TLRPC.TL_messageExtendedMedia) {
                TLRPC.MessageMedia media = ((TLRPC.TL_messageExtendedMedia) extendedMedia).media;
                if (media.photo != null) {
                    for (int j = 0; j < media.photo.sizes.size(); ++j) {
                        if (media.photo.sizes.get(j).location == location)
                            return holder.imageReceiver;
                    }
                    for (int j = 0; j < media.photo.video_sizes.size(); ++j) {
                        if (media.photo.video_sizes.get(j).location == location)
                            return holder.imageReceiver;
                    }
                }
                if (media.document != null) {
                    for (int j = 0; j < media.document.thumbs.size(); ++j) {
                        if (media.document.thumbs.get(j).location == location)
                            return holder.imageReceiver;
                    }
                    for (int j = 0; j < media.document.video_thumbs.size(); ++j) {
                        if (media.document.video_thumbs.get(j).location == location)
                            return holder.imageReceiver;
                    }
                }
            }
        }
        return null;
    }

    public boolean allVisible() {
        for (MediaHolder holder : holders) {
            if (!holder.imageReceiver.getVisible()) {
                return false;
            }
        }
        return true;
    }

    private Text buttonText;
    private long buttonTextPrice;
    private Text priceText;
    private long priceTextPrice;
    private Path clipPath = new Path();
    private Path clipPath2 = new Path();
    private RectF clipRect = new RectF();

    public void draw(Canvas canvas) {
        if (layout == null) return;
        float hiddenAlpha = animatedHidden.set(hidden);

//        Theme.MessageDrawable backgroundDrawable = cell.currentBackgroundDrawable;
//        if (backgroundDrawable != null && hiddenAlpha > 0) {
//            canvas.save();
//            canvas.clipRect(x - dp(20), y - dp(2), x + width + dp(20), y + height + dp(3));
//            canvas.clipPath(backgroundDrawable.makePath());
//            drawBlurred(canvas, hiddenAlpha);
//            canvas.drawColor(Theme.multAlpha(Theme.isCurrentThemeDark() ? 0x80000000 : 0x90FFFFFF, hiddenAlpha));
//            canvas.restore();
//        }

        drawImages(canvas, true);
        if (buttonText != null) {
            if (hiddenAlpha > 0) {
                final float s = bounce.getScale(.05f);
                final float buttonWidth = dp(14 + 14) + buttonText.getCurrentWidth();
                final float buttonHeight = dp(32);
                clipRect.set(
                    x + (width - buttonWidth) / 2f,
                    y + (height - buttonHeight) / 2f,
                    x + (width + buttonWidth) / 2f,
                    y + (height + buttonHeight) / 2f
                );
                clipPath.rewind();
                clipPath.addRoundRect(clipRect, buttonHeight / 2f, buttonHeight / 2f, Path.Direction.CW);
                canvas.save();
                canvas.scale(s, s, x + width / 2f, y + height / 2f);

                canvas.save();
                canvas.clipPath(clipPath);
                drawBlurred(canvas, hiddenAlpha);
                canvas.drawColor(Theme.multAlpha(0x50000000, hiddenAlpha));
                buttonText.draw(canvas, x + width / 2f - buttonWidth / 2f + dp(14), y + height / 2f, 0xFFFFFFFF, hiddenAlpha);
                canvas.restore();

                final boolean loading = isLoading();
                if (loading) {
                    if (loadingDrawable == null) {
                        loadingDrawable = new LoadingDrawable();
                        loadingDrawable.setCallback(cell);
                        loadingDrawable.setColors(
                                Theme.multAlpha(Color.WHITE, .1f),
                                Theme.multAlpha(Color.WHITE, .3f),
                                Theme.multAlpha(Color.WHITE, .35f),
                                Theme.multAlpha(Color.WHITE, .8f)
                        );
                        loadingDrawable.setAppearByGradient(true);
                        loadingDrawable.strokePaint.setStrokeWidth(AndroidUtilities.dpf2(1.25f));
                    } else if (loadingDrawable.isDisappeared() || loadingDrawable.isDisappearing()) {
                        loadingDrawable.reset();
                        loadingDrawable.resetDisappear();
                    }
                } else if (loadingDrawable != null && !loadingDrawable.isDisappearing() && !loadingDrawable.isDisappeared()) {
                    loadingDrawable.disappear();
                }

                if (loadingDrawable != null) {
                    loadingDrawable.setBounds(clipRect);
                    loadingDrawable.setRadiiDp(buttonHeight / 2f);
                    loadingDrawable.setAlpha((int) (hiddenAlpha * 0xFF));
                    loadingDrawable.draw(canvas);
                }

                canvas.restore();
            }
        }
        if (priceText != null && hiddenAlpha < 1 && allVisible()) {
            float alpha = (1f - hiddenAlpha) * cell.getTimeAlpha();
            final float w = dp(2 * 5.66f) + priceText.getCurrentWidth(), h = dp(17);
            final float p = dp(5);
            clipRect.set(x + width - w - p, y + p, x + width - p, y + p + h);
            clipPath.rewind();
            clipPath.addRoundRect(clipRect, h / 2f, h / 2f, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
//            drawBlurred(canvas, alpha);
            canvas.drawColor(Theme.multAlpha(0x40000000, alpha));
            priceText.draw(canvas, x + width - w - p + dp(5.66f), y + p + h / 2f, 0xFFFFFFFF, alpha);
            canvas.restore();
        }
    }

    public boolean isLoading() {
        return cell.getDelegate() != null && cell.getDelegate().isProgressLoading(cell, ChatActivity.PROGRESS_PAID_MEDIA);
    }

    public void drawBlurRect(Canvas canvas, RectF rect, float r, float alpha) {
        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);
//        drawBlurred(canvas, alpha);
        canvas.drawColor(0x40000000);
        canvas.restore();
    }

    private Bitmap blurBitmap;
    private Paint blurBitmapPaint;
    private int blurBitmapState;
    private int blurBitmapMessageId;
    private int blurBitmapWidth, blurBitmapHeight;

    public void checkBlurBitmap() {
        final int maxSize = 100;
        final int msg_id = cell.getMessageObject() != null ? cell.getMessageObject().getId() : 0;
        final int width =  (int) Math.max(1, this.width > this.height ? maxSize : (float) this.width / this.height * maxSize);
        final int height = (int) Math.max(1, this.height > this.width ? maxSize : (float) this.height / this.width * maxSize);
        int state = 0;
        for (int i = 0; i < holders.size(); ++i) {
            final MediaHolder h = holders.get(i);
            if (h.imageReceiver.hasImageSet() && h.imageReceiver.getBitmap() != null) state |= 1 << i;
        }
        if (blurBitmap == null || blurBitmapMessageId != msg_id || blurBitmapState != state || blurBitmapWidth != width || blurBitmapHeight != height) {
            blurBitmapState = state;
            blurBitmapMessageId = msg_id;
            blurBitmapWidth = width;
            blurBitmapHeight = height;

            if (blurBitmap != null) {
                blurBitmap.recycle();
            }
            blurBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(blurBitmap);
            canvas.scale((float) width / this.width, (float) width / this.width);
            for (int i = 0; i < holders.size(); ++i) {
                final MediaHolder h = holders.get(i);
                h.imageReceiver.setImageCoords(h.l, h.t, h.r - h.l, h.b - h.t);
                h.imageReceiver.draw(canvas);
            }
            Utilities.stackBlurBitmap(blurBitmap, 12);

            if (blurBitmapPaint == null) {
                blurBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                ColorMatrix colorMatrix = new ColorMatrix();
                colorMatrix.setSaturation(1.5f);
                blurBitmapPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            }
        }
    }

    public void drawBlurred(Canvas canvas, float alpha) {
        if (layout == null) return;
        checkBlurBitmap();
        if (blurBitmap != null) {
            canvas.save();
            canvas.translate(x, y);
            canvas.scale((float) this.width / blurBitmap.getWidth(), (float) this.width / blurBitmap.getWidth());
            blurBitmapPaint.setAlpha((int) (0xFF * alpha));
            canvas.drawBitmap(blurBitmap, 0, 0, blurBitmapPaint);
            canvas.restore();
        }
    }

    public void drawImages(Canvas canvas, boolean withSpoilers) {
        float hiddenAlpha = animatedHidden.set(hidden);
        final MessageObject messageObject = cell.getMessageObject();
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, b = Float.MIN_VALUE, r = Float.MIN_VALUE;
        clipPath2.rewind();
        for (int i = 0; i < holders.size(); ++i) {
            final MediaHolder h = holders.get(i);

            h.imageReceiver.setImageCoords(x + h.l, y + h.t, h.r - h.l, h.b - h.t);
            h.imageReceiver.draw(canvas);
            if (h.imageReceiver.getAnimation() != null) {
                int sec = Math.round(h.imageReceiver.getAnimation().currentTime / 1000f);
                h.setTime(sec);
            }
            if (hiddenAlpha > 0) {
                l = Math.min(x + h.l, l);
                t = Math.min(y + h.t, t);
                r = Math.max(x + h.r, r);
                b = Math.max(y + h.b, b);
                AndroidUtilities.rectTmp.set(x + h.l, y + h.t, x + h.r, y + h.b);
                clipPath2.addRoundRect(AndroidUtilities.rectTmp, h.radii, Path.Direction.CW);
            }

            h.radialProgress.setColorKeys(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
            h.radialProgress.setProgressRect(
                    h.imageReceiver.getImageX() + (h.imageReceiver.getImageWidth() / 2f - h.radialProgress.getRadius()),
                    h.imageReceiver.getImageY() + (h.imageReceiver.getImageHeight() / 2f - h.radialProgress.getRadius()),
                    h.imageReceiver.getImageX() + (h.imageReceiver.getImageWidth() / 2f + h.radialProgress.getRadius()),
                    h.imageReceiver.getImageY() + (h.imageReceiver.getImageHeight() / 2f + h.radialProgress.getRadius())
            );
            if (messageObject.isSending()) {
                SendMessagesHelper sendMessagesHelper = SendMessagesHelper.getInstance(messageObject.currentAccount);
                long[] progress = ImageLoader.getInstance().getFileProgressSizes(h.attachPath);
                final boolean sending = sendMessagesHelper.isSendingPaidMessage(messageObject.getId(), i);
                if (progress == null && sending) {
                    h.radialProgress.setProgress(1.0f, true);
                    h.setIcon(h.album ? MediaActionDrawable.ICON_CHECK : h.getDefaultIcon());
                }
            } else if (FileLoader.getInstance(messageObject.currentAccount).isLoadingFile(h.filename)) {
                h.setIcon(MediaActionDrawable.ICON_CANCEL);
            } else {
                h.setIcon(h.getDefaultIcon());
            }
            canvas.saveLayerAlpha(h.radialProgress.getProgressRect(), (int) (0xFF * (1f - hiddenAlpha)), Canvas.ALL_SAVE_FLAG);
            h.radialProgress.draw(canvas);
            canvas.restore();
        }
        if (hiddenAlpha > 0 && withSpoilers) {
            canvas.save();
            canvas.clipPath(clipPath2);
            canvas.translate(l, t);
            canvas.saveLayerAlpha(0, 0, (int) (r - l), (int) (b - t), (int) (0xFF * hiddenAlpha), Canvas.ALL_SAVE_FLAG);
            spoilerEffect.draw(canvas, cell, (int) (r - l), (int) (b - t), 1f, cell.drawingToBitmap);
            canvas.restore();
            canvas.restore();
            cell.invalidate();
        }
        for (int i = 0; i < holders.size(); ++i) {
            final MediaHolder h = holders.get(i);

            if (h.durationText != null) {
                float alpha = 1f;//cell.getTimeAlpha();
                final float bw = dp(2 * 5.7f) + h.durationText.getCurrentWidth(), bh = dp(17);
                final float p = dp(5);
                clipRect.set(x + h.l + p, y + h.t + p, x + h.l + p + bw, y + h.t + p + bh);
                if (priceText != null && clipRect.right > x + width - (dp(2 * 5.66f) + priceText.getCurrentWidth()) - p && clipRect.top <= y + p) {
                    continue;
                }
                clipPath.rewind();
                clipPath.addRoundRect(clipRect, bh / 2f, bh / 2f, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clipPath);
                drawBlurred(canvas, hiddenAlpha);
                canvas.drawColor(Theme.multAlpha(0x40000000, alpha));
                h.durationText.draw(canvas, x + h.l + p + dp(5.66f), y + h.t + p + bh / 2f, 0xFFFFFFFF, alpha);
                canvas.restore();
            }
        }
    }

    public static class MediaHolder implements DownloadController.FileDownloadProgressListener {

        public int l, t, r, b;

        public final ChatMessageCell cell;
        public final ImageReceiver imageReceiver;
        public boolean hidden;
        private final int w, h;

        public final float[] radii = new float[8];
        public final RectF clipRect = new RectF();
        public final Path clipPath = new Path();

        public String filename;

        public boolean album, video, autoplay;
        public TLRPC.MessageExtendedMedia media;
        public String attachPath;

        public final RadialProgress2 radialProgress;

        public int icon = MediaActionDrawable.ICON_NONE;

        private final int TAG;

        private int duration = 0;
        private int durationValue = 0;
        private Text durationText;

        public void setIcon(int icon) {
            if (icon != this.icon) {
                radialProgress.setIcon(this.icon = icon, true, true);
            }
        }

        public void setTime(int time) {
            if (video) return;
            final int newDurationValue = Math.max(0, duration - time);
            if (durationValue != newDurationValue) {
                durationText = new Text(AndroidUtilities.formatLongDuration(durationValue = newDurationValue), 12);
            }
        }

        public MediaHolder(
            ChatMessageCell cell,
            MessageObject msg,
            TLRPC.MessageExtendedMedia media,
            boolean album,
            int w, int h
        ) {
            this.cell = cell;
            this.album = album;
            this.video = false;
            if (media instanceof TLRPC.TL_messageExtendedMedia) {
                TLRPC.MessageMedia messageMedia = ((TLRPC.TL_messageExtendedMedia) media).media;
                video = messageMedia instanceof TLRPC.TL_messageMediaDocument && MessageObject.isVideoDocument(messageMedia.document);
                duration = (int) Math.max(1, Math.round(MessageObject.getDocumentDuration(messageMedia.document)));
            } else if (media instanceof TLRPC.TL_messageExtendedMediaPreview) {
                TLRPC.TL_messageExtendedMediaPreview p = (TLRPC.TL_messageExtendedMediaPreview) media;
                video = (p.flags & 4) != 0;
                duration = p.video_duration;
            }
            if (video) {
                durationText = new Text(AndroidUtilities.formatLongDuration(durationValue = duration), 12);
            }
            this.imageReceiver = new ImageReceiver(cell);
            this.imageReceiver.setColorFilter(null);
            this.w = w;
            this.h = h;

            this.TAG = DownloadController.getInstance(cell.currentAccount).generateObserverTag();

//            this.spoilerEffect = SpoilerEffect2.getInstance(cell);

            updateMedia(media, msg);

            this.radialProgress = new RadialProgress2(cell, cell.getResourcesProvider());
            this.radialProgress.setIcon(icon = getDefaultIcon(), false, false);
        }

        public void updateMedia(TLRPC.MessageExtendedMedia media, MessageObject msg) {
            if (this.media == media)
                return;
            this.media = media;

            autoplay = false;
            String filter = w + "_" + h;
            if (media instanceof TLRPC.TL_messageExtendedMediaPreview) {
                hidden = true;
                filename = null;

                TLRPC.TL_messageExtendedMediaPreview mediaPreview = (TLRPC.TL_messageExtendedMediaPreview) media;
                imageReceiver.setImage(ImageLocation.getForObject(mediaPreview.thumb, msg.messageOwner), filter + "_b2", null, null, msg, 0);

                ColorMatrix colorMatrix = new ColorMatrix();
                colorMatrix.setSaturation(1.4f);
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, -.1f);
                this.imageReceiver.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            } else if (media instanceof TLRPC.TL_messageExtendedMedia) {
                hidden = msg.isRepostPreview;
                if (hidden) {
                    filter += "_b3";
                }
                final int cacheType = 0;

                this.imageReceiver.setColorFilter(null);

                TLRPC.TL_messageExtendedMedia extMedia = (TLRPC.TL_messageExtendedMedia) media;
                TLRPC.MessageMedia messageMedia = extMedia.media;
                filename = MessageObject.getFileName(messageMedia);
                if (messageMedia instanceof TLRPC.TL_messageMediaPhoto) {
                    TLRPC.TL_messageMediaPhoto mediaPhoto = (TLRPC.TL_messageMediaPhoto) messageMedia;
                    TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(mediaPhoto.photo.sizes, AndroidUtilities.getPhotoSize(), true, null, true);
                    TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(mediaPhoto.photo.sizes, Math.min(w, h) / 100, false, photoSize, false);
                    ImageLocation photoLocation = ImageLocation.getForPhoto(photoSize, mediaPhoto.photo);
                    ImageLocation thumbLocation = ImageLocation.getForPhoto(thumbSize, mediaPhoto.photo);
                    imageReceiver.setImage(
                        photoLocation, filter,
                        thumbLocation, filter,
                        0, null,
                        msg, cacheType
                    );
                } else if (messageMedia instanceof TLRPC.TL_messageMediaDocument) {
                    TLRPC.TL_messageMediaDocument mediaDocument = (TLRPC.TL_messageMediaDocument) messageMedia;
                    autoplay = !hidden && !album && video && SharedConfig.isAutoplayVideo();
//                    if (!TextUtils.isEmpty(extMedia.attachPath)) {
//                        imageReceiver.setImage(ImageLocation.getForPath(extMedia.attachPath), filter + (autoplay ? "_g" : ""), null, null, msg, 0);
//                        return;
//                    }
                    if (!album && video) {
                        if (mediaDocument.document != null) {
                            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(mediaDocument.document.thumbs, AndroidUtilities.getPhotoSize(), true, null, true);
                            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(mediaDocument.document.thumbs, Math.min(w, h), false, photoSize, false);
                            ImageLocation mediaLocation = ImageLocation.getForDocument(mediaDocument.document);
                            ImageLocation photoLocation = ImageLocation.getForDocument(photoSize, mediaDocument.document);
                            ImageLocation thumbLocation = ImageLocation.getForDocument(thumbSize, mediaDocument.document);
                            imageReceiver.setImage(
                                autoplay ? mediaLocation : null, filter + (autoplay ? "_g" : ""),
                                photoLocation, filter,
                                thumbLocation, filter, null,
                                0, null,
                                msg, cacheType
                            );
                            return;
                        }
                    }
                    if (mediaDocument.document != null) {
                        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(mediaDocument.document.thumbs, AndroidUtilities.getPhotoSize(), true, null, true);
                        TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(mediaDocument.document.thumbs, Math.min(w, h), false, photoSize, false);
                        ImageLocation photoLocation = ImageLocation.getForDocument(photoSize, mediaDocument.document);
                        ImageLocation thumbLocation = ImageLocation.getForDocument(thumbSize, mediaDocument.document);
                        imageReceiver.setImage(
                            photoLocation, filter,
                            thumbLocation, filter,
                            0, null,
                            msg, cacheType
                        );
                    }
                }
            }
        }

        public boolean attached;

        public void attach() {
            if (attached) return;
            attached = true;
            imageReceiver.onAttachedToWindow();
//            if (spoilerEffect.destroyed) {
//                spoilerEffect = SpoilerEffect2.getInstance(cell);
//            } else {
//                spoilerEffect.attach(cell);
//            }
        }

        public void detach() {
            if (!attached) return;
            attached = false;
            imageReceiver.onDetachedFromWindow();
//            this.spoilerEffect.detach(cell);
        }

        @Override
        public void onFailedDownload(String fileName, boolean canceled) {

        }

        @Override
        public void onSuccessDownload(String fileName) {

        }

        @Override
        public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
            float progress = totalSize == 0 ? 0 : Math.min(1f, downloadSize / (float) totalSize);
            radialProgress.setProgress(media.downloadProgress = progress, true);
            setIcon(progress < 1f ? MediaActionDrawable.ICON_CANCEL : getDefaultIcon());
            cell.invalidate();
        }

        @Override
        public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {
            float progress = totalSize == 0 ? 0 : Math.min(1f, uploadedSize / (float) totalSize);
            radialProgress.setProgress(media.uploadProgress = progress, true);
            setIcon(progress < 1f ? MediaActionDrawable.ICON_CANCEL : (album ? MediaActionDrawable.ICON_CHECK : getDefaultIcon()));
            cell.invalidate();
        }

        private int getDefaultIcon() {
            return video && !autoplay ? MediaActionDrawable.ICON_PLAY : MediaActionDrawable.ICON_NONE;
        }

        @Override
        public int getObserverTag() {
            return TAG;
        }
    }

    public boolean attached;

    public void onAttachedToWindow() {
        if (attached) return;
        attached = true;
        if (spoilerEffect != null) {
            spoilerEffect.detach(cell);
        }
        for (int i = 0; i < holders.size(); ++i) {
            holders.get(i).attach();
        }
    }

    public void onDetachedFromWindow() {
        if (!attached) return;
        attached = false;
        if (spoilerEffect != null) {
            spoilerEffect.attach(cell);
        }
        for (int i = 0; i < holders.size(); ++i) {
            holders.get(i).detach();
        }
    }

    public static class GroupedMessages {

        public long groupId;
        public boolean hasSibling;
        public ArrayList<TLRPC.MessageExtendedMedia> medias = new ArrayList<>();
        public ArrayList<GroupedMessagePosition> posArray = new ArrayList<>();
        public HashMap<TLRPC.MessageExtendedMedia, GroupedMessagePosition> positions = new HashMap<>();

        int width, maxX, maxY;
        float height;

        public GroupedMessagePosition getPosition(TLRPC.MessageExtendedMedia media) {
            if (media == null) {
                return null;
            }
            return positions.get(media);
        }

        public int maxSizeWidth = 800;
        public float maxSizeHeight = 814;

        public final GroupedMessages.TransitionParams transitionParams = new GroupedMessages.TransitionParams();

        private static class MessageGroupedLayoutAttempt {

            public int[] lineCounts;
            public float[] heights;

            public MessageGroupedLayoutAttempt(int i1, int i2, float f1, float f2) {
                lineCounts = new int[]{i1, i2};
                heights = new float[]{f1, f2};
            }

            public MessageGroupedLayoutAttempt(int i1, int i2, int i3, float f1, float f2, float f3) {
                lineCounts = new int[]{i1, i2, i3};
                heights = new float[]{f1, f2, f3};
            }

            public MessageGroupedLayoutAttempt(int i1, int i2, int i3, int i4, float f1, float f2, float f3, float f4) {
                lineCounts = new int[]{i1, i2, i3, i4};
                heights = new float[]{f1, f2, f3, f4};
            }
        }

        private float multiHeight(float[] array, int start, int end) {
            float sum = 0;
            for (int a = start; a < end; a++) {
                sum += array[a];
            }
            return maxSizeWidth / sum;
        }

        public void calculate() {
            posArray.clear();
            positions.clear();

            maxX = 0;
            final int count = medias.size();
            if (count == 0) {
                width = 0;
                height = 0;
                maxY = 0;
                return;
            }
            maxSizeWidth = 800;
            int firstSpanAdditionalSize = 200;

            StringBuilder proportions = new StringBuilder();
            float averageAspectRatio = 1.0f;
            boolean isOut = false;
            boolean forceCalc = false;
            boolean needShare = false;
            hasSibling = false;

            for (int a = 0; a < count;) {
                final TLRPC.MessageExtendedMedia media = medias.get(a);

                final GroupedMessagePosition position = new GroupedMessagePosition();
                position.last = a == count - 1;
                if (media instanceof TLRPC.TL_messageExtendedMediaPreview) {
                    TLRPC.TL_messageExtendedMediaPreview m = (TLRPC.TL_messageExtendedMediaPreview) media;
                    position.photoWidth = m.w;
                    position.photoHeight = m.h;
                } else if (media instanceof TLRPC.TL_messageExtendedMedia) {
                    TLRPC.PhotoSize photoSize;
                    TLRPC.TL_messageExtendedMedia m = (TLRPC.TL_messageExtendedMedia) media;
                    if (m.media instanceof TLRPC.TL_messageMediaPhoto) {
                        TLRPC.TL_messageMediaPhoto photo = (TLRPC.TL_messageMediaPhoto) m.media;
                        photoSize = photo.photo == null ? null : FileLoader.getClosestPhotoSizeWithSize(photo.photo.sizes, AndroidUtilities.getPhotoSize());
                    } else if (m.media instanceof TLRPC.TL_messageMediaDocument) {
                        TLRPC.TL_messageMediaDocument doc = (TLRPC.TL_messageMediaDocument) m.media;
                        photoSize = doc.document == null ? null : FileLoader.getClosestPhotoSizeWithSize(doc.document.thumbs, AndroidUtilities.getPhotoSize());
                    } else {
                        photoSize = null;
                    }
                    position.photoWidth = photoSize == null ? 100 : photoSize.w;
                    position.photoHeight = photoSize == null ? 100 : photoSize.h;
                } else {
                    position.photoWidth = 100;
                    position.photoHeight = 100;
                }
                if (position.photoWidth <= 0 || position.photoHeight <= 0) {
                    position.photoWidth = 50;
                    position.photoHeight = 50;
                }
                position.aspectRatio = position.photoWidth / (float) position.photoHeight;

                if (position.aspectRatio > 1.2f) {
                    proportions.append("w");
                } else if (position.aspectRatio < 0.8f) {
                    proportions.append("n");
                } else {
                    proportions.append("q");
                }

                averageAspectRatio += position.aspectRatio;

                if (position.aspectRatio > 2.0f) {
                    forceCalc = true;
                }

                positions.put(media, position);
                posArray.add(position);

                a++;
            }

            if (needShare) {
                maxSizeWidth -= 50;
                firstSpanAdditionalSize += 50;
            }

            int minHeight = dp(120);
            int minWidth = (int) (dp(120) / (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) maxSizeWidth));
            int paddingsWidth = (int) (dp(40) / (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) maxSizeWidth));

            float maxAspectRatio = maxSizeWidth / maxSizeHeight;
            averageAspectRatio = averageAspectRatio / count;

            float minH = dp(100) / maxSizeHeight;

            if (count == 1) {
                GroupedMessagePosition position1 = posArray.get(0);
                float w, h;
                if (position1.aspectRatio >= 1) {
                    w = maxSizeWidth;
                    h = w / position1.aspectRatio / maxSizeWidth * maxSizeHeight;
                } else {
                    h = maxSizeHeight;
                    w = h * position1.aspectRatio / maxSizeHeight * maxSizeWidth;
                }
                position1.set(0, 0, 0, 0, (int) w, h / maxSizeHeight, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP | POSITION_FLAG_BOTTOM);
            } else if (!forceCalc && (count == 2 || count == 3 || count == 4)) {
                if (count == 2) {
                    GroupedMessagePosition position1 = posArray.get(0);
                    GroupedMessagePosition position2 = posArray.get(1);
                    String pString = proportions.toString();
                    if (pString.equals("ww") && averageAspectRatio > 1.4 * maxAspectRatio && position1.aspectRatio - position2.aspectRatio < 0.2) {
                        float height = Math.round(Math.min(maxSizeWidth / position1.aspectRatio, Math.min(maxSizeWidth / position2.aspectRatio, maxSizeHeight / 2.0f))) / maxSizeHeight;
                        position1.set(0, 0, 0, 0, maxSizeWidth, height, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);
                        position2.set(0, 0, 1, 1, maxSizeWidth, height, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                    } else if (pString.equals("ww") || pString.equals("qq")) {
                        int width = maxSizeWidth / 2;
                        float height = Math.round(Math.min(width / position1.aspectRatio, Math.min(width / position2.aspectRatio, maxSizeHeight))) / maxSizeHeight;
                        position1.set(0, 0, 0, 0, width, height, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                        position2.set(1, 1, 0, 0, width, height, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                        maxX = 1;
                    } else {
                        int secondWidth = (int) Math.max(0.4f * maxSizeWidth, Math.round((maxSizeWidth / position1.aspectRatio / (1.0f / position1.aspectRatio + 1.0f / position2.aspectRatio))));
                        int firstWidth = maxSizeWidth - secondWidth;
                        if (firstWidth < minWidth) {
                            int diff = minWidth - firstWidth;
                            firstWidth = minWidth;
                            secondWidth -= diff;
                        }

                        float height = Math.min(maxSizeHeight, Math.round(Math.min(firstWidth / position1.aspectRatio, secondWidth / position2.aspectRatio))) / maxSizeHeight;
                        position1.set(0, 0, 0, 0, firstWidth, height, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                        position2.set(1, 1, 0, 0, secondWidth, height, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                        maxX = 1;
                    }
                } else if (count == 3) {
                    GroupedMessagePosition position1 = posArray.get(0);
                    GroupedMessagePosition position2 = posArray.get(1);
                    GroupedMessagePosition position3 = posArray.get(2);
                    if (proportions.charAt(0) == 'n') {
                        float thirdHeight = Math.min(maxSizeHeight * 0.5f, Math.round(position2.aspectRatio * maxSizeWidth / (position3.aspectRatio + position2.aspectRatio)));
                        float secondHeight = maxSizeHeight - thirdHeight;
                        int rightWidth = (int) Math.max(minWidth, Math.min(maxSizeWidth * 0.5f, Math.round(Math.min(thirdHeight * position3.aspectRatio, secondHeight * position2.aspectRatio))));

                        int leftWidth = Math.round(Math.min(maxSizeHeight * position1.aspectRatio + paddingsWidth, maxSizeWidth - rightWidth));
                        position1.set(0, 0, 0, 1, leftWidth, 1.0f, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);

                        position2.set(1, 1, 0, 0, rightWidth, secondHeight / maxSizeHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                        position3.set(1, 1, 1, 1, rightWidth, thirdHeight / maxSizeHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        position3.spanSize = maxSizeWidth;

                        position1.siblingHeights = new float[]{thirdHeight / maxSizeHeight, secondHeight / maxSizeHeight};

                        if (isOut) {
                            position1.spanSize = maxSizeWidth - rightWidth;
                        } else {
                            position2.spanSize = maxSizeWidth - leftWidth;
                            position3.leftSpanOffset = leftWidth;
                        }
                        hasSibling = true;
                        maxX = 1;
                    } else {
                        float firstHeight = Math.round(Math.min(maxSizeWidth / position1.aspectRatio, (maxSizeHeight) * 0.66f)) / maxSizeHeight;
                        position1.set(0, 1, 0, 0, maxSizeWidth, firstHeight, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                        int width = maxSizeWidth / 2;
                        float secondHeight = Math.min(maxSizeHeight - firstHeight, Math.round(Math.min(width / position2.aspectRatio, width / position3.aspectRatio))) / maxSizeHeight;
                        if (secondHeight < minH) {
                            secondHeight = minH;
                        }
                        position2.set(0, 0, 1, 1, width, secondHeight, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM);
                        position3.set(1, 1, 1, 1, width, secondHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        maxX = 1;
                    }
                } else {
                    GroupedMessagePosition position1 = posArray.get(0);
                    GroupedMessagePosition position2 = posArray.get(1);
                    GroupedMessagePosition position3 = posArray.get(2);
                    GroupedMessagePosition position4 = posArray.get(3);
                    if (proportions.charAt(0) == 'w') {
                        float h0 = Math.round(Math.min(maxSizeWidth / position1.aspectRatio, maxSizeHeight * 0.66f)) / maxSizeHeight;
                        position1.set(0, 2, 0, 0, maxSizeWidth, h0, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                        float h = Math.round(maxSizeWidth / (position2.aspectRatio + position3.aspectRatio + position4.aspectRatio));
                        int w0 = (int) Math.max(minWidth, Math.min(maxSizeWidth * 0.4f, h * position2.aspectRatio));
                        int w2 = (int) Math.max(Math.max(minWidth, maxSizeWidth * 0.33f), h * position4.aspectRatio);
                        int w1 = maxSizeWidth - w0 - w2;
                        if (w1 < dp(58)) {
                            int diff = dp(58) - w1;
                            w1 = dp(58);
                            w0 -= diff / 2;
                            w2 -= (diff - diff / 2);
                        }
                        h = Math.min(maxSizeHeight - h0, h);
                        h /= maxSizeHeight;
                        if (h < minH) {
                            h = minH;
                        }
                        position2.set(0, 0, 1, 1, w0, h, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM);
                        position3.set(1, 1, 1, 1, w1, h, POSITION_FLAG_BOTTOM);
                        position4.set(2, 2, 1, 1, w2, h, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        maxX = 2;
                    } else {
                        int w = Math.max(minWidth, Math.round(maxSizeHeight / (1.0f / position2.aspectRatio + 1.0f / position3.aspectRatio + 1.0f / position4.aspectRatio)));
                        float h0 = Math.min(0.33f, Math.max(minHeight, w / position2.aspectRatio) / maxSizeHeight);
                        float h1 = Math.min(0.33f, Math.max(minHeight, w / position3.aspectRatio) / maxSizeHeight);
                        float h2 = 1.0f - h0 - h1;
                        int w0 = Math.round(Math.min(maxSizeHeight * position1.aspectRatio + paddingsWidth, maxSizeWidth - w));

                        position1.set(0, 0, 0, 2, w0, h0 + h1 + h2, POSITION_FLAG_LEFT | POSITION_FLAG_TOP | POSITION_FLAG_BOTTOM);

                        position2.set(1, 1, 0, 0, w, h0, POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                        position3.set(1, 1, 1, 1, w, h1, POSITION_FLAG_RIGHT);
                        position3.spanSize = maxSizeWidth;

                        position4.set(1, 1, 2, 2, w, h2, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        position4.spanSize = maxSizeWidth;

                        if (isOut) {
                            position1.spanSize = maxSizeWidth - w;
                        } else {
                            position2.spanSize = maxSizeWidth - w0;
                            position3.leftSpanOffset = w0;
                            position4.leftSpanOffset = w0;
                        }
                        position1.siblingHeights = new float[]{h0, h1, h2};
                        hasSibling = true;
                        maxX = 1;
                    }
                }
            } else {
                float[] croppedRatios = new float[posArray.size()];
                for (int a = 0; a < count; a++) {
                    if (averageAspectRatio > 1.1f) {
                        croppedRatios[a] = Math.max(1.0f, posArray.get(a).aspectRatio);
                    } else {
                        croppedRatios[a] = Math.min(1.0f, posArray.get(a).aspectRatio);
                    }
                    croppedRatios[a] = Math.max(0.66667f, Math.min(1.7f, croppedRatios[a]));
                }

                int firstLine;
                int secondLine;
                int thirdLine;
                int fourthLine;
                ArrayList<GroupedMessages.MessageGroupedLayoutAttempt> attempts = new ArrayList<>();
                for (firstLine = 1; firstLine < croppedRatios.length; firstLine++) {
                    secondLine = croppedRatios.length - firstLine;
                    if (firstLine > 3 || secondLine > 3) {
                        continue;
                    }
                    attempts.add(new GroupedMessages.MessageGroupedLayoutAttempt(firstLine, secondLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, croppedRatios.length)));
                }

                for (firstLine = 1; firstLine < croppedRatios.length - 1; firstLine++) {
                    for (secondLine = 1; secondLine < croppedRatios.length - firstLine; secondLine++) {
                        thirdLine = croppedRatios.length - firstLine - secondLine;
                        if (firstLine > 3 || secondLine > (averageAspectRatio < 0.85f ? 4 : 3) || thirdLine > 3) {
                            continue;
                        }
                        attempts.add(new GroupedMessages.MessageGroupedLayoutAttempt(firstLine, secondLine, thirdLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, firstLine + secondLine), multiHeight(croppedRatios, firstLine + secondLine, croppedRatios.length)));
                    }
                }

                for (firstLine = 1; firstLine < croppedRatios.length - 2; firstLine++) {
                    for (secondLine = 1; secondLine < croppedRatios.length - firstLine; secondLine++) {
                        for (thirdLine = 1; thirdLine < croppedRatios.length - firstLine - secondLine; thirdLine++) {
                            fourthLine = croppedRatios.length - firstLine - secondLine - thirdLine;
                            if (firstLine > 3 || secondLine > 3 || thirdLine > 3 || fourthLine > 3) {
                                continue;
                            }
                            attempts.add(new GroupedMessages.MessageGroupedLayoutAttempt(firstLine, secondLine, thirdLine, fourthLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, firstLine + secondLine), multiHeight(croppedRatios, firstLine + secondLine, firstLine + secondLine + thirdLine), multiHeight(croppedRatios, firstLine + secondLine + thirdLine, croppedRatios.length)));
                        }
                    }
                }

                GroupedMessages.MessageGroupedLayoutAttempt optimal = null;
                float optimalDiff = 0.0f;
                float maxHeight = maxSizeWidth / 3 * 4;
                for (int a = 0; a < attempts.size(); a++) {
                    GroupedMessages.MessageGroupedLayoutAttempt attempt = attempts.get(a);
                    float height = 0;
                    float minLineHeight = Float.MAX_VALUE;
                    for (int b = 0; b < attempt.heights.length; b++) {
                        height += attempt.heights[b];
                        if (attempt.heights[b] < minLineHeight) {
                            minLineHeight = attempt.heights[b];
                        }
                    }

                    float diff = Math.abs(height - maxHeight);
                    if (attempt.lineCounts.length > 1) {
                        if (attempt.lineCounts[0] > attempt.lineCounts[1] || (attempt.lineCounts.length > 2 && attempt.lineCounts[1] > attempt.lineCounts[2]) || (attempt.lineCounts.length > 3 && attempt.lineCounts[2] > attempt.lineCounts[3])) {
                            diff *= 1.2f;
                        }
                    }

                    if (minLineHeight < minWidth) {
                        diff *= 1.5f;
                    }

                    if (optimal == null || diff < optimalDiff) {
                        optimal = attempt;
                        optimalDiff = diff;
                    }
                }
                if (optimal == null) {
                    return;
                }

                int index = 0;
                float y = 0.0f;

                for (int i = 0; i < optimal.lineCounts.length; i++) {
                    int c = optimal.lineCounts[i];
                    float lineHeight = optimal.heights[i];
                    int spanLeft = maxSizeWidth;
                    GroupedMessagePosition posToFix = null;
                    maxX = Math.max(maxX, c - 1);
                    for (int k = 0; k < c; k++) {
                        float ratio = croppedRatios[index];
                        int width = (int) (ratio * lineHeight);
                        spanLeft -= width;
                        GroupedMessagePosition pos = posArray.get(index);
                        int flags = 0;
                        if (i == 0) {
                            flags |= POSITION_FLAG_TOP;
                        }
                        if (i == optimal.lineCounts.length - 1) {
                            flags |= POSITION_FLAG_BOTTOM;
                        }
                        if (k == 0) {
                            flags |= POSITION_FLAG_LEFT;
                            if (isOut) {
                                posToFix = pos;
                            }
                        }
                        if (k == c - 1) {
                            flags |= POSITION_FLAG_RIGHT;
                            if (!isOut) {
                                posToFix = pos;
                            }
                        }
                        pos.set(k, k, i, i, width, Math.max(minH, lineHeight / maxSizeHeight), flags);
                        index++;
                    }
                    posToFix.pw += spanLeft;
                    posToFix.spanSize += spanLeft;
                    y += lineHeight;
                }
            }
            int avatarOffset = 108;
            for (int a = 0; a < count; a++) {
                GroupedMessagePosition pos = posArray.get(a);
                if (isOut) {
                    if (pos.minX == 0) {
                        pos.spanSize += firstSpanAdditionalSize;
                    }
                    if ((pos.flags & POSITION_FLAG_RIGHT) != 0) {
                        pos.edge = true;
                    }
                } else {
                    if (pos.maxX == maxX || (pos.flags & POSITION_FLAG_RIGHT) != 0) {
                        pos.spanSize += firstSpanAdditionalSize;
                    }
                    if ((pos.flags & POSITION_FLAG_LEFT) != 0) {
                        pos.edge = true;
                    }
                }
                TLRPC.MessageExtendedMedia media = medias.get(a);
                if (!isOut && true /* media.needDrawAvatarInternal()*/) {
                    if (pos.edge) {
                        if (pos.spanSize != 1000) {
                            pos.spanSize += avatarOffset;
                        }
                        pos.pw += avatarOffset;
                    } else if ((pos.flags & POSITION_FLAG_RIGHT) != 0) {
                        if (pos.spanSize != 1000) {
                            pos.spanSize -= avatarOffset;
                        } else if (pos.leftSpanOffset != 0) {
                            pos.leftSpanOffset += avatarOffset;
                        }
                    }
                }
            }
            for (int a = 0; a < count; a++) {
                MessageObject.GroupedMessagePosition pos = posArray.get(a);
                if (pos.minX == 0) {
                    pos.spanSize += firstSpanAdditionalSize;
                }
                if ((pos.flags & POSITION_FLAG_RIGHT) != 0) {
                    pos.edge = true;
                }
                maxX = Math.max(maxX, pos.maxX);
                maxY = Math.max(maxY, pos.maxY);
                pos.left = getLeft(pos, pos.minY, pos.maxY, pos.minX);
            }
            for (int a = 0; a < count; ++a) {
                MessageObject.GroupedMessagePosition pos = posArray.get(a);
                pos.top = getTop(pos, pos.minY);
            }

            width = getWidth();
            height = getHeight();
        }

        public int getWidth() {
            int[] lineWidths = new int[10];
            Arrays.fill(lineWidths, 0);
            final int count = posArray.size();
            for (int i = 0; i < count; ++i) {
                MessageObject.GroupedMessagePosition pos = posArray.get(i);
                int width = pos.pw;
                for (int y = pos.minY; y <= pos.maxY; ++y) {
                    lineWidths[y] += width;
                }
            }
            int width = lineWidths[0];
            for (int y = 1; y < lineWidths.length; ++y) {
                if (width < lineWidths[y]) {
                    width = lineWidths[y];
                }
            }
            return width;
        }

        public float getHeight() {
            float[] lineHeights = new float[10];
            Arrays.fill(lineHeights, 0f);
            final int count = posArray.size();
            for (int i = 0; i < count; ++i) {
                MessageObject.GroupedMessagePosition pos = posArray.get(i);
                float height = pos.ph;
                for (int x = pos.minX; x <= pos.maxX; ++x) {
                    lineHeights[x] += height;
                }
            }
            float height = lineHeights[0];
            for (int y = 1; y < lineHeights.length; ++y) {
                if (height < lineHeights[y]) {
                    height = lineHeights[y];
                }
            }
            return height;
        }

        private float getLeft(MessageObject.GroupedMessagePosition except, int minY, int maxY, int minX) {
            float[] sums = new float[maxY - minY + 1];
            Arrays.fill(sums, 0f);
            final int count = posArray.size();
            for (int i = 0; i < count; ++i) {
                MessageObject.GroupedMessagePosition pos = posArray.get(i);
                if (pos != except && pos.maxX < minX) {
                    final int end = Math.min(pos.maxY, maxY) - minY;
                    for (int y = Math.max(pos.minY - minY, 0); y <= end; ++y) {
                        sums[y] += pos.pw;
                    }
                }
            }
            float max = 0;
            for (int i = 0; i < sums.length; ++i) {
                if (max < sums[i]) {
                    max = sums[i];
                }
            }
            return max;
        }

        private float getTop(MessageObject.GroupedMessagePosition except, int minY) {
            float[] sums = new float[maxX + 1];
            Arrays.fill(sums, 0f);
            final int count = posArray.size();
            for (int i = 0; i < count; ++i) {
                MessageObject.GroupedMessagePosition pos = posArray.get(i);
                if (pos != except && pos.maxY < minY) {
                    for (int x = pos.minX; x <= pos.maxX; ++x) {
                        sums[x] += pos.ph;
                    }
                }
            }
            float max = 0;
            for (int i = 0; i < sums.length; ++i) {
                if (max < sums[i]) {
                    max = sums[i];
                }
            }
            return max;
        }

        public TLRPC.MessageExtendedMedia findMediaWithFlags(int flags) {
            if (!medias.isEmpty() && positions.isEmpty()) {
                calculate();
            }
            for (int i = 0; i < medias.size(); i++) {
                TLRPC.MessageExtendedMedia media = medias.get(i);
                GroupedMessagePosition position = positions.get(media);
                if (position != null && (position.flags & (flags)) == flags) {
                    return media;
                }
            }
            return null;
        }

        public static class TransitionParams {
            public int left;
            public int top;
            public int right;
            public int bottom;

            public float offsetLeft;
            public float offsetTop;
            public float offsetRight;
            public float offsetBottom;

            public boolean drawBackgroundForDeletedItems;
            public boolean backgroundChangeBounds;

            public boolean pinnedTop;
            public boolean pinnedBotton;

            public ChatMessageCell cell;
            public float captionEnterProgress = 1f;
            public boolean drawCaptionLayout;
            public boolean isNewGroup;

            public void reset() {
                captionEnterProgress = 1f;
                offsetBottom = 0;
                offsetTop = 0;
                offsetRight = 0;
                offsetLeft = 0;
                backgroundChangeBounds = false;
            }
        }
    }

}
