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

public class SearchAdsInfoBottomSheet extends BottomSheetWithRecyclerListView {

    private static final int ITEM_HORIZONTAL_PADDING = 27;
    private static final int ICON_SIZE = 24;
    private static final int ITEM_TEXT_PADDING = 68;

    private final Paint topIconBgPaint;
    private final LinearLayout customView;

    @SuppressLint("UseCompatLoadingForDrawables")
    public SearchAdsInfoBottomSheet(Context context, Theme.ResourcesProvider resourcesProvider, Runnable remove) {
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

        linearLayout.addView(topView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 100, 0, 0, 0, 0));

        TextView topTitle = new TextView(context);
        topTitle.setText(LocaleController.getString(R.string.SearchAdsAboutTitle));
        topTitle.setTypeface(AndroidUtilities.bold());
        topTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        topTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        topTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.addView(topTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 22, 14, 22, 0));

        TextView topSubtitle = new TextView(context);
        topSubtitle.setText(LocaleController.getString(R.string.SearchAdsAboutSubtitle));
        topSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        topSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        topSubtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.addView(topSubtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 22, 8, 22, 0));

        FrameLayout info1 = new FeatureCell(context, R.drawable.menu_privacy, LocaleController.getString(R.string.SearchAdsAbout1Title), LocaleController.getString(R.string.RevenueSharingAdsInfo1Subtitle));
        linearLayout.addView(info1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20, 0, 0));

        final boolean premium = UserConfig.getInstance(currentAccount).isPremium();
        FrameLayout info2 = new FeatureCell(context, R.drawable.menu_feature_noads, LocaleController.getString(R.string.SearchAdsAbout2Title), AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(premium ? R.string.SearchAdsAbout2SubtitlePremium : R.string.SearchAdsAbout2Subtitle), () -> {
            if (premium) {
                MessagesController.getInstance(currentAccount).disableAds(true);
                remove.run();
            } else {
                BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment == null) return;
                BaseFragment premiumFragment = new PremiumPreviewFragment(PremiumPreviewFragment.featureTypeToServerString(PremiumPreviewFragment.PREMIUM_FEATURE_ADS));
                lastFragment.presentFragment(premiumFragment);
            }
            dismiss();
        }), true));
        linearLayout.addView(info2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16, 0, 0));

        View divider = new View(getContext());
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        LinearLayout.LayoutParams dividerLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerLayoutParams.setMargins(AndroidUtilities.dp(24), AndroidUtilities.dp(20), AndroidUtilities.dp(24), AndroidUtilities.dp(20));
        linearLayout.addView(divider, dividerLayoutParams);

        TextView textViewDescription4 = new TextView(context);
        textViewDescription4.setText(LocaleController.getString(R.string.SearchAdsAboutLaunchTitle));
        textViewDescription4.setTypeface(AndroidUtilities.bold());
        textViewDescription4.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textViewDescription4.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textViewDescription4.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        textViewDescription4.setGravity(Gravity.CENTER);
        linearLayout.addView(textViewDescription4, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 22, 0, 22, 0));

        SpannableStringBuilder bottomSubtitle1 = AndroidUtilities.replaceTags(LocaleController.getString(R.string.SearchAdsAboutLaunchSubtitle));
        String bottomSubtitle2 = getString(R.string.SearchAdsAboutLaunchLearnMore);
        CharSequence stringBuilder2 = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(bottomSubtitle2, () -> {
            dismiss();
            Browser.openUrl(getContext(), LocaleController.getString(R.string.PromoteUrl));
        }), true);
        SpannableStringBuilder bottomSubtitleFinal = AndroidUtilities.replaceCharSequence("%1$s", bottomSubtitle1, stringBuilder2);
        LinkSpanDrawable.LinksTextView textViewSubtitle4 = new LinkSpanDrawable.LinksTextView(context);
        textViewSubtitle4.setText(bottomSubtitleFinal);
        textViewSubtitle4.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textViewSubtitle4.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
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
        buttonTextView.setText(LocaleController.getString(R.string.SearchAdsAboutUnderstood));
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
            final boolean isRtl = LocaleController.isRTL;
            final ImageView ivIcon = new ImageView(getContext());
            Drawable iconDrawable = getContext().getResources().getDrawable(icon).mutate();
            iconDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            ivIcon.setImageDrawable(iconDrawable);
            addView(ivIcon, LayoutHelper.createFrame(ICON_SIZE, ICON_SIZE, isRtl ? Gravity.RIGHT : Gravity.LEFT, isRtl ? 0 : ITEM_HORIZONTAL_PADDING, 6, isRtl ? ITEM_HORIZONTAL_PADDING : 0, 0));

            final TextView tvTitle = new TextView(getContext());
            tvTitle.setText(header);
            tvTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            tvTitle.setTypeface(AndroidUtilities.bold());
            addView(tvTitle, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, isRtl ? Gravity.RIGHT : Gravity.LEFT, isRtl ? ITEM_HORIZONTAL_PADDING : ITEM_TEXT_PADDING, 0, isRtl ? ITEM_TEXT_PADDING : ITEM_HORIZONTAL_PADDING, 0));

            final LinkSpanDrawable.LinksTextView tvSubtitle = new LinkSpanDrawable.LinksTextView(getContext());
            tvSubtitle.setText(text);
            tvSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            tvSubtitle.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle, resourcesProvider));
            tvSubtitle.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            tvSubtitle.setLineSpacing(AndroidUtilities.dp(2), 1f);
            tvSubtitle.setPadding(dp(4), 0, dp(4), 0);
            addView(tvSubtitle, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, isRtl ? Gravity.RIGHT : Gravity.LEFT, (isRtl ? ITEM_HORIZONTAL_PADDING : ITEM_TEXT_PADDING) - 4, 18, (isRtl ? ITEM_TEXT_PADDING : ITEM_HORIZONTAL_PADDING) - 4, 0));
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
