package org.telegram.ui.Charts;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.SegmentTree;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Charts.data.ChartData;
import org.telegram.ui.Charts.data.StackBarChartData;
import org.telegram.ui.Charts.view_data.LineViewData;
import org.telegram.ui.Charts.view_data.StackBarViewData;

public class StackBarChartView extends BaseChartView<StackBarChartData, StackBarViewData> {

    private int[] yMaxPoints;

    public StackBarChartView(Context context) {
        this(context, null);
    }

    public StackBarChartView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        superDraw = true;
        useAlphaSignature = true;
    }

    @Override
    public StackBarViewData createLineViewData(ChartData.Line line) {
        return new StackBarViewData(line, resourcesProvider);
    }

    @Override
    protected void drawChart(Canvas canvas) {
        if (chartData == null) return;
        float fullWidth = (chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart));
        float offset = fullWidth * (pickerDelegate.pickerStart) - HORIZONTAL_PADDING;

        float p;
        float lineWidth;
        if (chartData.xPercentage.length < 2) {
            p = 1f;
            lineWidth = 1f;
        } else {
            p = chartData.xPercentage[1] * fullWidth;
            lineWidth = chartData.xPercentage[1] * (fullWidth - p);
        }
        int additionalPoints = (int) (HORIZONTAL_PADDING / p) + 1;
        int localStart = Math.max(0, startXIndex - additionalPoints - 2);
        int localEnd = Math.min(chartData.xPercentage.length - 1, endXIndex + additionalPoints + 2);

        for (int k = 0; k < lines.size(); k++) {
            LineViewData line = lines.get(k);
            line.linesPathBottomSize = 0;
        }

        float transitionAlpha = 1f;
        canvas.save();
        if (transitionMode == TRANSITION_MODE_PARENT) {
            postTransition = true;
            selectionA = 0f;
            transitionAlpha = 1f - transitionParams.progress;

            canvas.scale(
                    1 + 2 * transitionParams.progress, 1f,
                    transitionParams.pX, transitionParams.pY
            );

        } else if (transitionMode == TRANSITION_MODE_CHILD) {

            transitionAlpha = transitionParams.progress;

            canvas.scale(
                    transitionParams.progress, 1f,
                    transitionParams.pX, transitionParams.pY
            );
        } else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
            transitionAlpha = transitionParams.progress;
        }

        boolean selected = selectedIndex >= 0 && legendShowing;

        for (int i = localStart; i <= localEnd; i++) {
            float stackOffset = 0;
            if (selectedIndex == i && selected) continue;
            for (int k = 0; k < lines.size(); k++) {
                LineViewData line = lines.get(k);
                if (!line.enabled && line.alpha == 0) continue;


                int[] y = line.line.y;


                float xPoint = p / 2 + chartData.xPercentage[i] * (fullWidth - p) - offset;
                float yPercentage = (float) y[i] / currentMaxHeight;

                float height = (yPercentage) * (getMeasuredHeight() - chartBottom - SIGNATURE_TEXT_HEIGHT) * line.alpha;
                float yPoint = getMeasuredHeight() - chartBottom - height;

                line.linesPath[line.linesPathBottomSize++] = xPoint;
                line.linesPath[line.linesPathBottomSize++] = yPoint - stackOffset;

                line.linesPath[line.linesPathBottomSize++] = xPoint;
                line.linesPath[line.linesPathBottomSize++] = getMeasuredHeight() - chartBottom - stackOffset;

                stackOffset += height;
            }
        }

        for (int k = 0; k < lines.size(); k++) {
            StackBarViewData line = lines.get(k);

            Paint paint = selected || postTransition ? line.unselectedPaint : line.paint;
            if (selected) {
                line.unselectedPaint.setColor(ColorUtils.blendARGB(line.lineColor, line.blendColor, selectionA));
            }

            if (postTransition) {
                line.unselectedPaint.setColor(ColorUtils.blendARGB(line.lineColor, line.blendColor, 1f));
            }

            paint.setAlpha((int) (255 * transitionAlpha));
            paint.setStrokeWidth(lineWidth);
            canvas.drawLines(line.linesPath, 0, line.linesPathBottomSize, paint);
        }

        if (selected) {
            float stackOffset = 0;
            for (int k = 0; k < lines.size(); k++) {
                LineViewData line = lines.get(k);
                if (!line.enabled && line.alpha == 0) continue;


                int[] y = line.line.y;


                float xPoint = p / 2 + chartData.xPercentage[selectedIndex] * (fullWidth - p) - offset;
                float yPercentage = (float) y[selectedIndex] / currentMaxHeight;

                float height = (yPercentage) * (getMeasuredHeight() - chartBottom - SIGNATURE_TEXT_HEIGHT) * line.alpha;
                float yPoint = getMeasuredHeight() - chartBottom - height;

                line.paint.setStrokeWidth(lineWidth);
                line.paint.setAlpha((int) (255 * transitionAlpha));
                canvas.drawLine(xPoint, yPoint - stackOffset,
                        xPoint, getMeasuredHeight() - chartBottom - stackOffset, line.paint);

                stackOffset += height;
            }
        }
        canvas.restore();

    }

    @Override
    protected void selectXOnChart(int x, int y) {
        if (chartData == null) return;
        int oldSelectedIndex = selectedIndex;
        float offset = chartFullWidth * (pickerDelegate.pickerStart) - HORIZONTAL_PADDING;
        float p;
        if (chartData.xPercentage.length < 2) {
            p = 1f;
        } else {
            p = chartData.xPercentage[1] * chartFullWidth;
        }
        float xP = (offset + x) / (chartFullWidth - p);
        selectedCoordinate = xP;
        if (xP < 0) {
            selectedIndex = 0;
            selectedCoordinate = 0f;
        } else if (xP > 1) {
            selectedIndex = chartData.x.length - 1;
            selectedCoordinate = 1f;
        } else {
            selectedIndex = chartData.findIndex(startXIndex, endXIndex, xP);
            if (selectedIndex > endXIndex) selectedIndex = endXIndex;
            if (selectedIndex < startXIndex) selectedIndex = startXIndex;
        }

        if (oldSelectedIndex != selectedIndex) {
            legendShowing = true;
            animateLegend(true);
            moveLegend(offset);
            if (dateSelectionListener != null) {
                dateSelectionListener.onDateSelected(getSelectedDate());
            }
            invalidate();
            runSmoothHaptic();
        }
    }

    @Override
    protected void drawPickerChart(Canvas canvas) {
        if (chartData != null) {

            int n = chartData.xPercentage.length;
            int nl = lines.size();
            for (int k = 0; k < lines.size(); k++) {
                LineViewData line = lines.get(k);
                line.linesPathBottomSize = 0;
            }

            int step = Math.max(1, Math.round(n / 200f));

            if (yMaxPoints == null || yMaxPoints.length < nl) {
                yMaxPoints = new int[nl];
            }

            for (int i = 0; i < n; i++) {
                float stackOffset = 0;
                float xPoint = chartData.xPercentage[i] * pickerWidth;

                for (int k = 0; k < nl; k++) {
                    LineViewData line = lines.get(k);
                    if (!line.enabled && line.alpha == 0) continue;
                    int y = line.line.y[i];
                    if (y > yMaxPoints[k]) yMaxPoints[k] = y;
                }

                if (i % step == 0) {
                    for (int k = 0; k < nl; k++) {
                        LineViewData line = lines.get(k);
                        if (!line.enabled && line.alpha == 0) continue;

                        float h = ANIMATE_PICKER_SIZES ? pickerMaxHeight : chartData.maxValue;
                        float yPercentage = (float) yMaxPoints[k] / h * line.alpha;
                        float yPoint = (yPercentage) * (pikerHeight);


                        line.linesPath[line.linesPathBottomSize++] = xPoint;
                        line.linesPath[line.linesPathBottomSize++] = pikerHeight - yPoint - stackOffset;

                        line.linesPath[line.linesPathBottomSize++] = xPoint;
                        line.linesPath[line.linesPathBottomSize++] = pikerHeight - stackOffset;

                        stackOffset += yPoint;

                        yMaxPoints[k] = 0;
                    }
                }
            }

            float p;
            if (chartData.xPercentage.length < 2) {
                p = 1f;
            } else {
                p = chartData.xPercentage[1] * pickerWidth;
            }

            for (int k = 0; k < nl; k++) {
                LineViewData line = lines.get(k);
                line.paint.setStrokeWidth(p * step);
                line.paint.setAlpha(255);
                canvas.drawLines(line.linesPath, 0, line.linesPathBottomSize, line.paint);
            }
        }
    }

    public void onCheckChanged() {
        int n = chartData.lines.get(0).y.length;
        int k = chartData.lines.size();

        chartData.ySum = new int[n];
        for (int i = 0; i < n; i++) {
            chartData.ySum[i] = 0;
            for (int j = 0; j < k; j++) {
                if (lines.get(j).enabled) chartData.ySum[i] += chartData.lines.get(j).y[i];
            }
        }

        chartData.ySumSegmentTree = new SegmentTree(chartData.ySum);
        super.onCheckChanged();
    }

    @Override
    protected void drawSelection(Canvas canvas) {

    }

    public int findMaxValue(int startXIndex, int endXIndex) {
        return chartData.findMax(startXIndex, endXIndex);
    }


    protected void updatePickerMinMaxHeight() {
        if (!ANIMATE_PICKER_SIZES) return;
        int max = 0;

        int n = chartData.x.length;
        int nl = lines.size();
        for (int i = 0; i < n; i++) {
            int h = 0;
            for (int k = 0; k < nl; k++) {
                StackBarViewData l = lines.get(k);
                if (l.enabled) h += l.line.y[i];
            }
            if (h > max) max = h;
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

    @Override
    protected void initPickerMaxHeight() {
        super.initPickerMaxHeight();
        pickerMaxHeight = 0;
        int n = chartData.x.length;
        int nl = lines.size();
        for (int i = 0; i < n; i++) {
            int h = 0;
            for (int k = 0; k < nl; k++) {
                StackBarViewData l = lines.get(k);
                if (l.enabled) h += l.line.y[i];
            }
            if (h > pickerMaxHeight) pickerMaxHeight = h;
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

    protected float getMinDistance() {
        return 0.1f;
    }
}
