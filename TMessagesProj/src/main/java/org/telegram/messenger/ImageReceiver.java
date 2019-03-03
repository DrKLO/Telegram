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
        private boolean recycleOnRelease;
        public Bitmap bitmap;

        public BitmapHolder(Bitmap b, String k) {
            bitmap = b;
            key = k;
            if (key != null) {
                ImageLoader.getInstance().incrementUseCount(key);
            }
        }

        public BitmapHolder(Bitmap b) {
            bitmap = b;
            recycleOnRelease = true;
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
                if (recycleOnRelease && bitmap != null) {
                    bitmap.recycle();
                }
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
        public Object mediaLocation;
        public String mediaFilter;
        public int size;
        public int cacheType;
        public Object parentObject;
        public String ext;
    }

    public final static int TYPE_IMAGE = 0;
    public final static int TYPE_THUMB = 1;
    private final static int TYPE_CROSSFDADE = 2;
    public final static int TYPE_MEDIA = 3;

    private int currentAccount;
    private View parentView;

    private int param;
    private Object currentParentObject;
    private boolean canceledLoading;
    private static PorterDuffColorFilter selectedColorFilter = new PorterDuffColorFilter(0xffdddddd, PorterDuff.Mode.MULTIPLY);
    private static PorterDuffColorFilter selectedGroupColorFilter = new PorterDuffColorFilter(0xffbbbbbb, PorterDuff.Mode.MULTIPLY);
    private boolean forceLoding;

    private SetImageBackup setImageBackup;

    private Object currentImageLocation;
    private String currentImageKey;
    private String currentImageFilter;
    private int imageTag;
    private Drawable currentImageDrawable;
    private BitmapShader imageShader;
    private int imageOrientation;

    private Object currentThumbLocation;
    private String currentThumbKey;
    private String currentThumbFilter;
    private int thumbTag;
    private Drawable currentThumbDrawable;
    private BitmapShader thumbShader;
    private int thumbOrientation;

    private Object currentMediaLocation;
    private String currentMediaKey;
    private String currentMediaFilter;
    private int mediaTag;
    private Drawable currentMediaDrawable;
    private BitmapShader mediaShader;

    private Drawable staticThumbDrawable;

    private String currentExt;

    private int currentSize;
    private int currentCacheType;
    private boolean allowStartAnimation = true;
    private boolean allowDecodeSingleFrame;

    private boolean crossfadeWithOldImage;
    private boolean crossfadingWithThumb;
    private Drawable crossfadeImage;
    private String crossfadeKey;
    private BitmapShader crossfadeShader;

    private boolean needsQualityThumb;
    private boolean shouldGenerateQualityThumb;
    private TLRPC.Document qulityThumbDocument;
    private boolean currentKeyQuality;
    private boolean invalidateAll;

    private int imageX, imageY, imageW, imageH;
    private Rect drawRegion = new Rect();
    private boolean isVisible = true;
    private boolean isAspectFit;
    private boolean forcePreview;
    private boolean forceCrossfade;
    private int roundRadius;

    private Paint roundPaint;
    private RectF roundRect = new RectF();
    private RectF bitmapRect = new RectF();
    private Matrix shaderMatrix = new Matrix();
    private float overrideAlpha = 1.0f;
    private int isPressed;
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
        ImageLoader.getInstance().cancelLoadingForImageReceiver(this, true);
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

    public void setImage(Object fileLocation, String filter, TLObject thumbLocation, String thumbFilter, int size, String ext, Object parentObject, int cacheType) {
        setImage(fileLocation, filter, null, thumbLocation, thumbFilter, size, ext, parentObject, cacheType);
    }

    public void setImage(Object fileLocation, String filter, Drawable thumb, Object thumbLocation, String thumbFilter, int size, String ext, Object parentObject, int cacheType) {
        setImage(null, null, fileLocation, filter, thumb, thumbLocation, thumbFilter, size, ext, parentObject, cacheType);
    }

    private String getLocationKey(Object fileLocation, Object parentObject) {
        if (fileLocation instanceof SecureDocument) {
            SecureDocument document = (SecureDocument) fileLocation;
            return document.secureFile.dc_id + "_" + document.secureFile.id;
        } else if (fileLocation instanceof TLRPC.FileLocation) {
            TLRPC.FileLocation location = (TLRPC.FileLocation) fileLocation;
            return location.volume_id + "_" + location.local_id;
        } else if (fileLocation instanceof TLRPC.TL_photoStrippedSize) {
            TLRPC.TL_photoStrippedSize location = (TLRPC.TL_photoStrippedSize) fileLocation;
            if (location.bytes.length > 0) {
                return "stripped" + FileRefController.getKeyForParentObject(parentObject);
            }
        } else if (fileLocation instanceof TLRPC.TL_photoSize || fileLocation instanceof TLRPC.TL_photoCachedSize) {
            TLRPC.PhotoSize photoSize = (TLRPC.PhotoSize) fileLocation;
            return photoSize.location.volume_id + "_" + photoSize.location.local_id;
        } else if (fileLocation instanceof WebFile) {
            WebFile location = (WebFile) fileLocation;
            return Utilities.MD5(location.url);
        } else if (fileLocation instanceof TLRPC.Document) {
            TLRPC.Document location = (TLRPC.Document) fileLocation;
            if (location.dc_id != 0) {
                return location.dc_id + "_" + location.id;
            }
        } else if (fileLocation instanceof String) {
            return Utilities.MD5((String) fileLocation);
        }
        return null;
    }

    private boolean isInvalidLocation(Object fileLocation) {
        return fileLocation != null &&
                !(fileLocation instanceof TLRPC.TL_fileLocation)
                && !(fileLocation instanceof TLRPC.TL_fileEncryptedLocation)
                && !(fileLocation instanceof TLRPC.TL_document)
                && !(fileLocation instanceof WebFile)
                && !(fileLocation instanceof TLRPC.TL_documentEncrypted)
                && !(fileLocation instanceof TLRPC.PhotoSize)
                && !(fileLocation instanceof SecureDocument)
                && !(fileLocation instanceof String);
    }

    public void setImage(Object mediaLocation, String mediaFilter, Object fileLocation, String imageFilter, Drawable thumb, Object thumbLocation, String thumbFilter, int size, String ext, Object parentObject, int cacheType) {
        if (setImageBackup != null) {
            setImageBackup.fileLocation = null;
            setImageBackup.thumbLocation = null;
            setImageBackup.mediaLocation = null;
            setImageBackup.thumb = null;
        }

        if ((fileLocation == null && thumbLocation == null && mediaLocation == null) || isInvalidLocation(fileLocation) || isInvalidLocation(mediaLocation)) {
            for (int a = 0; a < 4; a++) {
                recycleBitmap(null, a);
            }
            currentImageLocation = null;
            currentImageKey = null;
            currentImageFilter = null;
            currentMediaLocation = null;
            currentMediaKey = null;
            currentMediaFilter = null;
            currentThumbLocation = null;
            currentThumbKey = null;
            currentThumbFilter = null;

            currentMediaDrawable = null;
            mediaShader = null;
            currentImageDrawable = null;
            imageShader = null;
            thumbShader = null;
            crossfadeShader = null;

            currentExt = ext;
            currentParentObject = null;
            currentCacheType = 0;
            staticThumbDrawable = thumb;
            currentAlpha = 1.0f;
            currentSize = 0;

            ImageLoader.getInstance().cancelLoadingForImageReceiver(this, true);
            if (parentView != null) {
                if (invalidateAll) {
                    parentView.invalidate();
                } else {
                    parentView.invalidate(imageX, imageY, imageX + imageW, imageY + imageH);
                }
            }
            if (delegate != null) {
                delegate.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null);
            }
            return;
        }
        if (isInvalidLocation(thumbLocation)) {
            thumbLocation = null;
        }
        String imageKey = getLocationKey(fileLocation, parentObject);
        if (imageKey == null && fileLocation != null) {
            fileLocation = null;
        }
        currentKeyQuality = false;
        if (imageKey == null && needsQualityThumb && (parentObject instanceof MessageObject || qulityThumbDocument != null)) {
            TLRPC.Document document = qulityThumbDocument != null ? qulityThumbDocument : ((MessageObject) parentObject).getDocument();
            if (document != null && document.dc_id != 0 && document.id != 0) {
                imageKey = "q_" + document.dc_id + "_" + document.id;
                currentKeyQuality = true;
            }
        }
        if (imageKey != null && imageFilter != null) {
            imageKey += "@" + imageFilter;
        }

        String mediaKey = getLocationKey(mediaLocation, parentObject);
        if (mediaKey == null && mediaLocation != null) {
            mediaLocation = null;
        }
        if (mediaKey != null && mediaFilter != null) {
            mediaKey += "@" + mediaFilter;
        }

        if (mediaKey == null && currentImageKey != null && currentImageKey.equals(imageKey) || currentMediaKey != null && currentMediaKey.equals(mediaKey)) {
            if (delegate != null) {
                delegate.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null);
            }
            if (!canceledLoading && !forcePreview) {
                return;
            }
        }

        String thumbKey = getLocationKey(thumbLocation, parentObject);
        if (thumbKey != null && thumbFilter != null) {
            thumbKey += "@" + thumbFilter;
        }

        if (crossfadeWithOldImage) {
            if (currentImageDrawable != null) {
                recycleBitmap(thumbKey, TYPE_THUMB);
                recycleBitmap(null, TYPE_CROSSFDADE);
                recycleBitmap(mediaKey, TYPE_MEDIA);
                crossfadeShader = imageShader;
                crossfadeImage = currentImageDrawable;
                crossfadeKey = currentImageKey;
                crossfadingWithThumb = false;
                currentImageDrawable = null;
                currentImageKey = null;
            } else if (currentThumbDrawable != null) {
                recycleBitmap(imageKey, TYPE_IMAGE);
                recycleBitmap(null, TYPE_CROSSFDADE);
                recycleBitmap(mediaKey, TYPE_MEDIA);
                crossfadeShader = thumbShader;
                crossfadeImage = currentThumbDrawable;
                crossfadeKey = currentThumbKey;
                crossfadingWithThumb = false;
                currentThumbDrawable = null;
                currentThumbKey = null;
            } else if (staticThumbDrawable != null) {
                recycleBitmap(imageKey, TYPE_IMAGE);
                recycleBitmap(thumbKey, TYPE_THUMB);
                recycleBitmap(null, TYPE_CROSSFDADE);
                recycleBitmap(mediaKey, TYPE_MEDIA);
                crossfadeShader = thumbShader;
                crossfadeImage = staticThumbDrawable;
                crossfadingWithThumb = false;
                crossfadeKey = null;
                currentThumbDrawable = null;
                currentThumbKey = null;
            } else {
                recycleBitmap(imageKey, TYPE_IMAGE);
                recycleBitmap(thumbKey, TYPE_THUMB);
                recycleBitmap(null, TYPE_CROSSFDADE);
                recycleBitmap(mediaKey, TYPE_MEDIA);
                crossfadeShader = null;
            }
        } else {
            recycleBitmap(imageKey, TYPE_IMAGE);
            recycleBitmap(thumbKey, TYPE_THUMB);
            recycleBitmap(null, TYPE_CROSSFDADE);
            recycleBitmap(mediaKey, TYPE_MEDIA);
            crossfadeShader = null;
        }

        currentImageLocation = fileLocation;
        currentImageKey = imageKey;
        currentImageFilter = imageFilter;
        currentMediaLocation = mediaLocation;
        currentMediaKey = mediaKey;
        currentMediaFilter = mediaFilter;
        currentThumbLocation = thumbLocation;
        currentThumbKey = thumbKey;
        currentThumbFilter = thumbFilter;

        currentParentObject = parentObject;
        currentExt = ext;
        currentSize = size;
        currentCacheType = cacheType;
        staticThumbDrawable = thumb;
        imageShader = null;
        thumbShader = null;
        mediaShader = null;
        currentAlpha = 1.0f;

        if (delegate != null) {
            delegate.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null);
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
        return currentMediaDrawable instanceof ExtendedBitmapDrawable || currentImageDrawable instanceof ExtendedBitmapDrawable || currentThumbDrawable instanceof ExtendedBitmapDrawable || staticThumbDrawable instanceof ExtendedBitmapDrawable;
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
        imageOrientation = thumbOrientation = angle;
        centerRotation = center;
    }

    public void setInvalidateAll(boolean value) {
        invalidateAll = value;
    }

    public Drawable getStaticThumb() {
        return staticThumbDrawable;
    }

    public int getAnimatedOrientation() {
        AnimatedFileDrawable animation = getAnimation();
        return animation != null ? animation.getOrientation() : 0;
    }

    public int getOrientation() {
        return imageOrientation;
    }

    public void setImageBitmap(Bitmap bitmap) {
        setImageBitmap(bitmap != null ? new BitmapDrawable(null, bitmap) : null);
    }

    public void setImageBitmap(Drawable bitmap) {
        ImageLoader.getInstance().cancelLoadingForImageReceiver(this, true);

        if (crossfadeWithOldImage) {
            if (currentImageDrawable != null) {
                recycleBitmap(null, TYPE_THUMB);
                recycleBitmap(null, TYPE_CROSSFDADE);
                recycleBitmap(null, TYPE_MEDIA);
                crossfadeShader = imageShader;
                crossfadeImage = currentImageDrawable;
                crossfadeKey = currentImageKey;
                crossfadingWithThumb = true;
            } else if (currentThumbDrawable != null) {
                recycleBitmap(null, TYPE_IMAGE);
                recycleBitmap(null, TYPE_CROSSFDADE);
                recycleBitmap(null, TYPE_MEDIA);
                crossfadeShader = thumbShader;
                crossfadeImage = currentThumbDrawable;
                crossfadeKey = currentThumbKey;
                crossfadingWithThumb = true;
            } else if (staticThumbDrawable != null) {
                recycleBitmap(null, TYPE_IMAGE);
                recycleBitmap(null, TYPE_THUMB);
                recycleBitmap(null, TYPE_CROSSFDADE);
                recycleBitmap(null, TYPE_MEDIA);
                crossfadeShader = thumbShader;
                crossfadeImage = staticThumbDrawable;
                crossfadingWithThumb = true;
                crossfadeKey = null;
            } else {
                for (int a = 0; a < 4; a++) {
                    recycleBitmap(null, a);
                }
                crossfadeShader = null;
            }
        } else {
            for (int a = 0; a < 4; a++) {
                recycleBitmap(null, a);
            }
        }

        if (staticThumbDrawable instanceof RecyclableDrawable) {
            RecyclableDrawable drawable = (RecyclableDrawable) staticThumbDrawable;
            drawable.recycle();
        }
        if (bitmap instanceof AnimatedFileDrawable) {
            AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) bitmap;
            fileDrawable.setParentView(parentView);
            if (allowStartAnimation) {
                fileDrawable.start();
            }
            fileDrawable.setAllowDecodeSingleFrame(allowDecodeSingleFrame);
        }
        staticThumbDrawable = bitmap;
        if (roundRadius != 0 && bitmap instanceof BitmapDrawable) {
            if (bitmap instanceof AnimatedFileDrawable) {
                ((AnimatedFileDrawable) bitmap).setRoundRadius(roundRadius);
            } else {
                Bitmap object = ((BitmapDrawable) bitmap).getBitmap();
                thumbShader = new BitmapShader(object, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }
        } else {
            thumbShader = null;
        }
        currentMediaLocation = null;
        currentMediaDrawable = null;
        currentMediaKey = null;
        currentMediaFilter = null;
        mediaShader = null;

        currentImageLocation = null;
        currentImageDrawable = null;
        currentImageKey = null;
        currentImageFilter = null;
        imageShader = null;

        currentThumbLocation = null;
        currentThumbKey = null;
        currentThumbFilter = null;

        currentKeyQuality = false;
        currentExt = null;
        currentSize = 0;
        currentCacheType = 0;
        currentAlpha = 1;

        if (setImageBackup != null) {
            setImageBackup.fileLocation = null;
            setImageBackup.thumbLocation = null;
            setImageBackup.mediaLocation = null;
            setImageBackup.thumb = null;
        }

        if (delegate != null) {
            delegate.didSetImage(this, currentThumbDrawable != null || staticThumbDrawable != null, true);
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
            crossfadeWithThumb = currentThumbDrawable != null || staticThumbDrawable != null;
        }
    }

    public void clearImage() {
        for (int a = 0; a < 4; a++) {
            recycleBitmap(null, a);
        }
        ImageLoader.getInstance().cancelLoadingForImageReceiver(this, true);
    }

    public void onDetachedFromWindow() {
        if (currentImageLocation != null || currentMediaLocation != null || currentThumbLocation != null || staticThumbDrawable != null) {
            if (setImageBackup == null) {
                setImageBackup = new SetImageBackup();
            }
            setImageBackup.mediaLocation = currentMediaLocation;
            setImageBackup.mediaFilter = currentMediaFilter;
            setImageBackup.fileLocation = currentImageLocation;
            setImageBackup.filter = currentImageFilter;
            setImageBackup.thumb = staticThumbDrawable;
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
        if (setImageBackup != null && (setImageBackup.fileLocation != null || setImageBackup.thumbLocation != null || setImageBackup.mediaLocation != null || setImageBackup.thumb != null)) {
            setImage(setImageBackup.mediaLocation, setImageBackup.mediaFilter, setImageBackup.fileLocation, setImageBackup.filter, setImageBackup.thumb, setImageBackup.thumbLocation, setImageBackup.thumbFilter, setImageBackup.size, setImageBackup.ext, setImageBackup.parentObject, setImageBackup.cacheType);
            return true;
        }
        return false;
    }

    private void drawDrawable(Canvas canvas, Drawable drawable, int alpha, BitmapShader shader, int orientation) {
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
                } else if (staticThumbDrawable != drawable) {
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
                if (Math.abs(scaleW - scaleH) > 0.0005f) {
                    if (bitmapW / scaleH > imageW) {
                        drawRegion.set(imageX - ((int) (bitmapW / scaleH) - imageW) / 2, imageY, imageX + ((int) (bitmapW / scaleH) + imageW) / 2, imageY + imageH);
                    } else {
                        drawRegion.set(imageX, imageY - ((int) (bitmapH / scaleW) - imageH) / 2, imageX + imageW, imageY + ((int) (bitmapH / scaleW) + imageH) / 2);
                    }
                } else {
                    drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH);
                }
                if (isVisible) {
                    if (Math.abs(scaleW - scaleH) > 0.0005f) {
                        int w = (int) Math.ceil(imageW * scale);
                        int h = (int) Math.ceil(imageH * scale);
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
                            onBitmapException(bitmapDrawable);
                            FileLog.e(e);
                        }
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
                                onBitmapException(bitmapDrawable);
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
                                onBitmapException(bitmapDrawable);
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

    private void onBitmapException(Drawable bitmapDrawable) {
        if (bitmapDrawable == currentMediaDrawable && currentMediaKey != null) {
            ImageLoader.getInstance().removeImage(currentMediaKey);
            currentMediaKey = null;
        } else if (bitmapDrawable == currentImageDrawable && currentImageKey != null) {
            ImageLoader.getInstance().removeImage(currentImageKey);
            currentImageKey = null;
        } else if (bitmapDrawable == currentThumbDrawable && currentThumbKey != null) {
            ImageLoader.getInstance().removeImage(currentThumbKey);
            currentThumbKey = null;
        }
        setImage(currentMediaLocation, currentMediaFilter, currentImageLocation, currentImageFilter, currentThumbDrawable, currentThumbLocation, currentThumbFilter, currentSize, currentExt, currentParentObject, currentCacheType);
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
            AnimatedFileDrawable animation = getAnimation();
            boolean animationNotReady = animation != null && !animation.hasBitmap();
            int orientation = 0;
            BitmapShader shaderToUse = null;
            if (!forcePreview && currentMediaDrawable != null && !animationNotReady) {
                drawable = currentMediaDrawable;
                shaderToUse = mediaShader;
                orientation = imageOrientation;
            } else if (!forcePreview && currentImageDrawable != null && (!animationNotReady || currentMediaDrawable != null)) {
                drawable = currentImageDrawable;
                shaderToUse = imageShader;
                orientation = imageOrientation;
                animationNotReady = false;
            } else if (crossfadeImage != null && !crossfadingWithThumb) {
                drawable = crossfadeImage;
                shaderToUse = crossfadeShader;
                orientation = imageOrientation;
            } else if (staticThumbDrawable instanceof BitmapDrawable) {
                drawable = staticThumbDrawable;
                shaderToUse = thumbShader;
                orientation = thumbOrientation;
            } else if (currentThumbDrawable != null) {
                drawable = currentThumbDrawable;
                shaderToUse = thumbShader;
                orientation = thumbOrientation;
            }
            if (drawable != null) {
                if (crossfadeAlpha != 0) {
                    if (crossfadeWithThumb && animationNotReady) {
                        drawDrawable(canvas, drawable, (int) (overrideAlpha * 255), shaderToUse, orientation);
                    } else {
                        if (crossfadeWithThumb && currentAlpha != 1.0f) {
                            Drawable thumbDrawable = null;
                            BitmapShader thumbShaderToUse = null;
                            if (drawable == currentImageDrawable || drawable == currentMediaDrawable) {
                                if (crossfadeImage != null) {
                                    thumbDrawable = crossfadeImage;
                                    thumbShaderToUse = crossfadeShader;
                                } else if (currentThumbDrawable != null) {
                                    thumbDrawable = currentThumbDrawable;
                                    thumbShaderToUse = thumbShader;
                                } else if (staticThumbDrawable != null) {
                                    thumbDrawable = staticThumbDrawable;
                                    thumbShaderToUse = thumbShader;
                                }
                            } else if (drawable == currentThumbDrawable || drawable == crossfadeImage) {
                                if (staticThumbDrawable != null) {
                                    thumbDrawable = staticThumbDrawable;
                                    thumbShaderToUse = thumbShader;
                                }
                            } else if (drawable == staticThumbDrawable) {
                                if (crossfadeImage != null) {
                                    thumbDrawable = crossfadeImage;
                                    thumbShaderToUse = crossfadeShader;
                                }
                            }
                            if (thumbDrawable != null) {
                                drawDrawable(canvas, thumbDrawable, (int) (overrideAlpha * 255), thumbShaderToUse, thumbOrientation);
                            }
                        }
                        drawDrawable(canvas, drawable, (int) (overrideAlpha * currentAlpha * 255), shaderToUse, orientation);
                    }
                } else {
                    drawDrawable(canvas, drawable, (int) (overrideAlpha * 255), shaderToUse, orientation);
                }

                checkAlphaAnimation(animationNotReady && crossfadeWithThumb);
                return true;
            } else if (staticThumbDrawable != null) {
                drawDrawable(canvas, staticThumbDrawable, (int) (overrideAlpha * 255), null, thumbOrientation);
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

    public Drawable getDrawable() {
        if (currentMediaDrawable != null) {
            return currentMediaDrawable;
        } else if (currentImageDrawable != null) {
            return currentImageDrawable;
        } else if (currentThumbDrawable != null) {
            return currentThumbDrawable;
        } else if (staticThumbDrawable != null) {
            return staticThumbDrawable;
        }
        return null;
    }

    public Bitmap getBitmap() {
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null && animation.hasBitmap()) {
            return animation.getAnimatedBitmap();
        } else if (currentMediaDrawable instanceof BitmapDrawable && !(currentMediaDrawable instanceof AnimatedFileDrawable)) {
            return ((BitmapDrawable) currentMediaDrawable).getBitmap();
        } else if (currentImageDrawable instanceof BitmapDrawable && !(currentImageDrawable instanceof AnimatedFileDrawable)) {
            return ((BitmapDrawable) currentImageDrawable).getBitmap();
        } else if (currentThumbDrawable instanceof BitmapDrawable && !(currentThumbDrawable instanceof AnimatedFileDrawable)) {
            return ((BitmapDrawable) currentThumbDrawable).getBitmap();
        } else if (staticThumbDrawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) staticThumbDrawable).getBitmap();
        }
        return null;
    }

    public BitmapHolder getBitmapSafe() {
        Bitmap bitmap = null;
        String key = null;
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null && animation.hasBitmap()) {
            bitmap = animation.getAnimatedBitmap();
        } else if (currentMediaDrawable instanceof BitmapDrawable && !(currentMediaDrawable instanceof AnimatedFileDrawable)) {
            bitmap = ((BitmapDrawable) currentMediaDrawable).getBitmap();
            key = currentMediaKey;
        } else if (currentImageDrawable instanceof BitmapDrawable && !(currentImageDrawable instanceof AnimatedFileDrawable)) {
            bitmap = ((BitmapDrawable) currentImageDrawable).getBitmap();
            key = currentImageKey;
        } else if (currentThumbDrawable instanceof BitmapDrawable && !(currentThumbDrawable instanceof AnimatedFileDrawable)) {
            bitmap = ((BitmapDrawable) currentThumbDrawable).getBitmap();
            key = currentThumbKey;
        } else if (staticThumbDrawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) staticThumbDrawable).getBitmap();
        }
        if (bitmap != null) {
            return new BitmapHolder(bitmap, key);
        }
        return null;
    }

    public Bitmap getThumbBitmap() {
        if (currentThumbDrawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) currentThumbDrawable).getBitmap();
        } else if (staticThumbDrawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) staticThumbDrawable).getBitmap();
        }
        return null;
    }

    public BitmapHolder getThumbBitmapSafe() {
        Bitmap bitmap = null;
        String key = null;
        if (currentThumbDrawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) currentThumbDrawable).getBitmap();
            key = currentThumbKey;
        } else if (staticThumbDrawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) staticThumbDrawable).getBitmap();
        }
        if (bitmap != null) {
            return new BitmapHolder(bitmap, key);
        }
        return null;
    }

    public int getBitmapWidth() {
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null) {
            return imageOrientation % 360 == 0 || imageOrientation % 360 == 180 ? animation.getIntrinsicWidth() : animation.getIntrinsicHeight();
        }
        Bitmap bitmap = getBitmap();
        if (bitmap == null) {
            if (staticThumbDrawable != null) {
                return staticThumbDrawable.getIntrinsicWidth();
            }
            return 1;
        }
        return imageOrientation % 360 == 0 || imageOrientation % 360 == 180 ? bitmap.getWidth() : bitmap.getHeight();
    }

    public int getBitmapHeight() {
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null) {
            return imageOrientation % 360 == 0 || imageOrientation % 360 == 180 ? animation.getIntrinsicHeight() : animation.getIntrinsicWidth();
        }
        Bitmap bitmap = getBitmap();
        if (bitmap == null) {
            if (staticThumbDrawable != null) {
                return staticThumbDrawable.getIntrinsicHeight();
            }
            return 1;
        }
        return imageOrientation % 360 == 0 || imageOrientation % 360 == 180 ? bitmap.getHeight() : bitmap.getWidth();
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

    public boolean hasImageSet() {
        return currentImageDrawable != null || currentMediaDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentImageKey != null || currentMediaKey != null;
    }

    public boolean hasBitmapImage() {
        return currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null;
    }

    public boolean hasNotThumb() {
        return currentImageDrawable != null || currentMediaDrawable != null;
    }

    public boolean hasStaticThumb() {
        return staticThumbDrawable != null;
    }

    public void setAspectFit(boolean value) {
        isAspectFit = value;
    }

    public boolean isAspectFit() {
        return isAspectFit;
    }

    public void setParentView(View view) {
        parentView = view;
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null) {
            animation.setParentView(parentView);
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
        return imageOrientation % 180 != 0 ? drawRegion.height() / (float) drawRegion.width() : drawRegion.width() / (float) drawRegion.height();
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

    public String getImageKey() {
        return currentImageKey;
    }

    public String getImageFilter() {
        return currentImageFilter;
    }

    public String getMediaKey() {
        return currentMediaKey;
    }

    public String getMediaFilter() {
        return currentMediaFilter;
    }

    public String getThumbKey() {
        return currentThumbKey;
    }

    public String getThumbFilter() {
        return currentThumbFilter;
    }

    public int getSize() {
        return currentSize;
    }

    public Object getMediaLocation() {
        return currentMediaLocation;
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

    public void setQualityThumbDocument(TLRPC.Document document) {
        qulityThumbDocument = document;
    }

    public TLRPC.Document getQulityThumbDocument() {
        return qulityThumbDocument;
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
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null) {
            animation.start();
        }
    }

    public void stopAnimation() {
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null) {
            animation.stop();
        }
    }

    public boolean isAnimationRunning() {
        AnimatedFileDrawable animation = getAnimation();
        return animation != null && animation.isRunning();
    }

    public AnimatedFileDrawable getAnimation() {
        AnimatedFileDrawable animatedFileDrawable;
        if (currentMediaDrawable instanceof AnimatedFileDrawable) {
            return (AnimatedFileDrawable) currentMediaDrawable;
        } else if (currentImageDrawable instanceof AnimatedFileDrawable) {
            return (AnimatedFileDrawable) currentImageDrawable;
        } else if (currentThumbDrawable instanceof AnimatedFileDrawable) {
            return (AnimatedFileDrawable) currentThumbDrawable;
        } else if (staticThumbDrawable instanceof AnimatedFileDrawable) {
            return (AnimatedFileDrawable) staticThumbDrawable;
        }
        return null;
    }

    protected int getTag(int type) {
        if (type == TYPE_THUMB) {
            return thumbTag;
        } else if (type == TYPE_MEDIA) {
            return mediaTag;
        } else {
            return imageTag;
        }
    }

    protected void setTag(int value, int type) {
        if (type == TYPE_THUMB) {
            thumbTag = value;
        } else if (type == TYPE_MEDIA) {
            mediaTag = value;
        } else {
            imageTag = value;
        }
    }

    public void setParam(int value) {
        param = value;
    }

    public int getParam() {
        return param;
    }

    protected boolean setImageBitmapByKey(BitmapDrawable bitmap, String key, int type, boolean memCache) {
        if (bitmap == null || key == null) {
            return false;
        }
        if (type == TYPE_IMAGE) {
            if (!key.equals(currentImageKey)) {
                return false;
            }
            if (!(bitmap instanceof AnimatedFileDrawable)) {
                ImageLoader.getInstance().incrementUseCount(currentImageKey);
            }
            currentImageDrawable = bitmap;
            if (bitmap instanceof ExtendedBitmapDrawable) {
                imageOrientation = ((ExtendedBitmapDrawable) bitmap).getOrientation();
            }
            if (roundRadius != 0 && bitmap instanceof BitmapDrawable) {
                if (bitmap instanceof AnimatedFileDrawable) {
                    ((AnimatedFileDrawable) bitmap).setRoundRadius(roundRadius);
                } else {
                    Bitmap object = bitmap.getBitmap();
                    imageShader = new BitmapShader(object, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                }
            } else {
                imageShader = null;
            }

            if (!memCache && !forcePreview || forceCrossfade) {
                boolean allowCorssfade = true;
                if (currentMediaDrawable instanceof AnimatedFileDrawable && ((AnimatedFileDrawable) currentMediaDrawable).hasBitmap()) {
                    allowCorssfade = false;
                }
                if (allowCorssfade && (currentThumbDrawable == null && staticThumbDrawable == null || currentAlpha == 1.0f || forceCrossfade)) {
                    currentAlpha = 0.0f;
                    lastUpdateAlphaTime = System.currentTimeMillis();
                    crossfadeWithThumb = crossfadeImage != null || currentThumbDrawable != null || staticThumbDrawable != null;
                }
            } else {
                currentAlpha = 1.0f;
            }
        } else if (type == TYPE_MEDIA) {
            if (!key.equals(currentMediaKey)) {
                return false;
            }
            if (!(bitmap instanceof AnimatedFileDrawable)) {
                ImageLoader.getInstance().incrementUseCount(currentMediaKey);
            }
            currentMediaDrawable = bitmap;
            if (roundRadius != 0 && bitmap instanceof BitmapDrawable) {
                if (bitmap instanceof AnimatedFileDrawable) {
                    ((AnimatedFileDrawable) bitmap).setRoundRadius(roundRadius);
                } else {
                    Bitmap object = bitmap.getBitmap();
                    mediaShader = new BitmapShader(object, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                }
            } else {
                mediaShader = null;
            }

            if (currentImageDrawable == null) {
                if (!memCache && !forcePreview || forceCrossfade) {
                    if (currentThumbDrawable == null && staticThumbDrawable == null || currentAlpha == 1.0f || forceCrossfade) {
                        currentAlpha = 0.0f;
                        lastUpdateAlphaTime = System.currentTimeMillis();
                        crossfadeWithThumb = crossfadeImage != null || currentThumbDrawable != null || staticThumbDrawable != null;
                    }
                } else {
                    currentAlpha = 1.0f;
                }
            }
        } else if (type == TYPE_THUMB) {
            if (currentThumbDrawable != null) {
                return false;
            }
            if (!forcePreview) {
                AnimatedFileDrawable animation = getAnimation();
                if (animation != null && animation.hasBitmap()) {
                    return false;
                }
                if (currentImageDrawable != null && !(currentImageDrawable instanceof AnimatedFileDrawable) || currentMediaDrawable != null && !(currentMediaDrawable instanceof AnimatedFileDrawable)) {
                    return false;
                }
            }
            if (!key.equals(currentThumbKey)) {
                return false;
            }
            ImageLoader.getInstance().incrementUseCount(currentThumbKey);

            currentThumbDrawable = bitmap;
            if (bitmap instanceof ExtendedBitmapDrawable) {
                thumbOrientation = ((ExtendedBitmapDrawable) bitmap).getOrientation();
            }

            if (roundRadius != 0 && bitmap instanceof BitmapDrawable) {
                if (bitmap instanceof AnimatedFileDrawable) {
                    ((AnimatedFileDrawable) bitmap).setRoundRadius(roundRadius);
                } else {
                    Bitmap object = bitmap.getBitmap();
                    thumbShader = new BitmapShader(object, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                }
            } else {
                thumbShader = null;
            }

            if (!memCache && crossfadeAlpha != 2) {
                if (currentParentObject instanceof MessageObject && ((MessageObject) currentParentObject).isRoundVideo() && ((MessageObject) currentParentObject).isSending()) {
                    currentAlpha = 1.0f;
                } else {
                    currentAlpha = 0.0f;
                    lastUpdateAlphaTime = System.currentTimeMillis();
                    crossfadeWithThumb = staticThumbDrawable != null && currentImageKey == null && currentMediaKey == null;
                }
            } else {
                currentAlpha = 1.0f;
            }
        }
        if (bitmap instanceof AnimatedFileDrawable) {
            AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) bitmap;
            fileDrawable.setParentView(parentView);
            if (allowStartAnimation) {
                fileDrawable.start();
            }
            fileDrawable.setAllowDecodeSingleFrame(allowDecodeSingleFrame);
        }
        if (parentView != null) {
            if (invalidateAll) {
                parentView.invalidate();
            } else {
                parentView.invalidate(imageX, imageY, imageX + imageW, imageY + imageH);
            }
        }
        if (delegate != null) {
            delegate.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null);
        }
        return true;
    }

    private void recycleBitmap(String newKey, int type) {
        String key;
        Drawable image;
        if (type == TYPE_MEDIA) {
            key = currentMediaKey;
            image = currentMediaDrawable;
        } else if (type == TYPE_CROSSFDADE) {
            key = crossfadeKey;
            image = crossfadeImage;
        } else if (type == TYPE_THUMB) {
            key = currentThumbKey;
            image = currentThumbDrawable;
        } else {
            key = currentImageKey;
            image = currentImageDrawable;
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
        if (type == TYPE_MEDIA) {
            currentMediaKey = null;
            currentMediaDrawable = null;
        } else if (type == TYPE_CROSSFDADE) {
            crossfadeKey = null;
            crossfadeImage = null;
        } else if (type == TYPE_THUMB) {
            currentThumbDrawable = null;
            currentThumbKey = null;
        } else {
            currentImageDrawable = null;
            currentImageKey = null;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didReplacedPhotoInMemCache) {
            String oldKey = (String) args[0];
            if (currentMediaKey != null && currentMediaKey.equals(oldKey)) {
                currentMediaKey = (String) args[1];
                currentMediaLocation = args[2];
                if (setImageBackup != null) {
                    setImageBackup.mediaLocation = args[2];
                }
            }
            if (currentImageKey != null && currentImageKey.equals(oldKey)) {
                currentImageKey = (String) args[1];
                currentImageLocation = args[2];
                if (setImageBackup != null) {
                    setImageBackup.fileLocation = args[2];
                }
            }
            if (currentThumbKey != null && currentThumbKey.equals(oldKey)) {
                currentThumbKey = (String) args[1];
                currentThumbLocation = args[2];
                if (setImageBackup != null) {
                    setImageBackup.thumbLocation = args[2];
                }
            }
        }
    }
}
