package org.telegram.ui.Components;

import android.content.Context;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;

import java.util.HashMap;

public class RLottieImageView extends ImageView {

    private HashMap<String, Integer> layerColors;
    private RLottieDrawable drawable;
    private boolean autoRepeat;
    private boolean attachedToWindow;
    private boolean playing;
    private boolean startOnAttach;

    public RLottieImageView(Context context) {
        super(context);
    }

    public void clearLayerColors() {
        layerColors.clear();
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

    public void setAnimation(RLottieDrawable lottieDrawable) {
        drawable = lottieDrawable;
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

    public void clearAnimationDrawable() {
        if (drawable != null) {
            drawable.stop();
        }
        drawable = null;
        setImageDrawable(null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
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
    }

    public boolean isPlaying() {
        return drawable != null && drawable.isRunning();
    }

    public void setAutoRepeat(boolean repeat) {
        autoRepeat = repeat;
    }

    public void setProgress(float progress) {
        if (drawable == null) {
            return;
        }
        drawable.setProgress(progress);
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        drawable = null;
    }

    public void playAnimation() {
        if (drawable == null) {
            return;
        }
        playing = true;
        if (attachedToWindow) {
            drawable.start();
        } else {
            startOnAttach = true;
        }
    }

    public void stopAnimation() {
        if (drawable == null) {
            return;
        }
        playing = false;
        if (attachedToWindow) {
            drawable.stop();
        } else {
            startOnAttach = false;
        }
    }

    public RLottieDrawable getAnimatedDrawable() {
        return drawable;
    }
}
