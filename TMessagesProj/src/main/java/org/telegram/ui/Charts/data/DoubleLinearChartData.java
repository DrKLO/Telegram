package org.telegram.ui.Charts.data;

import org.json.JSONException;
import org.json.JSONObject;

public class DoubleLinearChartData extends ChartData {

    public float[] linesK;


    public DoubleLinearChartData(JSONObject jsonObject) throws JSONException {
        super(jsonObject);
    }

    @Override
    protected void measure() {
        super.measure();
        int n = lines.size();
        long max = 0;
        for (int i = 0; i < n; i++) {
            final long m = lines.get(i).maxValue;
            if (m > max) max = m;
        }

        linesK = new float[n];

        for (int i = 0; i < n; i++) {
            final long m = lines.get(i).maxValue;
            if (max == m) {
                linesK[i] = 1;
                continue;
            }

            linesK[i] = max / m;
        }
    }
}
