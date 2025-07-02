package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.GiftPremiumBottomSheet;
import org.telegram.ui.Components.Premium.PremiumPreviewBottomSheet;
import org.telegram.ui.Components.Premium.boosts.cells.ActionBtnCell;
import org.telegram.ui.Components.Premium.boosts.cells.LinkCell;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

public class PremiumPreviewGiftLinkBottomSheet extends PremiumPreviewBottomSheet {
    private static final int BOTTOM_HEIGHT_DP = 68;
    private static final int CELL_TYPE_LINK = 6;
    private static PremiumPreviewGiftLinkBottomSheet instance;

    private ActionBtnCell actionBtn;
    private final String slug;
    private final boolean isUsed;

    public static void show(String slug, Browser.Progress progress) {
        GiftInfoBottomSheet.show(LaunchActivity.getLastFragment(), slug, progress);
    }

    public static void show(String slug, TLRPC.TL_premiumGiftOption giftOption, TLRPC.User user, boolean isUsed) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment == null || instance != null) {
            return;
        }
        GiftPremiumBottomSheet.GiftTier tier = new GiftPremiumBottomSheet.GiftTier(giftOption, null);
        PremiumPreviewGiftLinkBottomSheet sheet = new PremiumPreviewGiftLinkBottomSheet(fragment, UserConfig.selectedAccount, user, tier, slug, isUsed, fragment.getResourceProvider());
        sheet.show();
        instance = sheet;
    }

    public PremiumPreviewGiftLinkBottomSheet(BaseFragment fragment, int currentAccount, TLRPC.User user, GiftPremiumBottomSheet.GiftTier gift, String slug, boolean isUsed, Theme.ResourcesProvider resourcesProvider) {
        super(fragment, currentAccount, user, gift, resourcesProvider);
        this.slug = slug;
        this.isUsed = isUsed;
        init();
    }

    @Override
    protected void updateRows() {
        paddingRow = rowCount++;
        additionStartRow = rowCount;
        additionEndRow = ++rowCount;
        featuresStartRow = rowCount;
        rowCount += premiumFeatures.size();
        featuresEndRow = rowCount;
        sectionRow = rowCount++;
    }

    @Override
    public void setTitle(boolean animated) {
        super.setTitle(animated);
        subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        ((ViewGroup.MarginLayoutParams) subtitleView.getLayoutParams()).bottomMargin = dp(14);
        ((ViewGroup.MarginLayoutParams) subtitleView.getLayoutParams()).topMargin = dp(12);
        String subTitleText = getString("GiftPremiumAboutThisLink", R.string.GiftPremiumAboutThisLink);
        SpannableStringBuilder subTitleWithLink = AndroidUtilities.replaceSingleTag(
                subTitleText,
                Theme.key_chat_messageLinkIn, 0,
                this::share);
        subtitleView.setText(AndroidUtilities.replaceCharSequence("%1$s", subTitleWithLink, replaceTags(getString("GiftPremiumAboutThisLinkEnd", R.string.GiftPremiumAboutThisLinkEnd))));
    }

    private void share() {
        final String slugLink = "https://t.me/giftcode/" + slug;
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity dialogFragment = new DialogsActivity(args);
        dialogFragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, topicsFragment) -> {
            long did = 0;
            for (int a = 0; a < dids.size(); a++) {
                did = dids.get(a).dialogId;
                getBaseFragment().getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(slugLink, did, null, null, null, true, null, null, null, true, 0, null, false));
            }
            fragment1.finishFragment();
            BoostDialogs.showGiftLinkForwardedBulletin(did);
            return true;
        });
        getBaseFragment().presentFragment(dialogFragment);
        dismiss();
    }

    @Override
    protected View onCreateAdditionCell(int viewType, Context context) {
        if (viewType == CELL_TYPE_LINK) {
            LinkCell cell = new LinkCell(context, getBaseFragment(), resourcesProvider);
            cell.setPadding(0, 0, 0, dp(8));
            return cell;
        }
        return null;
    }

    @Override
    protected void onBindAdditionCell(View view, int pos) {
        ((LinkCell) view).setSlug(slug);
    }

    @Override
    protected int getAdditionItemViewType(int position) {
        return CELL_TYPE_LINK;
    }

    private void init() {
        Bulletin.addDelegate((FrameLayout) containerView, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return dp(BOTTOM_HEIGHT_DP);
            }
        });
        if (!isUsed) {
            recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(BOTTOM_HEIGHT_DP));
            actionBtn = new ActionBtnCell(getContext(), resourcesProvider);
            actionBtn.setOnClickListener(v -> {
                if (actionBtn.isLoading()) {
                    return;
                }
                actionBtn.updateLoading(true);
                BoostRepository.applyGiftCode(slug, result -> {
                    actionBtn.updateLoading(false);
                    dismiss();
                    AndroidUtilities.runOnUIThread(() -> {
                        PremiumPreviewBottomSheet previewBottomSheet = new PremiumPreviewBottomSheet(getBaseFragment(), UserConfig.selectedAccount, null, null, resourcesProvider)
                                .setAnimateConfetti(true)
                                .setAnimateConfettiWithStars(true)
                                .setOutboundGift(true);
                        getBaseFragment().showDialog(previewBottomSheet);
                    }, 200);
                }, error -> {
                    actionBtn.updateLoading(false);
                    BoostDialogs.processApplyGiftCodeError(error, (FrameLayout) containerView, resourcesProvider, this::share);
                });
            });
            actionBtn.setActivateForFreeStyle();
            containerView.addView(actionBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, BOTTOM_HEIGHT_DP, Gravity.BOTTOM, 0, 0, 0, 0));
        }
        fixNavigationBar();
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        instance = null;
    }
}
