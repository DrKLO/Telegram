package org.telegram.ui.Charts;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Charts.data.ChartData;
import org.telegram.ui.Charts.data.StackLinearChartData;
import org.telegram.ui.Charts.view_data.ChartHorizontalLinesData;
import org.telegram.ui.Charts.view_data.LegendSignatureView;
import org.telegram.ui.Charts.view_data.LineViewData;
import org.telegram.ui.Charts.view_data.PieLegendView;
import org.telegram.ui.Charts.view_data.TransitionParams;


public class PieChartView extends StackLinearChartView<PieChartViewData> {

    float[] values;
    float[] darawingValuesPercentage;
    float sum;

    boolean isEmpty;
    int currentSelection = -1;

    RectF rectF = new RectF();

    TextPaint textPaint;

    float MIN_TEXT_SIZE = AndroidUtilities.dp(9);
    float MAX_TEXT_SIZE = AndroidUtilities.dp(13);

    String[] lookupTable = new String[101];

    PieLegendView pieLegendView;

    float emptyDataAlpha = 1f;

    public PieChartView(Context context) {
        super(context);
        for (int i = 1; i <= 100; i++) {
            lookupTable[i] = i + "%";
        }

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        canCaptureChartSelection = true;
    }


    @Override
    protected void drawChart(Canvas canvas) {
        if (chartData == null) return;

        int transitionAlpha = 255;

        if (canvas != null) {
            canvas.save();
        }
        if (transitionMode == TRANSITION_MODE_CHILD) {
            transitionAlpha = (int) (transitionParams.progress * transitionParams.progress * 255);
        }

        if (isEmpty) {
            if (emptyDataAlpha != 0) {
                emptyDataAlpha -= 0.12f;
                if (emptyDataAlpha < 0) {
                    emptyDataAlpha = 0;
                }
                invalidate();
            }
        } else {
            if (emptyDataAlpha != 1f) {
                emptyDataAlpha += 0.12f;
                if (emptyDataAlpha > 1f) {
                    emptyDataAlpha = 1f;
                }
                invalidate();
            }
        }

        transitionAlpha = (int) (transitionAlpha * emptyDataAlpha);
        float sc = 0.4f + emptyDataAlpha * 0.6f;
        if (canvas != null) {
            canvas.scale(sc, sc,
                    chartArea.centerX(),
                    chartArea.centerY()
            );
        }

        int radius = (int) ((chartArea.width() > chartArea.height() ? chartArea.height() : chartArea.width()) * 0.45f);
        rectF.set(
                chartArea.centerX() - radius,
                chartArea.centerY() + AndroidUtilities.dp(16) - radius,
                chartArea.centerX() + radius,
                chartArea.centerY() + AndroidUtilities.dp(16) + radius
        );


        float a = -90f;
        float rText;

        int n = lines.size();

        float localSum = 0f;
        for (int i = 0; i < n; i++) {
            float v = lines.get(i).drawingPart * lines.get(i).alpha;
            localSum += v;
        }
        if (localSum == 0) {
            if (canvas != null) {
                canvas.restore();
            }
            return;
        }
        for (int i = 0; i < n; i++) {
            if (lines.get(i).alpha <= 0 && !lines.get(i).enabled) continue;
            lines.get(i).paint.setAlpha(transitionAlpha);

            float currentPercent = lines.get(i).drawingPart / localSum * lines.get(i).alpha;
            darawingValuesPercentage[i] = currentPercent;

            if (currentPercent == 0) {
                continue;
            }

            if (canvas != null) {
                canvas.save();
            }

            double textAngle = a + (currentPercent / 2f) * 360f;

            if (lines.get(i).selectionA > 0f) {
                float ai = INTERPOLATOR.getInterpolation(lines.get(i).selectionA);
                if (canvas != null) {
                    canvas.translate(
                            (float) (Math.cos(Math.toRadians(textAngle)) * AndroidUtilities.dp(8) * ai),
                            (float) (Math.sin(Math.toRadians(textAngle)) * AndroidUtilities.dp(8) * ai)
                    );
                }
            }

            lines.get(i).paint.setStyle(Paint.Style.FILL_AND_STROKE);
            lines.get(i).paint.setStrokeWidth(1);
            lines.get(i).paint.setAntiAlias(!USE_LINES);

            if (canvas != null && transitionMode != TRANSITION_MODE_CHILD) {
                canvas.drawArc(
                        rectF,
                        a,
                        (currentPercent) * 360f,
                        true,
                        lines.get(i).paint);
                lines.get(i).paint.setStyle(Paint.Style.STROKE);
                canvas.restore();
            }

            lines.get(i).paint.setAlpha(255);
            a += currentPercent * 360f;
        }
        a = -90f;

        if (canvas != null) {
            for (int i = 0; i < n; i++) {
                if (lines.get(i).alpha <= 0 && !lines.get(i).enabled) continue;
                float currentPercent = (lines.get(i).drawingPart * lines.get(i).alpha / localSum);

                canvas.save();
                double textAngle = a + (currentPercent / 2f) * 360f;

                if (lines.get(i).selectionA > 0f) {
                    float ai = INTERPOLATOR.getInterpolation(lines.get(i).selectionA);
                    canvas.translate(
                            (float) (Math.cos(Math.toRadians(textAngle)) * AndroidUtilities.dp(8) * ai),
                            (float) (Math.sin(Math.toRadians(textAngle)) * AndroidUtilities.dp(8) * ai)
                    );
                }

                int percent = (int) (100f * currentPercent);
                if (currentPercent >= 0.02f && percent > 0 && percent <= 100) {
                    rText = (float) (rectF.width() * 0.42f * Math.sqrt(1f - currentPercent));
                    textPaint.setTextSize(MIN_TEXT_SIZE + currentPercent * MAX_TEXT_SIZE);
                    textPaint.setAlpha((int) (transitionAlpha * lines.get(i).alpha));
                    canvas.drawText(
                            lookupTable[percent],
                            (float) (rectF.centerX() + rText * Math.cos(Math.toRadians(textAngle))),
                            (float) (rectF.centerY() + rText * Math.sin(Math.toRadians(textAngle))) - ((textPaint.descent() + textPaint.ascent()) / 2),
                            textPaint);
                }

                canvas.restore();

                lines.get(i).paint.setAlpha(255);
                a += currentPercent * 360f;
            }

            canvas.restore();
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

            float p = (1f / chartData.xPercentage.length) * pickerWidth;

            for (int i = 0; i < n; i++) {
                float stackOffset = 0;
                float xPoint = p / 2 + chartData.xPercentage[i] * (pickerWidth - p);

                float sum = 0;
                int drawingLinesCount = 0;
                boolean allDisabled = true;
                for (int k = 0; k < nl; k++) {
                    LineViewData line = lines.get(k);
                    if (!line.enabled && line.alpha == 0) continue;
                    float v = line.line.y[i] * line.alpha;
                    sum += v;
                    if (v > 0) {
                        drawingLinesCount++;
                        if (line.enabled) {
                            allDisabled = false;
                        }
                    }
                }

                for (int k = 0; k < nl; k++) {
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
                        } else if (allDisabled) {
                            yPercentage = (y[i] / sum) * line.alpha * line.alpha;
                        } else {
                            yPercentage = (y[i] / sum) * line.alpha;
                        }
                    }

                    float yPoint = (yPercentage) * (pikerHeight);


                    line.linesPath[line.linesPathBottomSize++] = xPoint;
                    line.linesPath[line.linesPathBottomSize++] = pikerHeight - yPoint - stackOffset;

                    line.linesPath[line.linesPathBottomSize++] = xPoint;
                    line.linesPath[line.linesPathBottomSize++] = pikerHeight - stackOffset;

                    stackOffset += yPoint;
                }
            }

            for (int k = 0; k < nl; k++) {
                LineViewData line = lines.get(k);
                line.paint.setStrokeWidth(p);
                line.paint.setAlpha(255);
                line.paint.setAntiAlias(false);
                canvas.drawLines(line.linesPath, 0, line.linesPathBottomSize, line.paint);
            }
        }
    }

    @Override
    protected void drawBottomLine(Canvas canvas) {

    }

    @Override
    protected void drawSelection(Canvas canvas) {

    }

    @Override
    protected void drawHorizontalLines(Canvas canvas, ChartHorizontalLinesData a) {

    }

    @Override
    protected void drawSignaturesToHorizontalLines(Canvas canvas, ChartHorizontalLinesData a) {

    }

    @Override
    void drawBottomSignature(Canvas canvas) {

    }


    @Override
    public void setData(StackLinearChartData chartData) {
        super.setData(chartData);
        if (chartData != null) {
            values = new float[chartData.lines.size()];
            darawingValuesPercentage = new float[chartData.lines.size()];
            onPickerDataChanged(false, true, false);
        }
    }

    @Override
    public PieChartViewData createLineViewData(ChartData.Line line) {
        return new PieChartViewData(line);
    }


    protected void selectXOnChart(int x, int y) {
        if (chartData == null || isEmpty) return;
        double theta = Math.atan2(chartArea.centerY() + AndroidUtilities.dp(16) - y, chartArea.centerX() - x);

        float a = (float) (Math.toDegrees(theta) - 90);
        if (a < 0) a += 360D;
        a /= 360;

        float p = 0;
        int newSelection = -1;

        float selectionStartA = 0f;
        float selectionEndA = 0f;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).enabled && lines.get(i).alpha == 0) {
                continue;
            }
            if (a > p && a < p + darawingValuesPercentage[i]) {
                newSelection = i;
                selectionStartA = p;
                selectionEndA = p + darawingValuesPercentage[i];
                break;
            }
            p += darawingValuesPercentage[i];
        }
        if (currentSelection != newSelection && newSelection >= 0) {
            currentSelection = newSelection;
            invalidate();
            pieLegendView.setVisibility(VISIBLE);
            LineViewData l = lines.get(newSelection);

            pieLegendView.setData(l.line.name, (int) values[currentSelection], l.lineColor);
            pieLegendView.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST));

            float r = rectF.width() / 2;
            int xl = (int) Math.min(
                    rectF.centerX() + r * Math.cos(Math.toRadians((selectionEndA * 360f) - 90f)),
                    rectF.centerX() + r * Math.cos(Math.toRadians(((selectionStartA * 360f) - 90f)))
            );

            if (xl < 0) xl = 0;
            if (xl + pieLegendView.getMeasuredWidth() > getMeasuredWidth() - AndroidUtilities.dp((16))) {
                xl -= xl + pieLegendView.getMeasuredWidth() - (getMeasuredWidth() - AndroidUtilities.dp(16));
            }

            int yl = (int) Math.min(
                    (rectF.centerY() + r * Math.sin(Math.toRadians((selectionStartA * 360f) - 90f))),
                    rectF.centerY() + r * Math.sin(Math.toRadians(((selectionEndA * 360f) - 90f)))
            );

            yl = (int) Math.min(rectF.centerY(), yl);

            yl -= AndroidUtilities.dp(50);
           // if (yl < 0) yl = 0;

            pieLegendView.setTranslationX(xl);
            pieLegendView.setTranslationY(yl);
            AndroidUtilities.vibrateCursor(this);
        }
        moveLegend();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (chartData != null) {
            for (int i = 0; i < lines.size(); i++) {
                if (i == currentSelection) {
                    if (lines.get(i).selectionA < 1f) {
                        lines.get(i).selectionA += 0.1f;
                        if (lines.get(i).selectionA > 1f) lines.get(i).selectionA = 1f;
                        invalidate();
                    }
                } else {
                    if (lines.get(i).selectionA > 0) {
                        lines.get(i).selectionA -= 0.1f;
                        if (lines.get(i).selectionA < 0) lines.get(i).selectionA = 0;
                        invalidate();
                    }
                }
            }
        }
        super.onDraw(canvas);
    }

    protected void onActionUp() {
        currentSelection = -1;
        pieLegendView.setVisibility(GONE);
        invalidate();
    }

    int oldW = 0;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getMeasuredWidth() != oldW) {
            oldW = getMeasuredWidth();
            int r = (int) ((chartArea.width() > chartArea.height() ? chartArea.height() : chartArea.width()) * 0.45f);
            MIN_TEXT_SIZE = r / 13;
            MAX_TEXT_SIZE = r / 7;
        }
    }

    public void updatePicker(ChartData chartData, long d) {
        int n = chartData.x.length;
        long startOfDay = d - d % 86400000L;
        int startIndex = 0;

        for (int i = 0; i < n; i++) {
            if (startOfDay >= chartData.x[i]) startIndex = i;
        }

        float p;
        if (chartData.xPercentage.length < 2) {
            p = 0.5f;
        } else {
            p = 1f / chartData.x.length;
        }

        if (startIndex == 0) {
            pickerDelegate.pickerStart = 0;
            pickerDelegate.pickerEnd = p;
            return;
        }

        if (startIndex >= chartData.x.length - 1) {
            pickerDelegate.pickerStart = 1f - p;
            pickerDelegate.pickerEnd = 1f;
            return;
        }

        pickerDelegate.pickerStart = p * startIndex;
        pickerDelegate.pickerEnd = pickerDelegate.pickerStart + p;
        if (pickerDelegate.pickerEnd > 1f) {
            pickerDelegate.pickerEnd = 1f;
        }

        onPickerDataChanged(true, true, false);
    }

    @Override
    protected LegendSignatureView createLegendView() {
        return pieLegendView = new PieLegendView(getContext());
    }

    int lastStartIndex = -1;
    int lastEndIndex = -1;

    @Override
    public void onPickerDataChanged(boolean animated, boolean force, boolean useAnimator) {
        super.onPickerDataChanged(animated, force, useAnimator);
        if (chartData == null || chartData.xPercentage == null) {
            return;
        }
        float startPercentage = pickerDelegate.pickerStart;
        float endPercentage = pickerDelegate.pickerEnd;
        updateCharValues(startPercentage, endPercentage, force);
    }

    private void updateCharValues(float startPercentage, float endPercentage, boolean force) {
        if (values == null) {
            return;
        }
        int n = chartData.xPercentage.length;
        int nl = lines.size();


        int startIndex = -1;
        int endIndex = -1;
        for (int j = 0; j < n; j++) {
            if (chartData.xPercentage[j] >= startPercentage && startIndex == -1) {
                startIndex = j;
            }
            if (chartData.xPercentage[j] <= endPercentage) {
                endIndex = j;
            }
        }
        if (endIndex < startIndex) {
            startIndex = endIndex;
        }


        if (!force && lastEndIndex == endIndex && lastStartIndex == startIndex) {
            return;
        }
        lastEndIndex = endIndex;
        lastStartIndex = startIndex;

        isEmpty = true;
        sum = 0;
        for (int i = 0; i < nl; i++) {
            values[i] = 0;
        }

        for (int j = startIndex; j <= endIndex; j++) {
            for (int i = 0; i < nl; i++) {
                values[i] += chartData.lines.get(i).y[j];
                sum += chartData.lines.get(i).y[j];
                if (isEmpty && lines.get(i).enabled && chartData.lines.get(i).y[j] > 0) {
                    isEmpty = false;
                }
            }
        }
        if (!force) {
            for (int i = 0; i < nl; i++) {
                PieChartViewData line = lines.get(i);
                if (line.animator != null) line.animator.cancel();
                float animateTo;
                if (sum == 0) {
                    animateTo = 0;
                } else {
                    animateTo = values[i] / sum;
                }
                ValueAnimator animator = createAnimator(line.drawingPart, animateTo, (animation -> {
                    line.drawingPart = (float) animation.getAnimatedValue();
                    invalidate();
                }));
                line.animator = animator;
                animator.start();
            }
        } else {
            for (int i = 0; i < nl; i++) {
                if (sum == 0) {
                    lines.get(i).drawingPart = 0;
                } else {
                    lines.get(i).drawingPart = values[i] / sum;
                }
            }
        }
    }

    @Override
    public void onPickerJumpTo(float start, float end, boolean force) {
        if (chartData == null) return;
        if (force) {
            updateCharValues(start, end, false);
        } else {
            updateIndexes();
            invalidate();
        }
    }

    @Override
    public void fillTransitionParams(TransitionParams params) {
        drawChart(null);
        float p = 0;
        for (int i = 0; i < darawingValuesPercentage.length; i++) {
            p += darawingValuesPercentage[i];
            params.angle[i] = p * 360 - 180;
        }
    }
}
