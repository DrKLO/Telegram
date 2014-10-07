/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.TLRPC;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

public class ImageReceiver {
    private TLRPC.FileLocation last_path = null;
    private String last_httpUrl = null;
    private String last_filter = null;
    private Drawable last_placeholder = null;
    private int last_size = 0;
    private String currentPath = null;
    private boolean isPlaceholder = false;
    private Drawable currentImage = null;
    private Integer tag = null;
    private View parentView = null;
    private int imageX = 0, imageY = 0, imageW = 0, imageH = 0;
    private Rect drawRegion = new Rect();
    private boolean isVisible = true;
    private boolean isAspectFit = false;
    private boolean lastCacheOnly = false;

    public ImageReceiver() {

    }

    public ImageReceiver(View view) {
        parentView = view;
    }

    public void setImage(TLRPC.FileLocation path, String filter, Drawable placeholder, boolean cacheOnly) {
        setImage(path, null, filter, placeholder, 0, cacheOnly);
    }

    public void setImage(TLRPC.FileLocation path, String filter, Drawable placeholder, int size, boolean cacheOnly) {
        setImage(path, null, filter, placeholder, size, cacheOnly);
    }

    public void setImage(String path, String filter, Drawable placeholder) {
        setImage(null, path, filter, placeholder, 0, true);
    }

    public void setImage(TLRPC.FileLocation fileLocation, String httpUrl, String filter, Drawable placeholder, int size, boolean cacheOnly) {
        if ((fileLocation == null && httpUrl == null) || (fileLocation != null && !(fileLocation instanceof TLRPC.TL_fileLocation) && !(fileLocation instanceof TLRPC.TL_fileEncryptedLocation))) {
            recycleBitmap(null);
            currentPath = null;
            isPlaceholder = true;
            last_path = null;
            last_httpUrl = null;
            last_filter = null;
            lastCacheOnly = false;
            last_placeholder = placeholder;
            last_size = 0;
            currentImage = null;
            ImageLoader.getInstance().cancelLoadingForImageView(this);
            if (parentView != null) {
                parentView.invalidate();
            }
            return;
        }
        String key;
        if (fileLocation != null) {
            key = fileLocation.volume_id + "_" + fileLocation.local_id;
        } else {
            key = Utilities.MD5(httpUrl);
        }
        if (filter != null) {
            key += "@" + filter;
        }
        BitmapDrawable img = null;
        if (currentPath != null) {
            if (currentPath.equals(key)) {
                if (currentImage != null) {
                    return;
                } else {
                    img = ImageLoader.getInstance().getImageFromMemory(fileLocation, httpUrl, filter, this);
                }
            } else {
                img = ImageLoader.getInstance().getImageFromMemory(fileLocation, httpUrl, filter, this);
                recycleBitmap(img);
            }
        }
        img = ImageLoader.getInstance().getImageFromMemory(fileLocation, httpUrl, filter, this);
        currentPath = key;
        last_path = fileLocation;
        last_httpUrl = httpUrl;
        last_filter = filter;
        last_placeholder = placeholder;
        last_size = size;
        lastCacheOnly = cacheOnly;
        if (img == null) {
            isPlaceholder = true;
            ImageLoader.getInstance().loadImage(fileLocation, httpUrl, this, size, cacheOnly);
        } else {
            setImageBitmap(img, currentPath);
        }
    }

    public void setImageBitmap(BitmapDrawable bitmap, String imgKey) {
        if (currentPath == null || !imgKey.equals(currentPath)) {
            return;
        }
        isPlaceholder = false;
        ImageLoader.getInstance().incrementUseCount(currentPath);
        currentImage = bitmap;
        if (parentView != null) {
            parentView.invalidate();
        }
    }

    public void setImageBitmap(Bitmap bitmap) {
        ImageLoader.getInstance().cancelLoadingForImageView(this);
        recycleBitmap(null);
        if (bitmap != null) {
            last_placeholder = new BitmapDrawable(null, bitmap);
        } else {
            last_placeholder = null;
        }
        isPlaceholder = true;
        currentPath = null;
        last_path = null;
        last_httpUrl = null;
        last_filter = null;
        currentImage = null;
        last_size = 0;
        lastCacheOnly = false;
        if (parentView != null) {
            parentView.invalidate();
        }
    }

    public void setImageBitmap(Drawable bitmap) {
        ImageLoader.getInstance().cancelLoadingForImageView(this);
        recycleBitmap(null);
        last_placeholder = bitmap;
        isPlaceholder = true;
        currentPath = null;
        currentImage = null;
        last_path = null;
        last_httpUrl = null;
        last_filter = null;
        last_size = 0;
        lastCacheOnly = false;
        if (parentView != null) {
            parentView.invalidate();
        }
    }

    public void clearImage() {
        recycleBitmap(null);
    }

    private void recycleBitmap(BitmapDrawable newBitmap) {
        if (currentImage == null || isPlaceholder) {
            return;
        }
        if (currentImage instanceof BitmapDrawable) {
            if (currentImage != newBitmap) {
                if (currentPath != null) {
                    Bitmap bitmap = ((BitmapDrawable) currentImage).getBitmap();
                    boolean canDelete = ImageLoader.getInstance().decrementUseCount(currentPath);
                    if (!ImageLoader.getInstance().isInCache(currentPath)) {
                        if (ImageLoader.getInstance().runtimeHack != null) {
                            ImageLoader.getInstance().runtimeHack.trackAlloc(bitmap.getRowBytes() * bitmap.getHeight());
                        }
                        if (canDelete) {
                            currentImage = null;
                            bitmap.recycle();
                        }
                    } else {
                        currentImage = null;
                    }
                    currentPath = null;
                }
            }
        }
    }

    public boolean draw(Canvas canvas, int x, int y, int w, int h) {
        try {
            Drawable bitmapDrawable = currentImage;
            if (bitmapDrawable == null && last_placeholder != null && last_placeholder instanceof BitmapDrawable) {
                bitmapDrawable = last_placeholder;
            }
            if (bitmapDrawable != null) {
                int bitmapW = bitmapDrawable.getIntrinsicWidth();
                int bitmapH = bitmapDrawable.getIntrinsicHeight();
                float scaleW = bitmapW / (float)w;
                float scaleH = bitmapH / (float)h;

                if (isAspectFit) {
                    float scale = Math.max(scaleW, scaleH);
                    canvas.save();
                    bitmapW /= scale;
                    bitmapH /= scale;
                    drawRegion.set(x + (w - bitmapW) / 2, y + (h - bitmapH) / 2, x + (w + bitmapW) / 2, y + (h + bitmapH) / 2);
                    bitmapDrawable.setBounds(drawRegion);
                    try {
                        bitmapDrawable.draw(canvas);
                    } catch (Exception e) {
                        if (currentPath != null) {
                            ImageLoader.getInstance().removeImage(currentPath);
                            currentPath = null;
                        }
                        setImage(last_path, last_httpUrl, last_filter, last_placeholder, last_size, lastCacheOnly);
                        FileLog.e("tmessages", e);
                    }
                    canvas.restore();
                } else {
                    if (Math.abs(scaleW - scaleH) > 0.00001f) {
                        canvas.save();
                        canvas.clipRect(x, y, x + w, y + h);

                        if (bitmapW / scaleH > w) {
                            bitmapW /= scaleH;
                            drawRegion.set(x - (bitmapW - w) / 2, y, x + (bitmapW + w) / 2, y + h);
                        } else {
                            bitmapH /= scaleW;
                            drawRegion.set(x, y - (bitmapH - h) / 2, x + w, y + (bitmapH + h) / 2);
                        }
                        bitmapDrawable.setBounds(drawRegion);
                        if (isVisible) {
                            try {
                                bitmapDrawable.draw(canvas);
                            } catch (Exception e) {
                                if (currentPath != null) {
                                    ImageLoader.getInstance().removeImage(currentPath);
                                    currentPath = null;
                                }
                                setImage(last_path, last_httpUrl, last_filter, last_placeholder, last_size, lastCacheOnly);
                                FileLog.e("tmessages", e);
                            }
                        }

                        canvas.restore();
                    } else {
                        drawRegion.set(x, y, x + w, y + h);
                        bitmapDrawable.setBounds(drawRegion);
                        if (isVisible) {
                            try {
                                bitmapDrawable.draw(canvas);
                            } catch (Exception e) {
                                if (currentPath != null) {
                                    ImageLoader.getInstance().removeImage(currentPath);
                                    currentPath = null;
                                }
                                setImage(last_path, last_httpUrl, last_filter, last_placeholder, last_size, lastCacheOnly);
                                FileLog.e("tmessages", e);
                            }
                        }
                    }
                }
                return true;
            } else if (last_placeholder != null) {
                drawRegion.set(x, y, x + w, y + h);
                last_placeholder.setBounds(drawRegion);
                if (isVisible) {
                    try {
                        last_placeholder.draw(canvas);
                    } catch (Exception e) {
                        if (currentPath != null) {
                            ImageLoader.getInstance().removeImage(currentPath);
                            currentPath = null;
                        }
                        setImage(last_path, last_httpUrl, last_filter, last_placeholder, last_size, lastCacheOnly);
                        FileLog.e("tmessages", e);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return false;
    }

    public Bitmap getBitmap() {
        if (currentImage != null && currentImage instanceof BitmapDrawable) {
            return ((BitmapDrawable)currentImage).getBitmap();
        } else if (isPlaceholder && last_placeholder != null && last_placeholder instanceof BitmapDrawable) {
            return ((BitmapDrawable)last_placeholder).getBitmap();
        }
        return null;
    }

    public void setVisible(boolean value, boolean invalidate) {
        if (isVisible == value) {
            return;
        }
        isVisible = value;
        if (invalidate && parentView != null) {
            parentView.invalidate();
        }
    }

    public boolean getVisible() {
        return isVisible;
    }

    public boolean hasImage() {
        return currentImage != null || last_placeholder != null || currentPath != null || last_httpUrl != null;
    }

    public void setAspectFit(boolean value) {
        isAspectFit = value;
    }

    public void setParentView(View view) {
        parentView = view;
    }

    protected Integer getTag() {
        return tag;
    }

    protected void setTag(Integer tag) {
        this.tag = tag;
    }

    public void setImageCoords(int x, int y, int width, int height) {
        imageX = x;
        imageY = y;
        imageW = width;
        imageH = height;
    }

    public int getImageX() {
        return imageX;
    }

    public int getImageY() {
        return imageY;
    }

    public int getImageWidth() {
        return imageW;
    }

    public int getImageHeight() {
        return imageH;
    }

    public boolean isInsideImage(float x, float y) {
        return x >= imageX && x <= imageX + imageW && y >= imageY && y <= imageY + imageH;
    }

    public Rect getDrawRegion() {
        return drawRegion;
    }

    public String getFilter() {
        return last_filter;
    }

    public String getKey() {
        return currentPath;
    }
}
