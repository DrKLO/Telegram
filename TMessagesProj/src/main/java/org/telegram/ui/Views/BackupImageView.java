/*
 * This is the source code of Telegram for Android v. 1.2.3.
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
import android.graphics.drawable.NinePatchDrawable;
import android.widget.ImageView;

import org.telegram.TL.TLRPC;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.Utilities;

public class BackupImageView extends ImageView {
    boolean makeRequest = true;
    public String currentPath;
    private boolean isPlaceholder;
    private boolean ignoreLayout = true;

    public BackupImageView(android.content.Context context) {
        super(context);
    }

    public BackupImageView(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public BackupImageView(android.content.Context context, android.util.AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setImage(TLRPC.FileLocation path, String filter, int placeholder) {
        setImage(path, null, filter, placeholder, null);
    }

    public void setImage(TLRPC.FileLocation path, String filter, Bitmap placeholderBitmap) {
        setImage(path, null, filter, 0, placeholderBitmap);
    }

    public void setImage(String path, String filter, int placeholder) {
        setImage(null, path, filter, placeholder, null);
    }

    public void setImage(TLRPC.FileLocation path, String httpUrl, String filter, int placeholder, Bitmap placeholderBitmap) {
        if ((path == null && httpUrl == null) || (path != null && !(path instanceof TLRPC.TL_fileLocation) && !(path instanceof TLRPC.TL_fileEncryptedLocation))) {
            recycleBitmap(null);
            currentPath = null;
            isPlaceholder = true;
            FileLoader.Instance.cancelLoadingForImageView(this);
            if (placeholder != 0) {
                setImageResourceMy(placeholder);
            } else if (placeholderBitmap != null) {
                setImageBitmapMy(placeholderBitmap);
            }
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
                img = FileLoader.Instance.getImageFromMemory(path, httpUrl, this, filter, true);
                recycleBitmap(img);
            }
        } else {
            img = FileLoader.Instance.getImageFromMemory(path, httpUrl, this, filter, true);
        }
        currentPath = key;
        if (img == null) {
            isPlaceholder = true;
            if (placeholder != 0) {
                setImageResourceMy(placeholder);
            } else if (placeholderBitmap != null) {
                setImageBitmapMy(placeholderBitmap);
            }
            FileLoader.Instance.loadImage(path, httpUrl, this, filter, true);
        } else {
            setImageBitmap(img, currentPath);
        }
    }

    public void setImageBitmap(Bitmap bitmap, String imgKey) {
        if (currentPath == null || !imgKey.equals(currentPath)) {
            return;
        }
        isPlaceholder = false;
        FileLoader.Instance.incrementUseCount(currentPath);
        if (ignoreLayout) {
            makeRequest = false;
        }
        super.setImageBitmap(bitmap);
        if (ignoreLayout) {
            makeRequest = true;
        }
    }

    public void clearImage() {
        recycleBitmap(null);
    }

    private void recycleBitmap(Bitmap newBitmap) {
        Drawable drawable = getDrawable();
        if (drawable == null || isPlaceholder) {
            return;
        }
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable)drawable).getBitmap();
            if (bitmap != null && bitmap != newBitmap) {
                if (currentPath != null) {
                    boolean canDelete = FileLoader.Instance.decrementUseCount(currentPath);
                    if (!FileLoader.Instance.isInCache(currentPath)) {
                        if (FileLoader.Instance.runtimeHack != null) {
                            FileLoader.Instance.runtimeHack.trackAlloc(bitmap.getRowBytes() * bitmap.getHeight());
                        }
                        if (canDelete) {
                            setImageBitmap(null);
                            bitmap.recycle();
                        }
                    } else {
                        setImageBitmap(null);
                    }
                } else {
                    //Log.e("tmeesages", "recycle bitmap placeholder");
                    //bitmap.recycle();
                }
            }
        } else if (drawable instanceof NinePatchDrawable) {

        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            super.onDraw(canvas);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        recycleBitmap(null);
        super.finalize();
    }

    public void setImageResourceMy(int resId) {
        if (ignoreLayout) {
            makeRequest = false;
        }
        super.setImageResource(resId);
        if (ignoreLayout) {
            makeRequest = true;
        }
    }

    public void setImageResource(int resId) {
        if (resId != 0) {
            recycleBitmap(null);
        }
        currentPath = null;
        if (ignoreLayout) {
            makeRequest = false;
        }
        super.setImageResource(resId);
        if (ignoreLayout) {
            makeRequest = true;
        }
    }

    public void setImageBitmapMy(Bitmap bitmap) {
        if (ignoreLayout) {
            makeRequest = false;
        }
        super.setImageBitmap(bitmap);
        if (ignoreLayout) {
            makeRequest = true;
        }
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            recycleBitmap(null);
        }
        currentPath = null;
        if (ignoreLayout) {
            makeRequest = false;
        }
        super.setImageBitmap(bitmap);
        if (ignoreLayout) {
            makeRequest = true;
        }
    }

    @Override public void requestLayout() {
        if (makeRequest) {
            super.requestLayout();
        }
    }
}
