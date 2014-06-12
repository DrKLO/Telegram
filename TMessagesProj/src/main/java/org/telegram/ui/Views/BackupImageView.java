/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import org.telegram.messenger.TLRPC;

import java.lang.ref.WeakReference;

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
        imageReceiver = new ImageReceiver();
        imageReceiver.parentView = this;
    }

    public void setImage(TLRPC.FileLocation path, String filter, int placeholder) {
        setImage(path, null, filter, placeholder, null, 0);
    }

    public void setImage(TLRPC.FileLocation path, String filter, Bitmap placeholderBitmap) {
        setImage(path, null, filter, 0, placeholderBitmap, 0);
    }

    public void setImage(TLRPC.FileLocation path, String filter, int placeholder, int size) {
        setImage(path, null, filter, placeholder, null, size);
    }

    public void setImage(TLRPC.FileLocation path, String filter, Bitmap placeholderBitmap, int size) {
        setImage(path, null, filter, 0, placeholderBitmap, size);
    }

    public void setImage(String path, String filter, int placeholder) {
        setImage(null, path, filter, placeholder, null, 0);
    }

    public void setImage(TLRPC.FileLocation path, String httpUrl, String filter, int placeholder, Bitmap placeholderBitmap, int size) {
        Drawable placeholderDrawable = null;
        if (placeholderBitmap != null) {
            placeholderDrawable = new BitmapDrawable(null, placeholderBitmap);
        } else if (placeholder != 0) {
            placeholderDrawable = getResources().getDrawable(placeholder);
        }
        imageReceiver.setImage(path, httpUrl, filter, placeholderDrawable, size);
    }

    public void setImageBitmap(Bitmap bitmap) {
        imageReceiver.setImageBitmap(bitmap);
    }

    public void setImageResource(int resId) {
        imageReceiver.setImageBitmap(getResources().getDrawable(resId));
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
        imageReceiver.imageX = 0;
        imageReceiver.imageY = 0;
        imageReceiver.imageW = getWidth();
        imageReceiver.imageH = getHeight();
        imageReceiver.draw(canvas, 0, 0, imageReceiver.imageW, imageReceiver.imageH);
    }
}
