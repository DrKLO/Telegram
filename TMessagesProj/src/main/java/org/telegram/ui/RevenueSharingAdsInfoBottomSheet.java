package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

public class RevenueSharingAdsInfoBottomSheet extends BottomSheetWithRecyclerListView {

    private static final int ITEM_HORIZONTAL_PADDING = 27;
    private static final int ICON_SIZE = 24;
    private static final int ITEM_TEXT_PADDING = 68;

    private final Paint topIconBgPaint;
    private final LinearLayout customView;

    @SuppressLint("UseCompatLoadingForDrawables")
    public RevenueSharingAdsInfoBottomSheet(Context context, boolean bot, Theme.ResourcesProvider resourcesProvider, Utilities.Callback<ItemOptions> options) {
        super(context, null, false, false, false, resourcesProvider);
        fixNavigationBar();

        topPadding = .2f;

        topIconBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        topIconBgPaint.setStyle(Paint.Style.FILL);
        topIconBgPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));

        LinearLayout linearLayout = customView = new LinearLayout(context);
        linearLayout.setPadding(backgroundPaddingLeft + dp(6), 0, backgroundPaddingLeft + dp(6), 0);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        FrameLayout topView = new FrameLayout(context);
        RLottieImageView imageView = new RLottieImageView(getContext());
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.large_ads_info);
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        imageView.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        topView.addView(imageView, LayoutHelper.createFrame(80, 80, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));

        if (options != null) {
            ImageView optionsView = new ImageView(context);
            optionsView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_ab_other));
            optionsView.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
            optionsView.setScaleType(ImageView.ScaleType.CENTER);
            optionsView.setColorFilter(Theme.getColor(Theme.key_dialogTextGray3));
            optionsView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
            optionsView.setOnClickListener(v -> {
                options.run(
                    ItemOptions.makeOptions(container, resourcesProvider, optionsView, true)
                        .setGravity(Gravity.RIGHT)
                        .setDrawScrim(false)
                        .translate(dp(12), dp(-32))
                );
            });
            topView.addView(optionsView, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.TOP, 12, 14, 14, 12));
        }

        linearLayout.addView(topView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 100, 0, 0, 0, 0));

        TextView topTitle = new TextView(context);
        topTitle.setText(LocaleController.getString(R.string.AboutRevenueSharingAds));
        topTitle.setTypeface(AndroidUtilities.bold());
        topTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        topTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        topTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.addView(topTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 22, 14, 22, 0));

        TextView topSubtitle = new TextView(context);
        topSubtitle.setText(LocaleController.getString(R.string.RevenueSharingAdsAlertSubtitle));
        topSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        topSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        topSubtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.addView(topSubtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 22, 8, 22, 0));

        FrameLayout info1 = new FeatureCell(context, R.drawable.menu_privacy, LocaleController.getString(bot ? R.string.RevenueSharingAdsInfo1TitleBot : R.string.RevenueSharingAdsInfo1Title), LocaleController.getString(bot ? R.string.RevenueSharingAdsInfo1SubtitleBot : R.string.RevenueSharingAdsInfo1Subtitle));
        linearLayout.addView(info1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20, 0, 0));

        FrameLayout info2 = new FeatureCell(context, R.drawable.menu_feature_split, LocaleController.getString(bot ? R.string.RevenueSharingAdsInfo2TitleBot : R.string.RevenueSharingAdsInfo2Title), LocaleController.getString(bot ? R.string.RevenueSharingAdsInfo2SubtitleBot : R.string.RevenueSharingAdsInfo2Subtitle));
        linearLayout.addView(info2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16, 0, 0));

        String info3DescriptionString = LocaleController.formatString(bot ? R.string.RevenueSharingAdsInfo3SubtitleBot : R.string.RevenueSharingAdsInfo3Subtitle, MessagesController.getInstance(UserConfig.selectedAccount).channelRestrictSponsoredLevelMin);
        SpannableStringBuilder info3Description = AndroidUtilities.replaceSingleTag(info3DescriptionString, Theme.key_chat_messageLinkIn, 0, () -> {
            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment == null) return;
            BaseFragment premiumFragment = new PremiumPreviewFragment(PremiumPreviewFragment.featureTypeToServerString(PremiumPreviewFragment.PREMIUM_FEATURE_ADS));
            lastFragment.presentFragment(premiumFragment);
            dismiss();
        });

        FrameLayout info3 = new FeatureCell(context, R.drawable.menu_feature_noads, LocaleController.getString(R.string.RevenueSharingAdsInfo3Title), info3Description);
        linearLayout.addView(info3, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16, 0, 0));

        View divider = new View(getContext());
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        LinearLayout.LayoutParams dividerLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerLayoutParams.setMargins(AndroidUtilities.dp(24), AndroidUtilities.dp(20), AndroidUtilities.dp(24), AndroidUtilities.dp(20));
        linearLayout.addView(divider, dividerLayoutParams);

        TextView textViewDescription4 = new TextView(context);
        textViewDescription4.setText(LocaleController.getString(bot ? R.string.RevenueSharingAdsInfo4TitleBot : R.string.RevenueSharingAdsInfo4Title));
        textViewDescription4.setTypeface(AndroidUtilities.bold());
        textViewDescription4.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textViewDescription4.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textViewDescription4.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        textViewDescription4.setGravity(Gravity.CENTER);
        linearLayout.addView(textViewDescription4, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 22, 0, 22, 0));

        SpannableStringBuilder bottomSubtitle1 = AndroidUtilities.replaceTags(LocaleController.getString(bot ? R.string.RevenueSharingAdsInfo4Subtitle2Bot : R.string.RevenueSharingAdsInfo4Subtitle2));
        String bottomSubtitle2 = getString(R.string.RevenueSharingAdsInfo4SubtitleLearnMore);
        SpannableStringBuilder stringBuilder2 = AndroidUtilities.replaceSingleTag(bottomSubtitle2, Theme.key_chat_messageLinkIn, 0, () -> {
            dismiss();
            Browser.openUrl(getContext(), LocaleController.getString(R.string.PromoteUrl));
        });
        SpannableString arrowStr = new SpannableString(">");
        ColoredImageSpan span = new ColoredImageSpan(R.drawable.attach_arrow_right);
        span.setOverrideColor(Theme.getColor(Theme.key_chat_messageLinkIn));
        span.setScale(.7f, .7f);
        span.setWidth(dp(12));
        span.setTranslateY(1);
        arrowStr.setSpan(span, 0, arrowStr.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableStringBuilder bottomSubtitleFinal = AndroidUtilities.replaceCharSequence(">", AndroidUtilities.replaceCharSequence("%1$s", bottomSubtitle1, stringBuilder2), arrowStr);
        LinkSpanDrawable.LinksTextView textViewSubtitle4 = new LinkSpanDrawable.LinksTextView(context);
        textViewSubtitle4.setText(bottomSubtitleFinal);
        textViewSubtitle4.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textViewSubtitle4.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textViewSubtitle4.setGravity(Gravity.CENTER_HORIZONTAL);
        textViewSubtitle4.setLineSpacing(AndroidUtilities.dp(2), 1f);
        linearLayout.addView(textViewSubtitle4, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 26, 8, 26, 0));

        TextView buttonTextView = new TextView(context);
        buttonTextView.setLines(1);
        buttonTextView.setSingleLine(true);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setEllipsize(TextUtils.TruncateAt.END);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setText(LocaleController.getString(R.string.RevenueSharingAdsAlertButton));
        buttonTextView.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), 6));
        buttonTextView.setOnClickListener(e -> dismiss());
        linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 14, 22, 14, 14));

//        ScrollView scrollView = new ScrollView(getContext());
//        scrollView.addView(linearLayout);
//        setCustomView(scrollView);

        adapter.update(false);
    }

    public static RevenueSharingAdsInfoBottomSheet showAlert(Context context, BaseFragment fragment, boolean bot, Theme.ResourcesProvider resourcesProvider) {
        return showAlert(context, fragment, bot, resourcesProvider, null);
    }

    public static RevenueSharingAdsInfoBottomSheet showAlert(Context context, BaseFragment fragment, boolean bot, Theme.ResourcesProvider resourcesProvider, Utilities.Callback<ItemOptions> options) {
        RevenueSharingAdsInfoBottomSheet alert = new RevenueSharingAdsInfoBottomSheet(context, bot, resourcesProvider, options);
        if (fragment != null) {
            if (fragment.getParentActivity() != null) {
                fragment.showDialog(alert);
            }
        } else {
            alert.show();
        }
        return alert;
    }

    private class FeatureCell extends FrameLayout {
        public FeatureCell(Context context, int icon, CharSequence header, CharSequence text) {
            super(context);
            boolean isRtl = LocaleController.isRTL;
            ImageView ivIcon = new ImageView(getContext());
            Drawable iconDrawable = getContext().getResources().getDrawable(icon).mutate();
            iconDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            ivIcon.setImageDrawable(iconDrawable);
            addView(ivIcon, LayoutHelper.createFrame(ICON_SIZE, ICON_SIZE, isRtl ? Gravity.RIGHT : Gravity.LEFT, isRtl ? 0 : ITEM_HORIZONTAL_PADDING, 6, isRtl ? ITEM_HORIZONTAL_PADDING : 0, 0));

            TextView tvTitle = new TextView(getContext());
            tvTitle.setText(header);
            tvTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            tvTitle.setTypeface(AndroidUtilities.bold());
            addView(tvTitle, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, isRtl ? Gravity.RIGHT : Gravity.LEFT, isRtl ? ITEM_HORIZONTAL_PADDING : ITEM_TEXT_PADDING, 0, isRtl ? ITEM_TEXT_PADDING : ITEM_HORIZONTAL_PADDING, 0));

            LinkSpanDrawable.LinksTextView tvSubtitle = new LinkSpanDrawable.LinksTextView(getContext());
            tvSubtitle.setText(text);
            tvSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            tvSubtitle.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle, resourcesProvider));
            tvSubtitle.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            tvSubtitle.setLineSpacing(AndroidUtilities.dp(2), 1f);
            addView(tvSubtitle, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, isRtl ? Gravity.RIGHT : Gravity.LEFT, isRtl ? ITEM_HORIZONTAL_PADDING : ITEM_TEXT_PADDING, 18, isRtl ? ITEM_TEXT_PADDING : ITEM_HORIZONTAL_PADDING, 0));
        }
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.AboutRevenueSharingAds);
    }

    private UniversalAdapter adapter;

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(customView));
    }

}
