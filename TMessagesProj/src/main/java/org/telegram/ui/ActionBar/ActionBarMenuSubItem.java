package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;

public class ActionBarMenuSubItem extends FrameLayout {

    private TextView textView;
    private TextView subtextView;
    private ImageView imageView;
    private CheckBox2 checkView;
    private ImageView rightIcon;

    private int textColor;
    private int iconColor;
    private int selectorColor;

    boolean top;
    boolean bottom;

    private int itemHeight = 48;
    private final Theme.ResourcesProvider resourcesProvider;

    public ActionBarMenuSubItem(Context context, boolean top, boolean bottom) {
        this(context, false, top, bottom);
    }

    public ActionBarMenuSubItem(Context context, boolean needCheck, boolean top, boolean bottom) {
        this(context, needCheck, top, bottom, null);
    }

    public ActionBarMenuSubItem(Context context, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        this(context, false, top, bottom, resourcesProvider);
    }

    public ActionBarMenuSubItem(Context context, boolean needCheck, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        this.top = top;
        this.bottom = bottom;

        textColor = getThemedColor(Theme.key_actionBarDefaultSubmenuItem);
        iconColor = getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon);
        selectorColor = getThemedColor(Theme.key_dialogButtonSelector);

        updateBackground();
        setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

        textView = new TextView(context);
        textView.setLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.LEFT);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(textColor);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));

        if (needCheck) {
            checkView = new CheckBox2(context, 26, resourcesProvider);
            checkView.setDrawUnchecked(false);
            checkView.setColor(null, null, Theme.key_radioBackgroundChecked);
            checkView.setDrawBackgroundAsArc(-1);
            addView(checkView, LayoutHelper.createFrame(26, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(itemHeight), View.MeasureSpec.EXACTLY));
    }

    public void setItemHeight(int itemHeight) {
        this.itemHeight = itemHeight;
    }

    public void setChecked(boolean checked) {
        if (checkView == null) {
            return;
        }
        checkView.setChecked(checked, true);
    }

    public void setCheckColor(String colorKey) {
        checkView.setColor(null, null, colorKey);
    }

    public void setRightIcon(int icon) {
        if (rightIcon == null) {
            rightIcon = new ImageView(getContext());
            rightIcon.setScaleType(ImageView.ScaleType.CENTER);
            rightIcon.setColorFilter(textColor, PorterDuff.Mode.MULTIPLY);
            if (LocaleController.isRTL) {
                rightIcon.setScaleX(-1);
            }
            addView(rightIcon, LayoutHelper.createFrame(24, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));
        }
        setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 8 : 18), 0, AndroidUtilities.dp(LocaleController.isRTL ? 18 : 8), 0);
        rightIcon.setImageResource(icon);
    }

    public void setTextAndIcon(CharSequence text, int icon) {
        setTextAndIcon(text, icon, null);
    }

    public void setMultiline() {
        textView.setLines(2);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setSingleLine(false);
    }

    public void setTextAndIcon(CharSequence text, int icon, Drawable iconDrawable) {
        textView.setText(text);
        if (icon != 0 || iconDrawable != null || checkView != null) {
            if (iconDrawable != null) {
                imageView.setImageDrawable(iconDrawable);
            } else {
                imageView.setImageResource(icon);
            }
            imageView.setVisibility(VISIBLE);
            textView.setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(43), 0, LocaleController.isRTL ? AndroidUtilities.dp(43) : 0, 0);
        } else {
            imageView.setVisibility(INVISIBLE);
            textView.setPadding(0, 0, 0, 0);
        }
    }

    public ActionBarMenuSubItem setColors(int textColor, int iconColor) {
        setTextColor(textColor);
        setIconColor(iconColor);
        return this;
    }

    public void setTextColor(int textColor) {
        if (this.textColor != textColor) {
            textView.setTextColor(this.textColor = textColor);
        }
    }

    public void setIconColor(int iconColor) {
        if (this.iconColor != iconColor) {
            imageView.setColorFilter(new PorterDuffColorFilter(this.iconColor = iconColor, PorterDuff.Mode.MULTIPLY));
        }
    }

    public void setIcon(int resId) {
        imageView.setImageResource(resId);
    }

    public void setText(String text) {
        textView.setText(text);
    }

    public void setSubtextColor(int color) {
        subtextView.setTextColor(color);
    }

    public void setSubtext(String text) {
        if (subtextView == null) {
            subtextView = new TextView(getContext());
            subtextView.setLines(1);
            subtextView.setSingleLine(true);
            subtextView.setGravity(Gravity.LEFT);
            subtextView.setEllipsize(TextUtils.TruncateAt.END);
            subtextView.setTextColor(0xff7C8286);
            subtextView.setVisibility(GONE);
            subtextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtextView.setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(43), 0, LocaleController.isRTL ? AndroidUtilities.dp(43) : 0, 0);
            addView(subtextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 0, 10, 0, 0));
        }
        boolean visible = !TextUtils.isEmpty(text);
        boolean oldVisible = subtextView.getVisibility() == VISIBLE;
        if (visible != oldVisible) {
            subtextView.setVisibility(visible ? VISIBLE : GONE);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) textView.getLayoutParams();
            layoutParams.bottomMargin = visible ? AndroidUtilities.dp(10) : 0;
            textView.setLayoutParams(layoutParams);
        }
        subtextView.setText(text);
    }

    public TextView getTextView() {
        return textView;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void setSelectorColor(int selectorColor) {
        if (this.selectorColor != selectorColor) {
            this.selectorColor = selectorColor;
            updateBackground();
        }
    }

    public void updateSelectorBackground(boolean top, boolean bottom) {
        if (this.top == top && this.bottom == bottom) {
            return;
        }
        this.top = top;
        this.bottom = bottom;
        updateBackground();
    }

    void updateBackground() {
        int topBackgroundRadius = top ? 6 : 0;
        int bottomBackgroundRadius = bottom ? 6 : 0;
        setBackground(Theme.createRadSelectorDrawable(selectorColor, topBackgroundRadius, bottomBackgroundRadius));
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    public CheckBox2 getCheckView() {
        return checkView;
    }
}
