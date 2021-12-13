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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class LanguageCell extends FrameLayout {

    private TextView textView;
    private TextView textView2;
    private ImageView checkImage;
    private boolean needDivider;
    private LocaleController.LocaleInfo currentLocale;
    private boolean isDialog;

    public LanguageCell(Context context, boolean dialog) {
        super(context);
        if (Theme.dividerPaint == null) {
            Theme.createCommonResources(context);
        }

        setWillNotDraw(false);
        isDialog = dialog;

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 23 + 48 : 23, (isDialog ? 4 : 7), LocaleController.isRTL ? 23 : 23 + 48, 0));

        textView2 = new TextView(context);
        textView2.setTextColor(Theme.getColor(dialog ? Theme.key_dialogTextGray3 : Theme.key_windowBackgroundWhiteGrayText3));
        textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView2.setLines(1);
        textView2.setMaxLines(1);
        textView2.setSingleLine(true);
        textView2.setEllipsize(TextUtils.TruncateAt.END);
        textView2.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(textView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 23 + 48 : 23, (isDialog ? 25 : 29), LocaleController.isRTL ? 23 : 23 + 48, 0));

        checkImage = new ImageView(context);
        checkImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addedIcon), PorterDuff.Mode.MULTIPLY));
        checkImage.setImageResource(R.drawable.sticker_added);
        addView(checkImage, LayoutHelper.createFrame(19, 14, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 23, 0, 23, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(isDialog ? 50 : 54) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setLanguage(LocaleController.LocaleInfo language, String desc, boolean divider) {
        textView.setText(desc != null ? desc : language.name);
        textView2.setText(language.nameEnglish);
        currentLocale = language;
        needDivider = divider;
    }

    public void setValue(String name, String nameEnglish) {
        textView.setText(name);
        textView2.setText(nameEnglish);
        checkImage.setVisibility(INVISIBLE);
        currentLocale = null;
        needDivider = false;
    }

    public LocaleController.LocaleInfo getCurrentLocale() {
        return currentLocale;
    }

    public void setLanguageSelected(boolean value) {
        checkImage.setVisibility(value ? VISIBLE : INVISIBLE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
