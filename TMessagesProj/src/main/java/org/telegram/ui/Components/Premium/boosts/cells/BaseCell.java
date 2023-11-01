package org.telegram.ui.Components.Premium.boosts.cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;
import org.telegram.ui.Components.RecyclerListView;

@SuppressLint("ViewConstructor")
public abstract class BaseCell extends FrameLayout {

    protected final Theme.ResourcesProvider resourcesProvider;

    protected final AvatarDrawable avatarDrawable = new AvatarDrawable();
    protected final BackupImageView imageView;

    protected final SimpleTextView titleTextView;
    protected final SimpleTextView subtitleTextView;

    protected final RadioButton radioButton;

    protected final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected boolean needDivider;
    protected View backgroundView;

    public BaseCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        backgroundView = new View(context);
        addView(backgroundView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        backgroundView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        avatarDrawable.setRoundRadius(AndroidUtilities.dp(40));

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(20));
        addView(imageView);

        titleTextView = new SimpleTextView(context) {
            @Override
            public boolean setText(CharSequence value) {
                value = Emoji.replaceEmoji(value, getPaint().getFontMetricsInt(), AndroidUtilities.dp(15), false);
                return super.setText(value);
            }
        };
        NotificationCenter.listenEmojiLoading(titleTextView);
        NotificationCenter.listenEmojiLoading(imageView);
        titleTextView.setTextSize(16);
        titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(titleTextView);

        subtitleTextView = new SimpleTextView(context);
        subtitleTextView.setTextSize(14);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        subtitleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(subtitleTextView);

        radioButton = new RadioButton(context);
        radioButton.setSize(AndroidUtilities.dp(20));
        radioButton.setColor(Theme.getColor(Theme.key_checkboxDisabled, resourcesProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
        addView(radioButton);

        updateLayouts();

        if (!needCheck()) {
            radioButton.setVisibility(View.GONE);
        }
    }

    protected abstract boolean needCheck();

    protected void updateLayouts() {
        imageView.setLayoutParams(LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), needCheck() ? 53 : 16, 0, needCheck() ? 53 : 16, 0));
        titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 20 : (needCheck() ? 105 : 70), 0, LocaleController.isRTL ? (needCheck() ? 105 : 70) : 20, 0));
        subtitleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 20 : (needCheck() ? 105 : 70), 0, LocaleController.isRTL ? (needCheck() ? 105 : 70) : 20, 0));
        radioButton.setLayoutParams(LayoutHelper.createFrame(22, 22, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 15 : 20, 0, LocaleController.isRTL ? 20 : 15, 0));
    }

    public void setChecked(boolean checked, boolean animated) {
        if (radioButton.getVisibility() == View.VISIBLE) {
            radioButton.setChecked(checked, animated);
        }
    }

    public void markChecked(RecyclerListView recyclerListView) {
        if (!needCheck()) {
            return;
        }
        for (int i = 0; i < recyclerListView.getChildCount(); i++) {
            View child = recyclerListView.getChildAt(i);
            if (child.getClass().isInstance(this)) {
                BaseCell cell = (BaseCell) child;
                cell.setChecked(child == this, true);
            }
        }
    }

    protected CharSequence withArrow(CharSequence text) {
        SpannableString arrow = new SpannableString(">");
        Drawable arrowDrawable = getContext().getResources().getDrawable(R.drawable.attach_arrow_right);
        ColoredImageSpan span = new ColoredImageSpan(arrowDrawable, ColoredImageSpan.ALIGN_CENTER);
        arrowDrawable.setBounds(0, dp(1), dp(11), dp(1 + 11));
        arrow.setSpan(span, 0, arrow.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableStringBuilder finalText = new SpannableStringBuilder();
        finalText.append(text).append(" ").append(arrow);
        return finalText;
    }

    protected void setSubtitle(CharSequence text) {
        if (text == null) {
            titleTextView.setTranslationY(0);
            subtitleTextView.setVisibility(View.GONE);
        } else {
            titleTextView.setTranslationY(AndroidUtilities.dp(-9));
            subtitleTextView.setTranslationY(AndroidUtilities.dp(12));
            subtitleTextView.setText(text);
            subtitleTextView.setVisibility(View.VISIBLE);
        }
        if (imageView.getVisibility() == GONE) {
            if (LocaleController.isRTL) {
                titleTextView.setTranslationX(AndroidUtilities.dp(40));
                subtitleTextView.setTranslationX(AndroidUtilities.dp(40));
            } else {
                titleTextView.setTranslationX(AndroidUtilities.dp(-40));
                subtitleTextView.setTranslationX(AndroidUtilities.dp(-40));
            }
        }
    }

    public void setDivider(boolean divider) {
        needDivider = divider;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(getFullHeight()), MeasureSpec.EXACTLY)
        );
    }

    protected int getFullHeight() {
        return 56;
    }

    protected int dividerPadding() {
        return 0;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (needDivider) {
            dividerPaint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
            int paddingDp = needCheck() ? 105 : 70;
            if (imageView.getVisibility() == GONE) {
                paddingDp -= 40;
            }
            paddingDp += dividerPadding();
            if (LocaleController.isRTL) {
                canvas.drawRect(0, getHeight() - 1, getWidth() - dp(paddingDp), getHeight(), dividerPaint);
            } else {
                canvas.drawRect(dp(paddingDp), getHeight() - 1, getWidth(), getHeight(), dividerPaint);
            }
        }
    }
}
