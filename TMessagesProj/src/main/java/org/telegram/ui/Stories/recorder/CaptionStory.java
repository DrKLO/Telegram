package org.telegram.ui.Stories.recorder;

import static org.telegram.ui.ActionBar.Theme.RIPPLE_MASK_CIRCLE_20DP;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

public class CaptionStory extends CaptionContainerView {

    public ImageView periodButton;
    public PeriodDrawable periodDrawable;
    private ItemOptions periodPopup;
    private boolean periodVisible = true;

    public static final int[] periods = new int[] { 6 * 3600, 12 * 3600, 86400, 2 * 86400 };
    public static final int[] periodDrawables = new int[] { R.drawable.msg_story_6h, R.drawable.msg_story_12h, R.drawable.msg_story_24h, R.drawable.msg_story_48h };
    private int periodIndex = 0;

    public CaptionStory(Context context, FrameLayout rootView, SizeNotifierFrameLayout sizeNotifierFrameLayout, FrameLayout containerView, Theme.ResourcesProvider resourcesProvider, BlurringShader.BlurManager blurManager) {
        super(context, rootView, sizeNotifierFrameLayout, containerView, resourcesProvider, blurManager);

        periodButton = new ImageView(context);
        periodButton.setImageDrawable(periodDrawable = new PeriodDrawable());
        periodButton.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, RIPPLE_MASK_CIRCLE_20DP, AndroidUtilities.dp(18)));
        periodButton.setScaleType(ImageView.ScaleType.CENTER);
        periodButton.setOnClickListener(e -> {
            if (periodPopup != null && periodPopup.isShown()) {
                return;
            }

            Utilities.Callback<Integer> onPeriodSelected = period -> {
                setPeriod(period);
                if (onPeriodUpdate != null) {
                    onPeriodUpdate.run(period);
                }
            };

            final boolean isPremium = UserConfig.getInstance(currentAccount).isPremium();

            Utilities.Callback<Integer> showPremiumHint = isPremium ? null : period -> {
                if (onPremiumHintShow != null) {
                    onPremiumHintShow.run(period);
                }
            };

            periodPopup = ItemOptions.makeOptions(rootView, resourcesProvider, periodButton);
            periodPopup.addText(LocaleController.getString("StoryPeriodHint"), 13);
            periodPopup.addGap();
            for (int i = 0; i < periods.length; ++i) {
                final int period = periods[i];
                periodPopup.add(
                        0,
                        period == Integer.MAX_VALUE ?
                            LocaleController.getString("StoryPeriodKeep") :
                            LocaleController.formatPluralString("Hours", period / 3600),
                        Theme.key_actionBarDefaultSubmenuItem,
                        () -> onPeriodSelected.run(period)
                ).putPremiumLock(
                    isPremium || period == 86400 || period == Integer.MAX_VALUE ? null : () -> showPremiumHint.run(period)
                );
                if (periodIndex == i) {
                    periodPopup.putCheck();
                }
            }
            periodPopup.setDimAlpha(0).show();
        });
        setPeriod(86400, false);
        addView(periodButton, LayoutHelper.createFrame(44, 44, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 11, 10));
    }

    public void setPeriod(int period) {
        setPeriod(period, true);
    }

    public void setPeriodVisible(boolean visible) {
        periodVisible = visible;
        periodButton.setVisibility(periodVisible && !keyboardShown ? View.VISIBLE : View.GONE);
    }

    public void setPeriod(int period, boolean animated) {
        int index = 2;
        for (int i = 0; i < periods.length; ++i) {
            if (periods[i] == period) {
                index = i;
                break;
            }
        }
        if (periodIndex == index) {
            return;
        }
        periodIndex = index;
        periodDrawable.setValue(period / 3600, false, animated);
    }

    public void hidePeriodPopup() {
        if (periodPopup != null) {
            periodPopup.dismiss();
            periodPopup = null;
        }
    }

    private Utilities.Callback<Integer> onPeriodUpdate;
    public void setOnPeriodUpdate(Utilities.Callback<Integer> listener) {
        this.onPeriodUpdate = listener;
    }

    private Utilities.Callback<Integer> onPremiumHintShow;
    public void setOnPremiumHint(Utilities.Callback<Integer> listener) {
        this.onPremiumHintShow = listener;
    }

    @Override
    protected void beforeUpdateShownKeyboard(boolean show) {
        if (!show) {
            periodButton.setVisibility(periodVisible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onUpdateShowKeyboard(float keyboardT) {
        periodButton.setAlpha(1f - keyboardT);
    }

    @Override
    protected void afterUpdateShownKeyboard(boolean show) {
        periodButton.setVisibility(!show && periodVisible ? View.VISIBLE : View.GONE);
        if (show) {
            periodButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected int getCaptionPremiumLimit() {
        return MessagesController.getInstance(currentAccount).storyCaptionLengthLimitPremium;
    }

    @Override
    protected int getCaptionDefaultLimit() {
        return MessagesController.getInstance(currentAccount).storyCaptionLengthLimitDefault;
    }
}
