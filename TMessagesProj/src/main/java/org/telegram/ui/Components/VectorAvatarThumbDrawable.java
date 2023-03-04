package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashSet;

public class VectorAvatarThumbDrawable extends Drawable implements AnimatedEmojiSpan.InvalidateHolder, AttachableDrawable, NotificationCenter.NotificationCenterDelegate {

    public static final int TYPE_SMALL = 1;
    public static final int TYPE_PROFILE = 2;
    public static final int TYPE_STATIC = 3;
    public final GradientTools gradientTools = new GradientTools();
    private final int type;
    float roundRadius;
    boolean isPremium;

    ImageReceiver currentParent;
    HashSet<ImageReceiver> parents = new HashSet<>();

    AnimatedEmojiDrawable animatedEmojiDrawable;
    ImageReceiver imageReceiver;
    ImageReceiver stickerPreloadImageReceiver = new ImageReceiver();
    final int currentAccount = UserConfig.selectedAccount;
    boolean imageSeted;
    TLRPC.TL_videoSizeStickerMarkup sizeStickerMarkup;

    public VectorAvatarThumbDrawable(TLRPC.VideoSize vectorImageMarkup, boolean isPremiumUser, int type) {
        this.type = type;
        this.isPremium = isPremiumUser;
        int color1 = ColorUtils.setAlphaComponent(vectorImageMarkup.background_colors.get(0), 255);
        int color2 =  vectorImageMarkup.background_colors.size() > 1 ? ColorUtils.setAlphaComponent(vectorImageMarkup.background_colors.get(1), 255) : 0;
        int color3 = vectorImageMarkup.background_colors.size() > 2 ? ColorUtils.setAlphaComponent(vectorImageMarkup.background_colors.get(2), 255) : 0;
        int color4 = vectorImageMarkup.background_colors.size() > 3 ? ColorUtils.setAlphaComponent(vectorImageMarkup.background_colors.get(3), 255) : 0;
        gradientTools.setColors(color1, color2, color3, color4);
        if (vectorImageMarkup instanceof TLRPC.TL_videoSizeEmojiMarkup) {
            TLRPC.TL_videoSizeEmojiMarkup emojiMarkup = (TLRPC.TL_videoSizeEmojiMarkup) vectorImageMarkup;
            int cacheType = AnimatedEmojiDrawable.STANDARD_LOTTIE_FRAME;
            if (type == TYPE_SMALL && isPremiumUser) {
                cacheType = AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS;
            } else if (type == TYPE_PROFILE) {
                cacheType = AnimatedEmojiDrawable.CACHE_TYPE_AVATAR_CONSTRUCTOR_PREVIEW2;
            }

            animatedEmojiDrawable = new AnimatedEmojiDrawable(cacheType, UserConfig.selectedAccount, emojiMarkup.emoji_id);
        } else if (vectorImageMarkup instanceof TLRPC.TL_videoSizeStickerMarkup) {
            sizeStickerMarkup = (TLRPC.TL_videoSizeStickerMarkup) vectorImageMarkup;
            imageReceiver = new ImageReceiver() {
                @Override
                public void invalidate() {
                    VectorAvatarThumbDrawable.this.invalidate();
                }
            };
            imageReceiver.setInvalidateAll(true);
            if (type == TYPE_SMALL) {
                imageReceiver.setAutoRepeatCount(2);
            }
            setImage();
        }
    }

    private void setImage() {
        TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(currentAccount).getStickerSet(sizeStickerMarkup.stickerset, false);
        if (set != null) {
            imageSeted = true;
            for (int i = 0; i < set.documents.size(); i++) {
                if (set.documents.get(i).id == sizeStickerMarkup.sticker_id) {
                    TLRPC.Document document = set.documents.get(i);
                    TLRPC.Document thumb =  null;
                    String filter = "50_50_firstframe";
                    String thumbFilter = null;
                    if (isPremium && type == TYPE_SMALL) {
                        filter = "50_50";
                        thumbFilter = "50_50_firstframe";
                        thumb = document;
                    } else if (type == TYPE_PROFILE) {
                        filter = "100_100";
                        thumbFilter = "50_50_firstframe";
                        thumb = document;
                    }
                    SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
                    imageReceiver.setImage(ImageLocation.getForDocument(document), filter, ImageLocation.getForDocument(thumb), thumbFilter, null, null, svgThumb, 0, "tgs", document, 0);
                    if (type == TYPE_STATIC) {
                        stickerPreloadImageReceiver.setImage(ImageLocation.getForDocument(document), "100_100", null, null, null, 0, "tgs", document, 0);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        gradientTools.setBounds(getBounds().left, getBounds().top, getBounds().right, getBounds().bottom);

        if (currentParent != null) {
            roundRadius = currentParent.getRoundRadius()[0];
        }
        if (roundRadius == 0) {
            canvas.drawRect(getBounds(), gradientTools.paint);
        } else {
            canvas.drawRoundRect(gradientTools.bounds, roundRadius, roundRadius, gradientTools.paint);
        }
        int cx = getBounds().centerX();
        int cy = getBounds().centerY();
        int size = (int) (getBounds().width() * AvatarConstructorFragment.STICKER_DEFAULT_SCALE) >> 1;
        if (animatedEmojiDrawable != null) {
            if (animatedEmojiDrawable.getImageReceiver() != null) {
                animatedEmojiDrawable.getImageReceiver().setRoundRadius((int) (size * 2 * AvatarConstructorFragment.STICKER_DEFAULT_ROUND_RADIUS));
            }
            animatedEmojiDrawable.setBounds(cx - size, cy - size, cx + size, cy + size);
            animatedEmojiDrawable.draw(canvas);
        }
        if (imageReceiver != null) {
            imageReceiver.setRoundRadius((int) (size * 2 * AvatarConstructorFragment.STICKER_DEFAULT_ROUND_RADIUS));
            imageReceiver.setImageCoords(cx - size, cy - size, size * 2, size * 2);
            imageReceiver.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        gradientTools.paint.setAlpha(alpha);
        if (animatedEmojiDrawable != null) {
            animatedEmojiDrawable.setAlpha(alpha);
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }

    public void setRoundRadius(float roundRadius) {
        this.roundRadius = roundRadius;
    }

    static int attachedToWindowCount = 0;
    @Override
    public void onAttachedToWindow(ImageReceiver parent) {
        if (parent == null) {
            return;
        }
        roundRadius = parent.getRoundRadius()[0];
        if (parents.isEmpty()) {
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.addView(this);
            }
            if (imageReceiver != null) {
                imageReceiver.onAttachedToWindow();
            }
            if (stickerPreloadImageReceiver != null) {
                stickerPreloadImageReceiver.onAttachedToWindow();
            }
        }
        parents.add(parent);
        if (sizeStickerMarkup != null) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
        }
    }

    @Override
    public void onDetachedFromWindow(ImageReceiver parent) {
        parents.remove(parent);
        if (parents.isEmpty()) {
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.removeView(this);
            }
            if (imageReceiver != null) {
                imageReceiver.onDetachedFromWindow();
            }
            if (stickerPreloadImageReceiver != null) {
                stickerPreloadImageReceiver.onDetachedFromWindow();
            }
        }
        if (sizeStickerMarkup != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);
        }
    }

    @Override
    public void invalidate() {
        for (ImageReceiver parent : parents) {
            parent.invalidate();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VectorAvatarThumbDrawable that = (VectorAvatarThumbDrawable) o;
        if (type == that.type && gradientTools.color1 == that.gradientTools.color1 && gradientTools.color2 == that.gradientTools.color2 && gradientTools.color3 == that.gradientTools.color3 && gradientTools.color4 == that.gradientTools.color4) {
            if (animatedEmojiDrawable != null && that.animatedEmojiDrawable != null) {
                return animatedEmojiDrawable.getDocumentId() == that.animatedEmojiDrawable.getDocumentId();
            }
            if (sizeStickerMarkup != null && that.sizeStickerMarkup != null) {
                return sizeStickerMarkup.stickerset.id == that.sizeStickerMarkup.stickerset.id && sizeStickerMarkup.sticker_id == that.sizeStickerMarkup.sticker_id;
            }
        }
        return false;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.groupStickersDidLoad) {
            if (!imageSeted) {
                setImage();
                return;
            }
        }
    }

    public void setParent(ImageReceiver imageReceiver) {
        currentParent = imageReceiver;
    }
}
