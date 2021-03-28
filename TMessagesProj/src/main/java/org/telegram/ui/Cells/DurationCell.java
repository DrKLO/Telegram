package org.telegram.ui.Cells;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class DurationCell extends ViewGroup {

    private static final int[] durations = new int[] { 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1500, 2000, 3000 };
    private static final int leftRightSpace = AndroidUtilities.dp(21);

    private final TextView titleText = new TextView(getContext());
    private final TextView durationText = new TextView(getContext());

    @Nullable
    private OnDurationSelectedListener durationListener;
    private int duration;

    public DurationCell(@NonNull Context context) {
        super(context);

        titleText.setEllipsize(TextUtils.TruncateAt.END);
        titleText.setSingleLine(true);
        titleText.setText(LocaleController.getString("", R.string.AnimationSettingsDuration));
        titleText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        addView(titleText);

        durationText.setSingleLine(true);
        durationText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        durationText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        durationText.setOnClickListener(v -> {
            // TODO agolokoz: use nice background
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1);
            for (int value : durations) {
                adapter.add(LocaleController.formatString("", R.string.AnimationSettingsDurationMs, value));
            }
            ListPopupWindow window = new ListPopupWindow(getContext());
            window.setAdapter(adapter);
            window.setAnchorView(durationText);
            window.setOnItemClickListener((parent, view, position, id) -> {
                window.dismiss();
                int selectedDuration = durations[position];
                setDuration(selectedDuration);
                if (durationListener != null) {
                    durationListener.onDurationSelected(getTag(), selectedDuration);
                }
            });
            window.show();
        });
        addView(durationText);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.dp(36);
        setMeasuredDimension(width, height);

        int wMeasureSpec = MeasureSpec.makeMeasureSpec(width / 2 - leftRightSpace * 2, MeasureSpec.AT_MOST);
        int hMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
        durationText.measure(wMeasureSpec, hMeasureSpec);

        wMeasureSpec = MeasureSpec.makeMeasureSpec(width - leftRightSpace - durationText.getMeasuredWidth(), MeasureSpec.AT_MOST);
        hMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
        titleText.measure(wMeasureSpec, hMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int durationLeft = getMeasuredWidth() - leftRightSpace - durationText.getMeasuredWidth();
        durationText.layout(durationLeft, 0, getMeasuredWidth(), getMeasuredHeight());
        titleText.layout(leftRightSpace, 0, durationLeft, getMeasuredHeight());
    }

    public void setDurationListener(@Nullable OnDurationSelectedListener durationListener) {
        this.durationListener = durationListener;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
        durationText.setText(LocaleController.formatString("", R.string.AnimationSettingsDurationMs, duration));
    }


    public interface OnDurationSelectedListener {

        void onDurationSelected(@Nullable Object tag, int duration);
    }
}
