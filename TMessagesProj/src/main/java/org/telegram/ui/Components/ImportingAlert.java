/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

public class ImportingAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private TextView[] importCountTextView = new TextView[2];
    private TextView percentTextView;
    private LineProgressView lineProgressView;
    private ChatActivity parentFragment;
    private RLottieImageView imageView;
    private BottomSheetCell cell;
    private boolean compteled;
    private RLottieDrawable completedDrawable;
    private TextView[] infoTextView = new TextView[2];

    public static class BottomSheetCell extends FrameLayout {

        private View background;
        private TextView textView;
        private RLottieImageView imageView;
        private LinearLayout linearLayout;

        public BottomSheetCell(Context context) {
            super(context);

            background = new View(context);
            background.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
            addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 16, 16, 16, 16));

            linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            imageView = new RLottieImageView(context);
            imageView.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(20), Theme.getColor(Theme.key_featuredStickers_buttonText)));
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.MULTIPLY));
            imageView.setAnimation(R.raw.import_check, 26, 26);
            imageView.setScaleX(0.8f);
            imageView.setScaleY(0.8f);
            linearLayout.addView(imageView, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL));

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY));
        }

        public void setTextColor(int color) {
            textView.setTextColor(color);
        }

        public void setGravity(int gravity) {
            textView.setGravity(gravity);
        }

        public void setText(CharSequence text) {
            textView.setText(text);
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final Runnable onFinishCallback = () -> {
        if (compteled) {
            imageView.getAnimatedDrawable().setAutoRepeat(0);
            imageView.setAnimation(completedDrawable);
            imageView.playAnimation();
        }
    };

    public ImportingAlert(final Context context, ChatActivity chatActivity) {
        super(context, false);
        setApplyBottomPadding(false);
        setApplyTopPadding(false);
        parentFragment = chatActivity;

        FrameLayout frameLayout = new FrameLayout(context);
        setCustomView(frameLayout);

        TextView textView = new TextView(context);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setText(LocaleController.getString("ImportImportingTitle", R.string.ImportImportingTitle));
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 17, 20, 17, 0));

        completedDrawable = new RLottieDrawable(R.raw.import_finish, "" + R.raw.import_finish, AndroidUtilities.dp(120), AndroidUtilities.dp(120), false, null);
        completedDrawable.setAllowDecodeSingleFrame(true);

        imageView = new RLottieImageView(context);
        imageView.setAutoRepeat(true);
        imageView.setAnimation(R.raw.import_loop, 120, 120);
        imageView.playAnimation();
        frameLayout.addView(imageView, LayoutHelper.createFrame(160, 160, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 79, 17, 0));
        imageView.getAnimatedDrawable().setOnFinishCallback(onFinishCallback, 178);

        SendMessagesHelper.ImportingHistory importingHistory = parentFragment.getSendMessagesHelper().getImportingHistory(parentFragment.getDialogId());

        percentTextView = new TextView(context);
        percentTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        percentTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        percentTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        percentTextView.setText(String.format("%d%%", importingHistory.uploadProgress));
        frameLayout.addView(percentTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 17, 262, 17, 0));

        lineProgressView = new LineProgressView(getContext());
        lineProgressView.setProgress(importingHistory.uploadProgress / 100.0f, false);
        lineProgressView.setProgressColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        lineProgressView.setBackColor(Theme.getColor(Theme.key_dialogLineProgressBackground));
        frameLayout.addView(lineProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 4, Gravity.LEFT | Gravity.TOP, 50, 307, 50, 0));

        cell = new BottomSheetCell(context);
        cell.setBackground(null);
        cell.setText(LocaleController.getString("ImportDone", R.string.ImportDone));
        cell.setVisibility(View.INVISIBLE);
        cell.background.setOnClickListener(v -> dismiss());
        cell.background.setPivotY(AndroidUtilities.dp(48));
        cell.background.setScaleY(0.04f);
        frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 34, 247, 34, 0));

        for (int a = 0; a < 2; a++) {
            importCountTextView[a] = new TextView(context);
            importCountTextView[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            importCountTextView[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            importCountTextView[a].setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            frameLayout.addView(importCountTextView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 17, 340, 17, 0));

            infoTextView[a] = new TextView(context);
            infoTextView[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            infoTextView[a].setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
            infoTextView[a].setGravity(Gravity.CENTER_HORIZONTAL);
            frameLayout.addView(infoTextView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 30, 368, 30, 44));

            if (a == 0) {
                infoTextView[a].setText(LocaleController.getString("ImportImportingInfo", R.string.ImportImportingInfo));
                importCountTextView[a].setText(LocaleController.formatString("ImportCount", R.string.ImportCount, AndroidUtilities.formatFileSize(importingHistory.getUploadedCount()), AndroidUtilities.formatFileSize(importingHistory.getTotalCount())));
            } else {
                infoTextView[a].setText(LocaleController.getString("ImportDoneInfo", R.string.ImportDoneInfo));
                importCountTextView[a].setText(LocaleController.getString("ImportDoneTitle", R.string.ImportDoneTitle));

                infoTextView[a].setAlpha(0.0f);
                infoTextView[a].setTranslationY(AndroidUtilities.dp(10));
                importCountTextView[a].setAlpha(0.0f);
                importCountTextView[a].setTranslationY(AndroidUtilities.dp(10));
            }
        }

        parentFragment.getNotificationCenter().addObserver(this, NotificationCenter.historyImportProgressChanged);
    }

    public void setCompteled() {
        compteled = true;
        imageView.setAutoRepeat(false);
        cell.setVisibility(View.VISIBLE);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(250);
        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(percentTextView, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(percentTextView, View.TRANSLATION_Y, -AndroidUtilities.dp(10)),
                ObjectAnimator.ofFloat(infoTextView[0], View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(infoTextView[0], View.TRANSLATION_Y, -AndroidUtilities.dp(10)),
                ObjectAnimator.ofFloat(importCountTextView[0], View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(importCountTextView[0], View.TRANSLATION_Y, -AndroidUtilities.dp(10)),
                ObjectAnimator.ofFloat(infoTextView[1], View.ALPHA, 1.0f),
                ObjectAnimator.ofFloat(infoTextView[1], View.TRANSLATION_Y, 0),
                ObjectAnimator.ofFloat(importCountTextView[1], View.ALPHA, 1.0f),
                ObjectAnimator.ofFloat(importCountTextView[1], View.TRANSLATION_Y, 0),
                ObjectAnimator.ofFloat(lineProgressView, View.ALPHA, 0),
                ObjectAnimator.ofFloat(cell.linearLayout, View.TRANSLATION_Y, AndroidUtilities.dp(8), 0)
        );
        cell.background.animate().scaleY(1.0f).setInterpolator(new OvershootInterpolator(1.02f)).setDuration(250).start();
        cell.imageView.animate().scaleY(1.0f).scaleX(1.0f).setInterpolator(new OvershootInterpolator(1.02f)).setDuration(250).start();
        cell.imageView.playAnimation();
        animatorSet.start();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.historyImportProgressChanged) {
            if (args.length > 1) {
                dismiss();
                return;
            }
            long dialogId = parentFragment.getDialogId();
            SendMessagesHelper.ImportingHistory importingHistory = parentFragment.getSendMessagesHelper().getImportingHistory(dialogId);
            if (importingHistory == null) {
                setCompteled();
                return;
            }
            if (!compteled) {
                double timeToEndAnimation = (180 - imageView.getAnimatedDrawable().getCurrentFrame()) * 16.6 + 3000;
                if (timeToEndAnimation >= importingHistory.timeUntilFinish) {
                    imageView.setAutoRepeat(false);
                    compteled = true;
                }
            }

            percentTextView.setText(String.format("%d%%", importingHistory.uploadProgress));
            importCountTextView[0].setText(LocaleController.formatString("ImportCount", R.string.ImportCount, AndroidUtilities.formatFileSize(importingHistory.getUploadedCount()), AndroidUtilities.formatFileSize(importingHistory.getTotalCount())));
            lineProgressView.setProgress(importingHistory.uploadProgress / 100.0f, true);
        }
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        parentFragment.getNotificationCenter().removeObserver(this, NotificationCenter.historyImportProgressChanged);
    }
}
