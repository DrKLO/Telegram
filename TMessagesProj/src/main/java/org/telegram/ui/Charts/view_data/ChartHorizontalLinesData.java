package org.telegram.ui.Charts.view_data;

import org.telegram.messenger.AndroidUtilities;

public class ChartHorizontalLinesData {

    public int[] values;
    public String[] valuesStr;
    public String[] valuesStr2;
    public int alpha;

    public int fixedAlpha = 255;

    public ChartHorizontalLinesData(int newMaxHeight, int newMinHeight, boolean useMinHeight) {
        this(newMaxHeight, newMinHeight, useMinHeight, 0);
    }

    public ChartHorizontalLinesData(int newMaxHeight, int newMinHeight, boolean useMinHeight, float k) {
        if (!useMinHeight) {
            int v = newMaxHeight;
            if (newMaxHeight > 100) {
                v = round(newMaxHeight);
            }

            int step = Math.max(1, (int) Math.ceil(v / 5f));

            int n;
            if (v < 6) {
                n = Math.max(2, v + 1);
            } else if (v / 2 < 6) {
                n = v / 2 + 1;
                if (v % 2 != 0) {
                    n++;
                }
            } else {
                n = 6;
            }

            values = new int[n];
            valuesStr = new String[n];

            for (int i = 1; i < n; i++) {
                values[i] = i * step;
                valuesStr[i] = AndroidUtilities.formatWholeNumber(values[i], 0);
            }
        } else {
            int n;
            int dif = newMaxHeight - newMinHeight;
            float step;
            if (dif == 0) {
                newMinHeight--;
                n = 3;
                step = 1f;
            } else if (dif < 6) {
                n = Math.max(2, dif + 1);
                step = 1f;
            } else if (dif / 2 < 6) {
                n = dif / 2 + dif % 2 + 1;
                step = 2f;
            } else {
                step = (newMaxHeight - newMinHeight) / 5f;
                if (step <= 0) {
                    step = 1;
                    n = Math.max(2, newMaxHeight - newMinHeight + 1);
                } else {
                    n = 6;
                }
            }

            values = new int[n];
            valuesStr = new String[n];
            if (k > 0) valuesStr2 = new String[n];
            boolean skipFloatValues = step / k < 1;
            for (int i = 0; i < n; i++) {
                values[i] = newMinHeight + (int) (i * step);
                valuesStr[i] = AndroidUtilities.formatWholeNumber(values[i], 0);
                if (k > 0) {
                    float v = (values[i] / k);
                    if (skipFloatValues) {
                        if (v - ((int) v) < 0.01f) {
                            valuesStr2[i] = AndroidUtilities.formatWholeNumber((int) v, 0);
                        } else {
                            valuesStr2[i] = "";
                        }
                    } else {
                        valuesStr2[i] = AndroidUtilities.formatWholeNumber((int) v, 0);
                    }
                }
            }
        }
    }

    public static int lookupHeight(int maxValue) {
        int v = maxValue;
        if (maxValue > 100) {
            v = round(maxValue);
        }

        int step = (int) Math.ceil(v / 5f);
        return step * 5;
    }

    private static int round(int maxValue) {
        float k = maxValue / 5;
        if (k % 10 == 0) return maxValue;
        else return ((maxValue / 10 + 1) * 10);
    }
}
