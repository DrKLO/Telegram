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
import android.util.AttributeSet;
import android.view.View;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SecureDocument;

public class BackupImageView extends View {

    private ImageReceiver imageReceiver;
    private ImageReceiver foregroundImageReceiver;

    private float foregroundAlpha;

    private int width = -1;
    private int height = -1;

    public BackupImageView(Context context) {
        super(context);
        init();
    }

    public BackupImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BackupImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        imageReceiver = new ImageReceiver(this);
        foregroundImageReceiver = new ImageReceiver(this);
    }

    public void setOrientation(int angle, boolean center) {
        imageReceiver.setOrientation(angle, center);
        foregroundImageReceiver.setOrientation(angle, center);
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

    public void setImage(ImageLocation imageLocation, String imageFilter, Bitmap thumb, Object parentObject) {
        setImage(imageLocation, imageFilter, null, null, null, thumb, null, 0, parentObject);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Drawable thumb, int size, Object parentObject) {
        setImage(imageLocation, imageFilter, null, null, thumb, null, null, size, parentObject);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Bitmap thumb, int size, Object parentObject) {
        setImage(imageLocation, imageFilter, null, null, null, thumb, null, size, parentObject);
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

    public void setForegroundImage(ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, Drawable thumb) {
        foregroundImageReceiver.setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, thumb, 0, null, null, 0);
    }

    public void setForegroundImageDrawable(Drawable drawable) {
        foregroundImageReceiver.setImageBitmap(drawable);
    }

    public float getForegroundAlpha() {
        return foregroundAlpha;
    }

    public void setForegroundAlpha(float foregroundAlpha) {
        this.foregroundAlpha = foregroundAlpha;
        invalidate();
    }

    public void clearForeground() {
        foregroundImageReceiver.clearImage();
        foregroundAlpha = 0f;
        invalidate();
    }

    public void setLayerNum(int value) {
        imageReceiver.setLayerNum(value);
    }

    public void setRoundRadius(int value) {
        imageReceiver.setRoundRadius(value);
        invalidate();
    }

    public int getRoundRadius() {
        return imageReceiver.getRoundRadius();
    }

    public void setAspectFit(boolean value) {
        imageReceiver.setAspectFit(value);
    }

    public ImageReceiver getImageReceiver() {
        return imageReceiver;
    }

    public ImageReceiver getForegroundImageReceiver() {
        return foregroundImageReceiver;
    }

    public void setSize(int w, int h) {
        width = w;
        height = h;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
        foregroundImageReceiver.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
        foregroundImageReceiver.onAttachedToWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final boolean drawImage = foregroundAlpha < 1f;
        final boolean drawForeground = foregroundAlpha > 0f;
        if (width != -1 && height != -1) {
            if (drawImage) {
                imageReceiver.setImageCoords((getWidth() - width) / 2,
                        (getHeight() - height) / 2, width, height);
            }
            if (drawForeground) {
                foregroundImageReceiver.setImageCoords((getWidth() - width) / 2,
                        (getHeight() - height) / 2, width, height);
            }
        } else {
            if (drawImage) {
                imageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
            }
            if (drawForeground) {
                foregroundImageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
            }
        }
        if (drawImage) {
            imageReceiver.draw(canvas);
        }
        if (drawForeground) {
            foregroundImageReceiver.setAspectFit(imageReceiver.isAspectFit());
            foregroundImageReceiver.setRoundRadius(imageReceiver.getRoundRadius());
            foregroundImageReceiver.setAlpha(foregroundAlpha);
            foregroundImageReceiver.draw(canvas);
        }
    }
}
