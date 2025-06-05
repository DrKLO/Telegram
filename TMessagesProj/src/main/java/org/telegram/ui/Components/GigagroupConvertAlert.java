/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

public class GigagroupConvertAlert extends BottomSheet {

    public static class BottomSheetCell extends FrameLayout {

        private View background;
        private TextView textView;
        private LinearLayout linearLayout;

        public BottomSheetCell(Context context) {
            super(context);

            background = new View(context);
            background.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 4));
            addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 16, 16, 16, 16));

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTypeface(AndroidUtilities.bold());
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY));
        }

        public void setText(CharSequence text) {
            textView.setText(text);
        }
    }

    public GigagroupConvertAlert(final Context context, BaseFragment parentFragment) {
        super(context, true);
        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        setCustomView(linearLayout);

        RLottieImageView lottieImageView = new RLottieImageView(context);
        lottieImageView.setAutoRepeat(true);
        lottieImageView.setAnimation(R.raw.utyan_gigagroup, 120, 120);
        lottieImageView.playAnimation();
        linearLayout.addView(lottieImageView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 30, 17, 0));

        TextView percentTextView = new TextView(context);
        percentTextView.setTypeface(AndroidUtilities.bold());
        percentTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        percentTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        percentTextView.setText(LocaleController.getString(R.string.GigagroupConvertTitle));
        linearLayout.addView(percentTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 17, 18, 17, 0));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(container, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        for (int a = 0; a < 3; a++) {
            LinearLayout linearLayout2 = new LinearLayout(context);
            linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
            container.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 8, 0, 0));

            ImageView imageView = new ImageView(context);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextGray3), PorterDuff.Mode.MULTIPLY));
            imageView.setImageResource(R.drawable.list_circle);

            TextView textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
            textView.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            textView.setMaxWidth(AndroidUtilities.dp(260));

            switch (a) {
                case 0:
                    textView.setText(LocaleController.getString(R.string.GigagroupConvertInfo1));
                    break;
                case 1:
                    textView.setText(LocaleController.getString(R.string.GigagroupConvertInfo2));
                    break;
                case 2:
                    textView.setText(LocaleController.getString(R.string.GigagroupConvertInfo3));
                    break;
            }

            if (LocaleController.isRTL) {
                linearLayout2.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                linearLayout2.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 7, 0, 0));
            } else {
                linearLayout2.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 8, 0));
                linearLayout2.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            }
        }

        BottomSheetCell clearButton = new BottomSheetCell(context);
        clearButton.setBackground(null);
        clearButton.setText(LocaleController.getString(R.string.GigagroupConvertProcessButton));
        clearButton.background.setOnClickListener(v -> {
            dismiss();
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(LocaleController.getString(R.string.GigagroupConvertAlertTitle));
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.GigagroupConvertAlertText)));
            builder.setPositiveButton(LocaleController.getString(R.string.GigagroupConvertAlertConver), (dialogInterface, i) -> onCovert());
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            parentFragment.showDialog(builder.create());
        });
        linearLayout.addView(clearButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 29, 0, 0));

        TextView cancelTextView = new TextView(context);
        cancelTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        cancelTextView.setText(LocaleController.getString(R.string.GigagroupConvertCancelButton));
        cancelTextView.setGravity(Gravity.CENTER);
        linearLayout.addView(cancelTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 17, 0, 17, 16));
        cancelTextView.setOnClickListener(v -> {
            onCancel();
            dismiss();
        });
    }

    protected void onCovert() {

    }

    protected void onCancel() {

    }
}
