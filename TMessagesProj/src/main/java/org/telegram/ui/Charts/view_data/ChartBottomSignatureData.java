package org.telegram.ui.Charts.view_data;

public class ChartBottomSignatureData {

    final public int step;
    final public int stepMax;
    final public int stepMin;

    public int alpha;

    public int fixedAlpha = 255;

    public ChartBottomSignatureData(int step, int stepMax, int stepMin) {
        this.step = step;
        this.stepMax = stepMax;
        this.stepMin = stepMin;
    }
}
