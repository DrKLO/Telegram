package org.telegram.ui.Charts.view_data;

import android.graphics.Paint;

import org.telegram.ui.Charts.data.ChartData;

public class BarViewData extends LineViewData {


    public final Paint unselectedPaint = new Paint();

    public int blendColor = 0;

    public BarViewData(ChartData.Line line) {
        super(line, false);
        paint.setStyle(Paint.Style.STROKE);
        unselectedPaint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(false);
    }

    public void updateColors() {
        super.updateColors();
    }
}
