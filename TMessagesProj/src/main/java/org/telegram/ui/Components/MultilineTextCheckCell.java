package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

public class MultilineTextCheckCell extends FrameLayout {

    private LinearLayout textLayout;
    private TextView titleTextView;
    private TextView subtitleTextView;
    private Switch checkBox;

    public MultilineTextCheckCell(Context context) {
        this(context, null);
    }

    public MultilineTextCheckCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 70 : 22, 0, LocaleController.isRTL ? 22 : 70, 0));

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        textLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleTextView = new TextView(context);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        subtitleTextView.setEllipsize(TextUtils.TruncateAt.END);
        textLayout.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 4, 0, 1));

        checkBox = new Switch(context, resourcesProvider);
        checkBox.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        addView(checkBox, LayoutHelper.createFrame(37, 20, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.getSize(heightMeasureSpec) < AndroidUtilities.dp(50) ?
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY) :
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.AT_MOST)
        );
    }

    public void setChecked(boolean checked) {
        checkBox.setChecked(checked, true);
    }

    private boolean needDivivider;

    public void setTextAndCheck(CharSequence title, boolean checked, boolean divider) {
        titleTextView.setText(title);
        subtitleTextView.setVisibility(View.GONE);
        checkBox.setChecked(checked, false);
        setWillNotDraw(!(needDivivider = divider));
    }

    public void setTextAndSubtextAndCheck(CharSequence title, CharSequence subtitle, boolean checked, boolean divider) {
        titleTextView.setText(title);
        subtitleTextView.setVisibility(View.VISIBLE);
        subtitleTextView.setText(subtitle);
        checkBox.setChecked(checked, false);
        setWillNotDraw(!(needDivivider = divider));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (needDivivider) {
            canvas.drawRect(
                LocaleController.isRTL ? 0 : AndroidUtilities.dp(22),
                getMeasuredHeight() - 1,
                LocaleController.isRTL ? AndroidUtilities.dp(22) : 0,
                getMeasuredHeight(),
                Theme.dividerPaint
            );
        }
    }
}
