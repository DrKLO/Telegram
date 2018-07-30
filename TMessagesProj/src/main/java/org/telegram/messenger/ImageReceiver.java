/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFileDrawable;

public class ImageReceiver implements NotificationCenter.NotificationCenterDelegate {

    public interface ImageReceiverDelegate {
        void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb);
    }

    public static class BitmapHolder {

        private String key;
        public Bitmap bitmap;

        public BitmapHolder(Bitmap b, String k) {
            bitmap = b;
            key = k;
            if (key != null) {
                ImageLoader.getInstance().incrementUseCount(key);
            }
        }

        public int getWidth() {
            return bitmap != null ? bitmap.getWidth() : 0;
        }

        public int getHeight() {
            return bitmap != null ? bitmap.getHeight() : 0;
        }

        public boolean isRecycled() {
            return bitmap == null || bitmap.isRecycled();
        }

        public void release() {
            if (key == null) {
                bitmap = null;
                return;
            }
            boolean canDelete = ImageLoader.getInstance().decrementUseCount(key);
            if (!ImageLoader.getInstance().isInCache(key)) {
                if (canDelete) {
                    bitmap.recycle();
                }
            }
            key = null;
            bitmap = null;
        }
    }

    private class SetImageBackup {
        public TLObject fileLocation;
        public String httpUrl;
        public String filter;
        public Drawable thumb;
        public TLRPC.FileLocation thumbLocation;
        public String thumbFilter;
        public int size;
        public int cacheType;
        public String ext;
    }

    private int currentAccount;
    private View parentView;
    private int tag;
    private int thumbTag;
    private int param;
    private MessageObject parentMessageObject;
    private boolean canceledLoading;
    private static PorterDuffColorFilter selectedColorFilter = new PorterDuffColorFilter(0xffdddddd, PorterDuff.Mode.MULTIPLY);
    private static PorterDuffColorFilter selectedGroupColorFilter = new PorterDuffColorFilter(0xffbbbbbb, PorterDuff.Mode.MULTIPLY);
    private boolean forceLoding;

    private SetImageBackup setImageBackup;

    private TLObject currentImageLocation;
    private String currentKey;
    private String currentThumbKey;
    private String currentHttpUrl;
    private String currentFilter;
    private String currentThumbFilter;
    private String currentExt;
    private TLRPC.FileLocation currentThumbLocation;
    private int currentSize;
    private int currentCacheType;
    private Drawable currentImage;
    private Drawable currentThumb;
    private Drawable staticThumb;
    private boolean allowStartAnimation = true;
    private boolean allowDecodeSingleFrame;

    private boolean crossfadeWithOldImage;
    private Drawable crossfadeImage;
    private String crossfadeKey;
    private BitmapShader crossfadeShader;

    private boolean needsQualityThumb;
    private boolean shouldGenerateQualityThumb;
    private boolean invalidateAll;

    private int imageX, imageY, imageW, imageH;
    private Rect drawRegion = new Rect();
    private boolean isVisible = true;
    private boolean isAspectFit;
    private boolean forcePreview;
    private boolean forceCrossfade;
    private int roundRadius;
    private BitmapShader bitmapShader;
    private BitmapShader bitmapShaderThumb;
    private Paint roundPaint;
    private RectF roundRect = new RectF();
    private RectF bitmapRect = new RectF();
    private Matrix shaderMatrix = new Matrix();
    private float overrideAlpha = 1.0f;
    private int isPressed;
    private int orientation;
    private boolean centerRotation;
    private ImageReceiverDelegate delegate;
    private float currentAlpha;
    private long lastUpdateAlphaTime;
    private byte crossfadeAlpha = 1;
    private boolean manualAlphaAnimator;
    private boolean crossfadeWithThumb;
    private ColorFilter colorFilter;

    public ImageReceiver() {
        this(null);
    }

    public ImageReceiver(View view) {
        parentView = view;
        roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        currentAccount = UserConfig.selectedAccount;
    }

    public void cancelLoadImage() {
        forceLoding = false;
        ImageLoader.getInstance().cancelLoadingForImageReceiver(this, 0);
        canceledLoading = true;
    }

    public void setForceLoading(boolean value) {
        forceLoding = value;
    }

    public boolean isForceLoding() {
        return forceLoding;
    }

    public void setImage(TLObject path, String filter, Drawable thumb, String ext, int cacheType) {
        setImage(path, null, filter, thumb, null, null, 0, ext, cacheType);
    }

    public void setImage(TLObject path, String filter, Drawable thumb, int size, String ext, int cacheType) {
        setImage(path, null, filter, thumb, null, null, size, ext, cacheType);
    }

    public void setImage(String httpUrl, String filter, Drawable thumb, String ext, int size) {
        setImage(null, httpUrl, filter, thumb, null, null, size, ext, 1);
    }

    public void setImage(TLObject fileLocation, String filter, TLRPC.FileLocation thumbLocation, String thumbFilter, String ext, int cacheType) {
        setImage(fileLocation, null, filter, null, thumbLocation, thumbFilter, 0, ext, cacheType);
    }

    public void setImage(TLObject fileLocation, String filter, TLRPC.FileLocation thumbLocation, String thumbFilter, int size, String ext, int cacheType) {
        setImage(fileLocation, null, filter, null, thumbLocation, thumbFilter, size, ext, cacheType);
    }

    public void setImage(TLObject fileLocation, String httpUrl, String filter, Drawable thumb, TLRPC.FileLocation thumbLocation, String thumbFilter, int size, String ext, int cacheType) {
        if (setImageBackup != null) {
            setImageBackup.fileLocation = null;
            setImageBackup.httpUrl = null;
            setImageBackup.thumbLocation = null;
            setImageBackup.thumb = null;
        }

        if ((fileLocation == null && httpUrl == null && thumbLocation == null)
                || (fileLocation != null && !(fileLocation instanceof TLRPC.TL_fileLocation)
                && !(fileLocation instanceof TLRPC.TL_fileEncryptedLocation)
                && !(fileLocation instanceof TLRPC.TL_document)
                && !(fileLocation instanceof WebFile)
                && !(fileLocation instanceof TLRPC.TL_documentEncrypted)
                && !(fileLocation instanceof SecureDocument))) {
            for (int a = 0; a < 3; a++) {
                recycleBitmap(null, a);
            }
            currentKey = null;
            currentExt = ext;
            currentThumbKey = null;
            currentThumbFilter = null;
            currentImageLocation = null;
            currentHttpUrl = null;
            currentFilter = null;
            currentCacheType = 0;
            staticThumb = thumb;
            currentAlpha = 1;
            currentThumbLocation = null;
            currentSize = 0;
            currentImage = null;
            bitmapShader = null;
            bitmapShaderThumb = null;
            crossfadeShader = null;
            ImageLoader.getInstance().cancelLoadingForImageReceiver(this, 0);
            if (parentView != null) {
                if (invalidateAll) {
                    parentView.invalidate();
                } else {
                    parentView.invalidate(imageX, imageY, imageX + imageW, imageY + imageH);
                }
            }
            if (delegate != null) {
                delegate.didSetImage(this, currentImage != null || currentThumb != null || staticThumb != null, currentImage == null);
            }
            return;
        }

        if (!(thumbLocation instanceof TLRPC.TL_fileLocation) && !(thumbLocation instanceof TLRPC.TL_fileEncryptedLocation)) {
            thumbLocation = null;
        }

        String key = null;
        if (fileLocation != null) {
            if (fileLocation instanceof SecureDocument) {
                SecureDocument document = (SecureDocument) fileLocation;
                key = document.secureFile.dc_id + "_" + document.secureFile.id;
            } else if (fileLocation instanceof TLRPC.FileLocation) {
                TLRPC.FileLocation location = (TLRPC.FileLocation) fileLocation;
                key = location.volume_id + "_" + location.local_id;
            } else if (fileLocation instanceof WebFile) {
                WebFile location = (WebFile) fileLocation;
                key = Utilities.MD5(location.url);
            } else {
                TLRPC.Document location = (TLRPC.Document) fileLocation;
                if (location.dc_id != 0) {
                    if (location.version == 0) {
                        key = location.dc_id + "_" + location.id;
                    } else {
                        key = location.dc_id + "_" + location.id + "_" + location.version;
                    }
                } else {
                    fileLocation = null;
                }
            }
        } else if (httpUrl != null) {
            key = Utilities.MD5(httpUrl);
        }
        if (key != null) {
            if (filter != null) {
                key += "@" + filter;
            }
        }

        if (currentKey != null && key != null && currentKey.equals(key)) {
            if (delegate != null) {
                delegate.didSetImage(this, currentImage != null || currentThumb != null || staticThumb != null, currentImage == null);
            }
            if (!canceledLoading && !forcePreview) {
                return;
            }
        }

        String thumbKey = null;
        if (thumbLocation != null) {
            thumbKey = thumbLocation.volume_id + "_" + thumbLocation.local_id;
            if (thumbFilter != null) {
                thumbKey += "@" + thumbFilter;
            }
        }

        if (crossfadeWithOldImage) {
            if (currentImage != null) {
                recycleBitmap(thumbKey, 1);
                recycleBitmap(null, 2);
                crossfadeShader = bitmapShader;
                crossfadeImage = currentImage;
                crossfadeKey = currentKey;
                currentImage = null;
                currentKey = null;
            } else if (currentThumb != null) {
                recycleBitmap(key, 0);
                recycleBitmap(null, 2);
                crossfadeShader = bitmapShaderThumb;
                crossfadeImage = currentThumb;
                crossfadeKey = currentThumbKey;
                currentThumb = null;
                currentThumbKey = null;
            } else {
                recycleBitmap(key, 0);
                recycleBitmap(thumbKey, 1);
                recycleBitmap(null, 2);
                crossfadeShader = null;
            }
        } else {
            recycleBitmap(key, 0);
            recycleBitmap(thumbKey, 1);
            recycleBitmap(null, 2);
            crossfadeShader = null;
        }

        currentThumbKey = thumbKey;
        currentKey = key;
        currentExt = ext;
        currentImageLocation = fileLocation;
        currentHttpUrl = httpUrl;
        currentFilter = filter;
        currentThumbFilter = thumbFilter;
        currentSize = size;
        currentCacheType = cacheType;
        currentThumbLocation = thumbLocation;
        staticThumb = thumb;
        bitmapShader = null;
        bitmapShaderThumb = null;
        currentAlpha = 1.0f;

        if (delegate != null) {
            delegate.didSetImage(this, currentImage != null || currentThumb != null || staticThumb != null, currentImage == null);
        }

        ImageLoader.getInstance().loadImageForImageReceiver(this);
        if (parentView != null) {
            if (invalidateAll) {
                parentView.invalidate();
            } else {
                parentView.invalidate(imageX, imageY, imageX + imageW, imageY + imageH);
            }
        }
    }

    public void setColorFilter(ColorFilter filter) {
        colorFilter = filter;
    }

    public void setDelegate(ImageReceiverDelegate delegate) {
        this.delegate = delegate;
    }

    public void setPressed(int value) {
        isPressed = value;
    }

    public boolean getPressed() {
        return isPressed != 0;
    }

    public void setOrientation(int angle, boolean center) {
        while (angle < 0) {
            angle += 360;
        }
        while (angle > 360) {
            angle -= 360;
        }
        orientation = angle;
        centerRotation = center;
    }

    public void setInvalidateAll(boolean value) {
        invalidateAll = value;
    }

    public Drawable getStaticThumb() {
        return staticThumb;
    }

    public int getAnimatedOrientation() {
        if (currentImage instanceof AnimatedFileDrawable) {
            return ((AnimatedFileDrawable) currentImage).getOrientation();
        } else if (staticThumb instanceof AnimatedFileDrawable) {
            return ((AnimatedFileDrawable) staticThumb).getOrientation();
        }
        return 0;
    }

    public int getOrientation() {
        return orientation;
    }

    public void setImageBitmap(Bitmap bitmap) {
        setImageBitmap(bitmap != null ? new BitmapDrawable(null, bitmap) : null);
    }

    public void setImageBitmap(Drawable bitmap) {
        ImageLoader.getInstance().cancelLoadingForImageReceiver(this, 0);
        for (int a = 0; a < 3; a++) {
            recycleBitmap(null, a);
        }
        staticThumb = bitmap;
        if (roundRadius != 0 && bitmap instanceof BitmapDrawable) {
            Bitmap object = ((BitmapDrawable) bitmap).getBitmap();
            bitmapShaderThumb = new BitmapShader(object, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        } else {
            bitmapShaderThumb = null;
        }
        currentThumbLocation = null;
        currentKey = null;
        currentExt = null;
        currentThumbKey = null;
        currentImage = null;
        currentThumbFilter = null;
        currentImageLocation = null;
        currentHttpUrl = null;
        currentFilter = null;
        currentSize = 0;
        currentCacheType = 0;
        bitmapShader = null;
        crossfadeShader = null;
        if (setImageBackup != null) {
            setImageBackup.fileLocation = null;
            setImageBackup.httpUrl = null;
            setImageBackup.thumbLocation = null;
            setImageBackup.thumb = null;
        }
        currentAlpha = 1;
        if (delegate != null) {
            delegate.didSetImage(this, currentThumb != null || staticThumb != null, true);
        }
        if (parentView != null) {
            if (invalidateAll) {
                parentView.invalidate();
            } else {
                parentView.invalidate(imageX, imageY, imageX + imageW, imageY + imageH);
            }
        }
    }

    public void clearImage() {
        for (int a = 0; a < 3; a++) {
            recycleBitmap(null, a);
        }
        if (needsQualityThumb) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.messageThumbGenerated);
        }
        ImageLoader.getInstance().cancelLoadingForImageReceiver(this, 0);
    }

    public void onDetachedFromWindow() {
        if (currentImageLocation != null || currentHttpUrl != null || currentThumbLocation != null || staticThumb != null) {
            if (setImageBackup == null) {
                setImageBackup = new SetImageBackup();
            }
            setImageBackup.fileLocation = currentImageLocation;
            setImageBackup.httpUrl = currentHttpUrl;
            setImageBackup.filter = currentFilter;
            setImageBackup.thumb = staticThumb;
            setImageBackup.thumbLocation = currentThumbLocation;
            setImageBackup.thumbFilter = currentThumbFilter;
            setImageBackup.size = currentSize;
            setImageBackup.ext = currentExt;
            setImageBackup.cacheType = currentCacheType;
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReplacedPhotoInMemCache);
        clearImage();
    }

    public boolean onAttachedToWindow() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReplacedPhotoInMemCache);
        if (needsQualityThumb) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messageThumbGenerated);
        }
        if (setImageBackup != null && (setImageBackup.fileLocation != null || setImageBackup.httpUrl != null || setImageBackup.thumbLocation != null || setImageBackup.thumb != null)) {
            setImage(setImageBackup.fileLocation, setImageBackup.httpUrl, setImageBackup.filter, setImageBackup.thumb, setImageBackup.thumbLocation, setImageBackup.thumbFilter, setImageBackup.size, setImageBackup.ext, setImageBackup.cacheType);
            return true;
        }
        return false;
    }

    private void drawDrawable(Canvas canvas, Drawable drawable, int alpha, BitmapShader shader) {
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;

            Paint paint;
            if (shader != null) {
                paint = roundPaint;
            } else {
                paint = bitmapDrawable.getPaint();
            }
            boolean hasFilter = paint != null && paint.getColorFilter() != null;
            if (hasFilter && isPressed == 0) {
                if (shader != null) {
                    roundPaint.setColorFilter(null);
                } else if (staticThumb != drawable) {
                    bitmapDrawable.setColorFilter(null);
                }
            } else if (!hasFilter && isPressed != 0) {
                if (isPressed == 1) {
                    if (shader != null) {
                        roundPaint.setColorFilter(selectedColorFilter);
                    } else {
                        bitmapDrawable.setColorFilter(selectedColorFilter);
                    }
                } else {
                    if (shader != null) {
                        roundPaint.setColorFilter(selectedGroupColorFilter);
                    } else {
                        bitmapDrawable.setColorFilter(selectedGroupColorFilter);
                    }
                }
            }
            if (colorFilter != null) {
                if (shader != null) {
                    roundPaint.setColorFilter(colorFilter);
                } else {
                    bitmapDrawable.setColorFilter(colorFilter);
                }
            }
            int bitmapW;
            int bitmapH;
            if (bitmapDrawable instanceof AnimatedFileDrawable) {
                if (orientation % 360 == 90 || orientation % 360 == 270) {
                    bitmapW = bitmapDrawable.getIntrinsicHeight();
                    bitmapH = bitmapDrawable.getIntrinsicWidth();
                } else {
                    bitmapW = bitmapDrawable.getIntrinsicWidth();
                    bitmapH = bitmapDrawable.getIntrinsicHeight();
                }
            } else {
                if (orientation % 360 == 90 || orientation % 360 == 270) {
                    bitmapW = bitmapDrawable.getBitmap().getHeight();
                    bitmapH = bitmapDrawable.getBitmap().getWidth();
                } else {
                    bitmapW = bitmapDrawable.getBitmap().getWidth();
                    bitmapH = bitmapDrawable.getBitmap().getHeight();
                }
            }
            float scaleW = bitmapW / (float) imageW;
            float scaleH = bitmapH / (float) imageH;

            if (shader != null) {
                roundPaint.setShader(shader);
                float scale = Math.min(scaleW, scaleH);
                roundRect.set(imageX, imageY, imageX + imageW, imageY + imageH);
                shaderMatrix.reset();
                if (Math.abs(scaleW - scaleH) > 0.00001f) {
                    if (bitmapW / scaleH > imageW) {
                        drawRegion.set(imageX - ((int) (bitmapW / scaleH) - imageW) / 2, imageY, imageX + ((int) (bitmapW / scaleH) + imageW) / 2, imageY + imageH);
                    } else {
                        drawRegion.set(imageX, imageY - ((int) (bitmapH / scaleW) - imageH) / 2, imageX + imageW, imageY + ((int) (bitmapH / scaleW) + imageH) / 2);
                    }
                } else {
                    drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH);
                }
                if (isVisible) {
                    if (Math.abs(scaleW - scaleH) > 0.00001f) {
                        int w = (int) Math.floor(imageW * scale);
                        int h = (int) Math.floor(imageH * scale);
                        bitmapRect.set((bitmapW - w) / 2, (bitmapH - h) / 2, (bitmapW + w) / 2, (bitmapH + h) / 2);
                        shaderMatrix.setRectToRect(bitmapRect, roundRect, Matrix.ScaleToFit.START);
                    } else {
                        bitmapRect.set(0, 0, bitmapW, bitmapH);
                        shaderMatrix.setRectToRect(bitmapRect, roundRect, Matrix.ScaleToFit.FILL);
                    }
                    shader.setLocalMatrix(shaderMatrix);
                    roundPaint.setAlpha(alpha);
                    canvas.drawRoundRect(roundRect, roundRadius, roundRadius, roundPaint);
                }
            } else {
                if (isAspectFit) {
                    float scale = Math.max(scaleW, scaleH);
                    canvas.save();
                    bitmapW /= scale;
                    bitmapH /= scale;
                    drawRegion.set(imageX + (imageW - bitmapW) / 2, imageY + (imageH - bitmapH) / 2, imageX + (imageW + bitmapW) / 2, imageY + (imageH + bitmapH) / 2);
                    bitmapDrawable.setBounds(drawRegion);
                    try {
                        bitmapDrawable.setAlpha(alpha);
                        bitmapDrawable.draw(canvas);
                    } catch (Exception e) {
                        if (bitmapDrawable == currentImage && currentKey != null) {
                            ImageLoader.getInstance().removeImage(currentKey);
                            currentKey = null;
                        } else if (bitmapDrawable == currentThumb && currentThumbKey != null) {
                            ImageLoader.getInstance().removeImage(currentThumbKey);
                            currentThumbKey = null;
                        }
                        setImage(currentImageLocation, currentHttpUrl, currentFilter, currentThumb, currentThumbLocation, currentThumbFilter, currentSize, currentExt, currentCacheType);
                        FileLog.e(e);
                    }
                    canvas.restore();
                } else {
                    if (Math.abs(scaleW - scaleH) > 0.00001f) {
                        canvas.save();
                        canvas.clipRect(imageX, imageY, imageX + imageW, imageY + imageH);

                        if (orientation % 360 != 0) {
                            if (centerRotation) {
                                canvas.rotate(orientation, imageW / 2, imageH / 2);
                            } else {
                                canvas.rotate(orientation, 0, 0);
                            }
                        }

                        if (bitmapW / scaleH > imageW) {
                            bitmapW /= scaleH;
                            drawRegion.set(imageX - (bitmapW - imageW) / 2, imageY, imageX + (bitmapW + imageW) / 2, imageY + imageH);
                        } else {
                            bitmapH /= scaleW;
                            drawRegion.set(imageX, imageY - (bitmapH - imageH) / 2, imageX + imageW, imageY + (bitmapH + imageH) / 2);
                        }
                        if (bitmapDrawable instanceof AnimatedFileDrawable) {
                            ((AnimatedFileDrawable) bitmapDrawable).setActualDrawRect(imageX, imageY, imageW, imageH);
                        }
                        if (orientation % 360 == 90 || orientation % 360 == 270) {
                            int width = (drawRegion.right - drawRegion.left) / 2;
                            int height = (drawRegion.bottom - drawRegion.top) / 2;
                            int centerX = (drawRegion.right + drawRegion.left) / 2;
                            int centerY = (drawRegion.top + drawRegion.bottom) / 2;
                            bitmapDrawable.setBounds(centerX - height, centerY - width, centerX + height, centerY + width);
                        } else {
                            bitmapDrawable.setBounds(drawRegion);
                        }
                        if (isVisible) {
                            try {
                                bitmapDrawable.setAlpha(alpha);
                                bitmapDrawable.draw(canvas);
                            } catch (Exception e) {
                                if (bitmapDrawable == currentImage && currentKey != null) {
                                    ImageLoader.getInstance().removeImage(currentKey);
                                    currentKey = null;
                                } else if (bitmapDrawable == currentThumb && currentThumbKey != null) {
                                    ImageLoader.getInstance().removeImage(currentThumbKey);
                                    currentThumbKey = null;
                                }
                                setImage(currentImageLocation, currentHttpUrl, currentFilter, currentThumb, currentThumbLocation, currentThumbFilter, currentSize, currentExt, currentCacheType);
                                FileLog.e(e);
                            }
                        }

                        canvas.restore();
                    } else {
                        canvas.save();
                        if (orientation % 360 != 0) {
                            if (centerRotation) {
                                canvas.rotate(orientation, imageW / 2, imageH / 2);
                            } else {
                                canvas.rotate(orientation, 0, 0);
                            }
                        }
                        drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH);
                        if (bitmapDrawable instanceof AnimatedFileDrawable) {
                            ((AnimatedFileDrawable) bitmapDrawable).setActualDrawRect(imageX, imageY, imageW, imageH);
                        }
                        if (orientation % 360 == 90 || orientation % 360 == 270) {
                            int width = (drawRegion.right - drawRegion.left) / 2;
                            int height = (drawRegion.bottom - drawRegion.top) / 2;
                            int centerX = (drawRegion.right + drawRegion.left) / 2;
                            int centerY = (drawRegion.top + drawRegion.bottom) / 2;
                            bitmapDrawable.setBounds(centerX - height, centerY - width, centerX + height, centerY + width);
                        } else {
                            bitmapDrawable.setBounds(drawRegion);
                        }
                        if (isVisible) {
                            try {
                                bitmapDrawable.setAlpha(alpha);
                                bitmapDrawable.draw(canvas);
                            } catch (Exception e) {
                                if (bitmapDrawable == currentImage && currentKey != null) {
                                    ImageLoader.getInstance().removeImage(currentKey);
                                    currentKey = null;
                                } else if (bitmapDrawable == currentThumb && currentThumbKey != null) {
                                    ImageLoader.getInstance().removeImage(currentThumbKey);
                                    currentThumbKey = null;
                                }
                                setImage(currentImageLocation, currentHttpUrl, currentFilter, currentThumb, currentThumbLocation, currentThumbFilter, currentSize, currentExt, currentCacheType);
                                FileLog.e(e);
                            }
                        }
                        canvas.restore();
                    }
                }
            }
        } else {
            drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH);
            drawable.setBounds(drawRegion);
            if (isVisible) {
                try {
                    drawable.setAlpha(alpha);
                    drawable.draw(canvas);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    private void checkAlphaAnimation(boolean skip) {
        if (manualAlphaAnimator) {
            return;
        }
        if (currentAlpha != 1) {
            if (!skip) {
                long currentTime = System.currentTimeMillis();
                long dt = currentTime - lastUpdateAlphaTime;
                if (dt > 18) {
                    dt = 18;
                }
                currentAlpha += dt / 150.0f;
                if (currentAlpha > 1) {
                    currentAlpha = 1;
                    if (crossfadeImage != null) {
                        recycleBitmap(null, 2);
                        crossfadeShader = null;
                    }
                }
            }
            lastUpdateAlphaTime = System.currentTimeMillis();
            if (parentView != null) {
                if (invalidateAll) {
                    parentView.invalidate();
                } else {
                    parentView.invalidate(imageX, imageY, imageX + imageW, imageY + imageH);
                }
            }
        }
    }

    public boolean draw(Canvas canvas) {
        try {
            Drawable drawable = null;
            boolean animationNotReady = currentImage instanceof AnimatedFileDrawable && !((AnimatedFileDrawable) currentImage).hasBitmap();
            boolean isThumb = false;
            BitmapShader customShader = null;
            if (!forcePreview && currentImage != null && !animationNotReady) {
                drawable = currentImage;
            } else if (crossfadeImage != null) {
                drawable = crossfadeImage;
                customShader = crossfadeShader;
            } else if (staticThumb instanceof BitmapDrawable) {
                drawable = staticThumb;
                isThumb = true;
            } else if (currentThumb != null) {
                drawable = currentThumb;
                isThumb = true;
            }
            if (drawable != null) {
                if (crossfadeAlpha != 0) {
                    if (crossfadeWithThumb && animationNotReady) {
                        drawDrawable(canvas, drawable, (int) (overrideAlpha * 255), bitmapShaderThumb);
                    } else {
                        if (crossfadeWithThumb && currentAlpha != 1.0f) {
                            Drawable thumbDrawable = null;
                            BitmapShader customThumbShader = null;
                            if (drawable == currentImage) {
                                if (crossfadeImage != null) {
                                    thumbDrawable = crossfadeImage;
                                    customThumbShader = crossfadeShader;
                                } else if (staticThumb != null) {
                                    thumbDrawable = staticThumb;
                                } else if (currentThumb != null) {
                                    thumbDrawable = currentThumb;
                                }
                            } else if (drawable == currentThumb) {
                                if (staticThumb != null) {
                                    thumbDrawable = staticThumb;
                                }
                            }
                            if (thumbDrawable != null) {
                                drawDrawable(canvas, thumbDrawable, (int) (overrideAlpha * 255), customThumbShader != null ? customThumbShader : bitmapShaderThumb);
                            }
                        }
                        drawDrawable(canvas, drawable, (int) (overrideAlpha * currentAlpha * 255), customShader != null ? customShader : (isThumb ? bitmapShaderThumb : bitmapShader));
                    }
                } else {
                    drawDrawable(canvas, drawable, (int) (overrideAlpha * 255), customShader != null ? customShader : (isThumb ? bitmapShaderThumb : bitmapShader));
                }

                checkAlphaAnimation(animationNotReady && crossfadeWithThumb);
                return true;
            } else if (staticThumb != null) {
                drawDrawable(canvas, staticThumb, 255, null);
                checkAlphaAnimation(animationNotReady);
                return true;
            } else {
                checkAlphaAnimation(animationNotReady);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public void setManualAlphaAnimator(boolean value) {
        manualAlphaAnimator = value;
    }

    public float getCurrentAlpha() {
        return currentAlpha;
    }

    public void setCurrentAlpha(float value) {
        currentAlpha = value;
    }

    public Bitmap getBitmap() {
        if (currentImage instanceof AnimatedFileDrawable) {
            return ((AnimatedFileDrawable) currentImage).getAnimatedBitmap();
        } else if (staticThumb instanceof AnimatedFileDrawable) {
            return ((AnimatedFileDrawable) staticThumb).getAnimatedBitmap();
        } else if (currentImage instanceof BitmapDrawable) {
            return ((BitmapDrawable) currentImage).getBitmap();
        } else if (currentThumb instanceof BitmapDrawable) {
            return ((BitmapDrawable) currentThumb).getBitmap();
        } else if (staticThumb instanceof BitmapDrawable) {
            return ((BitmapDrawable) staticThumb).getBitmap();
        }
        return null;
    }

    public BitmapHolder getBitmapSafe() {
        Bitmap bitmap = null;
        String key = null;
        if (currentImage instanceof AnimatedFileDrawable) {
            bitmap = ((AnimatedFileDrawable) currentImage).getAnimatedBitmap();
        } else if (staticThumb instanceof AnimatedFileDrawable) {
            bitmap = ((AnimatedFileDrawable) staticThumb).getAnimatedBitmap();
        } else if (currentImage instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) currentImage).getBitmap();
            key = currentKey;
        } else if (currentThumb instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) currentThumb).getBitmap();
            key = currentThumbKey;
        } else if (staticThumb instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) staticThumb).getBitmap();
        }
        if (bitmap != null) {
            return new BitmapHolder(bitmap, key);
        }
        return null;
    }

    public Bitmap getThumbBitmap() {
        if (currentThumb instanceof BitmapDrawable) {
            return ((BitmapDrawable) currentThumb).getBitmap();
        } else if (staticThumb instanceof BitmapDrawable) {
            return ((BitmapDrawable) staticThumb).getBitmap();
        }
        return null;
    }

    public BitmapHolder getThumbBitmapSafe() {
        Bitmap bitmap = null;
        String key = null;
        if (currentThumb instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) currentThumb).getBitmap();
            key = currentThumbKey;
        } else if (staticThumb instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) staticThumb).getBitmap();
        }
        if (bitmap != null) {
            return new BitmapHolder(bitmap, key);
        }
        return null;
    }

    public int getBitmapWidth() {
        if (currentImage instanceof AnimatedFileDrawable) {
            return orientation % 360 == 0 || orientation % 360 == 180 ? currentImage.getIntrinsicWidth() : currentImage.getIntrinsicHeight();
        } else if (staticThumb instanceof AnimatedFileDrawable) {
            return orientation % 360 == 0 || orientation % 360 == 180 ? staticThumb.getIntrinsicWidth() : staticThumb.getIntrinsicHeight();
        }
        Bitmap bitmap = getBitmap();
        if (bitmap == null) {
            if (staticThumb != null) {
                return staticThumb.getIntrinsicWidth();
            }
            return 1;
        }
        return orientation % 360 == 0 || orientation % 360 == 180 ? bitmap.getWidth() : bitmap.getHeight();
    }

    public int getBitmapHeight() {
        if (currentImage instanceof AnimatedFileDrawable) {
            return orientation % 360 == 0 || orientation % 360 == 180 ? currentImage.getIntrinsicHeight() : currentImage.getIntrinsicWidth();
        } else if (staticThumb instanceof AnimatedFileDrawable) {
            return orientation % 360 == 0 || orientation % 360 == 180 ? staticThumb.getIntrinsicHeight() : staticThumb.getIntrinsicWidth();
        }
        Bitmap bitmap = getBitmap();
        if (bitmap == null) {
            if (staticThumb != null) {
                return staticThumb.getIntrinsicHeight();
            }
            return 1;
        }
        return orientation % 360 == 0 || orientation % 360 == 180 ? bitmap.getHeight() : bitmap.getWidth();
    }

    public void setVisible(boolean value, boolean invalidate) {
        if (isVisible == value) {
            return;
        }
        isVisible = value;
        if (invalidate && parentView != null) {
            if (invalidateAll) {
                parentView.invalidate();
            } else {
                parentView.invalidate(imageX, imageY, imageX + imageW, imageY + imageH);
            }
        }
    }

    public boolean getVisible() {
        return isVisible;
    }

    public void setAlpha(float value) {
        overrideAlpha = value;
    }

    public void setCrossfadeAlpha(byte value) {
        crossfadeAlpha = value;
    }

    public boolean hasImage() {
        return currentImage != null || currentThumb != null || currentKey != null || currentHttpUrl != null || staticThumb != null;
    }

    public boolean hasBitmapImage() {
        return currentImage != null || currentThumb != null || staticThumb != null;
    }

    public boolean hasNotThumb() {
        return currentImage != null;
    }

    public void setAspectFit(boolean value) {
        isAspectFit = value;
    }

    public void setParentView(View view) {
        parentView = view;
        if (currentImage instanceof AnimatedFileDrawable) {
            AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) currentImage;
            fileDrawable.setParentView(parentView);
        }
    }

    public void setImageX(int x) {
        imageX = x;
    }

    public void setImageY(int y) {
        imageY = y;
    }

    public void setImageWidth(int width) {
        imageW = width;
    }

    public void setImageCoords(int x, int y, int width, int height) {
        imageX = x;
        imageY = y;
        imageW = width;
        imageH = height;
    }

    public float getCenterX() {
        return imageX + imageW / 2.0f;
    }

    public float getCenterY() {
        return imageY + imageH / 2.0f;
    }

    public int getImageX() {
        return imageX;
    }

    public int getImageX2() {
        return imageX + imageW;
    }

    public int getImageY() {
        return imageY;
    }

    public int getImageY2() {
        return imageY + imageH;
    }

    public int getImageWidth() {
        return imageW;
    }

    public int getImageHeight() {
        return imageH;
    }

    public String getExt() {
        return currentExt;
    }

    public boolean isInsideImage(float x, float y) {
        return x >= imageX && x <= imageX + imageW && y >= imageY && y <= imageY + imageH;
    }

    public Rect getDrawRegion() {
        return drawRegion;
    }

    public String getFilter() {
        return currentFilter;
    }

    public String getThumbFilter() {
        return currentThumbFilter;
    }

    public String getKey() {
        return currentKey;
    }

    public String getThumbKey() {
        return currentThumbKey;
    }

    public int getSize() {
        return currentSize;
    }

    public TLObject getImageLocation() {
        return currentImageLocation;
    }

    public TLRPC.FileLocation getThumbLocation() {
        return currentThumbLocation;
    }

    public String getHttpImageLocation() {
        return currentHttpUrl;
    }

    public int getCacheType() {
        return currentCacheType;
    }

    public void setForcePreview(boolean value) {
        forcePreview = value;
    }

    public void setForceCrossfade(boolean value) {
        forceCrossfade = value;
    }

    public boolean isForcePreview() {
        return forcePreview;
    }

    public void setRoundRadius(int value) {
        roundRadius = value;
    }

    public void setCurrentAccount(int value) {
        currentAccount = value;
    }

    public int getRoundRadius() {
        return roundRadius;
    }

    public void setParentMessageObject(MessageObject messageObject) {
        parentMessageObject = messageObject;
    }

    public MessageObject getParentMessageObject() {
        return parentMessageObject;
    }

    public void setNeedsQualityThumb(boolean value) {
        needsQualityThumb = value;
        if (needsQualityThumb) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messageThumbGenerated);
        } else {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.messageThumbGenerated);
        }
    }

    public void setCrossfadeWithOldImage(boolean value) {
        crossfadeWithOldImage = value;
    }

    public boolean isNeedsQualityThumb() {
        return needsQualityThumb;
    }

    public int getcurrentAccount() {
        return currentAccount;
    }

    public void setShouldGenerateQualityThumb(boolean value) {
        shouldGenerateQualityThumb = value;
    }

    public boolean isShouldGenerateQualityThumb() {
        return shouldGenerateQualityThumb;
    }

    public void setAllowStartAnimation(boolean value) {
        allowStartAnimation = value;
    }

    public void setAllowDecodeSingleFrame(boolean value) {
        allowDecodeSingleFrame = value;
    }

    public boolean isAllowStartAnimation() {
        return allowStartAnimation;
    }

    public void startAnimation() {
        if (currentImage instanceof AnimatedFileDrawable) {
            ((AnimatedFileDrawable) currentImage).start();
        }
    }

    public void stopAnimation() {
        if (currentImage instanceof AnimatedFileDrawable) {
            ((AnimatedFileDrawable) currentImage).stop();
        }
    }

    public boolean isAnimationRunning() {
        return currentImage instanceof AnimatedFileDrawable && ((AnimatedFileDrawable) currentImage).isRunning();
    }

    public AnimatedFileDrawable getAnimation() {
        return currentImage instanceof AnimatedFileDrawable ? (AnimatedFileDrawable) currentImage : null;
    }

    protected int getTag(boolean thumb) {
        if (thumb) {
            return thumbTag;
        } else {
            return tag;
        }
    }

    protected void setTag(int value, boolean thumb) {
        if (thumb) {
            thumbTag = value;
        } else {
            tag = value;
        }
    }

    public void setParam(int value) {
        param = value;
    }

    public int getParam() {
        return param;
    }

    protected boolean setImageBitmapByKey(BitmapDrawable bitmap, String key, boolean thumb, boolean memCache) {
        if (bitmap == null || key == null) {
            return false;
        }
        if (!thumb) {
            if (currentKey == null || !key.equals(currentKey)) {
                return false;
            }
            if (!(bitmap instanceof AnimatedFileDrawable)) {
                ImageLoader.getInstance().incrementUseCount(currentKey);
            }
            currentImage = bitmap;
            if (roundRadius != 0 && bitmap instanceof BitmapDrawable) {
                if (bitmap instanceof AnimatedFileDrawable) {
                    ((AnimatedFileDrawable) bitmap).setRoundRadius(roundRadius);
                } else {
                    Bitmap object = bitmap.getBitmap();
                    bitmapShader = new BitmapShader(object, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                }
            } else {
                bitmapShader = null;
            }

            if (!memCache && !forcePreview || forceCrossfade) {
                if (currentThumb == null && staticThumb == null || currentAlpha == 1.0f || forceCrossfade) {
                    currentAlpha = 0.0f;
                    lastUpdateAlphaTime = System.currentTimeMillis();
                    crossfadeWithThumb = currentThumb != null || staticThumb != null;
                }
            } else {
                currentAlpha = 1.0f;
            }
            if (bitmap instanceof AnimatedFileDrawable) {
                AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) bitmap;
                fileDrawable.setParentView(parentView);
                if (allowStartAnimation) {
                    fileDrawable.start();
                } else {
                    fileDrawable.setAllowDecodeSingleFrame(allowDecodeSingleFrame);
                }
            }

            if (parentView != null) {
                if (invalidateAll) {
                    parentView.invalidate();
                } else {
                    parentView.invalidate(imageX, imageY, imageX + imageW, imageY + imageH);
                }
            }
        } else if (currentThumb == null && (currentImage == null || (currentImage instanceof AnimatedFileDrawable && !((AnimatedFileDrawable) currentImage).hasBitmap()) || forcePreview)) {
            if (currentThumbKey == null || !key.equals(currentThumbKey)) {
                return false;
            }
            ImageLoader.getInstance().incrementUseCount(currentThumbKey);

            currentThumb = bitmap;

            if (roundRadius != 0 && bitmap instanceof BitmapDrawable) {
                if (bitmap instanceof AnimatedFileDrawable) {
                    ((AnimatedFileDrawable) bitmap).setRoundRadius(roundRadius);
                } else {
                    Bitmap object = bitmap.getBitmap();
                    bitmapShaderThumb = new BitmapShader(object, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                }
            } else {
                bitmapShaderThumb = null;
            }

            if (!memCache && crossfadeAlpha != 2) {
                if (parentMessageObject != null && parentMessageObject.isRoundVideo() && parentMessageObject.isSending()) {
                    currentAlpha = 1.0f;
                } else {
                    currentAlpha = 0.0f;
                    lastUpdateAlphaTime = System.currentTimeMillis();
                    crossfadeWithThumb = staticThumb != null && currentKey == null;
                }
            } else {
                currentAlpha = 1.0f;
            }

            if (!(staticThumb instanceof BitmapDrawable) && parentView != null) {
                if (invalidateAll) {
                    parentView.invalidate();
                } else {
                    parentView.invalidate(imageX, imageY, imageX + imageW, imageY + imageH);
                }
            }
        }

        if (delegate != null) {
            delegate.didSetImage(this, currentImage != null || currentThumb != null || staticThumb != null, currentImage == null);
        }
        return true;
    }

    private void recycleBitmap(String newKey, int type) {
        String key;
        Drawable image;
        if (type == 2) {
            key = crossfadeKey;
            image = crossfadeImage;
        } else if (type == 1) {
            key = currentThumbKey;
            image = currentThumb;
        } else {
            key = currentKey;
            image = currentImage;
        }
        if (key != null && key.startsWith("-")) {
            String replacedKey = ImageLoader.getInstance().getReplacedKey(key);
            if (replacedKey != null) {
                key = replacedKey;
            }
        }
        String replacedKey = ImageLoader.getInstance().getReplacedKey(key);
        if (key != null && (newKey == null || !newKey.equals(key)) && image != null) {
            if (image instanceof AnimatedFileDrawable) {
                AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) image;
                fileDrawable.recycle();
            } else if (image instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) image).getBitmap();
                boolean canDelete = ImageLoader.getInstance().decrementUseCount(key);
                if (!ImageLoader.getInstance().isInCache(key)) {
                    if (canDelete) {
                        bitmap.recycle();
                    }
                }
            }
        }
        if (type == 2) {
            crossfadeKey = null;
            crossfadeImage = null;
        } else if (type == 1) {
            currentThumb = null;
            currentThumbKey = null;
        } else {
            currentImage = null;
            currentKey = null;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messageThumbGenerated) {
            String key = (String) args[1];
            if (currentThumbKey != null && currentThumbKey.equals(key)) {
                if (currentThumb == null) {
                    ImageLoader.getInstance().incrementUseCount(currentThumbKey);
                }
                currentThumb = (BitmapDrawable) args[0];
                if (roundRadius != 0 && currentImage == null && currentThumb instanceof BitmapDrawable && !(currentThumb instanceof AnimatedFileDrawable)) {
                    Bitmap object = ((BitmapDrawable) currentThumb).getBitmap();
                    bitmapShaderThumb = new BitmapShader(object, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                } else {
                    bitmapShaderThumb = null;
                }
                if (staticThumb instanceof BitmapDrawable) {
                    staticThumb = null;
                }
                if (parentView != null) {
                    if (invalidateAll) {
                        parentView.invalidate();
                    } else {
                        parentView.invalidate(imageX, imageY, imageX + imageW, imageY + imageH);
                    }
                }
            }
        } else if (id == NotificationCenter.didReplacedPhotoInMemCache) {
            String oldKey = (String) args[0];
            if (currentKey != null && currentKey.equals(oldKey)) {
                currentKey = (String) args[1];
                currentImageLocation = (TLRPC.FileLocation) args[2];
            }
            if (currentThumbKey != null && currentThumbKey.equals(oldKey)) {
                currentThumbKey = (String) args[1];
                currentThumbLocation = (TLRPC.FileLocation) args[2];
            }
            if (setImageBackup != null) {
                if (currentKey != null && currentKey.equals(oldKey)) {
                    currentKey = (String) args[1];
                    currentImageLocation = (TLRPC.FileLocation) args[2];
                }
                if (currentThumbKey != null && currentThumbKey.equals(oldKey)) {
                    currentThumbKey = (String) args[1];
                    currentThumbLocation = (TLRPC.FileLocation) args[2];
                }
            }
        }
    }
}
