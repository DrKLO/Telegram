/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;

public class LanguageCell extends FrameLayout {

    private RadioButton radioButton;
    private TextView textView;
    public TextView textView2;
    private boolean needDivider;
    private LocaleController.LocaleInfo currentLocale;
    private int marginStartDp = 62, marginEndDp = 23;

    public LanguageCell(Context context) {
        super(context);
        if (Theme.dividerPaint == null) {
            Theme.createCommonResources(context);
        }

        setWillNotDraw(false);

        radioButton = new RadioButton(context);
        radioButton.setSize(AndroidUtilities.dp(20));
        radioButton.setColor(Theme.getColor(Theme.key_dialogRadioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
        addView(radioButton, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, (LocaleController.isRTL ? 0 : 20), 0, (LocaleController.isRTL ? 20 : 0), 0));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? marginEndDp : marginStartDp, 0, LocaleController.isRTL ? marginStartDp : marginEndDp, 17));

        textView2 = new TextView(context);
        textView2.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView2.setSingleLine(true);
        textView2.setEllipsize(TextUtils.TruncateAt.END);
        textView2.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? marginEndDp : marginStartDp, 20, LocaleController.isRTL ? marginStartDp : marginEndDp, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setLanguage(LocaleController.LocaleInfo language, String desc, boolean divider) {
        textView.setText(desc != null ? desc : language.name);
        textView2.setText(language.nameEnglish);
        currentLocale = language;
        needDivider = divider;
    }

    public void setValue(CharSequence name, CharSequence nameEnglish) {
        textView.setText(name);
        textView2.setText(nameEnglish);
        radioButton.setChecked(false, false);
        currentLocale = null;
        needDivider = false;
    }

    public LocaleController.LocaleInfo getCurrentLocale() {
        return currentLocale;
    }

    public void setLanguageSelected(boolean value, boolean animated) {
        radioButton.setChecked(value, animated);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(marginStartDp - 3), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(marginStartDp - 3) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
