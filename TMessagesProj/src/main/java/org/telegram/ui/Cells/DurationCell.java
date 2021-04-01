package org.telegram.ui.Cells;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
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

    private static final int leftRightSpace = AndroidUtilities.dp(21);

    private final TextView titleText = new TextView(getContext());
    private final TextView durationText = new TextView(getContext());

    private int duration;

    public DurationCell(@NonNull Context context) {
        super(context);

        titleText.setEllipsize(TextUtils.TruncateAt.END);
        titleText.setGravity(Gravity.CENTER_VERTICAL);
        titleText.setSingleLine(true);
        titleText.setText(LocaleController.getString("", R.string.AnimationSettingsDuration));
        titleText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        addView(titleText);

        durationText.setGravity(Gravity.CENTER_VERTICAL);
        durationText.setSingleLine(true);
        durationText.setPadding(leftRightSpace, 0, leftRightSpace, 0);
        durationText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        durationText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        addView(durationText);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.dp(48);
        setMeasuredDimension(width, height);

        int wMeasureSpec = MeasureSpec.makeMeasureSpec(width / 2, MeasureSpec.AT_MOST);
        int hMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        durationText.measure(wMeasureSpec, hMeasureSpec);

        wMeasureSpec = MeasureSpec.makeMeasureSpec(width - leftRightSpace - durationText.getMeasuredWidth(), MeasureSpec.AT_MOST);
        hMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        titleText.measure(wMeasureSpec, hMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int durationLeft = getMeasuredWidth() - durationText.getMeasuredWidth();
        durationText.layout(durationLeft, 0, getMeasuredWidth(), getMeasuredHeight());
        titleText.layout(leftRightSpace, 0, durationLeft, getMeasuredHeight());
    }

    public void setDuration(int duration) {
        this.duration = duration;
        durationText.setText(LocaleController.formatString("", R.string.AnimationSettingsDurationMs, duration));
    }

    public View getAnchorView() {
        return durationText;
    }


    public interface OnDurationSelectedListener {

        default void onDurationSelected(@Nullable Object tag, int duration) {}
    }
}
