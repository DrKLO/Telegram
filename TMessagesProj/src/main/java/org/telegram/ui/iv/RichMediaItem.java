package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.spoilers.SpoilerEffect2;

import java.util.ArrayList;

public class RichMediaItem {

    private final View parent;
    private final ImageReceiver imageReceiver;
    private final ImageReceiver blurImageReceiver;
    private Bitmap blurSource;
    private static ColorMatrixColorFilter fancyBlurFilter;
    private final RadialProgress2 radialProgress;

    private MediaUploadState media;
    private boolean attached;
    private String loadedKey;

    public RichMediaItem(View parent, Theme.ResourcesProvider resourcesProvider) {
        this.parent = parent;
        imageReceiver = new ImageReceiver(parent);
        blurImageReceiver = new ImageReceiver(parent);
        radialProgress = new RadialProgress2(parent, resourcesProvider);
        radialProgress.setProgressColor(0xffffffff);
        radialProgress.setColors(0x66000000, 0x7f000000, 0xffffffff, 0xffd9d9d9);
        radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, false, false);
    }

    public void setMedia(MediaUploadState media) {
        this.media = media;
        applyImage();
    }

    public MediaUploadState getMedia() {
        return media;
    }

    public boolean isEmpty() {
        return media == null || (media.localPath == null && !media.isReady());
    }

    public boolean hasImage() {
        return media != null && (media.localPath != null || media.isReady());
    }

    public int getWidth() {
        if (media == null) return 0;
        return isLocalRotated90() ? media.height : media.width;
    }

    public int getHeight() {
        if (media == null) return 0;
        return isLocalRotated90() ? media.width : media.height;
    }

    private boolean isLocalRotated90() {
        return media != null && !media.isVideo && !media.isReady()
            && (media.orientation == 90 || media.orientation == 270);
    }

    public void attach() {
        attached = true;
        imageReceiver.onAttachedToWindow();
        blurImageReceiver.onAttachedToWindow();
        loadedKey = null;
        applyImage();
    }

    private String imageKey() {
        if (media == null) return "null";
        final String type = media.isVideo ? "v" : (media.isAudio ? "a" : "p");
        // While the local file is available we always render from it, so keep the key stable
        // across the upload->done transition; otherwise the state/id flip would rebind and
        // pointlessly re-fetch the same image from the server (blurred thumb flash).
        if (media.localPath != null) {
            return type + ":local:" + media.localPath;
        }
        long id = 0;
        if (media.isReady()) {
            id = media.document != null ? media.document.id : (media.photo != null ? media.photo.id : 0);
        }
        return type + ":" + media.state + ":" + id;
    }

    public void detach() {
        attached = false;
        imageReceiver.onDetachedFromWindow();
        blurImageReceiver.onDetachedFromWindow();
        blurSource = null;
    }

    private boolean ensureBlur() {
        if (!hasImage()) return false;
        final Bitmap bitmap = imageReceiver.getBitmap();
        if (bitmap == null || bitmap.isRecycled()) return false;
        final boolean animatedReady = blurImageReceiver.getBitmap() != null && imageReceiver.getAnimation() != null;
        if (!animatedReady && (bitmap != blurSource || blurImageReceiver.getBitmap() == null)) {
            blurSource = bitmap;
            blurImageReceiver.setImageBitmap(Utilities.stackBlurBitmapMax(bitmap, false));
            if (fancyBlurFilter == null) {
                final ColorMatrix cm = new ColorMatrix();
                AndroidUtilities.multiplyBrightnessColorMatrix(cm, .9f);
                AndroidUtilities.adjustSaturationColorMatrix(cm, +.6f);
                fancyBlurFilter = new ColorMatrixColorFilter(cm);
            }
            blurImageReceiver.setColorFilter(fancyBlurFilter);
        }
        return blurImageReceiver.getBitmap() != null;
    }

    public void drawBlurBackground(Canvas canvas, RectF fullBounds) {
        if (!ensureBlur()) return;
        blurImageReceiver.setImageCoords(fullBounds);
        blurImageReceiver.setAlpha(imageReceiver.getCurrentAlpha());
        blurImageReceiver.draw(canvas);
    }

    public void draw(Canvas canvas, RectF bounds) {
        final int x = Math.round(bounds.left);
        final int y = Math.round(bounds.top);
        final int w = Math.round(bounds.width());
        final int h = Math.round(bounds.height());
        imageReceiver.setImageCoords(x, y, w, h);

        if (hasImage()) {
            imageReceiver.draw(canvas);
        }

        drawProgress(canvas, bounds);
    }

    /**
     * Overlays the blurred image + animated spoiler particles on top of the already-drawn media,
     * hiding it. Call {@link #draw} first so the image is loaded and its cross-fade alpha advances.
     * {@code effect} is the shared {@link SpoilerEffect2}; {@code holder} is the invalidation view
     * it is attached to. Both must be obtained on the UI draw pass, never during layout.
     */
    public void drawSpoiler(Canvas canvas, RectF bounds, SpoilerEffect2 effect, View holder) {
        canvas.save();
        canvas.clipRect(bounds);
        if (ensureBlur()) {
            blurImageReceiver.setImageCoords(bounds);
            blurImageReceiver.setAlpha(imageReceiver.getCurrentAlpha());
            blurImageReceiver.draw(canvas);
        }
        if (effect != null) {
            canvas.translate(bounds.left, bounds.top);
            effect.draw(canvas, holder, Math.round(bounds.width()), Math.round(bounds.height()), imageReceiver.getCurrentAlpha());
        }
        canvas.restore();
    }

    private void drawProgress(Canvas canvas, RectF bounds) {
        if (media != null && media.isPending()) {
            final int btn = dp(48);
            final int cx = Math.round(bounds.centerX());
            final int cy = Math.round(bounds.centerY());
            radialProgress.setProgressRect(cx - btn / 2, cy - btn / 2, cx + btn / 2, cy + btn / 2);
            radialProgress.setProgress(media.progress, true);
            radialProgress.draw(canvas);
        }
    }

    private void applyImage() {
        if (media == null) {
            loadedKey = null;
            imageReceiver.setImageBitmap((Drawable) null);
            return;
        }
        final int side = AndroidUtilities.displaySize.x;
        final String filter = side + "_" + side;

        final String key = imageKey() + "@" + filter;
        if (key.equals(loadedKey)) return;

        final Drawable localThumbDrawable = media.localThumbBitmap != null
            ? new BitmapDrawable(parent.getResources(), media.localThumbBitmap) : null;

        if (media.isVideo) {
            if (media.localPath != null) {
                imageReceiver.setOrientation(0, 0, false);
                imageReceiver.setImage(
                    ImageLocation.getForVideoPath(media.localPath), ImageLoader.AUTOPLAY_FILTER,
                    null, filter,
                    null, filter,
                    localThumbDrawable, 0, null, null, 0
                );
            } else if (media.isReady() && media.document != null) {
                final TLRPC.PhotoSize big = pickNonStrippedClosest(media.document.thumbs, AndroidUtilities.getPhotoSize());
                final TLRPC.PhotoSize stripped = pickStripped(media.document.thumbs);
                imageReceiver.setOrientation(0, 0, false);
                imageReceiver.setImage(
                    ImageLocation.getForDocument(media.document), ImageLoader.AUTOPLAY_FILTER,
                    ImageLocation.getForDocument(big, media.document), filter,
                    ImageLocation.getForDocument(stripped, media.document), filter,
                    localThumbDrawable, 0, null, media.document, 0
                );
            } else {
                imageReceiver.setImageBitmap((Drawable) null);
            }
        } else if (media.localPath != null) {
            imageReceiver.setOrientation(media.orientation, media.invert, true);
            imageReceiver.setImage(ImageLocation.getForPath(media.localPath), filter, null, null, null, 0);
        } else if (media.isReady() && media.photo != null) {
            final TLRPC.PhotoSize big = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, AndroidUtilities.getPhotoSize());
            final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, 100);
            imageReceiver.setOrientation(0, 0, false);
            imageReceiver.setImage(
                ImageLocation.getForPhoto(big, media.photo), filter,
                ImageLocation.getForPhoto(thumb, media.photo), filter,
                null, 0, null, media.photo, 0
            );
        } else {
            imageReceiver.setImageBitmap((Drawable) null);
        }
    }

    private static TLRPC.PhotoSize pickNonStrippedClosest(ArrayList<TLRPC.PhotoSize> sizes, int target) {
        if (sizes == null) return null;
        TLRPC.PhotoSize best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < sizes.size(); i++) {
            TLRPC.PhotoSize s = sizes.get(i);
            if (s instanceof TLRPC.TL_photoStrippedSize || s instanceof TLRPC.TL_photoPathSize) continue;
            int side = Math.max(s.w, s.h);
            int dist = Math.abs(side - target);
            if (dist < bestDist) {
                bestDist = dist;
                best = s;
            }
        }
        return best;
    }

    private static TLRPC.PhotoSize pickStripped(ArrayList<TLRPC.PhotoSize> sizes) {
        if (sizes == null) return null;
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i) instanceof TLRPC.TL_photoStrippedSize) return sizes.get(i);
        }
        return null;
    }
}
