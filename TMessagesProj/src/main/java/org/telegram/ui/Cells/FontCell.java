package org.telegram.ui.Cells;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.FontType;

public class FontCell extends FrameLayout {

    private TextView fontNameTextView;
    private TextView fontSampletextView;
    private ImageView checkImage;
    private boolean needDivider;
    private boolean isDialog;

    public FontCell(Context context, boolean dialog) {
        super(context);
        if (Theme.dividerPaint == null) {
            Theme.createCommonResources(context);
        }

        setWillNotDraw(false);
        isDialog = dialog;

        fontNameTextView = new TextView(context);
        fontNameTextView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
        fontNameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        fontNameTextView.setLines(1);
        fontNameTextView.setMaxLines(1);
        fontNameTextView.setSingleLine(true);
        fontNameTextView.setEllipsize(TextUtils.TruncateAt.END);
        fontNameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(fontNameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 23 + 48 : 23, (isDialog ? 4 : 7), LocaleController.isRTL ? 23 : 23 + 48, 0));

        fontSampletextView = new TextView(context);
        fontSampletextView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogTextGray3 : Theme.key_windowBackgroundWhiteGrayText3));
        fontSampletextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        fontSampletextView.setLines(1);
        fontSampletextView.setMaxLines(1);
        fontSampletextView.setSingleLine(true);
        fontSampletextView.setEllipsize(TextUtils.TruncateAt.END);
        fontSampletextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(fontSampletextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 23 + 48 : 23, (isDialog ? 25 : 29), LocaleController.isRTL ? 23 : 23 + 48, 0));

        checkImage = new ImageView(context);
        checkImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addedIcon), PorterDuff.Mode.MULTIPLY));
        checkImage.setImageResource(R.drawable.sticker_added);
        addView(checkImage, LayoutHelper.createFrame(19, 14, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 23, 0, 23, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(isDialog ? 50 : 54) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setFont(FontType font, boolean divider) {
        fontNameTextView.setText(font.fontName);
        fontSampletextView.setText(font.fontSampleText);
        fontSampletextView.setTypeface(font.font);
        checkImage.setVisibility(INVISIBLE);
        needDivider = divider;
    }

    public void setValue(FontType fontType) {
        String previouslySelectedFont = LocaleController.getInstance().getSelectedFont();
        if (previouslySelectedFont == null) {
            if (fontType.fontName.equals(LocaleController.getString("Default_font", R.string.Default))) {
                checkImage.setVisibility(VISIBLE);
            } else {
                checkImage.setVisibility(INVISIBLE);
            }
        } else {
            if (!previouslySelectedFont.equals(LocaleController.getString("Default_font", R.string.Default))) {
                if (fontType.fontPath.equals(previouslySelectedFont)) {
                    checkImage.setVisibility(VISIBLE);
                } else {
                    checkImage.setVisibility(INVISIBLE);
                }
            } else {
                if (fontType.fontName.equals(LocaleController.getString("Default_font", R.string.Default))) {
                    checkImage.setVisibility(VISIBLE);
                } else {
                    checkImage.setVisibility(INVISIBLE);
                }
            }
        }
    }

    public void setFontSelected(boolean value) {
        checkImage.setVisibility(value ? VISIBLE : INVISIBLE);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
