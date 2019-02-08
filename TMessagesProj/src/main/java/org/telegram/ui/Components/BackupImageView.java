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

import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SecureDocument;
import org.telegram.tgnet.TLObject;

public class BackupImageView extends View {

    private ImageReceiver imageReceiver;
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
    }

    public void setImage(SecureDocument path, String filter) {
        setImage(path, filter, null, null, null, null, null, 0, null);
    }

    public void setImage(TLObject path, String filter, String ext, Drawable thumb, Object parentObject) {
        setImage(path, filter, thumb, null, null, null, ext, 0, parentObject);
    }

    public void setImage(TLObject path, String filter, Drawable thumb, Object parentObject) {
        setImage(path, filter, thumb, null, null, null, null, 0, parentObject);
    }

    public void setImage(TLObject path, String filter, Bitmap thumb, Object parentObject) {
        setImage(path, filter, null, thumb, null, null, null, 0, parentObject);
    }

    public void setImage(TLObject path, String filter, Drawable thumb, int size, Object parentObject) {
        setImage(path, filter, thumb, null, null, null, null, size, parentObject);
    }

    public void setImage(TLObject path, String filter, Bitmap thumb, int size, Object parentObject) {
        setImage(path, filter, null, thumb, null, null, null, size, parentObject);
    }

    public void setImage(TLObject path, String filter, TLObject thumb, int size, Object parentObject) {
        setImage(path, filter, null, null, thumb, null, null, size, parentObject);
    }

    public void setImage(String path, String filter, Drawable thumb) {
        setImage(path, filter, thumb, null, null, null, null, 0, null);
    }

    public void setImage(String path, String filter, String thumbPath, String thumbFilter) {
        setImage(path, filter, null, null, thumbPath, thumbFilter, null, 0, null);
    }

    public void setOrientation(int angle, boolean center) {
        imageReceiver.setOrientation(angle, center);
    }

    public void setImage(Object path, String filter, Drawable thumb, Bitmap thumbBitmap, Object thumbLocation, String thumbFilter, String ext, int size, Object parentObject) {
        if (thumbBitmap != null) {
            thumb = new BitmapDrawable(null, thumbBitmap);
        }
        imageReceiver.setImage(path, filter, thumb, thumbLocation, thumbFilter, size, ext, parentObject, 0);
    }

    public void setImage(TLObject path, String filter, TLObject thumbLocation, String thumbFilter, String ext, int size, int cacheType, Object parentObject) {
        imageReceiver.setImage(path, filter, null, thumbLocation, thumbFilter, size, ext, parentObject, cacheType);
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
