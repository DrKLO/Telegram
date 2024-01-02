package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.Premium.PremiumPreviewBottomSheet;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.Premium.boosts.cells.DurationWithDiscountCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorBtnCell;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PremiumPreviewGiftToUsersBottomSheet extends PremiumPreviewBottomSheet {
    private static final int BOTTOM_HEIGHT_DP = 64;
    private static final int CELL_TYPE_HEADER = 6,
            CELL_TYPE_SHADOW = 7,
            CELL_TYPE_DURATION = 8;

    private GradientButtonWithCounterView actionBtn;
    private SelectorBtnCell buttonContainer;
    private final List<TLRPC.User> selectedUsers = new ArrayList<>();
    private final List<TLRPC.TL_premiumGiftCodeOption> giftCodeOptions = new ArrayList<>();
    private int selectedMonths = 3;

    public static void show(List<TLRPC.User> selectedUsers, List<TLRPC.TL_premiumGiftCodeOption> giftCodeOptions) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment == null) {
            return;
        }
        new PremiumPreviewGiftToUsersBottomSheet(fragment, UserConfig.selectedAccount, selectedUsers, giftCodeOptions, fragment.getResourceProvider()).show();
    }

    public PremiumPreviewGiftToUsersBottomSheet(BaseFragment fragment, int currentAccount, List<TLRPC.User> selectedUsers, List<TLRPC.TL_premiumGiftCodeOption> giftCodeOptions, Theme.ResourcesProvider resourcesProvider) {
        super(fragment, currentAccount, null, null, resourcesProvider);
        this.selectedUsers.addAll(selectedUsers);
        this.giftCodeOptions.addAll(giftCodeOptions);
        Collections.sort(giftCodeOptions, Comparator.comparingLong(o -> o.amount));
        init();
    }

    @Override
    protected boolean needDefaultPremiumBtn() {
        return false;
    }

    @Override
    protected void updateRows() {
        rowCount = 0;
        paddingRow = rowCount++;
        additionStartRow = rowCount;
        rowCount += (giftCodeOptions != null ? giftCodeOptions.size() : 0) + 2;
        additionEndRow = rowCount;
        featuresStartRow = rowCount;
        rowCount += premiumFeatures.size();
        featuresEndRow = rowCount;
        termsRow = rowCount++;
    }

    @Override
    public void setTitle(boolean animated) {
        titleView[0].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        subtitleView.setPadding(dp(30), 0, dp(30), 0);
        subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        titleView[0].setText(getString("GiftTelegramPremiumTitle", R.string.GiftTelegramPremiumTitle));
        ((ViewGroup.MarginLayoutParams) subtitleView.getLayoutParams()).bottomMargin = dp(16);
        ((ViewGroup.MarginLayoutParams) subtitleView.getLayoutParams()).topMargin = dp(4f);

        String subTitle;
        switch (selectedUsers.size()) {
            case 1: {
                String names = formatString("GiftPremiumUsersOne", R.string.GiftPremiumUsersOne, UserObject.getFirstName(selectedUsers.get(0)));
                subTitle = formatString("GiftPremiumUsersGiveAccessManyZero", R.string.GiftPremiumUsersGiveAccessManyZero, names);
                break;
            }
            case 2: {
                String names = formatString("GiftPremiumUsersTwo", R.string.GiftPremiumUsersTwo, UserObject.getFirstName(selectedUsers.get(0)), UserObject.getFirstName(selectedUsers.get(1)));
                subTitle = formatString("GiftPremiumUsersGiveAccessManyZero", R.string.GiftPremiumUsersGiveAccessManyZero, names);
                break;
            }
            case 3: {
                String names = formatString("GiftPremiumUsersThree", R.string.GiftPremiumUsersThree, UserObject.getFirstName(selectedUsers.get(0)), UserObject.getFirstName(selectedUsers.get(1)), UserObject.getFirstName(selectedUsers.get(2)));
                subTitle = formatString("GiftPremiumUsersGiveAccessManyZero", R.string.GiftPremiumUsersGiveAccessManyZero, names);
                break;
            }
            default: {
                String names = formatString("GiftPremiumUsersThree", R.string.GiftPremiumUsersThree, UserObject.getFirstName(selectedUsers.get(0)), UserObject.getFirstName(selectedUsers.get(1)), UserObject.getFirstName(selectedUsers.get(2)));
                subTitle = formatPluralString("GiftPremiumUsersGiveAccessMany", selectedUsers.size() - 3, names);
                break;
            }
        }
        subtitleView.setText(replaceTags(subTitle));

        subtitleView.append("\n");
        subtitleView.append("\n");
        CharSequence boostInfo = replaceTags(formatPluralString("GiftPremiumWillReceiveBoostsPlural", selectedUsers.size() * BoostRepository.boostsPerSentGift()));
        SpannableStringBuilder boostInfoSpannableBuilder = new SpannableStringBuilder(boostInfo);
        ColoredImageSpan span = new ColoredImageSpan(R.drawable.mini_boost_button);
        span.setSize(dp(20));
        span.setWidth(dp(11));
        span.setTranslateX(-dp(4));
        span.setTranslateY(-dp(1));
        span.setColorKey(Theme.key_windowBackgroundWhiteBlueText4);
        String lightning = "⚡";
        int lightningIndex = TextUtils.indexOf(boostInfo, lightning);
        if (lightningIndex >= 0) {
            boostInfoSpannableBuilder.setSpan(span, lightningIndex, lightningIndex + lightning.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        subtitleView.append(boostInfoSpannableBuilder);
    }

    @Override
    protected View onCreateAdditionCell(int viewType, Context context) {
        switch (viewType) {
            case CELL_TYPE_HEADER: {
                HeaderCell cell = new HeaderCell(context,Theme.key_windowBackgroundWhiteBlueHeader, 21, 12, false, resourcesProvider);
                cell.setTextSize(15);
                cell.setPadding(0, 0, 0, dp(2));
                cell.setText(getString("GiftPremiumWhatsIncluded", R.string.GiftPremiumWhatsIncluded));
                return cell;
            }
            case CELL_TYPE_SHADOW: {
                return new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            }
            case CELL_TYPE_DURATION: {
                return new DurationWithDiscountCell(context, resourcesProvider);
            }
            default:
                return null;
        }
    }

    @Override
    protected void onAdditionItemClicked(View view) {
        if (view instanceof DurationWithDiscountCell) {
            DurationWithDiscountCell cell = ((DurationWithDiscountCell) view);
            selectedMonths = cell.getOption().months;
            cell.markChecked(recyclerListView);
            updateActionButton(true);
        }
    }

    @Override
    protected boolean isAdditionViewClickable(int viewType) {
        return viewType == 8;
    }

    @Override
    protected void onBindAdditionCell(View view, int pos) {
        if (view instanceof DurationWithDiscountCell) {
            pos = pos - 1;
            TLRPC.TL_premiumGiftCodeOption option = giftCodeOptions.get(pos);
            ((DurationWithDiscountCell) view).setDuration(option, giftCodeOptions.get(giftCodeOptions.size() - 1), selectedUsers.size(), pos != giftCodeOptions.size() - 1, selectedMonths == option.months);
        }
    }

    @Override
    protected int getAdditionItemViewType(int position) {
        if (position <= giftCodeOptions.size()) {
            return CELL_TYPE_DURATION;
        } else if (position == giftCodeOptions.size() + 1) {
            return CELL_TYPE_SHADOW;
        } else if (position == giftCodeOptions.size() + 2) {
            return CELL_TYPE_HEADER;
        }
        return 0;
    }

    private TLRPC.TL_premiumGiftCodeOption getSelectedOption() {
        for (TLRPC.TL_premiumGiftCodeOption giftCodeOption : giftCodeOptions) {
            if (giftCodeOption.months == selectedMonths) {
                return giftCodeOption;
            }
        }
        return giftCodeOptions.get(0);
    }

    private void updateActionButton(boolean animated) {
        TLRPC.TL_premiumGiftCodeOption giftCodeOption = getSelectedOption();
        String priceStr = BillingController.getInstance().formatCurrency(giftCodeOption.amount, giftCodeOption.currency);
        if (selectedUsers.size() == 1) {
            actionBtn.setText(formatString("GiftSubscriptionFor", R.string.GiftSubscriptionFor, priceStr), animated);
        } else {
            actionBtn.setText(formatPluralString("GiftSubscriptionCountFor", selectedUsers.size(), priceStr), animated);
        }
    }

    private void chooseMaxSelectedMonths() {
        for (TLRPC.TL_premiumGiftCodeOption giftCodeOption : giftCodeOptions) {
            selectedMonths = Math.max(giftCodeOption.months, selectedMonths);
        }
    }

    private void init() {
        chooseMaxSelectedMonths();
        updateRows();
        useBackgroundTopPadding = false;
        setApplyTopPadding(false);
        backgroundPaddingTop = 0;
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(BOTTOM_HEIGHT_DP));

        buttonContainer = new SelectorBtnCell(getContext(), resourcesProvider, recyclerListView);
        buttonContainer.setClickable(true);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setPadding(dp(8), dp(8), dp(8), dp(8));
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        actionBtn = new GradientButtonWithCounterView(getContext(), true, resourcesProvider);
        actionBtn.setOnClickListener(v -> {
            if (actionBtn.isLoading()) {
                return;
            }
            actionBtn.setLoading(true);
            BoostRepository.payGiftCode(new ArrayList<>(selectedUsers), getSelectedOption(), null, getBaseFragment(), result -> {
                dismiss();
                NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.giftsToUserSent);
                AndroidUtilities.runOnUIThread(() -> PremiumPreviewGiftSentBottomSheet.show(selectedUsers), 250);
            }, error -> {
                actionBtn.setLoading(false);
                BoostDialogs.showToastError(getContext(), error);
            });
        });
        buttonContainer.addView(actionBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        containerView.addView(buttonContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));

        overrideTitleIcon = AvatarHolderView.createAvatarsContainer(getContext(), selectedUsers);
        updateActionButton(false);
        fixNavigationBar();
    }

    protected void afterCellCreated(int viewType, View view) {
        if (viewType == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                view.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        float cornerRadius = AndroidUtilities.dp(12);
                        outline.setRoundRect(0, 0, view.getWidth(), (int) (view.getHeight() + cornerRadius), cornerRadius);
                    }
                });
                view.setClipToOutline(true);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            }
            ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).topMargin = -dp(6);
        }
    }

    @Override
    protected void attachIconContainer(LinearLayout container) {
        container.addView(overrideTitleIcon, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                selectedUsers.size() == 1 ? 94 : 83,
                0,
                selectedUsers.size() == 1 ? 28 : 34,
                0,
                selectedUsers.size() == 1 ? 9 : 14)
        );
    }

    @SuppressLint("ViewConstructor")
    static class AvatarHolderView extends FrameLayout {

        public static View createAvatarsContainer(Context context, List<TLRPC.User> selectedUsers) {
            FrameLayout avatarsContainer = new FrameLayout(context);
            avatarsContainer.setClipChildren(false);
            FrameLayout avatarsWrapper = new FrameLayout(context);
            avatarsWrapper.setClipChildren(false);

            if (selectedUsers.size() == 1) {
                avatarsContainer.addView(avatarsWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 94, 0, 0, 0, 0, 0));
                PremiumPreviewGiftToUsersBottomSheet.AvatarHolderView avatarHolderView = new PremiumPreviewGiftToUsersBottomSheet.AvatarHolderView(context, 47);
                avatarHolderView.drawCycle = false;
                avatarHolderView.setUser(selectedUsers.get(0));
                avatarsWrapper.addView(avatarHolderView, 0, LayoutHelper.createFrame(94, 94, Gravity.CENTER));
            } else {
                avatarsContainer.addView(avatarsWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 83, 0, 0, 0, 0, 0));
                int visibleCount = 0;
                for (int i = 0; i < selectedUsers.size(); i++) {
                    TLRPC.User user = selectedUsers.get(i);
                    PremiumPreviewGiftToUsersBottomSheet.AvatarHolderView avatarHolderView = new PremiumPreviewGiftToUsersBottomSheet.AvatarHolderView(context, 41.5f);
                    avatarHolderView.setUser(user);
                    avatarsWrapper.addView(avatarHolderView, 0, LayoutHelper.createFrame(83, 83, Gravity.CENTER));
                    avatarHolderView.setTranslationX(-i * dp(29));
                    if (i == 0 && selectedUsers.size() > 3) {
                        avatarHolderView.iconView.setAlpha(1f);
                        avatarHolderView.iconView.count = selectedUsers.size() - 3;
                    }
                    visibleCount++;
                    if (i == 2) {
                        break;
                    }
                }
                avatarsContainer.setTranslationX(dp(29 / 2f) * (visibleCount - 1));
            }
            return avatarsContainer;
        }

        private final BackupImageView imageView;
        protected final AdditionalCounterView iconView;
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public TLRPC.User user;
        public boolean drawCycle = true;
        AvatarDrawable fromAvatarDrawable = new AvatarDrawable();

        public AvatarHolderView(Context context, float radiusDp) {
            super(context);
            imageView = new BackupImageView(getContext());
            imageView.setRoundRadius(AndroidUtilities.dp(radiusDp));
            iconView = new AdditionalCounterView(context);
            iconView.setAlpha(0f);
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 5, 5, 5, 5));
            addView(iconView, LayoutHelper.createFrame(26, 26, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 1, 3));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bgPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            } else {
                bgPaint.setColor(Theme.getColor(Theme.key_dialogBackground));
            }
        }

        public void setUser(TLRPC.User user) {
            this.user = user;
            fromAvatarDrawable.setInfo(user);
            imageView.setForUserOrChat(user, fromAvatarDrawable);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (drawCycle) {
                canvas.drawCircle(getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, (getMeasuredHeight() / 2f) - dp(2f), bgPaint);
            }
            super.dispatchDraw(canvas);
        }
    }

    static class AdditionalCounterView extends View {

        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        int count;

        public AdditionalCounterView(Context context) {
            super(context);
            paint.setTextAlign(Paint.Align.CENTER);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            } else {
                paint.setColor(Theme.getColor(Theme.key_dialogBackground));
            }
            paint.setTextSize(dp(11.5f));
            paint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getMeasuredWidth() / 2f;
            float cy = getMeasuredHeight() / 2f;
            canvas.drawCircle(cx, cy, getMeasuredWidth() / 2f, paint);
            PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, getMeasuredWidth(), getMeasuredHeight(), -AndroidUtilities.dp(10), 0);
            canvas.drawCircle(cx, cy, getMeasuredWidth() / 2f - AndroidUtilities.dp(1.5f), PremiumGradient.getInstance().getMainGradientPaint());
            cy = (int) (cy - ((paint.descent() + paint.ascent()) / 2f));
            canvas.drawText("+" + count, cx, cy, paint);
        }
    }
}
