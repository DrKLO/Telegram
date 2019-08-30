package org.telegram.ui.Components;

import android.content.Context;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;

import java.util.HashMap;

public class RLottieImageView extends ImageView {

    private HashMap<String, Integer> layerColors;
    private RLottieDrawable drawable;

    public RLottieImageView(Context context) {
        super(context);
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

    public void setAnimation(int resId, int w, int h) {
        drawable = new RLottieDrawable(resId, "" + resId, AndroidUtilities.dp(w), AndroidUtilities.dp(h), false);
        drawable.beginApplyLayerColors();
        if (layerColors != null) {
            for (HashMap.Entry<String, Integer> entry : layerColors.entrySet()) {
                drawable.setLayerColor(entry.getKey(), entry.getValue());
            }
        }
        drawable.commitApplyLayerColors();
        drawable.setAllowDecodeSingleFrame(true);
        setImageDrawable(drawable);
    }

    public void setProgress(float progress) {
        if (drawable == null) {
            return;
        }
        drawable.setProgress(progress);
    }

    public void playAnimation() {
        if (drawable == null) {
            return;
        }
        drawable.start();
    }
}
