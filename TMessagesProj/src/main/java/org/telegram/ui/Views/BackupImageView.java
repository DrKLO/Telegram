/*
 * This is the source code of Telegram for Android v. 1.3.2.
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
import android.os.Build;
import android.widget.ImageView;

import org.telegram.messenger.TLRPC;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

public class BackupImageView extends ImageView {
    boolean makeRequest = true;
    public String currentPath;
    private boolean isPlaceholder;
    private boolean ignoreLayout = true;

    TLRPC.FileLocation last_path;
    String last_httpUrl;
    String last_filter;
    int last_placeholder;
    Bitmap last_placeholderBitmap;
    int last_size;

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
        if ((path == null && httpUrl == null) || (path != null && !(path instanceof TLRPC.TL_fileLocation) && !(path instanceof TLRPC.TL_fileEncryptedLocation))) {
            recycleBitmap(null);
            currentPath = null;
            isPlaceholder = true;

            last_path = null;
            last_httpUrl = null;
            last_filter = null;
            last_placeholder = 0;
            last_size = 0;
            last_placeholderBitmap = null;

            FileLoader.getInstance().cancelLoadingForImageView(this);
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
        last_placeholderBitmap = placeholderBitmap;
        last_size = size;
        if (img == null) {
            isPlaceholder = true;
            if (placeholder != 0) {
                setImageResourceMy(placeholder);
            } else if (placeholderBitmap != null) {
                setImageBitmapMy(placeholderBitmap);
            }
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
                    boolean canDelete = FileLoader.getInstance().decrementUseCount(currentPath);
                    if (!FileLoader.getInstance().isInCache(currentPath)) {
                        if (FileLoader.getInstance().runtimeHack != null) {
                            FileLoader.getInstance().runtimeHack.trackAlloc(bitmap.getRowBytes() * bitmap.getHeight());
                        }
                        if (canDelete) {
                            setImageBitmap(null);
                            if (Build.VERSION.SDK_INT < 11) {
                                bitmap.recycle();
                            }
                        }
                    } else {
                        setImageBitmap(null);
                    }
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
            FileLoader.getInstance().removeImage(currentPath);
            currentPath = null;
            setImage(last_path, last_httpUrl, last_filter, last_placeholder, last_placeholderBitmap, last_size);
            FileLog.e("tmessages", e);
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
        last_path = null;
        last_httpUrl = null;
        last_filter = null;
        last_placeholder = 0;
        last_size = 0;
        last_placeholderBitmap = null;
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
        last_path = null;
        last_httpUrl = null;
        last_filter = null;
        last_placeholder = 0;
        last_size = 0;
        last_placeholderBitmap = null;
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
