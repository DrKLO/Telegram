/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.android;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
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
    private boolean forcePreview = false;
    private int roundRadius = 0;
    private BitmapShader bitmapShader = null;
    private Paint roundPaint = null;
    private RectF roundRect = null;
    private RectF bitmapRect = null;
    private Matrix shaderMatrix = null;

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
            bitmapShader = null;
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
        bitmapShader = null;
        if (img == null) {
            isPlaceholder = true;
            ImageLoader.getInstance().loadImage(fileLocation, httpUrl, this, size, cacheOnly);
            if (parentView != null) {
                parentView.invalidate();
            }
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
        if (roundRadius != 0) {
            bitmapShader = new BitmapShader(bitmap.getBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            roundPaint.setShader(bitmapShader);
            bitmapRect.set(0, 0, bitmap.getBitmap().getWidth(), bitmap.getBitmap().getHeight());
        }
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
        bitmapShader = null;
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
        bitmapShader = null;
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

    public boolean draw(Canvas canvas) {
        try {
            Drawable bitmapDrawable = currentImage;
            if (forcePreview || bitmapDrawable == null && last_placeholder != null && last_placeholder instanceof BitmapDrawable) {
                bitmapDrawable = last_placeholder;
            }
            if (bitmapDrawable != null) {
                if (bitmapShader != null) {
                    drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH);
                    if (isVisible) {
                        roundRect.set(drawRegion);
                        shaderMatrix.reset();
                        shaderMatrix.setRectToRect(bitmapRect, roundRect, Matrix.ScaleToFit.FILL);
                        bitmapShader.setLocalMatrix(shaderMatrix);
                        canvas.drawRoundRect(roundRect, roundRadius, roundRadius, roundPaint);
                    }
                } else {
                    int bitmapW = bitmapDrawable.getIntrinsicWidth();
                    int bitmapH = bitmapDrawable.getIntrinsicHeight();
                    float scaleW = bitmapW / (float) imageW;
                    float scaleH = bitmapH / (float) imageH;

                    if (isAspectFit) {
                        float scale = Math.max(scaleW, scaleH);
                        canvas.save();
                        bitmapW /= scale;
                        bitmapH /= scale;
                        drawRegion.set(imageX + (imageW - bitmapW) / 2, imageY + (imageH - bitmapH) / 2, imageX + (imageW + bitmapW) / 2, imageY + (imageH + bitmapH) / 2);
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
                            canvas.clipRect(imageX, imageY, imageX + imageW, imageY + imageH);

                            if (bitmapW / scaleH > imageW) {
                                bitmapW /= scaleH;
                                drawRegion.set(imageX - (bitmapW - imageW) / 2, imageY, imageX + (bitmapW + imageW) / 2, imageY + imageH);
                            } else {
                                bitmapH /= scaleW;
                                drawRegion.set(imageX, imageY - (bitmapH - imageH) / 2, imageX + imageW, imageY + (bitmapH + imageH) / 2);
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
                            drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH);
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
                }
                return true;
            } else if (last_placeholder != null) {
                drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH);
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

    public void setForcePreview(boolean value) {
        forcePreview = value;
    }

    public void setRoundRadius(int value) {
        roundRadius = value;
        if (roundRadius != 0) {
            if (roundPaint == null) {
                roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                roundRect = new RectF();
                shaderMatrix = new Matrix();
                bitmapRect = new RectF();
            }
        } else {
            roundPaint = null;
            roundRect = null;
            shaderMatrix = null;
            bitmapRect = null;
        }
    }

    public int getRoundRadius() {
        return roundRadius;
    }
}
