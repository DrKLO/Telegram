package org.telegram.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.MotionBackgroundDrawable;

import java.util.ArrayList;
import java.util.Objects;

public class ChatBackgroundDrawable extends Drawable {

    private final boolean themeIsDark;
    boolean isPattern;
    View parent;
    int alpha = 255;
    float dimAmount;
    ImageReceiver imageReceiver = new ImageReceiver() {
        @Override
        public void invalidate() {
            if (parent != null) {
                parent.invalidate();
            }
        }
    };

    MotionBackgroundDrawable motionBackgroundDrawable;
    final TLRPC.WallPaper wallpaper;
    private boolean colorFilterSetted;

    public static Drawable getOrCreate(Drawable backgroundDrawable, TLRPC.WallPaper wallpaper, boolean themeIsDark) {
        if (backgroundDrawable instanceof ChatBackgroundDrawable) {
            ChatBackgroundDrawable chatBackgroundDrawable = (ChatBackgroundDrawable) backgroundDrawable;
            if (wallpaper.uploadingImage != null) {
                if (wallpaper.uploadingImage.equals(chatBackgroundDrawable.wallpaper.uploadingImage)) {
                    if (wallpaper.settings != null && chatBackgroundDrawable.wallpaper.settings != null && wallpaper.settings.intensity > 0) {
                        if (chatBackgroundDrawable.themeIsDark == themeIsDark) {
                            return chatBackgroundDrawable;
                        }
                    } else {
                        return chatBackgroundDrawable;
                    }
                }
            } else if (wallpaper.id == chatBackgroundDrawable.wallpaper.id && TextUtils.equals(hash(wallpaper.settings), hash(chatBackgroundDrawable.wallpaper.settings))) {
                if (wallpaper.document != null && !wallpaper.pattern && wallpaper.settings != null && wallpaper.settings.intensity > 0) {
                    if (chatBackgroundDrawable.themeIsDark == themeIsDark) {
                        return chatBackgroundDrawable;
                    }
                } else {
                    return chatBackgroundDrawable;
                }
            }
        }
        return new ChatBackgroundDrawable(wallpaper, themeIsDark, false);
    }

    public void setParent(View parent) {
        this.parent = parent;
        if (motionBackgroundDrawable != null) {
            motionBackgroundDrawable.setParentView(parent);
        }
    }

    public ChatBackgroundDrawable(TLRPC.WallPaper wallPaper) {
        this(wallPaper, false, false);
    }

    public ChatBackgroundDrawable(TLRPC.WallPaper wallPaper, boolean themeIsDark, boolean preview) {
        imageReceiver.setInvalidateAll(true);
        isPattern = wallPaper.pattern;
        this.wallpaper = wallPaper;
        this.themeIsDark = themeIsDark;
        if (themeIsDark && (wallpaper.document != null || wallpaper.uploadingImage != null) && !wallpaper.pattern && wallpaper.settings != null) {
            dimAmount = wallpaper.settings.intensity / 100f;
           // imageReceiver.setColorFilter(new PorterDuffColorFilter(ColorUtils.setAlphaComponent(Color.BLACK, (int) (dimAmount * 255)), PorterDuff.Mode.DARKEN));
        }
        if ((isPattern || wallPaper.document == null) && wallPaper.settings != null && wallPaper.settings.second_background_color != 0 && wallPaper.settings.third_background_color != 0) {
            motionBackgroundDrawable = new MotionBackgroundDrawable();
            motionBackgroundDrawable.setColors(
                    wallPaper.settings.background_color,
                    wallPaper.settings.second_background_color,
                    wallPaper.settings.third_background_color,
                    wallPaper.settings.fourth_background_color
            );
            EmojiThemes.loadWallpaperImage(UserConfig.selectedAccount, wallPaper.id, wallPaper, result -> {
                motionBackgroundDrawable.setPatternBitmap(wallPaper.settings.intensity, result.second);
                if (parent != null) {
                    parent.invalidate();
                }
            });
        } else {
            String imageFilter;
            int w = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            int h = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            if (preview) {
                imageFilter = "150_150_wallpaper";
            } else {
                imageFilter = (int) (w / AndroidUtilities.density) + "_" + (int) (h / AndroidUtilities.density) + "_wallpaper";
            }
            imageFilter += wallPaper.id;
            imageFilter += hash(wallPaper.settings);

            Drawable thumb = createThumb(wallPaper);
            if (wallPaper.uploadingImage != null) {
                imageReceiver.setImage(ImageLocation.getForPath(wallPaper.uploadingImage), imageFilter, thumb, null, wallPaper, 1);
            } else if (wallPaper.document != null) {
                imageReceiver.setImage(ImageLocation.getForDocument(wallPaper.document), imageFilter, thumb, null, wallPaper, 1);
            } else {
                imageReceiver.setImageBitmap(thumb);
            }
        }
    }

    public static Drawable createThumb(TLRPC.WallPaper wallPaper) {
        Drawable thumb = null;
        if (wallPaper.thumbDrawable != null) {
            return wallPaper.thumbDrawable;
        }
        if (wallPaper.stripedThumb != null) {
            return new BitmapDrawable(wallPaper.stripedThumb);
        }
        if (wallPaper.pattern && wallPaper.settings == null) {
            return new ColorDrawable(Color.BLACK);
        }
        if (wallPaper.document != null) {
            for (int i = 0; i < wallPaper.document.thumbs.size(); i++) {
                if (wallPaper.document.thumbs.get(i) instanceof TLRPC.TL_photoStrippedSize) {
                    thumb = new BitmapDrawable(ImageLoader.getStrippedPhotoBitmap(wallPaper.document.thumbs.get(i).bytes, "b"));
                }
            }
        } else {
            if (wallPaper.settings == null || wallPaper.settings.intensity < 0) {
                thumb = bitmapDrawableOf(new ColorDrawable(Color.BLACK));
            } else {
                if (wallPaper.settings.second_background_color == 0) { //one color
                    thumb = bitmapDrawableOf(new ColorDrawable(ColorUtils.setAlphaComponent(wallPaper.settings.background_color, 255)));
                } else if (wallPaper.settings.third_background_color == 0) { //two color
                    int color1 = ColorUtils.setAlphaComponent(wallPaper.settings.background_color, 255);
                    int color2 = ColorUtils.setAlphaComponent(wallPaper.settings.second_background_color, 255);
                    thumb = bitmapDrawableOf(new GradientDrawable(BackgroundGradientDrawable.getGradientOrientation(wallPaper.settings.rotation), new int[]{color1, color2}));
                } else {
                    int color1 = ColorUtils.setAlphaComponent(wallPaper.settings.background_color, 255);
                    int color2 = ColorUtils.setAlphaComponent(wallPaper.settings.second_background_color, 255);
                    int color3 = ColorUtils.setAlphaComponent(wallPaper.settings.third_background_color, 255);
                    int color4 = wallPaper.settings.fourth_background_color == 0 ? 0 : ColorUtils.setAlphaComponent(wallPaper.settings.fourth_background_color, 255);
                    MotionBackgroundDrawable motionBackgroundDrawable = new MotionBackgroundDrawable();
                    motionBackgroundDrawable.setColors(color1, color2, color3, color4);
                    thumb = new BitmapDrawable(motionBackgroundDrawable.getBitmap());
                }
            }
        }
        return wallPaper.thumbDrawable = thumb;
    }

    private static Drawable bitmapDrawableOf(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, 20, 20);
        drawable.draw(canvas);
        return new BitmapDrawable(bitmap);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (motionBackgroundDrawable != null) {
            motionBackgroundDrawable.setBounds(getBounds());
            motionBackgroundDrawable.setAlpha(alpha);
            motionBackgroundDrawable.draw(canvas);
        } else {
            boolean drawDim = false;
            if (!imageReceiver.hasImageLoaded() || imageReceiver.getCurrentAlpha() != 1f) {
                drawDim = true;
            } else if (!colorFilterSetted) {
                colorFilterSetted = true;
                imageReceiver.setColorFilter(new PorterDuffColorFilter(ColorUtils.setAlphaComponent(Color.BLACK, (int) (dimAmount * 255)), PorterDuff.Mode.DARKEN));
            }
            imageReceiver.setImageCoords(getBounds());
            imageReceiver.setAlpha(alpha / 255f);
            imageReceiver.draw(canvas);
            if (drawDim && dimAmount != 0) {
                canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (dimAmount * 255)));
            }
        }
    }

    public float getDimAmount() {
        if (motionBackgroundDrawable == null) {
            return dimAmount;
        }
        return 0;
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha != alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }

    private boolean attached;
    private final ArrayList<View> attachedViews = new ArrayList<>();
    private boolean isAttached() {
        return attachedViews.size() > 0;
    }

    public void onAttachedToWindow(View view) {
        if (!attachedViews.contains(view)) {
            attachedViews.add(view);
        }
        if (isAttached() && !attached) {
            attached = true;
            imageReceiver.onAttachedToWindow();
        } else if (!isAttached() && attached) {
            attached = false;
            imageReceiver.onDetachedFromWindow();
        }
    }

    public void onDetachedFromWindow(View view) {
        if (!attachedViews.contains(view)) {
            attachedViews.remove(view);
        }
        if (isAttached() && !attached) {
            attached = true;
            imageReceiver.onAttachedToWindow();
        } else if (!isAttached() && attached) {
            attached = false;
            imageReceiver.onDetachedFromWindow();
        }
    }

    public Drawable getDrawable(boolean prioritizeThumb) {
        if (motionBackgroundDrawable != null) {
            return motionBackgroundDrawable;
        }
        if (prioritizeThumb && imageReceiver.getStaticThumb() != null) {
            return imageReceiver.getStaticThumb();
        } else if (imageReceiver.getThumb() != null) {
            return imageReceiver.getThumb();
        } else if (imageReceiver.getDrawable() != null) {
            return imageReceiver.getDrawable();
        } else {
            return imageReceiver.getStaticThumb();
        }
    }

    public static String hash(TLRPC.WallPaperSettings settings) {
        if (settings == null) {
            return "";
        }
        return String.valueOf(Objects.hash(settings.blur, settings.motion, settings.intensity, settings.background_color, settings.second_background_color, settings.third_background_color, settings.fourth_background_color));
    }
}
