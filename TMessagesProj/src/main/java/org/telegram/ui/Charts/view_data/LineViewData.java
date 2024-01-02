package org.telegram.ui.Charts.view_data;

import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Charts.BaseChartView;
import org.telegram.ui.Charts.data.ChartData;


public class LineViewData {

    public final ChartData.Line line;
    public final Paint bottomLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public final Path bottomLinePath = new Path();
    public final Path chartPath = new Path();
    public final Path chartPathPicker = new Path();
    public ValueAnimator animatorIn;
    public ValueAnimator animatorOut;
    public int linesPathBottomSize;

    public float[] linesPath;
    public float[] linesPathBottom;

    public int lineColor;

    public boolean enabled = true;

    public float alpha = 1f;

    private Theme.ResourcesProvider resourcesProvider;

    public LineViewData(ChartData.Line line) {
        this(line, null);
    }

    public LineViewData(ChartData.Line line, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        this.line = line;

        paint.setStrokeWidth(AndroidUtilities.dpf2(2));
        paint.setStyle(Paint.Style.STROKE);
        if (!BaseChartView.USE_LINES) {
            paint.setStrokeJoin(Paint.Join.ROUND);
        }
        paint.setColor(line.color);

        bottomLinePaint.setStrokeWidth(AndroidUtilities.dpf2(1));
        bottomLinePaint.setStyle(Paint.Style.STROKE);
        bottomLinePaint.setColor(line.color);

        selectionPaint.setStrokeWidth(AndroidUtilities.dpf2(10));
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeCap(Paint.Cap.ROUND);
        selectionPaint.setColor(line.color);


        linesPath = new float[line.y.length << 2];
        linesPathBottom = new float[line.y.length << 2];
    }

    public void updateColors() {
        if (line.colorKey >= 0 && Theme.hasThemeKey(line.colorKey)) {
            lineColor = Theme.getColor(line.colorKey, resourcesProvider);
        } else {
            int color = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            boolean darkBackground = ColorUtils.calculateLuminance(color) < 0.5f;
            lineColor = darkBackground ? line.colorDark : line.color;
        }
        paint.setColor(lineColor);
        bottomLinePaint.setColor(lineColor);
        selectionPaint.setColor(lineColor);
    }
}
