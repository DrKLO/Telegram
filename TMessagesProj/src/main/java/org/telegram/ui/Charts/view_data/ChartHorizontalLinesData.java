package org.telegram.ui.Charts.view_data;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChannelMonetizationLayout;
import org.telegram.ui.Charts.data.ChartData;
import org.telegram.ui.Components.AnimatedEmojiSpan;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class ChartHorizontalLinesData {

    public long[] values;
    public CharSequence[] valuesStr;
    public CharSequence[] valuesStr2;
    private StaticLayout[] layouts;
    private StaticLayout[] layouts2;
    public int alpha;

    public int fixedAlpha = 255;

    public ChartHorizontalLinesData(
        long newMaxHeight,
        long newMinHeight,
        boolean useMinHeight,
        float k,
        int formatter,
        TextPaint firstTextPaint, TextPaint secondTextPaint
    ) {
        if (!useMinHeight) {
            long v = newMaxHeight;
            if (newMaxHeight > 100) {
                v = round(newMaxHeight);
            }

            int step = Math.max(1, (int) Math.ceil(v / 5.0));

            int n;
            if (v < 6) {
                n = (int) Math.max(2, v + 1);
            } else if (v / 2 < 6) {
                n = (int) (v / 2 + 1);
                if (v % 2 != 0) {
                    n++;
                }
            } else {
                n = 6;
            }

            values = new long[n];
            valuesStr = new CharSequence[n];
            layouts = new StaticLayout[n];
            if (k > 0) {
                valuesStr2 = new CharSequence[n];
                layouts2 = new StaticLayout[n];
            }
            boolean skipFloatValues = step / k < 1;
            for (int i = 1; i < n; i++) {
                values[i] = i * step;
                valuesStr[i] = format(0, firstTextPaint, values[i], formatter);
                if (k > 0) {
                    float v2 = (values[i] / k);
                    if (skipFloatValues) {
                        if (v2 - ((int) v2) < 0.01f || formatter == ChartData.FORMATTER_TON) {
                            valuesStr2[i] = format(1, secondTextPaint, (long) v2, formatter);
                        } else {
                            valuesStr2[i] = "";
                        }
                    } else {
                        valuesStr2[i] = format(1, secondTextPaint, (long) v2, formatter);
                    }
                }
            }
        } else {
            int n;
            long dif = newMaxHeight - newMinHeight;
            float step;
            if (dif == 0) {
                newMinHeight--;
                n = 3;
                step = 1f;
            } else if (dif < 6) {
                n = (int) Math.max(2, dif + 1);
                step = 1f;
            } else if (dif / 2 < 6) {
                n = (int) (dif / 2 + dif % 2 + 1);
                step = 2f;
            } else {
                step = (newMaxHeight - newMinHeight) / 5f;
                if (step <= 0) {
                    step = 1;
                    n = (int) (Math.max(2, newMaxHeight - newMinHeight + 1));
                } else {
                    n = 6;
                }
            }

            values = new long[n];
            valuesStr = new CharSequence[n];
            layouts = new StaticLayout[n];
            if (k > 0) {
                valuesStr2 = new CharSequence[n];
                layouts2 = new StaticLayout[n];
            }
            boolean skipFloatValues = step / k < 1;
            for (int i = 0; i < n; i++) {
                values[i] = newMinHeight + (int) (i * step);
                valuesStr[i] = format(0, firstTextPaint, newMinHeight + (long) (i * step), formatter);
                if (k > 0) {
                    float v = (values[i] / k);
                    if (skipFloatValues) {
                        if (v - ((int) v) < 0.01f || formatter == ChartData.FORMATTER_TON) {
                            valuesStr2[i] = format(1, secondTextPaint, (long) v, formatter);
                        } else {
                            valuesStr2[i] = "";
                        }
                    } else {
                        valuesStr2[i] = format(1, secondTextPaint, (long) v, formatter);
                    }
                }
            }
        }
    }

    private DecimalFormat formatterTON;
    public CharSequence format(int a, TextPaint paint, long v, int formatter) {
        if (formatter == ChartData.FORMATTER_TON) {
            if (a == 1) {
                return "~" + BillingController.getInstance().formatCurrency(v, "USD");
            }
            if (formatterTON == null) {
                DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
                symbols.setDecimalSeparator('.');
                formatterTON = new DecimalFormat("#.##", symbols);
                formatterTON.setMinimumFractionDigits(2);
                formatterTON.setMaximumFractionDigits(6);
                formatterTON.setGroupingUsed(false);
            }
            formatterTON.setMaximumFractionDigits(v > 1_000_000_000 ? 2 : 6);
            return ChannelMonetizationLayout.replaceTON("TON " + formatterTON.format(v / 1_000_000_000.0), paint, .8f, -dp(.66f), false);
        }
        return AndroidUtilities.formatWholeNumber((int) v, 0);
    }

    public static int lookupHeight(long maxValue) {
        long v = maxValue;
        if (maxValue > 100) {
            v = round(maxValue);
        }

        int step = (int) Math.ceil(v / 5f);
        return step * 5;
    }

    private static long round(long maxValue) {
        float k = maxValue / 5;
        if (k % 10 == 0) return maxValue;
        else return ((maxValue / 10 + 1) * 10);
    }

    public void drawText(Canvas canvas, int a, int i, float x, float y, TextPaint paint) {
        StaticLayout layout = (a == 0 ? layouts : layouts2)[i];
        if (layout == null) {
            CharSequence string = (a == 0 ? valuesStr : valuesStr2)[i];
            (a == 0 ? layouts : layouts2)[i] = layout = new StaticLayout(
                string,
                paint,
                AndroidUtilities.displaySize.x,
                Layout.Alignment.ALIGN_NORMAL,
                1f, 0f, false
            );
        }
        canvas.save();
        canvas.translate(x, y + paint.ascent());
        layout.draw(canvas);
        canvas.restore();
    }

}
