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
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclableDrawable;

import androidx.annotation.Keep;

public class ImageReceiver implements NotificationCenter.NotificationCenterDelegate {

    public interface ImageReceiverDelegate {
        void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache);
        default void onAnimationReady(ImageReceiver imageReceiver) {
        }
    }

    public static class BitmapHolder {

        private String key;
        private boolean recycleOnRelease;
        public Bitmap bitmap;
        public Drawable drawable;
        public int orientation;

        public BitmapHolder(Bitmap b, String k, int o) {
            bitmap = b;
            key = k;
            orientation = o;
            if (key != null) {
                ImageLoader.getInstance().incrementUseCount(key);
            }
        }

        public BitmapHolder(Drawable d, String k, int o) {
            drawable = d;
            key = k;
            orientation = o;
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
                drawable = null;
                return;
            }
            boolean canDelete = ImageLoader.getInstance().decrementUseCount(key);
            if (!ImageLoader.getInstance().isInMemCache(key, false)) {
                if (canDelete) {
                    if (bitmap != null) {
                        bitmap.recycle();
                    } else if (drawable != null) {
                        if (drawable instanceof RLottieDrawable) {
                            RLottieDrawable fileDrawable = (RLottieDrawable) drawable;
                            fileDrawable.recycle();
                        } else if (drawable instanceof AnimatedFileDrawable) {
                            AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) drawable;
                            fileDrawable.recycle();
                        } else if (drawable instanceof BitmapDrawable) {
                            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                            bitmap.recycle();
                        }
                    }
                }
            }
            key = null;
            bitmap = null;
            drawable = null;
        }
    }

    private static class SetImageBackup {
        public ImageLocation imageLocation;
        public String imageFilter;
        public ImageLocation thumbLocation;
        public String thumbFilter;
        public ImageLocation mediaLocation;
        public String mediaFilter;
        public Drawable thumb;
        public int size;
        public int cacheType;
        public Object parentObject;
        public String ext;

        private boolean isSet() {
            return imageLocation != null || thumbLocation != null || mediaLocation != null || thumb != null;
        }

        private boolean isWebfileSet() {
            return imageLocation != null && (imageLocation.webFile != null || imageLocation.path != null) ||
                    thumbLocation != null && (thumbLocation.webFile != null || thumbLocation.path != null) ||
                    mediaLocation != null && (mediaLocation.webFile != null || mediaLocation.path != null);
        }

        private void clear() {
            imageLocation = null;
            thumbLocation = null;
            mediaLocation = null;
            thumb = null;
        }
    }

    public final static int TYPE_IMAGE = 0;
    public final static int TYPE_THUMB = 1;
    private final static int TYPE_CROSSFDADE = 2;
    public final static int TYPE_MEDIA = 3;

    public final static int DEFAULT_CROSSFADE_DURATION = 150;

    private int currentAccount;
    private View parentView;

    private int param;
    private Object currentParentObject;
    private boolean canceledLoading;
    private static PorterDuffColorFilter selectedColorFilter = new PorterDuffColorFilter(0xffdddddd, PorterDuff.Mode.MULTIPLY);
    private static PorterDuffColorFilter selectedGroupColorFilter = new PorterDuffColorFilter(0xffbbbbbb, PorterDuff.Mode.MULTIPLY);
    private boolean forceLoding;

    private int currentLayerNum;
    private int currentOpenedLayerFlags;

    private SetImageBackup setImageBackup;

    private ImageLocation strippedLocation;
    private ImageLocation currentImageLocation;
    private String currentImageFilter;
    private String currentImageKey;
    private int imageTag;
    private Drawable currentImageDrawable;
    private BitmapShader imageShader;
    private int imageOrientation;

    private ImageLocation currentThumbLocation;
    private String currentThumbFilter;
    private String currentThumbKey;
    private int thumbTag;
    private Drawable currentThumbDrawable;
    private BitmapShader thumbShader;
    private int thumbOrientation;

    private ImageLocation currentMediaLocation;
    private String currentMediaFilter;
    private String currentMediaKey;
    private int mediaTag;
    private Drawable currentMediaDrawable;
    private BitmapShader mediaShader;

    private Drawable staticThumbDrawable;

    private String currentExt;

    private boolean ignoreImageSet;

    private int currentGuid;

    private int currentSize;
    private int currentCacheType;
    private boolean allowStartAnimation = true;
    private boolean allowStartLottieAnimation = true;
    private boolean useSharedAnimationQueue;
    private boolean allowDecodeSingleFrame;
    private int autoRepeat = 1;
    private boolean animationReadySent;

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

    private float imageX, imageY, imageW, imageH;
    private float sideClip;
    private RectF drawRegion = new RectF();
    private boolean isVisible = true;
    private boolean isAspectFit;
    private boolean forcePreview;
    private boolean forceCrossfade;
    private int[] roundRadius = new int[4];
    private boolean isRoundRect;

    private Paint roundPaint;
    private RectF roundRect = new RectF();
    private RectF bitmapRect = new RectF();
    private Matrix shaderMatrix = new Matrix();
    private Path roundPath = new Path();
    private static float[] radii = new float[8];
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
    private boolean isRoundVideo;
    private long startTime;
    private long endTime;
    private int crossfadeDuration = DEFAULT_CROSSFADE_DURATION;
    private float pressedProgress;
    private int animateFromIsPressed;

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

    public void setStrippedLocation(ImageLocation location) {
        strippedLocation = location;
    }

    public void setIgnoreImageSet(boolean value) {
        ignoreImageSet = value;
    }

    public ImageLocation getStrippedLocation() {
        return strippedLocation;
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Drawable thumb, String ext, Object parentObject, int cacheType) {
        setImage(imageLocation, imageFilter, null, null, thumb, 0, ext, parentObject, cacheType);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Drawable thumb, int size, String ext, Object parentObject, int cacheType) {
        setImage(imageLocation, imageFilter, null, null, thumb, size, ext, parentObject, cacheType);
    }

    public void setImage(String imagePath, String imageFilter, Drawable thumb, String ext, int size) {
        setImage(ImageLocation.getForPath(imagePath), imageFilter, null, null, thumb, size, ext, null, 1);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, String ext, Object parentObject, int cacheType) {
        setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, null, 0, ext, parentObject, cacheType);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, int size, String ext, Object parentObject, int cacheType) {
        setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, null, size, ext, parentObject, cacheType);
    }

    public void setImage(ImageLocation fileLocation, String fileFilter, ImageLocation thumbLocation, String thumbFilter, Drawable thumb, int size, String ext, Object parentObject, int cacheType) {
        setImage(null, null, fileLocation, fileFilter, thumbLocation, thumbFilter, thumb, size, ext, parentObject, cacheType);
    }

    public void setImage(ImageLocation mediaLocation, String mediaFilter, ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, Drawable thumb, int size, String ext, Object parentObject, int cacheType) {
        if (ignoreImageSet) {
            return;
        }
        if (crossfadeWithOldImage && setImageBackup != null && setImageBackup.isWebfileSet()) {
            setBackupImage();
        }
        if (setImageBackup != null) {
            setImageBackup.clear();
        }

        if (imageLocation == null && thumbLocation == null && mediaLocation == null) {
            for (int a = 0; a < 4; a++) {
                recycleBitmap(null, a);
            }
            currentImageLocation = null;
            currentImageFilter = null;
            currentImageKey = null;
            currentMediaLocation = null;
            currentMediaFilter = null;
            currentMediaKey = null;
            currentThumbLocation = null;
            currentThumbFilter = null;
            currentThumbKey = null;

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
                    parentView.invalidate((int) imageX, (int) imageY, (int) (imageX + imageW), (int) (imageY + imageH));
                }
            }
            if (delegate != null) {
                delegate.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null, false);
            }
            return;
        }
        String imageKey = imageLocation != null ? imageLocation.getKey(parentObject, null, false) : null;
        if (imageKey == null && imageLocation != null) {
            imageLocation = null;
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

        String mediaKey = mediaLocation != null ? mediaLocation.getKey(parentObject, null, false) : null;
        if (mediaKey == null && mediaLocation != null) {
            mediaLocation = null;
        }
        if (mediaKey != null && mediaFilter != null) {
            mediaKey += "@" + mediaFilter;
        }

        if (mediaKey == null && currentImageKey != null && currentImageKey.equals(imageKey) || currentMediaKey != null && currentMediaKey.equals(mediaKey)) {
            if (delegate != null) {
                delegate.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null, false);
            }
            if (!canceledLoading) {
                return;
            }
        }

        ImageLocation strippedLoc;
        if (strippedLocation != null) {
            strippedLoc = strippedLocation;
        } else {
            strippedLoc = mediaLocation != null ? mediaLocation : imageLocation;
        }

        String thumbKey = thumbLocation != null ? thumbLocation.getKey(parentObject, strippedLoc, false) : null;
        if (thumbKey != null && thumbFilter != null) {
            thumbKey += "@" + thumbFilter;
        }

        if (crossfadeWithOldImage) {
            if (currentMediaDrawable != null) {
                if (currentMediaDrawable instanceof AnimatedFileDrawable) {
                    ((AnimatedFileDrawable) currentMediaDrawable).stop();
                }
                recycleBitmap(thumbKey, TYPE_THUMB);
                recycleBitmap(null, TYPE_CROSSFDADE);
                recycleBitmap(mediaKey, TYPE_IMAGE);
                crossfadeImage = currentMediaDrawable;
                crossfadeShader = mediaShader;
                crossfadeKey = currentImageKey;
                crossfadingWithThumb = false;
                currentMediaDrawable = null;
                currentMediaKey = null;
            } else if (currentImageDrawable != null) {
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

        currentImageLocation = imageLocation;
        currentImageFilter = imageFilter;
        currentImageKey = imageKey;
        currentMediaLocation = mediaLocation;
        currentMediaFilter = mediaFilter;
        currentMediaKey = mediaKey;
        currentThumbLocation = thumbLocation;
        currentThumbFilter = thumbFilter;
        currentThumbKey = thumbKey;

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
            delegate.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null, false);
        }

        ImageLoader.getInstance().loadImageForImageReceiver(this);
        if (parentView != null) {
            if (invalidateAll) {
                parentView.invalidate();
            } else {
                parentView.invalidate((int)imageX, (int) imageY, (int) (imageX + imageW), (int) (imageY + imageH));
            }
        }

        isRoundVideo = parentObject instanceof MessageObject && ((MessageObject) parentObject).isRoundVideo();
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

    public void setLayerNum(int value) {
        currentLayerNum = value;
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
            fileDrawable.setUseSharedQueue(useSharedAnimationQueue);
            if (allowStartAnimation) {
                fileDrawable.start();
            }
            fileDrawable.setAllowDecodeSingleFrame(allowDecodeSingleFrame);
        } else if (bitmap instanceof RLottieDrawable) {
            RLottieDrawable fileDrawable = (RLottieDrawable) bitmap;
            fileDrawable.addParentView(parentView);
            if (allowStartLottieAnimation && currentOpenedLayerFlags == 0) {
                fileDrawable.start();
            }
            fileDrawable.setAllowDecodeSingleFrame(true);
        }
        staticThumbDrawable = bitmap;
        updateDrawableRadius(bitmap);
        currentMediaLocation = null;
        currentMediaFilter = null;
        currentMediaDrawable = null;
        currentMediaKey = null;
        mediaShader = null;

        currentImageLocation = null;
        currentImageFilter = null;
        currentImageDrawable = null;
        currentImageKey = null;
        imageShader = null;

        currentThumbLocation = null;
        currentThumbFilter = null;
        currentThumbKey = null;

        currentKeyQuality = false;
        currentExt = null;
        currentSize = 0;
        currentCacheType = 0;
        currentAlpha = 1;

        if (setImageBackup != null) {
            setImageBackup.clear();
        }

        if (delegate != null) {
            delegate.didSetImage(this, currentThumbDrawable != null || staticThumbDrawable != null, true, false);
        }
        if (parentView != null) {
            if (invalidateAll) {
                parentView.invalidate();
            } else {
                parentView.invalidate((int)imageX, (int) imageY, (int) (imageX + imageW), (int) (imageY + imageH));
            }
        }
        if (forceCrossfade && crossfadeWithOldImage && crossfadeImage != null) {
            currentAlpha = 0.0f;
            lastUpdateAlphaTime = System.currentTimeMillis();
            crossfadeWithThumb = currentThumbDrawable != null || staticThumbDrawable != null;
        }
    }

    private void setDrawableShader(Drawable drawable, BitmapShader shader) {
        if (drawable == currentThumbDrawable || drawable == staticThumbDrawable) {
            thumbShader = shader;
        } else if (drawable == currentMediaDrawable) {
            mediaShader = shader;
        } else if (drawable == currentImageDrawable) {
            imageShader = shader;
        }
    }

    private boolean hasRoundRadius() {
        /*for (int a = 0; a < roundRadius.length; a++) {
            if (roundRadius[a] != 0) {
                return true;
            }
        }*/
        return true;
    }

    private void updateDrawableRadius(Drawable drawable) {
        if (hasRoundRadius() && drawable instanceof BitmapDrawable) {
            if (drawable instanceof RLottieDrawable) {

            } else if (drawable instanceof AnimatedFileDrawable) {
                AnimatedFileDrawable animatedFileDrawable = (AnimatedFileDrawable) drawable;
                animatedFileDrawable.setRoundRadius(roundRadius);
            } else {
                setDrawableShader(drawable, new BitmapShader(((BitmapDrawable) drawable).getBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            }
        } else {
            setDrawableShader(drawable, null);
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
            setImageBackup.imageLocation = currentImageLocation;
            setImageBackup.imageFilter = currentImageFilter;
            setImageBackup.thumbLocation = currentThumbLocation;
            setImageBackup.thumbFilter = currentThumbFilter;
            setImageBackup.thumb = staticThumbDrawable;
            setImageBackup.size = currentSize;
            setImageBackup.ext = currentExt;
            setImageBackup.cacheType = currentCacheType;
            setImageBackup.parentObject = currentParentObject;
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReplacedPhotoInMemCache);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.stopAllHeavyOperations);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.startAllHeavyOperations);

        if (staticThumbDrawable != null) {
            staticThumbDrawable = null;
            thumbShader = null;
        }
        clearImage();
        if (isPressed == 0) {
            pressedProgress = 0f;
        }
    }

    private boolean setBackupImage() {
        if (setImageBackup != null && setImageBackup.isSet()) {
            SetImageBackup temp = setImageBackup;
            setImageBackup = null;
            setImage(temp.mediaLocation, temp.mediaFilter, temp.imageLocation, temp.imageFilter, temp.thumbLocation, temp.thumbFilter, temp.thumb, temp.size, temp.ext, temp.parentObject, temp.cacheType);
            temp.clear();
            setImageBackup = temp;
            if (allowStartLottieAnimation && currentOpenedLayerFlags == 0) {
                RLottieDrawable lottieDrawable = getLottieAnimation();
                if (lottieDrawable != null) {
                    lottieDrawable.start();
                }
            }
            return true;
        }
        return false;
    }

    public boolean onAttachedToWindow() {
        currentOpenedLayerFlags = NotificationCenter.getGlobalInstance().getCurrentHeavyOperationFlags();
        currentOpenedLayerFlags &=~ currentLayerNum;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReplacedPhotoInMemCache);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.stopAllHeavyOperations);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.startAllHeavyOperations);
        if (setBackupImage()) {
            return true;
        }
        if (allowStartLottieAnimation && currentOpenedLayerFlags == 0) {
            RLottieDrawable lottieDrawable = getLottieAnimation();
            if (lottieDrawable != null) {
                lottieDrawable.start();
            }
        }
        return false;
    }

    private void drawDrawable(Canvas canvas, Drawable drawable, int alpha, BitmapShader shader, int orientation) {
        if (isPressed == 0 && pressedProgress != 0) {
            pressedProgress -= 16 / 150f;
            if (pressedProgress < 0) {
                pressedProgress = 0;
            }
            if (parentView != null) {
                parentView.invalidate();
            }
        }
        if (isPressed != 0) {
            pressedProgress = 1f;
            animateFromIsPressed = isPressed;
        }
        if (pressedProgress == 0 || pressedProgress == 1f) {
            drawDrawable(canvas, drawable, alpha, shader, orientation, isPressed);
        } else {
            drawDrawable(canvas, drawable, alpha, shader, orientation, isPressed);
            drawDrawable(canvas, drawable, (int) (alpha * pressedProgress), shader, orientation, animateFromIsPressed);
        }
    }

    private void drawDrawable(Canvas canvas, Drawable drawable, int alpha, BitmapShader shader, int orientation, int isPressed) {
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
            if (bitmapDrawable instanceof AnimatedFileDrawable || bitmapDrawable instanceof RLottieDrawable) {
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
            float realImageW = imageW - sideClip * 2;
            float realImageH = imageH - sideClip * 2;
            float scaleW = imageW == 0 ? 1.0f : (bitmapW / realImageW);
            float scaleH = imageH == 0 ? 1.0f : (bitmapH / realImageH);

            if (shader != null) {
                if (isAspectFit) {
                    float scale = Math.max(scaleW, scaleH);
                    bitmapW /= scale;
                    bitmapH /= scale;
                    drawRegion.set(imageX + (imageW - bitmapW) / 2, imageY + (imageH - bitmapH) / 2, imageX + (imageW + bitmapW) / 2, imageY + (imageH + bitmapH) / 2);

                    if (isVisible) {
                        roundPaint.setShader(shader);
                        shaderMatrix.reset();
                        shaderMatrix.setTranslate(drawRegion.left, drawRegion.top);
                        shaderMatrix.preScale(1.0f / scale, 1.0f / scale);

                        shader.setLocalMatrix(shaderMatrix);
                        roundPaint.setAlpha(alpha);
                        roundRect.set(drawRegion);

                        if (isRoundRect) {
                            canvas.drawRoundRect(roundRect,roundRadius[0], roundRadius[0],roundPaint);
                        } else {
                            for (int a = 0; a < roundRadius.length; a++) {
                                radii[a * 2] = roundRadius[a];
                                radii[a * 2 + 1] = roundRadius[a];
                            }
                            roundPath.reset();
                            roundPath.addRoundRect(roundRect, radii, Path.Direction.CW);
                            roundPath.close();
                            canvas.drawPath(roundPath, roundPaint);
                        }
                    }
                } else {
                    roundPaint.setShader(shader);
                    float scale = 1.0f / Math.min(scaleW, scaleH);
                    roundRect.set(imageX + sideClip, imageY + sideClip, imageX + imageW - sideClip, imageY + imageH - sideClip);
                    shaderMatrix.reset();
                    if (Math.abs(scaleW - scaleH) > 0.0005f) {
                        if (bitmapW / scaleH > realImageW) {
                            bitmapW /= scaleH;
                            drawRegion.set(imageX - (bitmapW - realImageW) / 2, imageY, imageX + (bitmapW + realImageW) / 2, imageY + realImageH);
                        } else {
                            bitmapH /= scaleW;
                            drawRegion.set(imageX, imageY - (bitmapH - realImageH) / 2, imageX + realImageW, imageY + (bitmapH + realImageH) / 2);
                        }
                    } else {
                        drawRegion.set(imageX, imageY, imageX + realImageW, imageY + realImageH);
                    }
                    if (isVisible) {
                        shaderMatrix.reset();
                        shaderMatrix.setTranslate(drawRegion.left + sideClip, drawRegion.top + sideClip);
                        if (orientation == 90) {
                            shaderMatrix.preRotate(90);
                            shaderMatrix.preTranslate(0, -drawRegion.width());
                        } else if (orientation == 180) {
                            shaderMatrix.preRotate(180);
                            shaderMatrix.preTranslate(-drawRegion.width(), -drawRegion.height());
                        } else if (orientation == 270) {
                            shaderMatrix.preRotate(270);
                            shaderMatrix.preTranslate(-drawRegion.height(), 0);
                        }
                        shaderMatrix.preScale(scale, scale);
                        if (isRoundVideo) {
                            float postScale = (realImageW + AndroidUtilities.roundMessageInset * 2) / realImageW;
                            shaderMatrix.postScale(postScale, postScale, drawRegion.centerX(), drawRegion.centerY());
                        }
                        shader.setLocalMatrix(shaderMatrix);
                        roundPaint.setAlpha(alpha);

                        if (isRoundRect) {
                            canvas.drawRoundRect(roundRect, roundRadius[0], roundRadius[0], roundPaint);
                        } else {
                            for (int a = 0; a < roundRadius.length; a++) {
                                radii[a * 2] = roundRadius[a];
                                radii[a * 2 + 1] = roundRadius[a];
                            }
                            roundPath.reset();
                            roundPath.addRoundRect(roundRect, radii, Path.Direction.CW);
                            roundPath.close();
                            canvas.drawPath(roundPath, roundPaint);
                        }
                    }
                }
            } else {
                if (isAspectFit) {
                    float scale = Math.max(scaleW, scaleH);
                    canvas.save();
                    bitmapW /= scale;
                    bitmapH /= scale;
                    drawRegion.set(imageX + (imageW - bitmapW) / 2.0f, imageY + (imageH - bitmapH) / 2.0f, imageX + (imageW + bitmapW) / 2.0f, imageY + (imageH + bitmapH) / 2.0f);
                    bitmapDrawable.setBounds((int) drawRegion.left, (int) drawRegion.top, (int) drawRegion.right, (int) drawRegion.bottom);
                    if (bitmapDrawable instanceof AnimatedFileDrawable) {
                        ((AnimatedFileDrawable) bitmapDrawable).setActualDrawRect(drawRegion.left, drawRegion.top, drawRegion.width(), drawRegion.height());
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
                            drawRegion.set(imageX - (bitmapW - imageW) / 2.0f, imageY, imageX + (bitmapW + imageW) / 2.0f, imageY + imageH);
                        } else {
                            bitmapH /= scaleW;
                            drawRegion.set(imageX, imageY - (bitmapH - imageH) / 2.0f, imageX + imageW, imageY + (bitmapH + imageH) / 2.0f);
                        }
                        if (bitmapDrawable instanceof AnimatedFileDrawable) {
                            ((AnimatedFileDrawable) bitmapDrawable).setActualDrawRect(imageX, imageY, imageW, imageH);
                        }
                        if (orientation % 360 == 90 || orientation % 360 == 270) {
                            float width = drawRegion.width() / 2;
                            float height = drawRegion.height() / 2;
                            float centerX = drawRegion.centerX();
                            float centerY = drawRegion.centerY();
                            bitmapDrawable.setBounds((int) (centerX - height), (int) (centerY - width), (int) (centerX + height), (int) (centerY + width));
                        } else {
                            bitmapDrawable.setBounds((int) drawRegion.left, (int) drawRegion.top, (int) drawRegion.right, (int) drawRegion.bottom);
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
                        if (isRoundVideo) {
                            drawRegion.inset(-AndroidUtilities.roundMessageInset, -AndroidUtilities.roundMessageInset);
                        }
                        if (bitmapDrawable instanceof AnimatedFileDrawable) {
                            ((AnimatedFileDrawable) bitmapDrawable).setActualDrawRect(imageX, imageY, imageW, imageH);
                        }
                        if (orientation % 360 == 90 || orientation % 360 == 270) {
                            float width = drawRegion.width() / 2;
                            float height = drawRegion.height() / 2;
                            float centerX = drawRegion.centerX();
                            float centerY = drawRegion.centerY();
                            bitmapDrawable.setBounds((int) (centerX - height), (int) (centerY - width), (int) (centerX + height), (int) (centerY + width));
                        } else {
                            bitmapDrawable.setBounds((int) drawRegion.left, (int) drawRegion.top, (int) drawRegion.right, (int) drawRegion.bottom);
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
            drawable.setBounds((int) drawRegion.left, (int) drawRegion.top, (int) drawRegion.right, (int) drawRegion.bottom);
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
        setImage(currentMediaLocation, currentMediaFilter, currentImageLocation, currentImageFilter, currentThumbLocation, currentThumbFilter, currentThumbDrawable, currentSize, currentExt, currentParentObject, currentCacheType);
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
                currentAlpha += dt / (float) crossfadeDuration;
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
                    parentView.invalidate((int)imageX, (int) imageY, (int) (imageX + imageW), (int) (imageY + imageH));
                }
            }
        }
    }

    public boolean draw(Canvas canvas) {
        try {
            Drawable drawable = null;
            AnimatedFileDrawable animation = getAnimation();
            RLottieDrawable lottieDrawable = getLottieAnimation();
            boolean animationNotReady = animation != null && !animation.hasBitmap() || lottieDrawable != null && !lottieDrawable.hasBitmap();
            if (animation != null) {
                animation.setRoundRadius(roundRadius);
            }
            if (lottieDrawable != null) {
                lottieDrawable.setCurrentParentView(parentView);
            }
            if ((animation != null || lottieDrawable != null) && !animationNotReady && !animationReadySent) {
                animationReadySent = true;
                if (delegate != null) {
                    delegate.onAnimationReady(this);
                }
            }
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

    @Keep
    public float getCurrentAlpha() {
        return currentAlpha;
    }

    @Keep
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
        RLottieDrawable lottieDrawable = getLottieAnimation();
        if (lottieDrawable != null && lottieDrawable.hasBitmap()) {
            return lottieDrawable.getAnimatedBitmap();
        } else if (animation != null && animation.hasBitmap()) {
            return animation.getAnimatedBitmap();
        } else if (currentMediaDrawable instanceof BitmapDrawable && !(currentMediaDrawable instanceof AnimatedFileDrawable) && !(currentMediaDrawable instanceof RLottieDrawable)) {
            return ((BitmapDrawable) currentMediaDrawable).getBitmap();
        } else if (currentImageDrawable instanceof BitmapDrawable && !(currentImageDrawable instanceof AnimatedFileDrawable) && !(currentMediaDrawable instanceof RLottieDrawable)) {
            return ((BitmapDrawable) currentImageDrawable).getBitmap();
        } else if (currentThumbDrawable instanceof BitmapDrawable && !(currentThumbDrawable instanceof AnimatedFileDrawable) && !(currentMediaDrawable instanceof RLottieDrawable)) {
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
        RLottieDrawable lottieDrawable = getLottieAnimation();
        int orientation = 0;
        if (lottieDrawable != null && lottieDrawable.hasBitmap()) {
            bitmap = lottieDrawable.getAnimatedBitmap();
        } else if (animation != null && animation.hasBitmap()) {
            bitmap = animation.getAnimatedBitmap();
            orientation = animation.getOrientation();
            if (orientation != 0) {
                return new BitmapHolder(Bitmap.createBitmap(bitmap), null, orientation);
            }
        } else if (currentMediaDrawable instanceof BitmapDrawable && !(currentMediaDrawable instanceof AnimatedFileDrawable) && !(currentMediaDrawable instanceof RLottieDrawable)) {
            bitmap = ((BitmapDrawable) currentMediaDrawable).getBitmap();
            key = currentMediaKey;
        } else if (currentImageDrawable instanceof BitmapDrawable && !(currentImageDrawable instanceof AnimatedFileDrawable) && !(currentMediaDrawable instanceof RLottieDrawable)) {
            bitmap = ((BitmapDrawable) currentImageDrawable).getBitmap();
            key = currentImageKey;
        } else if (currentThumbDrawable instanceof BitmapDrawable && !(currentThumbDrawable instanceof AnimatedFileDrawable) && !(currentMediaDrawable instanceof RLottieDrawable)) {
            bitmap = ((BitmapDrawable) currentThumbDrawable).getBitmap();
            key = currentThumbKey;
        } else if (staticThumbDrawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) staticThumbDrawable).getBitmap();
        }
        if (bitmap != null) {
            return new BitmapHolder(bitmap, key, orientation);
        }
        return null;
    }

    public BitmapHolder getDrawableSafe() {
        Drawable drawable = null;
        String key = null;
        if (currentMediaDrawable instanceof BitmapDrawable && !(currentMediaDrawable instanceof AnimatedFileDrawable) && !(currentMediaDrawable instanceof RLottieDrawable)) {
            drawable = currentMediaDrawable;
            key = currentMediaKey;
        } else if (currentImageDrawable instanceof BitmapDrawable && !(currentImageDrawable instanceof AnimatedFileDrawable) && !(currentMediaDrawable instanceof RLottieDrawable)) {
            drawable = currentImageDrawable;
            key = currentImageKey;
        } else if (currentThumbDrawable instanceof BitmapDrawable && !(currentThumbDrawable instanceof AnimatedFileDrawable) && !(currentMediaDrawable instanceof RLottieDrawable)) {
            drawable = currentThumbDrawable;
            key = currentThumbKey;
        } else if (staticThumbDrawable instanceof BitmapDrawable) {
            drawable = staticThumbDrawable;
        }
        if (drawable != null) {
            return new BitmapHolder(drawable, key, 0);
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
            return new BitmapHolder(bitmap, key, 0);
        }
        return null;
    }

    public int getBitmapWidth() {
        Drawable drawable = getDrawable();
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null) {
            return imageOrientation % 360 == 0 || imageOrientation % 360 == 180 ? animation.getIntrinsicWidth() : animation.getIntrinsicHeight();
        }
        RLottieDrawable lottieDrawable = getLottieAnimation();
        if (lottieDrawable != null) {
            return lottieDrawable.getIntrinsicWidth();
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
        Drawable drawable = getDrawable();
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null) {
            return imageOrientation % 360 == 0 || imageOrientation % 360 == 180 ? animation.getIntrinsicHeight() : animation.getIntrinsicWidth();
        }
        RLottieDrawable lottieDrawable = getLottieAnimation();
        if (lottieDrawable != null) {
            return lottieDrawable.getIntrinsicHeight();
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
                parentView.invalidate((int) imageX, (int) imageY, (int) (imageX + imageW), (int) (imageY + imageH));
            }
        }
    }

    public boolean getVisible() {
        return isVisible;
    }

    @Keep
    public void setAlpha(float value) {
        overrideAlpha = value;
    }

    @Keep
    public float getAlpha() {
        return overrideAlpha;
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

    public void setImageCoords(float x, float y, float width, float height) {
        imageX = x;
        imageY = y;
        imageW = width;
        imageH = height;
    }

    public void setSideClip(float value) {
        sideClip = value;
    }

    public float getCenterX() {
        return imageX + imageW / 2.0f;
    }

    public float getCenterY() {
        return imageY + imageH / 2.0f;
    }

    public float getImageX() {
        return imageX;
    }

    public float getImageX2() {
        return imageX + imageW;
    }

    public float getImageY() {
        return imageY;
    }

    public float getImageY2() {
        return imageY + imageH;
    }

    public float getImageWidth() {
        return imageW;
    }

    public float getImageHeight() {
        return imageH;
    }

    public float getImageAspectRatio() {
        return imageOrientation % 180 != 0 ? drawRegion.height() / drawRegion.width() : drawRegion.width() / drawRegion.height();
    }

    public String getExt() {
        return currentExt;
    }

    public boolean isInsideImage(float x, float y) {
        return x >= imageX && x <= imageX + imageW && y >= imageY && y <= imageY + imageH;
    }

    public RectF getDrawRegion() {
        return drawRegion;
    }

    public int getNewGuid() {
        return ++currentGuid;
    }

    public String getImageKey() {
        return currentImageKey;
    }

    public String getMediaKey() {
        return currentMediaKey;
    }

    public String getThumbKey() {
        return currentThumbKey;
    }

    public int getSize() {
        return currentSize;
    }

    public ImageLocation getMediaLocation() {
        return currentMediaLocation;
    }

    public ImageLocation getImageLocation() {
        return currentImageLocation;
    }

    public ImageLocation getThumbLocation() {
        return currentThumbLocation;
    }

    public String getMediaFilter() {
        return currentMediaFilter;
    }

    public String getImageFilter() {
        return currentImageFilter;
    }

    public String getThumbFilter() {
        return currentThumbFilter;
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
        setRoundRadius(new int[]{value, value, value, value});
    }

    public void setRoundRadius(int tl, int tr, int bl, int br) {
        setRoundRadius(new int[]{tl, tr, bl, br});
    }

    public void setRoundRadius(int[] value) {
        boolean changed = false;
        int firstValue = value[0];
        isRoundRect = true;
        for (int a = 0; a < roundRadius.length; a++) {
            if (roundRadius[a] != value[a]) {
                changed = true;
            }
            if (firstValue != value[a]) {
                isRoundRect = false;
            }
            roundRadius[a] = value[a];
        }
        if (changed) {
            if (currentImageDrawable != null && imageShader == null) {
                updateDrawableRadius(currentImageDrawable);
            }
            if (currentMediaDrawable != null && mediaShader == null) {
                updateDrawableRadius(currentMediaDrawable);
            }
            if (thumbShader == null) {
                if (currentThumbDrawable != null) {
                    updateDrawableRadius(currentThumbDrawable);
                } else if (staticThumbDrawable != null) {
                    updateDrawableRadius(staticThumbDrawable);
                }
            }
        }
    }

    public void setCurrentAccount(int value) {
        currentAccount = value;
    }

    public int[] getRoundRadius() {
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

    public boolean getAllowStartAnimation() {
        return allowStartAnimation;
    }

    public void setAllowStartLottieAnimation(boolean value) {
        allowStartLottieAnimation = value;
    }

    public void setAllowDecodeSingleFrame(boolean value) {
        allowDecodeSingleFrame = value;
    }

    public void setAutoRepeat(int value) {
        autoRepeat = value;
        RLottieDrawable drawable = getLottieAnimation();
        if (drawable != null) {
            drawable.setAutoRepeat(value);
        }
    }

    public void setUseSharedAnimationQueue(boolean value) {
        useSharedAnimationQueue = value;
    }

    public boolean isAllowStartAnimation() {
        return allowStartAnimation;
    }

    public void startAnimation() {
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null) {
            animation.setUseSharedQueue(useSharedAnimationQueue);
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

    public RLottieDrawable getLottieAnimation() {
        RLottieDrawable animatedFileDrawable;
        if (currentMediaDrawable instanceof RLottieDrawable) {
            return (RLottieDrawable) currentMediaDrawable;
        } else if (currentImageDrawable instanceof RLottieDrawable) {
            return (RLottieDrawable) currentImageDrawable;
        } else if (currentThumbDrawable instanceof RLottieDrawable) {
            return (RLottieDrawable) currentThumbDrawable;
        } else if (staticThumbDrawable instanceof RLottieDrawable) {
            return (RLottieDrawable) staticThumbDrawable;
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

    protected boolean setImageBitmapByKey(Drawable drawable, String key, int type, boolean memCache, int guid) {
        if (drawable == null || key == null || currentGuid != guid) {
            return false;
        }
        if (type == TYPE_IMAGE) {
            if (!key.equals(currentImageKey)) {
                return false;
            }
            if (!(drawable instanceof AnimatedFileDrawable)) {
                ImageLoader.getInstance().incrementUseCount(currentImageKey);
            } else {
                ((AnimatedFileDrawable) drawable).setStartEndTime(startTime, endTime);
            }
            currentImageDrawable = drawable;
            if (drawable instanceof ExtendedBitmapDrawable) {
                imageOrientation = ((ExtendedBitmapDrawable) drawable).getOrientation();
            }
            updateDrawableRadius(drawable);

            if (isVisible && (!memCache && !forcePreview || forceCrossfade)) {
                boolean allowCorssfade = true;
                if (currentMediaDrawable instanceof AnimatedFileDrawable && ((AnimatedFileDrawable) currentMediaDrawable).hasBitmap()) {
                    allowCorssfade = false;
                } else if (currentImageDrawable instanceof RLottieDrawable) {
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
            if (!(drawable instanceof AnimatedFileDrawable)) {
                ImageLoader.getInstance().incrementUseCount(currentMediaKey);
            } else {
                ((AnimatedFileDrawable) drawable).setStartEndTime(startTime, endTime);
            }
            currentMediaDrawable = drawable;
            updateDrawableRadius(drawable);

            if (currentImageDrawable == null) {
                boolean allowCorssfade = true;
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

            currentThumbDrawable = drawable;
            if (drawable instanceof ExtendedBitmapDrawable) {
                thumbOrientation = ((ExtendedBitmapDrawable) drawable).getOrientation();
            }
            updateDrawableRadius(drawable);

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
        if (delegate != null) {
            delegate.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null, memCache);
        }
        if (drawable instanceof AnimatedFileDrawable) {
            AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) drawable;
            fileDrawable.setParentView(parentView);
            fileDrawable.setUseSharedQueue(useSharedAnimationQueue);
            if (allowStartAnimation) {
                fileDrawable.start();
            }
            fileDrawable.setAllowDecodeSingleFrame(allowDecodeSingleFrame);
            animationReadySent = false;
        } else if (drawable instanceof RLottieDrawable) {
            RLottieDrawable fileDrawable = (RLottieDrawable) drawable;
            fileDrawable.addParentView(parentView);
            if (allowStartLottieAnimation && currentOpenedLayerFlags == 0) {
                fileDrawable.start();
            }
            fileDrawable.setAllowDecodeSingleFrame(true);
            fileDrawable.setAutoRepeat(autoRepeat);
            animationReadySent = false;
        }
        if (parentView != null) {
            if (invalidateAll) {
                parentView.invalidate();
            } else {
                parentView.invalidate((int) imageX, (int) imageY, (int) (imageX + imageW), (int) (imageY + imageH));
            }
        }
        return true;
    }

    public void setMediaStartEndTime(long startTime, long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;

        if (currentMediaDrawable instanceof AnimatedFileDrawable) {
            ((AnimatedFileDrawable) currentMediaDrawable).setStartEndTime(startTime, endTime);
        }
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
        if (key != null && (key.startsWith("-") || key.startsWith("strippedmessage-"))) {
            String replacedKey = ImageLoader.getInstance().getReplacedKey(key);
            if (replacedKey != null) {
                key = replacedKey;
            }
        }
        if (image instanceof RLottieDrawable) {
            RLottieDrawable lottieDrawable = (RLottieDrawable) image;
            lottieDrawable.removeParentView(parentView);
        }
        if (key != null && (newKey == null || !newKey.equals(key)) && image != null) {
            if (image instanceof RLottieDrawable) {
                RLottieDrawable fileDrawable = (RLottieDrawable) image;
                boolean canDelete = ImageLoader.getInstance().decrementUseCount(key);
                if (!ImageLoader.getInstance().isInMemCache(key, true)) {
                    if (canDelete) {
                        fileDrawable.recycle();
                    }
                }
            } else if (image instanceof AnimatedFileDrawable) {
                AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) image;
                fileDrawable.recycle();
            } else if (image instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) image).getBitmap();
                boolean canDelete = ImageLoader.getInstance().decrementUseCount(key);
                if (!ImageLoader.getInstance().isInMemCache(key, false)) {
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

    public void setCrossfadeDuration(int duration) {
        crossfadeDuration = duration;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didReplacedPhotoInMemCache) {
            String oldKey = (String) args[0];
            if (currentMediaKey != null && currentMediaKey.equals(oldKey)) {
                currentMediaKey = (String) args[1];
                currentMediaLocation = (ImageLocation) args[2];
                if (setImageBackup != null) {
                    setImageBackup.mediaLocation = (ImageLocation) args[2];
                }
            }
            if (currentImageKey != null && currentImageKey.equals(oldKey)) {
                currentImageKey = (String) args[1];
                currentImageLocation = (ImageLocation) args[2];
                if (setImageBackup != null) {
                    setImageBackup.imageLocation = (ImageLocation) args[2];
                }
            }
            if (currentThumbKey != null && currentThumbKey.equals(oldKey)) {
                currentThumbKey = (String) args[1];
                currentThumbLocation = (ImageLocation) args[2];
                if (setImageBackup != null) {
                    setImageBackup.thumbLocation = (ImageLocation) args[2];
                }
            }
        } else if (id == NotificationCenter.stopAllHeavyOperations) {
            Integer layer = (Integer) args[0];
            if (currentLayerNum >= layer) {
                return;
            }
            currentOpenedLayerFlags |= layer;
            if (currentOpenedLayerFlags != 0) {
                RLottieDrawable lottieDrawable = getLottieAnimation();
                if (lottieDrawable != null) {
                    lottieDrawable.stop();
                }
            }
        } else if (id == NotificationCenter.startAllHeavyOperations) {
            Integer layer = (Integer) args[0];
            if (currentLayerNum >= layer || currentOpenedLayerFlags == 0) {
                return;
            }
            currentOpenedLayerFlags &=~ layer;
            if (currentOpenedLayerFlags == 0) {
                RLottieDrawable lottieDrawable = getLottieAnimation();
                if (allowStartLottieAnimation && lottieDrawable != null) {
                    lottieDrawable.start();
                }
            }
        }
    }
}
