package org.telegram.ui.Components.Premium;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class GiftPremiumBottomSheet extends BottomSheetWithRecyclerListView {
    private PremiumGradient.PremiumGradientTools gradientTools;
    private PremiumGradient.PremiumGradientTools outlineGradient;
    private PremiumButtonView premiumButtonView;
    private PremiumGiftTierCell dummyCell;

    private List<GiftTier> giftTiers = new ArrayList<>();
    private int selectedTierIndex = 0;

    private int totalGradientHeight;

    private int rowsCount;
    private int headerRow;
    private int tiersStartRow;
    private int tiersEndRow;
    private int footerRow;
    private int buttonRow;

    private TLRPC.User user;

    @SuppressLint("NotifyDataSetChanged")
    public GiftPremiumBottomSheet(BaseFragment fragment, TLRPC.User user) {
        super(fragment, false, true);
        this.user = user;

        gradientTools = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, null, null);
        gradientTools.exactly = true;
        gradientTools.x1 = 0;
        gradientTools.y1 = 0f;
        gradientTools.x2 = 0;
        gradientTools.y2 = 1f;
        gradientTools.cx = 0;
        gradientTools.cy = 0;

        outlineGradient = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, Theme.key_premiumGradient3, Theme.key_premiumGradient4);
        outlineGradient.paint.setStyle(Paint.Style.STROKE);
        outlineGradient.paint.setStrokeWidth(AndroidUtilities.dp(1.5f));

        dummyCell = new PremiumGiftTierCell(getContext());

        TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(user.id);
        if (userFull != null) {
            List<QueryProductDetailsParams.Product> products = new ArrayList<>();
            long pricePerMonthMax = 0;
            for (TLRPC.TL_premiumGiftOption option : userFull.premium_gifts) {
                GiftTier giftTier = new GiftTier(option);
                giftTiers.add(giftTier);
                if (BuildVars.useInvoiceBilling()) {
                    if (giftTier.getPricePerMonth() > pricePerMonthMax) {
                        pricePerMonthMax = giftTier.getPricePerMonth();
                    }
                } else if (giftTier.giftOption.store_product != null && BillingController.getInstance().isReady()) {
                    products.add(QueryProductDetailsParams.Product.newBuilder()
                                    .setProductType(BillingClient.ProductType.INAPP)
                                    .setProductId(giftTier.giftOption.store_product)
                            .build());
                }
            }
            if (BuildVars.useInvoiceBilling()) {
                for (GiftTier tier : giftTiers) {
                    tier.setPricePerMonthRegular(pricePerMonthMax);
                }
            } else if (!products.isEmpty()) {
                long startMs = System.currentTimeMillis();
                BillingController.getInstance().queryProductDetails(products, (billingResult, list) -> {
                    long pricePerMonthMaxStore = 0;

                    for (ProductDetails details : list) {
                        for (GiftTier giftTier : giftTiers) {
                            if (giftTier.giftOption.store_product != null && giftTier.giftOption.store_product.equals(details.getProductId())) {
                                giftTier.setGooglePlayProductDetails(details);

                                if (giftTier.getPricePerMonth() > pricePerMonthMaxStore) {
                                    pricePerMonthMaxStore = giftTier.getPricePerMonth();
                                }
                                break;
                            }
                        }
                    }

                    for (GiftTier giftTier : giftTiers) {
                        giftTier.setPricePerMonthRegular(pricePerMonthMaxStore);
                    }
                    AndroidUtilities.runOnUIThread(()-> {
                        recyclerListView.getAdapter().notifyDataSetChanged();
                        updateButtonText(System.currentTimeMillis() - startMs > 1000);
                    });
                });
            }
        }

        if (!giftTiers.isEmpty()) {
            selectedTierIndex = 0;
            updateButtonText(false);
        }

        headerRow = rowsCount++;
        tiersStartRow = rowsCount;
        rowsCount += giftTiers.size();
        tiersEndRow = rowsCount;
        footerRow = rowsCount++;
        buttonRow = rowsCount++;

        recyclerListView.setOnItemClickListener((view, position) -> {
            if (view instanceof PremiumGiftTierCell) {
                PremiumGiftTierCell giftTierCell = (PremiumGiftTierCell) view;
                selectedTierIndex = giftTiers.indexOf(giftTierCell.tier);
                updateButtonText(true);
                giftTierCell.setChecked(true, true);

                for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                    View ch = recyclerListView.getChildAt(i);
                    if (ch instanceof PremiumGiftTierCell) {
                        PremiumGiftTierCell otherCell = (PremiumGiftTierCell) ch;
                        if (otherCell.tier != giftTierCell.tier) {
                            otherCell.setChecked(false, true);
                        }
                    }
                }

                for (int i = 0; i < recyclerListView.getHiddenChildCount(); i++) {
                    View ch = recyclerListView.getHiddenChildAt(i);
                    if (ch instanceof PremiumGiftTierCell) {
                        PremiumGiftTierCell otherCell = (PremiumGiftTierCell) ch;
                        if (otherCell.tier != giftTierCell.tier) {
                            otherCell.setChecked(false, true);
                        }
                    }
                }

                for (int i = 0; i < recyclerListView.getCachedChildCount(); i++) {
                    View ch = recyclerListView.getCachedChildAt(i);
                    if (ch instanceof PremiumGiftTierCell) {
                        PremiumGiftTierCell otherCell = (PremiumGiftTierCell) ch;
                        if (otherCell.tier != giftTierCell.tier) {
                            otherCell.setChecked(false, true);
                        }
                    }
                }

                for (int i = 0; i < recyclerListView.getAttachedScrapChildCount(); i++) {
                    View ch = recyclerListView.getAttachedScrapChildAt(i);
                    if (ch instanceof PremiumGiftTierCell) {
                        PremiumGiftTierCell otherCell = (PremiumGiftTierCell) ch;
                        if (otherCell.tier != giftTierCell.tier) {
                            otherCell.setChecked(false, true);
                        }
                    }
                }
            }
        });
        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        Path path = new Path();
        recyclerListView.setSelectorTransformer(canvas -> {
            path.rewind();
            Rect selectorRect = recyclerListView.getSelectorRect();
            AndroidUtilities.rectTmp.set(selectorRect.left + AndroidUtilities.dp(20), selectorRect.top + AndroidUtilities.dp(3), selectorRect.right - AndroidUtilities.dp(20), selectorRect.bottom - AndroidUtilities.dp(3));
            path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(12), AndroidUtilities.dp(12), Path.Direction.CW);
            canvas.clipPath(path);
        });
    }

    private void updateButtonText(boolean animated) {
        if (LocaleController.isRTL) {
            animated = false;
        }
        if (!BuildVars.useInvoiceBilling() && (!BillingController.getInstance().isReady() || giftTiers.get(selectedTierIndex).googlePlayProductDetails == null)) {
            premiumButtonView.setButton(LocaleController.getString(R.string.Loading), v -> {}, !LocaleController.isRTL);
            premiumButtonView.setFlickerDisabled(true);
            return;
        }
        premiumButtonView.setButton(LocaleController.formatString(R.string.GiftSubscriptionFor, giftTiers.get(selectedTierIndex).getFormattedPrice()), v -> onGiftPremium(), animated);
        premiumButtonView.setFlickerDisabled(false);
    }

    private void onGiftSuccess(boolean fromGooglePlay) {
        TLRPC.UserFull full = MessagesController.getInstance(currentAccount).getUserFull(user.id);
        if (full != null) {
            user.premium = true;
            MessagesController.getInstance(currentAccount).putUser(user, true);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, user.id, full);
        }

        if (getBaseFragment() != null) {
            List<BaseFragment> fragments = new ArrayList<>(((LaunchActivity) getBaseFragment().getParentActivity()).getActionBarLayout().getFragmentStack());

            INavigationLayout layout = getBaseFragment().getParentLayout();
            ChatActivity lastChatActivity = null;
            for (BaseFragment fragment : fragments) {
                if (fragment instanceof ChatActivity) {
                    lastChatActivity = (ChatActivity) fragment;
                    if (lastChatActivity.getDialogId() != user.id) {
                        fragment.removeSelfFromStack();
                    }
                } else if (fragment instanceof ProfileActivity) {
                    if (fromGooglePlay && layout.getLastFragment() == fragment) {
                        fragment.finishFragment();
                    } else {
                        fragment.removeSelfFromStack();
                    }
                }
            }
            if (lastChatActivity == null || lastChatActivity.getDialogId() != user.id) {
                Bundle args = new Bundle();
                args.putLong("user_id", user.id);
                layout.presentFragment(new ChatActivity(args), true);
            }
        }
    }

    private void onGiftPremium() {
        GiftTier tier = giftTiers.get(selectedTierIndex);
        if (BuildVars.useInvoiceBilling()) {
            if (getBaseFragment().getParentActivity() instanceof LaunchActivity) {
                Uri uri = Uri.parse(tier.giftOption.bot_url);
                if (uri.getHost().equals("t.me")) {
                    if (!uri.getPath().startsWith("/$") && !uri.getPath().startsWith("/invoice/")) {
                        ((LaunchActivity) getBaseFragment().getParentActivity()).setNavigateToPremiumBot(true);
                    } else {
                        ((LaunchActivity) getBaseFragment().getParentActivity()).setNavigateToPremiumGiftCallback(()-> onGiftSuccess(false));
                    }
                }
                Browser.openUrl(getBaseFragment().getParentActivity(), tier.giftOption.bot_url);
                dismiss();
            }
        } else {
            if (BillingController.getInstance().isReady() && tier.googlePlayProductDetails != null) {
                TLRPC.TL_inputStorePaymentGiftPremium giftPremium = new TLRPC.TL_inputStorePaymentGiftPremium();
                giftPremium.user_id = MessagesController.getInstance(currentAccount).getInputUser(user);
                ProductDetails.OneTimePurchaseOfferDetails offerDetails = tier.googlePlayProductDetails.getOneTimePurchaseOfferDetails();
                giftPremium.currency = offerDetails.getPriceCurrencyCode();
                giftPremium.amount = (long) ((offerDetails.getPriceAmountMicros() / Math.pow(10, 6)) * Math.pow(10, BillingController.getInstance().getCurrencyExp(giftPremium.currency)));

                BillingController.getInstance().addResultListener(tier.giftOption.store_product, billingResult -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        AndroidUtilities.runOnUIThread(()-> onGiftSuccess(true));
                    }
                });

                TLRPC.TL_payments_canPurchasePremium req = new TLRPC.TL_payments_canPurchasePremium();
                req.purpose = giftPremium;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(()->{
                    if (response instanceof TLRPC.TL_boolTrue) {
                        BillingController.getInstance().launchBillingFlow(getBaseFragment().getParentActivity(), AccountInstance.getInstance(currentAccount), giftPremium, Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(tier.googlePlayProductDetails)
                                .build()));
                    } else if (error != null) {
                        AlertsCreator.processError(currentAccount, error, getBaseFragment(), req);
                    }
                }));
            }
        }
    }

    @Override
    public void onViewCreated(FrameLayout containerView) {
        super.onViewCreated(containerView);

        premiumButtonView = new PremiumButtonView(getContext(), true);

        FrameLayout buttonContainer = new FrameLayout(getContext());
        buttonContainer.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
        buttonContainer.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));
    }

    @Override
    protected void onPreMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onPreMeasure(widthMeasureSpec, heightMeasureSpec);
        measureGradient(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(heightMeasureSpec));
    }

    private void measureGradient(int w, int h) {
        int yOffset = 0;
        for (int i = 0; i < giftTiers.size(); i++) {
            dummyCell.bind(giftTiers.get(i));
            dummyCell.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
            giftTiers.get(i).yOffset = yOffset;
            yOffset += dummyCell.getMeasuredHeight();
        }

        totalGradientHeight = yOffset;
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.GiftTelegramPremiumTitle);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter() {
        return new RecyclerListView.SelectionAdapter() {
            private final static int VIEW_TYPE_HEADER = 0,
                VIEW_TYPE_TIER = 1,
                VIEW_TYPE_FOOTER = 2,
                VIEW_TYPE_BUTTON = 3;

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == VIEW_TYPE_TIER;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                switch (viewType) {
                    default:
                    case VIEW_TYPE_HEADER:
                        view = new PremiumGiftHeaderCell(getContext());
                        break;
                    case VIEW_TYPE_TIER:
                        AtomicReference<Float> progressRef = new AtomicReference<>(0f);
                        PremiumGiftTierCell premiumGiftTierCell = new PremiumGiftTierCell(getContext()) {
                            @Override
                            protected void dispatchDraw(Canvas canvas) {
                                if (discountView.getVisibility() == VISIBLE) {
                                    AndroidUtilities.rectTmp.set(discountView.getLeft(), discountView.getTop(), discountView.getRight(), discountView.getBottom());
                                    gradientTools.gradientMatrix(0, 0, getMeasuredWidth(), totalGradientHeight, 0, -tier.yOffset);
                                    canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), gradientTools.paint);
                                }

                                float progress = progressRef.get();
                                int alpha = outlineGradient.paint.getAlpha();
                                outlineGradient.paint.setAlpha((int) (progress * alpha));
                                AndroidUtilities.rectTmp.set(AndroidUtilities.dp(20), AndroidUtilities.dp(3), getWidth() - AndroidUtilities.dp(20), getHeight() - AndroidUtilities.dp(3));
                                outlineGradient.gradientMatrix(0, 0, getMeasuredWidth(), getMeasuredHeight(), 0, 0);
                                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(12), AndroidUtilities.dp(12), outlineGradient.paint);
                                outlineGradient.paint.setAlpha(alpha);

                                super.dispatchDraw(canvas);
                            }
                        };
                        premiumGiftTierCell.setCirclePaintProvider(obj -> {
                            gradientTools.gradientMatrix(0, 0, premiumGiftTierCell.getMeasuredWidth(), totalGradientHeight, 0, -premiumGiftTierCell.tier.yOffset);
                            return gradientTools.paint;
                        });
                        premiumGiftTierCell.setProgressDelegate(progress -> {
                            progressRef.set(progress);
                            premiumGiftTierCell.invalidate();
                        });
                        view = premiumGiftTierCell;
                        break;
                    case VIEW_TYPE_FOOTER:
                        TextInfoPrivacyCell privacyCell = new TextInfoPrivacyCell(getContext());
                        privacyCell.setTopPadding(28);
                        privacyCell.getTextView().setGravity(Gravity.CENTER_HORIZONTAL);
                        String str = LocaleController.getString(R.string.GiftPremiumListFeaturesAndTerms);
                        int startIndex = str.indexOf('*'), lastIndex = str.lastIndexOf('*');
                        if (startIndex != -1 && lastIndex != -1) {
                            str = str.substring(0, startIndex) + str.substring(startIndex + 1, lastIndex) + str.substring(lastIndex + 1);
                            SpannableString span = new SpannableString(str);
                            span.setSpan(new LinkSpan(), startIndex, lastIndex - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            privacyCell.setText(span);
                        } else {
                            privacyCell.setText(str);
                        }
                        privacyCell.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
                        view = privacyCell;
                        break;
                    case VIEW_TYPE_BUTTON:
                        view = new View(getContext()) {
                            @Override
                            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(68), MeasureSpec.EXACTLY));
                            }
                        };
                        break;
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (position == headerRow) {
                    ((PremiumGiftHeaderCell) holder.itemView).bind(user);
                } else if (position >= tiersStartRow && position < tiersEndRow) {
                    PremiumGiftTierCell giftTierCell = (PremiumGiftTierCell) holder.itemView;
                    giftTierCell.bind(giftTiers.get(position - tiersStartRow));
                    giftTierCell.setChecked(position - tiersStartRow == selectedTierIndex, false);
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (position == headerRow) {
                    return VIEW_TYPE_HEADER;
                } else if (position >= tiersStartRow && position < tiersEndRow) {
                    return VIEW_TYPE_TIER;
                } else if (position == footerRow) {
                    return VIEW_TYPE_FOOTER;
                } else if (position == buttonRow) {
                    return VIEW_TYPE_BUTTON;
                }
                return VIEW_TYPE_HEADER;
            }

            @Override
            public int getItemCount() {
                return rowsCount;
            }
        };
    }

    private final class LinkSpan extends ClickableSpan {

        @Override
        public void onClick(View widget) {
            getBaseFragment().presentFragment(new PremiumPreviewFragment("profile"));
            dismiss();
        }

        @Override
        public void updateDrawState(TextPaint p) {
            super.updateDrawState(p);
            p.setUnderlineText(false);
        }
    }

    public final static class GiftTier {
        public final TLRPC.TL_premiumGiftOption giftOption;
        private int discount;
        private long pricePerMonth;

        private long pricePerMonthRegular;
        private ProductDetails googlePlayProductDetails;

        public int yOffset;

        public GiftTier(TLRPC.TL_premiumGiftOption giftOption) {
            this.giftOption = giftOption;
        }

        public ProductDetails getGooglePlayProductDetails() {
            return googlePlayProductDetails;
        }

        public void setGooglePlayProductDetails(ProductDetails googlePlayProductDetails) {
            this.googlePlayProductDetails = googlePlayProductDetails;
        }

        public void setPricePerMonthRegular(long pricePerMonthRegular) {
            this.pricePerMonthRegular = pricePerMonthRegular;
        }

        public int getMonths() {
            return giftOption.months;
        }

        public int getDiscount() {
            if (discount == 0) {
                if (getPricePerMonth() == 0) {
                    return 0;
                }

                if (pricePerMonthRegular != 0) {
                    discount = (int) ((1.0 - getPricePerMonth() / (double) pricePerMonthRegular) * 100);

                    if (discount == 0) {
                        discount = -1;
                    }
                }
            }
            return discount;
        }

        public long getPricePerMonth() {
            if (pricePerMonth == 0) {
                long price = getPrice();
                if (price != 0) {
                    pricePerMonth = price / giftOption.months;
                }
            }
            return pricePerMonth;
        }

        public String getFormattedPricePerMonth() {
            if (BuildVars.useInvoiceBilling() || giftOption.store_product == null) {
                return BillingController.getInstance().formatCurrency(getPricePerMonth(), getCurrency());
            }

            return googlePlayProductDetails == null ? "" : BillingController.getInstance().formatCurrency(getPricePerMonth(), getCurrency(), 6);
        }

        public String getFormattedPrice() {
            if (BuildVars.useInvoiceBilling() || giftOption.store_product == null) {
                return BillingController.getInstance().formatCurrency(getPrice(), getCurrency());
            }

            return googlePlayProductDetails == null ? "" : BillingController.getInstance().formatCurrency(getPrice(), getCurrency(), 6);
        }

        public long getPrice() {
            if (BuildVars.useInvoiceBilling() || giftOption.store_product == null) {
                return giftOption.amount;
            }
            return googlePlayProductDetails == null ? 0 : googlePlayProductDetails.getOneTimePurchaseOfferDetails().getPriceAmountMicros();
        }

        public String getCurrency() {
            if (BuildVars.useInvoiceBilling() || giftOption.store_product == null) {
                return giftOption.currency;
            }
            return googlePlayProductDetails == null ? "" : googlePlayProductDetails.getOneTimePurchaseOfferDetails().getPriceCurrencyCode();
        }
    }
}
