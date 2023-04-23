/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SecureDocument;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;

public class BackupImageView extends View {

    protected ImageReceiver imageReceiver;
    protected ImageReceiver blurImageReceiver;
    protected int width = -1;
    protected int height = -1;
    public AnimatedEmojiDrawable animatedEmojiDrawable;
    private AvatarDrawable avatarDrawable;
    boolean attached;

    protected boolean hasBlur;
    protected boolean blurAllowed;
    public boolean drawFromStart;

    public BackupImageView(Context context) {
        super(context);
        imageReceiver = new ImageReceiver(this);

        imageReceiver.setDelegate((imageReceiver1, set, thumb, memCache) -> {
            if (set && !thumb) {
                checkCreateBlurredImage();
            }
        });
    }

    public void setBlurAllowed(boolean blurAllowed) {
        if (attached) {
            throw new IllegalStateException("You should call setBlurAllowed(...) only when detached!");
        }
        this.blurAllowed = blurAllowed;
        if (blurAllowed) {
            blurImageReceiver = new ImageReceiver();
        }
    }

    public void setHasBlur(boolean hasBlur) {
        if (hasBlur && !blurAllowed) {
            throw new IllegalStateException("You should call setBlurAllowed(...) before calling setHasBlur(true)!");
        }
        this.hasBlur = hasBlur;
        if (!hasBlur) {
            if (blurImageReceiver.getBitmap() != null && !blurImageReceiver.getBitmap().isRecycled()) {
                blurImageReceiver.getBitmap().recycle();
            }
            blurImageReceiver.setImageBitmap((Bitmap) null);
        }
        checkCreateBlurredImage();
    }

    public void onNewImageSet() {
        if (hasBlur) {
            if (blurImageReceiver.getBitmap() != null && !blurImageReceiver.getBitmap().isRecycled()) {
                blurImageReceiver.getBitmap().recycle();
            }
            blurImageReceiver.setImageBitmap((Bitmap) null);
            checkCreateBlurredImage();
        }
    }

    private void checkCreateBlurredImage() {
        if (hasBlur && blurImageReceiver.getBitmap() == null && imageReceiver.getBitmap() != null) {
            Bitmap bitmap = imageReceiver.getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                blurImageReceiver.setImageBitmap(Utilities.stackBlurBitmapMax(bitmap));
                invalidate();
            }
        }
    }

    public void setOrientation(int angle, boolean center) {
        imageReceiver.setOrientation(angle, center);
    }

    public void setImage(SecureDocument secureDocument, String filter) {
        setImage(ImageLocation.getForSecureDocument(secureDocument), filter, null, null, null, null, null, 0, null);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, String ext, Drawable thumb, Object parentObject) {
        setImage(imageLocation, imageFilter, null, null, thumb, null, ext, 0, parentObject);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Drawable thumb, Object parentObject) {
        setImage(imageLocation, imageFilter, null, null, thumb, null, null, 0, parentObject);
    }

    public void setImage(ImageLocation mediaLocation, String mediaFilter, ImageLocation imageLocation, String imageFilter, Drawable thumb, Object parentObject) {
        imageReceiver.setImage(mediaLocation, mediaFilter, imageLocation, imageFilter, null, null, thumb, 0, null, parentObject, 1);
        onNewImageSet();
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Bitmap thumb, Object parentObject) {
        setImage(imageLocation, imageFilter, null, null, null, thumb, null, 0, parentObject);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Drawable thumb, int size, Object parentObject) {
        setImage(imageLocation, imageFilter, null, null, thumb, null, null, size, parentObject);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Bitmap thumbBitmap, int size, int cacheType, Object parentObject) {
        Drawable thumb = null;
        if (thumbBitmap != null) {
            thumb = new BitmapDrawable(null, thumbBitmap);
        }
        imageReceiver.setImage(imageLocation, imageFilter, null, null, thumb, size, null, parentObject, cacheType);
        onNewImageSet();
    }

    public void setForUserOrChat(TLObject object, AvatarDrawable avatarDrawable) {
        imageReceiver.setForUserOrChat(object, avatarDrawable);
        onNewImageSet();
    }

    public void setForUserOrChat(TLObject object, AvatarDrawable avatarDrawable, Object parent) {
        imageReceiver.setForUserOrChat(object, avatarDrawable, parent);
        onNewImageSet();
    }

    public void setImageMedia(ImageLocation mediaLocation, String mediaFilter, ImageLocation imageLocation, String imageFilter, Bitmap thumbBitmap, int size, int cacheType, Object parentObject) {
        Drawable thumb = null;
        if (thumbBitmap != null) {
            thumb = new BitmapDrawable(null, thumbBitmap);
        }
        imageReceiver.setImage(mediaLocation, mediaFilter, imageLocation, imageFilter, null, null, thumb, size, null, parentObject, cacheType);
        onNewImageSet();
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, int size, Object parentObject) {
        setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, null, null, null, size, parentObject);
    }

    public void setImage(String path, String filter, Drawable thumb) {
        setImage(ImageLocation.getForPath(path), filter, null, null, thumb, null, null, 0, null);
    }

    public void setImage(String path, String filter, String thumbPath, String thumbFilter) {
        setImage(ImageLocation.getForPath(path), filter, ImageLocation.getForPath(thumbPath), thumbFilter, null, null, null, 0, null);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, Drawable thumb, Bitmap thumbBitmap, String ext, int size, Object parentObject) {
        if (thumbBitmap != null) {
            thumb = new BitmapDrawable(null, thumbBitmap);
        }
        imageReceiver.setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, thumb, size, ext, parentObject, 0);
        onNewImageSet();
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, String ext, long size, int cacheType, Object parentObject) {
        imageReceiver.setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, null, size, ext, parentObject, cacheType);
        onNewImageSet();
    }

    public void setImageMedia(VectorAvatarThumbDrawable vectorAvatar, ImageLocation mediaLocation, String mediaFilter, ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, String ext, int size, int cacheType, Object parentObject) {
        if (vectorAvatar != null) {
            imageReceiver.setImageBitmap(vectorAvatar);
        } else {
            imageReceiver.setImage(mediaLocation, mediaFilter, imageLocation, imageFilter, thumbLocation, thumbFilter, null, size, ext, parentObject, cacheType);
        }
        onNewImageSet();
    }

    public void setImageBitmap(Bitmap bitmap) {
        imageReceiver.setImageBitmap(bitmap);
        onNewImageSet();
    }

    public void setImageResource(int resId) {
        Drawable drawable = getResources().getDrawable(resId);
        imageReceiver.setImageBitmap(drawable);
        invalidate();
        onNewImageSet();
    }

    public void setImageResource(int resId, int color) {
        Drawable drawable = getResources().getDrawable(resId);
        if (drawable != null) {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
        imageReceiver.setImageBitmap(drawable);
        invalidate();
        onNewImageSet();
    }

    public void setImageDrawable(Drawable drawable) {
        imageReceiver.setImageBitmap(drawable);
        onNewImageSet();
    }

    public void setLayerNum(int value) {
        imageReceiver.setLayerNum(value);
    }

    public void setRoundRadius(int value) {
        imageReceiver.setRoundRadius(value);
        if (blurAllowed) {
            blurImageReceiver.setRoundRadius(value);
        }
        invalidate();
    }

    public void setRoundRadius(int tl, int tr, int bl, int br) {
        imageReceiver.setRoundRadius(tl, tr, bl ,br);
        if (blurAllowed) {
            blurImageReceiver.setRoundRadius(tl, tr, bl, br);
        }
        invalidate();
    }

    public int[] getRoundRadius() {
        return imageReceiver.getRoundRadius();
    }

    public void setAspectFit(boolean value) {
        imageReceiver.setAspectFit(value);
    }

    public ImageReceiver getImageReceiver() {
        return imageReceiver;
    }

    public void setSize(int w, int h) {
        width = w;
        height = h;
        invalidate();
    }

    public AvatarDrawable getAvatarDrawable() {
        if (avatarDrawable == null) {
            avatarDrawable = new AvatarDrawable();
        }
        return avatarDrawable;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        imageReceiver.onDetachedFromWindow();
        if (blurAllowed) {
            blurImageReceiver.onDetachedFromWindow();
        }
        if (animatedEmojiDrawable != null) {
            animatedEmojiDrawable.removeView(this);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        imageReceiver.onAttachedToWindow();
        if (blurAllowed) {
            blurImageReceiver.onAttachedToWindow();
        }
        if (animatedEmojiDrawable != null) {
            animatedEmojiDrawable.addView(this);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        ImageReceiver imageReceiver = animatedEmojiDrawable != null ? animatedEmojiDrawable.getImageReceiver() : this.imageReceiver;
        if (imageReceiver == null) {
            return;
        }
        if (width != -1 && height != -1) {
            if (drawFromStart) {
                imageReceiver.setImageCoords(0, 0, width, height);
                if (blurAllowed) {
                    blurImageReceiver.setImageCoords(0, 0, width, height);
                }
            } else {
                imageReceiver.setImageCoords((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
                if (blurAllowed) {
                    blurImageReceiver.setImageCoords((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
                }
            }
        } else {
            imageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
            if (blurAllowed) {
                blurImageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
            }
        }
        imageReceiver.draw(canvas);
        if (blurAllowed) {
            blurImageReceiver.draw(canvas);
        }
    }

    public void setColorFilter(ColorFilter colorFilter) {
        imageReceiver.setColorFilter(colorFilter);
    }

    public void setAnimatedEmojiDrawable(AnimatedEmojiDrawable animatedEmojiDrawable) {
        if (this.animatedEmojiDrawable == animatedEmojiDrawable) {
            return;
        }
        if (attached && this.animatedEmojiDrawable != null) {
            this.animatedEmojiDrawable.removeView(this);
        }
        this.animatedEmojiDrawable = animatedEmojiDrawable;
        if (attached && animatedEmojiDrawable != null) {
            animatedEmojiDrawable.addView(this);
        }
        invalidate();
    }

    ValueAnimator roundRadiusAnimator;
    
    public void animateToRoundRadius(int animateToRad) {
        if (getRoundRadius()[0] != animateToRad) {
            if (roundRadiusAnimator != null) {
                roundRadiusAnimator.cancel();
            }
            roundRadiusAnimator = ValueAnimator.ofInt(getRoundRadius()[0], animateToRad);
            roundRadiusAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setRoundRadius((Integer) animation.getAnimatedValue());
                }
            });
            roundRadiusAnimator.setDuration(200);
            roundRadiusAnimator.start();
        }
    }
}
