package org.telegram.ui.Charts.data;

import android.graphics.Color;
import android.text.TextUtils;

import androidx.core.graphics.ColorUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.SegmentTree;
import org.telegram.ui.ActionBar.ThemeColors;
import org.telegram.ui.Stars.StarsController;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChartData {

    public long[] x;
    public float[] xPercentage;
    public String[] daysLookup;
    public ArrayList<Line> lines = new ArrayList<>();
    public long maxValue = 0;
    public long minValue = Long.MAX_VALUE;

    public float oneDayPercentage = 0f;

    public static final int FORMATTER_TON = 1;
    public static final int FORMATTER_XTR = 2;

    public int xTickFormatter = 0;
    public int xTooltipFormatter = 0;
    public float yRate = 0;
    public int yTickFormatter = 0;
    public int yTooltipFormatter = 0;

    protected ChartData() {
    }

    protected long timeStep;

    public ChartData(JSONObject jsonObject) throws JSONException {
        JSONArray columns = jsonObject.getJSONArray("columns");

        int n = columns.length();
        for (int i = 0; i < columns.length(); i++) {
            JSONArray a = columns.getJSONArray(i);
            if (a.getString(0).equals("x")) {
                int len = a.length() - 1;
                x = new long[len];
                for (int j = 0; j < len; j++) {
                    x[j] = a.getLong(j + 1);
                }
            } else {
                Line l = new Line();
                lines.add(l);
                int len = a.length() - 1;
                l.id = a.getString(0);
                l.y = new long[len];
                for (int j = 0; j < len; j++) {
                    l.y[j] = a.getLong(j + 1);
                    if (l.y[j] > l.maxValue) l.maxValue = l.y[j];
                    if (l.y[j] < l.minValue) l.minValue = l.y[j];
                }
            }

            if (x.length > 1) {
                timeStep = x[1] - x[0];
            } else {
                timeStep = 86400000L;
            }
            measure();
        }

        JSONObject colors = jsonObject.optJSONObject("colors");
        JSONObject names = jsonObject.optJSONObject("names");

        try {
            xTickFormatter = getFormatter(jsonObject.getString("xTickFormatter"));
            yTickFormatter = getFormatter(jsonObject.getString("yTickFormatter"));
            xTooltipFormatter = getFormatter(jsonObject.getString("xTooltipFormatter"));
            yTooltipFormatter = getFormatter(jsonObject.getString("yTooltipFormatter"));
        } catch (Exception ignore) {}

        Pattern colorPattern = Pattern.compile("(.*)(#.*)");
        for (int i = 0; i < lines.size(); i++) {
            ChartData.Line line = lines.get(i);

            if (colors != null) {
                Matcher matcher = colorPattern.matcher(colors.getString(line.id));
                if (matcher.matches()) {
                    String key = matcher.group(1);
                    if (!TextUtils.isEmpty(key)) {
                        line.colorKey = ThemeColors.stringKeyToInt("statisticChartLine_" + matcher.group(1).toLowerCase());
                    }

                    line.color = Color.parseColor(matcher.group(2));
                    line.colorDark = ColorUtils.blendARGB(Color.WHITE, line.color, 0.85f);
                }
            }

            if (names != null) {
                line.name = names.getString(line.id);
            }

        }
    }

    public int getFormatter(String value) {
        if (TextUtils.isEmpty(value)) return 0;
        if (value.contains("TON")) return FORMATTER_TON;
        if (value.contains(StarsController.currency)) return FORMATTER_XTR;
        return 0;
    }


    protected void measure() {
        int n = x.length;
        if (n == 0) {
            return;
        }
        long start = x[0];
        long end = x[n - 1];

        xPercentage = new float[n];
        if (n == 1) {
            xPercentage[0] = 1;
        } else {
            for (int i = 0; i < n; i++) {
                xPercentage[i] = (float) (x[i] - start) / (float) (end - start);
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).maxValue > maxValue) maxValue = lines.get(i).maxValue;
            if (lines.get(i).minValue < minValue) minValue = lines.get(i).minValue;

            lines.get(i).segmentTree = new SegmentTree(lines.get(i).y);
        }


        daysLookup = new String[(int) ((end - start) / timeStep) + 10];
        SimpleDateFormat formatter;
        if (timeStep == 1) {
            formatter = null;
        } else if (timeStep < 86400000L) {
            formatter = new SimpleDateFormat("HH:mm");
        } else {
            formatter = new SimpleDateFormat("MMM d");
        }

        for (int i = 0; i < daysLookup.length; i++) {
            if (timeStep == 1) {
                daysLookup[i] = String.format(Locale.ENGLISH, "%02d:00", i);
            } else {
                daysLookup[i] = formatter.format(new Date(start + (i * timeStep)));
            }
        }

        oneDayPercentage = timeStep / (float) (x[x.length - 1] - x[0]);
    }

    public String getDayString(int i) {
        return daysLookup[(int) ((x[i] - x[0]) / timeStep)];
    }

    public int findStartIndex(float v) {
        if (v == 0) return 0;
        int n = xPercentage.length;

        if (n < 2) {
            return 0;
        }
        int left = 0;
        int right = n - 1;


        while (left <= right) {
            int middle = (right + left) >> 1;
            if (v < xPercentage[middle] && (middle == 0 || v > xPercentage[middle - 1])) {
                return middle;
            }
            if (v == xPercentage[middle]) {
                return middle;
            }
            if (v < xPercentage[middle]) {
                right = middle - 1;
            } else if (v > xPercentage[middle]) {
                left = middle + 1;
            }
        }
        return left;
    }

    public int findEndIndex(int left, float v) {
        int n = xPercentage.length;
        if (v == 1f) return n - 1;
        int right = n - 1;

        while (left <= right) {
            int middle = (right + left) >> 1;
            if (v > xPercentage[middle] && (middle == n - 1 || v < xPercentage[middle + 1])) {
                return middle;
            }
            if (v == xPercentage[middle]) {
                return middle;
            }
            if (v < xPercentage[middle]) {
                right = middle - 1;
            } else if (v > xPercentage[middle]) {
                left = middle + 1;
            }
        }
        return right;
    }


    public int findIndex(int left, int right, float v) {

        int n = xPercentage.length;

        if (v <= xPercentage[left]) {
            return left;
        }
        if (v >= xPercentage[right]) {
            return right;
        }

        while (left <= right) {
            int middle = (right + left) >> 1;
            if (v > xPercentage[middle] && (middle == n - 1 || v < xPercentage[middle + 1])) {
                return middle;
            }

            if (v == xPercentage[middle]) {
                return middle;
            }
            if (v < xPercentage[middle]) {
                right = middle - 1;
            } else if (v > xPercentage[middle]) {
                left = middle + 1;
            }
        }
        return right;
    }

    public class Line {
        public long[] y;

        public SegmentTree segmentTree;
        public String id;
        public String name;
        public long maxValue = 0;
        public long minValue = Long.MAX_VALUE;
        public int colorKey;
        public int color = Color.BLACK;
        public int colorDark = Color.WHITE;
    }
}
