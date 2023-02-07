package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;

public class RLottieImageView extends ImageView {

    private HashMap<String, Integer> layerColors;
    private RLottieDrawable drawable;
    private ImageReceiver imageReceiver;
    private boolean autoRepeat;
    private boolean attachedToWindow;
    private boolean playing;
    private boolean startOnAttach;
    private Integer layerNum;
    private boolean onlyLastFrame;
    public boolean cached;

    public RLottieImageView(Context context) {
        super(context);
    }

    public void clearLayerColors() {
        layerColors.clear();
    }

    public void setLayerNum(Integer layerNum) {
        this.layerNum = layerNum;
        if (this.imageReceiver != null) {
            this.imageReceiver.setLayerNum(layerNum);
        }
    }

    public void setLayerColor(String layer, int color) {
        if (layerColors == null) {
            layerColors = new HashMap<>();
        }
        layerColors.put(layer, color);
        if (drawable != null) {
            drawable.setLayerColor(layer, color);
        }
    }

    public void replaceColors(int[] colors) {
        if (drawable != null) {
            drawable.replaceColors(colors);
        }
    }

    public void setAnimation(int resId, int w, int h) {
        setAnimation(resId, w, h, null);
    }

    public void setAnimation(int resId, int w, int h, int[] colorReplacement) {
        setAnimation(new RLottieDrawable(resId, "" + resId, AndroidUtilities.dp(w), AndroidUtilities.dp(h), false, colorReplacement));
    }

    public void setOnAnimationEndListener(Runnable r) {
        if (drawable != null) {
            drawable.setOnAnimationEndListener(r);
        }
    }

    public void setAnimation(RLottieDrawable lottieDrawable) {
        if (drawable == lottieDrawable) {
            return;
        }
        if (imageReceiver != null) {
            imageReceiver.onDetachedFromWindow();
            imageReceiver = null;
        }
        drawable = lottieDrawable;
        drawable.setMasterParent(this);
        if (autoRepeat) {
            drawable.setAutoRepeat(1);
        }
        if (layerColors != null) {
            drawable.beginApplyLayerColors();
            for (HashMap.Entry<String, Integer> entry : layerColors.entrySet()) {
                drawable.setLayerColor(entry.getKey(), entry.getValue());
            }
            drawable.commitApplyLayerColors();
        }
        drawable.setAllowDecodeSingleFrame(true);
        setImageDrawable(drawable);
    }


    public void setOnlyLastFrame(boolean onlyLastFrame) {
        this.onlyLastFrame = onlyLastFrame;
    }

    public void setAnimation(TLRPC.Document document, int w, int h) {
        if (imageReceiver != null) {
            imageReceiver.onDetachedFromWindow();
            imageReceiver = null;
        }
        if (document == null) {
            return;
        }
        imageReceiver = new ImageReceiver() {
            @Override
            protected boolean setImageBitmapByKey(Drawable drawable, String key, int type, boolean memCache, int guid) {
                if (drawable != null) {
                    onLoaded();
                }
                return super.setImageBitmapByKey(drawable, key, type, memCache, guid);
            }
        };
        if (onlyLastFrame) {
            imageReceiver.setImage(ImageLocation.getForDocument(document), w + "_" + h + "_lastframe", null, null, null, null, null, 0, null, document, 1);
        } else if ("video/webm".equals(document.mime_type)) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
            imageReceiver.setImage(ImageLocation.getForDocument(document), w + "_" + h + (cached ? "_pcache" : "") + "_" + ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForDocument(thumb, document), null, null, document.size, null, document, 1);
        } else {
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document.thumbs, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
            if (svgThumb != null) {
                svgThumb.overrideWidthAndHeight(512, 512);
            }
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
            imageReceiver.setImage(ImageLocation.getForDocument(document), w + "_" + h + (cached ? "_pcache" : ""), ImageLocation.getForDocument(thumb, document), null, null, null, svgThumb, 0, null, document, 1);
        }
        imageReceiver.setAspectFit(true);
        imageReceiver.setParentView(this);
        if (autoRepeat) {
            imageReceiver.setAutoRepeat(1);
            imageReceiver.setAllowStartLottieAnimation(true);
            imageReceiver.setAllowStartAnimation(true);
        } else {
            imageReceiver.setAutoRepeat(0);
        }
        imageReceiver.setLayerNum(layerNum != null ? layerNum : 7);
        imageReceiver.clip = false;

        setImageDrawable(new Drawable() {

            @Override
            public void draw(@NonNull Canvas canvas) {
                AndroidUtilities.rectTmp2.set(
                    getBounds().centerX() - AndroidUtilities.dp(w) / 2,
                    getBounds().centerY() - AndroidUtilities.dp(h) / 2,
                    getBounds().centerX() + AndroidUtilities.dp(w) / 2,
                    getBounds().centerY() + AndroidUtilities.dp(h) / 2
                );
                imageReceiver.setImageCoords(AndroidUtilities.rectTmp2);
                imageReceiver.draw(canvas);
            }

            @Override
            public void setAlpha(int alpha) {
                imageReceiver.setAlpha(alpha / 255f);
            }

            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {
                imageReceiver.setColorFilter(colorFilter);
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }
        });

        if (attachedToWindow) {
            imageReceiver.onAttachedToWindow();
        }
    }

    protected void onLoaded() {

    }

    public void clearAnimationDrawable() {
        if (drawable != null) {
            drawable.stop();
        }
        if (imageReceiver != null) {
            imageReceiver.onDetachedFromWindow();
            imageReceiver = null;
        }
        drawable = null;
        setImageDrawable(null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        if (imageReceiver != null) {
            imageReceiver.onAttachedToWindow();
            if (playing) {
                imageReceiver.startAnimation();
            }
        }
        if (drawable != null) {
            drawable.setCallback(this);
            if (playing) {
                drawable.start();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        if (drawable != null) {
            drawable.stop();
        }
        if (imageReceiver != null) {
            imageReceiver.onDetachedFromWindow();
        }
    }

    public boolean isPlaying() {
        return drawable != null && drawable.isRunning();
    }

    public void setAutoRepeat(boolean repeat) {
        autoRepeat = repeat;
    }

    public void setProgress(float progress) {
        if (drawable != null) {
            drawable.setProgress(progress);
        }
    }

    public ImageReceiver getImageReceiver() {
        return imageReceiver;
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        drawable = null;
    }

    public void playAnimation() {
        if (drawable == null && imageReceiver == null) {
            return;
        }
        playing = true;
        if (attachedToWindow) {
            if (drawable != null) {
                drawable.start();
            }
            if (imageReceiver != null) {
                imageReceiver.startAnimation();
            }
        } else {
            startOnAttach = true;
        }
    }

    public void stopAnimation() {
        if (drawable == null && imageReceiver == null) {
            return;
        }
        playing = false;
        if (attachedToWindow) {
            if (drawable != null) {
                drawable.stop();
            }
            if (imageReceiver != null) {
                imageReceiver.stopAnimation();
            }
        } else {
            startOnAttach = false;
        }
    }

    public RLottieDrawable getAnimatedDrawable() {
        return drawable;
    }
}
