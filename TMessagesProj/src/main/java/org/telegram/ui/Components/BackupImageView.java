/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import org.telegram.android.ImageReceiver;
import org.telegram.messenger.TLRPC;

public class BackupImageView extends View {
    public ImageReceiver imageReceiver;
    public boolean processDetach = true;

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

    public void setImage(TLRPC.FileLocation path, String filter, Drawable placeholder) {
        setImage(path, null, filter, placeholder, null, 0);
    }

    public void setImage(TLRPC.FileLocation path, String filter, Bitmap placeholderBitmap) {
        setImage(path, null, filter, null, placeholderBitmap, 0);
    }

    public void setImage(TLRPC.FileLocation path, String filter, Drawable placeholder, int size) {
        setImage(path, null, filter, placeholder, null, size);
    }

    public void setImage(TLRPC.FileLocation path, String filter, Bitmap placeholderBitmap, int size) {
        setImage(path, null, filter, null, placeholderBitmap, size);
    }

    public void setImage(String path, String filter, Drawable placeholder) {
        setImage(null, path, filter, placeholder, null, 0);
    }

    public void setImage(TLRPC.FileLocation path, String httpUrl, String filter, Drawable placeholder, Bitmap placeholderBitmap, int size) {
        Drawable placeholderDrawable = null;
        if (placeholderBitmap != null) {
            placeholderDrawable = new BitmapDrawable(null, placeholderBitmap);
        } else if (placeholder != null) {
            placeholderDrawable = placeholder;
        }
        imageReceiver.setImage(path, httpUrl, filter, placeholderDrawable, size, false);
    }

    public void setImageBitmap(Bitmap bitmap) {
        imageReceiver.setImageBitmap(bitmap);
    }

    public void setImageResource(int resId) {
        imageReceiver.setImageBitmap(getResources().getDrawable(resId));
    }

    public void setImageDrawable(Drawable drawable) {
        imageReceiver.setImageBitmap(drawable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (processDetach) {
            imageReceiver.clearImage();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        imageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
        imageReceiver.draw(canvas);
    }
}
