package org.telegram.ui.Charts.view_data;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChannelMonetizationLayout;
import org.telegram.ui.Charts.data.ChartData;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class LegendSignatureView extends FrameLayout {

    public boolean isTopHourChart;
    LinearLayout content;
    Holder[] holders;
    TextView time;
    TextView hourTime;
    Drawable background;
    public ImageView chevron;
    private RadialProgressView progressView;

    SimpleDateFormat format = new SimpleDateFormat("E, ");
    SimpleDateFormat format2 = new SimpleDateFormat("MMM dd");
    SimpleDateFormat format3 =  new SimpleDateFormat("d MMM yyyy");
    SimpleDateFormat format4 =  new SimpleDateFormat("d MMM");
    SimpleDateFormat hourFormat = new SimpleDateFormat(" HH:mm");

    public boolean useWeek;
    public boolean useHour;
    public boolean showPercentage;
    public boolean zoomEnabled;

    public boolean canGoZoom = true;

    Drawable shadowDrawable;
    Drawable backgroundDrawable;
    private Theme.ResourcesProvider resourcesProvider;

    Runnable showProgressRunnable = new Runnable() {
        @Override
        public void run() {
            chevron.animate().setDuration(120).alpha(0f);
            progressView.animate().setListener(null).start();
            if (progressView.getVisibility() != View.VISIBLE) {
                progressView.setVisibility(View.VISIBLE);
                progressView.setAlpha(0);
            }

            progressView.animate().setDuration(120).alpha(1f).start();
        }
    };

    public LegendSignatureView(Context context) {
        this(context, null);
    }

    public LegendSignatureView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        time = new TextView(context);
        time.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        time.setTypeface(AndroidUtilities.bold());
        hourTime = new TextView(context);
        hourTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hourTime.setTypeface(AndroidUtilities.bold());

        chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_chevron_right_black_18dp);

        progressView = new RadialProgressView(context);
        progressView.setSize(AndroidUtilities.dp(12));
        progressView.setStrokeWidth(AndroidUtilities.dp(0.5f));
        progressView.setVisibility(View.GONE);

        addView(content, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY, 0, 22, 0, 0));
        addView(time, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 4, 0, 4, 0));
        addView(hourTime, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END, 4, 0, 4, 0));
        addView(chevron, LayoutHelper.createFrame(18, 18, Gravity.END | Gravity.TOP, 0, 2, 0, 0));
        addView(progressView, LayoutHelper.createFrame(18, 18, Gravity.END | Gravity.TOP, 0, 2, 0, 0));

        recolor();
    }

    public void recolor() {
        time.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        hourTime.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        chevron.setColorFilter(Theme.getColor(Theme.key_statisticChartChevronColor, resourcesProvider));
        progressView.setProgressColor(Theme.getColor(Theme.key_statisticChartChevronColor, resourcesProvider));

        shadowDrawable = getContext().getResources().getDrawable(R.drawable.stats_tooltip).mutate();
        backgroundDrawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_dialogBackground, resourcesProvider), Theme.getColor(Theme.key_listSelector, resourcesProvider), 0xff000000);
        CombinedDrawable drawable = new CombinedDrawable(shadowDrawable, backgroundDrawable, AndroidUtilities.dp(3), AndroidUtilities.dp(3));
        drawable.setFullsize(true);
        setBackground(drawable);
    }

    public void setSize(int n) {
        content.removeAllViews();
        holders = new Holder[n];
        for (int i = 0; i < n; i++) {
            holders[i] = new Holder();
            content.addView(holders[i].root);
        }
    }


    public void setData(
        int index,
        long date,
        ArrayList<LineViewData> lines,
        boolean animateChanges,
        int formatter,
        float k
    ) {
        int n = holders.length;
        if (animateChanges) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                TransitionSet transition = new TransitionSet();
                transition.
                        addTransition(new Fade(Fade.OUT).setDuration(150)).
                        addTransition(new ChangeBounds().setDuration(150)).
                        addTransition(new Fade(Fade.IN).setDuration(150));
                transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
                TransitionManager.beginDelayedTransition(this, transition);
            }
        }

        if (isTopHourChart) {
            time.setText(String.format(Locale.ENGLISH, "%02d:00", date));
        } else {
            if (useWeek) {
                time.setText(String.format("%s — %s", format4.format(new Date(date)), format3.format(new Date(date + 86400000L * 7))));
            } else {
                time.setText(formatData(new Date(date)));
            }
            if (useHour) hourTime.setText(hourFormat.format(date));
        }

        long sum = 0;

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).enabled) sum += lines.get(i).line.y[index];
        }

        for (int i = 0; i < n; i++) {
            Holder h = holders[i];
            int formatterIndex = i % 2;
            LineViewData l = lines.get(formatter == ChartData.FORMATTER_TON ? i / 2 : i);

            if (!l.enabled) {
                h.root.setVisibility(View.GONE);
            } else {
                if (h.root.getMeasuredHeight() == 0) {
                    h.root.requestLayout();
                }
                h.root.setVisibility(View.VISIBLE);
                h.value.setText(formatWholeNumber(l.line.y[index], formatter, formatterIndex, h.value, k));
                if (formatter == ChartData.FORMATTER_TON) {
                    h.signature.setText(LocaleController.formatString(formatterIndex == 0 ? R.string.MonetizationChartInTON : R.string.MonetizationChartInUSD, l.line.name));
                } else {
                    h.signature.setText(l.line.name);
                }
                if (l.line.colorKey >= 0 && Theme.hasThemeKey(l.line.colorKey)) {
                    h.value.setTextColor(Theme.getColor(l.line.colorKey, resourcesProvider));
                } else {
                    h.value.setTextColor(Theme.getCurrentTheme().isDark() ? l.line.colorDark : l.line.color);
                }
                h.signature.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));

                if (showPercentage && h.percentage != null) {
                    h.percentage.setVisibility(VISIBLE);
                    h.percentage.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                    float v = lines.get(i).line.y[index] / (float) sum;
                    if (v < 0.1f && v != 0f) {
                        h.percentage.setText(String.format(Locale.ENGLISH, "%.1f%s", (100f * v), "%"));
                    } else {
                        h.percentage.setText(String.format(Locale.ENGLISH, "%d%s", Math.round(100 * v), "%"));
                    }
                }
            }
        }

        if (zoomEnabled) {
            canGoZoom = sum > 0;
            chevron.setVisibility(sum > 0 ? View.VISIBLE : View.GONE);
        } else {
            canGoZoom = false;
            chevron.setVisibility(View.GONE);
        }
    }

    private String formatData(Date date) {
        if (useHour) return capitalize(format2.format(date));
        return capitalize(format.format(date)) + capitalize(format2.format(date));
    }

    private String capitalize(String s) {
        if (s.length() > 0)
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        return s;
    }

    private DecimalFormat formatterTON;
    public CharSequence formatWholeNumber(long v, int formatter, int formatterIndex, TextView textView, float k) {
        if (formatter == ChartData.FORMATTER_TON) {
            if (formatterIndex == 0) {
                if (formatterTON == null) {
                    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
                    symbols.setDecimalSeparator('.');
                    formatterTON = new DecimalFormat("#.##", symbols);
                    formatterTON.setMinimumFractionDigits(2);
                    formatterTON.setMaximumFractionDigits(6);
                    formatterTON.setGroupingUsed(false);
                }
                formatterTON.setMaximumFractionDigits(v > 1_000_000_000 ? 2 : 6);
                return ChannelMonetizationLayout.replaceTON("TON " + formatterTON.format(v / 1_000_000_000.), textView.getPaint(), .82f, false);
            } else {
                return "~" + BillingController.getInstance().formatCurrency((long) (v / k), "USD");
            }
        }
        float num_ = v;
        int count = 0;
        if (v < 10_000) {
            return String.format("%d", v);
        }
        while (num_ >= 1_000 && count < AndroidUtilities.numbersSignatureArray.length - 1) {
            num_ /= 1000;
            count++;
        }
        return String.format("%.2f", num_) + AndroidUtilities.numbersSignatureArray[count];
    }


    public void showProgress(boolean show, boolean force) {
        if (show) {
            AndroidUtilities.runOnUIThread(showProgressRunnable, 300);
        } else {
            AndroidUtilities.cancelRunOnUIThread(showProgressRunnable);
            if (force) {
                progressView.setVisibility(View.GONE);
            } else {
                chevron.animate().setDuration(80).alpha(1f).start();
                if (progressView.getVisibility() == View.VISIBLE) {
                    progressView.animate().setDuration(80).alpha(0f).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            progressView.setVisibility(View.GONE);
                        }
                    }).start();
                }
            }
        }
    }

    public void setUseWeek(boolean useWeek) {
        this.useWeek = useWeek;
    }

    class Holder {
        final AnimatedEmojiSpan.TextViewEmojis value;
        final TextView signature;
        TextView percentage;
        final LinearLayout root;

        Holder() {
            root = new LinearLayout(getContext());
            root.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(2), AndroidUtilities.dp(4), AndroidUtilities.dp(2));

            if (showPercentage) {
                root.addView(percentage = new TextView(getContext()));
                percentage.getLayoutParams().width = AndroidUtilities.dp(36);
                percentage.setVisibility(GONE);
                percentage.setTypeface(AndroidUtilities.bold());
                percentage.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            }

            root.addView(signature = new TextView(getContext()), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 20, 0));
//            signature.getLayoutParams().width = showPercentage ? AndroidUtilities.dp(80) : AndroidUtilities.dp(96);
            root.addView(value = new AnimatedEmojiSpan.TextViewEmojis(getContext()), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            signature.setGravity(Gravity.START);
            value.setGravity(Gravity.END);

            value.setTypeface(AndroidUtilities.bold());
            value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
//            value.setMinEms(4);
//            value.setMaxEms(4);
            signature.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        }
    }
}
