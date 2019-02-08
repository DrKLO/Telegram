/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
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
import android.support.annotation.Keep;
import android.view.View;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.RecyclableDrawable;

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
        public Object fileLocation;
        public String filter;
        public Drawable thumb;
        public Object thumbLocation;
        public String thumbFilter;
        public int size;
        public int cacheType;
        public Object parentObject;
        public String ext;
    }

    private int currentAccount;
    private View parentView;
    private int tag;
    private int thumbTag;
    private int param;
    private Object currentParentObject;
    private boolean canceledLoading;
    private static PorterDuffColorFilter selectedColorFilter = new PorterDuffColorFilter(0xffdddddd, PorterDuff.Mode.MULTIPLY);
    private static PorterDuffColorFilter selectedGroupColorFilter = new PorterDuffColorFilter(0xffbbbbbb, PorterDuff.Mode.MULTIPLY);
    private boolean forceLoding;

    private SetImageBackup setImageBackup;

    private Object currentImageLocation;
    private String currentKey;
    private String currentThumbKey;
    private String currentFilter;
    private String currentThumbFilter;
    private String currentExt;
    private Object currentThumbLocation;
    private int currentSize;
    private int currentCacheType;
    private Drawable currentImage;
    private Drawable currentThumb;
    private Drawable staticThumb;
    private boolean allowStartAnimation = true;
    private boolean allowDecodeSingleFrame;

    private boolean crossfadeWithOldImage;
    private boolean crossfadingWithThumb;
    private Drawable crossfadeImage;
    private String crossfadeKey;
    private BitmapShader crossfadeShader;

    private boolean needsQualityThumb;
    private boolean shouldGenerateQualityThumb;
    private boolean currentKeyQuality;
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
    private int thumbOrientation;
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
        roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
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

    public void setImage(TLObject path, String filter, Drawable thumb, String ext, Object parentObject, int cacheType) {
        setImage(path, filter, thumb, null, null, 0, ext, parentObject, cacheType);
    }

    public void setImage(TLObject path, String filter, Drawable thumb, int size, String ext, Object parentObject, int cacheType) {
        setImage(path, filter, thumb, null, null, size, ext, parentObject, cacheType);
    }

    public void setImage(String httpUrl, String filter, Drawable thumb, String ext, int size) {
        setImage(httpUrl, filter, thumb, null, null, size, ext, null, 1);
    }

    public void setImage(TLObject fileLocation, String filter, TLObject thumbLocation, String thumbFilter, String ext, Object parentObject, int cacheType) {
        setImage(fileLocation, filter, null, thumbLocation, thumbFilter, 0, ext, parentObject, cacheType);
    }

    public void setImage(TLObject fileLocation, String filter, TLObject thumbLocation, String thumbFilter, int size, String ext, Object parentObject, int cacheType) {
        setImage(fileLocation, filter, null, thumbLocation, thumbFilter, size, ext, parentObject, cacheType);
    }

    public void setImage(Object fileLocation, String filter, Drawable thumb, Object thumbLocation, String thumbFilter, int size, String ext, Object parentObject, int cacheType) {
        if (setImageBackup != null) {
            setImageBackup.fileLocation = null;
            setImageBackup.thumbLocation = null;
            setImageBackup.thumb = null;
        }

        if ((fileLocation == null && thumbLocation == null)
                || (fileLocation != null && !(fileLocation instanceof TLRPC.TL_fileLocation)
                && !(fileLocation instanceof TLRPC.TL_fileEncryptedLocation)
                && !(fileLocation instanceof TLRPC.TL_document)
                && !(fileLocation instanceof WebFile)
                && !(fileLocation instanceof TLRPC.TL_documentEncrypted)
                && !(fileLocation instanceof TLRPC.PhotoSize)
                && !(fileLocation instanceof SecureDocument)
                && !(fileLocation instanceof String))) {
            for (int a = 0; a < 3; a++) {
                recycleBitmap(null, a);
            }
            currentKey = null;
            currentExt = ext;
            currentThumbKey = null;
            currentThumbFilter = null;
            currentImageLocation = null;
            currentFilter = null;
            currentParentObject = null;
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

        if (!(thumbLocation instanceof String) && !(thumbLocation instanceof TLRPC.PhotoSize) && !(thumbLocation instanceof TLRPC.TL_fileLocation) && !(thumbLocation instanceof TLRPC.TL_fileEncryptedLocation)) {
            thumbLocation = null;
        }

        String key = null;
        if (fileLocation instanceof SecureDocument) {
            SecureDocument document = (SecureDocument) fileLocation;
            key = document.secureFile.dc_id + "_" + document.secureFile.id;
        } else if (fileLocation instanceof TLRPC.FileLocation) {
            TLRPC.FileLocation location = (TLRPC.FileLocation) fileLocation;
            key = location.volume_id + "_" + location.local_id;
        } else if (fileLocation instanceof TLRPC.TL_photoStrippedSize) {
            TLRPC.TL_photoStrippedSize location = (TLRPC.TL_photoStrippedSize) fileLocation;
            key = "stripped" + FileRefController.getKeyForParentObject(parentObject);
        } else if (fileLocation instanceof TLRPC.TL_photoSize || fileLocation instanceof TLRPC.TL_photoCachedSize) {
            TLRPC.PhotoSize photoSize = (TLRPC.PhotoSize) fileLocation;
            key = photoSize.location.volume_id + "_" + photoSize.location.local_id;
        } else if (fileLocation instanceof WebFile) {
            WebFile location = (WebFile) fileLocation;
            key = Utilities.MD5(location.url);
        } else if (fileLocation instanceof TLRPC.Document) {
            TLRPC.Document location = (TLRPC.Document) fileLocation;
            if (location.dc_id != 0) {
                key = location.dc_id + "_" + location.id;
            } else {
                fileLocation = null;
            }
        } else if (fileLocation instanceof String) {
            key = Utilities.MD5((String) fileLocation);
        }

        currentKeyQuality = false;
        if (key == null && needsQualityThumb && parentObject instanceof MessageObject) {
            TLRPC.Document document = ((MessageObject) parentObject).getDocument();
            if (document != null && document.dc_id != 0 && document.id != 0) {
                key = "q_" + document.dc_id + "_" + document.id;
                currentKeyQuality = true;
            }
        }
        if (key != null && filter != null) {
            key += "@" + filter;
        }

        if (currentKey != null && currentKey.equals(key)) {
            if (delegate != null) {
                delegate.didSetImage(this, currentImage != null || currentThumb != null || staticThumb != null, currentImage == null);
            }
            if (!canceledLoading && !forcePreview) {
                return;
            }
        }

        String thumbKey = null;
        if (thumbLocation instanceof TLRPC.FileLocation) {
            TLRPC.FileLocation location = (TLRPC.FileLocation) thumbLocation;
            thumbKey = location.volume_id + "_" + location.local_id;
        } else if (thumbLocation instanceof TLRPC.TL_photoStrippedSize) {
            TLRPC.TL_photoStrippedSize location = (TLRPC.TL_photoStrippedSize) thumbLocation;
            thumbKey = "stripped" + FileRefController.getKeyForParentObject(parentObject);
        } else if (thumbLocation instanceof TLRPC.TL_photoSize || thumbLocation instanceof TLRPC.TL_photoCachedSize) {
            TLRPC.PhotoSize photoSize = (TLRPC.PhotoSize) thumbLocation;
            thumbKey = photoSize.location.volume_id + "_" + photoSize.location.local_id;
        } else if (thumbLocation instanceof String) {
            thumbKey = Utilities.MD5((String) thumbLocation);
        }
        if (thumbKey != null && thumbFilter != null) {
            thumbKey += "@" + thumbFilter;
        }

        if (crossfadeWithOldImage) {
            if (currentImage != null) {
                recycleBitmap(thumbKey, 1);
                recycleBitmap(null, 2);
                crossfadeShader = bitmapShader;
                crossfadeImage = currentImage;
                crossfadeKey = currentKey;
                crossfadingWithThumb = false;
                currentImage = null;
                currentKey = null;
            } else if (currentThumb != null) {
                recycleBitmap(key, 0);
                recycleBitmap(null, 2);
                crossfadeShader = bitmapShaderThumb;
                crossfadeImage = currentThumb;
                crossfadeKey = currentThumbKey;
                crossfadingWithThumb = false;
                currentThumb = null;
                currentThumbKey = null;
            } else if (staticThumb != null) {
                recycleBitmap(key, 0);
                recycleBitmap(thumbKey, 1);
                recycleBitmap(null, 2);
                crossfadeShader = bitmapShaderThumb;
                crossfadeImage = staticThumb;
                crossfadingWithThumb = false;
                crossfadeKey = null;
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

        currentParentObject = parentObject;
        currentThumbKey = thumbKey;
        currentKey = key;
        currentExt = ext;
        currentImageLocation = fileLocation;
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

    public boolean canInvertBitmap() {
        return currentImage instanceof ExtendedBitmapDrawable || currentThumb instanceof ExtendedBitmapDrawable || staticThumb instanceof ExtendedBitmapDrawable;
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
        orientation = thumbOrientation = angle;
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
        } else if (currentImage instanceof ExtendedBitmapDrawable) {
            return ((ExtendedBitmapDrawable) currentImage).getOrientation();
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

        if (crossfadeWithOldImage) {
            if (currentImage != null) {
                recycleBitmap(null, 1);
                recycleBitmap(null, 2);
                crossfadeShader = bitmapShader;
                crossfadeImage = currentImage;
                crossfadeKey = currentKey;
                crossfadingWithThumb = true;
            } else if (currentThumb != null) {
                recycleBitmap(null, 0);
                recycleBitmap(null, 2);
                crossfadeShader = bitmapShaderThumb;
                crossfadeImage = currentThumb;
                crossfadeKey = currentThumbKey;
                crossfadingWithThumb = true;
            } else if (staticThumb != null) {
                recycleBitmap(null, 0);
                recycleBitmap(null, 1);
                recycleBitmap(null, 2);
                crossfadeShader = bitmapShaderThumb;
                crossfadeImage = staticThumb;
                crossfadingWithThumb = true;
                crossfadeKey = null;
            } else {
                for (int a = 0; a < 3; a++) {
                    recycleBitmap(null, a);
                }
                crossfadeShader = null;
            }
        } else {
            for (int a = 0; a < 3; a++) {
                recycleBitmap(null, a);
            }
        }

        if (staticThumb instanceof RecyclableDrawable) {
            RecyclableDrawable drawable = (RecyclableDrawable) staticThumb;
            drawable.recycle();
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
        currentKeyQuality = false;
        currentImage = null;
        currentThumbFilter = null;
        currentImageLocation = null;
        currentFilter = null;
        currentSize = 0;
        currentCacheType = 0;
        bitmapShader = null;
        if (setImageBackup != null) {
            setImageBackup.fileLocation = null;
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
        if (forceCrossfade && crossfadeWithOldImage && crossfadeImage != null) {
            currentAlpha = 0.0f;
            lastUpdateAlphaTime = System.currentTimeMillis();
            crossfadeWithThumb = currentThumb != null || staticThumb != null;
        }
    }

    public void clearImage() {
        for (int a = 0; a < 3; a++) {
            recycleBitmap(null, a);
        }
        ImageLoader.getInstance().cancelLoadingForImageReceiver(this, 0);
    }

    public void onDetachedFromWindow() {
        if (currentImageLocation != null || currentThumbLocation != null || staticThumb != null) {
            if (setImageBackup == null) {
                setImageBackup = new SetImageBackup();
            }
            setImageBackup.fileLocation = currentImageLocation;
            setImageBackup.filter = currentFilter;
            setImageBackup.thumb = staticThumb;
            setImageBackup.thumbLocation = currentThumbLocation;
            setImageBackup.thumbFilter = currentThumbFilter;
            setImageBackup.size = currentSize;
            setImageBackup.ext = currentExt;
            setImageBackup.cacheType = currentCacheType;
            setImageBackup.parentObject = currentParentObject;
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReplacedPhotoInMemCache);
        clearImage();
    }

    public boolean onAttachedToWindow() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReplacedPhotoInMemCache);
        if (setImageBackup != null && (setImageBackup.fileLocation != null || setImageBackup.thumbLocation != null || setImageBackup.thumb != null)) {
            setImage(setImageBackup.fileLocation, setImageBackup.filter, setImageBackup.thumb, setImageBackup.thumbLocation, setImageBackup.thumbFilter, setImageBackup.size, setImageBackup.ext, setImageBackup.parentObject, setImageBackup.cacheType);
            return true;
        }
        return false;
    }

    private void drawDrawable(Canvas canvas, Drawable drawable, int alpha, BitmapShader shader, boolean thumb) {
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;

            int o = thumb ? thumbOrientation : orientation;
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
                if (o % 360 == 90 || o % 360 == 270) {
                    bitmapW = bitmapDrawable.getIntrinsicHeight();
                    bitmapH = bitmapDrawable.getIntrinsicWidth();
                } else {
                    bitmapW = bitmapDrawable.getIntrinsicWidth();
                    bitmapH = bitmapDrawable.getIntrinsicHeight();
                }
            } else {
                if (o % 360 == 90 || o % 360 == 270) {
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
                            setImage(currentImageLocation, currentFilter, currentThumb, currentThumbLocation, currentThumbFilter, currentSize, currentExt, currentParentObject, currentCacheType);
                            FileLog.e(e);
                        }
                    }
                    canvas.restore();
                } else {
                    if (Math.abs(scaleW - scaleH) > 0.00001f) {
                        canvas.save();
                        canvas.clipRect(imageX, imageY, imageX + imageW, imageY + imageH);

                        if (o % 360 != 0) {
                            if (centerRotation) {
                                canvas.rotate(o, imageW / 2, imageH / 2);
                            } else {
                                canvas.rotate(o, 0, 0);
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
                        if (o % 360 == 90 || o % 360 == 270) {
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
                                setImage(currentImageLocation, currentFilter, currentThumb, currentThumbLocation, currentThumbFilter, currentSize, currentExt, currentParentObject, currentCacheType);
                                FileLog.e(e);
                            }
                        }

                        canvas.restore();
                    } else {
                        canvas.save();
                        if (o % 360 != 0) {
                            if (centerRotation) {
                                canvas.rotate(o, imageW / 2, imageH / 2);
                            } else {
                                canvas.rotate(o, 0, 0);
                            }
                        }
                        drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH);
                        if (bitmapDrawable instanceof AnimatedFileDrawable) {
                            ((AnimatedFileDrawable) bitmapDrawable).setActualDrawRect(imageX, imageY, imageW, imageH);
                        }
                        if (o % 360 == 90 || o % 360 == 270) {
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
                                setImage(currentImageLocation, currentFilter, currentThumb, currentThumbLocation, currentThumbFilter, currentSize, currentExt, currentParentObject, currentCacheType);
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
            } else if (crossfadeImage != null && !crossfadingWithThumb) {
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
                        drawDrawable(canvas, drawable, (int) (overrideAlpha * 255), bitmapShaderThumb, isThumb);
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
                            } else if (drawable == currentThumb || drawable == crossfadeImage) {
                                if (staticThumb != null) {
                                    thumbDrawable = staticThumb;
                                }
                            } else if (drawable == staticThumb) {
                                if (crossfadeImage != null) {
                                    thumbDrawable = crossfadeImage;
                                    customThumbShader = crossfadeShader;
                                }
                            }
                            if (thumbDrawable != null) {
                                drawDrawable(canvas, thumbDrawable, (int) (overrideAlpha * 255), customThumbShader != null ? customThumbShader : bitmapShaderThumb, true);
                            }
                        }
                        drawDrawable(canvas, drawable, (int) (overrideAlpha * currentAlpha * 255), customShader != null ? customShader : (isThumb ? bitmapShaderThumb : bitmapShader), isThumb);
                    }
                } else {
                    drawDrawable(canvas, drawable, (int) (overrideAlpha * 255), customShader != null ? customShader : (isThumb ? bitmapShaderThumb : bitmapShader), isThumb);
                }

                checkAlphaAnimation(animationNotReady && crossfadeWithThumb);
                return true;
            } else if (staticThumb != null) {
                drawDrawable(canvas, staticThumb, (int) (overrideAlpha * 255), null, true);
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

    @Keep
    public void setCurrentAlpha(float value) {
        currentAlpha = value;
    }

    public Drawable getDrawable() {
        if (currentImage != null) {
            return currentImage;
        } else if (currentThumb != null) {
            return currentThumb;
        } else if (staticThumb != null) {
            return staticThumb;
        }
        return null;
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
        return currentImage != null || currentThumb != null || currentKey != null || staticThumb != null;
    }

    public boolean hasBitmapImage() {
        return currentImage != null || currentThumb != null || staticThumb != null;
    }

    public boolean hasNotThumb() {
        return currentImage != null;
    }

    public boolean hasStaticThumb() {
        return staticThumb != null;
    }

    public void setAspectFit(boolean value) {
        isAspectFit = value;
    }

    public boolean isAspectFit() {
        return isAspectFit;
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

    public float getImageAspectRatio() {
        return orientation % 180 != 0 ? drawRegion.height() / (float) drawRegion.width() : drawRegion.width() / (float) drawRegion.height();
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

    public Object getImageLocation() {
        return currentImageLocation;
    }

    public Object getThumbLocation() {
        return currentThumbLocation;
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

    public Object getParentObject() {
        return currentParentObject;
    }

    public void setNeedsQualityThumb(boolean value) {
        needsQualityThumb = value;
    }

    public void setCrossfadeWithOldImage(boolean value) {
        crossfadeWithOldImage = value;
    }

    public boolean isNeedsQualityThumb() {
        return needsQualityThumb;
    }

    public boolean isCurrentKeyQuality() {
        return currentKeyQuality;
    }

    public int getCurrentAccount() {
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
            if (!key.equals(currentKey)) {
                return false;
            }
            if (!(bitmap instanceof AnimatedFileDrawable)) {
                ImageLoader.getInstance().incrementUseCount(currentKey);
            }
            currentImage = bitmap;
            if (bitmap instanceof ExtendedBitmapDrawable) {
                orientation = ((ExtendedBitmapDrawable) bitmap).getOrientation();
            }
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
                    crossfadeWithThumb = crossfadeImage != null || currentThumb != null || staticThumb != null;
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
            if (!key.equals(currentThumbKey)) {
                return false;
            }
            ImageLoader.getInstance().incrementUseCount(currentThumbKey);

            currentThumb = bitmap;
            if (bitmap instanceof ExtendedBitmapDrawable) {
                thumbOrientation = ((ExtendedBitmapDrawable) bitmap).getOrientation();
            }

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
                if (currentParentObject instanceof MessageObject && ((MessageObject) currentParentObject).isRoundVideo() && ((MessageObject) currentParentObject).isSending()) {
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
        if (id == NotificationCenter.didReplacedPhotoInMemCache) {
            String oldKey = (String) args[0];
            if (currentKey != null && currentKey.equals(oldKey)) {
                currentKey = (String) args[1];
                currentImageLocation = args[2];
            }
            if (currentThumbKey != null && currentThumbKey.equals(oldKey)) {
                currentThumbKey = (String) args[1];
                currentThumbLocation = args[2];
            }
            if (setImageBackup != null) {
                if (currentKey != null && currentKey.equals(oldKey)) {
                    currentKey = (String) args[1];
                    currentImageLocation = args[2];
                }
                if (currentThumbKey != null && currentThumbKey.equals(oldKey)) {
                    currentThumbKey = (String) args[1];
                    currentThumbLocation = args[2];
                }
            }
        }
    }
}
