package org.telegram.ui.Charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Charts.data.ChartData;
import org.telegram.ui.Charts.data.StackLinearChartData;
import org.telegram.ui.Charts.view_data.LineViewData;
import org.telegram.ui.Charts.view_data.StackLinearViewData;
import org.telegram.ui.Charts.view_data.TransitionParams;

public class StackLinearChartView<T extends StackLinearViewData> extends BaseChartView<StackLinearChartData, T> {

    private Matrix matrix = new Matrix();
    private float[] mapPoints = new float[2];

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
    float startFromY[];

    @Override
    protected void drawChart(Canvas canvas) {
        if (chartData != null) {
            float fullWidth = (chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart));
            float offset = fullWidth * (pickerDelegate.pickerStart) - HORIZONTAL_PADDING;

            float cX = chartArea.centerX();
            float cY = chartArea.centerY() + AndroidUtilities.dp(16);

            for (int k = 0; k < lines.size(); k++) {
                lines.get(k).chartPath.reset();
                lines.get(k).chartPathPicker.reset();
            }

            canvas.save();
            if (skipPoints == null || skipPoints.length < chartData.lines.size()) {
                skipPoints = new boolean[chartData.lines.size()];
                startFromY = new float[chartData.lines.size()];
            }

            boolean hasEmptyPoint = false;
            int transitionAlpha = 255;
            float transitionProgressHalf = 0;
            if (transitionMode == TRANSITION_MODE_PARENT) {
                transitionProgressHalf = transitionParams.progress / 0.6f;
                if (transitionProgressHalf > 1f) {
                    transitionProgressHalf = 1f;
                }
               // transitionAlpha = (int) ((1f - transitionParams.progress) * 255);
                ovalPath.reset();

                float radiusStart = (chartArea.width() > chartArea.height() ? chartArea.width() : chartArea.height());
                float radiusEnd = (chartArea.width() > chartArea.height() ? chartArea.height() : chartArea.width()) * 0.45f;
                float radius = radiusEnd + ((radiusStart - radiusEnd) / 2) * (1 - transitionParams.progress);

                RectF rectF = new RectF();
                rectF.set(
                        cX - radius,
                        cY - radius,
                        cX + radius,
                        cY + radius
                );
                ovalPath.addRoundRect(
                        rectF, radius, radius, Path.Direction.CW
                );
                canvas.clipPath(ovalPath);
            } else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
                transitionAlpha = (int) (transitionParams.progress * 255);
            }

            float dX = 0;
            float dY = 0;
            float x1 = 0;
            float y1 = 0;

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
                int lastEnabled = 0;

                int drawingLinesCount = 0;
                for (int k = 0; k < lines.size(); k++) {
                    LineViewData line = lines.get(k);
                    if (!line.enabled && line.alpha == 0) continue;
                    if (line.line.y[i] > 0) {
                        sum += line.line.y[i] * line.alpha;
                        drawingLinesCount++;
                    }
                    lastEnabled = k;
                }

                for (int k = 0; k < lines.size(); k++) {
                    LineViewData line = lines.get(k);
                    if (!line.enabled && line.alpha == 0) continue;
                    final long[] y = line.line.y;

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

                    float xPoint = chartData.xPercentage[i] * fullWidth - offset;
                    float nextXPoint;
                    if (i == localEnd) {
                        nextXPoint = getMeasuredWidth();
                    } else {
                        nextXPoint = chartData.xPercentage[i + 1] * fullWidth - offset;
                    }

                    if (yPercentage == 0 && k == lastEnabled) {
                        hasEmptyPoint = true;
                    }
                    float height = (yPercentage) * (getMeasuredHeight() - chartBottom - SIGNATURE_TEXT_HEIGHT);
                    float yPoint = getMeasuredHeight() - chartBottom - height - stackOffset;
                    startFromY[k] = yPoint;

                    float angle = 0;
                    float yPointZero = getMeasuredHeight() - chartBottom;
                    float xPointZero = xPoint;
                    if (i == localEnd) {
                        endXPoint = xPoint;
                    } else if (i == localStart) {
                        startXPoint = xPoint;
                    }
                    if (transitionMode == TRANSITION_MODE_PARENT && k != lastEnabled) {
                        if (xPoint < cX) {
                            x1 = transitionParams.startX[k];
                            y1 = transitionParams.startY[k];
                        } else {
                            x1 = transitionParams.endX[k];
                            y1 = transitionParams.endY[k];
                        }

                        dX = cX - x1;
                        dY = cY - y1;
                        float yTo = dY * (xPoint - x1) / dX + y1;

                        yPoint = yPoint * (1f - transitionProgressHalf) + yTo * transitionProgressHalf;
                        yPointZero = yPointZero * (1f - transitionProgressHalf) + yTo * transitionProgressHalf;

                        float angleK = dY / dX;
                        if (angleK > 0) {
                            angle = (float) Math.toDegrees(-Math.atan(angleK));
                        } else {
                            angle = (float) Math.toDegrees(Math.atan(Math.abs(angleK)));
                        }
                        angle -= 90;

                        if (xPoint >= cX) {
                            mapPoints[0] = xPoint;
                            mapPoints[1] = yPoint;
                            matrix.reset();
                            matrix.postRotate(transitionParams.progress * angle, cX, cY);
                            matrix.mapPoints(mapPoints);

                            xPoint = mapPoints[0];
                            yPoint = mapPoints[1];
                            if (xPoint < cX) xPoint = cX;

                            mapPoints[0] = xPointZero;
                            mapPoints[1] = yPointZero;
                            matrix.reset();
                            matrix.postRotate(transitionParams.progress * angle, cX, cY);
                            matrix.mapPoints(mapPoints);
                            yPointZero = mapPoints[1];
                            if (xPointZero < cX) xPointZero = cX;
                        } else {
                            if (nextXPoint >= cX) {
                                xPointZero = xPoint = xPoint * (1f - transitionProgressHalf) + cX * transitionProgressHalf;
                                yPointZero = yPoint = yPoint * (1f - transitionProgressHalf) + cY * transitionProgressHalf;
                            } else {
                                mapPoints[0] = xPoint;
                                mapPoints[1] = yPoint;
                                matrix.reset();
                                matrix.postRotate(transitionParams.progress * angle + transitionParams.progress * transitionParams.angle[k], cX, cY);
                                matrix.mapPoints(mapPoints);
                                xPoint = mapPoints[0];
                                yPoint = mapPoints[1];

                                if (nextXPoint >= cX) {
                                    mapPoints[0] = xPointZero * (1f - transitionParams.progress) + cX * transitionParams.progress;
                                } else {
                                    mapPoints[0] = xPointZero;
                                }
                                mapPoints[1] = yPointZero;
                                matrix.reset();
                                matrix.postRotate(transitionParams.progress * angle + transitionParams.progress * transitionParams.angle[k], cX, cY);
                                matrix.mapPoints(mapPoints);

                                xPointZero = mapPoints[0];
                                yPointZero = mapPoints[1];
                            }
                        }
                    }

                    if (i == localStart) {
                        float localX = 0;
                        float localY = getMeasuredHeight();
                        if (transitionMode == TRANSITION_MODE_PARENT && k != lastEnabled) {
                            mapPoints[0] = localX - cX;
                            mapPoints[1] = localY;
                            matrix.reset();
                            matrix.postRotate(transitionParams.progress * angle + transitionParams.progress * transitionParams.angle[k], cX, cY);
                            matrix.mapPoints(mapPoints);
                            localX = mapPoints[0];
                            localY = mapPoints[1];
                        }
                        line.chartPath.moveTo(localX, localY);
                        skipPoints[k] = false;
                    }

                    float transitionProgress = transitionParams == null ? 0f : transitionParams.progress;
                    if (yPercentage == 0 && (i > 0 && y[i - 1] == 0) && (i < localEnd && y[i + 1] == 0) && transitionMode != TRANSITION_MODE_PARENT) {
                        if (!skipPoints[k]) {
                            if (k == lastEnabled) {
                                line.chartPath.lineTo(xPointZero, yPointZero * (1f - transitionProgress));
                            } else {
                                line.chartPath.lineTo(xPointZero, yPointZero);
                            }
                        }
                        skipPoints[k] = true;
                    } else {
                        if (skipPoints[k]) {
                            if (k == lastEnabled) {
                                line.chartPath.lineTo(xPointZero, yPointZero * (1f - transitionProgress));
                            } else {
                                line.chartPath.lineTo(xPointZero, yPointZero);
                            }
                        }
                        if (k == lastEnabled) {
                            line.chartPath.lineTo(xPoint, yPoint * (1f - transitionProgress));
                        } else {
                            line.chartPath.lineTo(xPoint, yPoint);
                        }
                        skipPoints[k] = false;
                    }

                    if (i == localEnd) {
                        float localX = getMeasuredWidth();
                        float localY = getMeasuredHeight();
                        if (transitionMode == TRANSITION_MODE_PARENT && k != lastEnabled) {
                            mapPoints[0] = localX + cX;
                            mapPoints[1] = localY;
                            matrix.reset();
                            matrix.postRotate(transitionParams.progress * transitionParams.angle[k], cX, cY);
                            matrix.mapPoints(mapPoints);
                            localX = mapPoints[0];
                            localY = mapPoints[1];
                        } else {
                            line.chartPath.lineTo(localX, localY);
                        }

                        if (transitionMode == TRANSITION_MODE_PARENT && k != lastEnabled) {

                            x1 = transitionParams.startX[k];
                            y1 = transitionParams.startY[k];

                            dX = cX - x1;
                            dY = cY - y1;
                            float angleK = dY / dX;
                            if (angleK > 0) {
                                angle = (float) Math.toDegrees(-Math.atan(angleK));
                            } else {
                                angle = (float) Math.toDegrees(Math.atan(Math.abs(angleK)));
                            }
                            angle -= 90;

                            localX = transitionParams.startX[k];
                            localY = transitionParams.startY[k];
                            mapPoints[0] = localX;
                            mapPoints[1] = localY;
                            matrix.reset();
                            matrix.postRotate(transitionParams.progress * angle + transitionParams.progress * transitionParams.angle[k], cX, cY);
                            matrix.mapPoints(mapPoints);
                            localX = mapPoints[0];
                            localY = mapPoints[1];

                            // 0 right_top
                            // 1 right_bottom
                            // 2 left_bottom
                            // 3 left_top
                            int endQuarter;
                            int startQuarter;

                            if (Math.abs(xPoint - localX) < 0.001 && ((localY < cY && yPoint < cY) || (localY > cY && yPoint > cY))) {
                                if (transitionParams.angle[k] == -180f) {
                                    endQuarter = 0;
                                    startQuarter = 0;
                                } else {
                                    endQuarter = 0;
                                    startQuarter = 3;
                                }
                            } else {
                                endQuarter = quarterForPoint(xPoint, yPoint);
                                startQuarter = quarterForPoint(localX, localY);
                            }

                            for (int q = endQuarter; q <= startQuarter; q++) {
                                if (q == 0) {
                                    line.chartPath.lineTo(getMeasuredWidth(), 0);
                                } else if (q == 1) {
                                    line.chartPath.lineTo(getMeasuredWidth(), getMeasuredHeight());
                                } else if (q == 2) {
                                    line.chartPath.lineTo(0, getMeasuredHeight());
                                } else {
                                    line.chartPath.lineTo(0, 0);
                                }
                            }
                        }
                    }

                    stackOffset += height;
                }
            }

            canvas.save();

            canvas.clipRect(startXPoint, SIGNATURE_TEXT_HEIGHT, endXPoint, getMeasuredHeight() - chartBottom);

            if (hasEmptyPoint) {
                canvas.drawColor(Theme.getColor(Theme.key_statisticChartLineEmpty));
            }
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

    private int quarterForPoint(float x, float y) {
        float cX = chartArea.centerX();
        float cY = chartArea.centerY() + AndroidUtilities.dp(16);

        if (x >= cX && y <= cY) {
            return 0;
        }
        if (x >= cX && y >= cY) {
            return 1;
        }
        if (x < cX && y >= cY) {
            return 2;
        }
        return 3;
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

            boolean hasEmptyPoint = false;

            for (int i = 0; i < n; i++) {
                float stackOffset = 0;
                float sum = 0;
                int lastEnabled = 0;

                int drawingLinesCount = 0;
                for (int k = 0; k < lines.size(); k++) {
                    LineViewData line = lines.get(k);
                    if (!line.enabled && line.alpha == 0) continue;
                    if (chartData.simplifiedY[k][i] > 0) {
                        sum += chartData.simplifiedY[k][i] * line.alpha;
                        drawingLinesCount++;
                    }
                    lastEnabled = k;
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

                    if (yPercentage == 0 && k == lastEnabled) {
                        hasEmptyPoint = true;
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

            if (hasEmptyPoint) {
                canvas.drawColor(Theme.getColor(Theme.key_statisticChartLineEmpty));
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
    public long findMaxValue(int startXIndex, int endXIndex) {
        return 100;
    }

    protected float getMinDistance() {
        return 0.1f;
    }

    @Override
    public void fillTransitionParams(TransitionParams params) {
        if (chartData == null) {
            return;
        }
        float fullWidth = (chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart));
        float offset = fullWidth * (pickerDelegate.pickerStart) - HORIZONTAL_PADDING;

        float p;
        if (chartData.xPercentage.length < 2) {
            p = 1f;
        } else {
            p = chartData.xPercentage[1] * fullWidth;
        }

        int additionalPoints = (int) (HORIZONTAL_PADDING / p) + 1;
        int localStart = Math.max(0, startXIndex - additionalPoints - 1);
        int localEnd = Math.min(chartData.xPercentage.length - 1, endXIndex + additionalPoints + 1);


        transitionParams.startX = new float[chartData.lines.size()];
        transitionParams.startY = new float[chartData.lines.size()];
        transitionParams.endX = new float[chartData.lines.size()];
        transitionParams.endY = new float[chartData.lines.size()];
        transitionParams.angle = new float[chartData.lines.size()];


        for (int j = 0; j < 2; j++) {
            int i = localStart;
            if (j == 1) {
                i = localEnd;
            }
            int stackOffset = 0;
            float sum = 0;
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
                final long[] y = line.line.y;

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

                float xPoint = chartData.xPercentage[i] * fullWidth - offset;
                float height = (yPercentage) * (getMeasuredHeight() - chartBottom - SIGNATURE_TEXT_HEIGHT);
                float yPoint = getMeasuredHeight() - chartBottom - height - stackOffset;
                stackOffset += height;

                if (j == 0) {
                    transitionParams.startX[k] = xPoint;
                    transitionParams.startY[k] = yPoint;
                } else {
                    transitionParams.endX[k] = xPoint;
                    transitionParams.endY[k] = yPoint;
                }
            }
        }
    }


}
