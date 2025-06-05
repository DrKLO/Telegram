package org.telegram.ui.Charts.data;


import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.SegmentTree;

public class StackBarChartData extends ChartData {

    public long[] ySum;
    public SegmentTree ySumSegmentTree;

    public StackBarChartData(JSONObject jsonObject) throws JSONException {
        super(jsonObject);
        init();
    }

    public void init() {
        int n = lines.get(0).y.length;
        int k = lines.size();

        ySum = new long[n];
        for (int i = 0; i < n; i++) {
            ySum[i] = 0;
            for (int j = 0; j < k; j++) {
                ySum[i] += lines.get(j).y[i];
            }
        }

        ySumSegmentTree = new SegmentTree(ySum);
    }

    public long findMax(int start, int end) {
        return ySumSegmentTree.rMaxQ(start, end);
    }

}
