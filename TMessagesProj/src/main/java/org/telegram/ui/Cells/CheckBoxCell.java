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
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class CheckBoxCell extends FrameLayout {

    public final static int
            TYPE_CHECK_BOX_DEFAULT = 1,
            TYPE_CHECK_BOX_ENTER_PHONE = 2,
            TYPE_CHECK_BOX_UNKNOWN = 3,
            TYPE_CHECK_BOX_ROUND = 4,
            TYPE_CHECK_BOX_URL = 5;

    private final Theme.ResourcesProvider resourcesProvider;
    private final TextView textView;
    private final TextView valueTextView;
    private final View checkBox;
    private CheckBoxSquare checkBoxSquare;
    private CheckBox2 checkBoxRound;
    private View collapsedArrow;

    private final int currentType;
    private final int checkBoxSize;
    private boolean needDivider;
    private boolean isMultiline;

    public CheckBoxCell(Context context, int type) {
        this(context, type, 17, null);
    }

    public CheckBoxCell(Context context, int type, Theme.ResourcesProvider resourcesProvider) {
        this(context, type, 17, resourcesProvider);
    }

    public CheckBoxCell(Context context, int type, int padding, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.currentType = type;

        textView = new TextView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                updateCollapseArrowTranslation();
            }

            @Override
            public void setText(CharSequence text, BufferType type) {
                text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), false);
                super.setText(text, type);
            }
        };
        NotificationCenter.listenEmojiLoading(textView);
        textView.setTag(getThemedColor(type == TYPE_CHECK_BOX_DEFAULT || type == TYPE_CHECK_BOX_URL ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        if (type == TYPE_CHECK_BOX_UNKNOWN) {
            textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 29, 0, 0, 0));
            textView.setPadding(0, 0, 0, AndroidUtilities.dp(3));
        } else {
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            if (type == TYPE_CHECK_BOX_ENTER_PHONE) {
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 8 : 29), 0, (LocaleController.isRTL ? 29 : 8), 0));
            } else {
                int offset = type == TYPE_CHECK_BOX_ROUND ? 56 : 46;
                addView(textView, LayoutHelper.createFrame(type == TYPE_CHECK_BOX_ROUND ? LayoutHelper.WRAP_CONTENT : LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? padding : offset + (padding - 17)), 0, (LocaleController.isRTL ? offset + (padding - 17) : padding), 0));
            }
        }

        valueTextView = new TextView(context);
        valueTextView.setTag(type == TYPE_CHECK_BOX_DEFAULT || type == TYPE_CHECK_BOX_URL ? Theme.key_dialogTextBlue : Theme.key_windowBackgroundWhiteValueText);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, padding, 0, padding, 0));

        if (type == TYPE_CHECK_BOX_ROUND) {
            checkBox = checkBoxRound = new CheckBox2(context, 21, resourcesProvider);
            checkBoxRound.setDrawUnchecked(true);
            checkBoxRound.setChecked(true, false);
            checkBoxRound.setDrawBackgroundAsArc(10);
            checkBoxSize = 21;
            addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : padding), 16, (LocaleController.isRTL ? padding : 0), 0));
        } else {
            checkBox = checkBoxSquare = new CheckBoxSquare(context, type == TYPE_CHECK_BOX_DEFAULT || type == TYPE_CHECK_BOX_URL, resourcesProvider);
            checkBoxSize = 18;
            if (type == TYPE_CHECK_BOX_URL) {
                addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, (LocaleController.isRTL ? 0 : padding), 0, (LocaleController.isRTL ? padding : 0), 0));
            } else if (type == TYPE_CHECK_BOX_UNKNOWN) {
                addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, Gravity.LEFT | Gravity.TOP, 0, 15, 0, 0));
            } else if (type == TYPE_CHECK_BOX_ENTER_PHONE) {
                addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 15, 0, 0));
            } else {
                addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : padding), 16, (LocaleController.isRTL ? padding : 0), 0));
            }
        }
        updateTextColor();
    }

    public void updateTextColor() {
        textView.setTextColor(getThemedColor(currentType == TYPE_CHECK_BOX_DEFAULT || currentType == TYPE_CHECK_BOX_URL ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
        textView.setLinkTextColor(getThemedColor(currentType == TYPE_CHECK_BOX_DEFAULT || currentType == TYPE_CHECK_BOX_URL ? Theme.key_dialogTextLink : Theme.key_windowBackgroundWhiteLinkText));
        valueTextView.setTextColor(getThemedColor(currentType == TYPE_CHECK_BOX_DEFAULT || currentType == TYPE_CHECK_BOX_URL ? Theme.key_dialogTextBlue : Theme.key_windowBackgroundWhiteValueText));
    }

    private View click1Container, click2Container;
    public void setOnSectionsClickListener(OnClickListener onTextClick, OnClickListener onCheckboxClick) {
        if (onTextClick == null) {
            if (click1Container != null) {
                removeView(click1Container);
                click1Container = null;
            }
        } else {
            if (click1Container == null) {
                click1Container = new View(getContext());
                click1Container.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                addView(click1Container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            }
            click1Container.setOnClickListener(onTextClick);
        }

        if (onCheckboxClick == null) {
            if (click2Container != null) {
                removeView(click2Container);
                click2Container = null;
            }
        } else {
            if (click2Container == null) {
                click2Container = new View(getContext());
                addView(click2Container, LayoutHelper.createFrame(56, LayoutHelper.MATCH_PARENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            }
            click2Container.setOnClickListener(onCheckboxClick);
        }
    }

    public void setCollapsed(Boolean collapsed) {
        if (collapsed == null) {
            if (collapsedArrow != null) {
                removeView(collapsedArrow);
                collapsedArrow = null;
            }
        } else {
            if (collapsedArrow == null) {
                collapsedArrow = new View(getContext());
                Drawable drawable = getContext().getResources().getDrawable(R.drawable.arrow_more).mutate();
                drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
                collapsedArrow.setBackground(drawable);
                addView(collapsedArrow, LayoutHelper.createFrame(16, 16, Gravity.CENTER_VERTICAL));
            }

            updateCollapseArrowTranslation();
            collapsedArrow.animate().cancel();
            collapsedArrow.animate().rotation(collapsed ? 0 : 180).setDuration(340).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
    }

    private void updateCollapseArrowTranslation() {
        if (collapsedArrow == null) {
            return;
        }

        float textWidth = 0;
        try {
            textWidth = textView.getMeasuredWidth();
        } catch (Exception e) {}

        float translateX;
        if (LocaleController.isRTL) {
            translateX = textView.getRight() - textWidth - AndroidUtilities.dp(20);
        } else {
            translateX = textView.getLeft() + textWidth + AndroidUtilities.dp(4);
        }
        collapsedArrow.setTranslationX(translateX);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (currentType == TYPE_CHECK_BOX_UNKNOWN) {
            valueTextView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(10), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
            textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(34), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
            checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(checkBoxSize), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(checkBoxSize), MeasureSpec.EXACTLY));

            setMeasuredDimension(textView.getMeasuredWidth() + AndroidUtilities.dp(29), AndroidUtilities.dp(50));
        } else if (isMultiline) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        } else {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50) + (needDivider ? 1 : 0));

            int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(currentType == TYPE_CHECK_BOX_ROUND ? 60 : 34);
            if (valueTextView.getLayoutParams() instanceof MarginLayoutParams) {
                availableWidth -= ((MarginLayoutParams) valueTextView.getLayoutParams()).rightMargin;
            }

            valueTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth / 2, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
            textView.measure(MeasureSpec.makeMeasureSpec(availableWidth - (int) Math.abs(textView.getTranslationX()) - valueTextView.getMeasuredWidth() - AndroidUtilities.dp(8), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
            checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(checkBoxSize), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(checkBoxSize), MeasureSpec.EXACTLY));
        }

        if (click1Container != null) {
            MarginLayoutParams margin = (MarginLayoutParams) click1Container.getLayoutParams();
            click1Container.measure(MeasureSpec.makeMeasureSpec(width - margin.leftMargin - margin.rightMargin, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
        }
        if (click2Container != null) {
            click2Container.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
        }
        if (collapsedArrow != null) {
            collapsedArrow.measure(
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(16), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(16), MeasureSpec.EXACTLY)
            );
        }
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setText(CharSequence text, String value, boolean checked, boolean divider) {
        setText(text, value, checked, divider, false);
    }

    public void setText(CharSequence text, String value, boolean checked, boolean divider, boolean animated) {
        textView.setText(text);
        if (checkBoxRound != null) {
            checkBoxRound.setChecked(checked, animated);
        } else {
            checkBoxSquare.setChecked(checked, animated);
        }
        valueTextView.setText(value);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setPad(int pad) {
        int offset = AndroidUtilities.dp(pad * 40 * (LocaleController.isRTL ? -1 : 1));
        if (checkBox != null) {
            checkBox.setTranslationX(offset);
        }
        textView.setTranslationX(offset);
        if (click1Container != null) {
            click1Container.setTranslationX(offset);
        }
        if (click2Container != null) {
            click2Container.setTranslationX(offset);
        }
    }

    public void setNeedDivider(boolean needDivider) {
        this.needDivider = needDivider;
    }

    public void setMultiline(boolean value) {
        isMultiline = value;
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        LayoutParams layoutParams1 = (LayoutParams) checkBox.getLayoutParams();
        if (isMultiline) {
            textView.setLines(0);
            textView.setMaxLines(0);
            textView.setSingleLine(false);
            textView.setEllipsize(null);
            if (currentType != TYPE_CHECK_BOX_URL) {
                textView.setPadding(0, 0, 0, AndroidUtilities.dp(5));
                layoutParams.height = LayoutParams.WRAP_CONTENT;
                layoutParams.topMargin = AndroidUtilities.dp(10);
                layoutParams1.topMargin = AndroidUtilities.dp(12);
            }
        } else {
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setPadding(0, 0, 0, 0);

            layoutParams.height = LayoutParams.MATCH_PARENT;
            layoutParams.topMargin = 0;
            layoutParams1.topMargin = AndroidUtilities.dp(15);
        }
        textView.setLayoutParams(layoutParams);
        checkBox.setLayoutParams(layoutParams1);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        textView.setAlpha(enabled ? 1.0f : 0.5f);
        valueTextView.setAlpha(enabled ? 1.0f : 0.5f);
        checkBox.setAlpha(enabled ? 1.0f : 0.5f);
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBoxRound != null) {
            checkBoxRound.setChecked(checked, animated);
        } else {
            checkBoxSquare.setChecked(checked, animated);
        }
    }

    public boolean isChecked() {
        if (checkBoxRound != null) {
            return checkBoxRound.isChecked();
        } else {
            return checkBoxSquare.isChecked();
        }
    }

    public TextView getTextView() {
        return textView;
    }

    public TextView getValueTextView() {
        return valueTextView;
    }

    public View getCheckBoxView() {
        return checkBox;
    }

    public void setCheckBoxColor(int background, int background1, int check) {
        if (checkBoxRound != null) {
            checkBoxRound.setColor(background, background, check);
        }
    }

    public CheckBox2 getCheckBoxRound() {
        return checkBoxRound;
    }

    public void setSquareCheckBoxColor(int uncheckedColor, int checkedColor, int checkColor) {
        if (checkBoxSquare != null) {
            checkBoxSquare.setColors(uncheckedColor, checkedColor, checkColor);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            int offset = AndroidUtilities.dp(currentType == TYPE_CHECK_BOX_ROUND ? 60 : 20) + (int) Math.abs(textView.getTranslationX());
            canvas.drawLine(LocaleController.isRTL ? 0 : offset, getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? offset : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.CheckBox");
        info.setCheckable(true);
        info.setChecked(isChecked());
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void setIcon(int icon) {
        checkBoxRound.setIcon(icon);
    }

    public boolean hasIcon() {
        return checkBoxRound.hasIcon();
    }
}
