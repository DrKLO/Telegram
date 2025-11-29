package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarsController.findAttribute;
import static org.telegram.ui.Stars.StarsIntroActivity.StarsTransactionView.getPlatformDrawable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
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
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.FrameTickScheduler;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.AccountFrozenAlert;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BatchParticlesDrawHelper;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CompatDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EffectsTextView;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.GiftPremiumBottomSheet;
import org.telegram.ui.Components.Premium.PremiumLockIconView;
import org.telegram.ui.Components.Premium.PremiumPreviewBottomSheet;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Shaker;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.ExplainStarsSheet;
import org.telegram.ui.Stars.StarGiftPatterns;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stars.StarsReactionsSheet;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class GiftSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private UniversalAdapter adapter;
    private List<TLRPC.TL_premiumGiftCodeOption> options;
    private final Runnable closeParentSheet;
    private TLRPC.DisallowedGiftsSettings userSettings;

    private final long dialogId;
    private final boolean self;
    private final String name;

    private final StarsIntroActivity.StarsBalanceView balanceView;
    private final FrameLayout topView;
    private final FrameLayout premiumHeaderView;
    private final LinearLayout starsHeaderView;
    private final ExtendedGridLayoutManager layoutManager;
    private final DefaultItemAnimator itemAnimator;

    private final LinkSpanDrawable.LinksTextView subtitleStarsView;
    private final LinkSpanDrawable.LinksTextView subtitleCollectiblesStarsView;

    private final ArrayList<GiftPremiumBottomSheet.GiftTier> premiumTiers = new ArrayList<>();

    private final StarsController.GiftsList myGifts;
    private int TAB_ALL = -1;
    private int TAB_MY_GIFTS = -1;
    private int TAB_LIMITED = -1;
    private int TAB_IN_STOCK = -1;
    private int TAB_RESALE = -1;
    private int TAB_COLLECTIBLES = -1;
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
        myGifts = StarsController.getInstance(currentAccount).getProfileGiftsList(UserConfig.getInstance(currentAccount).getClientUserId());

        StarsController.getInstance(currentAccount).loadStarGifts();

        final BackupImageView avatarImageView = new BackupImageView(context);
        final AvatarDrawable avatarDrawable = new AvatarDrawable();

        if (dialogId > 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            this.name = UserObject.getForcedFirstName(user);
            avatarDrawable.setInfo(user);
            avatarImageView.setForUserOrChat(user, avatarDrawable);

            final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
            userSettings = dialogId != UserConfig.getInstance(currentAccount).getClientUserId() && userFull != null ? userFull.disallowed_stargifts : null;
            if (userFull == null) {
                MessagesController.getInstance(currentAccount).loadFullUser(user, 0, true);
            }
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            this.name = chat == null ? "" : chat.title;
            avatarDrawable.setInfo(chat);
            avatarImageView.setForUserOrChat(chat, avatarDrawable);
        }
        topPadding = 0.10f;

        balanceView = new StarsIntroActivity.StarsBalanceView(context, currentAccount, resourcesProvider);
        ScaleStateListAnimator.apply(balanceView);
        balanceView.setOnClickListener(v -> {
            if (balanceView.lastBalance <= 0) return;
            final BaseFragment lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment != null) {
                final BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
                bottomSheetParams.transitionFromLeft = true;
                bottomSheetParams.allowNestedScroll = false;
                lastFragment.showAsSheet(new StarsIntroActivity(), bottomSheetParams);
            }
        });

        // Gift Premium header
        premiumHeaderView = new FrameLayout(context);

        topView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(120), MeasureSpec.EXACTLY)
                );
            }
        };
        topView.setClipChildren(false);
        topView.setClipToPadding(false);

        final StarParticlesView particlesView = StarsIntroActivity.makeParticlesView(context, 70, 0);
        topView.addView(particlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        avatarImageView.setRoundRadius(dp(42));
        topView.addView(avatarImageView, LayoutHelper.createFrame(84, 84, Gravity.CENTER, 0, 15, 0, 17));
        ScaleStateListAnimator.apply(avatarImageView);
        avatarImageView.setOnClickListener(v -> {
            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment == null) return;
            dismiss();
            lastFragment.presentFragment(ProfileActivity.of(dialogId));
        });
        topView.addView(balanceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, -3, -10, 0));

        final LinearLayout bottomView = new LinearLayout(context);
        bottomView.setOrientation(LinearLayout.VERTICAL);

        premiumHeaderView.addView(bottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        final TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setGravity(Gravity.CENTER);
        bottomView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 0));
        titleView.setMaxWidth(HintView2.cutInFancyHalf(titleView.getText(), titleView.getPaint()));

        final LinkSpanDrawable.LinksTextView subtitleView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        subtitleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setLineSpacing(dp(2.33f), 1.0f);
        bottomView.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 4, 4, 12));

        titleView.setText(getString(R.string.Gift2Premium));
        subtitleView.setText(TextUtils.concat(
            AndroidUtilities.replaceTags(formatString(R.string.Gift2PremiumInfo, name)),
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

        final TextView titleStarsView = new TextView(context);
        titleStarsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleStarsView.setTypeface(AndroidUtilities.bold());
        titleStarsView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleStarsView.setGravity(Gravity.CENTER);
        starsHeaderView.addView(titleStarsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 0));

        subtitleStarsView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (getAlpha() < 0.95f) return false;
                return super.dispatchTouchEvent(event);
            }
        };
        subtitleStarsView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        subtitleStarsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleStarsView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        subtitleStarsView.setGravity(Gravity.CENTER);

        subtitleCollectiblesStarsView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (getAlpha() < 0.95f) return false;
                return super.dispatchTouchEvent(event);
            }
        };
        subtitleCollectiblesStarsView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        subtitleCollectiblesStarsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleCollectiblesStarsView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        subtitleCollectiblesStarsView.setGravity(Gravity.CENTER);
        subtitleCollectiblesStarsView.setAlpha(0.0f);
        subtitleCollectiblesStarsView.setScaleX(0.85f);
        subtitleCollectiblesStarsView.setScaleY(0.85f);

        FrameLayout subtitleLayout = new FrameLayout(context);
        subtitleLayout.addView(subtitleStarsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 26, 0, 26, 0));
        subtitleLayout.addView(subtitleCollectiblesStarsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 26, 0, 26, 0));

        titleStarsView.setText(getString(dialogId < 0 ? R.string.Gift2StarsChannel : self ? R.string.Gift2StarsSelf : R.string.Gift2Stars));
        if (self) {
            starsHeaderView.addView(subtitleLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 4));

            final LinkSpanDrawable.LinksTextView subtitleStarsView2 = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            subtitleStarsView2.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            subtitleStarsView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleStarsView2.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            subtitleStarsView2.setGravity(Gravity.CENTER);
            starsHeaderView.addView(subtitleStarsView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 26, 4, 26, 6));

            subtitleStarsView.setText(getString(R.string.Gift2StarsSelfInfo1));
            subtitleStarsView2.setText(getString(R.string.Gift2StarsSelfInfo2));
        } else if (dialogId < 0) {
            starsHeaderView.addView(subtitleLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 4));
            NotificationCenter.listenEmojiLoading(subtitleStarsView);
            subtitleStarsView.setText(Emoji.replaceEmoji(AndroidUtilities.replaceTags(formatString(R.string.Gift2StarsChannelInfo, name)), subtitleStarsView.getPaint().getFontMetricsInt(), false));
        } else {
            starsHeaderView.addView(subtitleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 6));

            final StarsController.GiftsList list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
            final Runnable setSubtitle = () -> {
                for (int a = 0; a < 2; ++a) {
                    final SpannableStringBuilder subtitle = new SpannableStringBuilder();
                    subtitle.append(AndroidUtilities.replaceTags(a == 1 ? getString(R.string.Gift2StarsCollectibleInfo) : formatString(R.string.Gift2StarsInfo, name)));
                    subtitle.append(" ");
                    final HashSet<Long> emojiDocumentIds = new HashSet<>();
                    final HashSet<TLRPC.Document> emojiDocuments = new HashSet<>();
                    for (int i = 0; i < list.gifts.size() && emojiDocumentIds.size() < 3; ++i) {
                        final TL_stars.SavedStarGift savedStarGift = list.gifts.get(i);
                        if (savedStarGift != null && savedStarGift.gift != null) {
                            final TLRPC.Document document = savedStarGift.gift.getDocument();
                            if (document != null && !emojiDocumentIds.contains(document.id)) {
                                emojiDocuments.add(document);
                                emojiDocumentIds.add(document.id);
                            }
                        }
                    }
                    if (emojiDocuments.size() > 0) {
                        final SpannableStringBuilder link = new SpannableStringBuilder();
                        link.append(formatString(R.string.Gift2StarsInfoProfileLink, DialogObject.getShortName(dialogId)).replaceAll(" ", " "));
                        link.append(" ");
                        for (final TLRPC.Document document : emojiDocuments) {
                            link.append("\u2060e");
                            link.setSpan(new AnimatedEmojiSpan(document, subtitleStarsView.getPaint().getFontMetricsInt()), link.length() - 1, link.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        link.append(" >");
                        subtitle.append(AndroidUtilities.replaceArrows(AndroidUtilities.makeClickable(link, () -> {
                            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                            if (lastFragment == null) return;
                            dismiss();
                            if (closeParentSheet != null) {
                                closeParentSheet.run();
                            }
                            final Bundle args = new Bundle();
                            args.putLong("user_id", dialogId);
                            args.putBoolean("open_gifts", true);
                            lastFragment.presentFragment(new ProfileActivity(args));
                        }), true));
                    } else {
                        subtitle.append(AndroidUtilities.replaceArrows(AndroidUtilities.makeClickable(getString(R.string.Gift2StarsInfoLink), () -> {
                            new ExplainStarsSheet(context).show();
                        }), true));
                    }

                    final LinkSpanDrawable.LinksTextView textView = a == 0 ? subtitleStarsView : subtitleCollectiblesStarsView;
                    textView.setText(subtitle);
                    textView.setMaxWidth(HintView2.cutInFancyHalf(textView.getText(), textView.getPaint()));
                }
            };
            setSubtitle.run();
            subtitleStarsView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    setSubtitle.run();
                }
                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {}
            });

            if (list.gifts.size() < 3) {
                list.load();
            }
            NotificationCenter.getInstance(currentAccount).listen(subtitleStarsView, NotificationCenter.starUserGiftsLoaded, args -> {
                if (args[1] == list) {
                    setSubtitle.run();
                }
            });
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
        recyclerListView.setClipChildren(false);
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
                    }) {
                        @Override
                        protected BulletinFactory getParentBulletinFactory() {
                            return BulletinFactory.of(GiftSheet.this.container, GiftSheet.this.resourcesProvider);
                        }
                    }.show();
                    return;
                } else if (item.object instanceof TL_stars.StarGift) {
                    final TL_stars.StarGift gift = (TL_stars.StarGift) item.object;
                    if (myGifts != null && selectedTab == TAB_MY_GIFTS) {
                        TL_stars.SavedStarGift savedStarGift = null;
                        for (TL_stars.SavedStarGift g : myGifts.gifts) {
                            if (g.gift.id == gift.id) {
                                savedStarGift = g;
                                break;
                            }
                        }
                        if (savedStarGift == null) {
                            return;
                        }
                        StarGiftSheet sheet = new StarGiftSheet(getContext(), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), resourcesProvider) {
                            @Override
                            public BulletinFactory getBulletinFactory() {
                                return BulletinFactory.of(GiftSheet.this.container, GiftSheet.this.resourcesProvider);
                            }
                        }.set(savedStarGift, null);
                        sheet.openTransferAlert(dialogId, progress -> {
                            progress.init();
                            sheet.doTransfer(dialogId, err -> {
                                progress.end();
                                if (closeParentSheet != null) {
                                    closeParentSheet.run();
                                }
                                GiftSheet.this.dismiss();
                                if (err != null) {
                                    AndroidUtilities.runOnUIThread(() -> sheet.getBulletinFactory().showForError(err));
                                    return;
                                }
                                dismiss();
                            });
                        });
                        return;
                    }
                    if (item.accent && gift.availability_resale > 0) {
                        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment == null) return;
                        final BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
                        bottomSheetParams.transitionFromLeft = true;
                        bottomSheetParams.allowNestedScroll = false;

                        ViewTreeObserver observer = container.getViewTreeObserver();
                        ViewTreeObserver.OnPreDrawListener onPreDrawListener = () -> false;

                        final ResaleGiftsFragment fragment = new ResaleGiftsFragment(dialogId, gift.title, gift.id, resourcesProvider) {
                            @Override
                            public void onPause() {
                                super.onPause();
                                observer.removeOnPreDrawListener(onPreDrawListener);
                            }

                            @Override
                            public void onResume() {
                                super.onResume();
                                observer.addOnPreDrawListener(onPreDrawListener);
                            }
                        };
                        fragment.setCloseParentSheet(() -> {
                            if (closeParentSheet != null) {
                                closeParentSheet.run();
                            }
                            dismiss();
                        });
                        lastFragment.showAsSheet(fragment, bottomSheetParams);
                        return;
                    }
                    if (gift.auction) {
                        AuctionJoinSheet.show(context, resourcesProvider, currentAccount, dialogId, gift.id, () -> {
                            if (closeParentSheet != null) {
                                closeParentSheet.run();
                            }
                            dismiss();
                        });
                        return;
                    }

                    if (gift.sold_out) {
                        StarsIntroActivity.showSoldOutGiftSheet(context, currentAccount, gift, resourcesProvider);
                        return;
                    }
                    if (gift.limited_per_user && gift.per_user_remains <= 0) {
                        BulletinFactory.of(GiftSheet.this.container, GiftSheet.this.resourcesProvider)
                            .createSimpleMultiBulletin(gift.getDocument(), AndroidUtilities.replaceTags(formatPluralStringComma("Gift2PerUserLimit", gift.per_user_total)))
                            .show();
                        return;
                    }
                    final Runnable openSendSheet = () -> {
                        new SendGiftSheet(context, currentAccount, gift, this.dialogId, () -> {
                            if (closeParentSheet != null) {
                                closeParentSheet.run();
                            }
                            dismiss();
                        }, gift.limited && userSettings != null && userSettings.disallow_limited_stargifts, gift.limited && userSettings != null && userSettings.disallow_unique_stargifts) {
                            @Override
                            protected BulletinFactory getParentBulletinFactory() {
                                return BulletinFactory.of(GiftSheet.this.container, GiftSheet.this.resourcesProvider);
                            }
                        }.show();
                    };

                    if (gift.locked_until_date > ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
                        final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                        progressDialog.showDelayed(500);

                        final TL_stars.checkCanSendGift req = new TL_stars.checkCanSendGift();
                        req.gift_id = gift.id;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                            progressDialog.dismiss();

                            if (res instanceof TL_stars.checkCanSendGiftResultOk) {
                                openSendSheet.run();
                            } else if (res instanceof TL_stars.checkCanSendGiftResultFail) {
                                final TL_stars.checkCanSendGiftResultFail fail = (TL_stars.checkCanSendGiftResultFail) res;
                                final AlertDialog dialog = new AlertDialog.Builder(getContext(), resourcesProvider)
                                    .setTitle(getString(R.string.GiftLocked))
                                    .setMessage(MessageObject.formatTextWithEntities(fail.reason, false))
                                    .setPositiveButton(getString(R.string.OK), null)
                                    .show();
                                final TextView textView = dialog.getMessageTextView();
                                if (textView instanceof EffectsTextView) {
                                    ((EffectsTextView) textView).setOnLinkPressListener(link -> {
                                        dialog.dismiss();
                                        if (closeParentSheet != null) {
                                            closeParentSheet.run();
                                        }
                                        dismiss();
                                        link.onClick(textView);
                                    });
                                }
                            } else if (err != null) {
                                BulletinFactory.of(GiftSheet.this.container, GiftSheet.this.resourcesProvider)
                                    .showForError(err);
                            }
                        }));
                        return;
                    }
                    if (gift.require_premium && !UserConfig.getInstance(currentAccount).isPremium()) {
                        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                        if (fragment == null) return;
                        PremiumPreviewBottomSheet sheet = new PremiumPreviewBottomSheet(fragment, currentAccount, null, null, gift, resourcesProvider);
                        BackupImageView icon = new BackupImageView(getContext());
                        AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable drawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(icon, dp(160), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE);
                        icon.setImageDrawable(drawable);
                        icon.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                            @Override
                            public void onViewAttachedToWindow(@NonNull View v) {
                                drawable.attach();
                            }
                            @Override
                            public void onViewDetachedFromWindow(@NonNull View v) {
                                drawable.detach();
                            }
                        });
                        drawable.set(gift.getDocument(), false);
                        sheet.overrideTitleIcon = icon;
                        sheet.show();
                        drawable.play();
                        return;
                    }

                    openSendSheet.run();
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
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);

        actionBar.setTitle(getTitle());
        NotificationCenter.listenEmojiLoading(actionBar.getTitleTextView());
    }

    private boolean shownCollectiblesInfo;
    public void setShowCollectiblesInfo(boolean show) {
        if (show == shownCollectiblesInfo) return;

        shownCollectiblesInfo = show;
        subtitleStarsView.animate()
            .alpha(!show ? 1.0f : 0.0f)
            .scaleX(!show ? 1.0f : 0.85f)
            .scaleY(!show ? 1.0f : 0.85f)
            .setDuration(380)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .start();
        subtitleCollectiblesStarsView.animate()
            .alpha(show ? 1.0f : 0.0f)
            .scaleX(show ? 1.0f : 0.85f)
            .scaleY(show ? 1.0f : 0.85f)
            .setDuration(380)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .start();
    }

    @Override
    public void show() {
        if (MessagesController.getInstance(currentAccount).isFrozen()) {
            AccountFrozenAlert.show(currentAccount);
            return;
        }
        if (userSettings != null && userSettings.disallow_premium_gifts && userSettings.disallow_unique_stargifts && userSettings.disallow_limited_stargifts && userSettings.disallow_unlimited_stargifts) {
            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment != null) {
                BulletinFactory.of(lastFragment).createSimpleBulletin(R.raw.error, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserDisallowedGifts, DialogObject.getShortName(dialogId)))).show();
            }
            return;
        }
        super.show();
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
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
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
            if ((long) args[0] == dialogId) {
                if (dialogId > 0) {
                    final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
                    userSettings = dialogId != UserConfig.getInstance(currentAccount).getClientUserId() && userFull != null ? userFull.disallowed_stargifts : null;
                    if (userSettings != null && userSettings.disallow_premium_gifts && userSettings.disallow_unique_stargifts && userSettings.disallow_limited_stargifts && userSettings.disallow_unlimited_stargifts) {
                        dismiss();
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            BulletinFactory.of(lastFragment).createSimpleBulletin(R.raw.error, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserDisallowedGifts, DialogObject.getShortName(dialogId)))).show();
                        }
                        return;
                    }
                    if (adapter != null) {
                        adapter.update(true);
                    }
                }
            }
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
                .createEmojiBulletin(gift.sticker, getString(R.string.Gift2SoldOutTitle), AndroidUtilities.replaceTags(formatPluralStringComma("Gift2SoldOutCount", gift.availability_total)))
                .show();
            if (adapter != null) {
                adapter.update(true);
            }
        } else if (id == NotificationCenter.starUserGiftsLoaded) {
            if (args[1] == myGifts) {
                if (adapter != null) {
                    adapter.update(true);
                }
            }
        }
    }

    private void updatePremiumTiers() {
        premiumTiers.clear();
        if (premiumTiers.isEmpty() && options != null && !options.isEmpty()) {
            List<QueryProductDetailsParams.Product> products = new ArrayList<>();
            long pricePerMonthMax = 0;
            for (int i = options.size() - 1; i >= 0; i--) {
                final TLRPC.TL_premiumGiftCodeOption option = options.get(i);
                if ("XTR".equalsIgnoreCase(option.currency)) continue;
                Object starsOption = null;
                for (TLRPC.TL_premiumGiftCodeOption o : options) {
                    if (o != option && "XTR".equalsIgnoreCase(o.currency) && o.months == option.months) {
                        starsOption = o;
                        break;
                    }
                }
                final GiftPremiumBottomSheet.GiftTier giftTier = new GiftPremiumBottomSheet.GiftTier(option, starsOption);
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
        return Emoji.replaceEmoji(formatString(R.string.Gift2User, name), null, false);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean pushedTopView = false;
        if (!self && dialogId >= 0 && !(userSettings != null && userSettings.disallow_premium_gifts)) {
            items.add(UItem.asCustom(topView));
            pushedTopView = true;
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
        ArrayList<TL_stars.StarGift> gifts;
        if (birthday) {
            gifts = s.birthdaySortedGifts;
        } else {
            gifts = s.sortedGifts;
        }
        if (userSettings != null) {
            gifts = gifts.stream().filter(gift -> {
                if (gift instanceof TL_stars.TL_starGiftUnique) {
                    return !userSettings.disallow_unique_stargifts;
                } else if (gift.limited) {
                    return !userSettings.disallow_limited_stargifts || gift.can_upgrade && !userSettings.disallow_unique_stargifts;
                } else {
                    return !userSettings.disallow_unlimited_stargifts;
                }
            }).collect(Collectors.toCollection(ArrayList::new));
        }

        if (dialogId < 0) {
            gifts = gifts.stream().filter(gift -> !gift.auction).collect(Collectors.toCollection(ArrayList::new));
        }

        boolean myGiftsHaveUnique = false;
        if (dialogId != UserConfig.getInstance(currentAccount).getClientUserId()) {
            if (myGifts != null) {
                for (TL_stars.SavedStarGift savedStarGift : myGifts.gifts) {
                    if (savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
                        myGiftsHaveUnique = true;
                        break;
                    }
                }
            }
        }
        if (!MessagesController.getInstance(currentAccount).stargiftsBlocked && (!gifts.isEmpty() || userSettings != null && !userSettings.disallow_unique_stargifts && myGifts != null && !myGifts.gifts.isEmpty())) {
            if (!pushedTopView) {
                items.add(UItem.asCustom(topView));
            } else {
                items.add(UItem.asSpace(dp(16)));
            }
            items.add(UItem.asCustom(starsHeaderView));
            boolean hasResale = false;
            final TreeSet<Long> prices = new TreeSet<>();
            if (userSettings == null || !userSettings.disallow_unique_stargifts) {
                for (int i = 0; i < gifts.size(); ++i) {
                    final TL_stars.StarGift gift = gifts.get(i);
                    prices.add(gift.stars);
                    if (gift.availability_resale > 0) {
                        hasResale = true;
                    }
                }
            }

            final ArrayList<CharSequence> tabs = new ArrayList<>();
            TAB_ALL = TAB_IN_STOCK = TAB_LIMITED = TAB_MY_GIFTS = -1;
            if (!gifts.isEmpty()) {
                TAB_ALL = tabs.size();
                tabs.add(getString(R.string.Gift2TabAll));
            }
            if ((userSettings == null || !userSettings.disallow_unique_stargifts) && myGiftsHaveUnique) {
                TAB_MY_GIFTS = tabs.size();
                tabs.add(getString(R.string.Gift2TabMine));
            }
            TAB_COLLECTIBLES = tabs.size();
            tabs.add(getString(R.string.Gift2TabCollectibles));

            items.add(Tabs.Factory.asTabs(1, tabs, selectedTab, this::selectTab));
            setShowCollectiblesInfo(selectedTab == TAB_COLLECTIBLES && !self && dialogId >= 0);

            final ArrayList<TL_stars.StarGift> finalGifts;
            if (myGifts != null && selectedTab == TAB_MY_GIFTS) {
                finalGifts = new ArrayList<>();
                for (TL_stars.SavedStarGift savedStarGift : myGifts.gifts) {
                    if (savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
                        finalGifts.add(savedStarGift.gift);
                    }
                }
            } else {
                finalGifts = gifts;
            }
            int giftsCount = 0;
            for (int i = 0; i < finalGifts.size(); ++i) {
                final TL_stars.StarGift gift = finalGifts.get(i);
                if (
                    selectedTab == TAB_ALL ||
                    selectedTab == TAB_MY_GIFTS ||
                    selectedTab == TAB_COLLECTIBLES && (gift.availability_resale > 0 || gift.require_premium || gift.locked_until_date != 0)
                ) {
                    if (!gift.sold_out && gift.availability_resale > 0 && selectedTab != TAB_COLLECTIBLES) {
                        items.add(GiftCell.Factory.asStarGift(selectedTab, gift, selectedTab == TAB_MY_GIFTS, gift.limited && userSettings != null && userSettings.disallow_limited_stargifts, false, false));
                        giftsCount++;
                    }
                    items.add(GiftCell.Factory.asStarGift(selectedTab, gift, selectedTab == TAB_MY_GIFTS, gift.limited && userSettings != null && userSettings.disallow_limited_stargifts, true, false));
                    giftsCount++;
                }
            }
            if (selectedTab == TAB_MY_GIFTS && myGifts != null && !myGifts.endReached) {
                myGifts.load();
                items.add(UItem.asFlicker(4, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(5, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(6, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            } else if (selectedTab != TAB_MY_GIFTS && s.giftsLoading) {
                items.add(UItem.asFlicker(4, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(5, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(6, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            }
            items.add(UItem.asSpace(dp(giftsCount < 9 ? 300 : 40)));
        } else if (userSettings != null && !userSettings.disallow_unique_stargifts && gifts.isEmpty()) {
            items.add(UItem.asSpace(dp(300)));
        }
    }

    private void selectTab(int tab) {
        if (selectedTab == tab) return;
        selectedTab = tab;
        itemAnimator.endAnimations();
        adapter.update(true);
    }

    public static class GiftCell extends FrameLayout {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;

        private final Shaker shaker;

        private final FrameLayout card;
        private final CardBackground cardBackground;
        private final Ribbon ribbon;
        private final AvatarDrawable avatarDrawable;
        private final BackupImageView avatarView;
        private final FrameLayout.LayoutParams avatarViewLayout1;
        private final FrameLayout.LayoutParams avatarViewLayout2;
        private final FrameLayout pinnedView;
        private final ImageView pinnedImageView;
        private final ImageView tonOnlySaleView;
        public final BackupImageView imageView;
        private final FrameLayout.LayoutParams imageViewLayoutParams;
        private final PremiumLockIconView lockView;
        private final PremiumLockIconView pinView;

        private final TextView titleView;
        private final TextView subtitleView;
        private final FrameLayout priceLayout;
        private final StarsBackgroundView priceBackground;
        private final TextView priceView;
        private final TextView starsPriceView;

        private Runnable cancel;

        public GiftCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            ScaleStateListAnimator.apply(this, .04f, 1.5f);
            this.shaker = new Shaker(this);

            card = new FrameLayout(context);
            card.setBackground(cardBackground = new CardBackground(card, resourcesProvider, true));
            addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            ribbon = new Ribbon(context);
            addView(ribbon, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 2, 1, 0));

            imageView = new BackupImageView(context);
            imageView.getImageReceiver().setAutoRepeat(0);
            card.addView(imageView, imageViewLayoutParams = LayoutHelper.createFrame(80, 80, Gravity.CENTER, 0, 12, 0, 12));

            lockView = new PremiumLockIconView(context, PremiumLockIconView.TYPE_GIFT_LOCK, resourcesProvider);
            lockView.setImageReceiver(imageView.getImageReceiver());
            card.addView(lockView, LayoutHelper.createFrame(30, 30, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 38, 0, 0));

            pinView = new PremiumLockIconView(context, PremiumLockIconView.TYPE_GIFT_PIN, resourcesProvider);
            pinView.setImageReceiver(imageView.getImageReceiver());
            card.addView(pinView, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
            pinView.setAlpha(0.0f);
            pinView.setScaleX(0.3f);
            pinView.setScaleY(0.3f);
            pinView.setVisibility(View.GONE);

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setGravity(Gravity.CENTER);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleView.setTypeface(AndroidUtilities.bold());
            card.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 93 - 4, 0, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            subtitleView.setGravity(Gravity.CENTER);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            card.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 111 - 4, 0, 0));

            priceLayout = new FrameLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    priceBackground.measure(
                        MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY)
                    );
                }
            };
            priceView = new TextView(context);
            priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            priceView.setTypeface(AndroidUtilities.bold());
            priceView.setPadding(dp(10), 0, dp(10), 0);
            priceView.setGravity(Gravity.CENTER);

            priceView.setTextColor(0xFF3391D4);
            card.addView(priceLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 11));

            priceBackground = new StarsBackgroundView(context);
            priceBackground.setBackgroundColor(0xFF0000FF);
            priceLayout.addView(priceBackground, LayoutHelper.createFrame(0, 0));
            priceLayout.addView(priceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.CENTER));

            priceBackground.setBackground(new StarsBackground(Theme.isCurrentThemeDark() ? 0x1EEBA52D : 0x40E8AB02));

            starsPriceView = new TextView(context);
            starsPriceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.66f);
            starsPriceView.setGravity(Gravity.CENTER);
            starsPriceView.setTextColor(Theme.isCurrentThemeDark() ? 0xFFEBA52D : 0xFFD67722);
            starsPriceView.setVisibility(View.GONE);
            card.addView(starsPriceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 161, 0, 8));

            avatarDrawable = new AvatarDrawable();
            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(20));
            avatarView.setVisibility(View.GONE);
            card.addView(avatarView, avatarViewLayout1 = LayoutHelper.createFrame(20, 20, Gravity.TOP | Gravity.LEFT, 2, 2, 2, 2));
            avatarViewLayout2 = LayoutHelper.createFrame(20, 20, Gravity.TOP | Gravity.LEFT, 5, 5, 2, 2);

            pinnedView = new FrameLayout(context);
            pinnedView.setAlpha(0.0f);
            pinnedView.setScaleX(0.3f);
            pinnedView.setScaleY(0.3f);
            pinnedView.setVisibility(View.GONE);

            pinnedImageView = new ImageView(context);
            pinnedImageView.setImageResource(R.drawable.msg_limit_pin);
            pinnedImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            pinnedImageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            pinnedView.addView(pinnedImageView, LayoutHelper.createFrame(12.66f, 12.66f, Gravity.CENTER));

            card.addView(pinnedView, LayoutHelper.createFrame(20, 20, Gravity.TOP | Gravity.LEFT, 2, 2, 2, 2));

            tonOnlySaleView = new ImageView(context);
            tonOnlySaleView.setImageResource(R.drawable.ton_16);
            tonOnlySaleView.setVisibility(GONE);
            tonOnlySaleView.setScaleType(ImageView.ScaleType.CENTER);
            card.addView(tonOnlySaleView, LayoutHelper.createFrame(20, 20, Gravity.TOP | Gravity.LEFT, 3, 3, 3, 3));
        }

        public void setImageSize(int size) {
            imageViewLayoutParams.width = size;
            imageViewLayoutParams.height = size;
        }

        public void setImageLayer(int l) {
            imageView.setLayerNum(l);
        }

        public void hidePrice() {
            priceLayout.setVisibility(GONE);
        }

        public void setSelected(boolean selected, boolean animated) {
            cardBackground.setSelected(selected, animated);
            if (animated) {
                tonOnlySaleView.animate()
                    .translationX(selected ? dp(6) : 0)
                    .translationY(selected ? dp(6) : 0)
                    .setDuration(320)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
            } else {
                tonOnlySaleView.animate().cancel();
                tonOnlySaleView.setTranslationX(selected ? dp(6) : 0);
                tonOnlySaleView.setTranslationY(selected ? dp(6) : 0);
            }
        }

        public void invalidateCustom() {
            card.invalidate();
            card.invalidateDrawable(cardBackground);
        }

        private Text title, subtitle;
        private final Rect cardBackgroundPadding = new Rect();
        public void customDraw(View view, Canvas canvas, float width, float height, float progress) {
            canvas.save();
            canvas.scale(getScaleX(), getScaleY(), width / 2.0f, height / 2.0f);

            final TL_stars.TL_starGiftUnique gift = getUniqueStarGift();
            final float topPadding = gift != null ? dp(63) * progress : 0;

            cardBackground.setBounds(0, 0, (int) width, (int) height);
            cardBackground.draw(canvas, progress);
            cardBackground.getPadding(cardBackgroundPadding);

            final float imageSize = lerp(dp(80), dp(120), progress);
            imageView.getImageReceiver().setImageCoords((width - imageSize) / 2f, (height - topPadding - imageSize) / 2f, imageSize, imageSize);
            imageView.getImageReceiver().draw(canvas);
            if (imageView.getImageReceiver().isLottieRunning()) {
                view.invalidate();
            }

            if (lockView.getVisibility() == View.VISIBLE && lockView.getAlpha() > 0) {
                canvas.save();
                canvas.translate((width - lockView.getMeasuredWidth()) / 2.0f, lerp(lockView.getY(), (height - topPadding - lockView.getMeasuredHeight()) / 2, progress));
                canvas.saveLayerAlpha(0, 0, lockView.getWidth(), lockView.getHeight(), (int) (0xFF * (1.0f - progress) * lockView.getAlpha()), Canvas.ALL_SAVE_FLAG);
                lockView.draw(canvas);
                canvas.restore();
                canvas.restore();
            }

            if (pinnedView.getVisibility() == View.VISIBLE && pinnedView.getAlpha() > 0) {
                canvas.save();
                canvas.translate(cardBackgroundPadding.left + dp(2), cardBackgroundPadding.top + dp(2));
                canvas.saveLayerAlpha(0, 0, pinnedView.getWidth(), pinnedView.getHeight(), (int) (0xFF * pinnedView.getAlpha()), Canvas.ALL_SAVE_FLAG);
                pinnedView.draw(canvas);
                canvas.restore();
                canvas.restore();
            }

            if (avatarView.getVisibility() == View.VISIBLE && avatarView.getAlpha() > 0) {
                canvas.save();
                canvas.translate(cardBackgroundPadding.left + dp(2), cardBackgroundPadding.top + dp(2));
                avatarView.draw(canvas);
                canvas.restore();
            }

            if (ribbon.getVisibility() == View.VISIBLE && ribbon.getAlpha() > 0) {
                canvas.save();
                canvas.translate(width - dp(1), dp(2));
                final float s = lerp(1.0f, 1.25f, progress);
                canvas.scale(s, s);
                canvas.translate(-ribbon.getWidth(), 0);
                ribbon.draw(canvas);
                canvas.restore();
            }

            if (gift != null) {
                if (title == null) {
                    title = new Text(gift.title, 20, AndroidUtilities.bold());
                }
                if (subtitle == null) {
                    subtitle = new Text(LocaleController.formatPluralStringComma("Gift2CollectionNumber", gift.num), 13);
                }

                title
                    .ellipsize(width - dp(8))
                    .draw(canvas, (width - title.getWidth()) / 2.0f, height - dp(40) - title.getHeight() / 2.0f + dp(50) * (1f - progress), 0xFFFFFFFF, progress);

                subtitle
                    .ellipsize(width - dp(8))
                    .draw(canvas, (width - subtitle.getWidth()) / 2.0f, height - dp(19) - subtitle.getHeight() / 2.0f + dp(50) * (1f - progress), 0xFFFFFFFF, .6f * progress);
            }

            if (priceLayout != null && priceLayout.getVisibility() == View.VISIBLE) {
                canvas.save();
                canvas.translate(priceLayout.getX(), priceLayout.getY());
                canvas.saveLayerAlpha(0, 0, priceLayout.getWidth(), priceLayout.getHeight(), (int) (0xFF * (1.0f - progress) * priceLayout.getAlpha()), Canvas.ALL_SAVE_FLAG);
                priceLayout.draw(canvas);
                canvas.restore();
                canvas.restore();
            }

            if (tonOnlySaleView != null && tonOnlySaleView.getVisibility() == View.VISIBLE) {
                canvas.save();
                canvas.translate(tonOnlySaleView.getX(), tonOnlySaleView.getY());
                canvas.saveLayerAlpha(0, 0, tonOnlySaleView.getWidth(), tonOnlySaleView.getHeight(), (int) (0xFF * (1.0f - progress) * tonOnlySaleView.getAlpha()), Canvas.ALL_SAVE_FLAG);
                tonOnlySaleView.draw(canvas);
                canvas.restore();
                canvas.restore();
            }

            canvas.restore();
        }

        private boolean pinned;
        public void setPinned(boolean pin, boolean animated) {
            if (pinned == pin) return;
            pinned = pin;
            if (animated) {
                pinnedView.setVisibility(View.VISIBLE);
                pinnedView.animate()
                    .alpha(pin ? 1.0f : 0.0f)
                    .scaleX(pin ? 1.0f : 0.3f)
                    .scaleY(pin ? 1.0f : 0.3f)
                    .withEndAction(() -> {
                        if (!pin) pinnedView.setVisibility(View.GONE);
                    })
                    .start();
            } else {
                pinnedView.setVisibility(pin ? View.VISIBLE : View.GONE);
                pinnedView.setAlpha(pin ? 1.0f : 0.0f);
                pinnedView.setScaleX(pin ? 1.0f : 0.3f);
                pinnedView.setScaleY(pin ? 1.0f : 0.3f);
            }
            setShowPinIcon(!pinned && reordering && !inCollection && (userGift != null && userGift.gift instanceof TL_stars.TL_starGiftUnique), animated);
            updateRibbonText();
        }

        private TL_stars.TL_starGiftUnique getUniqueStarGift() {
            if (userGift != null && userGift.gift instanceof TL_stars.TL_starGiftUnique) {
                return ((TL_stars.TL_starGiftUnique) userGift.gift);
            }
            return null;
        }

        private boolean pinnedIcon;
        public void setShowPinIcon(boolean pinIcon, boolean animated) {
            if (pinnedIcon == pinIcon) return;
            pinnedIcon = pinIcon;
            if (animated) {
                pinView.setVisibility(View.VISIBLE);
                pinView.animate()
                    .alpha(pinIcon ? 1.0f : 0.0f)
                    .scaleX(pinIcon ? 1.0f : 0.3f)
                    .scaleY(pinIcon ? 1.0f : 0.3f)
                    .withEndAction(() -> {
                        if (!pinIcon) pinView.setVisibility(View.GONE);
                    })
                    .start();
            } else {
                pinView.setVisibility(pinIcon ? View.VISIBLE : View.GONE);
                pinView.setAlpha(pinIcon ? 1.0f : 0.0f);
                pinView.setScaleX(pinIcon ? 1.0f : 0.3f);
                pinView.setScaleY(pinIcon ? 1.0f : 0.3f);
            }
        }

        private boolean reordering;
        private final AnimatedFloat animatedReordering = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        public void setReordering(boolean reordering, boolean animated) {
            if (this.reordering == reordering) return;
            this.reordering = reordering;
            if (!animated) {
                animatedReordering.force(reordering);
            }
            invalidate();
            setShowPinIcon(!pinned && reordering && !inCollection && (userGift != null && userGift.gift instanceof TL_stars.TL_starGiftUnique), animated);
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            canvas.save();
            canvas.translate(getWidth() / 2.0f, getHeight() / 2.0f);
            final float reorderingAlpha = animatedReordering.set(reordering);// * pinnedView.getAlpha();
            if (reorderingAlpha > 0) {
                shaker.concat(canvas, reorderingAlpha);
            }
            canvas.translate(-getWidth() / 2.0f, -getHeight() / 2.0f);
            super.dispatchDraw(canvas);
            canvas.restore();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//            if (getMeasuredHeight() < getMeasuredWidth()) {
//                heightMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
//                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//            }
        }

        private GiftPremiumBottomSheet.GiftTier premiumTier;
        private TL_stars.StarGift gift;
        private boolean priotityAuction;
        private boolean giftMine;
        private TL_stars.SavedStarGift userGift;
        public boolean allowResaleInGifts, inResalePage;
        public boolean inCollection;

        public GiftPremiumBottomSheet.GiftTier getPremiumTier() {
            return premiumTier;
        }
        public TL_stars.StarGift getGift() {
            return gift;
        }
        public TL_stars.SavedStarGift getSavedGift() {
            return userGift;
        }

        private GiftPremiumBottomSheet.GiftTier lastTier;

        public void setPriorityAuction() {
            priotityAuction = true;
        }

        public boolean setPremiumGift(GiftPremiumBottomSheet.GiftTier tier) {
            final int months = tier.getMonths();
            if (lastTier != tier) {
                cancel = StarsIntroActivity.setPremiumGiftImage(imageView, imageView.getImageReceiver(), months);
                if (cancel != null) {
                    cancel.run();
                    cancel = null;
                }
            }

            cardBackground.setBackdrop(null);
            cardBackground.setPattern(null);
            cardBackground.setStrokeColors(null);
            titleView.setText(LocaleController.formatPluralString("Gift2Months", months));
            subtitleView.setText(getString(R.string.TelegramPremiumShort));
            titleView.setVisibility(View.VISIBLE);
            subtitleView.setVisibility(View.VISIBLE);
            imageView.setTranslationY(-dp(8));
            avatarView.setVisibility(View.GONE);
            lockView.setVisibility(View.GONE);
            if (tier.isStarsPaymentAvailable()) {
                starsPriceView.setTextColor(Theme.isCurrentThemeDark() ? 0xFFEBA52D : 0xFFD67722);
                starsPriceView.setVisibility(View.VISIBLE);
                final SpannableStringBuilder starsPrice = new SpannableStringBuilder("" + LocaleController.formatNumber(tier.getStarsPrice(), ','));
                starsPrice.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, starsPrice.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                final ColoredImageSpan[] span = new ColoredImageSpan[1];
                starsPriceView.setText(StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatSpannable(R.string.PremiumOrStarsPrice, starsPrice), .48f, span));
                span[0].spaceScaleX = .8f;
            } else {
                starsPriceView.setVisibility(View.GONE);
            }

            imageViewLayoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            imageView.setLayoutParams(imageViewLayoutParams);

            priceView.setPadding(dp(10), 0, dp(10), 0);
            priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            priceView.setText(tier.getFormattedPrice());
            priceBackground.setBackground(Theme.createRoundRectDrawable(dp(13), 0x193391D4));
            priceView.setTextColor(0xFF3391D4);
            ((MarginLayoutParams) priceLayout.getLayoutParams()).topMargin = dp(130);
            ((FrameLayout.LayoutParams) priceLayout.getLayoutParams()).gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;

            lastTier = tier;
            lastDocument = null;

            this.premiumTier = tier;
            this.gift = null;
            this.giftMine = false;
            this.userGift = null;
            this.allowResaleInGifts = false;
            this.inResalePage = false;
            this.inCollection = false;
            title = null;
            subtitle = null;

            setPinned(false, false);
            updateRibbonText();

            return false;
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

        public static final int[] PREMIUM_STROKE = new int[] { 0xFFD58F25, 0xFFC8851D };
        public boolean setStarsGift(TL_stars.StarGift gift, boolean mine, boolean includeUpgradeInPrice, boolean allowResaleInGifts, boolean inResalePage) {
            if (cancel != null) {
                cancel.run();
                cancel = null;
            }

            setSticker(gift.getDocument(), gift);
            final TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            cardBackground.setBackdrop(backdrop);
            cardBackground.setPattern(findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class));
            if (gift.auction && (!gift.sold_out || priotityAuction)) {
                if (gift.sold_out) {
                    cardBackground.setStrokeColors(new int[] {
                        Theme.getColor(Theme.key_gift_ribbon_soldout, resourcesProvider),
                        Theme.getColor(Theme.key_gift_ribbon_soldout, resourcesProvider)
                    });
                } else {
                    cardBackground.setStrokeColors(PREMIUM_STROKE);
                }
            } else {
                cardBackground.setStrokeColors(gift.require_premium && !(allowResaleInGifts && gift.availability_resale > 0) ? PREMIUM_STROKE : null);
            }
            titleView.setVisibility(View.GONE);
            subtitleView.setVisibility(View.GONE);
            imageView.setTranslationY(0);
            lockView.setVisibility(View.GONE);
            tonOnlySaleView.setVisibility(gift.resale_ton_only ? View.VISIBLE: View.GONE);

            imageViewLayoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            imageView.setLayoutParams(imageViewLayoutParams);

            if (!inResalePage && !(allowResaleInGifts && gift.availability_resale > 0) && gift.locked_until_date > ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
                avatarView.setVisibility(View.VISIBLE);
                avatarView.setLayoutParams(avatarViewLayout2);
                avatarView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_color_red, resourcesProvider), PorterDuff.Mode.SRC_IN));
                avatarView.setImageResource(R.drawable.mini_gift_lock);
            } else {
                avatarView.setColorFilter(null);
                avatarView.setVisibility(View.GONE);
            }

            priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            if (mine) {
                priceView.setPadding(dp(10), 0, dp(10), 0);
                priceView.setText(LocaleController.getString(R.string.Gift2TransferMine));
                final int backgroundColor;
                if (backdrop != null) {
                    backgroundColor = Theme.blendOver(backdrop.center_color | 0xFF000000, Theme.multAlpha(backdrop.pattern_color | 0xFF000000, .55f));
                } else {
                    backgroundColor = 0x40FFFFFF;
                }
                priceBackground.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(13), backgroundColor, Theme.blendOver(backgroundColor, 0x30FFFFFF)));
                priceView.setTextColor(0xFFFFFFFF);

                tonOnlySaleView.setColorFilter(0xFFFFFFFF);
                tonOnlySaleView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(13), backgroundColor, Theme.blendOver(backgroundColor, 0x30FFFFFF)));
            } else if (inResalePage) {
                priceView.setPadding(dp(8), 0, dp(10), 0);
                final long stars = gift.getResellStars();
                final int backgroundColor = Theme.blendOver(backdrop.center_color | 0xFF000000, Theme.multAlpha(backdrop.pattern_color | 0xFF000000, .55f));
                priceView.setText(StarsIntroActivity.replaceStars("XTR " + LocaleController.formatNumber(stars, ',')));
                priceBackground.setBackground(new StarsBackground(0x70FFFFFF, backgroundColor));
                priceView.setTextColor(0xFFFFFFFF);

                tonOnlySaleView.setColorFilter(0xFFFFFFFF);
                tonOnlySaleView.setBackground(Theme.createRoundRectDrawable(dp(13), backgroundColor));
            } else {
                priceView.setPadding(dp(8), 0, dp(10), 0);
                boolean plus = false;
                final long stars;
                if (allowResaleInGifts && gift.availability_resale > 0) {
                    stars = gift.resell_min_stars;
                    if (gift.availability_resale > 1 && stars < MessagesController.getInstance(currentAccount).config.starsStarGiftResaleAmountMax.get()) {
                        plus = true;
                    }
                } else {
                    stars = gift.stars + (includeUpgradeInPrice && gift.can_upgrade ? gift.upgrade_stars : 0);
                }

                if (gift.auction) {
                    priceView.setText(getString(gift.sold_out ? R.string.Gift2AuctionPriceView : R.string.Gift2AuctionPriceJoin));
                } else {
                    priceView.setText(StarsIntroActivity.replaceStarsWithPlain("XTR " + LocaleController.formatNumber(stars, ',') + (plus ? "+" : ""), .71f));
                }

                priceBackground.setBackground(new StarsBackground(gift instanceof TL_stars.TL_starGiftUnique ? 0x40FFFFFF : (Theme.isCurrentThemeDark() ? 0x1EEBA52D : 0x40E8AB02)));
                priceView.setTextColor(Theme.isCurrentThemeDark() ? 0xFFEBA52D : 0xFFD67722);

                tonOnlySaleView.setColorFilter(Theme.isCurrentThemeDark() ? 0xFFEBA52D : 0xFFD67722);
                tonOnlySaleView.setBackground(Theme.createRoundRectDrawable(dp(13), gift instanceof TL_stars.TL_starGiftUnique ? 0x40FFFFFF : (Theme.isCurrentThemeDark() ? 0x1EEBA52D : 0x40E8AB02)));
            }
            ((MarginLayoutParams) priceLayout.getLayoutParams()).topMargin = dp(103);
            ((FrameLayout.LayoutParams) priceLayout.getLayoutParams()).gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            starsPriceView.setVisibility(View.GONE);

            lastTier = null;

            this.premiumTier = null;
            this.gift = gift;
            this.giftMine = mine;
            this.userGift = null;
            this.allowResaleInGifts = allowResaleInGifts;
            this.inResalePage = inResalePage;
            this.inCollection = false;
            title = null;
            subtitle = null;

            setPinned(false, false);
            updateRibbonText();

            return false;
        }

        public long getGiftId() {
            if (gift != null) {
                return gift.id;
            }
            return 0;
        }

        private TL_stars.SavedStarGift lastUserGift;

        public boolean setStarsGift(TL_stars.SavedStarGift userGift, boolean noprice, boolean inCollection) {
            if (cancel != null) {
                cancel.run();
                cancel = null;
            }

            setSticker(userGift.gift.getDocument(), userGift);
            final TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(userGift.gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            cardBackground.setBackdrop(backdrop);
            cardBackground.setPattern(findAttribute(userGift.gift.attributes, TL_stars.starGiftAttributePattern.class));
            cardBackground.setStrokeColors(null);
            titleView.setVisibility(View.GONE);
            subtitleView.setVisibility(View.GONE);
            imageView.setTranslationY(0);
            lockView.setWaitingImage();
            lockView.setBlendWithColor(backdrop != null ? Theme.multAlpha(backdrop.center_color | 0xFF000000, .75f) : null);
            pinView.setWaitingImage();
            pinView.setBlendWithColor(backdrop != null ? Theme.multAlpha(backdrop.center_color | 0xFF000000, .75f) : null);
            tonOnlySaleView.setVisibility(userGift.gift.resale_ton_only ? View.VISIBLE: View.GONE);
            if (backdrop != null) {
                pinnedView.setBackground(Theme.createCircleDrawable(dp(20), Theme.adaptHSV(backdrop.center_color | 0xFF000000, +0.1f, -0.2f)));
            } else {
                pinnedView.setBackground(Theme.createCircleDrawable(dp(20), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
            }

            imageViewLayoutParams.gravity = Gravity.CENTER;
            imageView.setLayoutParams(imageViewLayoutParams);

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
            avatarView.setColorFilter(null);
            avatarView.setLayoutParams(avatarViewLayout1);
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

            if (backdrop != null && userGift.gift.resell_amount != null) {
                priceView.setVisibility(View.VISIBLE);
                imageViewLayoutParams.topMargin = 0;
                imageViewLayoutParams.bottomMargin = 0;
                priceView.setPadding(dp(8), 0, dp(10), 0);
                priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                ColoredImageSpan[] spans = new ColoredImageSpan[1];
                if (userGift.gift.resale_ton_only && DialogObject.getPeerDialogId(userGift.gift.owner_id) == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    priceView.setText(StarsIntroActivity.replaceStars(true, "XTR " + StarsIntroActivity.formatStarsAmount(userGift.gift.getResellAmount(AmountUtils.Currency.TON).toTl(), 1, ','), .95f, spans));
                } else {
                    priceView.setText(StarsIntroActivity.replaceStars("XTR " + LocaleController.formatNumber(userGift.gift.getResellStars(), ','), .95f, spans));
                }
                if (spans[0] != null) {
                    spans[0].translate(0, dp(0.5f));
                }
                final int backgroundColor = Theme.blendOver(backdrop.center_color | 0xFF000000, Theme.multAlpha(backdrop.pattern_color | 0xFF000000, .55f));
                priceBackground.setBackground(new StarsBackground(0x70FFFFFF, backgroundColor));
                priceView.setTextColor(0xFFFFFFFF);
                tonOnlySaleView.setBackground(Theme.createRoundRectDrawable(dp(13), backgroundColor));
                tonOnlySaleView.setColorFilter(0xFFFFFFFF);
                ((FrameLayout.LayoutParams) priceLayout.getLayoutParams()).gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                ((MarginLayoutParams) priceLayout.getLayoutParams()).topMargin = dp(79);
            } else {
                if (noprice) {
                    priceView.setVisibility(View.GONE);
                    imageViewLayoutParams.topMargin = dp(12);
                    imageViewLayoutParams.bottomMargin = dp(12);
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
                priceView.setTextColor(unique ? 0xFFFFFFFF : (Theme.isCurrentThemeDark() ? 0xFFEBA52D : 0xFFBF7600));
                priceBackground.setBackground(new StarsBackground(unique ? 0x40FFFFFF : (Theme.isCurrentThemeDark() ? 0x1EEBA52D : 0x40E8AB02)));
                tonOnlySaleView.setBackground(Theme.createRoundRectDrawable(dp(13), (unique ? 0x40FFFFFF : (Theme.isCurrentThemeDark() ? 0x1EEBA52D : 0x40E8AB02))));
                tonOnlySaleView.setColorFilter(unique ? 0xFFFFFFFF : (Theme.isCurrentThemeDark() ? 0xFFEBA52D : 0xFFBF7600));
                ((FrameLayout.LayoutParams) priceLayout.getLayoutParams()).gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                ((MarginLayoutParams) priceLayout.getLayoutParams()).topMargin = dp(103);
            }
            starsPriceView.setVisibility(View.GONE);

            lastUserGift = userGift;
            lastTier = null;

            final TL_stars.SavedStarGift oldUserGift = this.userGift;
            this.premiumTier = null;
            this.gift = null;
            this.giftMine = false;
            this.userGift = userGift;
            this.allowResaleInGifts = false;
            this.inResalePage = false;
            this.inCollection = inCollection;
            title = null;
            subtitle = null;
            setPinned(userGift.pinned_to_top, oldUserGift == userGift);
            updateRibbonText();

            return oldUserGift == userGift;
        }

        private CheckBox2 checkBox;
        public void setChecked(boolean checked, boolean animated) {
            if (checkBox == null) {
                checkBox = new CheckBox2(getContext(), 21);
                checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
                checkBox.setDrawUnchecked(false);
                card.addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.LEFT, 4, 4, 4, 4));
            }
            avatarView.setVisibility(View.GONE);
            checkBox.setChecked(checked, animated);
        }

        private void updateRibbonText() {
            if (userGift != null) {
                if (userGift.gift instanceof TL_stars.TL_starGiftUnique) {
                    ribbon.setVisibility(View.VISIBLE);
                    if (userGift.gift.resell_amount != null) {
                        final int backgroundColor = Theme.blendOver(
                            Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider),
                            Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.04f)
                        );
                        ribbon.setColor(Theme.getColor(Theme.key_color_green, resourcesProvider));
                        ribbon.setStrokeColor(backgroundColor);
                        ribbon.setBackdrop(null);
                        ribbon.setText(getString(R.string.Gift2OnSale), false);
                    } else {
                        ribbon.setColor(Theme.getColor(Theme.key_gift_ribbon, resourcesProvider));
                        ribbon.setStrokeColor(0);
                        ribbon.setBackdrop(findAttribute(userGift.gift.attributes, TL_stars.starGiftAttributeBackdrop.class));
                        if (pinned) {
                            ribbon.setText("#" + LocaleController.formatNumber(userGift.gift.num, ','), true);
                        } else {
                            ribbon.setText(formatString(R.string.Gift2Limited1OfRibbon, AndroidUtilities.formatWholeNumber(userGift.gift.availability_issued, 0)), true);
                        }
                    }
                } else if (userGift.gift.limited) {
                    ribbon.setVisibility(View.VISIBLE);
                    ribbon.setColor(Theme.getColor(Theme.key_gift_ribbon, resourcesProvider));
                    ribbon.setStrokeColor(0);
                    ribbon.setBackdrop(null);
                    ribbon.setText(formatString(R.string.Gift2Limited1OfRibbon, AndroidUtilities.formatWholeNumber(userGift.gift.availability_total, 0)), true);
                } else {
                    ribbon.setBackdrop(null);
                    ribbon.setVisibility(View.GONE);
                }
            } else if (gift != null) {
                if (inResalePage) {
                    ribbon.setVisibility(View.VISIBLE);
                    ribbon.setColor(Theme.getColor(Theme.key_gift_ribbon, resourcesProvider));
                    ribbon.setBackdrop(findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class));
                    ribbon.setStrokeColor(0);
                    ribbon.setText("#" + LocaleController.formatNumber(gift.num, ','), true);
                } else if (allowResaleInGifts && gift.availability_resale > 0) {
                    ribbon.setVisibility(View.VISIBLE);
                    ribbon.setColor(Theme.getColor(Theme.key_color_green, resourcesProvider));
                    ribbon.setStrokeColor(0);
                    ribbon.setBackdrop(null);
                    ribbon.setText(getString(R.string.Gift2Resale), false);
                } else if (giftMine) {
                    ribbon.setVisibility(View.VISIBLE);
                    ribbon.setColor(Theme.getColor(Theme.key_gift_ribbon, resourcesProvider));
                    ribbon.setStrokeColor(0);
                    ribbon.setBackdrop(findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class));
                    ribbon.setText(formatString(R.string.Gift2Limited1OfRibbon, AndroidUtilities.formatWholeNumber(gift.availability_issued, 0)), true);
                } else if (gift.limited && gift.availability_remains <= 0) {
                    ribbon.setVisibility(View.VISIBLE);
                    ribbon.setColor(Theme.getColor(Theme.key_gift_ribbon_soldout, resourcesProvider));
                    ribbon.setStrokeColor(0);
                    ribbon.setBackdrop(null);
                    ribbon.setText(LocaleController.getString(R.string.Gift2SoldOut), true);
                } else if (gift.auction) {
                    ribbon.setVisibility(View.VISIBLE);
                    ribbon.setBackdrop(null);
                    ribbon.setColors(0xFFD79023, 0xFFBF7D16);
                    ribbon.setStrokeColor(0);
                    ribbon.setText(getString(R.string.Gift2LimitedAuction), true);
                } else if (gift.require_premium) {
                    ribbon.setVisibility(View.VISIBLE);
                    ribbon.setBackdrop(null);
                    ribbon.setColors(0xFFD79023, 0xFFBF7D16);
                    ribbon.setStrokeColor(0);
                    ribbon.setText(getString(R.string.Gift2LimitedPremium), true);
                } else if (gift.limited) {
                    ribbon.setVisibility(View.VISIBLE);
                    ribbon.setColor(Theme.getColor(Theme.key_gift_ribbon, resourcesProvider));
                    ribbon.setStrokeColor(0);
                    ribbon.setBackdrop(null);
                    ribbon.setText(getString(R.string.Gift2LimitedRibbon), true);
                } else {
                    ribbon.setBackdrop(null);
                    ribbon.setStrokeColor(0);
                    ribbon.setVisibility(View.GONE);
                }
            } else if (premiumTier != null) {
                if (premiumTier.getDiscount() > 0) {
                    ribbon.setVisibility(View.VISIBLE);
                    ribbon.setBackdrop(null);
                    ribbon.setColors(0xFFD94FFF, 0xFF826DFF);
                    ribbon.setStrokeColor(0);
                    ribbon.setText(12, formatString(R.string.GiftPremiumOptionDiscount, premiumTier.getDiscount()), true);
                } else {
                    ribbon.setVisibility(View.GONE);
                    ribbon.setBackdrop(null);
                    ribbon.setStrokeColor(0);
                }
            }
        }

        public static class Factory extends UItem.UItemFactory<GiftCell> {
            static { setup(new Factory()); }

            @Override
            public GiftCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new GiftCell(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                boolean animated = false;
                if (item.object instanceof GiftPremiumBottomSheet.GiftTier) {
                    animated = ((GiftCell) view).setPremiumGift((GiftPremiumBottomSheet.GiftTier) item.object);
                } else if (item.object instanceof TL_stars.StarGift) {
                    TL_stars.StarGift gift = (TL_stars.StarGift) item.object;
                    animated = ((GiftCell) view).setStarsGift(gift, item.checked, item.object2 instanceof Boolean ? (Boolean) item.object2 : false, item.accent, item.red);
                } else if (item.object instanceof TL_stars.SavedStarGift) {
                    TL_stars.SavedStarGift gift = (TL_stars.SavedStarGift) item.object;
                    animated = ((GiftCell) view).setStarsGift(gift, item.accent, item.red);
                }
                if (item.collapsed) { // checkable
                    ((GiftCell) view).setChecked(item.checked, animated);
                }
                ((GiftCell) view).setReordering(item.reordering, animated);
            }

            @Override
            public void attachedView(View view, UItem item) {
                ((GiftCell) view).setReordering(item.reordering, false);
            }

            public static UItem asPremiumGift(GiftPremiumBottomSheet.GiftTier tier) {
                final UItem item = UItem.ofFactory(Factory.class).setSpanCount(1);
                item.object = tier;
                return item;
            }

            public static UItem asStarGift(int tab, TL_stars.StarGift gift, boolean mine, boolean includeUpgradeInPrice, boolean allowResaleInGifts, boolean inResalePage) {
                final UItem item = UItem.ofFactory(Factory.class).setSpanCount(1);
                item.intValue = tab;
                item.object = gift;
                item.checked = mine;
                item.object2 = includeUpgradeInPrice;
                item.red = inResalePage;
                item.accent = allowResaleInGifts;
                return item;
            }

            public static UItem asStarGift(int tab, TL_stars.SavedStarGift gift) {
                return asStarGift(tab, gift, false);
            }
            public static UItem asStarGift(int tab, TL_stars.SavedStarGift gift, boolean noprice) {
                return asStarGift(tab, gift, noprice, false, false);
            }
            public static UItem asStarGift(int tab, TL_stars.SavedStarGift gift, boolean noprice, boolean checkable, boolean inCollection) {
                final UItem item = UItem.ofFactory(Factory.class).setSpanCount(1);
                item.intValue = tab;
                item.object = gift;
                item.accent = noprice;
                item.collapsed = checkable;
                item.red = inCollection;
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
                        return ag.gift.id == bg.gift.id && ag.date == bg.date && ag.saved_id == bg.saved_id;
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
    }

    public static class RibbonDrawable extends CompatDrawable {

        private Text text;
        private Path path = new Path();
        private Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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
            strokePaint.setColor(0);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
        }

        public void setColor(int color) {
            paint.setShader(null);
            paint.setColor(color);
        }

        public void setStrokeColor(int color) {
            strokePaint.setColor(color);
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
            canvas.translate(getBounds().right - dp(48), getBounds().top);
            if (strokePaint.getAlpha() > 0) {
                strokePaint.setStrokeWidth(2 * dp(1.33f));
                canvas.drawPath(path, strokePaint);
            }
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

        public void setStrokeColor(int strokeColor) {
            drawable.setStrokeColor(strokeColor);
        }

        public void setColors(int color1, int color2) {
            drawable.setColors(color1, color2);
        }

        public void setBackdrop(TL_stars.starGiftAttributeBackdrop backdrop) {
            drawable.setBackdrop(backdrop, false);
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(dp(50), dp(50));
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            drawable.setBounds(0, 0, getWidth(), getHeight());
            drawable.draw(canvas);
        }
    }

    private static class StarsBackgroundView extends View {
        private StarsBackground currentBackground;

        public StarsBackgroundView(Context context) {
            super(context);
        }

        @Override
        public void setBackground(Drawable background) {
            if (currentBackground != null) {
                if (isAttachedToWindow()) {
                    currentBackground.detach();
                }
                currentBackground = null;
            }

            super.setBackground(background);
            if (background instanceof StarsBackground) {
                currentBackground = (StarsBackground) background;
                if (isAttachedToWindow()) {
                    currentBackground.attach();
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (currentBackground != null) {
                currentBackground.attach();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (currentBackground != null) {
                currentBackground.detach();
            }
        }
    }

    private static class StarsBackground extends Drawable {
        private static int tickIndex;

        private final int particlesColor;
        private final int color;

        public StarsBackground(int color) {
            this(ColorUtils.setAlphaComponent(color, 0x80), color);
        }

        public StarsBackground(int particlesColor, int color) {
            this.particlesColor = particlesColor;
            this.color = color;
            backgroundPaint.setColor(color);

            if (BatchParticlesDrawHelper.isAvailable()) {
                particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 25);
            } else {
                particles = null;
            }
        }

        public final RectF rectF = new RectF();
        public final Path path = new Path();
        public final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public final @Nullable StarsReactionsSheet.Particles particles;

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawPath(path, backgroundPaint);
            if (particles != null && (particlesAllowed || !isAttached)) {
                canvas.save();
                canvas.clipPath(path);
                if (invalidateRunnable == null) {
                    particles.process();
                }
                particles.draw(canvas, particlesColor);
                canvas.restore();

                if (invalidateRunnable == null) {
                    invalidateSelf();
                }
            }
        }

        private void invalidateParticles() {
            if (particles != null) {
                particles.process();
                invalidateSelf();
            }
        }

        private boolean particlesAllowed;
        private void checkParticlesAllowed() {
            boolean particlesAllowed = particles != null && isAttached && LiteMode.isEnabled(LiteMode.FLAG_PARTICLES);

            if (this.particlesAllowed == particlesAllowed) {
                return;
            }
            this.particlesAllowed = particlesAllowed;

            if (particlesAllowed) {
                final int sparse = FrameTickScheduler.getFrameSparseness(15);
                FrameTickScheduler.subscribe(invalidateRunnable = this::invalidateParticles, sparse, 0/*(tickIndex++) % sparse*/);
            } else {
                FrameTickScheduler.unsubscribe(invalidateRunnable);
            }
            invalidateSelf();
        }


        private Runnable invalidateRunnable;
        private Utilities.Callback<Boolean> liteModeCallback;
        private boolean isAttached;

        public void attach() {
            if (!isAttached) {
                isAttached = true;
                checkParticlesAllowed();
                LiteMode.addOnPowerSaverAppliedListener(liteModeCallback = b -> checkParticlesAllowed());
            }
        }

        public void detach() {
            if (isAttached) {
                isAttached = false;
                checkParticlesAllowed();
                LiteMode.removeOnPowerSaverAppliedListener(liteModeCallback);
            }
        }


        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            super.onBoundsChange(bounds);

            final float r = Math.min(bounds.width(), bounds.height()) / 2f;
            rectF.set(bounds);
            path.rewind();
            path.addRoundRect(rectF, r, r, Path.Direction.CW);
            if (particles != null) {
                particles.setBounds(rectF);
            }
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
        public final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clipPath = new Path();

        private TL_stars.starGiftAttributeBackdrop backdrop;

        private int gradientRadius;
        private RadialGradient gradient;
        private final Matrix gradientMatrix = new Matrix();
        private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable pattern;

        private int[] strokeColors;
        private int strokeGradientWidth, strokeGradientHeight;
        private LinearGradient strokeGradient;
        private final Path strokeClipPath = new Path();
        private final Matrix strokeGradientMatrix = new Matrix();

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
            strokePaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            draw(canvas, 0.0f);
        }

        public static final float PADDING_HORIZONTAL_DP = 3.33f;
        public static final float PADDING_VERTICAL_DP = 4;

        public void draw(@NonNull Canvas canvas, float largerParticlesAlpha) {
            Rect bounds = getBounds();
            final float selected = animatedSelected.set(this.selected);
            rect.set(bounds);
            rect.inset(dp(PADDING_HORIZONTAL_DP), dp(PADDING_VERTICAL_DP));
            if (backdrop != null) {
                final int radius = lerp(Math.min(bounds.width(), bounds.height()), Math.max(bounds.width(), bounds.height()), 0.35f) / 2;
                if (gradient == null || gradientRadius != radius) {
                    gradient = new RadialGradient(0, 0, gradientRadius = radius, new int[] { backdrop.center_color | 0xFF000000, backdrop.center_color | 0xFF000000, backdrop.edge_color | 0xFF000000 }, new float[] { 0, 0f, 1 }, Shader.TileMode.CLAMP);
                }
                gradientMatrix.reset();
                gradientMatrix.postTranslate(bounds.centerX(), Math.min(dp(50), bounds.centerY()));
                gradient.setLocalMatrix(gradientMatrix);
                paint.setShader(gradient);
            } else {
                paint.setShader(null);
            }
            canvas.drawRoundRect(rect, dp(11), dp(11), paint);
            final boolean clip = strokeColors != null || backdrop != null && !pattern.isEmpty();
            if (clip) {
                canvas.save();
                clipPath.rewind();
                clipPath.addRoundRect(rect, dp(11), dp(11), Path.Direction.CW);
                canvas.clipPath(clipPath);
            }
            if (strokeColors != null) {
                if (strokeGradient == null) {
                    strokeGradient = new LinearGradient(0, 0, 100, 0, strokeColors, new float[] { 0, 1f }, Shader.TileMode.CLAMP);
                }
                strokeGradientMatrix.reset();
                strokeGradientMatrix.postTranslate(bounds.left, bounds.top);
                strokeGradientMatrix.postRotate((float) (Math.atan2(bounds.height(), bounds.width()) / Math.PI * 180f));
                final float length = (float) Math.sqrt(Math.pow(bounds.width(), 2) + Math.pow(bounds.height(), 2));
                strokeGradientMatrix.postScale(length / 100f, length / 100f);
                strokeGradient.setLocalMatrix(strokeGradientMatrix);
                strokePaint.setShader(strokeGradient);
                strokePaint.setStrokeWidth(dp(4.66f));
                canvas.drawRoundRect(rect, dp(11), dp(11), strokePaint);
            }
            if (backdrop != null && !pattern.isEmpty()) {
                int color = backdrop.pattern_color | 0xFF000000;

                canvas.save();
                canvas.translate(bounds.centerX(), bounds.centerY());
                final float s = lerp(1.0f, 0.925f, selected);
                canvas.scale(s, s);

                boolean drawLegacy = true;
                if (BatchParticlesDrawHelper.isAvailable()) {
                    final Bitmap pBitmap = getStableBitmapFromPattern(pattern);
                    if (pBitmap != null) {
                        // color = 0xFF00FF00;

                        boolean paintChanged = false;
                        if (lastDrawnBitmap != pBitmap || lastDrawnBitmapPaint == null) {
                            lastDrawnBitmap = pBitmap;
                            lastDrawnBitmapPaint = BatchParticlesDrawHelper.createBatchParticlesPaint(pBitmap);
                            paintChanged = true;
                        }
                        if (lastDrawnColor != color || paintChanged) {
                            lastDrawnColor = color;
                            if (Build.VERSION.SDK_INT >= 29) {
                                lastDrawnBitmapPaint.setColorFilter(new BlendModeColorFilter(color, BlendMode.SRC_IN));
                            } else {
                                lastDrawnBitmapPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                            }
                        }

                        if (largerParticlesAlpha < 1)
                            StarGiftPatterns.drawPatternBatch(canvas, StarGiftPatterns.TYPE_GIFT, lastDrawnBitmapPaint, pBitmap, bounds.width(), bounds.height(), 1.0f - largerParticlesAlpha, 1.0f);
                        if (largerParticlesAlpha > 0) {
                            canvas.translate(0, dp(-31));
                            StarGiftPatterns.drawPatternBatch(canvas, StarGiftPatterns.TYPE_DEFAULT, lastDrawnBitmapPaint, pBitmap, bounds.width(), bounds.height(), largerParticlesAlpha, 1.0f);
                        }
                        drawLegacy = false;
                    }
                }

                if (drawLegacy) {
                    pattern.setColor(color);
                    if (largerParticlesAlpha < 1)
                        StarGiftPatterns.drawPattern(canvas, StarGiftPatterns.TYPE_GIFT, pattern, bounds.width(), bounds.height(), 1.0f - largerParticlesAlpha, 1.0f);
                    if (largerParticlesAlpha > 0) {
                        canvas.translate(0, dp(-31));
                        StarGiftPatterns.drawPattern(canvas, StarGiftPatterns.TYPE_DEFAULT, pattern, bounds.width(), bounds.height(), largerParticlesAlpha, 1.0f);
                    }
                }
                canvas.restore();
            }
            if (clip) {
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

        public long patternDocumentId;
        public void setPattern(TL_stars.starGiftAttributePattern pattern) {
            patternDocumentId = 0;
            if (pattern == null) {
                this.pattern.set((Drawable) null, false);
            } else {
                this.pattern.set(pattern.document, false);
                if (pattern.document != null) {
                    patternDocumentId = pattern.document.id;
                }
            }
        }

        public void setStrokeColors(int[] colors) {
            if (strokeColors == colors) return;
            strokeColors = colors;
            strokeGradient = null;
            invalidate();
        }

        public void setSelected(boolean selected, boolean animated) {
            if (this.selected == selected) return;
            this.selected = selected;
            if (!animated) {
                animatedSelected.force(selected);
            }
            invalidate();
        }


        private Bitmap lastDrawnBitmap;
        private Paint lastDrawnBitmapPaint;
        private int lastDrawnColor;

        private Bitmap getStableBitmapFromPattern(AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable d) {
            if (!d.isStable()) {
                return null;
            }

            Drawable drawable = d.getDrawable();
            if (drawable instanceof AnimatedEmojiDrawable) {
                AnimatedEmojiDrawable animatedEmojiDrawable = (AnimatedEmojiDrawable) drawable;

                ImageReceiver imageReceiver = animatedEmojiDrawable.getImageReceiver();
                long documentId = animatedEmojiDrawable.getDocumentId();
                if (imageReceiver != null && documentId == patternDocumentId) {
                    Bitmap bitmap = imageReceiver.getBitmap();
                    if (bitmap != null) {
                        return bitmap;
                    }
                }
            }

            return null;
        }
    }

    public static class Tabs extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;

        private final HorizontalScrollView scrollView;
        private final LinearLayout layout;
        private int selected;
        private AnimatedFloat animatedSelected;
        private final ArrayList<TextView> tabs = new ArrayList<>();

        private final RectF flooredRect = new RectF(), ceiledRect = new RectF();
        private final RectF selectedRect = new RectF();
        private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Tabs(Context context, boolean center, Theme.ResourcesProvider resourcesProvider) {
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
                    rect.set(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
                }
            };
            layout.setClipToPadding(false);
            layout.setClipChildren(false);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setPadding(0, dp(8), 0, dp(10));

            if (center) {
                scrollView = null;
                addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
            } else {
                layout.setPadding(dp(12), dp(8), dp(12), dp(3));

                scrollView = new HorizontalScrollView(context);
                scrollView.setHorizontalScrollBarEnabled(false);
                scrollView.setClipToPadding(false);
                scrollView.setClipChildren(false);
                scrollView.addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
                addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            }

            setHorizontalScrollBarEnabled(false);
            setClipToPadding(false);
            setClipChildren(false);

            animatedSelected = new AnimatedFloat(layout, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        }

        public void setSelected(int selected, boolean animated) {
            this.selected = selected;
            if (!animated) {
                animatedSelected.set(selected, true);
            }
            layout.invalidate();
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
                    final LinkSpanDrawable.LinksTextView tab = new LinkSpanDrawable.LinksTextView(getContext());
                    tab.setGravity(Gravity.CENTER);
                    tab.setText(tabs.get(a));
                    tab.setTypeface(AndroidUtilities.bold());
                    tab.setTextColor(Theme.blendOver(Theme.getColor(Theme.key_dialogGiftsBackground), Theme.getColor(Theme.key_dialogGiftsTabText)));
                    tab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    tab.setPadding(dp(12), 0, dp(12), 0);
                    tab.setEllipsize(TextUtils.TruncateAt.END);
                    tab.setSingleLine();
                    tab.setMaxLines(1);
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
//                    final TextView tab = this.tabs.get(tabIndex);
//                    smoothScrollTo(tab.getLeft() + tab.getWidth() / 2 - getWidth() / 2, 0);
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

        public void updateColors() {
            for (int i = 0; i < this.tabs.size(); ++i) {
                final TextView textView = this.tabs.get(i);
                textView.setTextColor(Theme.blendOver(Theme.getColor(Theme.key_dialogGiftsBackground), Theme.getColor(Theme.key_dialogGiftsTabText)));
            }
            layout.invalidate();
        }

        public static class Factory extends UItem.UItemFactory<Tabs> {
            static { setup(new Factory()); }

            @Override
            public Tabs createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new Tabs(context, true, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
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
