package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.tgnet.TLRPC.TL_payments_checkedGiftCode.NO_USER_ID;
import static org.telegram.ui.Components.Premium.LimitReachedBottomSheet.TYPE_FEATURES;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChannelBoostsController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.FixedHeightEmptyCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Charts.view_data.ChartHeaderView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.FillLastLinearLayoutManager;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkActionView;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.Premium.LimitPreviewView;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.Premium.boosts.BoostDialogs;
import org.telegram.ui.Components.Premium.boosts.BoostPagerBottomSheet;
import org.telegram.ui.Components.Premium.boosts.GiftInfoBottomSheet;
import org.telegram.ui.Components.Premium.boosts.cells.statistics.GiftedUserCell;
import org.telegram.ui.Components.Premium.boosts.cells.statistics.GiveawayCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Stars.StarsIntroActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class BoostsActivity extends GradientHeaderActivity implements NotificationCenter.NotificationCenterDelegate {

    private final static int OVERVIEW_TYPE = 0;
    private final static int HEADER_VIEW_TYPE = 1;
    private final static int DIVIDER_VIEW_TYPE = 2;
    private final static int LINK_VIEW_TYPE = 3;
    private final static int BOOST_VIEW = 4;
    private final static int USER_VIEW_TYPE = 5;
    private final static int DIVIDER_TEXT_VIEW_TYPE = 6;
    private final static int EMPTY_VIEW_8DP = 7;
    private final static int NO_USERS_HINT = 8;
    private final static int SHOW_MORE_VIEW_TYPE = 9;
    private final static int SHOW_BOOST_BY_GIFTS = 10;
    private final static int SHOW_PREPARED_GIVE_AWAY = 11;
    private final static int HEADER_VIEW_TYPE_SMALL = 12;
    private final static int HEADER_VIEW_TYPE_TABS = 13;
    private final static int HEADER_PADDING = 14;
    private final static int BOTTOM_PADDING = 15;
    private final static int HEADER_VIEW_TYPE_OVERVIEW = 16;

    private static final int TAB_BOOSTS = 0;
    private static final int TAB_GIFTS = 1;

    private final long dialogId;
    int currentAccount = UserConfig.selectedAccount;

    private TL_stories.TL_premium_boostsStatus boostsStatus;
    private ChannelBoostsController.CanApplyBoost canApplyBoost;
    private ScrollSlidingTextTabStrip boostsTabs;

    private final ArrayList<TL_stories.Boost> boosters = new ArrayList<>();
    private final ArrayList<TL_stories.Boost> gifts = new ArrayList<>();
    private boolean hasBoostsNext;
    private int nextBoostRemaining;
    private boolean hasGiftsNext;
    private int nextGiftsRemaining;
    private final ArrayList<ItemInternal> items = new ArrayList<>();
    private int selectedTab = TAB_BOOSTS;
    private LimitPreviewView limitPreviewView;

    public void setBoostsStatus(TL_stories.TL_premium_boostsStatus boostsStatus) {
        this.boostsStatus = boostsStatus;
        loadCanApplyBoosts();
    }

    AdapterWithDiffUtils adapter = new AdapterWithDiffUtils() {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return items.get(holder.getAdapterPosition()).selectable;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case BOTTOM_PADDING:
                    view = new View(getContext()) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            int lastItemHeight = 0;
                            if (layoutManager instanceof FillLastLinearLayoutManager) {
                                lastItemHeight = ((FillLastLinearLayoutManager) layoutManager).getLastItemHeight();
                            }
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.max(0, lastItemHeight), MeasureSpec.EXACTLY));
                        }
                    };
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                    break;
                case HEADER_PADDING:
                    view = getHeader(getContext());
                    break;
                case DIVIDER_TEXT_VIEW_TYPE:
                    view = new TextInfoPrivacyCell(parent.getContext(), 20, resourceProvider);
                    break;
                case DIVIDER_VIEW_TYPE:
                    view = new ShadowSectionCell(parent.getContext(), 12, Theme.getColor(Theme.key_windowBackgroundGray));
                    break;
                case OVERVIEW_TYPE:
                    view = new StatisticActivity.OverviewCell(getContext());
                    break;
                case HEADER_VIEW_TYPE_OVERVIEW:
                case HEADER_VIEW_TYPE:
                    view = new ChartHeaderView(getContext());
                    view.setPadding(view.getPaddingLeft(), AndroidUtilities.dp(16), view.getRight(), AndroidUtilities.dp(16));
                    break;
                case HEADER_VIEW_TYPE_SMALL:
                    view = new ChartHeaderView(getContext());
                    view.setPadding(view.getPaddingLeft(), AndroidUtilities.dp(16), view.getRight(), AndroidUtilities.dp(8));
                    break;
                case HEADER_VIEW_TYPE_TABS:
                    boostsTabs = new ScrollSlidingTextTabStrip(getContext(), resourceProvider);
                    boostsTabs.setColors(Theme.key_profile_tabSelectedLine, Theme.key_profile_tabSelectedText, Theme.key_profile_tabText, Theme.key_profile_tabSelector);
                    FrameLayout frameLayoutWrapper = new FrameLayout(getContext()) {
                        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                        @Override
                        protected void dispatchDraw(Canvas canvas) {
                            super.dispatchDraw(canvas);
                            dividerPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
                            canvas.drawRect(0, getHeight() - 2, getWidth(), getHeight(), dividerPaint);
                        }
                    };
                    boostsTabs.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
                        @Override
                        public void onPageSelected(int id, boolean forward) {
                            selectedTab = id;
                            updateRows(true);
                        }

                        @Override
                        public void onSamePageSelected() {

                        }

                        @Override
                        public void onPageScrolled(float progress) {

                        }
                    });
                    frameLayoutWrapper.addView(boostsTabs, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48));
                    view = frameLayoutWrapper;
                    break;
                case SHOW_BOOST_BY_GIFTS:
                    TextCell textCell = new TextCell(getContext());
                    textCell.setTextAndIcon(LocaleController.formatString("BoostingGetBoostsViaGifts", R.string.BoostingGetBoostsViaGifts), R.drawable.msg_gift_premium, false);
                    textCell.offsetFromImage = 64;
                    textCell.setColors(Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueText4);
                    view = textCell;
                    break;
                case LINK_VIEW_TYPE:
                    LinkActionView linkActionView = new LinkActionView(getContext(), BoostsActivity.this, null, 0, false, false);
                    view = linkActionView;
                    linkActionView.hideOptions();
                    view.setPadding(AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11), AndroidUtilities.dp(24));
                    break;
                case SHOW_PREPARED_GIVE_AWAY:
                    view = new GiveawayCell(getContext(), 0, 0, false);
                    break;
                case USER_VIEW_TYPE:
                    view = new GiftedUserCell(getContext(), 0, 0, false);
                    break;
                case EMPTY_VIEW_8DP:
                    view = new FixedHeightEmptyCell(getContext(), 8);
                    break;
                case NO_USERS_HINT:
                    FrameLayout frameLayout = new FrameLayout(getContext()) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
                        }
                    };
                    TextView textView = new TextView(getContext());
                    textView.setText(LocaleController.getString(isChannel() ? R.string.NoBoostersHint : R.string.NoBoostersGroupHint));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    textView.setGravity(Gravity.CENTER);
                    frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 16, 0, 0));
                    view = frameLayout;
                    break;
                case SHOW_MORE_VIEW_TYPE:
                    ManageChatTextCell actionCell = new ManageChatTextCell(getContext()) {
                        @Override
                        protected int getFullHeight() {
                            return AndroidUtilities.dp(50);
                        }
                    };
                    actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    view = actionCell;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == BOOST_VIEW || holder.getItemViewType() == HEADER_PADDING || holder.getItemViewType() == BOTTOM_PADDING) {

            } else if (holder.getItemViewType() == HEADER_VIEW_TYPE || holder.getItemViewType() == HEADER_VIEW_TYPE_SMALL || holder.getItemViewType() == HEADER_VIEW_TYPE_OVERVIEW) {
                ChartHeaderView headerCell = (ChartHeaderView) holder.itemView;
                headerCell.setTitle(items.get(position).title);
                headerCell.showDate(false);
                if (holder.getItemViewType() == HEADER_VIEW_TYPE_SMALL) {
                    headerCell.setPadding(dp(3), headerCell.getPaddingTop(), headerCell.getPaddingRight(), headerCell.getPaddingBottom());
                }
                if (holder.getItemViewType() == HEADER_VIEW_TYPE_OVERVIEW) {
                    headerCell.setPadding(dp(6), 0, dp(6), headerCell.getPaddingBottom());
                }
            } else if (holder.getItemViewType() == OVERVIEW_TYPE) {
                StatisticActivity.OverviewCell overviewCell = (StatisticActivity.OverviewCell) holder.itemView;
                overviewCell.setData(0, Integer.toString(boostsStatus.level), null, LocaleController.getString(R.string.BoostsLevel2));
                if (boostsStatus.premium_audience != null && boostsStatus.premium_audience.total != 0) {
                    float percent = (((float) boostsStatus.premium_audience.part / (float) boostsStatus.premium_audience.total) * 100f);
                    overviewCell.setData(1, "≈" + (int) boostsStatus.premium_audience.part, String.format(Locale.US, "%.1f", percent) + "%", LocaleController.getString(isChannel() ? R.string.PremiumSubscribers : R.string.PremiumMembers));
                } else {
                    overviewCell.setData(1, "~0", "0%", LocaleController.getString(isChannel() ? R.string.PremiumSubscribers : R.string.PremiumMembers));
                }
                overviewCell.setData(2, String.valueOf(boostsStatus.boosts), null, LocaleController.getString(R.string.BoostsExisting));
                overviewCell.setData(3, String.valueOf(Math.max(0, boostsStatus.next_level_boosts - boostsStatus.boosts)), null, LocaleController.getString(R.string.BoostsToLevel));
                overviewCell.setPadding(dp(23), overviewCell.getPaddingTop(), dp(23), overviewCell.getPaddingBottom());
            } else if (holder.getItemViewType() == USER_VIEW_TYPE) {
                TL_stories.Boost booster = items.get(position).booster;
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(booster.user_id);
                GiftedUserCell userCell = (GiftedUserCell) holder.itemView;
                String str = booster.multiplier > 1
                        ? LocaleController.formatString("BoostsExpireOn", R.string.BoostsExpireOn, LocaleController.formatDate(booster.expires))
                        : LocaleController.formatString("BoostExpireOn", R.string.BoostExpireOn, LocaleController.formatDate(booster.expires));
                userCell.setData(user, ContactsController.formatName(user), str, 0, !items.get(position).isLast);
                userCell.setStatus(booster);
                userCell.setAvatarPadding(5);
            } else if (holder.getItemViewType() == DIVIDER_TEXT_VIEW_TYPE) {
                TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                privacyCell.setText(items.get(position).title);
                boolean isLast = position == items.size() - 2;
                Drawable shadowDrawable = Theme.getThemedDrawable(getContext(), isLast ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourceProvider));
                Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
                CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                combinedDrawable.setFullsize(true);
                privacyCell.setBackground(combinedDrawable);
            } else if (holder.getItemViewType() == SHOW_MORE_VIEW_TYPE) {
                ManageChatTextCell actionCell = (ManageChatTextCell) holder.itemView;
                if (selectedTab == TAB_BOOSTS) {
                    actionCell.setText(LocaleController.formatPluralString("BoostingShowMoreBoosts", nextBoostRemaining), null, R.drawable.arrow_more, false);
                } else {
                    actionCell.setText(LocaleController.formatPluralString("BoostingShowMoreGifts", nextGiftsRemaining), null, R.drawable.arrow_more, false);
                }
            } else if (holder.getItemViewType() == LINK_VIEW_TYPE) {
                LinkActionView linkActionView = (LinkActionView) holder.itemView;
                linkActionView.setLink(items.get(position).title);
            } else if (holder.getItemViewType() == SHOW_PREPARED_GIVE_AWAY) {
                ItemInternal item = items.get(position);
                TL_stories.PrepaidGiveaway prepaidGiveaway = item.prepaidGiveaway;
                GiveawayCell giveawayCell = (GiveawayCell) holder.itemView;
                if (prepaidGiveaway instanceof TL_stories.TL_prepaidGiveaway) {
                    String name = LocaleController.formatPluralString("BoostingTelegramPremiumCountPlural", prepaidGiveaway.quantity);
                    String info = LocaleController.formatPluralString("BoostingSubscriptionsCountPlural", prepaidGiveaway.quantity, LocaleController.formatPluralString("PrepaidGiveawayMonths", ((TL_stories.TL_prepaidGiveaway) prepaidGiveaway).months));
                    giveawayCell.setData(prepaidGiveaway, name, info, 0, !item.isLast);
                } else if (prepaidGiveaway instanceof TL_stories.TL_prepaidStarsGiveaway) {
                    TL_stories.TL_prepaidStarsGiveaway starsGiveaway = (TL_stories.TL_prepaidStarsGiveaway) prepaidGiveaway;
                    String name = LocaleController.formatPluralStringComma("BoostingStarsCountPlural", (int) starsGiveaway.stars);
                    String info = LocaleController.formatPluralString("AmongWinners", starsGiveaway.quantity);
                    giveawayCell.setData(prepaidGiveaway, name, info, 0, !item.isLast);
                }
                giveawayCell.setImage(prepaidGiveaway);
                giveawayCell.setAvatarPadding(5);
            } else if (holder.getItemViewType() == HEADER_VIEW_TYPE_TABS) {
                if (boostsTabs.getTag() == null || (int) boostsTabs.getTag() != Objects.hash(totalBoosts, totalGifts)) {
                    boostsTabs.setTag(Objects.hash(totalBoosts, totalGifts));
                    boostsTabs.removeTabs();
                    boostsTabs.addTextTab(TAB_BOOSTS, LocaleController.formatPluralString("BoostingBoostsCount", totalBoosts));
                    if (MessagesController.getInstance(currentAccount).giveawayGiftsPurchaseAvailable && totalGifts > 0 && totalGifts != totalBoosts) {
                        boostsTabs.addTextTab(TAB_GIFTS, LocaleController.formatPluralString("BoostingGiftsCount", totalGifts));
                    }
                    boostsTabs.setInitialTabId(selectedTab);
                    boostsTabs.finishAddingTabs();
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }
    };

    boolean usersLoading;
    private LinearLayout progressLayout;
    private final TLRPC.Chat currentChat;

    public BoostsActivity(long dialogId) {
        this.dialogId = dialogId;
        this.currentChat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
    }

    public void updateRows(boolean animated) {
        ArrayList<ItemInternal> oldItems = new ArrayList<>(items);
        items.clear();
        items.add(new ItemInternal(HEADER_PADDING, false));
        if (boostsStatus != null) {
            items.add(new ItemInternal(HEADER_VIEW_TYPE_OVERVIEW, LocaleController.getString(R.string.StatisticOverview)));
            items.add(new ItemInternal(OVERVIEW_TYPE, false));
            items.add(new ItemInternal(DIVIDER_VIEW_TYPE, false));

            if (boostsStatus.prepaid_giveaways.size() > 0) {
                items.add(new ItemInternal(HEADER_VIEW_TYPE_SMALL, LocaleController.getString(R.string.BoostingPreparedGiveaways)));
                for (int i = 0; i < boostsStatus.prepaid_giveaways.size(); i++) {
                    TL_stories.PrepaidGiveaway prepaidGiveaway = boostsStatus.prepaid_giveaways.get(i);
                    items.add(new ItemInternal(SHOW_PREPARED_GIVE_AWAY, prepaidGiveaway, i == boostsStatus.prepaid_giveaways.size() - 1));
                }
                items.add(new ItemInternal(DIVIDER_TEXT_VIEW_TYPE, LocaleController.getString(R.string.BoostingSelectPaidGiveaway)));
            }

            items.add(new ItemInternal(HEADER_VIEW_TYPE_TABS, LocaleController.getString(R.string.Boosters)));
            if (selectedTab == TAB_BOOSTS) {
                if (boosters.isEmpty()) {
                    items.add(new ItemInternal(NO_USERS_HINT, false));
                    items.add(new ItemInternal(DIVIDER_VIEW_TYPE, false));
                } else {
                    for (int i = 0; i < boosters.size(); i++) {
                        items.add(new ItemInternal(USER_VIEW_TYPE, boosters.get(i), i == boosters.size() - 1 && !hasBoostsNext, selectedTab));
                    }
                    if (hasBoostsNext) {
                        items.add(new ItemInternal(SHOW_MORE_VIEW_TYPE, true));
                    } else {
                        items.add(new ItemInternal(EMPTY_VIEW_8DP, false));
                    }
                    items.add(new ItemInternal(DIVIDER_TEXT_VIEW_TYPE, LocaleController.getString(isChannel() ? R.string.BoostersInfoDescription : R.string.BoostersInfoGroupDescription)));
                }
            } else {
                if (gifts.isEmpty()) {
                    items.add(new ItemInternal(NO_USERS_HINT, false));
                    items.add(new ItemInternal(DIVIDER_VIEW_TYPE, false));
                } else {
                    for (int i = 0; i < gifts.size(); i++) {
                        items.add(new ItemInternal(USER_VIEW_TYPE, gifts.get(i), i == gifts.size() - 1 && !hasGiftsNext, selectedTab));
                    }
                    if (hasGiftsNext) {
                        items.add(new ItemInternal(SHOW_MORE_VIEW_TYPE, true));
                    } else {
                        items.add(new ItemInternal(EMPTY_VIEW_8DP, false));
                    }
                    items.add(new ItemInternal(DIVIDER_TEXT_VIEW_TYPE, LocaleController.getString(isChannel() ? R.string.BoostersInfoDescription : R.string.BoostersInfoGroupDescription)));
                }
            }

            items.add(new ItemInternal(HEADER_VIEW_TYPE, LocaleController.getString(R.string.LinkForBoosting)));
            items.add(new ItemInternal(LINK_VIEW_TYPE, boostsStatus.boost_url));
            if (MessagesController.getInstance(currentAccount).giveawayGiftsPurchaseAvailable && ChatObject.hasAdminRights(currentChat)) {
                items.add(new ItemInternal(DIVIDER_TEXT_VIEW_TYPE, LocaleController.getString(isChannel() ? R.string.BoostingShareThisLink : R.string.BoostingShareThisLinkGroup)));
                items.add(new ItemInternal(SHOW_BOOST_BY_GIFTS, true));
                items.add(new ItemInternal(DIVIDER_TEXT_VIEW_TYPE, LocaleController.getString(isChannel() ? R.string.BoostingGetMoreBoosts2 : R.string.BoostingGetMoreBoostsGroup)));
            } else {
                items.add(new ItemInternal(DIVIDER_TEXT_VIEW_TYPE, ""));
            }
            items.add(new ItemInternal(BOTTOM_PADDING, false));
        }
        if (animated) {
            adapter.setItems(oldItems, items);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    private boolean isChannel() {
        return ChatObject.isChannelAndNotMegaGroup(currentChat);
    }

    private void loadStatistic() {
        progressLayout.setAlpha(0);
        if (boostsStatus == null) {
            progressLayout.animate().alpha(1f).setDuration(200).setStartDelay(500).start();
            getMessagesController().getBoostsController().getBoostsStats(dialogId, tl_stories_boostsStatus -> AndroidUtilities.runOnUIThread(() -> {
                boostsStatus = tl_stories_boostsStatus;
                loadCanApplyBoosts();
                progressLayout.animate().cancel();
                progressLayout.animate().alpha(0).setDuration(100).setStartDelay(0).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        progressLayout.setVisibility(View.GONE);
                    }
                });
                resetHeader(true);
                updateRows(true);
                loadUsers(null);
            }));
        } else {
            progressLayout.setVisibility(View.GONE);
            loadUsers(null);
        }
    }

    private void loadCanApplyBoosts() {
        if (boostsStatus == null) {
            return;
        }
        getMessagesController().getBoostsController().userCanBoostChannel(dialogId, boostsStatus, canApplyBoost -> this.canApplyBoost = canApplyBoost);
    }

    private String lastBoostsOffset = "";
    private String lastGiftsOffset = "";
    private int limitGifts = 5;
    private int limitBoosts = 5;
    private int totalGifts;
    private int totalBoosts;

    private void loadUsers(Boolean isGift) {
        if (usersLoading) {
            return;
        }
        usersLoading = true;
        if (isGift == null) {
            Utilities.globalQueue.postRunnable(() -> {
                CountDownLatch latch = new CountDownLatch(2);
                loadOnlyBoosts(latch, null);
                loadOnlyGifts(latch, null);
                try {
                    latch.await();
                } catch (InterruptedException ignore) {

                }
                NotificationCenter.getInstance(currentAccount).doOnIdle(() -> AndroidUtilities.runOnUIThread(() -> {
                    usersLoading = false;
                    updateRows(true);
                }));
            });
        } else if (isGift) {
            loadOnlyGifts(null, () -> {
                usersLoading = false;
                updateRows(true);
            });
        } else {
            loadOnlyBoosts(null, () -> {
                usersLoading = false;
                updateRows(true);
            });
        }
    }

    private void loadOnlyBoosts(CountDownLatch latch, Runnable after) {
        TL_stories.TL_premium_getBoostsList listReq = new TL_stories.TL_premium_getBoostsList();
        listReq.limit = limitBoosts;
        listReq.offset = lastBoostsOffset;
        listReq.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);

        ConnectionsManager.getInstance(currentAccount).sendRequest(listReq, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (latch != null) {
                latch.countDown();
            }
            if (response != null) {
                limitBoosts = 20;
                TL_stories.TL_premium_boostsList list = (TL_stories.TL_premium_boostsList) response;
                MessagesController.getInstance(currentAccount).putUsers(list.users, false);
                lastBoostsOffset = list.next_offset;
                boosters.addAll(list.boosts);
                int shownBoosts = 0;
                for (TL_stories.Boost booster : boosters) {
                    shownBoosts += booster.multiplier > 0 ? booster.multiplier : 1;
                }
                nextBoostRemaining = Math.max(0, list.count - shownBoosts);
                hasBoostsNext = !TextUtils.isEmpty(list.next_offset) && nextBoostRemaining > 0;
                totalBoosts = list.count;
                if (after != null) {
                    after.run();
                }
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void loadOnlyGifts(CountDownLatch latch, Runnable after) {
        TL_stories.TL_premium_getBoostsList listReq = new TL_stories.TL_premium_getBoostsList();
        listReq.limit = limitGifts;
        listReq.gifts = true;
        listReq.offset = lastGiftsOffset;
        listReq.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);

        ConnectionsManager.getInstance(currentAccount).sendRequest(listReq, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (latch != null) {
                latch.countDown();
            }
            if (response != null) {
                limitGifts = 20;
                TL_stories.TL_premium_boostsList list = (TL_stories.TL_premium_boostsList) response;
                MessagesController.getInstance(currentAccount).putUsers(list.users, false);
                lastGiftsOffset = list.next_offset;
                gifts.addAll(list.boosts);
                int shownGifts = 0;
                for (TL_stories.Boost booster : gifts) {
                    shownGifts += booster.multiplier > 0 ? booster.multiplier : 1;
                }
                nextGiftsRemaining = Math.max(0, list.count - shownGifts);
                hasGiftsNext = !TextUtils.isEmpty(list.next_offset) && nextGiftsRemaining > 0;
                totalGifts = list.count;
                if (after != null) {
                    after.run();
                }
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.boostByChannelCreated) {
            TLRPC.Chat chat = (TLRPC.Chat) args[0];
            boolean isGiveaway = (boolean) args[1];
            List<BaseFragment> fragmentStack = getParentLayout().getFragmentStack();
            BaseFragment chatEditFragment = fragmentStack.size() >= 2 ? fragmentStack.get(fragmentStack.size() - 2) : null;
            if (chatEditFragment instanceof ChatEditActivity) {
                getParentLayout().removeFragmentFromStack(chatEditFragment);
            }
            fragmentStack = getParentLayout().getFragmentStack();
            BaseFragment secondFragment = fragmentStack.size() >= 2 ? fragmentStack.get(fragmentStack.size() - 2) : null;
            if (isGiveaway) {
                BaseFragment thirdFragment = fragmentStack.size() >= 3 ? fragmentStack.get(fragmentStack.size() - 3) : null;
                if (secondFragment instanceof ProfileActivity) {
                    getParentLayout().removeFragmentFromStack(secondFragment);
                }
                finishFragment();
                if (thirdFragment instanceof ChatActivity) {
                    BoostDialogs.showBulletin(thirdFragment, chat, true);
                }
                if (secondFragment instanceof ChatActivity) {
                    BoostDialogs.showBulletin(secondFragment, chat, true);
                }
            } else {
                finishFragment();
                if (secondFragment instanceof ProfileActivity || secondFragment instanceof ChatActivity) {
                    BoostDialogs.showBulletin(secondFragment, chat, false);
                }
            }
        } else if (id == NotificationCenter.chatWasBoostedByUser) {
            if (dialogId == (long) args[2]) {
                boostsStatus = (TL_stories.TL_premium_boostsStatus) args[0];
                canApplyBoost = (ChannelBoostsController.CanApplyBoost) args[1];
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.boostByChannelCreated);
        getNotificationCenter().addObserver(this, NotificationCenter.chatWasBoostedByUser);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.boostByChannelCreated);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatWasBoostedByUser);
        super.onFragmentDestroy();
    }

    private class ItemInternal extends AdapterWithDiffUtils.Item {

        String title;
        TL_stories.Boost booster;
        TL_stories.PrepaidGiveaway prepaidGiveaway;
        boolean isLast;
        int tab;

        public ItemInternal(int viewType, String title) {
            super(viewType, false);
            this.title = title;
        }

        public ItemInternal(int viewType, TL_stories.Boost booster, boolean isLast, int tab) {
            super(viewType, true);
            this.booster = booster;
            this.isLast = isLast;
            this.tab = tab;
        }

        public ItemInternal(int viewType, TL_stories.PrepaidGiveaway prepaidGiveaway, boolean isLast) {
            super(viewType, true);
            this.prepaidGiveaway = prepaidGiveaway;
            this.isLast = isLast;
        }

        public ItemInternal(int viewType, boolean selectable) {
            super(viewType, selectable);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemInternal that = (ItemInternal) o;
            if (prepaidGiveaway != null && that.prepaidGiveaway != null) {
                return prepaidGiveaway.id == that.prepaidGiveaway.id && isLast == that.isLast;
            } else if (booster != null && that.booster != null) {
                return booster.id.hashCode() == that.booster.id.hashCode() && isLast == that.isLast && tab == that.tab;
            } else {
                return true;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, booster, prepaidGiveaway, isLast, tab);
        }
    }

    public void createEmptyView(Context context) {
        progressLayout = new LinearLayout(context);
        progressLayout.setOrientation(LinearLayout.VERTICAL);

        View view = new View(context) {
            private final CircularProgressDrawable drawable;

            {
                drawable = new CircularProgressDrawable(dp(30), dp(3), Theme.getColor(Theme.key_dialogTextBlue));
            }

            @Override
            protected void onDraw(Canvas canvas) {
                drawable.setBounds(0, 0, getWidth(), getHeight());
                drawable.setAlpha(255);
                drawable.draw(canvas);
                invalidate();
                super.onDraw(canvas);
            }
        };
        progressLayout.addView(view, LayoutHelper.createLinear(100, 100, Gravity.CENTER, 0, 120, 0, 0));
        ((ViewGroup) fragmentView).addView(progressLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
    }

    @Override
    protected RecyclerView.Adapter<?> createAdapter() {
        return adapter;
    }

    private void resetHeader(boolean animate) {
        if (getContext() == null) return;
        if (limitPreviewView == null) {
            limitPreviewView = new LimitPreviewView(getContext(), R.drawable.filled_limit_boost, 0, 0, resourceProvider);
            limitPreviewView.isStatistic = true;
            limitPreviewView.setDarkGradientProvider(this::setDarkGradientLocation);
        }

        if (limitPreviewView.getParent() != null) {
            ((ViewGroup) limitPreviewView.getParent()).removeView(limitPreviewView);
        }

        if (boostsStatus != null) {
            limitPreviewView.setBoosts(boostsStatus, false);
            if (animate) {
                limitPreviewView.setAlpha(0f);
                limitPreviewView.animate().alpha(1f).start();
            }
        }

        CharSequence title = LocaleController.getString(isChannel() ? R.string.BoostingBoostForChannels : R.string.BoostingBoostForGroups);
        CharSequence subTitle = AndroidUtilities.replaceTags(LocaleController.getString(isChannel() ? R.string.BoostingBoostForChannelsInfo : R.string.BoostingBoostForGroupsInfo));

        View aboveTitleView = new FrameLayout(getContext()) {
            {
                addView(boostsStatus != null ? limitPreviewView : new View(getContext()), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, boostsStatus != null ? LayoutHelper.WRAP_CONTENT : 110, 0, 8, 46, 8, 33));
            }
        };

        View underSubTitleView = new FrameLayout(getContext()) {

            private final HeaderButtonView buttonView1;
            private final HeaderButtonView buttonView2;
            private final HeaderButtonView buttonView3;

            {
                setWillNotDraw(false);
                buttonView1 = new HeaderButtonView(getContext());
                buttonView2 = new HeaderButtonView(getContext());
                buttonView3 = new HeaderButtonView(getContext());

                buttonView1.setTextAndIcon(LocaleController.getString(R.string.BoostBtn), R.drawable.filled_boost_plus);
                buttonView2.setTextAndIcon(LocaleController.getString(R.string.GiveawayBtn), R.drawable.filled_gift_premium);
                buttonView3.setTextAndIcon(LocaleController.getString(R.string.FeaturesBtn), R.drawable.filled_info);
                buttonView1.setOnClickListener(v -> {
                    LimitReachedBottomSheet.openBoostsForUsers(BoostsActivity.this, true, dialogId, canApplyBoost, boostsStatus, null);
                });
                buttonView2.setOnClickListener(v -> {
                    updateDialogVisibility(true);
                    BoostPagerBottomSheet.show(BoostsActivity.this, dialogId, resourceProvider);
                    BoostPagerBottomSheet.getInstance().setOnHideListener(dialog -> updateDialogVisibility(false));
                });
                buttonView3.setOnClickListener(v -> {
                    LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(BoostsActivity.this, getContext(), TYPE_FEATURES, currentAccount, getResourceProvider());
                    limitReachedBottomSheet.setBoostsStats(boostsStatus, true);
                    limitReachedBottomSheet.setDialogId(dialogId);
                    showDialog(limitReachedBottomSheet);
                });
                LinearLayout linearLayout = new LinearLayout(getContext());
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                linearLayout.addView(buttonView1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 6, 0));
                if (MessagesController.getInstance(currentAccount).giveawayGiftsPurchaseAvailable && ChatObject.hasAdminRights(currentChat)) {
                    linearLayout.addView(buttonView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 6, 0));
                }
                linearLayout.addView(buttonView3, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 6, 0));

                addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 19, 0, 0));
            }
        };
        configureHeader(title, subTitle, aboveTitleView, underSubTitleView);
    }

    private class HeaderButtonView extends FrameLayout {

        private final ImageView imageView;
        private final TextView textView;
        private final RectF rect = new RectF();

        public HeaderButtonView(@NonNull Context context) {
            super(context);
            setWillNotDraw(false);
            imageView = new ImageView(context);
            textView = new TextView(context);
            textView.setTextColor(Color.WHITE);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 25, 0, 0));
            setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            setMinimumWidth(AndroidUtilities.dp(100));
            setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, 80)));
        }

        public void setTextAndIcon(CharSequence text, int icon) {
            textView.setText(text);
            imageView.setImageDrawable(ContextCompat.getDrawable(getContext(), icon));
        }

        @Override
        public void draw(Canvas canvas) {
            rect.set(0, 0, getWidth(), getHeight());
            Paint p = setDarkGradientLocation((((ViewGroup) getParent()).getX() + getX()), ((ViewGroup) getParent().getParent().getParent()).getY());
            canvas.drawRoundRect(rect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), p);
            invalidate();
            super.draw(canvas);
        }
    }

    @Override
    public View createView(Context context) {
        View rootView = super.createView(context);
        resetHeader(false);
        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();
        defaultItemAnimator.setSupportsChangeAnimations(false);
        defaultItemAnimator.setDelayAnimations(false);
        listView.setItemAnimator(defaultItemAnimator);
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof GiftedUserCell) {
                GiftedUserCell cell = (GiftedUserCell) view;
                TL_stories.Boost boost = cell.getBoost();
                if (boost.giveaway && boost.stars > 0) {
                    StarsIntroActivity.showBoostsSheet(context, currentAccount, dialogId, boost, getResourceProvider());
                } else if (((boost.gift || boost.giveaway) && boost.user_id >= 0) || boost.unclaimed) {
                    TLRPC.TL_payments_checkedGiftCode giftCode = new TLRPC.TL_payments_checkedGiftCode();
                    giftCode.giveaway_msg_id = boost.giveaway_msg_id;
                    giftCode.to_id = boost.user_id;
                    giftCode.from_id = MessagesController.getInstance(UserConfig.selectedAccount).getPeer(-currentChat.id);
                    giftCode.date = boost.date;
                    giftCode.via_giveaway = boost.giveaway;
                    giftCode.days = (boost.expires - boost.date) / 86400;
                    giftCode.months = (boost.expires - boost.date) / 30 / 86400;
                    if (boost.unclaimed) {
                        giftCode.to_id = NO_USER_ID;
                        giftCode.flags = -1;
                    } else {
                        giftCode.boost = boost;
                    }
                    new GiftInfoBottomSheet(this, false, true, giftCode, boost.used_gift_slug).show();
                } else if (boost.giveaway && boost.user_id == NO_USER_ID) {
                    final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getParentActivity(), getResourceProvider());
                    layout.setAnimation(R.raw.chats_infotip, 36, 36);
                    layout.textView.setText(LocaleController.getString(R.string.BoostingRecipientWillBeSelected));
                    layout.textView.setSingleLine(false);
                    layout.textView.setMaxLines(2);
                    Bulletin.make(this, layout, Bulletin.DURATION_LONG).show();
                } else if (!boost.gift && !boost.giveaway) {
                    presentFragment(ProfileActivity.of(cell.getDialogId()));
                }
            }
            if (view instanceof TextCell) {
                BoostPagerBottomSheet.show(this, dialogId, resourceProvider);
            }
            if (view instanceof GiveawayCell) {
                BoostPagerBottomSheet.show(this, resourceProvider, dialogId, ((GiveawayCell) view).getPrepaidGiveaway());
            }
            if (items.get(position).viewType == SHOW_MORE_VIEW_TYPE) {
                loadUsers(selectedTab == TAB_GIFTS);
            }
        });

        createEmptyView(getContext());
        loadStatistic();
        updateRows(false);
        return rootView;
    }
}
