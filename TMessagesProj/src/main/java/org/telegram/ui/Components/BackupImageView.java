/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SecureDocument;
import org.telegram.tgnet.TLObject;

public class BackupImageView extends View {

    protected ImageReceiver imageReceiver;
    protected int width = -1;
    protected int height = -1;

    public BackupImageView(Context context) {
        super(context);
        imageReceiver = new ImageReceiver(this);
    }

    public void setOrientation(int angle, boolean center) {
        imageReceiver.setOrientation(angle, center);
    }

    public void setImage(SecureDocument secureDocument, String filter) {
        setImage(ImageLocation.getForSecureDocument(secureDocument), filter, null, null, null, null, null, 0, null);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, String ext, Drawable thumb, Object parentObject) {
        setImage(imageLocation, imageFilter, null, null, thumb, null, ext, 0, parentObject);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Drawable thumb, Object parentObject) {
        setImage(imageLocation, imageFilter, null, null, thumb, null, null, 0, parentObject);
    }

    public void setImage(ImageLocation mediaLocation, String mediaFilter, ImageLocation imageLocation, String imageFilter, Drawable thumb, Object parentObject) {
        imageReceiver.setImage(mediaLocation, mediaFilter, imageLocation, imageFilter, null, null, thumb, 0, null, parentObject, 1);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Bitmap thumb, Object parentObject) {
        setImage(imageLocation, imageFilter, null, null, null, thumb, null, 0, parentObject);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Drawable thumb, int size, Object parentObject) {
        setImage(imageLocation, imageFilter, null, null, thumb, null, null, size, parentObject);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Bitmap thumbBitmap, int size, int cacheType, Object parentObject) {
        Drawable thumb = null;
        if (thumbBitmap != null) {
            thumb = new BitmapDrawable(null, thumbBitmap);
        }
        imageReceiver.setImage(imageLocation, imageFilter, null, null, thumb, size, null, parentObject, cacheType);
    }

    public void setForUserOrChat(TLObject object, AvatarDrawable avatarDrawable) {
        imageReceiver.setForUserOrChat(object, avatarDrawable);
    }

    public void setForUserOrChat(TLObject object, AvatarDrawable avatarDrawable, Object parent) {
        imageReceiver.setForUserOrChat(object, avatarDrawable, parent);
    }

    public void setImageMedia(ImageLocation mediaLocation, String mediaFilter, ImageLocation imageLocation, String imageFilter, Bitmap thumbBitmap, int size, int cacheType, Object parentObject) {
        Drawable thumb = null;
        if (thumbBitmap != null) {
            thumb = new BitmapDrawable(null, thumbBitmap);
        }
        imageReceiver.setImage(mediaLocation, mediaFilter, imageLocation, imageFilter, null, null, thumb, size, null, parentObject, cacheType);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, int size, Object parentObject) {
        setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, null, null, null, size, parentObject);
    }

    public void setImage(String path, String filter, Drawable thumb) {
        setImage(ImageLocation.getForPath(path), filter, null, null, thumb, null, null, 0, null);
    }

    public void setImage(String path, String filter, String thumbPath, String thumbFilter) {
        setImage(ImageLocation.getForPath(path), filter, ImageLocation.getForPath(thumbPath), thumbFilter, null, null, null, 0, null);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, Drawable thumb, Bitmap thumbBitmap, String ext, int size, Object parentObject) {
        if (thumbBitmap != null) {
            thumb = new BitmapDrawable(null, thumbBitmap);
        }
        imageReceiver.setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, thumb, size, ext, parentObject, 0);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, String ext, int size, int cacheType, Object parentObject) {
        imageReceiver.setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, null, size, ext, parentObject, cacheType);
    }

    public void setImageMedia(ImageLocation mediaLocation, String mediaFilter, ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, String ext, int size, int cacheType, Object parentObject) {
        imageReceiver.setImage(mediaLocation, mediaFilter, imageLocation, imageFilter, thumbLocation, thumbFilter, null, size, ext, parentObject, cacheType);
    }

    public void setImageBitmap(Bitmap bitmap) {
        imageReceiver.setImageBitmap(bitmap);
    }

    public void setImageResource(int resId) {
        Drawable drawable = getResources().getDrawable(resId);
        imageReceiver.setImageBitmap(drawable);
        invalidate();
    }

    public void setImageResource(int resId, int color) {
        Drawable drawable = getResources().getDrawable(resId);
        if (drawable != null) {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
        imageReceiver.setImageBitmap(drawable);
        invalidate();
    }

    public void setImageDrawable(Drawable drawable) {
        imageReceiver.setImageBitmap(drawable);
    }

    public void setLayerNum(int value) {
        imageReceiver.setLayerNum(value);
    }

    public void setRoundRadius(int value) {
        imageReceiver.setRoundRadius(value);
        invalidate();
    }

    public void setRoundRadius(int tl, int tr, int bl, int br) {
        imageReceiver.setRoundRadius(tl, tr, bl ,br);
        invalidate();
    }

    public int[] getRoundRadius() {
        return imageReceiver.getRoundRadius();
    }

    public void setAspectFit(boolean value) {
        imageReceiver.setAspectFit(value);
    }

    public ImageReceiver getImageReceiver() {
        return imageReceiver;
    }

    public void setSize(int w, int h) {
        width = w;
        height = h;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (width != -1 && height != -1) {
            imageReceiver.setImageCoords((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
        } else {
            imageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
        }
        imageReceiver.draw(canvas);
    }
}
