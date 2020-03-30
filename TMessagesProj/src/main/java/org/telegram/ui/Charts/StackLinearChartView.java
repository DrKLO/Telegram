package org.telegram.ui.Charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;

import org.telegram.ui.Charts.data.ChartData;
import org.telegram.ui.Charts.data.StackLinearChartData;
import org.telegram.ui.Charts.view_data.LineViewData;
import org.telegram.ui.Charts.view_data.StackLinearViewData;

public class StackLinearChartView<T extends StackLinearViewData> extends BaseChartView<StackLinearChartData, T> {


    public StackLinearChartView(Context context) {
        super(context);
        superDraw = true;
        useAlphaSignature = true;
        drawPointOnSelection = false;
    }

    @Override
    public T createLineViewData(ChartData.Line line) {
        return (T) new StackLinearViewData(line);
    }

    Path ovalPath = new Path();
    boolean[] skipPoints;

    @Override
    protected void drawChart(Canvas canvas) {
        if (chartData != null) {
            float fullWidth = (chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart));
            float offset = fullWidth * (pickerDelegate.pickerStart) - HORIZONTAL_PADDING;

            for (int k = 0; k < lines.size(); k++) {
                lines.get(k).chartPath.reset();
                lines.get(k).chartPathPicker.reset();
            }

            canvas.save();
            if (skipPoints == null || skipPoints.length < chartData.lines.size()) {
                skipPoints = new boolean[chartData.lines.size()];
            }

            int transitionAlpha = 255;
            if (transitionMode == TRANSITION_MODE_PARENT) {

                transitionAlpha = (int) ((1f - transitionParams.progress) * 255);
                ovalPath.reset();

                int radiusStart = (chartArea.width() > chartArea.height() ? chartArea.width() : chartArea.height());
                int radiusEnd = (int) ((chartArea.width() > chartArea.height() ? chartArea.height() : chartArea.width()) / 2f);
                float radius = radiusEnd + ((radiusStart - radiusEnd) / 2) * (1 - transitionParams.progress);

                radius *= 1f - transitionParams.progress;
                RectF rectF = new RectF();
                rectF.set(
                        chartArea.centerX() - radius,
                        chartArea.centerY() - radius,
                        chartArea.centerX() + radius,
                        chartArea.centerY() + radius
                );
                ovalPath.addRoundRect(
                        rectF, radius, radius, Path.Direction.CW
                );
                canvas.clipPath(ovalPath);
            } else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
                transitionAlpha = (int) (transitionParams.progress * 255);
            }

            float p;
            if (chartData.xPercentage.length < 2) {
                p = 1f;
            } else {
                p = chartData.xPercentage[1] * fullWidth;
            }

            int additionalPoints = (int) (HORIZONTAL_PADDING / p) + 1;
            int localStart = Math.max(0, startXIndex - additionalPoints - 1);
            int localEnd = Math.min(chartData.xPercentage.length - 1, endXIndex + additionalPoints + 1);

            float startXPoint = 0;
            float endXPoint = 0;
            for (int i = localStart; i <= localEnd; i++) {
                float stackOffset = 0;
                float sum = 0;
                float xPoint = chartData.xPercentage[i] * fullWidth - offset;

                int drawingLinesCount = 0;
                for (int k = 0; k < lines.size(); k++) {
                    LineViewData line = lines.get(k);
                    if (!line.enabled && line.alpha == 0) continue;
                    if (line.line.y[i] > 0) {
                        sum += line.line.y[i] * line.alpha;
                        drawingLinesCount++;
                    }
                }

                for (int k = 0; k < lines.size(); k++) {
                    LineViewData line = lines.get(k);
                    if (!line.enabled && line.alpha == 0) continue;
                    int[] y = line.line.y;

                    float yPercentage;

                    if (drawingLinesCount == 1) {
                        if (y[i] == 0) {
                            yPercentage = 0;
                        } else {
                            yPercentage = line.alpha;
                        }
                    } else {
                        if (sum == 0) {
                            yPercentage = 0;
                        } else {
                            yPercentage = y[i] * line.alpha / sum;
                        }
                    }


                    float height = (yPercentage) * (getMeasuredHeight() - chartBottom - SIGNATURE_TEXT_HEIGHT);
                    float yPoint = getMeasuredHeight() - chartBottom - height - stackOffset;

                    if (i == localStart) {
                        line.chartPath.moveTo(0, getMeasuredHeight());
                        startXPoint = xPoint;
                        skipPoints[k] = false;
                    }

                    if (yPercentage == 0 && (i > 0 && y[i - 1] == 0) && (i < localEnd && y[i + 1] == 0)) {
                        if (!skipPoints[k]) {
                            line.chartPath.lineTo(xPoint, getMeasuredHeight() - chartBottom);
                        }
                        skipPoints[k] = true;
                    } else {
                        if (skipPoints[k]) {
                            line.chartPath.lineTo(xPoint, getMeasuredHeight() - chartBottom);
                        }
                        line.chartPath.lineTo(xPoint, yPoint);
                        skipPoints[k] = false;
                    }

                    if (i == localEnd) {
                        line.chartPath.lineTo(getMeasuredWidth(), getMeasuredHeight());
                        endXPoint = xPoint;
                    }

                    stackOffset += height;
                }
            }


            canvas.save();
            canvas.clipRect(startXPoint, SIGNATURE_TEXT_HEIGHT, endXPoint, getMeasuredHeight() - chartBottom);
            for (int k = lines.size() - 1; k >= 0; k--) {
                LineViewData line = lines.get(k);
                line.paint.setAlpha(transitionAlpha);

                canvas.drawPath(line.chartPath, line.paint);

                line.paint.setAlpha(255);
            }
            canvas.restore();
            canvas.restore();
        }
    }

    @Override
    protected void drawPickerChart(Canvas canvas) {
        if (chartData != null) {
            int nl = lines.size();
            for (int k = 0; k < nl; k++) {
                lines.get(k).chartPathPicker.reset();
            }

            int n = chartData.simplifiedSize;

            if (skipPoints == null || skipPoints.length < chartData.lines.size()) {
                skipPoints = new boolean[chartData.lines.size()];
            }

            for (int i = 0; i < n; i++) {
                float stackOffset = 0;
                float sum = 0;


                int drawingLinesCount = 0;
                for (int k = 0; k < lines.size(); k++) {
                    LineViewData line = lines.get(k);
                    if (!line.enabled && line.alpha == 0) continue;
                    if (chartData.simplifiedY[k][i] > 0) {
                        sum += chartData.simplifiedY[k][i] * line.alpha;
                        drawingLinesCount++;
                    }
                }

                float xPoint = i / (float) (n - 1) * pickerWidth;

                for (int k = 0; k < lines.size(); k++) {
                    LineViewData line = lines.get(k);
                    if (!line.enabled && line.alpha == 0) continue;
                    float yPercentage;

                    if (drawingLinesCount == 1) {
                        if (chartData.simplifiedY[k][i] == 0) {
                            yPercentage = 0;
                        } else {
                            yPercentage = line.alpha;
                        }
                    } else {
                        if (sum == 0) {
                            yPercentage = 0;
                        } else {
                            yPercentage = (chartData.simplifiedY[k][i] * line.alpha) / sum;
                        }
                    }

                    float height = (yPercentage) * (pikerHeight);
                    float yPoint = pikerHeight - height - stackOffset;

                    if (i == 0) {
                        line.chartPathPicker.moveTo(0, pikerHeight);
                        skipPoints[k] = false;
                    }

                    if (chartData.simplifiedY[k][i] == 0 && (i > 0 && chartData.simplifiedY[k][i - 1] == 0) && (i < n - 1 && chartData.simplifiedY[k][i + 1] == 0)) {
                        if (!skipPoints[k]) {
                            line.chartPathPicker.lineTo(xPoint, pikerHeight);
                        }
                        skipPoints[k] = true;
                    } else {
                        if (skipPoints[k]) {
                            line.chartPathPicker.lineTo(xPoint, pikerHeight);
                        }
                        line.chartPathPicker.lineTo(xPoint, yPoint);
                        skipPoints[k] = false;
                    }

                    if (i == n - 1) {
                        line.chartPathPicker.lineTo(pickerWidth, pikerHeight);
                    }


                    stackOffset += height;

                }
            }

            for (int k = lines.size() - 1; k >= 0; k--) {
                LineViewData line = lines.get(k);
                canvas.drawPath(line.chartPathPicker, line.paint);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        tick();
        drawChart(canvas);
        drawBottomLine(canvas);
        tmpN = horizontalLines.size();
        for (tmpI = 0; tmpI < tmpN; tmpI++) {
            drawHorizontalLines(canvas, horizontalLines.get(tmpI));
            drawSignaturesToHorizontalLines(canvas, horizontalLines.get(tmpI));
        }
        drawBottomSignature(canvas);
        drawPicker(canvas);
        drawSelection(canvas);

        super.onDraw(canvas);
    }

    @Override
    public int findMaxValue(int startXIndex, int endXIndex) {
        return 100;
    }

    protected float getMinDistance() {
        return 0.1f;
    }

}
