package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableString;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

public class StroageUsageView extends FrameLayout {

    private Paint paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paintCalculcating = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paintProgress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paintProgress2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint bgPaint = new Paint();
    private long totalSize;
    private long totalDeviceFreeSize;
    private long totalDeviceSize;
    private boolean calculating;
    ProgressView progressView;

    TextView telegramCacheTextView;
    TextView telegramDatabaseTextView;
    TextView freeSizeTextView;
    TextView totlaSizeTextView;
    TextView calculatingTextView;
    View divider;

    int lastProgressColor;

    TextSettingsCell textSettingsCell;

    float progress;
    float progress2;
    ValueAnimator valueAnimator;
    ValueAnimator valueAnimator2;
    public ViewGroup legendLayout;

    EllipsizeSpanAnimator ellipsizeSpanAnimator;

    float calculatingProgress;
    boolean calculatingProgressIncrement;

    CellFlickerDrawable cellFlickerDrawable = new CellFlickerDrawable(220, 255);

    public StroageUsageView(Context context) {
        super(context);
        setWillNotDraw(false);

        cellFlickerDrawable.drawFrame = false;
        paintFill.setStrokeWidth(AndroidUtilities.dp(6));
        paintCalculcating.setStrokeWidth(AndroidUtilities.dp(6));
        paintProgress.setStrokeWidth(AndroidUtilities.dp(6));
        paintProgress2.setStrokeWidth(AndroidUtilities.dp(6));
        paintFill.setStrokeCap(Paint.Cap.ROUND);
        paintCalculcating.setStrokeCap(Paint.Cap.ROUND);
        paintProgress.setStrokeCap(Paint.Cap.ROUND);
        paintProgress2.setStrokeCap(Paint.Cap.ROUND);

        progressView = new ProgressView(context);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        legendLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec),MeasureSpec.EXACTLY), heightMeasureSpec);
                int currentW = 0;
                int currentH = 0;
                int n = getChildCount();
                int lastChildH = 0;
                for (int i = 0; i < n; i++) {
                    if (getChildAt(i).getVisibility() == View.GONE) {
                        continue;
                    }
                    if (currentW + getChildAt(i).getMeasuredWidth() > MeasureSpec.getSize(widthMeasureSpec)) {
                        currentW = 0;
                        currentH += getChildAt(i).getMeasuredHeight() + AndroidUtilities.dp(8);
                    }
                    currentW += getChildAt(i).getMeasuredWidth() + AndroidUtilities.dp(16);
                    lastChildH = currentH + getChildAt(i).getMeasuredHeight();
                }
                setMeasuredDimension(getMeasuredWidth(), lastChildH);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int currentW = 0;
                int currentH = 0;
                int n = getChildCount();
                for (int i = 0; i < n; i++) {
                    if (getChildAt(i).getVisibility() == View.GONE) {
                        continue;
                    }
                    if (currentW + getChildAt(i).getMeasuredWidth() > getMeasuredWidth()) {
                        currentW = 0;
                        currentH += getChildAt(i).getMeasuredHeight() + AndroidUtilities.dp(8);
                    }
                    getChildAt(i).layout(currentW, currentH,
                            currentW + getChildAt(i).getMeasuredWidth(),
                            currentH + getChildAt(i).getMeasuredHeight());

                    currentW += getChildAt(i).getMeasuredWidth() + AndroidUtilities.dp(16);
                }
            }
        };
        linearLayout.addView(legendLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 21, 40, 21, 16));


        calculatingTextView = new TextView(context);
        calculatingTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        String calculatingString = LocaleController.getString("CalculatingSize",R.string.CalculatingSize);
        int indexOfDots = calculatingString.indexOf("...");
        if (indexOfDots >= 0) {
            SpannableString spannableString = new SpannableString(calculatingString);
            ellipsizeSpanAnimator = new EllipsizeSpanAnimator(calculatingTextView);
            ellipsizeSpanAnimator.wrap(spannableString, indexOfDots);
            calculatingTextView.setText(spannableString);
        } else {
            calculatingTextView.setText(calculatingString);
        }


        telegramCacheTextView = new TextView(context);
        telegramCacheTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        telegramCacheTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        telegramDatabaseTextView = new TextView(context);
        telegramDatabaseTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        telegramDatabaseTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        freeSizeTextView = new TextView(context);
        freeSizeTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        freeSizeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        totlaSizeTextView = new TextView(context);
        totlaSizeTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        totlaSizeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));


        lastProgressColor = Theme.getColor(Theme.key_player_progress);

        telegramCacheTextView.setCompoundDrawablesWithIntrinsicBounds(Theme.createCircleDrawable(AndroidUtilities.dp(10), lastProgressColor), null, null, null);
        telegramCacheTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        freeSizeTextView.setCompoundDrawablesWithIntrinsicBounds(Theme.createCircleDrawable(AndroidUtilities.dp(10), ColorUtils.setAlphaComponent(lastProgressColor,64)), null, null, null);
        freeSizeTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        totlaSizeTextView.setCompoundDrawablesWithIntrinsicBounds(Theme.createCircleDrawable(AndroidUtilities.dp(10), ColorUtils.setAlphaComponent(lastProgressColor,127)), null, null, null);
        totlaSizeTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        telegramDatabaseTextView.setCompoundDrawablesWithIntrinsicBounds(Theme.createCircleDrawable(AndroidUtilities.dp(10), lastProgressColor), null, null, null);
        telegramDatabaseTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));

        legendLayout.addView(calculatingTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        legendLayout.addView(telegramDatabaseTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        legendLayout.addView(telegramCacheTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        legendLayout.addView(totlaSizeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        legendLayout.addView(freeSizeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));


        divider = new View(getContext());
        linearLayout.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 0, 0, 0));
        divider.getLayoutParams().height = 1;
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));

        textSettingsCell = new TextSettingsCell(getContext());
        linearLayout.addView(textSettingsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

    }

    public void setStorageUsage(boolean calculating, long database, long totalSize, long totalDeviceFreeSize, long totalDeviceSize) {
        this.calculating = calculating;
        this.totalSize = totalSize;
        this.totalDeviceFreeSize = totalDeviceFreeSize;
        this.totalDeviceSize = totalDeviceSize;


        freeSizeTextView.setText(LocaleController.formatString("TotalDeviceFreeSize", R.string.TotalDeviceFreeSize, AndroidUtilities.formatFileSize(totalDeviceFreeSize)));
        totlaSizeTextView.setText(LocaleController.formatString("TotalDeviceSize", R.string.TotalDeviceSize, AndroidUtilities.formatFileSize(totalDeviceSize - totalDeviceFreeSize)));

        if (calculating) {
            calculatingTextView.setVisibility(View.VISIBLE);
            telegramCacheTextView.setVisibility(View.GONE);
            freeSizeTextView.setVisibility(View.GONE);
            totlaSizeTextView.setVisibility(View.GONE);
            telegramDatabaseTextView.setVisibility(View.GONE);
            divider.setVisibility(GONE);
            textSettingsCell.setVisibility(GONE);
            progress = 0f;
            progress2 = 0;
            if (ellipsizeSpanAnimator != null) {
                ellipsizeSpanAnimator.addView(calculatingTextView);
            }
        } else {
            if (ellipsizeSpanAnimator != null) {
                ellipsizeSpanAnimator.removeView(calculatingTextView);
            }
            calculatingTextView.setVisibility(View.GONE);
            if (totalSize > 0) {
                divider.setVisibility(VISIBLE);
                textSettingsCell.setVisibility(VISIBLE);
                telegramCacheTextView.setVisibility(View.VISIBLE);
                telegramDatabaseTextView.setVisibility(GONE);
                textSettingsCell.setText(LocaleController.getString("ClearTelegramCache", R.string.ClearTelegramCache), false);
                telegramCacheTextView.setText(LocaleController.formatString("TelegramCacheSize", R.string.TelegramCacheSize, AndroidUtilities.formatFileSize(totalSize + database)));

            } else {
                telegramCacheTextView.setVisibility(View.GONE);
                telegramDatabaseTextView.setVisibility(VISIBLE);
                telegramDatabaseTextView.setText(LocaleController.formatString("LocalDatabaseSize", R.string.LocalDatabaseSize, AndroidUtilities.formatFileSize(database)));
                divider.setVisibility(GONE);
                textSettingsCell.setVisibility(GONE);
            }
            freeSizeTextView.setVisibility(View.VISIBLE);
            totlaSizeTextView.setVisibility(View.VISIBLE);

            float p = (totalSize + database) / (float) (totalDeviceSize);
            float p2 = (totalDeviceSize - totalDeviceFreeSize) / (float) (totalDeviceSize);
            if (progress != p) {
                if (valueAnimator != null) {
                    valueAnimator.cancel();
                }
                valueAnimator = ValueAnimator.ofFloat(progress, p);
                valueAnimator.addUpdateListener(animation -> {
                    progress = (float) animation.getAnimatedValue();
                    invalidate();
                });
                valueAnimator.start();;
            }

            if (progress2 != p2) {
                if (valueAnimator2 != null) {
                    valueAnimator2.cancel();
                }
                valueAnimator2 = ValueAnimator.ofFloat(progress2, p2);
                valueAnimator2.addUpdateListener(animation -> {
                    progress2 = (float) animation.getAnimatedValue();
                    invalidate();
                });
                valueAnimator2.start();;
            }
        }

        textSettingsCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        requestLayout();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        progressView.invalidate();

        if (lastProgressColor != Theme.getColor(Theme.key_player_progress)){
            lastProgressColor = Theme.getColor(Theme.key_player_progress);

            telegramCacheTextView.setCompoundDrawablesWithIntrinsicBounds(Theme.createCircleDrawable(AndroidUtilities.dp(10), lastProgressColor), null, null, null);
            telegramCacheTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));

            telegramDatabaseTextView.setCompoundDrawablesWithIntrinsicBounds(Theme.createCircleDrawable(AndroidUtilities.dp(10), lastProgressColor), null, null, null);
            telegramDatabaseTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));

            freeSizeTextView.setCompoundDrawablesWithIntrinsicBounds(Theme.createCircleDrawable(AndroidUtilities.dp(10), ColorUtils.setAlphaComponent(lastProgressColor,64)), null, null, null);
            freeSizeTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));

            totlaSizeTextView.setCompoundDrawablesWithIntrinsicBounds(Theme.createCircleDrawable(AndroidUtilities.dp(10), ColorUtils.setAlphaComponent(lastProgressColor,127)), null, null, null);
            totlaSizeTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        }

        textSettingsCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
    }

    private class ProgressView extends View {

        public ProgressView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(40), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int color = Theme.getColor(Theme.key_player_progress);

            paintFill.setColor(color);
            paintProgress.setColor(color);
            paintProgress2.setColor(color);

            paintProgress.setAlpha(255);
            paintProgress2.setAlpha(82);
            paintFill.setAlpha(46);

            bgPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            canvas.drawLine(AndroidUtilities.dp(24), AndroidUtilities.dp(20), getMeasuredWidth() - AndroidUtilities.dp(24), AndroidUtilities.dp(20), paintFill);
            if (calculating || calculatingProgress != 0) {
                if (calculating) {
                    if (calculatingProgressIncrement) {
                        calculatingProgress += 16f / 650;
                        if (calculatingProgress > 1f) {
                            calculatingProgress = 1f;
                            calculatingProgressIncrement = false;
                        }
                    } else {
                        calculatingProgress -= 16f / 650;
                        if (calculatingProgress < 0) {
                            calculatingProgress = 0;
                            calculatingProgressIncrement = true;
                        }

                    }
                } else {
                    calculatingProgress -= 16f / 150;
                    if (calculatingProgress < 0) {
                        calculatingProgress = 0;
                    }
                }
                invalidate();
//                paintCalculcating.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (150 * calculatingProgress)));
//                canvas.drawLine(AndroidUtilities.dp(24), AndroidUtilities.dp(20), getMeasuredWidth() - AndroidUtilities.dp(24), AndroidUtilities.dp(20), paintCalculcating);
                AndroidUtilities.rectTmp.set(AndroidUtilities.dp(24), AndroidUtilities.dp(17), getMeasuredWidth() - AndroidUtilities.dp(24), AndroidUtilities.dp(23));
                cellFlickerDrawable.setParentWidth(getMeasuredWidth());
                cellFlickerDrawable.draw(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(3));
            }
            int currentP = AndroidUtilities.dp(24);
            if (!calculating) {
                int progressWidth = (int) ((getMeasuredWidth() - AndroidUtilities.dp(24) * 2) * progress2);
                int left = AndroidUtilities.dp(24) + progressWidth;
                canvas.drawLine(currentP, AndroidUtilities.dp(20), AndroidUtilities.dp(24) + progressWidth, AndroidUtilities.dp(20), paintProgress2);
                canvas.drawRect(left, AndroidUtilities.dp(20) - AndroidUtilities.dp(3), left + AndroidUtilities.dp(3), AndroidUtilities.dp(20) + AndroidUtilities.dp(3), bgPaint);
            }

            if (!calculating) {
                int progressWidth = (int) ((getMeasuredWidth() - AndroidUtilities.dp(24) * 2) * progress);
                if (progressWidth < AndroidUtilities.dp(1f)) {
                    progressWidth = AndroidUtilities.dp(1f);
                }
                int left = AndroidUtilities.dp(24) + progressWidth;
                canvas.drawLine(currentP, AndroidUtilities.dp(20), AndroidUtilities.dp(24) + progressWidth, AndroidUtilities.dp(20), paintProgress);
                canvas.drawRect(left, AndroidUtilities.dp(20) - AndroidUtilities.dp(3), left + AndroidUtilities.dp(3), AndroidUtilities.dp(20) + AndroidUtilities.dp(3), bgPaint);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (ellipsizeSpanAnimator != null) {
            ellipsizeSpanAnimator.onAttachedToWindow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (ellipsizeSpanAnimator != null) {
            ellipsizeSpanAnimator.onDetachedFromWindow();
        }
    }
}
