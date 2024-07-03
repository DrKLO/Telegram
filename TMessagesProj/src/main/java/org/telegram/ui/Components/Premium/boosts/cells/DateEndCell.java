package org.telegram.ui.Components.Premium.boosts.cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Date;

@SuppressLint("ViewConstructor")
public class DateEndCell extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final SimpleTextView titleTextView;
    private final SimpleTextView timeTextView;
    private long selectedTime;

    public DateEndCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        titleTextView = new SimpleTextView(context);
        titleTextView.setTextSize(16);
        titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(titleTextView);

        timeTextView = new SimpleTextView(context);
        timeTextView.setTextSize(16);
        timeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        timeTextView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        addView(timeTextView);
        titleTextView.setText(LocaleController.formatString("BoostingDateAndTime", R.string.BoostingDateAndTime));

        titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 21, 0, LocaleController.isRTL ? 21 : 0, 0));
        timeTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? 21 : 0, 0, LocaleController.isRTL ? 0 : 21, 0));
        setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
    }

    public void setDate(long time) {
        selectedTime = time;
        Date date = new Date(time);
        String monthTxt = LocaleController.getInstance().getFormatterDayMonth().format(date);
        String timeTxt = LocaleController.getInstance().getFormatterDay().format(date);
        timeTextView.setText(LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, monthTxt, timeTxt));
    }

    public long getSelectedTime() {
        return selectedTime;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY)
        );
    }
}
