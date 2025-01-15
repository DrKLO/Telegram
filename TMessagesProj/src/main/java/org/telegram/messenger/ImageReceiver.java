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
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ComposeShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.Keep;

import com.google.android.exoplayer2.util.Log;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AttachableDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ClipRoundedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LoadingStickerDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclableDrawable;
import org.telegram.ui.Components.VectorAvatarThumbDrawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImageReceiver implements NotificationCenter.NotificationCenterDelegate {

    List<ImageReceiver> preloadReceivers;
    private boolean allowCrossfadeWithImage = true;
    private boolean allowDrawWhileCacheGenerating;
    private ArrayList<Decorator> decorators;

    public boolean updateThumbShaderMatrix() {
        if (currentThumbDrawable != null && thumbShader != null) {
            drawDrawable(null, currentThumbDrawable, 255, thumbShader, 0, 0, 0, null);
            return true;
        }
        if (staticThumbDrawable != null && staticThumbShader != null) {
            drawDrawable(null, staticThumbDrawable, 255, staticThumbShader, 0, 0, 0, null);
            return true;
        }
        return false;
    }

    public void setPreloadingReceivers(List<ImageReceiver> preloadReceivers) {
        this.preloadReceivers = preloadReceivers;
    }

    public Drawable getImageDrawable() {
        return currentImageDrawable;
    }

    public Drawable getMediaDrawable() {
        return currentMediaDrawable;
    }

    public void updateStaticDrawableThump(Bitmap bitmap) {
        staticThumbShader = null;
        roundPaint.setShader(null);
        setStaticDrawable(new BitmapDrawable(bitmap));
    }

    public void setAllowDrawWhileCacheGenerating(boolean allow) {
        allowDrawWhileCacheGenerating = allow;
    }

    public interface ImageReceiverDelegate {
        void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache);

        default void onAnimationReady(ImageReceiver imageReceiver) {
        }

        default void didSetImageBitmap(int type, String key, Drawable drawable) {

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

        public String getKey() {
            return key;
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
                            fileDrawable.recycle(false);
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
        public long size;
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
    private Runnable parentRunnable;

    private int param;
    private Object currentParentObject;
    private boolean canceledLoading;
    private static PorterDuffColorFilter selectedColorFilter = new PorterDuffColorFilter(0xffdddddd, PorterDuff.Mode.MULTIPLY);
    private static PorterDuffColorFilter selectedGroupColorFilter = new PorterDuffColorFilter(0xffbbbbbb, PorterDuff.Mode.MULTIPLY);
    private boolean forceLoding;
    private long currentTime;
    private int fileLoadingPriority = FileLoader.PRIORITY_NORMAL;

    private int currentLayerNum;
    public boolean ignoreNotifications;
    private int currentOpenedLayerFlags;
    private int isLastFrame;

    private SetImageBackup setImageBackup;

    private Object blendMode;

    private Bitmap gradientBitmap;
    private BitmapShader gradientShader;
    private ComposeShader composeShader;
    private Bitmap legacyBitmap;
    private BitmapShader legacyShader;
    private Canvas legacyCanvas;
    private Paint legacyPaint;

    private ImageLocation strippedLocation;
    private ImageLocation currentImageLocation;
    private String currentImageFilter;
    private String currentImageKey;
    private int imageTag;
    private Drawable currentImageDrawable;
    private BitmapShader imageShader;
    protected int imageOrientation, imageInvert;

    private ImageLocation currentThumbLocation;
    private String currentThumbFilter;
    private String currentThumbKey;
    private int thumbTag;
    private Drawable currentThumbDrawable;
    public BitmapShader thumbShader;
    public BitmapShader staticThumbShader;
    private int thumbOrientation, thumbInvert;

    private ImageLocation currentMediaLocation;
    private String currentMediaFilter;
    private String currentMediaKey;
    private int mediaTag;
    private Drawable currentMediaDrawable;
    private BitmapShader mediaShader;

    private boolean useRoundForThumb = true;

    private Drawable staticThumbDrawable;

    private String currentExt;

    private boolean ignoreImageSet;

    private int currentGuid;

    private long currentSize;
    private int currentCacheType;
    private boolean allowLottieVibration = true;
    private boolean allowStartAnimation = true;
    private boolean allowStartLottieAnimation = true;
    private boolean useSharedAnimationQueue;
    private boolean allowDecodeSingleFrame;
    private int autoRepeat = 1;
    private int autoRepeatCount = -1;
    private long autoRepeatTimeout;
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
    private final RectF drawRegion = new RectF();
    private boolean isVisible = true;
    private boolean isAspectFit;
    private boolean forcePreview;
    private boolean forceCrossfade;
    private boolean useRoundRadius = true;
    private final int[] roundRadius = new int[4];
    private int[] emptyRoundRadius;
    private boolean isRoundRect = true;
    private Object mark;

    private Paint roundPaint;
    private final RectF roundRect = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private final Path roundPath = new Path();
    private static final float[] radii = new float[8];
    private float overrideAlpha = 1.0f;
    private int isPressed;
    private boolean centerRotation;
    private ImageReceiverDelegate delegate;
    private float currentAlpha;
    private float previousAlpha = 1f;
    private long lastUpdateAlphaTime;
    private byte crossfadeAlpha = 1;
    private boolean manualAlphaAnimator;
    private boolean crossfadeWithThumb;
    private float crossfadeByScale = .05f;
    private ColorFilter colorFilter;
    private boolean isRoundVideo;
    private long startTime;
    private long endTime;
    private int crossfadeDuration = DEFAULT_CROSSFADE_DURATION;
    private float pressedProgress;
    private int animateFromIsPressed;
    private String uniqKeyPrefix;
    private ArrayList<Runnable> loadingOperations = new ArrayList<>();
    private boolean attachedToWindow;
    private boolean videoThumbIsSame;
    private boolean allowLoadingOnAttachedOnly = false;
    private boolean skipUpdateFrame;
    public boolean clip = true;

    public int animatedFileDrawableRepeatMaxCount;

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

    public void setImage(ImageLocation imageLocation, String imageFilter, Drawable thumb, long size, String ext, Object parentObject, int cacheType) {
        setImage(imageLocation, imageFilter, null, null, thumb, size, ext, parentObject, cacheType);
    }

    public void setImage(String imagePath, String imageFilter, Drawable thumb, String ext, long size) {
        setImage(ImageLocation.getForPath(imagePath), imageFilter, null, null, thumb, size, ext, null, 1);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, String ext, Object parentObject, int cacheType) {
        setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, null, 0, ext, parentObject, cacheType);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, long size, String ext, Object parentObject, int cacheType) {
        setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, null, size, ext, parentObject, cacheType);
    }

    public void setForUserOrChat(TLObject object, Drawable avatarDrawable) {
        setForUserOrChat(object, avatarDrawable, null);
    }
    public void setForUserOrChat(TLObject object, Drawable avatarDrawable, Object parentObject) {
        setForUserOrChat(object, avatarDrawable, parentObject, false, 0, false);
    }

    public void setForUserOrChat(TLObject object, Drawable avatarDrawable, Object parentObject, boolean animationEnabled, int vectorType, boolean big) {
        if (parentObject == null) {
            parentObject = object;
        }
        setUseRoundForThumbDrawable(true);
        BitmapDrawable strippedBitmap = null;
        boolean hasStripped = false;
        ImageLocation videoLocation = null;
        TLRPC.VideoSize vectorImageMarkup = null;
        boolean isPremium = false;
        if (object instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) object;
            isPremium = user.premium;
            if (user.photo != null) {
                strippedBitmap = user.photo.strippedBitmap;
                hasStripped = user.photo.stripped_thumb != null;
                if (vectorType == VectorAvatarThumbDrawable.TYPE_STATIC) {
                    final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(user.id);
                    if (userFull != null) {
                        TLRPC.Photo photo = user.photo.personal ? userFull.personal_photo : userFull.profile_photo;
                        if (photo != null) {
                            vectorImageMarkup = FileLoader.getVectorMarkupVideoSize(photo);
                        }
                    }
                }
                if (vectorImageMarkup == null && animationEnabled && MessagesController.getInstance(currentAccount).isPremiumUser(user) && user.photo.has_video && LiteMode.isEnabled(LiteMode.FLAG_AUTOPLAY_VIDEOS)) {
                    final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(user.id);
                    if (userFull == null) {
                        MessagesController.getInstance(currentAccount).loadFullUser(user, currentGuid, false);
                    } else {
                        TLRPC.Photo photo = user.photo.personal ? userFull.personal_photo : userFull.profile_photo;
                        if (photo != null) {
                            vectorImageMarkup = FileLoader.getVectorMarkupVideoSize(photo);
                            if (vectorImageMarkup == null) {
                                ArrayList<TLRPC.VideoSize> videoSizes = photo.video_sizes;
                                if (videoSizes != null && !videoSizes.isEmpty()) {
                                    TLRPC.VideoSize videoSize = FileLoader.getClosestVideoSizeWithSize(videoSizes, 100);
                                    for (int i = 0; i < videoSizes.size(); i++) {
                                        TLRPC.VideoSize videoSize1 = videoSizes.get(i);
                                        if ("p".equals(videoSize1.type)) {
                                            videoSize = videoSize1;
                                        }
                                        if (videoSize1 instanceof TLRPC.TL_videoSizeEmojiMarkup || videoSize1 instanceof TLRPC.TL_videoSizeStickerMarkup) {
                                            vectorImageMarkup = videoSize1;
                                        }
                                    }
                                    videoLocation = ImageLocation.getForPhoto(videoSize, photo);
                                }
                            }
                        }
                    }
                }
            }
        } else if (object instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) object;
            if (chat.photo != null) {
                strippedBitmap = chat.photo.strippedBitmap;
                hasStripped = chat.photo.stripped_thumb != null;
            }
        }
        if (vectorImageMarkup != null && vectorType != 0) {
            VectorAvatarThumbDrawable drawable = new VectorAvatarThumbDrawable(vectorImageMarkup, isPremium, vectorType);
            setImageBitmap(drawable);
        } else {
            ImageLocation location;
            String filter;
            if (!big) {
                location = ImageLocation.getForUserOrChat(object, ImageLocation.TYPE_SMALL);
                filter = "50_50";
            } else {
                location = ImageLocation.getForUserOrChat(object, ImageLocation.TYPE_BIG);
                filter = "100_100";
            }
            if (videoLocation != null) {
                setImage(videoLocation, "avatar", location, filter, null, null, strippedBitmap, 0, null, parentObject, 0);
                animatedFileDrawableRepeatMaxCount = 3;
            } else {
                if (strippedBitmap != null) {
                    setImage(location, filter, strippedBitmap, null, parentObject, 0);
                } else if (hasStripped) {
                    setImage(location, filter, ImageLocation.getForUserOrChat(object, ImageLocation.TYPE_STRIPPED), "50_50_b", avatarDrawable, parentObject, 0);
                } else {
                    setImage(location, filter, avatarDrawable, null, parentObject, 0);
                }
            }
        }

    }

    public void setImage(ImageLocation fileLocation, String fileFilter, ImageLocation thumbLocation, String thumbFilter, Drawable thumb, Object parentObject, int cacheType) {
        setImage(null, null, fileLocation, fileFilter, thumbLocation, thumbFilter, thumb, 0, null, parentObject, cacheType);
    }

    public void setImage(ImageLocation fileLocation, String fileFilter, ImageLocation thumbLocation, String thumbFilter, Drawable thumb, long size, String ext, Object parentObject, int cacheType) {
        setImage(null, null, fileLocation, fileFilter, thumbLocation, thumbFilter, thumb, size, ext, parentObject, cacheType);
    }

    public void setImage(ImageLocation mediaLocation, String mediaFilter, ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, Drawable thumb, long size, String ext, Object parentObject, int cacheType) {
        if (allowLoadingOnAttachedOnly && !attachedToWindow) {
            if (setImageBackup == null) {
                setImageBackup = new SetImageBackup();
            }
            setImageBackup.mediaLocation = mediaLocation;
            setImageBackup.mediaFilter = mediaFilter;
            setImageBackup.imageLocation = imageLocation;
            setImageBackup.imageFilter = imageFilter;
            setImageBackup.thumbLocation = thumbLocation;
            setImageBackup.thumbFilter = thumbFilter;
            setImageBackup.thumb = thumb;
            setImageBackup.size = size;
            setImageBackup.ext = ext;
            setImageBackup.cacheType = cacheType;
            setImageBackup.parentObject = parentObject;
            return;
        }
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
            composeShader = null;
            thumbShader = null;
            crossfadeShader = null;
            legacyShader = null;
            legacyCanvas = null;
            if (legacyBitmap != null) {
                legacyBitmap.recycle();
                legacyBitmap = null;
            }

            currentExt = ext;
            currentParentObject = null;
            currentCacheType = 0;
            roundPaint.setShader(null);
            setStaticDrawable(thumb);
            currentAlpha = 1.0f;
            previousAlpha = 1f;
            currentSize = 0;

            updateDrawableRadius(staticThumbDrawable);

            ImageLoader.getInstance().cancelLoadingForImageReceiver(this, true);
            invalidate();
            if (delegate != null) {
                delegate.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null, false);
            }
            return;
        }
        String imageKey = imageLocation != null ? imageLocation.getKey(parentObject, null, false) : null;
        if (imageKey == null && imageLocation != null) {
            imageLocation = null;
        }
        animatedFileDrawableRepeatMaxCount = Math.max(autoRepeatCount, 0);
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

        if (uniqKeyPrefix != null) {
            imageKey = uniqKeyPrefix + imageKey;
        }

        String mediaKey = mediaLocation != null ? mediaLocation.getKey(parentObject, null, false) : null;
        if (mediaKey == null && mediaLocation != null) {
            mediaLocation = null;
        }
        if (mediaKey != null && mediaFilter != null) {
            mediaKey += "@" + mediaFilter;
        }

        if (uniqKeyPrefix != null) {
            mediaKey = uniqKeyPrefix + mediaKey;
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
        if (strippedLoc == null) {
            strippedLoc = thumbLocation;
        }

        String thumbKey = thumbLocation != null ? thumbLocation.getKey(parentObject, strippedLoc, false) : null;
        if (thumbKey != null && thumbFilter != null) {
            thumbKey += "@" + thumbFilter;
        }

        if (crossfadeWithOldImage) {
            if (currentParentObject instanceof MessageObject && ((MessageObject) currentParentObject).lastGeoWebFileSet != null && MessageObject.getMedia((MessageObject) currentParentObject) instanceof TLRPC.TL_messageMediaGeoLive) {
                ((MessageObject) currentParentObject).lastGeoWebFileLoaded = ((MessageObject) currentParentObject).lastGeoWebFileSet;
            }
            if (currentMediaDrawable != null) {
                if (currentMediaDrawable instanceof AnimatedFileDrawable) {
                    ((AnimatedFileDrawable) currentMediaDrawable).stop();
                    ((AnimatedFileDrawable) currentMediaDrawable).removeParent(this);
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
                crossfadeShader = staticThumbShader;
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
        setStaticDrawable(thumb);
        imageShader = null;
        composeShader = null;
        thumbShader = null;
        staticThumbShader = null;
        mediaShader = null;
        legacyShader = null;
        legacyCanvas = null;
        roundPaint.setShader(null);
        if (legacyBitmap != null) {
            legacyBitmap.recycle();
            legacyBitmap = null;
        }
        currentAlpha = 1.0f;
        previousAlpha = 1f;

        updateDrawableRadius(staticThumbDrawable);

        if (delegate != null) {
            delegate.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null, false);
        }
        loadImage();
        isRoundVideo = parentObject instanceof MessageObject && ((MessageObject) parentObject).isRoundVideo();
    }

    private void loadImage() {
        ImageLoader.getInstance().loadImageForImageReceiver(this, preloadReceivers);
        invalidate();
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
        setOrientation(angle, 0, center);
    }

    public void setOrientation(int angle, int invert, boolean center) {
        while (angle < 0) {
            angle += 360;
        }
        while (angle > 360) {
            angle -= 360;
        }
        imageOrientation = thumbOrientation = angle;
        imageInvert = thumbInvert = invert;
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

    public int getInvert() {
        return imageInvert;
    }

    public void setLayerNum(int value) {
        currentLayerNum = value;
        if (attachedToWindow) {
            currentOpenedLayerFlags = NotificationCenter.getGlobalInstance().getCurrentHeavyOperationFlags();
            currentOpenedLayerFlags &= ~currentLayerNum;
        }
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
                crossfadeShader = staticThumbShader;
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
            if (attachedToWindow) {
                fileDrawable.addParent(this);
            }
            fileDrawable.setUseSharedQueue(useSharedAnimationQueue || fileDrawable.isWebmSticker);
            if (allowStartAnimation && currentOpenedLayerFlags == 0) {
                fileDrawable.checkRepeat();
            }
            fileDrawable.setAllowDecodeSingleFrame(allowDecodeSingleFrame);
        } else if (bitmap instanceof RLottieDrawable) {
            RLottieDrawable fileDrawable = (RLottieDrawable) bitmap;
            if (attachedToWindow) {
                fileDrawable.addParentView(this);
            }
            if (fileDrawable != null) {
                fileDrawable.setAllowVibration(allowLottieVibration);
            }
            if (allowStartLottieAnimation && (!fileDrawable.isHeavyDrawable() || currentOpenedLayerFlags == 0)) {
                fileDrawable.start();
            }
            fileDrawable.setAllowDecodeSingleFrame(true);
        }
        staticThumbShader = null;
        thumbShader = null;
        roundPaint.setShader(null);
        setStaticDrawable(bitmap);

        updateDrawableRadius(bitmap);
        currentMediaLocation = null;
        currentMediaFilter = null;
        if (currentMediaDrawable instanceof AnimatedFileDrawable) {
            ((AnimatedFileDrawable) currentMediaDrawable).removeParent(this);
        }
        currentMediaDrawable = null;
        currentMediaKey = null;
        mediaShader = null;

        currentImageLocation = null;
        currentImageFilter = null;
        currentImageDrawable = null;
        currentImageKey = null;
        imageShader = null;
        composeShader = null;
        legacyShader = null;
        legacyCanvas = null;
        if (legacyBitmap != null) {
            legacyBitmap.recycle();
            legacyBitmap = null;
        }

        currentThumbLocation = null;
        currentThumbFilter = null;
        currentThumbKey = null;

        currentKeyQuality = false;
        currentExt = null;
        currentSize = 0;
        currentCacheType = 0;
        currentAlpha = 1;
        previousAlpha = 1f;

        if (setImageBackup != null) {
            setImageBackup.clear();
        }

        if (delegate != null) {
            delegate.didSetImage(this, currentThumbDrawable != null || staticThumbDrawable != null, true, false);
        }
        invalidate();
        if (forceCrossfade && crossfadeWithOldImage && crossfadeImage != null) {
            currentAlpha = 0.0f;
            lastUpdateAlphaTime = System.currentTimeMillis();
            crossfadeWithThumb = currentThumbDrawable != null || staticThumbDrawable != null;
        }
    }

    private void setStaticDrawable(Drawable bitmap) {
        if (bitmap == staticThumbDrawable) {
            return;
        }
        AttachableDrawable oldDrawable = null;
        if (staticThumbDrawable instanceof AttachableDrawable) {
            if (staticThumbDrawable.equals(bitmap)) {
                return;
            }
            oldDrawable = (AttachableDrawable) staticThumbDrawable;
        }
        staticThumbDrawable = bitmap;
        if (attachedToWindow && staticThumbDrawable instanceof AttachableDrawable) {
            ((AttachableDrawable) staticThumbDrawable).onAttachedToWindow(this);
        }
        if (attachedToWindow && oldDrawable != null) {
            oldDrawable.onDetachedFromWindow(this);
        }
    }

    private void setDrawableShader(Drawable drawable, BitmapShader shader) {
        if (drawable == currentThumbDrawable) {
            thumbShader = shader;
        } else if (drawable == staticThumbDrawable) {
            staticThumbShader = shader;
        } else if (drawable == currentMediaDrawable) {
            mediaShader = shader;
        } else if (drawable == currentImageDrawable) {
            imageShader = shader;
            if (gradientShader != null && drawable instanceof BitmapDrawable) {
                if (Build.VERSION.SDK_INT >= 28) {
                    composeShader = new ComposeShader(gradientShader, imageShader, PorterDuff.Mode.DST_IN);
                } else {
                    BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                    int w = bitmapDrawable.getBitmap().getWidth();
                    int h = bitmapDrawable.getBitmap().getHeight();
                    if (legacyBitmap == null || legacyBitmap.getWidth() != w || legacyBitmap.getHeight() != h) {
                        if (legacyBitmap != null) {
                            legacyBitmap.recycle();
                        }
                        legacyBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        legacyCanvas = new Canvas(legacyBitmap);
                        legacyShader = new BitmapShader(legacyBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                        if (legacyPaint == null) {
                            legacyPaint = new Paint();
                            legacyPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                        }
                    }
                }
            }
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
        if (drawable == null) {
            return;
        }
        int[] r = getRoundRadius(true);
        if (drawable instanceof ClipRoundedDrawable) {
            ((ClipRoundedDrawable) drawable).setRadii(r[0], r[1], r[2], r[3]);
        } else if ((hasRoundRadius() || gradientShader != null) && (drawable instanceof BitmapDrawable || drawable instanceof AvatarDrawable)) {
            if (drawable instanceof AvatarDrawable) {
                ((AvatarDrawable) drawable).setRoundRadius(r[0]);
            } else {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                if (bitmapDrawable instanceof RLottieDrawable) {

                } else if (bitmapDrawable instanceof AnimatedFileDrawable) {
                    AnimatedFileDrawable animatedFileDrawable = (AnimatedFileDrawable) drawable;
                    animatedFileDrawable.setRoundRadius(r);
                } else if (bitmapDrawable.getBitmap() != null && !bitmapDrawable.getBitmap().isRecycled()) {
                    setDrawableShader(drawable, new BitmapShader(bitmapDrawable.getBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                }
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
        if (!attachedToWindow) {
            return;
        }
        attachedToWindow = false;
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
        if (!ignoreNotifications) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReplacedPhotoInMemCache);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.stopAllHeavyOperations);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.startAllHeavyOperations);
        }
        if (staticThumbDrawable instanceof AttachableDrawable) {
            ((AttachableDrawable) staticThumbDrawable).onDetachedFromWindow(this);
        }

        if (staticThumbDrawable != null) {
            setStaticDrawable(null);
            staticThumbShader = null;
        }
        clearImage();
        roundPaint.setShader(null);
        if (isPressed == 0) {
            pressedProgress = 0f;
        }

        AnimatedFileDrawable animatedFileDrawable = getAnimation();
        if (animatedFileDrawable != null) {
            animatedFileDrawable.removeParent(this);
        }
        RLottieDrawable lottieDrawable = getLottieAnimation();
        if (lottieDrawable != null) {
            lottieDrawable.removeParentView(this);
        }
        if (decorators != null) {
            for (int i = 0; i < decorators.size(); i++) {
                decorators.get(i).onDetachedFromWidnow();
            }
        }
    }

    public boolean setBackupImage() {
        if (setImageBackup != null && setImageBackup.isSet()) {
            SetImageBackup temp = setImageBackup;
            setImageBackup = null;
            if (temp.thumb instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) temp.thumb;
                if (!(bitmapDrawable instanceof RLottieDrawable) && !(bitmapDrawable instanceof AnimatedFileDrawable) && bitmapDrawable.getBitmap() != null && bitmapDrawable.getBitmap().isRecycled()) {
                    temp.thumb = null;
                }
            }
            setImage(temp.mediaLocation, temp.mediaFilter, temp.imageLocation, temp.imageFilter, temp.thumbLocation, temp.thumbFilter, temp.thumb, temp.size, temp.ext, temp.parentObject, temp.cacheType);
            temp.clear();
            setImageBackup = temp;
            RLottieDrawable lottieDrawable = getLottieAnimation();
            if (lottieDrawable != null) {
                lottieDrawable.setAllowVibration(allowLottieVibration);
            }
            if (lottieDrawable != null && allowStartLottieAnimation && (!lottieDrawable.isHeavyDrawable() || currentOpenedLayerFlags == 0)) {
                lottieDrawable.start();
            }
            return true;
        }
        return false;
    }

    public boolean onAttachedToWindow() {
        if (attachedToWindow) {
            return false;
        }
        attachedToWindow = true;
        currentOpenedLayerFlags = NotificationCenter.getGlobalInstance().getCurrentHeavyOperationFlags();
        currentOpenedLayerFlags &= ~currentLayerNum;
        if (!ignoreNotifications) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReplacedPhotoInMemCache);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.stopAllHeavyOperations);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.startAllHeavyOperations);
        }
        if (setBackupImage()) {
            return true;
        }
        RLottieDrawable lottieDrawable = getLottieAnimation();
        if (lottieDrawable != null) {
            lottieDrawable.addParentView(this);
            lottieDrawable.setAllowVibration(allowLottieVibration);
        }
        if (lottieDrawable != null && allowStartLottieAnimation && (!lottieDrawable.isHeavyDrawable() || currentOpenedLayerFlags == 0)) {
            lottieDrawable.start();
        }
        AnimatedFileDrawable animatedFileDrawable = getAnimation();
        if (animatedFileDrawable != null) {
            animatedFileDrawable.addParent(this);
        }
        if (animatedFileDrawable != null && allowStartAnimation && currentOpenedLayerFlags == 0) {
            animatedFileDrawable.checkRepeat();
            invalidate();
        }
        if (NotificationCenter.getGlobalInstance().isAnimationInProgress()) {
            didReceivedNotification(NotificationCenter.stopAllHeavyOperations, currentAccount, 512);
        }
        if (staticThumbDrawable instanceof AttachableDrawable) {
            ((AttachableDrawable) staticThumbDrawable).onAttachedToWindow(this);
        }
        if (decorators != null) {
            for (int i = 0; i < decorators.size(); i++) {
                decorators.get(i).onAttachedToWindow(this);
            }
        }
        return false;
    }

    private void drawDrawable(Canvas canvas, Drawable drawable, int alpha, BitmapShader shader, int orientation, int invert,  BackgroundThreadDrawHolder backgroundThreadDrawHolder) {
        if (isPressed == 0 && pressedProgress != 0) {
            pressedProgress -= 16 / 150f;
            if (pressedProgress < 0) {
                pressedProgress = 0;
            }
            invalidate();
        }
        if (isPressed != 0) {
            pressedProgress = 1f;
            animateFromIsPressed = isPressed;
        }
        if (pressedProgress == 0 || pressedProgress == 1f) {
            drawDrawable(canvas, drawable, alpha, shader, orientation, invert, isPressed, backgroundThreadDrawHolder);
        } else {
            drawDrawable(canvas, drawable, alpha, shader, orientation, invert, isPressed, backgroundThreadDrawHolder);
            drawDrawable(canvas, drawable, (int) (alpha * pressedProgress), shader, orientation, invert, animateFromIsPressed, backgroundThreadDrawHolder);
        }
    }

    public void setUseRoundForThumbDrawable(boolean value) {
        useRoundForThumb = value;
    }

    protected void drawDrawable(Canvas canvas, Drawable drawable, int alpha, BitmapShader shader, int orientation, int invert, int isPressed, BackgroundThreadDrawHolder backgroundThreadDrawHolder) {
        float imageX, imageY, imageH, imageW;
        RectF drawRegion;
        ColorFilter colorFilter;
        int[] roundRadius;
        boolean reactionLastFrame = false;
        if (backgroundThreadDrawHolder != null) {
            imageX = backgroundThreadDrawHolder.imageX;
            imageY = backgroundThreadDrawHolder.imageY;
            imageH = backgroundThreadDrawHolder.imageH;
            imageW = backgroundThreadDrawHolder.imageW;
            drawRegion = backgroundThreadDrawHolder.drawRegion;
            colorFilter = backgroundThreadDrawHolder.colorFilter;
            roundRadius = backgroundThreadDrawHolder.roundRadius;
        } else {
            imageX = this.imageX;
            imageY = this.imageY;
            imageH = this.imageH;
            imageW = this.imageW;
            drawRegion = this.drawRegion;
            colorFilter = this.colorFilter;
            roundRadius = this.roundRadius;
        }
        if (!useRoundRadius) roundRadius = emptyRoundRadius;
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (drawable instanceof RLottieDrawable) {
                ((RLottieDrawable) drawable).skipFrameUpdate = skipUpdateFrame;
            } else if (drawable instanceof AnimatedFileDrawable) {
                ((AnimatedFileDrawable) drawable).skipFrameUpdate = skipUpdateFrame;
            }

            Paint paint;
            if (shader != null) {
                paint = roundPaint;
            } else {
                paint = bitmapDrawable.getPaint();
            }
            if (Build.VERSION.SDK_INT >= 29) {
                if (blendMode != null && gradientShader == null) {
                    paint.setBlendMode((BlendMode) blendMode);
                } else {
                    paint.setBlendMode(null);
                }
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
            if (colorFilter != null && gradientShader == null) {
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
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap != null && bitmap.isRecycled()) {
                    return;
                }
                if (orientation % 360 == 90 || orientation % 360 == 270) {
                    bitmapW = bitmap.getHeight();
                    bitmapH = bitmap.getWidth();
                } else {
                    bitmapW = bitmap.getWidth();
                    bitmapH = bitmap.getHeight();
                }
                reactionLastFrame = bitmapDrawable instanceof ReactionLastFrame;
            }
            float realImageW = imageW - sideClip * 2;
            float realImageH = imageH - sideClip * 2;
            float scaleW = imageW == 0 ? 1.0f : (bitmapW / realImageW);
            float scaleH = imageH == 0 ? 1.0f : (bitmapH / realImageH);

            if (reactionLastFrame) {
                scaleW /= ReactionLastFrame.LAST_FRAME_SCALE;
                scaleH /= ReactionLastFrame.LAST_FRAME_SCALE;
            }
            if (shader != null && backgroundThreadDrawHolder == null) {
                if (isAspectFit) {
                    float scale = Math.max(scaleW, scaleH);
                    bitmapW /= scale;
                    bitmapH /= scale;
                    drawRegion.set(imageX + (imageW - bitmapW) / 2, imageY + (imageH - bitmapH) / 2, imageX + (imageW + bitmapW) / 2, imageY + (imageH + bitmapH) / 2);

                    if (isVisible) {
                        shaderMatrix.reset();
                        shaderMatrix.setTranslate((int) drawRegion.left, (int) drawRegion.top);
                        if (invert != 0) {
                            shaderMatrix.preScale(invert == 1 ? -1 : 1, invert == 2 ? -1 : 1, drawRegion.width() / 2f, drawRegion.height() / 2f);
                        }
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
                        final float toScale = 1.0f / scale;
                        shaderMatrix.preScale(toScale, toScale);

                        shader.setLocalMatrix(shaderMatrix);
                        roundPaint.setShader(shader);
                        roundPaint.setAlpha(alpha);
                        roundRect.set(drawRegion);

                        if (isRoundRect && useRoundRadius) {
                            try {
                                if (canvas != null) {
                                    if (roundRadius[0] == 0) {
                                        canvas.drawRect(roundRect, roundPaint);
                                    } else {
                                        canvas.drawRoundRect(roundRect, roundRadius[0], roundRadius[0], roundPaint);
                                    }
                                }
                            } catch (Exception e) {
                                onBitmapException(bitmapDrawable);
                                FileLog.e(e);
                            }
                        } else {
                            for (int a = 0; a < roundRadius.length; a++) {
                                radii[a * 2] = roundRadius[a];
                                radii[a * 2 + 1] = roundRadius[a];
                            }
                            roundPath.reset();
                            roundPath.addRoundRect(roundRect, radii, Path.Direction.CW);
                            roundPath.close();
                            if (canvas != null) {
                                canvas.drawPath(roundPath, roundPaint);
                            }
                        }
                    }
                } else {
                    if (legacyCanvas != null) {
                        roundRect.set(0, 0, legacyBitmap.getWidth(), legacyBitmap.getHeight());
                        legacyCanvas.drawBitmap(gradientBitmap, null, roundRect, null);
                        legacyCanvas.drawBitmap(bitmapDrawable.getBitmap(), null, roundRect, legacyPaint);
                    }
                    if (shader == imageShader && gradientShader != null) {
                        if (composeShader != null) {
                            roundPaint.setShader(composeShader);
                        } else {
                            roundPaint.setShader(legacyShader);
                        }
                    } else {
                        roundPaint.setShader(shader);
                    }
                    float scale = 1.0f / Math.min(scaleW, scaleH);
                    roundRect.set(imageX + sideClip, imageY + sideClip, imageX + imageW - sideClip, imageY + imageH - sideClip);
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
                        if (reactionLastFrame) {
                            shaderMatrix.setTranslate((drawRegion.left + sideClip) - (drawRegion.width() * ReactionLastFrame.LAST_FRAME_SCALE - drawRegion.width()) / 2f, drawRegion.top + sideClip - (drawRegion.height() * ReactionLastFrame.LAST_FRAME_SCALE - drawRegion.height()) / 2f);
                        } else {
                            shaderMatrix.setTranslate(drawRegion.left + sideClip, drawRegion.top + sideClip);
                        }
                        if (invert != 0) {
                            shaderMatrix.preScale(invert == 1 ? -1 : 1, invert == 2 ? -1 : 1, drawRegion.width() / 2f, drawRegion.height() / 2f);
                        }
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
                        if (legacyShader != null) {
                            legacyShader.setLocalMatrix(shaderMatrix);
                        }
                        shader.setLocalMatrix(shaderMatrix);

                        if (composeShader != null) {
                            int bitmapW2 = gradientBitmap.getWidth();
                            int bitmapH2 = gradientBitmap.getHeight();
                            float scaleW2 = imageW == 0 ? 1.0f : (bitmapW2 / realImageW);
                            float scaleH2 = imageH == 0 ? 1.0f : (bitmapH2 / realImageH);
                            if (Math.abs(scaleW2 - scaleH2) > 0.0005f) {
                                if (bitmapW2 / scaleH2 > realImageW) {
                                    bitmapW2 /= scaleH2;
                                    drawRegion.set(imageX - (bitmapW2 - realImageW) / 2, imageY, imageX + (bitmapW2 + realImageW) / 2, imageY + realImageH);
                                } else {
                                    bitmapH2 /= scaleW2;
                                    drawRegion.set(imageX, imageY - (bitmapH2 - realImageH) / 2, imageX + realImageW, imageY + (bitmapH2 + realImageH) / 2);
                                }
                            } else {
                                drawRegion.set(imageX, imageY, imageX + realImageW, imageY + realImageH);
                            }
                            scale = 1.0f / Math.min(imageW == 0 ? 1.0f : (bitmapW2 / realImageW), imageH == 0 ? 1.0f : (bitmapH2 / realImageH));

                            shaderMatrix.reset();
                            shaderMatrix.setTranslate(drawRegion.left + sideClip, drawRegion.top + sideClip);
                            shaderMatrix.preScale(scale, scale);
                            gradientShader.setLocalMatrix(shaderMatrix);
                        }

                        roundPaint.setAlpha(alpha);

                        if (isRoundRect && useRoundRadius) {
                            try {
                                if (canvas != null) {
                                    if (roundRadius[0] == 0) {
                                        if (reactionLastFrame) {
                                            AndroidUtilities.rectTmp.set(roundRect);
                                            AndroidUtilities.rectTmp.inset(-(drawRegion.width() * ReactionLastFrame.LAST_FRAME_SCALE - drawRegion.width()) / 2f, -(drawRegion.height() * ReactionLastFrame.LAST_FRAME_SCALE - drawRegion.height()) / 2f);
                                            canvas.drawRect(AndroidUtilities.rectTmp, roundPaint);
                                        } else {
                                            canvas.drawRect(roundRect, roundPaint);
                                        }
                                    } else {
                                        canvas.drawRoundRect(roundRect, roundRadius[0], roundRadius[0], roundPaint);
                                    }
                                }
                            } catch (Exception e) {
                                if (backgroundThreadDrawHolder == null) {
                                    onBitmapException(bitmapDrawable);
                                }
                                FileLog.e(e);
                            }
                        } else {
                            for (int a = 0; a < roundRadius.length; a++) {
                                radii[a * 2] = roundRadius[a];
                                radii[a * 2 + 1] = roundRadius[a];
                            }
                            roundPath.reset();
                            roundPath.addRoundRect(roundRect, radii, Path.Direction.CW);
                            roundPath.close();
                            if (canvas != null) {
                                canvas.drawPath(roundPath, roundPaint);
                            }
                        }
                    }
                }
            } else {
                if (isAspectFit) {
                    float scale = Math.max(scaleW, scaleH);
                    canvas.save();
                    bitmapW /= scale;
                    bitmapH /= scale;
                    if (backgroundThreadDrawHolder == null) {
                        drawRegion.set(imageX + (imageW - bitmapW) / 2.0f, imageY + (imageH - bitmapH) / 2.0f, imageX + (imageW + bitmapW) / 2.0f, imageY + (imageH + bitmapH) / 2.0f);
                        bitmapDrawable.setBounds((int) drawRegion.left, (int) drawRegion.top, (int) drawRegion.right, (int) drawRegion.bottom);
                        if (bitmapDrawable instanceof AnimatedFileDrawable) {
                            ((AnimatedFileDrawable) bitmapDrawable).setActualDrawRect(drawRegion.left, drawRegion.top, drawRegion.width(), drawRegion.height());
                        }
                    }
                    if (backgroundThreadDrawHolder != null && roundRadius != null && roundRadius[0] > 0) {
                        canvas.save();
                        Path path = backgroundThreadDrawHolder.roundPath == null ? backgroundThreadDrawHolder.roundPath = new Path() : backgroundThreadDrawHolder.roundPath;
                        path.rewind();
                        AndroidUtilities.rectTmp.set(imageX, imageY, imageX + imageW, imageY + imageH);
                        path.addRoundRect(AndroidUtilities.rectTmp, roundRadius[0], roundRadius[2], Path.Direction.CW);
                        canvas.clipPath(path);
                    }
                    if (isVisible) {
                        try {
                            bitmapDrawable.setAlpha(alpha);
                            drawBitmapDrawable(canvas, bitmapDrawable, backgroundThreadDrawHolder, alpha);
                        } catch (Exception e) {
                            if (backgroundThreadDrawHolder == null) {
                                onBitmapException(bitmapDrawable);
                            }
                            FileLog.e(e);
                        }
                    }
                    canvas.restore();
                    if (backgroundThreadDrawHolder != null && roundRadius != null && roundRadius[0] > 0) {
                        canvas.restore();
                    }
                } else {
                    if (canvas != null) {
                        if (Math.abs(scaleW - scaleH) > 0.00001f) {
                            canvas.save();
                            if (clip) {
                                canvas.clipRect(imageX, imageY, imageX + imageW, imageY + imageH);
                            }

                            if (invert == 1) {
                                canvas.scale(-1, 1, imageW / 2, imageH / 2);
                            } else if (invert == 2) {
                                canvas.scale(1, -1, imageW / 2, imageH / 2);
                            }
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
                            if (backgroundThreadDrawHolder == null) {
                                if (orientation % 360 == 90 || orientation % 360 == 270) {
                                    float width = drawRegion.width() / 2;
                                    float height = drawRegion.height() / 2;
                                    float centerX = drawRegion.centerX();
                                    float centerY = drawRegion.centerY();
                                    bitmapDrawable.setBounds((int) (centerX - height), (int) (centerY - width), (int) (centerX + height), (int) (centerY + width));
                                } else {
                                    bitmapDrawable.setBounds((int) drawRegion.left, (int) drawRegion.top, (int) drawRegion.right, (int) drawRegion.bottom);
                                }
                            }
                            if (isVisible) {
                                try {
                                    if (Build.VERSION.SDK_INT >= 29) {
                                        if (blendMode != null) {
                                            bitmapDrawable.getPaint().setBlendMode((BlendMode) blendMode);
                                        } else {
                                            bitmapDrawable.getPaint().setBlendMode(null);
                                        }
                                    }
                                    drawBitmapDrawable(canvas, bitmapDrawable, backgroundThreadDrawHolder, alpha);
                                } catch (Exception e) {
                                    if (backgroundThreadDrawHolder == null) {
                                        onBitmapException(bitmapDrawable);
                                    }
                                    FileLog.e(e);
                                }
                            }

                            canvas.restore();
                        } else {
                            canvas.save();
                            if (invert == 1) {
                                canvas.scale(-1, 1, imageW / 2, imageH / 2);
                            } else if (invert == 2) {
                                canvas.scale(1, -1, imageW / 2, imageH / 2);
                            }
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
                            if (backgroundThreadDrawHolder == null) {
                                if (orientation % 360 == 90 || orientation % 360 == 270) {
                                    float width = drawRegion.width() / 2;
                                    float height = drawRegion.height() / 2;
                                    float centerX = drawRegion.centerX();
                                    float centerY = drawRegion.centerY();
                                    bitmapDrawable.setBounds((int) (centerX - height), (int) (centerY - width), (int) (centerX + height), (int) (centerY + width));
                                } else {
                                    bitmapDrawable.setBounds((int) drawRegion.left, (int) drawRegion.top, (int) drawRegion.right, (int) drawRegion.bottom);
                                }
                            }
                            if (isVisible) {
                                try {
                                    if (Build.VERSION.SDK_INT >= 29) {
                                        if (blendMode != null) {
                                            bitmapDrawable.getPaint().setBlendMode((BlendMode) blendMode);
                                        } else {
                                            bitmapDrawable.getPaint().setBlendMode(null);
                                        }
                                    }

                                    drawBitmapDrawable(canvas, bitmapDrawable, backgroundThreadDrawHolder, alpha);
                                } catch (Exception e) {
                                    onBitmapException(bitmapDrawable);
                                    FileLog.e(e);
                                }
                            }
                            canvas.restore();
                        }
                    }
                }
            }

            if (drawable instanceof RLottieDrawable) {
                ((RLottieDrawable) drawable).skipFrameUpdate = false;
            } else if (drawable instanceof AnimatedFileDrawable) {
                ((AnimatedFileDrawable) drawable).skipFrameUpdate = false;
            }
        } else {
            if (backgroundThreadDrawHolder == null) {
                if (isAspectFit) {
                    int bitmapW = drawable.getIntrinsicWidth();
                    int bitmapH = drawable.getIntrinsicHeight();
                    float realImageW = imageW - sideClip * 2;
                    float realImageH = imageH - sideClip * 2;
                    float scaleW = imageW == 0 ? 1.0f : (bitmapW / realImageW);
                    float scaleH = imageH == 0 ? 1.0f : (bitmapH / realImageH);
                    float scale = Math.max(scaleW, scaleH);
                    bitmapW /= scale;
                    bitmapH /= scale;
                    drawRegion.set(imageX + (imageW - bitmapW) / 2.0f, imageY + (imageH - bitmapH) / 2.0f, imageX + (imageW + bitmapW) / 2.0f, imageY + (imageH + bitmapH) / 2.0f);
                } else {
                    drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH);
                }
                drawable.setBounds((int) drawRegion.left, (int) drawRegion.top, (int) drawRegion.right, (int) drawRegion.bottom);
            }
            if (isVisible && canvas != null) {
                SvgHelper.SvgDrawable svgDrawable = null;
                if (drawable instanceof SvgHelper.SvgDrawable) {
                    svgDrawable = (SvgHelper.SvgDrawable) drawable;
                    svgDrawable.setParent(this);
                } else if (drawable instanceof ClipRoundedDrawable && ((ClipRoundedDrawable) drawable).getDrawable() instanceof SvgHelper.SvgDrawable) {
                    svgDrawable = (SvgHelper.SvgDrawable) ((ClipRoundedDrawable) drawable).getDrawable();
                    svgDrawable.setParent(this);
                }
                if (colorFilter != null && drawable != null) {
                    drawable.setColorFilter(colorFilter);
                }
                try {
                    drawable.setAlpha(alpha);
                    if (backgroundThreadDrawHolder != null) {
                        if (svgDrawable != null) {
                            long time = backgroundThreadDrawHolder.time;
                            if (time == 0) {
                                time = System.currentTimeMillis();
                            }
                            ((SvgHelper.SvgDrawable) drawable).drawInternal(canvas, true, backgroundThreadDrawHolder.threadIndex, time, backgroundThreadDrawHolder.imageX, backgroundThreadDrawHolder.imageY, backgroundThreadDrawHolder.imageW, backgroundThreadDrawHolder.imageH);
                        } else {
                            drawable.draw(canvas);
                        }
                    } else {
                        drawable.draw(canvas);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (svgDrawable != null) {
                    svgDrawable.setParent(null);
                }
            }
        }
    }

    private void drawBitmapDrawable(Canvas canvas, BitmapDrawable bitmapDrawable, BackgroundThreadDrawHolder backgroundThreadDrawHolder, int alpha) {
        if (backgroundThreadDrawHolder != null) {
            if (bitmapDrawable instanceof RLottieDrawable) {
                ((RLottieDrawable) bitmapDrawable).drawInBackground(canvas, backgroundThreadDrawHolder.imageX, backgroundThreadDrawHolder.imageY, backgroundThreadDrawHolder.imageW, backgroundThreadDrawHolder.imageH, alpha, backgroundThreadDrawHolder.colorFilter, backgroundThreadDrawHolder.threadIndex);
            } else if (bitmapDrawable instanceof AnimatedFileDrawable) {
                ((AnimatedFileDrawable) bitmapDrawable).drawInBackground(canvas, backgroundThreadDrawHolder.imageX, backgroundThreadDrawHolder.imageY, backgroundThreadDrawHolder.imageW, backgroundThreadDrawHolder.imageH, alpha, backgroundThreadDrawHolder.colorFilter, backgroundThreadDrawHolder.threadIndex);
            } else {
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap != null) {
                    if (backgroundThreadDrawHolder.paint == null) {
                        backgroundThreadDrawHolder.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    }
                    backgroundThreadDrawHolder.paint.setAlpha(alpha);
                    backgroundThreadDrawHolder.paint.setColorFilter(backgroundThreadDrawHolder.colorFilter);
                    canvas.save();
                    canvas.translate(backgroundThreadDrawHolder.imageX, backgroundThreadDrawHolder.imageY);
                    canvas.scale(backgroundThreadDrawHolder.imageW / bitmap.getWidth(), backgroundThreadDrawHolder.imageH / bitmap.getHeight());
                    canvas.drawBitmap(bitmap, 0, 0, backgroundThreadDrawHolder.paint);
                    canvas.restore();
                }
            }
        } else {
            bitmapDrawable.setAlpha(alpha);
            if (bitmapDrawable instanceof RLottieDrawable) {
                ((RLottieDrawable) bitmapDrawable).drawInternal(canvas, null, false, currentTime, 0);
            } else if (bitmapDrawable instanceof AnimatedFileDrawable) {
                ((AnimatedFileDrawable) bitmapDrawable).drawInternal(canvas, false, currentTime, 0);
            } else {
                bitmapDrawable.draw(canvas);
            }
        }
    }

    public void setBlendMode(Object mode) {
        blendMode = mode;
        invalidate();
    }

    public void setGradientBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            if (gradientShader == null || gradientBitmap != bitmap) {
                gradientShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                updateDrawableRadius(currentImageDrawable);
            }
            isRoundRect = true;
        } else {
            gradientShader = null;
            composeShader = null;
            legacyShader = null;
            legacyCanvas = null;
            if (legacyBitmap != null) {
                legacyBitmap.recycle();
                legacyBitmap = null;
            }
        }
        gradientBitmap = bitmap;
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

    private void checkAlphaAnimation(boolean skip, BackgroundThreadDrawHolder backgroundThreadDrawHolder) {
        if (manualAlphaAnimator) {
            return;
        }
        if (currentAlpha != 1) {
            if (!skip) {
                if (backgroundThreadDrawHolder != null) {
                    long currentTime = System.currentTimeMillis();
                    long dt = currentTime - lastUpdateAlphaTime;
                    if (lastUpdateAlphaTime == 0) {
                        dt = 16;
                    }
                    if (dt > 30 && AndroidUtilities.screenRefreshRate > 60) {
                        dt = 30;
                    }
                    currentAlpha += dt / (float) crossfadeDuration;
                } else {
                    currentAlpha += 16f / (float) crossfadeDuration;
                }
                if (currentAlpha > 1) {
                    currentAlpha = 1;
                    previousAlpha = 1f;
                    if (crossfadeImage != null) {
                        recycleBitmap(null, 2);
                        crossfadeShader = null;
                    }
                }
            }
            if (backgroundThreadDrawHolder != null) {
                AndroidUtilities.runOnUIThread(this::invalidate);
            } else {
                invalidate();
            }
        }
    }

    public void skipDraw() {
//        RLottieDrawable lottieDrawable = getLottieAnimation();
//        if (lottieDrawable != null) {
//            lottieDrawable.setCurrentParentView(parentView);
//            lottieDrawable.updateCurrentFrame();
//        }
    }

    public boolean draw(Canvas canvas) {
        return draw(canvas, null);
    }

    public boolean draw(Canvas canvas, BackgroundThreadDrawHolder backgroundThreadDrawHolder) {
        boolean result = false;
        if (gradientBitmap != null && currentImageKey != null) {
            canvas.save();
            canvas.clipRect(imageX, imageY, imageX + imageW, imageY + imageH);
            canvas.drawColor(0xff000000);
        }
        try {
            Drawable drawable = null;
            AnimatedFileDrawable animation;
            RLottieDrawable lottieDrawable;
            Drawable currentMediaDrawable;
            BitmapShader mediaShader;
            Drawable currentImageDrawable;
            BitmapShader imageShader;
            Drawable currentThumbDrawable;
            BitmapShader thumbShader;
            BitmapShader staticThumbShader;

            boolean crossfadeWithOldImage;
            boolean crossfadingWithThumb;
            Drawable crossfadeImage;
            BitmapShader crossfadeShader;
            Drawable staticThumbDrawable;
            float currentAlpha;
            float previousAlpha;
            float overrideAlpha;

            int[] roundRadius;
            boolean animationNotReady;
            ColorFilter colorFilter;
            boolean drawInBackground = backgroundThreadDrawHolder != null;
            if (drawInBackground) {
                animation = backgroundThreadDrawHolder.animation;
                lottieDrawable = backgroundThreadDrawHolder.lottieDrawable;
                roundRadius = backgroundThreadDrawHolder.roundRadius;
                currentMediaDrawable = backgroundThreadDrawHolder.mediaDrawable;
                mediaShader = backgroundThreadDrawHolder.mediaShader;
                currentImageDrawable = backgroundThreadDrawHolder.imageDrawable;
                imageShader = backgroundThreadDrawHolder.imageShader;
                thumbShader = backgroundThreadDrawHolder.thumbShader;
                staticThumbShader = backgroundThreadDrawHolder.staticThumbShader;
                crossfadeImage = backgroundThreadDrawHolder.crossfadeImage;
                crossfadeWithOldImage = backgroundThreadDrawHolder.crossfadeWithOldImage;
                crossfadingWithThumb = backgroundThreadDrawHolder.crossfadingWithThumb;
                currentThumbDrawable = backgroundThreadDrawHolder.thumbDrawable;
                staticThumbDrawable = backgroundThreadDrawHolder.staticThumbDrawable;
                currentAlpha = backgroundThreadDrawHolder.currentAlpha;
                previousAlpha = backgroundThreadDrawHolder.previousAlpha;
                crossfadeShader = backgroundThreadDrawHolder.crossfadeShader;
                animationNotReady = backgroundThreadDrawHolder.animationNotReady;
                overrideAlpha = backgroundThreadDrawHolder.overrideAlpha;
                colorFilter = backgroundThreadDrawHolder.colorFilter;
            } else {
                animation = getAnimation();
                lottieDrawable = getLottieAnimation();
                roundRadius = this.roundRadius;
                currentMediaDrawable = this.currentMediaDrawable;
                mediaShader = this.mediaShader;
                currentImageDrawable = this.currentImageDrawable;
                imageShader = this.imageShader;
                currentThumbDrawable = this.currentThumbDrawable;
                thumbShader = this.thumbShader;
                staticThumbShader = this.staticThumbShader;
                crossfadeWithOldImage = this.crossfadeWithOldImage;
                crossfadingWithThumb = this.crossfadingWithThumb;
                crossfadeImage = this.crossfadeImage;
                staticThumbDrawable = this.staticThumbDrawable;
                currentAlpha = this.currentAlpha;
                previousAlpha = this.previousAlpha;
                crossfadeShader = this.crossfadeShader;
                overrideAlpha = this.overrideAlpha;
                animationNotReady = animation != null && !animation.hasBitmap() || lottieDrawable != null && !lottieDrawable.hasBitmap();
                colorFilter = this.colorFilter;
            }
            if (!useRoundRadius) roundRadius = emptyRoundRadius;

            if (animation != null) {
                animation.setRoundRadius(roundRadius);
            }
            if (lottieDrawable != null && !drawInBackground) {
                lottieDrawable.setCurrentParentView(parentView);
            }
            if ((animation != null || lottieDrawable != null) && !animationNotReady && !animationReadySent && !drawInBackground) {
                animationReadySent = true;
                if (delegate != null) {
                    delegate.onAnimationReady(this);
                }
            }
            int orientation = 0, invert = 0;
            BitmapShader shaderToUse = null;
            if (!forcePreview && currentMediaDrawable != null && !animationNotReady) {
                drawable = currentMediaDrawable;
                shaderToUse = mediaShader;
                orientation = imageOrientation;
                invert = imageInvert;
            } else if (!forcePreview && currentImageDrawable != null && (!animationNotReady || currentMediaDrawable != null)) {
                drawable = currentImageDrawable;
                shaderToUse = imageShader;
                orientation = imageOrientation;
                invert = imageInvert;
                animationNotReady = false;
            } else if (crossfadeImage != null && !crossfadingWithThumb) {
                drawable = crossfadeImage;
                shaderToUse = crossfadeShader;
                orientation = imageOrientation;
                invert = imageInvert;
            } else if (currentThumbDrawable != null) {
                drawable = currentThumbDrawable;
                shaderToUse = thumbShader;
                orientation = thumbOrientation;
                invert = thumbInvert;
            } else if (staticThumbDrawable instanceof BitmapDrawable) {
                drawable = staticThumbDrawable;
                if (useRoundForThumb && staticThumbShader == null) {
                    updateDrawableRadius(staticThumbDrawable);
                    staticThumbShader = this.staticThumbShader;
                }
                shaderToUse = staticThumbShader;
                orientation = thumbOrientation;
                invert = thumbInvert;
            }

            float crossfadeProgress = currentAlpha;
            if (crossfadeByScale > 0) {
                currentAlpha = Math.min(currentAlpha + crossfadeByScale * currentAlpha, 1);
            }

            if (drawable != null) {
                if (crossfadeAlpha != 0) {
                    if (previousAlpha != 1f && (drawable == currentImageDrawable || drawable == currentMediaDrawable) && staticThumbDrawable != null) {
                        if (useRoundForThumb && staticThumbShader == null) {
                            updateDrawableRadius(staticThumbDrawable);
                            staticThumbShader = this.staticThumbShader;
                        }
                        drawDrawable(canvas, staticThumbDrawable, (int) (overrideAlpha * 255), staticThumbShader, orientation, invert, backgroundThreadDrawHolder);
                    }
                    if (crossfadeWithThumb && animationNotReady) {
                        drawDrawable(canvas, drawable, (int) (overrideAlpha * 255), shaderToUse, orientation, invert, backgroundThreadDrawHolder);
                    } else {
                        Drawable thumbDrawable = null;
                        if (crossfadeWithThumb && currentAlpha != 1.0f) {
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
                                    if (useRoundForThumb && staticThumbShader == null) {
                                        updateDrawableRadius(staticThumbDrawable);
                                        staticThumbShader = this.staticThumbShader;
                                    }
                                    thumbShaderToUse = staticThumbShader;
                                }
                            } else if (drawable == currentThumbDrawable || drawable == crossfadeImage) {
                                if (staticThumbDrawable != null) {
                                    thumbDrawable = staticThumbDrawable;
                                    if (useRoundForThumb && staticThumbShader == null) {
                                        updateDrawableRadius(staticThumbDrawable);
                                        staticThumbShader = this.staticThumbShader;
                                    }
                                    thumbShaderToUse = staticThumbShader;
                                }
                            } else if (drawable == staticThumbDrawable) {
                                if (crossfadeImage != null) {
                                    thumbDrawable = crossfadeImage;
                                    thumbShaderToUse = crossfadeShader;
                                }
                            }
                            if (thumbDrawable != null) {
                                int alpha;
                                if (thumbDrawable instanceof SvgHelper.SvgDrawable || thumbDrawable instanceof Emoji.EmojiDrawable) {
                                    alpha = (int) (overrideAlpha * (1.0f - currentAlpha) * 255);
                                } else {
                                    alpha = (int) (overrideAlpha * previousAlpha * 255);
                                }
                                drawDrawable(canvas, thumbDrawable, alpha, thumbShaderToUse, thumbOrientation, thumbInvert, backgroundThreadDrawHolder);
                                if (alpha != 255 && thumbDrawable instanceof Emoji.EmojiDrawable) {
                                    thumbDrawable.setAlpha(255);
                                }
                            }
                        }
                        boolean restore = false;
                        if (crossfadeByScale > 0 && currentAlpha < 1 && crossfadingWithThumb) {
                            canvas.save();
                            restore = true;
                            roundPath.rewind();
                            AndroidUtilities.rectTmp.set(imageX, imageY, imageX + imageW, imageY + imageH);
                            for (int a = 0; a < roundRadius.length; a++) {
                                radii[a * 2] = roundRadius[a];
                                radii[a * 2 + 1] = roundRadius[a];
                            }
                            roundPath.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
                            canvas.clipPath(roundPath);
                            float s = 1f + crossfadeByScale * (1f - CubicBezierInterpolator.EASE_IN.getInterpolation(crossfadeProgress));
                            canvas.scale(s, s, getCenterX(), getCenterY());
                        }
                        drawDrawable(canvas, drawable, (int) (overrideAlpha * currentAlpha * 255), shaderToUse, orientation, invert, backgroundThreadDrawHolder);
                        if (restore) {
                            canvas.restore();
                        }
                    }
                } else {
                    drawDrawable(canvas, drawable, (int) (overrideAlpha * 255), shaderToUse, orientation, invert, backgroundThreadDrawHolder);
                }

                checkAlphaAnimation(animationNotReady && crossfadeWithThumb, backgroundThreadDrawHolder);
                result = true;
            } else if (staticThumbDrawable != null) {
                if (staticThumbDrawable instanceof VectorAvatarThumbDrawable) {
                    ((VectorAvatarThumbDrawable) staticThumbDrawable).setParent(this);
                }
                drawDrawable(canvas, staticThumbDrawable, (int) (overrideAlpha * 255), null, thumbOrientation, thumbInvert, backgroundThreadDrawHolder);
                checkAlphaAnimation(animationNotReady, backgroundThreadDrawHolder);
                result = true;
            } else {
                checkAlphaAnimation(animationNotReady, backgroundThreadDrawHolder);
            }

            if (drawable == null && animationNotReady && !drawInBackground) {
                invalidate();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (gradientBitmap != null && currentImageKey != null) {
            canvas.restore();
        }
        if (result && isVisible && decorators != null) {
            for (int i = 0; i < decorators.size(); i++) {
                decorators.get(i).onDraw(canvas, this);
            }
        }
        return result;
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
        RLottieDrawable lottieDrawable = getLottieAnimation();
        if (lottieDrawable != null && lottieDrawable.hasBitmap()) {
            return lottieDrawable.getAnimatedBitmap();
        }
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null && animation.hasBitmap()) {
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

    public Drawable getThumb() {
        return currentThumbDrawable;
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
        if (invalidate) {
            invalidate();
        }
    }

    public void invalidate() {
        if (parentView == null) {
            return;
        }
        if (invalidateAll) {
            parentView.invalidate();
        } else {
            parentView.invalidate((int) imageX, (int) imageY, (int) (imageX + imageW), (int) (imageY + imageH));
        }
    }

    public void getParentPosition(int[] position) {
        if (parentView == null) {
            return;
        }
        parentView.getLocationInWindow(position);
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

    public boolean hasMediaSet() {
        return currentMediaDrawable != null;
    }

    public boolean hasBitmapImage() {
        return currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null;
    }

    public boolean hasImageLoaded() {
        return currentImageDrawable != null || currentMediaDrawable != null;
    }

    public boolean hasNotThumb() {
        return currentImageDrawable != null || currentMediaDrawable != null || staticThumbDrawable instanceof VectorAvatarThumbDrawable;
    }

    public boolean hasNotThumbOrOnlyStaticThumb() {
        return currentImageDrawable != null || currentMediaDrawable != null || staticThumbDrawable instanceof VectorAvatarThumbDrawable || (staticThumbDrawable != null && !(staticThumbDrawable instanceof AvatarDrawable) && currentImageKey == null && currentMediaKey == null);
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
        View oldParent = parentView;
        parentView = view;
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null && attachedToWindow) {
            animation.setParentView(parentView);
        }
    }

    public void setImageX(float x) {
        imageX = x;
    }

    public void setImageY(float y) {
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

    public void setImageCoords(Rect bounds) {
        if (bounds != null) {
            imageX = bounds.left;
            imageY = bounds.top;
            imageW = bounds.width();
            imageH = bounds.height();
        }
    }

    public void setImageCoords(RectF bounds) {
        if (bounds != null) {
            imageX = bounds.left;
            imageY = bounds.top;
            imageW = bounds.width();
            imageH = bounds.height();
        }
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

    public long getSize() {
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

    public void setRoundRadius(int tl, int tr, int br, int bl) {
        setRoundRadius(new int[]{tl, tr, br, bl});
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
            if (currentThumbDrawable != null) {
                updateDrawableRadius(currentThumbDrawable);
            }
            if (staticThumbDrawable != null) {
                updateDrawableRadius(staticThumbDrawable);
            }
        }
    }

    public void setRoundRadiusEnabled(boolean enabled) {
        if (useRoundRadius != enabled) {
            useRoundRadius = enabled;
            if (!useRoundRadius && emptyRoundRadius == null) {
                emptyRoundRadius = new int[4];
                emptyRoundRadius[0] = emptyRoundRadius[1] = emptyRoundRadius[2] = emptyRoundRadius[3] = 0;
            }
            if (currentImageDrawable != null && imageShader == null) {
                updateDrawableRadius(currentImageDrawable);
            }
            if (currentMediaDrawable != null && mediaShader == null) {
                updateDrawableRadius(currentMediaDrawable);
            }
            if (currentThumbDrawable != null) {
                updateDrawableRadius(currentThumbDrawable);
            }
            if (staticThumbDrawable != null) {
                updateDrawableRadius(staticThumbDrawable);
            }
        }
    }

    public void setMark(Object mark) {
        this.mark = mark;
    }

    public Object getMark() {
        return mark;
    }

    public void setCurrentAccount(int value) {
        currentAccount = value;
    }

    public int[] getRoundRadius() {
        return roundRadius;
    }

    public int[] getRoundRadius(boolean includingEmpty) {
        return !useRoundRadius && includingEmpty ? emptyRoundRadius : roundRadius;
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

    public TLRPC.Document getQualityThumbDocument() {
        return qulityThumbDocument;
    }

    public void setCrossfadeWithOldImage(boolean value) {
        crossfadeWithOldImage = value;
    }

    public boolean isCrossfadingWithOldImage() {
        return crossfadeWithOldImage && crossfadeImage != null && !crossfadingWithThumb;
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

    public void setAllowLottieVibration(boolean allow) {
        allowLottieVibration = allow;
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

    public int getAutoRepeat() {
        return autoRepeat;
    }

    public void setAutoRepeatCount(int count) {
        autoRepeatCount = count;
        if (getLottieAnimation() != null) {
            getLottieAnimation().setAutoRepeatCount(count);
        } else {
            animatedFileDrawableRepeatMaxCount = count;
            if (getAnimation() != null) {
                getAnimation().repeatCount = 0;
            }
        }
    }

    public void setAutoRepeatTimeout(long timeout) {
        autoRepeatTimeout = timeout;
        RLottieDrawable drawable = getLottieAnimation();
        if (drawable != null) {
            drawable.setAutoRepeatTimeout(autoRepeatTimeout);
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
        } else {
            RLottieDrawable rLottieDrawable = getLottieAnimation();
            if (rLottieDrawable != null && !rLottieDrawable.isRunning()) {
                rLottieDrawable.restart();
            }
        }
    }

    public void stopAnimation() {
        AnimatedFileDrawable animation = getAnimation();
        if (animation != null) {
            animation.stop();
        } else {
            RLottieDrawable rLottieDrawable = getLottieAnimation();
            if (rLottieDrawable != null) {
                rLottieDrawable.stop();
            }
        }
    }

    private boolean emojiPaused;
    public void setEmojiPaused(boolean paused) {
        if (emojiPaused == paused) return;
        emojiPaused = paused;
        allowStartLottieAnimation = !paused;
        RLottieDrawable rLottieDrawable = getLottieAnimation();
        if (rLottieDrawable != null) {
            if (paused) {
                rLottieDrawable.stop();
            } else if (!rLottieDrawable.isRunning()) {
                rLottieDrawable.start();
            }
        }
    }

    public boolean isAnimationRunning() {
        AnimatedFileDrawable animation = getAnimation();
        return animation != null && animation.isRunning();
    }

    public AnimatedFileDrawable getAnimation() {
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
            if (delegate != null) {
                delegate.didSetImageBitmap(type, key, drawable);
            }
            boolean allowCrossFade = true;
            if (!(drawable instanceof AnimatedFileDrawable)) {
                ImageLoader.getInstance().incrementUseCount(currentImageKey);
                if (videoThumbIsSame) {
                    allowCrossFade = drawable != currentImageDrawable && currentAlpha >= 1;
                }
            } else {
                AnimatedFileDrawable animatedFileDrawable = (AnimatedFileDrawable) drawable;
                animatedFileDrawable.setStartEndTime(startTime, endTime);
                if (animatedFileDrawable.isWebmSticker) {
                    ImageLoader.getInstance().incrementUseCount(currentImageKey);
                }
                if (videoThumbIsSame) {
                    allowCrossFade = !animatedFileDrawable.hasBitmap();
                }
            }
            currentImageDrawable = drawable;

            if (drawable instanceof ExtendedBitmapDrawable) {
                imageOrientation = ((ExtendedBitmapDrawable) drawable).getOrientation();
                imageInvert = ((ExtendedBitmapDrawable) drawable).getInvert();
            }
            updateDrawableRadius(drawable);

            if (allowCrossFade && isVisible && (!memCache && !forcePreview || forceCrossfade) && crossfadeDuration != 0) {
                boolean allowCrossfade = true;
                if (currentMediaDrawable instanceof RLottieDrawable && ((RLottieDrawable) currentMediaDrawable).hasBitmap()) {
                    allowCrossfade = false;
                } else if (currentMediaDrawable instanceof AnimatedFileDrawable && ((AnimatedFileDrawable) currentMediaDrawable).hasBitmap()) {
                    allowCrossfade = false;
                } else if (currentImageDrawable instanceof RLottieDrawable) {
                    allowCrossfade = staticThumbDrawable instanceof LoadingStickerDrawable || staticThumbDrawable instanceof SvgHelper.SvgDrawable || staticThumbDrawable instanceof Emoji.EmojiDrawable;
                }
                if (allowCrossfade && (currentThumbDrawable != null || staticThumbDrawable != null || forceCrossfade)) {
                    if (currentThumbDrawable != null && staticThumbDrawable != null) {
                        previousAlpha = currentAlpha;
                    } else {
                        previousAlpha = 1f;
                    }
                    currentAlpha = 0.0f;
                    lastUpdateAlphaTime = System.currentTimeMillis();
                    crossfadeWithThumb = crossfadeImage != null || currentThumbDrawable != null || staticThumbDrawable != null;
                }
            } else {
                currentAlpha = 1.0f;
                previousAlpha = 1f;
            }
        } else if (type == TYPE_MEDIA) {
            if (!key.equals(currentMediaKey)) {
                return false;
            }
            if (delegate != null) {
                delegate.didSetImageBitmap(type, key, drawable);
            }
            if (!(drawable instanceof AnimatedFileDrawable)) {
                ImageLoader.getInstance().incrementUseCount(currentMediaKey);
            } else {
                AnimatedFileDrawable animatedFileDrawable = (AnimatedFileDrawable) drawable;
                animatedFileDrawable.setStartEndTime(startTime, endTime);
                if (animatedFileDrawable.isWebmSticker) {
                    ImageLoader.getInstance().incrementUseCount(currentMediaKey);
                }
                if (videoThumbIsSame && (currentThumbDrawable instanceof AnimatedFileDrawable || currentImageDrawable instanceof AnimatedFileDrawable)) {
                    long currentTimestamp = 0;
                    if (currentThumbDrawable instanceof AnimatedFileDrawable) {
                        currentTimestamp = ((AnimatedFileDrawable) currentThumbDrawable).getLastFrameTimestamp();
                    }
                    animatedFileDrawable.seekTo(currentTimestamp, true, true);
                }
            }
            currentMediaDrawable = drawable;
            updateDrawableRadius(drawable);

            if (currentImageDrawable == null) {
                boolean allowCrossfade = true;
                if (!memCache && !forcePreview || forceCrossfade) {
                    if (currentThumbDrawable == null && staticThumbDrawable == null || currentAlpha == 1.0f || forceCrossfade) {
                        if (currentThumbDrawable != null && staticThumbDrawable != null) {
                            previousAlpha = currentAlpha;
                        } else {
                            previousAlpha = 1f;
                        }
                        currentAlpha = 0.0f;
                        lastUpdateAlphaTime = System.currentTimeMillis();
                        crossfadeWithThumb = crossfadeImage != null || currentThumbDrawable != null || staticThumbDrawable != null;
                    }
                } else {
                    currentAlpha = 1.0f;
                    previousAlpha = 1f;
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
            if (delegate != null) {
                delegate.didSetImageBitmap(type, key, drawable);
            }
            ImageLoader.getInstance().incrementUseCount(currentThumbKey);

            currentThumbDrawable = drawable;
            if (drawable instanceof ExtendedBitmapDrawable) {
                thumbOrientation = ((ExtendedBitmapDrawable) drawable).getOrientation();
                thumbInvert = ((ExtendedBitmapDrawable) drawable).getInvert();
            }
            updateDrawableRadius(drawable);

            if (!memCache && crossfadeAlpha != 2) {
                if (currentParentObject instanceof MessageObject && ((MessageObject) currentParentObject).isRoundVideo() && ((MessageObject) currentParentObject).isSending()) {
                    currentAlpha = 1.0f;
                    previousAlpha = 1f;
                } else {
                    currentAlpha = 0.0f;
                    previousAlpha = 1f;
                    lastUpdateAlphaTime = System.currentTimeMillis();
                    crossfadeWithThumb = staticThumbDrawable != null;
                }
            } else {
                currentAlpha = 1.0f;
                previousAlpha = 1f;
            }
        }
        if (delegate != null) {
            delegate.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumbDrawable != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null, memCache);
        }
        if (drawable instanceof AnimatedFileDrawable) {
            AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) drawable;
            fileDrawable.setUseSharedQueue(useSharedAnimationQueue);
            if (attachedToWindow) {
                fileDrawable.addParent(this);
            }
            if (allowStartAnimation && currentOpenedLayerFlags == 0) {
                fileDrawable.checkRepeat();
            }
            fileDrawable.setAllowDecodeSingleFrame(allowDecodeSingleFrame);
            animationReadySent = false;
            if (parentView != null) {
                parentView.invalidate();
            }
        } else if (drawable instanceof RLottieDrawable) {
            RLottieDrawable fileDrawable = (RLottieDrawable) drawable;
            if (attachedToWindow) {
                fileDrawable.addParentView(this);
            }
            if (allowStartLottieAnimation && (!fileDrawable.isHeavyDrawable() || currentOpenedLayerFlags == 0)) {
                fileDrawable.start();
            }
            fileDrawable.setAllowDecodeSingleFrame(true);
            fileDrawable.setAutoRepeat(autoRepeat);
            fileDrawable.setAutoRepeatCount(autoRepeatCount);
            fileDrawable.setAutoRepeatTimeout(autoRepeatTimeout);
            fileDrawable.setAllowDrawFramesWhileCacheGenerating(allowDrawWhileCacheGenerating);
            animationReadySent = false;
        }
        invalidate();
        return true;
    }

    public void setMediaStartEndTime(long startTime, long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;

        if (currentMediaDrawable instanceof AnimatedFileDrawable) {
            ((AnimatedFileDrawable) currentMediaDrawable).setStartEndTime(startTime, endTime);
        }
    }

    public void recycleBitmap(String newKey, int type) {
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
            lottieDrawable.removeParentView(this);
        }
        if (image instanceof AnimatedFileDrawable) {
            AnimatedFileDrawable animatedFileDrawable = (AnimatedFileDrawable) image;
            animatedFileDrawable.removeParent(this);
        }
        if (key != null && (newKey == null || !newKey.equals(key)) && image != null) {
            if (image instanceof RLottieDrawable) {
                RLottieDrawable fileDrawable = (RLottieDrawable) image;
                boolean canDelete = ImageLoader.getInstance().decrementUseCount(key);
                if (!ImageLoader.getInstance().isInMemCache(key, true)) {
                    if (canDelete) {
                        fileDrawable.recycle(false);
                    }
                }
            } else if (image instanceof AnimatedFileDrawable) {
                AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) image;
                if (fileDrawable.isWebmSticker) {
                    boolean canDelete = ImageLoader.getInstance().decrementUseCount(key);
                    if (!ImageLoader.getInstance().isInMemCache(key, true)) {
                        if (canDelete) {
                            fileDrawable.recycle();
                        }
                    } else if (canDelete) {
                        fileDrawable.stop();
                    }
                } else {
                    if (fileDrawable.getParents().isEmpty()) {
                        fileDrawable.recycle();
                    }
                }
            } else if (image instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) image).getBitmap();
                boolean canDelete = ImageLoader.getInstance().decrementUseCount(key);
                if (!ImageLoader.getInstance().isInMemCache(key, false)) {
                    if (canDelete) {
                        ArrayList<Bitmap> bitmapToRecycle = new ArrayList<>();
                        bitmapToRecycle.add(bitmap);
                        AndroidUtilities.recycleBitmaps(bitmapToRecycle);
                    }
                }
            }
        }
        if (type == TYPE_MEDIA) {
            currentMediaKey = null;
            currentMediaDrawable = null;
            mediaShader = null;
        } else if (type == TYPE_CROSSFDADE) {
            crossfadeKey = null;
            crossfadeImage = null;
            crossfadeShader = null;
        } else if (type == TYPE_THUMB) {
            currentThumbDrawable = null;
            currentThumbKey = null;
            thumbShader = null;
        } else {
            currentImageDrawable = null;
            currentImageKey = null;
            imageShader = null;
        }
    }

    public void setCrossfadeDuration(int duration) {
        crossfadeDuration = duration;
    }

    public void setCrossfadeByScale(float value) {
        crossfadeByScale = value;
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
                if (lottieDrawable != null && lottieDrawable.isHeavyDrawable()) {
                    lottieDrawable.stop();
                }
                AnimatedFileDrawable animatedFileDrawable = getAnimation();
                if (animatedFileDrawable != null) {
                    animatedFileDrawable.stop();
                }
            }
        } else if (id == NotificationCenter.startAllHeavyOperations) {
            Integer layer = (Integer) args[0];
            if (currentLayerNum >= layer || currentOpenedLayerFlags == 0) {
                return;
            }
            currentOpenedLayerFlags &= ~layer;
            if (currentOpenedLayerFlags == 0) {
                RLottieDrawable lottieDrawable = getLottieAnimation();
                if (lottieDrawable != null) {
                    lottieDrawable.setAllowVibration(allowLottieVibration);
                }
                if (allowStartLottieAnimation && lottieDrawable != null && lottieDrawable.isHeavyDrawable()) {
                    lottieDrawable.start();
                }
                AnimatedFileDrawable animatedFileDrawable = getAnimation();
                if (allowStartAnimation && animatedFileDrawable != null) {
                    animatedFileDrawable.checkRepeat();
                    invalidate();
                }
            }
        }
    }

    public void startCrossfadeFromStaticThumb(Bitmap thumb) {
        startCrossfadeFromStaticThumb(new BitmapDrawable(null, thumb));
    }

    public void startCrossfadeFromStaticThumb(Drawable thumb) {
        currentThumbKey = null;
        currentThumbDrawable = null;
        thumbShader = null;
        staticThumbShader = null;
        roundPaint.setShader(null);
        setStaticDrawable(thumb);
        crossfadeWithThumb = true;
        currentAlpha = 0f;
        updateDrawableRadius(staticThumbDrawable);
    }

    public void setUniqKeyPrefix(String prefix) {
        uniqKeyPrefix = prefix;
    }

    public String getUniqKeyPrefix() {
        return uniqKeyPrefix;
    }

    public void addLoadingImageRunnable(Runnable loadOperationRunnable) {
        loadingOperations.add(loadOperationRunnable);
    }

    public ArrayList<Runnable> getLoadingOperations() {
        return loadingOperations;
    }

    public void moveImageToFront() {
        ImageLoader.getInstance().moveToFront(currentImageKey);
        ImageLoader.getInstance().moveToFront(currentThumbKey);
    }


    public void moveLottieToFront() {
        BitmapDrawable drawable = null;
        String key = null;
        if (currentMediaDrawable instanceof RLottieDrawable) {
            drawable = (BitmapDrawable) currentMediaDrawable;
            key = currentMediaKey;
        } else if (currentImageDrawable instanceof RLottieDrawable) {
            drawable = (BitmapDrawable) currentImageDrawable;
            key = currentImageKey;
        }
        if (key != null && drawable != null) {
            ImageLoader.getInstance().moveToFront(key);
            if (!ImageLoader.getInstance().isInMemCache(key, true)) {
                ImageLoader.getInstance().getLottieMemCahce().put(key, drawable);
            }
        }
    }

    public View getParentView() {
        return parentView;
    }

    public boolean isAttachedToWindow() {
        return attachedToWindow;
    }

    public void setVideoThumbIsSame(boolean b) {
        videoThumbIsSame = b;
    }

    public void setAllowLoadingOnAttachedOnly(boolean b) {
        allowLoadingOnAttachedOnly = b;
    }

    public void setSkipUpdateFrame(boolean skipUpdateFrame) {
        this.skipUpdateFrame = skipUpdateFrame;
    }

    public void setCurrentTime(long time) {
        this.currentTime = time;
    }

    public void setFileLoadingPriority(int fileLoadingPriority) {
        if (this.fileLoadingPriority != fileLoadingPriority) {
            this.fileLoadingPriority = fileLoadingPriority;
            if (attachedToWindow && hasImageSet()) {
                ImageLoader.getInstance().changeFileLoadingPriorityForImageReceiver(this);
            }
        }
    }

    public void bumpPriority() {
        ImageLoader.getInstance().changeFileLoadingPriorityForImageReceiver(this);
    }

    public int getFileLoadingPriority() {
        return fileLoadingPriority;
    }

    public BackgroundThreadDrawHolder setDrawInBackgroundThread(BackgroundThreadDrawHolder holder, int threadIndex) {
        if (holder == null) {
            holder = new BackgroundThreadDrawHolder();
        }
        holder.threadIndex = threadIndex;
        holder.animation = getAnimation();
        holder.lottieDrawable = getLottieAnimation();
        for (int i = 0; i < 4; i++) {
            holder.roundRadius[i] = roundRadius[i];
        }
        holder.mediaDrawable = currentMediaDrawable;
        holder.mediaShader = mediaShader;
        holder.imageDrawable = currentImageDrawable;
        holder.imageShader = imageShader;
        holder.thumbDrawable = currentThumbDrawable;
        holder.thumbShader = thumbShader;
        holder.staticThumbShader = staticThumbShader;
        holder.staticThumbDrawable = staticThumbDrawable;
        holder.crossfadeImage = crossfadeImage;
        holder.colorFilter = colorFilter;
        holder.crossfadingWithThumb = crossfadingWithThumb;
        holder.crossfadeWithOldImage = crossfadeWithOldImage;
        holder.currentAlpha = currentAlpha;
        holder.previousAlpha = previousAlpha;
        holder.crossfadeShader = crossfadeShader;
        holder.animationNotReady = holder.animation != null && !holder.animation.hasBitmap() || holder.lottieDrawable != null && !holder.lottieDrawable.hasBitmap();
        holder.imageX = imageX;
        holder.imageY = imageY;
        holder.imageW = imageW;
        holder.imageH = imageH;
        holder.overrideAlpha = overrideAlpha;
        return holder;
    }

    public void clearDecorators() {
        if (decorators != null) {
            if (attachedToWindow) {
                for (int i = 0; i < decorators.size(); i++) {
                    decorators.get(i).onDetachedFromWidnow();
                }
            }
            decorators.clear();
        }
    }
    public void addDecorator(Decorator decorator) {
        if (decorators == null) {
            decorators = new ArrayList<>();
        }
        decorators.add(decorator);
        if (attachedToWindow) {
            decorator.onAttachedToWindow(this);
        }
    }

    public static class BackgroundThreadDrawHolder {
        public boolean animationNotReady;
        public float overrideAlpha;
        public long time;
        public int threadIndex;
        public BitmapShader staticThumbShader;
        private AnimatedFileDrawable animation;
        private RLottieDrawable lottieDrawable;
        private int[] roundRadius = new int[4];
        private BitmapShader mediaShader;
        private Drawable mediaDrawable;
        private BitmapShader imageShader;
        private Drawable imageDrawable;
        private Drawable thumbDrawable;
        private BitmapShader thumbShader;
        private Drawable staticThumbDrawable;
        private float currentAlpha;
        private float previousAlpha;
        private BitmapShader crossfadeShader;
        public float imageH, imageW, imageX, imageY;
        private boolean crossfadeWithOldImage;
        private boolean crossfadingWithThumb;
        private Drawable crossfadeImage;
        public RectF drawRegion = new RectF();
        public ColorFilter colorFilter;
        Paint paint;
        private Path roundPath;

        public void release() {
            animation = null;
            lottieDrawable = null;
            for (int i = 0; i < 4; i++) {
                roundRadius[i] = roundRadius[i];
            }
            mediaDrawable = null;
            mediaShader = null;
            imageDrawable = null;
            imageShader = null;
            thumbDrawable = null;
            thumbShader = null;
            staticThumbShader = null;
            staticThumbDrawable = null;
            crossfadeImage = null;
            colorFilter = null;
        }

        public void setBounds(Rect bounds) {
            if (bounds != null) {
                imageX = bounds.left;
                imageY = bounds.top;
                imageW = bounds.width();
                imageH = bounds.height();
            }
        }

        public void getBounds(RectF out) {
            if (out != null) {
                out.left = imageX;
                out.top = imageY;
                out.right = out.left + imageW;
                out.bottom = out.top + imageH;
            }
        }

        public void getBounds(Rect out) {
            if (out != null) {
                out.left = (int) imageX;
                out.top = (int) imageY;
                out.right = (int) (out.left + imageW);
                out.bottom = (int) (out.top + imageH);
            }
        }
    }

    public static class ReactionLastFrame extends BitmapDrawable {

        public final static float LAST_FRAME_SCALE = 1.2f;

        public ReactionLastFrame(Bitmap bitmap) {
            super(bitmap);
        }
    }

    public static abstract class Decorator {
        protected abstract void onDraw(Canvas canvas, ImageReceiver imageReceiver);
        public void onAttachedToWindow(ImageReceiver imageReceiver) {

        }
        public void onDetachedFromWidnow() {

        }
    }
}
