package org.telegram.ui.Charts;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Charts.data.ChartData;
import org.telegram.ui.Charts.data.DoubleLinearChartData;
import org.telegram.ui.Charts.view_data.ChartHorizontalLinesData;
import org.telegram.ui.Charts.view_data.LineViewData;

public class DoubleLinearChartView extends BaseChartView<DoubleLinearChartData, LineViewData> {

    public DoubleLinearChartView(Context context) {
        this(context, null);
    }

    public DoubleLinearChartView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }

    @Override
    protected void init() {
        useMinHeight = true;
        super.init();
    }

    @Override
    protected void drawChart(Canvas canvas) {
        if (chartData != null) {
            float fullWidth = (chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart));
            float offset = fullWidth * (pickerDelegate.pickerStart) - HORIZONTAL_PADDING;

            canvas.save();
            float transitionAlpha = 1f;
            if (transitionMode == TRANSITION_MODE_PARENT) {

                transitionAlpha = transitionParams.progress > 0.5f ? 0 : 1f - transitionParams.progress * 2f;

                canvas.scale(
                        1 + 2 * transitionParams.progress, 1f,
                        transitionParams.pX, transitionParams.pY
                );

            } else if (transitionMode == TRANSITION_MODE_CHILD) {

                transitionAlpha = transitionParams.progress < 0.3f ? 0 : transitionParams.progress;

                canvas.save();
                canvas.scale(
                        transitionParams.progress, transitionParams.progress,
                        transitionParams.pX, transitionParams.pY
                );
            } else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
                transitionAlpha = transitionParams.progress;
            }

            for (int k = 0; k < lines.size(); k++) {
                LineViewData line = lines.get(k);
                if (!line.enabled && line.alpha == 0) continue;

                int j = 0;

                int[] y = line.line.y;

                line.chartPath.reset();
                boolean first = true;

                float p;
                if (chartData.xPercentage.length < 2) {
                    p = 1f;
                } else {
                    p = chartData.xPercentage[1] * fullWidth;
                }

                int additionalPoints = (int) (HORIZONTAL_PADDING / p) + 1;
                int localStart = Math.max(0, startXIndex - additionalPoints);
                int localEnd = Math.min(chartData.xPercentage.length - 1, endXIndex + additionalPoints);

                for (int i = localStart; i <= localEnd; i++) {
                    if (y[i] < 0) continue;
                    float xPoint = chartData.xPercentage[i] * fullWidth - offset;
                    float yPercentage = ((float) y[i] * chartData.linesK[k] - currentMinHeight) / (currentMaxHeight - currentMinHeight);
                    float padding = line.paint.getStrokeWidth() / 2f;
                    float yPoint = getMeasuredHeight() - chartBottom - padding - (yPercentage) * (getMeasuredHeight() - chartBottom - SIGNATURE_TEXT_HEIGHT - padding);

                    if (USE_LINES) {
                        if (j == 0) {
                            line.linesPath[j++] = xPoint;
                            line.linesPath[j++] = yPoint;
                        } else {
                            line.linesPath[j++] = xPoint;
                            line.linesPath[j++] = yPoint;
                            line.linesPath[j++] = xPoint;
                            line.linesPath[j++] = yPoint;
                        }
                    } else {
                        if (first) {
                            first = false;
                            line.chartPath.moveTo(xPoint, yPoint);
                        } else {
                            line.chartPath.lineTo(xPoint, yPoint);
                        }
                    }
                }

                if (endXIndex - startXIndex > 100) {
                    line.paint.setStrokeCap(Paint.Cap.SQUARE);
                } else {
                    line.paint.setStrokeCap(Paint.Cap.ROUND);
                }
                line.paint.setAlpha((int) (255 * line.alpha * transitionAlpha));
                if (!USE_LINES) canvas.drawPath(line.chartPath, line.paint);
                else canvas.drawLines(line.linesPath, 0, j, line.paint);
            }

            canvas.restore();
        }

    }

    @Override
    protected void drawPickerChart(Canvas canvas) {
        int bottom = getMeasuredHeight() - PICKER_PADDING;
        int top = getMeasuredHeight() - pikerHeight - PICKER_PADDING;

        int nl = lines.size();
        if (chartData != null) {
            for (int k = 0; k < nl; k++) {
                LineViewData line = lines.get(k);
                if (!line.enabled && line.alpha == 0) continue;

                line.bottomLinePath.reset();

                int n = chartData.xPercentage.length;
                int j = 0;

                int[] y = line.line.y;

                line.chartPath.reset();
                for (int i = 0; i < n; i++) {
                    if (y[i] < 0) continue;

                    float xPoint = chartData.xPercentage[i] * pickerWidth;
                    float h = ANIMATE_PICKER_SIZES ? pickerMaxHeight : chartData.maxValue;

                    float yPercentage = (float) y[i] * chartData.linesK[k] / h;
                    float yPoint = (1f - yPercentage) * (bottom - top);

                    if (USE_LINES) {
                        if (j == 0) {
                            line.linesPathBottom[j++] = xPoint;
                            line.linesPathBottom[j++] = yPoint;
                        } else {
                            line.linesPathBottom[j++] = xPoint;
                            line.linesPathBottom[j++] = yPoint;
                            line.linesPathBottom[j++] = xPoint;
                            line.linesPathBottom[j++] = yPoint;
                        }
                    } else {
                        if (i == 0) {
                            line.bottomLinePath.moveTo(xPoint, yPoint);
                        } else {
                            line.bottomLinePath.lineTo(xPoint, yPoint);
                        }
                    }
                }

                line.linesPathBottomSize = j;


                if (!line.enabled && line.alpha == 0) continue;
                line.bottomLinePaint.setAlpha((int) (255 * line.alpha));
                if (USE_LINES)
                    canvas.drawLines(line.linesPathBottom, 0, line.linesPathBottomSize, line.bottomLinePaint);
                else
                    canvas.drawPath(line.bottomLinePath, line.bottomLinePaint);

            }
        }
    }

    @Override
    protected void drawSelection(Canvas canvas) {
        if (selectedIndex < 0 || !legendShowing) return;

        int alpha = (int) (chartActiveLineAlpha * selectionA);

        float fullWidth = (chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart));
        float offset = fullWidth * (pickerDelegate.pickerStart) - HORIZONTAL_PADDING;

        float xPoint = chartData.xPercentage[selectedIndex] * fullWidth - offset;


        selectedLinePaint.setAlpha(alpha);
        canvas.drawLine(xPoint, 0, xPoint, chartArea.bottom, selectedLinePaint);

        tmpN = lines.size();
        for (tmpI = 0; tmpI < tmpN; tmpI++) {
            LineViewData line = lines.get(tmpI);
            if (!line.enabled && line.alpha == 0) continue;
            float yPercentage = ((float) line.line.y[selectedIndex] * chartData.linesK[tmpI] - currentMinHeight) / (currentMaxHeight - currentMinHeight);
            float yPoint = getMeasuredHeight() - chartBottom - (yPercentage) * (getMeasuredHeight() - chartBottom - SIGNATURE_TEXT_HEIGHT);

            line.selectionPaint.setAlpha((int) (255 * line.alpha * selectionA));
            selectionBackgroundPaint.setAlpha((int) (255 * line.alpha * selectionA));

            canvas.drawPoint(xPoint, yPoint, line.selectionPaint);
            canvas.drawPoint(xPoint, yPoint, selectionBackgroundPaint);
        }
    }

    @Override
    protected void drawSignaturesToHorizontalLines(Canvas canvas, ChartHorizontalLinesData a) {
        int n = a.values.length;
        int rightIndex = chartData.linesK[0] == 1 ? 1 : 0;
        int leftIndex = (rightIndex + 1) % 2;

        float additionalOutAlpha = 1f;
        if (n > 2) {
            float v = (a.values[1] - a.values[0]) / (float) (currentMaxHeight - currentMinHeight);
            if (v < 0.1) {
                additionalOutAlpha = v / 0.1f;
            }
        }

        float transitionAlpha = 1f;
        if (transitionMode == TRANSITION_MODE_PARENT) {
            transitionAlpha = 1f - transitionParams.progress;
        } else if (transitionMode == TRANSITION_MODE_CHILD) {
            transitionAlpha = transitionParams.progress;
        } else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
            transitionAlpha = transitionParams.progress;
        }


        linePaint.setAlpha((int) (a.alpha * 0.1f * transitionAlpha));
        int chartHeight = getMeasuredHeight() - chartBottom - SIGNATURE_TEXT_HEIGHT;

        int textOffset = (int) (SIGNATURE_TEXT_HEIGHT - signaturePaint.getTextSize());
        for (int i = 0; i < n; i++) {
            int y = (int) ((getMeasuredHeight() - chartBottom) - chartHeight * ((a.values[i] - currentMinHeight) / (currentMaxHeight - currentMinHeight)));
            if (a.valuesStr != null && lines.size() > 0) {
                if (a.valuesStr2 == null || lines.size() < 2) {
                    signaturePaint.setColor(Theme.getColor(Theme.key_statisticChartSignature, resourcesProvider));
                    signaturePaint.setAlpha((int) (a.alpha * signaturePaintAlpha * transitionAlpha * additionalOutAlpha));
                } else {
                    signaturePaint.setColor(lines.get(leftIndex).lineColor);
                    signaturePaint.setAlpha((int) (a.alpha * lines.get(leftIndex).alpha * transitionAlpha * additionalOutAlpha));
                }

                canvas.drawText(a.valuesStr[i], HORIZONTAL_PADDING, y - textOffset, signaturePaint);
            }
            if (a.valuesStr2 != null && lines.size() > 1) {
                signaturePaint2.setColor(lines.get(rightIndex).lineColor);
                signaturePaint2.setAlpha((int) (a.alpha * lines.get(rightIndex).alpha * transitionAlpha * additionalOutAlpha));
                canvas.drawText(a.valuesStr2[i], getMeasuredWidth() - HORIZONTAL_PADDING, y - textOffset, signaturePaint2);
            }
        }
    }

    @Override
    public LineViewData createLineViewData(ChartData.Line line) {
        return new LineViewData(line, resourcesProvider);
    }

    public int findMaxValue(int startXIndex, int endXIndex) {
        if (lines.isEmpty()) {
            return 0;
        }
        int n = lines.size();
        int max = 0;
        for (int i = 0; i < n; i++) {
            int localMax = lines.get(i).enabled ? (int) (chartData.lines.get(i).segmentTree.rMaxQ(startXIndex, endXIndex) * chartData.linesK[i]) : 0;
            if (localMax > max) max = localMax;
        }
        return max;
    }

    public int findMinValue(int startXIndex, int endXIndex) {
        if (lines.isEmpty()) {
            return 0;
        }
        int n = lines.size();
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int localMin = lines.get(i).enabled ? (int) (chartData.lines.get(i).segmentTree.rMinQ(startXIndex, endXIndex) * chartData.linesK[i]) : Integer.MAX_VALUE;
            if (localMin < min) min = localMin;
        }
        return min;
    }

    protected void updatePickerMinMaxHeight() {
        if (!ANIMATE_PICKER_SIZES) return;
        if (lines.get(0).enabled) {
            super.updatePickerMinMaxHeight();
            return;
        }

        int max = 0;
        for (LineViewData l : lines) {
            if (l.enabled && l.line.maxValue > max) max = l.line.maxValue;
        }
        if (lines.size() > 1) {
            max = (int) (max * chartData.linesK[1]);
        }

        if (max > 0 && max != animatedToPickerMaxHeight) {
            animatedToPickerMaxHeight = max;
            if (pickerAnimator != null) pickerAnimator.cancel();

            pickerAnimator = createAnimator(pickerMaxHeight, animatedToPickerMaxHeight, new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    pickerMaxHeight = (float) animation.getAnimatedValue();
                    invalidatePickerChart = true;
                    invalidate();
                }
            });
            pickerAnimator.start();
        }
    }

    protected ChartHorizontalLinesData createHorizontalLinesData(int newMaxHeight, int newMinHeight) {
        float k;
        if (chartData.linesK.length < 2) {
            k = 1;
        } else {
            int rightIndex = chartData.linesK[0] == 1 ? 1 : 0;
            k = chartData.linesK[rightIndex];
        }
        return new ChartHorizontalLinesData(newMaxHeight, newMinHeight, useMinHeight, k);
    }
}
