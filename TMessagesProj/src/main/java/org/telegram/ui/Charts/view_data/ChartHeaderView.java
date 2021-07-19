package org.telegram.ui.Charts.view_data;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Charts.BaseChartView;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ChartHeaderView extends FrameLayout {

    private TextView title;
    private TextView dates;
    private TextView datesTmp;
    public TextView back;
    private boolean showDate = true;
    private boolean useWeekInterval;

    private Drawable zoomIcon;

    SimpleDateFormat formatter = new SimpleDateFormat("d MMM yyyy");

    int textMargin;

    public ChartHeaderView(Context context) {
        super(context);
        TextPaint textPaint = new TextPaint();
        textPaint.setTextSize(14);
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textMargin = (int) textPaint.measureText("00 MMM 0000 - 00 MMM 000");

        title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, textMargin, 0));

        back = new TextView(context);
        back.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        addView(back, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));

        dates = new TextView(context);
        dates.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        dates.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        dates.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        addView(dates, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));

        datesTmp = new TextView(context);
        datesTmp.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        datesTmp.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        datesTmp.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        addView(datesTmp, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
        datesTmp.setVisibility(View.GONE);


        back.setVisibility(View.GONE);
        back.setText(LocaleController.getString("ZoomOut", R.string.ZoomOut));
        zoomIcon = ContextCompat.getDrawable(getContext(), R.drawable.stats_zoom);
        back.setCompoundDrawablesWithIntrinsicBounds(zoomIcon, null, null, null);
        back.setCompoundDrawablePadding(AndroidUtilities.dp(4));
        back.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        back.setBackground(Theme.getRoundRectSelectorDrawable(Theme.getColor(Theme.key_featuredStickers_removeButtonText)));

        datesTmp.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            datesTmp.setPivotX(datesTmp.getMeasuredWidth() * 0.7f);
            dates.setPivotX(dates.getMeasuredWidth() * 0.7f);
        });
        recolor();
    }


    public void recolor() {
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        dates.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        datesTmp.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        back.setTextColor(Theme.getColor(Theme.key_statisticChartBackZoomColor));
        zoomIcon.setColorFilter(Theme.getColor(Theme.key_statisticChartBackZoomColor), PorterDuff.Mode.SRC_IN);
    }

    public void setDates(long start, long end) {
        if (!showDate) {
            dates.setVisibility(GONE);
            datesTmp.setVisibility(GONE);
            return;
        }
        if (useWeekInterval) {
            end += 86400000L * 7;
        }
        final String newText;
        if (end - start >= 86400000L) {
            newText = formatter.format(new Date(start)) + " — " + formatter.format(new Date(end));
        } else {
            newText = formatter.format(new Date(start));
        }

        dates.setText(newText);
        dates.setVisibility(View.VISIBLE);
    }

    public void setTitle(String s) {
        title.setText(s);
    }

    public void zoomTo(BaseChartView chartView, long d, boolean animate) {
        setDates(d, d);
        back.setVisibility(View.VISIBLE);

        if (animate) {
            back.setAlpha(0);
            back.setScaleX(0.3f);
            back.setScaleY(0.3f);
            back.setPivotX(0);
            back.setPivotY(AndroidUtilities.dp(40));
            back.animate().alpha(1f)
                    .scaleY(1f)
                    .scaleX(1f)
                    .setDuration(200)
                    .start();

            title.setAlpha(1f);
            title.setTranslationX(0);
            title.setTranslationY(0);
            title.setScaleX(1f);
            title.setScaleY(1f);
            title.setPivotX(0);
            title.setPivotY(0);
            title.animate()
                    .alpha(0f)
                    .scaleY(0.3f)
                    .scaleX(0.3f)
                    .setDuration(200)
                    .start();
        } else {
            back.setAlpha(1f);
            back.setTranslationX(0);
            back.setTranslationY(0);
            back.setScaleX(1f);
            back.setScaleY(1f);
            title.setAlpha(0f);
        }
    }

    public void zoomOut(BaseChartView chartView, boolean animated) {
        setDates(chartView.getStartDate(), chartView.getEndDate());
        if (animated) {
            title.setAlpha(0);
            title.setScaleX(0.3f);
            title.setScaleY(0.3f);
            title.setPivotX(0);
            title.setPivotY(0);
            title.animate().alpha(1f)
                    .scaleY(1f)
                    .scaleX(1f)
                    .setDuration(200)
                    .start();

            back.setAlpha(1f);
            back.setTranslationX(0);
            back.setTranslationY(0);
            back.setScaleX(1f);
            back.setScaleY(1f);
            back.setPivotY(AndroidUtilities.dp(40));
            back.animate()
                    .alpha(0f)
                    .scaleY(0.3f)
                    .scaleX(0.3f)
                    .setDuration(200)
                    .start();
        } else {
            title.setAlpha(1f);
            title.setScaleX(1f);
            title.setScaleY(1f);
            back.setAlpha(0);
        }
    }

    public void setUseWeekInterval(boolean useWeekInterval) {
        this.useWeekInterval = useWeekInterval;
    }

    public void showDate(boolean b) {
        showDate = b;
        if (!showDate) {
            datesTmp.setVisibility(GONE);
            dates.setVisibility(GONE);
            title.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
            title.requestLayout();
        }  else {
            title.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, textMargin, 0));
        }
    }
}
