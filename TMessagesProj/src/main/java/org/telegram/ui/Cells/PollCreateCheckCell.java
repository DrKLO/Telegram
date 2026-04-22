/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.SettingsActivity;

@SuppressLint("ViewConstructor")
public class PollCreateCheckCell extends FrameLayout {

    private final TextView textView;
    private final TextView multilineValueTextView;
    private final ImageView imageView;
    private final Switch checkBox;
    private boolean animationsEnabled;
    private boolean divider;
    private final Theme.ResourcesProvider resourcesProvider;

    public PollCreateCheckCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        imageView = new ImageView(context);
        imageView.setFocusable(false);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, LayoutHelper.createFrame(28, 28, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 18, 16, 18, 9));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 66 : 64, 8, LocaleController.isRTL ? 64 : 66, 0));

        multilineValueTextView = new TextView(context);
        multilineValueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        multilineValueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        multilineValueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        multilineValueTextView.setLines(0);
        multilineValueTextView.setMaxLines(0);
        multilineValueTextView.setSingleLine(false);
        multilineValueTextView.setEllipsize(null);
        multilineValueTextView.setLineSpacing(dp(1.66f), 1f);
        addView(multilineValueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 66 : 64, 38 - 7, LocaleController.isRTL ? 64 : 66, 10));

        checkBox = new Switch(context, resourcesProvider);
        checkBox.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        addView(checkBox, LayoutHelper.createFrame(37, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 10, 19, 0));
        checkBox.setFocusable(false);
    }

    public Switch getCheckBox() {
        return checkBox;
    }

    public void setTextAndValueAndIconAndCheck(CharSequence text, CharSequence value, IconBackgroundColors color, int iconResId, boolean checked) {
        textView.setText(text);

        final boolean border = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        SettingsActivity.SettingCell.Background drawable = new SettingsActivity.SettingCell.Background();
        drawable.setColor(color.top, color.bottom);
        drawable.setDrawBorder(border);
        imageView.setBackground(drawable);
        imageView.setImageResource(iconResId);
        checkBox.setChecked(checked, 0, animationsEnabled);
        multilineValueTextView.setText(value);
        checkBox.setContentDescription(text);
    }

    public void setDivider(boolean divider) {
        this.divider = divider;
        invalidate();
    }

    public void setValue(CharSequence value) {
        multilineValueTextView.setText(value);
    }

    public void setChecked(boolean checked) {
        checkBox.setChecked(checked, 0, true);
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    public void setAnimationsEnabled(boolean animationsEnabled) {
        this.animationsEnabled = animationsEnabled;
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (divider) {
            Paint dividerPaint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : Theme.dividerPaint;
            if (dividerPaint == null) {
                dividerPaint = Theme.dividerPaint;
            }
            if (dividerPaint != null) {
                canvas.drawLine(LocaleController.isRTL ? 0 : dp(19), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(19) : 0), getMeasuredHeight() - 1, dividerPaint);
            }
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        StringBuilder sb = new StringBuilder();
        sb.append(textView.getText());
        if (multilineValueTextView != null && !TextUtils.isEmpty(multilineValueTextView.getText())) {
            sb.append("\n");
            sb.append(multilineValueTextView.getText());
        }
        info.setContentDescription(sb);
        info.setCheckable(true);
        info.setChecked(checkBox.isChecked());
    }
}
