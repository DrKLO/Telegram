package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarsController.findAttribute;
import static org.telegram.ui.Stars.StarsIntroActivity.StarsTransactionView.getPlatformDrawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CompatDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.GiftPremiumBottomSheet;
import org.telegram.ui.Components.Premium.PremiumLockIconView;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.ExplainStarsSheet;
import org.telegram.ui.Stars.StarGiftPatterns;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stars.StarsReactionsSheet;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class GiftSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private UniversalAdapter adapter;
    private List<TLRPC.TL_premiumGiftCodeOption> options;
    private final Runnable closeParentSheet;

    private final long dialogId;
    private final boolean self;
    private final String name;

    private final FrameLayout premiumHeaderView;
    private final LinearLayout starsHeaderView;
    private final ExtendedGridLayoutManager layoutManager;
    private final DefaultItemAnimator itemAnimator;

    private final ArrayList<GiftPremiumBottomSheet.GiftTier> premiumTiers = new ArrayList<>();

    private final int TAB_ALL = 0;
    private final int TAB_LIMITED = 1;
    private final int TAB_IN_STOCK = 2;
    private final ArrayList<CharSequence> tabs = new ArrayList<>();
    private int selectedTab;

    private boolean birthday;

    public GiftSheet(Context context, int currentAccount, long userId, Runnable closeParentSheet) {
        this(context, currentAccount, userId, null, closeParentSheet);
    }

    public GiftSheet(Context context, int currentAccount, long dialogId, List<TLRPC.TL_premiumGiftCodeOption> options, Runnable closeParentSheet) {
        super(context, null, false, false, false, null);

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.self = UserConfig.getInstance(currentAccount).getClientUserId() == dialogId;
        this.options = options;
        this.closeParentSheet = closeParentSheet;
        setBackgroundColor(Theme.getColor(Theme.key_dialogGiftsBackground));
        fixNavigationBar(Theme.getColor(Theme.key_dialogGiftsBackground));

        StarsController.getInstance(currentAccount).loadStarGifts();

        final BackupImageView avatarImageView = new BackupImageView(context);
        final AvatarDrawable avatarDrawable = new AvatarDrawable();

        if (dialogId > 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            this.name = UserObject.getForcedFirstName(user);
            avatarDrawable.setInfo(user);
            avatarImageView.setForUserOrChat(user, avatarDrawable);
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            this.name = chat == null ? "" : chat.title;
            avatarDrawable.setInfo(chat);
            avatarImageView.setForUserOrChat(chat, avatarDrawable);
        }
        topPadding = 0.15f;

        // Gift Premium header
        premiumHeaderView = new FrameLayout(context);

        final FrameLayout topView = new FrameLayout(context);
        topView.setClipChildren(false);
        topView.setClipToPadding(false);

        final StarParticlesView particlesView = StarsIntroActivity.makeParticlesView(context, 70, 0);
        topView.addView(particlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        avatarImageView.setRoundRadius(dp(50));
        topView.addView(avatarImageView, LayoutHelper.createFrame(100, 100, Gravity.CENTER, 0, 32, 0, 24));
        ScaleStateListAnimator.apply(avatarImageView);
        avatarImageView.setOnClickListener(v -> {
            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment == null) return;
            dismiss();
            lastFragment.presentFragment(ProfileActivity.of(dialogId));
        });

        final LinearLayout bottomView = new LinearLayout(context);
        bottomView.setOrientation(LinearLayout.VERTICAL);

        if (!self && dialogId >= 0) {
            premiumHeaderView.addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 150));
        }
        premiumHeaderView.addView(bottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 145, 0, 0));

        final TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setGravity(Gravity.CENTER);
        bottomView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 0));

        final LinkSpanDrawable.LinksTextView subtitleView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        subtitleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        subtitleView.setGravity(Gravity.CENTER);
        bottomView.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 9, 4, 10));

        titleView.setText(getString(R.string.Gift2Premium));
        subtitleView.setText(TextUtils.concat(
            AndroidUtilities.replaceTags(LocaleController.formatString(R.string.Gift2PremiumInfo, name)),
            " ",
            AndroidUtilities.replaceArrows(AndroidUtilities.makeClickable(getString(R.string.Gift2PremiumInfoLink), () -> {
                BaseFragment lastFragment = LaunchActivity.getLastFragment();
                if (lastFragment == null) {
                    return;
                }
                BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                params.transitionFromLeft = true;
                params.allowNestedScroll = false;
                lastFragment.showAsSheet(new PremiumPreviewFragment("gifts"), params);
            }), true)
        ));
        subtitleView.setMaxWidth(HintView2.cutInFancyHalf(subtitleView.getText(), subtitleView.getPaint()));

        // Gift Stars header
        starsHeaderView = new LinearLayout(context);
        starsHeaderView.setOrientation(LinearLayout.VERTICAL);

        if (self || dialogId < 0) {
            starsHeaderView.addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 150));
        }

        final TextView titleStarsView = new TextView(context);
        titleStarsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleStarsView.setTypeface(AndroidUtilities.bold());
        titleStarsView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleStarsView.setGravity(Gravity.CENTER);
        starsHeaderView.addView(titleStarsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 16, 4, 0));

        final LinkSpanDrawable.LinksTextView subtitleStarsView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        subtitleStarsView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        subtitleStarsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleStarsView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        subtitleStarsView.setGravity(Gravity.CENTER);

        titleStarsView.setText(getString(dialogId < 0 ? R.string.Gift2StarsChannel : self ? R.string.Gift2StarsSelf : R.string.Gift2Stars));
        if (self) {
            starsHeaderView.addView(subtitleStarsView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 26, 9, 26, 4));

            final LinkSpanDrawable.LinksTextView subtitleStarsView2 = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            subtitleStarsView2.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            subtitleStarsView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleStarsView2.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            subtitleStarsView2.setGravity(Gravity.CENTER);
            starsHeaderView.addView(subtitleStarsView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 26, 4, 26, 10));

            subtitleStarsView.setText(getString(R.string.Gift2StarsSelfInfo1));
            subtitleStarsView2.setText(getString(R.string.Gift2StarsSelfInfo2));
        } else if (dialogId < 0) {
            starsHeaderView.addView(subtitleStarsView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 26, 9, 26, 4));
            NotificationCenter.listenEmojiLoading(subtitleStarsView);
            subtitleStarsView.setText(Emoji.replaceEmoji(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.Gift2StarsChannelInfo, name)), subtitleStarsView.getPaint().getFontMetricsInt(), false));
        } else {
            starsHeaderView.addView(subtitleStarsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 9, 4, 10));

            subtitleStarsView.setText(TextUtils.concat(
                AndroidUtilities.replaceTags(LocaleController.formatString(R.string.Gift2StarsInfo, name)),
                " ",
                AndroidUtilities.replaceArrows(AndroidUtilities.makeClickable(getString(R.string.Gift2StarsInfoLink), () -> {
                    new ExplainStarsSheet(context).show();
                }), true)
            ));
            subtitleStarsView.setMaxWidth(HintView2.cutInFancyHalf(subtitleStarsView.getText(), subtitleStarsView.getPaint()));
        }

        layoutManager = new ExtendedGridLayoutManager(context, 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (adapter == null || position == 0)
                    return layoutManager.getSpanCount();
                final UItem item = adapter.getItem(position - 1);
                if (item == null || item.spanCount == UItem.MAX_SPAN_COUNT)
                    return layoutManager.getSpanCount();
                return item.spanCount;
            }
        });
        recyclerListView.setPadding(dp(16), 0, dp(16), 0);
        recyclerListView.setClipToPadding(false);
        recyclerListView.setLayoutManager(layoutManager);
        recyclerListView.setSelectorType(9);
        recyclerListView.setSelectorDrawableColor(0);
        itemAnimator = new DefaultItemAnimator() {
            @Override
            protected float animateByScale(View view) {
                return .3f;
            }
        };
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayIncrement(40);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.setOnItemClickListener((view, position) -> {
            final UItem item = adapter.getItem(position - 1);
            if (item == null) return;

            if (item.instanceOf(GiftCell.Factory.class)) {
                if (item.object instanceof GiftPremiumBottomSheet.GiftTier) {
                    final GiftPremiumBottomSheet.GiftTier premiumTier = (GiftPremiumBottomSheet.GiftTier) item.object;
                    new SendGiftSheet(context, currentAccount, premiumTier, this.dialogId, () -> {
                        if (closeParentSheet != null) {
                            closeParentSheet.run();
                        }
                        dismiss();
                    }).show();
                    return;
                } else if (item.object instanceof TL_stars.StarGift) {
                    final TL_stars.StarGift gift = (TL_stars.StarGift) item.object;
                    if (gift.sold_out) {
                        StarsIntroActivity.showSoldOutGiftSheet(context, currentAccount, gift, resourcesProvider);
                        return;
                    }
                    new SendGiftSheet(context, currentAccount, gift, this.dialogId, () -> {
                        if (closeParentSheet != null) {
                            closeParentSheet.run();
                        }
                        dismiss();
                    }).show();
                }
            }
        });

        updatePremiumTiers();
        adapter.update(false);
        updateTitle();

        if (BirthdayController.getInstance(currentAccount).isToday(dialogId)) {
            setBirthday();
        }

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.billingProductDetailsUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starGiftsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starGiftSoldOut);

        actionBar.setTitle(getTitle());
        NotificationCenter.listenEmojiLoading(actionBar.getTitleTextView());
    }

    public GiftSheet setBirthday() {
        return setBirthday(true);
    }

    public GiftSheet setBirthday(boolean b) {
        this.birthday = b;
        adapter.update(false);
        return this;
    }

    private void onGiftSuccess(boolean fromGooglePlay) {
        TLRPC.UserFull full = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
        final TLObject user = MessagesController.getInstance(currentAccount).getUserOrChat(dialogId);
        if (full != null) {
            if (user instanceof TLRPC.User) {
                ((TLRPC.User) user).premium = true;
                MessagesController.getInstance(currentAccount).putUser((TLRPC.User) user, true);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, ((TLRPC.User) user).id, full);
            }
        }

        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment != null && lastFragment.getParentActivity() instanceof LaunchActivity) {
            List<BaseFragment> fragments = new ArrayList<>(((LaunchActivity) lastFragment.getParentActivity()).getActionBarLayout().getFragmentStack());

            INavigationLayout layout = lastFragment.getParentLayout();
            ChatActivity lastChatActivity = null;
            for (BaseFragment fragment : fragments) {
                if (fragment instanceof ChatActivity) {
                    lastChatActivity = (ChatActivity) fragment;
                    if (lastChatActivity.getDialogId() != dialogId) {
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
            if (lastChatActivity == null || lastChatActivity.getDialogId() != dialogId) {
                AndroidUtilities.runOnUIThread(() -> {
                    Bundle args = new Bundle();
                    args.putLong("user_id", dialogId);
                    layout.presentFragment(new ChatActivity(args), true);
                }, 200);
            }
        }

        dismiss();
        if (closeParentSheet != null) {
            closeParentSheet.run();
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.billingProductDetailsUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starGiftsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starGiftSoldOut);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.billingProductDetailsUpdated) {
            updatePremiumTiers();
        } else if (id == NotificationCenter.starGiftsLoaded) {
            if (adapter != null) {
                adapter.update(true);
            }
        } else if (id == NotificationCenter.userInfoDidLoad) {
            if (!isShown()) return;
            if (premiumTiers == null || premiumTiers.isEmpty()) {
                updatePremiumTiers();
                if (adapter != null) {
                    adapter.update(true);
                }
            }
        } else if (id == NotificationCenter.starGiftSoldOut) {
            if (!isShown()) return;
            final TL_stars.StarGift gift = (TL_stars.StarGift) args[0];
            BulletinFactory.of(container, resourcesProvider)
                .createEmojiBulletin(gift.sticker, getString(R.string.Gift2SoldOutTitle), AndroidUtilities.replaceTags(formatPluralStringComma("Gift2SoldOut", gift.availability_total)))
                .show();
            if (adapter != null) {
                adapter.update(true);
            }
        }
    }

    private void updatePremiumTiers() {
        premiumTiers.clear();
//        TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
//        if (userFull != null) {
//            List<QueryProductDetailsParams.Product> products = new ArrayList<>();
//            long pricePerMonthMax = 0;
//            for (int i = userFull.premium_gifts.size() - 1; i >= 0; i--) {
//                final TLRPC.TL_premiumGiftOption option = userFull.premium_gifts.get(i);
//                GiftPremiumBottomSheet.GiftTier giftTier = new GiftPremiumBottomSheet.GiftTier(option);
//                premiumTiers.add(giftTier);
//                if (BuildVars.useInvoiceBilling()) {
//                    if (giftTier.getPricePerMonth() > pricePerMonthMax) {
//                        pricePerMonthMax = giftTier.getPricePerMonth();
//                    }
//                } else if (giftTier.getStoreProduct() != null && BillingController.getInstance().isReady()) {
//                    products.add(QueryProductDetailsParams.Product.newBuilder()
//                            .setProductType(BillingClient.ProductType.INAPP)
//                            .setProductId(giftTier.getStoreProduct())
//                            .build());
//                }
//            }
//            if (BuildVars.useInvoiceBilling()) {
//                for (GiftPremiumBottomSheet.GiftTier tier : premiumTiers) {
//                    tier.setPricePerMonthRegular(pricePerMonthMax);
//                }
//            } else if (!products.isEmpty()) {
//                long startMs = System.currentTimeMillis();
//                BillingController.getInstance().queryProductDetails(products, (billingResult, list) -> {
//                    long pricePerMonthMaxStore = 0;
//
//                    for (ProductDetails details : list) {
//                        for (GiftPremiumBottomSheet.GiftTier giftTier : premiumTiers) {
//                            if (giftTier.getStoreProduct() != null && giftTier.getStoreProduct().equals(details.getProductId())) {
//                                giftTier.setGooglePlayProductDetails(details);
//
//                                if (giftTier.getPricePerMonth() > pricePerMonthMaxStore) {
//                                    pricePerMonthMaxStore = giftTier.getPricePerMonth();
//                                }
//                                break;
//                            }
//                        }
//                    }
//
//                    for (GiftPremiumBottomSheet.GiftTier giftTier : premiumTiers) {
//                        giftTier.setPricePerMonthRegular(pricePerMonthMaxStore);
//                    }
//                    AndroidUtilities.runOnUIThread(() -> {
//                        if (adapter != null) {
//                            adapter.update(false);
//                        }
//                    });
//                });
//            }
//        }
        if (premiumTiers.isEmpty() && options != null && !options.isEmpty()) {
            List<QueryProductDetailsParams.Product> products = new ArrayList<>();
            long pricePerMonthMax = 0;
            for (int i = options.size() - 1; i >= 0; i--) {
                final TLRPC.TL_premiumGiftCodeOption option = options.get(i);
                GiftPremiumBottomSheet.GiftTier giftTier = new GiftPremiumBottomSheet.GiftTier(option);
                premiumTiers.add(giftTier);
                if (BuildVars.useInvoiceBilling()) {
                    if (giftTier.getPricePerMonth() > pricePerMonthMax) {
                        pricePerMonthMax = giftTier.getPricePerMonth();
                    }
                } else if (giftTier.getStoreProduct() != null && BillingController.getInstance().isReady()) {
                    products.add(QueryProductDetailsParams.Product.newBuilder()
                            .setProductType(BillingClient.ProductType.INAPP)
                            .setProductId(giftTier.getStoreProduct())
                            .build());
                }
            }
            if (BuildVars.useInvoiceBilling()) {
                for (GiftPremiumBottomSheet.GiftTier tier : premiumTiers) {
                    tier.setPricePerMonthRegular(pricePerMonthMax);
                }
            } else if (!products.isEmpty()) {
                long startMs = System.currentTimeMillis();
                BillingController.getInstance().queryProductDetails(products, (billingResult, list) -> {
                    long pricePerMonthMaxStore = 0;

                    for (ProductDetails details : list) {
                        for (GiftPremiumBottomSheet.GiftTier giftTier : premiumTiers) {
                            if (giftTier.getStoreProduct() != null && giftTier.getStoreProduct().equals(details.getProductId())) {
                                giftTier.setGooglePlayProductDetails(details);

                                if (giftTier.getPricePerMonth() > pricePerMonthMaxStore) {
                                    pricePerMonthMaxStore = giftTier.getPricePerMonth();
                                }
                                break;
                            }
                        }
                    }

                    for (GiftPremiumBottomSheet.GiftTier giftTier : premiumTiers) {
                        giftTier.setPricePerMonthRegular(pricePerMonthMaxStore);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (adapter != null) {
                            adapter.update(false);
                        }
                    });
                });
            }
        }
        if (premiumTiers.isEmpty()) {
//            if (userFull == null) {
//                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
//                if (user != null) {
//                    MessagesController.getInstance(currentAccount).loadUserInfo(user, true, 0);
//                }
//            }
            BoostRepository.loadGiftOptions(currentAccount, null, paymentOptions -> {
                if (getContext() == null || !isShown()) return;
                options = BoostRepository.filterGiftOptions(paymentOptions, 1);
                options = BoostRepository.filterGiftOptionsByBilling(options);
                if (!options.isEmpty()) {
                    updatePremiumTiers();
                    if (adapter != null) {
                        adapter.update(true);
                    }
                }
            });
        }
    }

    @Override
    protected CharSequence getTitle() {
        if (self) {
            return getString(R.string.Gift2TitleSelf1);
        }
        return Emoji.replaceEmoji(LocaleController.formatString(R.string.Gift2User, name), null, false);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (!self && dialogId >= 0) {
            items.add(UItem.asCustom(premiumHeaderView));
            if (premiumTiers != null && !premiumTiers.isEmpty()) {
                for (GiftPremiumBottomSheet.GiftTier tier : premiumTiers) {
                    items.add(GiftCell.Factory.asPremiumGift(tier));
                }
            } else {
                items.add(UItem.asFlicker(1, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(2, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(3, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            }
        }

        final StarsController s = StarsController.getInstance(currentAccount);
        final ArrayList<TL_stars.StarGift> gifts = birthday ? s.birthdaySortedGifts : s.sortedGifts;
        if (!MessagesController.getInstance(currentAccount).stargiftsBlocked && !gifts.isEmpty()) {
            items.add(UItem.asCustom(starsHeaderView));
            final TreeSet<Long> prices = new TreeSet<>();
            for (int i = 0; i < gifts.size(); ++i) {
                final TL_stars.StarGift gift = gifts.get(i);
                prices.add(gift.stars);
            }

            final ArrayList<CharSequence> tabs = new ArrayList<>();
            tabs.add(getString(R.string.Gift2TabAll));
            tabs.add(getString(R.string.Gift2TabLimited));
            tabs.add(getString(R.string.Gift2TabInStock));
            final Iterator<Long> priceIt = prices.iterator();
            final ArrayList<Long> pricesArray = new ArrayList<>();
            while (priceIt.hasNext()) {
                final long price = priceIt.next();
                tabs.add(StarsIntroActivity.replaceStarsWithPlain("⭐️ " + LocaleController.formatNumber(price, ','), .8f));
                pricesArray.add(price);
            }
            items.add(Tabs.Factory.asTabs(1, tabs, selectedTab, tab -> {
                if (selectedTab == tab) return;
                selectedTab = tab;
                itemAnimator.endAnimations();
                adapter.update(true);
            }));

            final long selectedPrice = selectedTab - 3 >= 0 && selectedTab - 3 < pricesArray.size() ? pricesArray.get(selectedTab - 3) : 0;
            for (int i = 0; i < gifts.size(); ++i) {
                final TL_stars.StarGift gift = gifts.get(i);
                if (
                    selectedTab == TAB_ALL ||
                    selectedTab == TAB_LIMITED && gift.limited ||
                    selectedTab == TAB_IN_STOCK && !gift.sold_out ||
                    selectedTab >= 3 && gift.stars == selectedPrice
                ) {
                    items.add(GiftCell.Factory.asStarGift(selectedTab, gift));
                }
            }
            if (s.giftsLoading) {
                items.add(UItem.asFlicker(4, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(5, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(6, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            }
            items.add(UItem.asSpace(dp(40)));
        }
    }

    public static class GiftCell extends FrameLayout implements ItemOptions.ScrimView {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;

        private final FrameLayout card;
        private final CardBackground cardBackground;
        private final Ribbon ribbon;
        private final AvatarDrawable avatarDrawable;
        private final BackupImageView avatarView;
        private final BackupImageView imageView;
        private final FrameLayout.LayoutParams imageViewLayoutParams;
        private final PremiumLockIconView lockView;

        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView priceView;

        private Runnable cancel;

        public GiftCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            ScaleStateListAnimator.apply(this, .04f, 1.5f);

            card = new FrameLayout(context);
            card.setBackground(cardBackground = new CardBackground(card, resourcesProvider, true));
            addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            ribbon = new Ribbon(context);
            addView(ribbon, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 2, 1, 0));

            imageView = new BackupImageView(context);
            imageView.getImageReceiver().setAutoRepeat(0);
            card.addView(imageView, imageViewLayoutParams = LayoutHelper.createFrame(96, 96, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 2, 0, 2));

            lockView = new PremiumLockIconView(context, PremiumLockIconView.TYPE_GIFT_LOCK, resourcesProvider);
            lockView.setImageReceiver(imageView.getImageReceiver());
            card.addView(lockView, LayoutHelper.createFrame(30, 30, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 38, 0, 0));

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setGravity(Gravity.CENTER);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleView.setTypeface(AndroidUtilities.bold());
            card.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 93, 0, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            subtitleView.setGravity(Gravity.CENTER);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            card.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 111, 0, 0));

            priceView = new TextView(context);
            priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            priceView.setTypeface(AndroidUtilities.bold());
            priceView.setPadding(dp(10), 0, dp(10), 0);
            priceView.setGravity(Gravity.CENTER);
            priceView.setBackground(new StarsBackground(false));
            priceView.setTextColor(0xFF3391D4);
            card.addView(priceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 133, 0, 11));

            avatarDrawable = new AvatarDrawable();
            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(20));
            avatarView.setVisibility(View.GONE);
            card.addView(avatarView, LayoutHelper.createFrame(20, 20, Gravity.TOP | Gravity.LEFT, 2, 2, 2, 2));
        }

        private GiftPremiumBottomSheet.GiftTier lastTier;

        public void setPremiumGift(GiftPremiumBottomSheet.GiftTier tier) {
            final int months = tier.getMonths();
            int type = months <= 3 ? 2 : months <= 6 ? 3 : 4;
            if (lastTier != tier) {
                cancel = StarsIntroActivity.setGiftImage(imageView, imageView.getImageReceiver(), type);
                if (cancel != null) {
                    cancel.run();
                    cancel = null;
                }
            }

            cardBackground.setBackdrop(null);
            cardBackground.setPattern(null);
            titleView.setText(LocaleController.formatPluralString("Gift2Months", months));
            subtitleView.setText(getString(R.string.TelegramPremiumShort));
            titleView.setVisibility(View.VISIBLE);
            subtitleView.setVisibility(View.VISIBLE);
            imageView.setTranslationY(-dp(8));
            avatarView.setVisibility(View.GONE);
            lockView.setVisibility(View.GONE);

            if (tier.getDiscount() > 0) {
                ribbon.setVisibility(View.VISIBLE);
                ribbon.setColors(0xFFD94FFF, 0xFF826DFF);
                ribbon.setText(12, LocaleController.formatString(R.string.GiftPremiumOptionDiscount, tier.getDiscount()), true);
            } else {
                ribbon.setVisibility(View.GONE);
            }

            priceView.setPadding(dp(10), 0, dp(10), 0);
            priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            priceView.setText(tier.getFormattedPrice());
            priceView.setBackground(Theme.createRoundRectDrawable(dp(13), 0x193391D4));
            priceView.setTextColor(0xFF3391D4);
            ((MarginLayoutParams) priceView.getLayoutParams()).topMargin = dp(133);

            lastTier = tier;
            lastDocument = null;
        }

        private TLRPC.Document lastDocument;
        private long lastDocumentId;
        private void setSticker(TLRPC.Document document, Object parentObject) {
            if (document == null) {
                imageView.clearImage();
                lastDocument = null;
                lastDocumentId = 0;
                return;
            }

            if (lastDocument == document) return;
            lastDocument = document;
            lastDocumentId = document.id;

            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(100));
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 0.3f);

            imageView.setImage(
                ImageLocation.getForDocument(document), "100_100",
                ImageLocation.getForDocument(photoSize, document), "100_100",
                svgThumb,
                parentObject
            );
        }

        private void setSticker(long documentId, Object parentObject) {
            if (documentId == 0) {
                imageView.clearImage();
                lastDocument = null;
                lastDocumentId = 0;
                return;
            }

            if (lastDocumentId == documentId) return;
            lastDocumentId = documentId;

            AnimatedEmojiDrawable.getDocumentFetcher(currentAccount).fetchDocument(documentId, document -> {
                if (document == null || lastDocumentId != document.id) {
                    return;
                }

                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(100));
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 0.3f);

                imageView.setImage(
                        ImageLocation.getForDocument(document), "100_100",
                        ImageLocation.getForDocument(photoSize, document), "100_100",
                        svgThumb,
                        parentObject
                );
            });
        }

        public void setStarsGift(TL_stars.StarGift gift) {
            if (cancel != null) {
                cancel.run();
                cancel = null;
            }

            setSticker(gift.getDocument(), gift);
            cardBackground.setBackdrop(findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class));
            cardBackground.setPattern(findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class));
            titleView.setVisibility(View.GONE);
            subtitleView.setVisibility(View.GONE);
            imageView.setTranslationY(0);
            lockView.setVisibility(View.GONE);

            if (gift.limited && gift.availability_remains <= 0) {
                ribbon.setVisibility(View.VISIBLE);
                ribbon.setColor(Theme.getColor(Theme.key_gift_ribbon_soldout, resourcesProvider));
                ribbon.setBackdrop(null);
                ribbon.setText(LocaleController.getString(R.string.Gift2SoldOut), true);
            } else if (gift.limited) {
                ribbon.setVisibility(View.VISIBLE);
                ribbon.setColor(Theme.getColor(Theme.key_gift_ribbon, resourcesProvider));
                ribbon.setBackdrop(null);
                ribbon.setText(getString(R.string.Gift2LimitedRibbon), false);
            } else {
                ribbon.setVisibility(View.GONE);
            }

            avatarView.setVisibility(View.GONE);

            priceView.setPadding(dp(8), 0, dp(10), 0);
            priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            priceView.setText(StarsIntroActivity.replaceStarsWithPlain("XTR " + LocaleController.formatNumber(gift.stars, ','), .71f));
            priceView.setBackground(new StarsBackground(gift instanceof TL_stars.TL_starGiftUnique));
            priceView.setTextColor(0xFFBF7600);
            ((MarginLayoutParams) priceView.getLayoutParams()).topMargin = dp(103);

            lastTier = null;
        }

        private TL_stars.SavedStarGift lastUserGift;

        public void setStarsGift(TL_stars.SavedStarGift userGift, boolean noprice) {
            if (cancel != null) {
                cancel.run();
                cancel = null;
            }

            setSticker(userGift.gift.getDocument(), userGift);
            final TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(userGift.gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            cardBackground.setBackdrop(backdrop);
            cardBackground.setPattern(findAttribute(userGift.gift.attributes, TL_stars.starGiftAttributePattern.class));
            titleView.setVisibility(View.GONE);
            subtitleView.setVisibility(View.GONE);
            imageView.setTranslationY(0);
            lockView.setWaitingImage();
            lockView.setBlendWithColor(backdrop != null ? Theme.multAlpha(backdrop.center_color | 0xFF000000, .75f) : null);

            lockView.setVisibility(View.VISIBLE);
            if (lastUserGift == userGift) {
                lockView.animate()
                    .alpha(userGift.unsaved ? 1f : 0f)
                    .scaleX(userGift.unsaved ? 1f : .4f)
                    .scaleY(userGift.unsaved ? 1f : .4f)
                    .setDuration(350)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
            } else {
                lockView.setAlpha(userGift.unsaved ? 1f : 0f);
                lockView.setScaleX(userGift.unsaved ? 1f : 0.4f);
                lockView.setScaleY(userGift.unsaved ? 1f : 0.4f);
            }

            final boolean unique = userGift.gift instanceof TL_stars.TL_starGiftUnique;
            if (unique) {
                ribbon.setVisibility(View.VISIBLE);
                ribbon.setColor(Theme.getColor(Theme.key_gift_ribbon, resourcesProvider));
                ribbon.setBackdrop(backdrop);
                ribbon.setText(LocaleController.formatString(R.string.Gift2Limited1OfRibbon, AndroidUtilities.formatWholeNumber(userGift.gift.availability_total, 0)), true);
            } else if (userGift.gift.limited) {
                ribbon.setVisibility(View.VISIBLE);
                ribbon.setColor(Theme.getColor(Theme.key_gift_ribbon, resourcesProvider));
                ribbon.setBackdrop(null);
                ribbon.setText(LocaleController.formatString(R.string.Gift2Limited1OfRibbon, AndroidUtilities.formatWholeNumber(userGift.gift.availability_total, 0)), true);
            } else {
                ribbon.setVisibility(View.GONE);
            }

            if (unique) {
                avatarView.setVisibility(View.GONE);
            } else if (userGift.name_hidden) {
                avatarView.setVisibility(View.VISIBLE);
                CombinedDrawable iconDrawable = getPlatformDrawable("anonymous");
                iconDrawable.setIconSize(dp(16), dp(16));
                avatarView.setImageDrawable(iconDrawable);
            } else {
                final long dialogId = DialogObject.getPeerDialogId(userGift.from_id);
                if (dialogId > 0) {
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                    if (user != null) {
                        avatarView.setVisibility(View.VISIBLE);
                        avatarDrawable.setInfo(user);
                        avatarView.setForUserOrChat(user, avatarDrawable);
                    } else {
                        avatarView.setVisibility(View.GONE);
                    }
                } else {
                    final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    if (chat != null) {
                        avatarView.setVisibility(View.VISIBLE);
                        avatarDrawable.setInfo(chat);
                        avatarView.setForUserOrChat(chat, avatarDrawable);
                    } else {
                        avatarView.setVisibility(View.GONE);
                    }
                }
            }

            if (noprice) {
                priceView.setVisibility(View.GONE);
                imageViewLayoutParams.topMargin = dp(8);
                imageViewLayoutParams.bottomMargin = dp(8);
            } else {
                priceView.setVisibility(View.VISIBLE);
                imageViewLayoutParams.topMargin = 0;
                imageViewLayoutParams.bottomMargin = 0;
            }
            if (unique) {
                priceView.setPadding(dp(8), 0, dp(8), 0);
                priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                priceView.setText(getString(R.string.Gift2PriceUnique));
            } else {
                priceView.setPadding(dp(8), 0, dp(10), 0);
                priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                priceView.setText(StarsIntroActivity.replaceStarsWithPlain("XTR " + LocaleController.formatNumber(Math.max(userGift.gift.stars, userGift.convert_stars > 0 ? userGift.convert_stars : userGift.gift.convert_stars), ','), .66f));
            }
            priceView.setBackground(new StarsBackground(unique));
            priceView.setTextColor(unique ? 0xFFFFFFFF : 0xFFBF7600);
            ((MarginLayoutParams) priceView.getLayoutParams()).topMargin = dp(103);

            lastUserGift = userGift;
            lastTier = null;
        }

        public static class Factory extends UItem.UItemFactory<GiftCell> {
            static { setup(new Factory()); }

            @Override
            public GiftCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new GiftCell(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                if (item.object instanceof GiftPremiumBottomSheet.GiftTier) {
                    ((GiftCell) view).setPremiumGift((GiftPremiumBottomSheet.GiftTier) item.object);
                } else if (item.object instanceof TL_stars.StarGift) {
                    TL_stars.StarGift gift = (TL_stars.StarGift) item.object;
                    ((GiftCell) view).setStarsGift(gift);
                } else if (item.object instanceof TL_stars.SavedStarGift) {
                    TL_stars.SavedStarGift gift = (TL_stars.SavedStarGift) item.object;
                    ((GiftCell) view).setStarsGift(gift, item.accent);
                }
            }

            public static UItem asPremiumGift(GiftPremiumBottomSheet.GiftTier tier) {
                final UItem item = UItem.ofFactory(Factory.class).setSpanCount(1);
                item.object = tier;
                return item;
            }

            public static UItem asStarGift(int tab, TL_stars.StarGift gift) {
                final UItem item = UItem.ofFactory(Factory.class).setSpanCount(1);
                item.intValue = tab;
                item.object = gift;
                return item;
            }

            public static UItem asStarGift(int tab, TL_stars.SavedStarGift gift) {
                return asStarGift(tab, gift, false);
            }
            public static UItem asStarGift(int tab, TL_stars.SavedStarGift gift, boolean noprice) {
                final UItem item = UItem.ofFactory(Factory.class).setSpanCount(1);
                item.intValue = tab;
                item.object = gift;
                item.accent = noprice;
                return item;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                if (a.accent != b.accent) return false;
                if (a.object != null || b.object != null) {
                    if (a.object instanceof GiftPremiumBottomSheet.GiftTier) {
                        return a.object == b.object;
                    } else if (a.object instanceof TL_stars.StarGift && b.object instanceof TL_stars.StarGift) {
                        final TL_stars.StarGift ag = (TL_stars.StarGift) a.object;
                        final TL_stars.StarGift bg = (TL_stars.StarGift) b.object;
                        return ag.id == bg.id;
                    } else if (a.object instanceof TL_stars.SavedStarGift && b.object instanceof TL_stars.SavedStarGift) {
                        final TL_stars.SavedStarGift ag = (TL_stars.SavedStarGift) a.object;
                        final TL_stars.SavedStarGift bg = (TL_stars.SavedStarGift) b.object;
                        return ag.gift.id == bg.gift.id;
                    }
                }
                return (
                    a.intValue == b.intValue &&
                    a.checked == b.checked &&
                    a.longValue == b.longValue &&
                    TextUtils.equals(a.text, b.text)
                );
            }
        }

        private final Rect cardBackgroundPadding = new Rect();
        @Override
        public void getBounds(RectF bounds) {
            cardBackground.getPadding(cardBackgroundPadding);
            bounds.set(cardBackgroundPadding.left, cardBackgroundPadding.top, getWidth() - cardBackgroundPadding.right, getHeight() - cardBackgroundPadding.bottom);
        }
    }

    public static class RibbonDrawable extends CompatDrawable {

        private Text text;
        private Path path = new Path();

        public static void fillRibbonPath(Path path, float s) {
            path.rewind();
            path.moveTo(dp(s * 46.83f), dp(s * 24.5f));
            path.lineTo(dp(s * 23.5f), dp(s * 1.17f));
            path.cubicTo(dp(s * 22.75f), dp(s * 0.42f), dp(s * 21.73f), 0f, dp(s * 20.68f), 0f);
            path.cubicTo(dp(s * 19.62f), 0f, dp(s * 2.73f), dp(s * 0.05f), dp(s * 1.55f), dp(s * 0.05f));
            path.cubicTo(dp(s * 0.36f), dp(s * 0.05f), dp(s * -0.23f), dp(s * 1.4885f), dp(s * 0.6f), dp(s * 2.32f));
            path.lineTo(dp(s * 45.72f), dp(s * 47.44f));
            path.cubicTo(dp(s * 46.56f), dp(s * 48.28f), dp(s * 48f), dp(s * 47.68f), dp(s * 48f), dp(s * 46.5f));
            path.cubicTo(dp(s * 48f), dp(s * 45.31f), dp(s * 48f), dp(s * 28.38f), dp(s * 48f), dp(s * 27.32f));
            path.cubicTo(dp(s * 48f), dp(s * 26.26f), dp(s * 47.5f), dp(s * 25.24f), dp(s * 46.82f), dp(s * 24.5f));
            path.close();
        }

        public RibbonDrawable(View view, float scale) {
            super(view);
            fillRibbonPath(path, scale);

            paint.setColor(0xFFF55951);
            paint.setPathEffect(new CornerPathEffect(dp(2.33f)));
        }

        public void setColor(int color) {
            paint.setShader(null);
            paint.setColor(color);
        }

        public void setColors(int color1, int color2) {
            paint.setShader(new LinearGradient(0, 0, dp(48), dp(48), new int[]{ color1, color2 }, new float[] { 0, 1 }, Shader.TileMode.CLAMP));
        }

        public void setBackdrop(TL_stars.starGiftAttributeBackdrop backdrop, boolean swap) {
            if (backdrop == null) {
                paint.setShader(null);
            } else {
                paint.setShader(new LinearGradient(0, 0, dp(48), dp(48), new int[]{
                    Theme.adaptHSV(backdrop.center_color | 0xFF000000, swap ? +0.07f : +0.05f, swap ? -0.15f : -0.1f),
                    Theme.adaptHSV(backdrop.edge_color | 0xFF000000, swap ? +0.07f : +0.05f, swap ? -0.15f : -0.1f)
                }, new float[] { swap ? 1 : 0, swap ? 0 : 1 }, Shader.TileMode.CLAMP));
            }
        }

        public void setText(int textSizeDp, CharSequence text, boolean bold) {
            this.text = new Text(text, textSizeDp, bold ? AndroidUtilities.bold() : null);
        }

        private int textColor = 0xFFFFFFFF;
        public void setTextColor(int textColor) {
            this.textColor = textColor;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.drawPath(path, paint);
            if (text != null) {
                canvas.save();
                canvas.rotate(45, getBounds().width() / 2f + dp(6), getBounds().height() / 2f - dp(6));
                final float scale = Math.min(1, dp(40) / text.getCurrentWidth());
                canvas.scale(scale, scale, getBounds().width() / 2f + dp(6), getBounds().height() / 2f - dp(6));
                text.draw(canvas, getBounds().width() / 2f + dp(6) - text.getWidth() / 2f, getBounds().height() / 2f - dp(5), textColor, 1f);
                canvas.restore();
            }
            canvas.restore();
        }
    }

    public static class Ribbon extends View {

        private RibbonDrawable drawable = new RibbonDrawable(this, 1.0f);

        public Ribbon(Context context) {
            super(context);
        }

        public void setText(CharSequence text, boolean bold) {
            drawable.setText(bold ? 10 : 11, text, bold);
        }

        public void setText(int textSizeDp, CharSequence text, boolean bold) {
            drawable.setText(textSizeDp, text, bold);
        }

        public void setColor(int color) {
            drawable.setColor(color);
        }

        public void setColors(int color1, int color2) {
            drawable.setColors(color1, color2);
        }

        public void setBackdrop(TL_stars.starGiftAttributeBackdrop backdrop) {
            drawable.setBackdrop(backdrop, false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(dp(48), dp(48));
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            drawable.setBounds(0, 0, getWidth(), getHeight());
            drawable.draw(canvas);
        }
    }

    public static class StarsBackground extends Drawable {

        private final boolean white;
        public StarsBackground(boolean white) {
            this.white = white;
            backgroundPaint.setColor(white ? 0x40FFFFFF : 0x40E8AB02);
        }

        public final RectF rectF = new RectF();
        public final Path path = new Path();
        public final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public final StarsReactionsSheet.Particles particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 25);

        @Override
        public void draw(@NonNull Canvas canvas) {
            final float r = Math.min(getBounds().width(), getBounds().height()) / 2f;
            rectF.set(getBounds());
            path.rewind();
            path.addRoundRect(rectF, r, r, Path.Direction.CW);
            canvas.drawPath(path, backgroundPaint);
            canvas.save();
            canvas.clipPath(path);
            particles.setBounds(rectF);
            particles.process();
            particles.draw(canvas, white ? 0xFFFFFFFF : 0xFFF0981D);
            canvas.restore();
            invalidateSelf();
        }

        @Override
        public void setAlpha(int alpha) {
            backgroundPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            backgroundPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    public TLRPC.Document getStarGiftDocument(String emoji) {
        TLRPC.TL_messages_stickerSet set;
        TLRPC.Document document = null;

        final String packName = "RestrictedEmoji";
        set = MediaDataController.getInstance(currentAccount).getStickerSetByName(packName);
        if (set == null) {
            set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(packName);
        }
        if (set != null) {
            for (int i = 0; i < set.packs.size(); ++i) {
                TLRPC.TL_stickerPack pack = set.packs.get(i);
                if (TextUtils.equals(pack.emoticon, emoji) && !pack.documents.isEmpty()) {
                    long documentId = pack.documents.get(0);
                    for (int j = 0; j < set.documents.size(); ++j) {
                        TLRPC.Document d = set.documents.get(j);
                        if (d != null && d.id == documentId) {
                            document = d;
                            break;
                        }
                    }
                    break;
                }
            }
            if (document == null && !set.documents.isEmpty()) {
                document = set.documents.get(0);
            }
        }
        return document;
    }

    public static Runnable setStarGiftImage(View view, ImageReceiver imageReceiver, String emoji) {
        final boolean[] played = new boolean[1];
        final int currentAccount = imageReceiver.getCurrentAccount();
        Runnable setImage = () -> {
            TLRPC.TL_messages_stickerSet set;
            TLRPC.Document document = null;

            final String packName = "RestrictedEmoji";
            set = MediaDataController.getInstance(currentAccount).getStickerSetByName(packName);
            if (set == null) {
                set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(packName);
            }
            if (set != null) {
                for (int i = 0; i < set.packs.size(); ++i) {
                    TLRPC.TL_stickerPack pack = set.packs.get(i);
                    if (TextUtils.equals(pack.emoticon, emoji) && !pack.documents.isEmpty()) {
                        long documentId = pack.documents.get(0);
                        for (int j = 0; j < set.documents.size(); ++j) {
                            TLRPC.Document d = set.documents.get(j);
                            if (d != null && d.id == documentId) {
                                document = d;
                                break;
                            }
                        }
                        break;
                    }
                }
                if (document == null && !set.documents.isEmpty()) {
                    document = set.documents.get(0);
                }
            }

            if (document != null) {
                imageReceiver.setAllowStartLottieAnimation(true);
                imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
                    @Override
                    public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
                        if (set) {
                            RLottieDrawable drawable = imageReceiver.getLottieAnimation();
                            if (drawable != null && !played[0]) {
                                drawable.setCurrentFrame(0, false);
                                AndroidUtilities.runOnUIThread(drawable::restart);
                                played[0] = true;
                            }
                        }
                    }
                });
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 0.3f);
                imageReceiver.setAutoRepeat(0);
                imageReceiver.setImage(ImageLocation.getForDocument(document), String.format(Locale.US, "%d_%d_nr", 160, 160), svgThumb, "tgs", set, 1);
            } else {
                MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(packName, false, set == null);
            }
        };
        setImage.run();
        final Runnable cancel1 = NotificationCenter.getInstance(currentAccount).listen(view, NotificationCenter.didUpdatePremiumGiftStickers, args -> setImage.run());
        final Runnable cancel2 = NotificationCenter.getInstance(currentAccount).listen(view, NotificationCenter.diceStickersDidLoad, args -> setImage.run());
        return () -> {
            cancel1.run();
            cancel2.run();
        };
    }

    public static class CardBackground extends Drawable {

        private final View view;
        private final Theme.ResourcesProvider resourcesProvider;
        public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clipPath = new Path();

        private TL_stars.starGiftAttributeBackdrop backdrop;

        private int gradientRadius;
        private RadialGradient gradient;
        private final Matrix gradientMatrix = new Matrix();
        private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable pattern;

        private boolean selected;
        private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private AnimatedFloat animatedSelected = new AnimatedFloat(this::invalidate, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        public CardBackground(View view, Theme.ResourcesProvider resourcesProvider, boolean withShadow) {
            this.view = view;
            this.resourcesProvider = resourcesProvider;
            pattern = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(view, dp(28)) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    if (CardBackground.this.getCallback() != null) {
                        CardBackground.this.getCallback().invalidateDrawable(CardBackground.this);
                    }
                }
            };
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    pattern.attach();
                }
                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    pattern.detach();
                }
            });
            if (view.isAttachedToWindow()) pattern.attach();
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            if (withShadow) {
                paint.setShadowLayer(dp(1.66f), 0, dp(.33f), Theme.getColor(Theme.key_dialogCardShadow, resourcesProvider));
            }
            selectedPaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            final float selected = animatedSelected.set(this.selected);
            rect.set(bounds);
            rect.inset(dp(3.33f), dp(4));
            if (backdrop != null) {
                final int radius = lerp(Math.min(bounds.width(), bounds.height()), Math.max(bounds.width(), bounds.height()), 0.35f);
                if (gradient == null || gradientRadius != radius) {
                    gradient = new RadialGradient(0, 0, gradientRadius = radius, new int[] { backdrop.center_color | 0xFF000000, backdrop.edge_color | 0xFF000000 }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
                }
                gradientMatrix.reset();
                gradientMatrix.postTranslate(bounds.centerX(), Math.min(dp(50), bounds.centerY()));
                gradient.setLocalMatrix(gradientMatrix);
                paint.setShader(gradient);
            } else {
                paint.setShader(null);
            }
            canvas.drawRoundRect(rect, dp(11), dp(11), paint);
            if (backdrop != null && !pattern.isEmpty()) {
                pattern.setColor(backdrop.pattern_color | 0xFF000000);
                canvas.save();
                clipPath.rewind();
                clipPath.addRoundRect(rect, dp(11), dp(11), Path.Direction.CW);
                canvas.clipPath(clipPath);
                canvas.translate(bounds.centerX(), bounds.centerY());
                final float s = lerp(1.0f, 0.925f, selected);
                canvas.scale(s, s);
                StarGiftPatterns.drawPattern(canvas, StarGiftPatterns.TYPE_GIFT, pattern, bounds.width(), bounds.height(), 1.0f, 1.0f);
                canvas.restore();
            }

            if (selected > 0) {
                selectedPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                selectedPaint.setStrokeWidth(dpf2(2.33f));
                AndroidUtilities.rectTmp.set(rect);
                final float b = lerp(-dpf2(2.33f), dp(5.166f), selected);
                AndroidUtilities.rectTmp.inset(b, b);
                final float r = lerp(dpf2(11), dpf2(6.66f), selected);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, selectedPaint);
            }
        }

        @Override
        public boolean getPadding(@NonNull Rect padding) {
            padding.set(
                dp(3.33f),
                dp(4),
                dp(3.33f),
                dp(4)
            );
            return true;
        }

        @Override
        public void setAlpha(int alpha) {}
        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        public void invalidate() {
            view.invalidate();
            if (getCallback() != null) {
                getCallback().invalidateDrawable(this);
            }
        }

        public void setBackdrop(TL_stars.starGiftAttributeBackdrop backdrop) {
            if (this.backdrop != backdrop) {
                gradient = null;
            }
            this.backdrop = backdrop;
            invalidate();
        }

        public void setPattern(TL_stars.starGiftAttributePattern pattern) {
            if (pattern == null) {
                this.pattern.set((Drawable) null, false);
            } else {
                this.pattern.set(pattern.document, false);
            }
        }

        public void setSelected(boolean selected, boolean animated) {
            if (this.selected == selected) return;
            this.selected = selected;
            if (!animated) {
                animatedSelected.force(selected);
            }
            invalidate();
        }
    }

    public static class Tabs extends HorizontalScrollView {

        private final Theme.ResourcesProvider resourcesProvider;

        private final LinearLayout layout;
        private int selected;
        private AnimatedFloat animatedSelected;
        private final ArrayList<TextView> tabs = new ArrayList<>();

        private final RectF flooredRect = new RectF(), ceiledRect = new RectF();
        private final RectF selectedRect = new RectF();
        private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Tabs(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.resourcesProvider = resourcesProvider;

            layout = new LinearLayout(context) {
                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    selectedPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_dialogGiftsTabText), .1f));
                    final float selected = animatedSelected.set(Tabs.this.selected);

                    int flooredIndex = Utilities.clamp((int) Math.floor(selected), tabs.size() - 1, 0);
                    int ceiledIndex = Utilities.clamp((int) Math.ceil(selected), tabs.size() - 1, 0);
                    if (flooredIndex < tabs.size()) {
                        setBounds(flooredRect, tabs.get(flooredIndex));
                    } else if (ceiledIndex < tabs.size()) {
                        setBounds(flooredRect, tabs.get(ceiledIndex));
                    } else {
                        flooredRect.set(0,0,0,0);
                    }
                    if (ceiledIndex < tabs.size()) {
                        setBounds(ceiledRect, tabs.get(ceiledIndex));
                    } else if (flooredIndex < tabs.size()) {
                        setBounds(ceiledRect, tabs.get(flooredIndex));
                    } else {
                        ceiledRect.set(0,0,0,0);
                    }
                    lerp(flooredRect, ceiledRect, selected - flooredIndex, selectedRect);

                    final float r = selectedRect.height() / 2f;
                    canvas.drawRoundRect(selectedRect, r, r, selectedPaint);

                    super.dispatchDraw(canvas);
                }

                private final void setBounds(RectF rect, View view) {
                    rect.set(view.getLeft() + dp(5), view.getTop(), view.getRight() - dp(5), view.getBottom());
                }
            };
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setPadding(0, dp(8), 0, dp(12));
            addView(layout);

            setHorizontalScrollBarEnabled(false);

            animatedSelected = new AnimatedFloat(layout, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        }

        private int lastId = Integer.MIN_VALUE;
        public void set(int id, ArrayList<CharSequence> tabs, int selected, Utilities.Callback<Integer> whenTabSelected) {
            final boolean animated = lastId == id;
            lastId = id;

            if (this.tabs.size() != tabs.size()) {
                int a = 0;
                for (int i = 0; i < this.tabs.size(); ++i) {
                    CharSequence tabText = a < tabs.size() ? tabs.get(a) : null;
                    if (tabText == null) {
                        layout.removeView(this.tabs.remove(i));
                        i--;
                    } else {
                        this.tabs.get(i).setText(tabText);
                    }
                    a++;
                }
                for (; a < tabs.size(); ++a) {
                    final TextView tab = new TextView(getContext());
                    tab.setGravity(Gravity.CENTER);
                    tab.setText(tabs.get(a));
                    tab.setTypeface(AndroidUtilities.bold());
                    tab.setTextColor(Theme.blendOver(Theme.getColor(Theme.key_dialogGiftsBackground), Theme.getColor(Theme.key_dialogGiftsTabText)));
                    tab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    tab.setPadding(dp(16), 0, dp(16), 0);
                    ScaleStateListAnimator.apply(tab, 0.075f, 1.4f);
                    layout.addView(tab, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 26));
                    this.tabs.add(tab);
                }
            }

            this.selected = selected;
            if (!animated) {
                animatedSelected.set(selected, true);
            }
            layout.invalidate();

            for (int i = 0; i < this.tabs.size(); ++i) {
                final int tabIndex = i;
                this.tabs.get(i).setOnClickListener(v -> {
                    TextView tab = this.tabs.get(tabIndex);
                    smoothScrollTo(tab.getLeft() - tab.getWidth() / 2, 0);
                    if (whenTabSelected != null) {
                        whenTabSelected.run(tabIndex);
                    }
                });
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                heightMeasureSpec
            );
        }

        public static class Factory extends UItem.UItemFactory<Tabs> {
            static { setup(new Factory()); }

            @Override
            public Tabs createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new Tabs(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                ((Tabs) view).set(item.id, (ArrayList<CharSequence>) item.object, item.intValue, (Utilities.Callback<Integer>) item.object2);
            }

            public static UItem asTabs(int id, ArrayList<CharSequence> tabs, int selected, Utilities.Callback<Integer> whenTabSelected) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = tabs;
                item.intValue = selected;
                item.object2 = whenTabSelected;
                return item;
            }

            private static boolean eq(ArrayList<CharSequence> a, ArrayList<CharSequence> b) {
                if (a == b) return true;
                if (a == null && b == null) return true;
                if (a == null || b == null) return false;
                if (a.size() != b.size()) return false;
                for (int i = 0; i < a.size(); ++i) {
                    if (!TextUtils.equals(a.get(i), b.get(i)))
                        return false;
                }
                return true;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return (
                    a.id == b.id &&
                    eq((ArrayList<CharSequence>) a.object, (ArrayList<CharSequence>) b.object)
                );
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a.intValue == b.intValue && a.object2 == b.object2 && equals(a, b);
            }
        }

    }

}
