package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.FixedHeightEmptyCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Charts.view_data.ChartHeaderView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkActionView;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.Premium.LimitPreviewView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.ChannelBoostUtilities;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

public class ChannelBoostLayout extends FrameLayout {

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

    private final long dialogId;
    int currentAccount = UserConfig.selectedAccount;
    BaseFragment fragment;

    TLRPC.TL_stories_boostsStatus boostsStatus;

    private final Theme.ResourcesProvider resourcesProvider;

    ArrayList<TLRPC.TL_booster> boosters = new ArrayList<>();
    boolean hasNext;
    int nextRemaining;
    ArrayList<ItemInternal> items = new ArrayList<>();

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
                case BOOST_VIEW:
                    LimitPreviewView limitPreviewView = new LimitPreviewView(getContext(), R.drawable.filled_limit_boost, 10, 0, resourcesProvider);
                    limitPreviewView.isStatistic = true;
                    view = limitPreviewView;
                    Drawable shadowDrawable = Theme.getThemedDrawable(getContext(), R.drawable.greydivider, Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider));
                    Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                    combinedDrawable.setFullsize(true);

                    view.setPadding(0, dp(20), 0, AndroidUtilities.dp(20));
                    view.setBackground(combinedDrawable);
                    limitPreviewView.setBoosts(boostsStatus, false);
                    break;
                case DIVIDER_TEXT_VIEW_TYPE:
                    view = new TextInfoPrivacyCell(parent.getContext(), 12, resourcesProvider);
                    shadowDrawable = Theme.getThemedDrawable(getContext(), R.drawable.greydivider, Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider));
                    background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
                    combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                    combinedDrawable.setFullsize(true);
                    view.setBackground(combinedDrawable);
                    break;
                case DIVIDER_VIEW_TYPE:
                    view = new ShadowSectionCell(parent.getContext(), 12, Theme.getColor(Theme.key_windowBackgroundGray));
                    break;
                case OVERVIEW_TYPE:
                    view = new StatisticActivity.OverviewCell(getContext());
                    break;
                case HEADER_VIEW_TYPE:
                    view = new ChartHeaderView(getContext());
                    view.setPadding(view.getPaddingLeft(), AndroidUtilities.dp(16), view.getRight(), AndroidUtilities.dp(16));
                    break;
                case LINK_VIEW_TYPE:
                    LinkActionView linkActionView = new LinkActionView(getContext(), fragment, null, 0, false, false);
                    view = linkActionView;
                    linkActionView.hideOptions();
                    linkActionView.setLink(ChannelBoostUtilities.createLink(currentAccount, dialogId));
                    view.setPadding(AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11), AndroidUtilities.dp(24));
                    break;
                case USER_VIEW_TYPE:
                    view = new UserCell(getContext(), 0, 0, false);
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
                    textView.setText(LocaleController.getString("NoBoostersHint", R.string.NoBoostersHint));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    textView.setGravity(Gravity.CENTER);
                    frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 7, 0, 0));
                    view = frameLayout;
                    break;
                case SHOW_MORE_VIEW_TYPE:
                    ManageChatTextCell actionCell = new ManageChatTextCell(getContext());
                    actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                   // actionCell.setText(LocaleController.getString("ShowMore", R.string.ShowMore), null, R.drawable.arrow_more, false);
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
            if (holder.getItemViewType() == BOOST_VIEW) {

            } else if (holder.getItemViewType() == HEADER_VIEW_TYPE) {
                ChartHeaderView headerCell = (ChartHeaderView) holder.itemView;
                headerCell.setTitle(items.get(position).title);
                headerCell.showDate(false);
            } else if (holder.getItemViewType() == OVERVIEW_TYPE) {
                StatisticActivity.OverviewCell overviewCell = (StatisticActivity.OverviewCell) holder.itemView;

                overviewCell.setData(0, Integer.toString(boostsStatus.level), null, LocaleController.getString("BoostsLevel2", R.string.BoostsLevel2));
                if (boostsStatus.premium_audience != null && boostsStatus.premium_audience.total == 0) {
                    float percent = (((float) boostsStatus.premium_audience.part / (float) boostsStatus.premium_audience.total) * 100f);
                    overviewCell.setData(1, "~" + (int) boostsStatus.premium_audience.part, String.format(Locale.US, "%.1f",percent) + "%", LocaleController.getString("PremiumSubscribers", R.string.PremiumSubscribers));
                } else {
                    overviewCell.setData(1, "~0", "0%", LocaleController.getString("PremiumSubscribers", R.string.PremiumSubscribers));
                }
                overviewCell.setData(2, String.valueOf(boostsStatus.boosts), null, LocaleController.getString("BoostsExisting", R.string.BoostsExisting));
                overviewCell.setData(3, String.valueOf(boostsStatus.next_level_boosts - boostsStatus.boosts), null, LocaleController.getString("BoostsToLevel", R.string.BoostsToLevel));
            } else if (holder.getItemViewType() == USER_VIEW_TYPE) {
                TLRPC.TL_booster booster = items.get(position).booster;
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(booster.user_id);
                UserCell userCell = (UserCell) holder.itemView;
                String str = LocaleController.formatString("BoostExpireOn", R.string.BoostExpireOn, LocaleController.formatDate(booster.expires));
                userCell.setData(user, ContactsController.formatName(user), str, 0);
            } else if (holder.getItemViewType() == DIVIDER_TEXT_VIEW_TYPE) {
                TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                privacyCell.setText(items.get(position).title);
            } else if (holder.getItemViewType() == SHOW_MORE_VIEW_TYPE) {
                ManageChatTextCell actionCell = (ManageChatTextCell) holder.itemView;
                actionCell.setText(LocaleController.formatPluralString("ShowVotes", nextRemaining), null, R.drawable.arrow_more, false);
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

    RecyclerListView listView;
    boolean usersLoading;
    private LinearLayout progressLayout;

    public ChannelBoostLayout(BaseFragment fragment, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        super(fragment.getContext());
        this.fragment = fragment;
        Context context = fragment.getContext();
        this.resourcesProvider = resourcesProvider;
        this.dialogId = dialogId;
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();
        defaultItemAnimator.setSupportsChangeAnimations(false);
        defaultItemAnimator.setDelayAnimations(false);
        listView.setItemAnimator(defaultItemAnimator);
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof UserCell) {
                UserCell cell = (UserCell) view;
                fragment.presentFragment(ProfileActivity.of(cell.getDialogId()));
            }
            if (items.get(position).viewType == SHOW_MORE_VIEW_TYPE) {
                loadUsers();
            }
        });
        addView(listView);

        loadStatistic();
        listView.setAdapter(adapter);
        updateRows(false);
        createEmptyView(getContext());
        progressLayout.setAlpha(0);
        progressLayout.animate().alpha(1f).setDuration(200).setStartDelay(500).start();
    }

    private void updateRows(boolean animated) {
        ArrayList<ItemInternal> oldItems = new ArrayList<>(items);
        items.clear();
        if (boostsStatus != null) {
            items.add(new ItemInternal(BOOST_VIEW, false));
            items.add(new ItemInternal(HEADER_VIEW_TYPE, LocaleController.getString("StatisticOverview", R.string.StatisticOverview)));
            items.add(new ItemInternal(OVERVIEW_TYPE, true));
            items.add(new ItemInternal(DIVIDER_VIEW_TYPE, false));


            items.add(new ItemInternal(HEADER_VIEW_TYPE, LocaleController.getString("Boosters", R.string.Boosters)));
            if (boosters.isEmpty()) {
                items.add(new ItemInternal(NO_USERS_HINT, false));
                items.add(new ItemInternal(DIVIDER_VIEW_TYPE, false));
            } else {
                for (int i = 0; i < boosters.size(); i++) {
                    items.add(new ItemInternal(USER_VIEW_TYPE, boosters.get(i)));
                }
                if (hasNext) {
                    items.add(new ItemInternal(SHOW_MORE_VIEW_TYPE, false));
                } else {
                    items.add(new ItemInternal(EMPTY_VIEW_8DP, false));
                }
                items.add(new ItemInternal(DIVIDER_TEXT_VIEW_TYPE, LocaleController.getString("BoostersInfoDescription", R.string.BoostersInfoDescription)));
            }
            items.add(new ItemInternal(HEADER_VIEW_TYPE, LocaleController.getString("LinkForBoosting", R.string.LinkForBoosting)));
            items.add(new ItemInternal(LINK_VIEW_TYPE, false));
        }
        if (animated) {
            adapter.setItems(oldItems, items);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    private void loadStatistic() {
        MessagesController.getInstance(currentAccount).getBoostsController().getBoostsStats(dialogId, tl_stories_boostsStatus -> AndroidUtilities.runOnUIThread(() -> {
            boostsStatus = tl_stories_boostsStatus;
            progressLayout.animate().cancel();
            progressLayout.animate().alpha(0).setDuration(100).setStartDelay(0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    progressLayout.setVisibility(View.GONE);
                }
            });
            updateRows(true);
            loadUsers();
        }));
    }


    private void loadUsers() {
        if (usersLoading) {
            return;
        }
        usersLoading = true;
        TLRPC.TL_stories_getBoostersList listReq = new TLRPC.TL_stories_getBoostersList();
        listReq.limit = 25;
        listReq.offset = "";
        listReq.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        ConnectionsManager.getInstance(currentAccount).sendRequest(listReq, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            usersLoading = false;
            if (response != null) {
                TLRPC.TL_stories_boostersList list = (TLRPC.TL_stories_boostersList) response;
                MessagesController.getInstance(currentAccount).putUsers(list.users, false);
                boosters.addAll(list.boosters);
                hasNext = !TextUtils.isEmpty(list.next_offset) && boosters.size() < list.count;
                nextRemaining = list.count - boosters.size();
                updateRows(true);
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private class ItemInternal extends AdapterWithDiffUtils.Item {

        String title;
        TLRPC.TL_booster booster;

        public ItemInternal(int viewType, String title) {
            super(viewType, false);
            this.title = title;
        }

        public ItemInternal(int viewType, TLRPC.TL_booster booster) {
            super(viewType, false);
            this.booster = booster;
        }

        public ItemInternal(int viewType, boolean selectable) {
            super(viewType, selectable);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemInternal that = (ItemInternal) o;
            if (booster != null && that.booster != null) {
                return booster.user_id == that.booster.user_id;
            } else {
                return true;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, booster);
        }

    }

    public void createEmptyView(Context context) {
        progressLayout = new LinearLayout(context);
        progressLayout.setOrientation(LinearLayout.VERTICAL);

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setAutoRepeat(true);
        imageView.setAnimation(R.raw.statistic_preload, 120, 120);
        imageView.playAnimation();


        TextView loadingTitle = new TextView(context);
        loadingTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        loadingTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        loadingTitle.setTextColor(Theme.getColor(Theme.key_player_actionBarTitle));
        loadingTitle.setTag(Theme.key_player_actionBarTitle);
        loadingTitle.setText(LocaleController.getString("LoadingStats", R.string.LoadingStats));
        loadingTitle.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView loadingSubtitle = new TextView(context);
        loadingSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        loadingSubtitle.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        loadingSubtitle.setTag(Theme.key_player_actionBarSubtitle);
        loadingSubtitle.setText(LocaleController.getString("LoadingStatsDescription", R.string.LoadingStatsDescription));
        loadingSubtitle.setGravity(Gravity.CENTER_HORIZONTAL);

        progressLayout.addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));
        progressLayout.addView(loadingTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));
        progressLayout.addView(loadingSubtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        addView(progressLayout, LayoutHelper.createFrame(240, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 30));
    }
}
