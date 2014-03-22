/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import org.telegram.messenger.TLRPC;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.lang.ref.WeakReference;

public class ImageReceiver {
    private TLRPC.FileLocation last_path = null;
    private String last_httpUrl = null;
    private String last_filter = null;
    private Drawable last_placeholder = null;
    private int last_size = 0;
    private String currentPath = null;
    private boolean isPlaceholder = false;
    private Drawable currentImage = null;
    public Integer TAG = null;
    public WeakReference<View> parentView = null;
    public int imageX = 0, imageY = 0, imageW = 0, imageH = 0;

    public void setImage(TLRPC.FileLocation path, String filter, Drawable placeholder) {
        setImage(path, null, filter, placeholder, 0);
    }

    public void setImage(TLRPC.FileLocation path, String filter, Drawable placeholder, int size) {
        setImage(path, null, filter, placeholder, size);
    }

    public void setImage(String path, String filter, Drawable placeholder) {
        setImage(null, path, filter, placeholder, 0);
    }

    public void setImage(TLRPC.FileLocation path, String httpUrl, String filter, Drawable placeholder, int size) {
        if ((path == null && httpUrl == null) || (path != null && !(path instanceof TLRPC.TL_fileLocation) && !(path instanceof TLRPC.TL_fileEncryptedLocation))) {
            recycleBitmap(null);
            currentPath = null;
            isPlaceholder = true;
            last_path = null;
            last_httpUrl = null;
            last_filter = null;
            last_placeholder = placeholder;
            last_size = 0;
            currentImage = null;
            FileLoader.getInstance().cancelLoadingForImageView(this);
            return;
        }
        String key;
        if (path != null) {
            key = path.volume_id + "_" + path.local_id;
        } else {
            key = Utilities.MD5(httpUrl);
        }
        if (filter != null) {
            key += "@" + filter;
        }
        Bitmap img;
        if (currentPath != null) {
            if (currentPath.equals(key)) {
                return;
            } else {
                img = FileLoader.getInstance().getImageFromMemory(path, httpUrl, this, filter, true);
                recycleBitmap(img);
            }
        } else {
            img = FileLoader.getInstance().getImageFromMemory(path, httpUrl, this, filter, true);
        }
        currentPath = key;
        last_path = path;
        last_httpUrl = httpUrl;
        last_filter = filter;
        last_placeholder = placeholder;
        last_size = size;
        if (img == null) {
            isPlaceholder = true;
            FileLoader.getInstance().loadImage(path, httpUrl, this, filter, true, size);
        } else {
            setImageBitmap(img, currentPath);
        }
    }

    public void setImageBitmap(Bitmap bitmap, String imgKey) {
        if (currentPath == null || !imgKey.equals(currentPath)) {
            return;
        }
        isPlaceholder = false;
        FileLoader.getInstance().incrementUseCount(currentPath);
        currentImage = new BitmapDrawable(null, bitmap);
        if (parentView.get() != null) {
            if (imageW != 0) {
                parentView.get().invalidate(imageX, imageY, imageX + imageW, imageY + imageH);
            } else {
                parentView.get().invalidate();
            }
        }
    }

    public void clearImage() {
        recycleBitmap(null);
    }

    private void recycleBitmap(Bitmap newBitmap) {
        if (currentImage == null || isPlaceholder) {
            return;
        }
        if (currentImage instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable)currentImage).getBitmap();
            if (bitmap != null && bitmap != newBitmap) {
                if (currentPath != null) {
                    boolean canDelete = FileLoader.getInstance().decrementUseCount(currentPath);
                    if (!FileLoader.getInstance().isInCache(currentPath)) {
                        if (FileLoader.getInstance().runtimeHack != null) {
                            FileLoader.getInstance().runtimeHack.trackAlloc(bitmap.getRowBytes() * bitmap.getHeight());
                        }
                        if (canDelete) {
                            currentImage = null;
                            if (Build.VERSION.SDK_INT < 11) {
                                bitmap.recycle();
                            }
                        }
                    } else {
                        currentImage = null;
                    }
                }
            }
        }
    }

    public void draw(Canvas canvas, int x, int y, int w, int h) {
        try {
            if (currentImage != null) {
                currentImage.setBounds(x, y, x + w, y + h);
                currentImage.draw(canvas);
            } else if (last_placeholder != null) {
                last_placeholder.setBounds(x, y, x + w, y + h);
                last_placeholder.draw(canvas);
            }
        } catch (Exception e) {
            if (currentPath != null) {
                FileLoader.getInstance().removeImage(currentPath);
                currentPath = null;
            }
            setImage(last_path, last_httpUrl, last_filter, last_placeholder, last_size);
            FileLog.e("tmessages", e);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        recycleBitmap(null);
        super.finalize();
    }
}
