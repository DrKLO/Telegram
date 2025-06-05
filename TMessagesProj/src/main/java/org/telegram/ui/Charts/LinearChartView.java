package org.telegram.ui.Charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.telegram.ui.Charts.data.ChartData;
import org.telegram.ui.Charts.view_data.LineViewData;

public class LinearChartView extends BaseChartView<ChartData, LineViewData> {
    public LinearChartView(Context context) {
        super(context);
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


            for (int k = 0; k < lines.size(); k++) {
                LineViewData line = lines.get(k);
                if (!line.enabled && line.alpha == 0) continue;

                int j = 0;

                float p;
                if (chartData.xPercentage.length < 2) {
                    p = 0f;
                } else {
                    p = chartData.xPercentage[1] * fullWidth;
                }
                final long[] y = line.line.y;
                int additionalPoints = (int) (HORIZONTAL_PADDING / p) + 1;

                line.chartPath.reset();
                boolean first = true;

                int localStart = Math.max(0, startXIndex - additionalPoints);
                int localEnd = Math.min(chartData.xPercentage.length - 1, endXIndex + additionalPoints);
                for (int i = localStart; i <= localEnd; i++) {
                    if (y[i] < 0) continue;
                    float xPoint = chartData.xPercentage[i] * fullWidth - offset;
                    float yPercentage = ((float) y[i] - currentMinHeight) / (currentMaxHeight - currentMinHeight);
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
                            transitionParams.progress, transitionParams.needScaleY ? transitionParams.progress : 1f,
                            transitionParams.pX, transitionParams.pY
                    );
                } else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
                    transitionAlpha = transitionParams.progress;
                }
                line.paint.setAlpha((int) (255 * line.alpha * transitionAlpha));
                if(endXIndex - startXIndex > 100){
                    line.paint.setStrokeCap(Paint.Cap.SQUARE);
                } else {
                    line.paint.setStrokeCap(Paint.Cap.ROUND);
                }
                if (!USE_LINES) canvas.drawPath(line.chartPath, line.paint);
                else canvas.drawLines(line.linesPath, 0, j, line.paint);

                canvas.restore();
            }
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

                final long[] y = line.line.y;

                line.chartPath.reset();
                for (int i = 0; i < n; i++) {
                    if (y[i] < 0) continue;
                    float xPoint = chartData.xPercentage[i] * pickerWidth;
                    float h = ANIMATE_PICKER_SIZES ? pickerMaxHeight : chartData.maxValue;
                    float hMin = ANIMATE_PICKER_SIZES ? pickerMinHeight : chartData.minValue;
                    float yPercentage = (y[i] - hMin) / (h - hMin);
                    float yPoint = (1f - yPercentage) * pikerHeight;

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
    public LineViewData createLineViewData(ChartData.Line line) {
        return new LineViewData(line, false);
    }
}
