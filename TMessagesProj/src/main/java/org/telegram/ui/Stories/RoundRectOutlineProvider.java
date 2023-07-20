package org.telegram.ui.Stories;

import android.graphics.Outline;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RoundRectOutlineProvider extends ViewOutlineProvider {

    public float radiusInDp;

    public RoundRectOutlineProvider(int radiusInDp) {
        this.radiusInDp = radiusInDp;
    }

    @Override
    public void getOutline(View view, Outline outline) {
        outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), AndroidUtilities.dpf2(radiusInDp));
    }
}
