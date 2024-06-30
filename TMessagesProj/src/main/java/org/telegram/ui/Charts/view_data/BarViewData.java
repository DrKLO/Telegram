package org.telegram.ui.Charts.view_data;

import android.graphics.Paint;

import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Charts.data.ChartData;

public class BarViewData extends LineViewData {


    private Theme.ResourcesProvider resourcesProvider;

    public final Paint unselectedPaint = new Paint();

    public int blendColor = 0;

    public BarViewData(ChartData.Line line, Theme.ResourcesProvider resourcesProvider) {
        super(line, false);
        this.resourcesProvider = resourcesProvider;
        paint.setStyle(Paint.Style.STROKE);
        unselectedPaint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(false);
    }

    public void updateColors() {
        super.updateColors();
        blendColor = ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), lineColor,0.3f);
    }
}
