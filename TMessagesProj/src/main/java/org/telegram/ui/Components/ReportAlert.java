/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

public class ReportAlert extends BottomSheet {

    private BottomSheetCell clearButton;
    private EditTextBoldCursor editText;

    public static class BottomSheetCell extends FrameLayout {

        private View background;
        private TextView textView;
        private LinearLayout linearLayout;

        public BottomSheetCell(Context context, Theme.ResourcesProvider resourcesProvider) {
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
            textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
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

    public ReportAlert(final Context context, int type, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, resourcesProvider);
        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        setCustomView(scrollView);

        FrameLayout frameLayout = new FrameLayout(context);
        scrollView.addView(frameLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setAnimation(R.raw.report_police, 120, 120);
        imageView.playAnimation();
        frameLayout.addView(imageView, LayoutHelper.createFrame(160, 160, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 14, 17, 0));

        TextView percentTextView = new TextView(context);
        percentTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        percentTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        percentTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        if (type == AlertsCreator.REPORT_TYPE_SPAM) {
            percentTextView.setText(LocaleController.getString("ReportTitleSpam", R.string.ReportTitleSpam));
        } else if (type == AlertsCreator.REPORT_TYPE_FAKE_ACCOUNT) {
            percentTextView.setText(LocaleController.getString("ReportTitleFake", R.string.ReportTitleFake));
        } else if (type == AlertsCreator.REPORT_TYPE_VIOLENCE) {
            percentTextView.setText(LocaleController.getString("ReportTitleViolence", R.string.ReportTitleViolence));
        } else if (type == AlertsCreator.REPORT_TYPE_CHILD_ABUSE) {
            percentTextView.setText(LocaleController.getString("ReportTitleChild", R.string.ReportTitleChild));
        } else if (type == AlertsCreator.REPORT_TYPE_PORNOGRAPHY) {
            percentTextView.setText(LocaleController.getString("ReportTitlePornography", R.string.ReportTitlePornography));
        } else if (type == AlertsCreator.REPORT_TYPE_OTHER) {
            percentTextView.setText(LocaleController.getString("ReportChat", R.string.ReportChat));
        }
        frameLayout.addView(percentTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 17, 197, 17, 0));

        TextView infoTextView = new TextView(context);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        infoTextView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
        infoTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        infoTextView.setText(LocaleController.getString("ReportInfo", R.string.ReportInfo));
        frameLayout.addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 30, 235, 30, 44));

        editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setBackgroundDrawable(null);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
        editText.setMaxLines(1);
        editText.setLines(1);
        editText.setPadding(0, 0, 0, 0);
        editText.setSingleLine(true);
        editText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setHint(LocaleController.getString("ReportHint", R.string.ReportHint));
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                clearButton.background.callOnClick();
                return true;
            }
            return false;
        });
        frameLayout.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 17, 305, 17, 0));

        clearButton = new BottomSheetCell(context, resourcesProvider);
        clearButton.setBackground(null);
        clearButton.setText(LocaleController.getString("ReportSend", R.string.ReportSend));
        clearButton.background.setOnClickListener(v -> {
            AndroidUtilities.hideKeyboard(editText);
            onSend(type, editText.getText().toString());
            dismiss();
        });
        frameLayout.addView(clearButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 357, 0, 0));
        smoothKeyboardAnimationEnabled = true;
    }

    protected void onSend(int type, String message) {

    }
}
