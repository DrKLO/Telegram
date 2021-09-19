package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class StorageDiagramView extends View {

    private RectF rectF = new RectF();
    private ClearViewData[] data;
    private float[] drawingPercentage;
    private float[] animateToPercentage;
    private float[] startFromPercentage;

    private float singleProgress = 0;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    StaticLayout layout1;
    StaticLayout layout2;

    int enabledCount;

    ValueAnimator valueAnimator;

    public StorageDiagramView(Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(110), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(110), MeasureSpec.EXACTLY)
        );
        rectF.set(AndroidUtilities.dp(3), AndroidUtilities.dp(3), getMeasuredWidth() - AndroidUtilities.dp(3), getMeasuredHeight() - AndroidUtilities.dp(3));
        updateDescription();
        textPaint.setTextSize(AndroidUtilities.dp(24));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        textPaint2.setTextSize(AndroidUtilities.dp(13));
    }

    public void setData(ClearViewData[] data) {
        this.data = data;
        invalidate();
        drawingPercentage = new float[data.length];
        animateToPercentage = new float[data.length];
        startFromPercentage = new float[data.length];

        update(false);

        if (enabledCount > 1) {
            singleProgress = 0;
        } else {
            singleProgress = 1f;
        }


    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (data == null) {
            return;
        }

        if (enabledCount > 1) {
            if (singleProgress > 0) {
                singleProgress -= 0.04;
                if (singleProgress < 0) {
                    singleProgress = 0;
                }
            }
        } else {
            if (singleProgress < 1f) {
                singleProgress += 0.04;
                if (singleProgress > 1f) {
                    singleProgress = 1f;
                }
            }
        }

        float startFrom = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == null || drawingPercentage[i] == 0) {
                continue;
            }
            float percent = drawingPercentage[i];
            if (data[i].firstDraw) {
                float a = -360 * percent + (1f - singleProgress) * 10;
                if (a > 0) {
                    a = 0;
                }
                data[i].paint.setColor(Theme.getColor(data[i].color));
                data[i].paint.setAlpha(255);
                float r = (rectF.width() / 2);
                float len = (float) ((Math.PI * r / 180) * a);
                if (Math.abs(len) <= 1f) {
                    float x = rectF.centerX() + (float) (r * Math.cos(Math.toRadians(-90 - 360 * startFrom)));
                    float y = rectF.centerY() + (float) (r * Math.sin(Math.toRadians(-90 - 360 * startFrom)));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        canvas.drawPoint(x,y,data[i].paint);
                    } else {
                        data[i].paint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(x, y, data[i].paint.getStrokeWidth() / 2, data[i].paint);
                    }
                } else {
                    data[i].paint.setStyle(Paint.Style.STROKE);
                    canvas.drawArc(rectF, -90 - 360 * startFrom, a, false, data[i].paint);
                }
            }
            startFrom += percent;
        }

        startFrom = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == null || drawingPercentage[i] == 0) {
                continue;
            }
            float percent = drawingPercentage[i];
            if (!data[i].firstDraw) {
                float a = -360 * percent + (1f - singleProgress) * 10;
                if (a > 0) {
                    a = 0;
                }
                data[i].paint.setColor(Theme.getColor(data[i].color));
                data[i].paint.setAlpha(255);
                float r = (rectF.width() / 2);
                float len = (float) ((Math.PI * r / 180) * a);
                if (Math.abs(len) <= 1f) {
                    float x = rectF.centerX() + (float) (r * Math.cos(Math.toRadians(-90 - 360 * startFrom)));
                    float y = rectF.centerY() + (float) (r * Math.sin(Math.toRadians(-90 - 360 * startFrom)));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        canvas.drawPoint(x,y,data[i].paint);
                    } else {
                        data[i].paint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(x, y, data[i].paint.getStrokeWidth() / 2, data[i].paint);
                    }
                } else {
                    data[i].paint.setStyle(Paint.Style.STROKE);
                    canvas.drawArc(rectF, -90 - 360 * startFrom, a, false, data[i].paint);
                }
            }
            startFrom += percent;
        }

        if (layout1 != null) {
            canvas.save();
            canvas.translate(
                    (getMeasuredWidth() - layout1.getWidth()) >> 1,
                    ((getMeasuredHeight() - layout1.getHeight() - layout2.getHeight()) >> 1) + AndroidUtilities.dp(2)
            );
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            layout1.draw(canvas);
            canvas.translate(0,layout1.getHeight());
            layout2.draw(canvas);
            canvas.restore();
        }

    }

    public static class ClearViewData {

        public String color;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public boolean clear = true;
        boolean firstDraw = false;
        public long size;

        private final StorageDiagramView parentView;

        public ClearViewData(StorageDiagramView parentView) {
            this.parentView = parentView;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(5));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }


        public void setClear(boolean clear) {
            if (this.clear != clear) {
                this.clear = clear;
                parentView.updateDescription();
                firstDraw = true;
                parentView.update(true);
            }
        }
    }

    private void update(boolean animate) {
        long total = 0;
        ClearViewData[] data = this.data;
        if (data == null) {
            return;
        }
        for (int i = 0; i < data.length; i++) {
            if (data[i] == null || !data[i].clear) {
                continue;
            }
            total += data[i].size;
        }

        float k = 0;
        float max= 0;
        enabledCount = 0;

        for (int i = 0; i < data.length; i++) {
            if (data[i] != null) {
                if (data[i].clear) {
                    enabledCount++;
                }
            }
            if (data[i] == null || !data[i].clear) {
                animateToPercentage[i] = 0;
                continue;
            }
            float percent = data[i].size / (float) total;
            if (percent < 0.02777f) {
                percent = 0.02777f;
            }
            k += percent;
            if (percent > max && data[i].clear) {
                max = percent;
            }
            animateToPercentage[i] = percent;
        }
        if (k > 1) {
            float l = 1f / k;
            for (int i = 0; i < data.length; i++) {
                if (data[i] == null) {
                    continue;
                }
                animateToPercentage[i] *= l;
            }
        }

        if (!animate) {
            System.arraycopy(animateToPercentage, 0, drawingPercentage, 0, data.length);
        } else {
            System.arraycopy(drawingPercentage, 0, startFromPercentage, 0, data.length);

            if (valueAnimator != null) {
                valueAnimator.removeAllListeners();
                valueAnimator.cancel();
            }
            valueAnimator = ValueAnimator.ofFloat(0, 1f);
            valueAnimator.addUpdateListener(animation -> {
                float v = (float) animation.getAnimatedValue();
                for (int i = 0; i < data.length; i++) {
                    drawingPercentage[i] = startFromPercentage[i] * (1f - v) + animateToPercentage[i] * v;
                }
                invalidate();
            });
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    for (int i = 0; i < data.length; i++) {
                        if (data[i] != null) {
                            data[i].firstDraw = false;
                        }
                    }
                }
            });
            valueAnimator.setDuration(450);
            valueAnimator.setInterpolator(new FastOutSlowInInterpolator());
            valueAnimator.start();
        }
    }

    private void updateDescription() {
        if (data == null) {
            return;
        }
        long total = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == null || !data[i].clear) {
                continue;
            }
            total += data[i].size;
        }
        String[] str = AndroidUtilities.formatFileSize(total).split(" ");
        if (str.length > 1) {
            layout1 = new StaticLayout(total == 0 ? " " : str[0], textPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_CENTER, 1f, 0, false);
            layout2 = new StaticLayout(total == 0 ? " " : str[1], textPaint2, getMeasuredWidth(), Layout.Alignment.ALIGN_CENTER, 1f, 0, false);
        }
    }
}
