/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.CarouselView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.List;

public class GraySectionCell extends FrameLayout {

    private AnimatedEmojiSpan.TextViewEmojis textView;
    private AnimatedTextView rightTextView;
    private final Theme.ResourcesProvider resourcesProvider;
    private int layerHeight = 32;

    public GraySectionCell(Context context) {
        this(context, null);
    }

    public GraySectionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setBackgroundColor(getThemedColor(Theme.key_graySection));

        textView = new AnimatedEmojiSpan.TextViewEmojis(getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        textView.setTextColor(getThemedColor(Theme.key_graySectionText));
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 16, 0, 16, 0));

        rightTextView = new AnimatedTextView(getContext(), true, true, true) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };
        rightTextView.setPadding(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(2), 0);
        rightTextView.setAnimationProperties(.9f, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        rightTextView.setTextSize(AndroidUtilities.dp(14));
        rightTextView.setTextColor(getThemedColor(Theme.key_graySectionText));
        rightTextView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        addView(rightTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 16, 0, 16, 0));

        ViewCompat.setAccessibilityHeading(this, true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(layerHeight), MeasureSpec.EXACTLY));
    }

    public void setLayerHeight(int dp){
        layerHeight = dp;
        requestLayout();
    }

    public void setTextColor(int key) {
        int color = getThemedColor(key);
        textView.setTextColor(color);
        rightTextView.setTextColor(color);
    }

    public CharSequence getText() {
        return textView.getText();
    }

    public void setText(CharSequence text) {
        textView.setText(text);
        rightTextView.setVisibility(GONE);
        rightTextView.setOnClickListener(null);
    }

    public void setText(String left, String right, OnClickListener onClickListener) {
        textView.setText(left);
        rightTextView.setText(right, false);
        rightTextView.setOnClickListener(onClickListener);
        rightTextView.setVisibility(VISIBLE);
    }

    public void setRightText(String right) {
        setRightText(right, true);
    }

    public void setRightText(CharSequence right, boolean moveDown) {
        rightTextView.setText(right, true, moveDown);
        rightTextView.setVisibility(VISIBLE);
    }

    public void setRightText(CharSequence right, OnClickListener onClickListener) {
        rightTextView.setText(right, false);
        rightTextView.setOnClickListener(onClickListener);
        rightTextView.setVisibility(VISIBLE);
    }

    public void setRightText(CharSequence right, boolean moveDown, OnClickListener onClickListener) {
        rightTextView.setText(right, true, moveDown);
        rightTextView.setOnClickListener(onClickListener);
        rightTextView.setVisibility(VISIBLE);
    }

    public static void createThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView listView) {
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"rightTextView"}, null, null, null, Theme.key_graySectionText));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection));
    }

    public TextView getTextView() {
        return textView;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
