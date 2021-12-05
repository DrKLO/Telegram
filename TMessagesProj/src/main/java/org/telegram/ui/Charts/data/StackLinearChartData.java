package org.telegram.ui.Charts.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.SegmentTree;

import java.util.ArrayList;
import java.util.Arrays;

public class StackLinearChartData extends ChartData {

    int[] ySum;
    SegmentTree ySumSegmentTree;

    public int[][] simplifiedY;
    public int simplifiedSize;


    public StackLinearChartData(JSONObject jsonObject,boolean isLanguages) throws JSONException {
        super(jsonObject);

        if (isLanguages) {
            long[] totalCount = new long[lines.size()];
            int[] emptyCount = new int[lines.size()];
            long total = 0;
            for (int k = 0; k < lines.size(); k++) {
                int n = x.length;
                for (int i = 0; i < n; i++) {
                    int v = lines.get(k).y[i];
                    totalCount[k] += v;
                    if (v == 0) {
                        emptyCount[k]++;
                    }
                }
                total += totalCount[k];
            }

            ArrayList<Line> removed = new ArrayList<>();
            for (int k = 0; k < lines.size(); k++) {
                if ((totalCount[k] / (double) total) < 0.01 && emptyCount[k] > (x.length / 2f)) {
                    removed.add(lines.get(k));
                }
            }
            for (Line r : removed) {
                lines.remove(r);
            }
        }

        int n = lines.get(0).y.length;
        int k = lines.size();

        ySum = new int[n];
        for (int i = 0; i < n; i++) {
            ySum[i] = 0;
            for (int j = 0; j < k; j++) {
                ySum[i] += lines.get(j).y[i];
            }
        }
        ySumSegmentTree = new SegmentTree(ySum);
    }

    public StackLinearChartData(ChartData data, long d) {
        int index = Arrays.binarySearch(data.x, d);
        int startIndex = index - 4;
        int endIndex = index + 4;

        if (startIndex < 0) {
            endIndex += -startIndex;
            startIndex = 0;
        }
        if (endIndex > data.x.length - 1) {
            startIndex -= endIndex - data.x.length;
            endIndex = data.x.length - 1;
        }

        if (startIndex < 0) {
            startIndex = 0;
        }

        int n = endIndex - startIndex + 1;

        x = new long[n];
        xPercentage = new float[n];
        lines = new ArrayList<>();

        for (int i = 0; i < data.lines.size(); i++) {
            Line line = new Line();
            line.y = new int[n];
            line.id = data.lines.get(i).id;
            line.name = data.lines.get(i).name;
            line.colorKey = data.lines.get(i).colorKey;
            line.color = data.lines.get(i).color;
            line.colorDark = data.lines.get(i).colorDark;
            lines.add(line);
        }
        int i = 0;
        for (int j = startIndex; j <= endIndex; j++) {
            x[i] = data.x[j];

            for (int k = 0; k < lines.size(); k++) {
                Line line = lines.get(k);
                line.y[i] = data.lines.get(k).y[j];
            }
            i++;
        }

        timeStep = 86400000L;
        measure();
    }

    @Override
    protected void measure() {
        super.measure();
        simplifiedSize = 0;
        int n = xPercentage.length;
        int nl = lines.size();
        int step = Math.max(1, Math.round(n / 140f));
        int maxSize = n / step;
        simplifiedY = new int[nl][maxSize];

        int[] max = new int[nl];

        for (int i = 0; i < n; i++) {
           for(int k = 0; k < nl; k++) {
               ChartData.Line line = lines.get(k);
               if (line.y[i] > max[k]) max[k] = line.y[i];
           }
           if (i % step == 0) {
               for(int k = 0; k < nl; k++) {
                   simplifiedY[k][simplifiedSize] = max[k];
                   max[k] = 0;
               }
               simplifiedSize++;
               if (simplifiedSize >= maxSize) {
                   break;
               }
           }
        }
    }
}
